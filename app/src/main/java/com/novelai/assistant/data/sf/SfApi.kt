package com.novelai.assistant.data.sf

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.util.UUID

class SfApi(
    private val transport: SfTransport,
    private val device: String,
    val guard: SfRequestGuard = SfRequestGuard()
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var embeddedShelves: Map<String, List<SfShelfBook>> = emptyMap()
    fun clearShelfCache() { embeddedShelves = emptyMap() }

    private suspend fun request(
        path: String,
        cookie: String = "",
        body: String? = null,
        method: String = if (body == null) "GET" else "POST",
        recovery: Boolean = false
    ): Pair<JsonElement, SfResponse> {
        // Public reads carry no account session. A transient 417 is a protocol error,
        // not proof of account risk. Re-sign at most twice, still using the shared queue.
        val anonymousRead = cookie.isBlank() && body == null && method == "GET" &&
            (path == "noveltypes" || listOf("novels/", "novel/", "ranks/", "search/", "Chaps/").any { path.startsWith(it) })
        val attempts = if (anonymousRead) 3 else 1
        repeat(attempts) { attempt ->
            try {
                return requestOnce(path, cookie, body, method, recovery)
            } catch (e: SfException) {
                if (e.kind != SfFailureKind.SIGNATURE || attempt == attempts - 1) throw e
                delay((attempt + 1) * 1_000L)
            }
        }
        error("Unreachable")
    }

    private suspend fun requestOnce(
        path: String,
        cookie: String,
        body: String?,
        method: String = if (body == null) "GET" else "POST",
        recovery: Boolean = false
    ): Pair<JsonElement, SfResponse> = guard.request(path, recovery) {
        val nonce = UUID.randomUUID().toString().uppercase()
        val timestamp = System.currentTimeMillis()
        val headers = mutableMapOf(
            "Accept" to "application/vnd.sfacg.api+json;version=1",
            "Authorization" to "Basic YW5kcm9pZHVzZXI6MWEjJDUxLXl0Njk7KkFjdkBxeHE=",
            "User-Agent" to "boluobao/5.2.16(android;35)/OPPO/${device.lowercase()}/OPPO",
            "sfsecurity" to "nonce=$nonce&timestamp=$timestamp&devicetoken=$device&sign=${SfProtocol.signature(nonce, timestamp, device)}"
        )
        if (cookie.isNotBlank()) headers["Cookie"] = cookie
        if (body != null) headers["Content-Type"] = "application/json; charset=UTF-8"
        val response = try {
            transport.exchange(path, headers, body, method)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SfException) {
            throw e
        } catch (_: Exception) {
            throw SfException(SfFailureKind.NETWORK, "无法连接菠萝包，请检查网络后重试")
        }
        // HTTP-level controls must be processed even when a proxy returns HTML or an empty body.
        if (response.status == 429) throw SfException(SfFailureKind.RATE_LIMIT, "请求被限流", response.retryAfterMillis)
        if (response.status in 500..599 || response.status == 408) {
            throw SfException(SfFailureKind.NETWORK, "服务暂不可用，请稍后重试")
        }
        if (response.status in 300..399) {
            throw SfException(SfFailureKind.CLIENT, "服务要求重定向，请检查客户端更新")
        }
        val payload = try {
            json.parseToJsonElement(response.body).jsonObject
        } catch (_: Exception) {
            if (Regex("验证码|安全验证|captcha", RegexOption.IGNORE_CASE).containsMatchIn(response.body)) {
                throw SfException(SfFailureKind.RISK, "平台要求安全验证，已停止请求")
            }
            if (response.status == 417) throw SfException(SfFailureKind.SIGNATURE, SIGNATURE_MESSAGE)
            throw SfException(
                when (response.status) {
                    401 -> SfFailureKind.AUTH
                    403 -> SfFailureKind.RISK
                    else -> SfFailureKind.NETWORK
                },
                "服务未返回可识别的数据，已停止本次操作"
            )
        }
        val status = payload.obj("status")
        val code = status.text("httpCode").toIntOrNull() ?: response.status
        val serverMessage = status.text("msg") + status.text("message")
        // 立即熔断风控，绝不盲目重试防止账号封控
        if (status.text("errorCode") == "1148") {
            throw SfException(SfFailureKind.RISK, "账号需要安全验证（1148）。请到官方菠萝包 App 完成验证，再回来重新登录")
        }
        if (Regex("验证码|安全验证|风险|风控|请求频繁|异常登录|captcha|verification", RegexOption.IGNORE_CASE).containsMatchIn(serverMessage)) {
            throw SfException(SfFailureKind.RISK, "平台要求安全验证，已停止请求")
        }
        if (status.text("errorCode") == "507") {
            throw SfException(SfFailureKind.CLIENT, "菠萝包拒绝了当前客户端标识（507），接口适配需要更新")
        }
        if (code == 429) throw SfException(SfFailureKind.RATE_LIMIT, "请求被限流", response.retryAfterMillis)
        if (code == 417 || response.status == 417) throw SfException(SfFailureKind.SIGNATURE, SIGNATURE_MESSAGE)
        if (code == 401 || response.status == 401) {
            throw SfException(
                SfFailureKind.AUTH,
                if (body == null) "登录已失效，请重新登录"
                else "登录未成功，请检查账号密码；若需安全验证，请先到官方 App 完成"
            )
        }
        if (code == 403 || response.status == 403) {
            throw SfException(
                if (path.startsWith("Chaps/") && Regex("购买|订阅|未购|未订|权限").containsMatchIn(serverMessage)) {
                    SfFailureKind.UNPURCHASED
                } else {
                    SfFailureKind.RISK
                },
                "账号无权读取此内容，请在官方 App 确认权限或安全验证"
            )
        }
        if (code !in 200..299 || response.status !in 200..299) {
            throw SfException(
                if (code in 500..599) SfFailureKind.NETWORK else SfFailureKind.INVALID,
                "菠萝包接口返回状态 $code，请稍后重试"
            )
        }
        (payload["data"] ?: JsonNull) to response
    }

    private fun validateChapterRequest(id: String, cookie: String, paid: Boolean): String {
        require(id.matches(Regex("\\d+")))
        if (paid && cookie.isBlank()) {
            throw SfException(SfFailureKind.AUTH, "请先登录菠萝包账号，再读取已购章节")
        }
        return if (paid) cookie else ""
    }

    private companion object {
        const val SIGNATURE_MESSAGE = "菠萝包暂未接受请求签名，请稍后重新加载（417）"
    }

    suspend fun login(username: String, password: String): Pair<SfAccount, String> {
        require(username.isNotBlank() && password.isNotBlank()) { "请填写账号和密码" }
        val body = buildJsonObject {
            put("username", username.trim())
            put("password", password)
            put("shuMeiId", "")
        }.toString()
        val response = request("sessions", body = body, recovery = true).second
        val cookie = SfProtocol.loginCookie(response.cookies)
        val account = SfProtocol.account(request("user", cookie, recovery = true).first.jsonObject)
        guard.loginSucceeded()
        return account to cookie
    }

    suspend fun inspect(cookie: String): SfAccount = SfProtocol.account(request("user", cookie).first.jsonObject)

    suspend fun shelves(cookie: String): List<Pair<String, String>> {
        if (cookie.isBlank()) throw SfException(SfFailureKind.AUTH, "请先登录菠萝包账号")
        val data = request("user/Pockets?expand=novels", cookie).first as? JsonArray
            ?: throw SfException(SfFailureKind.INVALID, "书架列表格式已变化")
        embeddedShelves = data.map { it.jsonObject }.filter { it.obj("expand")["novels"] is JsonArray }.associate { shelf ->
            shelf.text("pocketId") to shelf.obj("expand").array("novels")
                .mapNotNull { (it as? JsonObject)?.let { b -> SfProtocol.shelfBook(b, shelf.text("name")) } }
        }
        return data.map { it.jsonObject }.map { it.text("pocketId") to it.text("name") }
    }

    suspend fun shelfPage(cookie: String, shelfId: String, name: String, page: Int): List<SfShelfBook> {
        if (cookie.isBlank()) throw SfException(SfFailureKind.AUTH, "请先登录菠萝包账号")
        require(shelfId.matches(Regex("-?\\d+")) && page > 0)
        embeddedShelves[shelfId]?.let { return it.drop((page - 1) * 30).take(30) }
        val data = request("pockets/$shelfId/novels?page=$page&size=30&expand=novel", cookie).first
        val list = if (data is JsonArray) data else (data as? JsonObject)?.array("novels")
            ?: throw SfException(SfFailureKind.INVALID, "书架作品列表格式已变化")
        return list.mapNotNull { (it as? JsonObject)?.let { item -> SfProtocol.shelfBook(item, name) } }
    }

    suspend fun catalog(id: String): SfCatalog {
        require(id.matches(Regex("\\d+"))) { "无效书号" }
        val meta = request("novels/$id?expand=bigNovelCover").first.jsonObject
        val dirs = request("novels/$id/dirs?expand=originNeedFireMoney").first.jsonObject
        val chapters = dirs.array("volumeList").flatMap { volume ->
            val v = volume.jsonObject
            v.array("chapterList").map { item ->
                val c = item.jsonObject
                SfRemoteChapter(
                    c.text("chapId"),
                    c.text("title").ifBlank { c.text("chapName") },
                    c.flag("isVip") || (c.text("needFireMoney").toDoubleOrNull() ?: 0.0) > 0 ||
                        (c.text("originNeedFireMoney").toDoubleOrNull() ?: 0.0) > 0,
                    v.text("title")
                )
            }
        }.distinctBy { it.id }
        if (chapters.isEmpty() || chapters.any { !it.id.matches(Regex("\\d+")) }) {
            throw SfException(SfFailureKind.INVALID, "没有取得有效章节目录")
        }
        return SfCatalog(
            id,
            meta.text("novelName"),
            meta.text("authorName"),
            meta.obj("expand").text("bigNovelCover").ifBlank { meta.text("novelCover") }.takeIf { it.isNotBlank() },
            chapters
        )
    }

    suspend fun chapter(id: String, cookie: String, paid: Boolean, decode: (String) -> String): String {
        val requestCookie = validateChapterRequest(id, cookie, paid)
        val data = request("Chaps/$id?expand=content%2Cexpand.content", requestCookie).first.jsonObject
        return SfProtocol.content(data, paid, decode)
    }

    suspend fun chapterWithComments(
        id: String,
        cookie: String,
        paid: Boolean,
        decode: (String) -> String
    ): Pair<String, List<SfParagraphCommentSummary>> {
        val requestCookie = validateChapterRequest(id, cookie, paid)
        val data = request(
            "Chaps/$id?expand=content%2Cexpand.content%2Ctsukkomi%2Cchatlines&autoOrder=false",
            requestCookie
        ).first.jsonObject
        return SfProtocol.chapterContentAndCommentSummaries(data, paid, decode)
    }

    suspend fun search(keyword: String, page: Int = 1, size: Int = 20): List<SfSearchResult> {
        val q = URLEncoder.encode(keyword.trim(), "UTF-8")
        val data = request("search/novels/result?q=$q&page=$page&size=$size&expand=sysTags,tags,intro,bigNovelCover").first
        return SfProtocol.parseSearchResults(data)
    }

    suspend fun browseRank(sort: String = "hot", page: Int = 1, size: Int = 20): List<SfSearchResult> {
        val p0 = (page - 1).coerceAtLeast(0)
        val route = when (sort.lowercase()) {
            "month", "view" -> "ranks/month/novels?page=$p0&size=$size&expand=bigNovelCover,sysTags,tags,intro"
            "week", "mark" -> "ranks/week/novels?page=$p0&size=$size&expand=bigNovelCover,sysTags,tags,intro"
            "date", "latest" -> "novels/0/sysTags/novels?page=$p0&size=$size&expand=bigNovelCover,sysTags,tags,intro"
            else -> "ranks/all/novels?page=$p0&size=$size&expand=bigNovelCover,sysTags,tags,intro"
        }
        val data = request(route).first
        return SfProtocol.parseSearchResults(data)
    }

    suspend fun novelDetail(id: String): SfBookDetail {
        require(id.matches(Regex("\\d+"))) { "无效书号" }
        val meta = request("novels/$id?expand=bigNovelCover,tags,sysTags,intro,typeName,authorName,author,point,ticket,fav")
            .first.jsonObject
        return SfProtocol.parseBookDetail(id, meta)
    }

    suspend fun paragraphReplies(commentId: String): List<SfParagraphReply> {
        require(commentId.matches(Regex("\\d+")))
        val data = request("tsukkomis/$commentId/replys?expand=avatar%2CroleName").first
        if (data !is JsonArray) throw SfException(SfFailureKind.INVALID, "回复返回格式已变化")
        return SfProtocol.parseParagraphReplies(JsonObject(mapOf("replys" to data)))
    }

    suspend fun paragraphComments(
        chapterId: String,
        page: Int = 1,
        size: Int = 20,
        sort: String = "hot",
        row: Int? = null
    ): List<SfParagraphComment> {
        require(chapterId.matches(Regex("\\d+")) && page > 0 && size in 1..50)
        val p0 = page - 1
        val order = if (sort == "new") "addtime" else "hot"
        val rowQuery = row?.takeIf { it >= 0 }?.let { "&row=$it" }.orEmpty()
        val expand = "&expand=vipLevel%2Cavatar%2CroleName%2Cwidgets%2Cgrowup%2Creplys"
        val data = request("chaps/0/$chapterId/tsukkomis?page=$p0&size=$size&sort=$order$rowQuery$expand").first
        return SfProtocol.parseParagraphComments(data)
    }

    suspend fun fetchOfficialTags(): List<SfSysTag> {
        val data = request("novels/0/sysTags").first
        val list = if (data is JsonArray) data else (data as? JsonObject)?.array("sysTags") ?: emptyList()
        return list.mapNotNull {
            val obj = it as? JsonObject ?: return@mapNotNull null
            val id = obj.text("sysTagId").toIntOrNull() ?: return@mapNotNull null
            val name = obj.text("tagName").ifBlank { return@mapNotNull null }
            SfSysTag(id, name)
        }
    }

    suspend fun fetchCategories(): List<SfCategory> = request("noveltypes").first.jsonArray.mapNotNull {
        val category = it as? JsonObject ?: return@mapNotNull null
        val id = category.text("typeId").toIntOrNull() ?: return@mapNotNull null
        category.text("typeName").takeIf { name -> name.isNotBlank() }?.let { name -> SfCategory(id, name) }
    }

    suspend fun browseFiltered(
        filter: SfBrowseFilter,
        page: Int = 1,
        size: Int = 20,
        sort: String = "viewtimes"
    ): List<SfSearchResult> {
        require(filter.categoryId >= 0 && filter.tagIds.all { it > 0 } && page > 0 && size in 1..50)
        require(sort in listOf("viewtimes", "updatetime"))
        val p0 = (page - 1).coerceAtLeast(0)
        val ids = filter.tagIds.sorted().joinToString(",")
        val tags = if (ids.isEmpty()) "" else "&systagids=$ids"
        val route = "novels/${filter.categoryId}/sysTags/novels?page=$p0&size=$size&sort=$sort$tags&expand=bigNovelCover,sysTags,tags,intro"
        val data = request(route).first
        return SfProtocol.parseSearchResults(data)
    }

    suspend fun wealth(cookie: String): SfWealth {
        if (cookie.isBlank()) throw SfException(SfFailureKind.AUTH, "请先登录菠萝包账号")
        val data = request("user/money", cookie).first.jsonObject
        val exp = data.obj("expand")
        val fireMoney = data.text("fireMoneyRemain").toIntOrNull()
            ?: data.text("fireMoney").toIntOrNull()
            ?: exp.text("fireMoneyRemain").toIntOrNull()
            ?: data.text("money").toIntOrNull()
            ?: data.text("fireCoin").toIntOrNull()
            ?: 0
        val coupons = data.text("couponsRemain").toIntOrNull()
            ?: data.text("coupons").toIntOrNull()
            ?: exp.text("couponsRemain").toIntOrNull()
            ?: data.text("coupon").toIntOrNull()
            ?: 0
        val vipLevel = data.text("vipLevel").toIntOrNull()
            ?: exp.text("vipLevel").toIntOrNull()
            ?: 0
        val tickets = data.text("ticketRemain").toIntOrNull()
            ?: data.text("ticket").toIntOrNull()
            ?: data.text("tickets").toIntOrNull()
            ?: exp.text("ticket").toIntOrNull()
            ?: 0
        return SfWealth(fireMoney, coupons, vipLevel, tickets)
    }

    suspend fun newRecommendations(page: Int = 1, size: Int = 20): List<SfSearchResult> {
        require(page > 0 && size in 1..50)
        return SfDiscoveryParser.recommendations(
            request("novels/specialpushs?pushNames=newpush&page=${page - 1}&size=$size&expand=bigNovelCover,sysTags,intro").first
        )
    }

    suspend fun relatedBookLists(id: String, page: Int = 1, size: Int = 20): List<SfBookList> {
        require(id.matches(Regex("\\d+")) && page > 0 && size in 1..50)
        return SfDiscoveryParser.bookLists(request("novel/$id/bookList?page=${page - 1}&size=$size").first)
    }

    suspend fun sign(cookie: String): SfSignResult {
        if (cookie.isBlank()) throw SfException(SfFailureKind.AUTH, "请先登录菠萝包账号")
        return try {
            // 优先尝试移动端新签到接口 PUT user/newSign
            val (data, _) = request("user/newSign", cookie, body = "{}", method = "PUT")
            val msg = (data as? JsonObject)?.text("msg")?.ifBlank { "签到成功，已领取代券" } ?: "签到成功"
            SfSignResult(true, msg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SfException) {
            throw e
        } catch (e: Exception) {
            val err = e.message.orEmpty()
            if (err.contains("已签到") || err.contains("重复") || err.contains("今天已经签到") || err.contains("明日再来")) {
                SfSignResult(true, err.ifBlank { "今日已完成签到，无需重复签到" })
            } else {
                SfSignResult(false, err.ifBlank { "签到未成功，请稍后重试" })
            }
        }
    }

    suspend fun addToCloudShelf(cookie: String, novelId: String, pocketId: String = "-1"): SfSignResult {
        if (cookie.isBlank()) throw SfException(SfFailureKind.AUTH, "请先登录菠萝包账号")
        val numId = novelId.removePrefix("sf:").toIntOrNull() ?: throw SfException(SfFailureKind.INVALID, "无效书号")
        val body = buildJsonObject { put("novelId", numId) }.toString()
        return try {
            val (data, _) = request("pockets/$pocketId/novels", cookie, body = body, method = "POST")
            val msg = (data as? JsonObject)?.text("msg")?.ifBlank { "已成功加入菠萝包云端书架" } ?: "已成功加入菠萝包云端书架"
            SfSignResult(true, msg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val err = e.message.orEmpty()
            if (err.contains("已存在") || err.contains("重复") || err.contains("已经添加")) {
                SfSignResult(true, "该书已在菠萝包云端书架")
            } else {
                SfSignResult(false, err.ifBlank { "加入云端书架失败，请稍后重试" })
            }
        }
    }

    suspend fun novelComments(novelId: String, page: Int = 1, size: Int = 20): List<SfComment> {
        val numId = novelId.removePrefix("sf:")
        require(numId.matches(Regex("\\d+"))) { "无效书号" }
        val p0 = (page - 1).coerceAtLeast(0)
        val data = request("novels/$numId/Cmts?page=$p0&size=$size&type=clear&sort=smart&replyUserId=0").first
        return parseNovelComments(data)
    }

    fun parseNovelComments(data: JsonElement): List<SfComment> = SfProtocol.parseNovelComments(data)
}

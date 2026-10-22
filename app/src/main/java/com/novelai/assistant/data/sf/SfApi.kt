package com.novelai.assistant.data.sf

import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject

enum class SfFailureKind { AUTH, RISK, UNPURCHASED, NETWORK, SIGNATURE, INVALID, RATE_LIMIT, CLIENT, COOLDOWN }
class SfException(val kind: SfFailureKind, message: String, val retryAfterMillis: Long? = null) : Exception(message)
data class SfAccount(val id: String, val name: String, val vipLevel: Int = 0, val avatar: String? = null)
data class SfWealth(val fireMoney: Int = 0, val coupons: Int = 0, val vipLevel: Int = 0, val tickets: Int = 0)
data class SfSearchResult(
    val id: String,
    val title: String,
    val author: String,
    val cover: String?,
    val intro: String = "",
    val tags: List<String> = emptyList(),
    val point: Float = 0f,
    val markCount: Long = 0L,
    val viewTimes: Long = 0L
)
data class SfSysTag(val id: Int, val name: String)
data class SfComment(
    val id: String,
    val displayName: String,
    val avatar: String?,
    val content: String,
    val createTime: String,
    val favCount: Int = 0,
    val replyNum: Int = 0,
    val vipLevel: Int = 0,
    val isStick: Boolean = false
)
data class SfBookDetail(
    val id: String,
    val title: String,
    val author: String,
    val cover: String?,
    val intro: String = "",
    val tags: List<String> = emptyList(),
    val typeName: String = "",
    val isFinish: Boolean = false,
    val charCount: Long = 0L,
    val markCount: Long = 0L,
    val viewTimes: Long = 0L,
    val favCount: Long = 0L,
    val ticketCount: Int = 0,
    val point: Float = 0f,
    val lastUpdateTime: String = "",
    val latestChapterTitle: String = "",
    val totalChapters: Int = 0
)
data class SfParagraphComment(
    val id: String,
    val row: Int,
    val displayName: String,
    val avatar: String?,
    val content: String,
    val createTime: String,
    val favCount: Int = 0,
    val replyNum: Int = 0,
    val roleName: String = ""
)
data class SfSignResult(val success: Boolean, val message: String)
data class SfShelfBook(val id: String, val title: String, val author: String, val cover: String?, val shelf: String)
data class SfRemoteChapter(val id: String, val title: String, val paid: Boolean, val volume: String)
data class SfCatalog(val id: String, val title: String, val author: String, val cover: String?, val chapters: List<SfRemoteChapter>)
data class SfResponse(val status: Int, val body: String, val cookies: List<String> = emptyList(), val retryAfterMillis: Long? = null)

fun interface SfTransport {
    suspend fun exchange(path: String, headers: Map<String, String>, body: String?): SfResponse
    suspend fun exchange(path: String, headers: Map<String, String>, body: String?, method: String): SfResponse = exchange(path, headers, body)
}

class KtorSfTransport @Inject constructor() : SfTransport {
    private val sfClient = HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
        followRedirects = false; expectSuccess = false
        engine { config {
            followRedirects(false); followSslRedirects(false); retryOnConnectionFailure(false)
            connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
        } }
    }
    override suspend fun exchange(path: String, headers: Map<String, String>, body: String?): SfResponse =
        exchange(path, headers, body, if (body == null) "GET" else "POST")

    override suspend fun exchange(path: String, headers: Map<String, String>, body: String?, method: String): SfResponse = withTimeoutOrNull(25_000) {
        require(!path.contains("..") && !path.contains('#') && !path.contains('\r') && !path.contains('\n'))
        require(method in listOf("GET", "POST", "PUT", "DELETE"))
        val response = sfClient.request("https://api.sfacg.com/$path") {
            this.method = HttpMethod.parse(method.uppercase())
            headers.forEach { (key, value) -> this.headers.append(key, value) }
            if (body != null) setBody(body)
        }
        SfResponse(response.status.value, response.bodyAsText(), response.headers.getAll("Set-Cookie").orEmpty(),
            SfHttpPolicy.retryAfter(response.headers["Retry-After"]))
    } ?: throw SfException(SfFailureKind.NETWORK, "菠萝包请求超时，请稍后重试")
}

object SfHttpPolicy {
    fun retryAfter(value: String?, now: Long = System.currentTimeMillis()): Long? {
        if (value == null) return null
        value.trim().toLongOrNull()?.takeIf { it >= 0 }?.let { return it.coerceAtMost(Long.MAX_VALUE / 1000) * 1000 }
        return runCatching { (java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant().toEpochMilli() - now).coerceAtLeast(0) }.getOrNull()
    }
}

internal fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.obj(key: String): JsonObject = get(key) as? JsonObject ?: JsonObject(emptyMap())
internal fun JsonObject.array(key: String): List<JsonElement> = (get(key) as? JsonArray).orEmpty()
internal fun JsonObject.flag(key: String): Boolean = text(key) == "true" || text(key) == "1"

object SfProtocol {
    // The legacy SF App protocol currently rejects unregistered random IDs (401 / 507).
    // This is the compatibility identifier used by the referenced client, not a hardware identifier.
    const val COMPATIBLE_DEVICE = "910D166A-736E-3231-8B21-8D12DFD75F16"
    fun signature(nonce: String, timestamp: Long, device: String): String {
        val repeat = nonce.repeat(4)
        val lengths = listOf(13, 16, 36, 36)
        val mix = lengths.mapIndexed { i, n -> repeat.substring(repeat[i + 1].code % 36).take(n) }.joinToString("")
        val auth = "$timestamp" + "lPQDb9AKO7\$LjkPG" + device + nonce
        require(mix.length == 101 && auth.length >= 101)
        val value = mix.mapIndexed { i, c -> ((c.code + auth[i].code) shr 1).toChar() }.joinToString("")
        val reordered = value.substring(65) + value.substring(0, 13) + value.substring(29, 65) + value.substring(13, 29)
        val normalized = reordered.map { c ->
            val n = c.code
            when {
                n < 48 -> if (n + 19 in 58..64) '9' else (n + 19).toChar()
                n in 58..64 || n in 91..96 -> (n + 19).toChar()
                else -> c
            }
        }.joinToString("")
        return MessageDigest.getInstance("MD5").digest(normalized.toByteArray()).joinToString("") { "%02X".format(it) }
    }

    fun loginCookie(headers: List<String>): String {
        val combined = headers.joinToString(", ")
        return listOf(".SFCommunity", "session_APP").joinToString("; ") { name ->
            val match = Regex("(?:^|[,;]\\s*)${Regex.escape(name)}=([^;,\\s]+)").find(combined)
                ?: throw SfException(SfFailureKind.INVALID, "登录未返回完整的 App 会话，请稍后重试")
            "$name=${match.groupValues[1]}"
        }
    }

    fun account(data: JsonObject): SfAccount {
        val id = data.text("accountId").ifBlank { data.text("userId") }.ifBlank { data.text("id") }
        if (id.isBlank()) throw SfException(SfFailureKind.INVALID, "账号验证返回格式已变化")
        val vip = data.text("vipLevel").toIntOrNull() ?: data.obj("expand").text("vipLevel").toIntOrNull() ?: 0
        val avatar = data.text("avatar").ifBlank { data.obj("expand").text("avatar") }.takeIf { it.isNotBlank() }
        return SfAccount(id, data.text("nickName").ifBlank { data.text("nickname") }.ifBlank { data.text("userName") }.ifBlank { "菠萝包读者" }, vip, avatar)
    }

    fun shelfBook(data: JsonObject, shelf: String): SfShelfBook? {
        val book = (data["novel"] as? JsonObject) ?: data
        val id = book.text("novelId")
        if (!id.matches(Regex("\\d+"))) return null
        return SfShelfBook(id, book.text("novelName").ifBlank { "未命名作品" }, book.text("authorName"),
            book.text("novelCover").ifBlank { book.obj("expand").text("bigNovelCover") }.takeIf { it.isNotBlank() }, shelf)
    }

    fun content(data: JsonObject, paid: Boolean = true, decode: (String) -> String): String {
        if ((paid && listOf("isPurchased", "isBuy").any { data.text(it) == "false" || data.obj("expand").text(it) == "false" }) || data.flag("isPreview") || data.obj("expand").flag("isPreview")) {
            throw SfException(SfFailureKind.UNPURCHASED, "本章未购买或只返回试读内容，请在官方 App 确认订阅")
        }
        val raw = data.text("content") + data.obj("expand").text("content")
        if (raw.isBlank()) throw SfException(SfFailureKind.UNPURCHASED, "未取得完整正文，请在官方 App 确认本章已购买")
        val text = org.jsoup.Jsoup.parse(decode(raw).replace(Regex("<br\\s*/?>|</p>", RegexOption.IGNORE_CASE), "\n\n"))
            .wholeText().trim()
        if (text.isBlank() || Regex("^(请先登录|登录后阅读|购买本章|订阅本章)").containsMatchIn(text)) {
            throw SfException(SfFailureKind.UNPURCHASED, "本章没有可阅读正文，请确认订阅权限")
        }
        return text
    }

    fun parseSearchResult(obj: JsonObject): SfSearchResult? {
        val id = obj.text("novelId").ifBlank { obj.text("id") }
        if (!id.matches(Regex("\\d+"))) return null
        val title = obj.text("novelName").ifBlank { obj.text("name") }.ifBlank { "未命名作品" }
        val author = obj.text("authorName").ifBlank { obj.text("author") }
        val cover = obj.obj("expand").text("bigNovelCover").ifBlank { obj.text("novelCover") }.takeIf { it.isNotBlank() }
        val intro = obj.obj("expand").text("intro").ifBlank { obj.text("intro") }
        val exp = obj.obj("expand")
        val tags = (exp["sysTags"] as? JsonArray ?: obj["sysTags"] as? JsonArray ?: exp["tags"] as? JsonArray ?: obj["tags"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.text("tagName")?.takeIf { t -> t.isNotBlank() } ?: (it as? JsonPrimitive)?.contentOrNull?.takeIf { t -> t.isNotBlank() } }
            ?: emptyList()
        val point = obj.text("point").toFloatOrNull() ?: 0f
        val mark = obj.text("markCount").toLongOrNull() ?: 0L
        val views = obj.text("viewTimes").toLongOrNull() ?: 0L
        return SfSearchResult(id, title, author, cover, intro, tags, point, mark, views)
    }

    fun parseSearchResults(data: JsonElement): List<SfSearchResult> {
        val list = if (data is JsonArray) data else (data as? JsonObject)?.array("novels") ?: emptyList()
        return list.mapNotNull { (it as? JsonObject)?.let { obj -> parseSearchResult(obj) } }
    }

    fun parseParagraphComments(data: JsonElement): List<SfParagraphComment> {
        val list = when (data) {
            is JsonArray -> data
            is JsonObject -> data.array("tsukkomis")
                .ifEmpty { data.array("items") }
            else -> throw SfException(SfFailureKind.INVALID, "章节间贴返回格式已变化")
        }
        if (list.any { it !is JsonObject }) throw SfException(SfFailureKind.INVALID, "章节间贴条目格式已变化")
        return list.map { item ->
            val obj = item.jsonObject
            val id = obj.text("tsukkomiId").ifBlank { obj.text("id") }
            val content = obj.text("content").ifBlank { obj.text("text") }.let(::plainComment)
            val user = obj["postUser"] as? JsonObject
                ?: obj["user"] as? JsonObject
                ?: obj.obj("expand")["user"] as? JsonObject
            if (!id.matches(Regex("\\d+")) || content.isBlank()) {
                throw SfException(SfFailureKind.INVALID, "章节间贴条目缺少编号或正文")
            }
            val row = obj.text("row").toIntOrNull()
                ?: obj.text("paraIndex").toIntOrNull()
                ?: obj.text("paragraphIndex").toIntOrNull()
                ?: obj.text("chapParaIndex").toIntOrNull()
                ?: 0
            SfParagraphComment(
                id = id,
                row = row,
                displayName = user?.text("nickName").orEmpty()
                    .ifBlank { obj.text("displayName") }
                    .ifBlank { "菠萝包读者" },
                avatar = user?.text("avatar").orEmpty()
                    .ifBlank { obj.text("avatar") }
                    .takeIf { it.isNotBlank() },
                content = content,
                createTime = obj.text("postDate")
                    .ifBlank { obj.text("createTime") }
                    .replace("T", " ")
                    .substringBefore('.'),
                favCount = obj.text("favNum").toIntOrNull()
                    ?: obj.text("favCount").toIntOrNull()
                    ?: obj.text("likeCount").toIntOrNull()
                    ?: 0,
                replyNum = obj.text("replyNum").toIntOrNull() ?: 0,
                roleName = obj.text("roleName").ifBlank { obj.text("title") }
            )
        }
    }

    private fun plainComment(raw: String): String =
        org.jsoup.Jsoup.parse(
            raw.replace(Regex("\\[em:sfgirl:\\d+]", RegexOption.IGNORE_CASE), "[表情]")
                .replace(Regex("<br\\s*/?>|</p>", RegexOption.IGNORE_CASE), "\n")
        ).wholeText().trim()
}

class SfApi(private val transport: SfTransport, private val device: String, val guard: SfRequestGuard = SfRequestGuard()) {
    private val json = Json { ignoreUnknownKeys = true }
    private var embeddedShelves: Map<String, List<SfShelfBook>> = emptyMap()
    fun clearShelfCache() { embeddedShelves = emptyMap() }

    private suspend fun request(path: String, cookie: String = "", body: String? = null,
        method: String = if (body == null) "GET" else "POST", recovery: Boolean = false): Pair<JsonElement, SfResponse> {
        // Public reads carry no account session. A transient 417 is a protocol error,
        // not proof of account risk. Re-sign at most twice, still using the shared queue.
        val anonymousRead = cookie.isBlank() && body == null && method == "GET" &&
            (path == "noveltypes" || listOf("novels/", "novel/", "ranks/", "search/", "Chaps/").any { path.startsWith(it) })
        val attempts = if (anonymousRead) 3 else 1
        repeat(attempts) { attempt ->
            try { return requestOnce(path, cookie, body, method, recovery) }
            catch (e: SfException) {
                if (e.kind != SfFailureKind.SIGNATURE || attempt == attempts - 1) throw e
                delay((attempt + 1) * 1_000L)
            }
        }
        error("Unreachable")
    }

    private suspend fun requestOnce(path: String, cookie: String, body: String?,
        method: String = if (body == null) "GET" else "POST", recovery: Boolean = false): Pair<JsonElement, SfResponse> = guard.request(path, recovery) {
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
            val response = try { transport.exchange(path, headers, body, method) }
            catch (e: CancellationException) { throw e }
            catch (e: SfException) { throw e }
            catch (_: Exception) { throw SfException(SfFailureKind.NETWORK, "无法连接菠萝包，请检查网络后重试") }
            // HTTP-level controls must be processed even when a proxy returns HTML or an empty body.
            if (response.status == 429) throw SfException(SfFailureKind.RATE_LIMIT, "请求被限流", response.retryAfterMillis)
            if (response.status in 500..599 || response.status == 408) throw SfException(SfFailureKind.NETWORK, "服务暂不可用，请稍后重试")
            if (response.status in 300..399) throw SfException(SfFailureKind.CLIENT, "服务要求重定向，请检查客户端更新")
            val payload = try { json.parseToJsonElement(response.body).jsonObject }
            catch (_: Exception) {
                if (Regex("验证码|安全验证|captcha", RegexOption.IGNORE_CASE).containsMatchIn(response.body))
                    throw SfException(SfFailureKind.RISK, "平台要求安全验证，已停止请求")
                if (response.status == 417) throw SfException(SfFailureKind.SIGNATURE, SIGNATURE_MESSAGE)
                throw SfException(when (response.status) { 401 -> SfFailureKind.AUTH; 403 -> SfFailureKind.RISK; else -> SfFailureKind.NETWORK }, "服务未返回可识别的数据，已停止本次操作")
            }
            val status = payload.obj("status")
            val code = status.text("httpCode").toIntOrNull() ?: response.status
            val serverMessage = status.text("msg") + status.text("message")
            // 立即熔断风控，绝不盲目重试防止账号封控
            if (status.text("errorCode") == "1148") throw SfException(SfFailureKind.RISK, "账号需要安全验证（1148）。请到官方菠萝包 App 完成验证，再回来重新登录")
            if (Regex("验证码|安全验证|风险|风控|请求频繁|异常登录|captcha|verification", RegexOption.IGNORE_CASE).containsMatchIn(serverMessage))
                throw SfException(SfFailureKind.RISK, "平台要求安全验证，已停止请求")
            if (status.text("errorCode") == "507") throw SfException(SfFailureKind.CLIENT, "菠萝包拒绝了当前客户端标识（507），接口适配需要更新")
            if (code == 429) throw SfException(SfFailureKind.RATE_LIMIT, "请求被限流", response.retryAfterMillis)
            if (code == 417 || response.status == 417) throw SfException(SfFailureKind.SIGNATURE, SIGNATURE_MESSAGE)
            if (code == 401 || response.status == 401) throw SfException(SfFailureKind.AUTH, if (body == null) "登录已失效，请重新登录" else "登录未成功，请检查账号密码；若需安全验证，请先到官方 App 完成")
            if (code == 403 || response.status == 403) throw SfException(if (path.startsWith("Chaps/") && Regex("购买|订阅|未购|未订|权限").containsMatchIn(serverMessage)) SfFailureKind.UNPURCHASED else SfFailureKind.RISK, "账号无权读取此内容，请在官方 App 确认权限或安全验证")
            if (code !in 200..299 || response.status !in 200..299) throw SfException(if (code in 500..599) SfFailureKind.NETWORK else SfFailureKind.INVALID, "菠萝包接口返回状态 $code，请稍后重试")
            (payload["data"] ?: JsonNull) to response
    }

    private companion object {
        const val SIGNATURE_MESSAGE = "菠萝包暂未接受请求签名，请稍后重新加载（417）"
    }

    suspend fun login(username: String, password: String): Pair<SfAccount, String> {
        require(username.isNotBlank() && password.isNotBlank()) { "请填写账号和密码" }
        val body = buildJsonObject { put("username", username.trim()); put("password", password); put("shuMeiId", "") }.toString()
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
            shelf.text("pocketId") to shelf.obj("expand").array("novels").mapNotNull { (it as? JsonObject)?.let { b -> SfProtocol.shelfBook(b, shelf.text("name")) } }
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
                SfRemoteChapter(c.text("chapId"), c.text("title").ifBlank { c.text("chapName") },
                    c.flag("isVip") || (c.text("needFireMoney").toDoubleOrNull() ?: 0.0) > 0 ||
                        (c.text("originNeedFireMoney").toDoubleOrNull() ?: 0.0) > 0, v.text("title"))
            }
        }.distinctBy { it.id }
        if (chapters.isEmpty() || chapters.any { !it.id.matches(Regex("\\d+")) }) throw SfException(SfFailureKind.INVALID, "没有取得有效章节目录")
        return SfCatalog(id, meta.text("novelName"), meta.text("authorName"),
            meta.obj("expand").text("bigNovelCover").ifBlank { meta.text("novelCover") }.takeIf { it.isNotBlank() }, chapters)
    }

    suspend fun chapter(id: String, cookie: String, paid: Boolean, decode: (String) -> String): String {
        require(id.matches(Regex("\\d+")))
        if (paid && cookie.isBlank()) throw SfException(SfFailureKind.AUTH, "请先登录菠萝包账号，再读取已购章节")
        val data = request("Chaps/$id?expand=content%2Cexpand.content", if (paid) cookie else "").first.jsonObject
        return SfProtocol.content(data, paid, decode)
    }

    suspend fun search(keyword: String, page: Int = 1, size: Int = 20): List<SfSearchResult> {
        val q = java.net.URLEncoder.encode(keyword.trim(), "UTF-8")
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
        val meta = request("novels/$id?expand=bigNovelCover,tags,sysTags,intro,typeName,authorName,author,point,ticket,fav").first.jsonObject
        val exp = meta.obj("expand")
        val title = meta.text("novelName").ifBlank { meta.text("name") }.ifBlank { "未命名作品" }
        val author = meta.text("authorName").ifBlank { meta.text("author") }
        val cover = exp.text("bigNovelCover").ifBlank { meta.text("novelCover") }.takeIf { it.isNotBlank() }
        val intro = exp.text("intro").ifBlank { meta.text("intro") }
        // 纯净官方系统标签：优先从 sysTags 提取（重生/变身/热血/橘味等）
        val tags = (exp["sysTags"] as? JsonArray ?: meta["sysTags"] as? JsonArray ?: exp["tags"] as? JsonArray ?: meta["tags"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.text("tagName")?.takeIf { t -> t.isNotBlank() } ?: (it as? JsonPrimitive)?.contentOrNull?.takeIf { t -> t.isNotBlank() } }
            ?: emptyList()
        val typeName = meta.text("typeName").ifBlank { exp.text("typeName") }
        val isFinish = meta.flag("isFinish") || meta.text("isFinish") == "1"
        val charCount = meta.text("charCount").toLongOrNull() ?: exp.text("charCount").toLongOrNull() ?: 0L
        val markCount = meta.text("markCount").toLongOrNull() ?: exp.text("markCount").toLongOrNull() ?: 0L
        val viewTimes = meta.text("viewTimes").toLongOrNull() ?: exp.text("viewTimes").toLongOrNull() ?: 0L
        val favCount = exp.text("fav").toLongOrNull() ?: meta.text("fav").toLongOrNull() ?: 0L
        val ticketCount = exp.text("ticket").toIntOrNull() ?: exp.text("ticketForMonth").toIntOrNull() ?: meta.text("ticket").toIntOrNull() ?: 0
        val point = meta.text("point").toFloatOrNull() ?: exp.text("point").toFloatOrNull() ?: 0f
        val lastUpdateTime = meta.text("lastUpdateTime").ifBlank { meta.text("signStatus") }
        val latestChapterTitle = meta.text("latestChapTitle").ifBlank { exp.text("latestChapTitle") }
        return SfBookDetail(
            id = id,
            title = title,
            author = author,
            cover = cover,
            intro = intro,
            tags = tags,
            typeName = typeName,
            isFinish = isFinish,
            charCount = charCount,
            markCount = markCount,
            viewTimes = viewTimes,
            favCount = favCount,
            ticketCount = ticketCount,
            point = point,
            lastUpdateTime = lastUpdateTime,
            latestChapterTitle = latestChapterTitle,
        )
    }

    suspend fun paragraphComments(chapterId: String, page: Int = 1, size: Int = 20,
                                  sort: String = "hot"): List<SfParagraphComment> {
        require(chapterId.matches(Regex("\\d+")) && page > 0 && size in 1..50)
        val p0 = page - 1
        val order = if (sort == "new") "addtime" else "hot"
        val data = request("chaps/0/$chapterId/tsukkomis?page=$p0&size=$size&sort=$order").first
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

    suspend fun browseByTag(sysTagId: Int, page: Int = 1, size: Int = 20): List<SfSearchResult> {
        val p0 = (page - 1).coerceAtLeast(0)
        val route = "novels/0/sysTags/$sysTagId/novels?page=$p0&size=$size&expand=bigNovelCover,sysTags,tags,intro"
        val data = request(route).first
        return SfProtocol.parseSearchResults(data)
    }

    suspend fun browseByMultiTags(sysTagIds: List<Int>, page: Int = 1, size: Int = 20): List<SfSearchResult> {
        return browseFiltered(SfBrowseFilter(tagIds = sysTagIds.toSet()), page, size)
    }

    suspend fun fetchCategories(): List<SfCategory> = request("noveltypes").first.jsonArray.mapNotNull {
        val category = it as? JsonObject ?: return@mapNotNull null
        val id = category.text("typeId").toIntOrNull() ?: return@mapNotNull null
        category.text("typeName").takeIf { name -> name.isNotBlank() }?.let { name -> SfCategory(id, name) }
    }

    suspend fun browseFiltered(filter: SfBrowseFilter, page: Int = 1, size: Int = 20, sort: String = "viewtimes"): List<SfSearchResult> {
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
        return SfDiscoveryParser.recommendations(request("novels/specialpushs?pushNames=newpush&page=${page - 1}&size=$size&expand=bigNovelCover,sysTags,intro").first)
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
        } catch (e: CancellationException) { throw e }
        catch (e: SfException) { throw e }
        catch (e: Exception) {
            val err = e.message.orEmpty()
            if (err.contains("已签到") || err.contains("重复") || err.contains("今天已经签到") || err.contains("明日再来")) {
                SfSignResult(true, if (err.isNotBlank()) err else "今日已完成签到，无需重复签到")
            } else {
                SfSignResult(false, if (err.isNotBlank()) err else "签到未成功，请稍后重试")
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
        val data = request("novels/$numId/Cmts?page=$p0&size=$size").first
        val list = if (data is JsonArray) data else (data as? JsonObject)?.array("cmts") ?: emptyList()
        return list.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.text("commentId")
            val name = obj.text("displayName").ifBlank { "菠萝包读者" }
            val avatar = obj.text("avatar").takeIf { it.isNotBlank() }
            val content = obj.text("content").ifBlank { return@mapNotNull null }
            val time = obj.text("createTime").replace("T", " ").substringBefore('.')
            val fav = obj.text("favCount").toIntOrNull() ?: 0
            val reply = obj.text("replyNum").toIntOrNull() ?: 0
            val vip = obj.text("vipLevel").toIntOrNull() ?: 0
            val stick = obj.flag("isStick")
            SfComment(id, name, avatar, content, time, fav, reply, vip, stick)
        }
    }
}

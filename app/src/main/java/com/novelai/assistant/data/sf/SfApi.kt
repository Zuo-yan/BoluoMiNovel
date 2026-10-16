package com.novelai.assistant.data.sf

import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject

enum class SfFailureKind { AUTH, RISK, UNPURCHASED, NETWORK, SIGNATURE, INVALID }
class SfException(val kind: SfFailureKind, message: String) : Exception(message)
data class SfAccount(val id: String, val name: String)
data class SfShelfBook(val id: String, val title: String, val author: String, val cover: String?, val shelf: String)
data class SfRemoteChapter(val id: String, val title: String, val paid: Boolean, val volume: String)
data class SfCatalog(val id: String, val title: String, val author: String, val cover: String?, val chapters: List<SfRemoteChapter>)
data class SfResponse(val status: Int, val body: String, val cookies: List<String> = emptyList())

fun interface SfTransport {
    suspend fun exchange(path: String, headers: Map<String, String>, body: String?): SfResponse
}

class KtorSfTransport @Inject constructor(private val client: HttpClient) : SfTransport {
    private val sfClient = client.config { followRedirects = false; expectSuccess = false }
    override suspend fun exchange(path: String, headers: Map<String, String>, body: String?): SfResponse = withTimeout(25_000) {
        val response = sfClient.request("https://api.sfacg.com/$path") {
            method = if (body == null) HttpMethod.Get else HttpMethod.Post
            headers.forEach { (key, value) -> this.headers.append(key, value) }
            if (body != null) setBody(body)
        }
        SfResponse(response.status.value, response.bodyAsText(), response.headers.getAll("Set-Cookie").orEmpty())
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
        return SfAccount(id, data.text("nickName").ifBlank { data.text("nickname") }.ifBlank { data.text("userName") }.ifBlank { "菠萝包读者" })
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
}

class SfApi(private val transport: SfTransport, private val device: String) {
    private val json = Json { ignoreUnknownKeys = true }
    private var embeddedShelves: Map<String, List<SfShelfBook>> = emptyMap()
    fun clearShelfCache() { embeddedShelves = emptyMap() }

    private suspend fun request(path: String, cookie: String = "", body: String? = null): Pair<JsonElement, SfResponse> {
        repeat(8) { attempt ->
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
            val response = try { transport.exchange(path, headers, body) }
            catch (_: TimeoutCancellationException) { throw SfException(SfFailureKind.NETWORK, "菠萝包请求超时，请重试") }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { throw SfException(SfFailureKind.NETWORK, "无法连接菠萝包，请检查网络后重试") }
            val payload = try { json.parseToJsonElement(response.body).jsonObject }
            catch (_: Exception) { throw SfException(SfFailureKind.INVALID, "菠萝包未返回有效数据，请稍后重试") }
            val status = payload.obj("status")
            val code = status.text("httpCode").toIntOrNull() ?: response.status
            if (status.text("errorCode") == "1148") throw SfException(SfFailureKind.RISK, "账号需要安全验证（1148）。请到官方菠萝包 App 完成验证，再回来重新登录")
            if (status.text("errorCode") == "507") throw SfException(SfFailureKind.INVALID, "菠萝包拒绝了当前客户端标识（507），接口适配需要更新")
            if (code == 417) { delay(200L * (attempt + 1)); return@repeat }
            if (code == 401) throw SfException(SfFailureKind.AUTH, if (body == null) "登录已失效，请重新登录" else "登录未成功，请检查账号密码；若需安全验证，请先到官方 App 完成")
            if (code == 403) throw SfException(SfFailureKind.UNPURCHASED, "账号无权读取此内容，请在官方 App 确认已购买")
            if (code !in 200..299 || response.status !in 200..299) throw SfException(SfFailureKind.NETWORK, "菠萝包接口返回状态 $code，请稍后重试")
            return (payload["data"] ?: JsonNull) to response
        }
        throw SfException(SfFailureKind.SIGNATURE, "菠萝包签名校验失败，当前接口适配可能需要更新")
    }

    suspend fun login(username: String, password: String): Pair<SfAccount, String> {
        require(username.isNotBlank() && password.isNotBlank()) { "请填写账号和密码" }
        val body = buildJsonObject { put("username", username.trim()); put("password", password); put("shuMeiId", "") }.toString()
        val response = request("sessions", body = body).second
        val cookie = SfProtocol.loginCookie(response.cookies)
        return inspect(cookie) to cookie
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
}

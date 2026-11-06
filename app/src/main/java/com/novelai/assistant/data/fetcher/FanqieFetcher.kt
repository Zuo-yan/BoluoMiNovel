package com.novelai.assistant.data.fetcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 番茄小说抓取器：
 * - 目录：web 页面 fanqienovel.com/page/{bookId}（需桌面 UA，TLS 指纹可能拦截）
 * - 正文：第三方中转 API（/api/raw_full?item_id=），地址可配置
 */
@Singleton
class FanqieFetcher @Inject constructor() {

    var relayBaseUrl: String = DEFAULT_RELAY

    suspend fun fetchBook(bookIdOrUrl: String): Result<FetchedBook> = withContext(Dispatchers.IO) {
        runCatching {
            val id = extractBookId(bookIdOrUrl)
                ?: error("无法识别书籍 ID，请输入数字 ID 或 fanqienovel.com/page/xxx 链接")
            val url = "https://fanqienovel.com/page/$id"
            val doc = Jsoup.connect(url)
                .userAgent(DESKTOP_UA)
                .timeout(20_000)
                .get()

            var title = doc.selectFirst("h1")?.text()?.trim()
                ?: doc.title().substringBefore("-").trim().ifBlank { "未命名" }
            var author = doc.selectFirst(".author-name-text, .author a, [class*=author]")?.text()?.trim() ?: ""

            val chapters = mutableListOf<FetchedChapter>()
            for (a in doc.select("div.chapter a, .chapter-item a, a[href*=/reader/]")) {
                val href = a.absUrl("href").ifBlank { "https://fanqienovel.com${a.attr("href")}" }
                val itemId = Regex("/reader/(\\d+)").find(href)?.groupValues?.get(1) ?: continue
                val t = a.text().trim()
                if (t.isNotBlank()) chapters.add(FetchedChapter(itemId, t, ""))
            }

            if (chapters.isEmpty()) {
                // 尝试从页面嵌入的 window.__INITIAL_STATE__ 结构中解析完整章节列表
                val html = doc.html()
                val jsonMatch = Regex("window\\.__INITIAL_STATE__\\s*=\\s*(\\{.*?\\});?\\s*</script>", RegexOption.DOT_MATCHES_ALL).find(html)
                    ?: Regex("window\\.__INITIAL_STATE__\\s*=\\s*(\\{.*?\\})\\s*\\(", RegexOption.DOT_MATCHES_ALL).find(html)
                if (jsonMatch != null) {
                    val rawJson = jsonMatch.groupValues[1]
                    val root = runCatching { org.json.JSONObject(rawJson) }.getOrNull()
                    val pageObj = root?.optJSONObject("page")
                    val parsedTitle = pageObj?.optString("bookName")?.trim().orEmpty()
                    val parsedAuthor = pageObj?.optString("author")?.trim().orEmpty()
                    if (title == "未命名" && parsedTitle.isNotBlank()) title = parsedTitle
                    if (author.isBlank() && parsedAuthor.isNotBlank()) author = parsedAuthor

                    val chapterList = pageObj?.optJSONArray("chapterList")
                    if (chapterList != null) {
                        for (i in 0 until chapterList.length()) {
                            val c = chapterList.optJSONObject(i) ?: continue
                            val itemId = c.optString("itemId").ifBlank { c.optString("item_id") }
                            val t = c.optString("title").trim()
                            if (itemId.isNotBlank() && t.isNotBlank()) {
                                chapters.add(FetchedChapter(itemId, t, ""))
                            }
                        }
                    }
                    if (chapters.isEmpty()) {
                        val volumeList = pageObj?.optJSONArray("chapterListWithVolume")
                        if (volumeList != null) {
                            for (v in 0 until volumeList.length()) {
                                val volArr = volumeList.optJSONArray(v) ?: continue
                                for (i in 0 until volArr.length()) {
                                    val c = volArr.optJSONObject(i) ?: continue
                                    val itemId = c.optString("itemId").ifBlank { c.optString("item_id") }
                                    val t = c.optString("title").trim()
                                    if (itemId.isNotBlank() && t.isNotBlank()) {
                                        chapters.add(FetchedChapter(itemId, t, ""))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (chapters.isEmpty()) error("目录解析失败：页面可能需要滑动验证或番茄端结构已变化，建议稍后重试")
            FetchedBook(id, title, author, chapters, CoverUrlExtractor.extract(doc))
        }
    }

    /** 正文走中转 API，返回 {code:200, data:{content:"<p>..."}} */
    suspend fun fetchChapterContent(itemId: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val relay = relayBaseUrl.trim().trimEnd('/')
            if (relay.isBlank()) error("请先在「发现」界面配置番茄小说中转服务地址")
            val conn = URL("$relay/api/raw_full?item_id=$itemId").openConnection() as HttpURLConnection
            conn.connectTimeout = 12_000
            conn.readTimeout = 18_000
            conn.setRequestProperty("User-Agent", DESKTOP_UA)
            conn.setRequestProperty("Accept", "application/json, text/plain, */*")
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = runCatching { conn.errorStream?.use { it.readBytes().decodeToString() } }.getOrNull()
                error("中转服务返回 HTTP $code${if (!err.isNullOrBlank()) "：$err" else "，请检查中转地址有效性"}")
            }
            val body = conn.inputStream.use { it.readBytes().decodeToString() }

            val json = org.json.JSONObject(body)
            val resCode = json.optInt("code", -1)
            if (resCode != 200 && resCode != 0) {
                val msg = json.optString("message").ifBlank { json.optString("msg") }
                error("中转服务返回 code=$resCode${if (msg.isNotBlank()) " ($msg)" else ""}")
            }
            val content = json.optJSONObject("data")?.optString("content").orEmpty()
            if (content.isBlank()) error("章节内容为空（可能为 VIP 章节或内容拉取中）")

            Jsoup.parse(content).select("p").map { it.text().trim() }
                .filter { it.isNotBlank() }
                .ifEmpty { listOf(Jsoup.parse(content).text()) }
        }
    }

    companion object {
        const val DEFAULT_RELAY = "http://101.35.133.34:5000"

        fun extractBookId(input: String): String? {
            val trimmed = input.trim()
            trimmed.toLongOrNull()?.let { return trimmed }
            Regex("/page/(\\d+)").find(trimmed)?.let { return it.groupValues[1] }
            Regex("(\\d{6,})").find(trimmed)?.let { return it.groupValues[1] }
            return null
        }
    }
}

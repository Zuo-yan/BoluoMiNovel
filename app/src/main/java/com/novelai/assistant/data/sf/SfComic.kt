package com.novelai.assistant.data.sf

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import javax.inject.Inject

data class SfComic(
    val slug: String,
    val title: String,
    val author: String,
    val cover: String?,
    val intro: String,
    val latestChapter: String,
    val category: String,
    val score: String,
    val status: String
)

data class SfComicChapter(val id: String, val title: String)
data class SfComicInfo(val comic: SfComic, val chapters: List<SfComicChapter>)
data class SfComicCatalogPage(val comics: List<SfComic>, val lastPage: Int?)

class KtorSfComicClient @Inject constructor() {
    private val client = HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
        followRedirects = false
        expectSuccess = false
        engine { config {
            followRedirects(false)
            followSslRedirects(false)
            retryOnConnectionFailure(false)
            connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
        } }
    }

    suspend fun get(url: String, referer: String? = null): String = getFollowingOfficialRedirects(url, referer, 0)

    private suspend fun getFollowingOfficialRedirects(url: String, referer: String?, redirects: Int): String {
        if (!SfComicLibrary.isAllowedUrl(url)) {
            throw SfException(SfFailureKind.CLIENT, "漫画请求地址不属于已允许的官方域名")
        }
        val response = client.request(url) {
            method = HttpMethod.Get
            header(HttpHeaders.UserAgent, "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
            if (referer != null) header(HttpHeaders.Referrer, referer)
        }
        if (response.status.value in 300..399) {
            val target = response.headers[HttpHeaders.Location]?.let { java.net.URI(url).resolve(it) }
            if (redirects >= 4 || target == null || target.scheme !in setOf("http", "https")) {
                val location = response.headers[HttpHeaders.Location].orEmpty()
                throw SfException(SfFailureKind.CLIENT, "漫画网站返回未允许的跳转（${response.status.value} ${location.take(120)}）")
            }
            val safeUrl = java.net.URI(
                "https", target.rawAuthority, target.rawPath, target.rawQuery, target.rawFragment
            ).toString()
            if (!SfComicLibrary.isAllowedUrl(safeUrl)) {
                val location = response.headers[HttpHeaders.Location].orEmpty()
                throw SfException(SfFailureKind.CLIENT, "漫画网站返回未允许的跳转（${response.status.value} ${location.take(120)}）")
            }
            return getFollowingOfficialRedirects(safeUrl, referer, redirects + 1)
        }
        if (response.status.value == 429) throw SfException(SfFailureKind.RATE_LIMIT, "漫画网站请求被限流，请稍后重试")
        if (response.status.value !in 200..299) throw SfException(SfFailureKind.NETWORK, "漫画网站返回 HTTP ${response.status.value}")
        return response.bodyAsText()
    }

    suspend fun catalog(categoryId: Int? = null, page: Int = 1): SfComicCatalogPage {
        require(page > 0)
        val query = buildString {
            append("?PageIndex=$page")
            if (categoryId != null) append("&tid=$categoryId")
        }
        val html = get("https://manhua.sfacg.com/catalog/$query")
        return SfComicCatalogPage(SfComicLibrary.parseCatalog(html), SfComicLibrary.parseLastPage(html))
    }

    suspend fun search(query: String): List<SfComic> {
        val q = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        return SfComicLibrary.parseCatalog(get("https://s.sfacg.com/?Key=$q&S=0&SS=0"))
    }

    suspend fun detail(slug: String): SfComicInfo {
        require(slug.matches(Regex("[A-Za-z0-9]{1,20}")))
        return SfComicLibrary.parseDetail(slug, get("https://manhua.sfacg.com/mh/$slug/"))
    }

    suspend fun pages(slug: String, chapterId: String): List<String> {
        require(slug.matches(Regex("[A-Za-z0-9]{1,20}")))
        val oldChapter = chapterId.matches(Regex("\\d{1,12}"))
        val mobileChapter = chapterId.matches(Regex("[A-Za-z0-9]{1,12}/\\d{3,12}_\\d{1,12}"))
        require(oldChapter || mobileChapter)
        val baseUrl = if (oldChapter) "https://manhua.sfacg.com/mh/$slug/$chapterId/"
        else "https://mm.sfacg.com/b/$slug/$chapterId/"
        val html = get(baseUrl)
        val params = SfComicLibrary.chapterParams(html)
        val apiHost = if (oldChapter) "manhua.sfacg.com" else "mm.sfacg.com"
        val backend = SfComicLibrary.parsePicResponse(get(
            "https://$apiHost/ajax/Common.ashx?op=getPics&cid=${params[0]}&chapId=${params[4]}" +
                "&serial=${params[3]}&path=${params[2]}",
            referer = baseUrl
        ))
        if (backend.isEmpty()) throw SfException(SfFailureKind.UNPURCHASED, "本章没有取得漫画图片，可能需要登录或订阅权限")
        return backend
    }
}

object SfComicLibrary {
    private val allowedComicHosts = setOf(
        "manhua.sfacg.com", "mm.sfacg.com", "s.sfacg.com", "coldpic.sfacg.com", "rs.sfacg.com"
    )

    fun isAllowedUrl(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host in allowedComicHosts &&
            uri.rawUserInfo == null &&
            (uri.port == -1 || uri.port == 443)
    }.getOrDefault(false)

    fun parseCatalog(html: String): List<SfComic> {
        val doc = Jsoup.parse(html, "https://manhua.sfacg.com/")
        val catalogBlocks = doc.select("ul.Comic_Pic_List").takeIf { it.isNotEmpty() }
            ?: doc.select("ul").filter { it.selectFirst("a.orange_link2[href*=/mh/]") != null }
        return catalogBlocks.mapNotNull { block ->
            val links = block.select("a[href*=/mh/]")
            val link = links.firstOrNull { it.hasClass("orange_link2") }
                ?: links.firstOrNull { it.absUrl("href").isNotBlank() } ?: return@mapNotNull null
            val slug = Regex("/mh/([A-Za-z0-9]+)").find(link.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
            val title = block.selectFirst("a.orange_link2")?.text()?.trim()
                ?: block.selectFirst("img[alt]")?.attr("alt")?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null
            val text = block.text()
            val category = block.select("a[href*=tid=]").lastOrNull()?.text().orEmpty()
            SfComic(slug, title, author = text.substringAfter("作者：", "").substringBefore(" 综合信息").trim(),
                cover = block.selectFirst("img[src]")?.absUrl("src")?.takeIf { it.isNotBlank() },
                intro = "", latestChapter = "", category = category, score = "", status = "")
        }.distinctBy { it.slug }
    }

    fun parseLastPage(html: String): Int? = Regex(
        """href=["'][^"']*[?&]PageIndex=(\d+)(?:&[^"']*)?["'][^>]*>\s*\d+\s*</a>""",
        RegexOption.IGNORE_CASE
    ).findAll(html).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull()

    fun parseDetail(slug: String, html: String): SfComicInfo {
        val baseUrl = "https://mm.sfacg.com/b/$slug/"
        val doc = Jsoup.parse(html, baseUrl)
        val title = doc.selectFirst("span.book_newtitle")?.text()?.trim()
            ?: doc.title().substringBefore('_').substringBefore(',').trim()
        if (title.isBlank()) throw SfException(SfFailureKind.INVALID, "漫画页面缺少作品标题")

        val oldChapterPattern = Regex("/mh/$slug/(\\d{1,12})/?")
        val mobileChapterPattern = Regex("/b/$slug/([A-Za-z0-9]{1,12})/(\\d{3,12}_\\d{1,12})/?")
        val chapters = doc.select("a[href]").mapNotNull { a ->
            val absolute = a.absUrl("href").ifBlank { a.attr("href") }
            val id = oldChapterPattern.find(absolute)?.groupValues?.get(1)
                ?: mobileChapterPattern.find(absolute)?.let { "${it.groupValues[1]}/${it.groupValues[2]}" }
                ?: return@mapNotNull null
            val name = a.text().trim()
            if (name.isBlank() || name == "点击浏览") null else SfComicChapter(id, name)
        }.distinctBy { it.id }.reversed()
        if (chapters.isEmpty()) throw SfException(SfFailureKind.INVALID, "漫画目录为空或格式已变化")

        val intro = doc.selectFirst("ul.book_profile li.book_bk_qs1")?.text()?.trim()
            ?: doc.selectFirst("div.comic_info, div.comic_info2, p.comic_info")?.text().orEmpty()
        val cover = doc.selectFirst("ul.book_info img, div.comic_cover img")?.absUrl("src")
            ?.takeIf { it.isNotBlank() }
        val author = doc.selectFirst("ul.book_Author > li:first-child")?.text()?.trim().orEmpty()
        val categories = doc.select("div.book_info2 span")
        val category = categories.firstOrNull()?.text()?.trim().orEmpty()
        val status = categories.getOrNull(1)?.text()?.trim().orEmpty()
        return SfComicInfo(
            SfComic(slug, title, author, cover, intro, chapters.last().title, category, "", status),
            chapters
        )
    }

    /** Returns comic id, folder, serial, type folder and numeric chapter id used by the web reader. */
    fun chapterParams(html: String): List<String> {
        fun value(name: String) = Regex("var\\s+$name\\s*=\\s*\"?([^\";]+)\"?\\s*;").find(html)?.groupValues?.get(1)?.trim()
        val c = value("c") ?: value("comicId") ?: throw SfException(SfFailureKind.INVALID, "漫画章节缺少作品编号")
        val folder = value("comicFolder") ?: Regex("/b/([A-Za-z0-9]{1,20})/")
            .find(html)?.groupValues?.get(1) ?: throw SfException(SfFailureKind.INVALID, "漫画章节缺少作品目录")
        val serial = value("nv") ?: throw SfException(SfFailureKind.INVALID, "漫画章节缺少图片目录")
        val fn = value("fn") ?: "ZP"
        val chapterId = value("chapId") ?: value("chapterId")
            ?: throw SfException(SfFailureKind.INVALID, "漫画章节缺少章节编号")
        return listOf(c, folder, serial, fn, chapterId)
    }

    fun parsePicResponse(body: String): List<String> {
        val trim = body.trim()
        val root = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(trim).jsonObject }
            .getOrElse { throw SfException(SfFailureKind.INVALID, "漫画图片列表返回格式已变化") }
        if ((root["status"]?.jsonPrimitive?.content ?: "200") != "200") throw SfException(SfFailureKind.UNPURCHASED, "漫画图片不可用，请确认章节权限")
        return (root["data"] as? JsonArray).orEmpty().mapNotNull {
            (it as? JsonPrimitive)?.content
        }.filter { it.startsWith("https://coldpic.sfacg.com/") }
    }
}

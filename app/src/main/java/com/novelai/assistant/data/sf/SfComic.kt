package com.novelai.assistant.data.sf

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class SfComic(
    val slug: String,
    val title: String,
    val author: String,
    val cover: String?,
    val intro: String,
    val latestChapter: String,
    val category: String,
    val score: String,
    val status: String,
    val folderName: String = ""
)

data class SfComicChapter(
    val id: String,
    val title: String,
    val isVip: Boolean = false,
    val pathName: String = ""
)
data class SfComicInfo(val comic: SfComic, val chapters: List<SfComicChapter>)
data class SfComicCatalogPage(val comics: List<SfComic>, val lastPage: Int?)

@Singleton
class KtorSfComicClient @Inject constructor(
    private val sfApi: SfApi,
    @SfNonRedirectClient private val client: HttpClient
) {
    constructor() : this(
        SfApi(KtorSfTransport(), SfProtocol.COMPATIBLE_DEVICE),
        SfHttpClientFactory.nonRedirectingClient
    )

    private val folderToIdCache = ConcurrentHashMap<String, String>()

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

    suspend fun categories(): List<SfCategory> = sfApi.comicTypes()

    suspend fun catalog(categoryId: Int? = null, page: Int = 1): SfComicCatalogPage {
        require(page > 0)
        val comics = sfApi.comicCatalog(categoryId, page, size = 20)
        cacheFolderIds(comics)
        val lastPage = if (comics.size < 20) page else null
        return SfComicCatalogPage(comics, lastPage)
    }

    suspend fun search(query: String): List<SfComic> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        val comics = sfApi.comicSearch(trimmed, page = 1, size = 20)
        cacheFolderIds(comics)
        return comics
    }

    suspend fun detail(slug: String): SfComicInfo {
        require(slug.matches(Regex("[A-Za-z0-9]{1,20}")))
        val comicId = resolveComicIdOrNull(slug)
        if (comicId != null) {
            return sfApi.comicDetail(comicId).also { cacheFolderIds(listOf(it.comic)) }
        }
        return SfComicLibrary.parseDetail(slug, get("https://manhua.sfacg.com/mh/$slug/"))
    }

    suspend fun pages(slug: String, chapterId: String): List<String> {
        require(slug.matches(Regex("[A-Za-z0-9]{1,20}")))
        val oldChapter = chapterId.matches(Regex("\\d{1,12}"))
        val mobileChapter = chapterId.matches(Regex("[A-Za-z0-9]{1,12}/\\d{3,12}_\\d{1,12}"))
        require(oldChapter || mobileChapter)

        if (oldChapter) {
            val comicId = resolveComicIdOrNull(slug)
            if (comicId != null) {
                val backend = SfComicLibrary.parsePicResponse(
                    get(
                        "https://manhua.sfacg.com/ajax/Common.ashx?op=getPics&cid=$comicId&chapId=$chapterId",
                        referer = "https://manhua.sfacg.com/"
                    )
                )
                if (backend.isEmpty()) {
                    throw SfException(SfFailureKind.UNPURCHASED, "本章暂无可用漫画图片（可能为 VIP 付费章节或需登录账号）")
                }
                return backend
            }
        }

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

    private fun cacheFolderIds(comics: List<SfComic>) {
        comics.forEach { comic ->
            if (comic.slug.matches(Regex("\\d{1,12}")) && comic.folderName.isNotBlank()) {
                folderToIdCache[comic.folderName.uppercase()] = comic.slug
            }
        }
    }

    private suspend fun resolveComicIdOrNull(slugOrId: String): String? {
        if (slugOrId.matches(Regex("\\d{1,12}"))) return slugOrId
        val key = slugOrId.uppercase()
        folderToIdCache[key]?.let { return it }
        // Check first catalog pages or search by folder name if invoked with legacy alphabetic slug.
        val firstPage = runCatching { sfApi.comicCatalog(null, 1, 50) }.getOrDefault(emptyList())
        cacheFolderIds(firstPage)
        folderToIdCache[key]?.let { return it }
        val searched = runCatching { sfApi.comicSearch(slugOrId, 1, 20) }.getOrDefault(emptyList())
        cacheFolderIds(searched)
        return folderToIdCache[key]
    }
}

object SfComicLibrary {
    private val allowedComicHosts = setOf(
        "manhua.sfacg.com", "mm.sfacg.com", "s.sfacg.com",
        "coldpic.sfacg.com", "pic.sfacg.com", "rs.sfacg.com", "rss.sfacg.com"
    )
    private val imageCdnHosts = setOf(
        "coldpic.sfacg.com", "pic.sfacg.com", "rs.sfacg.com", "rss.sfacg.com"
    )
    private val defaultTypeNames = mapOf(
        1 to "热血", 2 to "校园", 3 to "悬疑", 5 to "冒险",
        7 to "恋爱", 8 to "搞笑", 9 to "科幻", 10 to "奇幻", 11 to "恐怖",
        12 to "都市", 13 to "爱情", 14 to "古风", 15 to "日常", 17 to "玄幻", 18 to "后宫"
    )
    private val typeNames = ConcurrentHashMap(defaultTypeNames)

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
        val pageTitle = doc.title().trim()
        if (pageTitle == "出错啦" || pageTitle.startsWith("糟糕") || pageTitle.contains("页面找不到")) {
            throw SfException(SfFailureKind.INVALID, "官方网页版漫画页面暂不可用，请从漫画列表重新打开")
        }
        val title = doc.selectFirst("span.book_newtitle")?.text()?.trim()
            ?: pageTitle.substringBefore('_').substringBefore(',').trim()
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
        }.filter { url ->
            runCatching {
                val uri = java.net.URI(url)
                isAllowedUrl(url) && uri.host in imageCdnHosts
            }.getOrDefault(false)
        }
    }

    fun parseApiComicTypes(data: JsonElement): List<SfCategory> =
        (data as? JsonArray).orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.text("typeId").toIntOrNull() ?: return@mapNotNull null
            val name = obj.text("typeName").trim().ifBlank { return@mapNotNull null }
            typeNames[id] = name
            SfCategory(id, name)
        }

    fun parseApiComic(obj: JsonObject): SfComic? {
        val comicId = obj.text("comicId").trim()
        if (!comicId.matches(Regex("\\d+"))) return null
        val title = obj.text("comicName").trim().ifBlank { return null }
        val exp = obj.obj("expand")
        val cover = obj.text("comicCover").trim().takeIf { it.startsWith("http") }
            ?.replace("http://", "https://")
        val author = exp.text("authorName").ifBlank { obj.text("authorName") }.trim()
        val typeId = obj.text("typeId").toIntOrNull()
        val category = exp.text("typeName").trim()
            .ifBlank { typeId?.let { typeNames[it] }.orEmpty() }
        val intro = exp.text("intro").trim()
        val latest = obj.text("latestChapterTitle").trim()
        val score = obj.text("point").trim().takeIf { it.isNotBlank() && it != "0" && it != "0.0" }.orEmpty()
        val status = if (obj.flag("isFinished")) "已完结" else "连载中"
        val folder = obj.text("folderName").trim()
        return SfComic(
            slug = comicId,
            title = title,
            author = author,
            cover = cover,
            intro = intro,
            latestChapter = latest,
            category = category,
            score = score,
            status = status,
            folderName = folder
        )
    }

    fun parseApiComicList(data: JsonElement): List<SfComic> =
        (data as? JsonArray).orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.let(::parseApiComic)
        }.distinctBy { it.slug }

    fun parseApiComicDetail(meta: JsonObject, chapsData: JsonElement): SfComicInfo {
        val comic = parseApiComic(meta)
            ?: throw SfException(SfFailureKind.INVALID, "漫画详情返回格式已变化")
        // Official /comics/{id}/chaps returns newest chapter first; reverse for chronological reading order.
        val chapters = (chapsData as? JsonArray).orEmpty().mapNotNull { element ->
            val c = element as? JsonObject ?: return@mapNotNull null
            val chapId = c.text("chapterId").trim()
            if (!chapId.matches(Regex("\\d+"))) return@mapNotNull null
            val title = c.text("chapterTitle").trim().ifBlank { "第 $chapId 话" }
            val exp = c.obj("expand")
            val isVip = c.flag("isVip") ||
                (exp.text("needFireMoney").toDoubleOrNull() ?: 0.0) > 0.0 ||
                (exp.text("originNeedFireMoney").toDoubleOrNull() ?: 0.0) > 0.0
            SfComicChapter(
                id = chapId,
                title = title,
                isVip = isVip,
                pathName = c.text("pathName").trim()
            )
        }.distinctBy { it.id }.reversed()
        if (chapters.isEmpty()) throw SfException(SfFailureKind.INVALID, "漫画暂无章节目录")
        return SfComicInfo(
            comic = comic.copy(latestChapter = comic.latestChapter.ifBlank { chapters.last().title }),
            chapters = chapters
        )
    }
}

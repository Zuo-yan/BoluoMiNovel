package com.novelai.assistant.data.sf

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import java.security.MessageDigest

internal fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.obj(key: String): JsonObject = get(key) as? JsonObject ?: JsonObject(emptyMap())
internal fun JsonObject.array(key: String): List<JsonElement> = (get(key) as? JsonArray).orEmpty()
internal fun JsonObject.flag(key: String): Boolean = text(key) == "true" || text(key) == "1"

object SfProtocol {
    // The legacy SF App protocol currently rejects unregistered random IDs (401 / 507).
    // This is the compatibility identifier used by the referenced client, not a hardware identifier.
    const val COMPATIBLE_DEVICE = "910D166A-736E-3231-8B21-8D12DFD75F16"

    private data class BookCoreMeta(
        val title: String,
        val author: String,
        val cover: String?,
        val intro: String,
        val tags: List<String>,
        val point: Float,
        val markCount: Long,
        val viewTimes: Long
    )

    private data class CommentAuthor(
        val displayName: String,
        val avatar: String?,
        val roleName: String
    )

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
        return SfAccount(
            id,
            data.text("nickName").ifBlank { data.text("nickname") }.ifBlank { data.text("userName") }.ifBlank { "菠萝包读者" },
            vip,
            avatar
        )
    }

    fun shelfBook(data: JsonObject, shelf: String): SfShelfBook? {
        val book = (data["novel"] as? JsonObject) ?: data
        val id = book.text("novelId")
        if (!id.matches(Regex("\\d+"))) return null
        return SfShelfBook(
            id,
            book.text("novelName").ifBlank { "未命名作品" },
            book.text("authorName"),
            book.text("novelCover").ifBlank { book.obj("expand").text("bigNovelCover") }.takeIf { it.isNotBlank() },
            shelf
        )
    }

    fun content(data: JsonObject, paid: Boolean = true, decode: (String) -> String): String {
        if ((paid && listOf("isPurchased", "isBuy").any {
                data.text(it) == "false" || data.obj("expand").text(it) == "false"
            }) || data.flag("isPreview") || data.obj("expand").flag("isPreview")
        ) {
            throw SfException(SfFailureKind.UNPURCHASED, "本章未购买或只返回试读内容，请在官方 App 确认订阅")
        }
        val raw = data.text("content") + data.obj("expand").text("content")
        if (raw.isBlank()) throw SfException(SfFailureKind.UNPURCHASED, "未取得完整正文，请在官方 App 确认本章已购买")
        val text = normalizeSfParagraphRows(decode(raw))
        if (text.isBlank() || Regex("^(请先登录|登录后阅读|购买本章|订阅本章)").containsMatchIn(text)) {
            throw SfException(SfFailureKind.UNPURCHASED, "本章没有可阅读正文，请确认订阅权限")
        }
        return text
    }

    /**
     * The App returns one server paragraph row per newline. Markdown-style
     * paragraph breaks were doubling those rows and shifting `tsukkomi.row`.
     */
    fun normalizeSfParagraphRows(html: String): String =
        Jsoup.parse(
            html.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n")
        ).also { document ->
            document.select("img[src]").forEach { image ->
                image.replaceWith(TextNode("[img]${image.attr("src")}[/img]"))
            }
        }.wholeText().replace("\r\n", "\n").replace('\r', '\n').trim()

    /**
     * Chapter responses expose an `expand.tsukkomi` summary with row/count.
     * This is the only field that ties comments to paragraph positions; the
     * paged comment endpoint itself does not provide a reliable row mapping.
     */
    fun paragraphCommentSummaries(data: JsonObject): List<SfParagraphCommentSummary> {
        val expand = data.obj("expand")
        val items = when (val value = expand["tsukkomi"]) {
            is JsonArray -> value
            is JsonObject -> value.array("items")
            else -> emptyList()
        }
        return items.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val row = item.text("row").toIntOrNull() ?: item.text("paraIndex").toIntOrNull()
            val count = item.text("count").toIntOrNull() ?: item.text("tsukkomiCount").toIntOrNull()
            if (row == null || row < 0 || count == null || count <= 0) null
            else SfParagraphCommentSummary(row, count)
        }.groupBy { it.row }.map { (row, grouped) ->
            // If the server supplies multiple entries for one row, use the
            // largest count instead of summing and inventing duplicate totals.
            SfParagraphCommentSummary(row, grouped.maxOf { it.count })
        }.sortedBy { it.row }
    }

    fun chapterContentAndCommentSummaries(
        data: JsonObject,
        paid: Boolean,
        decode: (String) -> String
    ): Pair<String, List<SfParagraphCommentSummary>> =
        content(data, paid, decode) to paragraphCommentSummaries(data)

    private fun extractBookTags(obj: JsonObject, exp: JsonObject): List<String> =
        (exp["sysTags"] as? JsonArray ?: obj["sysTags"] as? JsonArray
            ?: exp["tags"] as? JsonArray ?: obj["tags"] as? JsonArray)
            ?.mapNotNull {
                (it as? JsonObject)?.text("tagName")?.takeIf { t -> t.isNotBlank() }
                    ?: (it as? JsonPrimitive)?.contentOrNull?.takeIf { t -> t.isNotBlank() }
            }
            ?: emptyList()

    private fun extractBookCore(obj: JsonObject, exp: JsonObject = obj.obj("expand")): BookCoreMeta =
        BookCoreMeta(
            title = obj.text("novelName").ifBlank { obj.text("name") }.ifBlank { "未命名作品" },
            author = obj.text("authorName").ifBlank { obj.text("author") },
            cover = exp.text("bigNovelCover").ifBlank { obj.text("novelCover") }.takeIf { it.isNotBlank() },
            intro = exp.text("intro").ifBlank { obj.text("intro") },
            tags = extractBookTags(obj, exp),
            point = obj.text("point").toFloatOrNull() ?: exp.text("point").toFloatOrNull() ?: 0f,
            markCount = obj.text("markCount").toLongOrNull() ?: exp.text("markCount").toLongOrNull() ?: 0L,
            viewTimes = obj.text("viewTimes").toLongOrNull() ?: exp.text("viewTimes").toLongOrNull() ?: 0L
        )

    fun parseSearchResult(obj: JsonObject): SfSearchResult? {
        val id = obj.text("novelId").ifBlank { obj.text("id") }
        if (!id.matches(Regex("\\d+"))) return null
        val core = extractBookCore(obj)
        return SfSearchResult(
            id = id,
            title = core.title,
            author = core.author,
            cover = core.cover,
            intro = core.intro,
            tags = core.tags,
            point = core.point,
            markCount = core.markCount,
            viewTimes = core.viewTimes
        )
    }

    fun parseSearchResults(data: JsonElement): List<SfSearchResult> {
        val list = if (data is JsonArray) data else (data as? JsonObject)?.array("novels") ?: emptyList()
        return list.mapNotNull { (it as? JsonObject)?.let(::parseSearchResult) }
    }

    fun parseBookDetail(id: String, meta: JsonObject): SfBookDetail {
        val exp = meta.obj("expand")
        val core = extractBookCore(meta, exp)
        val typeName = meta.text("typeName").ifBlank { exp.text("typeName") }
        val isFinish = meta.flag("isFinish") || meta.text("isFinish") == "1"
        val charCount = meta.text("charCount").toLongOrNull() ?: exp.text("charCount").toLongOrNull() ?: 0L
        val favCount = exp.text("fav").toLongOrNull() ?: meta.text("fav").toLongOrNull() ?: 0L
        val ticketCount = exp.text("ticket").toIntOrNull()
            ?: exp.text("ticketForMonth").toIntOrNull()
            ?: meta.text("ticket").toIntOrNull()
            ?: 0
        val lastUpdateTime = meta.text("lastUpdateTime").ifBlank { meta.text("signStatus") }
        val latestChapterTitle = meta.text("latestChapTitle").ifBlank { exp.text("latestChapTitle") }
        return SfBookDetail(
            id = id,
            title = core.title,
            author = core.author,
            cover = core.cover,
            intro = core.intro,
            tags = core.tags,
            typeName = typeName,
            isFinish = isFinish,
            charCount = charCount,
            markCount = core.markCount,
            viewTimes = core.viewTimes,
            favCount = favCount,
            ticketCount = ticketCount,
            point = core.point,
            lastUpdateTime = lastUpdateTime,
            latestChapterTitle = latestChapterTitle
        )
    }

    private fun extractCommentAuthor(obj: JsonObject, includeReplyUser: Boolean = false): CommentAuthor {
        val user = (if (includeReplyUser) obj["replyUser"] as? JsonObject else null)
            ?: obj["postUser"] as? JsonObject
            ?: obj["user"] as? JsonObject
            ?: obj.obj("expand")["user"] as? JsonObject
        val displayName = user?.text("nickName").orEmpty()
            .ifBlank { obj.text("displayName") }
            .ifBlank { "菠萝包读者" }
        val avatar = user?.text("avatar").orEmpty()
            .ifBlank { user?.obj("expand")?.text("avatar").orEmpty() }
            .ifBlank { obj.text("avatar") }
            .takeIf { it.isNotBlank() }
        val roleName = obj.text("roleName").ifBlank { obj.text("title") }
        return CommentAuthor(displayName, avatar, roleName)
    }

    private fun formatSfTimestamp(vararg candidates: String): String =
        candidates.firstOrNull { it.isNotBlank() }.orEmpty()
            .replace("T", " ")
            .substringBefore('.')

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
            if (!id.matches(Regex("\\d+")) || content.isBlank()) {
                throw SfException(SfFailureKind.INVALID, "章节间贴条目缺少编号或正文")
            }
            val author = extractCommentAuthor(obj, includeReplyUser = false)
            val row = obj.text("row").toIntOrNull()
                ?: obj.text("paraIndex").toIntOrNull()
                ?: obj.text("paragraphIndex").toIntOrNull()
                ?: obj.text("chapParaIndex").toIntOrNull()
                ?: 0
            SfParagraphComment(
                id = id,
                row = row,
                displayName = author.displayName,
                avatar = author.avatar,
                content = content,
                createTime = formatSfTimestamp(obj.text("postDate"), obj.text("createTime")),
                favCount = obj.text("favNum").toIntOrNull()
                    ?: obj.text("favCount").toIntOrNull()
                    ?: obj.text("likeCount").toIntOrNull()
                    ?: 0,
                replyNum = obj.text("replyNum").toIntOrNull() ?: 0,
                roleName = author.roleName,
                replies = parseParagraphReplies(obj)
            )
        }
    }

    fun parseParagraphReplies(comment: JsonObject): List<SfParagraphReply> {
        val candidates = sequenceOf(
            comment.obj("expand")["replys"],
            comment["replys"],
            comment["replies"],
            comment["replyInfos"],
            comment["replyInfo"]
        )
        val items = candidates
            .filterIsInstance<JsonArray>()
            .firstOrNull()
            ?: return emptyList()
        return items.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.text("replyId").ifBlank { obj.text("tsukkomiReplyId") }
                .ifBlank { obj.text("id") }
            val content = obj.text("content").ifBlank { obj.text("replyContent") }
                .let(::plainComment)
            if (id.isBlank() || content.isBlank()) return@mapNotNull null
            val author = extractCommentAuthor(obj, includeReplyUser = true)
            SfParagraphReply(
                id = id,
                displayName = author.displayName,
                avatar = author.avatar,
                content = content,
                createTime = formatSfTimestamp(
                    obj.text("replyDate"),
                    obj.text("postDate"),
                    obj.text("createTime")
                ),
                roleName = author.roleName
            )
        }.distinctBy { it.id }
    }

    fun parseNovelComments(data: JsonElement): List<SfComment> {
        val list = when (data) {
            is JsonArray -> data
            is JsonObject -> data.array("cmts")
                .ifEmpty { data.array("comments") }
                .ifEmpty { data.array("items") }
            else -> emptyList()
        }
        return list.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.text("commentId")
            val name = obj.text("displayName").ifBlank { "菠萝包读者" }
            val avatar = obj.text("avatar").takeIf { it.isNotBlank() }
            val content = obj.text("content").ifBlank { return@mapNotNull null }
            val time = formatSfTimestamp(obj.text("createTime"))
            val fav = obj.text("favCount").toIntOrNull() ?: 0
            val reply = obj.text("replyNum").toIntOrNull() ?: 0
            val vip = obj.text("vipLevel").toIntOrNull() ?: 0
            val stick = obj.flag("isStick")
            SfComment(
                id.ifBlank { "${obj.text("accountId")}:${obj.text("createTime")}" },
                name, avatar, content, time, fav, reply, vip, stick
            )
        }
    }

    private fun plainComment(raw: String): String =
        Jsoup.parse(
            raw.replace(Regex("\\[em:sfgirl:\\d+]", RegexOption.IGNORE_CASE), "[表情]")
                .replace(Regex("<br\\s*/?>|</p>", RegexOption.IGNORE_CASE), "\n")
        ).wholeText().trim()
}

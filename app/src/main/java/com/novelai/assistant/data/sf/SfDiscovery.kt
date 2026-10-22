package com.novelai.assistant.data.sf

import kotlinx.serialization.json.*

data class SfBookList(
    val id: String, val title: String, val summary: String, val author: String,
    val totalBooks: Int, val books: List<SfSearchResult>
)

internal object SfDiscoveryParser {
    fun recommendations(data: JsonElement): List<SfSearchResult> {
        val entries = (data as? JsonObject)?.let { it["newPush"] ?: it["newpush"] } as? JsonArray
            ?: throw SfException(SfFailureKind.INVALID, "新书推荐返回格式已变化")
        return entries.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            SfProtocol.parseSearchResult((obj["novel"] as? JsonObject) ?: obj)
        }.distinctBy { it.id }
    }

    fun bookLists(data: JsonElement): List<SfBookList> {
        val entries = (data as? JsonObject)?.get("items") as? JsonArray
            ?: throw SfException(SfFailureKind.INVALID, "相关书单返回格式已变化")
        return entries.mapNotNull { entry ->
            val item = entry as? JsonObject ?: return@mapNotNull null
            val id = item.text("bookListID").ifBlank { item.text("bookListId") }
            if (!id.matches(Regex("\\d+"))) return@mapNotNull null
            val books = item.array("novels").mapNotNull { (it as? JsonObject)?.let(SfProtocol::parseSearchResult) }.distinctBy { it.id }
            SfBookList(id, item.text("title").ifBlank { "未命名书单" },
                org.jsoup.Jsoup.parse(item.text("summary")).text(),
                item.obj("user").text("nickName"),
                (item.text("novelNum").toIntOrNull() ?: books.size).coerceAtLeast(books.size), books)
        }.distinctBy { it.id }
    }
}

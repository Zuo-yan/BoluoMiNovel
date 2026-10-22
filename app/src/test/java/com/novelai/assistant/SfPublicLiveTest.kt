package com.novelai.assistant

import com.novelai.assistant.data.sf.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in public GET smoke test. Never reads accounts, passwords or stored sessions. */
class SfPublicLiveTest {
    @Test fun publicReadingAndCombinedFiltersMatchReturnedMetadata() = runBlocking {
        assumeTrue(System.getenv("NOVELAI_SF_PUBLIC_VERIFY") == "1")
        val transport = KtorSfTransport()
        val payloads = mutableMapOf<String, JsonElement>()
        val api = SfApi(SfTransport { path, headers, body ->
            assertNull(headers["Cookie"])
            assertNull(body)
            val response = transport.exchange(path, headers, null)
            runCatching { Json.parseToJsonElement(response.body).jsonObject["data"] }.getOrNull()?.let { payloads[path] = it }
            response
        }, SfProtocol.COMPATIBLE_DEVICE)
        val categories = api.fetchCategories().associate { it.name to it.id }
        val tags = api.fetchOfficialTags().associate { it.name to it.id }
        val wanted = setOf(tags.getValue("异世界"), tags.getValue("恋爱"))
        val filter = SfBrowseFilter(categories.getValue("魔幻"), wanted)

        suspend fun checkFilter(value: SfBrowseFilter, page: Int, sort: String = "viewtimes") {
            delay(1_000)
            val books = api.browseFiltered(value, page, size = 5, sort = sort)
            assertTrue("Expected real matching books", books.isNotEmpty())
            val path = payloads.keys.last { it.startsWith("novels/${value.categoryId}/sysTags/novels?") && it.contains("page=${page - 1}&") && it.contains("sort=$sort") }
            val raw = payloads.getValue(path).jsonArray
            assertEquals(books.size, raw.size)
            raw.forEach { item ->
                val book = item.jsonObject
                if (value.categoryId != 0) assertEquals(value.categoryId, book.getValue("typeId").jsonPrimitive.int)
                val ids = book.getValue("expand").jsonObject.getValue("sysTags").jsonArray
                    .map { it.jsonObject.getValue("sysTagId").jsonPrimitive.int }.toSet()
                assertTrue("Server must apply every chosen tag", ids.containsAll(value.tagIds))
            }
            if (sort == "updatetime") {
                val times = raw.map { it.jsonObject.getValue("lastUpdateTime").jsonPrimitive.content }
                assertEquals(times.sortedDescending(), times)
            }
            println("PASS public category=${value.categoryId}, tags=${value.tagIds.sorted()}, page=$page, sort=$sort, books=${books.size}")
        }
        checkFilter(filter, 1)
        checkFilter(filter, 2)
        checkFilter(filter.copy(categoryId = categories.getValue("科幻")), 1)
        checkFilter(filter.copy(categoryId = 0), 1)
        checkFilter(filter.copy(tagIds = emptySet()), 1)
        checkFilter(filter, 1, "updatetime")

        val catalog = api.catalog("388081")
        val chapter = catalog.chapters.first { !it.paid }
        val text = api.chapter(chapter.id, "", false) { it }
        assertTrue(text.isNotBlank())
        assertFalse(api.guard.state.value.blocked)
        println("PASS anonymous public chapter=${chapter.id}, textLength=${text.length}")
        val recommendations = api.newRecommendations(size = 3)
        assertTrue(recommendations.isNotEmpty())
        val lists = api.relatedBookLists("540307", size = 3)
        assertTrue(lists.isNotEmpty())
        assertTrue(lists.any { it.books.isNotEmpty() })
        assertTrue(lists.all { it.totalBooks >= it.books.size })
        println("PASS anonymous recommendations=${recommendations.size}, relatedLists=${lists.size}, previewBooks=${lists.sumOf { it.books.size }}")
    }
}

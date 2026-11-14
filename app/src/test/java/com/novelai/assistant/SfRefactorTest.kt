package com.novelai.assistant

import com.novelai.assistant.data.sf.SfHttpPolicy
import com.novelai.assistant.data.sf.SfProtocol
import com.novelai.assistant.data.sf.ShelfUpdate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SfRefactorTest {
    @Test
    fun retryAfterParsesSecondsAndRfc1123Dates() {
        assertNull(SfHttpPolicy.retryAfter(null))
        assertEquals(12_000L, SfHttpPolicy.retryAfter("12"))
        assertNull(SfHttpPolicy.retryAfter("-5"))
        val now = 1_700_000_000_000L
        assertEquals(0L, SfHttpPolicy.retryAfter("Wed, 21 Oct 2015 07:28:00 GMT", now))
    }

    @Test
    fun sharedBookCoreParserPopulatesSearchAndDetailConsistently() {
        val raw = Json.parseToJsonElement(
            """{
              "novelId": 777,
              "novelName": "星辰轨迹",
              "authorName": "测试作者",
              "point": 9.2,
              "markCount": 25000,
              "viewTimes": 180000,
              "charCount": 320000,
              "isFinish": true,
              "typeName": "科幻",
              "lastUpdateTime": "2026-10-01T12:00:00",
              "expand": {
                "bigNovelCover": "https://rs.sfacg.com/777.jpg",
                "intro": "一段旅程",
                "sysTags": [{"tagName": "科幻"}, {"tagName": "冒险"}],
                "fav": 1234,
                "ticket": 56,
                "latestChapTitle": "第100章 终章"
              }
            }"""
        ).jsonObject

        val searchItem = SfProtocol.parseSearchResult(raw)!!
        val detail = SfProtocol.parseBookDetail("777", raw)
        assertEquals(searchItem.title, detail.title)
        assertEquals(searchItem.author, detail.author)
        assertEquals(searchItem.cover, detail.cover)
        assertEquals(searchItem.intro, detail.intro)
        assertEquals(searchItem.tags, detail.tags)
        assertEquals(searchItem.point, detail.point)
        assertEquals(searchItem.markCount, detail.markCount)
        assertEquals(searchItem.viewTimes, detail.viewTimes)
        assertTrue(detail.isFinish)
        assertEquals("科幻", detail.typeName)
        assertEquals(320000L, detail.charCount)
        assertEquals(1234L, detail.favCount)
        assertEquals(56, detail.ticketCount)
        assertEquals("第100章 终章", detail.latestChapterTitle)
    }

    @Test
    fun shelfUpdateSerializesCleanly() {
        val update = ShelfUpdate(
            latest = "第50章",
            time = "2026-10-01 12:00:00",
            unread = 3,
            added = listOf("sf:1:50"),
            checkedAt = 123456789L
        )
        val encoded = Json.encodeToString(ShelfUpdate.serializer(), update)
        val decoded = Json.decodeFromString(ShelfUpdate.serializer(), encoded)
        assertEquals(update, decoded)
        assertTrue(decoded.label().contains("新增 1 章"))
    }
}

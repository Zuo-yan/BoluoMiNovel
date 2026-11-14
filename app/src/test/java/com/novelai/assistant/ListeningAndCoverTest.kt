package com.novelai.assistant

import com.novelai.assistant.data.fetcher.CoverUrlExtractor
import com.novelai.assistant.data.importer.BookImportManager
import com.novelai.assistant.data.db.BookSourceType
import com.novelai.assistant.data.text.ChapterTextParser
import com.novelai.assistant.data.tts.ListeningNavBus
import com.novelai.assistant.data.tts.TtsPlayer
import com.novelai.assistant.data.tts.TtsTimerMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningAndCoverTest {

    @Test
    fun testTtsTimerModeValues() {
        assertEquals(0, TtsTimerMode.OFF.minutes)
        assertEquals(-1, TtsTimerMode.END_OF_CHAPTER.minutes)
        assertEquals(15, TtsTimerMode.MIN_15.minutes)
        assertEquals(30, TtsTimerMode.MIN_30.minutes)
        assertEquals(60, TtsTimerMode.MIN_60.minutes)
    }

    @Test
    fun testCoverUrlExtractorWithOgImage() {
        val html = """
            <html>
                <head>
                    <meta property="og:image" content="https://example.com/cover_og.jpg" />
                </head>
                <body>
                    <img src="https://example.com/other.png" />
                </body>
            </html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val extracted = CoverUrlExtractor.extract(doc)
        assertEquals("https://example.com/cover_og.jpg", extracted)
    }

    @Test
    fun testCoverUrlExtractorCandidateFallback() {
        val html = """
            <html>
                <body>
                    <img src="https://example.com/icon.png" />
                    <img src="https://example.com/book_cover_thumb.jpg" />
                </body>
            </html>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val extracted = CoverUrlExtractor.extract(doc)
        assertEquals("https://example.com/book_cover_thumb.jpg", extracted)
    }

    @Test
    fun testSplitParagraphs() {
        val raw = """
            第一段内容。
            
            第二段内容。
               
            第三段内容。
        """.trimIndent()
        val list = ChapterTextParser.splitParagraphs(raw).filter { it.isNotEmpty() }
        assertEquals(3, list.size)
        assertEquals("第一段内容。", list[0])
        assertEquals("第二段内容。", list[1])
        assertEquals("第三段内容。", list[2])
    }

    @Test
    fun testSplitLongParagraph() {
        val shortText = "这是一个短段落，不需要拆分。"
        val singleResult = TtsPlayer.splitLongParagraph(shortText)
        assertEquals(1, singleResult.size)
        assertEquals(shortText, singleResult[0])

        // 构造超长段落（超过 1500 字）
        val sb = StringBuilder()
        repeat(120) { idx ->
            sb.append("这是第${idx}个测试句子，带有句号结尾。")
        }
        val longText = sb.toString()
        assertTrue(longText.length > 1500)

        val splitChunks = TtsPlayer.splitLongParagraph(longText)
        assertTrue(splitChunks.size > 1)
        for (chunk in splitChunks) {
            assertTrue(chunk.isNotBlank())
        }
        // 所有分块拼接后内容应等价
        assertEquals(longText, splitChunks.joinToString(""))
    }

    @Test
    fun testSourceTypeLabel() {
        assertEquals("TXT", BookImportManager.sourceTypeLabel(BookSourceType.LOCAL_TXT))
        assertEquals("EPUB", BookImportManager.sourceTypeLabel(BookSourceType.LOCAL_EPUB))
        assertEquals("番茄", BookImportManager.sourceTypeLabel(BookSourceType.FANQIE))
        assertEquals("菠萝包", BookImportManager.sourceTypeLabel(BookSourceType.BOLUOBAO))
    }

    @Test
    fun testListeningNavBusEvent() = runBlocking {
        val bus = ListeningNavBus()
        bus.requestOpenListening()
        val event = bus.openListening.first()
        assertNotNull(event)
    }
}

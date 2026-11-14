package com.novelai.assistant

import com.novelai.assistant.data.text.ChapterTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterTextParserTest {

    @Test
    fun splitParagraphsNormalizesLineEndingsAndPreservesBlankRows() {
        val rows = ChapterTextParser.splitParagraphs("  第一段 \r\n\r\n  第二段\r \n第三段")
        assertEquals(listOf("第一段", "", "第二段", "", "第三段"), rows)
    }

    @Test
    fun parseIllustrationUrlUpgradesHttpAndRejectsUnsafeUrls() {
        assertEquals(
            "https://rss.sfacg.com/a.jpg",
            ChapterTextParser.parseIllustrationUrl("  [img=700,875]http://rss.sfacg.com/a.jpg[/img]  ")
        )
        assertTrue(ChapterTextParser.isIllustration("[img]https://rss.sfacg.com/a.jpg[/img]"))
        assertFalse(ChapterTextParser.isIllustration("正文 [img]https://rss.sfacg.com/a.jpg[/img]"))
        assertNull(ChapterTextParser.parseIllustrationUrl("[img]file:///secret[/img]"))
        assertNull(ChapterTextParser.parseIllustrationUrl("[img]https://user:pass@example.com/a[/img]"))
    }

    @Test
    fun replaceIllustrationTagsReplacesValidTagsAndLeavesUnsafeTagsUntouched() {
        val raw = "前文\n[img=100,200]http://rss.sfacg.com/1.jpg[/img]\n[img]https://user:pass@example.com/x.jpg[/img]"
        val replaced = ChapterTextParser.replaceIllustrationTags(raw) { url -> "[图:$url]" }
        assertEquals(
            "前文\n[图:https://rss.sfacg.com/1.jpg]\n[img]https://user:pass@example.com/x.jpg[/img]",
            replaced
        )
    }
}

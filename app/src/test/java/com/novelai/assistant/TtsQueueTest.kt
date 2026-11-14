package com.novelai.assistant

import com.novelai.assistant.data.text.ChapterTextParser
import com.novelai.assistant.data.tts.TtsPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsQueueTest {
    @Test fun skipsBlankRowsAndMarksLastSpokenChunk() {
        val paragraphs = ChapterTextParser.splitParagraphs("第一段\r\n\r\n第二段\n \n")
        val queue = TtsPlayer.buildQueue(paragraphs, 0) { p, c -> "$p-$c" }
        assertEquals(listOf(0, 2), queue.map { it.paraIndex })
        assertEquals(listOf("第一段", "第二段"), queue.map { it.text })
        assertEquals(listOf(false, true), queue.map { it.isLastChunkOfChapter })
    }

    @Test fun startIndexAndEmptyChapterAreHandled() {
        val paragraphs = listOf("第一段", "", "第二段", " ")
        val queue = TtsPlayer.buildQueue(paragraphs, 1) { p, c -> "$p-$c" }
        assertEquals(listOf(2), queue.map { it.paraIndex })
        assertTrue(queue.single().isLastChunkOfChapter)
        assertTrue(TtsPlayer.buildQueue(paragraphs, 3) { p, c -> "$p-$c" }.isEmpty())
    }

    @Test fun illustrationParagraphsAreSpokenAsLabel() {
        val paragraphs = ChapterTextParser.splitParagraphs("前文\n[img=100,200]http://rss.sfacg.com/a.jpg[/img]\n后文")
        val queue = TtsPlayer.buildQueue(paragraphs, 0) { p, c -> "$p-$c" }
        assertEquals(listOf("前文", "插图", "后文"), queue.map { it.text })
    }
}

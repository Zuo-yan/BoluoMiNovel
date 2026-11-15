package com.novelai.assistant

import com.novelai.assistant.data.network.ProviderKind
import com.novelai.assistant.data.repository.AiConfigRepository
import com.novelai.assistant.data.repository.ApiProviderConfig
import com.novelai.assistant.data.repository.ModelRoute
import com.novelai.assistant.data.repository.TaskScene
import com.novelai.assistant.data.text.ChapterTextParser
import com.novelai.assistant.data.tts.TtsPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun splitLongParagraphRespectsAiMaxChunk() {
        val longText = "风".repeat(TtsPlayer.AI_MAX_CHUNK * 2 + 17)
        val chunks = TtsPlayer.splitLongParagraph(longText, maxChunk = TtsPlayer.AI_MAX_CHUNK)
        assertEquals(3, chunks.size)
        assertTrue(chunks.all { it.length <= TtsPlayer.AI_MAX_CHUNK })
        assertEquals(longText, chunks.joinToString(""))
    }

    @Test fun ttsRouteDoesNotFallBackToDefaultChatProvider() {
        val chatProvider = ApiProviderConfig(
            id = "chat_p",
            providerName = "Chat",
            kind = ProviderKind.OPENAI_COMPATIBLE,
            baseUrl = "https://api.openai.com/v1",
            apiKey = "sk-chat",
            selectedModel = "gpt-4o"
        )
        val resolvedUnconfigured = AiConfigRepository.resolveRouteForScene(
            scene = TaskScene.TTS,
            route = ModelRoute(),
            providers = listOf(chatProvider)
        )
        assertNull(resolvedUnconfigured)

        val resolvedConfigured = AiConfigRepository.resolveRouteForScene(
            scene = TaskScene.TTS,
            route = ModelRoute(providerId = "chat_p", model = "tts-1"),
            providers = listOf(chatProvider)
        )
        assertEquals("chat_p", resolvedConfigured?.first?.id)
        assertEquals("tts-1", resolvedConfigured?.second)
    }
}

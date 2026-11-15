package com.novelai.assistant

import com.novelai.assistant.data.generation.BackgroundGenerationState
import com.novelai.assistant.data.generation.GenerationNavBus
import com.novelai.assistant.data.generation.GenerationTaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundGenerationTest {

    private fun sampleState(
        status: GenerationTaskStatus,
        text: String = "",
        error: String? = null
    ) = BackgroundGenerationState(
        taskId = "task-1",
        bookId = "book-1",
        bookTitle = "星陨手记",
        parentChapterId = "ch-1",
        parentChapterTitle = "第一章",
        modelLabel = "DeepSeek · deepseek-chat",
        status = status,
        text = text,
        error = error
    )

    @Test
    fun backgroundStateVisibilityAndEditReadinessRules() {
        val runningEmpty = sampleState(GenerationTaskStatus.RUNNING, text = "")
        assertTrue(runningEmpty.isRunning)
        assertTrue(runningEmpty.hasDisplayableState)
        assertFalse(runningEmpty.isFinishedForEdit)

        val completed = sampleState(GenerationTaskStatus.COMPLETED, text = "夜色沉入湖心。")
        assertFalse(completed.isRunning)
        assertTrue(completed.hasDisplayableState)
        assertTrue(completed.isFinishedForEdit)

        val cancelledWithDraft = sampleState(GenerationTaskStatus.CANCELLED, text = "半段草稿")
        assertTrue(cancelledWithDraft.hasDisplayableState)
        assertTrue(cancelledWithDraft.isFinishedForEdit)

        val cancelledEmpty = sampleState(GenerationTaskStatus.CANCELLED, text = "")
        assertFalse(cancelledEmpty.hasDisplayableState)
        assertFalse(cancelledEmpty.isFinishedForEdit)

        val failedWithError = sampleState(GenerationTaskStatus.FAILED, text = "", error = "网络超时")
        assertTrue(failedWithError.hasDisplayableState)
        assertFalse(failedWithError.isFinishedForEdit)

        val failedWithDraft = sampleState(GenerationTaskStatus.FAILED, text = "已有片段", error = "流式中断")
        assertTrue(failedWithDraft.hasDisplayableState)
        assertTrue(failedWithDraft.isFinishedForEdit)
    }

    @Test
    fun generationNavBusDeliversRoutePayload() = runBlocking {
        val bus = GenerationNavBus()
        bus.requestOpenGeneration(bookId = "b-99", chapterId = "c-12", mode = "BRANCH")
        val target = bus.openGeneration.first()
        assertEquals("b-99", target.bookId)
        assertEquals("c-12", target.chapterId)
        assertEquals("BRANCH", target.mode)
    }
}

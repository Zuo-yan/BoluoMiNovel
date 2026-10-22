package com.novelai.assistant

import com.novelai.assistant.data.sf.SfComment
import com.novelai.assistant.ui.discover.SF_COMMENTS_PAGE_SIZE
import com.novelai.assistant.ui.discover.mergeSfComments
import org.junit.Assert.assertEquals
import org.junit.Test

class SfCommentPaginationTest {
    private fun comment(id: String) =
        SfComment(id, "读者$id", null, "内容$id", "2026-10-22 12:00:00")

    @Test fun commentsPageSizeIsExplicit() {
        assertEquals(20, SF_COMMENTS_PAGE_SIZE)
    }

    @Test fun historyMergingPreservesOrderAndRemovesRepeatedRows() {
        val first = listOf(comment("1"), comment("2"))
        val next = listOf(comment("2"), comment("3"))
        assertEquals(listOf("1", "2", "3"), mergeSfComments(first, next).map { it.id })
    }
}

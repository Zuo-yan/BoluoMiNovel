package com.novelai.assistant

import com.novelai.assistant.ui.reader.Paginator
import org.junit.Assert.assertEquals
import org.junit.Test

class PaginatorParagraphIndexTest {
    @Test fun blankRowsRemainAddressableForServerCommentRows() {
        val rows = Paginator.splitParagraphs("第一段\r\n\r\n第三段\n\n第五段")
        assertEquals(listOf("第一段", "", "第三段", "", "第五段"), rows)
    }
}

package com.novelai.assistant

import com.novelai.assistant.data.db.BookmarkEntity
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.db.ChapterOriginType
import com.novelai.assistant.ui.continuation.AnchorChapterMode
import com.novelai.assistant.ui.reader.ReaderBookmarkHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderBookmarkTest {

    private fun chapter(id: String, index: Int) = ChapterEntity(
        id = id,
        bookId = "book-1",
        chapterIndex = index,
        title = "第${index + 1}章",
        content = "正文-$id",
        originType = ChapterOriginType.ORIGINAL
    )

    @Test
    fun extractExcerptHandlesOffsetsFallbacksAndTruncation() {
        val text = "   \n\n第一段其实也很短。\n第二段是一段非常长非常长的测试文本用来验证截取逻辑是否准确会在超过指定长度后自动添加省略号继续下去。"
        // 索引 0/1 为空行，应自动回退到首个非空段；索引 2 为第一段，索引 3 为第二段
        val blankFallback = ReaderBookmarkHelper.extractExcerpt(text, paragraphIndex = 0, charOffset = 0, maxLength = 20)
        assertEquals("第一段其实也很短。", blankFallback)

        val fromPara = ReaderBookmarkHelper.extractExcerpt(text, paragraphIndex = 2, charOffset = 0, maxLength = 20)
        assertEquals("第一段其实也很短。", fromPara)

        val longPara = ReaderBookmarkHelper.extractExcerpt(text, paragraphIndex = 3, charOffset = 3, maxLength = 12)
        assertTrue(longPara.endsWith("…"))
        assertTrue(longPara.startsWith("是一段非常长"))

        // 越界偏移回退到整段，越界段落回退到末尾有效段
        val fallback = ReaderBookmarkHelper.extractExcerpt(text, paragraphIndex = 99, charOffset = 999, maxLength = 20)
        assertTrue(fallback.startsWith("第二段"))
    }

    @Test
    fun resolveTargetIndexPrefersChapterIdEvenWhenIndicesShift() {
        val chapters = listOf(
            chapter("ch-1", 0),
            chapter("ch-ai", 1),
            chapter("ch-2", 2)
        )
        val bookmark = BookmarkEntity(
            id = "bm-1",
            bookId = "book-1",
            chapterId = "ch-2",
            chapterIndex = 1,
            chapterTitle = "第2章",
            paragraphIndex = 4
        )
        assertEquals(2, ReaderBookmarkHelper.resolveBookmarkTargetIndex(chapters, bookmark))

        val orphaned = bookmark.copy(chapterId = "deleted-id", chapterIndex = 10)
        assertEquals(2, ReaderBookmarkHelper.resolveBookmarkTargetIndex(chapters, orphaned))
    }

    @Test
    fun findMatchingBookmarkMatchesChapterAndParagraph() {
        val list = listOf(
            BookmarkEntity("b1", "book-1", "ch-1", 0, "第一章", paragraphIndex = 3),
            BookmarkEntity("b2", "book-1", "ch-2", 1, "第二章", paragraphIndex = 0)
        )
        assertNotNull(ReaderBookmarkHelper.findMatchingBookmark(list, "ch-1", 3))
        assertNull(ReaderBookmarkHelper.findMatchingBookmark(list, "ch-1", 4))
        assertNull(ReaderBookmarkHelper.findMatchingBookmark(list, "ch-3", 0))
    }

    @Test
    fun formatBookmarkTimeProducesReadableRelativeStrings() {
        val now = 1_700_000_000_000L
        assertEquals("刚刚", ReaderBookmarkHelper.formatBookmarkTime(now - 20_000L, now))
        assertEquals("5 分钟前", ReaderBookmarkHelper.formatBookmarkTime(now - 300_000L, now))
        assertEquals("2 小时前", ReaderBookmarkHelper.formatBookmarkTime(now - 7_200_000L, now))
    }

    @Test
    fun anchorChapterModeCoversAllThreeScopes() {
        assertEquals(
            setOf(AnchorChapterMode.LATEST, AnchorChapterMode.CURRENT, AnchorChapterMode.CUSTOM),
            AnchorChapterMode.entries.toSet()
        )
    }
}

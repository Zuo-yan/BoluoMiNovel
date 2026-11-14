package com.novelai.assistant

import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.db.ChapterOriginType
import com.novelai.assistant.data.sf.SfParagraphComment
import com.novelai.assistant.data.sf.SfParagraphReply
import com.novelai.assistant.ui.reader.ReaderTocTree
import com.novelai.assistant.ui.reader.ReaderTsukkomiController
import com.novelai.assistant.ui.reader.TocFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderRefactorTest {

    private fun ch(
        id: String,
        index: Int,
        origin: ChapterOriginType = ChapterOriginType.ORIGINAL,
        parentId: String? = null,
        remoteId: String? = null
    ) = ChapterEntity(
        id = id,
        bookId = "b1",
        chapterIndex = index,
        title = "Chapter $id",
        content = "Content $id",
        originType = origin,
        parentChapterId = parentId,
        remoteId = remoteId
    )

    @Test
    fun tocTreePlacesBranchesUnderParentWithDepthAndFiltersCorrectly() {
        val c1 = ch("c1", 0, ChapterOriginType.ORIGINAL)
        val c2 = ch("c2", 1, ChapterOriginType.ORIGINAL)
        val b1 = ch("b1", 2, ChapterOriginType.AI_BRANCH, parentId = "c1")
        val b2 = ch("b2", 3, ChapterOriginType.AI_CONTINUATION, parentId = "b1")
        val chapters = listOf(c1, c2, b1, b2)

        val tree = ReaderTocTree.filterAndBuild(chapters, TocFilter.TREE)
        assertEquals(listOf("c1", "b1", "b2", "c2"), tree.map { it.chapter.id })
        assertEquals(listOf(0, 1, 2, 0), tree.map { it.depth })

        assertEquals(
            listOf("c1", "c2"),
            ReaderTocTree.filterAndBuild(chapters, TocFilter.ORIGINAL).map { it.chapter.id }
        )
        assertEquals(
            listOf("b1", "b2"),
            ReaderTocTree.filterAndBuild(chapters, TocFilter.AI).map { it.chapter.id }
        )
    }

    @Test
    fun tocTreeHandlesCyclesWithoutInfiniteRecursion() {
        val a = ch("a", 0, ChapterOriginType.AI_BRANCH, parentId = "b")
        val b = ch("b", 1, ChapterOriginType.AI_BRANCH, parentId = "a")
        val tree = ReaderTocTree.buildTree(listOf(a, b))
        assertEquals(listOf("a", "b"), tree.map { it.chapter.id })
    }

    @Test
    fun tsukkomiControllerLoadsPagesAndRepliesCleanly() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val page1 = (1..20).map { i ->
            SfParagraphComment(
                id = "cmt-$i",
                row = 2,
                displayName = "Reader $i",
                avatar = null,
                content = "Comment $i",
                createTime = "2026-11-14 12:00",
                replyNum = if (i == 1) 1 else 0
            )
        }
        val page2 = listOf(
            SfParagraphComment(
                id = "cmt-20",
                row = 2,
                displayName = "Reader 20",
                avatar = null,
                content = "Duplicate",
                createTime = "2026-11-14 12:00"
            ),
            SfParagraphComment(
                id = "cmt-21",
                row = 2,
                displayName = "Reader 21",
                avatar = null,
                content = "Page 2",
                createTime = "2026-11-14 12:01"
            )
        )
        val controller = ReaderTsukkomiController(
            scope = scope,
            fetchComments = { _, _, _, page -> if (page == 1) page1 else page2 },
            fetchReplies = { id ->
                listOf(SfParagraphReply("r-$id", "Replier", null, "Nice!", "2026-11-14 12:05"))
            }
        )

        val sfChapter = ch("sf:540307:6367390", 0, remoteId = "6367390")
        controller.open(sfChapter, sort = "hot", quote = "Hello", row = 2)
        assertTrue(controller.open.value)
        assertFalse(controller.loading.value)
        assertEquals(20, controller.comments.value.size)

        controller.loadMore(sfChapter)
        assertEquals(21, controller.comments.value.size)
        assertEquals("cmt-21", controller.comments.value.last().id)

        controller.toggleReplies("cmt-1")
        assertTrue("cmt-1" in controller.expandedReplies.value)
        assertTrue(controller.replyThreads.value["cmt-1"]?.loaded == true)
        assertEquals(1, controller.replyThreads.value["cmt-1"]?.replies?.size)

        controller.close()
        assertFalse(controller.open.value)
        assertNull(controller.row.value)
        assertTrue(controller.replyThreads.value.isEmpty())
        scope.cancel()
    }
}

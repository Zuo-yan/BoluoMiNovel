package com.novelai.assistant.ui.reader

import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.db.ChapterOriginType
import com.novelai.assistant.data.repository.BookRepository

enum class TocFilter(val label: String) {
    ALL("全部"), ORIGINAL("仅原作"), AI("AI 续写")
}

data class TocItem(
    val chapter: ChapterEntity,
    val depth: Int
)

object ReaderTocTree {
    fun filterAndBuild(list: List<ChapterEntity>, filter: TocFilter): List<TocItem> =
        when (filter) {
            TocFilter.ALL -> buildTree(list)
            TocFilter.ORIGINAL -> list.filter { it.originType == ChapterOriginType.ORIGINAL }.map { TocItem(it, 0) }
            TocFilter.AI -> buildTree(list)
                .filter { it.chapter.originType != ChapterOriginType.ORIGINAL }
                .map { it.copy(depth = (it.depth - 1).coerceAtLeast(0)) }
        }

    fun buildTree(list: List<ChapterEntity>): List<TocItem> {
        val flattened = BookRepository.flattenChapterTree(list)
        val byId = flattened.associateBy { it.id }
        fun depth(chapter: ChapterEntity): Int {
            var d = 0
            var cur = chapter
            val visited = mutableSetOf(cur.id)
            while (cur.parentChapterId != null) {
                val parent = byId[cur.parentChapterId] ?: break
                if (!visited.add(parent.id) || d > 20) break
                d++
                cur = parent
            }
            return d
        }
        return flattened.map { TocItem(it, depth(it)) }
    }
}

package com.novelai.assistant.ui.reader

import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.db.ChapterOriginType

enum class TocFilter(val label: String) {
    ALL("全部"), ORIGINAL("仅原作"), AI("AI 衍生"), TREE("分支树")
}

data class TocItem(
    val chapter: ChapterEntity,
    val depth: Int
)

object ReaderTocTree {
    fun filterAndBuild(list: List<ChapterEntity>, filter: TocFilter): List<TocItem> =
        when (filter) {
            TocFilter.ALL -> list.map { TocItem(it, 0) }
            TocFilter.ORIGINAL -> list.filter { it.originType == ChapterOriginType.ORIGINAL }.map { TocItem(it, 0) }
            TocFilter.AI -> list.filter { it.originType != ChapterOriginType.ORIGINAL }.map { TocItem(it, 0) }
            TocFilter.TREE -> buildTree(list)
        }

    fun buildTree(list: List<ChapterEntity>): List<TocItem> {
        val byId = list.associateBy { it.id }
        val childrenByParent = list.groupBy { it.parentChapterId }
        fun depth(chapter: ChapterEntity): Int {
            var d = 0
            var cur = chapter
            val visited = mutableSetOf(cur.id)
            while (cur.parentChapterId != null) {
                d++
                val parent = byId[cur.parentChapterId] ?: break
                if (!visited.add(parent.id) || d > 20) break
                cur = parent
            }
            return d
        }
        // 树形排序：原作章节依次展开，AI 章节紧跟其父章节之后
        val result = mutableListOf<TocItem>()
        val processed = mutableSetOf<String>()
        fun appendChildren(parent: ChapterEntity) {
            val children = childrenByParent[parent.id].orEmpty()
            for (child in children) {
                if (!processed.add(child.id)) continue
                result.add(TocItem(child, depth(child)))
                appendChildren(child)
            }
        }
        for (chapter in list) {
            if (!processed.add(chapter.id)) continue
            result.add(TocItem(chapter, depth(chapter)))
            appendChildren(chapter)
        }
        return result
    }
}

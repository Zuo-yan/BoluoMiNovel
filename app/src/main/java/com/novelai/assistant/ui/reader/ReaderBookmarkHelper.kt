package com.novelai.assistant.ui.reader

import com.novelai.assistant.data.db.BookmarkEntity
import com.novelai.assistant.data.db.ChapterEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ReaderDrawerTab(val label: String) {
    TOC("目录"),
    BOOKMARKS("书签")
}

object ReaderBookmarkHelper {

    fun extractExcerpt(
        content: String,
        paragraphIndex: Int,
        charOffset: Int = 0,
        maxLength: Int = 72
    ): String {
        val paragraphs = Paginator.splitParagraphs(content)
        if (paragraphs.isEmpty()) return ""
        val clampedIdx = paragraphIndex.coerceIn(0, paragraphs.lastIndex)
        val raw = paragraphs.getOrNull(clampedIdx)?.takeIf { it.isNotBlank() }
            ?: paragraphs.drop(clampedIdx).firstOrNull { it.isNotBlank() }
            ?: paragraphs.firstOrNull { it.isNotBlank() }
            ?: ""
        if (raw.isBlank()) return ""
        val cleanOffset = if (charOffset in raw.indices) charOffset else 0
        val slice = raw.substring(cleanOffset).trim()
            .ifBlank { raw.trim() }
        return if (slice.length > maxLength) slice.take(maxLength).trimEnd() + "…" else slice
    }

    fun findMatchingBookmark(
        bookmarks: List<BookmarkEntity>,
        chapterId: String,
        paragraphIndex: Int
    ): BookmarkEntity? = bookmarks.firstOrNull {
        it.chapterId == chapterId && it.paragraphIndex == paragraphIndex
    }

    fun resolveBookmarkTargetIndex(
        chapters: List<ChapterEntity>,
        bookmark: BookmarkEntity
    ): Int {
        if (chapters.isEmpty()) return -1
        val byId = chapters.indexOfFirst { it.id == bookmark.chapterId }
        if (byId >= 0) return byId
        return bookmark.chapterIndex.coerceIn(0, chapters.lastIndex)
    }

    fun formatBookmarkTime(
        timestampMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        val diff = (nowMs - timestampMs).coerceAtLeast(0L)
        return when {
            diff < 60_000L -> "刚刚"
            diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
            diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
            else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestampMs))
        }
    }
}

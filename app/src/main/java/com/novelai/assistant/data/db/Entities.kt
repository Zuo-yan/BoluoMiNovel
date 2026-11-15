package com.novelai.assistant.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BookSourceType {
    LOCAL_TXT, LOCAL_MD, LOCAL_EPUB, FANQIE, BOLUOBAO
}

enum class ChapterOriginType {
    ORIGINAL,        // [原] 原作章节
    AI_CONTINUATION  // [AI] AI 续写章节
}

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val coverUri: String?,
    val sourcePath: String,
    val sourceType: BookSourceType,
    val totalChapters: Int,
    val currentReadingChapterIndex: Int = 0,
    val readingProgressPercent: Float = 0f,
    val category: String = "默认",
    val createdAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long = 0L,
    @androidx.room.ColumnInfo(defaultValue = "1") val inBookshelf: Boolean = true
)

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val title: String,
    val content: String,
    val originType: ChapterOriginType,
    val parentChapterId: String? = null,
    val promptUsed: String? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    @androidx.room.ColumnInfo(defaultValue = "NULL") val remoteId: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val isPaid: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "0") val downloaded: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "''") val fetchError: String = ""
)

@Entity(tableName = "ai_chat_records")
data class AiChatRecordEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterId: String?,
    val role: String, // "user" | "assistant"
    val content: String,
    val modelUsed: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "book_outlines")
data class BookOutlineEntity(
    @PrimaryKey val bookId: String,
    val masterOutline: String = "",
    val upcomingPlan: String = "",
    val useInContinuation: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "outline_discussions",
    indices = [Index(value = ["bookId", "timestamp"])]
)
data class OutlineDiscussionEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val anchorChapterId: String? = null,
    val role: String, // "user" | "assistant"
    val content: String,
    val quickOptionsJson: String = "[]",
    val proposedMasterOutline: String? = null,
    val proposedUpcomingPlan: String? = null,
    val modelUsed: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chapter_summaries",
    indices = [Index(value = ["bookId"])]
)
data class ChapterSummaryEntity(
    @PrimaryKey val chapterId: String,
    val bookId: String,
    val contentHash: String,
    val plotSummary: String,
    val characterStates: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "rag_vector_cache",
    indices = [Index(value = ["bookId"]), Index(value = ["bookId", "chapterId"])]
)
data class RagVectorCacheEntity(
    @PrimaryKey val cacheKey: String,
    val bookId: String,
    val chapterId: String,
    val contentHash: String,
    val vectorBytes: ByteArray,
    val updatedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RagVectorCacheEntity) return false
        return cacheKey == other.cacheKey &&
            bookId == other.bookId &&
            chapterId == other.chapterId &&
            contentHash == other.contentHash &&
            vectorBytes.contentEquals(other.vectorBytes) &&
            updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = cacheKey.hashCode()
        result = 31 * result + bookId.hashCode()
        result = 31 * result + chapterId.hashCode()
        result = 31 * result + contentHash.hashCode()
        result = 31 * result + vectorBytes.contentHashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}

@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["bookId", "createdAt"]), Index(value = ["bookId", "chapterId"])]
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterId: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val paragraphIndex: Int = 0,
    val charOffset: Int = 0,
    val scrollOffset: Int = 0,
    val excerpt: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

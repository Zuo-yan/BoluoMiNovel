package com.novelai.assistant.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class BookSourceType {
    LOCAL_TXT, LOCAL_MD, LOCAL_EPUB, FANQIE, BOLUOBAO
}

enum class ChapterOriginType {
    ORIGINAL,        // [原] 原作章节
    AI_CONTINUATION, // [AI] AI 续写章节
    AI_BRANCH        // [分] AI 分支章节
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
    val lastReadAt: Long = 0L
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

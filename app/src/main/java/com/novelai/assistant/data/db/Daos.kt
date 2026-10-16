package com.novelai.assistant.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE inBookshelf = 1 ORDER BY lastReadAt DESC, createdAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    @Query("UPDATE books SET inBookshelf = :inBookshelf WHERE id = :bookId")
    suspend fun updateInBookshelf(bookId: String, inBookshelf: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE books SET currentReadingChapterIndex = :chapterIndex, readingProgressPercent = :percent, lastReadAt = :lastReadAt WHERE id = :bookId")
    suspend fun updateProgress(bookId: String, chapterIndex: Int, percent: Float, lastReadAt: Long)

    @Query("UPDATE books SET category = :category WHERE id = :bookId")
    suspend fun updateCategory(bookId: String, category: String)

    @Query("UPDATE books SET category = '默认' WHERE category = :categoryName")
    suspend fun resetCategoryBooks(categoryName: String)
}

@Dao
interface ChapterDao {
    @Query("UPDATE chapters SET content = :content, fetchError = '', downloaded = CASE WHEN :pin THEN 1 ELSE downloaded END WHERE id = :id")
    suspend fun cacheRemote(id: String, content: String, pin: Boolean)

    @Query("UPDATE chapters SET fetchError = :error WHERE id = :id")
    suspend fun setFetchError(id: String, error: String)

    @Query("UPDATE chapters SET content = '', fetchError = '' WHERE remoteId IS NOT NULL AND downloaded = 0")
    suspend fun clearReadingCache()

    @Query("SELECT COALESCE(SUM(length(CAST(content AS BLOB))), 0) FROM chapters WHERE remoteId IS NOT NULL AND downloaded = 0")
    fun observeCacheBytes(): Flow<Long>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterIndex ASC")
    fun observeByBook(bookId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterIndex ASC")
    suspend fun getByBook(bookId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getById(id: String): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE id = :id")
    fun observeById(id: String): Flow<ChapterEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ChapterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chapter: ChapterEntity)

    @Update
    suspend fun update(chapter: ChapterEntity)

    @Query("UPDATE chapters SET content = :content WHERE id = :id")
    suspend fun updateContent(id: String, content: String)

    @Query("UPDATE chapters SET isFavorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: String, favorite: Boolean)

    @Query("SELECT MAX(chapterIndex) FROM chapters WHERE bookId = :bookId")
    suspend fun maxChapterIndex(bookId: String): Int?

    @Query("SELECT COUNT(*) FROM chapters WHERE bookId = :bookId")
    suspend fun countByBook(bookId: String): Int

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: String)

    @Query("DELETE FROM chapters WHERE id = :id")
    suspend fun delete(id: String)

    @Transaction
    suspend fun deleteBookChapters(bookId: String) = deleteByBook(bookId)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM ai_chat_records WHERE bookId = :bookId ORDER BY timestamp ASC")
    fun observeByBook(bookId: String): Flow<List<AiChatRecordEntity>>

    @Query("SELECT * FROM ai_chat_records WHERE bookId = :bookId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(bookId: String, limit: Int): List<AiChatRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AiChatRecordEntity)

    @Query("DELETE FROM ai_chat_records WHERE bookId = :bookId")
    suspend fun clearByBook(bookId: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM categories WHERE name = :name")
    suspend fun deleteByName(name: String)
}

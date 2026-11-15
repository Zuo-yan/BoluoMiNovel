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

@Dao
interface OutlineDao {
    @Query("SELECT * FROM book_outlines WHERE bookId = :bookId")
    fun observeByBook(bookId: String): Flow<BookOutlineEntity?>

    @Query("SELECT * FROM book_outlines WHERE bookId = :bookId")
    suspend fun getByBook(bookId: String): BookOutlineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(outline: BookOutlineEntity)

    @Query("UPDATE book_outlines SET useInContinuation = :enabled, updatedAt = :updatedAt WHERE bookId = :bookId")
    suspend fun updateUseInContinuation(bookId: String, enabled: Boolean, updatedAt: Long)

    @Query("DELETE FROM book_outlines WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: String)
}

@Dao
interface OutlineDiscussionDao {
    @Query("SELECT * FROM outline_discussions WHERE bookId = :bookId ORDER BY timestamp ASC")
    fun observeByBook(bookId: String): Flow<List<OutlineDiscussionEntity>>

    @Query("SELECT * FROM outline_discussions WHERE bookId = :bookId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(bookId: String, limit: Int): List<OutlineDiscussionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: OutlineDiscussionEntity)

    @Query("DELETE FROM outline_discussions WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: String)
}

@Dao
interface ChapterSummaryDao {
    @Query("SELECT * FROM chapter_summaries WHERE bookId = :bookId")
    fun observeByBook(bookId: String): Flow<List<ChapterSummaryEntity>>

    @Query("SELECT * FROM chapter_summaries WHERE bookId = :bookId")
    suspend fun getByBook(bookId: String): List<ChapterSummaryEntity>

    @Query("SELECT * FROM chapter_summaries WHERE chapterId = :chapterId")
    suspend fun getByChapter(chapterId: String): ChapterSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: ChapterSummaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(summaries: List<ChapterSummaryEntity>)

    @Query("DELETE FROM chapter_summaries WHERE chapterId = :chapterId")
    suspend fun deleteByChapter(chapterId: String)

    @Query("DELETE FROM chapter_summaries WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: String)
}

@Dao
interface RagVectorCacheDao {
    @Query("SELECT * FROM rag_vector_cache WHERE cacheKey IN (:cacheKeys)")
    suspend fun getByKeys(cacheKeys: List<String>): List<RagVectorCacheEntity>

    @Query("SELECT COUNT(*) FROM rag_vector_cache WHERE bookId = :bookId")
    fun observeCountByBook(bookId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM rag_vector_cache WHERE bookId = :bookId")
    suspend fun countByBook(bookId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<RagVectorCacheEntity>)

    @Query("DELETE FROM rag_vector_cache WHERE bookId = :bookId AND chapterId = :chapterId")
    suspend fun deleteByChapter(bookId: String, chapterId: String)

    @Query("DELETE FROM rag_vector_cache WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: String)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeByBook(bookId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    suspend fun getByBook(bookId: String): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM bookmarks WHERE chapterId = :chapterId")
    suspend fun deleteByChapter(chapterId: String)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: String)
}

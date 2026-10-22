package com.novelai.assistant.data.sf

import androidx.room.withTransaction
import com.novelai.assistant.data.db.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SfLibraryRepository @Inject constructor(
    private val db: NovelDatabase,
    private val session: SfSessionRepository
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    val cacheBytes: Flow<Long> = db.chapterDao().observeCacheBytes()
    suspend fun clearReadingCache() = db.chapterDao().clearReadingCache()

    /** Refresh directory metadata, preserving cached text, downloads, AI chapters and local progress. */
    suspend fun openBook(remoteId: String): String {
        val catalog = session.catalog(remoteId)
        val bookId = "sf:$remoteId"
        db.withTransaction {
            val previous = db.bookDao().getById(bookId)
            val old = db.chapterDao().getByBook(bookId).associateBy { it.id }
            val chapters = catalog.chapters.mapIndexed { i, chapter ->
                val id = "$bookId:${chapter.id}"
                old[id]?.copy(chapterIndex = i, remoteId = chapter.id, isPaid = chapter.paid)
                    ?: ChapterEntity(id, bookId, i, chapter.title, "", ChapterOriginType.ORIGINAL,
                        remoteId = chapter.id, isPaid = chapter.paid)
            }
            db.chapterDao().insertAll(chapters)
            val extra = old.values.filter { it.remoteId == null }.sortedBy { it.chapterIndex }
            extra.forEachIndexed { i, c -> db.chapterDao().update(c.copy(chapterIndex = chapters.size + i)) }
            val count = db.chapterDao().countByBook(bookId)
            db.bookDao().insert(previous?.copy(title = catalog.title, author = catalog.author,
                coverUri = previous.coverUri ?: catalog.cover, totalChapters = count)
                ?: BookEntity(bookId, catalog.title, catalog.author, catalog.cover,
                    "https://book.sfacg.com/Novel/$remoteId/", BookSourceType.BOLUOBAO, count, inBookshelf = false))
        }
        return bookId
    }

    suspend fun readable(chapterId: String, pin: Boolean = false): ChapterEntity =
        locks.getOrPut(chapterId) { Mutex() }.withLock {
            val chapter = db.chapterDao().getById(chapterId) ?: error("章节已被删除")
            if (chapter.content.isNotBlank()) {
                if (pin && chapter.remoteId != null) db.chapterDao().cacheRemote(chapter.id, chapter.content, true)
                return@withLock chapter.copy(downloaded = chapter.downloaded || pin)
            }
            val remoteId = chapter.remoteId ?: error(chapter.fetchError.ifBlank { "本章没有可阅读的正文" })
            try {
                val content = session.chapter(remoteId, chapter.isPaid)
                db.chapterDao().cacheRemote(chapter.id, content, pin)
                chapter.copy(content = content, downloaded = chapter.downloaded || pin, fetchError = "")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                db.chapterDao().setFetchError(chapter.id, e.message ?: "正文加载失败")
                throw e
            }
        }

    /**
     * Load the chapter once for read/cache, along with row/count summaries.
     * Cached text is still paired with a fresh anonymous summary request so
     * comment badges do not become stale; failures only affect the badges.
     */
    suspend fun readableWithComments(
        chapterId: String
    ): Pair<ChapterEntity, List<SfParagraphCommentSummary>> =
        locks.getOrPut(chapterId) { Mutex() }.withLock {
            val chapter = db.chapterDao().getById(chapterId) ?: error("章节已被删除")
            val remoteId = chapter.remoteId ?: return@withLock chapter to emptyList()
            var loaded = chapter
            if (chapter.content.isBlank()) {
                val (content, summaries) = session.chapterWithComments(remoteId, chapter.isPaid)
                db.chapterDao().cacheRemote(chapter.id, content, false)
                loaded = chapter.copy(content = content, fetchError = "")
                loaded to summaries
            } else {
                // A cached chapter does not contain server metadata. Fetch just
                // the summary fields as part of this one read; if the optional
                // response cannot be read, return text without inventing badges.
                val summaries = runCatching {
                    session.chapterWithComments(remoteId, chapter.isPaid).second
                }.getOrDefault(emptyList())
                loaded to summaries
            }
        }
}

package com.novelai.assistant.data.rag

import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 伴读检索服务：按书维护 BM25 索引缓存，支持「不剧透」过滤（只召回当前阅读位置之前的章节）。
 */
@Singleton
class RetrievalService @Inject constructor(
    private val bookRepository: BookRepository
) {
    private data class CacheKey(val bookId: String, val version: Long)

    private val mutex = Mutex()
    private var cached: CacheKey? = null
    private var index: ChapterSearchIndex? = null
    private var chaptersRef: List<ChapterEntity> = emptyList()

    suspend fun retrieve(
        bookId: String,
        query: String,
        topK: Int = 3,
        spoilerFree: Boolean,
        currentChapterIndex: Int?
    ): List<ScoredChapter> = withContext(Dispatchers.Default) {
        val chapters = bookRepository.getChapters(bookId).filter { it.content.isNotBlank() }
        if (chapters.isEmpty()) return@withContext emptyList()

        mutex.withLock {
            val key = CacheKey(bookId, chapters.fold(1L) { hash, chapter -> 31 * hash + chapter.content.hashCode() })
            if (cached != key) {
                index = ChapterSearchIndex.build(chapters)
                chaptersRef = chapters
                cached = key
            }
        }
        val idx = index ?: return@withContext emptyList()
        idx.search(
            query = query,
            topK = topK,
            maxChapterIndex = if (spoilerFree) currentChapterIndex else null
        )
    }

    /** 书籍章节变化（如 AI 续写保存）后失效缓存 */
    fun evict(bookId: String) {
        if (cached?.bookId == bookId) {
            cached = null
            index = null
            chaptersRef = emptyList()
        }
    }
}

package com.novelai.assistant.data.repository

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.novelai.assistant.data.db.BookDao
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.BookSourceType
import com.novelai.assistant.data.db.ChapterDao
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.db.ChapterOriginType
import com.novelai.assistant.data.db.NovelDatabase
import com.novelai.assistant.data.fetcher.DESKTOP_UA
import com.novelai.assistant.data.importer.BookImportManager
import com.novelai.assistant.data.sf.BookExportFormat
import com.novelai.assistant.data.sf.BookExporter
import com.novelai.assistant.data.sf.SfLibraryRepository
import com.novelai.assistant.data.sf.SfParagraphCommentSummary
import com.novelai.assistant.data.sf.SfSessionRepository
import com.novelai.assistant.di.ApplicationScope
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepository @Inject constructor(
    private val database: NovelDatabase,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val importManager: BookImportManager,
    private val httpClient: HttpClient,
    private val sfLibrary: SfLibraryRepository,
    private val sfSession: SfSessionRepository,
    private val contentResolver: ContentResolver,
    @ApplicationScope private val appScope: CoroutineScope
) {
    fun observeBooks(): Flow<List<BookEntity>> = bookDao.observeAll()
    fun observeBook(id: String): Flow<BookEntity?> = bookDao.observeById(id)
    fun observeChapters(bookId: String): Flow<List<ChapterEntity>> =
        chapterDao.observeByBook(bookId).map { normalizeChaptersOrder(it) }
    fun observeChapter(id: String): Flow<ChapterEntity?> = chapterDao.observeById(id)

    suspend fun getBook(id: String): BookEntity? = bookDao.getById(id)
    suspend fun getChapters(bookId: String): List<ChapterEntity> =
        normalizeChaptersOrder(chapterDao.getByBook(bookId))
    suspend fun getChapter(id: String): ChapterEntity? = chapterDao.getById(id)
    suspend fun readableChapter(id: String): ChapterEntity = sfLibrary.readable(id)
    suspend fun readableChapterWithComments(
        id: String
    ): Pair<ChapterEntity, List<SfParagraphCommentSummary>> =
        sfLibrary.readableWithComments(id)

    suspend fun remoteParagraphComments(
        chapterId: String,
        sort: String,
        row: Int? = null,
        page: Int = 1
    ) = sfSession.paragraphComments(chapterId, page = page, sort = sort, row = row)

    suspend fun remoteParagraphReplies(id: String) = sfSession.paragraphReplies(id)

    suspend fun import(uri: Uri): BookImportManager.ImportResult = importManager.importFromUri(uri)

    /** 抓取器导入：直接落库在线抓取的章节（顺带下载源站封面并记录源站标识） */
    suspend fun importFetched(
        title: String,
        author: String,
        sourceType: BookSourceType,
        chapters: List<Pair<String, String>>,
        coverUrl: String? = null,
        sourceBookId: String? = null
    ): String {
        val bookId = UUID.randomUUID().toString()
        val sourcePathValue = when (sourceType) {
            BookSourceType.FANQIE ->
                sourceBookId?.let { "fanqienovel.com/page/$it" }
            BookSourceType.BOLUOBAO ->
                sourceBookId?.let { "book.sfacg.com/Novel/$it/MainIndex/" }
            else -> null
        } ?: title
        val coverPath = coverUrl?.let { url ->
            downloadBytes(url)?.let { bytes ->
                withContext(Dispatchers.IO) { importManager.storeCoverBytes(bookId, bytes) }
            }
        }
        database.withTransaction {
            bookDao.insert(
                BookEntity(
                    id = bookId,
                    title = title,
                    author = author,
                    coverUri = coverPath,
                    sourcePath = sourcePathValue,
                    sourceType = sourceType,
                    totalChapters = chapters.size,
                    createdAt = System.currentTimeMillis()
                )
            )
            chapterDao.insertAll(
                chapters.mapIndexed { i, (t, c) ->
                    ChapterEntity(
                        id = UUID.randomUUID().toString(),
                        bookId = bookId,
                        chapterIndex = i,
                        title = t,
                        content = c,
                        originType = ChapterOriginType.ORIGINAL
                    )
                }
            )
        }
        return bookId
    }

    /** 手动设置封面：从系统相册 URI 处理并落盘 */
    suspend fun setManualCover(bookId: String, uri: Uri): Boolean {
        val book = bookDao.getById(bookId) ?: return false
        val path = importManager.setCoverFromUri(bookId, book.coverUri, uri) ?: return false
        bookDao.update(book.copy(coverUri = path))
        return true
    }

    /** 恢复默认占位封面 */
    suspend fun clearManualCover(bookId: String) {
        val book = bookDao.getById(bookId) ?: return
        importManager.clearCover(book.coverUri)
        bookDao.update(book.copy(coverUri = null))
    }

    private suspend fun downloadBytes(url: String): ByteArray? = withTimeoutOrNull(COVER_TIMEOUT_MS) {
        try {
            val response = httpClient.get(url) {
                header(HttpHeaders.UserAgent, DESKTOP_UA)
            }
            if (response.status.value !in 200..299) return@withTimeoutOrNull null
            val bytes = response.bodyAsBytes()
            if (bytes.isEmpty() || bytes.size > MAX_COVER_BYTES) null else bytes
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun deleteBook(bookId: String) {
        database.withTransaction {
            chapterDao.deleteByBook(bookId)
            database.bookmarkDao().deleteByBook(bookId)
            database.chatDao().clearByBook(bookId)
            database.outlineDao().deleteByBook(bookId)
            database.outlineDiscussionDao().deleteByBook(bookId)
            database.chapterSummaryDao().deleteByBook(bookId)
            database.ragVectorCacheDao().deleteByBook(bookId)
            bookDao.delete(bookId)
        }
    }

    fun observeBookmarks(bookId: String): Flow<List<com.novelai.assistant.data.db.BookmarkEntity>> =
        database.bookmarkDao().observeByBook(bookId)

    suspend fun addBookmark(bookmark: com.novelai.assistant.data.db.BookmarkEntity) =
        database.bookmarkDao().insert(bookmark)

    suspend fun deleteBookmark(bookmarkId: String) =
        database.bookmarkDao().deleteById(bookmarkId)

    suspend fun saveProgress(bookId: String, chapterIndex: Int, totalChapters: Int) {
        val percent = if (totalChapters > 0) (chapterIndex + 1f) / totalChapters * 100f else 0f
        bookDao.updateProgress(bookId, chapterIndex, percent, System.currentTimeMillis())
    }

    fun saveProgressAsync(bookId: String, chapterIndex: Int, totalChapters: Int) {
        appScope.launch {
            runCatching { saveProgress(bookId, chapterIndex, totalChapters) }
        }
    }

    suspend fun exportChapterToUri(
        uri: Uri,
        book: BookEntity,
        chapter: ChapterEntity,
        format: BookExportFormat
    ) {
        BookExporter.exportToUri(
            contentResolver = contentResolver,
            uri = uri,
            book = book,
            selected = listOf(chapter),
            format = format,
            allowPartial = false
        )
    }

    suspend fun updateCategory(bookId: String, category: String) = bookDao.updateCategory(bookId, category)
    suspend fun resetCategoryBooks(categoryName: String) = bookDao.resetCategoryBooks(categoryName)

    suspend fun updateChapter(chapter: ChapterEntity) = chapterDao.update(chapter)

    suspend fun deleteChapter(bookId: String, chapterId: String) {
        database.withTransaction {
            val existing = normalizeChaptersOrder(chapterDao.getByBook(bookId))
            val deleted = existing.firstOrNull { it.id == chapterId }
            val currentBook = bookDao.getById(bookId)
            val viewedChapterId = currentBook?.let {
                existing.getOrNull(it.currentReadingChapterIndex)?.id
            }

            // 若删除了中间衍生章且其下仍有子续写，把子节点改挂到上一层父章节，避免脱节
            val reparented = existing
                .filter { it.id != chapterId }
                .map { ch ->
                    if (deleted != null && ch.parentChapterId == deleted.id) {
                        ch.copy(parentChapterId = deleted.parentChapterId)
                    } else {
                        ch
                    }
                }
            val reindexed = normalizeChaptersOrder(reparented)
            val beforeById = existing.associateBy { it.id }
            val changed = reindexed.filter { ch ->
                val prev = beforeById[ch.id]
                prev == null || prev.chapterIndex != ch.chapterIndex || prev.parentChapterId != ch.parentChapterId
            }

            chapterDao.delete(chapterId)
            database.bookmarkDao().deleteByChapter(chapterId)
            if (changed.isNotEmpty()) {
                chapterDao.insertAll(changed)
            }
            currentBook?.let { b ->
                val newIndex = when {
                    reindexed.isEmpty() -> 0
                    viewedChapterId != null && viewedChapterId != chapterId ->
                        reindexed.indexOfFirst { it.id == viewedChapterId }
                            .takeIf { it >= 0 }
                            ?: b.currentReadingChapterIndex.coerceIn(0, reindexed.lastIndex)
                    else -> b.currentReadingChapterIndex.coerceIn(0, reindexed.lastIndex)
                }
                bookDao.update(b.copy(totalChapters = reindexed.size, currentReadingChapterIndex = newIndex))
            }
        }
    }

    /** 保存 AI 生成内容为新章节（紧跟在所续写父章节及其已有子树之后） */
    suspend fun saveGeneratedChapter(
        bookId: String,
        parentChapterId: String?,
        title: String,
        content: String,
        promptUsed: String?,
        originType: ChapterOriginType = ChapterOriginType.AI_CONTINUATION
    ): ChapterEntity = database.withTransaction {
        val existing = normalizeChaptersOrder(chapterDao.getByBook(bookId))
        val existingById = existing.associateBy { it.id }
        val currentBook = bookDao.getById(bookId)
        val viewedChapterId = currentBook?.let {
            existing.getOrNull(it.currentReadingChapterIndex)?.id
        }

        val resolvedParentId = parentChapterId?.takeIf { it in existingById }
        val maxCreatedAt = existing.maxOfOrNull { it.createdAt } ?: 0L
        val createdAt = maxOf(System.currentTimeMillis(), maxCreatedAt + 1L)
        val normalizedOrigin = if (originType == ChapterOriginType.ORIGINAL) {
            ChapterOriginType.ORIGINAL
        } else {
            ChapterOriginType.AI_CONTINUATION
        }
        val newChapter = ChapterEntity(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            chapterIndex = existing.size,
            title = title,
            content = content.trim(),
            originType = normalizedOrigin,
            parentChapterId = resolvedParentId,
            promptUsed = promptUsed,
            createdAt = createdAt
        )

        val reindexed = normalizeChaptersOrder(existing + newChapter)
        val changed = reindexed.filter { ch ->
            ch.id == newChapter.id || existingById[ch.id]?.chapterIndex != ch.chapterIndex
        }
        chapterDao.insertAll(changed)

        currentBook?.let { b ->
            val newCurrentIndex = viewedChapterId?.let { vid ->
                reindexed.indexOfFirst { it.id == vid }.takeIf { it >= 0 }
            } ?: b.currentReadingChapterIndex
            bookDao.update(
                b.copy(
                    totalChapters = reindexed.size,
                    currentReadingChapterIndex = newCurrentIndex
                )
            )
        }
        reindexed.first { it.id == newChapter.id }
    }

    suspend fun appendToChapter(chapterId: String, extra: String) {
        val chapter = chapterDao.getById(chapterId) ?: return
        chapterDao.updateContent(chapterId, chapter.content + extra)
    }

    /**
     * 获取指定章节的完整前文剧情线：
     * - 若该章节有 parentChapterId，沿父节点逆向追溯直至原作主线，再拼接前序原作章
     * - 若缺失 parentChapterId（或中间父节点被删除），仅拼接其前的原作主线与无父节点主线续写
     * - 自动隔离开同源分叉出的其他平行续写线
     */
    suspend fun getChapterLineage(bookId: String, targetChapterId: String): List<ChapterEntity> {
        val all = getChapters(bookId)
        return resolveChapterLineage(all, targetChapterId)
    }

    companion object {
        private const val COVER_TIMEOUT_MS = 20_000L
        private const val MAX_COVER_BYTES = 8 * 1024 * 1024

        /**
         * 将章节列表按树形结构展平：
         * 原作主线章节按原序号依次推进，任何 AI 续写章及其子续写紧跟在其父章节之后，
         * 同一父章节下的多条续写按生成时间先后顺序排列。
         */
        fun flattenChapterTree(list: List<ChapterEntity>): List<ChapterEntity> {
            if (list.size <= 1) return list
            val baseOrdered = list.sortedWith(
                compareBy<ChapterEntity>({ it.chapterIndex }, { it.createdAt }, { it.id })
            )
            val byId = baseOrdered.associateBy { it.id }
            val childrenByParent = baseOrdered
                .filter { ch ->
                    val pid = ch.parentChapterId
                    pid != null && pid != ch.id && pid in byId
                }
                .groupBy { it.parentChapterId!! }
                .mapValues { (_, children) ->
                    children.sortedWith(
                        compareBy<ChapterEntity>({ it.createdAt }, { it.chapterIndex }, { it.id })
                    )
                }

            val result = ArrayList<ChapterEntity>(baseOrdered.size)
            val visited = HashSet<String>(baseOrdered.size)

            fun appendSubtree(node: ChapterEntity) {
                val children = childrenByParent[node.id].orEmpty()
                for (child in children) {
                    if (!visited.add(child.id)) continue
                    result.add(child)
                    appendSubtree(child)
                }
            }

            // 第一轮：先从真正根节点（无父节点或父节点已不存在）按主线顺序展开
            for (ch in baseOrdered) {
                if (ch.id in visited) continue
                val pid = ch.parentChapterId
                if (pid == null || pid !in byId) {
                    visited.add(ch.id)
                    result.add(ch)
                    appendSubtree(ch)
                }
            }
            // 兜底：若存在孤立环引用，按基线顺序补全，确保不丢任何章节且无死循环
            for (ch in baseOrdered) {
                if (!visited.add(ch.id)) continue
                result.add(ch)
                appendSubtree(ch)
            }
            return result
        }

        fun normalizeChaptersOrder(list: List<ChapterEntity>): List<ChapterEntity> =
            flattenChapterTree(list).mapIndexed { index, ch ->
                if (ch.chapterIndex == index) ch else ch.copy(chapterIndex = index)
            }

        fun resolveChapterLineage(
            all: List<ChapterEntity>,
            targetChapterId: String
        ): List<ChapterEntity> {
            val normalized = normalizeChaptersOrder(all)
            val target = normalized.firstOrNull { it.id == targetChapterId } ?: return emptyList()
            val byId = normalized.associateBy { it.id }
            val chain = mutableListOf<ChapterEntity>()
            var cur: ChapterEntity? = target
            val visited = mutableSetOf<String>()

            while (cur != null && cur.id !in visited) {
                visited.add(cur.id)
                chain.add(cur)
                if (cur.originType == ChapterOriginType.ORIGINAL) {
                    val originalHead = normalized
                        .filter { it.originType == ChapterOriginType.ORIGINAL && it.chapterIndex < cur.chapterIndex }
                        .sortedBy { it.chapterIndex }
                    return (originalHead + chain.reversed()).distinctBy { it.id }
                }
                val nextParent = cur.parentChapterId?.let { byId[it] }
                if (nextParent == null && cur.originType == ChapterOriginType.AI_CONTINUATION) {
                    val earlierMainline = normalized
                        .filter {
                            (it.originType == ChapterOriginType.ORIGINAL || it.parentChapterId == null) &&
                                it.chapterIndex < cur.chapterIndex
                        }
                        .sortedBy { it.chapterIndex }
                    return (earlierMainline + chain.reversed()).distinctBy { it.id }
                }
                cur = nextParent
            }
            return chain.reversed().distinctBy { it.id }
        }
    }
}

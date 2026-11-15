package com.novelai.assistant.data.rag

import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.db.RagVectorCacheDao
import com.novelai.assistant.data.db.RagVectorCacheEntity
import com.novelai.assistant.data.network.EmbeddingClient
import com.novelai.assistant.data.repository.AiConfigRepository
import com.novelai.assistant.data.repository.BookRepository
import com.novelai.assistant.data.repository.TaskScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 伴读与续写检索服务：
 * 1) 内存 L1 多书 LRU 保留最近访问书籍的段落级 BM25 索引；
 * 2) 当配置了向量模型（TaskScene.EMBEDDING）时，L1 内存 + L2 Room (`rag_vector_cache`) 双层持久化向量缓存，
 *    按「章节内容哈希 + 片段哈希」增量校验，重启 App 或保存新章均不丢失前文向量；
 * 3) 保存/追加章节后支持 `evictChapter(bookId, chapterId)` 局部失效，避免清空整本书旧缓存。
 */
@Singleton
class RetrievalService @Inject constructor(
    private val bookRepository: BookRepository,
    private val aiConfigRepository: AiConfigRepository,
    private val embeddingClient: EmbeddingClient,
    private val ragVectorCacheDao: RagVectorCacheDao
) {
    private data class CachedBookIndex(val versionHash: Long, val index: ChapterSearchIndex)

    private val mutex = Mutex()
    private val indexCache = object : LinkedHashMap<String, CachedBookIndex>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedBookIndex>?): Boolean =
            size > MAX_INDEX_BOOKS
    }
    private val vectorCache = ConcurrentHashMap<String, FloatArray>()

    fun observePersistedVectorCount(bookId: String): Flow<Int> =
        ragVectorCacheDao.observeCountByBook(bookId)

    suspend fun getPersistedVectorCount(bookId: String): Int =
        ragVectorCacheDao.countByBook(bookId)

    suspend fun retrieve(
        bookId: String,
        query: String,
        topK: Int = 3,
        spoilerFree: Boolean,
        currentChapterIndex: Int?
    ): List<ScoredChapter> = withContext(Dispatchers.Default) {
        val chapters = bookRepository.getChapters(bookId).filter { it.content.isNotBlank() }
        if (chapters.isEmpty()) return@withContext emptyList()

        val idx = ensureIndex(bookId, chapters) ?: return@withContext emptyList()
        idx.search(
            query = query,
            topK = topK,
            maxChapterIndex = if (spoilerFree) currentChapterIndex else null
        )
    }

    /**
     * 段落级混合检索：返回精确命中的段落片段（约 480 字），优先命中本地持久化向量缓存。
     */
    suspend fun retrieveSnippets(
        bookId: String,
        query: String,
        topK: Int = 3,
        spoilerFree: Boolean,
        currentChapterIndex: Int?,
        excludeChapterIds: Set<String> = emptySet()
    ): List<RetrievedSnippet> = withContext(Dispatchers.Default) {
        val chapters = bookRepository.getChapters(bookId).filter { it.content.isNotBlank() }
        if (chapters.isEmpty() || query.isBlank()) return@withContext emptyList()

        val idx = ensureIndex(bookId, chapters) ?: return@withContext emptyList()
        val maxIdx = if (spoilerFree) currentChapterIndex else null

        val embeddingTarget = aiConfigRepository.resolveActive(TaskScene.EMBEDDING)
        if (embeddingTarget == null) {
            return@withContext idx.searchSnippets(
                query = query,
                topK = topK,
                maxChapterIndex = maxIdx,
                excludeChapterIds = excludeChapterIds,
                distinctChapters = true
            )
        }

        // 阶段 1：段落级 BM25 粗召回；字面命中不足时补充时间线均匀抽样片段以便语义重排
        val candidateLimit = (topK * 6).coerceAtLeast(18)
        val bm25Candidates = idx.searchSnippets(
            query = query,
            topK = candidateLimit,
            maxChapterIndex = maxIdx,
            excludeChapterIds = excludeChapterIds,
            distinctChapters = false
        )
        val candidates = if (bm25Candidates.size >= 8) {
            bm25Candidates
        } else {
            val existingKeys = bm25Candidates.mapTo(HashSet()) { "${it.chapter.id}:${RagVectorCodec.snippetHash(it.snippet)}" }
            val sampled = idx.sampleCandidateSnippets(
                limit = 12,
                maxChapterIndex = maxIdx,
                excludeChapterIds = excludeChapterIds
            ).filter { "${it.chapter.id}:${RagVectorCodec.snippetHash(it.snippet)}" !in existingKeys }
            (bm25Candidates + sampled).take(candidateLimit)
        }
        if (candidates.isEmpty()) return@withContext emptyList()

        // 阶段 2：L1 内存 -> L2 Room 持久化缓存 -> 仅对真正缺失片段调用在线 Embedding 并回写落盘
        val (provider, model) = embeddingTarget
        val reranked = runCatching {
            val queryVec = embeddingClient.embed(provider, model, listOf(query)).getOrThrow().firstOrNull()
                ?: return@runCatching null

            val candidateMeta = candidates.map { item ->
                val chHash = RagVectorCodec.chapterContentHash(item.chapter.title, item.chapter.content)
                val snHash = RagVectorCodec.snippetHash(item.snippet)
                val key = "${provider.id}|$model|$bookId|${item.chapter.id}|$snHash"
                Triple(key, chHash, item)
            }

            val missingAfterL1 = mutableListOf<Int>()
            val candidateVecs = Array<FloatArray?>(candidates.size) { i ->
                val vec = vectorCache[candidateMeta[i].first]
                if (vec == null) missingAfterL1 += i
                vec
            }

            if (missingAfterL1.isNotEmpty()) {
                val lookupKeys = missingAfterL1.map { candidateMeta[it].first }
                val persisted = withContext(Dispatchers.IO) {
                    lookupKeys.chunked(200).flatMap { chunkKeys ->
                        ragVectorCacheDao.getByKeys(chunkKeys)
                    }
                }.associateBy { it.cacheKey }

                val stillMissing = mutableListOf<Int>()
                for (candIdx in missingAfterL1) {
                    val (key, chHash, _) = candidateMeta[candIdx]
                    val hit = persisted[key]
                    val decoded = hit?.takeIf { it.contentHash == chHash }?.let {
                        RagVectorCodec.decodeVector(it.vectorBytes)
                    }
                    if (decoded != null) {
                        candidateVecs[candIdx] = decoded
                        putL1Vector(key, decoded)
                    } else {
                        stillMissing += candIdx
                    }
                }

                if (stillMissing.isNotEmpty()) {
                    val missingTexts = stillMissing.map { candIdx ->
                        val item = candidates[candIdx]
                        "${item.chapter.title}\n${item.snippet}"
                    }
                    val fetched = embeddingClient.embed(provider, model, missingTexts).getOrThrow()
                    if (fetched.size != stillMissing.size) return@runCatching null

                    val now = System.currentTimeMillis()
                    val toPersist = ArrayList<RagVectorCacheEntity>(stillMissing.size)
                    stillMissing.forEachIndexed { pos, candIdx ->
                        val vec = fetched[pos]
                        val (key, chHash, item) = candidateMeta[candIdx]
                        candidateVecs[candIdx] = vec
                        putL1Vector(key, vec)
                        toPersist += RagVectorCacheEntity(
                            cacheKey = key,
                            bookId = bookId,
                            chapterId = item.chapter.id,
                            contentHash = chHash,
                            vectorBytes = RagVectorCodec.encodeVector(vec),
                            updatedAt = now
                        )
                    }
                    withContext(Dispatchers.IO) {
                        ragVectorCacheDao.upsertAll(toPersist)
                    }
                }
            }

            val vectors = candidateVecs.map { it ?: return@runCatching null }
            rerankSnippetsWithVectors(
                candidates = candidates,
                queryVector = queryVec,
                candidateVectors = vectors,
                topK = topK
            )
        }.getOrNull()

        reranked ?: bm25Candidates
            .sortedByDescending { it.score }
            .distinctBy { it.chapter.id }
            .take(topK)
    }

    private fun putL1Vector(key: String, vec: FloatArray) {
        if (vectorCache.size >= MAX_VECTOR_CACHE && !vectorCache.containsKey(key)) {
            vectorCache.clear()
        }
        vectorCache[key] = vec
    }

    private suspend fun ensureIndex(bookId: String, chapters: List<ChapterEntity>): ChapterSearchIndex? {
        val versionHash = chapters.fold(1L) { hash, chapter ->
            31 * hash + chapter.id.hashCode() * 17L + chapter.content.hashCode()
        }
        return mutex.withLock {
            val hit = indexCache[bookId]
            if (hit != null && hit.versionHash == versionHash) {
                hit.index
            } else {
                val built = ChapterSearchIndex.build(chapters)
                indexCache[bookId] = CachedBookIndex(versionHash, built)
                built
            }
        }
    }

    /** 仅局部失效单个改动章节的向量缓存并刷新对应书籍索引，绝不清空前文数百章历史缓存 */
    suspend fun evictChapter(bookId: String, chapterId: String) {
        mutex.withLock {
            indexCache.remove(bookId)
        }
        val marker = "|$bookId|$chapterId|"
        vectorCache.keys.removeIf { it.contains(marker) }
        withContext(Dispatchers.IO) {
            ragVectorCacheDao.deleteByChapter(bookId, chapterId)
        }
    }

    /** 整书重导入或删除时清理本书索引与向量缓存 */
    suspend fun evict(bookId: String) {
        mutex.withLock {
            indexCache.remove(bookId)
        }
        val marker = "|$bookId|"
        vectorCache.keys.removeIf { it.contains(marker) }
        withContext(Dispatchers.IO) {
            ragVectorCacheDao.deleteByBook(bookId)
        }
    }

    companion object {
        private const val MAX_INDEX_BOOKS = 4
        private const val MAX_VECTOR_CACHE = 1500

        /**
         * 混合打分并按章节去重：0.45 * 归一化 BM25 + 0.55 * 归一化余弦相似度。
         */
        internal fun rerankSnippetsWithVectors(
            candidates: List<RetrievedSnippet>,
            queryVector: FloatArray,
            candidateVectors: List<FloatArray>,
            topK: Int
        ): List<RetrievedSnippet> {
            if (candidates.isEmpty() || candidates.size != candidateVectors.size) return emptyList()
            val maxBm25 = candidates.maxOf { it.score }.coerceAtLeast(1e-9)
            val cosScores = candidateVectors.map { EmbeddingClient.cosineSimilarity(queryVector, it).coerceIn(-1.0, 1.0) }
            val minCos = cosScores.minOrNull() ?: 0.0
            val maxCos = cosScores.maxOrNull() ?: 0.0
            val cosSpan = (maxCos - minCos).takeIf { it > 1e-9 } ?: 1.0

            return candidates.mapIndexed { i, item ->
                val bm25Norm = (item.score / maxBm25).coerceIn(0.0, 1.0)
                val cosNorm = if (maxCos > minCos + 1e-9) {
                    ((cosScores[i] - minCos) / cosSpan).coerceIn(0.0, 1.0)
                } else {
                    ((cosScores[i] + 1.0) / 2.0).coerceIn(0.0, 1.0)
                }
                item.copy(score = 0.45 * bm25Norm + 0.55 * cosNorm, usedEmbedding = true)
            }
                .sortedByDescending { it.score }
                .distinctBy { it.chapter.id }
                .take(topK)
        }
    }
}

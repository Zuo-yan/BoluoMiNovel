package com.novelai.assistant.data.rag

import com.novelai.assistant.data.db.ChapterEntity

data class ScoredChapter(val chapter: ChapterEntity, val score: Double)

/** 检索召回的精确原文片段（用于续写与伴读注入，避免整章截取只取开头）。 */
data class RetrievedSnippet(
    val chapter: ChapterEntity,
    val snippet: String,
    val score: Double,
    val usedEmbedding: Boolean = false
)

internal data class IndexedChunk(
    val chapter: ChapterEntity,
    val chunkIndex: Int,
    val snippet: String
)

/** 分词：CJK 连续串按字符 bigram + 单字展开；拉丁/数字串按整词。检索与建索引共用。 */
internal fun tokenizeForIndex(text: String): List<String> {
    val terms = mutableListOf<String>()
    val buf = StringBuilder()
    var bufHasCjk = false

    fun flush() {
        val s = buf.toString()
        buf.setLength(0)
        if (s.isEmpty()) return
        if (!bufHasCjk) {
            terms.add(s)
            return
        }
        if (s.length == 1) {
            terms.add(s)
        } else {
            for (i in 0 until s.length - 1) terms.add(s.substring(i, i + 2))
            for (c in s) terms.add(c.toString())
        }
    }

    for (ch in text) {
        when {
            isCjkChar(ch) -> { buf.append(ch); bufHasCjk = true }
            ch.isLetterOrDigit() -> {
                if (bufHasCjk) flush()
                buf.append(ch.lowercaseChar())
                bufHasCjk = false
            }
            else -> flush()
        }
    }
    flush()
    return terms
}

private fun isCjkChar(c: Char): Boolean {
    val block = Character.UnicodeBlock.of(c) ?: return false
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
        block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
}

/**
 * 章节段落级 BM25 检索索引。纯 Kotlin 实现，无需外部分词/向量模型，
 * 将章节切分为约 480 字的带重叠片段，保证能精确定位章内伏笔与关键段落。
 */
class ChapterSearchIndex private constructor(
    private val chunks: List<IndexedChunk>,
    private val postings: Map<String, Map<Int, Int>>, // term -> (chunkIdx -> tf)
    private val docLengths: IntArray,
    private val avgDocLength: Double
) {

    fun search(query: String, topK: Int = 3, maxChapterIndex: Int? = null): List<ScoredChapter> =
        searchSnippets(
            query = query,
            topK = topK,
            maxChapterIndex = maxChapterIndex,
            distinctChapters = true
        ).map { ScoredChapter(it.chapter, it.score) }

    fun searchSnippets(
        query: String,
        topK: Int = 3,
        maxChapterIndex: Int? = null,
        excludeChapterIds: Set<String> = emptySet(),
        distinctChapters: Boolean = true
    ): List<RetrievedSnippet> {
        if (topK <= 0 || chunks.isEmpty()) return emptyList()
        val queryTerms = tokenizeForIndex(query).distinct()
        if (queryTerms.isEmpty()) return emptyList()

        val totalDocs = chunks.size
        val scores = HashMap<Int, Double>()

        for (term in queryTerms) {
            val docMap = postings[term] ?: continue
            val df = docMap.size.toDouble()
            val idf = kotlin.math.ln((totalDocs - df + 0.5) / (df + 0.5) + 1.0)
            for ((chunkIdx, tf) in docMap) {
                val chunk = chunks[chunkIdx]
                if (chunk.chapter.id in excludeChapterIds) continue
                if (maxChapterIndex != null && chunk.chapter.chapterIndex > maxChapterIndex) continue
                val tfNorm = (tf * (BM25_K + 1)) /
                    (tf + BM25_K * (1 - BM25_B + BM25_B * docLengths[chunkIdx] / avgDocLength))
                scores[chunkIdx] = (scores[chunkIdx] ?: 0.0) + idf * tfNorm
            }
        }

        val ranked = scores.entries
            .sortedByDescending { it.value }
            .map { (idx, score) ->
                val chunk = chunks[idx]
                RetrievedSnippet(
                    chapter = chunk.chapter,
                    snippet = chunk.snippet,
                    score = score,
                    usedEmbedding = false
                )
            }

        if (!distinctChapters) return ranked.take(topK)

        val seenChapters = HashSet<String>()
        val result = ArrayList<RetrievedSnippet>(topK)
        for (item in ranked) {
            if (seenChapters.add(item.chapter.id)) {
                result.add(item)
                if (result.size >= topK) break
            }
        }
        return result
    }

    /**
     * 当字面关键词未命中（例如纯抽象语义提问）且开启向量重排时，
     * 从符合防剧透条件的片段中均匀抽取候选供向量模型语义匹配。
     */
    fun sampleCandidateSnippets(
        limit: Int = 12,
        maxChapterIndex: Int? = null,
        excludeChapterIds: Set<String> = emptySet()
    ): List<RetrievedSnippet> {
        if (limit <= 0) return emptyList()
        val eligible = chunks.filter { chunk ->
            chunk.chapter.id !in excludeChapterIds &&
                (maxChapterIndex == null || chunk.chapter.chapterIndex <= maxChapterIndex)
        }
        if (eligible.isEmpty()) return emptyList()
        if (eligible.size <= limit) {
            return eligible.map { RetrievedSnippet(it.chapter, it.snippet, score = 0.0) }
        }
        val step = eligible.size.toDouble() / limit
        return (0 until limit).map { i ->
            val chunk = eligible[(i * step).toInt().coerceIn(0, eligible.lastIndex)]
            RetrievedSnippet(chunk.chapter, chunk.snippet, score = 0.0)
        }
    }

    companion object {
        private const val BM25_K = 1.4
        private const val BM25_B = 0.72
        private const val MAX_DOC_CHARS = 40_000
        internal const val CHUNK_TARGET_CHARS = 480
        internal const val CHUNK_MAX_CHARS = 650
        internal const val CHUNK_OVERLAP_CHARS = 100

        internal fun splitChapterIntoSnippets(content: String): List<String> {
            val bounded = content.take(MAX_DOC_CHARS).trim()
            if (bounded.isEmpty()) return emptyList()
            if (bounded.length <= CHUNK_MAX_CHARS) return listOf(bounded)

            val paragraphs = bounded.split(Regex("\\n+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val rawPieces = mutableListOf<String>()
            for (para in paragraphs) {
                if (para.length <= CHUNK_MAX_CHARS) {
                    rawPieces.add(para)
                } else {
                    var start = 0
                    while (start < para.length) {
                        val end = (start + CHUNK_TARGET_CHARS).coerceAtMost(para.length)
                        rawPieces.add(para.substring(start, end))
                        if (end >= para.length) break
                        start = (end - CHUNK_OVERLAP_CHARS).coerceAtLeast(start + 1)
                    }
                }
            }

            val chunks = mutableListOf<String>()
            val buf = StringBuilder()
            var lastParaTail = ""

            for (piece in rawPieces) {
                if (buf.isEmpty()) {
                    if (lastParaTail.isNotEmpty() && piece.length + lastParaTail.length + 1 <= CHUNK_MAX_CHARS) {
                        buf.append(lastParaTail).append('\n')
                    }
                    buf.append(piece)
                } else if (buf.length + 1 + piece.length <= CHUNK_TARGET_CHARS) {
                    buf.append('\n').append(piece)
                } else {
                    val finished = buf.toString().trim()
                    if (finished.isNotEmpty()) chunks.add(finished)
                    lastParaTail = finished.takeLast(CHUNK_OVERLAP_CHARS)
                    buf.setLength(0)
                    if (lastParaTail.isNotEmpty() && piece.length + lastParaTail.length + 1 <= CHUNK_MAX_CHARS) {
                        buf.append(lastParaTail).append('\n')
                    }
                    buf.append(piece)
                }
            }
            if (buf.isNotEmpty()) {
                val tail = buf.toString().trim()
                if (tail.isNotEmpty()) chunks.add(tail)
            }
            return chunks
        }

        fun build(chapters: List<ChapterEntity>): ChapterSearchIndex {
            val allChunks = mutableListOf<IndexedChunk>()
            for (chapter in chapters) {
                val snippets = splitChapterIntoChunksForChapter(chapter)
                snippets.forEachIndexed { idx, snippet ->
                    allChunks.add(IndexedChunk(chapter = chapter, chunkIndex = idx, snippet = snippet))
                }
            }

            val postings = HashMap<String, HashMap<Int, Int>>()
            val docLengths = IntArray(allChunks.size)
            var totalLength = 0L

            allChunks.forEachIndexed { chunkIdx, chunk ->
                val text = chunk.chapter.title + "\n" + chunk.snippet
                val terms = tokenizeForIndex(text)
                docLengths[chunkIdx] = terms.size
                totalLength += terms.size
                for (term in terms) {
                    val docMap = postings.getOrPut(term) { HashMap() }
                    docMap[chunkIdx] = (docMap[chunkIdx] ?: 0) + 1
                }
            }
            val avg = if (allChunks.isEmpty()) 1.0 else totalLength.toDouble() / allChunks.size
            return ChapterSearchIndex(allChunks, postings, docLengths, avg.coerceAtLeast(1.0))
        }

        private fun splitChapterIntoChunksForChapter(chapter: ChapterEntity): List<String> {
            val snippets = splitChapterIntoSnippets(chapter.content)
            return if (snippets.isNotEmpty()) snippets
            else if (chapter.title.isNotBlank()) listOf(chapter.title)
            else emptyList()
        }
    }
}

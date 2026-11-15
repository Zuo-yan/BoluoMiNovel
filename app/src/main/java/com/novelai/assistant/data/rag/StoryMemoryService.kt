package com.novelai.assistant.data.rag

import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.db.ChapterSummaryDao
import com.novelai.assistant.data.db.ChapterSummaryEntity
import com.novelai.assistant.data.network.AiClientFactory
import com.novelai.assistant.data.network.StreamEvent
import com.novelai.assistant.data.repository.AiConfigRepository
import com.novelai.assistant.data.repository.TaskScene
import com.novelai.assistant.data.repository.UsageRepository
import com.novelai.assistant.di.ApplicationScope
import com.novelai.assistant.domain.PromptBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 章节长期剧情记忆服务：
 * 为章节增量生成并持久化「剧情摘要 + 人物/伏笔状态卡」（Room `chapter_summaries`），
 * 根据章节 contentHash 校验有效性，仅在章节缺失或正文改动时调用轻量模型更新。
 */
@Singleton
class StoryMemoryService @Inject constructor(
    private val chapterSummaryDao: ChapterSummaryDao,
    private val aiConfigRepository: AiConfigRepository,
    private val aiClientFactory: AiClientFactory,
    private val usageRepository: UsageRepository,
    @param:ApplicationScope private val appScope: CoroutineScope
) {
    fun observeValidSummaryCount(bookId: String, chapters: List<ChapterEntity>): Flow<Int> =
        chapterSummaryDao.observeByBook(bookId).map { list ->
            val hashByChapter = chapters.associate {
                it.id to RagVectorCodec.chapterContentHash(it.title, it.content)
            }
            list.count { item -> hashByChapter[item.chapterId] == item.contentHash && item.plotSummary.isNotBlank() }
        }

    suspend fun getValidSummaryMap(
        bookId: String,
        chapters: List<ChapterEntity>
    ): Map<String, ChapterSummaryEntity> = withContext(Dispatchers.IO) {
        val hashByChapter = chapters.associate {
            it.id to RagVectorCodec.chapterContentHash(it.title, it.content)
        }
        chapterSummaryDao.getByBook(bookId)
            .filter { item -> hashByChapter[item.chapterId] == item.contentHash && item.plotSummary.isNotBlank() }
            .associateBy { it.chapterId }
    }

    /** 保存或追加章节后在后台静默归档该章记忆卡（优先使用快速低成本的伴读模型） */
    fun summarizeChapterInBackground(book: BookEntity, chapter: ChapterEntity) {
        if (chapter.content.isBlank()) return
        appScope.launch(Dispatchers.IO) {
            runCatching {
                val expectedHash = RagVectorCodec.chapterContentHash(chapter.title, chapter.content)
                val existing = chapterSummaryDao.getByChapter(chapter.id)
                if (existing != null && existing.contentHash == expectedHash && existing.plotSummary.isNotBlank()) {
                    return@runCatching
                }
                summarizeChapterInternal(book, chapter, expectedHash)
            }
        }
    }

    /** 批量为尚未建立或已过期的近期章节补全长期剧情记忆卡 */
    suspend fun warmUpMissingSummaries(
        book: BookEntity,
        chapters: List<ChapterEntity>,
        upToChapterIndex: Int? = null,
        maxBatch: Int = 15,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (resolveSummaryTarget() == null) {
            return@withContext Result.failure(IllegalStateException("尚未配置可用的 AI 模型，请先到「设置」配置"))
        }

        val existingMap = chapterSummaryDao.getByBook(book.id).associateBy { it.chapterId }
        val candidates = chapters
            .filter { ch ->
                ch.content.isNotBlank() &&
                    (upToChapterIndex == null || ch.chapterIndex <= upToChapterIndex)
            }
            .sortedByDescending { it.chapterIndex } // 优先补全离当前进度最近的章节
            .filter { ch ->
                val expectedHash = RagVectorCodec.chapterContentHash(ch.title, ch.content)
                val cached = existingMap[ch.id]
                cached == null || cached.contentHash != expectedHash || cached.plotSummary.isBlank()
            }
            .take(maxBatch)

        if (candidates.isEmpty()) return@withContext Result.success(0)

        var succeeded = 0
        candidates.forEachIndexed { index, chapter ->
            onProgress(index, candidates.size)
            val hash = RagVectorCodec.chapterContentHash(chapter.title, chapter.content)
            val ok = summarizeChapterInternal(book, chapter, hash)
            if (ok) succeeded++
        }
        onProgress(candidates.size, candidates.size)
        if (succeeded == 0) {
            Result.failure(IllegalStateException("生成剧情记忆卡失败，请检查当前模型连通性"))
        } else {
            Result.success(succeeded)
        }
    }

    private fun resolveSummaryTarget() =
        aiConfigRepository.resolveActive(TaskScene.COMPANION)
            ?: aiConfigRepository.resolveActive(TaskScene.CONTINUATION)

    private suspend fun summarizeChapterInternal(
        book: BookEntity,
        chapter: ChapterEntity,
        contentHash: String
    ): Boolean {
        val (provider, model) = resolveSummaryTarget() ?: return false
        val effectiveModel = model.ifBlank { provider.selectedModel }
        val effectiveProvider = provider.copy(selectedModel = effectiveModel)

        val messages = PromptBuilder.chapterSummaryMessages(book, chapter)
        val raw = StringBuilder()
        var promptTokens = 0L
        var completionTokens = 0L
        var hasError = false

        try {
            aiClientFactory.clientFor(effectiveProvider.kind)
                .streamChat(
                    config = effectiveProvider,
                    messages = messages,
                    maxTokens = minOf(maxOf(effectiveProvider.maxTokens, 512), 1024),
                    temperature = 0.25f
                )
                .collect { event ->
                    when (event) {
                        is StreamEvent.Delta -> raw.append(event.text)
                        is StreamEvent.Usage -> {
                            promptTokens = maxOf(promptTokens, event.promptTokens)
                            completionTokens = maxOf(completionTokens, event.completionTokens)
                        }
                        is StreamEvent.Error -> hasError = true
                        is StreamEvent.Done -> Unit
                    }
                }
            if (promptTokens > 0 || completionTokens > 0) {
                usageRepository.add(promptTokens, completionTokens)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return false
        }

        val parsed = parseSummaryReply(raw.toString()) ?: return false
        if (hasError && parsed.first.isBlank()) return false

        chapterSummaryDao.upsert(
            ChapterSummaryEntity(
                chapterId = chapter.id,
                bookId = book.id,
                contentHash = contentHash,
                plotSummary = parsed.first,
                characterStates = parsed.second,
                updatedAt = System.currentTimeMillis()
            )
        )
        return true
    }

    companion object {
        internal fun parseSummaryReply(rawText: String): Pair<String, String>? {
            val cleaned = rawText.trim()
            if (cleaned.isBlank()) return null

            val summaryMatch = Regex("【剧情摘要】[：:]\\s*(.+?)(?=\\n【|$)", RegexOption.DOT_MATCHES_ALL)
                .find(cleaned)?.groupValues?.getOrNull(1)?.trim()
            val stateMatch = Regex("【人物与伏笔状态】[：:]\\s*(.+?)(?=\\n【|$)", RegexOption.DOT_MATCHES_ALL)
                .find(cleaned)?.groupValues?.getOrNull(1)?.trim()

            val summary = (summaryMatch ?: cleaned.lineSequence().firstOrNull().orEmpty())
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(260)
            if (summary.isBlank()) return null

            val states = stateMatch.orEmpty()
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(180)
            return summary to states
        }
    }
}

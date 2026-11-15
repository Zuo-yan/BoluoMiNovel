package com.novelai.assistant.data.generation

import android.content.Context
import android.os.Build
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.network.AiClientFactory
import com.novelai.assistant.data.network.ChatMessage
import com.novelai.assistant.data.network.StreamEvent
import com.novelai.assistant.data.rag.RetrievalService
import com.novelai.assistant.data.rag.StoryMemoryService
import com.novelai.assistant.data.repository.AiConfigRepository
import com.novelai.assistant.data.repository.BookRepository
import com.novelai.assistant.data.repository.OutlineRepository
import com.novelai.assistant.data.repository.TaskScene
import com.novelai.assistant.data.repository.UsageRepository
import com.novelai.assistant.domain.ContinuationLengthMode
import com.novelai.assistant.domain.OutlineBeat
import com.novelai.assistant.domain.PromptBuilder
import com.novelai.assistant.domain.WorkshopStyleMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class GenerationTaskStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class BackgroundGenerationState(
    val taskId: String,
    val bookId: String,
    val bookTitle: String,
    val parentChapterId: String,
    val parentChapterTitle: String,
    val modelLabel: String,
    val status: GenerationTaskStatus,
    val text: String = "",
    val customTitle: String = "",
    val instruction: String = "",
    val useWorkshopCraft: Boolean = true,
    val styleMode: WorkshopStyleMode = WorkshopStyleMode.ORIGINAL_MIMIC,
    val lengthMode: ContinuationLengthMode = ContinuationLengthMode.STANDARD_CHAPTER,
    val retrievedTitles: List<String> = emptyList(),
    val retrievedUsedEmbedding: Boolean = false,
    val error: String? = null,
    val continuousCount: Int = 0,
    val savedChapterId: String? = null,
    val activeBeatIndex: Int? = null
) {
    val isRunning: Boolean get() = status == GenerationTaskStatus.RUNNING
    val hasDraft: Boolean get() = text.isNotBlank()
    val isFinishedForEdit: Boolean
        get() = !isRunning && hasDraft && (status == GenerationTaskStatus.COMPLETED || status == GenerationTaskStatus.CANCELLED || status == GenerationTaskStatus.FAILED)

    /** 只要正在运行、已完成等待查看、或保留了非空草稿/错误提示，就在悬浮胶囊中展示 */
    val hasDisplayableState: Boolean
        get() = isRunning || status == GenerationTaskStatus.COMPLETED ||
            (status == GenerationTaskStatus.FAILED && (hasDraft || !error.isNullOrBlank())) ||
            (status == GenerationTaskStatus.CANCELLED && hasDraft)
}

/**
 * 应用级 AI 续写后台任务管理器：
 * 脱离 ContinuationViewModel 生命周期运行，配合 GenerationForegroundService 与灵动悬浮胶囊，
 * 支持用户在生成途中返回阅读、锁屏或切出应用并在结束后无缝恢复草稿。
 */
@Singleton
class GenerationTaskManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val aiConfigRepository: AiConfigRepository,
    private val usageRepository: UsageRepository,
    private val aiClientFactory: AiClientFactory,
    private val retrievalService: RetrievalService,
    private val outlineRepository: OutlineRepository,
    private val storyMemoryService: StoryMemoryService
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<BackgroundGenerationState?>(null)
    val state: StateFlow<BackgroundGenerationState?> = _state.asStateFlow()

    private var activeJob: Job? = null

    fun startGenerate(
        book: BookEntity,
        parent: ChapterEntity,
        instruction: String,
        useWorkshopCraft: Boolean,
        styleMode: WorkshopStyleMode,
        lengthMode: ContinuationLengthMode,
        customTitle: String,
        continuousCount: Int,
        activeOutlineBeat: OutlineBeat? = null
    ): String? = launchGenerationStream(
        book = book,
        parent = parent,
        initialDraft = "",
        instruction = instruction,
        useWorkshopCraft = useWorkshopCraft,
        styleMode = styleMode,
        lengthMode = lengthMode,
        customTitle = customTitle,
        continuousCount = continuousCount,
        activeOutlineBeat = activeOutlineBeat
    )

    fun startContinueGenerating(
        book: BookEntity,
        parent: ChapterEntity,
        currentDraft: String,
        instruction: String,
        useWorkshopCraft: Boolean,
        styleMode: WorkshopStyleMode,
        lengthMode: ContinuationLengthMode,
        customTitle: String,
        continuousCount: Int,
        activeOutlineBeat: OutlineBeat? = null
    ): String? = launchGenerationStream(
        book = book,
        parent = parent,
        initialDraft = currentDraft,
        instruction = instruction,
        useWorkshopCraft = useWorkshopCraft,
        styleMode = styleMode,
        lengthMode = lengthMode,
        customTitle = customTitle,
        continuousCount = continuousCount,
        activeOutlineBeat = activeOutlineBeat
    )

    private fun launchGenerationStream(
        book: BookEntity,
        parent: ChapterEntity,
        initialDraft: String,
        instruction: String,
        useWorkshopCraft: Boolean,
        styleMode: WorkshopStyleMode,
        lengthMode: ContinuationLengthMode,
        customTitle: String,
        continuousCount: Int,
        activeOutlineBeat: OutlineBeat? = null
    ): String? {
        val active = aiConfigRepository.resolveActive(TaskScene.CONTINUATION)
            ?: return "尚未配置「续写场景」模型，请到设置页配置"
        val (provider, model) = active
        val effectiveModel = model.ifBlank { provider.selectedModel }
        val effectiveProvider = provider.copy(selectedModel = effectiveModel)
        val modelLabel = "${provider.providerName} · ${effectiveModel.ifBlank { "未命名模型" }}"
        val trimmedInstruction = instruction.trim()
        val taskId = UUID.randomUUID().toString()

        activeJob?.cancel()
        GenerationForegroundService.cancelCompletionNotification(context)

        _state.value = BackgroundGenerationState(
            taskId = taskId,
            bookId = book.id,
            bookTitle = book.title,
            parentChapterId = parent.id,
            parentChapterTitle = parent.title,
            modelLabel = modelLabel,
            status = GenerationTaskStatus.RUNNING,
            text = initialDraft,
            customTitle = customTitle,
            instruction = instruction,
            useWorkshopCraft = useWorkshopCraft,
            styleMode = styleMode,
            lengthMode = lengthMode,
            continuousCount = continuousCount,
            activeBeatIndex = activeOutlineBeat?.index
        )
        startForegroundServiceSafe()

        activeJob = appScope.launch {
            val builder = StringBuilder(initialDraft)
            val initialLength = builder.length
            var encounteredError: String? = null
            var wasCancelled = false

            try {
                val loadedParent = ensureReadableParent(parent) ?: run {
                    encounteredError = "本章尚无正文，请先在阅读器打开或下载后再续写"
                    return@launch
                }
                if (loadedParent.title != parent.title) {
                    _state.update { cur ->
                        if (cur?.taskId == taskId) cur.copy(parentChapterTitle = loadedParent.title) else cur
                    }
                }

                val lineage = bookRepository.getChapterLineage(book.id, loadedParent.id)
                    .filter { it.content.isNotBlank() }
                val previous = if (lineage.isNotEmpty()) {
                    lineage
                } else {
                    bookRepository.getChapters(book.id)
                        .filter { it.chapterIndex <= loadedParent.chapterIndex && it.content.isNotBlank() }
                }

                val outline = runCatching { outlineRepository.getOutline(book.id) }.getOrNull()
                val chapterSummaries = runCatching {
                    storyMemoryService.getValidSummaryMap(book.id, previous)
                }.getOrDefault(emptyMap())
                val beatCue = activeOutlineBeat?.toContinuationInstruction().orEmpty()
                val planCue = outline?.takeIf { it.useInContinuation }?.upcomingPlan.orEmpty()
                val outlineCue = listOf(beatCue, planCue).filter { it.isNotBlank() }.joinToString("\n")

                val retrievedSnippets = retrieveContextSnippets(
                    bookId = book.id,
                    loadedParent = loadedParent,
                    previous = previous,
                    instructionText = trimmedInstruction,
                    outlineCue = outlineCue,
                    draftTail = initialDraft
                )
                _state.update { cur ->
                    if (cur?.taskId == taskId) {
                        cur.copy(
                            retrievedTitles = retrievedSnippets.map { it.chapter.title },
                            retrievedUsedEmbedding = retrievedSnippets.any { it.usedEmbedding }
                        )
                    } else {
                        cur
                    }
                }

                val targetTitle = customTitle.trim()
                val messages: List<ChatMessage> = if (initialDraft.isNotEmpty()) {
                    PromptBuilder.continueGeneratingMessages(
                        book = book,
                        parentChapter = loadedParent,
                        previousChapters = previous,
                        currentDraft = initialDraft,
                        instruction = trimmedInstruction,
                        useWorkshopCraft = useWorkshopCraft,
                        styleMode = styleMode,
                        lengthMode = lengthMode,
                        retrievedSnippets = retrievedSnippets,
                        outline = outline,
                        chapterSummaries = chapterSummaries,
                        targetChapterTitle = targetTitle,
                        activeOutlineBeat = activeOutlineBeat
                    )
                } else {
                    PromptBuilder.continuationMessages(
                        book = book,
                        previousChapters = previous,
                        instruction = trimmedInstruction,
                        useWorkshopCraft = useWorkshopCraft,
                        styleMode = styleMode,
                        lengthMode = lengthMode,
                        retrievedSnippets = retrievedSnippets,
                        outline = outline,
                        chapterSummaries = chapterSummaries,
                        targetChapterTitle = targetTitle,
                        activeOutlineBeat = activeOutlineBeat
                    )
                }

                var promptTokens = 0L
                var completionTokens = 0L
                val requestMaxTokens = maxOf(effectiveProvider.maxTokens, lengthMode.minMaxTokens)

                aiClientFactory.clientFor(effectiveProvider.kind)
                    .streamChat(effectiveProvider, messages, requestMaxTokens, effectiveProvider.temperature)
                    .collect { event ->
                        when (event) {
                            is StreamEvent.Delta -> {
                                builder.append(event.text)
                                _state.update { cur ->
                                    if (cur?.taskId == taskId) cur.copy(text = builder.toString()) else cur
                                }
                            }
                            is StreamEvent.Usage -> {
                                promptTokens = maxOf(promptTokens, event.promptTokens)
                                completionTokens = maxOf(completionTokens, event.completionTokens)
                            }
                            is StreamEvent.Error -> encounteredError = event.message
                            is StreamEvent.Done -> Unit
                        }
                    }
                usageRepository.add(promptTokens, completionTokens)
            } catch (e: CancellationException) {
                wasCancelled = true
            } catch (t: Throwable) {
                val prefix = if (initialDraft.isNotEmpty()) "续接中断" else "生成中断"
                encounteredError = "$prefix：${t.message ?: t.javaClass.simpleName}"
            } finally {
                if (!wasCancelled && builder.length == initialLength && encounteredError == null) {
                    encounteredError = if (initialDraft.isNotEmpty()) "未收到新增内容，请重试" else "未收到任何内容，请重试"
                }
                _state.update { cur ->
                    if (cur?.taskId != taskId) return@update cur
                    val nextStatus = when {
                        encounteredError != null -> GenerationTaskStatus.FAILED
                        wasCancelled -> GenerationTaskStatus.CANCELLED
                        else -> GenerationTaskStatus.COMPLETED
                    }
                    if (nextStatus == GenerationTaskStatus.CANCELLED && builder.isBlank()) {
                        null
                    } else {
                        cur.copy(
                            text = builder.toString(),
                            status = nextStatus,
                            error = encounteredError
                        )
                    }
                }
            }
        }
        return null
    }

    private suspend fun ensureReadableParent(parent: ChapterEntity): ChapterEntity? {
        if (parent.content.isNotBlank()) return parent
        val fetched = runCatching { bookRepository.readableChapter(parent.id) }.getOrNull()
        return fetched?.takeIf { it.content.isNotBlank() }
    }

    private suspend fun retrieveContextSnippets(
        bookId: String,
        loadedParent: ChapterEntity,
        previous: List<ChapterEntity>,
        instructionText: String,
        outlineCue: String,
        draftTail: String
    ) = runCatching {
        val query = buildString {
            if (instructionText.isNotBlank()) append(instructionText).append('\n')
            if (outlineCue.isNotBlank()) append(outlineCue.take(300)).append('\n')
            previous.dropLast(1).lastOrNull()?.let { prev2 ->
                append(prev2.title).append('\n').append(prev2.content.takeLast(300)).append('\n')
            }
            append(loadedParent.title).append('\n')
            append(loadedParent.content.take(250)).append('\n')
            append(loadedParent.content.takeLast(600))
            if (draftTail.isNotBlank()) {
                append('\n').append(draftTail.takeLast(400))
            }
        }.trim()
        if (query.isBlank()) return@runCatching emptyList()
        val recentIds = previous.takeLast(3).mapTo(mutableSetOf()) { it.id }.apply {
            add(loadedParent.id)
        }
        retrievalService.retrieveSnippets(
            bookId = bookId,
            query = query,
            topK = 6,
            spoilerFree = true,
            currentChapterIndex = loadedParent.chapterIndex,
            excludeChapterIds = recentIds
        )
    }.getOrDefault(emptyList())

    fun stop() {
        val cur = _state.value ?: return
        activeJob?.cancel()
        if (!cur.hasDraft) {
            _state.value = null
            GenerationForegroundService.stopService(context)
        } else {
            _state.update { state ->
                if (state?.taskId == cur.taskId && state.isRunning) {
                    state.copy(status = GenerationTaskStatus.CANCELLED)
                } else {
                    state
                }
            }
        }
    }

    fun dismiss(bookId: String? = null) {
        val cur = _state.value
        if (cur != null && (bookId == null || cur.bookId == bookId)) {
            activeJob?.cancel()
            _state.value = null
            GenerationForegroundService.stopService(context)
            GenerationForegroundService.cancelCompletionNotification(context)
        }
    }

    fun clearCompletionNotificationIfShowing() {
        GenerationForegroundService.cancelCompletionNotification(context)
    }

    fun updateDraftText(bookId: String, newText: String) {
        _state.update { cur ->
            if (cur?.bookId == bookId && !cur.isRunning) cur.copy(text = newText) else cur
        }
    }

    fun updateInstruction(bookId: String, instruction: String) {
        _state.update { cur ->
            if (cur?.bookId == bookId) cur.copy(instruction = instruction) else cur
        }
    }

    fun updateCustomTitle(bookId: String, customTitle: String) {
        _state.update { cur ->
            if (cur?.bookId == bookId) cur.copy(customTitle = customTitle) else cur
        }
    }

    fun updateActiveBeatIndex(bookId: String, activeBeatIndex: Int?) {
        _state.update { cur ->
            if (cur?.bookId == bookId) cur.copy(activeBeatIndex = activeBeatIndex) else cur
        }
    }

    fun updateCraftSettings(
        bookId: String,
        useCraft: Boolean,
        styleMode: WorkshopStyleMode,
        lengthMode: ContinuationLengthMode
    ) {
        _state.update { cur ->
            if (cur?.bookId == bookId) {
                cur.copy(useWorkshopCraft = useCraft, styleMode = styleMode, lengthMode = lengthMode)
            } else {
                cur
            }
        }
    }

    fun markSaved(bookId: String, savedChapterId: String? = null) {
        val cur = _state.value ?: return
        if (cur.bookId == bookId) {
            activeJob?.cancel()
            _state.value = null
            GenerationForegroundService.stopService(context)
            GenerationForegroundService.cancelCompletionNotification(context)
        }
    }

    private fun startForegroundServiceSafe() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                context.startForegroundService(GenerationForegroundService.startIntent(context))
            }
        } else {
            runCatching {
                context.startService(GenerationForegroundService.startIntent(context))
            }
        }
    }
}

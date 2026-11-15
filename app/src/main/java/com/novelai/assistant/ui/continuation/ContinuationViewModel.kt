package com.novelai.assistant.ui.continuation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.BookOutlineEntity
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.db.ChapterOriginType
import com.novelai.assistant.data.db.OutlineDiscussionEntity
import com.novelai.assistant.data.generation.BackgroundGenerationState
import com.novelai.assistant.data.generation.GenerationTaskManager
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
import com.novelai.assistant.domain.OutlineBeatCodec
import com.novelai.assistant.domain.PlotDiscussionCodec
import com.novelai.assistant.domain.PromptBuilder
import com.novelai.assistant.domain.WorkshopStyleMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class AnchorChapterMode {
    LATEST,
    CURRENT,
    CUSTOM
}

data class PendingOutlineContinuationProposal(
    val nextBeat: OutlineBeat?,
    val masterSummaryPreview: String,
    val suggestedTitle: String,
    val suggestedInstruction: String,
    val totalBeatCount: Int,
    val remainingBeatCount: Int,
    val fromDiscussion: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContinuationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val aiConfigRepository: AiConfigRepository,
    private val retrievalService: RetrievalService,
    private val generationTaskManager: GenerationTaskManager,
    private val outlineRepository: OutlineRepository,
    private val storyMemoryService: StoryMemoryService,
    private val aiClientFactory: AiClientFactory,
    private val usageRepository: UsageRepository
) : ViewModel() {

    val bookId: String = savedStateHandle["bookId"] ?: ""
    private val initialNavChapterId: String = savedStateHandle["chapterId"] ?: ""

    /** 基准章节（可切换）：续写以其为衔接点，新章将紧跟在其后面 */
    private val _parentChapterId = MutableStateFlow(initialNavChapterId)
    val parentChapterId: String get() = _parentChapterId.value

    private val _book = MutableStateFlow<BookEntity?>(null)
    val book: StateFlow<BookEntity?> = _book.asStateFlow()

    val activeModelLabel: StateFlow<String> = combine(
        aiConfigRepository.providers,
        aiConfigRepository.continuationRoute
    ) { _, _ -> formatActiveModelLabel(TaskScene.CONTINUATION) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = formatActiveModelLabel(TaskScene.CONTINUATION)
        )

    val outlineModelLabel: StateFlow<String> = combine(
        aiConfigRepository.providers,
        aiConfigRepository.outlineRoute,
        aiConfigRepository.continuationRoute
    ) { _, _, _ -> formatActiveModelLabel(TaskScene.OUTLINE) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = formatActiveModelLabel(TaskScene.OUTLINE)
        )

    private fun formatActiveModelLabel(scene: TaskScene): String =
        aiConfigRepository.resolveActive(scene)?.let { (provider, model) ->
            val modelName = model.ifBlank { provider.selectedModel }.ifBlank { "未选择模型" }
            "${provider.providerName} · $modelName"
        } ?: "未配置模型"

    private val _parentChapter = MutableStateFlow<ChapterEntity?>(null)
    val parentChapter: StateFlow<ChapterEntity?> = _parentChapter.asStateFlow()

    private val _allChapters = MutableStateFlow<List<ChapterEntity>>(emptyList())
    val allChapters: StateFlow<List<ChapterEntity>> = _allChapters.asStateFlow()

    val outline: StateFlow<BookOutlineEntity?> = outlineRepository.observeOutline(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val discussionMessages: StateFlow<List<OutlineDiscussionEntity>> =
        outlineRepository.observeDiscussion(bookId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val validSummaryCount: StateFlow<Int> = _allChapters
        .flatMapLatest { chapters -> storyMemoryService.observeValidSummaryCount(bookId, chapters) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val persistedVectorCount: StateFlow<Int> = retrievalService.observePersistedVectorCount(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _discussionStreamingText = MutableStateFlow("")
    val discussionStreamingText: StateFlow<String> = _discussionStreamingText.asStateFlow()

    private val _discussionGenerating = MutableStateFlow(false)
    val discussionGenerating: StateFlow<Boolean> = _discussionGenerating.asStateFlow()

    private val _discussionError = MutableStateFlow<String?>(null)
    val discussionError: StateFlow<String?> = _discussionError.asStateFlow()

    private val _isWarmingSummaries = MutableStateFlow(false)
    val isWarmingSummaries: StateFlow<Boolean> = _isWarmingSummaries.asStateFlow()

    private val _memoryStatusMessage = MutableStateFlow<String?>(null)
    val memoryStatusMessage: StateFlow<String?> = _memoryStatusMessage.asStateFlow()

    private var discussionJob: Job? = null
    private var titleSuggestionJob: Job? = null

    /** 用户自定义章节标题（保存时用，空则自动命名） */
    val customTitle = MutableStateFlow("")

    val outlineBeats: StateFlow<List<OutlineBeat>> = combine(outline, _allChapters) { currentOutline, chapters ->
        val plan = currentOutline?.upcomingPlan.orEmpty()
        if (plan.isBlank()) {
            emptyList()
        } else {
            val existingTitles = chapters.mapNotNull { it.title.takeIf(String::isNotBlank) }.toSet()
            OutlineBeatCodec.parseBeats(plan, existingTitles)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedBeatIndex = MutableStateFlow<Int?>(null)
    val selectedBeatIndex: StateFlow<Int?> = _selectedBeatIndex.asStateFlow()

    private val _beatSelectionCleared = MutableStateFlow(false)

    val activeOutlineBeat: StateFlow<OutlineBeat?> = combine(
        outlineBeats,
        _selectedBeatIndex,
        _beatSelectionCleared
    ) { beats, selectedIdx, cleared ->
        if (beats.isEmpty() || cleared) {
            null
        } else {
            val matched = selectedIdx?.let { idx -> beats.firstOrNull { it.index == idx } }
            matched ?: beats.firstOrNull { !it.isCompleted }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val nextBeatAfterCurrent: StateFlow<OutlineBeat?> = combine(
        outlineBeats,
        activeOutlineBeat
    ) { beats, active ->
        if (active == null) {
            null
        } else {
            beats.firstOrNull { !it.isCompleted && it.index > active.index }
                ?: beats.firstOrNull { !it.isCompleted && it.index != active.index }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val remainingUnwrittenBeatsCount: StateFlow<Int> = outlineBeats
        .map { beats -> beats.count { !it.isCompleted } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _aiSuggestedTitles = MutableStateFlow<List<String>>(emptyList())

    private val _suggestingTitles = MutableStateFlow(false)
    val suggestingTitles: StateFlow<Boolean> = _suggestingTitles.asStateFlow()

    private val _titleSuggestionError = MutableStateFlow<String?>(null)
    val titleSuggestionError: StateFlow<String?> = _titleSuggestionError.asStateFlow()

    private val _pendingOutlineProposal = MutableStateFlow<PendingOutlineContinuationProposal?>(null)
    val pendingOutlineProposal: StateFlow<PendingOutlineContinuationProposal?> = _pendingOutlineProposal.asStateFlow()

    /** 基准章节来源模式：全书最新 / 当前阅读章 / 手动指定 */
    private val _anchorMode = MutableStateFlow(AnchorChapterMode.LATEST)
    val anchorMode: StateFlow<AnchorChapterMode> = _anchorMode.asStateFlow()

    val useLatestChapter: StateFlow<Boolean> = _anchorMode
        .map { it == AnchorChapterMode.LATEST }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _latestChapter = MutableStateFlow<ChapterEntity?>(null)
    val latestChapter: StateFlow<ChapterEntity?> = _latestChapter.asStateFlow()

    private val _currentReadingChapter = MutableStateFlow<ChapterEntity?>(null)
    val currentReadingChapter: StateFlow<ChapterEntity?> = _currentReadingChapter.asStateFlow()

    fun selectParentChapter(chapter: ChapterEntity) {
        _anchorMode.value = AnchorChapterMode.CUSTOM
        _parentChapterId.value = chapter.id
        _parentChapter.value = chapter
    }

    val instruction = MutableStateFlow("")

    /** 小说工坊技法（去AI味/章末钩子/潜台词对话） */
    private val _useWorkshopCraft = MutableStateFlow(true)
    val useWorkshopCraft: StateFlow<Boolean> = _useWorkshopCraft.asStateFlow()

    fun toggleWorkshopCraft() {
        _useWorkshopCraft.value = !_useWorkshopCraft.value
        syncCraftToTask()
    }

    private val _styleMode = MutableStateFlow(WorkshopStyleMode.ORIGINAL_MIMIC)
    val styleMode: StateFlow<WorkshopStyleMode> = _styleMode.asStateFlow()

    private val _lengthMode = MutableStateFlow(ContinuationLengthMode.STANDARD_CHAPTER)
    val lengthMode: StateFlow<ContinuationLengthMode> = _lengthMode.asStateFlow()

    fun selectStyleMode(mode: WorkshopStyleMode) {
        _styleMode.value = mode
        syncCraftToTask()
    }

    fun selectLengthMode(mode: ContinuationLengthMode) {
        _lengthMode.value = mode
        syncCraftToTask()
    }

    private fun syncCraftToTask() {
        generationTaskManager.updateCraftSettings(
            bookId = bookId,
            useCraft = _useWorkshopCraft.value,
            styleMode = _styleMode.value,
            lengthMode = _lengthMode.value
        )
    }

    private val matchingTask: StateFlow<BackgroundGenerationState?> = generationTaskManager.state
        .map { task -> if (task?.bookId == bookId) task else null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = generationTaskManager.state.value?.takeIf { it.bookId == bookId }
        )

    val retrievedChapterTitles: StateFlow<List<String>> = matchingTask
        .map { it?.retrievedTitles.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, matchingTask.value?.retrievedTitles.orEmpty())

    val retrievedUsedEmbedding: StateFlow<Boolean> = matchingTask
        .map { it?.retrievedUsedEmbedding == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, matchingTask.value?.retrievedUsedEmbedding == true)

    fun updateGeneratedText(newText: String) {
        generationTaskManager.updateDraftText(bookId, newText)
    }

    val streaming: StateFlow<Boolean> = matchingTask
        .map { it?.isRunning == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, matchingTask.value?.isRunning == true)

    val streamingText: StateFlow<String> = matchingTask
        .map { it?.text.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, matchingTask.value?.text.orEmpty())

    val recommendedTitles: StateFlow<List<String>> = combine(
        outlineBeats,
        activeOutlineBeat,
        _aiSuggestedTitles,
        _parentChapter,
        streamingText
    ) { beats, activeBeat, aiTitles, parent, draft ->
        val fallbackTitles = OutlineBeatCodec.deriveFallbackTitles(
            draftText = draft,
            activeBeat = activeBeat,
            parentTitle = parent?.title
        )
        val unwrittenBeatTitles = beats.filter { !it.isCompleted }.map { it.chapterTitle }
        buildList {
            activeBeat?.chapterTitle?.takeIf { it.isNotBlank() }?.let(::add)
            addAll(aiTitles)
            addAll(unwrittenBeatTitles)
            addAll(fallbackTitles)
        }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(6)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val finished: StateFlow<Boolean> = matchingTask
        .map { it?.isFinishedForEdit == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, matchingTask.value?.isFinishedForEdit == true)

    private val _localError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = combine(_localError, matchingTask) { localErr, task ->
        localErr ?: task?.error
    }.stateIn(viewModelScope, SharingStarted.Eagerly, matchingTask.value?.error)

    val continuousCount = MutableStateFlow(0)
    private val _savedChapterId = MutableStateFlow<String?>(null)
    val savedChapterId: StateFlow<String?> = _savedChapterId.asStateFlow()

    init {
        generationTaskManager.clearCompletionNotificationIfShowing()

        // 将输入框编辑实时回写到后台任务，避免切换页面后指令/标题丢失
        viewModelScope.launch {
            instruction.collect { generationTaskManager.updateInstruction(bookId, it) }
        }
        viewModelScope.launch {
            customTitle.collect { generationTaskManager.updateCustomTitle(bookId, it) }
        }

        viewModelScope.launch {
            val loadedBook = bookRepository.getBook(bookId)
            _book.value = loadedBook
            val chapters = bookRepository.getChapters(bookId)
            _allChapters.value = chapters
            val latest = chapters.maxByOrNull { it.chapterIndex }
            _latestChapter.value = latest
            val readingCh = chapters.firstOrNull { it.id == initialNavChapterId }
                ?: loadedBook?.let { b ->
                    chapters.getOrNull(b.currentReadingChapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)))
                }
                ?: latest
            _currentReadingChapter.value = readingCh

            val loadedOutline = runCatching { outlineRepository.getOutline(bookId) }.getOrNull()
            val existingTitles = chapters.mapNotNull { it.title.takeIf(String::isNotBlank) }.toSet()
            val initialBeats = if (loadedOutline != null && loadedOutline.upcomingPlan.isNotBlank()) {
                OutlineBeatCodec.parseBeats(loadedOutline.upcomingPlan, existingTitles)
            } else {
                emptyList()
            }

            val activeTask = generationTaskManager.state.value?.takeIf {
                it.bookId == bookId && it.hasDisplayableState
            }
            if (activeTask != null) {
                // 从后台悬浮胶囊或通知恢复现场
                instruction.value = activeTask.instruction
                customTitle.value = activeTask.customTitle
                _useWorkshopCraft.value = activeTask.useWorkshopCraft
                _styleMode.value = activeTask.styleMode
                _lengthMode.value = activeTask.lengthMode
                continuousCount.value = activeTask.continuousCount
                val restoredBeatIdx = activeTask.activeBeatIndex
                if (restoredBeatIdx != null && initialBeats.any { it.index == restoredBeatIdx }) {
                    _beatSelectionCleared.value = false
                    _selectedBeatIndex.value = restoredBeatIdx
                } else if (!_beatSelectionCleared.value) {
                    _selectedBeatIndex.value = initialBeats.firstOrNull { !it.isCompleted }?.index
                }

                val taskParent = chapters.firstOrNull { it.id == activeTask.parentChapterId }
                if (taskParent != null) {
                    _parentChapterId.value = taskParent.id
                    _parentChapter.value = taskParent
                    _anchorMode.value = when {
                        taskParent.id == readingCh?.id && readingCh.id != latest?.id -> AnchorChapterMode.CURRENT
                        taskParent.id == latest?.id -> AnchorChapterMode.LATEST
                        else -> AnchorChapterMode.CUSTOM
                    }
                    return@launch
                }
            }

            if (initialBeats.isNotEmpty() && _selectedBeatIndex.value == null && !_beatSelectionCleared.value) {
                initialBeats.firstOrNull { !it.isCompleted }?.let { firstUnwritten ->
                    _selectedBeatIndex.value = firstUnwritten.index
                }
            }

            // 默认：若从阅读页指定了非末章，则锁定该「当前章节」；否则直接追踪全书末章
            val specified = chapters.firstOrNull { it.id == _parentChapterId.value }
            if (specified != null && latest != null && specified.id != latest.id && initialNavChapterId.isNotBlank()) {
                _anchorMode.value = AnchorChapterMode.CURRENT
                _parentChapter.value = specified
            } else if (latest != null) {
                _anchorMode.value = AnchorChapterMode.LATEST
                _parentChapter.value = latest
                _parentChapterId.value = latest.id
            } else {
                _parentChapter.value = specified
            }
        }
    }

    fun setUseLatestChapter(useLatest: Boolean) {
        if (useLatest) {
            selectLatestChapter()
        } else {
            _anchorMode.value = AnchorChapterMode.CUSTOM
        }
    }

    fun selectLatestChapter() {
        _anchorMode.value = AnchorChapterMode.LATEST
        val latest = _allChapters.value.maxByOrNull { it.chapterIndex }
        if (latest != null) {
            _parentChapter.value = latest
            _parentChapterId.value = latest.id
        }
    }

    fun selectCurrentReadingChapter() {
        val current = _currentReadingChapter.value ?: _allChapters.value.maxByOrNull { it.chapterIndex }
        _anchorMode.value = AnchorChapterMode.CURRENT
        if (current != null) {
            _parentChapter.value = current
            _parentChapterId.value = current.id
        }
    }

    fun stop() {
        generationTaskManager.stop()
    }

    fun reset() {
        _localError.value = null
        generationTaskManager.dismiss(bookId)
    }

    fun generate() {
        generateInternal(overrideActiveBeat = null)
    }

    private fun generateInternal(overrideActiveBeat: OutlineBeat?) {
        val book = _book.value ?: run { _localError.value = "书籍未加载"; return }
        val parent = _parentChapter.value ?: run { _localError.value = "章节未加载"; return }
        _localError.value = null
        val targetBeat = overrideActiveBeat ?: activeOutlineBeat.value?.takeIf {
            outline.value?.useInContinuation != false
        }
        val err = generationTaskManager.startGenerate(
            book = book,
            parent = parent,
            instruction = instruction.value,
            useWorkshopCraft = _useWorkshopCraft.value,
            styleMode = _styleMode.value,
            lengthMode = _lengthMode.value,
            customTitle = customTitle.value,
            continuousCount = continuousCount.value,
            activeOutlineBeat = targetBeat
        )
        if (err != null) _localError.value = err
    }

    /** 基于已生成的未完草稿继续往下无缝续写 */
    fun continueGenerating() {
        val currentDraft = streamingText.value
        if (currentDraft.isBlank()) {
            generate()
            return
        }
        val book = _book.value ?: run { _localError.value = "书籍未加载"; return }
        val parent = _parentChapter.value ?: run { _localError.value = "章节未加载"; return }
        _localError.value = null
        val targetBeat = activeOutlineBeat.value?.takeIf {
            outline.value?.useInContinuation != false
        }
        val err = generationTaskManager.startContinueGenerating(
            book = book,
            parent = parent,
            currentDraft = currentDraft,
            instruction = instruction.value,
            useWorkshopCraft = _useWorkshopCraft.value,
            styleMode = _styleMode.value,
            lengthMode = _lengthMode.value,
            customTitle = customTitle.value,
            continuousCount = continuousCount.value,
            activeOutlineBeat = targetBeat
        )
        if (err != null) _localError.value = err
    }

    /** 把已生成内容追加到父章节末尾（而不是另存新章） */
    fun appendToParentChapter(onDone: () -> Unit) {
        val text = streamingText.value.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            bookRepository.appendToChapter(parentChapterId, "\n\n$text")
            retrievalService.evictChapter(bookId, parentChapterId)
            val book = _book.value ?: bookRepository.getBook(bookId)
            val updatedChapter = bookRepository.getChapter(parentChapterId)
            if (book != null && updatedChapter != null) {
                storyMemoryService.summarizeChapterInBackground(book, updatedChapter)
            }
            generationTaskManager.markSaved(bookId, parentChapterId)
            onDone()
        }
    }

    private suspend fun persistGeneratedChapter(text: String, defaultTitle: String): ChapterEntity {
        val title = customTitle.value.trim().ifBlank { defaultTitle }
        val chapter = bookRepository.saveGeneratedChapter(
            bookId = bookId,
            parentChapterId = _parentChapterId.value.ifBlank { null },
            title = title,
            content = text,
            promptUsed = instruction.value.trim().ifBlank { null },
            originType = ChapterOriginType.AI_CONTINUATION
        )
        retrievalService.evictChapter(bookId, chapter.id)
        val book = _book.value ?: bookRepository.getBook(bookId)
        if (book != null) {
            storyMemoryService.summarizeChapterInBackground(book, chapter)
        }
        _savedChapterId.value = chapter.id
        generationTaskManager.markSaved(bookId, chapter.id)
        return chapter
    }

    /** 保存当前章节并自动将新章设为基准章节，支持连续无限续写 */
    fun saveAndContinue(autoGenerateNext: Boolean = false) {
        val text = streamingText.value.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            val boundBeat = activeOutlineBeat.value
            val chaptersBeforeSave = _allChapters.value
            val baseLabel = _parentChapter.value?.title?.take(12) ?: "新章"
            val sectionSuffix = if (continuousCount.value > 0) " · ${continuousCount.value + 1}" else ""
            val defaultTitle = "AI 续写 · $baseLabel$sectionSuffix"
            val chapter = persistGeneratedChapter(text, defaultTitle)
            continuousCount.value += 1

            _allChapters.value = bookRepository.getChapters(bookId)
            _book.value = bookRepository.getBook(bookId)
            val newLatest = _allChapters.value.maxByOrNull { it.chapterIndex }
            _latestChapter.value = newLatest
            _currentReadingChapter.value = _allChapters.value.firstOrNull {
                it.id == _currentReadingChapter.value?.id
            } ?: newLatest

            if (_anchorMode.value == AnchorChapterMode.LATEST && newLatest != null) {
                _parentChapterId.value = newLatest.id
                _parentChapter.value = newLatest
            } else {
                _anchorMode.value = AnchorChapterMode.CUSTOM
                _parentChapterId.value = chapter.id
                _parentChapter.value = chapter
            }

            _aiSuggestedTitles.value = emptyList()
            _titleSuggestionError.value = null
            _localError.value = null

            val advancedViaOutline = markOutlineBeatAfterSave(
                chaptersBeforeSave = chaptersBeforeSave,
                boundBeat = boundBeat,
                advanceSelectionToNext = true
            )
            if (!advancedViaOutline) {
                customTitle.value = ""
            }

            if (autoGenerateNext) {
                generate()
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        val text = streamingText.value.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            val boundBeat = activeOutlineBeat.value
            val chaptersBeforeSave = _allChapters.value
            val defaultTitle = "AI 续写 · ${_parentChapter.value?.title?.take(12) ?: "新章"}"
            persistGeneratedChapter(text, defaultTitle)
            _allChapters.value = bookRepository.getChapters(bookId)
            _book.value = bookRepository.getBook(bookId)
            markOutlineBeatAfterSave(
                chaptersBeforeSave = chaptersBeforeSave,
                boundBeat = boundBeat,
                advanceSelectionToNext = false
            )
            onSaved()
        }
    }

    private suspend fun markOutlineBeatAfterSave(
        chaptersBeforeSave: List<ChapterEntity>,
        boundBeat: OutlineBeat?,
        advanceSelectionToNext: Boolean
    ): Boolean {
        val currentOutline = outline.value ?: outlineRepository.getOutline(bookId) ?: return false
        if (currentOutline.upcomingPlan.isBlank()) return false

        val existingBeforeSave = chaptersBeforeSave.mapNotNull { it.title.takeIf(String::isNotBlank) }.toSet()
        val beatsBeforeSave = OutlineBeatCodec.parseBeats(currentOutline.upcomingPlan, existingBeforeSave)
        if (beatsBeforeSave.isEmpty()) return false

        val existingNow = _allChapters.value.mapNotNull { it.title.takeIf(String::isNotBlank) }.toSet()
        val beatsAfterSave = OutlineBeatCodec.parseBeats(currentOutline.upcomingPlan, existingNow)

        val targetBeatIndex = when {
            boundBeat != null && beatsBeforeSave.any { it.index == boundBeat.index } -> boundBeat.index
            else -> beatsBeforeSave.firstOrNull { before ->
                !before.isCompleted && beatsAfterSave.getOrNull(before.index)?.isCompleted == true
            }?.index
        }

        val updatedPlan = if (targetBeatIndex != null) {
            OutlineBeatCodec.markBeatCompleted(
                upcomingPlan = currentOutline.upcomingPlan,
                beatIndex = targetBeatIndex,
                completed = true,
                existingChapterTitles = existingNow
            )
        } else {
            currentOutline.upcomingPlan
        }
        if (updatedPlan != currentOutline.upcomingPlan) {
            outlineRepository.saveOutline(
                bookId = bookId,
                masterOutline = currentOutline.masterOutline,
                upcomingPlan = updatedPlan,
                useInContinuation = currentOutline.useInContinuation
            )
        }

        if (!advanceSelectionToNext) return true

        val refreshedBeats = OutlineBeatCodec.parseBeats(updatedPlan, existingNow)
        val nextBeat = if (targetBeatIndex != null) {
            refreshedBeats.firstOrNull { !it.isCompleted && it.index > targetBeatIndex }
                ?: refreshedBeats.firstOrNull { !it.isCompleted }
        } else {
            refreshedBeats.firstOrNull { !it.isCompleted }
        }

        if (nextBeat != null) {
            _beatSelectionCleared.value = false
            _selectedBeatIndex.value = nextBeat.index
            customTitle.value = nextBeat.chapterTitle
            instruction.value = nextBeat.toContinuationInstruction()
            generationTaskManager.updateActiveBeatIndex(bookId, nextBeat.index)
        } else {
            _selectedBeatIndex.value = null
            customTitle.value = ""
            generationTaskManager.updateActiveBeatIndex(bookId, null)
            _memoryStatusMessage.value = "本轮分章细纲已全部写完，可点击「+ 续编后续细纲」继续规划"
        }
        return true
    }

    fun selectOutlineBeat(beat: OutlineBeat) {
        _beatSelectionCleared.value = false
        _selectedBeatIndex.value = beat.index
        customTitle.value = beat.chapterTitle
        instruction.value = beat.toContinuationInstruction()
        generationTaskManager.updateActiveBeatIndex(bookId, beat.index)
        if (outline.value?.useInContinuation == false) {
            viewModelScope.launch {
                outlineRepository.setUseInContinuation(bookId, true)
            }
        }
    }

    fun clearSelectedBeat() {
        _beatSelectionCleared.value = true
        _selectedBeatIndex.value = null
        generationTaskManager.updateActiveBeatIndex(bookId, null)
    }

    fun applyRecommendedTitle(title: String) {
        customTitle.value = title.trim()
    }

    fun toggleBeatCompleted(beat: OutlineBeat) {
        viewModelScope.launch {
            val current = outline.value ?: outlineRepository.getOutline(bookId) ?: return@launch
            val existingTitles = _allChapters.value.mapNotNull { it.title.takeIf(String::isNotBlank) }.toSet()
            val newCompleted = !beat.isCompleted
            val updatedPlan = OutlineBeatCodec.markBeatCompleted(
                upcomingPlan = current.upcomingPlan,
                beatIndex = beat.index,
                completed = newCompleted,
                existingChapterTitles = existingTitles
            )
            outlineRepository.saveOutline(
                bookId = bookId,
                masterOutline = current.masterOutline,
                upcomingPlan = updatedPlan,
                useInContinuation = current.useInContinuation
            )
            val refreshedBeats = OutlineBeatCodec.parseBeats(updatedPlan, existingTitles)
            val currentActive = activeOutlineBeat.value
            if (newCompleted && (currentActive?.index == beat.index || _selectedBeatIndex.value == beat.index)) {
                val nextUnwritten = refreshedBeats.firstOrNull { !it.isCompleted && it.index > beat.index }
                    ?: refreshedBeats.firstOrNull { !it.isCompleted }
                if (nextUnwritten != null) {
                    _beatSelectionCleared.value = false
                    _selectedBeatIndex.value = nextUnwritten.index
                    customTitle.value = nextUnwritten.chapterTitle
                    instruction.value = nextUnwritten.toContinuationInstruction()
                    generationTaskManager.updateActiveBeatIndex(bookId, nextUnwritten.index)
                } else {
                    _selectedBeatIndex.value = null
                    generationTaskManager.updateActiveBeatIndex(bookId, null)
                }
            }
        }
    }

    fun suggestChapterTitles() {
        val active = aiConfigRepository.resolveActive(TaskScene.COMPANION)
            ?: aiConfigRepository.resolveActive(TaskScene.CONTINUATION)
        if (active == null) {
            _titleSuggestionError.value = "尚未配置可用的 AI 模型，请先到「设置」添加"
            return
        }
        val (provider, model) = active
        val effectiveModel = model.ifBlank { provider.selectedModel }
        val effectiveProvider = provider.copy(selectedModel = effectiveModel)

        titleSuggestionJob?.cancel()
        titleSuggestionJob = viewModelScope.launch {
            _suggestingTitles.value = true
            _titleSuggestionError.value = null
            try {
                val loadedBook = _book.value ?: bookRepository.getBook(bookId)
                if (loadedBook == null) {
                    _titleSuggestionError.value = "书籍未加载，请稍候重试"
                    return@launch
                }
                val currentOutline = outline.value ?: outlineRepository.getOutline(bookId)
                val messages = PromptBuilder.suggestChapterTitlesMessages(
                    book = loadedBook,
                    draftText = streamingText.value,
                    parentChapter = _parentChapter.value,
                    outline = currentOutline,
                    activeBeat = activeOutlineBeat.value,
                    instruction = instruction.value.trim()
                )
                val builder = StringBuilder()
                var promptTokens = 0L
                var completionTokens = 0L
                var streamError: String? = null

                aiClientFactory.clientFor(effectiveProvider.kind)
                    .streamChat(
                        effectiveProvider,
                        messages,
                        maxOf(effectiveProvider.maxTokens, 2048),
                        effectiveProvider.temperature
                    )
                    .collect { event ->
                        when (event) {
                            is StreamEvent.Delta -> builder.append(event.text)
                            is StreamEvent.Usage -> {
                                promptTokens = maxOf(promptTokens, event.promptTokens)
                                completionTokens = maxOf(completionTokens, event.completionTokens)
                            }
                            is StreamEvent.Error -> streamError = event.message
                            is StreamEvent.Done -> Unit
                        }
                    }

                usageRepository.add(promptTokens, completionTokens)
                val parsed = OutlineBeatCodec.parseTitleSuggestions(builder.toString())
                if (parsed.isNotEmpty()) {
                    _aiSuggestedTitles.value = parsed
                } else {
                    _titleSuggestionError.value = streamError ?: "AI 暂未返回可用章节标题，请重试"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _titleSuggestionError.value = "拟定标题失败：${t.message ?: t.javaClass.simpleName}"
            } finally {
                _suggestingTitles.value = false
            }
        }
    }

    fun decodeQuickOptions(rawJson: String): List<String> =
        outlineRepository.decodeQuickOptions(rawJson)

    fun toggleUseOutline() {
        val current = outline.value?.useInContinuation ?: true
        viewModelScope.launch {
            outlineRepository.setUseInContinuation(bookId, !current)
        }
    }

    fun saveOutlineManual(
        master: String,
        upcoming: String,
        proposeContinuation: Boolean = true
    ) {
        viewModelScope.launch {
            val oldPlan = (outline.value ?: outlineRepository.getOutline(bookId))?.upcomingPlan.orEmpty()
            outlineRepository.saveOutline(
                bookId = bookId,
                masterOutline = master,
                upcomingPlan = upcoming
            )
            applySavedOutlineAndPropose(
                previousPlan = oldPlan,
                masterOutline = master,
                upcomingPlan = upcoming,
                fromDiscussion = false,
                proposeContinuation = proposeContinuation
            )
            _memoryStatusMessage.value = "长期大纲已保存，后续续写将自动生效"
        }
    }

    fun adoptProposedOutline(
        master: String?,
        upcoming: String?,
        appendBeats: Boolean = false
    ) {
        viewModelScope.launch {
            val current = outlineRepository.getOutline(bookId)
            val oldPlan = current?.upcomingPlan.orEmpty()
            val resolvedMaster = master?.takeIf { it.isNotBlank() } ?: current?.masterOutline.orEmpty()
            val incomingUpcoming = upcoming?.trim().orEmpty()
            val resolvedUpcoming = when {
                incomingUpcoming.isBlank() -> oldPlan
                appendBeats && oldPlan.isNotBlank() -> OutlineBeatCodec.appendBeats(oldPlan, incomingUpcoming)
                else -> incomingUpcoming
            }
            outlineRepository.saveOutline(
                bookId = bookId,
                masterOutline = resolvedMaster,
                upcomingPlan = resolvedUpcoming,
                useInContinuation = true
            )
            applySavedOutlineAndPropose(
                previousPlan = oldPlan,
                masterOutline = resolvedMaster,
                upcomingPlan = resolvedUpcoming,
                fromDiscussion = true,
                proposeContinuation = true
            )
            _memoryStatusMessage.value = if (appendBeats) {
                "已将新细纲追加拼接到现有长期大纲"
            } else {
                "已采纳 AI 讨论方案并写入长期大纲"
            }
        }
    }

    private fun applySavedOutlineAndPropose(
        previousPlan: String,
        masterOutline: String,
        upcomingPlan: String,
        fromDiscussion: Boolean,
        proposeContinuation: Boolean
    ) {
        val existingTitles = _allChapters.value.mapNotNull { it.title.takeIf(String::isNotBlank) }.toSet()
        val oldBeats = if (previousPlan.isBlank()) {
            emptyList()
        } else {
            OutlineBeatCodec.parseBeats(previousPlan, existingTitles)
        }
        val newBeats = if (upcomingPlan.isBlank()) {
            emptyList()
        } else {
            OutlineBeatCodec.parseBeats(upcomingPlan, existingTitles)
        }
        val nextBeat = newBeats.firstOrNull { !it.isCompleted }

        if (nextBeat != null) {
            _beatSelectionCleared.value = false
            _selectedBeatIndex.value = nextBeat.index
            generationTaskManager.updateActiveBeatIndex(bookId, nextBeat.index)

            val oldTitleKeys = oldBeats.map { it.chapterTitle.trim() }.filter { it.isNotBlank() }.toSet()
            val oldInstructions = oldBeats.map { it.toContinuationInstruction().trim() }.filter { it.isNotBlank() }.toSet()
            val curTitle = customTitle.value.trim()
            val curInstruction = instruction.value.trim()
            if (curTitle.isBlank() || curTitle in oldTitleKeys) {
                customTitle.value = nextBeat.chapterTitle
            }
            if (curInstruction.isBlank() || curInstruction in oldInstructions) {
                instruction.value = nextBeat.toContinuationInstruction()
            }
        } else {
            _selectedBeatIndex.value = null
            generationTaskManager.updateActiveBeatIndex(bookId, null)
        }

        _aiSuggestedTitles.value = emptyList()
        _titleSuggestionError.value = null

        val hasActionablePlan = nextBeat != null || (newBeats.isEmpty() && upcomingPlan.isNotBlank())
        if (proposeContinuation && hasActionablePlan) {
            val masterPreview = masterOutline.trim().replace(Regex("\\s+"), " ").take(120)
            val remainingCount = newBeats.count { !it.isCompleted }
            val suggestedTitle = nextBeat?.chapterTitle.orEmpty()
            val suggestedInstruction = nextBeat?.toContinuationInstruction()
                ?: upcomingPlan.trim().take(360)
            _pendingOutlineProposal.value = PendingOutlineContinuationProposal(
                nextBeat = nextBeat,
                masterSummaryPreview = masterPreview,
                suggestedTitle = suggestedTitle,
                suggestedInstruction = suggestedInstruction,
                totalBeatCount = newBeats.size,
                remainingBeatCount = remainingCount,
                fromDiscussion = fromDiscussion
            )
        }
    }

    fun acceptOutlineProposalAndGenerate() {
        val proposal = _pendingOutlineProposal.value ?: return
        _pendingOutlineProposal.value = null
        viewModelScope.launch {
            if (outline.value?.useInContinuation == false) {
                outlineRepository.setUseInContinuation(bookId, true)
            }
            applyProposalFields(proposal)
            generateInternal(overrideActiveBeat = proposal.nextBeat)
        }
    }

    fun applyOutlineProposalOnly() {
        val proposal = _pendingOutlineProposal.value ?: return
        _pendingOutlineProposal.value = null
        applyProposalFields(proposal)
        if (outline.value?.useInContinuation == false) {
            viewModelScope.launch {
                outlineRepository.setUseInContinuation(bookId, true)
            }
        }
    }

    fun dismissOutlineProposal() {
        _pendingOutlineProposal.value = null
    }

    private fun applyProposalFields(proposal: PendingOutlineContinuationProposal) {
        proposal.nextBeat?.let { beat ->
            _beatSelectionCleared.value = false
            _selectedBeatIndex.value = beat.index
            generationTaskManager.updateActiveBeatIndex(bookId, beat.index)
        }
        if (proposal.suggestedTitle.isNotBlank()) {
            customTitle.value = proposal.suggestedTitle
        }
        if (proposal.suggestedInstruction.isNotBlank()) {
            instruction.value = proposal.suggestedInstruction
        }
    }

    fun clearOutline() {
        _beatSelectionCleared.value = false
        _selectedBeatIndex.value = null
        _pendingOutlineProposal.value = null
        _aiSuggestedTitles.value = emptyList()
        _titleSuggestionError.value = null
        generationTaskManager.updateActiveBeatIndex(bookId, null)
        viewModelScope.launch {
            outlineRepository.clearOutline(bookId)
            _memoryStatusMessage.value = "已清空本书长期大纲"
        }
    }

    fun clearDiscussion() {
        discussionJob?.cancel()
        _discussionGenerating.value = false
        _discussionStreamingText.value = ""
        _discussionError.value = null
        viewModelScope.launch {
            outlineRepository.clearDiscussion(bookId)
        }
    }

    fun stopPlotDiscussion() {
        discussionJob?.cancel()
        _discussionGenerating.value = false
        _discussionStreamingText.value = ""
    }

    fun dismissMemoryStatusMessage() {
        _memoryStatusMessage.value = null
    }

    fun warmUpMissingSummaries() {
        if (_isWarmingSummaries.value) return
        val book = _book.value ?: return
        val chapters = _allChapters.value
        val anchorIdx = _parentChapter.value?.chapterIndex
        viewModelScope.launch {
            _isWarmingSummaries.value = true
            _memoryStatusMessage.value = "正在按最近进度整理剧情记忆卡…"
            val result = storyMemoryService.warmUpMissingSummaries(
                book = book,
                chapters = chapters,
                upToChapterIndex = anchorIdx,
                maxBatch = 15,
                onProgress = { done, total ->
                    if (total > 0 && done < total) {
                        _memoryStatusMessage.value = "正在缓存剧情记忆卡（$done/$total）…"
                    }
                }
            )
            _isWarmingSummaries.value = false
            _memoryStatusMessage.value = result.fold(
                onSuccess = { count ->
                    if (count == 0) "当前进度内的章节记忆卡已全部缓存完毕"
                    else "已新增并长期保存 $count 章剧情记忆卡"
                },
                onFailure = { it.message ?: "补全记忆卡失败" }
            )
        }
    }

    fun requestExtendOutlinePlan(countHint: Int = 6) {
        val safeCount = countHint.coerceIn(1, 20)
        val currentBeats = outlineBeats.value
        val tailHint = currentBeats.takeLast(2).joinToString(" -> ") { "《${it.chapterTitle}》" }
        val prompt = if (tailHint.isNotBlank()) {
            "请在已有分章细纲末尾（$tailHint）之后，结合当前最新剧情锚点，无缝续编接下来的 $safeCount 章分章细纲，给出每章具体章节标题、核心剧情推进与章末钩子，并输出 [[UPCOMING_PLAN]] 草案。"
        } else {
            "请结合当前最新剧情锚点与前文脉络，往后编排接下来 $safeCount 章分章细纲，给出每章具体章节标题、核心剧情推进与章末钩子，并输出 [[UPCOMING_PLAN]] 草案。"
        }
        sendPlotDiscussionInternal(
            userInput = prompt,
            requestSynthesizeOutline = false,
            extendExistingPlan = true,
            targetExtendCount = safeCount
        )
    }

    fun sendPlotDiscussion(
        userInput: String,
        requestSynthesizeOutline: Boolean = false,
        extendExistingPlan: Boolean = false
    ) {
        sendPlotDiscussionInternal(
            userInput = userInput,
            requestSynthesizeOutline = requestSynthesizeOutline,
            extendExistingPlan = extendExistingPlan,
            targetExtendCount = 6
        )
    }

    private fun sendPlotDiscussionInternal(
        userInput: String,
        requestSynthesizeOutline: Boolean,
        extendExistingPlan: Boolean,
        targetExtendCount: Int
    ) {
        val trimmed = userInput.trim()
        if (!requestSynthesizeOutline && !extendExistingPlan && trimmed.isBlank()) return

        val active = aiConfigRepository.resolveActive(TaskScene.OUTLINE)
            ?: aiConfigRepository.resolveActive(TaskScene.CONTINUATION)
            ?: aiConfigRepository.resolveActive(TaskScene.COMPANION)
        if (active == null) {
            _discussionError.value = "尚未配置可用的 AI 模型，请先到「设置」添加"
            return
        }
        val (provider, model) = active
        val effectiveModel = model.ifBlank { provider.selectedModel }
        val effectiveProvider = provider.copy(selectedModel = effectiveModel)
        val modelLabel = "${provider.providerName} · ${effectiveModel.ifBlank { "未命名模型" }}"

        discussionJob?.cancel()
        discussionJob = viewModelScope.launch {
            _discussionError.value = null
            _discussionGenerating.value = true
            _discussionStreamingText.value = ""

            try {
                val book = _book.value ?: bookRepository.getBook(bookId)
                if (book == null) {
                    _discussionError.value = "书籍未加载，请稍候重试"
                    return@launch
                }
                val chapters = _allChapters.value.ifEmpty { bookRepository.getChapters(bookId) }
                val anchor = _parentChapter.value ?: chapters.maxByOrNull { it.chapterIndex }
                val lineage = if (anchor != null) {
                    bookRepository.getChapterLineage(bookId, anchor.id).filter { it.content.isNotBlank() }
                } else {
                    emptyList()
                }
                val previous = if (lineage.isNotEmpty()) {
                    lineage
                } else {
                    chapters.filter {
                        (anchor == null || it.chapterIndex <= anchor.chapterIndex) && it.content.isNotBlank()
                    }
                }

                val displayUserContent = when {
                    trimmed.isNotBlank() -> trimmed
                    extendExistingPlan -> "请承接既有细纲末尾与当前剧情锚点，继续往后编排接下来 $targetExtendCount 章的分章细纲。"
                    else -> "请综合我们的讨论与前文剧情，整理并输出可保存的双层大纲草案。"
                }

                val history = outlineRepository.recentDiscussion(bookId, 12)
                    .filter { it.role == "user" || it.role == "assistant" }
                    .map { ChatMessage(it.role, it.content) }

                outlineRepository.saveDiscussionMessage(
                    OutlineDiscussionEntity(
                        id = UUID.randomUUID().toString(),
                        bookId = bookId,
                        anchorChapterId = anchor?.id,
                        role = "user",
                        content = displayUserContent
                    )
                )

                val currentOutline = outlineRepository.getOutline(bookId)
                val chapterSummaries = storyMemoryService.getValidSummaryMap(bookId, previous)
                val query = buildString {
                    append(displayUserContent)
                    if (currentOutline != null && currentOutline.upcomingPlan.isNotBlank()) {
                        append('\n').append(currentOutline.upcomingPlan.take(240))
                    }
                    if (anchor != null) {
                        append('\n').append(anchor.title).append('\n').append(anchor.content.takeLast(600))
                    }
                }
                val retrievedSnippets = runCatching {
                    retrievalService.retrieveSnippets(
                        bookId = bookId,
                        query = query,
                        topK = 6,
                        spoilerFree = true,
                        currentChapterIndex = anchor?.chapterIndex
                    )
                }.getOrDefault(emptyList())

                val messages = PromptBuilder.plotDiscussionMessages(
                    book = book,
                    anchorChapter = anchor,
                    previousChapters = previous,
                    currentOutline = currentOutline,
                    chapterSummaries = chapterSummaries,
                    retrievedSnippets = retrievedSnippets,
                    history = history,
                    userMessage = displayUserContent,
                    requestSynthesizeOutline = requestSynthesizeOutline,
                    extendExistingPlan = extendExistingPlan,
                    targetExtendCount = targetExtendCount
                )

                val builder = StringBuilder()
                var promptTokens = 0L
                var completionTokens = 0L

                aiClientFactory.clientFor(effectiveProvider.kind)
                    .streamChat(
                        effectiveProvider,
                        messages,
                        maxOf(effectiveProvider.maxTokens, 2048),
                        effectiveProvider.temperature
                    )
                    .collect { event ->
                        when (event) {
                            is StreamEvent.Delta -> {
                                builder.append(event.text)
                                _discussionStreamingText.value =
                                    PlotDiscussionCodec.stripStreamingTags(builder.toString())
                            }
                            is StreamEvent.Usage -> {
                                promptTokens = maxOf(promptTokens, event.promptTokens)
                                completionTokens = maxOf(completionTokens, event.completionTokens)
                            }
                            is StreamEvent.Error -> _discussionError.value = event.message
                            is StreamEvent.Done -> Unit
                        }
                    }

                usageRepository.add(promptTokens, completionTokens)
                val parsed = PlotDiscussionCodec.parse(builder.toString())
                if (parsed.displayContent.isNotBlank()) {
                    outlineRepository.saveDiscussionMessage(
                        OutlineDiscussionEntity(
                            id = UUID.randomUUID().toString(),
                            bookId = bookId,
                            anchorChapterId = anchor?.id,
                            role = "assistant",
                            content = parsed.displayContent,
                            quickOptionsJson = outlineRepository.encodeQuickOptions(parsed.directions),
                            proposedMasterOutline = parsed.proposedMasterOutline,
                            proposedUpcomingPlan = parsed.proposedUpcomingPlan,
                            modelUsed = modelLabel
                        )
                    )
                } else if (_discussionError.value == null) {
                    _discussionError.value = "AI 未返回有效内容，请重试"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _discussionError.value = "剧情讨论请求失败：${t.message ?: t.javaClass.simpleName}"
            } finally {
                _discussionGenerating.value = false
                _discussionStreamingText.value = ""
            }
        }
    }
}

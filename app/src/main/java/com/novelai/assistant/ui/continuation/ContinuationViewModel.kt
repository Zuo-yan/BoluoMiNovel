package com.novelai.assistant.ui.continuation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.db.ChapterOriginType
import com.novelai.assistant.data.network.AiClientFactory
import com.novelai.assistant.data.network.StreamEvent
import com.novelai.assistant.data.repository.AiConfigRepository
import com.novelai.assistant.data.repository.BookRepository
import com.novelai.assistant.data.repository.TaskScene
import com.novelai.assistant.data.repository.UsageRepository
import com.novelai.assistant.domain.PromptBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContinuationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val aiConfigRepository: AiConfigRepository,
    private val usageRepository: UsageRepository,
    private val aiClientFactory: AiClientFactory,
    private val retrievalService: com.novelai.assistant.data.rag.RetrievalService
) : ViewModel() {

    val bookId: String = savedStateHandle["bookId"] ?: ""
    val isBranchMode: Boolean = (savedStateHandle["mode"] ?: "CONTINUATION") == "BRANCH"

    /** 基准章节（可切换）：续写以其为衔接点，分支以其为剧情原点 */
    private val _parentChapterId = MutableStateFlow(savedStateHandle["chapterId"] ?: "")
    val parentChapterId: String get() = _parentChapterId.value

    private val _book = MutableStateFlow<BookEntity?>(null)
    val book: StateFlow<BookEntity?> = _book.asStateFlow()

    private val _parentChapter = MutableStateFlow<ChapterEntity?>(null)
    val parentChapter: StateFlow<ChapterEntity?> = _parentChapter.asStateFlow()

    private val _allChapters = MutableStateFlow<List<ChapterEntity>>(emptyList())
    val allChapters: StateFlow<List<ChapterEntity>> = _allChapters.asStateFlow()

    /** 用户自定义章节标题（保存时用，空则自动命名） */
    val customTitle = MutableStateFlow("")

    /** 是否直接基于全书最新章节续写（无需用户手动找章选章） */
    private val _useLatestChapter = MutableStateFlow(!isBranchMode)
    val useLatestChapter: StateFlow<Boolean> = _useLatestChapter.asStateFlow()

    private val _latestChapter = MutableStateFlow<ChapterEntity?>(null)
    val latestChapter: StateFlow<ChapterEntity?> = _latestChapter.asStateFlow()

    fun selectParentChapter(chapter: ChapterEntity) {
        val latest = _allChapters.value.maxByOrNull { it.chapterIndex }
        _useLatestChapter.value = (chapter.id == latest?.id)
        _parentChapterId.value = chapter.id
        _parentChapter.value = chapter
    }

    val instruction = MutableStateFlow("")

    /** 小说工坊技法（去AI味/章末钩子/潜台词对话） */
    private val _useWorkshopCraft = MutableStateFlow(true)
    val useWorkshopCraft: StateFlow<Boolean> = _useWorkshopCraft.asStateFlow()

    fun toggleWorkshopCraft() { _useWorkshopCraft.value = !_useWorkshopCraft.value }

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val continuousCount = MutableStateFlow(0)
    private val _savedChapterId = MutableStateFlow<String?>(null)
    val savedChapterId: StateFlow<String?> = _savedChapterId.asStateFlow()

    private var generateJob: Job? = null

    init {
        viewModelScope.launch {
            _book.value = bookRepository.getBook(bookId)
            val chapters = bookRepository.getChapters(bookId)
            _allChapters.value = chapters
            val latest = chapters.maxByOrNull { it.chapterIndex }
            _latestChapter.value = latest

            if (isBranchMode) {
                _useLatestChapter.value = false
                val specified = chapters.firstOrNull { it.id == _parentChapterId.value } ?: latest
                _parentChapter.value = specified
                _parentChapterId.value = specified?.id ?: ""
            } else {
                // 默认：直接基于全书最新一章续写，不选章节直接续写！
                val specified = chapters.firstOrNull { it.id == _parentChapterId.value }
                if (specified != null && latest != null && specified.id != latest.id && savedStateHandle.get<String>("chapterId") != null) {
                    // 若页面参数明确指定了历史章节，则显示该指定章，但同时可通过顶部开关一键切至最新章
                    _useLatestChapter.value = false
                    _parentChapter.value = specified
                } else if (latest != null) {
                    _useLatestChapter.value = true
                    _parentChapter.value = latest
                    _parentChapterId.value = latest.id
                } else {
                    _parentChapter.value = specified
                }
            }
        }
    }

    fun setUseLatestChapter(useLatest: Boolean) {
        _useLatestChapter.value = useLatest
        if (useLatest) {
            val latest = _allChapters.value.maxByOrNull { it.chapterIndex }
            if (latest != null) {
                _parentChapter.value = latest
                _parentChapterId.value = latest.id
            }
        }
    }

    fun selectLatestChapter() {
        setUseLatestChapter(true)
    }

    fun stop() {
        generateJob?.cancel()
        _streaming.value = false
        if (_streamingText.value.isNotBlank()) _finished.value = true
    }

    fun reset() {
        generateJob?.cancel()
        _streamingText.value = ""
        _finished.value = false
        _error.value = null
        _streaming.value = false
    }

    fun generate() {
        val book = _book.value ?: run { _error.value = "书籍未加载"; return }
        val parent = _parentChapter.value ?: run { _error.value = "章节未加载"; return }
        if (parent.content.isBlank()) { _error.value = "本章尚无正文，请先在阅读器打开或下载后再续写"; return }
        val active = aiConfigRepository.resolveActive(TaskScene.CONTINUATION)
            ?: run { _error.value = "尚未配置「续写场景」模型，请到设置页配置"; return }
        val (provider, model) = active
        val effectiveProvider = provider.copy(selectedModel = model)
        val instructionText = instruction.value.trim()

        generateJob = viewModelScope.launch {
            _error.value = null
            _streaming.value = true
            _finished.value = false
            _streamingText.value = ""

            val builder = StringBuilder()
            val craft = _useWorkshopCraft.value
            try {
                val lineage = bookRepository.getChapterLineage(bookId, parent.id).filter { it.content.isNotBlank() }
                val messages = if (isBranchMode) {
                    PromptBuilder.branchMessages(book, parent, instructionText.ifBlank { "自由推演一个有趣的平行分支" }, lineage, craft)
                } else {
                    val previous = if (lineage.isNotEmpty()) lineage
                    else bookRepository.getChapters(bookId).filter { it.chapterIndex <= parent.chapterIndex }
                    PromptBuilder.continuationMessages(book, previous, instructionText, craft)
                }

                var promptTokens = 0L
                var completionTokens = 0L

                aiClientFactory.clientFor(effectiveProvider.kind)
                    .streamChat(effectiveProvider, messages, effectiveProvider.maxTokens, effectiveProvider.temperature)
                    .collect { event ->
                        when (event) {
                            is StreamEvent.Delta -> {
                                builder.append(event.text)
                                _streamingText.value = builder.toString()
                            }
                            is StreamEvent.Usage -> {
                                promptTokens = maxOf(promptTokens, event.promptTokens)
                                completionTokens = maxOf(completionTokens, event.completionTokens)
                            }
                            is StreamEvent.Error -> _error.value = event.message
                            is StreamEvent.Done -> Unit
                        }
                    }
                usageRepository.add(promptTokens, completionTokens)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户主动停止：保留已生成内容
            } catch (t: Throwable) {
                _error.value = "生成中断：${t.message ?: t.javaClass.simpleName}"
            } finally {
                _streaming.value = false
                _finished.value = builder.isNotBlank()
                if (builder.isBlank() && _error.value == null) _error.value = "未收到任何内容，请重试"
            }
        }
    }

    /** 把已生成内容追加到父章节末尾（而不是另存新章） */
    fun appendToParentChapter(onDone: () -> Unit) {
        val text = _streamingText.value.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            bookRepository.appendToChapter(parentChapterId, "\n\n$text")
            retrievalService.evict(bookId)
            onDone()
        }
    }

    /** 保存当前章节并自动将新章设为基准章节，支持连续无限续写 */
    fun saveAndContinue(autoGenerateNext: Boolean = false) {
        val text = _streamingText.value.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            val defaultTitle = if (isBranchMode) {
                val branchSuffix = if (continuousCount.value > 0) " · 第 ${continuousCount.value + 1} 节" else ""
                "分支：${instruction.value.trim().take(10).ifBlank { "平行世界" }}$branchSuffix"
            } else {
                "AI 续写 · 第 ${(_parentChapter.value?.chapterIndex ?: 0) + 2} 章"
            }
            val title = customTitle.value.trim().ifBlank { defaultTitle }
            val chapter = bookRepository.saveGeneratedChapter(
                bookId = bookId,
                parentChapterId = if (isBranchMode) _parentChapterId.value else null,
                title = title,
                content = text,
                promptUsed = instruction.value.trim().ifBlank { null },
                originType = if (isBranchMode) ChapterOriginType.AI_BRANCH else ChapterOriginType.AI_CONTINUATION
            )
            retrievalService.evict(bookId)
            _savedChapterId.value = chapter.id
            continuousCount.value += 1

            // 关键：将刚刚生成保存的新章切换为下一章的前置基准
            _allChapters.value = bookRepository.getChapters(bookId)
            _book.value = bookRepository.getBook(bookId)
            val newLatest = _allChapters.value.maxByOrNull { it.chapterIndex }
            _latestChapter.value = newLatest

            if (_useLatestChapter.value && newLatest != null) {
                _parentChapterId.value = newLatest.id
                _parentChapter.value = newLatest
            } else {
                _parentChapterId.value = chapter.id
                _parentChapter.value = chapter
            }

            _streamingText.value = ""
            _finished.value = false
            customTitle.value = ""
            _error.value = null

            if (autoGenerateNext) {
                generate()
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        val text = _streamingText.value.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            val defaultTitle = if (isBranchMode) {
                "分支：${instruction.value.trim().take(12).ifBlank { "平行世界" }}"
            } else {
                "AI 续写 · ${_parentChapter.value?.title?.take(12) ?: "新章"}"
            }
            val title = customTitle.value.trim().ifBlank { defaultTitle }
            val chapter = bookRepository.saveGeneratedChapter(
                bookId = bookId,
                parentChapterId = if (isBranchMode) _parentChapterId.value else null,
                title = title,
                content = text,
                promptUsed = instruction.value.trim().ifBlank { null },
                originType = if (isBranchMode) ChapterOriginType.AI_BRANCH else ChapterOriginType.AI_CONTINUATION
            )
            retrievalService.evict(bookId)
            _savedChapterId.value = chapter.id
            onSaved()
        }
    }
}

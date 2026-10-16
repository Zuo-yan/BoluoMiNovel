package com.novelai.assistant.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.db.AiChatRecordEntity
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.network.AiClientFactory
import com.novelai.assistant.data.network.ChatMessage
import com.novelai.assistant.data.network.StreamEvent
import com.novelai.assistant.data.repository.AiConfigRepository
import com.novelai.assistant.data.repository.BookRepository
import com.novelai.assistant.data.repository.ChatRepository
import com.novelai.assistant.data.repository.TaskScene
import com.novelai.assistant.data.repository.UsageRepository
import com.novelai.assistant.domain.PromptBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val chatRepository: ChatRepository,
    private val aiConfigRepository: AiConfigRepository,
    private val usageRepository: UsageRepository,
    private val aiClientFactory: AiClientFactory,
    private val retrievalService: com.novelai.assistant.data.rag.RetrievalService
) : ViewModel() {

    val books: StateFlow<List<BookEntity>> = bookRepository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val selectedBookId = MutableStateFlow("")
    val currentBookId: StateFlow<String> = selectedBookId.asStateFlow()

    private val _currentBook = MutableStateFlow<BookEntity?>(null)
    val currentBook: StateFlow<BookEntity?> = _currentBook.asStateFlow()

    private val _currentChapter = MutableStateFlow<ChapterEntity?>(null)
    val currentChapter: StateFlow<ChapterEntity?> = _currentChapter.asStateFlow()

    val chatRecords: StateFlow<List<AiChatRecordEntity>> = selectedBookId
        .flatMapLatest { bookId ->
            if (bookId.isBlank()) flowOf(emptyList()) else chatRepository.observeChat(bookId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private val _pendingQuote = MutableStateFlow<String?>(null)
    val pendingQuote: StateFlow<String?> = _pendingQuote.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 不剧透模式：只召回当前阅读位置之前的章节 */
    private val _spoilerFree = MutableStateFlow(true)
    val spoilerFree: StateFlow<Boolean> = _spoilerFree.asStateFlow()

    /** 最近一次回答参考的章节名 */
    private val _retrievedHint = MutableStateFlow<String?>(null)
    val retrievedHint: StateFlow<String?> = _retrievedHint.asStateFlow()

    fun toggleSpoilerFree() { _spoilerFree.value = !_spoilerFree.value }

    private var generateJob: Job? = null
    private var initializedQuoteKey: String? = null

    fun selectBook(bookId: String) {
        if (bookId.isBlank()) return
        if (bookId == selectedBookId.value && _currentBook.value != null) return
        selectedBookId.value = bookId
        generateJob?.cancel()
        _generating.value = false
        _streamingText.value = ""
        viewModelScope.launch {
            val book = bookRepository.getBook(bookId) ?: return@launch
            _currentBook.value = book
            val chapters = bookRepository.getChapters(bookId)
            _currentChapter.value = chapters.getOrNull(book.currentReadingChapterIndex.coerceIn(0, chapters.lastIndex))
        }
    }

    fun setPendingQuote(quote: String?) {
        _pendingQuote.value = quote?.takeIf { it.isNotBlank() }
    }

    fun clearQuote() { _pendingQuote.value = null }

    /**
     * 阅读器划选进入专属伴读页时的初始化方法。
     * 若携带有特定 action（如 AI吐槽、解析深意、情绪总结），自动且仅自动触发一次问答请求。
     */
    fun initializeForQuote(bookId: String, quote: String, action: String) {
        selectBook(bookId)
        if (quote.isNotBlank()) {
            setPendingQuote(quote)
        }
        val key = "$bookId:$quote:$action"
        if (initializedQuoteKey == key) return
        initializedQuoteKey = key

        if (action.isNotBlank()) {
            sendAction(action, quote.takeIf { it.isNotBlank() })
        }
    }

    fun sendAction(action: String, quoteText: String? = null) {
        val q = quoteText ?: _pendingQuote.value
        val prompt = when (action) {
            "AI吐槽", "吐槽" -> PromptBuilder.QUICK_ROAST
            "解析深意", "解析" -> PromptBuilder.QUICK_ANALYZE
            "情绪总结", "情绪" -> PromptBuilder.QUICK_EMOTION
            else -> action
        }
        send(prompt, customQuote = q)
    }

    fun stopGenerating() {
        generateJob?.cancel()
        _generating.value = false
        _streamingText.value = ""
    }

    fun clearChat() {
        val bookId = selectedBookId.value
        if (bookId.isBlank()) return
        viewModelScope.launch { chatRepository.clear(bookId) }
    }

    fun send(question: String, customQuote: String? = null) {
        val bookId = selectedBookId.value
        if (bookId.isBlank()) { _error.value = "请先选择一本书"; return }
        val quote = customQuote ?: _pendingQuote.value
        val trimmed = question.trim()
        if (trimmed.isBlank() && quote.isNullOrBlank()) return

        val active = aiConfigRepository.resolveActive(TaskScene.COMPANION)
        if (active == null) {
            _error.value = "尚未配置可用的 AI 提供商，请到「设置」添加"
            return
        }
        val (provider, model) = active
        val effectiveProvider = provider.copy(selectedModel = model)

        generateJob = viewModelScope.launch {
            _error.value = null
            _generating.value = true
            _streamingText.value = ""
            _pendingQuote.value = null

            val book = _currentBook.value ?: bookRepository.getBook(bookId)
            if (book == null) {
                _error.value = "无法加载书籍信息，请稍候重试"
                _generating.value = false
                return@launch
            }
            _currentBook.value = book

            val latestBook = bookRepository.getBook(bookId) ?: book
            val availableChapters = bookRepository.getChapters(bookId)
            _currentChapter.value = if (availableChapters.isEmpty()) null else availableChapters.getOrNull(latestBook.currentReadingChapterIndex.coerceIn(0, availableChapters.lastIndex))
            val chapter = _currentChapter.value

            val history = chatRepository.recentMessages(bookId, 12)
                .filter { it.role == "user" || it.role == "assistant" }
                .map { ChatMessage(it.role, it.content) }

            // RAG：按问题检索相关章节（不剧透模式下只召回已读部分）
            val query = if (quote.isNullOrBlank()) trimmed else "$quote $trimmed"
            val retrieved = runCatching {
                retrievalService.retrieve(
                    bookId = bookId,
                    query = query,
                    topK = 3,
                    spoilerFree = _spoilerFree.value,
                    currentChapterIndex = chapter?.chapterIndex
                )
            }.getOrDefault(emptyList())
            _retrievedHint.value = retrieved.takeIf { it.isNotEmpty() }
                ?.joinToString("、") { it.chapter.title }

            val userContentToSave = if (quote.isNullOrBlank()) {
                trimmed
            } else {
                "【划线选段】\n$quote\n\n【提问】\n$trimmed"
            }

            chatRepository.save(
                AiChatRecordEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    chapterId = chapter?.id,
                    role = "user",
                    content = userContentToSave
                )
            )

            val messages = PromptBuilder.companionMessages(
                book = book,
                currentChapter = chapter,
                history = history,
                question = trimmed.ifBlank { "请解析上面的划线选段" },
                quotedText = quote,
                retrievedChapters = retrieved.map { it.chapter },
                spoilerFree = _spoilerFree.value
            )

            val builder = StringBuilder()
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

            val full = builder.toString()
            if (full.isNotBlank()) {
                chatRepository.save(
                    AiChatRecordEntity(
                        id = UUID.randomUUID().toString(),
                        bookId = bookId,
                        chapterId = chapter?.id,
                        role = "assistant",
                        content = full,
                        modelUsed = model
                    )
                )
            } else if (_error.value == null) {
                _error.value = "未收到任何回复内容"
            }
            _streamingText.value = ""
            _generating.value = false
        }
    }
}

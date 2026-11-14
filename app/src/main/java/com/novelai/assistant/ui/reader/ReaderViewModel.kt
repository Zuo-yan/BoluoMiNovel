package com.novelai.assistant.ui.reader

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.BookSourceType
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.prefs.PageMode
import com.novelai.assistant.data.prefs.ReaderBgTheme
import com.novelai.assistant.data.prefs.ReadingPreferencesRepository
import com.novelai.assistant.data.prefs.ReadingSettings
import com.novelai.assistant.data.repository.BookRepository
import com.novelai.assistant.data.sf.BookExportFormat
import com.novelai.assistant.data.sf.SfException
import com.novelai.assistant.data.sf.SfFailureKind
import com.novelai.assistant.data.sf.SfParagraphComment
import com.novelai.assistant.data.tts.TtsPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

typealias TtsTimerMode = com.novelai.assistant.data.tts.TtsTimerMode

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val readingPreferencesRepository: ReadingPreferencesRepository,
    private val ttsPlayer: TtsPlayer
) : ViewModel() {

    val bookId: String = savedStateHandle["bookId"] ?: ""
    private val initialChapterIndex: Int = (savedStateHandle.get<Int>("chapter") ?: -1).let {
        if (it >= 0) it else readingPreferencesRepository.getLastChapterIndex(bookId)
    }

    fun readPosition(chapterId: String): ReadingAnchor =
        readingPreferencesRepository.getChapterAnchor(chapterId)

    fun savePosition(chapterId: String, anchor: ReadingAnchor) {
        if (chapterId != currentChapter.value?.id) return
        val chapterIdx = currentChapter.value?.chapterIndex ?: _chapterIndex.value
        readingPreferencesRepository.saveChapterAnchor(bookId, chapterId, chapterIdx, anchor)
    }

    val book: StateFlow<BookEntity?> = bookRepository.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chapters: StateFlow<List<ChapterEntity>> = bookRepository.observeChapters(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<ReadingSettings> = readingPreferencesRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReadingSettings())

    // 初始定位到上次阅读章节
    private val _chapterIndex = MutableStateFlow(-1)
    val chapterIndex: StateFlow<Int> = _chapterIndex.asStateFlow()

    val currentChapter: StateFlow<ChapterEntity?> =
        combine(chapters, _chapterIndex) { list, idx ->
            if (idx in list.indices) list[idx] else null
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // UI 开关
    private val _chromeVisible = MutableStateFlow(true)
    val chromeVisible: StateFlow<Boolean> = _chromeVisible.asStateFlow()

    private val _tocOpen = MutableStateFlow(false)
    val tocOpen: StateFlow<Boolean> = _tocOpen.asStateFlow()

    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    private val _tocFilter = MutableStateFlow(TocFilter.ALL)
    val tocFilter: StateFlow<TocFilter> = _tocFilter.asStateFlow()

    // 划线即问 / 选段朗读
    private val _quotedText = MutableStateFlow<String?>(null)
    val quotedText: StateFlow<String?> = _quotedText.asStateFlow()

    private val _quotedParaIndex = MutableStateFlow<Int?>(null)
    val quotedParaIndex: StateFlow<Int?> = _quotedParaIndex.asStateFlow()

    // 间贴（SF 段落吐槽与回复）
    private val tsukkomi = ReaderTsukkomiController(
        scope = viewModelScope,
        fetchComments = { chapterId, sort, row, page ->
            bookRepository.remoteParagraphComments(chapterId, sort, row, page)
        },
        fetchReplies = { commentId ->
            bookRepository.remoteParagraphReplies(commentId)
        }
    )
    val paragraphComments: StateFlow<List<SfParagraphComment>> = tsukkomi.comments
    val paragraphCommentsLoading: StateFlow<Boolean> = tsukkomi.loading
    val paragraphCommentsLoadingMore: StateFlow<Boolean> = tsukkomi.loadingMore
    val paragraphCommentsError: StateFlow<String?> = tsukkomi.error
    val paragraphCommentsSort: StateFlow<String> = tsukkomi.sort
    val paragraphCommentsOpen: StateFlow<Boolean> = tsukkomi.open
    val paragraphCommentsQuote: StateFlow<String> = tsukkomi.quote
    val paragraphCommentsRow: StateFlow<Int?> = tsukkomi.row
    val paragraphCommentCounts: StateFlow<Map<Int, Int>> = tsukkomi.commentCounts
    val expandedParagraphReplies: StateFlow<Set<String>> = tsukkomi.expandedReplies
    val replyThreads: StateFlow<Map<String, ReplyThread>> = tsukkomi.replyThreads

    fun loadReplies(id: String) = tsukkomi.loadReplies(id)

    // 目录树（分支缩进）
    val tocItems: StateFlow<List<TocItem>> =
        combine(chapters, _tocFilter) { list, filter ->
            ReaderTocTree.filterAndBuild(list, filter)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var progressJob: Job? = null
    private val reload = MutableStateFlow(0)
    private val _chapterError = MutableStateFlow<String?>(null)
    val chapterError: StateFlow<String?> = _chapterError.asStateFlow()
    private val _chapterFailureKind = MutableStateFlow<SfFailureKind?>(null)
    val chapterFailureKind: StateFlow<SfFailureKind?> = _chapterFailureKind.asStateFlow()

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    fun retryChapter() { reload.value++ }
    fun clearExportMessage() { _exportMessage.value = null }

    init {
        var summaryChapterId: String? = null
        var summaryRequestedFor: String? = null
        viewModelScope.launch {
            combine(currentChapter, reload) { chapter, retry -> Triple(chapter?.id, chapter?.content?.isBlank(), retry) }
                .distinctUntilChanged().collectLatest { (id, empty, _) ->
                    _chapterError.value = null
                    _chapterFailureKind.value = null
                    if (id != summaryChapterId) {
                        summaryChapterId = id
                        summaryRequestedFor = null
                        tsukkomi.clearCommentCounts()
                    }
                    if (id != null) {
                        try {
                            val source = bookRepository.getBook(bookId)
                            if (source?.sourceType == BookSourceType.BOLUOBAO &&
                                summaryRequestedFor != id
                            ) {
                                summaryRequestedFor = id
                                val (_, summaries) = bookRepository.readableChapterWithComments(id)
                                if (summaryChapterId == id) {
                                    tsukkomi.setCommentCounts(summaries.associate { it.row to it.count })
                                }
                            } else if (empty == true) {
                                bookRepository.readableChapter(id)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            _chapterFailureKind.value = (e as? SfException)?.kind
                            _chapterError.value = e.message ?: "正文加载失败"
                            // A bounds error must not make the reader look permanently
                            // unreadable when only the optional comment summary failed.
                            if (empty != true && e !is SfException) {
                                summaryRequestedFor = null
                            }
                        }
                    }
                }
        }
        if (initialChapterIndex >= 0) {
            _chapterIndex.value = initialChapterIndex
        }
        // 仅当后台未在朗读其他书籍时，才自动同步书籍与章节上下文
        fun canBindTts(): Boolean {
            val playingBook = ttsPlayer.currentBookId.value
            return playingBook.isBlank() || playingBook == bookId
        }

        chapters.onEach { list ->
            if (list.isNotEmpty() && canBindTts()) {
                val bTitle = book.value?.title ?: ""
                ttsPlayer.bindBookContext(bookId, bTitle, list)
            }
        }.launchIn(viewModelScope)

        // 初始章节：书籍加载后定位到上次阅读位置
        book.onEach { b ->
            if (b != null) {
                if (canBindTts()) {
                    ttsPlayer.bindBookContext(bookId, b.title, chapters.value)
                }
                if (_chapterIndex.value == -1 && b.currentReadingChapterIndex >= 0) {
                    _chapterIndex.value = b.currentReadingChapterIndex
                }
            }
        }.launchIn(viewModelScope)

        // 若重新进入页面时后台正在朗读本书，无缝同步播放进度
        if (ttsPlayer.currentBookId.value == bookId && (ttsPlayer.speaking.value || ttsPlayer.paused.value)) {
            val playingIndex = ttsPlayer.currentChapterIndex.value
            if (playingIndex >= 0) {
                _chapterIndex.value = playingIndex
            }
        }

        // 当通知栏/锁屏/听书页切换当前书的朗读章节时，同步阅读器页内章节
        ttsPlayer.currentChapterIndex
            .drop(1)
            .distinctUntilChanged()
            .onEach { playingIdx ->
                val isThisBookPlaying = ttsPlayer.currentBookId.value == bookId &&
                    (ttsPlayer.speaking.value || ttsPlayer.paused.value)
                if (isThisBookPlaying && playingIdx in chapters.value.indices && playingIdx != _chapterIndex.value) {
                    _targetPageOnChapterLoad.value = 0
                    _chapterIndex.value = playingIdx
                    scheduleProgressSave()
                }
            }
            .launchIn(viewModelScope)
    }

    private val _targetPageOnChapterLoad = MutableStateFlow<Int?>(null)
    val targetPageOnChapterLoad: StateFlow<Int?> = _targetPageOnChapterLoad.asStateFlow()

    fun openPreviousChapter() {
        val idx = _chapterIndex.value
        if (idx > 0) {
            _targetPageOnChapterLoad.value = -1
            setChapterIndex(idx - 1)
        }
    }

    fun openNextChapter() {
        val idx = _chapterIndex.value
        if (idx < chapters.value.lastIndex) {
            _targetPageOnChapterLoad.value = 0
            setChapterIndex(idx + 1)
        }
    }

    fun consumeTargetPage(): Int? {
        val target = _targetPageOnChapterLoad.value
        _targetPageOnChapterLoad.value = null
        return target
    }

    fun setChapterIndex(index: Int) {
        if (index == _chapterIndex.value) return
        // 听书中手动切章：停止当前朗读（自动连播场景由 onTtsChapterEnd 自行驱动）
        if (ttsPlayer.speaking.value || ttsPlayer.paused.value) ttsPlayer.stop()
        _chapterIndex.value = index
        scheduleProgressSave()
    }

    // ---- 听书（TTS）与定时器 ----
    val ttsReady = ttsPlayer.ready
    val ttsSpeaking = ttsPlayer.speaking
    val ttsPaused = ttsPlayer.paused
    val ttsPosition = ttsPlayer.position
    val ttsSpeed: StateFlow<Float> = ttsPlayer.rate
    val ttsTimerMode: StateFlow<TtsTimerMode> = ttsPlayer.timerMode
    val ttsTimerRemainingSeconds: StateFlow<Int?> = ttsPlayer.timerRemainingSeconds

    val isCurrentBookSpeaking: StateFlow<Boolean> = combine(
        ttsPlayer.currentBookId,
        ttsPlayer.speaking,
        ttsPlayer.paused
    ) { currentId, speaking, paused ->
        currentId == bookId && (speaking || paused)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setTtsSpeed(speed: Float) {
        ttsPlayer.setRate(speed)
    }

    fun setTtsTimerMode(mode: TtsTimerMode) {
        ttsPlayer.setTimerMode(mode)
    }

    /** 开始/停止朗读当前章；支持指定起始段落，读毕自动连播下一章 */
    fun toggleTts(startIndex: Int = 0) {
        if (ttsPlayer.speaking.value || ttsPlayer.paused.value) {
            val sameBook = ttsPlayer.currentBookId.value == bookId
            ttsPlayer.stop()
            if (!sameBook) {
                startTts(startIndex)
            }
        } else {
            startTts(startIndex)
        }
    }

    fun startTts(startIndex: Int = 0) {
        val chapter = currentChapter.value ?: chapters.value.getOrNull(_chapterIndex.value) ?: return
        startTtsForChapter(chapter, startIndex)
    }

    fun startTtsForChapter(chapter: ChapterEntity, startIndex: Int = 0) {
        val bTitle = book.value?.title?.ifBlank { null } ?: chapter.title
        ttsPlayer.bindBookContext(bookId, bTitle, chapters.value)
        if (chapter.content.isBlank()) {
            val requestGeneration = ttsPlayer.playbackGeneration
            viewModelScope.launch {
                try {
                    val loaded = bookRepository.readableChapter(chapter.id)
                    if (requestGeneration == ttsPlayer.playbackGeneration && _chapterIndex.value == chapter.chapterIndex) {
                        startTtsForChapter(loaded, startIndex)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (requestGeneration == ttsPlayer.playbackGeneration) {
                        _chapterError.value = e.message ?: "本章无法朗读"
                        ttsPlayer.stop()
                    }
                }
            }
            return
        }
        val paragraphs = Paginator.splitParagraphs(chapter.content)
        if (paragraphs.isEmpty()) {
            onTtsChapterEnd()
            return
        }
        val firstReadable = paragraphs.indexOfFirst { it.isNotBlank() }.coerceAtLeast(0)
        var validIndex = startIndex.coerceIn(0, paragraphs.lastIndex)
        while (validIndex in paragraphs.indices && paragraphs[validIndex].isBlank()) validIndex++
        if (validIndex !in paragraphs.indices) validIndex = firstReadable
        ttsPlayer.play(
            paragraphs = paragraphs,
            startIndex = validIndex,
            chapterIndex = chapter.chapterIndex,
            chapterTitle = chapter.title,
            onChapterEnd = { onTtsChapterEnd() }
        )
    }

    fun pauseTts() {
        ttsPlayer.pause()
    }

    fun resumeTts() {
        ttsPlayer.resume()
    }

    fun stopTts(resetTimer: Boolean = false) {
        if (ttsPlayer.speaking.value || ttsPlayer.paused.value) ttsPlayer.stop()
        if (resetTimer) {
            ttsPlayer.resetTimer()
        }
    }

    private fun onTtsChapterEnd() {
        // 1. 若当前设置了“听完本章停止”，则停止朗读并重置定时模式
        if (ttsPlayer.timerMode.value == TtsTimerMode.END_OF_CHAPTER) {
            stopTts(resetTimer = true)
            return
        }

        // 2. 连播下一章：直接获取下一章实体并播放，绝不依赖异步 currentChapter 刷新
        val next = _chapterIndex.value + 1
        val list = chapters.value
        if (next in list.indices) {
            val nextChapter = list[next]
            _targetPageOnChapterLoad.value = 0
            _chapterIndex.value = next
            scheduleProgressSave()
            // 立即连播下一章，无需协程挂起延迟，确保锁屏息屏下无缝发声
            startTtsForChapter(nextChapter, 0)
        } else {
            // 已播放到全书最后一章，自动停止
            stopTts(resetTimer = true)
        }
    }

    override fun onCleared() {
        // 如果正在听书，保持后台播放器与前台服务继续运行，不因 Activity/ViewModel 回收而强杀
        val isTtsActive = ttsPlayer.speaking.value || ttsPlayer.paused.value
        if (!isTtsActive) {
            ttsPlayer.stop()
        }
        // 兜底保存进度（使用 Application 作用域协程，不阻塞主线程）
        val isThisBookTtsActive = isTtsActive && ttsPlayer.currentBookId.value == bookId
        val idx = if (isThisBookTtsActive) ttsPlayer.currentChapterIndex.value else _chapterIndex.value
        if (idx >= 0) {
            bookRepository.saveProgressAsync(bookId, idx, chapters.value.size)
        }
        super.onCleared()
    }

    fun toggleChrome() { _chromeVisible.value = !_chromeVisible.value }

    fun openToc() { _tocOpen.value = true; _chromeVisible.value = false }
    fun closeToc() { _tocOpen.value = false }
    fun openSettings() { _settingsOpen.value = true }
    fun closeSettings() { _settingsOpen.value = false }

    fun setTocFilter(filter: TocFilter) { _tocFilter.value = filter }

    fun renameChapter(chapterId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val chapter = chapters.value.firstOrNull { it.id == chapterId } ?: return@launch
            bookRepository.updateChapter(chapter.copy(title = trimmed))
        }
    }

    fun deleteChapter(chapterId: String) {
        viewModelScope.launch {
            val list = chapters.value
            if (list.none { it.id == chapterId }) return@launch
            bookRepository.deleteChapter(bookId, chapterId)
            val cur = _chapterIndex.value
            val remaining = list.filterNot { it.id == chapterId }
            if (remaining.isNotEmpty()) {
                val newIndex = if (cur >= remaining.size) remaining.lastIndex else cur
                _chapterIndex.value = newIndex
                scheduleProgressSave()
            }
        }
    }

    fun quoteParagraph(text: String, paraIndex: Int? = null) {
        _quotedText.value = text
        _quotedParaIndex.value = paraIndex
    }

    fun clearQuote() {
        _quotedText.value = null
        _quotedParaIndex.value = null
    }

    fun openParagraphComments(
        sort: String = paragraphCommentsSort.value,
        quote: String = paragraphCommentsQuote.value,
        row: Int? = paragraphCommentsRow.value ?: _quotedParaIndex.value
    ) {
        tsukkomi.open(currentChapter.value, sort = sort, quote = quote, row = row)
    }

    fun loadMoreParagraphComments() {
        tsukkomi.loadMore(currentChapter.value)
    }

    fun toggleParagraphReplies(commentId: String) {
        tsukkomi.toggleReplies(commentId)
    }

    fun closeParagraphComments() {
        tsukkomi.close()
    }

    fun playFromQuotedParagraph() {
        val idx = _quotedParaIndex.value ?: 0
        clearQuote()
        startTts(idx)
    }

    fun scheduleProgressSave() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            delay(600)
            val idx = _chapterIndex.value
            val total = chapters.value.size
            if (idx >= 0 && total > 0) bookRepository.saveProgress(bookId, idx, total)
        }
    }

    // ---- 阅读设置 ----
    fun setFontSize(size: Int) = launchPref { readingPreferencesRepository.setFontSize(size) }
    fun setLineSpacing(m: Float) = launchPref { readingPreferencesRepository.setLineSpacing(m) }
    fun setHorizontalPadding(dp: Int) = launchPref { readingPreferencesRepository.setHorizontalPadding(dp) }
    fun setParagraphSpacing(dp: Int) = launchPref { readingPreferencesRepository.setParagraphSpacing(dp) }
    fun setPageMode(mode: PageMode) = launchPref { readingPreferencesRepository.setPageMode(mode) }
    fun setBgTheme(theme: ReaderBgTheme) = launchPref { readingPreferencesRepository.setBgTheme(theme) }
    fun setSerif(serif: Boolean) = launchPref { readingPreferencesRepository.setSerif(serif) }

    private var lastLightTheme = ReaderBgTheme.WHITE

    /** 夜间模式快捷切换：亮色 ↔ 暗夜（记住离开前的亮色主题） */
    fun toggleNightMode() = launchPref {
        val cur = readingPreferencesRepository.current().bgTheme
        if (cur == ReaderBgTheme.DARK || cur == ReaderBgTheme.BLACK) {
            readingPreferencesRepository.setBgTheme(lastLightTheme)
        } else {
            lastLightTheme = cur
            readingPreferencesRepository.setBgTheme(ReaderBgTheme.DARK)
        }
    }

    private fun launchPref(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    // ---- 导出 ----
    fun exportChapter(uri: Uri?, markdown: Boolean) {
        if (uri == null) return
        val chapter = currentChapter.value ?: return
        if (chapter.content.isBlank()) {
            _exportMessage.value = "当前章节正文为空，无法导出"
            return
        }
        val targetBook = book.value ?: BookEntity(
            id = bookId,
            title = chapter.title,
            author = "",
            coverUri = null,
            sourcePath = "",
            sourceType = BookSourceType.LOCAL_TXT,
            totalChapters = chapters.value.size
        )
        val format = if (markdown) BookExportFormat.MARKDOWN else BookExportFormat.TXT
        viewModelScope.launch {
            try {
                bookRepository.exportChapterToUri(uri, targetBook, chapter, format)
                _exportMessage.value = "已导出 ${format.extension.uppercase()} 文件"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _exportMessage.value = e.message ?: "章节导出失败"
            }
        }
    }
}

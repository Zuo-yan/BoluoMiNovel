package com.novelai.assistant.ui.reader

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.db.ChapterOriginType
import com.novelai.assistant.data.prefs.PageMode
import com.novelai.assistant.data.prefs.ReaderBgTheme
import com.novelai.assistant.data.prefs.ReadingPreferencesRepository
import com.novelai.assistant.data.prefs.ReadingSettings
import com.novelai.assistant.data.repository.BookRepository
import com.novelai.assistant.data.tts.TtsMediaManager
import com.novelai.assistant.data.tts.TtsMediaActionListener
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

typealias TtsTimerMode = com.novelai.assistant.data.tts.TtsTimerMode

enum class TocFilter(val label: String) {
    ALL("全部"), ORIGINAL("仅原作"), AI("AI 衍生"), TREE("分支树")
}

data class TocItem(
    val chapter: ChapterEntity,
    val depth: Int
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val readingPreferencesRepository: ReadingPreferencesRepository,
    val ttsPlayer: com.novelai.assistant.data.tts.TtsPlayer
) : ViewModel() {

    val bookId: String = savedStateHandle["bookId"] ?: ""
    private val initialChapterIndex: Int = savedStateHandle["chapter"] ?: -1

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
    private val _paragraphComments = MutableStateFlow<List<com.novelai.assistant.data.sf.SfParagraphComment>>(emptyList())
    val paragraphComments = _paragraphComments.asStateFlow()
    private val _paragraphCommentsLoading = MutableStateFlow(false)
    val paragraphCommentsLoading = _paragraphCommentsLoading.asStateFlow()
    private val _paragraphCommentsError = MutableStateFlow<String?>(null)
    val paragraphCommentsError = _paragraphCommentsError.asStateFlow()
    private val _paragraphCommentsSort = MutableStateFlow("hot")
    val paragraphCommentsSort = _paragraphCommentsSort.asStateFlow()
    private val _paragraphCommentsOpen = MutableStateFlow(false)
    val paragraphCommentsOpen = _paragraphCommentsOpen.asStateFlow()
    private val _paragraphCommentsQuote = MutableStateFlow("")
    val paragraphCommentsQuote = _paragraphCommentsQuote.asStateFlow()
    private var paragraphCommentsJob: Job? = null

    // 目录树（分支缩进）
    val tocItems: StateFlow<List<TocItem>> =
        combine(chapters, _tocFilter) { list, filter ->
            when (filter) {
                TocFilter.ALL -> list.map { TocItem(it, 0) }
                TocFilter.ORIGINAL -> list.filter { it.originType == ChapterOriginType.ORIGINAL }.map { TocItem(it, 0) }
                TocFilter.AI -> list.filter { it.originType != ChapterOriginType.ORIGINAL }.map { TocItem(it, 0) }
                TocFilter.TREE -> buildTree(list)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var progressJob: Job? = null
    private val reload = MutableStateFlow(0)
    private val _chapterError = MutableStateFlow<String?>(null)
    val chapterError = _chapterError.asStateFlow()
    private val _chapterFailureKind = MutableStateFlow<com.novelai.assistant.data.sf.SfFailureKind?>(null)
    val chapterFailureKind = _chapterFailureKind.asStateFlow()
    fun retryChapter() { reload.value++ }

    init {
        viewModelScope.launch {
            combine(currentChapter, reload) { chapter, retry -> Triple(chapter?.id, chapter?.content?.isBlank(), retry) }
                .distinctUntilChanged().collectLatest { (id, empty, _) ->
                    _chapterError.value = null
                    _chapterFailureKind.value = null
                    if (id != null && empty == true) {
                        try { bookRepository.readableChapter(id) }
                        catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (e: Exception) {
                            _chapterFailureKind.value = (e as? com.novelai.assistant.data.sf.SfException)?.kind
                            _chapterError.value = e.message ?: "正文加载失败"
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

        // 注册系统媒体卡片/通知栏动作回调
        TtsMediaManager.setActionListener(object : TtsMediaActionListener {
            override fun onPlay() { resumeTts() }
            override fun onPause() { pauseTts() }
            override fun onSkipToNext() {
                val next = _chapterIndex.value + 1
                val list = chapters.value
                if (next in list.indices) {
                    val nextChapter = list[next]
                    _targetPageOnChapterLoad.value = 0
                    _chapterIndex.value = next
                    scheduleProgressSave()
                    startTtsForChapter(nextChapter, 0)
                }
            }
            override fun onSkipToPrevious() {
                val prev = _chapterIndex.value - 1
                val list = chapters.value
                if (prev in list.indices) {
                    val prevChapter = list[prev]
                    _targetPageOnChapterLoad.value = 0
                    _chapterIndex.value = prev
                    scheduleProgressSave()
                    startTtsForChapter(prevChapter, 0)
                }
            }
            override fun onStop() {
                stopTts(resetTimer = true)
            }
        })
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
        ttsPlayer.bindBookContext(bookId, book.value?.title ?: "", chapters.value)
        if (chapter.content.isBlank()) {
            val requestGeneration = ttsPlayer.playbackGeneration
            viewModelScope.launch {
                try {
                    val loaded = bookRepository.readableChapter(chapter.id)
                    if (requestGeneration == ttsPlayer.playbackGeneration && _chapterIndex.value == chapter.chapterIndex) startTtsForChapter(loaded, startIndex)
                }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { if (requestGeneration == ttsPlayer.playbackGeneration) { _chapterError.value = e.message ?: "本章无法朗读"; ttsPlayer.stop() } }
            }
            return
        }
        val paragraphs = Paginator.splitParagraphs(chapter.content)
        if (paragraphs.isEmpty()) {
            onTtsChapterEnd()
            return
        }
        val validIndex = startIndex.coerceIn(0, paragraphs.lastIndex)
        ttsPlayer.play(
            paragraphs = paragraphs,
            startIndex = validIndex,
            chapterIndex = chapter.chapterIndex,
            chapterTitle = chapter.title,
            onChapterEnd = { onTtsChapterEnd() }
        )
        syncMediaNotification(chapter, isPlaying = true, isPaused = false)
    }

    fun pauseTts() {
        ttsPlayer.pause()
        currentChapter.value?.let { syncMediaNotification(it, isPlaying = true, isPaused = true) }
    }

    fun resumeTts() {
        ttsPlayer.resume()
        currentChapter.value?.let { syncMediaNotification(it, isPlaying = true, isPaused = false) }
    }

    fun stopTts(resetTimer: Boolean = false) {
        if (ttsPlayer.speaking.value || ttsPlayer.paused.value) ttsPlayer.stop()
        if (resetTimer) {
            ttsPlayer.resetTimer()
        }
        TtsMediaManager.stopService(context)
    }

    private fun syncMediaNotification(chapter: ChapterEntity, isPlaying: Boolean, isPaused: Boolean) {
        val bTitle = book.value?.title ?: "小说阅读"
        TtsMediaManager.startOrUpdateService(
            context = context,
            bookId = bookId,
            bookTitle = bTitle,
            chapterTitle = chapter.title,
            chapterIndex = chapter.chapterIndex,
            totalChapters = chapters.value.size,
            isPlaying = isPlaying,
            isPaused = isPaused
        )
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
            TtsMediaManager.setActionListener(null)
            TtsMediaManager.stopService(context)
        }
        // 兜底保存进度
        val idx = if (isTtsActive) ttsPlayer.currentChapterIndex.value else _chapterIndex.value
        if (idx >= 0) {
            kotlinx.coroutines.runBlocking {
                runCatching { bookRepository.saveProgress(bookId, idx, chapters.value.size) }
            }
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
            val target = list.firstOrNull { it.id == chapterId } ?: return@launch
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
        sort: String = _paragraphCommentsSort.value,
        quote: String = _paragraphCommentsQuote.value
    ) {
        val chapter = currentChapter.value ?: return
        _paragraphCommentsQuote.value = quote
        _paragraphCommentsSort.value = sort
        _paragraphCommentsOpen.value = true
        _paragraphCommentsLoading.value = true
        _paragraphCommentsError.value = null
        paragraphCommentsJob?.cancel()
        val remoteId = sfTsukkomiChapterId(chapter.id, chapter.remoteId)
        if (remoteId == null) {
            _paragraphCommentsLoading.value = false
            _paragraphCommentsError.value = "本地章节没有可读取的菠萝包间贴"
            return
        }
        paragraphCommentsJob = viewModelScope.launch {
            try {
                val remote = bookRepository.remoteParagraphComments(remoteId, sort)
                _paragraphComments.value = remote
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                _paragraphComments.value = emptyList()
                _paragraphCommentsError.value = e.message ?: "章节间贴加载失败"
            } finally { _paragraphCommentsLoading.value = false }
        }
    }

    fun closeParagraphComments() {
        paragraphCommentsJob?.cancel()
        _paragraphCommentsOpen.value = false
        _paragraphCommentsLoading.value = false
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
        if (chapter.content.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val text = if (markdown) {
                    "# ${chapter.title}\n\n${chapter.content}"
                } else {
                    "${chapter.title}\n\n${chapter.content}"
                }
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    companion object {
        private fun buildTree(list: List<ChapterEntity>): List<TocItem> {
            val byId = list.associateBy { it.id }
            fun depth(chapter: ChapterEntity): Int {
                var d = 0
                var cur = chapter
                while (cur.parentChapterId != null) {
                    d++
                    cur = byId[cur.parentChapterId] ?: break
                    if (d > 20) break
                }
                return d
            }
            // 树形排序：原作章节依次展开，AI 章节紧跟其父章节之后
            val result = mutableListOf<TocItem>()
            val processed = mutableSetOf<String>()
            for (chapter in list) {
                if (chapter.id in processed) continue
                result.add(TocItem(chapter, depth(chapter)))
                processed.add(chapter.id)
                // 追加所有以它为父的衍生章节（递归）
                fun appendChildren(parent: ChapterEntity) {
                    val children = list.filter { it.parentChapterId == parent.id }
                    for (child in children) {
                        if (child.id in processed) continue
                        result.add(TocItem(child, depth(child)))
                        processed.add(child.id)
                        appendChildren(child)
                    }
                }
                appendChildren(chapter)
            }
            // 兜底：遗漏的章节补在末尾
            list.filter { it.id !in processed }.forEach { result.add(TocItem(it, 0)) }
            return result
        }
    }
}

/** Remote chapter IDs are separate from the local `sf:<book>:<chapter>` primary key. */
internal fun sfTsukkomiChapterId(localId: String, remoteId: String?): String? =
    remoteId?.takeIf { it.matches(Regex("\\d+")) }
        ?: localId.takeIf { it.matches(Regex("\\d+")) }

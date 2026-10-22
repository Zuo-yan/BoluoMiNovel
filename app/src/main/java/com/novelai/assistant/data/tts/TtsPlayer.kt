package com.novelai.assistant.data.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.prefs.ReadingPreferencesRepository
import com.novelai.assistant.data.repository.BookRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 听书段落块实体：
 * 支持长段拆分，维护准确的段落原序号与最后块标记
 */
data class TtsQueueItem(
    val paraIndex: Int,
    val chunkIndex: Int,
    val text: String,
    val isLastChunkOfChapter: Boolean,
    val utteranceId: String
)

/** 可选系统音色（来自设备 TTS 引擎） */
data class TtsVoiceOption(
    val name: String,
    val label: String,
    val isNetworkRequired: Boolean = false
)

/**
 * 听书播放器核心引擎：
 * 1. 采用双重缓冲预加载（Lookahead / QUEUE_ADD）流水线，消除段落间音频硬件静音空白，杜绝熄屏 Doze 切断；
 * 2. 深度接入系统音频焦点 (AUDIOFOCUS_GAIN)，确保锁屏媒体特权；
 * 3. 持有专用的局部 WakeLock 守护，确保在后台与锁屏切段时 CPU 保持唤醒；
 * 4. 具备脱离 Activity 独立在后台连播下一章并持久化进度的全自治能力。
 */
@Singleton
class TtsPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val prefs: ReadingPreferencesRepository
) {
    private var tts: TextToSpeech? = null
    private var initOk = false

    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    var playbackGeneration: Long = 0
        private set
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    /** 当前朗读到的段落序号（章节内） */
    private val _position = MutableStateFlow(0)
    val position: StateFlow<Int> = _position.asStateFlow()

    /** 朗读语速倍率（0.5 ~ 3.0） */
    private val _rate = MutableStateFlow(1.0f)
    val rate: StateFlow<Float> = _rate.asStateFlow()

    /** 当前正在听书的书籍与章节元数据 */
    private val _currentBookId = MutableStateFlow("")
    val currentBookId: StateFlow<String> = _currentBookId.asStateFlow()

    private val _currentBookTitle = MutableStateFlow("")
    val currentBookTitle: StateFlow<String> = _currentBookTitle.asStateFlow()

    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

    private val _currentChapterTitle = MutableStateFlow("")
    val currentChapterTitle: StateFlow<String> = _currentChapterTitle.asStateFlow()

    /** 可选系统音色与当前选择（null = 引擎默认音色） */
    private val _voices = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    val voices: StateFlow<List<TtsVoiceOption>> = _voices.asStateFlow()

    private val _voiceName = MutableStateFlow<String?>(null)
    val voiceName: StateFlow<String?> = _voiceName.asStateFlow()

    /** 当前正在朗读的段落原文（供悬浮胶囊与听书模块展示） */
    private val _currentParagraphText = MutableStateFlow("")
    val currentParagraphText: StateFlow<String> = _currentParagraphText.asStateFlow()

    // 听书定时：状态与倒计时收敛在全局播放器，离开阅读页后依然生效
    private val _timerMode = MutableStateFlow(TtsTimerMode.OFF)
    val timerMode: StateFlow<TtsTimerMode> = _timerMode.asStateFlow()

    private val _timerRemainingSeconds = MutableStateFlow<Int?>(null)
    val timerRemainingSeconds: StateFlow<Int?> = _timerRemainingSeconds.asStateFlow()

    private var timerJob: Job? = null

    // 播放队列与索引
    private var queue: List<TtsQueueItem> = emptyList()
    private var enqueuedCursor = 0
    private val utteranceMap = ConcurrentHashMap<String, TtsQueueItem>()
    private var rawParagraphs: List<String> = emptyList()

    // 章节缓存，支持后台自治连播
    private var chaptersCache: List<ChapterEntity> = emptyList()

    // 外部（如当前活跃的 ViewModel）注册的回调
    private var chapterEndCallback: (() -> Unit)? = null
    private var utteranceCounter = 0L

    // WakeLock 守护锁，防止息屏后 CPU 挂起
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val wakeLock: PowerManager.WakeLock? = powerManager?.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "NovelAI:TtsPlayerWakeLock"
    )?.apply {
        setReferenceCounted(false)
    }

    // 音频焦点管理
    private val audioFocusManager = TtsAudioFocusManager(
        context = context,
        onLoss = { isTransient ->
            if (isTransient) {
                // 收到临时音频丢失（如电话或导航音），暂停发音
                pause()
            } else {
                // 收到永久音频丢失（如其他音乐播放器启动），停止发音
                pause()
            }
        },
        onGain = {
            // 重新获取焦点，恢复发音
            if (_paused.value) {
                resume()
            }
        }
    )

    init {
        tts = TextToSpeech(context) { status ->
            initOk = (status == TextToSpeech.SUCCESS)
            if (initOk) {
                val audioAttrs = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .build()
                tts?.setAudioAttributes(audioAttrs)

                val result = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setSpeechRate(_rate.value)
                loadVoices()
            }
            _ready.value = initOk
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                acquireWakeLock()
                _speaking.value = true
                _paused.value = false
                if (utteranceId != null) {
                    utteranceMap[utteranceId]?.let { item ->
                        _position.value = item.paraIndex
                        _currentParagraphText.value = rawParagraphs.getOrNull(item.paraIndex).orEmpty()
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    handleUtteranceCompletion(utteranceId)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    handleUtteranceCompletion(utteranceId)
                }
            }
        })

        // 从偏好中恢复上次语速与音色（进程重启后依然生效）
        playerScope.launch {
            val savedRate = prefs.currentTtsSpeed()
            _rate.value = savedRate
            runCatching { tts?.setSpeechRate(savedRate) }
            val savedVoice = runCatching { prefs.currentTtsVoice() }.getOrNull()
            if (savedVoice != null && applyVoiceToEngine(savedVoice)) {
                _voiceName.value = savedVoice
            }
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld != true) {
                // 最长保持 2 小时，避免无限制泄漏，正常在停止/暂停时释放
                wakeLock?.acquire(2 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 调节语速，立即生效 */
    fun setRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 3.0f)
        _rate.value = clamped
        tts?.setSpeechRate(clamped)
        playerScope.launch { prefs.setTtsSpeed(clamped) }
        if (_speaking.value && !_paused.value) {
            // 语速改变时重置并从当前段重新发声
            playFromCurrentPosition()
        }
    }

    /** 加载系统 TTS 引擎可用音色（中文音色优先） */
    private fun loadVoices() {
        val engine = tts ?: return
        val list = runCatching { engine.voices }.getOrNull().orEmpty()
        _voices.value = list.map { v ->
            TtsVoiceOption(
                name = v.name,
                label = v.locale.displayName.ifBlank { v.name },
                isNetworkRequired = v.isNetworkConnectionRequired
            )
        }.sortedWith(
            compareBy(
                { !(it.label.startsWith("中") || it.label.startsWith("Chinese", true) || it.label.startsWith("cmn")) },
                { it.label },
                { it.name }
            )
        )
    }

    /** 将音色应用到引擎；找不到指定音色时保持引擎默认，返回是否成功应用 */
    private fun applyVoiceToEngine(name: String?): Boolean {
        val engine = tts ?: return false
        if (name == null) {
            runCatching { engine.voice = null }
            return true
        }
        val target = runCatching { engine.voices?.firstOrNull { it.name == name } }.getOrNull()
            ?: return false
        return runCatching {
            engine.voice = target
            true
        }.getOrDefault(false)
    }

    /**
     * 切换音色，立即生效：
     * 正在朗读时打断当前句，从当前段落开头以新音色重读；暂停中则在恢复时生效。
     */
    fun setVoice(name: String?) {
        _voiceName.value = name
        playerScope.launch { prefs.setTtsVoice(name) }
        applyVoiceToEngine(name)
        if (_speaking.value && !_paused.value) {
            playFromCurrentPosition()
        }
    }

    /** 试听指定音色：结束当前听书后播放固定短句 */
    fun auditionVoice(name: String?) {
        if (!initOk) return
        stop()
        applyVoiceToEngine(name)
        tts?.speak(AUDITION_TEXT, TextToSpeech.QUEUE_FLUSH, null, "audition_" + utteranceCounter++)
    }

    /** 设置听书定时：倒计时归零或听完本章后自动停止朗读 */
    fun setTimerMode(mode: TtsTimerMode) {
        timerJob?.cancel()
        _timerMode.value = mode
        if (mode.minutes > 0) {
            val totalSeconds = mode.minutes * 60
            _timerRemainingSeconds.value = totalSeconds
            timerJob = playerScope.launch {
                var remaining = totalSeconds
                while (remaining > 0) {
                    delay(1000)
                    remaining -= 1
                    _timerRemainingSeconds.value = remaining
                }
                stop()
                resetTimer()
            }
        } else {
            _timerRemainingSeconds.value = null
        }
    }

    /** 重置定时状态 */
    fun resetTimer() {
        timerJob?.cancel()
        timerJob = null
        _timerMode.value = TtsTimerMode.OFF
        _timerRemainingSeconds.value = null
    }

    /**
     * 绑定书籍元数据及全书章节缓存，为前台展示与后台无感连播提供支持
     */
    fun bindBookContext(bookId: String, bookTitle: String, chapters: List<ChapterEntity>) {
        if (_currentBookId.value != bookId) playbackGeneration++
        _currentBookId.value = bookId
        _currentBookTitle.value = bookTitle
        this.chaptersCache = chapters
    }

    /**
     * 开始朗读指定章节的段落序列
     */
    fun play(
        paragraphs: List<String>,
        startIndex: Int = 0,
        chapterIndex: Int = _currentChapterIndex.value,
        chapterTitle: String = _currentChapterTitle.value,
        onChapterEnd: (() -> Unit)? = null
    ) {
        if (!initOk) return
        playbackGeneration++
        chapterEndCallback = onChapterEnd
        rawParagraphs = paragraphs
        _currentChapterIndex.value = chapterIndex
        _currentChapterTitle.value = chapterTitle

        // 请求音频焦点
        audioFocusManager.requestFocus()
        acquireWakeLock()

        // 构建段落切块队列
        val items = mutableListOf<TtsQueueItem>()
        for (pIdx in startIndex until paragraphs.size) {
            val chunks = splitLongParagraph(if (com.novelai.assistant.ui.reader.ReaderIllustration.parse(paragraphs[pIdx]) != null) "插图" else paragraphs[pIdx])
            val isLastPara = (pIdx == paragraphs.lastIndex)
            for (cIdx in chunks.indices) {
                val isLastChunk = isLastPara && (cIdx == chunks.lastIndex)
                utteranceCounter++
                val uId = "utt_${pIdx}_${cIdx}_${utteranceCounter}"
                val item = TtsQueueItem(
                    paraIndex = pIdx,
                    chunkIndex = cIdx,
                    text = chunks[cIdx],
                    isLastChunkOfChapter = isLastChunk,
                    utteranceId = uId
                )
                items.add(item)
                utteranceMap[uId] = item
            }
        }

        queue = items
        enqueuedCursor = 0
        _position.value = startIndex
        _currentParagraphText.value = paragraphs.getOrNull(startIndex).orEmpty()
        _paused.value = false

        if (queue.isEmpty()) {
            _speaking.value = false
            onChapterFinished()
            return
        }

        // 停止之前的 utterance 并清空 TTS 引擎
        tts?.stop()

        // 核心双重缓冲机制：首段使用 QUEUE_FLUSH 启动，紧随其后的下一段使用 QUEUE_ADD 提前排队
        feedTtsPipeline(isInitial = true)
    }

    /**
     * 维持 TTS 引擎待播管道：始终保持引擎内有 1~2 个未播放完毕的 chunk，杜绝硬件音频设备进入空闲
     */
    private fun feedTtsPipeline(isInitial: Boolean = false) {
        val engine = tts ?: return
        if (enqueuedCursor >= queue.size) return

        if (isInitial) {
            // 第一块：清空并立即发声
            val first = queue[enqueuedCursor++]
            engine.speak(first.text, TextToSpeech.QUEUE_FLUSH, null, first.utteranceId)
            _speaking.value = true

            // 第二块：如果存在，提前 QUEUE_ADD 放入待播队列，形成无缝衔接
            if (enqueuedCursor < queue.size) {
                val second = queue[enqueuedCursor++]
                engine.speak(second.text, TextToSpeech.QUEUE_ADD, null, second.utteranceId)
            }
        } else {
            // 每次播完一块，如果队列里还有未提交的块，立即追加一块进入引擎
            if (enqueuedCursor < queue.size) {
                val next = queue[enqueuedCursor++]
                engine.speak(next.text, TextToSpeech.QUEUE_ADD, null, next.utteranceId)
            }
        }
    }

    /** 处理 utterance 播放完成 */
    private fun handleUtteranceCompletion(utteranceId: String?) {
        if (_paused.value) return
        val item = utteranceId?.let { utteranceMap.remove(it) }

        if (item?.isLastChunkOfChapter == true) {
            // 整章读完
            _speaking.value = false
            onChapterFinished()
        } else {
            if (item == null && queue.isEmpty()) {
                // 试听短句等引擎外播放结束：收尾播放态
                _speaking.value = false
                releaseWakeLock()
                audioFocusManager.abandonFocus()
            } else {
                // 读完当前块，继续补充管道中的下一块
                feedTtsPipeline(isInitial = false)
            }
        }
    }

    /** 本章读完后的处理：优先调用 ViewModel 外部回调，若无回调则走后台自治连播 */
    private fun onChapterFinished() {
        if (_timerMode.value == TtsTimerMode.END_OF_CHAPTER) {
            stop()
            resetTimer()
            return
        }
        if (chapterEndCallback != null) {
            chapterEndCallback?.invoke()
        } else {
            // 后台自治连播：ViewModel 已经离开或被销毁时自动播下一章
            playNextChapterAuto()
        }
    }

    /** 后台自治切下一章并持续朗读 */
    fun playNextChapterAuto() {
        val list = chaptersCache
        val nextIdx = _currentChapterIndex.value + 1
        if (list.isNotEmpty() && nextIdx in list.indices) {
            val nextChapter = list[nextIdx]
            val requestGeneration = playbackGeneration
            val bId = _currentBookId.value
            val bTitle = _currentBookTitle.value

            playerScope.launch {
                // 保存阅读进度到数据库
                runCatching {
                    bookRepository.saveProgress(bId, nextIdx, list.size)
                }

                // 拆分段落并播放
                val loaded = try { bookRepository.readableChapter(nextChapter.id) }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { if (playbackGeneration == requestGeneration) stop(); return@launch }
                if (playbackGeneration != requestGeneration) return@launch
                val paragraphs = splitParagraphs(loaded.content)
                play(
                    paragraphs = paragraphs,
                    startIndex = 0,
                    chapterIndex = nextIdx,
                    chapterTitle = nextChapter.title,
                    onChapterEnd = null
                )

                // 更新系统前台通知栏
                TtsMediaManager.startOrUpdateService(
                    context = context,
                    bookId = bId,
                    bookTitle = bTitle,
                    chapterTitle = nextChapter.title,
                    chapterIndex = nextIdx,
                    totalChapters = list.size,
                    isPlaying = true,
                    isPaused = false
                )
            }
        } else {
            // 到达最后一章，自动停止
            stop()
            TtsMediaManager.stopService(context)
        }
    }

    /** 后台自治切上一章 */
    fun playPreviousChapterAuto() {
        val list = chaptersCache
        val prevIdx = _currentChapterIndex.value - 1
        if (list.isNotEmpty() && prevIdx in list.indices) {
            val prevChapter = list[prevIdx]
            val requestGeneration = playbackGeneration
            val bId = _currentBookId.value
            val bTitle = _currentBookTitle.value

            playerScope.launch {
                runCatching {
                    bookRepository.saveProgress(bId, prevIdx, list.size)
                }
                val loaded = try { bookRepository.readableChapter(prevChapter.id) }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { if (playbackGeneration == requestGeneration) stop(); return@launch }
                if (playbackGeneration != requestGeneration) return@launch
                val paragraphs = splitParagraphs(loaded.content)
                play(
                    paragraphs = paragraphs,
                    startIndex = 0,
                    chapterIndex = prevIdx,
                    chapterTitle = prevChapter.title,
                    onChapterEnd = null
                )
                TtsMediaManager.startOrUpdateService(
                    context = context,
                    bookId = bId,
                    bookTitle = bTitle,
                    chapterTitle = prevChapter.title,
                    chapterIndex = prevIdx,
                    totalChapters = list.size,
                    isPlaying = true,
                    isPaused = false
                )
            }
        }
    }

    private fun playFromCurrentPosition() {
        if (rawParagraphs.isNotEmpty()) {
            play(rawParagraphs, startIndex = _position.value, onChapterEnd = chapterEndCallback)
        }
    }

    /** 暂停朗读 */
    fun pause() {
        playbackGeneration++
        if (!_speaking.value && !_paused.value) return
        _paused.value = true
        _speaking.value = false
        tts?.stop()
        releaseWakeLock()
        audioFocusManager.abandonFocus()
        TtsMediaManager.syncPlayerState(this, isPlaying = true, isPaused = true)
    }

    /** 恢复朗读 */
    fun resume() {
        if (!_paused.value) return
        _paused.value = false
        audioFocusManager.requestFocus()
        acquireWakeLock()
        playFromCurrentPosition()
        TtsMediaManager.syncPlayerState(this, isPlaying = true, isPaused = false)
    }

    /** 停止朗读并清理资源 */
    fun stop() {
        playbackGeneration++
        _paused.value = false
        _speaking.value = false
        queue = emptyList()
        utteranceMap.clear()
        enqueuedCursor = 0
        rawParagraphs = emptyList()
        _currentParagraphText.value = ""
        chapterEndCallback = null
        tts?.stop()
        releaseWakeLock()
        audioFocusManager.abandonFocus()
        TtsMediaManager.stopService(context)
    }

    /** 释放引擎 */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        initOk = false
        _ready.value = false
    }

    companion object {
        private const val MAX_CHUNK = 1500
        private const val AUDITION_TEXT = "你好，这是当前音色的试听效果"

        fun splitParagraphs(content: String): List<String> {
            return content.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        fun splitLongParagraph(text: String): List<String> {
            if (text.length <= MAX_CHUNK) return listOf(text)
            val chunks = mutableListOf<String>()
            val sentences = text.split(Regex("(?<=[。！？!?；;\n])"))
            var buf = StringBuilder()
            for (s in sentences) {
                if (buf.length + s.length > MAX_CHUNK && buf.isNotEmpty()) {
                    chunks.add(buf.toString())
                    buf = StringBuilder()
                }
                buf.append(s)
            }
            if (buf.isNotEmpty()) chunks.add(buf.toString())
            return chunks.ifEmpty { listOf(text) }
        }
    }
}

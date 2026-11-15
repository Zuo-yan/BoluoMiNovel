package com.novelai.assistant.data.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.novelai.assistant.data.db.ChapterEntity
import com.novelai.assistant.data.network.AiTtsVoice
import com.novelai.assistant.data.network.TtsSynthesisClient
import com.novelai.assistant.data.prefs.ReadingPreferencesRepository
import com.novelai.assistant.data.prefs.TtsEngineMode
import com.novelai.assistant.data.repository.AiConfigRepository
import com.novelai.assistant.data.repository.BookRepository
import com.novelai.assistant.data.repository.TaskScene
import com.novelai.assistant.data.text.ChapterTextParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
    private val prefs: ReadingPreferencesRepository,
    private val aiConfigRepository: AiConfigRepository,
    private val ttsSynthesisClient: TtsSynthesisClient,
    private val aiPipeline: AiTtsAudioPipeline
) {
    private var tts: TextToSpeech? = null
    private var initOk = false

    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    var playbackGeneration: Long = 0
        private set
    private var isFallbackMode = false
    private var fallbackJob: Job? = null
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

    /** 当前语音引擎模式（系统语音 / AI 语音模型） */
    private val _engineMode = MutableStateFlow(TtsEngineMode.SYSTEM)
    val engineMode: StateFlow<TtsEngineMode> = _engineMode.asStateFlow()

    /** 当前选中的 AI 音色 ID */
    private val _aiVoice = MutableStateFlow("")
    val aiVoice: StateFlow<String> = _aiVoice.asStateFlow()

    /** 当前服务商可用的 AI 音色列表 */
    private val _aiVoices = MutableStateFlow<List<AiTtsVoice>>(emptyList())
    val aiVoices: StateFlow<List<AiTtsVoice>> = _aiVoices.asStateFlow()

    /** AI 语音模型正在网络合成缓冲首段音频 */
    private val _aiBuffering = MutableStateFlow(false)
    val aiBuffering: StateFlow<Boolean> = _aiBuffering.asStateFlow()

    /** 云端合成异常时是否自动回退系统音色 */
    private val _aiFallbackEnabled = MutableStateFlow(true)
    val aiFallbackEnabled: StateFlow<Boolean> = _aiFallbackEnabled.asStateFlow()

    /** 听书错误或自动回退提示 */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** 当前正在朗读的段落原文（供悬浮胶囊与听书模块展示） */
    private val _currentParagraphText = MutableStateFlow("")
    val currentParagraphText: StateFlow<String> = _currentParagraphText.asStateFlow()

    /** 当前章节总段落数 */
    private val _totalParagraphs = MutableStateFlow(0)
    val totalParagraphs: StateFlow<Int> = _totalParagraphs.asStateFlow()

    // 听书定时：委托给 TtsSleepTimer 管理状态与倒计时
    private val sleepTimer = TtsSleepTimer(playerScope) { stop() }
    val timerMode: StateFlow<TtsTimerMode> = sleepTimer.timerMode
    val timerRemainingSeconds: StateFlow<Int?> = sleepTimer.timerRemainingSeconds

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
    private var activeAuditionId: String? = null
    private var pendingSavedVoice: String? = null
    private var userModifiedRate = false
    private var userModifiedVoice = false
    private var userModifiedEngine = false
    private var userModifiedAiVoice = false
    private var sessionForceSystemFallback = false
    private var activeUsingAiPipeline = false

    // WakeLock 守护锁，防止息屏后 CPU 挂起
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val wakeLock: PowerManager.WakeLock? = powerManager?.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "NovelAI:TtsPlayerWakeLock"
    )?.apply {
        setReferenceCounted(false)
    }

    // 音频焦点管理与焦点临时丢失自愈状态
    private var pausedByFocusLoss = false

    private val audioFocusManager = TtsAudioFocusManager(
        context = context,
        onLoss = { isTransient ->
            if (isTransient) {
                // 收到临时音频丢失（如电话或导航音），仅临时暂停发音避让，不主动释放系统焦点，以便挂断后自动恢复
                if (_speaking.value && !_paused.value) {
                    pausedByFocusLoss = true
                    pauseForTransientFocus()
                }
            } else {
                // 收到永久音频丢失（如其他音乐播放器启动），停止发音并主动释放焦点
                pausedByFocusLoss = false
                pause()
            }
        },
        onGain = {
            // 重新获取焦点，若此前因音频临时丢失而暂停，则自动恢复发音
            if (pausedByFocusLoss && _paused.value) {
                pausedByFocusLoss = false
                resume()
            }
        }
    )

    init {
        tts = TextToSpeech(context) { status ->
            mainHandler.post {
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
                    val voiceToApply = _voiceName.value ?: pendingSavedVoice
                    pendingSavedVoice = null
                    if (voiceToApply != null && applyVoiceToEngine(voiceToApply)) {
                        _voiceName.value = voiceToApply
                    }
                    _ready.value = true
                } else {
                    isFallbackMode = true
                    _ready.value = true
                }
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post {
                    if (utteranceId?.startsWith(AUDITION_UTTERANCE_PREFIX) == true) return@post
                    val item = utteranceId?.let { utteranceMap[it] } ?: return@post
                    acquireWakeLock()
                    _speaking.value = true
                    _paused.value = false
                    _position.value = item.paraIndex
                    _currentParagraphText.value = rawParagraphs.getOrNull(item.paraIndex).orEmpty()
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

            override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post { handleUtteranceCompletion(utteranceId) }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                mainHandler.post {
                    if (utteranceId?.startsWith(AUDITION_UTTERANCE_PREFIX) == true) {
                        finishAudition(utteranceId)
                    }
                }
            }
        })

        // 从偏好中恢复上次语速、音色、引擎模式与 AI 音色（进程重启后依然生效）
        playerScope.launch {
            val savedRate = prefs.currentTtsSpeed()
            if (!userModifiedRate) {
                _rate.value = savedRate
                if (initOk) {
                    runCatching { tts?.setSpeechRate(savedRate) }
                }
            }
            val savedVoice = runCatching { prefs.currentTtsVoice() }.getOrNull()
            if (!userModifiedVoice && savedVoice != null) {
                if (initOk) {
                    if (applyVoiceToEngine(savedVoice)) {
                        _voiceName.value = savedVoice
                    }
                } else {
                    pendingSavedVoice = savedVoice
                }
            }
            val savedEngine = runCatching { prefs.currentTtsEngineMode() }.getOrDefault(TtsEngineMode.SYSTEM)
            if (!userModifiedEngine) {
                _engineMode.value = savedEngine
            }
            val savedAiVoice = runCatching { prefs.currentAiTtsVoice() }.getOrDefault("")
            if (!userModifiedAiVoice && savedAiVoice.isNotBlank()) {
                _aiVoice.value = savedAiVoice
            }
            _aiFallbackEnabled.value = runCatching { prefs.currentAiTtsFallbackToSystem() }.getOrDefault(true)

            // 监听设置页或其他入口写入的引擎模式变化，确保配置 AI TTS 分流后立即生效
            prefs.ttsEngineModeFlow
                .distinctUntilChanged()
                .drop(1)
                .collect { mode ->
                    if (_engineMode.value != mode) {
                        sessionForceSystemFallback = false
                        _lastError.value = null
                        _engineMode.value = mode
                        if (_speaking.value && !_paused.value) {
                            playFromCurrentPosition()
                        } else if (_paused.value) {
                            aiPipeline.stop()
                            tts?.stop()
                            activeUsingAiPipeline = false
                        }
                    }
                }
        }

        // 绑定 AI TTS 音频流水线事件
        aiPipeline.setListener(object : AiTtsAudioPipeline.Listener {
            override fun onBufferingChanged(buffering: Boolean) {
                _aiBuffering.value = buffering
            }

            override fun onItemStarted(item: TtsQueueItem) {
                if (!activeUsingAiPipeline) return
                acquireWakeLock()
                _speaking.value = true
                _paused.value = false
                _position.value = item.paraIndex
                _currentParagraphText.value = rawParagraphs.getOrNull(item.paraIndex).orEmpty()
                _lastError.value = null
            }

            override fun onItemCompleted(item: TtsQueueItem) {
                if (!activeUsingAiPipeline || _paused.value) return
                if (item.isLastChunkOfChapter) {
                    onChapterFinished()
                }
            }

            override fun onItemFailed(item: TtsQueueItem, error: Throwable) {
                if (!activeUsingAiPipeline) return
                val detail = error.message?.takeIf { it.isNotBlank() } ?: "网络或服务响应异常"
                if (_aiFallbackEnabled.value) {
                    sessionForceSystemFallback = true
                    _lastError.value = "AI 语音合成失败（$detail），已自动切换系统语音续播"
                    _position.value = item.paraIndex
                    playFromCurrentPosition(keepFallback = true)
                } else {
                    _lastError.value = "AI 语音合成失败：$detail"
                    stopKeepingError()
                }
            }
        })

        // 监听 TTS 任务分流路由变化，自动刷新可用 AI 音色列表
        playerScope.launch {
            combine(aiConfigRepository.ttsRoute, aiConfigRepository.providers) { _, _ ->
                aiConfigRepository.resolveActive(TaskScene.TTS)
            }.collect { active ->
                if (active == null) {
                    _aiVoices.value = emptyList()
                } else {
                    refreshAiVoicesInternal(active.first)
                }
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
        userModifiedRate = true
        val clamped = rate.coerceIn(0.5f, 3.0f)
        _rate.value = clamped
        tts?.setSpeechRate(clamped)
        playerScope.launch { prefs.setTtsSpeed(clamped) }
        if (activeUsingAiPipeline && (_speaking.value || _paused.value)) {
            aiPipeline.updateRate(clamped)
            return
        }
        if (_speaking.value && !_paused.value) {
            // 语速改变时重置并从当前段重新发声
            playFromCurrentPosition()
        }
    }

    /** 加载系统 TTS 引擎可用音色（中文音色优先，按 name 去重避免列表 key 冲突） */
    private fun loadVoices() {
        val engine = tts ?: return
        val list = runCatching { engine.voices }.getOrNull().orEmpty()
        _voices.value = list.mapNotNull { v ->
            val rawName = runCatching { v.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val display = runCatching { v.locale?.displayName }.getOrNull().orEmpty()
            TtsVoiceOption(
                name = rawName,
                label = display.ifBlank { rawName },
                isNetworkRequired = runCatching { v.isNetworkConnectionRequired }.getOrDefault(false)
            )
        }.distinctBy { it.name }.sortedWith(
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
        userModifiedVoice = true
        pendingSavedVoice = null
        activeAuditionId = null
        _voiceName.value = name
        playerScope.launch { prefs.setTtsVoice(name) }
        applyVoiceToEngine(name)
        if (_speaking.value && !_paused.value) {
            playFromCurrentPosition()
        }
    }

    /** 试听指定音色：隔离试听 utterance，不改变朗读状态，试听结束后恢复已选音色 */
    fun auditionVoice(name: String?) {
        if (!initOk) return
        stop()
        val auditionId = "$AUDITION_UTTERANCE_PREFIX${++utteranceCounter}"
        activeAuditionId = auditionId
        applyVoiceToEngine(name)
        if (tts?.speak(AUDITION_TEXT, TextToSpeech.QUEUE_FLUSH, null, auditionId) != TextToSpeech.SUCCESS) {
            finishAudition(auditionId)
        }
    }

    /** 切换听书语音引擎模式（系统语音 / AI 语音模型） */
    fun setEngineMode(mode: TtsEngineMode) {
        userModifiedEngine = true
        sessionForceSystemFallback = false
        _lastError.value = null
        _engineMode.value = mode
        playerScope.launch { prefs.setTtsEngineMode(mode) }
        if (_speaking.value && !_paused.value) {
            playFromCurrentPosition()
        } else if (_paused.value) {
            // 暂停态切换引擎：清理旧管道音频，恢复时按新引擎从当前段续播
            aiPipeline.stop()
            tts?.stop()
            activeUsingAiPipeline = false
        }
    }

    /** 选择 AI 语音模型音色，并原子切换至 AI 引擎模式 */
    fun setAiVoice(voiceId: String) {
        userModifiedAiVoice = true
        userModifiedEngine = true
        sessionForceSystemFallback = false
        _lastError.value = null
        val trimmed = voiceId.trim()
        val modeChanged = _engineMode.value != TtsEngineMode.AI_MODEL
        _engineMode.value = TtsEngineMode.AI_MODEL
        _aiVoice.value = trimmed
        playerScope.launch {
            if (modeChanged) prefs.setTtsEngineMode(TtsEngineMode.AI_MODEL)
            prefs.setAiTtsVoice(trimmed)
        }
        if (_speaking.value && !_paused.value) {
            playFromCurrentPosition()
        } else if (_paused.value && modeChanged) {
            aiPipeline.stop()
            tts?.stop()
            activeUsingAiPipeline = false
        }
    }

    /** 设置云端失败时是否自动回退系统音色 */
    fun setAiFallbackToSystem(enabled: Boolean) {
        _aiFallbackEnabled.value = enabled
        playerScope.launch { prefs.setAiTtsFallbackToSystem(enabled) }
    }

    /** 试听指定的 AI 音色 */
    fun auditionAiVoice(voiceId: String) {
        val active = aiConfigRepository.resolveActive(TaskScene.TTS)
        if (active == null) {
            _lastError.value = "请先在「设置 -> AI 任务分流 -> AI 语音合成」中配置可用模型"
            return
        }
        // 仅暂停当前章节朗读以保留队列与进度，并请求音频焦点保证试听正常出声
        if (_speaking.value && !_paused.value) {
            pause()
        } else {
            tts?.stop()
        }
        audioFocusManager.requestFocus()
        _lastError.value = null
        val (config, model) = active
        val resolvedVoice = voiceId.trim().ifBlank { TtsSynthesisClient.defaultVoiceFor(config) }
        _aiBuffering.value = true
        aiPipeline.audition(
            config = config,
            model = model,
            voice = resolvedVoice,
            text = AI_AUDITION_TEXT,
            rate = _rate.value,
            onComplete = { _aiBuffering.value = false },
            onError = { err ->
                _aiBuffering.value = false
                _lastError.value = "试听失败：${err.message ?: "请检查模型与网络"}"
            }
        )
    }

    /** 主动刷新当前 TTS 服务商的可用音色列表 */
    fun refreshAiVoices() {
        val active = aiConfigRepository.resolveActive(TaskScene.TTS) ?: return
        playerScope.launch {
            refreshAiVoicesInternal(active.first)
        }
    }

    fun clearError() {
        _lastError.value = null
    }

    private suspend fun refreshAiVoicesInternal(config: com.novelai.assistant.data.repository.ApiProviderConfig) {
        val list = runCatching { ttsSynthesisClient.listVoices(config) }.getOrDefault(emptyList())
        _aiVoices.value = list
        if (list.isNotEmpty() && (_aiVoice.value.isBlank() || list.none { it.id.equals(_aiVoice.value, ignoreCase = true) })) {
            _aiVoice.value = list.first().id
        }
    }

    private fun finishAudition(utteranceId: String?) {
        if (utteranceId == null || utteranceId == activeAuditionId) {
            activeAuditionId = null
            applyVoiceToEngine(_voiceName.value)
        }
    }

    /** 设置听书定时：倒计时归零或听完本章后自动停止朗读 */
    fun setTimerMode(mode: TtsTimerMode) {
        sleepTimer.setMode(mode)
    }

    /** 重置定时状态 */
    fun resetTimer() {
        sleepTimer.reset()
    }

    /**
     * 绑定书籍元数据及全书章节缓存，为前台展示与后台无感连播提供支持
     */
    fun bindBookContext(bookId: String, bookTitle: String, chapters: List<ChapterEntity>) {
        if (_currentBookId.value != bookId) {
            playbackGeneration++
            sessionForceSystemFallback = false
            _lastError.value = null
        }
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
        playInternal(
            paragraphs = paragraphs,
            startIndex = startIndex,
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle,
            keepFallback = false,
            onChapterEnd = onChapterEnd
        )
    }

    private fun playInternal(
        paragraphs: List<String>,
        startIndex: Int = 0,
        chapterIndex: Int = _currentChapterIndex.value,
        chapterTitle: String = _currentChapterTitle.value,
        keepFallback: Boolean = false,
        onChapterEnd: (() -> Unit)? = null
    ) {
        if (!keepFallback) {
            sessionForceSystemFallback = false
        }
        val activeAiRoute = if (_engineMode.value == TtsEngineMode.AI_MODEL && !sessionForceSystemFallback) {
            aiConfigRepository.resolveActive(TaskScene.TTS)
        } else null
        val wantAiEngine = _engineMode.value == TtsEngineMode.AI_MODEL && !sessionForceSystemFallback

        if (wantAiEngine && activeAiRoute == null) {
            if (!_aiFallbackEnabled.value) {
                _lastError.value = "请先在「设置 -> AI 任务分流 -> AI 语音合成」中配置可用模型"
                return
            }
            _lastError.value = "未配置 AI 语音模型或服务商已禁用，已临时使用系统语音"
        }

        val useAiPipeline = wantAiEngine && activeAiRoute != null
        if (!useAiPipeline && !initOk && !isFallbackMode) return

        pausedByFocusLoss = false
        playbackGeneration++
        chapterEndCallback = onChapterEnd
        rawParagraphs = paragraphs
        _totalParagraphs.value = paragraphs.size
        _currentChapterIndex.value = chapterIndex
        _currentChapterTitle.value = chapterTitle

        if (activeAuditionId != null) {
            activeAuditionId = null
            applyVoiceToEngine(_voiceName.value)
        }

        // 请求音频焦点
        audioFocusManager.requestFocus()
        acquireWakeLock()

        // 停止旧引擎并构建段落切块队列
        tts?.stop()
        aiPipeline.stop()
        fallbackJob?.cancel()
        utteranceMap.clear()
        val chunkLimit = if (useAiPipeline) AI_MAX_CHUNK else MAX_CHUNK
        queue = buildQueue(paragraphs, startIndex, maxChunk = chunkLimit) { pIdx, cIdx ->
            "utt_${pIdx}_${cIdx}_${++utteranceCounter}"
        }
        enqueuedCursor = 0
        _position.value = startIndex
        _currentParagraphText.value = paragraphs.getOrNull(startIndex).orEmpty()
        _paused.value = false

        if (queue.isEmpty()) {
            activeUsingAiPipeline = false
            onChapterFinished()
            return
        }

        if (useAiPipeline && activeAiRoute != null) {
            activeUsingAiPipeline = true
            _speaking.value = true
            val (provider, model) = activeAiRoute
            val voiceId = _aiVoice.value.ifBlank { TtsSynthesisClient.defaultVoiceFor(provider) }
            aiPipeline.startQueue(
                items = queue,
                config = provider,
                model = model,
                voice = voiceId,
                rate = _rate.value
            )
            TtsMediaManager.startService(context)
            return
        }

        activeUsingAiPipeline = false
        _aiBuffering.value = false
        queue.forEach { utteranceMap[it.utteranceId] = it }

        // 核心双重缓冲机制：首段使用 QUEUE_FLUSH 启动，紧随其后的下一段使用 QUEUE_ADD 提前排队
        feedTtsPipeline(isInitial = true)
        if (_speaking.value) {
            TtsMediaManager.startService(context)
        }
    }

    /**
     * 维持 TTS 引擎待播管道：始终保持引擎内有 1~2 个未播放完毕的 chunk，杜绝硬件音频设备进入空闲
     */
    private fun feedTtsPipeline(isInitial: Boolean = false) {
        if (isFallbackMode) {
            _speaking.value = true
            _paused.value = false
            fallbackJob?.cancel()
            fallbackJob = playerScope.launch {
                while (_speaking.value && enqueuedCursor < queue.size) {
                    if (!_paused.value) {
                        val current = queue[enqueuedCursor++]
                        _position.value = current.paraIndex
                        _currentParagraphText.value = current.text
                        delay(2000L)
                        if (current.isLastChunkOfChapter) {
                            onChapterFinished()
                            break
                        }
                    } else {
                        delay(200L)
                    }
                }
            }
            return
        }
        val engine = tts ?: return
        if (enqueuedCursor >= queue.size) return

        val count = if (isInitial) 2 else 1
        repeat(count) { index ->
            if (enqueuedCursor >= queue.size) return
            val next = queue[enqueuedCursor++]
            val mode = if (isInitial && index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if (engine.speak(next.text, mode, null, next.utteranceId) != TextToSpeech.SUCCESS) {
                stop()
                return
            }
            _speaking.value = true
        }
    }

    /** 处理 utterance 播放完成 */
    private fun handleUtteranceCompletion(utteranceId: String?) {
        if (utteranceId?.startsWith(AUDITION_UTTERANCE_PREFIX) == true) {
            finishAudition(utteranceId)
            return
        }
        if (_paused.value) return
        val item = utteranceId?.let { utteranceMap.remove(it) } ?: return
        if (item.isLastChunkOfChapter) {
            // 整章读完
            onChapterFinished()
        } else {
            // 读完当前块，继续补充管道中的下一块
            feedTtsPipeline(isInitial = false)
        }
    }

    /** 本章读完后的处理：优先调用 ViewModel 外部回调，若无回调则走后台自治连播 */
    private fun onChapterFinished() {
        if (timerMode.value == TtsTimerMode.END_OF_CHAPTER) {
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
    fun playNextChapterAuto() = playChapterByOffset(delta = 1, stopAtBoundary = true)

    /** 后台自治切上一章 */
    fun playPreviousChapterAuto() = playChapterByOffset(delta = -1, stopAtBoundary = false)

    private fun playChapterByOffset(delta: Int, stopAtBoundary: Boolean) {
        val list = chaptersCache
        val targetIdx = _currentChapterIndex.value + delta
        if (list.isEmpty() || targetIdx !in list.indices) {
            if (stopAtBoundary) {
                stop()
            }
            return
        }

        val targetChapter = list[targetIdx]
        val requestGeneration = playbackGeneration
        val bId = _currentBookId.value

        playerScope.launch {
            runCatching {
                bookRepository.saveProgress(bId, targetIdx, list.size)
            }

            val loaded = try {
                bookRepository.readableChapter(targetChapter.id)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                if (playbackGeneration == requestGeneration) stop()
                return@launch
            }
            if (playbackGeneration != requestGeneration) return@launch

            val paragraphs = ChapterTextParser.splitParagraphs(loaded.content)
            play(
                paragraphs = paragraphs,
                startIndex = 0,
                chapterIndex = targetIdx,
                chapterTitle = targetChapter.title,
                onChapterEnd = null
            )
            TtsMediaManager.startService(context)
        }
    }

    private fun playFromCurrentPosition(keepFallback: Boolean = false) {
        if (rawParagraphs.isNotEmpty()) {
            playInternal(
                paragraphs = rawParagraphs,
                startIndex = _position.value,
                keepFallback = keepFallback,
                onChapterEnd = chapterEndCallback
            )
        }
    }

    /** 临时音频焦点避让（如来电通话），不调用 abandonFocus()，保留监听以在恢复焦点后自愈续播 */
    private fun pauseForTransientFocus() {
        playbackGeneration++
        fallbackJob?.cancel()
        _paused.value = true
        _speaking.value = false
        _aiBuffering.value = false
        if (activeUsingAiPipeline) {
            aiPipeline.pause()
        } else {
            tts?.stop()
        }
        releaseWakeLock()
    }

    /** 暂停朗读（用户主动暂停或永久丢失焦点） */
    fun pause() {
        pausedByFocusLoss = false
        playbackGeneration++
        fallbackJob?.cancel()
        if (!_speaking.value && !_paused.value) return
        _paused.value = true
        _speaking.value = false
        _aiBuffering.value = false
        if (activeUsingAiPipeline) {
            aiPipeline.pause()
        } else {
            tts?.stop()
        }
        releaseWakeLock()
        audioFocusManager.abandonFocus()
    }

    /** 恢复朗读 */
    fun resume() {
        pausedByFocusLoss = false
        if (!_paused.value) return
        _paused.value = false
        audioFocusManager.requestFocus()
        acquireWakeLock()
        if (activeUsingAiPipeline && _engineMode.value == TtsEngineMode.AI_MODEL && !sessionForceSystemFallback) {
            _speaking.value = true
            aiPipeline.resume()
            TtsMediaManager.startService(context)
            return
        }
        playFromCurrentPosition()
        TtsMediaManager.startService(context)
    }

    /** 停止朗读并清理资源 */
    fun stop() {
        _lastError.value = null
        stopInternal()
    }

    private fun stopKeepingError() {
        stopInternal()
    }

    private fun stopInternal() {
        pausedByFocusLoss = false
        sessionForceSystemFallback = false
        activeUsingAiPipeline = false
        playbackGeneration++
        fallbackJob?.cancel()
        _paused.value = false
        _speaking.value = false
        _aiBuffering.value = false
        queue = emptyList()
        utteranceMap.clear()
        enqueuedCursor = 0
        rawParagraphs = emptyList()
        _totalParagraphs.value = 0
        _currentParagraphText.value = ""
        chapterEndCallback = null
        tts?.stop()
        aiPipeline.stop()
        if (activeAuditionId != null) {
            activeAuditionId = null
            applyVoiceToEngine(_voiceName.value)
        }
        releaseWakeLock()
        audioFocusManager.abandonFocus()
        TtsMediaManager.stopService(context)
    }

    /** 释放引擎 */
    fun shutdown() {
        resetTimer()
        stop()
        tts?.shutdown()
        tts = null
        initOk = false
        _ready.value = false
    }

    companion object {
        private const val MAX_CHUNK = 1500
        internal const val AI_MAX_CHUNK = 260
        private const val AUDITION_UTTERANCE_PREFIX = "audition_"
        private const val AUDITION_TEXT = "你好，这是当前音色的试听效果"
        private const val AI_AUDITION_TEXT = "你好，这是 AI 语音模型的试听效果，祝你听书愉快。"

        internal fun buildQueue(
            paragraphs: List<String>,
            startIndex: Int,
            maxChunk: Int = MAX_CHUNK,
            nextId: (Int, Int) -> String
        ): List<TtsQueueItem> {
            val items = mutableListOf<TtsQueueItem>()
            for (pIdx in startIndex.coerceAtLeast(0) until paragraphs.size) {
                val paragraph = paragraphs[pIdx]
                if (paragraph.isBlank()) continue
                val text = if (ChapterTextParser.isIllustration(paragraph)) "插图" else paragraph
                splitLongParagraph(text, maxChunk).forEachIndexed { cIdx, chunk ->
                    if (chunk.isNotBlank()) items.add(TtsQueueItem(pIdx, cIdx, chunk, false, nextId(pIdx, cIdx)))
                }
            }
            if (items.isNotEmpty()) items[items.lastIndex] = items.last().copy(isLastChunkOfChapter = true)
            return items
        }

        fun splitLongParagraph(text: String, maxChunk: Int = MAX_CHUNK): List<String> {
            val limit = maxChunk.coerceAtLeast(50)
            if (text.length <= limit) return listOf(text)
            val chunks = mutableListOf<String>()
            val sentences = text.split(Regex("(?<=[。！？!?；;\n])"))
            var buf = StringBuilder()
            for (s in sentences) {
                if (s.length > limit) {
                    if (buf.isNotEmpty()) {
                        chunks.add(buf.toString())
                        buf = StringBuilder()
                    }
                    // 对无句末标点的超长句子按标点或定长切片，避免超出云端单次输入限制
                    var offset = 0
                    while (offset < s.length) {
                        val end = minOf(s.length, offset + limit)
                        chunks.add(s.substring(offset, end))
                        offset = end
                    }
                    continue
                }
                if (buf.length + s.length > limit && buf.isNotEmpty()) {
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

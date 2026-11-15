package com.novelai.assistant.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.util.Log
import kotlin.math.abs
import com.novelai.assistant.data.network.TtsSynthesisClient
import com.novelai.assistant.data.repository.ApiProviderConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI TTS 音频流水线控制器：
 * 1. 按需滑动窗口预取（Lookahead = 2），边播当前句边合成下 1~2 句，消除段落切换停顿；
 * 2. 磁盘音频 LRU 缓存（上限 50MB），暂停恢复、回退重听零重复扣费；
 * 3. 基于 MediaPlayer + PlaybackParams 实现真实暂停续播与 0.5x~3.0x 无级倍速。
 */
@Singleton
class AiTtsAudioPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val synthesisClient: TtsSynthesisClient
) {
    interface Listener {
        fun onBufferingChanged(buffering: Boolean)
        fun onItemStarted(item: TtsQueueItem)
        fun onItemCompleted(item: TtsQueueItem)
        fun onItemFailed(item: TtsQueueItem, error: Throwable)
    }

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefetchJob = SupervisorJob()
    private val ioScope = CoroutineScope(prefetchJob + Dispatchers.IO)
    private val synthSemaphore = Semaphore(2)

    private val cacheDir: File by lazy {
        File(context.cacheDir, "tts_audio").apply { mkdirs() }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var auditionPlayer: MediaPlayer? = null
    private var playJob: Job? = null
    private var auditionJob: Job? = null

    private var queue: List<TtsQueueItem> = emptyList()
    private var cursor: Int = 0
    private var activeConfig: ApiProviderConfig? = null
    private var activeModel: String = ""
    private var activeVoice: String = ""
    private var activeRate: Float = 1.0f
    private var isPaused: Boolean = false
    private var activeGeneration: Long = 0L
    private var listener: Listener? = null

    private val prefetchTasks = ConcurrentHashMap<String, Deferred<Result<File>>>()

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun startQueue(
        items: List<TtsQueueItem>,
        config: ApiProviderConfig,
        model: String,
        voice: String,
        rate: Float
    ) {
        stopInternal(resetCacheTasks = true)
        if (items.isEmpty()) return

        queue = items
        cursor = 0
        activeConfig = config
        activeModel = model
        activeVoice = voice
        activeRate = rate.coerceIn(0.5f, 3.0f)
        isPaused = false
        activeGeneration++

        playIndex(cursor, activeGeneration)
    }

    fun updateRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 3.0f)
        activeRate = clamped
        val mp = mediaPlayer ?: return
        runCatching {
            val wasPlaying = mp.isPlaying
            mp.playbackParams = PlaybackParams().setSpeed(clamped)
            // 部分设备上设置 PlaybackParams 会触发自动播放，若此前处于暂停态需恢复暂停
            if (!wasPlaying && isPaused && mp.isPlaying) {
                mp.pause()
            }
        }
    }

    fun pause() {
        isPaused = true
        playJob?.cancel()
        playJob = null
        listener?.onBufferingChanged(false)
        // 暂停时取消后续未完成的远端合成预取，避免用户不再继续听书却白白消耗额度
        cancelPrefetchTasks()
        runCatching {
            mediaPlayer?.let { if (it.isPlaying) it.pause() }
        }
    }

    fun resume() {
        if (!isPaused) return
        isPaused = false
        val mp = mediaPlayer
        if (mp != null) {
            runCatching {
                mp.start()
                if (abs(activeRate - 1.0f) > 0.01f) {
                    applyRate(mp, activeRate)
                }
                queue.getOrNull(cursor)?.let { listener?.onItemStarted(it) }
                scheduleLookahead(cursor + 1, activeGeneration)
                return
            }
        }
        if (cursor in queue.indices) {
            playIndex(cursor, activeGeneration)
        }
    }

    fun stop() {
        stopInternal(resetCacheTasks = true)
        stopAudition()
    }

    fun audition(
        config: ApiProviderConfig,
        model: String,
        voice: String,
        text: String,
        rate: Float,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        stopAudition()
        auditionJob = mainScope.launch {
            val result = withContext(Dispatchers.IO) {
                fetchOrLoadCachedAudio(config, model, voice, text)
            }
            result.fold(
                onSuccess = { file ->
                    playSingleFileForAudition(file, rate.coerceIn(0.5f, 3.0f), onComplete, onError)
                },
                onFailure = onError
            )
        }
    }

    fun stopAudition() {
        auditionJob?.cancel()
        auditionJob = null
        runCatching {
            auditionPlayer?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        }
        auditionPlayer = null
    }

    private fun playIndex(index: Int, generation: Long) {
        if (generation != activeGeneration || isPaused) return
        val item = queue.getOrNull(index) ?: return
        val config = activeConfig ?: return

        cursor = index
        playJob?.cancel()
        playJob = mainScope.launch {
            listener?.onBufferingChanged(true)
            // 确保当前项任务已存在，同时预取后续窗口
            val currentTask = ensureTask(index, item, config, activeModel, activeVoice)
            scheduleLookahead(index + 1, generation)

            val result = currentTask.await()
            if (generation != activeGeneration || isPaused) {
                listener?.onBufferingChanged(false)
                return@launch
            }
            listener?.onBufferingChanged(false)

            result.fold(
                onSuccess = { audioFile ->
                    startMediaPlayerForItem(item, audioFile, generation)
                },
                onFailure = { error ->
                    if (error !is CancellationException) {
                        Log.w(TAG, "AI TTS synthesis failed at index=$index: ${error.message}", error)
                        listener?.onItemFailed(item, error)
                    }
                }
            )
        }
    }

    private fun scheduleLookahead(startIndex: Int, generation: Long) {
        if (generation != activeGeneration || isPaused) return
        val config = activeConfig ?: return
        val endIndex = minOf(queue.size - 1, startIndex + LOOKAHEAD_COUNT - 1)
        for (i in startIndex..endIndex) {
            val item = queue[i]
            ensureTask(i, item, config, activeModel, activeVoice)
        }
    }

    private fun ensureTask(
        index: Int,
        item: TtsQueueItem,
        config: ApiProviderConfig,
        model: String,
        voice: String
    ): Deferred<Result<File>> {
        val key = "${activeGeneration}_${index}_${item.utteranceId}"
        return prefetchTasks.computeIfAbsent(key) {
            ioScope.async {
                fetchOrLoadCachedAudio(config, model, voice, item.text)
            }
        }
    }

    private suspend fun fetchOrLoadCachedAudio(
        config: ApiProviderConfig,
        model: String,
        voice: String,
        text: String
    ): Result<File> {
        val hash = cacheKey(CACHE_VERSION, config.id, config.baseUrl, model, voice, text)
        val existing = findCachedFile(hash)
        if (existing != null && existing.length() > 0L) {
            existing.setLastModified(System.currentTimeMillis())
            return Result.success(existing)
        }

        return synthSemaphore.withPermit {
            // 二次检查缓存（并发去重）
            val doubleCheck = findCachedFile(hash)
            if (doubleCheck != null && doubleCheck.length() > 0L) {
                return@withPermit Result.success(doubleCheck)
            }
            val synth = synthesisClient.synthesize(config, model, text, voice)
            synth.mapCatching { audio ->
                val target = File(cacheDir, "$hash.${audio.extension}")
                val temp = File(cacheDir, "$hash.${audio.extension}.tmp")
                temp.writeBytes(audio.bytes)
                if (!temp.renameTo(target)) {
                    target.writeBytes(audio.bytes)
                    temp.delete()
                }
                trimCacheIfNeeded()
                target
            }
        }
    }

    private fun findCachedFile(hash: String): File? =
        SUPPORTED_EXTENSIONS.asSequence()
            .map { ext -> File(cacheDir, "$hash.$ext") }
            .firstOrNull { it.exists() && it.length() > 0L }

    private fun startMediaPlayerForItem(item: TtsQueueItem, file: File, generation: Long) {
        if (generation != activeGeneration || isPaused) return
        releaseMainMediaPlayer()

        val mp = MediaPlayer()
        mediaPlayer = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.setOnPreparedListener { player ->
                if (generation != activeGeneration || isPaused) {
                    releaseMainMediaPlayer()
                    return@setOnPreparedListener
                }
                // 先启动播放再按需调节倍速，避免部分设备在 Prepared 状态设置 PlaybackParams 触发状态冲突
                player.start()
                if (abs(activeRate - 1.0f) > 0.01f) {
                    applyRate(player, activeRate)
                }
                listener?.onItemStarted(item)
            }
            mp.setOnCompletionListener {
                if (generation != activeGeneration || isPaused) return@setOnCompletionListener
                releaseMainMediaPlayer()
                listener?.onItemCompleted(item)
                val nextIndex = cursor + 1
                if (nextIndex < queue.size) {
                    playIndex(nextIndex, generation)
                }
            }
            mp.setOnErrorListener { _, what, extra ->
                if (generation != activeGeneration) return@setOnErrorListener true
                Log.w(TAG, "MediaPlayer error what=$what extra=$extra file=${file.name}")
                runCatching { file.delete() }
                releaseMainMediaPlayer()
                listener?.onItemFailed(item, IllegalStateException("音频播放异常 (what=$what, extra=$extra)"))
                true
            }
            mp.prepareAsync()
        } catch (t: Throwable) {
            runCatching { file.delete() }
            releaseMainMediaPlayer()
            listener?.onItemFailed(item, t)
        }
    }

    private fun playSingleFileForAudition(
        file: File,
        rate: Float,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val mp = MediaPlayer()
        auditionPlayer = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.setOnPreparedListener { player ->
                player.start()
                if (abs(rate - 1.0f) > 0.01f) {
                    applyRate(player, rate)
                }
            }
            mp.setOnCompletionListener {
                stopAudition()
                onComplete()
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "Audition MediaPlayer error what=$what extra=$extra file=${file.name}")
                runCatching { file.delete() }
                stopAudition()
                onError(IllegalStateException("试听播放失败 ($what/$extra)"))
                true
            }
            mp.prepareAsync()
        } catch (t: Throwable) {
            runCatching { file.delete() }
            stopAudition()
            onError(t)
        }
    }

    private fun applyRate(player: MediaPlayer, rate: Float) {
        runCatching {
            player.playbackParams = PlaybackParams().setSpeed(rate.coerceIn(0.5f, 3.0f))
        }
    }

    private fun stopInternal(resetCacheTasks: Boolean) {
        activeGeneration++
        isPaused = false
        playJob?.cancel()
        playJob = null
        listener?.onBufferingChanged(false)
        if (resetCacheTasks) {
            cancelPrefetchTasks()
        }
        queue = emptyList()
        cursor = 0
        releaseMainMediaPlayer()
    }

    private fun cancelPrefetchTasks() {
        prefetchJob.cancelChildren()
        prefetchTasks.clear()
    }

    private fun releaseMainMediaPlayer() {
        runCatching {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        }
        mediaPlayer = null
    }

    private fun trimCacheIfNeeded() {
        runCatching {
            val files = cacheDir.listFiles()?.filter { it.isFile } ?: return
            var totalSize = files.sumOf { it.length() }
            if (totalSize <= MAX_CACHE_BYTES) return
            val sorted = files.sortedBy { it.lastModified() }
            for (f in sorted) {
                if (totalSize <= TARGET_CACHE_BYTES) break
                val len = f.length()
                if (f.delete()) {
                    totalSize -= len
                }
            }
        }
    }

    private fun cacheKey(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach { part ->
            digest.update(part.toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "AiTts"
        private const val CACHE_VERSION = "v2"
        private const val LOOKAHEAD_COUNT = 2
        private const val MAX_CACHE_BYTES = 50L * 1024L * 1024L
        private const val TARGET_CACHE_BYTES = 35L * 1024L * 1024L
        private val SUPPORTED_EXTENSIONS = listOf("wav", "mp3", "ogg", "aac", "flac")
    }
}

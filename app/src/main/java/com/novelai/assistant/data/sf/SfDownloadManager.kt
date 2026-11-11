package com.novelai.assistant.data.sf

import com.novelai.assistant.data.db.NovelDatabase
import com.novelai.assistant.data.security.SecureStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SfDownloadTask(
    val bookId: String = "", val title: String = "", val chapterIds: List<String> = emptyList(),
    val completed: Int = 0, val failures: Map<String, String> = emptyMap(),
    val running: Boolean = false, val paused: Boolean = false, val message: String = ""
)

/** One user-visible task survives navigation; its plan and successful text survive process death. */
@Singleton
class SfDownloadManager @Inject constructor(
    private val library: SfLibraryRepository,
    private val db: NovelDatabase,
    private val storage: SecureStorage,
    private val api: SfApi
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val json = Json { ignoreUnknownKeys = true }
    private val _task = MutableStateFlow(runCatching {
        json.decodeFromString<SfDownloadTask>(storage.getPlain("sf_download_task") ?: "{}")
            .let { it.copy(running = false, paused = it.running || it.paused, message = if (it.running) "可继续上次下载，已有正文会被复用" else it.message) }
    }.getOrDefault(SfDownloadTask()))
    val task = _task.asStateFlow()
    private var job: Job? = null
    private var autoRetryJob: Job? = null
    private var generation = 0

    init {
        scope.launch {
            api.guard.state.collect { protection ->
                if (protection.blocked && (_task.value.running || autoRetryJob?.isActive == true)) {
                    autoRetryJob?.cancel()
                    pause(protection.message)
                }
            }
        }
    }

    suspend fun start(bookId: String, chapterIds: List<String>) {
        val protection = api.guard.state.value
        if (protection.blocked) throw SfException(protection.kind!!, protection.message)
        check(_task.value.running.not() && autoRetryJob?.isActive != true) { "请先暂停当前下载任务" }
        require(chapterIds.isNotEmpty()) { "请选择章节" }
        autoRetryJob?.cancel()
        job?.cancel()
        _task.value = SfDownloadTask(bookId, db.bookDao().getById(bookId)?.title.orEmpty(), chapterIds.distinct())
        resume()
    }

    fun pause(message: String = "已暂停，成功章节已保存") {
        generation++
        autoRetryJob?.cancel()
        job?.cancel()
        _task.update { it.copy(running = false, paused = true, message = message) }
        persist()
    }

    fun resume() {
        if (_task.value.chapterIds.isEmpty()) return
        autoRetryJob?.cancel()
        val protection = api.guard.state.value
        if (protection.blocked) { pause(protection.message); return }

        job?.cancel()
        val runId = ++generation
        val ids = _task.value.chapterIds

        job = scope.launch {
            val initialDownloaded = withContext(Dispatchers.IO) {
                ids.count { id -> db.chapterDao().getById(id)?.content?.isNotBlank() == true }
            }
            _task.update { it.copy(running = true, paused = false, completed = initialDownloaded, message = "正在下载") }
            persist()

            try {
                for ((index, id) in ids.withIndex()) {
                    ensureActive()
                    val chapter = withContext(Dispatchers.IO) { db.chapterDao().getById(id) }
                    if (chapter?.content?.isNotBlank() == true) {
                        if (!chapter.downloaded) {
                            withContext(Dispatchers.IO) { db.chapterDao().cacheRemote(id, chapter.content, true) }
                        }
                        continue
                    }

                    val maxChapterRetries = 4
                    var chapterSuccess = false
                    var fatalException: SfException? = null
                    var lastTransientException: Exception? = null

                    for (attempt in 1..maxChapterRetries) {
                        ensureActive()
                        try {
                            library.readable(id, pin = true)
                            chapterSuccess = true
                            break
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: SfException) {
                            when (e.kind) {
                                SfFailureKind.AUTH, SfFailureKind.RISK, SfFailureKind.CLIENT -> {
                                    fatalException = e
                                    break
                                }
                                SfFailureKind.UNPURCHASED -> {
                                    lastTransientException = e
                                    break
                                }
                                SfFailureKind.SIGNATURE, SfFailureKind.RATE_LIMIT,
                                SfFailureKind.NETWORK, SfFailureKind.INVALID, SfFailureKind.COOLDOWN -> {
                                    lastTransientException = e
                                    if (attempt < maxChapterRetries) {
                                        val chapTitle = chapter?.title?.ifBlank { "第 ${index + 1} 章" } ?: "第 ${index + 1} 章"
                                        val reason = if (e.kind == SfFailureKind.SIGNATURE) "签名校验重试" else (e.message ?: "网络重试")
                                        _task.update {
                                            it.copy(message = "$chapTitle $reason ($attempt/$maxChapterRetries)")
                                        }
                                        val delayMs = when (e.kind) {
                                            SfFailureKind.RATE_LIMIT -> (e.retryAfterMillis ?: 2500L).coerceIn(1500L, 5000L)
                                            SfFailureKind.SIGNATURE -> 1500L * attempt
                                            else -> 1200L * attempt
                                        }
                                        delay(delayMs)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            lastTransientException = e
                            if (attempt < maxChapterRetries) {
                                val chapTitle = chapter?.title?.ifBlank { "第 ${index + 1} 章" } ?: "第 ${index + 1} 章"
                                _task.update {
                                    it.copy(message = "$chapTitle 网络波动重试 ($attempt/$maxChapterRetries)")
                                }
                                delay(1200L * attempt)
                            }
                        }
                    }

                    if (fatalException != null) {
                        _task.update { it.copy(running = false, paused = true, message = fatalException.message.orEmpty()) }
                        persist()
                        return@launch
                    }

                    if (chapterSuccess) {
                        _task.update {
                            it.copy(
                                completed = it.completed + 1,
                                failures = it.failures - id,
                                message = "正在下载"
                            )
                        }
                    } else {
                        val ex = lastTransientException
                        if (ex is SfException && ex.kind == SfFailureKind.UNPURCHASED) {
                            _task.update {
                                it.copy(failures = it.failures + (id to (ex.message ?: "未购买")))
                            }
                        } else if (ex is SfException && ex.kind == SfFailureKind.INVALID) {
                            _task.update {
                                it.copy(failures = it.failures + (id to (ex.message ?: "章节数据无效")))
                            }
                        } else {
                            val errorMsg = ex?.message?.ifBlank { "网络或签名异常" } ?: "网络或签名异常"
                            scheduleAutoRetry(reason = errorMsg, delaySeconds = 5)
                            return@launch
                        }
                    }
                    persist()
                }

                val finalCompleted = withContext(Dispatchers.IO) {
                    ids.count { id -> db.chapterDao().getById(id)?.content?.isNotBlank() == true }
                }
                _task.update {
                    it.copy(
                        running = false,
                        paused = false,
                        completed = finalCompleted,
                        message = if (it.failures.isEmpty()) "下载完成：共 $finalCompleted 章"
                        else "下载完成：成功 $finalCompleted 章，未购或跳过 ${it.failures.size} 章"
                    )
                }
                persist()
            } finally {
                if (generation == runId && autoRetryJob?.isActive != true) {
                    _task.update { it.copy(running = false) }
                    persist()
                }
            }
        }
    }

    private fun scheduleAutoRetry(reason: String, delaySeconds: Int = 5) {
        autoRetryJob?.cancel()
        val retryRunId = ++generation
        autoRetryJob = scope.launch {
            try {
                for (sec in delaySeconds downTo 1) {
                    ensureActive()
                    _task.update {
                        it.copy(running = true, paused = false, message = "$reason · ${sec}秒后自动重试...")
                    }
                    delay(1000L)
                }
                ensureActive()
                _task.update { it.copy(message = "正在自动重新连接并继续下载...") }
                resume()
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    private fun persist() = storage.putPlain("sf_download_task", json.encodeToString(_task.value))
}

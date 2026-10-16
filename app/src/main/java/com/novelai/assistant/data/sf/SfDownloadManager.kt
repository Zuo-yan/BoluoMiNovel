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
    private val storage: SecureStorage
    , private val api: SfApi
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val json = Json { ignoreUnknownKeys = true }
    private val _task = MutableStateFlow(runCatching {
        json.decodeFromString<SfDownloadTask>(storage.getPlain("sf_download_task") ?: "{}")
            .let { it.copy(running = false, paused = it.running || it.paused, message = if (it.running) "可继续上次下载，已有正文会被复用" else it.message) }
    }.getOrDefault(SfDownloadTask()))
    val task = _task.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    init {
        scope.launch {
            api.guard.state.collect { protection ->
                if (protection.blocked && _task.value.running) pause(protection.message)
            }
        }
    }

    suspend fun start(bookId: String, chapterIds: List<String>) {
        val protection = api.guard.state.value
        if (protection.blocked) throw SfException(protection.kind!!, protection.message)
        check(_task.value.running.not()) { "请先暂停当前下载任务" }
        require(chapterIds.isNotEmpty()) { "请选择章节" }
        _task.value = SfDownloadTask(bookId, db.bookDao().getById(bookId)?.title.orEmpty(), chapterIds.distinct())
        resume()
    }

    fun pause(message: String = "已暂停，成功章节已保存") {
        generation++
        job?.cancel()
        _task.update { it.copy(running = false, paused = true, message = message) }
        persist()
    }

    fun resume() {
        if (_task.value.running || _task.value.chapterIds.isEmpty()) return
        val protection = api.guard.state.value
        if (protection.blocked) { pause(protection.message); return }
        val runId = ++generation
        _task.update { it.copy(running = true, paused = false, completed = 0, failures = emptyMap(), message = "正在下载") }
        persist()
        job = scope.launch {
            try {
                val ids = _task.value.chapterIds
                for (id in ids) {
                    ensureActive()
                    val hadContent = db.chapterDao().getById(id)?.content?.isNotBlank() == true
                    try {
                        library.readable(id, pin = true)
                        _task.update { it.copy(completed = it.completed + 1) }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        if (e is SfException && e.kind in listOf(SfFailureKind.AUTH, SfFailureKind.RISK,
                                SfFailureKind.RATE_LIMIT, SfFailureKind.SIGNATURE, SfFailureKind.CLIENT, SfFailureKind.COOLDOWN)) {
                            _task.update { it.copy(running = false, paused = true, message = e.message.orEmpty()) }
                            return@launch
                        }
                        _task.update { it.copy(failures = it.failures + (id to (e.message ?: "下载失败"))) }
                    }
                    persist()
                    // Actual network pacing and quotas are shared with reading/search in SfRequestGuard.
                }
                _task.update { it.copy(running = false, paused = false,
                    message = "成功 ${it.completed} 章，未购或失败 ${it.failures.size} 章") }
            } finally {
                if (generation == runId) {
                    _task.update { it.copy(running = false) }
                    persist()
                }
            }
        }
    }

    private fun persist() = storage.putPlain("sf_download_task", json.encodeToString(_task.value))
}

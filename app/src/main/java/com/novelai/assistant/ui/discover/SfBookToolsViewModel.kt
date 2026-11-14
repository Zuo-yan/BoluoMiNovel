package com.novelai.assistant.ui.discover

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.db.NovelDatabase
import com.novelai.assistant.data.sf.BookExportFormat
import com.novelai.assistant.data.sf.BookExporter
import com.novelai.assistant.data.sf.SfDownloadManager
import com.novelai.assistant.data.sf.SfSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SfBookToolsViewModel @Inject constructor(
    private val session: SfSessionRepository,
    private val db: NovelDatabase,
    val downloads: SfDownloadManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val safety = session.safety
    val task = downloads.task

    private val selectedBook = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val book = selectedBook
        .flatMapLatest { id -> if (id == null) flowOf(null) else db.bookDao().observeById(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val chapters = selectedBook
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else db.chapterDao().observeByBook(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _exporting = MutableStateFlow(false)
    val exporting = _exporting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage = _successMessage.asStateFlow()

    private var exportJob: Job? = null

    fun selectLocal(id: String) {
        if (selectedBook.value != id) {
            selectedBook.value = id
            _error.value = null
            _successMessage.value = null
        }
    }

    fun showError(message: String) {
        _successMessage.value = null
        _error.value = message
    }

    fun download(from: Int, to: Int) {
        val id = selectedBook.value ?: return
        val list = chapters.value
        if (from < 1 || to < from || to > list.size) {
            showError("章节范围应在 1 到 ${list.size} 之间")
            return
        }
        val ids = list.subList(from - 1, to).map { it.id }
        _error.value = null
        _successMessage.value = null
        viewModelScope.launch {
            try {
                downloads.start(id, ids)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(e.message ?: "无法开始下载")
            }
        }
    }

    fun export(uri: Uri, format: BookExportFormat, chapterIds: List<String>, allowPartial: Boolean) {
        // Snapshot before IO so switching the selected book cannot change an in-flight export.
        val current = book.value ?: return
        if (exportJob?.isActive == true) return
        exportJob = viewModelScope.launch {
            _exporting.value = true
            _error.value = null
            _successMessage.value = null
            try {
                val selected = withContext(Dispatchers.IO) {
                    val list = chapterIds.mapNotNull { db.chapterDao().getById(it) }
                    require(list.size == chapterIds.size && list.all { it.bookId == current.id }) {
                        "选定章节发生变化，请重新选择"
                    }
                    list
                }
                BookExporter.exportToUri(
                    contentResolver = context.contentResolver,
                    uri = uri,
                    book = current,
                    selected = selected,
                    format = format,
                    allowPartial = allowPartial
                )
                _successMessage.value = "已导出 ${format.extension.uppercase()} 文件"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: "导出失败"
            } finally {
                _exporting.value = false
            }
        }
    }

    fun acknowledgeCooldown() {
        viewModelScope.launch {
            try {
                session.acknowledgeCooldown()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: "解除暂停失败"
            }
        }
    }

    fun forceBypassSafety() {
        viewModelScope.launch { session.forceBypassSafety() }
    }
}

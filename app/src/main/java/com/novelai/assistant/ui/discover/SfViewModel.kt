package com.novelai.assistant.ui.discover

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.db.*
import com.novelai.assistant.data.sf.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SfBrowserState(
    val busy: Boolean = false, val error: String? = null,
    val shelves: List<Pair<String, String>> = emptyList(), val selectedShelf: String? = null,
    val books: List<SfShelfBook> = emptyList(), val page: Int = 0, val hasMore: Boolean = false,
    val localBookId: String? = null, val destination: String = ""
)

@HiltViewModel
class SfViewModel @Inject constructor(
    private val session: SfSessionRepository,
    private val library: SfLibraryRepository,
    private val db: NovelDatabase,
    val downloads: SfDownloadManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val account = session.account
    val localBooks = db.bookDao().observeAll().map { list -> list.filter { it.sourceType == BookSourceType.BOLUOBAO } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val task = downloads.task
    val cacheBytes = library.cacheBytes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    private val _state = MutableStateFlow(SfBrowserState())
    val state = _state.asStateFlow()
    private val selectedBook = MutableStateFlow<String?>(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val book = selectedBook.flatMapLatest { id -> if (id == null) flowOf(null) else db.bookDao().observeById(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val chapters = selectedBook.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else db.chapterDao().observeByBook(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private var operation: Job? = null
    init {
        viewModelScope.launch {
            account.collect { current -> if (current == null) _state.update {
                it.copy(shelves = emptyList(), books = emptyList(), selectedShelf = null, page = 0, hasMore = false)
            } }
        }
    }
    fun restoreSession() { if (session.hasSession && state.value.shelves.isEmpty()) action { session.validate(); refreshShelvesInternal() } }

    private fun action(block: suspend () -> Unit) {
        operation?.cancel()
        operation = viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(error = e.message ?: "操作失败") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }

    fun login(username: String, password: String) = action {
        session.login(username, password)
        refreshShelvesInternal()
    }
    fun logout() {
        operation?.cancel()
        downloads.pause()
        _state.value = SfBrowserState()
        viewModelScope.launch { session.logout() }
    }
    fun refreshShelves() = action { session.validate(); refreshShelvesInternal() }
    private suspend fun refreshShelvesInternal() {
        val shelves = session.shelves()
        _state.update { it.copy(shelves = shelves, books = emptyList(), selectedShelf = null, page = 0, hasMore = false) }
        shelves.firstOrNull()?.let { loadShelfInternal(it.first, it.second, 1) }
    }
    fun selectShelf(id: String) = action {
        val name = state.value.shelves.firstOrNull { it.first == id }?.second.orEmpty()
        _state.update { it.copy(books = emptyList(), selectedShelf = id, page = 0) }
        loadShelfInternal(id, name, 1)
    }
    fun loadMore() = action {
        val s = state.value
        val id = s.selectedShelf ?: return@action
        loadShelfInternal(id, s.shelves.firstOrNull { it.first == id }?.second.orEmpty(), s.page + 1)
    }
    private suspend fun loadShelfInternal(id: String, name: String, page: Int) {
        val books = session.shelfPage(id, name, page)
        val previous = if (page == 1) emptyList() else state.value.books
        val merged = (previous + books).distinctBy { it.id }
        _state.update { it.copy(selectedShelf = id, page = page, books = merged,
            hasMore = books.size >= 30 && (page == 1 || merged.size > previous.size)) }
    }
    fun openRemote(input: String, destination: String = "read") = action {
        val id = com.novelai.assistant.data.fetcher.SfacgFetcher.extractBookId(input) ?: error("请输入菠萝包书号或书籍链接")
        val local = db.bookDao().getById("sf:$id")?.takeIf { it.totalChapters > 0 }?.id ?: library.openBook(id)
        selectLocal(local)
        _state.update { it.copy(localBookId = local, destination = destination) }
    }
    fun localAction(id: String, destination: String) { selectLocal(id); _state.update { it.copy(localBookId = id, destination = destination) } }
    fun consumeDestination() { _state.update { it.copy(destination = "") } }
    fun refreshDirectory(id: String) = action { library.openBook(id.removePrefix("sf:")) }
    fun closeBook() { selectedBook.value = null; _state.update { it.copy(localBookId = null, destination = "") } }
    fun selectLocal(id: String) { selectedBook.value = id }
    fun clearCache() = action { library.clearReadingCache() }

    fun download(from: Int, to: Int) {
        val id = selectedBook.value ?: return
        val list = chapters.value
        if (from < 1 || to < from || to > list.size) { showError("章节范围应在 1 到 ${list.size} 之间"); return }
        val ids = list.subList(from - 1, to).map { it.id }
        viewModelScope.launch {
            try { downloads.start(id, ids) }
            catch (e: Exception) { showError(e.message ?: "无法开始下载") }
        }
    }

    fun showError(message: String) { _state.update { it.copy(error = message) } }
    fun export(uri: Uri, format: BookExportFormat, chapterIds: List<String>, allowPartial: Boolean) {
        // Snapshot before IO so switching the selected book cannot change an in-flight export.
        val current = book.value ?: return
        action {
            withContext(Dispatchers.IO) {
                val list = chapterIds.mapNotNull { db.chapterDao().getById(it) }
                require(list.size == chapterIds.size && list.all { it.bookId == current.id }) { "选定章节发生变化，请重新选择" }
                val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("无法写入所选文件")
                try { out.use { BookExporter.write(it, current, list, format, allowPartial) } }
                catch (e: Exception) { runCatching { context.contentResolver.delete(uri, null, null) }; throw e }
            }
            _state.update { it.copy(error = "已导出 ${format.extension.uppercase()} 文件") }
        }
    }
}

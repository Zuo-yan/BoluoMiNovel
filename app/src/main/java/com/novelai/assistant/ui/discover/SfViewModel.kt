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
    val wealth = session.wealth
    val safety = session.safety
    fun acknowledgeCooldown() = action { session.acknowledgeCooldown() }
    val localBooks = db.bookDao().observeAll().map { list -> list.filter { it.sourceType == BookSourceType.BOLUOBAO } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val task = downloads.task
    val cacheBytes = library.cacheBytes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    private val _state = MutableStateFlow(SfBrowserState())
    val state = _state.asStateFlow()

    // 搜索与榜单发现
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private val _searchResults = MutableStateFlow<List<SfSearchResult>>(emptyList())
    val searchResults = _searchResults.asStateFlow()
    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()
    private val _rankList = MutableStateFlow<List<SfSearchResult>>(emptyList())
    val rankList = _rankList.asStateFlow()
    private val _currentSort = MutableStateFlow("all")
    val currentSort = _currentSort.asStateFlow()
    private val _signMessage = MutableStateFlow<String?>(null)
    val signMessage = _signMessage.asStateFlow()

    // 书籍详情状态
    private val _detailBook = MutableStateFlow<SfBookDetail?>(null)
    val detailBook = _detailBook.asStateFlow()
    private val _isDetailLoading = MutableStateFlow(false)
    val isDetailLoading = _isDetailLoading.asStateFlow()

    // 官方动态系统标签列表（从接口 novels/0/sysTags 实时抓取，全量69个）
    val officialTags = MutableStateFlow<List<SfSysTag>>(emptyList())
    val categories = MutableStateFlow<List<SfCategory>>(emptyList())
    private val _browseFilter = MutableStateFlow(SfBrowseFilter())
    val browseFilter = _browseFilter.asStateFlow()
    private var optionsJob: Job? = null
    val cloudShelfMsg = MutableStateFlow<String?>(null)
    fun clearCloudShelfMsg() { cloudShelfMsg.value = null }

    // 评论状态
    val comments = MutableStateFlow<List<SfComment>>(emptyList())
    val isCommentsLoading = MutableStateFlow(false)

    // 分页加载状态
    val hasMoreBrowse = MutableStateFlow(true)
    val isBrowseLoadingMore = MutableStateFlow(false)
    private var browsePage = 1
    private var browseRevision = 0
    private var moreBrowseJob: Job? = null

    private val selectedBook = MutableStateFlow<String?>(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val book = selectedBook.flatMapLatest { id -> if (id == null) flowOf(null) else db.bookDao().observeById(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val chapters = selectedBook.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else db.chapterDao().observeByBook(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private var operation: Job? = null
    private var operationRevision = 0
    init {
        viewModelScope.launch {
            account.collect { current -> if (current == null) _state.update {
                it.copy(shelves = emptyList(), books = emptyList(), selectedShelf = null, page = 0, hasMore = false)
            } }
        }
    }
    fun restoreSession() { if (session.hasSession && state.value.shelves.isEmpty()) action { session.validate(); refreshShelvesInternal() } }

    private fun action(block: suspend () -> Unit) {
        val revision = ++operationRevision
        operation?.cancel()
        operation = viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (revision == operationRevision) _state.update { it.copy(error = e.message ?: "操作失败") } }
            finally { if (revision == operationRevision) _state.update { it.copy(busy = false) } }
        }
    }

    fun login(username: String, password: String) = action {
        session.login(username, password)
        refreshShelvesInternal()
        runCatching { session.refreshWealth() }
    }
    fun logout() {
        operation?.cancel()
        downloads.pause()
        _state.value = SfBrowserState()
        _signMessage.value = null
        viewModelScope.launch { session.logout() }
    }
    fun refreshShelves() = action {
        session.validate()
        refreshShelvesInternal()
        runCatching { session.refreshWealth() }
    }

    fun search(query: String) {
        if (query.isBlank()) { loadRank(); return }
        invalidateBrowsePage()
        browsePage = 1
        hasMoreBrowse.value = true
        _searchQuery.value = query
        _isSearching.value = true
        _searchResults.value = emptyList()
        val revision = browseRevision
        action {
            val results = session.search(query)
            if (revision != browseRevision) return@action
            _searchResults.value = results
            hasMoreBrowse.value = results.size >= 20
        }
    }

    private fun clearSearch() {
        invalidateBrowsePage()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    fun loadRank(sort: String = _currentSort.value) {
        clearSearch()
        // Filtered browsing uses the supported category endpoint sorts, rather than
        // presenting a sales/week rank that the filtered endpoint does not provide.
        _currentSort.value = if (browseFilter.value.active && sort !in listOf("month", "latest")) "month" else sort
        browsePage = 1
        hasMoreBrowse.value = true
        _rankList.value = emptyList()
        val filter = browseFilter.value
        val selectedSort = currentSort.value
        val revision = browseRevision
        action {
            val list = browsePage(filter, selectedSort, 1)
            if (revision != browseRevision) return@action
            _rankList.value = list
            hasMoreBrowse.value = list.size >= 20
        }
    }

    fun fetchOfficialTags() {
        if (optionsJob?.isActive == true || (officialTags.value.isNotEmpty() && categories.value.isNotEmpty())) return
        // Metadata must not cancel the initial book request, or be cancelled by it.
        optionsJob = viewModelScope.launch {
            try {
                if (officialTags.value.isEmpty()) officialTags.value = session.fetchOfficialTags()
                if (categories.value.isEmpty()) categories.value = session.fetchCategories()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(error = e.message ?: "筛选选项加载失败") } }
        }
    }

    fun selectCategory(categoryId: Int) {
        _browseFilter.update { it.copy(categoryId = categoryId) }
        loadRank()
    }

    fun loadMultiTagNovels(sysTagIds: List<Int>) {
        _browseFilter.update { it.copy(tagIds = sysTagIds.toSet()) }
        loadRank()
    }

    private fun invalidateBrowsePage() {
        browseRevision++
        moreBrowseJob?.cancel()
        isBrowseLoadingMore.value = false
    }

    private suspend fun browsePage(filter: SfBrowseFilter, sort: String, page: Int) =
        if (filter.active) session.browseFiltered(filter, page, if (sort == "latest") "updatetime" else "viewtimes")
        else session.browseRank(sort, page)

    fun addToCloudShelf(novelId: String) {
        viewModelScope.launch {
            try {
                val res = session.addToCloudShelf(novelId)
                cloudShelfMsg.value = res.message
            } catch (e: Exception) {
                cloudShelfMsg.value = e.message ?: "加入云端书架失败"
            }
        }
    }

    fun loadComments(novelId: String) {
        val remoteId = novelId.removePrefix("sf:")
        if (!remoteId.matches(Regex("\\d+"))) {
            comments.value = emptyList()
            return
        }
        isCommentsLoading.value = true
        viewModelScope.launch {
            try {
                val list = session.novelComments(remoteId, 1)
                comments.value = list
            } catch (_: Exception) {
                comments.value = emptyList()
            } finally {
                isCommentsLoading.value = false
            }
        }
    }

    fun loadMoreBrowse() {
        if (isBrowseLoadingMore.value || !hasMoreBrowse.value || state.value.busy) return
        val nextPage = browsePage + 1
        val revision = browseRevision
        val filter = browseFilter.value
        val sort = currentSort.value
        val searching = isSearching.value
        val query = searchQuery.value
        moreBrowseJob = viewModelScope.launch {
            isBrowseLoadingMore.value = true
            try {
                val newItems = if (searching) session.search(query, nextPage) else browsePage(filter, sort, nextPage)
                if (revision != browseRevision) return@launch
                if (newItems.isEmpty()) {
                    hasMoreBrowse.value = false
                } else {
                    browsePage = nextPage
                    if (searching) {
                        _searchResults.update { (it + newItems).distinctBy { item -> item.id } }
                    } else {
                        _rankList.update { (it + newItems).distinctBy { item -> item.id } }
                    }
                    if (newItems.size < 20) {
                        hasMoreBrowse.value = false
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (revision == browseRevision) _state.update { it.copy(error = e.message ?: "下一页加载失败") }
            } finally {
                if (revision == browseRevision) isBrowseLoadingMore.value = false
            }
        }
    }

    fun toggleBookshelf(bookId: String) {
        viewModelScope.launch {
            val current = db.bookDao().getById(bookId) ?: return@launch
            val newStatus = !current.inBookshelf
            db.bookDao().updateInBookshelf(bookId, newStatus)
        }
    }

    fun forceBypassSafety() = viewModelScope.launch {
        session.forceBypassSafety()
    }

    fun sign() = action {
        val res = session.sign()
        _signMessage.value = res.message
    }

    fun clearSignMessage() {
        _signMessage.value = null
    }

    fun refreshWealth() = action {
        session.refreshWealth()
    }
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
    fun loadBookDetail(rawId: String) {
        _isDetailLoading.value = true
        _detailBook.value = null
        selectLocal(rawId)
        viewModelScope.launch {
            try {
                val isSf = rawId.startsWith("sf:") || rawId.matches(Regex("\\d+"))
                if (isSf) {
                    val remoteId = rawId.removePrefix("sf:")
                    // 打开或同步书籍目录元数据到本地 Room
                    val localId = db.bookDao().getById("sf:$remoteId")?.takeIf { it.totalChapters > 0 }?.id
                        ?: library.openBook(remoteId)
                    selectLocal(localId)
                    // 拉取官方完整书籍详情（长简介、字数、分类、标签等）
                    val detail = session.novelDetail(remoteId)
                    val total = db.chapterDao().countByBook("sf:$remoteId")
                    _detailBook.value = detail.copy(totalChapters = if (total > 0) total else detail.totalChapters)
                } else {
                    // 本地书籍（TXT / EPUB 等）
                    selectLocal(rawId)
                    val local = db.bookDao().getById(rawId)
                    if (local != null) {
                        val chapters = db.chapterDao().getByBook(rawId)
                        val totalWords = chapters.sumOf { it.content.length.toLong() }
                        val previewIntro = chapters.firstOrNull { it.content.isNotBlank() }?.content
                            ?.lines()?.filter { it.isNotBlank() }?.take(4)?.joinToString("\n")
                            ?: "本地导入书籍，可在阅读器中畅享沉浸阅读与 AI 伴读。"
                        _detailBook.value = SfBookDetail(
                            id = local.id,
                            title = local.title,
                            author = local.author.ifBlank { "本地作品" },
                            cover = local.coverUri,
                            intro = previewIntro,
                            tags = listOf(local.category.ifBlank { "本地书库" }, local.sourceType.name),
                            typeName = "本地导入",
                            isFinish = true,
                            charCount = totalWords,
                            totalChapters = chapters.size,
                            latestChapterTitle = chapters.lastOrNull()?.title.orEmpty()
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "加载书籍详情失败") }
            } finally {
                _isDetailLoading.value = false
            }
        }
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

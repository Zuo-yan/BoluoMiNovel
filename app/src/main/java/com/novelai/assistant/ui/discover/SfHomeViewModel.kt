package com.novelai.assistant.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.db.BookSourceType
import com.novelai.assistant.data.db.NovelDatabase
import com.novelai.assistant.data.fetcher.SfacgFetcher
import com.novelai.assistant.data.sf.SfDownloadManager
import com.novelai.assistant.data.sf.SfException
import com.novelai.assistant.data.sf.SfFailureKind
import com.novelai.assistant.data.sf.SfLibraryRepository
import com.novelai.assistant.data.sf.SfSessionRepository
import com.novelai.assistant.data.sf.SfShelfBook
import com.novelai.assistant.data.sf.SfUpdateCheckStore
import com.novelai.assistant.data.sf.ShelfUpdate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SfBrowserState(
    val busy: Boolean = false,
    val error: String? = null,
    val shelves: List<Pair<String, String>> = emptyList(),
    val selectedShelf: String? = null,
    val books: List<SfShelfBook> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val localBookId: String? = null,
    val destination: String = ""
)

@HiltViewModel
class SfHomeViewModel @Inject constructor(
    private val session: SfSessionRepository,
    private val library: SfLibraryRepository,
    private val db: NovelDatabase,
    val downloads: SfDownloadManager,
    private val updateStore: SfUpdateCheckStore
) : ViewModel() {
    val account = session.account
    val wealth = session.wealth
    val safety = session.safety
    val task = downloads.task
    val updates = updateStore.updates
    val localBooks = db.bookDao().observeAll()
        .map { list -> list.filter { it.sourceType == BookSourceType.BOLUOBAO } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val cacheBytes = library.cacheBytes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _state = MutableStateFlow(SfBrowserState())
    val state = _state.asStateFlow()

    private val _signMessage = MutableStateFlow<String?>(null)
    val signMessage = _signMessage.asStateFlow()

    private val _checkingUpdates = MutableStateFlow(false)
    val checkingUpdates = _checkingUpdates.asStateFlow()

    private var shelfJob: Job? = null
    private var shelfRevision = 0
    private var wealthJob: Job? = null
    private var signJob: Job? = null
    private var openRemoteJob: Job? = null
    private var updateJob: Job? = null
    private var cacheJob: Job? = null

    init {
        viewModelScope.launch {
            account.collect { current ->
                if (current == null) {
                    _state.update {
                        it.copy(
                            shelves = emptyList(),
                            books = emptyList(),
                            selectedShelf = null,
                            page = 0,
                            hasMore = false
                        )
                    }
                }
            }
        }
    }

    private fun launchShelfAction(block: suspend () -> Unit) {
        val revision = ++shelfRevision
        shelfJob?.cancel()
        shelfJob = viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (revision == shelfRevision) {
                    _state.update { it.copy(error = e.message ?: "操作失败") }
                }
            } finally {
                if (revision == shelfRevision) {
                    _state.update { it.copy(busy = false) }
                }
            }
        }
    }

    fun restoreSession() {
        if (session.hasSession && state.value.shelves.isEmpty() && shelfJob?.isActive != true) {
            launchShelfAction {
                session.validate()
                refreshShelvesInternal()
            }
        }
    }

    fun login(username: String, password: String) = launchShelfAction {
        session.login(username, password)
        refreshShelvesInternal()
        runCatching { session.refreshWealth() }
    }

    fun logout() {
        shelfRevision++
        shelfJob?.cancel()
        wealthJob?.cancel()
        signJob?.cancel()
        openRemoteJob?.cancel()
        updateJob?.cancel()
        downloads.pause()
        _state.value = SfBrowserState()
        _signMessage.value = null
        viewModelScope.launch { session.logout() }
    }

    fun refreshShelves() = launchShelfAction {
        session.validate()
        refreshShelvesInternal()
        runCatching { session.refreshWealth() }
    }

    fun selectShelf(id: String) = launchShelfAction {
        val name = state.value.shelves.firstOrNull { it.first == id }?.second.orEmpty()
        _state.update { it.copy(books = emptyList(), selectedShelf = id, page = 0) }
        loadShelfInternal(id, name, 1)
    }

    fun loadMore() = launchShelfAction {
        val s = state.value
        val id = s.selectedShelf ?: return@launchShelfAction
        loadShelfInternal(id, s.shelves.firstOrNull { it.first == id }?.second.orEmpty(), s.page + 1)
    }

    private suspend fun refreshShelvesInternal() {
        val shelves = session.shelves()
        _state.update {
            it.copy(shelves = shelves, books = emptyList(), selectedShelf = null, page = 0, hasMore = false)
        }
        shelves.firstOrNull()?.let { loadShelfInternal(it.first, it.second, 1) }
    }

    private suspend fun loadShelfInternal(id: String, name: String, page: Int) {
        val books = session.shelfPage(id, name, page)
        val previous = if (page == 1) emptyList() else state.value.books
        val merged = (previous + books).distinctBy { it.id }
        _state.update {
            it.copy(
                selectedShelf = id,
                page = page,
                books = merged,
                hasMore = books.size >= 30 && (page == 1 || merged.size > previous.size)
            )
        }
    }

    fun refreshWealth() {
        if (wealthJob?.isActive == true) return
        wealthJob = viewModelScope.launch {
            _state.update { it.copy(error = null) }
            try {
                session.refreshWealth()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "刷新资产失败") }
            }
        }
    }

    fun sign() {
        if (signJob?.isActive == true) return
        signJob = viewModelScope.launch {
            try {
                val res = session.sign()
                _signMessage.value = res.message
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _signMessage.value = e.message ?: "签到失败"
            }
        }
    }

    fun clearSignMessage() {
        _signMessage.value = null
    }

    fun checkUpdates() {
        if (_checkingUpdates.value) return
        val ids = (localBooks.value.map { it.id } + state.value.books.map { "sf:${it.id}" }).distinct()
        _checkingUpdates.value = true
        updateJob = viewModelScope.launch {
            try {
                for (id in ids) {
                    ensureActive()
                    val old = db.chapterDao().getByBook(id).map { it.id }.toSet()
                    try {
                        val detail = session.novelDetail(id.removePrefix("sf:"))
                        library.openBook(id.removePrefix("sf:"))
                        val chapters = db.chapterDao().getByBook(id).filter { it.remoteId != null }
                        val current = db.bookDao().getById(id)
                        val added = if (old.isEmpty()) emptyList() else chapters.filter { it.id !in old }.map { it.id }
                        val previous = updates.value[id]?.added.orEmpty()
                        updateStore.put(
                            id,
                            ShelfUpdate(
                                latest = detail.latestChapterTitle.ifBlank { chapters.lastOrNull()?.title.orEmpty() },
                                time = detail.lastUpdateTime.replace("T", " "),
                                unread = chapters.count { c ->
                                    c.chapterIndex > (current?.takeIf { it.lastReadAt > 0 }?.currentReadingChapterIndex ?: -1)
                                },
                                added = (previous + added).distinct(),
                                checkedAt = System.currentTimeMillis()
                            )
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        updateStore.put(
                            id,
                            (updates.value[id] ?: ShelfUpdate()).copy(error = e.message ?: "检查失败")
                        )
                        if (e is SfException && e.kind in listOf(
                                SfFailureKind.RISK,
                                SfFailureKind.RATE_LIMIT,
                                SfFailureKind.SIGNATURE,
                                SfFailureKind.CLIENT,
                                SfFailureKind.COOLDOWN
                            )
                        ) {
                            break
                        }
                    }
                }
            } finally {
                _checkingUpdates.value = false
            }
        }
    }

    fun cancelUpdateCheck() {
        updateJob?.cancel()
    }

    fun downloadUpdates(id: String) {
        viewModelScope.launch {
            try {
                val added = updates.value[id]?.added.orEmpty().toSet()
                val targets = db.chapterDao().getByBook(id).filter { it.id in added && !it.downloaded }.map { it.id }
                if (targets.isEmpty()) error("没有待下载的新增章节，请先检查更新")
                downloads.start(id, targets)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "无法下载新增章节") }
            }
        }
    }

    fun openRemote(input: String, destination: String = "read") {
        openRemoteJob?.cancel()
        openRemoteJob = viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val id = SfacgFetcher.extractBookId(input) ?: error("请输入菠萝包书号或书籍链接")
                val local = db.bookDao().getById("sf:$id")?.takeIf { it.totalChapters > 0 }?.id
                    ?: library.openBook(id)
                _state.update { it.copy(localBookId = local, destination = destination) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "打开书籍失败") }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun localAction(id: String, destination: String) {
        _state.update { it.copy(localBookId = id, destination = destination) }
    }

    fun consumeDestination() {
        _state.update { it.copy(destination = "") }
    }

    fun clearCache() {
        if (cacheJob?.isActive == true) return
        cacheJob = viewModelScope.launch {
            try {
                library.clearReadingCache()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "清理缓存失败") }
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
                _state.update { it.copy(error = e.message ?: "解除暂停失败") }
            }
        }
    }

    fun forceBypassSafety() {
        viewModelScope.launch { session.forceBypassSafety() }
    }
}

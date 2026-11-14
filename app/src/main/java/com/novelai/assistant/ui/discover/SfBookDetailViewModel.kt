package com.novelai.assistant.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.db.NovelDatabase
import com.novelai.assistant.data.sf.SfAuthorPortfolio
import com.novelai.assistant.data.sf.SfBookDetail
import com.novelai.assistant.data.sf.SfComment
import com.novelai.assistant.data.sf.SfDownloadManager
import com.novelai.assistant.data.sf.SfLibraryRepository
import com.novelai.assistant.data.sf.SfSearchResult
import com.novelai.assistant.data.sf.SfSessionRepository
import com.novelai.assistant.data.sf.SfUpdateCheckStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

internal fun mergeSfComments(
    existing: List<SfComment>,
    nextPage: List<SfComment>
): List<SfComment> = (existing + nextPage).distinctBy { it.id }

internal const val SF_COMMENTS_PAGE_SIZE = 20

@HiltViewModel
class SfBookDetailViewModel @Inject constructor(
    private val session: SfSessionRepository,
    private val library: SfLibraryRepository,
    private val db: NovelDatabase,
    private val downloads: SfDownloadManager,
    updateStore: SfUpdateCheckStore
) : ViewModel() {
    val updates = updateStore.updates

    private val _detailBook = MutableStateFlow<SfBookDetail?>(null)
    val detailBook = _detailBook.asStateFlow()

    private val _isDetailLoading = MutableStateFlow(false)
    val isDetailLoading = _isDetailLoading.asStateFlow()

    private val _detailError = MutableStateFlow<String?>(null)
    val detailError = _detailError.asStateFlow()

    val cloudShelfMsg = MutableStateFlow<String?>(null)
    fun clearCloudShelfMsg() { cloudShelfMsg.value = null }

    // 评论状态
    val comments = MutableStateFlow<List<SfComment>>(emptyList())
    val isCommentsLoading = MutableStateFlow(false)
    val isCommentsLoadingMore = MutableStateFlow(false)
    val commentsHasMore = MutableStateFlow(false)
    val commentsError = MutableStateFlow<String?>(null)
    private var commentsBookId: String? = null
    private var commentsPage = 0
    private var commentsJob: Job? = null

    // 作者作品集状态
    val authorNovels = MutableStateFlow<List<SfSearchResult>>(emptyList())
    val isAuthorLoading = MutableStateFlow(false)
    val authorError = MutableStateFlow<String?>(null)
    val authorPortfolio = MutableStateFlow<SfAuthorPortfolio?>(null)
    private var authorJob: Job? = null

    // 目录页状态
    private val _directoryBusy = MutableStateFlow(false)
    val directoryBusy = _directoryBusy.asStateFlow()
    private val _directoryError = MutableStateFlow<String?>(null)
    val directoryError = _directoryError.asStateFlow()
    private var directoryJob: Job? = null

    private var detailJob: Job? = null
    private var cloudShelfJob: Job? = null

    private val selectedBook = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val book = selectedBook
        .flatMapLatest { id -> if (id == null) flowOf(null) else db.bookDao().observeById(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val chapters = selectedBook
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else db.chapterDao().observeByBook(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectLocal(id: String) {
        selectedBook.value = id
    }

    fun loadBookDetail(rawId: String) {
        detailJob?.cancel()
        _isDetailLoading.value = true
        _detailError.value = null
        _detailBook.value = null
        selectLocal(rawId)
        detailJob = viewModelScope.launch {
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
                        val localChapters = db.chapterDao().getByBook(rawId)
                        val totalWords = localChapters.sumOf { it.content.length.toLong() }
                        val previewIntro = localChapters.firstOrNull { it.content.isNotBlank() }?.content
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
                            totalChapters = localChapters.size,
                            latestChapterTitle = localChapters.lastOrNull()?.title.orEmpty()
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _detailError.value = e.message ?: "加载书籍详情失败"
            } finally {
                _isDetailLoading.value = false
            }
        }
    }

    fun addToCloudShelf(novelId: String) {
        if (cloudShelfJob?.isActive == true) return
        cloudShelfJob = viewModelScope.launch {
            try {
                val res = session.addToCloudShelf(novelId)
                cloudShelfMsg.value = res.message
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                cloudShelfMsg.value = e.message ?: "加入云端书架失败"
            }
        }
    }

    fun toggleBookshelf(bookId: String) {
        viewModelScope.launch {
            val current = db.bookDao().getById(bookId) ?: return@launch
            db.bookDao().updateInBookshelf(bookId, !current.inBookshelf)
        }
    }

    fun loadComments(novelId: String) {
        val remoteId = novelId.removePrefix("sf:")
        commentsJob?.cancel()
        commentsBookId = remoteId
        commentsPage = 0
        comments.value = emptyList()
        commentsHasMore.value = false
        commentsError.value = null
        if (!remoteId.matches(Regex("\\d+"))) {
            return
        }
        isCommentsLoading.value = true
        commentsJob = viewModelScope.launch {
            try {
                val list = session.novelComments(remoteId, 1)
                if (commentsBookId != remoteId) return@launch
                comments.value = mergeSfComments(emptyList(), list)
                commentsPage = 1
                commentsHasMore.value = list.size >= SF_COMMENTS_PAGE_SIZE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (commentsBookId == remoteId) {
                    commentsError.value = e.message ?: "书评加载失败"
                }
            } finally {
                if (commentsBookId == remoteId) isCommentsLoading.value = false
            }
        }
    }

    fun loadMoreComments() {
        if (isCommentsLoading.value || isCommentsLoadingMore.value || !commentsHasMore.value) return
        val remoteId = commentsBookId ?: return
        val nextPage = commentsPage + 1
        commentsJob = viewModelScope.launch {
            isCommentsLoadingMore.value = true
            commentsError.value = null
            try {
                val list = session.novelComments(remoteId, nextPage)
                if (commentsBookId != remoteId) return@launch
                val before = comments.value
                val merged = mergeSfComments(before, list)
                comments.value = merged
                commentsPage = nextPage
                // A short or duplicate-only page marks the end. This prevents
                // an infinite "load more" loop on an unsupported cursor.
                commentsHasMore.value = list.size >= SF_COMMENTS_PAGE_SIZE && merged.size > before.size
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (commentsBookId == remoteId) commentsError.value = e.message ?: "更多书评加载失败"
            } finally {
                if (commentsBookId == remoteId) isCommentsLoadingMore.value = false
            }
        }
    }

    fun loadAuthor(bookId: String) {
        authorJob?.cancel()
        if (!bookId.matches(Regex("\\d+"))) {
            authorNovels.value = emptyList()
            authorPortfolio.value = null
            authorError.value = "此作品没有可用于读取作者作品集的书号"
            return
        }
        authorNovels.value = emptyList()
        authorPortfolio.value = null
        authorError.value = null
        isAuthorLoading.value = true
        authorJob = viewModelScope.launch {
            try {
                val portfolio = session.authorPortfolio(bookId)
                authorPortfolio.value = portfolio
                // The public page carries titles and stable book IDs. The App detail
                // endpoint supplies cover/metadata; one malformed work does not erase others.
                authorNovels.value = coroutineScope {
                    portfolio.works.map { work ->
                        async {
                            runCatching { session.novelDetail(work.id) }.getOrNull()?.let { detail ->
                                SfSearchResult(
                                    id = work.id,
                                    title = detail.title.ifBlank { work.title },
                                    author = detail.author.ifBlank { portfolio.name },
                                    cover = detail.cover,
                                    intro = detail.intro,
                                    tags = detail.tags,
                                    point = detail.point,
                                    markCount = detail.markCount,
                                    viewTimes = detail.viewTimes
                                )
                            } ?: SfSearchResult(work.id, work.title, portfolio.name, null)
                        }
                    }.awaitAll().distinctBy { it.id }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                authorError.value = e.message ?: "作者作品加载失败"
            } finally {
                isAuthorLoading.value = false
            }
        }
    }

    fun refreshDirectory(id: String) {
        directoryJob?.cancel()
        directoryJob = viewModelScope.launch {
            _directoryBusy.value = true
            _directoryError.value = null
            try {
                library.openBook(id.removePrefix("sf:"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _directoryError.value = e.message ?: "刷新目录失败"
            } finally {
                _directoryBusy.value = false
            }
        }
    }

    fun downloadUpdates(id: String) {
        viewModelScope.launch {
            _directoryError.value = null
            try {
                val added = updates.value[id]?.added.orEmpty().toSet()
                val targets = db.chapterDao().getByBook(id).filter { it.id in added && !it.downloaded }.map { it.id }
                if (targets.isEmpty()) error("没有待下载的新增章节，请先检查更新")
                downloads.start(id, targets)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _directoryError.value = e.message ?: "无法下载新增章节"
            }
        }
    }
}

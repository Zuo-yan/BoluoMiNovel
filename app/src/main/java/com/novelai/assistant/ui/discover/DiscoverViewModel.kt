package com.novelai.assistant.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.db.BookSourceType
import com.novelai.assistant.data.fetcher.FetchedBook
import com.novelai.assistant.data.fetcher.FanqieFetcher
import com.novelai.assistant.data.fetcher.SfacgFetcher
import com.novelai.assistant.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FetchSource(val label: String, val hint: String) {
    SFACG("菠萝包 SF", "输入书籍数字 ID 或 book.sfacg.com/Novel/xxx 链接"),
    FANQIE("番茄小说", "输入书籍数字 ID 或 fanqienovel.com/page/xxx 链接")
}

sealed interface DiscoverUiState {
    data object Idle : DiscoverUiState
    data object Loading : DiscoverUiState
    data class Loaded(val book: FetchedBook) : DiscoverUiState
    data class Error(val message: String) : DiscoverUiState
    data class Imported(val title: String, val chapters: Int) : DiscoverUiState
}

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val sfacgFetcher: SfacgFetcher,
    private val fanqieFetcher: FanqieFetcher,
    private val bookRepository: BookRepository,
    private val secureStorage: com.novelai.assistant.data.security.SecureStorage
) : ViewModel() {

    private val _source = MutableStateFlow(FetchSource.SFACG)
    val source: StateFlow<FetchSource> = _source.asStateFlow()

    val input = MutableStateFlow("")

    private val _state = MutableStateFlow<DiscoverUiState>(DiscoverUiState.Idle)
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    private val _importProgress = MutableStateFlow<Int?>(null)
    val importProgress: StateFlow<Int?> = _importProgress.asStateFlow()

    private val _maxChapters = MutableStateFlow(0) // 0 = 全部
    val maxChapters: StateFlow<Int> = _maxChapters.asStateFlow()

    private val _relay = MutableStateFlow(FanqieFetcher.DEFAULT_RELAY)
    val relay: StateFlow<String> = _relay.asStateFlow()

    private var fetchJob: Job? = null

    fun cancelImport() {
        fetchJob?.cancel()
        _importProgress.value = null
    }

    init {
        _relay.value = secureStorage.getPlain("fanqie_relay") ?: FanqieFetcher.DEFAULT_RELAY
        fanqieFetcher.relayBaseUrl = _relay.value
        setSource(FetchSource.SFACG)
    }

    fun resetRelay() = setRelay(FanqieFetcher.DEFAULT_RELAY)

    fun setSource(s: FetchSource) {
        _source.value = s
        _state.value = DiscoverUiState.Idle
        fetchJob?.cancel()
        if (s == FetchSource.FANQIE) fanqieFetcher.relayBaseUrl = _relay.value
    }

    fun setMaxChapters(n: Int) { _maxChapters.value = n }

    fun setRelay(url: String) {
        _relay.value = url.trim()
        fanqieFetcher.relayBaseUrl = _relay.value
        secureStorage.putPlain("fanqie_relay", _relay.value)
    }

    fun fetch() {
        val query = input.value.trim()
        if (query.isBlank()) {
            _state.value = DiscoverUiState.Error("请先输入书籍 ID 或链接")
            return
        }
        fetchJob?.cancel()
        _state.value = DiscoverUiState.Loading
        fetchJob = viewModelScope.launch {
            val result = when (_source.value) {
                FetchSource.SFACG -> sfacgFetcher.fetchBook(query)
                FetchSource.FANQIE -> fanqieFetcher.fetchBook(query)
            }
            _state.value = result.fold(
                onSuccess = { DiscoverUiState.Loaded(it) },
                onFailure = { DiscoverUiState.Error(it.message ?: "抓取失败") }
            )
        }
    }

    fun import() {
        val book = (_state.value as? DiscoverUiState.Loaded)?.book ?: return
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            val limit = _maxChapters.value
            val chapters = book.chapters.take(if (limit > 0) limit else book.chapters.size)
            val imported = mutableListOf<Pair<String, String>>()
            try {
                chapters.forEachIndexed { i, ch ->
                    _importProgress.value = i
                    val content = when (_source.value) {
                        FetchSource.SFACG -> sfacgFetcher.fetchChapterContent(ch.url)
                        FetchSource.FANQIE -> fanqieFetcher.fetchChapterContent(ch.url)
                    }.getOrThrow()
                    check(content.any { it.isNotBlank() }) { "本章没有取得正文" }
                    imported.add(ch.title to content.joinToString("\n\n"))
                }
                val id = bookRepository.importFetched(
                    title = book.title,
                    author = book.author,
                    sourceType = if (_source.value == FetchSource.SFACG) BookSourceType.BOLUOBAO else BookSourceType.FANQIE,
                    chapters = imported,
                    coverUrl = book.coverUrl,
                    sourceBookId = book.bookId
                )
                _state.value = DiscoverUiState.Imported(book.title, imported.size)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.value = DiscoverUiState.Error("导入中断：${t.message}")
            } finally {
                _importProgress.value = null
            }
        }
    }
}

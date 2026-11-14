package com.novelai.assistant.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.sf.SfBrowseFilter
import com.novelai.assistant.data.sf.SfCategory
import com.novelai.assistant.data.sf.SfSearchResult
import com.novelai.assistant.data.sf.SfSessionRepository
import com.novelai.assistant.data.sf.SfSysTag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SfBrowseUiState(
    val busy: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SfBrowseViewModel @Inject constructor(
    private val session: SfSessionRepository
) : ViewModel() {
    val safety = session.safety

    private val _state = MutableStateFlow(SfBrowseUiState())
    val state = _state.asStateFlow()

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

    val officialTags = MutableStateFlow<List<SfSysTag>>(emptyList())
    val categories = MutableStateFlow<List<SfCategory>>(emptyList())

    private val _browseFilter = MutableStateFlow(SfBrowseFilter())
    val browseFilter = _browseFilter.asStateFlow()

    val hasMoreBrowse = MutableStateFlow(true)
    val isBrowseLoadingMore = MutableStateFlow(false)

    private var browsePage = 1
    private var browseRevision = 0
    private var browseJob: Job? = null
    private var moreBrowseJob: Job? = null
    private var optionsJob: Job? = null

    fun search(query: String) {
        if (query.isBlank()) {
            loadRank()
            return
        }
        invalidateBrowsePage()
        browsePage = 1
        hasMoreBrowse.value = true
        _searchQuery.value = query
        _isSearching.value = true
        _searchResults.value = emptyList()
        val revision = browseRevision
        browseJob?.cancel()
        browseJob = viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val results = session.search(query)
                if (revision != browseRevision) return@launch
                _searchResults.value = results
                hasMoreBrowse.value = results.size >= 20
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (revision == browseRevision) {
                    _state.update { it.copy(error = e.message ?: "搜索失败") }
                }
            } finally {
                if (revision == browseRevision) {
                    _state.update { it.copy(busy = false) }
                }
            }
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
        browseJob?.cancel()
        browseJob = viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val list = browsePage(filter, selectedSort, 1)
                if (revision != browseRevision) return@launch
                _rankList.value = list
                hasMoreBrowse.value = list.size >= 20
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (revision == browseRevision) {
                    _state.update { it.copy(error = e.message ?: "作品列表加载失败") }
                }
            } finally {
                if (revision == browseRevision) {
                    _state.update { it.copy(busy = false) }
                }
            }
        }
    }

    fun fetchOfficialTags() {
        if (optionsJob?.isActive == true || (officialTags.value.isNotEmpty() && categories.value.isNotEmpty())) return
        // Metadata must not cancel the initial book request, or be cancelled by it.
        optionsJob = viewModelScope.launch {
            try {
                if (officialTags.value.isEmpty()) officialTags.value = session.fetchOfficialTags()
                if (categories.value.isEmpty()) categories.value = session.fetchCategories()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "筛选选项加载失败") }
            }
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (revision == browseRevision) {
                    _state.update { it.copy(error = e.message ?: "下一页加载失败") }
                }
            } finally {
                if (revision == browseRevision) isBrowseLoadingMore.value = false
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

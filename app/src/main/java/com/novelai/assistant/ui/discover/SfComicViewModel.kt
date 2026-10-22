package com.novelai.assistant.ui.discover

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.sf.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SfComicBrowseState(
    val items: List<SfComic> = emptyList(), val loading: Boolean = false, val error: String? = null,
    val categoryId: Int? = null, val page: Int = 0, val lastPage: Int? = null,
    val hasMore: Boolean = false, val search: String = ""
)

data class SfComicDetailState(
    val info: SfComicInfo? = null, val pages: List<String> = emptyList(),
    val loading: Boolean = false, val error: String? = null,
    val currentChapterIndex: Int = 0,
    val loadedChapterIndex: Int? = null,
    val endReached: Boolean = false,
    val readyForNextChapter: Boolean = false,
    val endAnchorIndex: Int? = null
)

@HiltViewModel
class SfComicViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle, private val client: KtorSfComicClient
) : ViewModel() {
    val slug: String? = savedStateHandle.get<String>("slug")?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,20}")) }
    private val browse = MutableStateFlow(SfComicBrowseState())
    val browseState = browse.asStateFlow()
    private val detail = MutableStateFlow(SfComicDetailState())
    val detailState = detail.asStateFlow()
    private var browseJob: Job? = null
    private var pageJob: Job? = null
    private var revision = 0
    private var optionsLoaded = false

    init { if (slug == null) load(refresh = true) else loadDetail() }

    fun load(refresh: Boolean = false, search: String = browse.value.search, categoryId: Int? = browse.value.categoryId) {
        if (!refresh && (browse.value.loading || (browse.value.page > 0 && !browse.value.hasMore))) return
        if (refresh) browseJob?.cancel()
        val generation = ++revision
        optionsLoaded = true
        val previous = if (refresh) SfComicBrowseState(categoryId = categoryId, search = search) else
            browse.value.copy(categoryId = categoryId, search = search)
        val page = previous.page + 1
        browse.value = previous.copy(loading = true, error = null)
        browseJob = viewModelScope.launch {
            try {
                val catalogPage = if (search.isBlank()) client.catalog(categoryId, page)
                else SfComicCatalogPage(client.search(search), null)
                val found = catalogPage.comics
                if (generation != revision) return@launch
                val merged = (previous.items + found).distinctBy { it.slug }
                browse.value = previous.copy(items = merged, loading = false, page = page,
                    lastPage = catalogPage.lastPage ?: previous.lastPage,
                    hasMore = if (search.isBlank()) {
                        found.isNotEmpty() && merged.size > previous.items.size &&
                            (catalogPage.lastPage == null || page < catalogPage.lastPage)
                    } else {
                        found.size >= 20 && merged.size > previous.items.size
                    })
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == revision) browse.value = previous.copy(loading = false,
                    error = e.message ?: "漫画目录加载失败")
            }
        }
    }

    fun selectCategory(categoryId: Int?) = load(refresh = true, search = "", categoryId = categoryId)
    fun search(query: String) = load(refresh = true, search = query.trim(), categoryId = null)
    fun loadMore() = load()

    fun loadDetail() {
        val requested = slug ?: return
        detail.value = SfComicDetailState(loading = true)
        viewModelScope.launch {
            try {
                val info = client.detail(requested)
                detail.value = SfComicDetailState(info = info, loading = false, currentChapterIndex = 0)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { detail.value = SfComicDetailState(loading = false, error = e.message ?: "漫画详情加载失败") }
        }
    }

    fun openChapter(index: Int) {
        val info = detail.value.info ?: return
        if (index !in info.chapters.indices) return
        val chapter = info.chapters[index]
        pageJob?.cancel()
        detail.value = detail.value.copy(currentChapterIndex = index, pages = emptyList(), loading = true,
            error = null, loadedChapterIndex = null, endReached = false, readyForNextChapter = false)
        pageJob = viewModelScope.launch {
            try {
                val pages = client.pages(info.comic.slug, chapter.id)
                if (detail.value.currentChapterIndex == index) detail.value = detail.value.copy(
                    pages = pages, loading = false, loadedChapterIndex = index)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (detail.value.currentChapterIndex == index) detail.value = detail.value.copy(loading = false,
                    error = e.message ?: "漫画章节加载失败")
            }
        }
    }

    fun advanceIfAtEnd() {
        val state = detail.value
        val info = state.info ?: return
        if (state.loading || !state.readyForNextChapter || state.currentChapterIndex >= info.chapters.lastIndex) return
        openChapter(state.currentChapterIndex + 1)
    }

    fun markEndVisible() {
        val state = detail.value
        if (!state.loading && state.pages.isNotEmpty() && !state.endReached) {
            detail.value = state.copy(endReached = true)
        }
    }

    fun setEndAnchorIndex(index: Int?) {
        val state = detail.value
        if (state.endAnchorIndex != index) detail.value = state.copy(endAnchorIndex = index)
    }

    fun armNextChapter() {
        val state = detail.value
        if (!state.loading && state.endReached && !state.readyForNextChapter &&
            state.currentChapterIndex < (state.info?.chapters?.lastIndex ?: -1)) {
            detail.value = state.copy(readyForNextChapter = true)
        }
    }

}

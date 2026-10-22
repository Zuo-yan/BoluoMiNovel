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

data class SfExploreState(
    val books: List<SfSearchResult> = emptyList(), val lists: List<SfBookList> = emptyList(),
    val loading: Boolean = false, val error: String? = null, val page: Int = 0, val hasMore: Boolean = false
)

@HiltViewModel
class SfExploreViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle, private val session: SfSessionRepository
) : ViewModel() {
    val sourceBookId: String? = savedStateHandle.get<String>("bookId")?.removePrefix("sf:")
    private val mutable = MutableStateFlow(SfExploreState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var revision = 0

    init { load(refresh = true) }

    fun load(refresh: Boolean = false) {
        if (!refresh && (state.value.loading || (state.value.page > 0 && !state.value.hasMore))) return
        if (refresh) job?.cancel()
        val generation = ++revision
        val previous = if (refresh) SfExploreState() else state.value
        val page = previous.page + 1
        mutable.value = previous.copy(loading = true, error = null)
        job = viewModelScope.launch {
            try {
                val books = if (sourceBookId == null) session.newRecommendations(page) else emptyList()
                val lists = sourceBookId?.let { session.relatedBookLists(it, page) }.orEmpty()
                if (generation != revision) return@launch
                val mergedBooks = (previous.books + books).distinctBy { it.id }
                val mergedLists = (previous.lists + lists).distinctBy { it.id }
                val added = mergedBooks.size + mergedLists.size > previous.books.size + previous.lists.size
                mutable.value = previous.copy(books = mergedBooks, lists = mergedLists, page = page,
                    hasMore = added && books.size + lists.size >= 20)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == revision) mutable.value = previous.copy(error = e.message ?: "加载失败，请稍后重试")
            }
        }
    }
}

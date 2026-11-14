package com.novelai.assistant.ui.bookshelf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.importer.BookImportManager
import com.novelai.assistant.data.repository.BookRepository
import com.novelai.assistant.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ShelfViewMode { GRID, LIST }

@HiltViewModel
class BookshelfViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val selectedCategory = MutableStateFlow("全部")
    private val viewModeInternal = MutableStateFlow(ShelfViewMode.GRID)
    val viewMode: StateFlow<ShelfViewMode> = viewModeInternal.asStateFlow()

    private val allBooks = bookRepository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<String>> = categoryRepository.observeCategories()
        .map { cats -> listOf("全部") + cats.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("全部"))

    val currentCategory: StateFlow<String> = selectedCategory.asStateFlow()

    val books: StateFlow<List<BookEntity>> =
        combine(allBooks, selectedCategory) { list, cat ->
            if (cat == "全部") list else list.filter { it.category == cat }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    fun setViewMode(mode: ShelfViewMode) { viewModeInternal.value = mode }

    fun selectCategory(name: String) { selectedCategory.value = name }

    fun importBook(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _importing.value = true
            when (val result = bookRepository.import(uri)) {
                is BookImportManager.ImportResult.Success ->
                    _importMessage.value = "《${result.title}》导入成功，共 ${result.chapterCount} 章"
                is BookImportManager.ImportResult.Failure ->
                    _importMessage.value = result.message
            }
            _importing.value = false
        }
    }

    fun consumeMessage() { _importMessage.value = null }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch { bookRepository.deleteBook(book.id) }
    }

    fun moveBookToCategory(book: BookEntity, category: String) {
        viewModelScope.launch { bookRepository.updateCategory(book.id, category) }
    }

    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { categoryRepository.addCategory(name) }
    }

    fun deleteCategory(name: String) {
        if (name == "全部" || name.isBlank()) return
        viewModelScope.launch {
            categoryRepository.deleteCategoryByName(name)
            bookRepository.resetCategoryBooks(name)
            if (selectedCategory.value == name) {
                selectedCategory.value = "全部"
            }
        }
    }

    fun setBookCover(bookId: String, uri: Uri) {
        viewModelScope.launch {
            val ok = bookRepository.setManualCover(bookId, uri)
            _importMessage.value = if (ok) "封面设置成功" else "封面设置失败，请重试"
        }
    }

    fun clearBookCover(bookId: String) {
        viewModelScope.launch {
            bookRepository.clearManualCover(bookId)
            _importMessage.value = "已恢复默认封面"
        }
    }
}

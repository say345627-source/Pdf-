package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.engine.DocumentManager
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.WaraqDatabase
import com.example.data.model.BookEntity
import com.example.widget.WaraqWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortOption(val label: String) {
    LAST_READ("آخر فتح"),
    TITLE("الاسم"),
    DATE_ADDED("تاريخ الإضافة"),
    PROGRESS("نسبة التقدم")
}

data class LibraryUiState(
    val books: List<BookEntity> = emptyList(),
    val mostRecentBook: BookEntity? = null,
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.LAST_READ,
    val isGridView: Boolean = true,
    val isImporting: Boolean = false,
    val notificationMessage: String? = null
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WaraqDatabase.getInstance(application)
    private val bookDao = db.bookDao()
    private val documentManager = DocumentManager(application)
    private val userPreferencesRepo = UserPreferencesRepository(application)

    private val _searchQuery = MutableStateFlow("")
    private val _sortOption = MutableStateFlow(SortOption.LAST_READ)
    private val _isImporting = MutableStateFlow(false)
    private val _notificationMessage = MutableStateFlow<String?>(null)

    private val booksWithFiltersFlow = combine(
        bookDao.getAllBooksFlow(),
        _searchQuery,
        _sortOption,
        userPreferencesRepo.preferencesFlow
    ) { allBooks: List<BookEntity>, query: String, sort: SortOption, prefs ->

        val filtered = if (query.isBlank()) {
            allBooks
        } else {
            allBooks.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.originalFileName.contains(query, ignoreCase = true)
            }
        }

        val sorted = when (sort) {
            SortOption.LAST_READ -> filtered.sortedByDescending { it.lastReadTimestamp }
            SortOption.TITLE -> filtered.sortedBy { it.title }
            SortOption.DATE_ADDED -> filtered.sortedByDescending { it.dateAddedTimestamp }
            SortOption.PROGRESS -> filtered.sortedByDescending { it.progressPercent }
        }

        val mostRecent = allBooks.maxByOrNull { it.lastReadTimestamp }

        LibraryUiState(
            books = sorted,
            mostRecentBook = mostRecent,
            searchQuery = query,
            sortOption = sort,
            isGridView = prefs.isGridView
        )
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        booksWithFiltersFlow,
        _isImporting,
        _notificationMessage
    ) { baseState, importing, notification ->
        baseState.copy(
            isImporting = importing,
            notificationMessage = notification
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        LibraryUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onSortOptionChanged(option: SortOption) {
        _sortOption.value = option
    }

    fun toggleViewMode() {
        viewModelScope.launch {
            val current = uiState.value.isGridView
            userPreferencesRepo.setGridView(!current)
        }
    }

    fun dismissNotification() {
        _notificationMessage.value = null
    }

    fun importDocument(uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = documentManager.importDocument(uri)
            _isImporting.value = false
            if (result.isSuccess) {
                val book = result.getOrNull()
                _notificationMessage.value = "تمت إضافة «${book?.title ?: "المستند"}» إلى المكتبة بنجاح"
            } else {
                _notificationMessage.value = "تعذر استيراد الملف: ${result.exceptionOrNull()?.message ?: "خطأ غير معروف"}"
            }
        }
    }

    fun renameBook(bookId: Long, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            bookDao.renameBook(bookId, newTitle.trim())
            WaraqWidgetProvider.updateAllWidgets(getApplication())
            _notificationMessage.value = "تمت إعادة تسمية الكتاب بنجاح"
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            bookDao.deleteBook(book)
            WaraqWidgetProvider.updateAllWidgets(getApplication())
            _notificationMessage.value = "تمت إزالة «${book.title}» من المكتبة"
        }
    }
}

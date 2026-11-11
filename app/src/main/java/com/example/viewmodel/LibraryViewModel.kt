package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.engine.DocumentManager
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.WaraqDatabase
import com.example.data.local.WaraqThemeId
import com.example.data.model.BookEntity
import com.example.data.model.BookmarkEntity
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

enum class BottomNavTab(val title: String) {
    ALL_FILES("كل الملفات"),
    RECENTS("الأخيرة"),
    BOOKMARKS("العلامات المرجعية"),
    TOOLS("الأدوات والثيمات")
}

data class LibraryUiState(
    val books: List<BookEntity> = emptyList(),
    val allBooksCount: Int = 0,
    val recentBooks: List<BookEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val mostRecentBook: BookEntity? = null,
    val searchQuery: String = "",
    val sortOption: SortOption = SortOption.DATE_ADDED,
    val selectedCategoryTab: String = "الكل",
    val currentBottomNavTab: BottomNavTab = BottomNavTab.ALL_FILES,
    val currentThemeId: String = WaraqThemeId.CHARCOAL_NEON.name,
    val isGridView: Boolean = false,
    val isImporting: Boolean = false,
    val notificationMessage: String? = null
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WaraqDatabase.getInstance(application)
    private val bookDao = db.bookDao()
    private val bookmarkDao = db.bookmarkDao()
    private val documentManager = DocumentManager(application)
    private val userPreferencesRepo = UserPreferencesRepository(application)

    private val _searchQuery = MutableStateFlow("")
    private val _sortOption = MutableStateFlow(SortOption.DATE_ADDED)
    private val _selectedCategoryTab = MutableStateFlow("الكل")
    private val _currentBottomNavTab = MutableStateFlow(BottomNavTab.ALL_FILES)
    private val _isImporting = MutableStateFlow(false)
    private val _notificationMessage = MutableStateFlow<String?>(null)

    private val booksWithFiltersFlow = combine(
        bookDao.getAllBooksFlow(),
        _searchQuery,
        _sortOption,
        _selectedCategoryTab,
        userPreferencesRepo.preferencesFlow
    ) { allBooks: List<BookEntity>, query: String, sort: SortOption, category: String, prefs ->

        // Filter by category tab
        val categoryFiltered = when (category) {
            "PDF" -> allBooks.filter { it.fileType.equals("PDF", ignoreCase = true) }
            "Word" -> allBooks.filter { it.fileType.contains("DOC", ignoreCase = true) }
            "المفضلة" -> allBooks.filter { it.isFavorite }
            else -> allBooks
        }

        // Filter by search query
        val searchFiltered = if (query.isBlank()) {
            categoryFiltered
        } else {
            categoryFiltered.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.originalFileName.contains(query, ignoreCase = true)
            }
        }

        val sorted = when (sort) {
            SortOption.LAST_READ -> searchFiltered.sortedByDescending { it.lastReadTimestamp }
            SortOption.TITLE -> searchFiltered.sortedBy { it.title.lowercase() }
            SortOption.DATE_ADDED -> searchFiltered.sortedByDescending { it.dateAddedTimestamp }
            SortOption.PROGRESS -> searchFiltered.sortedByDescending { it.progressPercent }
        }

        val recentsList = allBooks.sortedByDescending { it.lastReadTimestamp }
        val mostRecent = recentsList.firstOrNull()

        LibraryUiState(
            books = sorted,
            allBooksCount = allBooks.size,
            recentBooks = recentsList,
            mostRecentBook = mostRecent,
            searchQuery = query,
            sortOption = sort,
            selectedCategoryTab = category,
            currentThemeId = prefs.themeId,
            isGridView = prefs.isGridView
        )
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        booksWithFiltersFlow,
        _currentBottomNavTab,
        bookmarkDao.getAllBookmarksFlow(),
        _isImporting,
        _notificationMessage
    ) { baseState, bottomTab, bookmarks, importing, notification ->
        baseState.copy(
            currentBottomNavTab = bottomTab,
            bookmarks = bookmarks,
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

    fun onCategoryTabSelected(category: String) {
        _selectedCategoryTab.value = category
    }

    fun onBottomNavTabSelected(tab: BottomNavTab) {
        _currentBottomNavTab.value = tab
    }

    fun selectTheme(themeId: String) {
        viewModelScope.launch {
            userPreferencesRepo.setThemeId(themeId)
            _notificationMessage.value = "تم تغيير مظهر التطبيق بنجاح"
        }
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
                _notificationMessage.value = "تمت إضافة «${book?.title ?: "المستند"}» بنجاح"
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

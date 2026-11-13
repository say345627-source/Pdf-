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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    val markedBooksCount: Int = 0,
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
    val isSelectionMode: Boolean = false,
    val selectedBookIds: Set<Long> = emptySet(),
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
    private val _isSelectionMode = MutableStateFlow(false)
    private val _selectedBookIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _notificationMessage = MutableStateFlow<String?>(null)

    private val booksWithFiltersFlow = combine(
        bookDao.getAllBooksFlow(),
        _searchQuery,
        _sortOption,
        _selectedCategoryTab,
        userPreferencesRepo.preferencesFlow
    ) { allBooks: List<BookEntity>, query: String, sort: SortOption, category: String, prefs ->

        val categoryFiltered = when (category) {
            "PDF" -> allBooks.filter { it.fileType.equals("PDF", ignoreCase = true) }
            "Word" -> allBooks.filter { it.fileType.contains("DOC", ignoreCase = true) }
            "المحددة", "المفضلة" -> allBooks.filter { it.isFavorite }
            else -> allBooks
        }

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
        val markedCount = allBooks.count { it.isFavorite }

        LibraryUiState(
            books = sorted,
            allBooksCount = allBooks.size,
            markedBooksCount = markedCount,
            recentBooks = recentsList,
            mostRecentBook = mostRecent,
            searchQuery = query,
            sortOption = sort,
            selectedCategoryTab = category,
            currentThemeId = prefs.themeId,
            isGridView = prefs.isGridView
        )
    }.flowOn(Dispatchers.Default)

    private val selectionStateFlow = combine(
        _isSelectionMode,
        _selectedBookIds
    ) { selectionMode, selectedIds -> selectionMode to selectedIds }

    val uiState: StateFlow<LibraryUiState> = combine(
        booksWithFiltersFlow,
        _currentBottomNavTab,
        bookmarkDao.getAllBookmarksFlow(),
        _isImporting,
        selectionStateFlow,
        _notificationMessage
    ) { values ->
        val baseState = values[0] as LibraryUiState
        val bottomTab = values[1] as BottomNavTab
        @Suppress("UNCHECKED_CAST")
        val bookmarks = values[2] as List<BookmarkEntity>
        val importing = values[3] as Boolean
        @Suppress("UNCHECKED_CAST")
        val selectionPair = values[4] as Pair<Boolean, Set<Long>>
        val notification = values[5] as String?

        baseState.copy(
            currentBottomNavTab = bottomTab,
            bookmarks = bookmarks,
            isImporting = importing,
            isSelectionMode = selectionPair.first,
            selectedBookIds = selectionPair.second,
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
        if (tab != BottomNavTab.ALL_FILES && tab != BottomNavTab.RECENTS && _isSelectionMode.value) {
            clearSelection()
        }
    }

    fun enterSelectionMode(initialBookId: Long? = null) {
        _isSelectionMode.value = true
        if (initialBookId != null) {
            _selectedBookIds.update { it + initialBookId }
        }
    }

    fun toggleBookSelection(bookId: Long) {
        _isSelectionMode.value = true
        _selectedBookIds.update { current ->
            if (bookId in current) current - bookId else current + bookId
        }
    }

    fun selectAllVisibleBooks() {
        val visibleIds = when (_currentBottomNavTab.value) {
            BottomNavTab.RECENTS -> uiState.value.recentBooks.map { it.id }
            else -> uiState.value.books.map { it.id }
        }
        if (visibleIds.isEmpty()) return

        _isSelectionMode.value = true
        _selectedBookIds.update { current ->
            val visibleSet = visibleIds.toSet()
            if (visibleIds.all { it in current }) {
                current - visibleSet
            } else {
                current + visibleSet
            }
        }
    }

    fun clearSelection() {
        _isSelectionMode.value = false
        _selectedBookIds.value = emptySet()
    }

    fun toggleBookMarked(book: BookEntity) {
        val newMarkedState = !book.isFavorite
        viewModelScope.launch {
            bookDao.updateFavorite(book.id, newMarkedState)
            _notificationMessage.value = if (newMarkedState) {
                "تم وضع علامة تحديد على «${book.title}»"
            } else {
                "تمت إزالة علامة التحديد من «${book.title}»"
            }
        }
    }

    fun markSelectedBooks(mark: Boolean) {
        val ids = _selectedBookIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            bookDao.updateFavorites(ids, mark)
            clearSelection()
            _notificationMessage.value = if (mark) {
                "تم وضع علامة تحديد على ${ids.size} ملفات"
            } else {
                "تمت إزالة العلامة من ${ids.size} ملفات"
            }
        }
    }

    fun deleteSelectedBooks() {
        val ids = _selectedBookIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val booksToDelete = withContext(Dispatchers.IO) {
                ids.mapNotNull { bookDao.getBookById(it) }
            }
            bookDao.deleteBooksByIds(ids)
            withContext(Dispatchers.IO) {
                booksToDelete.forEach { deletePrivateBookFiles(it) }
            }
            clearSelection()
            WaraqWidgetProvider.updateAllWidgets(getApplication())
            _notificationMessage.value = "تمت إزالة ${ids.size} ملفات من المكتبة"
        }
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
            withContext(Dispatchers.IO) {
                deletePrivateBookFiles(book)
            }
            WaraqWidgetProvider.updateAllWidgets(getApplication())
            _notificationMessage.value = "تمت إزالة «${book.title}» من المكتبة"
        }
    }

    private fun deletePrivateBookFiles(book: BookEntity) {
        try {
            val filesDir = getApplication<Application>().filesDir.canonicalFile
            if (book.localFilePath.isNotBlank()) {
                val file = File(book.localFilePath).canonicalFile
                if (file.path.startsWith(filesDir.path) && file.exists()) {
                    file.delete()
                }
            }
            val coverPath = book.coverImagePath
            if (!coverPath.isNullOrBlank()) {
                val coverFile = File(coverPath).canonicalFile
                if (coverFile.path.startsWith(filesDir.path) && coverFile.exists()) {
                    coverFile.delete()
                }
            }
        } catch (_: Exception) {
        }
    }
}

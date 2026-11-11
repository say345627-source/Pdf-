package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.engine.DocxEngine
import com.example.data.engine.PdfEngine
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.WaraqDatabase
import com.example.data.model.BookEntity
import com.example.data.model.BookmarkEntity
import com.example.data.model.DocElement
import com.example.widget.WaraqWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ReaderUiState(
    val book: BookEntity? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    // PDF State
    val pdfCurrentPage: Int = 1, // 1-indexed
    val pdfTotalPages: Int = 1,
    val currentBitmap: Bitmap? = null,
    val isRenderingPage: Boolean = false,
    val pdfFitMode: String = "WIDTH",
    val extractedPageText: String = "",
    val isTextSelectionMode: Boolean = false,
    val isPdfSearchActive: Boolean = false,
    val pdfSearchQuery: String = "",
    val pdfSearchResults: List<PdfEngine.SearchResult> = emptyList(),
    // Word State
    val wordElements: List<DocElement> = emptyList(),
    val wordFontSizeSp: Float = 17f,
    val wordCurrentParagraphIndex: Int = 0,
    // Bookmark
    val isBookmarked: Boolean = false,
    val bookmarks: List<BookmarkEntity> = emptyList()
)

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WaraqDatabase.getInstance(application)
    private val bookDao = db.bookDao()
    private val bookmarkDao = db.bookmarkDao()
    private val userPreferencesRepo = UserPreferencesRepository(application)

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var pdfEngine: PdfEngine? = null
    private var saveJob: Job? = null
    private var currentBookId: Long = -1

    fun loadBook(bookId: Long) {
        if (currentBookId == bookId && _uiState.value.book != null) return
        currentBookId = bookId

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val book = bookDao.getBookById(bookId)
            if (book == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "تعذر العثور على الكتاب المطلوب في قاعدة البيانات."
                    )
                }
                return@launch
            }

            val file = File(book.localFilePath)
            if (!file.exists()) {
                _uiState.update {
                    it.copy(
                        book = book,
                        isLoading = false,
                        errorMessage = "الملف غير متوفر على مسار الجهاز. قد يكون قد نُقل أو حُذف."
                    )
                }
                return@launch
            }

            val prefs = userPreferencesRepo.preferencesFlow.first()

            if (book.fileType.equals("PDF", ignoreCase = true)) {
                loadPdfDocument(book, file, prefs.pdfFitMode)
            } else {
                loadWordDocument(book, file, prefs.wordFontSizeSp)
            }

            loadBookmarks(bookId)
        }
    }

    private suspend fun loadPdfDocument(book: BookEntity, file: File, fitMode: String) = withContext(Dispatchers.IO) {
        try {
            pdfEngine?.close()
            val engine = PdfEngine(file)
            pdfEngine = engine

            val total = engine.pageCount.coerceAtLeast(1)
            // Restore last saved page (1-indexed) safely
            val savedPage = book.lastReadPage.coerceIn(1, total)

            _uiState.update {
                it.copy(
                    book = book,
                    isLoading = false,
                    pdfCurrentPage = savedPage,
                    pdfTotalPages = total,
                    pdfFitMode = fitMode
                )
            }

            renderPdfPageInternal(savedPage)
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "فشل فتح ملف PDF: ${e.localizedMessage ?: "تنسيق غير صالح"}"
                )
            }
        }
    }

    private suspend fun loadWordDocument(book: BookEntity, file: File, fontSizeSp: Float) = withContext(Dispatchers.IO) {
        try {
            val elements = DocxEngine.parseDocument(file)
            val total = elements.size.coerceAtLeast(1)
            val savedIndex = (book.lastReadPage - 1).coerceIn(0, total - 1)

            _uiState.update {
                it.copy(
                    book = book,
                    isLoading = false,
                    wordElements = elements,
                    wordFontSizeSp = fontSizeSp,
                    wordCurrentParagraphIndex = savedIndex
                )
            }

            checkBookmarkState(book.id, savedIndex + 1)
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "فشل فتح مستند Word: ${e.localizedMessage ?: "تنسيق غير صالح"}"
                )
            }
        }
    }

    private fun renderPdfPageInternal(page1Based: Int) {
        val engine = pdfEngine ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRenderingPage = true) }

            val bitmap = engine.renderPage(page1Based - 1, 1080)
            val text = engine.getPageText(page1Based - 1)

            _uiState.update {
                it.copy(
                    pdfCurrentPage = page1Based,
                    currentBitmap = bitmap,
                    extractedPageText = text,
                    isRenderingPage = false
                )
            }

            val book = _uiState.value.book
            if (book != null) {
                checkBookmarkState(book.id, page1Based)
                scheduleSaveProgress(
                    bookId = book.id,
                    page = page1Based,
                    totalPages = engine.pageCount,
                    snippet = text.take(120).trim()
                )
            }
        }
    }

    fun goToPdfPage(page: Int) {
        val total = _uiState.value.pdfTotalPages
        val target = page.coerceIn(1, total)
        if (target != _uiState.value.pdfCurrentPage) {
            renderPdfPageInternal(target)
        }
    }

    fun nextPdfPage() {
        goToPdfPage(_uiState.value.pdfCurrentPage + 1)
    }

    fun previousPdfPage() {
        goToPdfPage(_uiState.value.pdfCurrentPage - 1)
    }

    fun toggleTextSelectionMode() {
        _uiState.update { it.copy(isTextSelectionMode = !it.isTextSelectionMode) }
    }

    fun togglePdfSearch() {
        _uiState.update {
            it.copy(
                isPdfSearchActive = !it.isPdfSearchActive,
                pdfSearchQuery = "",
                pdfSearchResults = emptyList()
            )
        }
    }

    fun onPdfSearchQueryChanged(query: String) {
        _uiState.update { it.copy(pdfSearchQuery = query) }
        val engine = pdfEngine ?: return
        if (query.isBlank()) {
            _uiState.update { it.copy(pdfSearchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            val results = engine.search(query)
            _uiState.update { it.copy(pdfSearchResults = results) }
        }
    }

    fun toggleFitMode() {
        val newMode = if (_uiState.value.pdfFitMode == "WIDTH") "PAGE" else "WIDTH"
        _uiState.update { it.copy(pdfFitMode = newMode) }
        viewModelScope.launch {
            userPreferencesRepo.setPdfFitMode(newMode)
        }
    }

    fun changeWordFontSize(delta: Float) {
        val newSize = (_uiState.value.wordFontSizeSp + delta).coerceIn(13f, 30f)
        _uiState.update { it.copy(wordFontSizeSp = newSize) }
        viewModelScope.launch {
            userPreferencesRepo.setWordFontSize(newSize)
        }
    }

    fun onWordScrolledTo(paragraphIndex: Int) {
        val elements = _uiState.value.wordElements
        if (elements.isEmpty()) return
        val validIndex = paragraphIndex.coerceIn(0, elements.size - 1)
        _uiState.update { it.copy(wordCurrentParagraphIndex = validIndex) }

        val book = _uiState.value.book ?: return
        val snippet = when (val elem = elements[validIndex]) {
            is DocElement.Paragraph -> elem.text.take(120)
            is DocElement.Heading -> elem.text.take(120)
            is DocElement.ListItem -> elem.text.take(120)
            else -> ""
        }

        scheduleSaveProgress(
            bookId = book.id,
            page = validIndex + 1,
            totalPages = elements.size,
            snippet = snippet
        )
    }

    private fun scheduleSaveProgress(
        bookId: Long,
        page: Int,
        totalPages: Int,
        scrollOffset: Int = 0,
        snippet: String? = null
    ) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(350) // Debounce saves during rapid scrolling
            val total = totalPages.coerceAtLeast(1)
            val progress = (page.toFloat() / total.toFloat() * 100f).coerceIn(0f, 100f)
            val now = System.currentTimeMillis()

            bookDao.updateProgress(
                id = bookId,
                page = page,
                totalPages = total,
                progress = progress,
                scrollOffset = scrollOffset,
                timestamp = now,
                snippet = snippet
            )

            // Update app widget
            WaraqWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun savePositionImmediately() {
        val book = _uiState.value.book ?: return
        viewModelScope.launch {
            val isPdf = book.fileType.equals("PDF", ignoreCase = true)
            val page = if (isPdf) _uiState.value.pdfCurrentPage else (_uiState.value.wordCurrentParagraphIndex + 1)
            val total = if (isPdf) _uiState.value.pdfTotalPages else _uiState.value.wordElements.size.coerceAtLeast(1)
            val progress = (page.toFloat() / total.toFloat() * 100f).coerceIn(0f, 100f)
            val snippet = if (isPdf) _uiState.value.extractedPageText.take(120) else ""

            bookDao.updateProgress(
                id = book.id,
                page = page,
                totalPages = total,
                progress = progress,
                scrollOffset = 0,
                timestamp = System.currentTimeMillis(),
                snippet = snippet
            )
            WaraqWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun toggleBookmark() {
        val book = _uiState.value.book ?: return
        val isPdf = book.fileType.equals("PDF", ignoreCase = true)
        val page = if (isPdf) _uiState.value.pdfCurrentPage else (_uiState.value.wordCurrentParagraphIndex + 1)

        viewModelScope.launch {
            val isBookmarked = bookmarkDao.isBookmarked(book.id, page)
            if (isBookmarked) {
                bookmarkDao.deleteBookmarkByPage(book.id, page)
                _uiState.update { it.copy(isBookmarked = false) }
            } else {
                val snippet = if (isPdf) {
                    _uiState.value.extractedPageText.take(80)
                } else {
                    _uiState.value.wordElements.getOrNull(_uiState.value.wordCurrentParagraphIndex)?.let {
                        when (it) {
                            is DocElement.Paragraph -> it.text.take(80)
                            is DocElement.Heading -> it.text.take(80)
                            else -> ""
                        }
                    } ?: ""
                }
                val bookmark = BookmarkEntity(
                    bookId = book.id,
                    pageNumber = page,
                    title = "إشارة موضع: $page",
                    snippet = snippet
                )
                bookmarkDao.insertBookmark(bookmark)
                _uiState.update { it.copy(isBookmarked = true) }
            }
            loadBookmarks(book.id)
        }
    }

    private suspend fun checkBookmarkState(bookId: Long, page: Int) {
        val bookmarked = bookmarkDao.isBookmarked(bookId, page)
        _uiState.update { it.copy(isBookmarked = bookmarked) }
    }

    private suspend fun loadBookmarks(bookId: Long) {
        bookmarkDao.getBookmarksForBookFlow(bookId).collect { list ->
            _uiState.update { it.copy(bookmarks = list) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        savePositionImmediately()
        pdfEngine?.close()
        pdfEngine = null
    }
}

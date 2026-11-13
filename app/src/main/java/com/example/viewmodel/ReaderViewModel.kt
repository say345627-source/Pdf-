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
import com.example.data.model.DocPage
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
    // Page tracking
    val currentVisiblePage: Int = 1, // 1-indexed for the floating 550/11 badge
    // PDF State
    val pdfCurrentPage: Int = 1, // 1-indexed
    val pdfTotalPages: Int = 1,
    val currentBitmap: Bitmap? = null,
    val isRenderingPage: Boolean = false,
    val pdfFitMode: String = "WIDTH",
    val extractedPageText: String = "",
    val isTextSelectionMode: Boolean = false,
    val isPdfSearchActive: Boolean = false,
    val isSearching: Boolean = false,
    val pdfSearchQuery: String = "",
    val pdfSearchResults: List<PdfEngine.SearchResult> = emptyList(),
    val activeSearchIndex: Int = 0,
    // Word State
    val wordElements: List<DocElement> = emptyList(),
    val wordPages: List<DocPage> = emptyList(),
    val wordTotalPages: Int = 1,
    val wordCurrentPage: Int = 1,
    val wordFontSizeSp: Float = 17f,
    val isWordSearchActive: Boolean = false,
    val wordSearchQuery: String = "",
    val wordSearchResults: List<PdfEngine.SearchResult> = emptyList(),
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
    private var searchJob: Job? = null
    private var currentBookId: Long = -1

    fun getPdfEngine(): PdfEngine? = pdfEngine

    suspend fun renderPdfPageBitmap(pageIndex: Int): Bitmap? {
        return pdfEngine?.renderPage(pageIndex, 1080)
    }

    fun getPdfPageRatio(pageIndex: Int): Float {
        return pdfEngine?.getCachedAspectRatio(pageIndex) ?: 1.414f
    }

    fun reloadBook(bookId: Long) {
        currentBookId = -1L
        loadBook(bookId)
    }

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

            // Preload initial aspect ratios around saved page cleanly on IO
            engine.preloadAspectRatios((savedPage - 3).coerceAtLeast(0), 25)

            _uiState.update {
                it.copy(
                    book = book,
                    isLoading = false,
                    pdfCurrentPage = savedPage,
                    currentVisiblePage = savedPage,
                    pdfTotalPages = total,
                    pdfFitMode = fitMode
                )
            }

            loadPageTextInternal(savedPage)
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
            val rawElements = DocxEngine.parseDocument(file)
            val pages = DocxEngine.paginateElements(rawElements, book.title)
            val total = pages.size.coerceAtLeast(1)
            val savedPage = book.lastReadPage.coerceIn(1, total)

            _uiState.update {
                it.copy(
                    book = book,
                    isLoading = false,
                    wordElements = rawElements,
                    wordPages = pages,
                    wordTotalPages = total,
                    wordCurrentPage = savedPage,
                    currentVisiblePage = savedPage,
                    wordFontSizeSp = fontSizeSp
                )
            }

            checkBookmarkState(book.id, savedPage)
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

    fun onPdfPageScrolledTo(page1Based: Int) {
        val total = _uiState.value.pdfTotalPages
        val validPage = page1Based.coerceIn(1, total)
        if (validPage == _uiState.value.pdfCurrentPage) return

        _uiState.update {
            it.copy(
                pdfCurrentPage = validPage,
                currentVisiblePage = validPage
            )
        }

        loadPageTextInternal(validPage)

        // Prefetch upcoming pages silently on IO so scrolling never stalls
        val engine = pdfEngine
        if (engine != null) {
            viewModelScope.launch {
                engine.preloadAspectRatios(validPage, 10)
                if (validPage < total) {
                    engine.renderPage(validPage, 1080)
                }
            }
        }

        val book = _uiState.value.book ?: return
        checkBookmarkState(book.id, validPage)
        scheduleSaveProgress(
            bookId = book.id,
            page = validPage,
            totalPages = total,
            snippet = _uiState.value.extractedPageText.take(120).trim()
        )
    }

    fun onWordPageScrolledTo(page1Based: Int) {
        val total = _uiState.value.wordTotalPages
        val validPage = page1Based.coerceIn(1, total)
        if (validPage == _uiState.value.wordCurrentPage) return

        _uiState.update {
            it.copy(
                wordCurrentPage = validPage,
                currentVisiblePage = validPage
            )
        }

        val book = _uiState.value.book ?: return
        checkBookmarkState(book.id, validPage)

        val pageItem = _uiState.value.wordPages.getOrNull(validPage - 1)
        val snippet = pageItem?.elements?.firstOrNull()?.let { elem ->
            when (elem) {
                is DocElement.Paragraph -> elem.text.take(120)
                is DocElement.Heading -> elem.text.take(120)
                is DocElement.ListItem -> elem.text.take(120)
                else -> ""
            }
        } ?: ""

        scheduleSaveProgress(
            bookId = book.id,
            page = validPage,
            totalPages = total,
            snippet = snippet
        )
    }

    private fun loadPageTextInternal(page1Based: Int) {
        val engine = pdfEngine ?: return
        viewModelScope.launch {
            val text = engine.getPageText(page1Based - 1)
            _uiState.update { it.copy(extractedPageText = text) }
        }
    }

    fun goToPdfPage(page: Int) {
        val total = _uiState.value.pdfTotalPages
        val target = page.coerceIn(1, total)
        _uiState.update {
            it.copy(
                pdfCurrentPage = target,
                currentVisiblePage = target
            )
        }
        loadPageTextInternal(target)
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
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                isPdfSearchActive = !it.isPdfSearchActive,
                isSearching = false,
                pdfSearchQuery = "",
                pdfSearchResults = emptyList(),
                activeSearchIndex = 0
            )
        }
    }

    fun onPdfSearchQueryChanged(query: String) {
        _uiState.update { it.copy(pdfSearchQuery = query, activeSearchIndex = 0) }
        val engine = pdfEngine ?: return
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(pdfSearchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            delay(180)
            val results = engine.search(query)
            _uiState.update {
                it.copy(
                    pdfSearchResults = results,
                    isSearching = false,
                    activeSearchIndex = 0
                )
            }
        }
    }

    fun selectPdfSearchResult(index: Int) {
        val total = _uiState.value.pdfSearchResults.size
        if (total == 0) return
        _uiState.update { it.copy(activeSearchIndex = index.coerceIn(0, total - 1)) }
    }

    fun nextPdfSearchResult(): Int? {
        val results = _uiState.value.pdfSearchResults
        if (results.isEmpty()) return null
        val nextIdx = (_uiState.value.activeSearchIndex + 1) % results.size
        _uiState.update { it.copy(activeSearchIndex = nextIdx) }
        return results[nextIdx].pageNumber
    }

    fun previousPdfSearchResult(): Int? {
        val results = _uiState.value.pdfSearchResults
        if (results.isEmpty()) return null
        val prevIdx = if (_uiState.value.activeSearchIndex - 1 < 0) results.lastIndex else _uiState.value.activeSearchIndex - 1
        _uiState.update { it.copy(activeSearchIndex = prevIdx) }
        return results[prevIdx].pageNumber
    }

    fun toggleWordSearch() {
        _uiState.update {
            it.copy(
                isWordSearchActive = !it.isWordSearchActive,
                wordSearchQuery = "",
                wordSearchResults = emptyList(),
                activeSearchIndex = 0
            )
        }
    }

    fun onWordSearchQueryChanged(query: String) {
        _uiState.update { it.copy(wordSearchQuery = query, activeSearchIndex = 0) }
        val q = query.trim()
        if (q.isBlank()) {
            _uiState.update { it.copy(wordSearchResults = emptyList()) }
            return
        }
        val results = mutableListOf<PdfEngine.SearchResult>()
        _uiState.value.wordPages.forEach { page ->
            val pageText = page.elements.joinToString(" ") { elem ->
                when (elem) {
                    is DocElement.Heading -> elem.text
                    is DocElement.Paragraph -> elem.text
                    is DocElement.ListItem -> elem.text
                    is DocElement.Table -> elem.rows.flatten().joinToString(" ")
                    is DocElement.Notice -> elem.message
                    is DocElement.ImageItem -> elem.caption
                }
            }
            var count = 0
            var idx = pageText.indexOf(q, ignoreCase = true)
            val firstIdx = idx
            while (idx != -1) {
                count++
                idx = pageText.indexOf(q, idx + q.length, ignoreCase = true)
            }
            if (count > 0 && firstIdx != -1) {
                val start = (firstIdx - 24).coerceAtLeast(0)
                val end = (firstIdx + q.length + 36).coerceAtMost(pageText.length)
                results.add(
                    PdfEngine.SearchResult(
                        pageNumber = page.pageNumber,
                        matchCount = count,
                        snippet = pageText.substring(start, end).replace("\n", " ").trim(),
                        matchedTerm = q
                    )
                )
            }
        }
        _uiState.update { it.copy(wordSearchResults = results, activeSearchIndex = 0) }
    }

    fun selectWordSearchResult(index: Int) {
        val total = _uiState.value.wordSearchResults.size
        if (total == 0) return
        _uiState.update { it.copy(activeSearchIndex = index.coerceIn(0, total - 1)) }
    }

    fun nextWordSearchResult(): Int? {
        val results = _uiState.value.wordSearchResults
        if (results.isEmpty()) return null
        val nextIdx = (_uiState.value.activeSearchIndex + 1) % results.size
        _uiState.update { it.copy(activeSearchIndex = nextIdx) }
        return results[nextIdx].pageNumber
    }

    fun previousWordSearchResult(): Int? {
        val results = _uiState.value.wordSearchResults
        if (results.isEmpty()) return null
        val prevIdx = if (_uiState.value.activeSearchIndex - 1 < 0) results.lastIndex else _uiState.value.activeSearchIndex - 1
        _uiState.update { it.copy(activeSearchIndex = prevIdx) }
        return results[prevIdx].pageNumber
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
            // Re-paginate with new font size
            val book = _uiState.value.book ?: return@launch
            val raw = _uiState.value.wordElements
            val rePaginated = DocxEngine.paginateElements(raw, book.title)
            _uiState.update {
                it.copy(
                    wordPages = rePaginated,
                    wordTotalPages = rePaginated.size.coerceAtLeast(1)
                )
            }
        }
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
            delay(350) // Debounce saves during continuous scrolling
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
            val page = if (isPdf) _uiState.value.pdfCurrentPage else _uiState.value.wordCurrentPage
            val total = if (isPdf) _uiState.value.pdfTotalPages else _uiState.value.wordTotalPages
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
        val page = if (isPdf) _uiState.value.pdfCurrentPage else _uiState.value.wordCurrentPage

        viewModelScope.launch {
            val isBookmarked = bookmarkDao.isBookmarked(book.id, page)
            if (isBookmarked) {
                bookmarkDao.deleteBookmarkByPage(book.id, page)
                _uiState.update { it.copy(isBookmarked = false) }
            } else {
                val snippet = if (isPdf) {
                    _uiState.value.extractedPageText.take(80)
                } else {
                    val p = _uiState.value.wordPages.getOrNull(page - 1)
                    p?.elements?.firstOrNull()?.let {
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
                    title = "إشارة صفحة: $page",
                    snippet = snippet
                )
                bookmarkDao.insertBookmark(bookmark)
                _uiState.update { it.copy(isBookmarked = true) }
            }
            loadBookmarks(book.id)
        }
    }

    private fun checkBookmarkState(bookId: Long, page: Int) {
        viewModelScope.launch {
            val bookmarked = bookmarkDao.isBookmarked(bookId, page)
            _uiState.update { it.copy(isBookmarked = bookmarked) }
        }
    }

    private fun loadBookmarks(bookId: Long) {
        viewModelScope.launch {
            bookmarkDao.getBookmarksForBookFlow(bookId).collect { list ->
                _uiState.update { it.copy(bookmarks = list) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        savePositionImmediately()
        pdfEngine?.close()
        pdfEngine = null
    }
}

package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.engine.DocumentManager
import com.example.data.engine.DocxEngine
import com.example.data.engine.PageOperationSpec
import com.example.data.engine.PdfDocumentProcessor
import com.example.data.engine.PdfEngine
import com.example.data.local.WaraqDatabase
import com.example.data.model.BookEntity
import com.example.data.model.DocPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ManagedPageItem(
    val id: String,
    val originalPageNumber: Int, // 1-based
    val rotationDegrees: Int = 0,
    val isSelected: Boolean = false
)

data class PageManagerUiState(
    val book: BookEntity? = null,
    val isPdf: Boolean = true,
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false,
    val processingLabel: String? = null,
    val pages: List<ManagedPageItem> = emptyList(),
    val originalPageCount: Int = 0,
    val wordPages: List<DocPage> = emptyList(),
    val hasUnsavedChanges: Boolean = false,
    val errorMessage: String? = null,
    val notificationMessage: String? = null
) {
    val selectedCount: Int get() = pages.count { it.isSelected }
}

class PageManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WaraqDatabase.getInstance(application)
    private val bookDao = db.bookDao()
    private val documentManager = DocumentManager(application)

    private val _uiState = MutableStateFlow(PageManagerUiState())
    val uiState: StateFlow<PageManagerUiState> = _uiState.asStateFlow()

    private var pdfEngine: PdfEngine? = null
    private var loadedBookId: Long = -1L

    fun getPdfEngine(): PdfEngine? = pdfEngine

    fun loadBook(bookId: Long) {
        if (loadedBookId == bookId && _uiState.value.book != null) return
        loadedBookId = bookId

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val book = bookDao.getBookById(bookId)
            if (book == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "تعذر العثور على المستند")
                }
                return@launch
            }

            val file = File(book.localFilePath)
            if (!file.exists()) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "الملف غير متوفر على الجهاز")
                }
                return@launch
            }

            val isPdf = book.fileType.equals("PDF", ignoreCase = true)
            if (isPdf) {
                try {
                    pdfEngine?.close()
                    val engine = PdfEngine(file)
                    pdfEngine = engine
                    val count = engine.pageCount.coerceAtLeast(1)
                    engine.preloadAspectRatios(0, count.coerceAtMost(40))

                    val items = (1..count).map { pageNum ->
                        ManagedPageItem(id = "p_$pageNum", originalPageNumber = pageNum)
                    }

                    _uiState.update {
                        it.copy(
                            book = book,
                            isPdf = true,
                            isLoading = false,
                            pages = items,
                            originalPageCount = count,
                            hasUnsavedChanges = false
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "فشل قراءة صفحات PDF")
                    }
                }
            } else {
                try {
                    val elements = DocxEngine.parseDocument(file)
                    val docPages = DocxEngine.paginateElements(elements, book.title)
                    val count = docPages.size.coerceAtLeast(1)
                    val items = (1..count).map { pageNum ->
                        ManagedPageItem(id = "w_$pageNum", originalPageNumber = pageNum)
                    }

                    _uiState.update {
                        it.copy(
                            book = book,
                            isPdf = false,
                            isLoading = false,
                            pages = items,
                            wordPages = docPages,
                            originalPageCount = count,
                            hasUnsavedChanges = false
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "فشل قراءة صفحات Word")
                    }
                }
            }
        }
    }

    fun toggleSelect(pageId: String) {
        _uiState.update { state ->
            state.copy(
                pages = state.pages.map {
                    if (it.id == pageId) it.copy(isSelected = !it.isSelected) else it
                }
            )
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            val allSelected = state.pages.isNotEmpty() && state.pages.all { it.isSelected }
            state.copy(
                pages = state.pages.map { it.copy(isSelected = !allSelected) }
            )
        }
    }

    fun clearSelection() {
        _uiState.update { state ->
            state.copy(pages = state.pages.map { it.copy(isSelected = false) })
        }
    }

    fun selectRange(fromPosition1Based: Int, toPosition1Based: Int) {
        val total = _uiState.value.pages.size
        if (total == 0) return
        val start = minOf(fromPosition1Based, toPosition1Based).coerceIn(1, total)
        val end = maxOf(fromPosition1Based, toPosition1Based).coerceIn(1, total)

        _uiState.update { state ->
            state.copy(
                pages = state.pages.mapIndexed { idx, item ->
                    val pos = idx + 1
                    item.copy(isSelected = pos in start..end)
                },
                notificationMessage = "تم تحديد الصفحات من $start إلى $end"
            )
        }
    }

    fun rotatePage(pageId: String) {
        _uiState.update { state ->
            val updated = state.pages.map {
                if (it.id == pageId) it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) else it
            }
            state.copy(pages = updated, hasUnsavedChanges = computeHasChanges(updated, state.originalPageCount))
        }
    }

    fun rotateSelectedPages() {
        _uiState.update { state ->
            val updated = state.pages.map {
                if (it.isSelected) it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) else it
            }
            state.copy(pages = updated, hasUnsavedChanges = computeHasChanges(updated, state.originalPageCount))
        }
    }

    fun movePageEarlier(pageId: String) {
        _uiState.update { state ->
            val list = state.pages.toMutableList()
            val idx = list.indexOfFirst { it.id == pageId }
            if (idx > 0) {
                val item = list.removeAt(idx)
                list.add(idx - 1, item)
                state.copy(pages = list, hasUnsavedChanges = computeHasChanges(list, state.originalPageCount))
            } else {
                state
            }
        }
    }

    fun movePageLater(pageId: String) {
        _uiState.update { state ->
            val list = state.pages.toMutableList()
            val idx = list.indexOfFirst { it.id == pageId }
            if (idx in 0 until list.lastIndex) {
                val item = list.removeAt(idx)
                list.add(idx + 1, item)
                state.copy(pages = list, hasUnsavedChanges = computeHasChanges(list, state.originalPageCount))
            } else {
                state
            }
        }
    }

    fun deleteSelectedPages() {
        val current = _uiState.value.pages
        val remaining = current.filterNot { it.isSelected }
        if (remaining.isEmpty()) {
            _uiState.update {
                it.copy(notificationMessage = "يجب إبقاء صفحة واحدة على الأقل داخل المستند")
            }
            return
        }
        val deletedCount = current.size - remaining.size
        _uiState.update { state ->
            state.copy(
                pages = remaining,
                hasUnsavedChanges = computeHasChanges(remaining, state.originalPageCount),
                notificationMessage = "تمت إزالة $deletedCount صفحة من العرض (اضغط حفظ لتثبيت التعديل)"
            )
        }
    }

    fun resetChanges() {
        val count = _uiState.value.originalPageCount
        if (count <= 0) return
        val prefix = if (_uiState.value.isPdf) "p_" else "w_"
        val fresh = (1..count).map { pageNum ->
            ManagedPageItem(id = "$prefix$pageNum", originalPageNumber = pageNum)
        }
        _uiState.update {
            it.copy(
                pages = fresh,
                hasUnsavedChanges = false,
                notificationMessage = "تم التراجع واستعادة الترتيب الأصلي"
            )
        }
    }

    fun saveChangesToCurrentPdf(onSaved: () -> Unit) {
        val state = _uiState.value
        val book = state.book ?: return
        if (!state.isPdf) {
            savePagesAsNewPdfCopy("${book.title} (معدل)", useAllPages = true) {
                onSaved()
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "جاري حفظ التعديلات على المستند...") }
            val tempOut = documentManager.createTempExportFile("${book.title}_edited")
            val specs = state.pages.map {
                PageOperationSpec(
                    originalPageIndex = it.originalPageNumber - 1,
                    rotationDegrees = it.rotationDegrees
                )
            }

            // Close current reader handle cleanly before replacing file on disk
            pdfEngine?.close()
            pdfEngine = null

            val buildRes = PdfDocumentProcessor.buildPdfFromSpec(
                sourcePdfFile = File(book.localFilePath),
                pagesSpec = specs,
                outputFile = tempOut
            ) { current, total ->
                _uiState.update { it.copy(processingLabel = "جاري معالجة الصفحة $current من $total...") }
            }

            if (buildRes.isSuccess) {
                val replaceRes = documentManager.replacePdfBookFile(book, tempOut)
                tempOut.delete()

                if (replaceRes.isSuccess) {
                    val updatedBook = replaceRes.getOrNull() ?: book
                    val newEngine = PdfEngine(File(updatedBook.localFilePath))
                    pdfEngine = newEngine
                    val newCount = newEngine.pageCount.coerceAtLeast(1)
                    val freshItems = (1..newCount).map { num ->
                        ManagedPageItem(id = "p_$num", originalPageNumber = num)
                    }
                    _uiState.update {
                        it.copy(
                            book = updatedBook,
                            isProcessing = false,
                            processingLabel = null,
                            pages = freshItems,
                            originalPageCount = newCount,
                            hasUnsavedChanges = false,
                            notificationMessage = "تم حفظ تعديلات الصفحات في المستند بنجاح"
                        )
                    }
                    onSaved()
                } else {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            processingLabel = null,
                            notificationMessage = "فشل تحديث بيانات الكتاب"
                        )
                    }
                }
            } else {
                tempOut.delete()
                // Reopen engine on failure
                try {
                    pdfEngine = PdfEngine(File(book.localFilePath))
                } catch (_: Exception) {
                }
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingLabel = null,
                        notificationMessage = "تعذر حفظ التعديلات: ${buildRes.exceptionOrNull()?.localizedMessage ?: ""}"
                    )
                }
            }
        }
    }

    fun extractPagesToLibrary(
        outputTitle: String,
        useAllPages: Boolean = false,
        onCreated: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val fileResult = generatePdfForTargetPages(outputTitle, useAllPages)
            val tempFile = fileResult.getOrNull()
            if (tempFile == null) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingLabel = null,
                        notificationMessage = "فشل استخراج الصفحات: ${fileResult.exceptionOrNull()?.message ?: ""}"
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(processingLabel = "جاري حفظ الملف في مكتبة وَرَق...") }
            val regRes = documentManager.registerGeneratedPdfInLibrary(tempFile, outputTitle)
            tempFile.delete()

            if (regRes.isSuccess) {
                val newBook = regRes.getOrNull()!!
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingLabel = null,
                        notificationMessage = "تم استخراج وحفظ «${newBook.title}» في المكتبة"
                    )
                }
                onCreated(newBook.id)
            } else {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingLabel = null,
                        notificationMessage = "تعذر الحفظ في المكتبة"
                    )
                }
            }
        }
    }

    fun savePagesAsNewPdfCopy(
        outputTitle: String,
        useAllPages: Boolean = true,
        onCreated: (Long) -> Unit
    ) {
        extractPagesToLibrary(outputTitle, useAllPages = useAllPages, onCreated = onCreated)
    }

    fun exportSelectedPagesToFile(
        outputTitle: String,
        onFileReady: (File) -> Unit
    ) {
        viewModelScope.launch {
            val fileResult = generatePdfForTargetPages(outputTitle, useAllPages = false)
            val file = fileResult.getOrNull()
            _uiState.update { it.copy(isProcessing = false, processingLabel = null) }
            if (file != null) {
                onFileReady(file)
            } else {
                _uiState.update {
                    it.copy(notificationMessage = "فشل تجهيز الملف للتصدير")
                }
            }
        }
    }

    fun copyExportedFileToUri(sourceFile: File, destUri: android.net.Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "جاري الحفظ في مجلد الجهاز...") }
            val res = documentManager.copyFileToUri(sourceFile, destUri)
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingLabel = null,
                    notificationMessage = if (res.isSuccess) "تم حفظ الملف المستخرج في جهازك بنجاح" else "تعذر الحفظ في المسار المحدد"
                )
            }
        }
    }

    private suspend fun generatePdfForTargetPages(
        outputTitle: String,
        useAllPages: Boolean
    ): Result<File> {
        val state = _uiState.value
        val book = state.book ?: return Result.failure(IllegalStateException("لا يوجد مستند مفتوح"))
        val targetItems = if (useAllPages || state.selectedCount == 0) {
            state.pages
        } else {
            state.pages.filter { it.isSelected }
        }

        if (targetItems.isEmpty()) {
            return Result.failure(IllegalArgumentException("لم يتم تحديد أي صفحات"))
        }

        _uiState.update { it.copy(isProcessing = true, processingLabel = "جاري تجهيز الصفحات...") }
        val tempFile = documentManager.createTempExportFile(outputTitle)

        return if (state.isPdf) {
            val specs = targetItems.map {
                PageOperationSpec(
                    originalPageIndex = it.originalPageNumber - 1,
                    rotationDegrees = it.rotationDegrees
                )
            }
            PdfDocumentProcessor.buildPdfFromSpec(
                sourcePdfFile = File(book.localFilePath),
                pagesSpec = specs,
                outputFile = tempFile
            ) { current, total ->
                _uiState.update { it.copy(processingLabel = "جاري استخراج الصفحة $current من $total...") }
            }
        } else {
            val selectedDocPages = targetItems.mapNotNull { item ->
                state.wordPages.getOrNull(item.originalPageNumber - 1)
            }
            val rotMap = targetItems.associate { it.originalPageNumber to it.rotationDegrees }
            PdfDocumentProcessor.exportWordPagesToPdf(
                documentTitle = outputTitle.ifBlank { book.title },
                pages = selectedDocPages,
                rotations = rotMap,
                outputFile = tempFile
            ) { current, total ->
                _uiState.update { it.copy(processingLabel = "جاري تصدير الصفحة $current من $total...") }
            }
        }
    }

    fun dismissNotification() {
        _uiState.update { it.copy(notificationMessage = null) }
    }

    private fun computeHasChanges(items: List<ManagedPageItem>, originalCount: Int): Boolean {
        if (items.size != originalCount) return true
        return items.withIndex().any { (index, item) ->
            item.originalPageNumber != index + 1 || item.rotationDegrees != 0
        }
    }

    override fun onCleared() {
        super.onCleared()
        pdfEngine?.close()
        pdfEngine = null
    }
}

package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.WaraqDatabase
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PageManagerScreen
import com.example.ui.screens.PdfReaderScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WordReaderScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.LibraryViewModel
import com.example.viewmodel.PageManagerViewModel
import com.example.viewmodel.ReaderViewModel
import com.example.widget.WaraqWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val libraryViewModel: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val libraryUiState by libraryViewModel.uiState.collectAsState()

            MyApplicationTheme(themeId = libraryUiState.currentThemeId) {
                val navController = rememberNavController()

                // Check for intent actions (e.g. Widget click or external View intent)
                LaunchedEffect(intent) {
                    handleIntent(intent) { route ->
                        navController.navigate(route) {
                            popUpTo("library") { inclusive = false }
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = "library",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("library") {
                        LibraryScreen(
                            uiState = libraryUiState,
                            onBookClick = { bookId ->
                                openBookScreen(bookId) { route ->
                                    navController.navigate(route)
                                }
                            },
                            onImportUri = { uri ->
                                libraryViewModel.importDocument(uri)
                            },
                            onRenameBook = { id, title ->
                                libraryViewModel.renameBook(id, title)
                            },
                            onDeleteBook = { book ->
                                libraryViewModel.deleteBook(book)
                            },
                            onSortChanged = { option ->
                                libraryViewModel.onSortOptionChanged(option)
                            },
                            onCategorySelected = { category ->
                                libraryViewModel.onCategoryTabSelected(category)
                            },
                            onBottomNavTabSelected = { tab ->
                                libraryViewModel.onBottomNavTabSelected(tab)
                            },
                            onThemeSelected = { themeId ->
                                libraryViewModel.selectTheme(themeId)
                            },
                            onSearchChanged = { query ->
                                libraryViewModel.onSearchQueryChanged(query)
                            },
                            onSettingsClick = {
                                navController.navigate("settings")
                            },
                            onManagePagesClick = { bookId ->
                                navController.navigate("page_manager/$bookId")
                            },
                            onEnterSelectionMode = { initialId ->
                                libraryViewModel.enterSelectionMode(initialId)
                            },
                            onToggleBookSelection = { bookId ->
                                libraryViewModel.toggleBookSelection(bookId)
                            },
                            onSelectAllSelection = {
                                libraryViewModel.selectAllVisibleBooks()
                            },
                            onClearSelection = {
                                libraryViewModel.clearSelection()
                            },
                            onToggleBookMark = { book ->
                                libraryViewModel.toggleBookMarked(book)
                            },
                            onMarkSelected = { mark ->
                                libraryViewModel.markSelectedBooks(mark)
                            },
                            onDeleteSelected = {
                                libraryViewModel.deleteSelectedBooks()
                            },
                            onDismissNotification = {
                                libraryViewModel.dismissNotification()
                            }
                        )
                    }

                    composable(
                        route = "pdf_reader/{bookId}",
                        arguments = listOf(navArgument("bookId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val bookId = backStackEntry.arguments?.getLong("bookId") ?: -1L
                        val readerViewModel: ReaderViewModel = viewModel()
                        val bookModified by backStackEntry.savedStateHandle
                            .getStateFlow("book_modified", false)
                            .collectAsState()

                        LaunchedEffect(bookId) {
                            readerViewModel.loadBook(bookId)
                        }
                        LaunchedEffect(bookModified) {
                            if (bookModified) {
                                backStackEntry.savedStateHandle["book_modified"] = false
                                readerViewModel.reloadBook(bookId)
                            }
                        }
                        val uiState by readerViewModel.uiState.collectAsState()

                        PdfReaderScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onPageScrolled = { readerViewModel.onPdfPageScrolledTo(it) },
                            onGoToPage = { readerViewModel.goToPdfPage(it) },
                            onToggleBookmark = { readerViewModel.toggleBookmark() },
                            onToggleFitMode = { readerViewModel.toggleFitMode() },
                            onToggleTextSelectionMode = { readerViewModel.toggleTextSelectionMode() },
                            onToggleSearch = { readerViewModel.togglePdfSearch() },
                            onSearchQueryChanged = { readerViewModel.onPdfSearchQueryChanged(it) },
                            onSelectSearchResult = { readerViewModel.selectPdfSearchResult(it) },
                            onNextSearchResult = { readerViewModel.nextPdfSearchResult() },
                            onPreviousSearchResult = { readerViewModel.previousPdfSearchResult() },
                            onManagePagesClick = {
                                navController.navigate("page_manager/$bookId")
                            },
                            onSaveImmediately = { readerViewModel.savePositionImmediately() },
                            onSharePageText = { onReady -> readerViewModel.loadPageTextForShare(onReady) },
                            getPdfEngine = { readerViewModel.getPdfEngine() }
                        )
                    }

                    composable(
                        route = "word_reader/{bookId}",
                        arguments = listOf(navArgument("bookId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val bookId = backStackEntry.arguments?.getLong("bookId") ?: -1L
                        val readerViewModel: ReaderViewModel = viewModel()
                        LaunchedEffect(bookId) {
                            readerViewModel.loadBook(bookId)
                        }
                        val uiState by readerViewModel.uiState.collectAsState()

                        WordReaderScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onScrolledTo = { readerViewModel.onWordPageScrolledTo(it) },
                            onChangeFontSize = { readerViewModel.changeWordFontSize(it) },
                            onToggleBookmark = { readerViewModel.toggleBookmark() },
                            onToggleSearch = { readerViewModel.toggleWordSearch() },
                            onSearchQueryChanged = { readerViewModel.onWordSearchQueryChanged(it) },
                            onSelectSearchResult = { readerViewModel.selectWordSearchResult(it) },
                            onNextSearchResult = { readerViewModel.nextWordSearchResult() },
                            onPreviousSearchResult = { readerViewModel.previousWordSearchResult() },
                            onManagePagesClick = {
                                navController.navigate("page_manager/$bookId")
                            },
                            onSaveImmediately = { readerViewModel.savePositionImmediately() }
                        )
                    }

                    composable(
                        route = "page_manager/{bookId}",
                        arguments = listOf(navArgument("bookId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val bookId = backStackEntry.arguments?.getLong("bookId") ?: -1L
                        val pageManagerViewModel: PageManagerViewModel = viewModel()
                        LaunchedEffect(bookId) {
                            pageManagerViewModel.loadBook(bookId)
                        }
                        val pmState by pageManagerViewModel.uiState.collectAsState()

                        PageManagerScreen(
                            uiState = pmState,
                            getPdfEngine = { pageManagerViewModel.getPdfEngine() },
                            onBack = { didModify ->
                                if (didModify) {
                                    navController.previousBackStackEntry?.savedStateHandle?.set("book_modified", true)
                                }
                                navController.popBackStack()
                            },
                            onToggleSelect = { pageManagerViewModel.toggleSelect(it) },
                            onSelectAll = { pageManagerViewModel.selectAll() },
                            onClearSelection = { pageManagerViewModel.clearSelection() },
                            onSelectRange = { from, to -> pageManagerViewModel.selectRange(from, to) },
                            onRotatePage = { pageManagerViewModel.rotatePage(it) },
                            onRotateSelected = { pageManagerViewModel.rotateSelectedPages() },
                            onMoveEarlier = { pageManagerViewModel.movePageEarlier(it) },
                            onMoveLater = { pageManagerViewModel.movePageLater(it) },
                            onDeleteSelected = { pageManagerViewModel.deleteSelectedPages() },
                            onResetChanges = { pageManagerViewModel.resetChanges() },
                            onSaveChangesToBook = { onSaved ->
                                pageManagerViewModel.saveChangesToCurrentPdf(onSaved)
                            },
                            onExtractToLibrary = { title, useAll, onCreated ->
                                pageManagerViewModel.extractPagesToLibrary(title, useAll, onCreated)
                            },
                            onPrepareExportFile = { title, onReady ->
                                pageManagerViewModel.exportSelectedPagesToFile(title, onReady)
                            },
                            onCopyFileToUri = { file, uri ->
                                pageManagerViewModel.copyExportedFileToUri(file, uri)
                            },
                            onOpenExtractedBook = { newBookId ->
                                navController.navigate("pdf_reader/$newBookId") {
                                    popUpTo("page_manager/$bookId") { inclusive = true }
                                }
                            },
                            onDismissNotification = { pageManagerViewModel.dismissNotification() }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private suspend fun handleIntent(intent: Intent?, onNavigate: (String) -> Unit) {
        if (intent == null) return

        // 1. Handled click from Home Screen Widget
        val bookId = intent.getLongExtra(WaraqWidgetProvider.EXTRA_BOOK_ID, -1L)
        if (bookId != -1L) {
            openBookScreen(bookId, onNavigate)
            return
        }

        // 2. Handled external open with ACTION_VIEW (e.g. user clicked a PDF or Word file)
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val uri = intent.data ?: return
            val docManager = com.example.data.engine.DocumentManager(this)
            val result = docManager.importDocument(uri)
            if (result.isSuccess) {
                val book = result.getOrNull()
                if (book != null) {
                    openBookScreen(book.id, onNavigate)
                }
            }
        }
    }

    private fun openBookScreen(bookId: Long, onNavigate: (String) -> Unit) {
        val db = WaraqDatabase.getInstance(this)
        lifecycleScope.launch(Dispatchers.IO) {
            val book = db.bookDao().getBookById(bookId) ?: return@launch
            withContext(Dispatchers.Main) {
                val route = if (book.fileType.equals("PDF", ignoreCase = true)) {
                    "pdf_reader/$bookId"
                } else {
                    "word_reader/$bookId"
                }
                onNavigate(route)
            }
        }
    }
}

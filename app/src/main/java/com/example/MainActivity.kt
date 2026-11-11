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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.WaraqDatabase
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PdfReaderScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WordReaderScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.LibraryViewModel
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
            MyApplicationTheme {
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
                        val uiState by libraryViewModel.uiState.collectAsState()
                        LibraryScreen(
                            uiState = uiState,
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
                            onSearchChanged = { query ->
                                libraryViewModel.onSearchQueryChanged(query)
                            },
                            onToggleView = {
                                libraryViewModel.toggleViewMode()
                            },
                            onSettingsClick = {
                                navController.navigate("settings")
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
                        LaunchedEffect(bookId) {
                            readerViewModel.loadBook(bookId)
                        }
                        val uiState by readerViewModel.uiState.collectAsState()

                        PdfReaderScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onGoToPage = { readerViewModel.goToPdfPage(it) },
                            onNextPage = { readerViewModel.nextPdfPage() },
                            onPreviousPage = { readerViewModel.previousPdfPage() },
                            onToggleBookmark = { readerViewModel.toggleBookmark() },
                            onToggleFitMode = { readerViewModel.toggleFitMode() },
                            onToggleTextSelectionMode = { readerViewModel.toggleTextSelectionMode() },
                            onToggleSearch = { readerViewModel.togglePdfSearch() },
                            onSearchQueryChanged = { readerViewModel.onPdfSearchQueryChanged(it) },
                            onSaveImmediately = { readerViewModel.savePositionImmediately() }
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
                            onScrolledTo = { readerViewModel.onWordScrolledTo(it) },
                            onChangeFontSize = { readerViewModel.changeWordFontSize(it) },
                            onToggleBookmark = { readerViewModel.toggleBookmark() },
                            onSaveImmediately = { readerViewModel.savePositionImmediately() }
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
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
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

package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.BookEntity
import com.example.ui.components.BookCard
import com.example.ui.components.HeroContinueReadingCard
import com.example.ui.components.WaraqEmptyState
import com.example.ui.components.WaraqTopBar
import com.example.ui.theme.WaraqBorder
import com.example.ui.theme.WaraqDarkBg
import com.example.ui.theme.WaraqError
import com.example.ui.theme.WaraqNeonGreen
import com.example.ui.theme.WaraqSurfaceElevated
import com.example.ui.theme.WaraqTextPrimary
import com.example.ui.theme.WaraqTextSecondary
import com.example.viewmodel.LibraryUiState
import com.example.viewmodel.SortOption
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onBookClick: (Long) -> Unit,
    onImportUri: (Uri) -> Unit,
    onRenameBook: (Long, String) -> Unit,
    onDeleteBook: (BookEntity) -> Unit,
    onSortChanged: (SortOption) -> Unit,
    onSearchChanged: (String) -> Unit,
    onToggleView: () -> Unit,
    onSettingsClick: () -> Unit,
    onDismissNotification: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showSearchRow by remember { mutableStateOf(false) }

    // Rename dialog state
    var bookToRename by remember { mutableStateOf<BookEntity?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    // Delete dialog state
    var bookToDelete by remember { mutableStateOf<BookEntity?>(null) }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportUri(uri)
        }
    }

    LaunchedEffect(uiState.notificationMessage) {
        uiState.notificationMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onDismissNotification()
        }
    }

    Scaffold(
        topBar = {
            Column {
                WaraqTopBar(
                    isGridView = uiState.isGridView,
                    onToggleView = onToggleView,
                    onSearchClick = { showSearchRow = !showSearchRow },
                    onSettingsClick = onSettingsClick
                )

                // Inline search bar
                AnimatedVisibility(visible = showSearchRow) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(WaraqDarkBg)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchChanged,
                            placeholder = { Text("ابحث في أسماء الكتب...", color = WaraqTextSecondary) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = WaraqNeonGreen)
                            },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchChanged("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "مسح", tint = WaraqTextSecondary)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WaraqNeonGreen,
                                unfocusedBorderColor = WaraqBorder,
                                focusedTextColor = WaraqTextPrimary,
                                unfocusedTextColor = WaraqTextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("library_search_input")
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    filePickerLauncher.launch(
                        arrayOf(
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/msword"
                        )
                    )
                },
                containerColor = WaraqNeonGreen,
                contentColor = WaraqDarkBg,
                shape = CircleShape,
                modifier = Modifier.testTag("fab_import_document")
            ) {
                if (uiState.isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = WaraqDarkBg,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "استيراد مستند",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = WaraqDarkBg,
        modifier = modifier
    ) { innerPadding ->

        if (uiState.books.isEmpty() && uiState.searchQuery.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                WaraqEmptyState(
                    onImportClick = {
                        filePickerLauncher.launch(
                            arrayOf(
                                "application/pdf",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/msword"
                            )
                        )
                    }
                )
            }
        } else {
            if (uiState.isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 80.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("library_books_grid")
                ) {
                    // Sort Chips
                    item(span = { GridItemSpan(2) }) {
                        SortChipsRow(
                            selected = uiState.sortOption,
                            onSelect = onSortChanged
                        )
                    }

                    // Continue reading hero card (if not searching)
                    if (uiState.searchQuery.isEmpty() && uiState.mostRecentBook != null) {
                        item(span = { GridItemSpan(2) }) {
                            HeroContinueReadingCard(
                                book = uiState.mostRecentBook,
                                onContinueClick = { onBookClick(uiState.mostRecentBook.id) }
                            )
                        }
                    }

                    // Section title
                    item(span = { GridItemSpan(2) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = if (uiState.searchQuery.isEmpty()) "كتبي (${uiState.books.size})" else "نتائج البحث (${uiState.books.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = WaraqTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Grid items
                    items(uiState.books, key = { it.id }) { book ->
                        BookCard(
                            book = book,
                            isGrid = true,
                            onClick = { onBookClick(book.id) },
                            onRenameClick = {
                                bookToRename = book
                                renameInputText = book.title
                            },
                            onShareClick = {
                                shareBookFile(context, book)
                            },
                            onDeleteClick = {
                                bookToDelete = book
                            }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 80.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("library_books_list")
                ) {
                    item {
                        SortChipsRow(
                            selected = uiState.sortOption,
                            onSelect = onSortChanged
                        )
                    }

                    if (uiState.searchQuery.isEmpty() && uiState.mostRecentBook != null) {
                        item {
                            HeroContinueReadingCard(
                                book = uiState.mostRecentBook,
                                onContinueClick = { onBookClick(uiState.mostRecentBook.id) }
                            )
                        }
                    }

                    item {
                        Text(
                            text = if (uiState.searchQuery.isEmpty()) "كتبي (${uiState.books.size})" else "نتائج البحث (${uiState.books.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = WaraqTextPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    items(uiState.books, key = { it.id }) { book ->
                        BookCard(
                            book = book,
                            isGrid = false,
                            onClick = { onBookClick(book.id) },
                            onRenameClick = {
                                bookToRename = book
                                renameInputText = book.title
                            },
                            onShareClick = {
                                shareBookFile(context, book)
                            },
                            onDeleteClick = {
                                bookToDelete = book
                            }
                        )
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (bookToRename != null) {
        AlertDialog(
            onDismissRequest = { bookToRename = null },
            title = { Text("إعادة تسمية الكتاب", color = WaraqTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    label = { Text("اسم الكتاب", color = WaraqTextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WaraqNeonGreen,
                        unfocusedBorderColor = WaraqBorder,
                        focusedTextColor = WaraqTextPrimary,
                        unfocusedTextColor = WaraqTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val book = bookToRename
                        if (book != null && renameInputText.isNotBlank()) {
                            onRenameBook(book.id, renameInputText.trim())
                        }
                        bookToRename = null
                    }
                ) {
                    Text("حفظ", color = WaraqNeonGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToRename = null }) {
                    Text("إلغاء", color = WaraqTextSecondary)
                }
            },
            containerColor = WaraqSurfaceElevated
        )
    }

    // Delete Confirmation Dialog
    if (bookToDelete != null) {
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text("إزالة من المكتبة", color = WaraqTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "هل ترغب بإزالة «${bookToDelete?.title}» من مكتبة التطبيق؟\n(ملاحظة: لن يتم حذف الملف الأصلي من هاتفك)",
                    color = WaraqTextSecondary,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        bookToDelete?.let { onDeleteBook(it) }
                        bookToDelete = null
                    }
                ) {
                    Text("إزالة", color = WaraqError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) {
                    Text("إلغاء", color = WaraqTextSecondary)
                }
            },
            containerColor = WaraqSurfaceElevated
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortChipsRow(
    selected: SortOption,
    onSelect: (SortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SortOption.entries.forEach { option ->
            val isSelected = option == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option) },
                label = { Text(option.label, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = WaraqSurfaceElevated,
                    labelColor = WaraqTextSecondary,
                    selectedContainerColor = WaraqNeonGreen,
                    selectedLabelColor = WaraqDarkBg
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = WaraqBorder,
                    selectedBorderColor = WaraqNeonGreen
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

private fun shareBookFile(context: android.content.Context, book: BookEntity) {
    try {
        val file = File(book.localFilePath)
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (book.fileType.equals("PDF", ignoreCase = true)) "application/pdf" else "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, book.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة الملف"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

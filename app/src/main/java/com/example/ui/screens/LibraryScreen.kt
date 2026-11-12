package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.WaraqThemeId
import com.example.data.model.BookEntity
import com.example.data.model.BookmarkEntity
import com.example.ui.components.BookCard
import com.example.ui.components.HeroContinueReadingCard
import com.example.ui.components.WaraqEmptyState
import com.example.ui.theme.WaraqBorder
import com.example.ui.theme.WaraqError
import com.example.ui.theme.WaraqFolderYellow
import com.example.ui.theme.WaraqGreenContainer
import com.example.ui.theme.WaraqNeonGreen
import com.example.ui.theme.WaraqPdfColor
import com.example.ui.theme.WaraqSurfaceElevated
import com.example.ui.theme.WaraqTextPrimary
import com.example.ui.theme.WaraqTextSecondary
import com.example.viewmodel.BottomNavTab
import com.example.viewmodel.LibraryUiState
import com.example.viewmodel.SortOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    onBookClick: (Long) -> Unit,
    onImportUri: (Uri) -> Unit,
    onRenameBook: (Long, String) -> Unit,
    onDeleteBook: (BookEntity) -> Unit,
    onSortChanged: (SortOption) -> Unit,
    onCategorySelected: (String) -> Unit,
    onBottomNavTabSelected: (BottomNavTab) -> Unit,
    onThemeSelected: (String) -> Unit,
    onSearchChanged: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onManagePagesClick: (Long) -> Unit = {},
    onDismissNotification: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var isSearchActive by remember { mutableStateOf(false) }

    // Dialog states
    var bookToRename by remember { mutableStateOf<BookEntity?>(null) }
    var renameInputText by remember { mutableStateOf("") }
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Top Search & Status Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "وَرَق",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Offline Guarantee Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "محلي 100% بلا نت",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "بحث",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "الثيمات والمظهر",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                // Inline Search Bar
                AnimatedVisibility(visible = isSearchActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchChanged,
                            placeholder = { Text("ابحث في أسماء الملفات والمستندات...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { onSearchChanged("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "مسح", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Top Category Tabs Bar (Screenshot Replica: الكل | PDF | Word | Excel | PPT | المفضلة)
                if (uiState.currentBottomNavTab == BottomNavTab.ALL_FILES) {
                    TopCategoryTabsRow(
                        selectedCategory = uiState.selectedCategoryTab,
                        onSelectCategory = onCategorySelected
                    )
                }
            }
        },
        bottomBar = {
            // Bottom Navigation Bar (Screenshot Replica: كل الملفات | الأخيرة | العلامات المرجعية | الأدوات)
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.shadow(8.dp)
            ) {
                NavigationBarItem(
                    selected = uiState.currentBottomNavTab == BottomNavTab.ALL_FILES,
                    onClick = { onBottomNavTabSelected(BottomNavTab.ALL_FILES) },
                    icon = { Icon(Icons.Default.Description, contentDescription = null) },
                    label = { Text("كل الملفات", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                )

                NavigationBarItem(
                    selected = uiState.currentBottomNavTab == BottomNavTab.RECENTS,
                    onClick = { onBottomNavTabSelected(BottomNavTab.RECENTS) },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("الأخيرة", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                )

                NavigationBarItem(
                    selected = uiState.currentBottomNavTab == BottomNavTab.BOOKMARKS,
                    onClick = { onBottomNavTabSelected(BottomNavTab.BOOKMARKS) },
                    icon = { Icon(Icons.Default.BookmarkBorder, contentDescription = null) },
                    label = { Text("العلامات", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                )

                NavigationBarItem(
                    selected = uiState.currentBottomNavTab == BottomNavTab.TOOLS,
                    onClick = { onBottomNavTabSelected(BottomNavTab.TOOLS) },
                    icon = { Icon(Icons.Default.Widgets, contentDescription = null) },
                    label = { Text("الثيمات والأدوات", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                )
            }
        },
        floatingActionButton = {
            // Red circular floating + button (Screenshot replica)
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
                containerColor = WaraqPdfColor,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(56.dp)
                    .testTag("fab_import_document")
            ) {
                if (uiState.isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
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
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->

        when (uiState.currentBottomNavTab) {
            // Tab 1: ALL FILES (matching screenshot with "المضاف مؤخراً" banner & file list)
            BottomNavTab.ALL_FILES -> {
                AllFilesContent(
                    uiState = uiState,
                    innerPadding = innerPadding,
                    onBookClick = onBookClick,
                    onManagePagesClick = onManagePagesClick,
                    onRenameClick = { book ->
                        bookToRename = book
                        renameInputText = book.title
                    },
                    onShareClick = { book ->
                        shareBookFile(context, book)
                    },
                    onDeleteClick = { book ->
                        bookToDelete = book
                    },
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

            // Tab 2: RECENTS (قسم الأخيرة - specifically requested by user)
            BottomNavTab.RECENTS -> {
                RecentsContent(
                    recentBooks = uiState.recentBooks,
                    innerPadding = innerPadding,
                    onBookClick = onBookClick,
                    onManagePagesClick = onManagePagesClick,
                    onRenameClick = { book ->
                        bookToRename = book
                        renameInputText = book.title
                    },
                    onShareClick = { book ->
                        shareBookFile(context, book)
                    },
                    onDeleteClick = { book ->
                        bookToDelete = book
                    }
                )
            }

            // Tab 3: BOOKMARKS
            BottomNavTab.BOOKMARKS -> {
                BookmarksContent(
                    bookmarks = uiState.bookmarks,
                    books = uiState.books,
                    innerPadding = innerPadding,
                    onBookmarkClick = { bookId ->
                        onBookClick(bookId)
                    }
                )
            }

            // Tab 4: THEMES & TOOLS
            BottomNavTab.TOOLS -> {
                ThemesAndToolsContent(
                    currentThemeId = uiState.currentThemeId,
                    onThemeSelected = onThemeSelected,
                    onOpenSettings = onSettingsClick,
                    innerPadding = innerPadding
                )
            }
        }
    }

    // Rename Dialog
    if (bookToRename != null) {
        AlertDialog(
            onDismissRequest = { bookToRename = null },
            title = { Text("إعادة تسمية الملف", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    label = { Text("اسم المستند", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
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
                    Text("حفظ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToRename = null }) {
                    Text("إلغاء", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Delete Confirmation Dialog
    if (bookToDelete != null) {
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text("إزالة من القائمة", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "هل ترغب بإزالة «${bookToDelete?.title}» من القائمة؟\n(ملاحظة: لن يُحذف الملف الأصلي من هاتفك)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Text("إلغاء", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

/**
 * Top Category Tabs Row (Screenshot Replica: الكل | PDF | Word | المفضلة)
 */
@Composable
fun TopCategoryTabsRow(
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf("الكل", "PDF", "Word", "المفضلة")
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        categories.forEach { category ->
            val isSelected = category == selectedCategory
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onSelectCategory(category) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = category,
                    color = if (isSelected) WaraqPdfColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Active underline indicator
                Box(
                    modifier = Modifier
                        .height(3.dp)
                        .width(if (isSelected) 24.dp else 0.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(if (isSelected) WaraqPdfColor else Color.Transparent)
                )
            }
        }
    }
}

/**
 * All Files Tab Content (Screenshot replica with folder banner & document items)
 */
@Composable
fun AllFilesContent(
    uiState: LibraryUiState,
    innerPadding: PaddingValues,
    onBookClick: (Long) -> Unit,
    onManagePagesClick: (Long) -> Unit,
    onRenameClick: (BookEntity) -> Unit,
    onShareClick: (BookEntity) -> Unit,
    onDeleteClick: (BookEntity) -> Unit,
    onImportClick: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 6.dp,
            bottom = innerPadding.calculateBottomPadding() + 80.dp
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        // "المضاف مؤخراً" Banner (Screenshot replica)
        item {
            RecentlyAddedBanner(
                count = uiState.allBooksCount,
                onClick = {}
            )
        }

        if (uiState.books.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    WaraqEmptyState(onImportClick = onImportClick)
                }
            }
        } else {
            items(uiState.books, key = { it.id }) { book ->
                BookCard(
                    book = book,
                    isGrid = false,
                    onClick = { onBookClick(book.id) },
                    onManagePagesClick = { onManagePagesClick(book.id) },
                    onRenameClick = { onRenameClick(book) },
                    onShareClick = { onShareClick(book) },
                    onDeleteClick = { onDeleteClick(book) }
                )
            }
        }
    }
}

/**
 * Recently Added Folder Banner (Screenshot Replica)
 */
@Composable
fun RecentlyAddedBanner(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Yellow Folder Icon
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = WaraqFolderYellow,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "المضاف مؤخراً",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Count Pill Badge (e.g. +53 in screenshot)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFEBEE))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "+$count",
                    color = WaraqPdfColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Recents Tab Content (قسم الأخيرة - specifically requested by user)
 */
@Composable
fun RecentsContent(
    recentBooks: List<BookEntity>,
    innerPadding: PaddingValues,
    onBookClick: (Long) -> Unit,
    onManagePagesClick: (Long) -> Unit,
    onRenameClick: (BookEntity) -> Unit,
    onShareClick: (BookEntity) -> Unit,
    onDeleteClick: (BookEntity) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = innerPadding.calculateBottomPadding() + 80.dp
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "آخر الملفات التي تم فتحها (${recentBooks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (recentBooks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لم تقم بفتح أي ملفات بعد.\nاختر أي كتاب من «كل الملفات» لبدء القراءة وسيحفظ موضعه هنا تلقائياً.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                }
            }
        } else {
            // First book as hero continue card
            item {
                val topBook = recentBooks.first()
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    HeroContinueReadingCard(
                        book = topBook,
                        onContinueClick = { onBookClick(topBook.id) }
                    )
                }
            }

            items(recentBooks, key = { it.id }) { book ->
                BookCard(
                    book = book,
                    isGrid = false,
                    onClick = { onBookClick(book.id) },
                    onManagePagesClick = { onManagePagesClick(book.id) },
                    onRenameClick = { onRenameClick(book) },
                    onShareClick = { onShareClick(book) },
                    onDeleteClick = { onDeleteClick(book) }
                )
            }
        }
    }
}

/**
 * Bookmarks Tab Content
 */
@Composable
fun BookmarksContent(
    bookmarks: List<BookmarkEntity>,
    books: List<BookEntity>,
    innerPadding: PaddingValues,
    onBookmarkClick: (Long) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 12.dp,
            bottom = innerPadding.calculateBottomPadding() + 80.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "العلامات المرجعية المحفوظة (${bookmarks.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
        }

        if (bookmarks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد علامات مرجعية محفوظة.\nأثناء قراءة أي كتاب، اضغط على زر الإشارة المرجعية لحفظ الصفحة والرجوع إليها هنا.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                }
            }
        } else {
            items(bookmarks, key = { it.id }) { bookmark ->
                val book = books.find { it.id == bookmark.bookId }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBookmarkClick(bookmark.bookId) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = book?.title ?: "مستند",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "صفحة / موضع: ${bookmark.pageNumber}",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (bookmark.snippet.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "«${bookmark.snippet}»",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Themes & Tools Tab Content (ثيمات وألوان التطبيق + إعدادات الأوفلاين)
 */
@Composable
fun ThemesAndToolsContent(
    currentThemeId: String,
    onThemeSelected: (String) -> Unit,
    onOpenSettings: () -> Unit,
    innerPadding: PaddingValues
) {
    LazyColumn(
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 12.dp,
            bottom = innerPadding.calculateBottomPadding() + 80.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // Offline Security Guarantee Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "يعمل بدون اتصال بالإنترنت تماماً",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "تُقرأ جميع الملفات محلياً وتُحفظ المواضع في قاعدة بيانات داخلية فورياً حتى لو كان هاتفك في وضع الطيران.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "اختر مظهر وألوان التطبيق",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // 5 Themes Selection Cards
        items(WaraqThemeId.entries.toList(), key = { it.name }) { theme ->
            val isSelected = theme.name == currentThemeId
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onThemeSelected(theme.name) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Preview Swatch
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(parseHexColor(theme.previewColorHex)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = theme.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = theme.desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isSelected) {
                        Text(
                            text = "مفعّل",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("المزيد من تفضيلات القراءة والويدجت", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        WaraqNeonGreen
    }
}

private fun shareBookFile(context: android.content.Context, book: BookEntity) {
    try {
        val file = java.io.File(book.localFilePath)
        if (!file.exists()) return

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = if (book.fileType.equals("PDF", ignoreCase = true)) "application/pdf" else "application/octet-stream"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, book.title)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "مشاركة الملف"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

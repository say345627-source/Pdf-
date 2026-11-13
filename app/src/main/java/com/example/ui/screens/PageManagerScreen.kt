package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.engine.PdfEngine
import com.example.data.model.DocElement
import com.example.data.model.DocPage
import com.example.ui.theme.WaraqError
import com.example.viewmodel.ManagedPageItem
import com.example.viewmodel.PageManagerUiState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageManagerScreen(
    uiState: PageManagerUiState,
    getPdfEngine: () -> PdfEngine?,
    onBack: (didModifyBook: Boolean) -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectRange: (Int, Int) -> Unit,
    onRotatePage: (String) -> Unit,
    onRotateSelected: () -> Unit,
    onMoveEarlier: (String) -> Unit,
    onMoveLater: (String) -> Unit,
    onDeleteSelected: () -> Unit,
    onResetChanges: () -> Unit,
    onSaveChangesToBook: (onSaved: () -> Unit) -> Unit,
    onExtractToLibrary: (title: String, useAll: Boolean, onCreated: (Long) -> Unit) -> Unit,
    onPrepareExportFile: (title: String, onReady: (File) -> Unit) -> Unit,
    onCopyFileToUri: (File, android.net.Uri) -> Unit,
    onOpenExtractedBook: (Long) -> Unit,
    onDismissNotification: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var didModifyCurrentBook by remember { mutableStateOf(false) }
    var showRangeDialog by remember { mutableStateOf(false) }
    var rangeFromInput by remember { mutableStateOf("1") }
    var rangeToInput by remember { mutableStateOf("1") }

    var showExtractDialog by remember { mutableStateOf(false) }
    var extractTitleInput by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSaveCopyDialog by remember { mutableStateOf(false) }
    var saveCopyTitleInput by remember { mutableStateOf("") }

    var pendingTempExportFile by remember { mutableStateOf<File?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        val srcFile = pendingTempExportFile
        if (destUri != null && srcFile != null) {
            onCopyFileToUri(srcFile, destUri)
        }
    }

    LaunchedEffect(uiState.notificationMessage) {
        uiState.notificationMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onDismissNotification()
        }
    }

    BackHandler {
        onBack(didModifyCurrentBook)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "إدارة واستخراج الصفحات",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (uiState.selectedCount > 0) {
                                "تم تحديد ${uiState.selectedCount} من ${uiState.pages.size} صفحة"
                            } else {
                                "${uiState.book?.title ?: ""} • ${uiState.pages.size} صفحة"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { onBack(didModifyCurrentBook) },
                        modifier = Modifier.testTag("page_manager_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    // Range selection dialog trigger
                    IconButton(
                        onClick = {
                            rangeFromInput = "1"
                            rangeToInput = uiState.pages.size.coerceAtLeast(1).toString()
                            showRangeDialog = true
                        },
                        modifier = Modifier.testTag("page_manager_range_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "تحديد نطاق صفحات",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Select All / Clear toggle
                    IconButton(
                        onClick = onSelectAll,
                        modifier = Modifier.testTag("page_manager_select_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = "تحديد الكل",
                            tint = if (uiState.selectedCount == uiState.pages.size && uiState.pages.isNotEmpty()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            }
                        )
                    }

                    // Reset changes if modified
                    if (uiState.hasUnsavedChanges) {
                        IconButton(onClick = onResetChanges) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "استعادة الأصل",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (uiState.selectedCount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Primary Action: Extract selected pages
                            Button(
                                onClick = {
                                    extractTitleInput = "${uiState.book?.title ?: "مستند"} - مستخرج (${uiState.selectedCount} ص)"
                                    showExtractDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp)
                                    .testTag("extract_selected_pages_button")
                            ) {
                                Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("استخراج (${uiState.selectedCount})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            // Rotate selected pages
                            FilledTonalButton(
                                onClick = onRotateSelected,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(48.dp)
                                    .testTag("rotate_selected_pages_button")
                            ) {
                                Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تدوير", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // Delete selected pages
                            OutlinedButton(
                                onClick = { showDeleteConfirm = true },
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, WaraqError.copy(alpha = 0.7f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = WaraqError),
                                modifier = Modifier
                                    .weight(0.8f)
                                    .height(48.dp)
                                    .testTag("delete_selected_pages_button")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("حذف", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else if (uiState.hasUnsavedChanges) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "لديك تعديلات غير محفوظة (إعادة ترتيب / تدوير / حذف)",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (uiState.isPdf) {
                                    Button(
                                        onClick = {
                                            onSaveChangesToBook {
                                                didModifyCurrentBook = true
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("save_pdf_changes_button")
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("حفظ في المستند", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                FilledTonalButton(
                                    onClick = {
                                        saveCopyTitleInput = "${uiState.book?.title ?: "مستند"} (نسخة معدلة)"
                                        showSaveCopyDialog = true
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("حفظ كملف PDF جديد", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "اضغط على أي صفحة لتحديدها، أو استخدم الترتيب والتدوير والاستخراج",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = {
                                    rangeFromInput = "1"
                                    rangeToInput = uiState.pages.size.coerceAtLeast(1).toString()
                                    showRangeDialog = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("استخراج نطاق", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("page_manager_screen")
    ) { innerPadding ->

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("جاري تجهيز مصغرات الصفحات...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            uiState.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.errorMessage,
                        color = WaraqError,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 148.dp),
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        bottom = innerPadding.calculateBottomPadding() + 16.dp,
                        start = 12.dp,
                        end = 12.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("page_manager_grid")
                ) {
                    itemsIndexed(
                        items = uiState.pages,
                        key = { _, item -> item.id }
                    ) { index, item ->
                        val docPage = if (!uiState.isPdf) {
                            uiState.wordPages.getOrNull(item.originalPageNumber - 1)
                        } else {
                            null
                        }
                        ManagedPageGridCard(
                            item = item,
                            displayPosition = index + 1,
                            totalItems = uiState.pages.size,
                            isPdf = uiState.isPdf,
                            pdfEngine = getPdfEngine(),
                            wordPage = docPage,
                            onToggleSelect = { onToggleSelect(item.id) },
                            onRotate = { onRotatePage(item.id) },
                            onMoveEarlier = { onMoveEarlier(item.id) },
                            onMoveLater = { onMoveLater(item.id) }
                        )
                    }
                }
            }
        }
    }

    // Range Selection Dialog
    if (showRangeDialog) {
        AlertDialog(
            onDismissRequest = { showRangeDialog = false },
            title = { Text("تحديد نطاق صفحات", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "اختر نطاق الصفحات من 1 إلى ${uiState.pages.size} لتحديدها دفعة واحدة:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = rangeFromInput,
                            onValueChange = { rangeFromInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("من صفحة") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = rangeToInput,
                            onValueChange = { rangeToInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("إلى صفحة") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val from = rangeFromInput.toIntOrNull() ?: 1
                        val to = rangeToInput.toIntOrNull() ?: uiState.pages.size
                        onSelectRange(from, to)
                        showRangeDialog = false
                    }
                ) {
                    Text("تحديد النطاق", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRangeDialog = false }) {
                    Text("إلغاء")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("إزالة الصفحات المحددة", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "هل تريد إزالة ${uiState.selectedCount} صفحة محددة من ترتيب المستند؟\nيمكنك التراجع قبل الضغط على «حفظ في المستند».",
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteSelected()
                    }
                ) {
                    Text("إزالة", color = WaraqError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Extract Options Dialog
    if (showExtractDialog) {
        AlertDialog(
            onDismissRequest = { showExtractDialog = false },
            title = { Text("استخراج ${uiState.selectedCount} صفحة إلى ملف PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = extractTitleInput,
                        onValueChange = { extractTitleInput = it },
                        label = { Text("اسم الملف المستخرج") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "اختر طريقة حفظ أو إخراج الصفحات المحددة:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 1. Save directly in WARAQ offline library
                    Button(
                        onClick = {
                            val title = extractTitleInput.ifBlank { "صفحات مستخرجة" }
                            showExtractDialog = false
                            onExtractToLibrary(title, false) { newBookId ->
                                onClearSelection()
                                onOpenExtractedBook(newBookId)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("extract_save_to_library_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("حفظ في مكتبة وَرَق وفتحه", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // 2. Save directly to device storage (Downloads / Documents via SAF)
                    FilledTonalButton(
                        onClick = {
                            val title = extractTitleInput.ifBlank { "صفحات مستخرجة" }
                            showExtractDialog = false
                            onPrepareExportFile(title) { tempFile ->
                                pendingTempExportFile = tempFile
                                val safeFileName = "${title.replace("[/\\\\:*?\"<>|]".toRegex(), "_")}.pdf"
                                createDocumentLauncher.launch(safeFileName)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("extract_save_to_device_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("حفظ في ملفات الجهاز (تنزيلات)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    // 3. Share PDF externally
                    OutlinedButton(
                        onClick = {
                            val title = extractTitleInput.ifBlank { "صفحات مستخرجة" }
                            showExtractDialog = false
                            onPrepareExportFile(title) { tempFile ->
                                sharePdfFile(context, tempFile, title)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("extract_share_pdf_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("مشاركة كملف PDF", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExtractDialog = false }) {
                    Text("إغلاق")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Save As New Copy Dialog (for modified document)
    if (showSaveCopyDialog) {
        AlertDialog(
            onDismissRequest = { showSaveCopyDialog = false },
            title = { Text("حفظ كملف PDF جديد", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = saveCopyTitleInput,
                    onValueChange = { saveCopyTitleInput = it },
                    label = { Text("اسم النسخة الجديدة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val title = saveCopyTitleInput.ifBlank { "نسخة معدلة" }
                        showSaveCopyDialog = false
                        onExtractToLibrary(title, true) { newId ->
                            onOpenExtractedBook(newId)
                        }
                    }
                ) {
                    Text("حفظ وفتح", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveCopyDialog = false }) {
                    Text("إلغاء")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Processing Progress Overlay Dialog
    if (uiState.isProcessing) {
        AlertDialog(
            onDismissRequest = {},
            title = null,
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = uiState.processingLabel ?: "جاري المعالجة...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {},
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun ManagedPageGridCard(
    item: ManagedPageItem,
    displayPosition: Int,
    totalItems: Int,
    isPdf: Boolean,
    pdfEngine: PdfEngine?,
    wordPage: DocPage?,
    onToggleSelect: () -> Unit,
    onRotate: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit
) {
    var thumbnail by remember(item.originalPageNumber, pdfEngine) {
        mutableStateOf(pdfEngine?.getCachedThumbnail(item.originalPageNumber - 1))
    }

    LaunchedEffect(item.originalPageNumber, pdfEngine) {
        if (isPdf && pdfEngine != null && (thumbnail == null || thumbnail?.isRecycled == true)) {
            thumbnail = pdfEngine.renderThumbnail(item.originalPageNumber - 1, 280)
        }
    }

    val borderColor = if (item.isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (item.isSelected) 6.dp else 2.dp,
        border = androidx.compose.foundation.BorderStroke(if (item.isSelected) 2.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggleSelect() }
            .testTag("managed_page_card_${item.originalPageNumber}")
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Thumbnail Preview Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(0.8.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isPdf) {
                    val bmp = thumbnail
                    if (bmp != null && !bmp.isRecycled) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "صفحة ${item.originalPageNumber}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationZ = item.rotationDegrees.toFloat()
                                }
                        )
                    } else {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    // Miniature Word Page Preview
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .graphicsLayer {
                                rotationZ = item.rotationDegrees.toFloat()
                            }
                    ) {
                        val previewSnippet = remember(wordPage) {
                            wordPage?.elements?.take(3)?.joinToString("\n") { el ->
                                when (el) {
                                    is DocElement.Heading -> el.text
                                    is DocElement.Paragraph -> el.text
                                    is DocElement.ListItem -> "• ${el.text}"
                                    is DocElement.Notice -> el.message
                                    is DocElement.Table -> "جدول بيانات"
                                    is DocElement.ImageItem -> el.caption.ifBlank { "صورة" }
                                }
                            } ?: "صفحة ${item.originalPageNumber}"
                        }
                        Text(
                            text = previewSnippet,
                            color = Color(0xFF1F2421),
                            fontSize = 9.sp,
                            lineHeight = 13.sp,
                            maxLines = 9,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Selection Check Circle (Top-Start)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (item.isSelected) MaterialTheme.colorScheme.primary else Color(0x99000000)
                        )
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "محددة",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Rotation badge if rotated
                if (item.rotationDegrees != 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${item.rotationDegrees}°",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Page Number Pill (Bottom-Center)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC1E2421))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    val label = if (displayPosition != item.originalPageNumber) {
                        "$displayPosition (أصل ${item.originalPageNumber})"
                    } else {
                        "صفحة $displayPosition"
                    }
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Page Action Strip (Move Earlier / Rotate / Move Later)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onMoveEarlier,
                    enabled = displayPosition > 1,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "تقديم الصفحة",
                        tint = if (displayPosition > 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onRotate,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RotateRight,
                        contentDescription = "تدوير 90 درجة",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onMoveLater,
                    enabled = displayPosition < totalItems,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "تأخير الصفحة",
                        tint = if (displayPosition < totalItems) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun sharePdfFile(context: Context, pdfFile: File, title: String) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "مشاركة الملف المستخرج"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

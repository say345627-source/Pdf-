package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.engine.PdfEngine
import com.example.ui.theme.WaraqBorder
import com.example.ui.theme.WaraqDarkBg
import com.example.ui.theme.WaraqError
import com.example.ui.theme.WaraqGreenContainer
import com.example.ui.theme.WaraqNeonGreen
import com.example.ui.theme.WaraqSurfaceDark
import com.example.ui.theme.WaraqSurfaceElevated
import com.example.ui.theme.WaraqTextPrimary
import com.example.ui.theme.WaraqTextSecondary
import com.example.viewmodel.ReaderUiState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    uiState: ReaderUiState,
    onBack: () -> Unit,
    onPageScrolled: (Int) -> Unit,
    onGoToPage: (Int) -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleFitMode: () -> Unit,
    onToggleTextSelectionMode: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSaveImmediately: () -> Unit,
    getPdfEngine: () -> PdfEngine?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var showControls by remember { mutableStateOf(true) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpPageInput by remember { mutableStateOf("") }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isNightSheetMode by remember { mutableStateOf(false) }

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Intercept back button to ensure state is saved and cleanly pops
    BackHandler {
        onSaveImmediately()
        onBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            onSaveImmediately()
        }
    }

    // Scroll to initial saved page on first load
    LaunchedEffect(uiState.pdfTotalPages) {
        if (uiState.pdfTotalPages > 0) {
            val initialIndex = (uiState.pdfCurrentPage - 1).coerceIn(0, uiState.pdfTotalPages - 1)
            if (initialIndex > 0) {
                listState.scrollToItem(initialIndex)
            }
        }
    }

    // Monitor continuous scroll position and report visible page
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { visibleIndex ->
                onPageScrolled(visibleIndex + 1)
            }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isNightSheetMode) Color(0xFF141816) else WaraqDarkBg)
            .testTag("pdf_reader_screen")
    ) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = WaraqNeonGreen, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "جاري فتح وتجهيز صفحات المستند...",
                        color = WaraqTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else if (uiState.errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = uiState.errorMessage,
                        color = WaraqError,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = WaraqNeonGreen, contentColor = WaraqDarkBg)
                    ) {
                        Text("العودة إلى المكتبة")
                    }
                }
            }
        } else {
            // Continuous Vertical Pages Flow
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            if (scale == 1f) {
                                offset = Offset.Zero
                            } else {
                                val maxOffsetX = (size.width * (scale - 1)) / 2f
                                val maxOffsetY = (size.height * (scale - 1)) / 2f
                                offset = Offset(
                                    x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                    y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                )
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                scale = if (scale > 1.2f) 1f else 2f
                                offset = Offset.Zero
                            },
                            onTap = {
                                showControls = !showControls
                            }
                        )
                    }
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        top = 64.dp,
                        bottom = 80.dp,
                        start = 12.dp,
                        end = 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp), // Distinct gap between pages
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .testTag("pdf_pages_column")
                ) {
                    items(
                        count = uiState.pdfTotalPages,
                        key = { it }
                    ) { pageIndex ->
                        val engine = getPdfEngine()
                        PdfPageCardItem(
                            pageIndex = pageIndex,
                            totalPages = uiState.pdfTotalPages,
                            engine = engine,
                            fitMode = uiState.pdfFitMode,
                            isNightMode = isNightSheetMode
                        )
                    }
                }
            }

            // Floating Page Badge Pill (Matching the exact "550/11" badge in screenshot)
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 70.dp, end = 16.dp)
            ) {
                Surface(
                    onClick = {
                        jumpPageInput = uiState.pdfCurrentPage.toString()
                        showJumpDialog = true
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xDD252C28),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF)),
                    modifier = Modifier.testTag("floating_page_badge")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${uiState.pdfTotalPages}/${uiState.pdfCurrentPage}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Top Controls Overlay
            AnimatedVisibility(
                visible = showControls,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = WaraqDarkBg.copy(alpha = 0.95f),
                    tonalElevation = 6.dp
                ) {
                    Column {
                        TopAppBar(
                            title = {
                                Text(
                                    text = uiState.book?.title ?: "قارئ المستندات",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = WaraqTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        onSaveImmediately()
                                        onBack()
                                    },
                                    modifier = Modifier.testTag("reader_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "رجوع",
                                        tint = WaraqTextPrimary
                                    )
                                }
                            },
                            actions = {
                                // Search
                                IconButton(onClick = onToggleSearch) {
                                    Icon(Icons.Default.Search, contentDescription = "بحث", tint = WaraqTextPrimary)
                                }

                                // Bookmark
                                IconButton(onClick = onToggleBookmark) {
                                    Icon(
                                        imageVector = if (uiState.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = "إشارة مرجعية",
                                        tint = if (uiState.isBookmarked) WaraqNeonGreen else WaraqTextPrimary
                                    )
                                }

                                // Share
                                IconButton(
                                    onClick = {
                                        shareDocumentText(context, uiState.book?.title ?: "", uiState.extractedPageText)
                                    }
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = WaraqTextPrimary)
                                }

                                // More menu
                                Box {
                                    IconButton(onClick = { showMoreMenu = true }) {
                                        Icon(Icons.Default.MoreHoriz, contentDescription = "المزيد", tint = WaraqTextPrimary)
                                    }
                                    DropdownMenu(
                                        expanded = showMoreMenu,
                                        onDismissRequest = { showMoreMenu = false },
                                        modifier = Modifier.background(WaraqSurfaceElevated)
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (uiState.pdfFitMode == "WIDTH") "ملاءمة كامل الصفحة" else "ملاءمة العرض",
                                                    color = WaraqTextPrimary
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.FitScreen, contentDescription = null, tint = WaraqNeonGreen)
                                            },
                                            onClick = {
                                                showMoreMenu = false
                                                onToggleFitMode()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (isNightSheetMode) "الوضع الفاتح للصفحة" else "الوضع الهادئ/الداكن للصفحة",
                                                    color = WaraqTextPrimary
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.FormatAlignJustify, contentDescription = null, tint = WaraqNeonGreen)
                                            },
                                            onClick = {
                                                showMoreMenu = false
                                                isNightSheetMode = !isNightSheetMode
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("الانتقال إلى صفحة...", color = WaraqTextPrimary) },
                                            leadingIcon = {
                                                Icon(Icons.Default.SwapVert, contentDescription = null, tint = WaraqNeonGreen)
                                            },
                                            onClick = {
                                                showMoreMenu = false
                                                jumpPageInput = uiState.pdfCurrentPage.toString()
                                                showJumpDialog = true
                                            }
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = WaraqDarkBg.copy(alpha = 0.95f))
                        )

                        // In-document Search Row
                        AnimatedVisibility(visible = uiState.isPdfSearchActive) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                OutlinedTextField(
                                    value = uiState.pdfSearchQuery,
                                    onValueChange = onSearchQueryChanged,
                                    placeholder = { Text("ابحث في نصوص المستند...", color = WaraqTextSecondary) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = WaraqNeonGreen)
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = onToggleSearch) {
                                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = WaraqTextSecondary)
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = WaraqNeonGreen,
                                        unfocusedBorderColor = WaraqBorder,
                                        focusedTextColor = WaraqTextPrimary,
                                        unfocusedTextColor = WaraqTextPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (uiState.pdfSearchResults.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "عُثر على ${uiState.pdfSearchResults.size} نتائج (اضغط للانتقال):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = WaraqNeonGreen
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        uiState.pdfSearchResults.take(5).forEach { res ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(WaraqSurfaceElevated)
                                                    .border(1.dp, WaraqBorder, RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        scope.launch {
                                                            listState.scrollToItem((res.pageNumber - 1).coerceAtLeast(0))
                                                        }
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "ص ${res.pageNumber}",
                                                    color = WaraqTextPrimary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Tools Bar (Inspired by the 5 tools in user's screenshot)
            AnimatedVisibility(
                visible = showControls,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = WaraqDarkBg.copy(alpha = 0.95f),
                    tonalElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, WaraqBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. More Tools / View Mode
                        BottomToolItem(
                            icon = Icons.Default.FitScreen,
                            label = "المزيد",
                            onClick = { onToggleFitMode() }
                        )

                        // 2. Jump to page (Fill / Go)
                        BottomToolItem(
                            icon = Icons.Default.SwapVert,
                            label = "انتقال سريع",
                            onClick = {
                                jumpPageInput = uiState.pdfCurrentPage.toString()
                                showJumpDialog = true
                            }
                        )

                        // 3. Bookmark (Sign / Mark)
                        BottomToolItem(
                            icon = if (uiState.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            label = "إشارة",
                            tint = if (uiState.isBookmarked) WaraqNeonGreen else WaraqTextSecondary,
                            onClick = onToggleBookmark
                        )

                        // 4. Text Selection / Copy (Add Note / Select)
                        BottomToolItem(
                            icon = Icons.Default.SelectAll,
                            label = "تحديد ونسخ",
                            tint = if (uiState.isTextSelectionMode) WaraqNeonGreen else WaraqTextSecondary,
                            onClick = onToggleTextSelectionMode
                        )

                        // 5. Page Theme (Edit / Night)
                        BottomToolItem(
                            icon = Icons.Default.FormatAlignJustify,
                            label = if (isNightSheetMode) "وضع ناصع" else "وضع هادئ",
                            onClick = { isNightSheetMode = !isNightSheetMode }
                        )
                    }
                }
            }
        }

        // Jump to Page Dialog
        if (showJumpDialog) {
            AlertDialog(
                onDismissRequest = { showJumpDialog = false },
                title = { Text("الانتقال إلى صفحة", color = WaraqTextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = jumpPageInput,
                            onValueChange = { jumpPageInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("رقم الصفحة (1 - ${uiState.pdfTotalPages})", color = WaraqTextSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WaraqNeonGreen,
                                unfocusedBorderColor = WaraqBorder,
                                focusedTextColor = WaraqTextPrimary,
                                unfocusedTextColor = WaraqTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Slider(
                            value = (jumpPageInput.toIntOrNull() ?: uiState.pdfCurrentPage).toFloat(),
                            onValueChange = { jumpPageInput = it.toInt().toString() },
                            valueRange = 1f..uiState.pdfTotalPages.coerceAtLeast(1).toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = WaraqNeonGreen,
                                activeTrackColor = WaraqNeonGreen,
                                inactiveTrackColor = WaraqSurfaceElevated
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val target = jumpPageInput.toIntOrNull()
                            if (target != null && target in 1..uiState.pdfTotalPages) {
                                scope.launch {
                                    listState.scrollToItem(target - 1)
                                }
                            }
                            showJumpDialog = false
                        }
                    ) {
                        Text("انتقال", color = WaraqNeonGreen, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showJumpDialog = false }) {
                        Text("إلغاء", color = WaraqTextSecondary)
                    }
                },
                containerColor = WaraqSurfaceElevated
            )
        }

        // Text Selection & Copy Modal Sheet
        if (uiState.isTextSelectionMode) {
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = onToggleTextSelectionMode,
                sheetState = sheetState,
                containerColor = WaraqSurfaceElevated,
                dragHandle = null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "نص الصفحة ${uiState.pdfCurrentPage} (تحديد ونسخ)",
                            style = MaterialTheme.typography.titleMedium,
                            color = WaraqTextPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(onClick = onToggleTextSelectionMode) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = WaraqTextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (uiState.extractedPageText.isNotBlank()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("PDF Page Text", uiState.extractedPageText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "تم نسخ كامل نص الصفحة إلى الحافظة", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WaraqNeonGreen, contentColor = WaraqDarkBg),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("نسخ كامل الصفحة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, uiState.extractedPageText)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "مشاركة النص"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WaraqSurfaceDark, contentColor = WaraqTextPrimary),
                                border = androidx.compose.foundation.BorderStroke(1.dp, WaraqBorder),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("مشاركة النص", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "اضغط مطولاً على أي جملة أو فقرة لتحديدها ونسخها:",
                            style = MaterialTheme.typography.bodySmall,
                            color = WaraqNeonGreen
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(WaraqDarkBg)
                                .border(1.dp, WaraqBorder, RoundedCornerShape(12.dp))
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            SelectionContainer {
                                Text(
                                    text = uiState.extractedPageText,
                                    color = WaraqTextPrimary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    lineHeight = 28.sp
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(WaraqDarkBg)
                                .border(1.dp, WaraqBorder, RoundedCornerShape(12.dp))
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ملاحظة: هذا المستند يتكون من صفحات ممسوحة ضوئياً (صور) ولا يحتوي على طبقة نصية قابلة للبحث أو النسخ بدون تقنية OCR.",
                                color = WaraqTextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                lineHeight = 24.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Individual PDF Page Paper Sheet in the continuous vertical scroll list
 */
@Composable
fun PdfPageCardItem(
    pageIndex: Int,
    totalPages: Int,
    engine: PdfEngine?,
    fitMode: String,
    isNightMode: Boolean,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(pageIndex) { mutableStateOf(true) }

    LaunchedEffect(pageIndex, engine) {
        if (engine != null) {
            isLoading = true
            bitmap = engine.renderPage(pageIndex, 1080)
            isLoading = false
        }
    }

    val pageRatio = remember(pageIndex, engine) {
        engine?.getPageAspectRatio(pageIndex) ?: 1.414f
    }

    // Authentic Document Paper Page Card with white background, margin, and shadow
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .border(
                1.dp,
                if (isNightMode) Color(0xFF2C3830) else Color(0xFFE2E6E3),
                RoundedCornerShape(6.dp)
            ),
        color = if (isNightMode) Color(0xFF1E2521) else Color.White
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / pageRatio),
            contentAlignment = Alignment.Center
        ) {
            val pageBmp = bitmap
            if (pageBmp != null && !pageBmp.isRecycled) {
                Image(
                    bitmap = pageBmp.asImageBitmap(),
                    contentDescription = "صفحة ${pageIndex + 1} من $totalPages",
                    contentScale = if (fitMode == "WIDTH") ContentScale.FillWidth else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Skeleton loading card with watermark page number
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = WaraqNeonGreen,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "صفحة ${pageIndex + 1}",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun BottomToolItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = WaraqTextSecondary
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun shareDocumentText(context: Context, title: String, text: String) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(shareIntent, "مشاركة المستند"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

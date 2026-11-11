package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DocElement
import com.example.data.model.DocPage
import com.example.ui.theme.WaraqBorder
import com.example.ui.theme.WaraqDarkBg
import com.example.ui.theme.WaraqDocxColor
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
fun WordReaderScreen(
    uiState: ReaderUiState,
    onBack: () -> Unit,
    onScrolledTo: (Int) -> Unit,
    onChangeFontSize: (Float) -> Unit,
    onToggleBookmark: () -> Unit,
    onSaveImmediately: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var showControls by remember { mutableStateOf(true) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpPageInput by remember { mutableStateOf("") }
    var isSepiaPageMode by remember { mutableStateOf(false) }
    var isDarkPageMode by remember { mutableStateOf(false) }

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Intercept back button to guarantee position is saved before leaving
    BackHandler {
        onSaveImmediately()
        onBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            onSaveImmediately()
        }
    }

    // Restore saved page position on initial load
    LaunchedEffect(uiState.wordPages.size) {
        if (uiState.wordPages.isNotEmpty()) {
            val initialPageIndex = (uiState.wordCurrentPage - 1).coerceIn(0, uiState.wordPages.size - 1)
            if (initialPageIndex > 0) {
                listState.scrollToItem(initialPageIndex)
            }
        }
    }

    // Monitor continuous page scroll and report page number
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                onScrolledTo(index + 1)
            }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDarkPageMode) Color(0xFF141816) else WaraqDarkBg)
            .testTag("word_reader_screen")
    ) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = WaraqNeonGreen, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("جاري تقسيم وتنسيق صفحات المستند...", color = WaraqTextSecondary)
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
            // Multi-Page Continuous Flow Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 3.5f)
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
                                scale = if (scale > 1.2f) 1f else 1.8f
                                offset = Offset.Zero
                            },
                            onTap = {
                                showControls = !showControls
                            }
                        )
                    }
            ) {
                // SelectionContainer enables long-press selection on the page!
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            top = 64.dp,
                            bottom = 80.dp,
                            start = 12.dp,
                            end = 12.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp), // Distinct page gap like screenshot
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                            .testTag("word_pages_column")
                    ) {
                        itemsIndexed(
                            items = uiState.wordPages,
                            key = { _, page -> page.pageNumber }
                        ) { _, page ->
                            WordPageCard(
                                page = page,
                                totalPages = uiState.wordTotalPages,
                                baseFontSize = uiState.wordFontSizeSp,
                                searchQuery = searchQuery,
                                isSepia = isSepiaPageMode,
                                isDark = isDarkPageMode
                            )
                        }
                    }
                }
            }

            // Floating Page Badge Pill (Matching "550/11" in screenshot)
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
                        jumpPageInput = uiState.wordCurrentPage.toString()
                        showJumpDialog = true
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xDD252C28),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x44FFFFFF)),
                    modifier = Modifier.testTag("floating_word_page_badge")
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
                            text = "${uiState.wordTotalPages}/${uiState.wordCurrentPage}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Top Bar
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
                                    text = uiState.book?.title ?: "مستند Word",
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
                                    modifier = Modifier.testTag("word_reader_back_button")
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
                                IconButton(onClick = { isSearchActive = !isSearchActive }) {
                                    Icon(Icons.Default.Search, contentDescription = "بحث", tint = WaraqTextPrimary)
                                }

                                // Bookmark
                                IconButton(onClick = onToggleBookmark) {
                                    Icon(
                                        imageVector = if (uiState.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = "إشارة",
                                        tint = if (uiState.isBookmarked) WaraqNeonGreen else WaraqTextPrimary
                                    )
                                }

                                // Share
                                IconButton(
                                    onClick = {
                                        shareDocumentText(context, uiState.book?.title ?: "", "")
                                    }
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = WaraqTextPrimary)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = WaraqDarkBg.copy(alpha = 0.95f))
                        )

                        // Search Bar
                        AnimatedVisibility(visible = isSearchActive) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(WaraqDarkBg)
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("ابحث في صفحات المستند...", color = WaraqTextSecondary) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = WaraqNeonGreen)
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { isSearchActive = false }) {
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
                            }
                        }
                    }
                }
            }

            // Bottom Tools Bar (Matching screenshot tools)
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
                        // 1. Font Size Control
                        BottomToolItem(
                            icon = Icons.Default.FormatSize,
                            label = "تكبير A+",
                            onClick = { onChangeFontSize(1.5f) }
                        )

                        BottomToolItem(
                            icon = Icons.Default.FormatSize,
                            label = "تصغير A-",
                            onClick = { onChangeFontSize(-1.5f) }
                        )

                        // 2. Jump to page
                        BottomToolItem(
                            icon = Icons.Default.SwapVert,
                            label = "انتقال سريع",
                            onClick = {
                                jumpPageInput = uiState.wordCurrentPage.toString()
                                showJumpDialog = true
                            }
                        )

                        // 3. Bookmark
                        BottomToolItem(
                            icon = if (uiState.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            label = "إشارة",
                            tint = if (uiState.isBookmarked) WaraqNeonGreen else WaraqTextSecondary,
                            onClick = onToggleBookmark
                        )

                        // 4. Page Theme (Sepia / White / Dark)
                        BottomToolItem(
                            icon = Icons.Default.FormatAlignJustify,
                            label = when {
                                isSepiaPageMode -> "وضع داكن"
                                isDarkPageMode -> "ورق أبيض"
                                else -> "ورق دافئ"
                            },
                            onClick = {
                                if (!isSepiaPageMode && !isDarkPageMode) {
                                    isSepiaPageMode = true
                                } else if (isSepiaPageMode) {
                                    isSepiaPageMode = false
                                    isDarkPageMode = true
                                } else {
                                    isDarkPageMode = false
                                }
                            }
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
                            label = { Text("رقم الصفحة (1 - ${uiState.wordTotalPages})", color = WaraqTextSecondary) },
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
                            value = (jumpPageInput.toIntOrNull() ?: uiState.wordCurrentPage).toFloat(),
                            onValueChange = { jumpPageInput = it.toInt().toString() },
                            valueRange = 1f..uiState.wordTotalPages.coerceAtLeast(1).toFloat(),
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
                            if (target != null && target in 1..uiState.wordTotalPages) {
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
    }
}

/**
 * Authentic Document Paper Page Card (like printed document page in screenshot)
 */
@Composable
fun WordPageCard(
    page: DocPage,
    totalPages: Int,
    baseFontSize: Float,
    searchQuery: String,
    isSepia: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val pageBgColor = when {
        isDark -> Color(0xFF1E2622)
        isSepia -> Color(0xFFFBF0D9)
        else -> Color.White
    }

    val pageTextColor = when {
        isDark -> Color(0xFFECEFEA)
        isSepia -> Color(0xFF382D20)
        else -> Color(0xFF1A1F1C)
    }

    val pageSubTextColor = when {
        isDark -> Color(0xFF909E96)
        isSepia -> Color(0xFF7A6B56)
        else -> Color(0xFF6B7570)
    }

    val borderColor = when {
        isDark -> Color(0xFF2C3830)
        isSepia -> Color(0xFFE5D5B8)
        else -> Color(0xFFE2E6E3)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp)),
        color = pageBgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp)
        ) {
            // Running Header (like "التفكير السريع والبطيء" in screenshot)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = page.runningChapter.ifBlank { page.headerTitle },
                    color = pageSubTextColor,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${page.pageNumber}",
                    color = pageSubTextColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = borderColor.copy(alpha = 0.8f), thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // Page Content Elements
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                page.elements.forEach { element ->
                    when (element) {
                        is DocElement.Heading -> {
                            WordPageHeadingItem(
                                element = element,
                                baseFontSize = baseFontSize,
                                textColor = pageTextColor,
                                isDark = isDark
                            )
                        }
                        is DocElement.Paragraph -> {
                            WordPageParagraphItem(
                                element = element,
                                baseFontSize = baseFontSize,
                                textColor = pageTextColor,
                                highlightQuery = searchQuery
                            )
                        }
                        is DocElement.ListItem -> {
                            WordPageListItem(
                                element = element,
                                baseFontSize = baseFontSize,
                                textColor = pageTextColor
                            )
                        }
                        is DocElement.Table -> {
                            WordPageTableItem(
                                element = element,
                                baseFontSize = baseFontSize,
                                textColor = pageTextColor,
                                borderColor = borderColor
                            )
                        }
                        is DocElement.ImageItem -> {
                            WordPageImageItem(element = element)
                        }
                        is DocElement.Notice -> {
                            WordPageNoticeItem(element = element)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = borderColor.copy(alpha = 0.5f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Running Footer with page number
            Text(
                text = "— ${page.pageNumber} —",
                color = pageSubTextColor,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun WordPageHeadingItem(
    element: DocElement.Heading,
    baseFontSize: Float,
    textColor: Color,
    isDark: Boolean
) {
    val sizeSp = when (element.level) {
        1 -> (baseFontSize + 7f)
        2 -> (baseFontSize + 4f)
        else -> (baseFontSize + 2f)
    }

    Text(
        text = element.text,
        color = if (isDark && element.level == 1) WaraqNeonGreen else textColor,
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp * 1.35f).sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun WordPageParagraphItem(
    element: DocElement.Paragraph,
    baseFontSize: Float,
    textColor: Color,
    highlightQuery: String
) {
    val annotated = buildAnnotatedString {
        if (element.runs.isNotEmpty()) {
            for (run in element.runs) {
                val spanColor = if (highlightQuery.isNotBlank() && run.text.contains(highlightQuery, ignoreCase = true)) {
                    Color(0xFFE65100)
                } else {
                    textColor
                }

                withStyle(
                    SpanStyle(
                        fontSize = baseFontSize.sp,
                        color = spanColor,
                        fontWeight = if (run.isBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (run.isItalic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = if (run.isUnderline) TextDecoration.Underline else TextDecoration.None
                    )
                ) {
                    append(run.text)
                }
            }
        } else {
            withStyle(SpanStyle(fontSize = baseFontSize.sp, color = textColor)) {
                append(element.text)
            }
        }
    }

    Text(
        text = annotated,
        lineHeight = (baseFontSize * 1.6f).sp,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun WordPageListItem(
    element: DocElement.ListItem,
    baseFontSize: Float,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp)
    ) {
        Text(
            text = "•",
            color = Color(0xFF00874A),
            fontSize = (baseFontSize + 2f).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = element.text,
            color = textColor,
            fontSize = baseFontSize.sp,
            lineHeight = (baseFontSize * 1.5f).sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun WordPageTableItem(
    element: DocElement.Table,
    baseFontSize: Float,
    textColor: Color,
    borderColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
        color = Color(0x0A000000)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(6.dp)
        ) {
            element.rows.forEachIndexed { rowIndex, row ->
                val isHeader = rowIndex == 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isHeader) Color(0x1800874A) else Color.Transparent)
                        .padding(vertical = 6.dp)
                ) {
                    row.forEach { cell ->
                        Text(
                            text = cell,
                            color = textColor,
                            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                            fontSize = (baseFontSize - 2f).sp,
                            modifier = Modifier
                                .width(125.dp)
                                .padding(horizontal = 6.dp)
                        )
                    }
                }
                if (rowIndex < element.rows.size - 1) {
                    HorizontalDivider(color = borderColor, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
fun WordPageImageItem(element: DocElement.ImageItem) {
    val bitmap = element.bitmap
    if (bitmap != null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = element.caption,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(8.dp))
            )
            if (element.caption.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = element.caption,
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun WordPageNoticeItem(element: DocElement.Notice) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color(0x442979FF), RoundedCornerShape(8.dp)),
        color = Color(0x0F2979FF)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = WaraqDocxColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = element.message,
                color = Color(0xFF4A5568),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun shareDocumentText(context: Context, title: String, text: String) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text.ifBlank { title })
        }
        context.startActivity(Intent.createChooser(shareIntent, "مشاركة المستند"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

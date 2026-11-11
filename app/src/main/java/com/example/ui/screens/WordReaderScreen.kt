package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DocElement
import com.example.data.model.TextRun
import com.example.ui.theme.WaraqBorder
import com.example.ui.theme.WaraqDarkBg
import com.example.ui.theme.WaraqDocxColor
import com.example.ui.theme.WaraqError
import com.example.ui.theme.WaraqGreenContainer
import com.example.ui.theme.WaraqNeonGreen
import com.example.ui.theme.WaraqSurfaceElevated
import com.example.ui.theme.WaraqTextPrimary
import com.example.ui.theme.WaraqTextSecondary
import com.example.viewmodel.ReaderUiState
import kotlinx.coroutines.flow.distinctUntilChanged

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
    val listState = rememberLazyListState()

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

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

    // Restore saved scroll position upon initial document load
    LaunchedEffect(uiState.wordElements.size) {
        if (uiState.wordElements.isNotEmpty()) {
            val targetIndex = uiState.wordCurrentParagraphIndex.coerceIn(0, uiState.wordElements.size - 1)
            if (targetIndex > 0) {
                listState.scrollToItem(targetIndex)
            }
        }
    }

    // Monitor scrolling and report paragraph index for persistent progress saving
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                onScrolledTo(index)
            }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = uiState.book?.title ?: "قارئ Word",
                                style = MaterialTheme.typography.titleMedium,
                                color = WaraqTextPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val total = uiState.wordElements.size.coerceAtLeast(1)
                            val current = (uiState.wordCurrentParagraphIndex + 1).coerceIn(1, total)
                            Text(
                                text = "موضع $current من $total (${(current.toFloat() / total * 100).toInt()}%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = WaraqNeonGreen
                            )
                        }
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
                        // Font Size Decrease
                        IconButton(
                            onClick = { onChangeFontSize(-2f) },
                            modifier = Modifier.testTag("word_decrease_font_button")
                        ) {
                            Text("A-", color = WaraqTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        // Font Size Increase
                        IconButton(
                            onClick = { onChangeFontSize(2f) },
                            modifier = Modifier.testTag("word_increase_font_button")
                        ) {
                            Text("A+", color = WaraqNeonGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        // Search
                        IconButton(
                            onClick = { isSearchActive = !isSearchActive },
                            modifier = Modifier.testTag("word_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "بحث",
                                tint = if (isSearchActive) WaraqNeonGreen else WaraqTextPrimary
                            )
                        }

                        // Bookmark
                        IconButton(
                            onClick = onToggleBookmark,
                            modifier = Modifier.testTag("word_bookmark_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "إشارة موضع",
                                tint = if (uiState.isBookmarked) WaraqNeonGreen else WaraqTextSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = WaraqDarkBg)
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
                            placeholder = { Text("ابحث في نصوص المستند...", color = WaraqTextSecondary) },
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

                // Progress Bar line
                val total = uiState.wordElements.size.coerceAtLeast(1)
                val current = (uiState.wordCurrentParagraphIndex + 1).coerceIn(1, total)
                LinearProgressIndicator(
                    progress = { (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = WaraqNeonGreen,
                    trackColor = WaraqDarkBg
                )
            }
        },
        containerColor = WaraqDarkBg,
        modifier = modifier.testTag("word_reader_screen")
    ) { innerPadding ->

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = WaraqNeonGreen, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("جاري فتح وتحليل المستند...", color = WaraqTextSecondary)
                }
            }
        } else if (uiState.errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            // SelectionContainer enables long-press text selection, drag handles, and copy/share across all text!
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = innerPadding.calculateTopPadding() + 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + 40.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(uiState.wordElements) { index, element ->
                        when (element) {
                            is DocElement.Heading -> {
                                HeadingItem(element = element, baseFontSize = uiState.wordFontSizeSp)
                            }
                            is DocElement.Paragraph -> {
                                ParagraphItem(element = element, baseFontSize = uiState.wordFontSizeSp, highlightQuery = searchQuery)
                            }
                            is DocElement.ListItem -> {
                                ListItemComponent(element = element, baseFontSize = uiState.wordFontSizeSp)
                            }
                            is DocElement.Table -> {
                                TableComponent(element = element, baseFontSize = uiState.wordFontSizeSp)
                            }
                            is DocElement.ImageItem -> {
                                ImageComponent(element = element)
                            }
                            is DocElement.Notice -> {
                                NoticeComponent(element = element)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeadingItem(element: DocElement.Heading, baseFontSize: Float) {
    val (sizeSp, color) = when (element.level) {
        1 -> (baseFontSize + 8f) to WaraqNeonGreen
        2 -> (baseFontSize + 4f) to WaraqTextPrimary
        else -> (baseFontSize + 2f) to WaraqTextPrimary
    }

    Text(
        text = element.text,
        color = color,
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp * 1.4f).sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp)
    )
}

@Composable
fun ParagraphItem(element: DocElement.Paragraph, baseFontSize: Float, highlightQuery: String = "") {
    val annotated = buildAnnotatedString {
        if (element.runs.isNotEmpty()) {
            for (run in element.runs) {
                var spanStyle = SpanStyle(
                    fontSize = baseFontSize.sp,
                    color = run.hexColor?.let { parseHexColor(it) } ?: WaraqTextPrimary,
                    fontWeight = if (run.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (run.isItalic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (run.isUnderline) TextDecoration.Underline else TextDecoration.None
                )
                withStyle(spanStyle) {
                    append(run.text)
                }
            }
        } else {
            withStyle(SpanStyle(fontSize = baseFontSize.sp, color = WaraqTextPrimary)) {
                append(element.text)
            }
        }
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge,
        lineHeight = (baseFontSize * 1.6f).sp,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun ListItemComponent(element: DocElement.ListItem, baseFontSize: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp)
    ) {
        Text(
            text = "•",
            color = WaraqNeonGreen,
            fontSize = (baseFontSize + 2f).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = element.text,
            color = WaraqTextPrimary,
            fontSize = baseFontSize.sp,
            lineHeight = (baseFontSize * 1.5f).sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun TableComponent(element: DocElement.Table, baseFontSize: Float) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, WaraqBorder, RoundedCornerShape(10.dp)),
        color = WaraqSurfaceElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            element.rows.forEachIndexed { rowIndex, row ->
                val isHeader = rowIndex == 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isHeader) WaraqGreenContainer else Color.Transparent)
                        .padding(vertical = 6.dp)
                ) {
                    row.forEach { cell ->
                        Text(
                            text = cell,
                            color = if (isHeader) WaraqNeonGreen else WaraqTextPrimary,
                            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                            fontSize = (baseFontSize - 2f).sp,
                            modifier = Modifier
                                .width(130.dp)
                                .padding(horizontal = 8.dp)
                        )
                    }
                }
                if (rowIndex < element.rows.size - 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(WaraqBorder)
                    )
                }
            }
        }
    }
}

@Composable
fun ImageComponent(element: DocElement.ImageItem) {
    val bitmap = element.bitmap
    if (bitmap != null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = element.caption,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, WaraqBorder, RoundedCornerShape(12.dp))
            )
            if (element.caption.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = element.caption,
                    color = WaraqTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun NoticeComponent(element: DocElement.Notice) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, WaraqDocxColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        color = WaraqSurfaceElevated
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = WaraqDocxColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = element.message,
                color = WaraqTextSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun parseHexColor(hex: String): Color? {
    return try {
        val clean = if (hex.startsWith("#")) hex.substring(1) else hex
        if (clean.length == 6) {
            Color(android.graphics.Color.parseColor("#$clean"))
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

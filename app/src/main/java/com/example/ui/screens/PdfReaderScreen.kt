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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.example.ui.theme.WaraqBorder
import com.example.ui.theme.WaraqDarkBg
import com.example.ui.theme.WaraqError
import com.example.ui.theme.WaraqNeonGreen
import com.example.ui.theme.WaraqSurfaceDark
import com.example.ui.theme.WaraqSurfaceElevated
import com.example.ui.theme.WaraqTextPrimary
import com.example.ui.theme.WaraqTextSecondary
import com.example.viewmodel.ReaderUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    uiState: ReaderUiState,
    onBack: () -> Unit,
    onGoToPage: (Int) -> Unit,
    onNextPage: () -> Unit,
    onPreviousPage: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleFitMode: () -> Unit,
    onToggleTextSelectionMode: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSaveImmediately: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showControls by remember { mutableStateOf(true) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpPageInput by remember { mutableStateOf("") }

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WaraqDarkBg)
            .testTag("pdf_reader_screen")
    ) {
        // Document Rendering Canvas / Area
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
                },
            contentAlignment = Alignment.Center
        ) {
            val bitmap = uiState.currentBitmap
            if (bitmap != null && !bitmap.isRecycled) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "صفحة ${uiState.pdfCurrentPage}",
                    contentScale = if (uiState.pdfFitMode == "WIDTH") ContentScale.FillWidth else ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .testTag("pdf_page_image")
                )
            } else if (uiState.isLoading || uiState.isRenderingPage) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = WaraqNeonGreen,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "جاري تحميل الصفحة...",
                        color = WaraqTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (uiState.errorMessage != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
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
                color = WaraqSurfaceDark.copy(alpha = 0.95f),
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
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

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.book?.title ?: "قارئ المستندات",
                                style = MaterialTheme.typography.titleMedium,
                                color = WaraqTextPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "صفحة ${uiState.pdfCurrentPage} من ${uiState.pdfTotalPages}",
                                style = MaterialTheme.typography.bodySmall,
                                color = WaraqNeonGreen
                            )
                        }

                        // Text Selection Toggle
                        IconButton(
                            onClick = onToggleTextSelectionMode,
                            modifier = Modifier.testTag("reader_select_text_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = "تحديد ونسخ النص",
                                tint = if (uiState.isTextSelectionMode) WaraqNeonGreen else WaraqTextPrimary
                            )
                        }

                        // Search
                        IconButton(
                            onClick = onToggleSearch,
                            modifier = Modifier.testTag("reader_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "بحث داخل المستند",
                                tint = if (uiState.isPdfSearchActive) WaraqNeonGreen else WaraqTextPrimary
                            )
                        }

                        // Fit Mode Toggle
                        IconButton(
                            onClick = onToggleFitMode,
                            modifier = Modifier.testTag("reader_fit_mode_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.pdfFitMode == "WIDTH") Icons.Default.FitScreen else Icons.Default.FormatAlignJustify,
                                contentDescription = "تغيير نمط العرض",
                                tint = WaraqTextPrimary
                            )
                        }

                        // Bookmark Toggle
                        IconButton(
                            onClick = onToggleBookmark,
                            modifier = Modifier.testTag("reader_bookmark_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "إشارة مرجعية",
                                tint = if (uiState.isBookmarked) WaraqNeonGreen else WaraqTextSecondary
                            )
                        }
                    }

                    // In-document Search Row
                    AnimatedVisibility(visible = uiState.isPdfSearchActive) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            OutlinedTextField(
                                value = uiState.pdfSearchQuery,
                                onValueChange = onSearchQueryChanged,
                                placeholder = { Text("ابحث عن كلمة أو جملة داخل الـ PDF...", color = WaraqTextSecondary) },
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
                                    text = "تم العثور على ${uiState.pdfSearchResults.size} نتيجة (اضغط للانتقال):",
                                    style = MaterialTheme.typography.labelMedium,
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
                                                .clickable { onGoToPage(res.pageNumber) }
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

        // Bottom Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = WaraqSurfaceDark.copy(alpha = 0.95f),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Page Navigation Slider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = onPreviousPage,
                            enabled = uiState.pdfCurrentPage > 1,
                            modifier = Modifier.testTag("reader_prev_page_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "الصفحة السابقة",
                                tint = if (uiState.pdfCurrentPage > 1) WaraqTextPrimary else WaraqTextSecondary.copy(alpha = 0.3f)
                            )
                        }

                        Slider(
                            value = uiState.pdfCurrentPage.toFloat(),
                            onValueChange = { onGoToPage(it.toInt()) },
                            valueRange = 1f..uiState.pdfTotalPages.coerceAtLeast(1).toFloat(),
                            steps = (uiState.pdfTotalPages - 2).coerceAtLeast(0),
                            colors = SliderDefaults.colors(
                                thumbColor = WaraqNeonGreen,
                                activeTrackColor = WaraqNeonGreen,
                                inactiveTrackColor = WaraqSurfaceElevated
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reader_page_slider")
                        )

                        IconButton(
                            onClick = onNextPage,
                            enabled = uiState.pdfCurrentPage < uiState.pdfTotalPages,
                            modifier = Modifier.testTag("reader_next_page_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "الصفحة التالية",
                                tint = if (uiState.pdfCurrentPage < uiState.pdfTotalPages) WaraqTextPrimary else WaraqTextSecondary.copy(alpha = 0.3f)
                            )
                        }
                    }

                    // Page Indicator & Jump Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = {
                                jumpPageInput = uiState.pdfCurrentPage.toString()
                                showJumpDialog = true
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = WaraqSurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, WaraqBorder),
                            modifier = Modifier.testTag("reader_jump_page_button")
                        ) {
                            Text(
                                text = "صفحة ${uiState.pdfCurrentPage} من ${uiState.pdfTotalPages} • اضغط للانتقال ⤹",
                                color = WaraqTextPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
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
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val target = jumpPageInput.toIntOrNull()
                            if (target != null) {
                                onGoToPage(target)
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
                            text = "نص الصفحة ${uiState.pdfCurrentPage} (قابل للتحديد والنسخ)",
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
                        // Inform user that this page has no text layer (scanned image)
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

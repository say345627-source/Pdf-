package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.engine.PdfEngine

@Composable
fun DocumentSearchSheet(
    query: String,
    results: List<PdfEngine.SearchResult>,
    activeIndex: Int,
    isSearching: Boolean,
    onQueryChanged: (String) -> Unit,
    onResultClick: (index: Int, pageNumber: Int) -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val resultsListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(activeIndex, results.size) {
        if (results.isNotEmpty() && activeIndex in results.indices) {
            resultsListState.animateScrollToItem(activeIndex)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = Color(0xFF171A19),
            tonalElevation = 12.dp,
            shadowElevation = 16.dp,
            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF2D3330)),
            modifier = modifier
                .fillMaxWidth()
                .imePadding()
                .testTag("document_search_sheet")
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 10.dp, bottom = 12.dp)
        ) {
            // Top Drag Handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF4A524E))
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar Row (Screenshot replica: ✕ + pill [🔍 | query | 2/2 | ⊗])
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF282D2A),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "بحث",
                            tint = Color(0xFFB4BEB8),
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "ابحث داخل الملف...",
                                    color = Color(0xFF818A85),
                                    fontSize = 14.sp
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = onQueryChanged,
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(Color(0xFF39F58A)),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    keyboardController?.hide()
                                }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .testTag("document_search_input")
                            )
                        }

                        if (query.isNotBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (results.isNotEmpty()) "${activeIndex + 1}/${results.size}" else "0/0",
                                color = Color(0xFF9EA8A2),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("search_match_counter")
                            )

                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF545B57))
                                    .clickable { onQueryChanged("") }
                                    .testTag("search_clear_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "مسح البحث",
                                    tint = Color(0xFF171A19),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("search_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق البحث",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Results Section
            when {
                isSearching -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF39F58A),
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "جاري البحث في صفحات المستند...",
                            color = Color(0xFFB4BEB8),
                            fontSize = 13.sp
                        )
                    }
                }

                query.isNotBlank() && results.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لم يتم العثور على «$query» في النصوص القابلة للبحث داخل المستند.",
                            color = Color(0xFF9EA8A2),
                            fontSize = 13.sp
                        )
                    }
                }

                results.isNotEmpty() -> {
                    LazyColumn(
                        state = resultsListState,
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp)
                            .testTag("search_results_list")
                    ) {
                        itemsIndexed(results) { index, result ->
                            val isActive = index == activeIndex
                            SearchResultItemRow(
                                result = result,
                                query = query,
                                isActive = isActive,
                                onClick = {
                                    keyboardController?.hide()
                                    onResultClick(index, result.pageNumber)
                                }
                            )
                            if (index < results.lastIndex) {
                                HorizontalDivider(
                                    color = Color(0xFF262B28),
                                    thickness = 0.6.dp
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Navigation Arrows Row (Screenshot replica: [ ← ] [ → ])
            if (results.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    val canNavigate = results.size > 1
                    Surface(
                        onClick = onPreviousClick,
                        enabled = canNavigate,
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF282D2A),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("search_prev_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "النتيجة السابقة",
                                tint = if (canNavigate) Color.White else Color(0xFF5D6661),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onNextClick,
                        enabled = canNavigate,
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF282D2A),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("search_next_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "النتيجة التالية",
                                tint = if (canNavigate) Color.White else Color(0xFF5D6661),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun SearchResultItemRow(
    result: PdfEngine.SearchResult,
    query: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isActive) Color(0xFF1F3044) else Color.Transparent
    val matchBadgeBg = if (isActive) Color(0xFFD64C45) else Color(0xFF8C7323)
    val matchTerm = result.matchedTerm.ifBlank { query.trim() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("search_result_page_${result.pageNumber}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "الصفحة ${result.pageNumber}",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (result.matchCount == 1) {
                    "1 نتيجة مطابقة"
                } else {
                    "${result.matchCount} نتائج مطابقة"
                },
                color = Color(0xFF8B948F),
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Render snippet with highlighted search keyword badge
        val annotated = remember(result.snippet, matchTerm, matchBadgeBg) {
            buildAnnotatedString {
                val text = result.snippet
                if (matchTerm.isBlank()) {
                    append(text)
                } else {
                    var cursor = 0
                    while (cursor < text.length) {
                        val matchIdx = text.indexOf(matchTerm, cursor, ignoreCase = true)
                        if (matchIdx == -1) {
                            append(text.substring(cursor))
                            break
                        }
                        if (matchIdx > cursor) {
                            append(text.substring(cursor, matchIdx))
                        }
                        withStyle(
                            SpanStyle(
                                color = Color.White,
                                background = matchBadgeBg,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(" ${text.substring(matchIdx, matchIdx + matchTerm.length)} ")
                        }
                        cursor = matchIdx + matchTerm.length
                    }
                }
            }
        }

        Text(
            text = annotated,
            color = Color(0xFFD0D6D2),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BookEntity
import com.example.ui.theme.WaraqBorder
import com.example.ui.theme.WaraqDocxColor
import com.example.ui.theme.WaraqError
import com.example.ui.theme.WaraqNeonGreen
import com.example.ui.theme.WaraqPdfColor
import com.example.ui.theme.WaraqSurfaceElevated
import com.example.ui.theme.WaraqTextPrimary
import com.example.ui.theme.WaraqTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BookCard(
    book: BookEntity,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onManagePagesClick: () -> Unit = {},
    isGrid: Boolean = false,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isPdf = book.fileType.equals("PDF", ignoreCase = true)
    val badgeColor = if (isPdf) WaraqPdfColor else WaraqDocxColor
    val formattedSize = remember(book.fileSizeBytes) { formatFileSize(book.fileSizeBytes) }
    val formattedDate = remember(book.dateAddedTimestamp) { formatDate(book.dateAddedTimestamp) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("book_item_${book.id}")
            .clickable { onClick() },
        color = Color.Transparent
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // File Icon / Paper Badge (exact screenshot replica: white folded paper with red/blue tag)
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 54.dp)
                        .shadow(2.dp, RoundedCornerShape(topStart = 4.dp, bottomStart = 6.dp, bottomEnd = 6.dp, topEnd = 12.dp))
                        .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 6.dp, bottomEnd = 6.dp, topEnd = 12.dp))
                        .background(Color.White)
                        .border(
                            1.dp,
                            Color(0xFFE0E0E0),
                            RoundedCornerShape(topStart = 4.dp, bottomStart = 6.dp, bottomEnd = 6.dp, topEnd = 12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Folded corner effect
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(10.dp)
                            .background(Color(0xFFD5D5D5))
                    )

                    // Tag Badge (PDF / DOCX)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(badgeColor)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = book.fileType,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // File Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.originalFileName.ifBlank { book.title },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$formattedSize • $formattedDate",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )

                        if (book.progressPercent > 0) {
                            val unitLabel = if (isPdf) "ص" else "ف"
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "• $unitLabel ${book.lastReadPage} (%${book.progressPercent.toInt()})",
                                color = WaraqNeonGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (book.progressPercent > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (book.progressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = WaraqNeonGreen,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }

                // 3 Vertical Dots Menu (matching screenshot)
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "خيارات الملف",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        DropdownMenuItem(
                            text = { Text("فتح المستند", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = {
                                Icon(Icons.Default.MenuBook, contentDescription = null, tint = WaraqNeonGreen)
                            },
                            onClick = {
                                menuExpanded = false
                                onClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("إدارة واستخراج الصفحات", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = {
                                Icon(Icons.Default.GridView, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            onClick = {
                                menuExpanded = false
                                onManagePagesClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("إعادة تسمية", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            onClick = {
                                menuExpanded = false
                                onRenameClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("مشاركة الملف", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = {
                                Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            onClick = {
                                menuExpanded = false
                                onShareClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("إزالة من القائمة", color = WaraqError) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = WaraqError)
                            },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                thickness = 0.6.dp,
                modifier = Modifier.padding(start = 74.dp)
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes.toFloat() / (1024 * 1024))
        bytes >= 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes.toFloat() / 1024)
        bytes > 0 -> "$bytes B"
        else -> "1.2 MB"
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    return sdf.format(Date(if (timestamp > 0) timestamp else System.currentTimeMillis()))
}

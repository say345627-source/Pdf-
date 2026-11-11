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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.BookEntity
import com.example.ui.theme.WaraqBorder
import com.example.ui.theme.WaraqDarkBg
import com.example.ui.theme.WaraqDocxColor
import com.example.ui.theme.WaraqError
import com.example.ui.theme.WaraqNeonGreen
import com.example.ui.theme.WaraqPdfColor
import com.example.ui.theme.WaraqSurfaceElevated
import com.example.ui.theme.WaraqTextPrimary
import com.example.ui.theme.WaraqTextSecondary
import java.io.File

@Composable
fun BookCard(
    book: BookEntity,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isGrid: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (isGrid) {
        BookGridItem(
            book = book,
            onClick = onClick,
            onRenameClick = onRenameClick,
            onShareClick = onShareClick,
            onDeleteClick = onDeleteClick,
            modifier = modifier
        )
    } else {
        BookListItem(
            book = book,
            onClick = onClick,
            onRenameClick = onRenameClick,
            onShareClick = onShareClick,
            onDeleteClick = onDeleteClick,
            modifier = modifier
        )
    }
}

@Composable
fun BookGridItem(
    book: BookEntity,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isPdf = book.fileType.equals("PDF", ignoreCase = true)
    val badgeColor = if (isPdf) WaraqPdfColor else WaraqDocxColor

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("book_item_${book.id}")
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, WaraqBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = WaraqSurfaceElevated,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Cover Image with Top Badges
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(WaraqDarkBg)
                    .border(1.dp, WaraqBorder, RoundedCornerShape(10.dp))
            ) {
                val coverFile = book.coverImagePath?.let { File(it) }
                if (coverFile != null && coverFile.exists()) {
                    AsyncImage(
                        model = coverFile,
                        contentDescription = book.title,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = WaraqNeonGreen,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Type Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = book.fileType,
                        color = WaraqTextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title and Menu
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = WaraqTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "خيارات الكتاب",
                            tint = WaraqTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    BookDropdownMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onOpen = {
                            menuExpanded = false
                            onClick()
                        },
                        onRename = {
                            menuExpanded = false
                            onRenameClick()
                        },
                        onShare = {
                            menuExpanded = false
                            onShareClick()
                        },
                        onDelete = {
                            menuExpanded = false
                            onDeleteClick()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { (book.progressPercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = WaraqNeonGreen,
                trackColor = WaraqDarkBg
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Position and percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val unitLabel = if (isPdf) "ص" else "ف"
                Text(
                    text = "$unitLabel ${book.lastReadPage} / ${book.totalPages}",
                    style = MaterialTheme.typography.bodySmall,
                    color = WaraqTextSecondary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "%${book.progressPercent.toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = WaraqNeonGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun BookListItem(
    book: BookEntity,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isPdf = book.fileType.equals("PDF", ignoreCase = true)
    val badgeColor = if (isPdf) WaraqPdfColor else WaraqDocxColor
    val unitLabel = if (isPdf) "صفحة" else "فقرة"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("book_item_${book.id}")
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, WaraqBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = WaraqSurfaceElevated,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            val coverFile = book.coverImagePath?.let { File(it) }
            if (coverFile != null && coverFile.exists()) {
                AsyncImage(
                    model = coverFile,
                    contentDescription = book.title,
                    modifier = Modifier
                        .size(width = 54.dp, height = 72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, WaraqBorder, RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 54.dp, height = 72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(WaraqDarkBg)
                        .border(1.dp, WaraqBorder, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = WaraqNeonGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = book.fileType,
                            color = WaraqTextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = WaraqTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "آخر موضع: $unitLabel ${book.lastReadPage} من ${book.totalPages}",
                    style = MaterialTheme.typography.bodySmall,
                    color = WaraqTextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { (book.progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = WaraqNeonGreen,
                        trackColor = WaraqDarkBg
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "%${book.progressPercent.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = WaraqNeonGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Menu button
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "خيارات الكتاب",
                        tint = WaraqTextSecondary
                    )
                }

                BookDropdownMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onOpen = {
                        menuExpanded = false
                        onClick()
                    },
                    onRename = {
                        menuExpanded = false
                        onRenameClick()
                    },
                    onShare = {
                        menuExpanded = false
                        onShareClick()
                    },
                    onDelete = {
                        menuExpanded = false
                        onDeleteClick()
                    }
                )
            }
        }
    }
}

@Composable
fun BookDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(WaraqSurfaceElevated)
    ) {
        DropdownMenuItem(
            text = { Text("فتح الكتاب", color = WaraqTextPrimary) },
            leadingIcon = {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = WaraqNeonGreen)
            },
            onClick = onOpen
        )
        DropdownMenuItem(
            text = { Text("إعادة تسمية", color = WaraqTextPrimary) },
            leadingIcon = {
                Icon(Icons.Default.Edit, contentDescription = null, tint = WaraqTextSecondary)
            },
            onClick = onRename
        )
        DropdownMenuItem(
            text = { Text("مشاركة الملف", color = WaraqTextPrimary) },
            leadingIcon = {
                Icon(Icons.Default.Share, contentDescription = null, tint = WaraqTextSecondary)
            },
            onClick = onShare
        )
        DropdownMenuItem(
            text = { Text("إزالة من المكتبة", color = WaraqError) },
            leadingIcon = {
                Icon(Icons.Default.Delete, contentDescription = null, tint = WaraqError)
            },
            onClick = onDelete
        )
    }
}

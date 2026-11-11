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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.ui.theme.WaraqNeonGreen
import com.example.ui.theme.WaraqPdfColor
import com.example.ui.theme.WaraqSurfaceElevated
import com.example.ui.theme.WaraqTextPrimary
import com.example.ui.theme.WaraqTextSecondary
import java.io.File

@Composable
fun HeroContinueReadingCard(
    book: BookEntity,
    onContinueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPdf = book.fileType.equals("PDF", ignoreCase = true)
    val badgeColor = if (isPdf) WaraqPdfColor else WaraqDocxColor
    val unitLabel = if (isPdf) "صفحة" else "فقرة"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_continue_reading_card")
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, WaraqBorder, RoundedCornerShape(20.dp))
            .clickable { onContinueClick() },
        color = WaraqSurfaceElevated,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Tag
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = WaraqNeonGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "متابعة القراءة",
                    style = MaterialTheme.typography.labelLarge,
                    color = WaraqNeonGreen,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = book.fileType,
                        color = WaraqTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Body Row: Cover + Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Book Cover
                val coverFile = book.coverImagePath?.let { File(it) }
                if (coverFile != null && coverFile.exists()) {
                    AsyncImage(
                        model = coverFile,
                        contentDescription = "غلاف ${book.title}",
                        modifier = Modifier
                            .size(width = 64.dp, height = 88.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, WaraqBorder, RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 88.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, WaraqBorder, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = null,
                            tint = WaraqNeonGreen,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Info Column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = WaraqTextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "آخر موضع: $unitLabel ${book.lastReadPage} من ${book.totalPages.coerceAtLeast(1)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = WaraqTextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Progress bar
                    LinearProgressIndicator(
                        progress = { (book.progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = WaraqNeonGreen,
                        trackColor = WaraqDarkBg
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "إنجاز %${book.progressPercent.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = WaraqNeonGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Button
            Button(
                onClick = onContinueClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("continue_reading_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WaraqNeonGreen,
                    contentColor = WaraqDarkBg
                )
            ) {
                Text(
                    text = "متابعة من آخر موضع",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

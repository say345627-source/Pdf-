package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.WaraqDarkBg
import com.example.ui.theme.WaraqNeonGreen
import com.example.ui.theme.WaraqTextPrimary
import com.example.ui.theme.WaraqTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaraqTopBar(
    isGridView: Boolean,
    onToggleView: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(WaraqNeonGreen)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "وَرَق",
                    style = MaterialTheme.typography.titleLarge,
                    color = WaraqTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "WARAQ",
                    style = MaterialTheme.typography.labelSmall,
                    color = WaraqTextSecondary,
                    letterSpacing = 2.sp
                )
            }
        },
        actions = {
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.testTag("top_bar_search_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "البحث",
                    tint = WaraqTextPrimary
                )
            }

            IconButton(
                onClick = onToggleView,
                modifier = Modifier.testTag("top_bar_toggle_view_button")
            ) {
                Icon(
                    imageVector = if (isGridView) Icons.Default.ViewAgenda else Icons.Default.GridView,
                    contentDescription = if (isGridView) "عرض كقائمة" else "عرض كشبكة",
                    tint = WaraqTextPrimary
                )
            }

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.testTag("top_bar_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "الإعدادات",
                    tint = WaraqTextPrimary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = WaraqDarkBg,
            scrolledContainerColor = WaraqDarkBg
        ),
        modifier = modifier.testTag("waraq_top_bar")
    )
}

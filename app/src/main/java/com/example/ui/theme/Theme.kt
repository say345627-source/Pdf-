package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.data.local.WaraqThemeId

// 1. Charcoal Neon
val WaraqDarkColorScheme = darkColorScheme(
    primary = WaraqNeonGreen,
    onPrimary = WaraqDarkBg,
    primaryContainer = WaraqGreenContainer,
    onPrimaryContainer = WaraqOnGreenContainer,
    secondary = WaraqNeonGreen,
    onSecondary = WaraqDarkBg,
    background = WaraqDarkBg,
    onBackground = WaraqTextPrimary,
    surface = WaraqSurfaceDark,
    onSurface = WaraqTextPrimary,
    surfaceVariant = WaraqSurfaceElevated,
    onSurfaceVariant = WaraqTextSecondary,
    outline = WaraqBorder,
    outlineVariant = WaraqSurfaceHigher,
    error = WaraqError
)

// 2. AMOLED Pitch Black
val AmoledColorScheme = darkColorScheme(
    primary = AmoledAccent,
    onPrimary = Color.Black,
    primaryContainer = AmoledGreenContainer,
    onPrimaryContainer = AmoledAccent,
    secondary = AmoledAccent,
    onSecondary = Color.Black,
    background = AmoledDarkBg,
    onBackground = Color.White,
    surface = AmoledSurface,
    onSurface = Color.White,
    surfaceVariant = AmoledSurfaceElevated,
    onSurfaceVariant = Color(0xFFA5B2AA),
    outline = AmoledBorder,
    outlineVariant = Color(0xFF28302B),
    error = WaraqError
)

// 3. Midnight Navy
val NavyColorScheme = darkColorScheme(
    primary = NavyAccent,
    onPrimary = NavyDarkBg,
    primaryContainer = NavyContainer,
    onPrimaryContainer = Color(0xFF80EEF0),
    secondary = NavyAccent,
    onSecondary = NavyDarkBg,
    background = NavyDarkBg,
    onBackground = Color(0xFFF0F4FA),
    surface = NavySurface,
    onSurface = Color(0xFFF0F4FA),
    surfaceVariant = NavySurfaceElevated,
    onSurfaceVariant = Color(0xFFA2B4CD),
    outline = NavyBorder,
    outlineVariant = Color(0xFF2B436D),
    error = WaraqError
)

// 4. Warm Sepia
val SepiaColorScheme = lightColorScheme(
    primary = SepiaAccent,
    onPrimary = Color.White,
    primaryContainer = SepiaContainer,
    onPrimaryContainer = SepiaTextPrimary,
    secondary = SepiaAccent,
    onSecondary = Color.White,
    background = SepiaBg,
    onBackground = SepiaTextPrimary,
    surface = SepiaSurface,
    onSurface = SepiaTextPrimary,
    surfaceVariant = SepiaSurfaceElevated,
    onSurfaceVariant = SepiaTextSecondary,
    outline = SepiaBorder,
    outlineVariant = Color(0xFFC7B393),
    error = Color(0xFFBA1A1A)
)

// 5. Crisp Pure Light
val LightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F6CA),
    onPrimaryContainer = Color(0xFF00391C),
    secondary = Color(0xFF1B5E20),
    onSecondary = Color.White,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = Color(0xFFC7D2CC),
    error = Color(0xFFBA1A1A)
)

fun getThemeColorScheme(themeId: String): ColorScheme {
    return when (themeId) {
        WaraqThemeId.AMOLED_BLACK.name -> AmoledColorScheme
        WaraqThemeId.MIDNIGHT_NAVY.name -> NavyColorScheme
        WaraqThemeId.WARM_SEPIA.name -> SepiaColorScheme
        WaraqThemeId.PURE_LIGHT.name -> LightColorScheme
        else -> WaraqDarkColorScheme
    }
}

@Composable
fun MyApplicationTheme(
    themeId: String = WaraqThemeId.CHARCOAL_NEON.name,
    content: @Composable () -> Unit
) {
    val colorScheme = getThemeColorScheme(themeId)

    // Always provide RTL layout for Arabic interface
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

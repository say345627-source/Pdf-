package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val WaraqDarkColorScheme = darkColorScheme(
    primary = WaraqNeonGreen,
    onPrimary = WaraqDarkBg,
    primaryContainer = WaraqGreenContainer,
    onPrimaryContainer = WaraqOnGreenContainer,
    secondary = WaraqNeonGreen,
    onSecondary = WaraqDarkBg,
    secondaryContainer = WaraqGreenContainer,
    onSecondaryContainer = WaraqOnGreenContainer,
    background = WaraqDarkBg,
    onBackground = WaraqTextPrimary,
    surface = WaraqSurfaceDark,
    onSurface = WaraqTextPrimary,
    surfaceVariant = WaraqSurfaceElevated,
    onSurfaceVariant = WaraqTextSecondary,
    outline = WaraqBorder,
    outlineVariant = WaraqSurfaceHigher,
    error = WaraqError,
    onError = WaraqDarkBg,
    errorContainer = WaraqErrorContainer,
    onErrorContainer = WaraqTextPrimary
)

private val WaraqLightColorScheme = lightColorScheme(
    primary = Color(0xFF00874A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F6CA),
    onPrimaryContainer = Color(0xFF00391C),
    secondary = Color(0xFF1B5E20),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEDC8),
    onSecondaryContainer = Color(0xFF1B5E20),
    background = Color(0xFFF7FAF8),
    onBackground = Color(0xFF1A1C1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1A),
    surfaceVariant = Color(0xFFE8EFEA),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFFC2CBC4),
    outlineVariant = Color(0xFFE0E5E1),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to luxurious dark theme as specified
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) WaraqDarkColorScheme else WaraqLightColorScheme

    // Always provide RTL layout for Arabic interface
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

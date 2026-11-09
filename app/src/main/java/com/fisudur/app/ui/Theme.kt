package com.fisudur.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

// Night-sky navy with a warm lamp-light gold: the app's own look.
private val Navy = Color(0xFF1E3157)
private val NavyLight = Color(0xFF9DB4E0)
private val Gold = Color(0xFFC89A3C)
private val GoldLight = Color(0xFFE9C77E)

private val Light = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4F5),
    onPrimaryContainer = Navy,
    secondary = Gold,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E8C8),
    onSecondaryContainer = Color(0xFF4A3712),
    background = Color(0xFFF5F6FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDEFF5),
    onSurfaceVariant = Color(0xFF545B6B),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = NavyLight,
    onPrimary = Color(0xFF0E1A33),
    primaryContainer = Color(0xFF2A3F6B),
    onPrimaryContainer = Color(0xFFDCE4F5),
    secondary = GoldLight,
    onSecondary = Color(0xFF3A2A08),
    secondaryContainer = Color(0xFF5A4518),
    onSecondaryContainer = Color(0xFFF6E8C8),
    background = Color(0xFF0F1524),
    surface = Color(0xFF172036),
    surfaceVariant = Color(0xFF222C45),
    onSurfaceVariant = Color(0xFFB9C1D4),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}

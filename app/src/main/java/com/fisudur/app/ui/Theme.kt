package com.fisudur.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.fisudur.app.R

/** Classical naskh for everything Quranic: surah names and verses. */
val Amiri = FontFamily(
    Font(R.font.amiri_regular, FontWeight.Normal),
    Font(R.font.amiri_bold, FontWeight.Bold),
)

/** The interface face, in four weights (one variable font file). */
@OptIn(ExperimentalTextApi::class)
private fun readex(w: FontWeight) =
    Font(R.font.readex_pro, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))

val Readex = FontFamily(
    readex(FontWeight.Normal),
    readex(FontWeight.Medium),
    readex(FontWeight.SemiBold),
    readex(FontWeight.Bold),
)

/** Colors the screens use beyond the Material scheme. */
data class Palette(
    val hero: Brush,
    val onHero: Color,
    val onHeroMuted: Color,
    val gold: Color,
    val goldSoft: Color,
    val ink: Color,
    val muted: Color,
    val card: Color,
    val line: Color,
    val done: Color,
    val warn: Color,
    /** Days off for a reason (work, travel, sickness). */
    val excuse: Color,
)

private val NavyDeep = Color(0xFF101C38)
private val Navy = Color(0xFF1B2D55)
private val NavyMid = Color(0xFF26407A)
private val Gold = Color(0xFFC9A04A)
private val GoldBright = Color(0xFFE6C47A)
private val Parchment = Color(0xFFF6F1E6)

private val LightPalette = Palette(
    hero = Brush.linearGradient(listOf(NavyDeep, Navy, NavyMid)),
    onHero = Color(0xFFF8F3E7),
    onHeroMuted = Color(0xB3F8F3E7),
    gold = Gold,
    goldSoft = Color(0xFFF1E3BF),
    ink = Color(0xFF1A2236),
    muted = Color(0xFF6B6F7C),
    card = Color(0xFFFFFDF8),
    line = Color(0xFFE7DEC9),
    done = Color(0xFF3E8E6A),
    warn = Color(0xFFC0574A),
    excuse = Color(0xFF5B7FA8),
)

private val DarkPalette = Palette(
    hero = Brush.linearGradient(listOf(Color(0xFF0B1328), NavyDeep, Navy)),
    onHero = Color(0xFFF8F3E7),
    onHeroMuted = Color(0xB3F8F3E7),
    gold = GoldBright,
    goldSoft = Color(0xFF3B321D),
    ink = Color(0xFFEDE8DC),
    muted = Color(0xFFA2A7B5),
    card = Color(0xFF16213C),
    line = Color(0xFF26324F),
    done = Color(0xFF66C29A),
    warn = Color(0xFFE5857A),
    excuse = Color(0xFF8FB0D8),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

private val LightScheme = lightColorScheme(
    primary = Navy, onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE4F3), onPrimaryContainer = Navy,
    secondary = Gold, onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E3BF), onSecondaryContainer = Color(0xFF4A3712),
    background = Parchment, onBackground = Color(0xFF1A2236),
    surface = Color(0xFFFFFDF8), onSurface = Color(0xFF1A2236),
    surfaceVariant = Color(0xFFEFE8D9), onSurfaceVariant = Color(0xFF6B6F7C),
    outline = Color(0xFFE7DEC9), error = Color(0xFFB4483C),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFAFC2EA), onPrimary = NavyDeep,
    primaryContainer = Color(0xFF26407A), onPrimaryContainer = Color(0xFFDDE4F3),
    secondary = GoldBright, onSecondary = Color(0xFF3A2A08),
    secondaryContainer = Color(0xFF3B321D), onSecondaryContainer = Color(0xFFF1E3BF),
    background = Color(0xFF0C1427), onBackground = Color(0xFFEDE8DC),
    surface = Color(0xFF16213C), onSurface = Color(0xFFEDE8DC),
    surfaceVariant = Color(0xFF1F2B49), onSurfaceVariant = Color(0xFFA2A7B5),
    outline = Color(0xFF26324F), error = Color(0xFFE07A6E),
)

private fun ui(size: Int, weight: FontWeight = FontWeight.Normal, line: Int = (size * 1.5).toInt()) =
    TextStyle(fontFamily = Readex, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private fun readex(size: Int, weight: FontWeight = FontWeight.Bold, line: Int = (size * 1.4).toInt()) =
    TextStyle(fontFamily = Readex, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

val AppTypography = Typography(
    displayLarge = readex(56, line = 68),
    displayMedium = readex(44, line = 56),
    displaySmall = readex(34, line = 46),
    headlineLarge = readex(26),
    headlineMedium = readex(22),
    headlineSmall = readex(19),
    titleLarge = readex(17, FontWeight.SemiBold),
    titleMedium = ui(15, FontWeight.SemiBold),
    titleSmall = ui(14, FontWeight.SemiBold),
    bodyLarge = ui(15),
    bodyMedium = ui(14),
    bodySmall = ui(12, line = 18),
    labelLarge = ui(14, FontWeight.Medium),
    labelMedium = ui(12, FontWeight.Medium),
    labelSmall = ui(11, FontWeight.Medium),
)

/** Surah names: Amiri, sized to sit beside the interface text. */
val SurahStyle = TextStyle(fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 28.sp)

@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = AppTypography) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalPalette provides if (dark) DarkPalette else LightPalette,
            content = content,
        )
    }
}

package com.appsc.ca.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// The APPSC Prep palette, with green as the accent so the two apps are told apart.
object C {
    val Ink = Color(0xFF12203A)
    val Body = Color(0xFF1F2937)
    val Muted = Color(0xFF5A6B88)
    val Faint = Color(0xFF8A96AD)
    val Accent = Color(0xFF0F5132)
    val AccentSoft = Color(0xFFE3F1E9)
    val Line = Color(0xFFE5E8EF)
    val Chip = Color(0xFFF1F3F6)
    val Surface = Color(0xFFF7F8FA)
    val Ap = Color(0xFF9A3412)
    val ApSoft = Color(0xFFFFF4E8)
    val Fact = Color(0xFF6D28D9)
    val FactSoft = Color(0xFFF3EEFF)
    val Critical = Color(0xFFC62828)
    val CriticalSoft = Color(0xFFFDECEC)
    val High = Color(0xFFB45309)
    val HighSoft = Color(0xFFFFF4E0)
    val Medium = Color(0xFF1D4ED8)
    val MediumSoft = Color(0xFFE8EEFC)
    val Unread = Color(0xFF15803D)
}

private val colors = lightColorScheme(
    primary = C.Accent,
    onPrimary = Color.White,
    primaryContainer = C.AccentSoft,
    onPrimaryContainer = C.Accent,
    secondary = C.Accent,
    secondaryContainer = C.AccentSoft,
    onSecondaryContainer = C.Accent,
    background = Color.White,
    onBackground = C.Ink,
    surface = Color.White,
    onSurface = C.Ink,
    surfaceVariant = C.Surface,
    onSurfaceVariant = C.Muted,
    outline = C.Line,
    outlineVariant = C.Line,
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color.White,
)

private val type = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun CaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}

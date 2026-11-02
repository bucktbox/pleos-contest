package com.safecargo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalContentColor
import com.safecargo.app.R

val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

private val colors = darkColorScheme(
    primary = SC.accent, onPrimary = SC.accentOn,
    background = SC.bg, onBackground = SC.text,
    surface = SC.surface, onSurface = SC.text,
    surfaceVariant = SC.elevated, onSurfaceVariant = SC.textDim,
    outline = SC.borderStrong, outlineVariant = SC.border,
)

private fun ts(size: Int, w: FontWeight) = TextStyle(fontFamily = Pretendard, fontWeight = w, fontSize = size.sp, letterSpacing = (-0.5).sp)

private val typography = Typography(
    displayLarge = ts(56, FontWeight.Bold),
    headlineMedium = ts(30, FontWeight.Bold),   // Title
    titleMedium = ts(22, FontWeight.Bold),      // Label
    bodyLarge = ts(22, FontWeight.Medium),
    bodyMedium = ts(18, FontWeight.Medium),     // Caption
    labelLarge = ts(22, FontWeight.Bold),
    labelMedium = ts(18, FontWeight.Medium),
)

/** 다크 하나만. 시스템 다크 설정과 무관하게 항상 다크. */
@Composable
fun SafeCargoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography) {
        CompositionLocalProvider(LocalContentColor provides SC.text, content = content)
    }
}

package com.safecargo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalContentColor
import com.safecargo.app.R

val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

private fun colorsOf(p: ScPalette) = (if (p.isDark) darkColorScheme() else lightColorScheme()).copy(
    primary = p.accent, onPrimary = p.accentOn,
    background = p.bg, onBackground = p.text,
    surface = p.surface, onSurface = p.text,
    surfaceVariant = p.elevated, onSurfaceVariant = p.textDim,
    outline = p.borderStrong, outlineVariant = p.border,
)

// 피그마 글자 간격 -0.5% (Headline -1.5%). sp 가 아니라 em.
private fun ts(size: Int, w: FontWeight, lh: Float = 1.3f, ls: Float = -0.005f) = TextStyle(
    fontFamily = Pretendard, fontWeight = w, fontSize = size.sp, lineHeight = (size * lh).sp, letterSpacing = ls.em,
)

/**
 * 피그마 「01 시스템 가이드」 글자 스타일. SemiBold(600) 파일이 없어 Bold 로 대신한다.
 */
object ScType {
    val headline = ts(40, FontWeight.Bold, 1.3f, -0.015f)
    val title = ts(30, FontWeight.Bold, 1.35f)
    val label = ts(22, FontWeight.Bold, 1.3f)
    val bodyLarge = ts(26, FontWeight.Normal, 1.45f)
    val body = ts(22, FontWeight.Normal, 1.5f)
    val caption = ts(18, FontWeight.Medium, 1.4f)
}

private val typography = Typography(
    displayLarge = ts(56, FontWeight.Bold),
    headlineMedium = ts(30, FontWeight.Bold),   // Title
    titleMedium = ts(22, FontWeight.Bold),      // Label
    bodyLarge = ts(22, FontWeight.Medium),
    bodyMedium = ts(18, FontWeight.Medium),     // Caption
    labelLarge = ts(22, FontWeight.Bold),
    labelMedium = ts(18, FontWeight.Medium),
)

/**
 * 시스템 다크 / 라이트 설정을 따른다 (11/03 디자인 요청 §3 — 앱 안 전환 버튼은 없음).
 * [dark] 를 주면 그 모드로 고정 (프리뷰용).
 */
@Composable
fun SafeCargoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    // 전역 토큰([SC])을 이 컴포지션보다 먼저 바꾼다 — 같은 스냅샷 안이라 아래 화면이 바로 새 값을 읽는다.
    if (SC.palette !== p) SC.palette = p
    val scheme = remember(p) { colorsOf(p) }
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(LocalContentColor provides p.text, content = content)
    }
}

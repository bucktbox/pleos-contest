package com.safecargo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 피그마 Color 컬렉션의 Dark / Light 모드 값 (「01 시스템 가이드」 다크 13:92 · 라이트 14:166, 11/03 기준).
 * 같은 변수 이름에 모드별 값만 다르다.
 */
@Immutable
data class ScPalette(
    val bg: Color,
    /** 하단 고정 안내 바 (피그마 06 상태 안내 P) — bg 보다 한 단 깊음. */
    val bgDeep: Color,
    val surface: Color,
    val elevated: Color,
    val pressed: Color,
    val border: Color,
    val borderStrong: Color,
    val accent: Color,
    val accentPressed: Color,
    val accentOn: Color,
    val accentSubtle: Color,
    val text: Color,
    val textDim: Color,
    val textDisabled: Color,
    /** 열쇠 · 스트릭 노랑 (color/game/key). */
    val gameKey: Color,
    val gameKeySubtle: Color,
    val seatmapBody: Color,
    val seatmapGlass: Color,
    val isDark: Boolean,
)

val DarkPalette = ScPalette(
    bg = Color(0xFF090B0F), bgDeep = Color(0xFF000000),
    surface = Color(0xFF12151B), elevated = Color(0xFF1C2028), pressed = Color(0xFF2A2F38),
    border = Color(0xFF2A2F38), borderStrong = Color(0xFF5C6370),
    accent = Color(0xFF4C8DFF), accentPressed = Color(0xFF2A6FF0), accentOn = Color(0xFFFFFFFF), accentSubtle = Color(0xFF182A4A),
    text = Color(0xFFFFFFFF), textDim = Color(0xFFA3A9B4), textDisabled = Color(0xFF3F4550),
    gameKey = Color(0xFFFFD36B), gameKeySubtle = Color(0xFF3A3122),
    seatmapBody = Color(0xFF1C2028), seatmapGlass = Color(0xFF2A2F38),
    isDark = true,
)

val LightPalette = ScPalette(
    bg = Color(0xFFF4F5F7), bgDeep = Color(0xFFE6E8EC),
    surface = Color(0xFFFFFFFF), elevated = Color(0xFFE6E8EC), pressed = Color(0xFFC9CDD4),
    border = Color(0xFFC9CDD4), borderStrong = Color(0xFF7D8492),
    accent = Color(0xFF2A6FF0), accentPressed = Color(0xFF1D56C4), accentOn = Color(0xFFFFFFFF), accentSubtle = Color(0xFFDCE7FF),
    text = Color(0xFF090B0F), textDim = Color(0xFF5C6370), textDisabled = Color(0xFFA3A9B4),
    gameKey = Color(0xFFD69A00), gameKeySubtle = Color(0xFFFFF3D1),
    seatmapBody = Color(0xFFEEF4FF), seatmapGlass = Color(0xFFDCE7FF),
    isDark = false,
)

/**
 * 디자인 토큰. 색은 지금 테마([SafeCargoTheme] 이 시스템 다크 / 라이트 설정으로 고른다)의 [ScPalette] 를 읽는다.
 * 스냅샷 상태라 테마가 바뀌면 읽은 곳(그리기 람다 포함)이 다시 그려진다.
 */
object SC {
    var palette: ScPalette by mutableStateOf(DarkPalette)
        internal set

    val bg get() = palette.bg
    val bgDeep get() = palette.bgDeep
    val surface get() = palette.surface
    val elevated get() = palette.elevated
    val pressed get() = palette.pressed
    val border get() = palette.border
    val borderStrong get() = palette.borderStrong
    val accent get() = palette.accent
    val accentPressed get() = palette.accentPressed
    val accentOn get() = palette.accentOn
    val accentSubtle get() = palette.accentSubtle
    val text get() = palette.text
    val textDim get() = palette.textDim
    val textDisabled get() = palette.textDisabled
    val gameKey get() = palette.gameKey
    val gameKeySubtle get() = palette.gameKeySubtle
    val seatmapBody get() = palette.seatmapBody
    val seatmapGlass get() = palette.seatmapGlass

    // 등급 색: 피그마 color/grade/* 가 정본 (11/03 디자인 요청 §4). 두 모드 같은 값.
    // config/judge.json `gradeLightColors` (JudgeConfig 기본값) 와 같은 값이어야 한다 — 실내 조명 · 화면 테두리 · 언박싱 · 배지가 같은 색.
    val rare = Color(0xFF3DD9F0)
    val epic = Color(0xFF4C8DFF)
    val legendary = Color(0xFFB07CFF)

    val touchMin: Dp = 76.dp

    // 피그마 spacing-* (2xs 4 · xs 8 · sm 12 · md 16 · lg 24 · xl 32)
    val gap2xs: Dp = 4.dp
    val gapXs: Dp = 8.dp
    val gapSm: Dp = 12.dp
    val gap: Dp = 16.dp
    val gapLg: Dp = 24.dp
    val gapXl: Dp = 32.dp

    // 피그마 radius-* (sm 8 · md 16 · lg 24)
    val radiusSm: Dp = 8.dp
    val radius: Dp = 16.dp
    val radiusLg: Dp = 24.dp
}

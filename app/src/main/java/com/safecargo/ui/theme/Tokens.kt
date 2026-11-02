package com.safecargo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 디자인 토큰. 피그마 「01 시스템 가이드」 변수에서 가져온 값(bg/surface/elevated/pressed/border/text/accent,
 * size-touch-min, spacing-md)과, 피그마에 아직 확정되지 않은 게임 색(등급 3종 — AG-02 확정 전)으로 나뉜다.
 */
object SC {
    val bg = Color(0xFF090B0F)
    /** 하단 고정 안내 바 (피그마 06 상태 안내 P) — bg 보다 한 단 어두움. */
    val bgDeep = Color(0xFF000000)
    val surface = Color(0xFF12151B)
    val elevated = Color(0xFF1C2028)
    val pressed = Color(0xFF2A2F38)
    val border = Color(0xFF2A2F38)
    val borderStrong = Color(0xFF5C6370)
    val accent = Color(0xFF4C8DFF)
    val accentPressed = Color(0xFF2A6FF0)
    val accentOn = Color(0xFFFFFFFF)
    val text = Color(0xFFFFFFFF)
    val textDim = Color(0xFFA3A9B4)
    val textDisabled = Color(0xFF3F4550)

    // 등급 색: 피그마 미확정(AG-02). 임시값.
    val rare = Color(0xFF4C8DFF)
    val epic = Color(0xFFA06BFF)
    val legendary = Color(0xFFFFC247)

    /** 열쇠 · 스트릭 노랑 (피그마 color/game/key). */
    val gameKey = Color(0xFFFFD36B)

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

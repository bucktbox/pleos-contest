package com.safecargo.ui.screens

import androidx.compose.ui.graphics.toArgb
import com.safecargo.engine.DestCategory
import com.safecargo.engine.Destination
import com.safecargo.engine.Grade
import com.safecargo.engine.JudgeConfig
import com.safecargo.ui.theme.DarkPalette
import com.safecargo.ui.theme.LightPalette
import com.safecargo.ui.theme.SC
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 11/03 디자인 요청 반영분 — 등급 색 · 라이트 모드 · 목적지 화면 · 열쇠 게이지. */
class DesignTokensTest {
    @Test fun light_colors_match_cabin_light_config() {
        // 실내 조명(judge.json gradeLightColors = JudgeConfig 기본값)과 조명 실패 시 화면 테두리는 같은 색 — 레전더리는 보라 유지.
        val light = mapOf(Grade.RARE to 0x3DD9F0, Grade.EPIC to 0x4C8DFF, Grade.LEGENDARY to 0xB07CFF)
        assertEquals(light, JudgeConfig().gradeLightColors)
        Grade.entries.forEach { g -> assertEquals(g.name, light.getValue(g), lightColor(g).toArgb() and 0xFFFFFF) }
    }

    @Test fun screen_grade_colors_follow_figma_legendary_gold() {
        // 11/09 디자인 확정: 화면(언박싱 · 리워드)의 레전더리는 금색 = 피그마 color/game/key. 레어 · 에픽은 조명과 같은 색.
        assertEquals(0x3DD9F0, gradeColor(Grade.RARE).toArgb() and 0xFFFFFF)
        assertEquals(0x4C8DFF, gradeColor(Grade.EPIC).toArgb() and 0xFFFFFF)
        assertEquals(SC.gameKey, gradeColor(Grade.LEGENDARY))
        assertEquals(0xFFF5B700.toInt(), LightPalette.gameKey.toArgb())
    }

    @Test fun light_palette_1110_afternoon_values() {
        // 11/10 오후 피그마: 라이트만 바뀌고 다크는 그대로.
        assertEquals(0xFFF1F3F6.toInt(), LightPalette.elevated.toArgb())
        assertEquals(0xFF0E9AB8.toInt(), LightPalette.rare.toArgb())
        assertEquals(0xFFDD5A00.toInt(), LightPalette.warning.toArgb())
        assertEquals(LightPalette.text, LightPalette.gameKeyText)   // 라이트 금색 글자 → 검정
        assertEquals(0xFF1C2028.toInt(), DarkPalette.elevated.toArgb())
        assertEquals(0xFF3DD9F0.toInt(), DarkPalette.rare.toArgb())
        assertEquals(DarkPalette.gameKey, DarkPalette.gameKeyText)
        // 실내 조명 레어는 모드와 무관하게 judge.json 값.
        assertEquals(0x3DD9F0, SC.rareLight.toArgb() and 0xFFFFFF)
    }

    @Test fun light_palette_uses_figma_light_values() {
        assertEquals(0xFFF4F5F7.toInt(), LightPalette.bg.toArgb())
        assertEquals(0xFFFFFFFF.toInt(), LightPalette.surface.toArgb())
        assertEquals(0xFF090B0F.toInt(), LightPalette.text.toArgb())
        assertEquals(0xFF2A6FF0.toInt(), LightPalette.accent.toArgb())
        assertEquals(0xFFEEF4FF.toInt(), LightPalette.seatmapBody.toArgb())
        assertEquals(0xFFDCE7FF.toInt(), LightPalette.seatmapGlass.toArgb())
        // 두 팔레트는 필드 구성이 같고 값만 다르다.
        assertNotEquals(DarkPalette.bg, LightPalette.bg)
        assertEquals(DarkPalette, SC.palette)
    }

    @Test fun key_gauge_is_clamped_progress() {
        assertEquals(0f, keyProgress(0.0, 120.0), 0f)
        assertEquals(0.5f, keyProgress(60.0, 120.0), 1e-6f)
        assertEquals(1f, keyProgress(500.0, 120.0), 0f)
        assertEquals(0f, keyProgress(-5.0, 120.0), 0f)
        assertEquals(0f, keyProgress(30.0, 0.0), 0f)
    }

    private fun d(id: String, c: DestCategory?, cargo: String? = null) = Destination(id, "$id 예시", "주소", 0.0, 0.0, "r", c, cargo)

    @Test fun filter_keeps_catalog_order() {
        val all = listOf(d("a", DestCategory.CAFE), d("b", DestCategory.REST), d("c", DestCategory.CAFE), d("x", null))
        assertEquals(all, filterDestinations(all, null))
        assertEquals(listOf("a", "c"), filterDestinations(all, DestCategory.CAFE).map { it.id })
        assertEquals(emptyList<Destination>(), filterDestinations(all, DestCategory.MUNI))
    }

    @Test fun meta_line_falls_back_to_address() {
        // 11/10 오후: 「카페 ·」 머리말을 빼고 카고만. 카고가 없으면 주소.
        assertEquals("타르트 상자", metaLine(d("a", DestCategory.CAFE, "타르트 상자")))
        assertEquals("주소", metaLine(d("b", DestCategory.REST)))
        assertEquals("주소", metaLine(d("x", null)))
    }
}

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
    @Test fun grade_colors_follow_figma_and_match_light_config() {
        // 피그마 color/grade/* 가 정본. 실내 조명(judge.json gradeLightColors = JudgeConfig 기본값)과 같은 색이어야 한다.
        val figma = mapOf(Grade.RARE to 0x3DD9F0, Grade.EPIC to 0x4C8DFF, Grade.LEGENDARY to 0xB07CFF)
        assertEquals(figma, JudgeConfig().gradeLightColors)
        Grade.entries.forEach { g -> assertEquals(g.name, figma.getValue(g), gradeColor(g).toArgb() and 0xFFFFFF) }
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
        assertEquals("카페 · 타르트 상자", metaLine(d("a", DestCategory.CAFE, "타르트 상자")))
        assertEquals("휴게소", metaLine(d("b", DestCategory.REST)))
        assertEquals("주소", metaLine(d("x", null)))
    }
}

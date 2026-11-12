package com.safecargo.data

import com.safecargo.engine.Cargo
import com.safecargo.engine.CargoKind
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockCatalogTest {
    private val cat = MockCatalogLoader.parse(File("src/main/assets/mock/catalog.json").readText())

    @Test fun asset_has_four_sponsors_and_rewards_resolve() {
        assertEquals(listOf("cafe", "rest", "muni", "roast"), cat.destinations.map { it.id })
        cat.destinations.forEach { assertNotNull(cat.reward(it.rewardId)) }
        assertNotNull(cat.reward("general"))
        assertNotNull(cat.reward("daily"))
        assertEquals(Cargo("daily", "오늘의 데일리 카고", CargoKind.DAILY), cat.daily)
        assertEquals(7, cat.rewards.size)
        assertEquals(RewardType.EMPTY, cat.reward(RewardIds.EMPTY)?.type)
        assertEquals(4, cat.coupons.size)
        assertTrue(cat.coupons.all { !it.usePlace.isNullOrBlank() && it.howTo.isNotEmpty() })
        // 목적지 화면 유형 필터 · 카고 이름 (피그마 SC-02) — 모든 목적지에 있어야 한다.
        assertTrue(cat.destinations.all { it.category != null && !it.cargoName.isNullOrBlank() && !it.cargoDesc.isNullOrBlank() })
        assertTrue(cat.destinations.all { "예시" in it.name })
    }
}

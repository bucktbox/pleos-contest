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

    @Test fun asset_has_three_sponsors_and_rewards_resolve() {
        assertEquals(listOf("muni", "rest", "cafe"), cat.destinations.map { it.id })
        cat.destinations.forEach { assertNotNull(cat.reward(it.rewardId)) }
        assertNotNull(cat.reward("general"))
        assertNotNull(cat.reward("daily"))
        assertEquals(Cargo("daily", "오늘의 데일리 카고", CargoKind.DAILY), cat.daily)
        assertEquals(5, cat.rewards.size)
        assertTrue(cat.destinations.all { "예시" in it.name })
    }
}

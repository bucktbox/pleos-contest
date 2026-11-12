package com.safecargo.data

import com.safecargo.engine.Grade
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardConfigTest {
    @Test fun asset_matches_defaults() {
        val reasons = mutableListOf<String>()
        val c = RewardConfigLoader.parse(File("src/main/assets/config/reward.json").readText()) { reasons += it }
        assertEquals("shipped reward.json must be valid", emptyList<String>(), reasons)
        assertEquals(RewardConfig(), c)
        assertFalse("시연 모드는 기본 꺼짐 (FR-DATA-005)", c.demoMode)
    }

    @Test fun prd_values() {
        val c = RewardConfig()
        assertEquals(RewardOdds(80, 20, 0), c.odds[Grade.LEGENDARY])
        assertEquals(RewardOdds(50, 35, 15), c.odds[Grade.EPIC])
        assertEquals(RewardOdds(20, 45, 35), c.odds[Grade.RARE])
        assertEquals(c.odds[Grade.EPIC], c.bonusOdds)
        assertEquals(listOf(3, 1, 2, 10, 3, 14), listOf(c.tokenEmpty, c.tokenPoint, c.tokenPayback, c.bonusCost, c.bonusPerDay, c.couponValidDays))
    }

    private fun fallsBack(json: String) {
        val reasons = mutableListOf<String>()
        assertEquals(RewardConfig(), RewardConfigLoader.parse(json) { reasons += it })
        assertEquals(1, reasons.size)
    }

    @Test fun odds_not_100_fall_back() = fallsBack("""{"bonusOdds":{"coupon":50,"point":50,"empty":10}}""")
    @Test fun missing_grade_falls_back() = fallsBack("""{"odds":{"RARE":{"coupon":20,"point":45,"empty":35}}}""")
    @Test fun zero_bonus_cost_falls_back() = fallsBack("""{"bonusCost":0}""")
    @Test fun malformed_falls_back() = fallsBack("""{"odds":""")

    @Test fun unknown_keys_tolerated_and_partial_override() {
        var called = false
        val c = RewardConfigLoader.parse("""{"tokenEmpty":5,"foo":1}""") { called = true }
        assertFalse(called)
        assertEquals(5, c.tokenEmpty)
        assertTrue(c.odds.isNotEmpty())
    }

    @Test fun odds_pick_boundaries() {
        val o = RewardOdds(20, 45, 35)
        assertEquals(Outcome.COUPON, o.pick(0)); assertEquals(Outcome.COUPON, o.pick(19))
        assertEquals(Outcome.POINT, o.pick(20)); assertEquals(Outcome.POINT, o.pick(64))
        assertEquals(Outcome.EMPTY, o.pick(65)); assertEquals(Outcome.EMPTY, o.pick(99))
        assertEquals(Outcome.POINT, RewardOdds(80, 20, 0).pick(99))
    }
}

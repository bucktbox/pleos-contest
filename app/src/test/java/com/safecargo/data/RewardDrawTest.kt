package com.safecargo.data

import com.safecargo.engine.Grade
import org.junit.Assert.assertEquals
import org.junit.Test

class RewardDrawTest {
    private val c = RewardConfig()

    @Test fun coupon_uses_verified_sponsor() {
        assertEquals("coupon-cafe", RewardDraw.trip(Grade.EPIC, "coupon-cafe", false, c, demo = false, roll = 0))
    }

    @Test fun coupon_without_sponsor_falls_back_to_point() {
        // EX-03: 도착 인증이 없으면 쿠폰 대신 일반 포인트, 데일리는 데일리 포인트.
        assertEquals(RewardIds.GENERAL, RewardDraw.trip(Grade.LEGENDARY, null, false, c, false, 0))
        assertEquals(RewardIds.DAILY, RewardDraw.trip(Grade.LEGENDARY, null, true, c, false, 0))
    }

    @Test fun empty_by_odds() {
        assertEquals(RewardIds.EMPTY, RewardDraw.trip(Grade.RARE, "coupon-cafe", false, c, false, 99))
        assertEquals(RewardIds.GENERAL, RewardDraw.trip(Grade.RARE, "coupon-cafe", false, c, false, 50))
    }

    @Test fun legendary_is_never_empty() {
        (0..99).forEach { assertEquals(false, RewardDraw.trip(Grade.LEGENDARY, "c", false, c, false, it) == RewardIds.EMPTY) }
    }

    @Test fun demo_forces_rare_empty_and_epic_coupon() {
        (0..99).forEach {
            assertEquals(RewardIds.EMPTY, RewardDraw.trip(Grade.RARE, "coupon-cafe", false, c, demo = true, roll = it))
            assertEquals("coupon-cafe", RewardDraw.trip(Grade.EPIC, "coupon-cafe", false, c, demo = true, roll = it))
        }
    }

    @Test fun bonus_picks_catalog_coupon() {
        val coupons = listOf(Reward("a", "A", RewardType.COUPON, "1"), Reward("b", "B", RewardType.COUPON, "1"))
        assertEquals("b", RewardDraw.bonus(coupons, c, roll = 0, pickRoll = 1))
        assertEquals(RewardIds.GENERAL, RewardDraw.bonus(coupons, c, roll = 60, pickRoll = 0))
        assertEquals(RewardIds.EMPTY, RewardDraw.bonus(coupons, c, roll = 90, pickRoll = 0))
        assertEquals(RewardIds.GENERAL, RewardDraw.bonus(emptyList(), c, roll = 0, pickRoll = 0))
        assertEquals("a", RewardDraw.bonus(coupons, c, roll = 99, pickRoll = 0, demo = true))
    }
}

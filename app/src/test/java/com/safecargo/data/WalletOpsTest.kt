package com.safecargo.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletOpsTest {
    private val c = RewardConfig()
    private val day = 20_000L
    private val coupons = listOf("c1", "c2", "c3", "c4").map { Reward(it, it, RewardType.COUPON, "1") }

    private fun coupon(src: String = "trip-1", d: Long = day) =
        WalletOps.grant(WalletData(), Grant(src, "c1", RewardType.COUPON), d, c)

    @Test fun coupon_grant_creates_available_item_valid_14_days() {
        val w = coupon()
        val item = w.items.single()
        assertEquals(CouponStatus.AVAILABLE, item.status)
        assertEquals(day + 14, item.expiresDay)
        assertEquals(0, w.balance)
        assertTrue(item.code.matches(Regex("SC-\\d{4}-\\d{4}")))
    }

    @Test fun grant_is_idempotent_by_source() {
        // 복구 · 재시작 때 SaveResult 가 다시 와도 한 번만.
        val w = coupon()
        assertSame(w, WalletOps.grant(w, Grant("trip-1", "c1", RewardType.COUPON), day, c))
        val e = WalletOps.grant(WalletData(), Grant("trip-2", RewardIds.EMPTY, RewardType.EMPTY), day, c)
        assertEquals(3, e.balance)
        assertSame(e, WalletOps.grant(e, Grant("trip-2", RewardIds.EMPTY, RewardType.EMPTY), day, c))
    }

    @Test fun point_and_empty_give_tokens_without_coupon_state() {
        var w = WalletOps.grant(WalletData(), Grant("t1", RewardIds.GENERAL, RewardType.POINT), day, c)
        w = WalletOps.grant(w, Grant("t2", RewardIds.EMPTY, RewardType.EMPTY), day, c)
        assertEquals(4, w.balance)
        assertTrue(w.items.isEmpty())
        assertEquals(listOf(LedgerReason.POINT, LedgerReason.EMPTY_BOX), w.ledger.map { it.reason })
    }

    @Test fun expire_moves_only_past_due_available() {
        val w = coupon()
        assertSame(w, WalletOps.expire(w, day + 14)) // 만료일 당일까지 사용 가능
        val x = WalletOps.expire(w, day + 15).items.single()
        assertEquals(CouponStatus.VOID, x.status)
        assertEquals(VoidReason.EXPIRED, x.voidReason)
    }

    @Test fun use_pays_back_once() {
        val w = coupon()
        val id = w.items.single().id
        val used = WalletOps.use(w, id, day + 1, c)
        assertEquals(CouponStatus.USED, used.item(id)!!.status)
        assertEquals(day + 1, used.item(id)!!.usedDay)
        assertEquals(2, used.balance)
        assertEquals(2, used.item(id)!!.paybackTokens)
        // 두 번 눌러도 한 번 · 사용 완료는 되돌릴 수 없다.
        assertSame(used, WalletOps.use(used, id, day + 1, c))
        assertSame(used, WalletOps.use(used, "nope", day + 1, c))
    }

    @Test fun use_after_expiry_voids_instead() {
        val w = coupon()
        val id = w.items.single().id
        val r = WalletOps.use(w, id, day + 30, c)
        assertEquals(CouponStatus.VOID, r.item(id)!!.status)
        assertEquals(0, r.balance)
    }

    @Test fun void_cannot_be_used() {
        val w = WalletOps.expire(coupon(), day + 20)
        val id = w.items.single().id
        assertSame(w, WalletOps.use(w, id, day + 20, c))
    }

    private fun withTokens(n: Int) = WalletData(ledger = listOf(LedgerEntry(day, LedgerReason.POINT, n, "seed")))

    @Test fun bonus_gate() {
        assertEquals(BonusGate.NeedTokens(3), WalletOps.bonusGate(withTokens(7), day, c))
        assertEquals(BonusGate.Open, WalletOps.bonusGate(withTokens(10), day, c))
        val capped = withTokens(100).copy(bonusDay = day, bonusCount = 3)
        assertEquals(BonusGate.DailyLimit, WalletOps.bonusGate(capped, day, c))
        assertEquals(BonusGate.Open, WalletOps.bonusGate(capped, day + 1, c)) // 다음 날 초기화
    }

    @Test fun open_bonus_deducts_counts_and_grants_at_once() {
        val w = WalletOps.openBonus(withTokens(12), "b1", RewardIds.EMPTY, RewardType.EMPTY, day, c)!!
        assertEquals(12 - 10 + 3, w.balance) // 비면 위로 +3
        assertEquals(1, WalletOps.todayBonusCount(w, day))
        assertNull(WalletOps.openBonus(w, "b1", RewardIds.EMPTY, RewardType.EMPTY, day, c)) // 같은 id 재시도
        val coupon = WalletOps.openBonus(withTokens(10), "b2", "c1", RewardType.COUPON, day, c)!!
        assertEquals(0, coupon.balance)
        assertEquals("c1", coupon.items.single().rewardId)
        assertNull(WalletOps.openBonus(withTokens(9), "b3", "c1", RewardType.COUPON, day, c))
    }

    @Test fun three_bonuses_a_day() {
        var w = withTokens(100)
        repeat(3) { w = WalletOps.openBonus(w, "b$it", RewardIds.GENERAL, RewardType.POINT, day, c)!! }
        assertNull(WalletOps.openBonus(w, "b4", RewardIds.GENERAL, RewardType.POINT, day, c))
        assertNotNull(WalletOps.openBonus(w, "b5", RewardIds.GENERAL, RewardType.POINT, day + 1, c))
    }

    @Test fun demo_seed_is_2_1_1_and_7_tokens_once() {
        val w = WalletOps.seedDemo(WalletData(), coupons, day, c)
        assertEquals(2, w.count(CouponStatus.AVAILABLE))
        assertEquals(1, w.count(CouponStatus.USED))
        assertEquals(1, w.count(CouponStatus.VOID))
        assertEquals(7, w.balance)
        assertTrue(w.seeded)
        assertSame(w, WalletOps.seedDemo(w, coupons, day, c))
        // 시연 쿠폰은 만료 처리에 걸리지 않는다 (오늘 기준).
        assertSame(w, WalletOps.expire(w, day))
    }

    @Test fun tabs_sort_by_spec() {
        val w = WalletOps.seedDemo(WalletData(), coupons, day, c)
        val av = w.tab(CouponStatus.AVAILABLE)
        assertTrue(av[0].expiresDay <= av[1].expiresDay) // 만료 임박 순
    }

    @Test fun store_round_trips() {
        val dir = Files.createTempDirectory("wallet").toFile()
        try {
            val store = WalletStore(dir)
            assertEquals(WalletData(), store.load())
            val w = WalletOps.seedDemo(WalletData(), coupons, day, c)
            store.save(w)
            assertEquals(w, WalletStore(dir).load())
            File(dir, "wallet.json").writeText("{broken")
            assertEquals(WalletData(), store.load())
        } finally { dir.deleteRecursively() }
    }
}

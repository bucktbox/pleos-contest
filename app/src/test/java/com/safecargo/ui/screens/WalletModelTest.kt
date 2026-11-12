package com.safecargo.ui.screens

import com.safecargo.data.BonusGate
import com.safecargo.data.CouponStatus
import com.safecargo.data.RewardConfig
import com.safecargo.data.VoidReason
import com.safecargo.data.WalletItem
import com.safecargo.engine.DrivingMotion
import com.safecargo.ui.shell.TokenChipInfo
import com.safecargo.ui.shell.tokenChipText
import com.safecargo.ui.shell.tokenChipVisible
import com.safecargo.engine.Phase
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletModelTest {
    private val today = LocalDate.of(2026, 11, 9).toEpochDay()
    private fun item(s: CouponStatus = CouponStatus.AVAILABLE, exp: Long = today + 3) =
        WalletItem("w", "c", "s", s, receivedDay = today - 1, expiresDay = exp, code = "SC-2611-0042")

    @Test fun days_left_and_soon() {
        assertEquals("3일 남음", daysLeftLabel(item(), today))
        assertEquals("오늘까지", daysLeftLabel(item(exp = today), today))
        assertTrue(expiringSoon(item(), today))
        assertFalse(expiringSoon(item(exp = today + 4), today))
        assertFalse(expiringSoon(item(CouponStatus.USED), today))
    }

    @Test fun detail_lines() {
        assertEquals("3일 남음 · 쓰면 토큰 +2", itemDetailLine(item(), today, 2))
        val used = item(CouponStatus.USED).copy(usedDay = today, paybackTokens = 2)
        assertEquals("11/9 사용 · 토큰 +2 받음", itemDetailLine(used, today, 2))
        val void = item(CouponStatus.VOID, exp = today - 1).copy(voidReason = VoidReason.EXPIRED)
        assertEquals("기간이 지났어요 · 11/8 까지", itemDetailLine(void, today, 2))
    }

    @Test fun empty_tabs_ex12() {
        assertEquals("카고를 배달하면 쿠폰이 들어와요", emptyTabText(CouponStatus.AVAILABLE))
        assertEquals("매장에서 쿠폰을 쓰면 여기에 남아요", emptyTabText(CouponStatus.USED))
        assertEquals("없음", emptyTabText(CouponStatus.VOID))
    }

    @Test fun token_texts() {
        assertEquals("3개 더 모으면 보너스 상자", tokenHint(7, 10))
        assertEquals("보너스 상자를 열 수 있어요", tokenHint(10, 10))
        assertEquals("3개 더 필요해요", bonusBlockedText(BonusGate.NeedTokens(3)))
        assertEquals("내일 다시 열 수 있어요", bonusBlockedText(BonusGate.DailyLimit))
        assertNull(bonusBlockedText(BonusGate.Open))
        assertEquals("−10", signed(-10)); assertEquals("+3", signed(3))
        assertEquals("사용 완료 · 토큰 +2 받았어요 (7 → 9)", usedMessage(2, 7))
        assertEquals("쿠폰 50% · 포인트 35% · 아쉬운 상자 15%", oddsLine(RewardConfig()))
    }

    @Test fun chip() {
        assertEquals("7 / 10", tokenChipText(TokenChipInfo(7, 10), wide = true))
        assertEquals("7", tokenChipText(TokenChipInfo(7, 10), wide = false))
        assertFalse(tokenChipVisible(Phase.ESCORTING))
        assertFalse(tokenChipVisible(Phase.PAUSED))
        assertTrue(tokenChipVisible(Phase.IDLE))
    }

    @Test fun no_fail_words_in_copy() {
        // SC-06 06: 「꽝」「실패」 금지.
        val all = listOf(EMPTY_BOX_TITLE, EMPTY_BOX_NEXT, TOKEN_GUIDE_TITLE, TOKEN_NOT_FOR_SALE) +
            tokenGuideSteps(3, 2, 10) + guideSteps(2, 10) + CouponStatus.entries.map(::emptyTabText)
        all.forEach { assertFalse(it, "꽝" in it || "실패" in it) }
    }

    @Test fun guide_has_five_steps() {
        assertEquals(5, guideSteps(2, 10).size)
    }

    @Test fun motion_frames_return_home_and_stay_bounded() {
        DrivingMotion.entries.forEach { m ->
            assertEquals(MotionFrame(0f, 0f), motionFrame(m, 1f))
            (0..100).map { motionFrame(m, it / 100f) }.forEach {
                assertTrue(kotlin.math.abs(it.deg) <= SHAKE_DEG + 1e-3f)
                assertTrue(kotlin.math.abs(it.dxDp) <= SHIFT_DP + 1e-3f)
                assertTrue(it.dyDp <= 0f && it.dyDp >= -LIFT_DP - 1e-3f)
            }
            assertTrue(motionDurationMs(m) <= 1000) // NFR-SAF-002: 1초 이내
        }
        assertTrue(motionFrame(DrivingMotion.ACCEL, 0.2f).dxDp < 0f) // 뒤로 밀림
        assertTrue(motionFrame(DrivingMotion.BRAKE, 0.2f).dxDp > 0f) // 앞으로 쏠림
        assertEquals(1000, motionDurationMs(DrivingMotion.SHAKE))
        assertEquals(900, motionDurationMs(DrivingMotion.BRAKE))
    }

    @Test fun motion_feels_physical() {
        // 급정거: 앞으로 쏠리며 앞 모서리를 딛고 기운 뒤, 반대로 살짝 되튕긴다.
        val brake = (0..100).map { motionFrame(DrivingMotion.BRAKE, it / 100f) }
        val peak = brake.maxBy { it.dxDp }
        assertEquals(SHIFT_DP, peak.dxDp, 0.5f)
        assertTrue(peak.deg > 0f && peak.pivotX > 0.5f)
        assertTrue("되튕김", brake.any { it.dxDp < -0.5f })
        // 급가속은 거울상.
        val accel = (0..100).map { motionFrame(DrivingMotion.ACCEL, it / 100f) }
        assertEquals(-SHIFT_DP, accel.minBy { it.dxDp }.dxDp, 0.5f)
        // 흔들림: 기운 쪽 모서리를 딛고(반대쪽이 들림) 점점 잦아든다.
        val shake = (0..100).map { motionFrame(DrivingMotion.SHAKE, it / 100f) }
        shake.filter { kotlin.math.abs(it.deg) > 1f }.forEach { assertEquals(it.deg > 0f, it.pivotX > 0.5f) }
        val early = shake.take(35).maxOf { kotlin.math.abs(it.deg) }
        val late = shake.drop(65).maxOf { kotlin.math.abs(it.deg) }
        assertTrue("$early > $late", early > late * 2)
    }

    @Test fun motion_queue_plays_kinds_in_order() {
        val q = MotionQueue()
        assertTrue(q.offer(DrivingMotion.SHAKE))
        assertFalse(q.offer(DrivingMotion.SHAKE)) // 같은 종류 — 버림
        assertFalse(q.offer(DrivingMotion.BRAKE))
        assertFalse(q.offer(DrivingMotion.BRAKE)) // 줄에 이미 있음
        assertFalse(q.offer(DrivingMotion.ACCEL))
        assertEquals(listOf(DrivingMotion.BRAKE, DrivingMotion.ACCEL), q.pending)
        assertEquals(DrivingMotion.BRAKE, q.finish())
        assertEquals(DrivingMotion.ACCEL, q.finish())
        assertNull(q.finish())
        assertTrue(q.offer(DrivingMotion.SHAKE))
    }
}

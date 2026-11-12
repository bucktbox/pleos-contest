package com.safecargo.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ChestOpenModelTest {
    private fun maxAbsRot(from: Float, to: Float): Float =
        (from.toInt()..to.toInt() step 5).maxOf { abs(chestPose(it.toFloat()).deg) }

    @Test fun restsAtEndAndOpened() {
        val p = chestPose(UNBOX_TOTAL_MS.toFloat())
        assertTrue(p.opened)
        assertEquals(0f, p.dy, 1e-4f); assertEquals(0f, p.deg, 1e-4f)
        assertEquals(1f, p.sx, 1e-3f); assertEquals(1f, p.sy, 1e-3f)
    }

    @Test fun stillFrameIsRestingClosedChest() {
        val p = chestPose(CHEST_STILL_MS)
        assertFalse(p.opened)
        assertEquals(0f, p.dy, 0f); assertEquals(0f, p.deg, 0f); assertEquals(1f, p.sx, 0f); assertEquals(1f, p.sy, 0f)
    }

    @Test fun closedUntilBurstThenOpen() {
        assertFalse(chestPose(BURST_MS - 1f).opened)
        assertTrue(chestPose(BURST_MS).opened)
    }

    @Test fun joltsGrowStronger() {
        // 듀오링고식 예열: 덜컹이 점점 커진다.
        val a = JOLT_STARTS.map { maxAbsRot(it, it + JOLT_MS) }
        assertTrue("$a", a[0] < a[1])
    }

    @Test fun matchesDesignerTuning() {
        // 11/11 디자이너 튜닝값 그대로.
        assertEquals(listOf(520f, 1050f), JOLT_STARTS)
        assertEquals(320f, JOLT_MS, 0f)                       // 묶음마다 2번 × 160ms
        assertEquals(1449f, WINDUP_MS, 0f)
        assertEquals(1829f, BURST_MS, 0f)
        assertEquals(3529, UNBOX_TOTAL_MS)
        assertEquals(BURST_MS + SETTLE_MS, UNBOX_TOTAL_MS.toFloat(), 0f)
        assertEquals(8.0f, joltAmpDeg(0), 1e-4f); assertEquals(10.4f, joltAmpDeg(1), 1e-4f)
        assertEquals(0.040f, joltHop(0), 1e-5f); assertEquals(0.052f, joltHop(1), 1e-5f)
        assertEquals(28, PARTICLE_COUNT)
        // 실제로 그 기울기까지 간다 (5ms 샘플이라 살짝 모자랄 수 있음).
        assertEquals(8.0f, maxAbsRot(JOLT_STARTS[0], JOLT_STARTS[0] + JOLT_MS), 0.3f)
        assertEquals(10.4f, maxAbsRot(JOLT_STARTS[1], JOLT_STARTS[1] + JOLT_MS), 0.3f)
    }

    @Test fun eachBurstShakesTwiceBothWays() {
        // 묶음 하나 안에서 좌우로 두 번 — 양쪽 기울기가 모두 나온다.
        val degs = (JOLT_STARTS[0].toInt() until (JOLT_STARTS[0] + JOLT_MS).toInt() step 5).map { chestPose(it.toFloat()).deg }
        assertTrue(degs.any { it > 7f } && degs.any { it < -7f })
        val hops = (0 until 2).map { k -> chestPose(JOLT_STARTS[0] + 80f + k * 160f).dy }
        assertTrue("$hops", hops.all { it < -0.035f })        // 흔들림마다 들썩
    }

    @Test fun calmBetweenJolts() {
        val gap = JOLT_STARTS[0] + JOLT_MS + 10f
        val p = chestPose(gap)
        assertEquals(0f, p.deg, 0.01f); assertEquals(0f, p.dy, 0.01f)
    }

    @Test fun windupSquashesBeforeBurst() {
        val p = chestPose(BURST_MS - 20f)
        assertTrue("sy=${p.sy}", p.sy < 0.9f)
        assertTrue("sx=${p.sx}", p.sx > 1.05f)
        // 움츠림 끝 = 세로 -22% · 가로 +15%
        val end = chestPose(BURST_MS - 0.01f)
        assertEquals(0.78f, end.sy, 0.005f); assertEquals(1.15f, end.sx, 0.01f)
    }

    @Test fun popOvershootsThenSettles() {
        val peak = (BURST_MS.toInt()..(BURST_MS + 500).toInt() step 5).maxOf { chestPose(it.toFloat()).sx }
        assertEquals(1.35f, peak, 0.01f)                     // 크기 +35% 넘침
        val top = (BURST_MS.toInt()..(BURST_MS + 600).toInt() step 5).minOf { chestPose(it.toFloat()).dy }
        assertEquals(-0.28f, top, 0.005f)                     // 위로 0.28 튀어 오름
        // 펑 순간 자세가 움츠림에서 끊김 없이 이어진다
        val before = chestPose(BURST_MS - 0.01f); val after = chestPose(BURST_MS)
        assertEquals(before.sy, after.sy, 0.005f); assertEquals(before.sx, after.sx, 0.01f)
    }

    @Test fun screenFlashesBrieflyAtBurst() {
        assertEquals(0f, screenFlash(BURST_MS - 1f), 0f)
        assertEquals(0.6f, screenFlash(BURST_MS), 1e-4f)
        assertTrue(screenFlash(BURST_MS + 90f) in 0.29f..0.31f)
        assertEquals(0f, screenFlash(BURST_MS + SCREEN_FLASH_MS), 0f)
    }

    @Test fun tapJumpsToNextJoltThenWindupThenFinish() {
        assertEquals(TapResult.Jump(JOLT_STARTS[0]), tapAt(100f))
        assertEquals(TapResult.Jump(JOLT_STARTS[1]), tapAt(JOLT_STARTS[0] + 50f))
        assertEquals(TapResult.Jump(WINDUP_MS), tapAt(JOLT_STARTS[1] + 10f))
        assertEquals(TapResult.Jump(BURST_MS), tapAt(WINDUP_MS + 10f))
        assertEquals(TapResult.Finish, tapAt(BURST_MS + 10f))
    }

    @Test fun particlesFlyOutThenFade() {
        assertTrue(particles(BURST_MS - 1f).isEmpty())
        val mid = particles(BURST_MS + 250f)
        assertEquals(PARTICLE_COUNT, mid.size)
        assertTrue(mid.all { it.y < 0f })               // 위로 튀어 오른다 (상자 중심 기준, 위가 음수)
        assertTrue(particles(UNBOX_TOTAL_MS.toFloat()).all { it.alpha == 0f })
        assertEquals(mid, particles(BURST_MS + 250f))     // 같은 시각이면 같은 그림 (난수 없음)
    }

    @Test fun flashRingExpandsAndFades() {
        assertEquals(0f, flashRing(BURST_MS - 1f).alpha, 0f)
        val a = flashRing(BURST_MS + 50f); val b = flashRing(BURST_MS + 300f)
        assertTrue(b.radius > a.radius && b.alpha < a.alpha)
    }
}

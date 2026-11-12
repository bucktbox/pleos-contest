package com.safecargo.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HarshDetectorTest {
    private val c = JudgeConfig()
    private val d = HarshDetector(c)

    @Test fun fast_steer_shakes() {
        assertNull(d.onSteer(0f, 0))
        assertNull(d.onSteer(10f, 1000)) // 10°/s
        assertEquals(DrivingMotion.SHAKE, d.onSteer(200f, 1500)) // 380°/s > 300
    }

    @Test fun same_kind_has_10s_cooldown() {
        d.onSteer(0f, 0)
        assertEquals(DrivingMotion.SHAKE, d.onSteer(200f, 500))
        assertNull(d.onSteer(0f, 1000))
        assertNull(d.onSteer(200f, 9_000)) // 빠르지만 쿨다운 안
        assertNull(d.onSteer(200f, 10_550))
        assertEquals(DrivingMotion.SHAKE, d.onSteer(0f, 10_600)) // 쿨다운 지남
    }

    @Test fun accel_over_one_second_window() {
        assertNull(d.onSpeed(0f, 0))
        assertNull(d.onSpeed(2f, 500)) // 창이 아직 1초가 안 됨
        assertEquals(DrivingMotion.ACCEL, d.onSpeed(3.2f, 1000)) // (3.2 - 0) / 1s
    }

    @Test fun mild_accel_is_quiet() {
        d.onSpeed(10f, 0)
        assertNull(d.onSpeed(12f, 1000))
        assertNull(d.onSpeed(14f, 2000))
    }

    @Test fun hard_brake() {
        d.onSpeed(15f, 0)
        d.onSpeed(14f, 500)
        assertEquals(DrivingMotion.BRAKE, d.onSpeed(11f, 1000)) // -4 m/s²
    }

    @Test fun onchange_gap_uses_last_value_before_window() {
        // 2초 동안 값이 안 바뀌다가(ONCHANGE) 갑자기 +2 — 창 시작의 속도는 직전 값 10, 2/1s = 2 < 3.
        d.onSpeed(10f, 0)
        assertNull(d.onSpeed(12f, 2000))
    }

    @Test fun kinds_cool_down_independently() {
        d.onSteer(0f, 0)
        assertEquals(DrivingMotion.SHAKE, d.onSteer(200f, 500))
        d.onSpeed(0f, 0)
        assertEquals(DrivingMotion.ACCEL, d.onSpeed(4f, 1000))
    }

    @Test fun reset_and_nan() {
        d.onSpeed(0f, 0)
        assertNull(d.onSpeed(Float.NaN, 1000))
        d.reset()
        assertNull(d.onSpeed(5f, 2000))
    }
}

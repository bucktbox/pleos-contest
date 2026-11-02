package com.safecargo.engine

import com.safecargo.engine.Grade.EPIC
import com.safecargo.engine.Grade.LEGENDARY
import com.safecargo.engine.Grade.RARE
import com.safecargo.engine.Seat.DRIVER
import com.safecargo.engine.Seat.REAR_LEFT
import com.safecargo.engine.TurnSignal.LEFT
import com.safecargo.engine.TurnSignal.OFF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JudgeEngineTest {
    private val c = JudgeConfig()
    private fun JudgeState.turn(sig: TurnSignal, t: Long) = JudgeEngine.onTurn(this, sig, t, c)
    private fun JudgeState.steer(d: Float, t: Long) = JudgeEngine.onSteer(this, d, t, c)

    @Test fun signal_then_same_direction_turn_gives_bonus() {
        val s = JudgeState().turn(LEFT, 0).steer(-90f, 1000)
        assertEquals(30.0, s.keys, 0.001); assertEquals(1, s.signalFirst); assertEquals(0, s.perfect)
    }
    @Test fun perfect_signal_after_1_5s_gives_1_5x() {
        val s = JudgeState().turn(LEFT, 0).steer(-90f, 1500)
        assertEquals(45.0, s.keys, 0.001); assertEquals(1, s.perfect)
    }
    @Test fun opposite_direction_counts_as_no_signal() {
        val s = JudgeState().turn(LEFT, 0).steer(120f, 1000)
        assertEquals(0.0, s.keys, 0.001); assertEquals(1, s.noSignalTurns); assertEquals(0, s.signalFirst)
    }
    @Test fun same_turn_judged_once_even_with_resend() {
        val s = JudgeState().turn(LEFT, 0).steer(-90f, 1000).steer(-180f, 2000).steer(-180f, 5000)
        assertEquals(1, s.signalFirst); assertEquals(30.0, s.keys, 0.001); assertEquals(2000L, s.lastAt)
    }
    @Test fun rearm_after_return_below_third_and_smooth_bonus() {
        val s = JudgeState().turn(LEFT, 0).steer(-90f, 1000).steer(-180f, 2000).steer(-20f, 3000)
            .turn(LEFT, 4000).steer(-90f, 5000)
        assertEquals(2, s.signalFirst); assertEquals(1, s.smoothTurns); assertEquals(65.0, s.keys, 0.001)
    }
    @Test fun grace_after_signal_off_still_counts() {
        val s = JudgeState().turn(LEFT, 0).turn(OFF, 1000).steer(-90f, 2500)
        assertEquals(1, s.signalFirst); assertEquals(45.0, s.keys, 0.001)
    }
    @Test fun signal_off_beyond_grace_is_no_signal() {
        val s = JudgeState().turn(LEFT, 0).turn(OFF, 1000).steer(-90f, 3500)
        assertEquals(0, s.signalFirst); assertEquals(1, s.noSignalTurns)
    }
    @Test fun bonus_capped_at_max_per_trip() {
        val cc = c.copy(smoothTurnKey = 0.0)
        var s = JudgeState(); var t = 0L
        repeat(6) {
            s = JudgeEngine.onTurn(s, LEFT, t, cc); s = JudgeEngine.onSteer(s, -90f, t + 1000, cc); s = JudgeEngine.onSteer(s, 0f, t + 2000, cc); t += 3000
        }
        assertEquals(6, s.signalFirst); assertEquals(150.0, s.keys, 0.001); assertEquals(5, s.signalBonusCount)
    }
    @Test fun harsh_steer_recorded_without_penalty_by_default() {
        val s = JudgeState().steer(0f, 0).steer(400f, 500).steer(0f, 1000)
        assertEquals(1, s.harshTurns); assertEquals(1, s.noSignalTurns); assertEquals(0.0, s.keys, 0.001)
    }
    @Test fun keys_never_negative_with_penalties() {
        val cc = c.copy(penaltyNoSignalTurn = 50.0, penaltyHarshSteer = 50.0)
        var s = JudgeEngine.onSteer(JudgeState(), 0f, 0, cc); s = JudgeEngine.onSteer(s, 400f, 500, cc); s = JudgeEngine.onSteer(s, 0f, 1000, cc)
        assertEquals(0.0, s.keys, 0.001)
    }
    @Test fun family_buff_with_one_passenger_when_belt_untrusted() {
        val seats = SeatSnapshot(occupied = mapOf(DRIVER to true, REAR_LEFT to true), belted = mapOf(DRIVER to false, REAR_LEFT to false))
        val o = JudgeEngine.finalize(JudgeState(keys = 30.0), seats, c)
        assertTrue(o.familyBuff); assertFalse(o.beltVerified); assertEquals(30, o.baseKeys); assertEquals(45, o.finalKeys)
    }
    @Test fun belt_trusted_requires_buckled_passengers() {
        val seats = SeatSnapshot(occupied = mapOf(DRIVER to true, REAR_LEFT to true), belted = mapOf(REAR_LEFT to false))
        assertFalse(JudgeEngine.finalize(JudgeState(keys = 30.0), seats, c.copy(beltSignalTrusted = true)).familyBuff)
    }
    @Test fun driver_alone_has_no_buff() {
        assertFalse(JudgeEngine.finalize(JudgeState(keys = 30.0), SeatSnapshot(occupied = mapOf(DRIVER to true)), c).familyBuff)
    }
    @Test fun family_buff_active_follows_min_passengers_and_belts() {
        val two = SeatSnapshot(
            occupied = mapOf(DRIVER to true, REAR_LEFT to true, Seat.PASSENGER to true, Seat.REAR_RIGHT to null),
            belted = mapOf(REAR_LEFT to true, Seat.PASSENGER to false),
        )
        assertTrue(JudgeEngine.familyBuffActive(two, c))
        assertTrue(JudgeEngine.familyBuffActive(two, c.copy(familyBuffMinPassengers = 2)))
        assertFalse(JudgeEngine.familyBuffActive(two, c.copy(familyBuffMinPassengers = 3)))
        // 벨트 신뢰 시 동승자 전원 착용이어야 한다 (PASSENGER 미착용)
        assertFalse(JudgeEngine.familyBuffActive(two, c.copy(beltSignalTrusted = true)))
        val fb = JudgeEngine.familyBuff(two, c)
        assertEquals(2, fb.passengers); assertFalse(fb.beltVerified)
        // finalize 는 같은 판정을 쓴다
        assertEquals(JudgeEngine.familyBuffActive(two, c.copy(familyBuffMinPassengers = 3)),
            JudgeEngine.finalize(JudgeState(keys = 10.0), two, c.copy(familyBuffMinPassengers = 3)).familyBuff)
    }
    @Test fun grade_thresholds() {
        assertEquals(RARE, JudgeEngine.gradeOf(59, c)); assertEquals(EPIC, JudgeEngine.gradeOf(60, c)); assertEquals(LEGENDARY, JudgeEngine.gradeOf(120, c))
    }
    @Test fun belt_not_verified_without_passengers() {
        val alone = SeatSnapshot(occupied = mapOf(DRIVER to true), belted = mapOf(DRIVER to true))
        val fb = JudgeEngine.familyBuff(alone, c.copy(beltSignalTrusted = true))
        assertEquals(0, fb.passengers); assertFalse(fb.beltVerified); assertFalse(fb.active)
        assertFalse(JudgeEngine.familyBuff(SeatSnapshot(), c.copy(beltSignalTrusted = true)).beltVerified)
        // 동승자 전원 착용이면 확인됨
        val buckled = SeatSnapshot(occupied = mapOf(DRIVER to true, REAR_LEFT to true), belted = mapOf(REAR_LEFT to true))
        assertTrue(JudgeEngine.familyBuff(buckled, c.copy(beltSignalTrusted = true)).beltVerified)
    }
    @Test fun grade_of_is_bounds_safe() {
        assertEquals(RARE, JudgeEngine.gradeOf(500, c.copy(gradeThresholds = emptyList())))
        assertEquals(EPIC, JudgeEngine.gradeOf(500, c.copy(gradeThresholds = listOf(60))))
        assertEquals(RARE, JudgeEngine.gradeOf(10, c.copy(gradeThresholds = listOf(60))))
    }
}

package com.safecargo.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

enum class Grade { RARE, EPIC, LEGENDARY }
enum class MomentKind { SIGNAL_FIRST, PERFECT_SIGNAL, NO_SIGNAL_TURN, SMOOTH_TURN, HARSH_TURN }

@Serializable
data class JudgeMoment(val atMs: Long, val kind: MomentKind)

@Serializable
data class JudgeState(
    val keys: Double = 0.0,
    val signalFirst: Int = 0,
    val perfect: Int = 0,
    val noSignalTurns: Int = 0,
    val smoothTurns: Int = 0,
    val harshTurns: Int = 0,
    val signalBonusCount: Int = 0,
    val moments: List<JudgeMoment> = emptyList(),
    // 추적용
    val signalDir: TurnSignal = TurnSignal.OFF,
    val signalOnAt: Long? = null,
    val signalOffAt: Long? = null,
    // 원시 조향 샘플 — 저장 금지. 복구 뒤엔 새 샘플부터 다시 잰다.
    @Transient val lastDeg: Float? = null,
    @Transient val lastAt: Long? = null,
    val inTurn: Boolean = false,
    val judged: Boolean = false,
    @Transient val peakRate: Float = 0f,
)

data class JudgeOutcome(
    val baseKeys: Int,
    val finalKeys: Int,
    val familyBuff: Boolean,
    val beltVerified: Boolean,
    val grade: Grade,
)

object JudgeEngine {
    fun onTurn(s: JudgeState, signal: TurnSignal, atMs: Long, c: JudgeConfig): JudgeState =
        if (signal == TurnSignal.OFF) s.copy(signalOffAt = atMs)
        else s.copy(signalDir = signal, signalOnAt = atMs, signalOffAt = null)

    fun onSteer(s: JudgeState, deg: Float, atMs: Long, c: JudgeConfig): JudgeState {
        if (deg == s.lastDeg) return s
        val prevDeg = s.lastDeg
        val prevAt = s.lastAt
        val rate = if (prevDeg == null || prevAt == null) 0f else {
            val dt = (atMs - prevAt) / 1000f
            if (dt <= 0f) 0f else abs(deg - prevDeg) / dt
        }
        var n = s.copy(lastDeg = deg, lastAt = atMs)
        val a = abs(deg)
        val third = c.turnAngleDeg / 3f

        if (!n.inTurn && a >= third) n = n.copy(inTurn = true, judged = false, peakRate = rate)
        else if (n.inTurn) n = n.copy(peakRate = max(n.peakRate, rate))

        if (n.inTurn && !n.judged && a >= c.turnAngleDeg) {
            n = judge(n, if (deg < 0) TurnSignal.LEFT else TurnSignal.RIGHT, atMs, c)
        } else if (n.inTurn && a < third) {
            if (n.judged) {
                n = if (n.peakRate <= c.harshSteerDegPerSec) {
                    n.copy(smoothTurns = n.smoothTurns + 1, keys = n.keys + c.smoothTurnKey)
                        .withMoment(atMs, MomentKind.SMOOTH_TURN, c)
                } else {
                    n.copy(harshTurns = n.harshTurns + 1, keys = n.keys - c.penaltyHarshSteer)
                        .withMoment(atMs, MomentKind.HARSH_TURN, c)
                }
            }
            n = n.copy(inTurn = false)
        }
        return n.copy(keys = n.keys.coerceAtLeast(0.0))
    }

    private fun judge(s: JudgeState, dir: TurnSignal, atMs: Long, c: JudgeConfig): JudgeState {
        val offAt = s.signalOffAt
        val recognized = s.signalDir == dir &&
            (offAt == null || atMs - offAt <= (c.signalGraceSec * 1000).toLong())
        val n = if (recognized) {
            val onAt = s.signalOnAt
            val perfect = onAt != null && atMs - onAt >= (c.perfectSignalSec * 1000).toLong()
            val canBonus = s.signalBonusCount < c.maxSignalBonusPerTrip
            s.copy(
                signalFirst = s.signalFirst + 1,
                perfect = if (perfect) s.perfect + 1 else s.perfect,
                keys = if (canBonus) s.keys + c.signalBonusKey * (if (perfect) c.perfectMultiplier else 1.0) else s.keys,
                signalBonusCount = if (canBonus) s.signalBonusCount + 1 else s.signalBonusCount,
            ).withMoment(atMs, if (perfect) MomentKind.PERFECT_SIGNAL else MomentKind.SIGNAL_FIRST, c)
        } else {
            s.copy(noSignalTurns = s.noSignalTurns + 1, keys = s.keys - c.penaltyNoSignalTurn)
                .withMoment(atMs, MomentKind.NO_SIGNAL_TURN, c)
        }
        return n.copy(judged = true)
    }

    private fun JudgeState.withMoment(atMs: Long, kind: MomentKind, c: JudgeConfig): JudgeState =
        copy(moments = (moments + JudgeMoment(atMs, kind)).takeLast(c.momentsMax))

    fun finalize(s: JudgeState, seats: SeatSnapshot, c: JudgeConfig): JudgeOutcome {
        val passengers = seats.occupied.filter { (seat, occ) -> seat != Seat.DRIVER && occ == true }.keys
        val beltVerified = c.beltSignalTrusted && passengers.all { seats.belted[it] == true }
        val buff = passengers.size >= c.familyBuffMinPassengers && (!c.beltSignalTrusted || beltVerified)
        val base = floor(s.keys).toInt()
        val final = floor(s.keys * (if (buff) c.familyBuffMultiplier else 1.0)).toInt()
        return JudgeOutcome(base, final, buff, beltVerified, gradeOf(final, c))
    }

    fun gradeOf(keys: Int, c: JudgeConfig): Grade = when {
        keys >= c.gradeThresholds[1] -> Grade.LEGENDARY
        keys >= c.gradeThresholds[0] -> Grade.EPIC
        else -> Grade.RARE
    }
}

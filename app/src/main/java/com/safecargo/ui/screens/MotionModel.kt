package com.safecargo.ui.screens

import com.safecargo.engine.DrivingMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/*
 * 거친 운전 모션 (PRD v1.1 FR-VH-014 · 화면정의서 SC-03 08). 카고 상자만 움직인다 —
 * 깜빡임 · 색 · 크기 · 소리 · 글자 없음. 대기 루프와 다른 층에서 돌고, 도는 동안 대기 루프는 멈춘다.
 */

/** 한 번 재생 길이 (ms). 1초 이내 (NFR-SAF-002, 11/11 완화). */
fun motionDurationMs(m: DrivingMotion): Int = when (m) {
    DrivingMotion.SHAKE -> 1000
    DrivingMotion.ACCEL, DrivingMotion.BRAKE -> 900
}

/**
 * 한 순간의 변환 — 가로 이동 [dxDp] · 위로 들림 [dyDp](음수가 위) · 회전 [deg].
 * 회전 중심은 상자 바닥선 위의 [pivotX] (0 = 뒤 · 왼쪽 모서리, 0.5 = 가운데, 1 = 앞 · 오른쪽 모서리).
 */
data class MotionFrame(val dxDp: Float, val deg: Float, val dyDp: Float = 0f, val pivotX: Float = 0.5f)

const val SHAKE_DEG = 12f
const val SHIFT_DP = 12f
/** 급가속 · 급정거 때 기우는 각도. */
const val TILT_DEG = 5f
/** 흔들림 때 기울어진 쪽 반대 모서리가 들리는 높이. */
const val LIFT_DP = 3f

private val PIf = PI.toFloat()

/** 감쇠 진동 — 0 에서 시작해 [cycles] 번 흔들리며 줄어든다. 끝에서 0. */
private fun ring(p: Float, cycles: Float, decay: Float): Float =
    sin(p * cycles * 2f * PIf) * exp(-decay * p) * (1f - p)

/**
 * [t] 는 0..1 진행. 짐칸에 실린 상자처럼 — 관성으로 밀리고, 모서리를 딛고 기울고, 살짝 되튕긴 뒤 멈춘다.
 * - 흔들림: 바닥 모서리를 번갈아 딛고 ±12° 좌우로 까딱, 점점 잦아든다 (3번 반).
 * - 급가속: 뒤로 12dp 밀리며 뒤 모서리를 딛고 뒤로 기운다 → 앞으로 살짝 되튕김 → 제자리.
 * - 급정거: 앞으로 12dp 쏠리며 앞 모서리를 딛고 앞으로 기운다 → 뒤로 살짝 되튕김 → 제자리.
 * 화면에서 「앞」 은 오른쪽(진행 방향). 끝(t=1)에는 반드시 제자리. 크기 · 색 · 깜빡임 없음.
 */
fun motionFrame(m: DrivingMotion, t: Float): MotionFrame {
    val p = t.coerceIn(0f, 1f)
    if (p >= 1f) return MotionFrame(0f, 0f)
    return when (m) {
        DrivingMotion.SHAKE -> {
            val deg = SHAKE_DEG * ring(p, 3.5f, 1.2f)
            // 오른쪽으로 기울면(+) 오른쪽 아래 모서리를 딛는다 — 반대쪽이 들린다.
            MotionFrame(0f, deg, -LIFT_DP * abs(deg) / SHAKE_DEG, if (deg >= 0f) 0.85f else 0.15f)
        }
        DrivingMotion.ACCEL -> lurch(p, dir = -1f)
        DrivingMotion.BRAKE -> lurch(p, dir = 1f)
    }
}

/** 관성 쏠림 — 빠르게 [dir] 쪽으로 밀렸다가(0~25%) 감쇠 스프링으로 돌아온다. */
private fun lurch(p: Float, dir: Float): MotionFrame {
    val x = if (p < 0.25f) {
        sin(p / 0.25f * PIf / 2f)
    } else {
        val q = (p - 0.25f) / 0.75f
        cos(q * 1.5f * 2f * PIf) * exp(-4f * q) * (1f - q)
    }
    val deg = dir * TILT_DEG * x
    // 앞으로 쏠리면 앞 모서리, 뒤로 밀리면 뒤 모서리를 딛는다.
    val pivot = if (dir * x >= 0f) 0.85f else 0.15f
    return MotionFrame(dir * SHIFT_DP * x, deg, 0f, pivot)
}

/** 애니메이션 줄이기 (Q-08) 때 0.8초 보이는 작은 정적 표시. */
fun motionMark(m: DrivingMotion): String = when (m) {
    DrivingMotion.SHAKE -> "〰"
    DrivingMotion.ACCEL -> "▶▶"
    DrivingMotion.BRAKE -> "■"
}

const val MOTION_MARK_MS = 900L

/**
 * 재생 대기열. 지금 재생 중인 것과 같은 종류는 버리고, 다른 종류는 하나씩 뒤에 줄 세운다(같은 종류가 이미 줄에 있으면 버림).
 * 쿨다운은 [com.safecargo.engine.HarshDetector] 가 맡는다.
 */
class MotionQueue {
    var playing: DrivingMotion? = null
        private set
    private val waiting = ArrayDeque<DrivingMotion>()

    val pending: List<DrivingMotion> get() = waiting.toList()

    /** 바로 재생을 시작해야 하면 true. */
    fun offer(m: DrivingMotion): Boolean {
        val p = playing
        if (p == null) {
            playing = m
            return true
        }
        if (m != p && m !in waiting) waiting.addLast(m)
        return false
    }

    /** 지금 것이 끝났다. 다음 재생할 것 (없으면 null). */
    fun finish(): DrivingMotion? {
        playing = waiting.removeFirstOrNull()
        return playing
    }

    fun clear() {
        playing = null
        waiting.clear()
    }
}

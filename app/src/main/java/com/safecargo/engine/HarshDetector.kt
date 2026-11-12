package com.safecargo.engine

import kotlin.math.abs

/** 거친 운전 이벤트 (PRD v1.1 FR-VH-014). 메모리에서 모션 재생에만 쓰고 저장하지 않는다. */
enum class DrivingMotion { SHAKE, ACCEL, BRAKE }

/**
 * 거친 운전 감지. 순수 상태 + 함수 — 호출은 메인 스레드에서만.
 *
 * - 흔들림: 조향 변화율 |Δθ/Δt| 가 제2열쇠 임계([JudgeConfig.harshSteerDegPerSec])를 넘는 순간.
 * - 급가속 · 급정거: [JudgeConfig.harshWindowMs] 창 평균 가속도 = (지금 속도 − 창 시작 시각의 속도) / 창 길이.
 *   차속 콜백은 값이 바뀔 때만 와서(ONCHANGE) 속도를 계단 함수로 본다 — 창 시작 시각의 속도는 그 직전 마지막 값.
 * - 같은 종류는 [JudgeConfig.motionCooldownMs] 안에 다시 내지 않는다.
 *
 * 급가속 · 급정거는 모션만 — Key · HP · 등급에 넣지 않는다 (호출하는 쪽이 판정 엔진과 섞지 않는다).
 */
class HarshDetector(private val c: JudgeConfig) {
    private data class Sample(val atMs: Long, val mps: Float)

    private var lastDeg: Float? = null
    private var lastSteerAt: Long? = null
    private val speeds = ArrayDeque<Sample>()
    private val lastEmit = mutableMapOf<DrivingMotion, Long>()

    fun reset() {
        lastDeg = null; lastSteerAt = null
        speeds.clear()
        lastEmit.clear()
    }

    fun onSteer(deg: Float, atMs: Long): DrivingMotion? {
        val pd = lastDeg; val pt = lastSteerAt
        lastDeg = deg; lastSteerAt = atMs
        if (pd == null || pt == null) return null
        val dt = (atMs - pt) / 1000f
        if (dt <= 0f) return null
        return if (abs(deg - pd) / dt > c.harshSteerDegPerSec) emit(DrivingMotion.SHAKE, atMs) else null
    }

    fun onSpeed(mps: Float, atMs: Long): DrivingMotion? {
        if (mps.isNaN()) return null
        speeds.addLast(Sample(atMs, mps))
        val start = atMs - c.harshWindowMs
        // 창 시작 시각 이전 샘플은 마지막 하나만 남긴다 (그 값이 창 시작 시각의 속도).
        while (speeds.size >= 2 && speeds[1].atMs <= start) speeds.removeFirst()
        val base = speeds.first()
        if (base.atMs > start) return null // 창 길이만큼 쌓이지 않았다 (출발 직후 · 첫 샘플).
        val accel = (mps - base.mps) / (c.harshWindowMs / 1000f)
        return when {
            accel >= c.harshAccelMps2 -> emit(DrivingMotion.ACCEL, atMs)
            accel <= c.harshBrakeMps2 -> emit(DrivingMotion.BRAKE, atMs)
            else -> null
        }
    }

    private fun emit(m: DrivingMotion, atMs: Long): DrivingMotion? {
        val prev = lastEmit[m]
        if (prev != null && atMs - prev < c.motionCooldownMs) return null
        lastEmit[m] = atMs
        return m
    }
}

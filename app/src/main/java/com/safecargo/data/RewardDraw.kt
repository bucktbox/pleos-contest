package com.safecargo.data

import com.safecargo.engine.Grade

/**
 * 보상 추첨 (PRD v1.1 4장 「보상 추첨」). 운전 실수는 꽝을 만들지 않는다 — 등급별 확률로만 (ISSUE-07 A).
 * [roll] 은 0..99 — 호출하는 쪽이 난수를 넣어 테스트에서 고정할 수 있다.
 */
object RewardDraw {
    fun outcome(grade: Grade, c: RewardConfig, demo: Boolean, roll: Int): Outcome {
        if (demo) c.demoForce[grade]?.let { return it }
        return (c.odds[grade] ?: RewardOdds(0, 100, 0)).pick(roll)
    }

    /**
     * 운행 결과 보상 id.
     * 쿠폰이 뽑혀도 도착 인증된 스폰서 쿠폰이 없으면(데일리 · 도착 인증 없음 — EX-03) 일반 포인트로 준다.
     */
    fun trip(grade: Grade, sponsorCouponId: String?, daily: Boolean, c: RewardConfig, demo: Boolean, roll: Int): String {
        val point = if (daily) RewardIds.DAILY else RewardIds.GENERAL
        return when (outcome(grade, c, demo, roll)) {
            Outcome.COUPON -> sponsorCouponId ?: point
            Outcome.POINT -> point
            Outcome.EMPTY -> RewardIds.EMPTY
        }
    }

    /** 보너스 상자 — 풀은 에픽과 같고 쿠폰은 카탈로그 쿠폰 중 하나. */
    fun bonus(coupons: List<Reward>, c: RewardConfig, roll: Int, pickRoll: Int, demo: Boolean = false): String =
        when (if (demo) c.demoBonus else c.bonusOdds.pick(roll)) {
            Outcome.COUPON -> coupons.getOrNull(Math.floorMod(pickRoll, coupons.size.coerceAtLeast(1)))?.id ?: RewardIds.GENERAL
            Outcome.POINT -> RewardIds.GENERAL
            Outcome.EMPTY -> RewardIds.EMPTY
        }
}

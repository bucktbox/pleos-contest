package com.safecargo.data

import com.safecargo.engine.Grade
import kotlinx.serialization.Serializable

/** 추첨 결과의 종류. */
enum class Outcome { COUPON, POINT, EMPTY }

/** 한 풀의 확률(%) — 합이 100. */
@Serializable
data class RewardOdds(val coupon: Int, val point: Int, val empty: Int) {
    val total: Int get() = coupon + point + empty

    /** [roll] 은 0..99. */
    fun pick(roll: Int): Outcome = when {
        roll < coupon -> Outcome.COUPON
        roll < coupon + point -> Outcome.POINT
        else -> Outcome.EMPTY
    }
}

/**
 * 보상 · 토큰 수치 (PRD v1.1 4장 · ISSUE-09 — 전부 잠정). 코드에 박지 않고 `config/reward.json` 으로 바꾼다 (FR-DATA-002).
 */
@Serializable
data class RewardConfig(
    val odds: Map<Grade, RewardOdds> = mapOf(
        Grade.LEGENDARY to RewardOdds(80, 20, 0),
        Grade.EPIC to RewardOdds(50, 35, 15),
        Grade.RARE to RewardOdds(20, 45, 35),
    ),
    /** 보너스 상자 풀 — 에픽과 같다. */
    val bonusOdds: RewardOdds = RewardOdds(50, 35, 15),
    val tokenEmpty: Int = 3,
    val tokenPoint: Int = 1,
    val tokenPayback: Int = 2,
    val bonusCost: Int = 10,
    val bonusPerDay: Int = 3,
    val couponValidDays: Int = 14,
    /** 사용 가이드 info box 를 펼친 채 보여주는 횟수 (FR-APP-015). */
    val guideExpandedTimes: Int = 3,
    /**
     * 시연 모드 (FR-DATA-005 · ISSUE-07 권고). 기본 꺼짐. 켜면 첫 실행 보상함에 쿠폰 4장 + 토큰 [demoTokens],
     * 그리고 등급별 추첨 결과를 [demoForce] 로 고정한다.
     */
    val demoMode: Boolean = false,
    val demoTokens: Int = 7,
    val demoForce: Map<Grade, Outcome> = mapOf(
        Grade.RARE to Outcome.EMPTY,
        Grade.EPIC to Outcome.COUPON,
        Grade.LEGENDARY to Outcome.COUPON,
    ),
    /** 시연 모드의 보너스 상자 결과 — S4 5단계 「결과가 보상함으로 들어간다」 가 매번 보이게 쿠폰. */
    val demoBonus: Outcome = Outcome.COUPON,
)

object RewardConfigLoader {
    /** 읽을 수 없거나 [problems] 가 있으면 [onInvalid] 로 이유(값 없이)를 알리고 기본값. */
    fun parse(json: String, onInvalid: (String) -> Unit = {}): RewardConfig {
        val c = try {
            SafeCargoJson.decodeFromString(RewardConfig.serializer(), json)
        } catch (e: IllegalArgumentException) {
            onInvalid("reward.json unreadable (${e.javaClass.simpleName}) — defaults")
            return RewardConfig()
        }
        val bad = problems(c)
        if (bad.isEmpty()) return c
        onInvalid("reward.json invalid: ${bad.joinToString()} — defaults")
        return RewardConfig()
    }

    fun problems(c: RewardConfig): List<String> = buildList {
        if (!Grade.entries.all { it in c.odds }) add("odds.missing")
        val pools = c.odds.values + c.bonusOdds
        if (pools.any { it.total != 100 }) add("odds.sum")
        if (pools.any { it.coupon < 0 || it.point < 0 || it.empty < 0 }) add("odds.negative")
        if (c.tokenEmpty < 0 || c.tokenPoint < 0 || c.tokenPayback < 0) add("token.negative")
        if (c.bonusCost <= 0) add("bonusCost")
        if (c.bonusPerDay < 0) add("bonusPerDay")
        if (c.couponValidDays <= 0) add("couponValidDays")
    }
}

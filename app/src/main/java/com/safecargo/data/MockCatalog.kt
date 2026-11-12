package com.safecargo.data

import com.safecargo.engine.Cargo
import com.safecargo.engine.Destination
import kotlinx.serialization.Serializable

/** EMPTY = 「아쉬운 상자」(꽝). 상자는 항상 열리고, 비어 있는 것은 내용물이다 (PRD v1.1 D-05 · ISSUE-07 A). */
enum class RewardType { COUPON, POINT, BADGE, EMPTY }

@Serializable
data class Reward(
    val id: String,
    val title: String,
    val type: RewardType,
    val amountLabel: String,
    val partnerLabel: String? = null,
    /** 쿠폰 사용처 (예: 「매장 카운터」) — 쿠폰 상세 사용 조건 (PRD 5.1 v1.1). */
    val usePlace: String? = null,
    /** 함께 쓸 수 없는 조건 등 한 줄. */
    val condition: String? = null,
    /** 이 쿠폰 쓰는 법 (예시) — 쿠폰 상세의 info box 가 공용 5단계 대신 이 문구를 쓴다 (SD-12). */
    val howTo: List<String> = emptyList(),
)

@Serializable
data class MockCatalog(
    val destinations: List<Destination>,
    val daily: Cargo,
    val rewards: List<Reward>,
) {
    fun reward(id: String): Reward? = rewards.firstOrNull { it.id == id }

    /** 보너스 상자 · 시연 초기값이 고르는 쿠폰 풀 — 카탈로그의 쿠폰 전부. */
    val coupons: List<Reward> get() = rewards.filter { it.type == RewardType.COUPON }
}

object MockCatalogLoader {
    fun parse(json: String): MockCatalog = SafeCargoJson.decodeFromString(MockCatalog.serializer(), json)
}

/** 카탈로그에 꼭 있어야 하는 보상 id. */
object RewardIds {
    const val GENERAL = "general"
    const val DAILY = "daily"
    const val EMPTY = "empty"
}

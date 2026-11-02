package com.safecargo.data

import com.safecargo.engine.Cargo
import com.safecargo.engine.Destination
import kotlinx.serialization.Serializable

enum class RewardType { COUPON, POINT, BADGE }

@Serializable
data class Reward(
    val id: String,
    val title: String,
    val type: RewardType,
    val amountLabel: String,
    val partnerLabel: String? = null,
)

@Serializable
data class MockCatalog(
    val destinations: List<Destination>,
    val daily: Cargo,
    val rewards: List<Reward>,
) {
    fun reward(id: String): Reward? = rewards.firstOrNull { it.id == id }
}

object MockCatalogLoader {
    fun parse(json: String): MockCatalog = SafeCargoJson.decodeFromString(MockCatalog.serializer(), json)
}

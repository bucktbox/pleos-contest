package com.safecargo.engine

import kotlinx.serialization.Serializable

@Serializable
data class JudgeConfig(
    val drivingAudio: Boolean = false,
    val drivingScreen: String = "static",
    val penaltyNoSignalTurn: Double = 0.0,
    val penaltyHarshSteer: Double = 0.0,
    val signalBonusKey: Double = 30.0,
    val perfectSignalSec: Double = 1.5,
    val perfectMultiplier: Double = 1.5,
    val turnAngleDeg: Float = 90f,
    val harshSteerDegPerSec: Float = 300f,
    val smoothTurnKey: Double = 5.0,
    val maxSignalBonusPerTrip: Int = 5,
    val signalGraceSec: Double = 2.0,
    val familyBuffMinPassengers: Int = 1,
    val familyBuffMultiplier: Double = 1.5,
    val beltSignalTrusted: Boolean = false,
    val gradeThresholds: List<Int> = listOf(60, 120),
    val gradeLightColors: Map<Grade, Int> = mapOf(Grade.RARE to 0x00E5FF, Grade.EPIC to 0xAA00FF, Grade.LEGENDARY to 0xFFC400),
    val ambientFlashMs: Long = 3000L,
    val rankingEnabled: Boolean = false,
    val streakDays: Int = 5,
    val streakName: String = "무사 배송",
    val arrivalRadiusM: Double = 300.0,
    val locationMaxAgeSec: Int = 60,
    val steerSign: Float = -1f,
    /**
     * Vehicle SDK 2.3.2 의 착석 Boolean 은 raw `VehicleSeatOccupancyState` 를 `== 1` 로 바꾼다
     * (UNKNOWN=0 · VACANT=1 · OCCUPIED=2) → true 가 「비어 있음」 이다. true 면 앱이 뒤집어 쓴다.
     * 한계: SDK Boolean 으로는 UNKNOWN(0) 과 OCCUPIED(2) 를 구분할 수 없어, 뒤집으면 UNKNOWN 이 「착석」 으로 읽힌다.
     * SDK 가 고쳐진 실차에서는 false 로 둔다.
     */
    val seatOccupancyInverted: Boolean = true,
    val momentsMax: Int = 12,
)

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
    val momentsMax: Int = 12,
)

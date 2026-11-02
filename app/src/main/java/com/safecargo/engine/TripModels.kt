package com.safecargo.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

enum class CargoKind { SPONSOR, DAILY }

@Serializable
data class Destination(
    val id: String, val name: String, val address: String,
    val lat: Double, val lng: Double, val rewardId: String,
)

@Serializable
data class Cargo(val id: String, val name: String, val kind: CargoKind, val destination: Destination? = null)

enum class Phase { IDLE, LOADED, ESCORTING, PAUSED, OPENING }

@Serializable
data class TripSummary(
    val signalFirst: Int, val noSignalTurns: Int, val perfect: Int, val smoothTurns: Int,
    val moments: List<JudgeMoment>,
)

@Serializable
data class TripResult(
    val id: String, val cargoName: String, val destinationName: String?, val grade: Grade,
    val keys: Int, val familyBuff: Boolean, val beltVerified: Boolean, val arrivalVerified: Boolean,
    val rewardId: String, val epochDay: Long, val summary: TripSummary,
)

@Serializable
data class TripState(
    val phase: Phase = Phase.IDLE,
    val cargo: Cargo? = null,
    val judge: JudgeState = JudgeState(),
    val seats: SeatSnapshot = SeatSnapshot(),
    val checkingArrival: Boolean = false,
    val openAfterLocation: Boolean = false,
    val arrivalVerified: Boolean = false,
    val autoOpenPending: Boolean = false,
    val result: TripResult? = null,
    val revealed: Boolean = false,
    val lastResult: TripResult? = null,
    @Transient val gear: Gear = Gear.UNKNOWN,
    @Transient val uxRestricted: Boolean = true,
    @Transient val connected: Boolean = false,
) {
    val canArrive: Boolean get() = phase == Phase.PAUSED && gear == Gear.PARK && !uxRestricted && !checkingArrival
}

data class GeoFix(val lat: Double, val lng: Double, val ageMs: Long)

sealed interface TripEvent {
    data class Signal(val signal: VehicleSignal) : TripEvent
    data class SelectDestination(val destination: Destination) : TripEvent
    data class LocationResolved(val fix: GeoFix?) : TripEvent
    data object ArriveConfirmed : TripEvent
    data object RevealFinished : TripEvent
    data object CloseResult : TripEvent
    data class Restore(val saved: TripState) : TripEvent
    data object Reset : TripEvent
}

sealed interface TripEffect {
    data object RequestLocation : TripEffect
    data class RequestRoute(val destination: Destination) : TripEffect
    data class FlashLights(val grade: Grade) : TripEffect
    data object RestoreLights : TripEffect
    data object AnnounceArrival : TripEffect
    data object StopSpeech : TripEffect
    data class PersistActive(val state: TripState) : TripEffect
    data object ClearActive : TripEffect
    data class SaveResult(val result: TripResult) : TripEffect
}

data class Step(val state: TripState, val effects: List<TripEffect> = emptyList())

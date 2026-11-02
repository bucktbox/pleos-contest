package com.safecargo.engine

import kotlinx.serialization.Serializable

enum class Gear { PARK, DRIVE, REVERSE, NEUTRAL, UNKNOWN }
enum class TurnSignal { LEFT, RIGHT, OFF }
enum class Seat { DRIVER, PASSENGER, REAR_LEFT, REAR_RIGHT }

@Serializable
data class SeatSnapshot(
    val occupied: Map<Seat, Boolean?> = emptyMap(),
    val belted: Map<Seat, Boolean?> = emptyMap(),
)

sealed interface VehicleSignal {
    val atMs: Long

    data class GearChanged(val gear: Gear, override val atMs: Long) : VehicleSignal
    data class TurnChanged(val signal: TurnSignal, override val atMs: Long) : VehicleSignal
    /** deg: 보정 후 값 (음수 = 왼쪽) */
    data class SteerChanged(val deg: Float, override val atMs: Long) : VehicleSignal
    data class SeatsChanged(val seats: SeatSnapshot, override val atMs: Long) : VehicleSignal
    data class UxChanged(val restricted: Boolean, override val atMs: Long) : VehicleSignal
    data class Disconnected(override val atMs: Long) : VehicleSignal
}

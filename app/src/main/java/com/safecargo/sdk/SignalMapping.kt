package com.safecargo.sdk

import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import com.safecargo.engine.Gear
import com.safecargo.engine.Seat
import com.safecargo.engine.TurnSignal

// SDK 에 의존하지 않는 순수 매핑. enum 이름 문자열로 받아 JVM 테스트가 가능하다.

internal fun mapGearName(name: String?): Gear = when (name) {
    "GEAR_PARK" -> Gear.PARK
    "GEAR_DRIVE", "GEAR_1", "GEAR_2", "GEAR_3", "GEAR_4", "GEAR_5", "GEAR_6", "GEAR_7", "GEAR_8", "GEAR_9" -> Gear.DRIVE
    "GEAR_REVERSE" -> Gear.REVERSE
    "GEAR_NEUTRAL" -> Gear.NEUTRAL
    else -> Gear.UNKNOWN
}

internal fun mapTurnName(name: String?): TurnSignal = when (name) {
    "LEFT" -> TurnSignal.LEFT
    "RIGHT" -> TurnSignal.RIGHT
    else -> TurnSignal.OFF
}

internal fun mapSeatName(name: String?): Seat? = when (name) {
    "ROW_1_LEFT" -> Seat.DRIVER
    "ROW_1_RIGHT" -> Seat.PASSENGER
    "ROW_2_LEFT" -> Seat.REAR_LEFT
    "ROW_2_RIGHT" -> Seat.REAR_RIGHT
    else -> null
}

/**
 * SDK 착석 Boolean → 앱의 「착석」. [inverted] 면 뒤집는다(SDK 2.3.2 는 true = VACANT). null 은 그대로 null.
 * 한계: SDK 가 UNKNOWN(0) 도 false 로 주므로, 뒤집으면 UNKNOWN 이 착석으로 읽힌다. → JudgeConfig.seatOccupancyInverted
 */
internal fun occupiedFromSdk(v: Boolean?, inverted: Boolean): Boolean? = if (inverted) v?.not() else v

/** raw 가 null 이면 버린다(null 반환). */
internal fun steerDeg(raw: Float?, steerSign: Float): Float? = raw?.let { it * steerSign }

internal fun mapGear(g: VehicleGear?): Gear = mapGearName(g?.name)
internal fun mapTurn(t: TurnSignalValue?): TurnSignal = mapTurnName(t?.name)
internal fun mapSeat(s: SeatValue): Seat? = mapSeatName(s.name)

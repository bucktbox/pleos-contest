package com.safecargo.sdk

import com.safecargo.engine.Gear
import com.safecargo.engine.Seat
import com.safecargo.engine.TurnSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignalMappingTest {
    @Test fun gear() {
        assertEquals(Gear.PARK, mapGearName("GEAR_PARK"))
        assertEquals(Gear.DRIVE, mapGearName("GEAR_DRIVE"))
        assertEquals(Gear.DRIVE, mapGearName("GEAR_3"))
        assertEquals(Gear.REVERSE, mapGearName("GEAR_REVERSE"))
        assertEquals(Gear.NEUTRAL, mapGearName("GEAR_NEUTRAL"))
        assertEquals(Gear.UNKNOWN, mapGearName(null))
        assertEquals(Gear.UNKNOWN, mapGearName("GEAR_SPORT"))
    }

    @Test fun turn() {
        assertEquals(TurnSignal.LEFT, mapTurnName("LEFT"))
        assertEquals(TurnSignal.RIGHT, mapTurnName("RIGHT"))
        assertEquals(TurnSignal.OFF, mapTurnName("OFF"))
        assertEquals(TurnSignal.OFF, mapTurnName(null))
    }

    @Test fun seat() {
        assertEquals(Seat.DRIVER, mapSeatName("ROW_1_LEFT"))
        assertEquals(Seat.REAR_RIGHT, mapSeatName("ROW_2_RIGHT"))
        assertNull(mapSeatName("ROW_3_LEFT"))
    }

    @Test fun steerSign() {
        assertEquals(-90f, steerDeg(90f, -1f)!!, 0f)
        assertEquals(30f, steerDeg(30f, 1f)!!, 0f)
        assertNull(steerDeg(null, -1f))
    }

    @Test fun occupancy_inverted() {
        assertEquals(false, occupiedFromSdk(true, inverted = true))
        assertEquals(true, occupiedFromSdk(false, inverted = true))
        assertNull(occupiedFromSdk(null, inverted = true))
    }

    @Test fun occupancy_passthrough() {
        assertEquals(true, occupiedFromSdk(true, inverted = false))
        assertEquals(false, occupiedFromSdk(false, inverted = false))
        assertNull(occupiedFromSdk(null, inverted = false))
    }
}

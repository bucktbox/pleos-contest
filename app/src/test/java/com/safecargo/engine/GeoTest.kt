package com.safecargo.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    @Test
    fun distance_seoul_cityhall_to_gwanghwamun() {
        assertEquals(1040.0, distanceM(37.5665, 126.9780, 37.5759, 126.9769), 20.0)
    }
}

package com.safecargo.ui.shell

import com.safecargo.ui.shell.HeightClass.*
import com.safecargo.ui.shell.Orientation.*
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowClassTest {
    @Test
    fun all_twelve_windows() {
        val cases = listOf(
            Triple(1700, 1184, WindowClass(L, R)), Triple(840, 1184, WindowClass(P, R)),
            Triple(1360, 948, WindowClass(L, R)), Triple(672, 948, WindowClass(P, R)),
            Triple(1274, 855, WindowClass(L, S)), Triple(628, 855, WindowClass(P, S)),
            Triple(1640, 810, WindowClass(L, S)), Triple(800, 810, WindowClass(P, S)),
            Triple(1640, 1400, WindowClass(L, T)), Triple(800, 1400, WindowClass(P, T)),
            Triple(1256, 800, WindowClass(L, S)), Triple(648, 800, WindowClass(P, S)),
        )
        for ((w, h, e) in cases) assertEquals("${w}x$h", e, windowClassOf(w, h))
    }

    @Test
    fun boundaries() {
        assertEquals(WindowClass(L, R), windowClassOf(960, 900))
        assertEquals(WindowClass(P, S), windowClassOf(959, 899))
        assertEquals(WindowClass(L, T), windowClassOf(1000, 1200))
    }
}

package com.safecargo.data

import com.safecargo.engine.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TripStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun result(id: String = "t1") = TripResult(
        id, "c", "d", Grade.EPIC, 100, false, false, true, "general", 20000L,
        TripSummary(1, 0, 0, 0, emptyList()),
    )

    @Test fun save_load_active_roundtrip() {
        val store = TripStore(tmp.root)
        val cargo = Cargo("daily", "오늘의 데일리 카고", CargoKind.DAILY)
        val s = TripState(
            phase = Phase.PAUSED, cargo = cargo, judge = JudgeState(keys = 12.0, signalFirst = 2),
            gear = Gear.PARK, uxRestricted = false, connected = true,
        )
        store.saveActive(s)
        val l = store.loadActive()!!
        assertEquals(Phase.PAUSED, l.phase)
        assertEquals(cargo, l.cargo)
        assertEquals(s.judge, l.judge)
        assertEquals(Gear.UNKNOWN, l.gear)
        store.clearActive()
        assertNull(store.loadActive())
    }

    @Test fun append_same_result_twice_keeps_one() {
        val store = TripStore(tmp.root)
        assertTrue(store.appendResult(result()))
        assertFalse(store.appendResult(result()))
        assertEquals(1, store.results().size)
        assertTrue(store.appendResult(result("t2")))
        assertEquals(2, store.results().size)
        store.clearAll()
        assertTrue(store.results().isEmpty())
    }

    @Test fun corrupt_files_read_as_empty() {
        val store = TripStore(tmp.root)
        File(tmp.root, "active_trip.json").writeText("{")
        File(tmp.root, "trips.json").writeText("{")
        assertNull(store.loadActive())
        assertTrue(store.results().isEmpty())
    }
}

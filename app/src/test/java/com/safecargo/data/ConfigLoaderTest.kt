package com.safecargo.data

import com.safecargo.engine.JudgeConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigLoaderTest {
    @Test fun asset_matches_defaults() {
        assertEquals(JudgeConfig(), ConfigLoader.parse(File("src/main/assets/config/judge.json").readText()))
    }

    @Test fun unknown_and_missing_keys_tolerated() {
        val c = ConfigLoader.parse("""{"signalBonusKey":40,"foo":1}""")
        assertEquals(40.0, c.signalBonusKey, 0.0)
        assertEquals(90f, c.turnAngleDeg, 0f)
    }

    private fun fallsBack(json: String) {
        val reasons = mutableListOf<String>()
        assertEquals(JudgeConfig(), ConfigLoader.parse(json) { reasons += it })
        assertEquals(1, reasons.size)
    }

    @Test fun wrong_threshold_count_falls_back() = fallsBack("""{"gradeThresholds":[60]}""")
    @Test fun descending_thresholds_fall_back() = fallsBack("""{"gradeThresholds":[120,60]}""")
    @Test fun missing_grade_color_falls_back() = fallsBack("""{"gradeLightColors":{"RARE":1,"EPIC":2}}""")
    @Test fun non_positive_multiplier_falls_back() = fallsBack("""{"familyBuffMultiplier":0}""")
    @Test fun malformed_json_falls_back() = fallsBack("""{"gradeThresholds":""")

    @Test fun reason_has_no_values() {
        var reason = ""
        ConfigLoader.parse("""{"gradeThresholds":[777,555]}""") { reason = it }
        assertFalse(reason.contains("777")); assertTrue(reason.contains("gradeThresholds.order"))
    }

    @Test fun valid_config_reports_nothing() {
        var called = false
        ConfigLoader.parse("""{"gradeThresholds":[10,20]}""") { called = true }
        assertFalse(called)
    }
}

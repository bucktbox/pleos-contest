package com.safecargo.data

import com.safecargo.engine.JudgeConfig
import java.io.File
import org.junit.Assert.assertEquals
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
}

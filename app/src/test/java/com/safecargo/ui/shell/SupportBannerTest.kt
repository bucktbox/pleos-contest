package com.safecargo.ui.shell

import com.safecargo.engine.Phase
import com.safecargo.sdk.SupportStatus
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SupportBannerTest {
    private val unsupported = SupportStatus(turn = false)

    @Test fun hidden_while_escorting() {
        assertNull(supportBannerText(Phase.ESCORTING, unsupported))
    }

    @Test fun shown_in_other_phases() {
        Phase.entries.filter { it != Phase.ESCORTING }.forEach { assertNotNull(it.name, supportBannerText(it, unsupported)) }
    }

    @Test fun nothing_when_all_supported() {
        assertNull(supportBannerText(Phase.IDLE, SupportStatus()))
    }
}

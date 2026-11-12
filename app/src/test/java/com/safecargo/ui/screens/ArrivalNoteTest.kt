package com.safecargo.ui.screens

import com.safecargo.engine.ArrivalCheck
import com.safecargo.engine.Cargo
import com.safecargo.engine.CargoKind
import com.safecargo.engine.Phase
import com.safecargo.engine.TripState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrivalNoteTest {
    private val sponsor = TripState(phase = Phase.PAUSED, cargo = Cargo("c", "카고", CargoKind.SPONSOR))

    @Test fun out_of_range_only_with_fix() {
        assertEquals("목적지 근처가 아니에요 — 지금 열면 일반 리워드", arrivalNote(sponsor.copy(arrivalCheck = ArrivalCheck.OUT_OF_RANGE)))
    }

    @Test fun no_fix_says_no_verification() {
        assertEquals("도착 인증 없음 — 지금 열면 일반 리워드", arrivalNote(sponsor.copy(arrivalCheck = ArrivalCheck.NO_FIX)))
    }

    @Test fun silent_while_checking_verified_or_daily() {
        assertNull(arrivalNote(sponsor.copy(checkingArrival = true, arrivalCheck = ArrivalCheck.OUT_OF_RANGE)))
        assertNull(arrivalNote(sponsor.copy(arrivalVerified = true, arrivalCheck = ArrivalCheck.VERIFIED)))
        assertNull(arrivalNote(sponsor.copy(cargo = Cargo("d", "데일리", CargoKind.DAILY), arrivalCheck = ArrivalCheck.NO_FIX)))
    }
}

package com.safecargo.ui.screens

import com.safecargo.engine.Grade
import com.safecargo.engine.JudgeMoment
import com.safecargo.engine.MomentKind
import com.safecargo.engine.TripResult
import com.safecargo.engine.TripSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardModelTest {
    private val summary = TripSummary(signalFirst = 3, noSignalTurns = 1, perfect = 2, smoothTurns = 4, moments = emptyList())

    private fun r(
        rewardId: String = "coupon-cafe", dest: String? = "베이커리 카페 예시",
        arrival: Boolean = true, buff: Boolean = false, belt: Boolean = false,
    ) = TripResult(
        id = "t", cargoName = "c", destinationName = dest, grade = Grade.EPIC, keys = 90,
        familyBuff = buff, beltVerified = belt, arrivalVerified = arrival, rewardId = rewardId,
        epochDay = 0, summary = summary,
    )

    @Test fun summaryLineIsExact() {
        assertEquals("깜빡이 먼저 3회 · 퍼펙트 2회 · 부드러운 회전 4회", summaryLine(summary))
    }

    @Test fun summaryChipsShowOnlyNonZero() {
        val s = TripSummary(signalFirst = 2, noSignalTurns = 5, perfect = 0, smoothTurns = 1, moments = emptyList())
        assertEquals(listOf("깜빡이 먼저 2회", "부드러운 회전 1회"), summaryChips(s).map { it.text })
        assertTrue(summaryChips(TripSummary(0, 3, 0, 0, emptyList())).isEmpty())
    }

    @Test fun beltChipOnlyWhenAllBeltedVerified() {
        // 11/09 디자인 확정: 「모두 벨트」 칩은 맨 뒤, 벨트 신호로 전원 착용이 확인됐을 때만 (벨트라는 말은 확인됐을 때만).
        assertEquals(
            listOf("깜빡이 먼저 3회", "퍼펙트 2회", "부드러운 회전 4회", "모두 벨트"),
            summaryChips(summary, beltVerified = true).map { it.text },
        )
        assertFalse(summaryChips(summary, beltVerified = false).any { it.text.contains("벨트") })
        assertEquals(listOf("모두 벨트"), summaryChips(TripSummary(0, 0, 0, 0, emptyList()), beltVerified = true).map { it.text })
    }

    @Test fun lightNoteHiddenOnlyWhenLightsUnsupported() {
        // 11/10 디자인: 언박싱 「실내 조명이 3초 빛나요」. 조명 미지원 차에서는 빛난다고 말하지 않는다.
        assertEquals("실내 조명이 3초 빛나요", lightNote(true, 3000))
        assertEquals("실내 조명이 3초 빛나요", lightNote(null, 3000))
        assertNull(lightNote(false, 3000))
    }

    @Test fun gradeNames() {
        assertEquals(listOf("레어", "에픽", "레전더리"), Grade.entries.map(::gradeName))
    }

    @Test fun noArrivalOnlyForSponsorWithoutVerification() {
        assertTrue(showNoArrival(r(rewardId = "general", arrival = false)))
        assertFalse(showNoArrival(r(arrival = true)))
        assertFalse(showNoArrival(r(rewardId = "daily", dest = null, arrival = false)))
    }

    @Test fun buffUsesConfigMultiplierAndNeverSaysBeltUnlessVerified() {
        assertNull(buffLabel(r(buff = false), 1.5))
        assertEquals("패밀리 버프 ×1.5", buffLabel(r(buff = true), 1.5))
        assertEquals("패밀리 버프 ×2", buffLabel(r(buff = true), 2.0))
        assertFalse(buffLabel(r(buff = true, belt = false), 1.5)!!.contains("벨트"))
        assertTrue(buffLabel(r(buff = true, belt = true), 1.5)!!.contains("벨트"))
    }

    @Test fun replayIsChronological() {
        val m = listOf(JudgeMoment(300, MomentKind.SMOOTH_TURN), JudgeMoment(100, MomentKind.SIGNAL_FIRST), JudgeMoment(200, MomentKind.PERFECT_SIGNAL))
        assertEquals(listOf(100L, 200L, 300L), replayOrder(m).map { it.atMs })
    }

    @Test fun replayAndSummaryShowOnlyGoodMoves() {
        val m = listOf(
            JudgeMoment(100, MomentKind.NO_SIGNAL_TURN), JudgeMoment(200, MomentKind.SIGNAL_FIRST),
            JudgeMoment(300, MomentKind.HARSH_TURN), JudgeMoment(400, MomentKind.SMOOTH_TURN),
        )
        assertEquals(listOf(MomentKind.SIGNAL_FIRST, MomentKind.SMOOTH_TURN), replayOrder(m).map { it.kind })
        assertEquals(listOf("깜빡이 먼저", "퍼펙트", "부드러운 회전"), summaryItems(summary).map { it.label })
    }

    @Test fun highlightWalksEachMomentThenStops() {
        assertEquals(0, highlightIndex(0f, 3))
        assertEquals(1, highlightIndex(0.4f, 3))
        assertEquals(2, highlightIndex(0.99f, 3))
        assertEquals(-1, highlightIndex(1f, 3))
        assertEquals(-1, highlightIndex(0.5f, 0))
    }
}

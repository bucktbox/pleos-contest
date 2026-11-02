package com.safecargo.data

import com.safecargo.engine.Grade
import com.safecargo.engine.TripResult
import com.safecargo.engine.TripSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakTest {
    private fun r(day: Long, id: String = "r$day") = TripResult(
        id, "c", null, Grade.RARE, 0, false, false, false, "general", day,
        TripSummary(0, 0, 0, 0, emptyList()),
    )
    private val today = 20000L

    @Test fun none() = assertEquals(0, Streak.days(emptyList(), today))
    @Test fun todayOnly() = assertEquals(1, Streak.days(listOf(r(today)), today))
    @Test fun yesterdayAndBefore() = assertEquals(2, Streak.days(listOf(r(today - 1), r(today - 2)), today))
    @Test fun gap() = assertEquals(2, Streak.days(listOf(r(today), r(today - 1), r(today - 3)), today))
    @Test fun sameDayThree() = assertEquals(1, Streak.days(listOf(r(today, "a"), r(today, "b"), r(today, "c")), today))
    @Test fun neitherTodayNorYesterday() = assertEquals(0, Streak.days(listOf(r(today - 2)), today))
}

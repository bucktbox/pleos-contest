package com.safecargo.data

import com.safecargo.engine.TripResult

object Streak {
    fun days(results: List<TripResult>, todayEpochDay: Long): Int {
        val set = results.mapTo(HashSet()) { it.epochDay }
        var d = when {
            todayEpochDay in set -> todayEpochDay
            todayEpochDay - 1 in set -> todayEpochDay - 1
            else -> return 0
        }
        var n = 0
        while (d in set) { n++; d-- }
        return n
    }
}

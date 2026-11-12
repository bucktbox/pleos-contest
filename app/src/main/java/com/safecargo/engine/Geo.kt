package com.safecargo.engine

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** haversine, R = 6_371_000 m */
fun distanceM(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
    val r = 6_371_000.0
    val p1 = Math.toRadians(aLat)
    val p2 = Math.toRadians(bLat)
    val dp = p2 - p1
    val dl = Math.toRadians(bLng - aLng)
    val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
    return 2 * r * asin(sqrt(h))
}

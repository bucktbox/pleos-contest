package com.safecargo.ui.shell

enum class Orientation { L, P }
enum class HeightClass { S, R, T }
data class WindowClass(val orientation: Orientation, val height: HeightClass)

/** 폭 ≥ 960 → L(가로), 아니면 P. 높이 < 900 → S, 900~1199 → R, ≥ 1200 → T. */
fun windowClassOf(widthDp: Int, heightDp: Int): WindowClass = WindowClass(
    if (widthDp >= 960) Orientation.L else Orientation.P,
    when {
        heightDp < 900 -> HeightClass.S
        heightDp < 1200 -> HeightClass.R
        else -> HeightClass.T
    },
)

package com.safecargo.ui.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.safecargo.engine.TripState
import com.safecargo.sdk.SupportStatus
import com.safecargo.ui.shell.AppShell
import com.safecargo.ui.shell.windowClassOf
import com.safecargo.ui.theme.SafeCargoTheme

@Preview(name = "1700x1184", widthDp = 1700, heightDp = 1184, showBackground = true, backgroundColor = 0xFF090B0F)
@Preview(name = "840x1184", widthDp = 840, heightDp = 1184, showBackground = true, backgroundColor = 0xFF090B0F)
@Preview(name = "1360x948", widthDp = 1360, heightDp = 948, showBackground = true, backgroundColor = 0xFF090B0F)
@Preview(name = "672x948", widthDp = 672, heightDp = 948, showBackground = true, backgroundColor = 0xFF090B0F)
@Preview(name = "1274x855", widthDp = 1274, heightDp = 855, showBackground = true, backgroundColor = 0xFF090B0F)
@Preview(name = "628x855", widthDp = 628, heightDp = 855, showBackground = true, backgroundColor = 0xFF090B0F)
@Preview(name = "1640x810", widthDp = 1640, heightDp = 810, showBackground = true, backgroundColor = 0xFF090B0F)
@Preview(name = "800x810", widthDp = 800, heightDp = 810, showBackground = true, backgroundColor = 0xFF090B0F)
@Preview(name = "1640x1400", widthDp = 1640, heightDp = 1400, showBackground = true, backgroundColor = 0xFF090B0F)
@Preview(name = "800x1400", widthDp = 800, heightDp = 1400, showBackground = true, backgroundColor = 0xFF090B0F)
@Preview(name = "1256x800", widthDp = 1256, heightDp = 800, showBackground = true, backgroundColor = 0xFF090B0F)
@Preview(name = "648x800", widthDp = 648, heightDp = 800, showBackground = true, backgroundColor = 0xFF090B0F)
annotation class WindowPreviews

@WindowPreviews
@Composable
private fun ShellPreview() {
    val c = androidx.compose.ui.platform.LocalConfiguration.current
    SafeCargoTheme {
        AppShell(TripState(), streak = 3, support = SupportStatus(belts = false), wc = windowClassOf(c.screenWidthDp, c.screenHeightDp)) { Box(androidx.compose.ui.Modifier) }
    }
}

package com.safecargo.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.safecargo.ui.theme.SC
import com.safecargo.ui.theme.ScType

/** 피그마 Badge/Example (15:149). Mock 이름 · 금액 · 제휴명 바로 오른쪽에 붙인다 (SD-08). */
@Composable
fun ExampleBadge(modifier: Modifier = Modifier) {
    Text(
        "예시", style = ScType.caption, color = SC.textDim,
        modifier = modifier
            .border(1.5.dp, SC.borderStrong, RoundedCornerShape(SC.radiusSm))
            .padding(horizontal = SC.gapXs, vertical = SC.gap2xs),
    )
}

/** 피그마 Button Medium (11:218 계열). 높이 76 · 좌우 32 · radius 16. */
@Composable
fun ScButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    Box(
        modifier
            .heightIn(min = SC.touchMin)
            .widthIn(min = SC.touchMin)
            .clip(RoundedCornerShape(SC.radius))
            .background(if (primary) SC.accent else SC.elevated)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = SC.gapXl),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = ScType.label, color = if (primary) SC.accentOn else SC.text)
    }
}

/** 둥근 배경 안의 선 아이콘 (피그마 icon wrap · pin wrap · mark). */
@Composable
fun IconCircle(
    @DrawableRes icon: Int,
    tint: Color,
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    background: Color = SC.elevated,
) {
    Box(modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        ScIcon(icon, tint, iconSize)
    }
}

@Composable
fun ScIcon(@DrawableRes icon: Int, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** 배수 표기: 1.5 → 「×1.5」, 2.0 → 「×2」. */
fun multiplierLabel(m: Double): String =
    "×" + if (m % 1.0 == 0.0) m.toInt().toString() else m.toString()

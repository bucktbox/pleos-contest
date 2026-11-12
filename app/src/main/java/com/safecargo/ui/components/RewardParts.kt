package com.safecargo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.safecargo.app.R
import com.safecargo.data.CouponStatus
import com.safecargo.ui.screens.statusLabel
import com.safecargo.ui.theme.SC
import com.safecargo.ui.theme.ScType

/**
 * 쿠폰 상태 배지 (SC-09 04) — 색 + 아이콘 모양 + 글자. 색을 못 봐도 모양과 글자로 구분된다.
 * 11/10 디자인: 테두리 없이 연한 배경 + 모서리 8.
 * ✓ 사용 가능(연한 파랑 · 체크 원) / ◉ 사용 완료(연한 회색 · 도장) / ⊘ 사용 불가(연한 빨강 · 사선 원).
 */
@Composable
fun CouponStatusChip(status: CouponStatus, modifier: Modifier = Modifier, short: Boolean = false) {
    val (icon, fg, bg) = when (status) {
        CouponStatus.AVAILABLE -> Triple(R.drawable.ic_check_circle, SC.badgeAccentText, SC.tintAccent)
        CouponStatus.USED -> Triple(R.drawable.ic_stamp, SC.textDim, SC.bg)
        CouponStatus.VOID -> Triple(R.drawable.ic_block, SC.badgeDangerText, SC.tintDanger)
    }
    Row(
        modifier.clip(RoundedCornerShape(SC.radiusSm)).background(bg)
            .padding(horizontal = SC.gapXs, vertical = SC.gap2xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SC.gap2xs),
    ) {
        ScIcon(icon, fg, 22.dp)
        if (!short) Text(statusLabel(status), style = ScType.caption, color = fg, maxLines = 1)
    }
}

/** 토큰 아이콘 + 수 (예: 「+2」, 「7 / 10」). 🪙 자리 — 이모지 대신 선 아이콘. */
@Composable
fun TokenAmount(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = ScType.label,
    color: Color = SC.gameKeyText,
    iconSize: Dp = 24.dp,
) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "토큰 $text" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SC.gap2xs),
    ) {
        TokenCoin(iconSize)
        Text(text, style = style, color = color, maxLines = 1, softWrap = false)
    }
}

/**
 * 사용 가이드 (공용 — SD-12). 펼침 / 접힘 두 상태.
 * 11/10 디자인: 박스 배경 · 테두리 · ⓘ · 번호 동그라미 · 단계 사이 화살표 없이 글자만.
 * 제목은 파랑 글자 버튼, 화살표는 제목 바로 옆(접힘 › · 펼침 ⌃).
 * 단계 문자열은 「이름\n설명」 — 이름은 Label 기본 글자색, 번호 · 설명은 Caption 회색.
 * [tokenLast] 면 마지막 단계 번호 자리에 토큰 아이콘(피그마 Info Box Step 5).
 * [startExpanded] 는 처음 [com.safecargo.data.RewardConfig.guideExpandedTimes] 회만 true (FR-APP-015).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuideInfoBox(
    title: String,
    steps: List<String>,
    startExpanded: Boolean,
    modifier: Modifier = Modifier,
    /** 가로로 넓은 창에서 단계를 세로 줄 대신 가로로 나란히. */
    inline: Boolean = false,
    tokenLast: Boolean = false,
) {
    var open by rememberSaveable(title) { mutableStateOf(startExpanded) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SC.gapSm)) {
        Row(
            Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(SC.radiusSm))
                .clickable(role = Role.Button, onClickLabel = if (open) "접기" else "펼치기") { open = !open }
                .padding(horizontal = SC.gapXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = ScType.caption, color = SC.badgeAccentText)
            ScIcon(R.drawable.ic_chevron_right, SC.badgeAccentText, 20.dp, Modifier.rotate(if (open) -90f else 0f))
        }
        if (open) {
            if (inline) {
                FlowRow(
                    Modifier.padding(horizontal = SC.gapXs),
                    horizontalArrangement = Arrangement.spacedBy(SC.gapLg),
                    verticalArrangement = Arrangement.spacedBy(SC.gapSm),
                ) { steps.forEachIndexed { i, s -> GuideStep(i, s, token = tokenLast && i == steps.lastIndex) } }
            } else {
                // 11/10 오후: 번호 사이를 선으로 이어 순서가 보이게.
                Column(Modifier.padding(horizontal = SC.gapXs)) {
                    steps.forEachIndexed { i, s ->
                        GuideStepLinked(i, s, token = tokenLast && i == steps.lastIndex, last = i == steps.lastIndex)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideStep(i: Int, step: String, token: Boolean) {
    val name = step.substringBefore('\n')
    val desc = step.substringAfter('\n', "").takeIf { it.isNotBlank() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.widthIn(min = 20.dp), contentAlignment = Alignment.Center) {
            if (token) TokenCoin(24.dp) else Text("${i + 1}", style = ScType.caption, color = SC.textDisabled)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = ScType.label, color = SC.text)
            if (desc != null) Text(desc, style = ScType.caption, color = SC.textDim)
        }
    }
}

/** 세로 단계 — 번호 동그라미 아래로 다음 번호까지 선을 잇는다. */
@Composable
private fun GuideStepLinked(i: Int, step: String, token: Boolean, last: Boolean) {
    val name = step.substringBefore('\n')
    val desc = step.substringAfter('\n', "").takeIf { it.isNotBlank() }
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (token) TokenCoin(28.dp) else Box(
                Modifier.size(28.dp).clip(CircleShape).background(SC.surface).border(2.dp, SC.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("${i + 1}", style = ScType.caption, color = SC.accent) }
            if (!last) Box(Modifier.padding(vertical = SC.gap2xs).width(2.dp).weight(1f).background(SC.accent.copy(alpha = 0.6f)))
        }
        Column(
            Modifier.padding(top = 2.dp, bottom = if (last) 0.dp else SC.gapLg),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(name, style = ScType.label, color = SC.text)
            if (desc != null) Text(desc, style = ScType.caption, color = SC.textDim)
        }
    }
}

/** 토큰 코인 그림 — 테마와 무관하게 고정색(바탕 #FFC83D · 테두리 · 무늬 #B87900, 11/10 디자인). */
@Composable
fun TokenCoin(size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_token_coin), contentDescription = null, modifier = modifier.size(size))
}

/**
 * 예시 QR — 쿠폰 번호로 정해지는 무늬 (실제 결제 불가 · SD-08). 정사각형 유지, 최소 160dp (SC-10 01).
 * 바탕은 테마와 무관하게 흰색 · 칸은 검정 (스캐너 대비).
 */
@Composable
fun MockQr(code: String, modifier: Modifier = Modifier) {
    val cells = 25
    val bits = androidx.compose.runtime.remember(code) {
        val r = java.util.Random(code.hashCode().toLong())
        BooleanArray(cells * cells) { r.nextBoolean() }
    }
    Box(
        modifier.widthIn(min = 160.dp).aspectRatio(1f).clip(RoundedCornerShape(SC.radiusSm)).background(Color.White).padding(SC.gapSm)
            .semantics { contentDescription = "쿠폰 QR (예시) $code" },
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val c = size.minDimension / cells
            fun finder(x: Int, y: Int) {
                drawRect(Color.Black, Offset(x * c, y * c), Size(7 * c, 7 * c))
                drawRect(Color.White, Offset((x + 1) * c, (y + 1) * c), Size(5 * c, 5 * c))
                drawRect(Color.Black, Offset((x + 2) * c, (y + 2) * c), Size(3 * c, 3 * c))
            }
            fun inFinder(x: Int, y: Int) = (x < 8 && y < 8) || (x >= cells - 8 && y < 8) || (x < 8 && y >= cells - 8)
            for (y in 0 until cells) for (x in 0 until cells) {
                if (!inFinder(x, y) && bits[y * cells + x]) drawRect(Color.Black, Offset(x * c, y * c), Size(c, c))
            }
            finder(0, 0); finder(cells - 7, 0); finder(0, cells - 7)
        }
    }
}

/** 토큰 게이지 (SC-09 05) — 잔액 / 기준, 넘치면 가득. */
@Composable
fun TokenGauge(balance: Int, cost: Int, modifier: Modifier = Modifier) {
    val p = if (cost <= 0) 1f else (balance.toFloat() / cost).coerceIn(0f, 1f)
    val track = SC.gameKeySubtle
    val fill = SC.gameKey
    Canvas(modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50))) {
        drawRect(track)
        drawRect(fill, size = Size(size.width * p, size.height))
    }
}

/** 개수 배지 (내비 보상함 칸 · 탭) — 0 이면 그리지 않는다. */
@Composable
fun CountBadge(n: Int, modifier: Modifier = Modifier, color: Color = SC.accent) {
    if (n <= 0) return
    Box(
        modifier.heightIn(min = 28.dp).widthIn(min = 28.dp).clip(CircleShape).background(color).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(n.toString(), style = ScType.caption, color = SC.accentOn, maxLines = 1) }
}

/** 둥근 표면 카드. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(SC.radiusLg)).background(SC.surface)
            .padding(if (compact) SC.gap else SC.gapLg),
        verticalArrangement = Arrangement.spacedBy(if (compact) SC.gapXs else SC.gapSm),
        content = content,
    )
}

/** 비활성 버튼 (ScButton 과 같은 크기). */
@Composable
fun ScButtonDisabled(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = SC.touchMin).widthIn(min = SC.touchMin).clip(RoundedCornerShape(SC.radius))
            .background(SC.elevated).padding(horizontal = SC.gapXl),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = ScType.label, color = SC.textDisabled) }
}

/** 쿠폰 썸네일 자리 — 작은 예시 QR 대신 아이콘. */
@Composable
fun CouponThumb(status: CouponStatus, modifier: Modifier = Modifier) {
    val tint = when (status) {
        CouponStatus.AVAILABLE -> SC.accent
        else -> SC.textDim
    }
    IconCircle(R.drawable.ic_wallet, tint, 64.dp, 30.dp, modifier)
}

/** 크기만 있는 공간. */
@Composable
fun Gap(h: Dp) = Box(Modifier.size(h))

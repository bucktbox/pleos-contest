package com.safecargo.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecargo.engine.Phase
import com.safecargo.engine.Seat
import com.safecargo.engine.TripState
import com.safecargo.sdk.SupportStatus
import com.safecargo.ui.theme.SC

private const val T_MAX_BODY_DP = 1184

/** 앱 바 + (지원 배너) + 본문. L: 좌측 레일, P: 앱 바 아래 세그먼트. ESCORTING 이면 레일·세그먼트 숨김. */
@Composable
fun AppShell(
    state: TripState,
    streak: Int,
    support: SupportStatus,
    wc: WindowClass,
    rankingEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val navVisible = state.phase != Phase.ESCORTING
    val items = buildList {
        add("로비"); if (rankingEnabled) add("랭킹")
    }
    Column(Modifier.fillMaxSize().background(SC.bg)) {
        AppBar(state, streak)
        supportMessage(support)?.let { SupportBanner(it) }
        if (wc.orientation == Orientation.P && navVisible) Segments(items)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (wc.orientation == Orientation.L && navVisible) Rail(items)
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                val bodyMod = if (wc.height == HeightClass.T) Modifier.heightIn(max = T_MAX_BODY_DP.dp) else Modifier
                Box(bodyMod.fillMaxWidth().fillMaxHeight()) { content() }
            }
        }
    }
}

@Composable
private fun AppBar(state: TripState, streak: Int) {
    Row(
        Modifier.fillMaxWidth().background(SC.surface).padding(horizontal = SC.gapLg, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SC.gap),
    ) {
        Text("세이프 카고", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = SC.text)
        StatusChip(state.phase)
        Box(Modifier.weight(1f))
        if (hasPassenger(state)) BuffBadge()
        StreakBadge(streak)
    }
}

private fun hasPassenger(s: TripState) =
    s.seats.occupied.any { (seat, occ) -> seat != Seat.DRIVER && occ == true }

@Composable
private fun StatusChip(phase: Phase) {
    val label = when (phase) {
        Phase.ESCORTING -> "주행"
        Phase.PAUSED -> "일시 정차"
        else -> "정차"
    }
    val tint = if (phase == Phase.ESCORTING) SC.accent else SC.text
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(SC.elevated).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(22.dp)) {
            val w = size.width; val h = size.height
            when (phase) {
                Phase.ESCORTING -> drawPath(Path().apply {
                    moveTo(w * .2f, h * .1f); lineTo(w * .9f, h * .5f); lineTo(w * .2f, h * .9f); close()
                }, tint)
                Phase.PAUSED -> {
                    drawRect(tint, Offset(w * .2f, h * .15f), Size(w * .2f, h * .7f))
                    drawRect(tint, Offset(w * .6f, h * .15f), Size(w * .2f, h * .7f))
                }
                else -> drawRect(tint, Offset(w * .2f, h * .2f), Size(w * .6f, h * .6f))
            }
        }
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = tint)
    }
}

@Composable
private fun BuffBadge() {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(SC.accent.copy(alpha = .2f)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.size(20.dp)) { // 위쪽 화살표 = 버프
            drawPath(Path().apply {
                moveTo(size.width / 2, 0f); lineTo(size.width, size.height * .6f)
                lineTo(size.width * .65f, size.height * .6f); lineTo(size.width * .65f, size.height)
                lineTo(size.width * .35f, size.height); lineTo(size.width * .35f, size.height * .6f)
                lineTo(0f, size.height * .6f); close()
            }, SC.accent)
        }
        Text("×1.5", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SC.accent)
    }
}

@Composable
private fun StreakBadge(streak: Int) {
    val on = streak > 0
    val c = if (on) SC.legendary else SC.textDisabled
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(24.dp)) { // 방패
            val w = size.width; val h = size.height
            val p = Path().apply {
                moveTo(w / 2, 0f); lineTo(w, h * .2f); lineTo(w * .9f, h * .6f)
                lineTo(w / 2, h); lineTo(w * .1f, h * .6f); lineTo(0f, h * .2f); close()
            }
            if (on) drawPath(p, c) else drawPath(p, c, style = Stroke(width = 3f))
        }
        if (on) Text("무사 배송 ${streak}일", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SC.text)
    }
}

private fun supportMessage(s: SupportStatus): String? = when {
    s.belts == false -> "이 차량은 벨트를 감지하지 못해요 — 버프는 착석 기준"
    s.seats == false -> "이 차량은 좌석 감지를 지원하지 않아요 — 동승 버프를 쓸 수 없어요"
    s.turn == false -> "이 차량은 방향지시등을 감지하지 못해요 — 깜빡이 점수는 제외돼요"
    s.steer == false -> "이 차량은 핸들 각도를 감지하지 못해요 — 부드러운 회전 점수는 제외돼요"
    s.gear == false -> "이 차량은 기어 상태를 감지하지 못해요"
    s.lights == false -> "이 차량은 조명 제어를 지원하지 않아요 — 불빛 연출은 건너뛰어요"
    s.location == false -> "위치를 확인하지 못해요 — 도착 확인이 제한돼요"
    else -> null
}

@Composable
private fun SupportBanner(msg: String) {
    Text(
        msg, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = SC.textDim,
        modifier = Modifier.fillMaxWidth().background(SC.elevated).padding(horizontal = SC.gapLg, vertical = 10.dp),
    )
}

@Composable
private fun Rail(items: List<String>) {
    Column(
        Modifier.width(160.dp).fillMaxHeight().background(SC.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { items.forEachIndexed { i, t -> NavItem(t, selected = i == 0, Modifier.fillMaxWidth()) } }
}

@Composable
private fun Segments(items: List<String>) {
    Row(
        Modifier.fillMaxWidth().background(SC.surface).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { items.forEachIndexed { i, t -> NavItem(t, selected = i == 0, Modifier.weight(1f)) } }
}

@Composable
private fun NavItem(label: String, selected: Boolean, modifier: Modifier) {
    Box(
        modifier.heightIn(min = SC.touchMin).clip(RoundedCornerShape(SC.radius))
            .background(if (selected) SC.accent else SC.elevated),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (selected) SC.accentOn else SC.textDim)
    }
}

package com.safecargo.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.safecargo.engine.Phase
import com.safecargo.engine.TripState
import com.safecargo.sdk.SupportStatus
import com.safecargo.app.R
import com.safecargo.ui.components.CountBadge
import com.safecargo.ui.components.IconCircle
import com.safecargo.ui.components.TokenAmount
import com.safecargo.ui.components.ScIcon
import com.safecargo.ui.components.multiplierLabel
import com.safecargo.ui.theme.SC
import com.safecargo.ui.theme.ScType

private const val T_MAX_BODY_DP = 1184

/** 내비 탭 (PRD v1.1 FR-APP-018). 기록 탭(SC-08)은 범위 밖. */
enum class AppTab { LOBBY, WALLET, RANKING }

/** 앱 바 토큰 칩 (FR-APP-019). */
data class TokenChipInfo(val balance: Int, val cost: Int)

/** 토큰 칩 문구 — 넓으면 「7 / 10」, 좁으면 「7」. */
fun tokenChipText(t: TokenChipInfo, wide: Boolean): String = if (wide) "${t.balance} / ${t.cost}" else "${t.balance}"

/** 칩을 보이는 단계 — 주행 · 일시 정차에는 숨긴다 (NFR-SAF-001). */
fun tokenChipVisible(phase: Phase): Boolean = phase != Phase.ESCORTING && phase != Phase.PAUSED

/** 앱 바 + (지원 배너) + 본문. L: 좌측 레일, P: 앱 바 아래 세그먼트. ESCORTING 이면 레일·세그먼트 숨김. */
@Composable
fun AppShell(
    state: TripState,
    streak: Int,
    support: SupportStatus,
    wc: WindowClass,
    rankingEnabled: Boolean = false,
    /** [com.safecargo.engine.JudgeEngine.familyBuffActive] 결과. */
    buffActive: Boolean = false,
    buffMultiplier: Double = 1.5,
    tab: AppTab = AppTab.LOBBY,
    onTab: (AppTab) -> Unit = {},
    /** 사용 가능 쿠폰 수 — 보상함 칸 배지. */
    walletBadge: Int = 0,
    token: TokenChipInfo? = null,
    content: @Composable () -> Unit,
) {
    // 내비는 정차 화면에서만 — 호위(ESCORTING) · 일시 정차 · 개봉에는 숨긴다.
    val navVisible = state.phase == Phase.IDLE || state.phase == Phase.LOADED
    val items = buildList {
        add(NavEntry(AppTab.LOBBY, "로비", R.drawable.ic_lobby))
        add(NavEntry(AppTab.WALLET, "보상함", R.drawable.ic_wallet, walletBadge))
        if (rankingEnabled) add(NavEntry(AppTab.RANKING, "랭킹", null))
    }
    Column(Modifier.fillMaxSize().background(SC.bg)) {
        AppBar(state, streak, wc.orientation, buffActive, buffMultiplier, token?.takeIf { tokenChipVisible(state.phase) }) { onTab(AppTab.WALLET) }
        supportBannerText(state.phase, support)?.let { SupportBanner(it) }
        if (wc.orientation == Orientation.P && navVisible) Segments(items, tab, onTab)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (wc.orientation == Orientation.L && navVisible) Rail(items, tab, onTab)
            // T: 본문을 1184 로 막고 가운데 둔다 (RL-05) — 위아래 여백이 고르게.
            val align = if (wc.height == HeightClass.T) Alignment.Center else Alignment.TopCenter
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = align) {
                val bodyMod = if (wc.height == HeightClass.T) Modifier.heightIn(max = T_MAX_BODY_DP.dp) else Modifier
                Box(bodyMod.fillMaxWidth().fillMaxHeight()) { content() }
            }
        }
    }
}

private data class NavEntry(val tab: AppTab, val label: String, val icon: Int?, val badge: Int = 0)

/** 피그마 01 앱 바 (16:111 / 17:180): 높이 96. L 은 마크 + 앱 이름, P 는 마크만. */
@Composable
private fun AppBar(
    state: TripState,
    streak: Int,
    orientation: Orientation,
    buffActive: Boolean,
    buffMultiplier: Double,
    token: TokenChipInfo?,
    onToken: () -> Unit,
) {
    val wide = orientation == Orientation.L
    Row(
        Modifier.fillMaxWidth().height(96.dp).padding(horizontal = if (wide) SC.gapXl else SC.gapLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SC.gapSm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapSm)) {
            IconCircle(R.drawable.ic_key, SC.accentOn, 48.dp, 26.dp, background = SC.accent)
            val note = demoBarNote(state.phase)
            Column {
                if (wide) Text("세이프 카고", style = ScType.title, color = SC.text)
                if (note != null) Text(note, style = ScType.caption, color = SC.textDim, maxLines = 1)
            }
        }
        Box(Modifier.weight(1f))
        StatusChip(state.phase)
        token?.let { TokenChip(it, wide, onToken) }
        if (buffActive) BuffBadge(wide, multiplierLabel(buffMultiplier))
        StreakBadge(streak, wide)
    }
}

@Composable
private fun Chip(content: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(SC.elevated).padding(horizontal = SC.gap, vertical = SC.gapXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SC.gapXs),
    ) { content() }
}

/** 피그마 Status Chip (15:168): 아이콘 모양 + 글자. 「P단 · D단」 대신 「정차 · 주행」. */
@Composable
private fun StatusChip(phase: Phase) {
    val label = when (phase) {
        Phase.ESCORTING -> "주행"
        Phase.PAUSED -> "일시 정차"
        else -> "정차"
    }
    val tint = when (phase) {
        Phase.ESCORTING -> SC.accent
        Phase.PAUSED -> SC.warning   // 11/10 오후: 흰 바탕에서 약해 경고색
        else -> SC.text
    }
    Chip {
        when (phase) {
            Phase.ESCORTING, Phase.PAUSED -> Canvas(Modifier.size(24.dp)) {
                val w = size.width; val h = size.height
                if (phase == Phase.ESCORTING) drawPath(Path().apply {
                    moveTo(w * .25f, h * .15f); lineTo(w * .85f, h * .5f); lineTo(w * .25f, h * .85f); close()
                }, tint) else {
                    drawRect(tint, Offset(w * .25f, h * .2f), Size(w * .18f, h * .6f))
                    drawRect(tint, Offset(w * .57f, h * .2f), Size(w * .18f, h * .6f))
                }
            }
            else -> ScIcon(R.drawable.ic_parking, tint, 24.dp)
        }
        Text(label, style = ScType.label, color = tint)
    }
}

/** 토큰 칩 — 누르면 보상함. 보너스 상자를 열 수 있으면 선물 아이콘 + 「보너스 가능」. */
@Composable
private fun TokenChip(t: TokenChipInfo, wide: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = SC.touchMin).clip(RoundedCornerShape(50)).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Chip {
            TokenAmount(tokenChipText(t, wide))
            if (t.balance >= t.cost) {
                ScIcon(R.drawable.ic_gift, SC.accent, 24.dp)
                if (wide) Text("보너스 가능", style = ScType.label, color = SC.accent)
            }
        }
    }
}

@Composable
private fun BuffBadge(wide: Boolean, mult: String) {
    Chip {
        ScIcon(R.drawable.ic_family, SC.accent, 24.dp)
        Text(if (wide) "버프 $mult" else mult, style = ScType.label, color = SC.text)
    }
}

/** 스트릭 칩. 이름은 「무사 배송」 — 좁은 P 창에서는 「N일」 만. */
@Composable
private fun StreakBadge(streak: Int, wide: Boolean) {
    val on = streak > 0
    Chip {
        ScIcon(R.drawable.ic_flame, if (on) SC.gameKey else SC.textDisabled, 24.dp)
        Text(if (wide) "무사 배송 ${streak}일" else "${streak}일", style = ScType.label, color = if (on) SC.text else SC.textDim)
    }
}

/** 앱 바의 시연 안내 — 쿠폰 · 매장 · 금액이 모두 예시라는 한 줄 (11/10 오후: 화면마다 붙던 「예시」 꼬리표를 대신한다). */
const val DEMO_BAR_NOTE = "시연용 예시 데이터"

/** 주행 · 일시 정차에는 숨긴다 — 주행 중 화면에 문장 0개 (NFR-SAF-001). */
fun demoBarNote(phase: Phase): String? = if (phase == Phase.ESCORTING || phase == Phase.PAUSED) null else DEMO_BAR_NOTE

/** 지원 배너 문구. ESCORTING 에서는 null — 주행 중 화면에 문장 0개 (NFR-SAF-001). */
fun supportBannerText(phase: Phase, support: SupportStatus): String? =
    if (phase == Phase.ESCORTING) null else supportMessage(support)

private fun supportMessage(s: SupportStatus): String? = when {
    s.belts == false -> "이 차량은 벨트를 감지하지 못해요 — 버프는 착석 기준"
    s.seats == false -> "이 차량은 좌석 감지를 지원하지 않아요 — 동승 버프를 쓸 수 없어요"
    s.turn == false -> "이 차량은 방향지시등을 감지하지 못해요 — 깜빡이 점수는 제외돼요"
    s.steer == false -> "이 차량은 핸들 각도를 감지하지 못해요 — 부드러운 회전 점수는 제외돼요"
    s.gear == false -> "이 차량은 기어 상태를 감지하지 못해요"
    s.lights == false -> "이 차량은 조명 제어를 지원하지 않아요 — 불빛 연출은 건너뛰어요"
    s.location == false -> "위치를 확인하지 못해요 — 도착 확인이 제한돼요"
    // v1.1 EX-09: 차속을 못 받으면 급가속 · 급정거 모션만 끈다 (흔들림은 남는다).
    s.speed == false -> "급가속 · 급정거 표시 꺼짐"
    else -> null
}

@Composable
private fun SupportBanner(msg: String) {
    Text(
        msg, style = ScType.caption, color = SC.textDim,
        modifier = Modifier.fillMaxWidth().background(SC.elevated).padding(horizontal = SC.gapLg, vertical = 10.dp),
    )
}

/** 피그마 02 레일 (16:140): 폭 136, 칸 104×96 (아이콘 + 글자). */
@Composable
private fun Rail(items: List<NavEntry>, tab: AppTab, onTab: (AppTab) -> Unit) {
    Column(
        Modifier.width(136.dp).fillMaxHeight().padding(SC.gap),
        verticalArrangement = Arrangement.spacedBy(SC.gapXs),
    ) { items.forEach { t -> NavItem(t, selected = t.tab == tab, vertical = true, Modifier.size(104.dp, 96.dp)) { onTab(t.tab) } } }
}

/** 피그마 02 세그먼트 (17:205): 트랙 안 칸 높이 76. */
@Composable
private fun Segments(items: List<NavEntry>, tab: AppTab, onTab: (AppTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = SC.gapLg, vertical = SC.gap2xs)
            .clip(RoundedCornerShape(SC.radiusLg)).background(SC.surface).padding(SC.gap2xs),
        horizontalArrangement = Arrangement.spacedBy(SC.gap2xs),
    ) { items.forEach { t -> NavItem(t, selected = t.tab == tab, vertical = false, Modifier.weight(1f).height(SC.touchMin)) { onTab(t.tab) } } }
}

@Composable
private fun NavItem(entry: NavEntry, selected: Boolean, vertical: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = if (selected) SC.text else SC.textDim
    Box(
        modifier.clip(RoundedCornerShape(SC.radius)).background(if (selected) SC.accentSubtle else Color.Transparent)   // 11/10: 선택 = color/accent/subtle
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val inner: @Composable () -> Unit = {
            entry.icon?.let { ScIcon(it, c, 28.dp) }
            Text(entry.label, style = ScType.label, color = c)
            CountBadge(entry.badge)
        }
        if (vertical) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SC.gap2xs)) { inner() }
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapXs)) { inner() }
    }
}

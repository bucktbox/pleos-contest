package com.safecargo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.safecargo.app.R
import com.safecargo.data.BonusGate
import com.safecargo.data.CouponStatus
import com.safecargo.data.MockCatalog
import com.safecargo.data.RewardConfig
import com.safecargo.data.WalletData
import com.safecargo.data.WalletItem
import com.safecargo.data.WalletOps
import com.safecargo.engine.Cargo
import com.safecargo.engine.CargoKind
import com.safecargo.ui.components.CouponStatusChip
import com.safecargo.ui.components.CouponThumb
import com.safecargo.ui.components.GuideInfoBox
import com.safecargo.ui.components.ScButton
import com.safecargo.ui.components.ScButtonDisabled
import com.safecargo.ui.components.ScIcon
import com.safecargo.ui.components.SurfaceCard
import com.safecargo.ui.components.TokenAmount
import com.safecargo.ui.components.TokenGauge
import com.safecargo.ui.shell.HeightClass
import com.safecargo.ui.shell.Orientation
import com.safecargo.ui.shell.WindowClass
import com.safecargo.ui.shell.windowClassOf
import com.safecargo.ui.theme.SC
import com.safecargo.ui.theme.SafeCargoTheme
import com.safecargo.ui.theme.ScType

/** 사용 완료 · 사용 불가 항목은 흐리게 (SC-09 04). */
internal const val FADED_ALPHA = 0.55f

/**
 * SC-09 보상함 (FR-APP-013 · 014 · 017). 상태 탭(사용 가능 · 사용 완료 · 사용 불가) + 토큰 지갑 + 최근 원장 + 사용 가이드.
 * 정차 화면에서만 보인다 — 주행이 시작되면 라우터가 호위 화면으로 바꾼다 (PRD 5.3).
 */
@Composable
fun WalletScreen(
    wallet: WalletData,
    catalog: MockCatalog,
    config: RewardConfig,
    today: Long,
    bonusGate: BonusGate,
    wc: WindowClass,
    onOpenItem: (String) -> Unit,
    onBonus: () -> Unit,
    onLobby: () -> Unit,
    onGuideShown: () -> Unit,
    initialTab: CouponStatus = CouponStatus.AVAILABLE,
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val compact = wc.height == HeightClass.S
    val pad = if (compact) SC.gapLg else SC.gapXl
    val gap = if (compact) SC.gapSm else SC.gap
    val guideOpen = wallet.guideViews < config.guideExpandedTimes && wc.orientation == Orientation.L
    LaunchedEffect(Unit) { if (guideOpen) onGuideShown() }

    val tabs: @Composable () -> Unit = {
        StatusTabs(wallet, tab, short = wc.orientation == Orientation.P && compact) { tab = it }
    }
    val list: @Composable () -> Unit = { CouponList(wallet, catalog, tab, today, config.tokenPayback, onOpenItem, onLobby) }
    val tokenCard: @Composable () -> Unit = { TokenCard(wallet, config, today, bonusGate, onBonus, compact) }
    val guide: @Composable () -> Unit = {
        GuideInfoBox(GUIDE_TITLE, guideSteps(config.tokenPayback, config.bonusCost), guideOpen, tokenLast = true)
    }

    if (wc.orientation == Orientation.L) {
        Row(Modifier.fillMaxSize().padding(horizontal = pad, vertical = gap), horizontalArrangement = Arrangement.spacedBy(SC.gapXl)) {
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                Text("보상함", style = ScType.headline, color = SC.text)
                tabs()
                list()
            }
            Column(
                Modifier.weight(0.8f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) { tokenCard(); guide() }
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pad, vertical = gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            Text("보상함", style = ScType.headline, color = SC.text)
            tokenCard()
            tabs()
            list()
            guide()
        }
    }
}

@Composable
private fun StatusTabs(w: WalletData, selected: CouponStatus, short: Boolean, onSelect: (CouponStatus) -> Unit) {
    // 피그마 SC-09 「02 상태 탭」(Status Tab 99:934): 고른 탭은 파랑 알약 · 안 고른 탭은 흰 바탕 + 얇은 테두리. 최소 높이 64dp.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SC.gapXs)) {
        CouponStatus.entries.forEach { s ->
            val on = s == selected
            val n = w.count(s)
            Row(
                Modifier.weight(1f).heightIn(min = 64.dp).clip(CircleShape)
                    .background(if (on) SC.accent else SC.surface)
                    .then(if (on) Modifier else Modifier.border(1.dp, SC.border, CircleShape))
                    .clickable(role = Role.Tab) { onSelect(s) }
                    .semantics { contentDescription = "${statusLabel(s)} ${n}장" }
                    .padding(start = SC.gapLg, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                Text(if (short) statusShort(s) else statusLabel(s), style = ScType.label, color = if (on) SC.accentOn else SC.textDim, maxLines = 1)
                // 11/10 디자인: 고른 탭만 숫자 동그라미, 안 고른 탭은 숫자만.
                if (n > 0 && on) {
                    Box(
                        Modifier.clip(CircleShape).background(SC.accentOn.copy(alpha = 0.22f))
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                    ) { Text(n.toString(), style = ScType.label, color = SC.accentOn, maxLines = 1) }
                } else if (n > 0) {
                    Text(n.toString(), style = ScType.label, color = SC.text, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun CouponList(
    w: WalletData,
    catalog: MockCatalog,
    tab: CouponStatus,
    today: Long,
    payback: Int,
    onOpen: (String) -> Unit,
    onLobby: () -> Unit,
) {
    val items = w.tab(tab)
    if (items.isEmpty()) {
        SurfaceCard {
            Text(emptyTabText(tab), style = ScType.body, color = SC.textDim)
            if (tab == CouponStatus.AVAILABLE) ScButton("로비로", onLobby)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(SC.gapSm)) {
        items.forEach { CouponRow(it, catalog, today, payback, onOpen) }
    }
}

@Composable
private fun CouponRow(item: WalletItem, catalog: MockCatalog, today: Long, payback: Int, onOpen: (String) -> Unit) {
    val reward = catalog.reward(item.rewardId)
    val faded = item.status != CouponStatus.AVAILABLE
    Row(
        Modifier.fillMaxWidth().heightIn(min = SC.touchMin).clip(RoundedCornerShape(SC.radiusLg)).background(SC.surface)
            .clickable(role = Role.Button) { onOpen(item.id) }.padding(SC.gap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SC.gap),
    ) {
        Row(
            Modifier.weight(1f).alpha(if (faded) FADED_ALPHA else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SC.gap),
        ) {
            CouponThumb(item.status)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SC.gap2xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapXs)) {
                    Text(
                        reward?.title ?: "쿠폰", style = ScType.label, color = SC.text, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gap2xs)) {
                    val soon = expiringSoon(item, today)
                    if (soon) ScIcon(R.drawable.ic_clock, SC.warning, 22.dp)
                    Text(itemDetailLine(item, today, payback), style = ScType.caption, color = if (soon) SC.warning else SC.textDim, maxLines = 2)
                }
            }
        }
        // 칩은 흐리게 하지 않는다 — 상태는 언제나 또렷하게.
        CouponStatusChip(item.status)
    }
}

@Composable
private fun TokenCard(w: WalletData, c: RewardConfig, today: Long, gate: BonusGate, onBonus: () -> Unit, compact: Boolean) {
    SurfaceCard(compact = compact) {
        Text("토큰", style = ScType.caption, color = SC.textDim)
        TokenAmount("${w.balance} / ${c.bonusCost}", style = ScType.headline, iconSize = 36.dp)
        TokenGauge(w.balance, c.bonusCost)
        Text(tokenHint(w.balance, c.bonusCost), style = ScType.body, color = SC.text)
        val left = c.bonusPerDay - WalletOps.todayBonusCount(w, today)
        Text("보너스 상자 오늘 ${left.coerceAtLeast(0)} / ${c.bonusPerDay}회", style = ScType.caption, color = SC.textDim)
        val blocked = bonusBlockedText(gate)
        if (blocked == null) ScButton("보너스 상자 열기", onBonus, Modifier.fillMaxWidth(), primary = true)
        else ScButtonDisabled(blocked, Modifier.fillMaxWidth())
        Box(Modifier.height(SC.gap2xs))
        Text("최근 토큰", style = ScType.label, color = SC.text)
        val recent = w.recentLedger(3)
        if (recent.isEmpty()) Text("아쉬운 상자나 쿠폰 사용으로 토큰이 쌓여요", style = ScType.body, color = SC.textDim)
        recent.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ledgerLine(e), style = ScType.body, color = SC.text, modifier = Modifier.weight(1f))
                Text(signed(e.delta), style = ScType.label, color = if (e.delta >= 0) SC.gameKeyText else SC.textDim)
            }
        }
    }
}

// ---------------------------------------------------------------- 프리뷰

internal val previewCatalog = MockCatalog(
    destinations = emptyList(),
    daily = Cargo("daily", "오늘의 데일리 카고", CargoKind.DAILY),
    rewards = listOf(
        com.safecargo.data.Reward("coupon-cafe", "수제 타르트 할인 쿠폰", com.safecargo.data.RewardType.COUPON, "1,000원 할인", "베이커리 카페 예시"),
        com.safecargo.data.Reward("coupon-rest", "휴게소 간식 쿠폰", com.safecargo.data.RewardType.COUPON, "간식 1개", "휴게소 예시"),
        com.safecargo.data.Reward("coupon-muni", "광장 매점 음료 쿠폰", com.safecargo.data.RewardType.COUPON, "음료 1잔", "광장 예시"),
        com.safecargo.data.Reward("coupon-roast", "로스터리 음료 쿠폰", com.safecargo.data.RewardType.COUPON, "음료 1잔", "로스터리 예시"),
    ),
)

internal fun previewWallet(today: Long) = WalletOps.seedDemo(WalletData(), previewCatalog.coupons, today, RewardConfig())

@Preview(name = "보상함 1700x1184", widthDp = 1700, heightDp = 1184)
@Composable
private fun WalletLPreview() = WalletHost(1700, 1184)

@Preview(name = "보상함 800x1400", widthDp = 800, heightDp = 1400)
@Composable
private fun WalletPPreview() = WalletHost(800, 1400)

@Composable
private fun WalletHost(w: Int, h: Int) {
    val today = 20_400L
    val wallet = previewWallet(today)
    SafeCargoTheme {
        Box(Modifier.fillMaxSize().background(SC.bg)) {
            WalletScreen(
                wallet, previewCatalog, RewardConfig(), today, WalletOps.bonusGate(wallet, today, RewardConfig()),
                windowClassOf(w, h), onOpenItem = {}, onBonus = {}, onLobby = {}, onGuideShown = {},
            )
        }
    }
}

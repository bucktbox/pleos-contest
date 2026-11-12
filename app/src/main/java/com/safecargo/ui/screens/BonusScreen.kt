package com.safecargo.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.safecargo.app.R
import com.safecargo.data.BonusGate
import com.safecargo.data.MockCatalog
import com.safecargo.data.RewardConfig
import com.safecargo.data.RewardType
import com.safecargo.data.WalletData
import com.safecargo.data.WalletOps
import com.safecargo.ui.components.CLOSED_AR
import com.safecargo.ui.components.ChestArt
import com.safecargo.ui.components.ChestImage
import com.safecargo.ui.components.OPEN_AR
import com.safecargo.ui.components.ScButton
import com.safecargo.ui.components.ScButtonDisabled
import com.safecargo.ui.components.ScIcon
import com.safecargo.ui.components.SurfaceCard
import com.safecargo.ui.components.TokenAmount
import com.safecargo.ui.shell.HeightClass
import com.safecargo.ui.shell.Orientation
import com.safecargo.ui.shell.WindowClass
import com.safecargo.ui.shell.windowClassOf
import com.safecargo.ui.theme.SC
import com.safecargo.ui.theme.SafeCargoTheme
import com.safecargo.ui.theme.ScType

/** 보너스 상자 연출 길이 — 2초 미만 (SC-11 · FR-APP-020). 주변광 · 테두리 빛 없음. */

/** 확률표 한 줄 — 「쿠폰 50% · 포인트 35% · 아쉬운 상자 15%」. */
fun oddsLine(c: RewardConfig): String =
    "쿠폰 ${c.bonusOdds.coupon}% · 포인트 ${c.bonusOdds.point}% · 아쉬운 상자 ${c.bonusOdds.empty}%"

/**
 * SC-11 보너스 상자 (Could · FR-APP-020). 토큰 [RewardConfig.bonusCost] 개로 하루 [RewardConfig.bonusPerDay] 번.
 * [onOpen] 이 차감 · 추첨 · 저장을 먼저 끝내고 결과 id 를 돌려준다 — 연출은 그 뒤 (EX-11: 연출 중 이탈해도 결과는 남는다).
 */
@Composable
fun BonusScreen(
    wallet: WalletData,
    catalog: MockCatalog,
    config: RewardConfig,
    today: Long,
    gate: BonusGate,
    wc: WindowClass,
    onOpen: () -> String?,
    onBack: () -> Unit,
) {
    var resultId by rememberSaveable { mutableStateOf<String?>(null) }
    var round by rememberSaveable { mutableStateOf(0) }
    // 언박싱과 같은 개봉 연출(ChestOpenModel) — 0..UNBOX_TOTAL_MS. 상자를 누르면 다음 덜컹으로 건너뛴다.
    val total = UNBOX_TOTAL_MS.toFloat()
    val anim = remember { Animatable(total) }
    var jump by remember { mutableStateOf(0f) }
    var jumps by remember { mutableStateOf(0) }
    // 결과는 이번 판 연출이 끝까지 돈 뒤에만 보인다 — anim 값만 보면 「열기」 직후 첫 프레임(아직 total)에 결과가 번쩍 보인다.
    var shownRound by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(round, jumps) {
        if (round > 0) {
            anim.snapTo(jump)
            anim.animateTo(total, tween((total - jump).toInt().coerceAtLeast(1), easing = LinearEasing))
            shownRound = round
        }
    }
    val revealed = resultId != null && shownRound == round
    val compact = wc.height == HeightClass.S
    val pad = if (compact) SC.gapLg else SC.gapXl
    val gap = if (compact) SC.gapSm else SC.gap
    val open: () -> Unit = {
        onOpen()?.let { resultId = it; jump = 0f; round++ }
    }

    val box: @Composable (Modifier) -> Unit = { m ->
        Box(m.clip(RoundedCornerShape(SC.radiusLg)).background(SC.surface), contentAlignment = Alignment.Center) {
            // 피그마 보너스 상자 (131:3533): 언박싱과 같은 「덜컹덜컹 2번 → 펑」 → 결과와 함께 열린 상자.
            val chestW = if (compact) 200.dp else 300.dp
            if (!revealed) {
                val playing = resultId != null
                val ms = if (playing) anim.value else CHEST_STILL_MS
                val empty = playing && catalog.reward(resultId!!)?.type == RewardType.EMPTY
                val tap = if (playing) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = {
                        when (val r = tapAt(anim.value)) {
                            TapResult.Finish -> { jump = total; jumps++ }
                            is TapResult.Jump -> { jump = r.toMs; jumps++ }
                        }
                    },
                ) else Modifier
                UnboxingStage(
                    ChestArt.BONUS_CLOSED, ChestArt.BONUS_OPEN, SC.gameKey, ms,
                    Modifier.fillMaxSize().then(tap).semantics { contentDescription = "보너스 상자" },
                    closedScale = 0.75f, burst = !empty,
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ChestImage(ChestArt.BONUS_OPEN, Modifier.width(chestW * (300f / 260f)).aspectRatio(OPEN_AR))
                    BonusResult(resultId!!, catalog, config)
                }
            }
        }
    }
    val panel: @Composable () -> Unit = {
        SurfaceCard(compact = compact) {
            Text("보너스 상자", style = ScType.headline, color = SC.text)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapSm)) {
                Text("필요한 토큰", style = ScType.body, color = SC.textDim)
                TokenAmount("${config.bonusCost}")
                Text("· 지금", style = ScType.body, color = SC.textDim)
                TokenAmount("${wallet.balance}")
            }
            val left = (config.bonusPerDay - WalletOps.todayBonusCount(wallet, today)).coerceAtLeast(0)
            Text("오늘 남은 횟수 $left / ${config.bonusPerDay}", style = ScType.body, color = SC.text)
            Text(oddsLine(config), style = ScType.body, color = SC.text)
            Text(TOKEN_NOT_FOR_SALE, style = ScType.caption, color = SC.textDim)
        }
    }
    val buttons: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(top = gap), horizontalArrangement = Arrangement.spacedBy(SC.gap, Alignment.End)) {
            ScButton("보상함으로", onBack)
            val blocked = bonusBlockedText(gate)
            val busy = resultId != null && !revealed
            when {
                busy -> ScButtonDisabled("여는 중", Modifier.widthIn(min = 260.dp))
                blocked != null -> ScButtonDisabled(blocked, Modifier.widthIn(min = 260.dp))
                else -> ScButton(
                    if (resultId == null) "토큰 ${config.bonusCost}개로 열기" else "한 번 더 열기",
                    open, Modifier.widthIn(min = 260.dp), primary = true,
                )
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = pad, vertical = gap)) {
        if (wc.orientation == Orientation.L) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SC.gapXl)) {
                box(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { panel() }
            }
        } else {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                box(Modifier.fillMaxWidth().heightIn(min = 320.dp))
                panel()
            }
        }
        buttons()
    }
}

@Composable
private fun BonusResult(rewardId: String, catalog: MockCatalog, c: RewardConfig) {
    val r = catalog.reward(rewardId)
    Column(
        Modifier.padding(SC.gapXl).semantics(mergeDescendants = true) { contentDescription = r?.title ?: rewardId },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SC.gapSm),
    ) {
        when (r?.type) {
            RewardType.EMPTY -> {
                Text(EMPTY_BOX_TITLE, style = ScType.title, color = SC.text)
                TokenAmount("+${c.tokenEmpty}", style = ScType.headline, iconSize = 36.dp)
            }
            RewardType.POINT -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapXs)) {
                    Text(r.amountLabel, style = ScType.headline, color = SC.text)
                }
                TokenAmount("+${c.tokenPoint}", style = ScType.title)
            }
            RewardType.COUPON -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapXs)) {
                    Text(r.title, style = ScType.title, color = SC.text)
                }
                Text("보상함에 넣었어요", style = ScType.body, color = SC.textDim)
            }
            else -> Text("보상을 받았어요", style = ScType.title, color = SC.text)
        }
    }
}

// ---------------------------------------------------------------- 프리뷰

@Preview(name = "보너스 1700x1184", widthDp = 1700, heightDp = 1184)
@Composable
private fun BonusLPreview() = BonusHost(1700, 1184)

@Preview(name = "보너스 800x1400", widthDp = 800, heightDp = 1400)
@Composable
private fun BonusPPreview() = BonusHost(800, 1400)

@Composable
private fun BonusHost(w: Int, h: Int) {
    val today = 20_400L
    val wallet = previewWallet(today)
    SafeCargoTheme {
        Box(Modifier.fillMaxSize().background(SC.bg)) {
            BonusScreen(
                wallet, previewCatalog, RewardConfig(), today, WalletOps.bonusGate(wallet, today, RewardConfig()),
                windowClassOf(w, h), onOpen = { null }, onBack = {},
            )
        }
    }
}

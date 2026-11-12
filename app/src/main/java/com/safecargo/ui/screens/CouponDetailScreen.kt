package com.safecargo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.window.Dialog
import com.safecargo.app.R
import com.safecargo.data.CouponStatus
import com.safecargo.data.Reward
import com.safecargo.data.RewardConfig
import com.safecargo.data.WalletItem
import com.safecargo.ui.components.CouponStatusChip
import com.safecargo.ui.components.GuideInfoBox
import com.safecargo.ui.components.MockQr
import com.safecargo.ui.components.ScButton
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
import kotlinx.coroutines.delay

/** 사용 완료 안내를 보여준 뒤 사용 완료 탭으로 돌아가기까지 (SC-10 05). */
internal const val USED_TOAST_MS = 1500L

/** 「사용 완료 · 토큰 +2 받았어요 (7 → 9)」 */
fun usedMessage(pay: Int, before: Int): String =
    if (pay > 0) "사용 완료 · 토큰 +$pay 받았어요 ($before → ${before + pay})" else "사용 완료"

/**
 * SC-10 쿠폰 상세 (FR-APP-014 · 016). QR(예시) + 쿠폰 번호 + 사용 조건 + 쓰는 법 + [사용 완료 처리].
 * 사용 완료 · 사용 불가 쿠폰은 QR 을 감춘다 — 다시 쓸 수 없다.
 *
 * @param onUse 사용 완료로 바꾼다. 받은 페이백 토큰(0 포함), 바뀌지 않았으면 null.
 * @param onUsedDone 안내를 보여준 뒤 — 라우터가 보상함 「사용 완료」 탭으로 돌아간다.
 */
@Composable
fun CouponDetailScreen(
    item: WalletItem,
    reward: Reward?,
    config: RewardConfig,
    today: Long,
    balance: Int,
    wc: WindowClass,
    onBack: () -> Unit,
    onUse: () -> Int?,
    onUsedDone: () -> Unit,
) {
    var confirm by rememberSaveable(item.id) { mutableStateOf(false) }
    var done by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(done) {
        if (done != null) {
            delay(USED_TOAST_MS)
            onUsedDone()
        }
    }
    val compact = wc.height == HeightClass.S
    val pad = if (compact) SC.gapLg else SC.gapXl
    val gap = if (compact) SC.gapSm else SC.gap
    val available = item.status == CouponStatus.AVAILABLE && done == null

    val qr: @Composable (Modifier) -> Unit = { m ->
        if (available) {
            SurfaceCard(m, compact) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MockQr(item.code, Modifier.widthIn(min = 160.dp, max = if (compact) 220.dp else 320.dp).fillMaxWidth())
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapXs)) {
                    Text("쿠폰 번호 ${item.code}", style = ScType.label, color = SC.text)
                }
                Text("QR 이 안 읽히면 번호를 불러 주세요", style = ScType.caption, color = SC.textDim)
            }
        }
    }
    val info: @Composable () -> Unit = {
        Conditions(item, reward, today, compact)
        GuideInfoBox(
            "이 쿠폰 쓰는 법",
            reward?.howTo?.takeIf { it.isNotEmpty() } ?: guideSteps(config.tokenPayback, config.bonusCost).drop(2),
            startExpanded = available && wc.orientation == Orientation.L,
        )
        PaybackLine(item, config.tokenPayback, done != null)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = pad, vertical = gap)) {
        Header(item, reward, onBack, done)
        Box(Modifier.size(gap))
        if (wc.orientation == Orientation.L) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SC.gapXl)) {
                if (available) qr(Modifier.weight(1f))
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) { info() }
            }
        } else {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) { qr(Modifier); info() }
        }
        Row(Modifier.fillMaxWidth().padding(top = gap), horizontalArrangement = Arrangement.spacedBy(SC.gap, Alignment.End)) {
            ScButton("보상함으로", onBack)
            if (available) ScButton("사용 완료 처리", { confirm = true }, Modifier.widthIn(min = 240.dp), primary = true)
        }
    }

    if (confirm && available) {
        UseConfirmDialog(
            onConfirm = {
                confirm = false
                onUse()?.let { pay -> done = usedMessage(pay, balance) }
            },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun Header(item: WalletItem, reward: Reward?, onBack: () -> Unit, done: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gap)) {
        Box(
            Modifier.size(SC.touchMin).clip(RoundedCornerShape(SC.radius)).background(SC.surface)
                .border(1.dp, SC.border, RoundedCornerShape(SC.radius))   // 11/10: 뒤로 가기 = 흰 바탕 + 테두리
                .clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "뒤로" },
            contentAlignment = Alignment.Center,
        ) { ScIcon(R.drawable.ic_back, SC.text, 32.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SC.gap2xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapXs)) {
                Text(
                    reward?.title ?: "쿠폰", style = ScType.title, color = SC.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            reward?.partnerLabel?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapXs)) {
                    Text(it, style = ScType.body, color = SC.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
            }
        }
        if (done != null) {
            Text(done, style = ScType.label, color = SC.success, modifier = Modifier.weight(1f, fill = false))
        }
        CouponStatusChip(if (done != null) CouponStatus.USED else item.status)
    }
}

@Composable
private fun Conditions(item: WalletItem, reward: Reward?, today: Long, compact: Boolean) {
    val faded = item.status != CouponStatus.AVAILABLE
    SurfaceCard(Modifier.alpha(if (faded) FADED_ALPHA else 1f), compact) {
        Text("사용 조건", style = ScType.label, color = SC.text)
        reward?.amountLabel?.let { Line("혜택", it) }
        Line("사용처", reward?.usePlace ?: reward?.partnerLabel ?: "매장 카운터")
        Line(
            "유효기간",
            when (item.status) {
                CouponStatus.AVAILABLE -> "${monthDay(item.expiresDay)} 까지 · ${daysLeftLabel(item, today)}"
                CouponStatus.USED -> "${monthDay(item.usedDay ?: item.receivedDay)} 사용"
                CouponStatus.VOID -> "${item.voidReason?.label ?: "사용 불가"} · ${monthDay(item.expiresDay)} 까지였어요"
            },
        )
        Line("횟수", reward?.condition ?: "1회")
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapSm)) {
        Text(label, style = ScType.body, color = SC.textDim, modifier = Modifier.widthIn(min = 96.dp))
        Text(value, style = ScType.body, color = SC.text, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun PaybackLine(item: WalletItem, payback: Int, justUsed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapXs)) {
        when {
            item.status == CouponStatus.USED || justUsed -> {
                TokenAmount("+${if (justUsed) payback else item.paybackTokens}")
                Text("받음", style = ScType.body, color = SC.text)
            }
            item.status == CouponStatus.AVAILABLE -> {
                Text("쓴 뒤 사용 완료 처리하면", style = ScType.body, color = SC.text)
                TokenAmount("+$payback")
            }
            else -> Unit
        }
    }
}

/** 「매장에서 쓰셨나요? 사용 완료로 바꾸면 되돌릴 수 없어요」 [사용 완료] / [아직이에요] (SC-10 04). */
@Composable
private fun UseConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(min = 480.dp, max = 640.dp).clip(RoundedCornerShape(SC.radiusLg)).background(SC.surface).padding(SC.gapXl),
            verticalArrangement = Arrangement.spacedBy(SC.gap),
        ) {
            Text(USE_CONFIRM_TITLE, style = ScType.headline, color = SC.text)
            Text(USE_CONFIRM_BODY, style = ScType.body, color = SC.textDim)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SC.gap, Alignment.End)) {
                ScButton("아직이에요", onDismiss)
                ScButton("사용 완료", onConfirm, primary = true)
            }
        }
    }
}

// ---------------------------------------------------------------- 프리뷰

@Preview(name = "쿠폰 상세 1700x1184", widthDp = 1700, heightDp = 1184)
@Composable
private fun CouponDetailLPreview() = DetailHost(1700, 1184, 0)

@Preview(name = "쿠폰 상세 사용 불가 800x1400", widthDp = 800, heightDp = 1400)
@Composable
private fun CouponDetailVoidPreview() = DetailHost(800, 1400, 3)

@Composable
private fun DetailHost(w: Int, h: Int, index: Int) {
    val today = 20_400L
    val wallet = previewWallet(today)
    val item = wallet.items[index]
    SafeCargoTheme {
        Box(Modifier.fillMaxSize().background(SC.bg)) {
            CouponDetailScreen(
                item, previewCatalog.reward(item.rewardId), RewardConfig(), today, wallet.balance, windowClassOf(w, h),
                onBack = {}, onUse = { null }, onUsedDone = {},
            )
        }
    }
}

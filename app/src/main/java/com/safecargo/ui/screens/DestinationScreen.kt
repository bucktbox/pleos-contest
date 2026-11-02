package com.safecargo.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.safecargo.app.R
import com.safecargo.data.MockCatalog
import com.safecargo.data.Reward
import com.safecargo.data.RewardType
import com.safecargo.engine.Cargo
import com.safecargo.engine.CargoKind
import com.safecargo.engine.Destination
import com.safecargo.engine.TripState
import com.safecargo.sdk.SupportStatus
import com.safecargo.ui.components.ExampleBadge
import com.safecargo.ui.components.IconCircle
import com.safecargo.ui.components.ScButton
import com.safecargo.ui.preview.WindowPreviews
import com.safecargo.ui.shell.AppShell
import com.safecargo.ui.shell.Orientation
import com.safecargo.ui.shell.WindowClass
import com.safecargo.ui.shell.windowClassOf
import com.safecargo.ui.theme.SC
import com.safecargo.ui.theme.SafeCargoTheme
import com.safecargo.ui.theme.ScType

/**
 * SC-02 목적지 고르기. 목록 순서 = catalog.json 순서 그대로 (거리순 정렬 없음).
 * 각 행: 이름 + 「예시」 · 주소 · 리워드 요약 + 「예시」. 행 전체가 터치 대상 (76 이상).
 */
@Composable
fun DestinationScreen(
    destinations: List<Destination>,
    rewards: MockCatalog,
    wc: WindowClass,
    onSelect: (Destination) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val wide = wc.orientation == Orientation.L
    val side = if (wide) SC.gapXl else SC.gapLg
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = if (wide) SC.gapXs else side, end = side, top = SC.gapLg, bottom = SC.gap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SC.gap),
        ) {
            ScButton("뒤로", onBack)
            Column {
                Text("목적지 고르기", style = ScType.title, color = SC.text)
                Text("고르면 스폰서 카고가 실리고 내비에 경로가 가요", style = ScType.body, color = SC.textDim)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopStart) {
            LazyColumn(
                Modifier.widthIn(max = 1100.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = if (wide) SC.gapXs else side, end = side, bottom = SC.gapXl),
                verticalArrangement = Arrangement.spacedBy(SC.gap),
            ) {
                items(destinations, key = { it.id }) { d -> DestinationRow(d, rewards.reward(d.rewardId)) { onSelect(d) } }
            }
        }
    }
}

@Composable
private fun DestinationRow(d: Destination, reward: Reward?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = SC.touchMin).clip(RoundedCornerShape(SC.radiusLg)).background(SC.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = SC.gapLg, vertical = SC.gapLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SC.gap),
    ) {
        IconCircle(R.drawable.ic_pin, SC.accent, 56.dp, 28.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SC.gap2xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapSm)) {
                Text(d.name, style = ScType.title, color = SC.text, modifier = Modifier.weight(1f, fill = false))
                ExampleBadge()
            }
            Text(d.address, style = ScType.caption, color = SC.textDim)
            if (reward != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapSm)) {
                    Text(rewardSummary(reward), style = ScType.body, color = SC.gameKey, modifier = Modifier.weight(1f, fill = false))
                    ExampleBadge()
                }
            }
        }
    }
}

internal fun rewardSummary(r: Reward): String = when (r.type) {
    RewardType.COUPON -> "도착 보상 · ${r.title} (${r.amountLabel})"
    else -> "도착 보상 · ${r.title} ${r.amountLabel}"
}

@WindowPreviews
@Composable
private fun DestinationPreview() {
    val c = androidx.compose.ui.platform.LocalConfiguration.current
    val wc = windowClassOf(c.screenWidthDp, c.screenHeightDp)
    val dests = listOf(
        Destination("muni", "시청 광장 예시", "예시시 중앙로 1 (가상)", 0.0, 0.0, "coupon-muni"),
        Destination("rest", "휴게소 예시", "예시고속도로 하행 (가상)", 0.0, 0.0, "coupon-rest"),
        previewDestination,
    )
    val catalog = MockCatalog(
        dests, Cargo("daily", "오늘의 데일리 카고", CargoKind.DAILY),
        listOf(
            Reward("coupon-muni", "광장 매점 음료 쿠폰", RewardType.COUPON, "음료 1잔", "시청 광장 예시"),
            Reward("coupon-rest", "휴게소 간식 쿠폰", RewardType.COUPON, "간식 1개", "휴게소 예시"),
            Reward("coupon-cafe", "베이커리 카페 빵 쿠폰", RewardType.COUPON, "빵 1개", "베이커리 카페 예시"),
        ),
    )
    SafeCargoTheme {
        AppShell(TripState(), streak = 0, support = SupportStatus(), wc = wc) {
            DestinationScreen(dests, catalog, wc, onSelect = {}, onBack = {})
        }
    }
}

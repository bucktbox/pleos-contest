package com.safecargo.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.safecargo.engine.DestCategory
import com.safecargo.ui.components.ScIcon
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
 * SC-02 목적지 고르기 (피그마 41:350 가로 · 42:414 / 42:481 세로).
 * 고르기와 확정을 나눈다: 카드를 누르면 「선택한 목적지」 미리보기가 바뀌고, [이 목적지로 안내] 를 눌러야 카고가 실리고 내비에 경로가 간다.
 * - 가로: 왼쪽 목록 55% · 오른쪽 미리보기 45%.
 * - 세로: 목록 → 카드를 누르면 상세 → [이 목적지로 안내]. 뒤로 = 목록.
 * 목록 순서 = catalog.json 순서 그대로 (거리순 정렬 없음). 유형 필터는 목록에 있는 유형만 보인다.
 */
@Composable
fun DestinationScreen(
    destinations: List<Destination>,
    rewards: MockCatalog,
    wc: WindowClass,
    onSelect: (Destination) -> Unit,
    onBack: () -> Unit,
    /** 이미 고른 목적지 — 미리보기 초기값. */
    currentId: String? = null,
) {
    val wide = wc.orientation == Orientation.L
    var filter by rememberSaveable { mutableStateOf<DestCategory?>(null) }
    var pickedId by rememberSaveable { mutableStateOf(currentId ?: if (wide) destinations.firstOrNull()?.id else null) }
    // 세로: 상세를 보고 있는가. 가로에선 쓰지 않는다 (미리보기가 늘 보인다).
    var detail by rememberSaveable { mutableStateOf(false) }
    val picked = destinations.firstOrNull { it.id == pickedId }
    val shown = filterDestinations(destinations, filter)
    val showDetail = !wide && detail && picked != null

    BackHandler { if (showDetail) detail = false else onBack() }
    val side = if (wide) SC.gapXl else SC.gapLg

    Column(Modifier.fillMaxSize()) {
        Header(
            title = if (showDetail) picked!!.name else "목적지 고르기",
            onBack = { if (showDetail) detail = false else onBack() },
            side = side,
        )
        when {
            wide -> Row(
                Modifier.weight(1f).fillMaxWidth().padding(start = side, end = side, bottom = side),
                horizontalArrangement = Arrangement.spacedBy(SC.gapXl),
            ) {
                DestinationList(destinations, shown, filter, { filter = it }, pickedId, rewards, Modifier.weight(0.55f)) { pickedId = it.id }
                PreviewPanel(picked, rewards, Modifier.weight(0.45f).fillMaxHeight(), onConfirm = { picked?.let(onSelect) })
            }
            showDetail -> PreviewPanel(
                picked, rewards, Modifier.weight(1f).fillMaxWidth().padding(start = side, end = side, bottom = side),
                onConfirm = { onSelect(picked!!) },
            )
            else -> DestinationList(
                destinations, shown, filter, { filter = it }, pickedId, rewards,
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = side),
            ) { pickedId = it.id; detail = true }
        }
    }
}

/** 유형 필터 — null = 전체. */
internal fun filterDestinations(all: List<Destination>, f: DestCategory?): List<Destination> =
    if (f == null) all else all.filter { it.category == f }

/** 01 앱 바 자리 (41:352): 뒤로 · 제목 · 「시연용 예시 데이터」 안내. */
@Composable
private fun Header(title: String, onBack: () -> Unit, side: androidx.compose.ui.unit.Dp) {
    Row(
        Modifier.fillMaxWidth().padding(start = side, end = side, top = SC.gapLg, bottom = SC.gapLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SC.gap),
    ) {
        Box(
            Modifier.size(SC.touchMin).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "뒤로", onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            IconCircle(R.drawable.ic_signal, SC.text, 56.dp, 28.dp, Modifier.rotate(180f).semantics { contentDescription = "뒤로" })
        }
        Column(Modifier.weight(1f, fill = false)) {
            Text(title, style = ScType.title, color = SC.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(DEMO_NOTE, style = ScType.caption, color = SC.textDim)
        }
    }
}

const val DEMO_NOTE = "시연용 예시 데이터 · 실제 매장·쿠폰이 아니에요"

@Composable
private fun DestinationList(
    all: List<Destination>,
    shown: List<Destination>,
    filter: DestCategory?,
    onFilter: (DestCategory?) -> Unit,
    pickedId: String?,
    rewards: MockCatalog,
    modifier: Modifier,
    onPick: (Destination) -> Unit,
) {
    val cats = DestCategory.entries.filter { c -> all.any { it.category == c } }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = SC.gapXl), verticalArrangement = Arrangement.spacedBy(SC.gap)) {
        if (cats.isNotEmpty()) item(key = "filter") {
            Row(Modifier.padding(bottom = SC.gapXs), horizontalArrangement = Arrangement.spacedBy(SC.gapSm)) {
                FilterChip("전체", filter == null) { onFilter(null) }
                cats.forEach { c -> FilterChip(c.label, filter == c) { onFilter(c) } }
            }
        }
        items(shown, key = { it.id }) { d -> SponsorCard(d, rewards.reward(d.rewardId), d.id == pickedId) { onPick(d) } }
    }
}

/** 피그마 Filter Chip (40:346): 높이 64 · 좌우 28 · 알약. */
@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(50)).background(if (selected) SC.accent else SC.elevated)
            .clickable(role = Role.Tab, onClick = onClick).semantics { this.selected = selected }
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = ScType.label, color = if (selected) SC.accentOn else SC.textDim)
    }
}

/** 피그마 Sponsor Card (40:371): 핀 · 이름 · 「유형 · 카고」 · 도착 리워드, 선택되면 파란 테 + ✓. */
@Composable
private fun SponsorCard(d: Destination, reward: Reward?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = SC.touchMin).clip(RoundedCornerShape(SC.radiusLg)).background(SC.surface)
            .then(if (selected) Modifier.border(3.dp, SC.accent, RoundedCornerShape(SC.radiusLg)) else Modifier)
            .clickable(role = Role.Button, onClick = onClick).semantics { this.selected = selected }
            .padding(SC.gapLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        IconCircle(R.drawable.ic_pin, SC.accent, 64.dp, 30.dp, background = SC.accentSubtle)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SC.gap2xs)) {
            Text(d.name, style = ScType.title, color = SC.text)
            metaLine(d)?.let { Text(it, style = ScType.body, color = SC.textDim) }
            if (reward != null) Text(rewardSummary(reward), style = ScType.caption, color = SC.gameKey)
        }
        if (selected) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(SC.accent), contentAlignment = Alignment.Center) {
                ScIcon(R.drawable.ic_check, SC.accentOn, 24.dp)
            }
        }
    }
}

/** 「카페 · 바리스타의 수제 타르트 상자」 — 유형 · 카고 중 있는 것만. 둘 다 없으면 주소. */
internal fun metaLine(d: Destination): String? =
    listOfNotNull(d.category?.label, d.cargoName).joinToString(" · ").ifEmpty { d.address }

/** 03 선택 미리보기 (41:427): 이름 · 카고 그림 · 카고 · 도착 리워드 · 안내 · [이 목적지로 안내]. */
@Composable
private fun PreviewPanel(d: Destination?, rewards: MockCatalog, modifier: Modifier, onConfirm: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(SC.radiusLg)).background(SC.surface).padding(SC.gapXl),
        verticalArrangement = Arrangement.spacedBy(SC.gapLg),
    ) {
        if (d == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("왼쪽에서 목적지를 골라 주세요", style = ScType.bodyLarge, color = SC.textDim)
            }
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(SC.gap2xs)) {
            Text("선택한 목적지", style = ScType.caption, color = SC.textDim)
            Text(d.name, style = ScType.headline, color = SC.text)
        }
        Box(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 240.dp), contentAlignment = Alignment.Center) {
            CargoBox(Modifier.size(260.dp, 206.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(SC.gapXs)) {
            Text(d.cargoName ?: (d.name + " 카고"), style = ScType.title, color = SC.text)
            d.cargoDesc?.let { Text(it, style = ScType.body, color = SC.textDim) }
        }
        rewards.reward(d.rewardId)?.let { r ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(SC.radius)).background(SC.elevated).padding(SC.gap),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SC.gap),
            ) {
                IconCircle(R.drawable.ic_key, SC.gameKey, 56.dp, 28.dp, background = SC.gameKeySubtle)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("도착 리워드", style = ScType.caption, color = SC.textDim)
                    Text("${r.title} · ${r.amountLabel}", style = ScType.label, color = SC.text)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapSm)) {
            ScIcon(R.drawable.ic_parking, SC.textDim, 28.dp)
            Text("도착해서 차를 세우면 상자가 열려요", style = ScType.body, color = SC.textDim)
        }
        Spacer(Modifier.weight(1f))
        ScButton("이 목적지로 안내", onConfirm, Modifier.fillMaxWidth().heightIn(min = 88.dp), primary = true)
    }
}

/** 카드의 리워드 줄 — 「도착 리워드 · 빵 1개」. */
internal fun rewardSummary(r: Reward): String = "도착 리워드 · ${r.amountLabel}"

@WindowPreviews
@Composable
private fun DestinationPreview() {
    val c = androidx.compose.ui.platform.LocalConfiguration.current
    val wc = windowClassOf(c.screenWidthDp, c.screenHeightDp)
    val dests = listOf(
        Destination("muni", "시청 광장 예시", "예시시 중앙로 1 (가상)", 0.0, 0.0, "coupon-muni", DestCategory.MUNI, "어린 묘목 화분 상자"),
        Destination("rest", "휴게소 예시", "예시고속도로 하행 (가상)", 0.0, 0.0, "coupon-rest", DestCategory.REST, "갓 구운 호두과자 상자"),
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

package com.safecargo.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.safecargo.app.R
import com.safecargo.engine.Cargo
import com.safecargo.engine.CargoKind
import com.safecargo.engine.Destination
import com.safecargo.engine.Phase
import com.safecargo.engine.Seat
import com.safecargo.engine.SeatSnapshot
import com.safecargo.engine.TripState
import com.safecargo.sdk.SupportStatus
import com.safecargo.ui.components.ChestArt
import com.safecargo.ui.components.ChestImage
import com.safecargo.ui.components.IconCircle
import com.safecargo.ui.components.ScButton
import com.safecargo.ui.components.ScIcon
import com.safecargo.ui.components.multiplierLabel
import com.safecargo.ui.preview.WindowPreviews
import com.safecargo.ui.shell.AppShell
import com.safecargo.ui.shell.HeightClass
import com.safecargo.ui.shell.Orientation
import com.safecargo.ui.shell.WindowClass
import com.safecargo.ui.shell.windowClassOf
import com.safecargo.ui.theme.SC
import com.safecargo.ui.theme.SafeCargoTheme
import com.safecargo.ui.theme.ScType

/** 로비가 상태 외에 필요한 값. 컨트롤러 · 설정 · 카탈로그에서 [CargoApp] 이 채운다. */
data class LobbyInfo(
    val streak: Int = 0,
    val streakDays: Int = 5,
    val routeFailed: Boolean = false,
    val dailyCargoName: String = "오늘의 데일리 카고",
    /** 스폰서 카고의 제휴명 (Mock). */
    val sponsorLabel: String? = null,
    val beltTrusted: Boolean = false,
    val support: SupportStatus = SupportStatus(),
    /** [com.safecargo.engine.JudgeEngine.familyBuffActive] 결과 — 화면에서 다시 판정하지 않는다. */
    val buffActive: Boolean = false,
    val buffMultiplier: Double = 1.5,
    /** [com.safecargo.engine.FamilyBuff.beltVerified] — 동승자가 있고 모두 벨트를 맸다고 엔진이 판정. */
    val beltVerified: Boolean = false,
)

private const val NAVI_FAILED = "내비 안내를 보내지 못했어요 — 목적지는 앱에 저장돼 있어요"
private const val LOCATION_COPY = "도착했는지 정차할 때 한 번만 확인하고 바로 지워요. 저장 · 전송하지 않아요"

/**
 * SC-01 로비. 피그마 「02 SC-01 로비」 (L 16:110 · P 17:179) 기준.
 * 본문 높이가 충분하면 카드가 창을 채우고(fill), 모자라면 자연 높이로 세로 스크롤한다.
 */
@Composable
fun LobbyScreen(
    state: TripState,
    wc: WindowClass,
    locationGranted: Boolean,
    onPickDestination: () -> Unit,
    onRequestLocation: () -> Unit,
    onDebugReset: (() -> Unit)?,
    info: LobbyInfo = LobbyInfo(),
) {
    val m = LobbyModel(state, info)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (wc.orientation == Orientation.L) {
            // 보조 열이 기준: 좌석 카드 최소 ≈533 + 스트릭 ≈217(디버그 버튼 포함) + 안내 ≈183 + 간격 48 + 위아래 64
            // ≈ 1045. 지원 배너(≈45)가 뜬 1700×1184 창(본문 ≈1043)은 스크롤 쪽으로 보낸다.
            val fill = locationGranted && maxHeight >= 1060.dp && wc.height != HeightClass.S
            LobbyLandscape(m, fill, locationGranted, onPickDestination, onRequestLocation, onDebugReset)
        } else {
            val fill = locationGranted && maxHeight >= 900.dp && wc.height != HeightClass.S
            LobbyPortrait(m, fill, locationGranted, onPickDestination, onRequestLocation, onDebugReset)
        }
    }
}

private class LobbyModel(val state: TripState, val info: LobbyInfo) {
    val cargo: Cargo? = state.cargo
    val sponsor = cargo?.kind == CargoKind.SPONSOR
    val cargoName = cargo?.name ?: info.dailyCargoName
    val destination: Destination? = cargo?.destination
    val passengers = state.seats.occupied.count { (s, o) -> s != Seat.DRIVER && o == true }
    val riders = state.seats.occupied.count { it.value == true }
    val buff = info.buffActive
    val mult = multiplierLabel(info.buffMultiplier)
    // 엔진 판정(동승자 기준)을 그대로 쓴다 — 화면에서 다시 세지 않는다.
    val allBelted = info.beltVerified && passengers > 0

    fun seatsSummary(short: Boolean): String = when {
        info.support.seats == false -> if (short) "좌석 감지 미지원" else "이 차량은 좌석 감지를 지원하지 않아요"
        riders == 0 -> if (short) "좌석 확인 중" else "좌석 정보를 기다리고 있어요"
        allBelted -> if (short) "동승 ${passengers}명 벨트 OK" else "동승자 ${passengers}명 모두 벨트를 맸어요"
        buff -> if (short) "${riders}명 탑승" else "탄 사람 ${riders}명 — 패밀리 버프가 붙어요"
        passengers > 0 && info.beltTrusted -> if (short) "벨트를 매면 $mult" else "동승자가 모두 벨트를 매면 버프가 붙어요"
        passengers > 0 -> if (short) "${riders}명 탑승" else "탄 사람 ${riders}명 — 버프 조건을 아직 못 채웠어요"
        else -> if (short) "혼자 탑승" else "함께 타면 열쇠가 $mult 돼요"
    }
}

// ---------------------------------------------------------------- 가로 (L)

@Composable
private fun LobbyLandscape(
    m: LobbyModel, fill: Boolean, locationGranted: Boolean,
    onPick: () -> Unit, onRequestLocation: () -> Unit, onDebugReset: (() -> Unit)?,
) {
    val scroll = rememberScrollState()
    val outer = Modifier.fillMaxSize().let { if (fill) it else it.verticalScroll(scroll) }
    // 피그마 03 본문: 좌 8 · 우 32 · 위아래 32, 두 열 사이 32. 주 60% / 보조 40%.
    Row(outer.padding(start = SC.gapXs, end = SC.gapXl, top = SC.gapXl, bottom = SC.gapXl), horizontalArrangement = Arrangement.spacedBy(SC.gapXl)) {
        Column(Modifier.weight(0.6f).let { if (fill) it.fillMaxHeight() else it }, verticalArrangement = Arrangement.spacedBy(SC.gapLg)) {
            CargoCardLarge(m, if (fill) Modifier.weight(1f) else Modifier, fill)
            DestinationCard(m, onPick, large = true)
            if (!locationGranted) LocationCard(onRequestLocation)
            KeyCards(m.info.support, m.mult, compact = false)
        }
        Column(Modifier.weight(0.4f).let { if (fill) it.fillMaxHeight() else it }, verticalArrangement = Arrangement.spacedBy(SC.gapLg)) {
            SeatCard(m, if (fill) Modifier.weight(1f) else Modifier, fill)
            StreakCard(m.info, large = true, onDebugReset = onDebugReset)
            GuideCard()
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, padding: Dp = SC.gapXl, gap: Dp = SC.gap, bg: Color = SC.surface, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(SC.radiusLg)).background(bg).padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/** 01 오늘의 카고 (16:164). */
@Composable
private fun CargoCardLarge(m: LobbyModel, modifier: Modifier, fill: Boolean) {
    Card(modifier) {
        Text("오늘의 카고", style = ScType.label, color = SC.textDim)
        NameWithBadge(m.cargoName, ScType.headline)
        Text(cargoLine(m), style = ScType.bodyLarge, color = SC.textDim)
        // 카고 Lottie 자리 (16:171). 디자이너 Lottie 가 오기 전엔 임시 상자 그림.
        Box(
            // 회색 박스 없이 상자 그림의 바닥 그림자만 (11/03 디자인 요청 §3).
            Modifier.fillMaxWidth().let { if (fill) it.weight(1f) else it.height(240.dp) },
            contentAlignment = Alignment.Center,
        ) { CargoBox(Modifier.fillMaxHeight(0.7f)) }
        SponsorLine(m, ScType.body)
    }
}

@Composable
internal fun CargoBox(modifier: Modifier = Modifier) {
    ChestImage(ChestArt.INTACT, modifier.padding(SC.gap), contentDescription = "카고 상자")
}

private fun cargoLine(m: LobbyModel) = when {
    !m.sponsor -> "목적지 없이 달려도 무사히 모시면 상자가 열려요."
    else -> m.destination?.cargoDesc ?: "도착할 때까지 부서지지 않게 조심히 모셔 주세요."
}

@Composable
private fun SponsorLine(m: LobbyModel, style: androidx.compose.ui.text.TextStyle) {
    val label = m.info.sponsorLabel ?: return
    if (!m.sponsor) return
    // 「예시」 꼬리표는 앱 바 한 줄로 모았다 (11/10 오후 — AppShell.DEMO_BAR_NOTE).
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapSm)) {
        Text(label, style = style, color = SC.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Text("제공", style = style, color = SC.textDim)
    }
}

@Composable
private fun NameWithBadge(name: String, style: androidx.compose.ui.text.TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapSm)) {
        Text(name, style = style, color = SC.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    }
}

/** 02 목적지 (16:184 / 17:248). 없으면 [목적지 고르기], 있으면 이름 + 예시 + [변경]. */
@Composable
private fun DestinationCard(m: LobbyModel, onPick: () -> Unit, large: Boolean) {
    val d = m.destination
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(SC.radiusLg)).background(SC.surface)
            .padding(horizontal = if (large) SC.gapXl else SC.gapLg, vertical = SC.gapLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SC.gap),
    ) {
        IconCircle(R.drawable.ic_pin, SC.accent, if (large) 64.dp else 56.dp, if (large) 30.dp else 28.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SC.gap2xs)) {
            Text("목적지", style = ScType.caption, color = SC.textDim)
            if (d != null) {
                NameWithBadge(d.name, ScType.title)
                when {
                    m.info.routeFailed -> Text(NAVI_FAILED, style = ScType.body, color = SC.warning)
                    large -> Text("도착해서 차를 세우면 상자가 열려요", style = ScType.body, color = SC.textDim)
                }
            } else {
                Text("아직 고르지 않았어요", style = ScType.title, color = SC.text)
                Text("고르면 스폰서 카고가 실리고 내비에 경로가 가요", style = ScType.body, color = SC.textDim)
            }
        }
        if (d != null) ScButton("변경", onPick) else ScButton("목적지 고르기", onPick, primary = true)
    }
}

/** 위치 권한 카드 — 미허용일 때만. */
@Composable
private fun LocationCard(onRequest: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(SC.radiusLg)).background(SC.elevated).padding(SC.gapLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SC.gap),
    ) {
        IconCircle(R.drawable.ic_pin, SC.gameKey, 56.dp, 28.dp, background = SC.surface)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SC.gap2xs)) {
            Text("도착 확인에 위치를 써요", style = ScType.label, color = SC.text)
            Text(LOCATION_COPY, style = ScType.body, color = SC.textDim)
        }
        ScButton("허용", onRequest, primary = true)
    }
}

private data class KeyInfo(@DrawableRes val icon: Int, val title: String, val desc: String, val supported: Boolean)

private fun keyInfos(s: SupportStatus, mult: String) = listOf(
    KeyInfo(R.drawable.ic_signal, "깜빡이 먼저", "켜고 나서 방향 바꾸기", s.turn != false),
    KeyInfo(R.drawable.ic_steering, "부드럽게", "핸들은 천천히 돌리기", s.steer != false),
    KeyInfo(R.drawable.ic_family, "패밀리", "함께 타면 열쇠 $mult", s.seats != false),
)

/** 03 3대 열쇠 (16:199 / 17:297). 지원하지 않는 열쇠는 회색 + 사선 + 「미지원」 (EX-01). */
@Composable
private fun KeyCards(support: SupportStatus, mult: String, compact: Boolean) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(if (compact) SC.gapSm else SC.gap)) {
        keyInfos(support, mult).forEach { k ->
            val tint = if (k.supported) SC.gameKey else SC.textDisabled
            val text = if (k.supported) SC.text else SC.textDisabled
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(SC.radius)).background(SC.surface)
                    .then(if (k.supported) Modifier else Modifier.slash())
                    .padding(if (compact) SC.gap else SC.gapLg),
                horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(if (compact) SC.gapXs else SC.gapSm),
            ) {
                IconCircle(k.icon, tint, if (compact) 48.dp else 56.dp, if (compact) 24.dp else 28.dp)
                Text(k.title, style = if (compact) ScType.label else ScType.title, color = text)
                if (!k.supported) Text("미지원", style = ScType.caption, color = SC.textDim)
                else if (!compact) Text(k.desc, style = ScType.body, color = SC.textDim)
            }
        }
    }
}

private fun Modifier.slash() = drawBehind {
    drawLine(SC.border, Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 3.dp.toPx())
}

private val SEAT_ORDER = listOf(Seat.DRIVER to "운전석", Seat.PASSENGER to "조수석", Seat.REAR_LEFT to "뒤 왼쪽", Seat.REAR_RIGHT to "뒤 오른쪽")

/** 04 좌석 맵 · 패밀리 버프 (16:232). */
@Composable
private fun SeatCard(m: LobbyModel, modifier: Modifier, fill: Boolean) {
    Card(modifier, gap = SC.gapLg) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("탑승", style = ScType.title, color = SC.text, modifier = Modifier.weight(1f))
            BuffPill(m.buff, m.mult)
        }
        Box(Modifier.fillMaxWidth().let { if (fill) it.weight(1f) else it.padding(vertical = SC.gapLg) }, contentAlignment = Alignment.Center) {
            CarBody(m)
        }
        Text(m.seatsSummary(short = false), style = ScType.bodyLarge, color = SC.text)
    }
}

@Composable
private fun BuffPill(on: Boolean, mult: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (on) SC.accent else SC.elevated)
            .padding(horizontal = SC.gap, vertical = SC.gapXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SC.gapXs),
    ) {
        ScIcon(R.drawable.ic_family, if (on) SC.accentOn else SC.textDim, 24.dp)
        Text("패밀리 버프 $mult", style = ScType.label, color = if (on) SC.accentOn else SC.textDim)
    }
}

/** 차체 (16:244): 테두리 없이 채운 차체(위 120 · 아래 48 둥근) + 앞유리 + 2×2 좌석 (11/03). */
@Composable
private fun CarBody(m: LobbyModel) {
    Column(
        Modifier.clip(RoundedCornerShape(120.dp, 120.dp, 48.dp, 48.dp)).background(SC.seatmapBody)
            .padding(start = 36.dp, end = 36.dp, top = 40.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SC.gap),
    ) {
        Box(Modifier.size(150.dp, 12.dp).clip(RoundedCornerShape(6.dp)).background(SC.seatmapGlass))
        SEAT_ORDER.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(SC.gapXl)) {
                row.forEach { (seat, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(SC.gap2xs)) {
                        SeatTile(m, seat, 88.dp, 96.dp, SC.radius, 36.dp)
                        Text(label, style = ScType.caption, color = SC.textDim)
                    }
                }
            }
        }
    }
}

/** 착석 = 파란 칸 + ●, 벨트 신뢰 시 맨 좌석은 ✓. 빈 좌석 = 점선, 모름 = 점선 + ?. */
@Composable
private fun SeatTile(m: LobbyModel, seat: Seat, w: Dp, h: Dp, radius: Dp, icon: Dp) {
    val occ = m.state.seats.occupied[seat]
    val belted = m.state.seats.belted[seat] == true
    val shape = RoundedCornerShape(radius)
    if (occ == true) {
        Box(Modifier.size(w, h).clip(shape).background(SC.accent), contentAlignment = Alignment.Center) {
            if (m.info.beltTrusted && belted) ScIcon(R.drawable.ic_check, SC.accentOn, icon)
            else Box(Modifier.size(icon * 0.45f).clip(CircleShape).background(SC.accentOn))
        }
    } else {
        Box(Modifier.size(w, h).dashedBorder(radius), contentAlignment = Alignment.Center) {
            if (occ == null) Text("?", style = ScType.label, color = SC.textDisabled)
        }
    }
}

private fun Modifier.dashedBorder(radius: Dp) = drawBehind {
    val sw = 2.dp.toPx()
    drawRoundRect(
        SC.borderStrong, topLeft = Offset(sw / 2, sw / 2),
        size = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

/** 05 스트릭 (16:269 / 17:284). 이름은 「무사 배송」. */
@Composable
private fun StreakCard(info: LobbyInfo, large: Boolean, onDebugReset: (() -> Unit)?, modifier: Modifier = Modifier) {
    val on = info.streak > 0
    Card(modifier, padding = if (large) SC.gapXl else SC.gapLg, gap = if (large) SC.gap else SC.gapSm) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (large) SC.gapSm else SC.gapXs)) {
            ScIcon(R.drawable.ic_flame, if (on) SC.gameKey else SC.textDisabled, if (large) 32.dp else 28.dp)
            Text("무사 배송 ${info.streak}일", style = ScType.title, color = SC.text, modifier = Modifier.weight(1f))
            // 디버그 빌드 전용
            onDebugReset?.let { ScButton("기록 초기화", it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (large) SC.gapXs else SC.gap2xs)) {
            val filled = info.streak.coerceAtMost(info.streakDays)
            repeat(info.streakDays) { i ->
                Box(
                    Modifier.weight(1f).height(if (large) 12.dp else 10.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (i < filled) SC.gameKey else SC.elevated),
                )
            }
        }
        Text(
            if (on) "${info.streak}일 연속 무사히 모셨어요" else "오늘 무사히 모시면 1일",
            style = if (large) ScType.body else ScType.caption, color = SC.textDim,
        )
    }
}

/** 06 상태 안내 (16:282). */
@Composable
private fun GuideCard() {
    Card(bg = SC.elevated, gap = SC.gapSm) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapSm)) {
            ScIcon(R.drawable.ic_car, SC.accent, 32.dp)
            Text("출발하면 호위가 시작돼요", style = ScType.title, color = SC.text)
        }
        Text("달리는 동안엔 화면을 만지지 않아도 돼요. 상자를 열면 소리로 알려줄게요.", style = ScType.body, color = SC.textDim)
    }
}

// ---------------------------------------------------------------- 세로 (P)

@Composable
private fun LobbyPortrait(
    m: LobbyModel, fill: Boolean, locationGranted: Boolean,
    onPick: () -> Unit, onRequestLocation: () -> Unit, onDebugReset: (() -> Unit)?,
) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().let { if (fill) it else it.verticalScroll(scroll) }
                .padding(horizontal = SC.gapLg, vertical = SC.gap),
            verticalArrangement = Arrangement.spacedBy(SC.gap),
        ) {
            CargoCardPortrait(m, if (fill) Modifier.weight(1f) else Modifier, fill)
            DestinationCard(m, onPick, large = false)
            if (!locationGranted) LocationCard(onRequestLocation)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(SC.gap)) {
                MiniSeatCard(m, Modifier.weight(1f).fillMaxHeight())
                StreakCard(m.info, large = false, onDebugReset = null, modifier = Modifier.weight(1f).fillMaxHeight())
            }
            KeyCards(m.info.support, m.mult, compact = true)
            onDebugReset?.let { ScButton("기록 초기화", it, Modifier.fillMaxWidth()) }
        }
        GuideBar()
    }
}

/** 01 오늘의 카고 P (17:229): 왼쪽 그림 260 · 오른쪽 글. */
@Composable
private fun CargoCardPortrait(m: LobbyModel, modifier: Modifier, fill: Boolean) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(SC.radiusLg)).background(SC.surface).padding(SC.gapLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SC.gapLg),
    ) {
        Box(
            Modifier.width(260.dp).let { if (fill) it.fillMaxHeight() else it.height(240.dp) },
            contentAlignment = Alignment.Center,
        ) { CargoBox(Modifier.width(212.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SC.gapXs)) {
            Text("오늘의 카고", style = ScType.label, color = SC.textDim)
            NameWithBadge(m.cargoName, ScType.headline)
            Text(cargoLine(m), style = ScType.body, color = SC.textDim)
            SponsorLine(m, ScType.caption)
        }
    }
}

/** 04 좌석 P (17:263): 미니 좌석 2×2 + 패밀리 버프 배수. */
@Composable
private fun MiniSeatCard(m: LobbyModel, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(SC.radiusLg)).background(SC.surface).padding(SC.gapLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SC.gap),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SC.gap2xs)) {
            SEAT_ORDER.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(SC.gap2xs)) {
                    row.forEach { (seat, _) -> SeatTile(m, seat, 40.dp, 44.dp, SC.radiusSm, 22.dp) }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SC.gap2xs)) {
            Text("패밀리 버프", style = ScType.caption, color = SC.textDim)
            Text(m.mult, style = ScType.headline, color = if (m.buff) SC.accent else SC.textDisabled)
            Text(m.seatsSummary(short = true), style = ScType.caption, color = SC.textDim)
        }
    }
}

/** 06 상태 안내 P (17:320): 하단 고정. */
@Composable
private fun GuideBar() {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(SC.border))
        Row(
            Modifier.fillMaxWidth().background(SC.bgDeep).padding(horizontal = SC.gapLg, vertical = 25.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SC.gap),
        ) {
            IconCircle(R.drawable.ic_car, SC.accentOn, 56.dp, 28.dp, background = SC.accent)
            Column(verticalArrangement = Arrangement.spacedBy(SC.gap2xs)) {
                Text("출발하면 호위가 시작돼요", style = ScType.title, color = SC.text)
                Text("달리는 동안엔 만지지 않아도 돼요. 상자를 열면 소리로 알려줄게요", style = ScType.body, color = SC.textDim)
            }
        }
    }
}

// ---------------------------------------------------------------- 프리뷰

internal val previewDestination = Destination(
    "cafe", "베이커리 카페 예시", "예시동 한강로 7 (가상)", 37.7, 126.7, "coupon-cafe",
    com.safecargo.engine.DestCategory.CAFE, "바리스타의 수제 타르트 상자", "갓 구운 타르트가 부서지지 않게 조심히 모셔 주세요.",
)

internal val previewState = TripState(
    phase = Phase.LOADED,
    cargo = Cargo("cargo-cafe", "바리스타의 수제 타르트 상자", CargoKind.SPONSOR, previewDestination),
    seats = SeatSnapshot(
        occupied = mapOf(Seat.DRIVER to true, Seat.PASSENGER to true, Seat.REAR_LEFT to true, Seat.REAR_RIGHT to false),
        belted = mapOf(Seat.DRIVER to true, Seat.PASSENGER to true, Seat.REAR_LEFT to true, Seat.REAR_RIGHT to false),
    ),
)

@WindowPreviews
@Composable
private fun LobbyPreview() {
    val c = androidx.compose.ui.platform.LocalConfiguration.current
    val wc = windowClassOf(c.screenWidthDp, c.screenHeightDp)
    SafeCargoTheme {
        AppShell(previewState, streak = 3, support = SupportStatus(), wc = wc, buffActive = true) {
            LobbyScreen(
                previewState, wc, locationGranted = true, onPickDestination = {}, onRequestLocation = {}, onDebugReset = null,
                info = LobbyInfo(streak = 3, sponsorLabel = "베이커리 카페 예시", beltTrusted = true, buffActive = true, beltVerified = true),
            )
        }
    }
}

@Preview(name = "지원 배너 1700x1184", widthDp = 1700, heightDp = 1184)
@Composable
private fun LobbyBannerPreview() {
    val wc = windowClassOf(1700, 1184)
    val sup = SupportStatus(belts = false)
    SafeCargoTheme {
        AppShell(previewState, streak = 3, support = sup, wc = wc, buffActive = true) {
            LobbyScreen(previewState, wc, locationGranted = true, onPickDestination = {}, onRequestLocation = {}, onDebugReset = {},
                info = LobbyInfo(streak = 3, sponsorLabel = "베이커리 카페 예시", support = sup, buffActive = true))
        }
    }
}

@Preview(name = "IDLE · 권한 없음 1700x1184", widthDp = 1700, heightDp = 1184)
@Composable
private fun LobbyIdlePreview() {
    val wc = windowClassOf(1700, 1184)
    SafeCargoTheme {
        AppShell(TripState(), streak = 0, support = SupportStatus(turn = false), wc = wc) {
            LobbyScreen(TripState(), wc, locationGranted = false, onPickDestination = {}, onRequestLocation = {}, onDebugReset = {},
                info = LobbyInfo(support = SupportStatus(turn = false)))
        }
    }
}

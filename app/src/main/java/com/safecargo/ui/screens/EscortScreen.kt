package com.safecargo.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecargo.app.R
import com.safecargo.engine.CargoKind
import com.safecargo.engine.Gear
import com.safecargo.engine.JudgeState
import com.safecargo.engine.Phase
import com.safecargo.engine.TripState
import com.safecargo.ui.components.ScButton
import com.safecargo.ui.components.ScIcon
import com.safecargo.ui.components.multiplierLabel
import com.safecargo.ui.shell.Orientation
import com.safecargo.ui.shell.WindowClass
import com.safecargo.ui.shell.windowClassOf
import com.safecargo.ui.theme.SC
import com.safecargo.ui.theme.SafeCargoTheme
import com.safecargo.ui.theme.ScType
import kotlin.math.floor

private const val OUT_OF_RANGE = "목적지 근처가 아니에요 — 지금 열면 일반 리워드"
private const val NEED_STOP = "차를 완전히 세우면 열 수 있어요"

/**
 * SC-03 호위 · SC-04 일시 정차.
 * ESCORTING: 상자 그림 · 열쇠 아이콘 · 숫자 · 버프 아이콘만 — 문장 · 버튼 · 애니메이션 없음 (`drivingScreen="static"`).
 * PAUSED: 같은 화면 + [도착 · 개봉] (상태 칩은 [com.safecargo.ui.shell.AppShell] 이 그린다).
 *
 * 도착 흐름 (엔진): ArriveConfirmed → 스폰서 카고는 checkingArrival=true + 위치 1회 요청, 해소되면 반경 안일 때
 * 자동 개봉(reduce 의 autoOpen). 반경 밖이어도 openAfterLocation 이라 개봉은 되지만 리워드는 일반. 데일리는 즉시 개봉.
 */
@Composable
fun EscortScreen(
    state: TripState,
    wc: WindowClass,
    onArrive: () -> Unit,
    buffActive: Boolean = false,
    buffMultiplier: Double = 1.5,
) {
    val paused = state.phase == Phase.PAUSED
    val keys = floor(state.judge.keys).toInt()
    val keysRow: @Composable () -> Unit = { KeysRow(keys, buffActive, buffMultiplier) }
    val panel: @Composable () -> Unit = { if (paused) ArrivePanel(state, onArrive) }

    if (wc.orientation == Orientation.L) {
        Row(
            Modifier.fillMaxSize().padding(SC.gapXl),
            horizontalArrangement = Arrangement.spacedBy(SC.gapXl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(0.55f).fillMaxHeight().clip(RoundedCornerShape(SC.radiusLg)).background(SC.surface), contentAlignment = Alignment.Center) {
                CargoBox(Modifier.fillMaxHeight(0.7f))
            }
            Column(Modifier.weight(0.45f), verticalArrangement = Arrangement.spacedBy(SC.gapXl), horizontalAlignment = Alignment.CenterHorizontally) {
                keysRow()
                panel()
            }
        }
    } else {
        Column(
            Modifier.fillMaxSize().padding(SC.gapLg),
            verticalArrangement = Arrangement.spacedBy(SC.gapLg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(SC.radiusLg)).background(SC.surface), contentAlignment = Alignment.Center) {
                CargoBox(Modifier.fillMaxHeight(0.7f))
            }
            keysRow()
            panel()
        }
    }
}

/** 열쇠 3종 아이콘 + 열쇠 수 + (버프 중일 때만) 패밀리 버프 아이콘. 읽기 전용 — 값이 바뀌면 숫자만 바뀐다. */
@Composable
private fun KeysRow(keys: Int, buffActive: Boolean, mult: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapLg)) {
        ScIcon(R.drawable.ic_signal, SC.gameKey, 56.dp)
        ScIcon(R.drawable.ic_steering, SC.gameKey, 56.dp)
        ScIcon(R.drawable.ic_family, SC.gameKey, 56.dp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapXs)) {
            ScIcon(R.drawable.ic_key, SC.gameKey, 64.dp)
            Text(keys.toString(), style = ScType.headline.copy(fontSize = 96.sp), color = SC.text)
        }
        if (buffActive) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(SC.accent).padding(horizontal = SC.gap, vertical = SC.gapXs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SC.gapXs),
            ) {
                ScIcon(R.drawable.ic_family, SC.accentOn, 32.dp)
                Text(multiplierLabel(mult), style = ScType.title, color = SC.accentOn)
            }
        }
    }
}

@Composable
private fun ArrivePanel(state: TripState, onArrive: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    val sponsor = state.cargo?.kind == CargoKind.SPONSOR
    // 스폰서 PAUSED 는 항상 확인 중으로 시작하므로, 확인이 끝났는데(!checking) 인증이 안 됐다면 반경 밖이다.
    val outOfRange = sponsor && !state.checkingArrival && !state.arrivalVerified
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SC.gap), horizontalAlignment = Alignment.CenterHorizontally) {
        if (outOfRange) Text(OUT_OF_RANGE, style = ScType.body, color = SC.gameKey, textAlign = TextAlign.Center)
        ArriveButton(state.canArrive, state.checkingArrival) { confirm = true }
        if (!state.canArrive && !state.checkingArrival) Text(NEED_STOP, style = ScType.body, color = SC.textDim, textAlign = TextAlign.Center)
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = SC.surface,
            title = { Text("여기서 상자를 열까요?", style = ScType.title, color = SC.text) },
            confirmButton = { ScButton("열기", { confirm = false; onArrive() }, primary = true) },
            dismissButton = { ScButton("취소", { confirm = false }) },
        )
    }
}

@Composable
private fun ArriveButton(enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    if (enabled) {
        ScButton("도착 · 개봉", onClick, Modifier.fillMaxWidth().heightIn(min = 88.dp), primary = true)
    } else {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 88.dp).clip(RoundedCornerShape(SC.radius)).background(SC.elevated),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (loading) "도착 확인 중…" else "도착 · 개봉", style = ScType.label, color = SC.textDisabled.takeIf { !loading } ?: SC.textDim)
        }
    }
}

// ---------------------------------------------------------------- 프리뷰

private val previewSeats = previewState.seats

@androidx.compose.ui.tooling.preview.Preview(name = "호위 1700x1184", widthDp = 1700, heightDp = 1184)
@Composable
private fun EscortPreview() = Host(previewState.copy(phase = Phase.ESCORTING, judge = JudgeState(keys = 2.6), seats = previewSeats), 1700, 1184)

@androidx.compose.ui.tooling.preview.Preview(name = "정차 가능 800x1400", widthDp = 800, heightDp = 1400)
@Composable
private fun PausedOkPreview() = Host(previewState.copy(phase = Phase.PAUSED, gear = Gear.PARK, uxRestricted = false, judge = JudgeState(keys = 3.0)), 800, 1400)

@androidx.compose.ui.tooling.preview.Preview(name = "정차 불가 1640x810", widthDp = 1640, heightDp = 810)
@Composable
private fun PausedNoPreview() = Host(previewState.copy(phase = Phase.PAUSED, gear = Gear.PARK, uxRestricted = true, checkingArrival = true), 1640, 810)

@Composable
private fun Host(s: TripState, w: Int, h: Int) {
    val wc = windowClassOf(w, h)
    SafeCargoTheme {
        com.safecargo.ui.shell.AppShell(s, streak = 3, support = com.safecargo.sdk.SupportStatus(), wc = wc, buffActive = true) {
            EscortScreen(s, wc, onArrive = {}, buffActive = true)
        }
    }
}

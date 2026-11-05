package com.safecargo.ui.screens

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.safecargo.ui.components.IconCircle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.safecargo.app.R
import com.safecargo.engine.ArrivalCheck
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

private const val OUT_OF_RANGE = "목적지 근처가 아니에요 — 지금 열면 일반 리워드"
private const val NO_FIX = "도착 인증 없음 — 지금 열면 일반 리워드"
private const val NEED_STOP = "차를 완전히 세우면 열 수 있어요"

/**
 * SC-03 호위 · SC-04 일시 정차 (피그마 51:480 가로 · 51:572 세로).
 * ESCORTING: 카고 그림 · 열쇠 게이지(숫자 숨김) · 매너 아이콘 3칸 · 터치 잠금 표시만 — 문장 · 버튼 없음.
 * 열쇠 숫자는 언박싱에서 처음 공개한다 (11/03 디자인 요청 §1 · PRD 리뷰 H3).
 * 상자는 [idleLoop] 일 때 아주 천천히 흔들리는 대기 루프 하나만 — 깜빡이 · 조향 같은 이벤트에는 반응하지 않는다.
 * PAUSED: 같은 화면 + [도착 · 개봉] (상태 칩은 [com.safecargo.ui.shell.AppShell] 이 그린다).
 *
 * HP 게이지 · 호위 거리 칸은 피그마에 있으나 그리지 않는다: HP 는 1차에서 뺐고(설계 A2 · 가점만),
 * 트립 거리는 이 SDK 가 지원하지 않는다(POC ISSUE-05).
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
    /** 게이지가 가득 차는 열쇠 수 — 마지막 등급 기준값. */
    keyGoal: Double = 120.0,
    /** `drivingScreen != "static"` — 상자 대기 루프. */
    idleLoop: Boolean = true,
) {
    val paused = state.phase == Phase.PAUSED
    val progress = keyProgress(state.judge.keys, keyGoal)
    val destName = state.cargo?.destination?.name
    val cargo: @Composable (Modifier) -> Unit = { m -> CargoStage(destName, idleLoop, m) }
    val gauge: @Composable (Modifier) -> Unit = { m -> KeyGaugeCard(progress, m) }
    val manners: @Composable () -> Unit = { MannerRow(buffActive, buffMultiplier) }
    val bottom: @Composable () -> Unit = { if (paused) ArrivePanel(state, onArrive) else LockRow() }

    if (wc.orientation == Orientation.L) {
        Row(Modifier.fillMaxSize().padding(SC.gapXl), horizontalArrangement = Arrangement.spacedBy(SC.gapXl)) {
            cargo(Modifier.weight(0.55f).fillMaxHeight())
            Column(Modifier.weight(0.45f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(SC.gapLg)) {
                gauge(Modifier.weight(1f).fillMaxWidth())
                manners()
                bottom()
            }
        }
    } else {
        // 위 카고 · 아래 게이지 · 스크롤 없음 (51:659).
        Column(Modifier.fillMaxSize().padding(SC.gapLg), verticalArrangement = Arrangement.spacedBy(SC.gap)) {
            cargo(Modifier.weight(1f).fillMaxWidth())
            gauge(Modifier.fillMaxWidth().heightIn(min = 240.dp))
            manners()
            bottom()
        }
    }
}

/** 열쇠 게이지 채움 0..1. */
internal fun keyProgress(keys: Double, goal: Double): Float =
    if (goal <= 0.0) 0f else (keys / goal).toFloat().coerceIn(0f, 1f)

/** 01 카고 상태 (51:515): 광채 · 받침 테 · 상자 + 왼쪽 위 목적지 칩. */
@Composable
private fun CargoStage(destName: String?, idleLoop: Boolean, modifier: Modifier) {
    val glow = SC.gameKey
    val ring = SC.borderStrong
    Box(modifier.clip(RoundedCornerShape(SC.radiusLg)).background(SC.surface), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            drawCircle(
                Brush.radialGradient(listOf(glow.copy(alpha = 0.16f), Color.Transparent), center = c, radius = size.minDimension * 0.55f),
                radius = size.minDimension * 0.55f, center = c,
            )
            // 받침 테 — 상자 바닥(그림자 높이)을 감싸는 점선 타원. 상자 크기는 아래 CargoBox 와 같은 계산.
            val chestW = minOf(size.width * CHEST_W, size.height * CHEST_H * CHEST_AR)
            val chestH = chestW / CHEST_AR
            val w = chestW * 1.3f
            val h = w * 0.2f
            drawOval(
                ring.copy(alpha = 0.6f), Offset(c.x - w / 2, c.y + chestH * 0.41f - h / 2), Size(w, h),
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
            )
        }
        CargoBox(
            Modifier.fillMaxWidth(CHEST_W).fillMaxHeight(CHEST_H).aspectRatio(CHEST_AR)
                .then(if (idleLoop) Modifier.idleWobble() else Modifier),
        )
        if (destName != null) {
            Row(
                Modifier.align(Alignment.TopStart).padding(SC.gapXl).clip(RoundedCornerShape(50)).background(SC.elevated)
                    .padding(start = SC.gap, end = 20.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SC.gapSm),
            ) {
                ScIcon(R.drawable.ic_pin, SC.accent, 28.dp)
                Text(destName, style = ScType.label, color = SC.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * 대기 루프 (11/03 잠정 사양): 회전 ±2° · 위아래 4dp · 한 주기 4초 · ease-in-out · 무한 반복.
 * 깜빡임 · 크기 · 색 변화 없음.
 */
@Composable
private fun Modifier.idleWobble(): Modifier {
    val t by rememberInfiniteTransition(label = "idle").animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(IDLE_HALF_MS, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "wobble",
    )
    // graphicsLayer 회전은 레이어 가장자리에 금색 세로 머리카락 선을 남겨(에뮬 실측) 그리기 단계에서 변환한다.
    return drawWithContent {
        val dy = t * IDLE_DY.toPx()
        withTransform({
            translate(top = dy)
            rotate(t * IDLE_DEG)
        }) { this@drawWithContent.drawContent() }
    }
}

private const val CHEST_W = 0.62f
private const val CHEST_H = 0.62f
private const val CHEST_AR = 260f / 206f
private const val IDLE_HALF_MS = 2000
private const val IDLE_DEG = 2f
private val IDLE_DY = 4.dp

/** 03 열쇠 게이지 (52:529): 원형, 숫자 없이 열쇠 아이콘만. 값이 바뀌면 천천히 채운다. */
@Composable
private fun KeyGaugeCard(progress: Float, modifier: Modifier) {
    val p by animateFloatAsState(progress, tween(800), label = "keys")
    val key = SC.gameKey
    val track = SC.gameKeySubtle
    Box(
        modifier.clip(RoundedCornerShape(SC.radiusLg)).background(SC.surface).padding(SC.gapXl)
            .semantics { contentDescription = "열쇠 게이지" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 28.dp.toPx()
                val inset = sw / 2
                val arcSize = Size(size.width - sw, size.height - sw)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
                drawArc(key, -90f, 360f * p, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            }
            ScIcon(R.drawable.ic_key, key, 72.dp)
        }
    }
}

/** 매너 3종 (52:537): 깜빡이 먼저 · 부드러운 조향 · 모두 벨트 + 패밀리 버프. 아이콘만, 상태에 따라 바뀌지 않는다. */
@Composable
private fun MannerRow(buffActive: Boolean, mult: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SC.gapLg)) {
        MannerCell(Modifier.weight(1f)) { IconCircle(R.drawable.ic_signal, SC.textDim, 72.dp, 36.dp) }
        MannerCell(Modifier.weight(1f)) { IconCircle(R.drawable.ic_steering, SC.textDim, 72.dp, 36.dp) }
        MannerCell(Modifier.weight(1f)) {
            if (buffActive) {
                IconCircle(R.drawable.ic_family, SC.gameKey, 72.dp, 36.dp, background = SC.gameKeySubtle)
                Text(multiplierLabel(mult), style = ScType.headline, color = SC.text, maxLines = 1, softWrap = false)
            } else {
                IconCircle(R.drawable.ic_family, SC.textDisabled, 72.dp, 36.dp)
            }
        }
    }
}

@Composable
private fun MannerCell(modifier: Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(SC.radiusLg)).background(SC.surface).padding(horizontal = SC.gapSm, vertical = SC.gapLg),
        horizontalArrangement = Arrangement.spacedBy(SC.gapSm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** 07 터치 잠금 (51:567): 주행 중 화면 입력이 막혀 있다는 표시. */
@Composable
private fun LockRow() {
    Box(Modifier.fillMaxWidth().padding(horizontal = SC.gapLg), contentAlignment = Alignment.CenterEnd) {
        ScIcon(R.drawable.ic_lock, SC.textDisabled, 40.dp, Modifier.semantics { contentDescription = "주행 중 터치 잠금" })
    }
}

@Composable
private fun ArrivePanel(state: TripState, onArrive: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    // 열 수 없게 되면(다시 출발 · UX 제한 · 확인 시작) 열려 있던 확인 창을 닫는다.
    LaunchedEffect(state.canArrive) { if (!state.canArrive) confirm = false }
    val note = arrivalNote(state)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SC.gap), horizontalAlignment = Alignment.CenterHorizontally) {
        if (note != null) Text(note, style = ScType.body, color = SC.gameKey, textAlign = TextAlign.Center)
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

/**
 * 스폰서 카고의 도착 확인이 끝났는데 인증이 안 됐을 때의 안내.
 * 위치를 받았고 반경 밖일 때만 「근처가 아니에요」, 위치를 못 받았으면(권한 거부 · 시간 초과 · 오래된 값) 「인증 없음」.
 */
internal fun arrivalNote(state: TripState): String? {
    if (state.cargo?.kind != CargoKind.SPONSOR || state.checkingArrival || state.arrivalVerified) return null
    return when (state.arrivalCheck) {
        ArrivalCheck.OUT_OF_RANGE -> OUT_OF_RANGE
        ArrivalCheck.VERIFIED -> null
        // NO_FIX, 그리고 이 필드가 없던 옛 저장본(NONE) — 위치를 받았는지 모르므로 「반경 밖」 이라 말하지 않는다.
        ArrivalCheck.NO_FIX, ArrivalCheck.NONE -> NO_FIX
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
private fun EscortPreview() = Host(previewState.copy(phase = Phase.ESCORTING, judge = JudgeState(keys = 70.0), seats = previewSeats), 1700, 1184)

@androidx.compose.ui.tooling.preview.Preview(name = "호위 라이트 840x1184", widthDp = 840, heightDp = 1184)
@Composable
private fun EscortLightPreview() = Host(previewState.copy(phase = Phase.ESCORTING, judge = JudgeState(keys = 70.0), seats = previewSeats), 840, 1184, dark = false)

@androidx.compose.ui.tooling.preview.Preview(name = "정차 가능 800x1400", widthDp = 800, heightDp = 1400)
@Composable
private fun PausedOkPreview() = Host(previewState.copy(phase = Phase.PAUSED, gear = Gear.PARK, uxRestricted = false, judge = JudgeState(keys = 3.0)), 800, 1400)

@androidx.compose.ui.tooling.preview.Preview(name = "정차 불가 1640x810", widthDp = 1640, heightDp = 810)
@Composable
private fun PausedNoPreview() = Host(previewState.copy(phase = Phase.PAUSED, gear = Gear.PARK, uxRestricted = true, checkingArrival = true), 1640, 810)

@Composable
private fun Host(s: TripState, w: Int, h: Int, dark: Boolean = true) {
    val wc = windowClassOf(w, h)
    SafeCargoTheme(dark = dark) {
        com.safecargo.ui.shell.AppShell(s, streak = 3, support = com.safecargo.sdk.SupportStatus(), wc = wc, buffActive = true) {
            EscortScreen(s, wc, onArrive = {}, buffActive = true)
        }
    }
}

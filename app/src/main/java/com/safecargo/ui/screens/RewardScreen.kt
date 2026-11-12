package com.safecargo.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.safecargo.app.R
import com.safecargo.data.Reward
import com.safecargo.data.RewardType
import com.safecargo.engine.Grade
import com.safecargo.engine.JudgeMoment
import com.safecargo.engine.MomentKind
import com.safecargo.engine.TripResult
import com.safecargo.engine.TripSummary
import com.safecargo.ui.components.GuideInfoBox
import com.safecargo.ui.components.TokenAmount
import com.safecargo.ui.components.IconCircle
import com.safecargo.ui.components.ScButton
import com.safecargo.ui.components.ScIcon
import com.safecargo.ui.shell.HeightClass
import com.safecargo.ui.shell.Orientation
import com.safecargo.ui.shell.WindowClass
import com.safecargo.ui.shell.windowClassOf
import com.safecargo.ui.theme.SC
import com.safecargo.ui.theme.SafeCargoTheme
import com.safecargo.ui.theme.ScType

/**
 * SC-06 의 v1.1 추가분 (FR-APP-013 · 015 · 016): 토큰 지급 줄 · 사용 가이드 · [보상함].
 * @param tokenGain 이번 상자로 받은 토큰 (아쉬운 상자 +3 · 포인트 +1 · 쿠폰 0).
 * @param guideExpanded 가이드 info box 를 펼친 채 시작 — 처음 [com.safecargo.data.RewardConfig.guideExpandedTimes] 회만.
 */
data class RewardExtras(
    val tokenGain: Int = 0,
    val guideExpanded: Boolean = false,
    val payback: Int = 2,
    val bonusCost: Int = 10,
    val emptyTokens: Int = 3,
    val onWallet: (() -> Unit)? = null,
    val onGuideShown: () -> Unit = {},
)

/**
 * SC-06 리워드. 등급 · 열쇠 · 버프 · 리워드 카드(모두 「예시」) · 면책 문구 · 「이번 호위」 요약 · 리플레이.
 * 주행 중엔 정적 피드백뿐이라(A1) 깜빡이 조합과 비교는 여기서 처음 보여준다.
 *
 * @param buffMultiplier 설정값 [com.safecargo.engine.JudgeConfig.familyBuffMultiplier] — 「×1.5」 하드코딩 금지.
 */
@Composable
fun RewardScreen(
    result: TripResult,
    reward: Reward?,
    wc: WindowClass,
    onClose: () -> Unit,
    buffMultiplier: Double,
    extras: RewardExtras = RewardExtras(),
) {
    val compact = wc.height == HeightClass.S
    // P 는 항상 접힌 채 시작 (SC-06 07).
    val guideOpen = extras.guideExpanded && wc.orientation == Orientation.L
    LaunchedEffect(result.id) { if (guideOpen) extras.onGuideShown() }
    val rewardBlock: @Composable () -> Unit = { RewardBlock(result, reward, compact, extras, guideOpen) }
    val pad = if (compact) SC.gapLg else SC.gapXl
    val gap = if (compact) SC.gapSm else SC.gap

    // 리플레이: 처음 한 번 + [다시 보기] 마다 3초.
    var replayToken by remember(result.id) { mutableIntStateOf(0) }
    val replay = remember(result.id) { Animatable(0f) }
    LaunchedEffect(result.id, replayToken) {
        replay.snapTo(0f)
        replay.animateTo(1f, tween(REPLAY_MS, easing = LinearEasing))
    }
    val moments = remember(result.summary.moments) { replayOrder(result.summary.moments) }
    val hi = highlightIndex(replay.value, moments.size)
    val playing = replay.value < 1f

    Column(Modifier.fillMaxSize().padding(horizontal = pad, vertical = if (compact) SC.gap else SC.gapLg)) {
        if (wc.orientation == Orientation.L) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SC.gapXl)) {
                if (compact) {
                    // S: 높이가 모자라 히어로 없이 스크롤 (긴 이름 · 도착 인증 없음 · 큰 글꼴에서도 잘리지 않게).
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) { ResultHeader(result, buffMultiplier, compact); rewardBlock() }
                } else {
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        ResultHeader(result, buffMultiplier, compact)
                        // 히어로: 열린 상자 — 남는 높이를 채워 왼쪽 단의 빈 공간을 없앤다.
                        GradeHero(result.grade, reward?.type == RewardType.EMPTY, Modifier.weight(1f).fillMaxWidth().heightIn(min = 160.dp))
                        RewardCard(result, reward, compact)
                        TokenGainLine(extras.tokenGain)
                    }
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    if (!compact) RewardGuide(reward, extras, guideOpen)
                    SummaryCard(result.summary, result.beltVerified, compact); ReplayCard(moments, hi, playing, compact)
                }
            }
        } else {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                ResultHeader(result, buffMultiplier, compact)
                rewardBlock()
                SummaryCard(result.summary, result.beltVerified, compact)
                ReplayCard(moments, hi, playing, compact)
            }
        }
        // 버튼은 스크롤 밖 — 작은 창에서도 항상 보인다.
        Row(
            Modifier.fillMaxWidth().padding(top = gap),
            horizontalArrangement = Arrangement.spacedBy(SC.gap, Alignment.End),
        ) {
            if (moments.isNotEmpty()) ScButton("다시 보기", { replayToken++ })
            extras.onWallet?.let { ScButton("보상함", it) }
            ScButton("닫기", onClose, Modifier.widthIn(min = 200.dp), primary = true)
        }
    }
}

/** 등급 배지 + 카고 이름 + 열쇠 + 버프. */
@Composable
private fun ResultHeader(r: TripResult, mult: Double, compact: Boolean) {
    val color = gradeColor(r.grade)
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) SC.gapXs else SC.gapSm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gap)) {
            GradeBadge(r.grade, compact)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SC.gap2xs)) {
                Text("배송 완료", style = ScType.caption, color = SC.textDim)
                LabelWithBadge(r.cargoName, style = ScType.title)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SC.gapSm),
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(SC.radius)).background(SC.surface)
                    .padding(horizontal = SC.gap, vertical = SC.gapSm)
                    .semantics { contentDescription = "열쇠 ${r.keys}개" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SC.gapXs),
            ) {
                ScIcon(R.drawable.ic_key, SC.gameKey, 32.dp)
                Text("열쇠 ${r.keys}개", style = ScType.title, color = SC.gameKeyText)
            }
            buffLabel(r, mult)?.let { label ->
                Row(
                    Modifier.clip(RoundedCornerShape(SC.radius)).border(1.5.dp, color, RoundedCornerShape(SC.radius))
                        .padding(horizontal = SC.gap, vertical = SC.gapSm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SC.gapXs),
                ) {
                    ScIcon(R.drawable.ic_family, SC.text, 28.dp)
                    Text(label, style = ScType.label, color = SC.text)
                }
            }
        }
    }
}

/** L 왼쪽 단 히어로 — 언박싱 마지막 장면(열린 상자 + 등급 빛). 아쉬운 상자면 빈 상자 그림. */
@Composable
private fun GradeHero(g: Grade, empty: Boolean, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(SC.radiusLg)).background(SC.surface),
        contentAlignment = Alignment.Center,
    ) { GradeBoxIcon(g, Modifier.fillMaxSize(), opened = true, empty = empty) }
}

/** 등급 3중 표기 — 상자 모양 + 색 + 이름. */
@Composable
private fun GradeBadge(g: Grade, compact: Boolean) {
    val color = gradeColor(g)
    Row(
        Modifier.clip(RoundedCornerShape(SC.radiusLg)).background(color.copy(alpha = 0.14f))
            .border(2.dp, color, RoundedCornerShape(SC.radiusLg))
            .padding(end = SC.gapLg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradeBoxIcon(g, Modifier.size(if (compact) 76.dp else 96.dp))
        Text(gradeName(g), style = ScType.headline, color = gradeTextColor(g))
    }
}

@Composable
private fun LabelWithBadge(text: String, style: TextStyle, color: Color = SC.text) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapSm)) {
        Text(text, style = style, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun Card(compact: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(SC.radiusLg)).background(SC.surface)
            .padding(if (compact) SC.gap else SC.gapLg),
        verticalArrangement = Arrangement.spacedBy(if (compact) SC.gapXs else SC.gapSm),
        content = content,
    )
}

/** 리워드 카드 + 토큰 줄 + 가이드 (P · S 의 한 줄 흐름). */
@Composable
private fun RewardBlock(r: TripResult, reward: Reward?, compact: Boolean, extras: RewardExtras, guideOpen: Boolean) {
    RewardCard(r, reward, compact)
    TokenGainLine(extras.tokenGain)
    RewardGuide(reward, extras, guideOpen)
}

/** 「토큰 +3 받았어요」 — 0 이면 그리지 않는다. */
@Composable
private fun TokenGainLine(n: Int) {
    if (n <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapXs)) {
        TokenAmount("+$n", style = ScType.title)
        Text("토큰을 받았어요", style = ScType.body, color = SC.text)
    }
}

/** 쿠폰이면 쓰는 법, 아쉬운 상자면 토큰 모으는 법 (SC-06 07). 포인트는 가이드 없음. */
@Composable
private fun RewardGuide(reward: Reward?, extras: RewardExtras, open: Boolean) {
    when (reward?.type) {
        RewardType.COUPON -> GuideInfoBox(GUIDE_TITLE, guideSteps(extras.payback, extras.bonusCost), open, tokenLast = true)
        RewardType.EMPTY -> GuideInfoBox(
            TOKEN_GUIDE_TITLE, tokenGuideSteps(extras.emptyTokens, extras.payback, extras.bonusCost), open, inline = true,
        )
        else -> Unit
    }
}

/** 아쉬운 상자 카드 — 「꽝」「실패」 · 운전 실수와 연결하는 말은 쓰지 않는다 (SC-06 06). */
@Composable
private fun EmptyBoxCard(compact: Boolean) {
    Card(compact) {
        Text("아쉬운 상자", style = ScType.caption, color = SC.textDim)
        Text(EMPTY_BOX_TITLE, style = ScType.title, color = SC.text)
        Text(EMPTY_BOX_NEXT, style = ScType.body, color = SC.textDim)
    }
}

/** 리워드 카드 — Mock 값마다 바로 오른쪽에 「예시」 (SD-08). */
@Composable
private fun RewardCard(r: TripResult, reward: Reward?, compact: Boolean) {
    if (reward?.type == RewardType.EMPTY) return EmptyBoxCard(compact)
    Card(compact) {
        Text(rewardTypeLabel(reward?.type), style = ScType.caption, color = SC.textDim)
        if (reward != null) {
            LabelWithBadge(reward.title, style = ScType.title)
            LabelWithBadge(reward.amountLabel, style = ScType.headline, color = gradeTextColor(r.grade))
            // 「예시」 꼬리표는 앱 바 한 줄로 모았다 (11/10 오후 — AppShell.DEMO_BAR_NOTE).
            reward.partnerLabel?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapSm)) {
                    Text(it, style = ScType.body, color = SC.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text("제공", style = ScType.body, color = SC.textDim)
                }
            }
        } else {
            Text("보상 정보를 불러오지 못했어요", style = ScType.body, color = SC.textDim)
        }
        if (showNoArrival(r)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapXs)) {
                ScIcon(R.drawable.ic_pin, SC.warning, 24.dp)
                Text(NO_ARRIVAL, style = ScType.label, color = SC.warning)
            }
        }
        Text(REWARD_DISCLAIMER, style = ScType.caption, color = SC.textDim)
    }
}

private fun rewardTypeLabel(t: RewardType?): String = when (t) {
    RewardType.COUPON -> "쿠폰"
    RewardType.POINT -> "포인트"
    RewardType.BADGE -> "배지"
    RewardType.EMPTY -> "아쉬운 상자"
    null -> "보상"
}

/** 「이번 호위」 — 잘한 것만: 깜빡이 먼저 N회 · 퍼펙트 K회 · 부드러운 회전 J회. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(s: TripSummary, beltVerified: Boolean, compact: Boolean) {
    // 피그마 SC-06 「이번 호위 (잘한 것만)」: 연한 파랑 칩 + 파랑 체크. 0회는 칩 없음, 모두 0이면 카드 없음.
    val chips = summaryChips(s, beltVerified)
    if (chips.isEmpty()) return
    val line = chips.joinToString(" · ") { it.text }
    Card(compact) {
        Text("이번 호위", style = ScType.label, color = SC.textDim)
        FlowRow(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = line },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            chips.forEach { chip ->
                Row(
                    Modifier.clip(CircleShape).background(SC.tintAccent).padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SC.gapXs),
                ) {
                    ScIcon(R.drawable.ic_check, SC.accent, 25.dp)   // 앱 ic_check 은 여백이 커서 25dp ≈ 피그마 22dp 체크 글리프
                    Text(chip.text, style = ScType.label, color = SC.text, maxLines = 1)
                }
            }
        }
    }
}

/** 리플레이 — 잘한 판정 순간을 시간순 아이콘으로, 3초 동안 하나씩 하이라이트. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReplayCard(moments: List<JudgeMoment>, hi: Int, playing: Boolean, compact: Boolean) {
    Card(compact) {
        Text("다시 보기 · 시간순", style = ScType.label, color = SC.text)
        if (moments.isEmpty()) {
            Text("이번엔 기록된 순간이 없었어요", style = ScType.body, color = SC.textDim)
            return@Card
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SC.gapSm),
            verticalArrangement = Arrangement.spacedBy(SC.gapSm),
        ) {
            moments.forEachIndexed { i, m ->
                val on = i == hi
                val upcoming = playing && hi >= 0 && i > hi
                val c = momentColor(m.kind)
                Column(
                    Modifier.width(if (compact) 92.dp else 104.dp).alpha(if (upcoming) 0.3f else 1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SC.gap2xs),
                ) {
                    Box(Modifier.scale(if (on) 1.2f else 1f)) {
                        IconCircle(
                            momentIcon(m.kind), if (on) SC.bg else c,
                            if (compact) 52.dp else 60.dp, if (compact) 26.dp else 30.dp,
                            Modifier.then(if (on) Modifier.border(3.dp, SC.text, CircleShape) else Modifier),
                            background = if (on) c else SC.elevated,
                        )
                    }
                    Text(
                        momentLabel(m.kind), style = ScType.caption, color = if (on) SC.text else SC.textDim,
                        textAlign = TextAlign.Center, maxLines = 2,
                    )
                }
            }
        }
    }
}

private fun momentIcon(k: MomentKind): Int = when (k) {
    MomentKind.SIGNAL_FIRST, MomentKind.PERFECT_SIGNAL, MomentKind.NO_SIGNAL_TURN -> R.drawable.ic_signal
    MomentKind.SMOOTH_TURN, MomentKind.HARSH_TURN -> R.drawable.ic_steering
}

private fun momentColor(k: MomentKind): Color = when (k) {
    MomentKind.PERFECT_SIGNAL -> SC.gameKey
    MomentKind.SIGNAL_FIRST, MomentKind.SMOOTH_TURN -> SC.accent
    MomentKind.NO_SIGNAL_TURN, MomentKind.HARSH_TURN -> SC.textDim
}

// ---------------------------------------------------------------- 프리뷰

internal fun previewResult(
    grade: Grade,
    arrival: Boolean = true,
    daily: Boolean = false,
    buff: Boolean = true,
) = TripResult(
    id = "preview-$grade", cargoName = if (daily) "오늘의 데일리 카고" else "베이커리 카페 예시 카고",
    destinationName = if (daily) null else "베이커리 카페 예시", grade = grade,
    keys = when (grade) { Grade.RARE -> 42; Grade.EPIC -> 96; Grade.LEGENDARY -> 150 },
    familyBuff = buff, beltVerified = false, arrivalVerified = arrival,
    rewardId = when { daily -> "daily"; arrival -> "coupon-cafe"; else -> "general" }, epochDay = 0,
    summary = TripSummary(
        signalFirst = 2, noSignalTurns = 1, perfect = 1, smoothTurns = 2,
        moments = listOf(
            JudgeMoment(1000, MomentKind.PERFECT_SIGNAL), JudgeMoment(3000, MomentKind.SMOOTH_TURN),
            JudgeMoment(6000, MomentKind.NO_SIGNAL_TURN), JudgeMoment(8000, MomentKind.HARSH_TURN),
            JudgeMoment(12000, MomentKind.SIGNAL_FIRST), JudgeMoment(14000, MomentKind.SMOOTH_TURN),
        ),
    ),
)

private val previewCafe = Reward("coupon-cafe", "베이커리 카페 빵 쿠폰", RewardType.COUPON, "빵 1개", "베이커리 카페 예시")
private val previewGeneral = Reward("general", "일반 배송 포인트", RewardType.POINT, "100P")
private val previewDaily = Reward("daily", "데일리 카고 포인트", RewardType.POINT, "50P")

@Preview(name = "리워드 레어 · 인증 1700x1184", widthDp = 1700, heightDp = 1184)
@Composable
private fun RewardRarePreview() = RewardHost(previewResult(Grade.RARE), previewCafe, 1700, 1184)

@Preview(name = "리워드 에픽 · 인증 없음 800x1400", widthDp = 800, heightDp = 1400)
@Composable
private fun RewardEpicNoArrivalPreview() = RewardHost(previewResult(Grade.EPIC, arrival = false), previewGeneral, 800, 1400)

@Preview(name = "리워드 레전더리 · 데일리 1640x810", widthDp = 1640, heightDp = 810)
@Composable
private fun RewardLegendaryDailyPreview() =
    RewardHost(previewResult(Grade.LEGENDARY, arrival = false, daily = true, buff = false), previewDaily, 1640, 810)

@Preview(name = "리워드 레어 · 인증 648x800", widthDp = 648, heightDp = 800)
@Composable
private fun RewardSmallPreview() = RewardHost(previewResult(Grade.RARE), previewCafe, 648, 800)

@Composable
private fun RewardHost(r: TripResult, reward: Reward, w: Int, h: Int) {
    SafeCargoTheme {
        Box(Modifier.fillMaxSize().background(SC.bg)) { RewardScreen(r, reward, windowClassOf(w, h), onClose = {}, buffMultiplier = 1.5) }
    }
}

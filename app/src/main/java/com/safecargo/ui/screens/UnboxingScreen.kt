package com.safecargo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.safecargo.ui.components.CLOSED_AR
import com.safecargo.ui.components.ChestArt
import com.safecargo.ui.components.ChestImage
import com.safecargo.ui.components.OPEN_AR
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.safecargo.engine.Grade
import com.safecargo.engine.TripResult
import com.safecargo.ui.shell.HeightClass
import com.safecargo.ui.shell.WindowClass
import com.safecargo.ui.shell.windowClassOf
import com.safecargo.ui.theme.SC
import com.safecargo.ui.theme.SafeCargoTheme
import com.safecargo.ui.theme.ScType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 화면의 등급 색 (SD-07 3중 표기의 색) — 언박싱 · 리워드 · 배지.
 * 레전더리는 금색(피그마 color/game/key, 11/09 디자인 확정). 실내 조명 색은 [lightColor].
 */
fun gradeColor(g: Grade): Color = when (g) {   // 조명 실패 시 화면 테두리 빛(FR-VH-012)도 이 색 — 11/10 디자인: 보라 → 금색
    Grade.RARE -> SC.rare
    Grade.EPIC -> SC.epic
    Grade.LEGENDARY -> SC.gameKey
}

/** 등급 글자 색 — 라이트의 금색 글자는 검정 (11/10 오후). 빛 · 테두리는 [gradeColor]. */
fun gradeTextColor(g: Grade): Color = if (g == Grade.LEGENDARY) SC.gameKeyText else gradeColor(g)

/** 실내 조명의 등급 색 — judge.json `gradeLightColors` 와 같다. 레전더리는 보라 유지. 화면 테두리 빛은 [gradeColor] (11/10). */
fun lightColor(g: Grade): Color = when (g) {
    Grade.RARE -> SC.rareLight
    Grade.EPIC -> SC.epic
    Grade.LEGENDARY -> SC.legendary
}

// 타임라인은 [chestPose] (ChestOpenModel.kt). 등급 빛은 펑부터 700ms 에 걸쳐 퍼지고, 등급 이름은 [NAME_START_MS] 부터 400ms.
private const val GLOW_MS = 700f
private const val NAME_MS = 400f

/**
 * SC-05 언박싱. 듀오링고식 「덜컹덜컹 묶음 2번 → 움츠림 → 펑」, 총 [UNBOX_TOTAL_MS] 뒤 [onFinished] 한 번.
 * 화면을 누르면 다음 덜컹으로 바로 넘어가고([tapAt]), 펑 뒤에 누르면 끝난다 — 직접 두드려 여는 손맛. 진행 상태는 저장하지 않는다 — 창 크기 변경 · 재시작이면 처음부터 다시 재생 (허용).
 * 상자는 피그마 보물상자 G 그림(PNG). Lottie 는 쓰지 않는다 (11/10 디자인 확정).
 *
 * [playable]=false (복구 직후 기어 UNKNOWN 등 — 정차 · UX 해제 미확인) 면 닫힌 상자만 정지 그림으로 그린다:
 * 애니메이션 · 타이머 · 탭 건너뛰기 모두 없음. true 가 되면 처음부터 재생한다.
 */
@Composable
fun UnboxingScreen(
    result: TripResult,
    wc: WindowClass,
    playable: Boolean = true,
    /** 실내 조명 안내 (피그마 SC-05 04 「실내 조명이 3초 빛나요」). 조명을 지원하지 않는 차면 null — 안 빛나는데 빛난다고 하지 않는다. */
    lightNote: String? = null,
    /** 아쉬운 상자(빈 상자) — 열린 그림을 빈 상자로. */
    empty: Boolean = false,
    onFinished: () -> Unit,
) {
    val finish by rememberUpdatedState(onFinished)
    // 타이머와 탭이 겹쳐도 onFinished 는 한 번만.
    var done by remember(result.id) { mutableStateOf(false) }
    val fire: () -> Unit = remember(result.id) { { if (!done) { done = true; finish() } } }

    val t = remember(result.id) { Animatable(0f) }
    // 탭으로 건너뛸 시각. 바뀔 때마다 그 시각부터 끝까지 다시 재생한다 (이전 재생은 키 변경으로 취소).
    var jump by remember(result.id) { mutableStateOf(0f) }
    var jumps by remember(result.id) { mutableStateOf(0) }
    LaunchedEffect(result.id, playable, jumps) {
        if (!playable) return@LaunchedEffect
        t.snapTo(jump)
        val left = (UNBOX_TOTAL_MS - jump).toInt().coerceAtLeast(1)
        t.animateTo(UNBOX_TOTAL_MS.toFloat(), tween(left, easing = LinearEasing))
        fire()
    }

    // 정지 그림 = 내려앉은 닫힌 상자 (덜컹 · 빛 없음).
    val ms = if (playable) t.value else CHEST_STILL_MS
    val color = gradeColor(result.grade)
    val nameAlpha = ((ms - NAME_START_MS) / NAME_MS).coerceIn(0f, 1f)
    val compact = wc.height == HeightClass.S

    val tap = if (playable) Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null,
        role = Role.Button,
        onClick = {
            when (val r = tapAt(t.value)) {
                TapResult.Finish -> fire()
                is TapResult.Jump -> { jump = r.toMs; jumps++ }
            }
        },
    ).semantics { contentDescription = "상자 여는 중 — 누르면 더 빨리 열려요" } else Modifier
    Box(Modifier.fillMaxSize().then(tap)) {
        Column(
            Modifier.fillMaxSize().padding(vertical = if (compact) SC.gapLg else SC.gapXl, horizontal = SC.gapXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) SC.gapSm else SC.gap),
        ) {
            Text(if (playable) "배송 완료! 상자를 여는 중" else "배송 완료!", style = ScType.title, color = SC.text, textAlign = TextAlign.Center)
            UnboxingStage(ChestArt.INTACT, ChestArt.opened(result.grade, empty), color, ms, Modifier.fillMaxWidth().weight(1f), burst = !empty)
            Text(
                "${gradeName(result.grade)} 상자", style = ScType.headline, color = gradeTextColor(result.grade),
                modifier = Modifier.alpha(nameAlpha),
            )
            if (playable && lightNote != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SC.gapXs)) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                    Text(lightNote, style = ScType.caption, color = SC.text)
                }
            }
            if (playable) Text("상자를 톡톡 누르면 더 빨리 열려요", style = ScType.caption, color = SC.textDim)
        }
    }
}

private fun ease(x: Float) = FastOutSlowInEasing.transform(x.coerceIn(0f, 1f))

/**
 * 상자 그림 + 펑 효과 + 등급 빛. [ms] = 경과 시간, 자세는 [chestPose].
 * 피그마 G 상자는 뚜껑이 분리되지 않은 통짜 그림이라 뚜껑 회전 대신 펑 순간 열린 그림으로 바꾼다 (11/10 디자인).
 * [burst]=false (아쉬운 상자) 면 동전 · 반짝이를 튀기지 않는다 — 빈 상자인데 뭔가 쏟아지는 척하지 않는다.
 * 보너스 상자도 같은 연출을 쓴다 ([closed]·[open] 그림만 다르다).
 */
@Composable
internal fun UnboxingStage(
    closed: ChestArt,
    open: ChestArt,
    color: Color,
    ms: Float,
    modifier: Modifier,
    closedScale: Float = 0.62f,
    burst: Boolean = true,
) {
    val pose = chestPose(ms)
    val glowP = ease((ms - BURST_MS) / GLOW_MS)
    val coin = SC.gameKey
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        // 상자 폭: 칸에 맞추되 닫힌 상자 기준. 열린 상자는 같은 배율이라 300/260 만큼 넓다.
        val closedW = minOf(maxWidth * 0.8f, maxHeight * CLOSED_AR) * closedScale
        Canvas(Modifier.fillMaxSize()) {
            val s = closedW.toPx()
            if (glowP > 0f) drawGlow(color, glowP, s)
            val ring = flashRing(ms)
            if (ring.alpha > 0f) {
                drawCircle(
                    color.copy(alpha = ring.alpha), radius = ring.radius * s,
                    center = Offset(size.width / 2f, size.height / 2f - s * 0.1f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.04f * (0.4f + ring.alpha)),
                )
            }
        }
        val art = if (pose.opened) open else closed
        ChestImage(
            art,
            Modifier.width(closedW * art.widthFactor()).aspectRatio(art.aspect)
                .graphicsLayer {
                    translationY = pose.dy * closedW.toPx()
                    rotationZ = pose.deg
                    scaleX = pose.sx; scaleY = pose.sy
                    transformOrigin = TransformOrigin(0.5f, 0.9f)
                },
        )
        if (burst) {
            Canvas(Modifier.fillMaxSize()) {
                val s = closedW.toPx()
                val c = Offset(size.width / 2f, size.height / 2f)
                for (p in particles(ms)) {
                    if (p.alpha <= 0f) continue
                    val at = Offset(c.x + p.x * s, c.y + p.y * s)
                    val r = p.size * s / 2f
                    when (p.kind) {
                        ParticleKind.COIN -> drawCoin(coin, at, r, p.deg, p.alpha)
                        ParticleKind.SPARKLE -> drawSparkle(color, at, r, p.deg, p.alpha)
                    }
                }
                // 펑 순간 화면 번쩍 (디자이너 튜닝 0.6 · 180ms). 빈 상자에는 없다.
                val flash = screenFlash(ms)
                if (flash > 0f) drawRect(Color.White.copy(alpha = flash))
            }
        }
    }
}

/** 동전 — 금색 원 + 안쪽 테 + 하이라이트. 회전은 가로 폭을 줄여 도는 것처럼 보이게. */
private fun DrawScope.drawCoin(gold: Color, at: Offset, r: Float, deg: Float, alpha: Float) {
    val squeeze = 0.35f + 0.65f * kotlin.math.abs(cos(deg * PI.toFloat() / 180f))
    val sz = Size(r * 2f * squeeze, r * 2f)
    val tl = Offset(at.x - sz.width / 2f, at.y - r)
    drawOval(gold.copy(alpha = alpha), tl, sz)
    drawOval(Color(0xFF8A5A00).copy(alpha = 0.55f * alpha), tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.18f))
    drawOval(Color.White.copy(alpha = 0.6f * alpha), Offset(at.x - sz.width * 0.25f, at.y - r * 0.6f), Size(sz.width * 0.3f, r * 0.5f))
}

/** 반짝이 — 등급 색 네 갈래 별. */
private fun DrawScope.drawSparkle(color: Color, at: Offset, r: Float, deg: Float, alpha: Float) {
    rotate(deg, pivot = at) {
        val path = Path().apply {
            moveTo(at.x, at.y - r * 1.3f)
            quadraticTo(at.x, at.y, at.x + r * 1.3f, at.y)
            quadraticTo(at.x, at.y, at.x, at.y + r * 1.3f)
            quadraticTo(at.x, at.y, at.x - r * 1.3f, at.y)
            quadraticTo(at.x, at.y, at.x, at.y - r * 1.3f)
            close()
        }
        drawPath(path, color.copy(alpha = alpha))
        drawCircle(Color.White.copy(alpha = 0.8f * alpha), radius = r * 0.25f, center = at)
    }
}

private fun ChestArt.widthFactor(): Float = if (aspect == OPEN_AR) 300f / 260f else 1f

/** 등급 빛 — 상자 뒤에서 퍼지는 원형 광채 + 빛줄기. [s] = 닫힌 상자 폭(px). */
private fun DrawScope.drawGlow(color: Color, glowP: Float, s: Float) {
    val c = Offset(size.width / 2f, size.height / 2f - s * 0.1f)
    val r = s * (0.35f + 0.75f * glowP)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(color.copy(alpha = 0.75f * glowP), color.copy(alpha = 0.18f * glowP), Color.Transparent),
            center = c, radius = r,
        ),
        radius = r, center = c,
    )
    val rays = 12
    val spin = glowP * 20f
    for (i in 0 until rays) {
        val a = ((i * 360f / rays) + spin) * PI.toFloat() / 180f
        val r0 = s * 0.42f
        val r1 = s * (0.55f + 0.4f * glowP)
        drawLine(
            color.copy(alpha = 0.55f * glowP),
            Offset(c.x + cos(a) * r0, c.y + sin(a) * r0),
            Offset(c.x + cos(a) * r1, c.y + sin(a) * r1),
            strokeWidth = s * 0.025f, cap = StrokeCap.Round,
        )
    }
}

/**
 * 리워드 화면의 상자 그림 — 등급별 열린 G 상자(레어 파랑 상자 · 에픽 보석 · 레전더리 왕관).
 * [opened] 면 언박싱 마지막 장면(열린 상자 + 등급 빛), 아니면 배지용 작은 그림(빛 없음).
 */
@Composable
internal fun GradeBoxIcon(grade: Grade, modifier: Modifier = Modifier, opened: Boolean = false, empty: Boolean = false) {
    if (opened) {
        UnboxingStage(ChestArt.INTACT, ChestArt.opened(grade, empty), gradeColor(grade), UNBOX_TOTAL_MS.toFloat(), modifier, closedScale = 0.5f, burst = !empty)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            ChestImage(ChestArt.opened(grade, empty), Modifier.fillMaxSize(0.9f))
        }
    }
}

// ---------------------------------------------------------------- 프리뷰

@Preview(name = "언박싱 레어 1700x1184", widthDp = 1700, heightDp = 1184)
@Composable
private fun UnboxRarePreview() = UnboxHost(previewResult(Grade.RARE), 1700, 1184)

@Preview(name = "언박싱 에픽 800x1400", widthDp = 800, heightDp = 1400)
@Composable
private fun UnboxEpicPreview() = UnboxHost(previewResult(Grade.EPIC), 800, 1400)

@Preview(name = "언박싱 레전더리 1640x810", widthDp = 1640, heightDp = 810)
@Composable
private fun UnboxLegendaryPreview() = UnboxHost(previewResult(Grade.LEGENDARY), 1640, 810)

@Composable
private fun UnboxHost(r: TripResult, w: Int, h: Int) {
    SafeCargoTheme {
        Box(Modifier.fillMaxSize()) { UnboxingScreen(r, windowClassOf(w, h), onFinished = {}) }
    }
}

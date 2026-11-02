package com.safecargo.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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

/** 등급 색 (SD-07 3중 표기의 색). */
fun gradeColor(g: Grade): Color = when (g) {
    Grade.RARE -> SC.rare
    Grade.EPIC -> SC.epic
    Grade.LEGENDARY -> SC.legendary
}

// 타임라인 (ms): 흔들림 0~1000 · 뚜껑 1000~1600 · 등급 빛 1200~2500 · 등급 이름 1600~2000
private const val SHAKE_END = 1000f
private const val LID_START = 1000f
private const val LID_MS = 600f
private const val GLOW_START = 1200f
private const val GLOW_MS = 900f
private const val NAME_START = 1600f
private const val NAME_MS = 400f

/**
 * SC-05 언박싱. 상자 흔들림 → 뚜껑 열림 → 등급 빛, 총 [UNBOX_TOTAL_MS] 뒤 [onFinished] 한 번.
 * 탭하면 바로 건너뛴다. 진행 상태는 저장하지 않는다 — 창 크기 변경 · 재시작이면 처음부터 다시 재생 (허용).
 * Lottie 는 디자이너 에셋이 오면 이 화면만 교체한다.
 *
 * [playable]=false (복구 직후 기어 UNKNOWN 등 — 정차 · UX 해제 미확인) 면 닫힌 상자만 정지 그림으로 그린다:
 * 애니메이션 · 타이머 · 탭 건너뛰기 모두 없음. true 가 되면 처음부터 재생한다.
 */
@Composable
fun UnboxingScreen(result: TripResult, wc: WindowClass, playable: Boolean = true, onFinished: () -> Unit) {
    val finish by rememberUpdatedState(onFinished)
    // 타이머와 탭이 겹쳐도 onFinished 는 한 번만.
    var done by remember(result.id) { mutableStateOf(false) }
    val fire: () -> Unit = remember(result.id) { { if (!done) { done = true; finish() } } }

    val t = remember(result.id) { Animatable(0f) }
    LaunchedEffect(result.id, playable) {
        if (!playable) return@LaunchedEffect
        t.snapTo(0f)
        t.animateTo(UNBOX_TOTAL_MS.toFloat(), tween(UNBOX_TOTAL_MS, easing = LinearEasing))
        fire()
    }

    // 정지 그림 = 0ms 프레임 (흔들림 0 · 뚜껑 닫힘 · 빛 없음).
    val ms = if (playable) t.value else 0f
    val color = gradeColor(result.grade)
    val nameAlpha = ((ms - NAME_START) / NAME_MS).coerceIn(0f, 1f)
    val compact = wc.height == HeightClass.S

    val tap = if (playable) Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null,
        role = Role.Button, onClick = fire,
    ).semantics { contentDescription = "상자 여는 중 — 누르면 건너뛰어요" } else Modifier
    Box(Modifier.fillMaxSize().then(tap)) {
        Column(
            Modifier.fillMaxSize().padding(vertical = if (compact) SC.gapLg else SC.gapXl, horizontal = SC.gapXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) SC.gapSm else SC.gap),
        ) {
            Text(if (playable) "배송 완료! 상자를 여는 중" else "배송 완료!", style = ScType.title, color = SC.text, textAlign = TextAlign.Center)
            Canvas(Modifier.fillMaxWidth().weight(1f).clipToBounds()) { drawUnboxing(result.grade, color, ms) }
            Text(
                "${gradeName(result.grade)} 상자", style = ScType.headline, color = color,
                modifier = Modifier.alpha(nameAlpha),
            )
            if (playable) Text("화면을 누르면 바로 열려요", style = ScType.caption, color = SC.textDim)
        }
    }
}

private fun ease(x: Float) = FastOutSlowInEasing.transform(x.coerceIn(0f, 1f))

/** 상자 · 뚜껑 · 빛을 한 캔버스에. [ms] = 경과 시간. */
private fun DrawScope.drawUnboxing(grade: Grade, color: Color, ms: Float, sizeFactor: Float = 0.46f) {
    val s = minOf(size.width * 0.8f, size.height) * sizeFactor   // 상자 기준 길이
    val cx = size.width / 2f
    // 뚜껑이 들릴 자리를 위에 남긴다.
    val cy = size.height / 2f + s * if (sizeFactor < 0.6f) 0.35f else 0.12f
    val bodyTop = cy - s * 0.12f

    // 1) 흔들림 — 점점 세지다가 끝에서 멎음
    val shake = if (ms < SHAKE_END) {
        val ramp = (ms / SHAKE_END).let { if (it < 0.8f) it / 0.8f else (1f - it) / 0.2f }
        sin(ms / 1000f * 2f * PI.toFloat() * 7f) * 7f * ramp
    } else 0f
    val lidP = ease((ms - LID_START) / LID_MS)
    val glowP = ease((ms - GLOW_START) / GLOW_MS)

    // 3) 등급 빛 — 상자 뒤에서 퍼진다
    if (glowP > 0f) {
        val glowCenter = Offset(cx, bodyTop)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(color.copy(alpha = 0.75f * glowP), color.copy(alpha = 0.18f * glowP), Color.Transparent),
                center = glowCenter, radius = s * (0.35f + 0.75f * glowP),
            ),
            radius = s * (0.35f + 0.75f * glowP), center = glowCenter,
        )
        val rays = 12
        val spin = glowP * 20f
        for (i in 0 until rays) {
            val a = ((i * 360f / rays) + spin) * PI.toFloat() / 180f
            val r0 = s * 0.30f
            val r1 = s * (0.42f + 0.4f * glowP)
            drawLine(
                color.copy(alpha = 0.55f * glowP),
                Offset(glowCenter.x + cos(a) * r0, glowCenter.y + sin(a) * r0),
                Offset(glowCenter.x + cos(a) * r1, glowCenter.y + sin(a) * r1),
                strokeWidth = s * 0.025f, cap = StrokeCap.Round,
            )
        }
    }

    val body = lerp(color, Color.Black, 0.45f)
    val bodyW = s
    val bodyH = s * 0.62f
    val lidW = s * 1.1f
    val lidH = s * 0.2f
    val band = Color(0xFFF5F2EA)

    rotate(shake, pivot = Offset(cx, cy + bodyH * 0.5f)) {
        // 몸통
        drawRoundRect(body, Offset(cx - bodyW / 2, bodyTop), Size(bodyW, bodyH), CornerRadius(s * 0.05f))
        // 안쪽 그림자 (뚜껑이 열리면 보인다)
        if (lidP > 0f) {
            drawRect(Color.Black.copy(alpha = 0.35f * lidP), Offset(cx - bodyW / 2, bodyTop), Size(bodyW, s * 0.08f))
        }
        when (grade) {
            Grade.RARE -> // 사각 — 자물쇠 판 하나
                drawRoundRect(SC.gameKey, Offset(cx - s * 0.07f, bodyTop + bodyH * 0.22f), Size(s * 0.14f, s * 0.18f), CornerRadius(s * 0.03f))
            Grade.EPIC, Grade.LEGENDARY -> // 리본 띠
                drawRect(band, Offset(cx - s * 0.07f, bodyTop), Size(s * 0.14f, bodyH))
        }

        // 2) 뚜껑 — 위로 들리며 젖혀진다
        val lift = -lidP * s * 0.38f
        val lidTop = bodyTop - lidH
        translate(top = lift) {
            rotate(-28f * lidP, pivot = Offset(cx - lidW / 2, bodyTop)) {
                drawRoundRect(color, Offset(cx - lidW / 2, lidTop), Size(lidW, lidH), CornerRadius(s * 0.05f))
                when (grade) {
                    Grade.RARE -> {}
                    Grade.EPIC -> {
                        drawRect(band, Offset(cx - s * 0.07f, lidTop), Size(s * 0.14f, lidH))
                        drawBow(cx, lidTop, s, band)
                    }
                    Grade.LEGENDARY -> {
                        drawRect(band, Offset(cx - s * 0.07f, lidTop), Size(s * 0.14f, lidH))
                        drawCrown(cx, lidTop, s)
                    }
                }
            }
        }
    }
}

/** 에픽 — 리본 매듭. */
private fun DrawScope.drawBow(cx: Float, top: Float, s: Float, c: Color) {
    val r = s * 0.09f
    drawOval(c, Offset(cx - r * 2.1f, top - r * 1.3f), Size(r * 2f, r * 1.4f))
    drawOval(c, Offset(cx + r * 0.1f, top - r * 1.3f), Size(r * 2f, r * 1.4f))
    drawCircle(lerp(c, Color.Black, 0.15f), r * 0.5f, Offset(cx, top - r * 0.5f))
}

/** 레전더리 — 왕관 장식. */
private fun DrawScope.drawCrown(cx: Float, top: Float, s: Float) {
    val w = s * 0.42f
    val h = s * 0.24f
    val base = top + s * 0.01f
    val p = Path().apply {
        moveTo(cx - w / 2, base)
        lineTo(cx - w / 2, base - h * 0.55f)
        lineTo(cx - w / 4, base - h * 0.25f)
        lineTo(cx, base - h)
        lineTo(cx + w / 4, base - h * 0.25f)
        lineTo(cx + w / 2, base - h * 0.55f)
        lineTo(cx + w / 2, base)
        close()
    }
    val gold = Color(0xFFFFE08A)
    drawPath(p, gold)
    listOf(Offset(cx - w / 2, base - h * 0.55f), Offset(cx, base - h), Offset(cx + w / 2, base - h * 0.55f)).forEach {
        drawCircle(gold, s * 0.03f, it)
    }
    drawCircle(Color(0xFFE5484D), s * 0.028f, Offset(cx, base - h * 0.32f))
}

/** 리워드 화면 배지용 작은 상자 그림 — 같은 모양 규칙(사각 · 리본 · 왕관). */
@Composable
internal fun GradeBoxIcon(grade: Grade, modifier: Modifier = Modifier, opened: Boolean = false) {
    val color = gradeColor(grade)
    Canvas(modifier.clipToBounds()) {
        // opened: 언박싱 마지막 프레임(뚜껑 열림 + 등급 빛) 그대로.
        if (opened) drawUnboxing(grade, color, UNBOX_TOTAL_MS.toFloat())
        else drawUnboxing(grade, color, 0f, sizeFactor = 0.62f)
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

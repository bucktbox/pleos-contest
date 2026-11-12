package com.safecargo.ui.screens

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * 상자 개봉 연출 (정차 후 · SC-05 언박싱 · 보너스 상자). 듀오링고 상자처럼 「덜컹덜컹 → 움츠림 → 펑」.
 * Compose 없이 시각(ms) → 자세 · 파편 · 빛을 계산해 단위 테스트한다. 길이는 상자 폭 배수, 위가 음수.
 *
 * 값은 디자이너 튜닝값(11/11, [ChestTuning] 기본값)을 그대로 쓴다. 타임라인 (ms)
 *   0 ~ 440      떨어져 내려앉기 (착지 때 납작)
 *   520 · 1050   덜컹 묶음 2번 — 묶음마다 빠르게 2번 흔들림(1번 160ms), 묶음마다 30% 세진다 (8° → 10.4°)
 *   1449 ~ 1829  크게 움츠리며 부르르 (세로 -22% · 가로 +15%)
 *   1829         펑 — 열린 그림으로 바꾸고 0.28 튀어 오름 · 크기 +35% 넘쳤다 복귀 · 화면 번쩍 · 동전과 반짝이 28개
 *   ~ 3529       통통 튀다 멈춤
 * 상자를 누르면 다음 단계로 바로 넘어간다 ([tapAt]) — 직접 두드려 여는 손맛. 펑 뒤에 누르면 끝.
 *
 * 주행 중 화면이 아니라서 주행 안전 기준(NFR-SAF-002)의 상한은 여기에 걸리지 않는다. 소리는 없다.
 */

/** 디자이너 튜닝값. 필드 이름은 튜닝 도구의 raw JSON 키와 같다. */
data class ChestTuning(
    val bursts: Int = 2,          // 덜컹 묶음 수
    val shakes: Int = 2,          // 묶음마다 흔들림 수
    val shakeMs: Float = 160f,    // 흔들림 1번 길이
    val gapMs: Float = 210f,      // 묶음 사이 쉼 (첫 쉼)
    val accel: Float = 0.25f,     // 쉼이 매번 짧아지는 비율
    val ampDeg: Float = 8f,       // 첫 묶음 기울기
    val growth: Float = 0.3f,     // 묶음마다 세지는 비율 (기울기 · 들썩 높이)
    val hop: Float = 0.04f,       // 첫 묶음 들썩 높이 (상자 폭 배수)
    val windupMs: Float = 380f,   // 움츠림 길이
    val squash: Float = 0.22f,    // 움츠림 세로 줄어듦 (가로는 그 0.7배만큼 늘어남 → +15%)
    val launch: Float = 0.28f,    // 펑 때 튀어 오르는 높이 (상자 폭 배수)
    val overshoot: Float = 0.35f, // 펑 때 크기가 넘치는 정도
    val coins: Int = 28,          // 동전 · 반짝이 개수
    val speed: Float = 1.9f,      // 파편 기본 속도 (상자 폭/초)
    val flash: Float = 0.6f,      // 화면 번쩍 세기 (알파)
)

val CHEST_TUNING = ChestTuning()
private val T = CHEST_TUNING

data class ChestPose(val dy: Float, val deg: Float, val sx: Float, val sy: Float, val opened: Boolean)

private const val FIRST_JOLT_MS = 520f
/** 펑 뒤 멈출 때까지. 전체 길이 3529 = 펑 1829 + 1700. */
const val SETTLE_MS = 1700f
/** 화면 번쩍 길이. */
const val SCREEN_FLASH_MS = 180f
private const val STRETCH_PER_SQUASH = 0.7f

/** 덜컹 묶음 하나의 길이 (흔들림 수 × 흔들림 길이). */
val JOLT_MS: Float = T.shakes * T.shakeMs
private fun gapAfter(i: Int): Float = T.gapMs * (1f - T.accel).pow(i)

/** 묶음 시작 시각 — 쉼이 매번 [ChestTuning.accel] 만큼 짧아진다. */
val JOLT_STARTS: List<Float> = buildList {
    var at = FIRST_JOLT_MS
    repeat(T.bursts) { i -> add(at); at += JOLT_MS + gapAfter(i) }
}

/** 움츠림 시작 — 마지막 묶음 뒤, 다음 쉼의 절반만 쉬고 바로 움츠린다 (튜닝 도구 1449 와 일치). */
val WINDUP_MS: Float = (JOLT_STARTS.last() + JOLT_MS + gapAfter(T.bursts - 1) * 0.5f).roundToInt().toFloat()
val BURST_MS: Float = WINDUP_MS + T.windupMs
/** 내려앉아 멈춘 닫힌 상자 — 정지 그림(재생 불가 상태)에 쓴다. */
const val CHEST_STILL_MS = 480f
/** 등급 이름이 떠오르는 시각. */
val NAME_START_MS: Float = BURST_MS + 300f

private const val DROP_MS = 320f
private const val LAND_MS = 120f

private val REST_CLOSED = ChestPose(0f, 0f, 1f, 1f, false)
private val REST_OPEN = ChestPose(0f, 0f, 1f, 1f, true)

private fun easeIn(x: Float) = x * x
private fun easeOut(x: Float) = 1f - (1f - x) * (1f - x)
private fun bump(x: Float) = sin(PI.toFloat() * x.coerceIn(0f, 1f))

fun chestPose(ms: Float): ChestPose {
    if (ms >= UNBOX_TOTAL_MS) return REST_OPEN
    if (ms >= BURST_MS) return popPose(ms)
    if (ms < DROP_MS) return ChestPose(-0.35f * (1f - easeIn((ms / DROP_MS).coerceAtLeast(0f))), 0f, 1f, 1f, false)
    if (ms < DROP_MS + LAND_MS) {
        val d = bump((ms - DROP_MS) / LAND_MS)
        return ChestPose(0f, 0f, 1f + 0.08f * d, 1f - 0.12f * d, false)
    }
    JOLT_STARTS.forEachIndexed { i, start ->
        if (ms >= start && ms < start + JOLT_MS) return joltPose(ms - start, i)
    }
    if (ms >= WINDUP_MS) {
        val w = easeIn((ms - WINDUP_MS) / T.windupMs)
        val shiver = sin(ms / 1000f * 2f * PI.toFloat() * 18f) * 3f * w
        return ChestPose(0f, shiver, 1f + STRETCH_PER_SQUASH * T.squash * w, 1f - T.squash * w, false)
    }
    return REST_CLOSED
}

/** 묶음 [i] 의 기울기 · 들썩 높이 — 묶음마다 [ChestTuning.growth] 만큼 세진다. */
fun joltAmpDeg(i: Int): Float = T.ampDeg * (1f + T.growth * i)
fun joltHop(i: Int): Float = T.hop * (1f + T.growth * i)

/** 묶음 안 [local]ms. 흔들림마다 살짝 들썩이며 한쪽으로 기울었다 반대로 — 흔들림마다 방향을 바꾼다. */
private fun joltPose(local: Float, i: Int): ChestPose {
    val k = (local / T.shakeMs).toInt().coerceAtMost(T.shakes - 1)
    val u = (local - k * T.shakeMs) / T.shakeMs
    val dir = if ((k + i) % 2 == 0) 1f else -1f
    return ChestPose(-joltHop(i) * bump(u), dir * joltAmpDeg(i) * sin(2f * PI.toFloat() * u), 1f, 1f, false)
}

private const val POP_RISE_S = 0.09f
/** 펑 뒤 통통 — (길이 s, 높이 배수). 첫 번째가 [ChestTuning.launch] 만큼 튄다. */
private val BOUNCES = listOf(0.5f to 1f, 0.3f to 0.3f, 0.18f to 0.1f)

/** 펑 뒤: 움츠린 크기에서 +35% 까지 부풀었다가 감쇠 스프링으로 복귀, 높이는 세 번 통통. 끝에서 정확히 0 · 1. */
private fun popPose(ms: Float): ChestPose {
    val q = (ms - BURST_MS) / 1000f
    val peak = 1f + T.overshoot
    val sx: Float
    val sy: Float
    if (q < POP_RISE_S) {
        val e = easeOut(q / POP_RISE_S)
        sx = lerp(1f + STRETCH_PER_SQUASH * T.squash, peak, e)
        sy = lerp(1f - T.squash, peak, e)
    } else {
        val r = q - POP_RISE_S
        val fade = 1f - r / (SETTLE_MS / 1000f - POP_RISE_S)
        val s = 1f + T.overshoot * exp(-5f * r) * cos(2f * PI.toFloat() * 2f * r) * fade
        sx = s; sy = s
    }
    var t = q
    var hop = 0f
    for ((len, h) in BOUNCES) {
        if (t < len) { val u = t / len; hop = -T.launch * h * 4f * u * (1f - u); break }
        t -= len
    }
    return ChestPose(hop, 0f, sx, sy, true)
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** 상자를 눌렀을 때 — 다음 단계로 건너뛰거나 끝낸다. */
sealed interface TapResult {
    data class Jump(val toMs: Float) : TapResult
    data object Finish : TapResult
}

fun tapAt(ms: Float): TapResult {
    if (ms >= BURST_MS) return TapResult.Finish
    val next = (JOLT_STARTS + WINDUP_MS + BURST_MS).first { it > ms }
    return TapResult.Jump(next)
}

enum class ParticleKind { COIN, SPARKLE }

/** 펑 때 튀는 파편 하나. [x]·[y] 상자 중심 기준(상자 폭 배수), [size] 상자 폭 배수, [deg] 회전. */
data class Particle(val kind: ParticleKind, val x: Float, val y: Float, val size: Float, val deg: Float, val alpha: Float)

val PARTICLE_COUNT: Int = T.coins
private const val GRAVITY = 3.2f

/** 결정적(난수 없음) — 같은 시각이면 같은 그림이라 녹화 · 테스트가 매번 같다. */
fun particles(ms: Float): List<Particle> {
    if (ms < BURST_MS) return emptyList()
    val t = (ms - BURST_MS) / 1000f
    val life = SETTLE_MS / 1000f
    val alpha = when {
        t >= life -> 0f
        t < life * 0.55f -> 1f
        else -> 1f - (t - life * 0.55f) / (life * 0.45f)
    }
    return List(PARTICLE_COUNT) { i ->
        val f = i / (PARTICLE_COUNT - 1f)
        val jitter = frac(i * 0.618f)
        val a = ((-160f + 140f * f) + (jitter - 0.5f) * 12f) * PI.toFloat() / 180f
        val v = T.speed * (0.8f + 0.4f * frac(i * 0.381f + 0.2f))
        Particle(
            kind = if (i % 2 == 0) ParticleKind.COIN else ParticleKind.SPARKLE,
            x = cos(a) * v * t,
            y = -0.1f + sin(a) * v * t + 0.5f * GRAVITY * t * t,
            size = 0.07f + 0.04f * jitter,
            deg = t * 360f * (if (i % 2 == 0) 1f else -1f),
            alpha = alpha,
        )
    }
}

private fun frac(x: Float) = x - x.toInt()

/** 펑 순간 화면 번쩍 — [ChestTuning.flash] 에서 [SCREEN_FLASH_MS] 동안 0 으로. 알파를 돌려준다. */
fun screenFlash(ms: Float): Float {
    val t = (ms - BURST_MS) / SCREEN_FLASH_MS
    if (t < 0f || t >= 1f) return 0f
    return T.flash * (1f - t)
}

/** 펑 순간 퍼지는 빛 고리 — 400ms. [radius] 상자 폭 배수. */
data class FlashRing(val radius: Float, val alpha: Float)

private const val FLASH_MS = 400f

fun flashRing(ms: Float): FlashRing {
    val t = (ms - BURST_MS) / FLASH_MS
    if (t < 0f || t >= 1f) return FlashRing(0.4f, 0f)
    return FlashRing(0.4f + 1.0f * easeOut(t), 0.9f * (1f - t))
}

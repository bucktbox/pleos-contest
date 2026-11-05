package com.safecargo.ui.screens

import com.safecargo.engine.Grade
import com.safecargo.engine.JudgeMoment
import com.safecargo.engine.MomentKind
import com.safecargo.engine.TripResult
import com.safecargo.engine.TripSummary
import com.safecargo.ui.components.multiplierLabel

/*
 * 언박싱 · 리워드 화면의 문구와 순서 계산. Compose 없이 단위 테스트한다.
 * 주행 중 피드백은 정적이라(A1) 깜빡이 조합 · 비교는 정차 후 여기서만 보여준다.
 */

/** 등급 이름 — 색 · 상자 모양과 함께 3중 표기 (SD-07). */
fun gradeName(g: Grade): String = when (g) {
    Grade.RARE -> "레어"
    Grade.EPIC -> "에픽"
    Grade.LEGENDARY -> "레전더리"
}

/**
 * 「이번 호위」 칸 — 잘한 것만 (11/03 디자인 요청 §2 · PRD 리뷰 H4 가점만). 실수 횟수(깜빡이 없이)는 보여주지 않는다.
 * 숫자는 판정 엔진 카운터([TripSummary]) 그대로 — moments 는 최대 개수로 잘릴 수 있어 쓰지 않는다.
 */
data class SummaryItem(val label: String, val count: Int)

fun summaryItems(s: TripSummary): List<SummaryItem> = listOf(
    SummaryItem("깜빡이 먼저", s.signalFirst),
    SummaryItem("퍼펙트", s.perfect),
    SummaryItem("부드러운 회전", s.smoothTurns),
)

/** 「깜빡이 먼저 N회 · 퍼펙트 K회 · 부드러운 회전 J회」 */
fun summaryLine(s: TripSummary): String = summaryItems(s).joinToString(" · ") { "${it.label} ${it.count}회" }

/** 데일리 카고 결과 — 목적지가 없다. */
fun isDailyResult(r: TripResult): Boolean = r.rewardId == "daily" || r.destinationName == null

/** 스폰서 카고인데 도착 인증이 없을 때만 「도착 인증 없음」. */
fun showNoArrival(r: TripResult): Boolean = !r.arrivalVerified && !isDailyResult(r)

/**
 * 버프 줄. 버프가 없으면 null. 배수는 설정값([multiplierLabel]).
 * "벨트" 라는 말은 벨트 신호로 실제 확인됐을 때만 쓴다.
 */
fun buffLabel(r: TripResult, multiplier: Double): String? {
    if (!r.familyBuff) return null
    val base = "패밀리 버프 ${multiplierLabel(multiplier)}"
    return if (r.beltVerified) "$base · 벨트 확인" else base
}

/** 리플레이에 보이는 순간 — 잘한 것만 ([summaryItems] 와 같은 이유). */
fun isPositive(k: MomentKind): Boolean = when (k) {
    MomentKind.SIGNAL_FIRST, MomentKind.PERFECT_SIGNAL, MomentKind.SMOOTH_TURN -> true
    MomentKind.NO_SIGNAL_TURN, MomentKind.HARSH_TURN -> false
}

/** 리플레이 줄 — 잘한 순간만, 시간순. */
fun replayOrder(moments: List<JudgeMoment>): List<JudgeMoment> = moments.filter { isPositive(it.kind) }.sortedBy { it.atMs }

/** 리플레이 진행(0..1) → 하이라이트할 칸. 끝났거나 칸이 없으면 -1. */
fun highlightIndex(progress: Float, count: Int): Int {
    if (count <= 0 || progress >= 1f || progress < 0f) return -1
    return (progress * count).toInt().coerceIn(0, count - 1)
}

fun momentLabel(k: MomentKind): String = when (k) {
    MomentKind.SIGNAL_FIRST -> "깜빡이 먼저"
    MomentKind.PERFECT_SIGNAL -> "퍼펙트"
    MomentKind.NO_SIGNAL_TURN -> "깜빡이 없이"
    MomentKind.SMOOTH_TURN -> "부드러운 회전"
    MomentKind.HARSH_TURN -> "급한 회전"
}

const val UNBOX_TOTAL_MS = 2500
const val REPLAY_MS = 3000
const val REWARD_DISCLAIMER = "시연용 가상 보상 · 실제 사용 불가"
const val NO_ARRIVAL = "도착 인증 없음"

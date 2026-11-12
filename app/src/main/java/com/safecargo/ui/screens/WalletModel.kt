package com.safecargo.ui.screens

import com.safecargo.data.BonusGate
import com.safecargo.data.CouponStatus
import com.safecargo.data.LedgerEntry
import com.safecargo.data.WalletItem
import java.time.LocalDate

/*
 * 보상함 · 쿠폰 · 보너스 화면의 문구 규칙 (PRD v1.1 FR-APP-013 ~ 019 · 화면정의서 SC-06 · 09 · 10 · 11).
 * Compose 없이 단위 테스트한다. 「꽝」「실패」 는 쓰지 않는다 (SC-06 06).
 */

/** 「11/07」 */
fun monthDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).let { "${it.monthValue}/${it.dayOfMonth}" }

/** 남은 날 (만료일 포함). 0 = 오늘까지. */
fun daysLeft(item: WalletItem, today: Long): Long = item.expiresDay - today

fun daysLeftLabel(item: WalletItem, today: Long): String = when (val d = daysLeft(item, today)) {
    0L -> "오늘까지"
    else -> "${d.coerceAtLeast(0)}일 남음"
}

/** 3일 이하 남으면 시계 아이콘 (SC-09 03). */
fun expiringSoon(item: WalletItem, today: Long): Boolean = item.status == CouponStatus.AVAILABLE && daysLeft(item, today) <= 3

fun statusLabel(s: CouponStatus): String = when (s) {
    CouponStatus.AVAILABLE -> "사용 가능"
    CouponStatus.USED -> "사용 완료"
    CouponStatus.VOID -> "사용 불가"
}

/** P-S 탭용 2글자. */
fun statusShort(s: CouponStatus): String = when (s) {
    CouponStatus.AVAILABLE -> "가능"
    CouponStatus.USED -> "완료"
    CouponStatus.VOID -> "불가"
}

/** 목록 항목 둘째 줄 — 상태에 따라 남은 날 · 사용 일자 · 사유. */
fun itemDetailLine(item: WalletItem, today: Long, payback: Int): String = when (item.status) {
    CouponStatus.AVAILABLE -> "${daysLeftLabel(item, today)} · 쓰면 토큰 +$payback"
    CouponStatus.USED -> buildString {
        append("${monthDay(item.usedDay ?: item.receivedDay)} 사용")
        if (item.paybackTokens > 0) append(" · 토큰 +${item.paybackTokens} 받음")
    }
    CouponStatus.VOID -> "${item.voidReason?.label ?: "사용 불가"} · ${monthDay(item.expiresDay)} 까지"
}

/** 빈 탭 문구 (EX-12). */
fun emptyTabText(s: CouponStatus): String = when (s) {
    CouponStatus.AVAILABLE -> "카고를 배달하면 쿠폰이 들어와요"
    CouponStatus.USED -> "매장에서 쿠폰을 쓰면 여기에 남아요"
    CouponStatus.VOID -> "없음"
}

/** 토큰 지갑 아래 줄. */
fun tokenHint(balance: Int, cost: Int): String =
    if (balance >= cost) "보너스 상자를 열 수 있어요" else "${cost - balance}개 더 모으면 보너스 상자"

fun ledgerLine(e: LedgerEntry): String = "${monthDay(e.day)} ${e.reason.label}"

fun signed(n: Int): String = if (n >= 0) "+$n" else "−${-n}"

/** 보너스 상자를 열 수 없는 이유 한 줄. 열 수 있으면 null. */
fun bonusBlockedText(g: BonusGate): String? = when (g) {
    BonusGate.Open -> null
    is BonusGate.NeedTokens -> "${g.more}개 더 필요해요"
    BonusGate.DailyLimit -> "내일 다시 열 수 있어요"
}

/** 공용 사용 가이드 5단계 (FR-APP-015 · SD-12). 「이름\n설명」 — 피그마 Info Box 100:914 문구. */
fun guideSteps(payback: Int, cost: Int): List<String> = listOf(
    "카고를 배달하면\n쿠폰이 나와요",
    "보상함에\n자동으로 보관돼요",
    "매장에서\n쿠폰 QR을 보여주세요",
    "쓴 뒤\n[사용 완료 처리]",
    "토큰 +$payback 페이백\n${cost}개면 보너스 상자",
)

/** 아쉬운 상자 때의 토큰 가이드 (SC-06 07). */
fun tokenGuideSteps(empty: Int, payback: Int, cost: Int): List<String> = listOf(
    "아쉬운 상자 +$empty",
    "쿠폰 사용 +$payback",
    "${cost}개 모이면 보너스 상자",
)

const val GUIDE_TITLE = "쿠폰은 이렇게 써요"
const val TOKEN_GUIDE_TITLE = "토큰은 이렇게 모여요"
const val TOKEN_NOT_FOR_SALE = "토큰은 살 수 없고 운전과 쿠폰 사용으로만 모여요"
const val EMPTY_BOX_TITLE = "이번 상자는 비어 있었어요"
const val EMPTY_BOX_NEXT = "다음엔 더 좋은 상자가 기다리고 있어요"
const val USE_CONFIRM_TITLE = "매장에서 쓰셨나요?"
const val USE_CONFIRM_BODY = "사용 완료로 바꾸면 되돌릴 수 없어요"

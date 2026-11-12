package com.safecargo.data

import kotlinx.serialization.Serializable

/** 쿠폰 상태 — 한 방향: AVAILABLE → USED / AVAILABLE → VOID. 되돌리기 없음 (PRD 5.2.1). */
enum class CouponStatus { AVAILABLE, USED, VOID }

enum class VoidReason(val label: String) { EXPIRED("기간이 지났어요"), EVENT_ENDED("행사가 끝났어요") }

enum class LedgerReason(val label: String) {
    EMPTY_BOX("아쉬운 상자"), POINT("포인트"), PAYBACK("쿠폰 사용"), BONUS("보너스 상자"),
}

@Serializable
data class WalletItem(
    val id: String,
    val rewardId: String,
    /** 출처 — 운행 결과 id 또는 보너스 상자 id. 같은 출처는 한 번만 들어온다. */
    val source: String,
    val status: CouponStatus = CouponStatus.AVAILABLE,
    val voidReason: VoidReason? = null,
    val receivedDay: Long,
    /** 이 날까지 쓸 수 있다 (포함). 받은 날 + 유효기간(일) — 「14일 남음」. */
    val expiresDay: Long,
    val usedDay: Long? = null,
    /** QR 을 못 읽을 때 불러줄 쿠폰 번호 (예시). */
    val code: String,
    /** 사용 완료 처리 때 받은 페이백 토큰. 0 이면 아직 안 받음. */
    val paybackTokens: Int = 0,
)

@Serializable
data class LedgerEntry(
    val day: Long,
    val reason: LedgerReason,
    val delta: Int,
    /** 운행 · 쿠폰 · 보너스 id — 같은 (reason, ref) 는 한 번만 기록한다. */
    val ref: String,
)

@Serializable
data class WalletData(
    val items: List<WalletItem> = emptyList(),
    val ledger: List<LedgerEntry> = emptyList(),
    val bonusDay: Long = -1,
    val bonusCount: Int = 0,
    val guideViews: Int = 0,
    val seeded: Boolean = false,
) {
    /** 잔액은 원장의 합 — 따로 저장하지 않아 어긋날 수 없다 (전달메모 개발 당부 2). */
    val balance: Int get() = ledger.sumOf { it.delta }

    fun count(s: CouponStatus): Int = items.count { it.status == s }

    fun item(id: String): WalletItem? = items.firstOrNull { it.id == id }

    /** 탭별 정렬 — 사용 가능: 만료 임박 순 · 사용 완료: 최근 사용 순 · 사용 불가: 최근 순 (SC-09 03). */
    fun tab(s: CouponStatus): List<WalletItem> {
        val list = items.filter { it.status == s }
        return when (s) {
            CouponStatus.AVAILABLE -> list.sortedWith(compareBy({ it.expiresDay }, { it.receivedDay }))
            CouponStatus.USED -> list.sortedByDescending { it.usedDay ?: it.receivedDay }
            CouponStatus.VOID -> list.sortedByDescending { it.expiresDay }
        }
    }

    /** 최근 원장 [n] 건 (새 것 먼저). */
    fun recentLedger(n: Int): List<LedgerEntry> = ledger.asReversed().take(n)
}

/** 보너스 상자를 지금 열 수 있는지. */
sealed interface BonusGate {
    data object Open : BonusGate
    data class NeedTokens(val more: Int) : BonusGate
    data object DailyLimit : BonusGate
}

/** 운행 · 보너스 상자에서 들어온 보상 하나. */
data class Grant(val source: String, val rewardId: String, val type: RewardType)

/**
 * 보상함 · 토큰 원장 연산. 전부 순수 함수 — 같은 입력에 같은 결과, 두 번 적용해도 한 번만 반영된다.
 */
object WalletOps {
    /** 운행 결과 · 보너스 결과를 넣는다. 같은 출처가 이미 있으면 그대로 (복구 · 재시작 때 SaveResult 가 다시 와도 안전). */
    fun grant(w: WalletData, g: Grant, today: Long, c: RewardConfig): WalletData {
        if (w.items.any { it.source == g.source } || w.ledger.any { it.ref == g.source && it.reason != LedgerReason.BONUS }) return w
        return when (g.type) {
            RewardType.COUPON -> w.copy(
                items = w.items + WalletItem(
                    id = "w-" + g.source, rewardId = g.rewardId, source = g.source,
                    receivedDay = today, expiresDay = today + c.couponValidDays, code = couponCode(g.source, today),
                ),
            )
            RewardType.POINT -> addToken(w, today, LedgerReason.POINT, c.tokenPoint, g.source)
            RewardType.EMPTY -> addToken(w, today, LedgerReason.EMPTY_BOX, c.tokenEmpty, g.source)
            RewardType.BADGE -> w
        }
    }

    /** 유효기간이 지난 사용 가능 쿠폰을 사용 불가(기간 만료)로 옮긴다 — 앱 실행 · 보상함 진입 때. */
    fun expire(w: WalletData, today: Long): WalletData {
        if (w.items.none { it.status == CouponStatus.AVAILABLE && it.expiresDay < today }) return w
        return w.copy(items = w.items.map {
            if (it.status == CouponStatus.AVAILABLE && it.expiresDay < today) {
                it.copy(status = CouponStatus.VOID, voidReason = VoidReason.EXPIRED)
            } else it
        })
    }

    /**
     * 사용 완료 처리. 사용 가능한 쿠폰만 바뀌고 페이백은 쿠폰 1장당 1회 — 두 번 눌러도 한 번 (전달메모 개발 당부 4).
     * 바뀌지 않으면 같은 [WalletData] 를 돌려준다.
     */
    fun use(w: WalletData, itemId: String, today: Long, c: RewardConfig): WalletData {
        val item = w.item(itemId) ?: return w
        if (item.status != CouponStatus.AVAILABLE) return w
        if (item.expiresDay < today) return expire(w, today)
        val paid = w.ledger.any { it.reason == LedgerReason.PAYBACK && it.ref == itemId }
        val pay = if (paid) 0 else c.tokenPayback
        val used = item.copy(status = CouponStatus.USED, usedDay = today, paybackTokens = pay)
        val next = w.copy(items = w.items.map { if (it.id == itemId) used else it })
        return if (pay > 0) addToken(next, today, LedgerReason.PAYBACK, pay, itemId) else next
    }

    fun todayBonusCount(w: WalletData, today: Long): Int = if (w.bonusDay == today) w.bonusCount else 0

    fun bonusGate(w: WalletData, today: Long, c: RewardConfig): BonusGate = when {
        todayBonusCount(w, today) >= c.bonusPerDay -> BonusGate.DailyLimit
        w.balance < c.bonusCost -> BonusGate.NeedTokens(c.bonusCost - w.balance)
        else -> BonusGate.Open
    }

    /**
     * 보너스 상자: 토큰 차감 · 횟수 · 결과 저장을 한 번에 (연출 전에 끝낸다 — EX-11). 열 수 없으면 null.
     */
    fun openBonus(w: WalletData, bonusId: String, rewardId: String, type: RewardType, today: Long, c: RewardConfig): WalletData? {
        if (bonusGate(w, today, c) != BonusGate.Open) return null
        if (w.ledger.any { it.reason == LedgerReason.BONUS && it.ref == bonusId }) return null
        val paid = w.copy(
            ledger = w.ledger + LedgerEntry(today, LedgerReason.BONUS, -c.bonusCost, bonusId),
            bonusDay = today,
            bonusCount = todayBonusCount(w, today) + 1,
        )
        return grant(paid, Grant(bonusId, rewardId, type), today, c)
    }

    /**
     * 시연 초기값 (FR-DATA-005): 쿠폰 4장(사용 가능 2 · 사용 완료 1 · 사용 불가 1) + 토큰 [RewardConfig.demoTokens].
     * 한 번만 넣는다. 쿠폰이 4종보다 적으면 있는 것을 돌려 쓴다.
     */
    fun seedDemo(w: WalletData, coupons: List<Reward>, today: Long, c: RewardConfig): WalletData {
        if (w.seeded) return w
        if (coupons.isEmpty()) return w.copy(seeded = true)
        // 카탈로그 순서(광장 · 휴게소 · 카페 · 로스터리) 기준 — S4 3단계의 타르트 쿠폰(카페)이 「사용 가능」 맨 위에 오게.
        fun cp(i: Int) = coupons[i % coupons.size].id
        val items = listOf(
            WalletItem("w-demo-1", cp(2), "demo-1", receivedDay = today - 1, expiresDay = today + 3, code = couponCode("demo-1", today)),
            WalletItem("w-demo-2", cp(1), "demo-2", receivedDay = today - 2, expiresDay = today + 12, code = couponCode("demo-2", today)),
            WalletItem(
                "w-demo-3", cp(3), "demo-3", status = CouponStatus.USED, receivedDay = today - 6, expiresDay = today + 7,
                usedDay = today - 2, code = couponCode("demo-3", today), paybackTokens = c.tokenPayback,
            ),
            WalletItem(
                "w-demo-4", cp(0), "demo-4", status = CouponStatus.VOID, voidReason = VoidReason.EXPIRED,
                receivedDay = today - 25, expiresDay = today - 12, code = couponCode("demo-4", today),
            ),
        )
        // 원장: 아쉬운 상자 · 쿠폰 사용 페이백 · 포인트 … 합이 demoTokens 가 되게.
        val ledger = buildList {
            add(LedgerEntry(today - 4, LedgerReason.POINT, c.tokenPoint, "demo-l1"))
            add(LedgerEntry(today - 3, LedgerReason.EMPTY_BOX, c.tokenEmpty, "demo-l2"))
            add(LedgerEntry(today - 2, LedgerReason.PAYBACK, c.tokenPayback, "w-demo-3"))
            val rest = c.demoTokens - sumOf { e: LedgerEntry -> e.delta }
            if (rest != 0) add(LedgerEntry(today - 1, LedgerReason.POINT, rest, "demo-l3"))
        }
        return w.copy(items = w.items + items, ledger = w.ledger + ledger, seeded = true)
    }

    private fun addToken(w: WalletData, day: Long, reason: LedgerReason, delta: Int, ref: String): WalletData {
        if (delta == 0 || w.ledger.any { it.reason == reason && it.ref == ref }) return w
        return w.copy(ledger = w.ledger + LedgerEntry(day, reason, delta, ref))
    }

    /** 「SC-2611-0042」 꼴 쿠폰 번호 (예시) — 출처로 정해지는 값. */
    fun couponCode(source: String, day: Long): String {
        val d = java.time.LocalDate.ofEpochDay(day)
        val n = Math.floorMod(source.hashCode(), 10_000)
        return "SC-%02d%02d-%04d".format(d.year % 100, d.monthValue, n)
    }
}

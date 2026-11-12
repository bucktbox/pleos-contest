package com.safecargo.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safecargo.app.BuildConfig
import com.safecargo.app.TripController
import com.safecargo.data.CouponStatus
import com.safecargo.data.MockCatalog
import com.safecargo.data.RewardType
import com.safecargo.engine.Gear
import com.safecargo.engine.JudgeConfig
import com.safecargo.engine.JudgeEngine
import com.safecargo.engine.Phase
import com.safecargo.engine.TripEvent
import com.safecargo.ui.screens.BonusScreen
import com.safecargo.ui.screens.CouponDetailScreen
import com.safecargo.ui.screens.DestinationScreen
import com.safecargo.ui.screens.RewardExtras
import com.safecargo.ui.screens.WalletScreen
import com.safecargo.ui.screens.EscortScreen
import com.safecargo.ui.screens.LobbyInfo
import com.safecargo.ui.screens.LobbyScreen
import com.safecargo.ui.screens.RewardScreen
import com.safecargo.ui.screens.UnboxingScreen
import com.safecargo.ui.screens.gradeColor
import com.safecargo.ui.screens.lightNote
import com.safecargo.ui.shell.AppShell
import com.safecargo.ui.shell.AppTab
import com.safecargo.ui.shell.TokenChipInfo
import com.safecargo.ui.shell.windowClassOf

/** 정차 화면 안의 경로 (v1.1). 문자열로 두어 rememberSaveable 에 그대로 넣는다. */
private object Route {
    const val LOBBY = "lobby"
    const val WALLET = "wallet"
    const val WALLET_USED = "wallet:used"
    const val BONUS = "bonus"
    const val COUPON = "coupon:"
}

/**
 * 화면 라우터. ESCORTING · PAUSED → 호위, OPENING → 언박싱/리워드, 그 외 → 로비(안에서 목적지 화면 토글).
 */
@Composable
fun CargoApp(
    controller: TripController,
    catalog: MockCatalog,
    config: JudgeConfig,
    onRequestLocation: () -> Unit,
    locationGranted: Boolean,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val streak by controller.streak.collectAsStateWithLifecycle()
    val support by controller.support.collectAsStateWithLifecycle()
    val routeFailed by controller.routeFailed.collectAsStateWithLifecycle()
    val cfg = LocalConfiguration.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // 시스템 「애니메이션 줄이기」 (Q-08) — 애니메이터 배율 0.
    val reducedMotion = androidx.compose.runtime.remember(ctx) {
        android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val wc = windowClassOf(cfg.screenWidthDp, cfg.screenHeightDp)

    // 패밀리 버프는 엔진과 같은 규칙 하나로 판정한다.
    val family = JudgeEngine.familyBuff(state.seats, config)
    val buffActive = family.active
    val borderLight by controller.borderLight.collectAsStateWithLifecycle()
    val wallet by controller.wallet.collectAsStateWithLifecycle()
    val rc = controller.rewards

    // 정차 화면 경로. 주행이 시작되면 어느 탭에 있었든 로비로 — 멈춘 뒤엔 로비부터 (EX-10 · EX-12).
    var route by rememberSaveable { mutableStateOf(Route.LOBBY) }
    LaunchedEffect(state.phase) {
        if (state.phase == Phase.ESCORTING || state.phase == Phase.PAUSED) route = Route.LOBBY
    }
    val goWallet: () -> Unit = {
        controller.refreshWallet()
        route = Route.WALLET
    }
    val tab = when {
        route == Route.LOBBY -> AppTab.LOBBY
        else -> AppTab.WALLET
    }
    Box(Modifier.fillMaxSize()) {
        AppShell(
            state, streak, support, wc, rankingEnabled = config.rankingEnabled,
            buffActive = buffActive, buffMultiplier = config.familyBuffMultiplier,
            tab = tab,
            onTab = { t ->
                when (t) {
                    AppTab.LOBBY -> route = Route.LOBBY
                    AppTab.WALLET -> if (state.phase == Phase.OPENING) {
                        // 결과 화면에서 칩을 누르면 결과를 닫고 보상함으로 (결과는 이미 저장됨).
                        if (state.revealed) { controller.dispatch(TripEvent.CloseResult); goWallet() }
                    } else goWallet()
                    AppTab.RANKING -> Unit
                }
            },
            walletBadge = wallet.count(CouponStatus.AVAILABLE),
            token = TokenChipInfo(wallet.balance, rc.bonusCost),
        ) {
            when (state.phase) {
                Phase.ESCORTING, Phase.PAUSED -> EscortScreen(
                    state, wc,
                    onArrive = { controller.dispatch(TripEvent.ArriveConfirmed) },
                    buffActive = buffActive, buffMultiplier = config.familyBuffMultiplier,
                    keyGoal = config.gradeThresholds.last().toDouble(),
                    idleLoop = config.drivingScreen != "static",
                    motions = controller.motions,
                    reducedMotion = reducedMotion,
                )
                Phase.OPENING -> {
                    val r = state.result
                    when {
                        r == null -> Box(Modifier.fillMaxSize())
                        // 복구 직후엔 기어를 모른다(UNKNOWN) — 정차 · UX 해제가 확인되기 전엔 닫힌 상자만 (애니메이션 · 타이머 없음).
                        !state.revealed -> UnboxingScreen(
                            r, wc,
                            playable = state.gear == Gear.PARK && !state.uxRestricted,
                            lightNote = lightNote(support.lights, config.ambientFlashMs),
                            empty = catalog.reward(r.rewardId)?.type == RewardType.EMPTY,
                            onFinished = { controller.dispatch(TripEvent.RevealFinished) },
                        )
                        else -> {
                            val reward = catalog.reward(r.rewardId)
                            RewardScreen(
                                r, reward, wc,
                                onClose = { route = Route.LOBBY; controller.dispatch(TripEvent.CloseResult) },
                                buffMultiplier = config.familyBuffMultiplier,
                                extras = RewardExtras(
                                    tokenGain = when (reward?.type) {
                                        RewardType.EMPTY -> rc.tokenEmpty
                                        RewardType.POINT -> rc.tokenPoint
                                        else -> 0
                                    },
                                    guideExpanded = wallet.guideViews < rc.guideExpandedTimes,
                                    payback = rc.tokenPayback,
                                    bonusCost = rc.bonusCost,
                                    emptyTokens = rc.tokenEmpty,
                                    onWallet = { controller.dispatch(TripEvent.CloseResult); goWallet() },
                                    onGuideShown = { controller.guideShown() },
                                ),
                            )
                        }
                    }
                }
                Phase.IDLE, Phase.LOADED -> when {
                    route == Route.WALLET || route == Route.WALLET_USED -> WalletScreen(
                        wallet, catalog, rc, controller.today(), controller.bonusGate(), wc,
                        onOpenItem = { route = Route.COUPON + it },
                        onBonus = { route = Route.BONUS },
                        onLobby = { route = Route.LOBBY },
                        onGuideShown = { controller.guideShown() },
                        initialTab = if (route == Route.WALLET_USED) CouponStatus.USED else CouponStatus.AVAILABLE,
                    )
                    route == Route.BONUS -> BonusScreen(
                        wallet, catalog, rc, controller.today(), controller.bonusGate(), wc,
                        onOpen = { controller.openBonus() },
                        onBack = goWallet,
                    )
                    route.startsWith(Route.COUPON) -> {
                        val item = wallet.item(route.removePrefix(Route.COUPON))
                        if (item == null) {
                            LaunchedEffect(Unit) { route = Route.WALLET }
                        } else {
                            CouponDetailScreen(
                                item, catalog.reward(item.rewardId), rc, controller.today(), wallet.balance, wc,
                                onBack = goWallet,
                                onUse = { controller.useCoupon(item.id) },
                                onUsedDone = { route = Route.WALLET_USED },
                            )
                        }
                    }
                    else -> {
                    // 이 분기 안에서만 기억한다 — 호위로 넘어갔다 돌아오면 목적지 화면은 닫힌 상태.
                    var picking by rememberSaveable { mutableStateOf(false) }
                    if (picking) {
                        DestinationScreen(
                            destinations = catalog.destinations,
                            rewards = catalog,
                            wc = wc,
                            onSelect = { d ->
                                controller.dispatch(TripEvent.SelectDestination(d))
                                picking = false
                            },
                            onBack = { picking = false },
                            currentId = state.cargo?.destination?.id,
                        )
                    } else {
                        val cargo = state.cargo
                        LobbyScreen(
                            state = state,
                            wc = wc,
                            locationGranted = locationGranted,
                            onPickDestination = { picking = true },
                            onRequestLocation = onRequestLocation,
                            onDebugReset = if (BuildConfig.DEBUG) ({ controller.debugReset() }) else null,
                            info = LobbyInfo(
                                streak = streak,
                                streakDays = config.streakDays,
                                routeFailed = routeFailed && cargo?.destination != null,
                                dailyCargoName = catalog.daily.name,
                                sponsorLabel = cargo?.destination?.let { catalog.reward(it.rewardId)?.partnerLabel },
                                beltTrusted = config.beltSignalTrusted,
                                support = support,
                                buffActive = buffActive,
                                buffMultiplier = config.familyBuffMultiplier,
                                beltVerified = family.beltVerified,
                            ),
                        )
                    }
                    }
                }
            }
        }
        // 조명 실패 시 대체 (FR-VH-012): 화면 전체 테두리를 등급 색 6dp 로 ambientFlashMs 동안.
        // 화면 색이라 gradeColor — 레전더리는 금색 (11/10 디자인: 보라 테두리 빛 → 금색).
        borderLight?.let { g -> Box(Modifier.matchParentSize().border(6.dp, gradeColor(g))) }
    }
}

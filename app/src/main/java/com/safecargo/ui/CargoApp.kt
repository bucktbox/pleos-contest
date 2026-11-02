package com.safecargo.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import com.safecargo.data.MockCatalog
import com.safecargo.engine.Gear
import com.safecargo.engine.JudgeConfig
import com.safecargo.engine.JudgeEngine
import com.safecargo.engine.Phase
import com.safecargo.engine.TripEvent
import com.safecargo.ui.screens.DestinationScreen
import com.safecargo.ui.screens.EscortScreen
import com.safecargo.ui.screens.LobbyInfo
import com.safecargo.ui.screens.LobbyScreen
import com.safecargo.ui.screens.RewardScreen
import com.safecargo.ui.screens.UnboxingScreen
import com.safecargo.ui.screens.gradeColor
import com.safecargo.ui.shell.AppShell
import com.safecargo.ui.shell.windowClassOf

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
    val wc = windowClassOf(cfg.screenWidthDp, cfg.screenHeightDp)

    // 패밀리 버프는 엔진과 같은 규칙 하나로 판정한다.
    val family = JudgeEngine.familyBuff(state.seats, config)
    val buffActive = family.active
    val borderLight by controller.borderLight.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        AppShell(
            state, streak, support, wc, rankingEnabled = config.rankingEnabled,
            buffActive = buffActive, buffMultiplier = config.familyBuffMultiplier,
        ) {
            when (state.phase) {
                Phase.ESCORTING, Phase.PAUSED -> EscortScreen(
                    state, wc,
                    onArrive = { controller.dispatch(TripEvent.ArriveConfirmed) },
                    buffActive = buffActive, buffMultiplier = config.familyBuffMultiplier,
                )
                Phase.OPENING -> {
                    val r = state.result
                    when {
                        r == null -> Box(Modifier.fillMaxSize())
                        // 복구 직후엔 기어를 모른다(UNKNOWN) — 정차 · UX 해제가 확인되기 전엔 닫힌 상자만 (애니메이션 · 타이머 없음).
                        !state.revealed -> UnboxingScreen(
                            r, wc,
                            playable = state.gear == Gear.PARK && !state.uxRestricted,
                            onFinished = { controller.dispatch(TripEvent.RevealFinished) },
                        )
                        else -> RewardScreen(
                            r, catalog.reward(r.rewardId), wc,
                            onClose = { controller.dispatch(TripEvent.CloseResult) },
                            buffMultiplier = config.familyBuffMultiplier,
                        )
                    }
                }
                Phase.IDLE, Phase.LOADED -> {
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
        // 조명 실패 시 대체 (FR-VH-012): 화면 전체 테두리를 등급 색 6dp 로 ambientFlashMs 동안.
        borderLight?.let { g -> Box(Modifier.matchParentSize().border(6.dp, gradeColor(g))) }
    }
}

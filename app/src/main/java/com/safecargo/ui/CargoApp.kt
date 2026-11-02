package com.safecargo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safecargo.app.BuildConfig
import com.safecargo.app.TripController
import com.safecargo.data.MockCatalog
import com.safecargo.engine.JudgeConfig
import com.safecargo.engine.JudgeEngine
import com.safecargo.engine.Phase
import com.safecargo.engine.TripEvent
import com.safecargo.ui.screens.DestinationScreen
import com.safecargo.ui.screens.LobbyInfo
import com.safecargo.ui.screens.LobbyScreen
import com.safecargo.ui.shell.AppShell
import com.safecargo.ui.shell.windowClassOf
import com.safecargo.ui.theme.SC
import com.safecargo.ui.theme.ScType

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
    val buffActive = JudgeEngine.familyBuffActive(state.seats, config)
    AppShell(
        state, streak, support, wc, rankingEnabled = config.rankingEnabled,
        buffActive = buffActive, buffMultiplier = config.familyBuffMultiplier,
    ) {
        when (state.phase) {
            Phase.ESCORTING, Phase.PAUSED -> Placeholder("EscortScreen") // Task 10
            Phase.OPENING -> Placeholder(if (state.revealed) "RewardScreen" else "UnboxingScreen") // Task 11
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
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(name, style = ScType.title, color = SC.textDim)
    }
}

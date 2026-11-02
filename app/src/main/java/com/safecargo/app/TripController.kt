package com.safecargo.app

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.safecargo.data.Streak
import com.safecargo.data.TripStore
import com.safecargo.engine.Gear
import com.safecargo.engine.Grade
import com.safecargo.engine.JudgeConfig
import com.safecargo.engine.Phase
import com.safecargo.engine.TripEffect
import com.safecargo.engine.TripEvent
import com.safecargo.engine.TripState
import com.safecargo.engine.TripStateMachine
import com.safecargo.engine.VehicleSignal
import com.safecargo.sdk.AmbientLight
import com.safecargo.sdk.LocationOnce
import com.safecargo.sdk.NaviSource
import com.safecargo.sdk.SupportStatus
import com.safecargo.sdk.TtsPlayer
import com.safecargo.sdk.UxGate
import com.safecargo.sdk.VehicleSource
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 엔진([TripStateMachine])과 SDK 래퍼를 잇는다. 모든 상태 변경은 메인 스레드에서만 일어난다.
 * 로그에는 이벤트 이름 · 단계 전이 · 열쇠 수만 남긴다 (좌표 · 원시 조향값 금지).
 */
class TripController(
    private val machine: TripStateMachine,
    private val store: TripStore,
    private val vehicle: VehicleSource,
    private val ux: UxGate,
    private val location: LocationOnce,
    private val light: AmbientLight,
    private val tts: TtsPlayer,
    private val navi: NaviSource,
    private val config: JudgeConfig,
    private val scope: CoroutineScope,
    private val todayEpochDay: () -> Long = { LocalDate.now().toEpochDay() },
) {
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(TripState())
    val state: StateFlow<TripState> = _state.asStateFlow()

    val support: StateFlow<SupportStatus> get() = vehicle.support

    /** 조명 실패 시 화면 테두리 대체 (FR-VH-012). ambientFlashMs 뒤 null. */
    private val _borderLight = MutableStateFlow<Grade?>(null)
    val borderLight: StateFlow<Grade?> = _borderLight.asStateFlow()
    private var borderJob: Job? = null

    private val _streak = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak.asStateFlow()

    /** 내비 경로 요청 실패 — 로비가 「내비 안내를 보내지 못했어요」 를 보여준다. */
    val routeFailed: StateFlow<Boolean> get() = navi.routeFailed

    @Volatile var appVisible: Boolean = false

    private var started = false

    fun start() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "TripController.start off main" }
        if (started) return
        started = true
        _streak.value = Streak.days(store.results(), todayEpochDay())
        location.start()
        tts.start()
        navi.start()
        store.loadActive()?.let { dispatch(TripEvent.Restore(it)) }
        // 구독 먼저: 초기 기어 · UX 값은 start() 안에서 동기 emit 되고 replay 가 없다.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            vehicle.signals.collect { onSignal(it) }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            ux.signals.collect { onSignal(it) }
        }
        vehicle.start()
        ux.start()
    }

    private fun onSignal(sig: VehicleSignal) {
        if (sig is VehicleSignal.GearChanged && sig.gear == Gear.PARK) vehicle.refreshSeats()
        dispatch(TripEvent.Signal(sig))
    }

    /** 메인 스레드 전용. 다른 스레드에서 오면 메인으로 넘긴다. */
    fun dispatch(e: TripEvent) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { dispatch(e) }
            return
        }
        val old = _state.value
        val step = machine.reduce(old, e)
        _state.value = step.state
        log(e, old, step.state)
        step.effects.forEach(::run)
    }

    fun onAppHidden() {
        light.restore()
    }

    /** BuildConfig.DEBUG 에서만 호출. */
    fun debugReset() {
        store.clearAll()
        _streak.value = 0
        dispatch(TripEvent.Reset)
    }

    private fun run(effect: TripEffect) {
        when (effect) {
            TripEffect.RequestLocation -> location.request { dispatch(TripEvent.LocationResolved(it)) }
            is TripEffect.RequestRoute -> navi.requestRoute(effect.destination)
            is TripEffect.FlashLights -> {
                val g = effect.grade
                val rgb = config.gradeLightColors[g] ?: 0xFFFFFF
                light.flash(rgb, config.ambientFlashMs) { ok -> if (!ok) showBorder(g) }
            }
            TripEffect.RestoreLights -> {
                light.restore()
                borderJob?.cancel()
                _borderLight.value = null
            }
            TripEffect.AnnounceArrival -> tts.speak(
                if (appVisible) "배송 완료! 상자를 열어볼까요?" else "카고가 도착했어요. 세이프 카고를 열어 개봉하세요",
            )
            TripEffect.StopSpeech -> tts.stop()
            is TripEffect.PersistActive -> store.saveActive(effect.state)
            TripEffect.ClearActive -> store.clearActive()
            is TripEffect.SaveResult -> {
                store.appendResult(effect.result)
                _streak.value = Streak.days(store.results(), todayEpochDay())
            }
        }
    }

    private fun showBorder(g: Grade) {
        borderJob?.cancel()
        _borderLight.value = g
        borderJob = scope.launch {
            delay(config.ambientFlashMs)
            _borderLight.value = null
        }
    }

    private fun log(e: TripEvent, old: TripState, new: TripState) {
        val name = when (e) {
            is TripEvent.Signal -> e.signal::class.simpleName ?: "Signal"
            is TripEvent.SelectDestination -> "SelectDestination(${e.destination.id})"
            is TripEvent.LocationResolved -> "LocationResolved(${if (e.fix != null) "fix" else "none"})"
            else -> e::class.simpleName ?: "Event"
        }
        val oldKeys = keysOf(old)
        val newKeys = keysOf(new)
        // 조향 · 좌석 신호는 잦으므로 단계나 열쇠가 바뀔 때만 남긴다.
        val noisy = e is TripEvent.Signal &&
            (e.signal is VehicleSignal.SteerChanged || e.signal is VehicleSignal.SeatsChanged || e.signal is VehicleSignal.TurnChanged)
        if (noisy && old.phase == new.phase && oldKeys == newKeys) return
        val extra = if (new.phase == Phase.OPENING && old.phase != Phase.OPENING) " rewardId=${new.result?.rewardId}" else ""
        Log.i(TAG, "$name : ${old.phase} -> ${new.phase} keys=$newKeys$extra")
    }

    private fun keysOf(s: TripState): Int = s.result?.keys ?: s.judge.keys.toInt()

    private companion object { const val TAG = "SAFECARGO" }
}

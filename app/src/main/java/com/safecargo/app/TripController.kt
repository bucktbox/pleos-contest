package com.safecargo.app

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.safecargo.data.BonusGate
import com.safecargo.data.Grant
import com.safecargo.data.MockCatalog
import com.safecargo.data.RewardConfig
import com.safecargo.data.RewardDraw
import com.safecargo.data.RewardIds
import com.safecargo.data.RewardType
import com.safecargo.data.Streak
import com.safecargo.data.TripStore
import com.safecargo.data.WalletData
import com.safecargo.data.WalletOps
import com.safecargo.data.WalletStore
import com.safecargo.engine.DrivingMotion
import com.safecargo.engine.HarshDetector
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
import com.safecargo.sdk.SpeedSource
import com.safecargo.sdk.SupportStatus
import com.safecargo.sdk.TtsPlayer
import com.safecargo.sdk.UxGate
import com.safecargo.sdk.VehicleSource
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

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
    private val catalog: MockCatalog,
    private val rewardConfig: RewardConfig,
    private val walletStore: WalletStore,
    private val speed: SpeedSource,
    private val todayEpochDay: () -> Long = { LocalDate.now().toEpochDay() },
    private val random: Random = Random.Default,
) {
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(TripState())
    val state: StateFlow<TripState> = _state.asStateFlow()

    /** 차량 신호 지원 + 차속(v1.1 · EX-09). */
    val support: StateFlow<SupportStatus> by lazy {
        combine(vehicle.support, speed.available) { s, sp -> s.copy(speed = sp) }
            .stateIn(scope, SharingStarted.Eagerly, vehicle.support.value)
    }

    private val _wallet = MutableStateFlow(WalletData())
    /** 보상함 · 토큰 원장 (FR-DATA-004). 바뀔 때마다 디스크에 쓴다. */
    val wallet: StateFlow<WalletData> = _wallet.asStateFlow()

    /** 시연 모드 — 설정 파일 또는 디버그 인텐트(`--ez demo true`). */
    @Volatile var demoMode: Boolean = rewardConfig.demoMode
        private set

    private val detector = HarshDetector(config)
    private val _motions = MutableSharedFlow<DrivingMotion>(extraBufferCapacity = 8)
    /** 거친 운전 이벤트 (FR-VH-014) — 호위 중에만 나온다. 저장하지 않는다. */
    val motions: SharedFlow<DrivingMotion> = _motions.asSharedFlow()

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
        _wallet.value = WalletOps.expire(walletStore.load(), todayEpochDay())
        if (demoMode) seedDemo()
        store.loadActive()?.let { dispatch(TripEvent.Restore(it)) }
        // 구독 먼저: 초기 기어 · UX 값은 start() 안에서 동기 emit 되고 replay 가 없다.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            vehicle.signals.collect { onSignal(it) }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            ux.signals.collect { onSignal(it) }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            speed.samples.collect { s ->
                if (config.drivingMotion && _state.value.phase == Phase.ESCORTING) detector.onSpeed(s.mps, s.atMs)?.let(::emitMotion)
            }
        }
        vehicle.start()
        ux.start()
        speed.start()
    }

    /** CAR_SPEED 권한을 받은 뒤 다시 등록한다. */
    fun onSpeedPermissionChanged() {
        speed.stop()
        speed.start()
    }

    private fun emitMotion(m: DrivingMotion) {
        Log.i(TAG, "motion ${m.name}")
        _motions.tryEmit(m)
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
        if (step.state.phase == Phase.ESCORTING && old.phase != Phase.ESCORTING) detector.reset()
        if (config.drivingMotion && e is TripEvent.Signal && step.state.phase == Phase.ESCORTING) {
            val sig = e.signal
            if (sig is VehicleSignal.SteerChanged) detector.onSteer(sig.deg, sig.atMs)?.let(::emitMotion)
        }
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

    // ---------------------------------------------------------------- 보상함 (v1.1)

    /** 보상 수치 (reward.json). */
    val rewards: RewardConfig get() = rewardConfig

    fun today(): Long = todayEpochDay()

    /** 정차 화면에서만 보상함을 바꾼다 — 주행 중 · 일시 정차에는 아무 일도 하지 않는다 (PRD 5.3). */
    private val walletAllowed: Boolean
        get() = _state.value.phase.let { it == Phase.IDLE || it == Phase.LOADED || it == Phase.OPENING }

    private fun updateWallet(f: (WalletData) -> WalletData): Boolean {
        val old = _wallet.value
        val n = f(old)
        if (n == old) return false
        _wallet.value = n
        walletStore.save(n)
        return true
    }

    /** 보상함 진입 때 — 기간이 지난 쿠폰을 사용 불가로 옮긴다. */
    fun refreshWallet() { updateWallet { WalletOps.expire(it, todayEpochDay()) } }

    /** 사용 완료 처리. 받은 페이백 토큰 수를 돌려준다 (안 바뀌었으면 null). */
    fun useCoupon(itemId: String): Int? {
        if (!walletAllowed) return null
        val changed = updateWallet { WalletOps.use(it, itemId, todayEpochDay(), rewardConfig) }
        val item = _wallet.value.item(itemId)
        Log.i(TAG, "coupon use changed=$changed status=${item?.status}")
        return if (changed) item?.paybackTokens else null
    }

    fun bonusGate(): BonusGate = WalletOps.bonusGate(_wallet.value, todayEpochDay(), rewardConfig)

    /** 보너스 상자: 차감 · 추첨 · 저장을 먼저 끝낸다 (EX-11). 받은 보상 id, 열 수 없으면 null. */
    fun openBonus(): String? {
        if (!walletAllowed) return null
        val today = todayEpochDay()
        val id = "bonus-" + java.util.UUID.randomUUID()
        val rewardId = RewardDraw.bonus(catalog.coupons, rewardConfig, random.nextInt(100), random.nextInt(1000), demoMode)
        val type = catalog.reward(rewardId)?.type ?: RewardType.POINT
        val n = WalletOps.openBonus(_wallet.value, id, rewardId, type, today, rewardConfig) ?: return null
        updateWallet { n }
        Log.i(TAG, "bonus rewardId=$rewardId")
        return rewardId
    }

    /** 사용 가이드를 펼친 채 보여 준 횟수 (처음 3회만 펼침 — FR-APP-015). */
    fun guideShown() { updateWallet { it.copy(guideViews = it.guideViews + 1) } }

    /** 디버그 · 시연: 시연 모드를 켜고(첫 실행이면 초기값), [reset] 이면 보상함을 비우고 다시 넣는다. */
    fun setDemo(on: Boolean, reset: Boolean) {
        demoMode = on
        if (reset) updateWallet { WalletData() }
        if (on) seedDemo()
        Log.i(TAG, "demo on=$on reset=$reset balance=${_wallet.value.balance}")
    }

    private fun seedDemo() {
        updateWallet { WalletOps.seedDemo(it, catalog.coupons, todayEpochDay(), rewardConfig) }
    }

    /** 상태 머신의 개봉 추첨 — 시연 모드면 등급별 고정 결과. */
    fun drawTripReward(grade: Grade, sponsorCoupon: String?, daily: Boolean): String =
        RewardDraw.trip(grade, sponsorCoupon, daily, rewardConfig, demoMode, random.nextInt(100))

    private fun grantResult(r: com.safecargo.engine.TripResult) {
        val type = catalog.reward(r.rewardId)?.type ?: when (r.rewardId) {
            RewardIds.EMPTY -> RewardType.EMPTY
            else -> RewardType.POINT
        }
        updateWallet { WalletOps.grant(it, Grant(r.id, r.rewardId, type), r.epochDay, rewardConfig) }
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
                grantResult(effect.result)
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

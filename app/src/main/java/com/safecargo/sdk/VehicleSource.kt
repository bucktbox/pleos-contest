package com.safecargo.sdk

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.safecargo.engine.Seat
import com.safecargo.engine.SeatSnapshot
import com.safecargo.engine.TurnSignal
import com.safecargo.engine.VehicleSignal
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** null = 확인 중, true = 지원/수신됨, false = 실패. */
data class SupportStatus(
    val gear: Boolean? = null,
    val turn: Boolean? = null,
    val steer: Boolean? = null,
    val seats: Boolean? = null,
    val belts: Boolean? = null,
    val lights: Boolean? = null,
    val location: Boolean? = null,
)

class VehicleSource(
    private val context: Context,
    private val steerSign: Float,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private val _signals = MutableSharedFlow<VehicleSignal>(extraBufferCapacity = 64)
    val signals: SharedFlow<VehicleSignal> = _signals.asSharedFlow()

    private val _support = MutableStateFlow(SupportStatus())
    val support: StateFlow<SupportStatus> = _support.asStateFlow()

    @Volatile
    var vehicle: Vehicle? = null
        private set

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false

    private val seatValues = listOf(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT)
    private val lock = Any()
    private val occupied = mutableMapOf<Seat, Boolean?>()
    private val belted = mutableMapOf<Seat, Boolean?>()
    private var lastTurn: TurnSignal? = null
    private var lastSteer: Float? = null

    private fun emit(s: VehicleSignal) { _signals.tryEmit(s) }
    private fun update(f: (SupportStatus) -> SupportStatus) { _support.update(f) }
    private fun fail(src: String, e: Throwable) = Log.w(TAG, "$src FAILED ${e.javaClass.simpleName}")

    // ---------- 리스너 ----------
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = onGear(gear)
        override fun onFailed(e: Exception) { fail("gear", e); update { it.copy(gear = false) } }
    }
    private val turnListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) = onTurn(value)
        override fun onFailed(e: Exception) { fail("turn", e); update { it.copy(turn = false) } }
    }
    private val steerListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) = onSteer(angle)
        override fun onFailed(e: Exception) { fail("steer", e); update { it.copy(steer = false) } }
    }
    private val occupancyListeners = seatValues.associateWith { sv ->
        object : SeatOccupancyListener {
            override fun onSeatOccupancyUpdated(v: Boolean?) = onOccupancy(sv, v)
            override fun onFailed(e: Exception) { fail("occupied/${sv.name}", e); update { it.copy(seats = false) } }
        }
    }
    private val beltListeners = seatValues.associateWith { sv ->
        object : SeatBeltBuckledStateListener {
            override fun onSeatBeltBuckledStateUpdated(v: Boolean?) = onBelt(sv, v)
            override fun onFailed(e: Exception) { fail("belt/${sv.name}", e); update { it.copy(belts = false) } }
        }
    }

    // ---------- 콜백 처리 ----------
    private fun onGear(g: VehicleGear?) {
        update { it.copy(gear = true) }
        emit(VehicleSignal.GearChanged(mapGear(g), now()))
    }

    private fun onTurn(v: TurnSignalValue?) {
        update { it.copy(turn = true) }
        val mapped = mapTurn(v)
        synchronized(lock) {
            if (mapped == lastTurn) return
            lastTurn = mapped
        }
        emit(VehicleSignal.TurnChanged(mapped, now()))
    }

    private fun onSteer(raw: Float?) {
        val deg = steerDeg(raw, steerSign) ?: return
        update { it.copy(steer = true) }
        synchronized(lock) {
            if (deg == lastSteer) return
            lastSteer = deg
        }
        emit(VehicleSignal.SteerChanged(deg, now()))
    }

    private fun onOccupancy(sv: SeatValue, v: Boolean?) {
        val seat = mapSeat(sv) ?: return
        update { it.copy(seats = true) }
        emitSeats { occupied[seat] = v }
    }

    private fun onBelt(sv: SeatValue, v: Boolean?) {
        val seat = mapSeat(sv) ?: return
        update { it.copy(belts = true) }
        emitSeats { belted[seat] = v }
    }

    private fun emitSeats(mutate: () -> Unit) {
        val snap = synchronized(lock) {
            mutate()
            SeatSnapshot(occupied.toMap(), belted.toMap())
        }
        emit(VehicleSignal.SeatsChanged(snap, now()))
    }

    // ---------- 생명주기 ----------
    fun start() {
        if (running) return
        running = true
        val v = Vehicle(context)
        vehicle = v
        runCatching { v.initialize() }.onFailure {
            fail("vehicle.init", it)
            giveUp()
            return
        }
        probe(1)
    }

    /** initialize 는 비동기 바인딩이라 첫 get 이 성공할 때까지 500ms 간격 최대 10회 재시도한다. */
    private fun probe(attempt: Int) {
        if (!running) return
        val v = vehicle ?: return
        v.getGear().getCurrentGearSelectionState(
            { gear ->
                if (!running) return@getCurrentGearSelectionState
                main.post { if (running) { register(); readInitial(); onGear(gear) } }
            },
            { e ->
                if (!running) return@getCurrentGearSelectionState
                fail("gear.initial(attempt=$attempt)", e)
                if (attempt < MAX_PROBE) main.postDelayed({ probe(attempt + 1) }, PROBE_INTERVAL_MS) else giveUp()
            },
        )
    }

    private fun giveUp() {
        update { it.copy(gear = false) }
        emit(VehicleSignal.Disconnected(now()))
    }

    private fun register() {
        val v = vehicle ?: return
        runCatching { v.getGear().registerGearSelection(gearListener) }.onFailure { fail("gear.register", it); update { s -> s.copy(gear = false) } }
        runCatching { v.getTurnSignal().registerTurnSignalState(turnListener) }.onFailure { fail("turn.register", it); update { s -> s.copy(turn = false) } }
        runCatching { v.getSteeringWheel().registerSteeringWheelAngle(steerListener) }.onFailure { fail("steer.register", it); update { s -> s.copy(steer = false) } }
        seatValues.forEach { sv ->
            runCatching { v.getSeat().registerSeatOccupancy(SeatArea(sv), occupancyListeners.getValue(sv)) }.onFailure { fail("occupied.register/${sv.name}", it); update { s -> s.copy(seats = false) } }
            runCatching { v.getSeat().registerSeatBeltBuckledState(SeatArea(sv), beltListeners.getValue(sv)) }.onFailure { fail("belt.register/${sv.name}", it); update { s -> s.copy(belts = false) } }
        }
    }

    private fun readInitial() {
        val v = vehicle ?: return
        v.getTurnSignal().getCurrentTurnSignalState({ onTurn(it) }, { fail("turn.initial", it); update { s -> s.copy(turn = false) } })
        v.getSteeringWheel().getCurrentSteeringWheelAngle({ onSteer(it) }, { fail("steer.initial", it); update { s -> s.copy(steer = false) } })
        refreshSeats()
    }

    /** 좌석 착석 · 벨트 현재값을 다시 읽는다. */
    fun refreshSeats() {
        val v = vehicle ?: return
        seatValues.forEach { sv ->
            v.getSeat().getCurrentSeatOccupancy(SeatArea(sv), { onOccupancy(sv, it) }, { fail("occupied.initial/${sv.name}", it); update { s -> s.copy(seats = false) } })
            v.getSeat().getCurrentSeatBeltBuckledState(SeatArea(sv), { onBelt(sv, it) }, { fail("belt.initial/${sv.name}", it); update { s -> s.copy(belts = false) } })
        }
    }

    fun stop() {
        if (!running) return
        running = false
        main.removeCallbacksAndMessages(null)
        val v = vehicle
        if (v != null) {
            runCatching {
                v.getGear().unregisterGearSelection(gearListener)
                v.getTurnSignal().unregisterTurnSignalState(turnListener)
                v.getSteeringWheel().unregisterSteeringWheelAngle(steerListener)
                seatValues.forEach { sv ->
                    v.getSeat().unregisterSeatOccupancy(SeatArea(sv), occupancyListeners.getValue(sv))
                    v.getSeat().unregisterSeatBeltBuckledState(SeatArea(sv), beltListeners.getValue(sv))
                }
            }.onFailure { fail("vehicle.unregister", it) }
        }
        vehicle = null
        synchronized(lock) { lastTurn = null; lastSteer = null }
    }

    private companion object {
        const val TAG = "SAFECARGO"
        const val MAX_PROBE = 10
        const val PROBE_INTERVAL_MS = 500L
    }
}

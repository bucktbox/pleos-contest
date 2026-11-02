package com.safecargo.poc

import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.app.Activity
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/**
 * SafeCargo POC — 버리는 진단 앱.
 *
 * 확인할 것 (PRD 미결 번호):
 * - AG-03 기어 콜백 · U-02 각 신호 콜백 주기와 값 범위 · AG-04 조향각 범위
 * - AG-05 앰비언트 조명 get/set/원복 · U-09 내비 목적지/도착 이벤트 · ISSUE-06 ON_DEVICE TTS
 * - CarUxRestrictions 변화 (기어 D/P 와의 관계)
 * - 참고: 표준 AAOS 차속(PERF_VEHICLE_SPEED) 이 열리는지
 *
 * 모든 이벤트는 logcat 태그 SCPOC + 앱 내부 파일(files/poc-log.txt) 에
 * "경과ms | 소스 | 값 | 직전 같은 소스 이후 ms | 스레드" 형식으로 남긴다.
 */
class MainActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private val t0 = SystemClock.elapsedRealtime()
    private val lastAt = HashMap<String, Long>()
    private lateinit var logView: TextView
    private lateinit var logFile: File

    private var vehicle: Vehicle? = null
    private var navi: NaviHelper? = null
    private var tts: TextToSpeech? = null
    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private var propertyManager: CarPropertyManager? = null

    private val seats = listOf(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT)

    // ---------- 로그 ----------
    @Synchronized
    private fun log(source: String, value: String) {
        val now = SystemClock.elapsedRealtime()
        val gap = lastAt[source]?.let { now - it }?.toString() ?: "-"
        lastAt[source] = now
        val line = "${now - t0} | $source | $value | gap=$gap | ${Thread.currentThread().name}"
        Log.i(TAG, line)
        runCatching { logFile.appendText(line + "\n") }
        main.post { logView.append(line + "\n") }
    }

    private fun fail(source: String, e: Throwable) = log(source, "FAILED ${e.javaClass.simpleName}: ${e.message}")

    // ---------- 리스너 ----------
    private val gearListener = object : GearSelectionListener {
        private var wasDriving = false
        override fun onSelected(gear: VehicleGear?) {
            log("gear", gear?.name.toString())
            if (gear == VehicleGear.GEAR_DRIVE) wasDriving = true
            // 실제 앱의 "주행 → P 정차 → 개봉" 지점. POC 에선 여기서 조명·TTS 를 자동 시험한다
            if (gear == VehicleGear.GEAR_PARK && wasDriving) {
                wasDriving = false
                main.post {
                    log("scenario", "D→P 감지: 조명·TTS 시험")
                    ambientTest(0xFFAA00FF.toInt(), "Purple(VC #aa00ff)")
                    runCatching { tts?.speak("배송 완료! 상자를 열어볼까요?", true) }.onFailure { fail("tts.speak", it) }
                }
            }
        }
        override fun onFailed(e: Exception) = fail("gear", e)
    }
    private val turnListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) = log("turn", value?.name.toString())
        override fun onFailed(e: Exception) = fail("turn", e)
    }
    private val steerListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) = log("steer", angle.toString())
        override fun onFailed(e: Exception) = fail("steer", e)
    }
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) = log("brake", status.toString())
        override fun onFailed(e: Exception) = fail("brake", e)
    }
    private val tripListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(distance: Float?) = log("tripDist(m)", distance.toString())
        override fun onFailed(e: Exception) = fail("tripDist(m)", e)
    }
    private val occupancyListeners = seats.associateWith { s ->
        object : SeatOccupancyListener {
            override fun onSeatOccupancyUpdated(v: Boolean?) = log("occupied/${s.name}", v.toString())
            override fun onFailed(e: Exception) = fail("occupied/${s.name}", e)
        }
    }
    private val beltListeners = seats.associateWith { s ->
        object : SeatBeltBuckledStateListener {
            override fun onSeatBeltBuckledStateUpdated(v: Boolean?) = log("belt/${s.name}", v.toString())
            override fun onFailed(e: Exception) = fail("belt/${s.name}", e)
        }
    }
    private val naviListener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) = log("navi.routeStarted", "${info.name} dist=${info.distance} dur=${info.duration}")
        override fun onRouteEnded() = log("navi.routeEnded", "")
        override fun onRouteCancelled() = log("navi.routeCancelled", "")
        override fun onDestinationChanged() = log("navi.destinationChanged", "")
        override fun onDestinationInfo(info: DestinationInfo) = log("navi.destinationInfo", "${info.name} / ${info.address} remain=${info.remainDistance}m poi=${info.poiId}")
        override fun onDestinationArrived(info: DestinationArrivedInfo) = log("navi.destinationArrived", "${info.name} / ${info.address} dist=${info.distance} dur=${info.duration}")
        override fun onError(code: NaviErrorCode) = log("navi.error", code.toString())
    }
    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r -> log("uxRestrictions", describe(r)) }
    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        private var last: Any? = null
        override fun onChangeEvent(v: CarPropertyValue<*>) {
            if (v.value != last) { last = v.value; log("aaos.speed(m/s)", v.value.toString()) }
        }
        override fun onErrorEvent(propId: Int, zone: Int) = log("aaos.speed", "error prop=$propId zone=$zone")
    }

    private fun describe(r: CarUxRestrictions) =
        "requiresDO=${r.isRequiresDistractionOptimization} active=0x${Integer.toHexString(r.activeRestrictions)}"

    // ---------- 생명주기 ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logFile = File(filesDir, "poc-log.txt").apply { writeText("") }
        setContentView(buildUi())
        log("app", "start")
        requestPermissions(arrayOf("android.car.permission.CAR_SPEED"), 1)

        startVehicle()
        startCarApi()
        startNavi()
        startTts()
    }

    override fun onDestroy() {
        runCatching { unregisterVehicle() }.onFailure { fail("vehicle.unregister", it) }
        runCatching { vehicle?.release() }
        runCatching { navi?.removeListener(naviListener); navi?.release() }
        runCatching { tts?.release() }
        runCatching { uxManager?.unregisterListener() }
        runCatching { propertyManager?.unregisterCallback(speedCallback) }
        runCatching { car?.disconnect() }
        super.onDestroy()
    }

    // ---------- Vehicle ----------
    private fun startVehicle() {
        val v = Vehicle(this)
        vehicle = v
        runCatching { v.initialize() }.onFailure { fail("vehicle.init", it); return }
        log("vehicle.init", "called")
        probeVehicleThenRegister(attempt = 1)
    }

    /** initialize 는 비동기 바인딩일 수 있어, 첫 get 이 성공할 때까지 재시도한 뒤 리스너를 등록한다. */
    private fun probeVehicleThenRegister(attempt: Int) {
        val v = vehicle ?: return
        v.getGear().getCurrentGearSelectionState(
            { gear ->
                log("gear.initial", "${gear?.name} (attempt=$attempt)")
                main.post { registerVehicle(); readInitialValues() }
            },
            { e ->
                fail("gear.initial(attempt=$attempt)", e)
                if (attempt < 10) main.postDelayed({ probeVehicleThenRegister(attempt + 1) }, 500)
                else main.post { registerVehicle() } // 기어가 안 돼도 나머지 신호는 확인
            },
        )
    }

    private fun registerVehicle() {
        val v = vehicle ?: return
        runCatching { v.getGear().registerGearSelection(gearListener) }.onFailure { fail("gear.register", it) }
        runCatching { v.getTurnSignal().registerTurnSignalState(turnListener) }.onFailure { fail("turn.register", it) }
        runCatching { v.getSteeringWheel().registerSteeringWheelAngle(steerListener) }.onFailure { fail("steer.register", it) }
        runCatching { v.getBrake().registerBrakePedalStatus(brakeListener) }.onFailure { fail("brake.register", it) }
        runCatching { v.getOdometer().registerTripCurrentDrivingDistance(tripListener) }.onFailure { fail("trip.register", it) }
        seats.forEach { s ->
            runCatching { v.getSeat().registerSeatOccupancy(SeatArea(s), occupancyListeners.getValue(s)) }.onFailure { fail("occupied.register/${s.name}", it) }
            runCatching { v.getSeat().registerSeatBeltBuckledState(SeatArea(s), beltListeners.getValue(s)) }.onFailure { fail("belt.register/${s.name}", it) }
        }
        log("vehicle.register", "done")
    }

    private fun unregisterVehicle() {
        val v = vehicle ?: return
        v.getGear().unregisterGearSelection(gearListener)
        v.getTurnSignal().unregisterTurnSignalState(turnListener)
        v.getSteeringWheel().unregisterSteeringWheelAngle(steerListener)
        v.getBrake().unregisterBrakePedalStatus(brakeListener)
        v.getOdometer().unregisterTripCurrentDrivingDistance(tripListener)
        seats.forEach { s ->
            v.getSeat().unregisterSeatOccupancy(SeatArea(s), occupancyListeners.getValue(s))
            v.getSeat().unregisterSeatBeltBuckledState(SeatArea(s), beltListeners.getValue(s))
        }
    }

    private fun readInitialValues() {
        val v = vehicle ?: return
        v.getTurnSignal().getCurrentTurnSignalState({ log("turn.initial", it?.name.toString()) }, { fail("turn.initial", it) })
        v.getSteeringWheel().getCurrentSteeringWheelAngle({ log("steer.initial", it.toString()) }, { fail("steer.initial", it) })
        v.getBrake().getCurrentBrakePedalStatus({ log("brake.initial", it.toString()) }, { fail("brake.initial", it) })
        v.getOdometer().getTripCurrentDrivingDistance({ log("tripDist.initial", it.toString()) }, { fail("tripDist.initial", it) })
        seats.forEach { s ->
            v.getSeat().getCurrentSeatOccupancy(SeatArea(s), { log("occupied.initial/${s.name}", it.toString()) }, { fail("occupied.initial/${s.name}", it) })
            v.getSeat().getCurrentSeatBeltBuckledState(SeatArea(s), { log("belt.initial/${s.name}", it.toString()) }, { fail("belt.initial/${s.name}", it) })
        }
    }

    // ---------- 앰비언트 조명 (AG-05): 지원 확인 → 원래 값 백업 → 색 변경 → 3초 뒤 원복 ----------
    private fun ambientTest(color: Int, label: String) {
        val light = vehicle?.getLight() ?: return
        light.checkAmbientLightBrightnessColorCapabilities(
            { config, areas ->
                log("ambient.capability", "$config areas=${areas.map { it.values.joinToString() }}")
                val area = areas.firstOrNull() ?: return@checkAmbientLightBrightnessColorCapabilities log("ambient", "지원 영역 없음")
                light.getAmbientLightBrightnessColor(area, { originalOrNull ->
                    val original = originalOrNull ?: return@getAmbientLightBrightnessColor log("ambient.backup", "null — 원복 불가라 변경하지 않음")
                    log("ambient.backup", "0x${Integer.toHexString(original)}")
                    light.setAmbientLightBrightnessColor(area, color, { _, applied ->
                        log("ambient.set", "$label 요청=0x${Integer.toHexString(color)} 결과=${applied?.let { Integer.toHexString(it) }}")
                        main.postDelayed({
                            light.setAmbientLightBrightnessColor(area, original,
                                { _, r -> log("ambient.restore", "${r?.let { Integer.toHexString(it) }}") },
                                { fail("ambient.restore", it) })
                        }, 3000)
                    }, { fail("ambient.set", it) })
                }, { fail("ambient.backup", it) })
            },
            { fail("ambient.capability", it) },
        )
    }

    // ---------- 표준 AAOS: UX 제한 + (참고) 차속 ----------
    private fun startCarApi() {
        runCatching {
            val c = Car.createCar(this)
            car = c
            uxManager = (c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager).also { m ->
                log("uxRestrictions.initial", describe(m.currentCarUxRestrictions))
                m.registerListener(uxListener)
            }
            propertyManager = (c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager).also { pm ->
                val ok = pm.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_ONCHANGE)
                log("aaos.speed.register", "result=$ok")
            }
        }.onFailure { fail("carApi", it) }
    }

    // ---------- 내비 (U-09) ----------
    private fun startNavi() {
        runCatching {
            val n = NaviHelper(this)
            navi = n
            n.initialize(
                {
                    log("navi.init", "ok")
                    n.addListener(naviListener)
                    n.getDestinationInfo()
                    // adb: am start ... --ez route true → 앱이 내비에 목적지를 보내는 경로 시험
                    if (intent.getBooleanExtra("route", false)) requestTestRoute()
                },
                { fail("navi.init", it) },
            )
        }.onFailure { fail("navi", it) }
    }

    private fun requestTestRoute() {
        runCatching {
            navi?.requestRoute(
                RouteInfo(37.5665, 126.9780, null, null, "서울시청", "", "서울 중구 세종대로 110", "", RouteOption.RECOMMENDED, "", false),
            )
            log("navi.requestRoute", "서울시청")
        }.onFailure { fail("navi.requestRoute", it) }
    }

    // ---------- TTS (ISSUE-06) ----------
    private fun startTts() {
        runCatching {
            val t = TextToSpeech(this, Mode.ON_DEVICE)
            tts = t
            // SDK 내부 lateinit 때문에 initialize 성공 뒤에 리스너를 붙인다 (POC 1차 실행에서 확인)
            val events = object : EventListener {
                override fun onStart() = log("tts.event", "start")
                override fun onReady() = log("tts.event", "ready")
                override fun onDone() = log("tts.event", "done")
                override fun onError(type: ErrorType) = log("tts.event", "error $type")
                override fun onStop() = log("tts.event", "stop")
                override fun onUpdatedRms(rms: Double) = Unit
            }
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    log("tts.init", "ok")
                    runCatching { t.addEventListener(events) }.onFailure { fail("tts.listener", it) }
                }
                override fun onFailure(type: ErrorType) = log("tts.init", "FAILED $type")
            })
        }.onFailure { fail("tts", it) }
    }

    // ---------- UI ----------
    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(text: String, onClick: () -> Unit) = Button(this).apply {
            this.text = text; textSize = 20f; setOnClickListener { onClick() }
        }.also { row.addView(it) }

        button("초기값 다시 읽기") { readInitialValues(); navi?.getDestinationInfo() }
        button("경로 요청") { requestTestRoute() }
        button("조명 Purple 3초") { ambientTest(0xFF800080.toInt(), "Purple") }
        button("조명 Cyan 3초") { ambientTest(0xFF00FFFF.toInt(), "Cyan") }
        button("TTS 말하기") { runCatching { tts?.speak("배송 완료! 상자를 열어볼까요?", true) }.onFailure { fail("tts.speak", it) } }
        button("로그 지우기") { logView.text = "" }
        root.addView(row)

        logView = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f; setPadding(16, 16, 16, 16) }
        root.addView(ScrollView(this).apply { addView(logView) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    companion object { private const val TAG = "SCPOC" }
}

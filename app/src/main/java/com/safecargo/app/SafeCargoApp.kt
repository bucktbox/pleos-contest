package com.safecargo.app

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.safecargo.data.ConfigLoader
import com.safecargo.data.MockCatalog
import com.safecargo.data.MockCatalogLoader
import com.safecargo.data.TripStore
import com.safecargo.engine.JudgeConfig
import com.safecargo.engine.TripStateMachine
import com.safecargo.sdk.AmbientLight
import com.safecargo.sdk.LocationOnce
import com.safecargo.sdk.NaviSource
import com.safecargo.sdk.TtsPlayer
import com.safecargo.sdk.UxGate
import com.safecargo.sdk.VehicleSource
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.MainScope

/** 앱 전체 수명의 조립 지점. 컨트롤러 · SDK 래퍼는 프로세스와 함께 산다. */
class SafeCargoApp : Application() {
    lateinit var controller: TripController
    lateinit var catalog: MockCatalog
    lateinit var config: JudgeConfig

    override fun onCreate() {
        super.onCreate()
        config = ConfigLoader.parse(asset("config/judge.json"))
        catalog = MockCatalogLoader.parse(asset("mock/catalog.json"))
        val machine = TripStateMachine(
            config = config,
            dailyCargo = catalog.daily,
            newId = { UUID.randomUUID().toString() },
            todayEpochDay = { LocalDate.now().toEpochDay() },
        )
        val vehicle = VehicleSource(this, config.steerSign)
        controller = TripController(
            machine = machine,
            store = TripStore(filesDir),
            vehicle = vehicle,
            ux = UxGate(this),
            location = LocationOnce(this),
            light = AmbientLight({ vehicle.vehicle }, Handler(Looper.getMainLooper())),
            tts = TtsPlayer(this),
            navi = NaviSource(this),
            config = config,
            scope = MainScope(),
        )
        controller.start()
    }

    private fun asset(path: String): String = assets.open(path).bufferedReader().use { it.readText() }
}

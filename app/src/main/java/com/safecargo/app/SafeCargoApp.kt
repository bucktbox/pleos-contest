package com.safecargo.app

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.safecargo.data.ConfigLoader
import com.safecargo.data.MockCatalog
import com.safecargo.data.MockCatalogLoader
import com.safecargo.data.RewardConfig
import com.safecargo.data.RewardConfigLoader
import com.safecargo.data.TripStore
import com.safecargo.data.WalletStore
import com.safecargo.engine.JudgeConfig
import com.safecargo.engine.TripStateMachine
import com.safecargo.sdk.AmbientLight
import com.safecargo.sdk.LocationOnce
import com.safecargo.sdk.NaviSource
import com.safecargo.sdk.SpeedSource
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
    lateinit var rewardConfig: RewardConfig

    override fun onCreate() {
        super.onCreate()
        config = ConfigLoader.parse(asset("config/judge.json")) { android.util.Log.w("SAFECARGO", it) }
        rewardConfig = RewardConfigLoader.parse(asset("config/reward.json")) { android.util.Log.w("SAFECARGO", it) }
        catalog = MockCatalogLoader.parse(asset("mock/catalog.json"))
        val machine = TripStateMachine(
            config = config,
            dailyCargo = catalog.daily,
            newId = { UUID.randomUUID().toString() },
            todayEpochDay = { LocalDate.now().toEpochDay() },
            // 개봉 순간 추첨 (v1.1) — 컨트롤러가 시연 모드 · 난수를 쥐고 있다. 호출은 start() 이후뿐.
            drawReward = { g, coupon, daily -> controller.drawTripReward(g, coupon, daily) },
        )
        val vehicle = VehicleSource(this, config.steerSign, config.seatOccupancyInverted)
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
            catalog = catalog,
            rewardConfig = rewardConfig,
            walletStore = WalletStore(filesDir),
            speed = SpeedSource(this),
        )
        controller.start()
    }

    private fun asset(path: String): String = assets.open(path).bufferedReader().use { it.readText() }
}

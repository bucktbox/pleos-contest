package com.safecargo.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.safecargo.engine.TripEvent
import com.safecargo.ui.CargoApp
import com.safecargo.ui.theme.SafeCargoTheme

class MainActivity : ComponentActivity() {
    private val app get() = application as SafeCargoApp

    /** 위치 권한 — 로비의 권한 카드가 보고, onResume · 요청 결과에서 다시 확인한다. */
    private var locationGranted by mutableStateOf(false)

    /** 「다시 묻지 않음」 으로 거부됐으면 다음 [허용] 탭은 앱 설정 화면으로 보낸다. */
    private var openSettingsNext = false

    /** 차속 권한 (v1.1 급가속 · 급정거 모션). 거부하면 흔들림 모션만 남는다 (EX-09). */
    private val speedRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        app.controller.onSpeedPermissionChanged()
    }

    private val locationRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        locationGranted = hasLocation()
        openSettingsNext = !granted && !shouldShowRequestPermissionRationale(FUSED_LOCATION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationGranted = hasLocation()
        setContent {
            SafeCargoTheme {
                CargoApp(
                    controller = app.controller,
                    catalog = app.catalog,
                    config = app.config,
                    onRequestLocation = ::requestLocation,
                    locationGranted = locationGranted,
                )
            }
        }
        if (savedInstanceState == null) {
            handleDebugIntent(intent)
            // 차속은 호위 화면의 모션에만 쓴다 — 첫 실행에 한 번 묻고, 거부하면 다시 묻지 않는다(시스템이 막는다).
            if (ContextCompat.checkSelfPermission(this, SPEED) != PackageManager.PERMISSION_GRANTED) speedRequest.launch(SPEED)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDebugIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        app.controller.appVisible = true
    }

    override fun onResume() {
        super.onResume()
        locationGranted = hasLocation()
    }

    override fun onStop() {
        super.onStop()
        app.controller.appVisible = false
        app.controller.onAppHidden()
    }

    /** 로비 권한 카드에서만 부른다 (앱 시작 때 자동 요청하지 않음). */
    private fun requestLocation() {
        if (hasLocation()) return
        if (openSettingsNext) {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            try { startActivity(i) } catch (e: ActivityNotFoundException) { locationRequest.launch(FUSED_LOCATION) }
        } else {
            locationRequest.launch(FUSED_LOCATION)
        }
    }

    private fun hasLocation() =
        ContextCompat.checkSelfPermission(this, FUSED_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * 디버그 빌드 전용:
     * - `--ez demo true` 시연 모드 (보상함 초기값 · 추첨 고정), `--ez demoReset true` 보상함을 비우고 초기값부터
     * - `--es dest <id>` Mock 목적지를 고른다
     */
    private fun handleDebugIntent(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        if (intent.hasExtra("demo") || intent.hasExtra("demoReset")) {
            app.controller.setDemo(intent.getBooleanExtra("demo", true), intent.getBooleanExtra("demoReset", false))
        }
        val id = intent.getStringExtra("dest") ?: return
        val d = app.catalog.destinations.firstOrNull { it.id == id } ?: return
        app.controller.dispatch(TripEvent.SelectDestination(d))
    }

    private companion object {
        const val FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"
        const val SPEED = com.safecargo.sdk.SpeedSource.PERMISSION
    }
}

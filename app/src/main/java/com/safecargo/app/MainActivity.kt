package com.safecargo.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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

    private val locationRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        locationGranted = hasLocation()
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
        if (savedInstanceState == null) handleDebugIntent(intent)
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
        if (!hasLocation()) locationRequest.launch(FUSED_LOCATION)
    }

    private fun hasLocation() =
        ContextCompat.checkSelfPermission(this, FUSED_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** 디버그 빌드 전용: `--es dest <id>` 로 Mock 목적지를 고른다. */
    private fun handleDebugIntent(intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        val id = intent?.getStringExtra("dest") ?: return
        val d = app.catalog.destinations.firstOrNull { it.id == id } ?: return
        app.controller.dispatch(TripEvent.SelectDestination(d))
    }

    private companion object {
        const val FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"
    }
}

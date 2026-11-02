package com.safecargo.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.core.content.ContextCompat
import com.safecargo.engine.TripEvent

class MainActivity : ComponentActivity() {
    private val app get() = application as SafeCargoApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("세이프 카고") }
        // Task 9 의 권한 카드가 생기면 그쪽으로 옮긴다.
        if (ContextCompat.checkSelfPermission(this, FUSED_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(FUSED_LOCATION), REQ_LOCATION)
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

    override fun onStop() {
        super.onStop()
        app.controller.appVisible = false
        app.controller.onAppHidden()
    }

    /** 디버그 빌드 전용: `--es dest <id>` 로 Mock 목적지를 고른다. */
    private fun handleDebugIntent(intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        val id = intent?.getStringExtra("dest") ?: return
        val d = app.catalog.destinations.firstOrNull { it.id == id } ?: return
        app.controller.dispatch(TripEvent.SelectDestination(d))
    }

    private companion object {
        const val FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"
        const val REQ_LOCATION = 1
    }
}

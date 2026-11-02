package com.safecargo.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.safecargo.sdk.UxGate
import com.safecargo.sdk.VehicleSource
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // TEMP(Task 7): remove when TripController lands
    private lateinit var vehicleSource: VehicleSource
    private lateinit var uxGate: UxGate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("세이프 카고") }

        // TEMP(Task 7): remove when TripController lands
        vehicleSource = VehicleSource(applicationContext, steerSign = -1f)
        uxGate = UxGate(applicationContext)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vehicleSource.signals.collect { Log.i("SAFECARGO", it.toString()) } }
                launch { uxGate.signals.collect { Log.i("SAFECARGO", it.toString()) } }
                launch { vehicleSource.support.collect { Log.i("SAFECARGO", it.toString()) } }
                vehicleSource.start()
                uxGate.start()
                try { awaitCancellation() } finally { vehicleSource.stop(); uxGate.stop() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

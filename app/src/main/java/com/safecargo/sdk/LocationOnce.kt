package com.safecargo.sdk

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.safecargo.engine.GeoFix
import java.util.concurrent.atomic.AtomicBoolean

/** 도착 확인용 위치 1회 조회. 좌표는 로그에 남기지 않는다. 콜백은 항상 메인 스레드. */
class LocationOnce(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var fused: FusedLocation? = null

    /** null = 확인 전, true/false = isSupported() 결과. */
    @Volatile var supported: Boolean? = null
        private set

    fun start() {
        if (fused != null) return
        runCatching {
            val l = FusedLocation(context)
            l.initialize()
            fused = l
            supported = runCatching { l.isSupported() }.getOrNull()
        }.onFailure { Log.w(TAG, "location.init FAILED ${it.javaClass.simpleName}") }
    }

    fun request(onResult: (GeoFix?) -> Unit) {
        val done = AtomicBoolean(false)
        fun finish(fix: GeoFix?) {
            if (!done.compareAndSet(false, true)) return
            main.removeCallbacksAndMessages(done)
            main.post { runCatching { onResult(fix) }.onFailure { Log.w(TAG, "location.callback FAILED ${it.javaClass.simpleName}") } }
        }
        val l = fused
        if (l == null) return finish(null)
        if (ContextCompat.checkSelfPermission(context, PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "location.once no-permission")
            return finish(null)
        }
        val sup = runCatching { l.isSupported() }.getOrDefault(false)
        supported = sup
        if (!sup) return finish(null)
        main.postAtTime({ Log.w(TAG, "location.once timeout"); finish(null) }, done, android.os.SystemClock.uptimeMillis() + TIMEOUT_MS)
        runCatching {
            l.getLastKnownLocation(object : FusedLocationListener {
                override fun onChanged(data: LocationData?) {
                    if (data == null) return finish(null)
                    val age = System.currentTimeMillis() - data.timestampMillis
                    Log.i(TAG, "location fix age=${age}ms")
                    finish(GeoFix(data.latitude, data.longitude, age))
                }
                override fun onError(e: Exception) {
                    Log.w(TAG, "location.once FAILED ${e.javaClass.simpleName}")
                    finish(null)
                }
            })
        }.onFailure {
            Log.w(TAG, "location.once FAILED ${it.javaClass.simpleName}")
            finish(null)
        }
    }

    private companion object {
        const val TAG = "SAFECARGO"
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
        const val TIMEOUT_MS = 8_000L
    }
}

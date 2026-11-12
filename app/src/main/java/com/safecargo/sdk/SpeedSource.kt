package com.safecargo.sdk

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** 차속 한 샘플 (m/s). 메모리에서 거친 운전 감지에만 쓰고 저장 · 로그하지 않는다 (FR-DATA-004). */
data class SpeedSample(val mps: Float, val atMs: Long)

/**
 * 차속 (IF-11). Pleos Vehicle SDK 2.3.2 에는 차속 API 가 없어 AAOS 표준 `PERF_VEHICLE_SPEED` 를 쓴다 (11/02 POC · SRC-06).
 * 런타임 권한 `android.car.permission.CAR_SPEED` 가 필요하다. 권한 거부 · 미지원이면 [available] = false →
 * 급가속 · 급정거 모션만 끄고 흔들림은 남긴다 (EX-09 · AG-06).
 */
class SpeedSource(
    private val context: Context,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private val _samples = MutableSharedFlow<SpeedSample>(extraBufferCapacity = 64)
    val samples: SharedFlow<SpeedSample> = _samples.asSharedFlow()

    /** null = 확인 중, true = 받는 중, false = 권한 없음 · 미지원 · 오류. */
    private val _available = MutableStateFlow<Boolean?>(null)
    val available: StateFlow<Boolean?> = _available.asStateFlow()

    private var car: Car? = null
    private var manager: CarPropertyManager? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(v: CarPropertyValue<*>) {
            val mps = (v.value as? Float) ?: return
            _available.value = true
            _samples.tryEmit(SpeedSample(mps, now()))
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.w(TAG, "speed error")
            _available.value = false
        }
    }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** 권한이 있을 때만 등록한다. 권한을 나중에 받으면 다시 부른다. */
    fun start() {
        if (manager != null) return
        if (!hasPermission()) {
            _available.value = false
            return
        }
        runCatching {
            val c = Car.createCar(context)
            car = c
            val pm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            val ok = pm.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            if (ok) {
                manager = pm
                if (_available.value != true) _available.value = null
            } else {
                _available.value = false
                runCatching { c.disconnect() }
                car = null
            }
        }.onFailure {
            Log.w(TAG, "speed FAILED ${it.javaClass.simpleName}")
            _available.value = false
            runCatching { car?.disconnect() }
            car = null
        }
    }

    fun stop() {
        runCatching { manager?.unregisterCallback(callback) }
        runCatching { car?.disconnect() }
        manager = null
        car = null
    }

    companion object {
        const val PERMISSION = "android.car.permission.CAR_SPEED"
        private const val TAG = "SAFECARGO"
    }
}

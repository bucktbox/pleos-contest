package com.safecargo.sdk

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.safecargo.engine.VehicleSignal
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** CarUxRestrictions → UxChanged. 연결 실패 시 잠금 유지를 위해 restricted=true 를 한 번 낸다. */
class UxGate(
    private val context: Context,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private val _signals = MutableSharedFlow<VehicleSignal.UxChanged>(extraBufferCapacity = 64)
    val signals: SharedFlow<VehicleSignal.UxChanged> = _signals.asSharedFlow()

    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private val listener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { emit(it) }

    private fun emit(r: CarUxRestrictions) {
        _signals.tryEmit(VehicleSignal.UxChanged(r.isRequiresDistractionOptimization, now()))
    }

    fun start() {
        if (car != null) return
        runCatching {
            val c = Car.createCar(context)
            car = c
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            manager = m
            emit(m.currentCarUxRestrictions)
            m.registerListener(listener)
        }.onFailure {
            Log.w("SAFECARGO", "uxGate FAILED ${it.javaClass.simpleName}")
            _signals.tryEmit(VehicleSignal.UxChanged(true, now()))
        }
    }

    fun stop() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        manager = null
        car = null
    }
}

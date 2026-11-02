package com.safecargo.sdk

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import com.safecargo.engine.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 내비 연동. 목적지 이름만 노출하고 좌표 · 주소는 로그에 남기지 않는다. */
class NaviSource(private val context: Context) {
    private val _destinationName = MutableStateFlow<String?>(null)
    val destinationName: StateFlow<String?> = _destinationName.asStateFlow()

    @Volatile private var navi: NaviHelper? = null
    @Volatile private var ready = false

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) = Unit
        override fun onRouteEnded() = Unit
        override fun onRouteCancelled() { _destinationName.value = null }
        override fun onDestinationChanged() { runCatching { navi?.getDestinationInfo() }.onFailure { fail("navi.destInfo", it) } }
        override fun onDestinationInfo(info: DestinationInfo) { _destinationName.value = info.name?.takeIf { it.isNotBlank() } }
        override fun onDestinationArrived(info: DestinationArrivedInfo) = Unit
        override fun onError(code: NaviErrorCode) { Log.w(TAG, "navi.error $code") }
    }

    fun start() {
        if (navi != null) return
        runCatching {
            val n = NaviHelper(context)
            navi = n
            n.initialize(
                {
                    ready = true
                    runCatching {
                        n.addListener(listener)
                        n.getDestinationInfo()
                    }.onFailure { fail("navi.listener", it) }
                    Log.i(TAG, "navi ok")
                },
                { fail("navi.init", it); navi = null },
            )
        }.onFailure { fail("navi.start", it); navi = null }
    }

    fun requestRoute(d: Destination) {
        val n = navi
        if (n == null || !ready) { Log.w(TAG, "navi.requestRoute skipped (not ready)"); return }
        runCatching {
            n.requestRoute(RouteInfo(d.lat, d.lng, null, null, d.name, "", d.address, "", RouteOption.RECOMMENDED, "", false))
            Log.i(TAG, "navi.requestRoute ${d.name}")
        }.onFailure { fail("navi.requestRoute", it) }
    }

    private fun fail(src: String, e: Throwable) = Log.w(TAG, "$src FAILED ${e.javaClass.simpleName}")

    private companion object { const val TAG = "SAFECARGO" }
}

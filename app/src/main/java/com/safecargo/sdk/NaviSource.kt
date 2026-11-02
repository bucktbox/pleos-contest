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

    /** 마지막 경로 요청이 실패했는지. 다음 요청이 성공적으로 발행되면 false 로 돌아간다. */
    private val _routeFailed = MutableStateFlow(false)
    val routeFailed: StateFlow<Boolean> = _routeFailed.asStateFlow()

    @Volatile private var navi: NaviHelper? = null
    @Volatile private var ready = false
    /** 초기화 중에 들어온 요청. 준비되면 발행한다. */
    @Volatile private var pending: Destination? = null
    /** 발행 후 onRouteStarted 전 — 이 사이의 onError 는 경로 요청 실패로 본다. */
    @Volatile private var awaitingStart = false

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) { awaitingStart = false }
        override fun onRouteEnded() = Unit
        override fun onRouteCancelled() { _destinationName.value = null }
        override fun onDestinationChanged() { runCatching { navi?.getDestinationInfo() }.onFailure { fail("navi.destInfo", it) } }
        override fun onDestinationInfo(info: DestinationInfo) { _destinationName.value = info.name?.takeIf { it.isNotBlank() } }
        override fun onDestinationArrived(info: DestinationArrivedInfo) = Unit
        override fun onError(code: NaviErrorCode) {
            Log.w(TAG, "navi.error $code")
            if (awaitingStart) { awaitingStart = false; _routeFailed.value = true }
        }
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
                    pending?.let { pending = null; send(n, it) }
                },
                { fail("navi.init", it); navi = null; failPending() },
            )
        }.onFailure { fail("navi.start", it); navi = null; failPending() }
    }

    fun requestRoute(d: Destination) {
        val n = navi
        when {
            n == null -> { Log.w(TAG, "navi.requestRoute skipped (no navi)"); _routeFailed.value = true }
            !ready -> { Log.i(TAG, "navi.requestRoute deferred (initializing)"); pending = d }
            else -> send(n, d)
        }
    }

    private fun send(n: NaviHelper, d: Destination) {
        runCatching {
            awaitingStart = true
            n.requestRoute(RouteInfo(d.lat, d.lng, null, null, d.name, "", d.address, "", RouteOption.RECOMMENDED, "", false))
            _routeFailed.value = false
            Log.i(TAG, "navi.requestRoute ${d.name}")
        }.onFailure { awaitingStart = false; _routeFailed.value = true; fail("navi.requestRoute", it) }
    }

    private fun failPending() {
        if (pending != null) { pending = null; _routeFailed.value = true }
    }

    private fun fail(src: String, e: Throwable) = Log.w(TAG, "$src FAILED ${e.javaClass.simpleName}")

    private companion object { const val TAG = "SAFECARGO" }
}

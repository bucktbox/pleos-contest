package com.safecargo.sdk

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 무드등 색을 잠깐 바꿨다 원래 색으로 되돌린다. 모든 상태는 메인 스레드에서만 만진다.
 * - 지원하는 모든 영역을 백업(get)하고, 백업이 있는 영역만 되돌린다.
 * - flash 중 다시 flash 하면 백업은 유지(덮어쓰지 않음)하고 색과 원복 시각만 갱신한다.
 */
class AmbientLight(private val vehicle: () -> Vehicle?, private val main: Handler) {
    private val backups = LinkedHashMap<SeatArea, Int>()
    private var restoreTask: Runnable? = null
    private var gen = 0

    fun flash(rgb: Int, durationMs: Long, onResult: (applied: Boolean) -> Unit) = onMain {
        val token = ++gen
        fun deliver(ok: Boolean) { runCatching { onResult(ok) }.onFailure { fail("ambient.callback", it) } }
        val light = runCatching { vehicle()?.getLight() }.getOrNull()
        if (light == null) return@onMain deliver(false)
        val color = 0xFF000000.toInt() or (rgb and 0xFFFFFF)
        runCatching {
            light.checkAmbientLightBrightnessColorCapabilities(
                { _, areas ->
                    main.post {
                        if (token != gen) return@post deliver(false)
                        if (areas.isEmpty()) return@post deliver(false)
                        var pending = areas.size
                        var applied = 0
                        fun settle(ok: Boolean) {
                            if (ok) applied++
                            if (--pending > 0) return
                            if (token != gen) return deliver(false)
                            if (applied > 0) scheduleRestore(durationMs)
                            deliver(applied > 0)
                        }
                        fun set(area: SeatArea) {
                            runCatching {
                                light.setAmbientLightBrightnessColor(area, color,
                                    { _, _ -> main.post { settle(true) } },
                                    { e -> fail("ambient.set", e); main.post { settle(false) } })
                            }.onFailure { fail("ambient.set", it); settle(false) }
                        }
                        areas.forEach { area ->
                            if (backups.containsKey(area)) { set(area); return@forEach }
                            runCatching {
                                light.getAmbientLightBrightnessColor(area,
                                    { original ->
                                        main.post {
                                            if (token != gen) return@post settle(false)
                                            if (original == null) return@post settle(false) // 원복 불가 영역은 건드리지 않는다
                                            if (!backups.containsKey(area)) backups[area] = original
                                            set(area)
                                        }
                                    },
                                    { e -> fail("ambient.backup", e); main.post { settle(false) } })
                            }.onFailure { fail("ambient.backup", it); settle(false) }
                        }
                    }
                },
                { e -> fail("ambient.capability", e); main.post { if (token == gen) deliver(false) } },
            )
        }.onFailure { fail("ambient.capability", it); deliver(false) }
    }

    /** 멱등. 대기 중인 지연 원복을 취소하고, 백업이 있는 영역만 되돌린 뒤 백업을 비운다. */
    fun restore() = onMain {
        gen++
        restoreTask?.let { main.removeCallbacks(it) }
        restoreTask = null
        if (backups.isEmpty()) return@onMain
        val snapshot = backups.toMap()
        backups.clear()
        val light = runCatching { vehicle()?.getLight() }.getOrNull() ?: return@onMain
        snapshot.forEach { (area, original) ->
            runCatching {
                light.setAmbientLightBrightnessColor(area, original, { _, _ -> }, { e -> fail("ambient.restore", e) })
            }.onFailure { fail("ambient.restore", it) }
        }
    }

    private fun scheduleRestore(durationMs: Long) {
        restoreTask?.let { main.removeCallbacks(it) }
        val r = Runnable { restoreTask = null; restore() }
        restoreTask = r
        main.postDelayed(r, durationMs)
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == main.looper) block() else main.post(block)
    }

    private fun fail(src: String, e: Throwable) = Log.w(TAG, "$src FAILED ${e.javaClass.simpleName}")

    private companion object { const val TAG = "SAFECARGO" }
}

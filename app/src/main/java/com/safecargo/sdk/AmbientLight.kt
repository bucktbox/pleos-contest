package com.safecargo.sdk

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 무드등 색을 잠깐 바꿨다 원래 색으로 되돌린다. 모든 상태는 메인 스레드에서만 만진다.
 *
 * 설계: [originals] = 영역별 "알려진 원래 색". get 은 그 영역에 originals 가 없을 때만 한다 —
 * 그래서 원복 set 이 아직 끝나기 전에 다시 flash 해도 flash 색을 백업으로 오인하지 않는다.
 * originals 는 원복 set 이 성공한 뒤에만(그 사이 다시 켜지지 않았을 때) 지운다. [dirty] = flash 색을
 * set 한 뒤 아직 원복을 발행하지 않은 영역.
 * 모든 flash() 종료 경로에서 originals 가 있고 예약이 없으면 원복을 보장한다
 * (성공 = 지연 예약, 실패/타임아웃 = 즉시 원복).
 */
class AmbientLight(private val vehicle: () -> Vehicle?, private val main: Handler) {
    private val originals = LinkedHashMap<SeatArea, Int>()
    private val dirty = HashSet<SeatArea>()
    private var restoreTask: Runnable? = null
    private var gen = 0

    fun flash(rgb: Int, durationMs: Long, onResult: (applied: Boolean) -> Unit) = onMain {
        val token = ++gen
        var done = false
        var timeout: Runnable? = null
        fun complete(ok: Boolean) {
            if (done) return
            done = true
            timeout?.let { main.removeCallbacks(it) }
            if (token == gen) {
                if (ok) scheduleRestore(durationMs)
                else if (originals.isNotEmpty()) restore() // 실패 경로: 켜져 있을 수 있는 색을 즉시 원복
            }
            runCatching { onResult(ok && token == gen) }.onFailure { fail("ambient.callback", it) }
        }
        timeout = Runnable { fail("ambient.timeout", IllegalStateException()); complete(false) }
        main.postDelayed(timeout, TIMEOUT_MS)

        val light = runCatching { vehicle()?.getLight() }.getOrNull()
        if (light == null) return@onMain complete(false)
        val color = 0xFF000000.toInt() or (rgb and 0xFFFFFF)
        runCatching {
            light.checkAmbientLightBrightnessColorCapabilities(
                { _, areas ->
                    main.post {
                        if (done) return@post
                        if (token != gen || areas.isEmpty()) return@post complete(false)
                        var pending = areas.size
                        var applied = 0
                        fun settle(ok: Boolean) {
                            if (done) return
                            if (ok) applied++
                            if (--pending == 0) complete(applied > 0)
                        }
                        fun set(area: SeatArea) {
                            if (token != gen) return settle(false)
                            dirty.add(area)
                            runCatching {
                                light.setAmbientLightBrightnessColor(area, color,
                                    { _, _ -> main.post { settle(true) } },
                                    { e -> fail("ambient.set", e); main.post { settle(false) } })
                            }.onFailure { fail("ambient.set", it); settle(false) }
                        }
                        areas.forEach { area ->
                            if (originals.containsKey(area)) { set(area); return@forEach }
                            runCatching {
                                light.getAmbientLightBrightnessColor(area,
                                    { original ->
                                        main.post {
                                            if (done || token != gen) return@post settle(false)
                                            if (original == null) return@post settle(false) // 원복 불가 영역은 건드리지 않는다
                                            if (!originals.containsKey(area)) originals[area] = original
                                            set(area)
                                        }
                                    },
                                    { e -> fail("ambient.backup", e); main.post { settle(false) } })
                            }.onFailure { fail("ambient.backup", it); settle(false) }
                        }
                    }
                },
                { e -> fail("ambient.capability", e); main.post { complete(false) } },
            )
        }.onFailure { fail("ambient.capability", it); complete(false) }
    }

    /** 멱등. 대기 중인 지연 원복을 취소하고, 알려진 원래 색이 있는 영역만 되돌린다. */
    fun restore() = onMain {
        gen++
        restoreTask?.let { main.removeCallbacks(it) }
        restoreTask = null
        if (originals.isEmpty()) return@onMain
        val light = runCatching { vehicle()?.getLight() }.getOrNull() ?: return@onMain
        originals.toMap().forEach { (area, original) ->
            dirty.remove(area)
            runCatching {
                light.setAmbientLightBrightnessColor(area, original,
                    { _, _ -> main.post { if (!dirty.contains(area)) originals.remove(area) } },
                    { e -> fail("ambient.restore", e) }) // 실패 시 originals 유지 → 다음 restore 가 재시도
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

    private companion object { const val TAG = "SAFECARGO"; const val TIMEOUT_MS = 3_000L }
}

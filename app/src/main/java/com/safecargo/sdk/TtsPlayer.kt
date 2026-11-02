package com.safecargo.sdk

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log

/** 온디바이스 TTS. 어떤 실패도 밖으로 던지지 않는다 (EX-07). */
class TtsPlayer(private val context: Context) {
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private var events: EventListener? = null

    fun start() {
        if (tts != null) return
        runCatching {
            val t = TextToSpeech(context, Mode.ON_DEVICE)
            tts = t
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    // SDK 내부 lateinit 때문에 initialize 성공 뒤에만 리스너를 붙인다
                    val l = object : EventListener {
                        override fun onStart() = Unit
                        override fun onReady() = Unit
                        override fun onDone() { Log.i(TAG, "tts done") }
                        override fun onError(type: ErrorType) { Log.w(TAG, "tts error $type") }
                        override fun onStop() = Unit
                        override fun onUpdatedRms(rms: Double) = Unit
                    }
                    runCatching { t.addEventListener(l); events = l }.onFailure { fail("tts.listener", it) }
                    ready = true
                    Log.i(TAG, "tts ok")
                }
                override fun onFailure(type: ErrorType) { Log.w(TAG, "tts init FAILED $type") }
            })
        }.onFailure { fail("tts.start", it); tts = null }
    }

    fun speak(text: String) {
        if (!ready) return
        runCatching { tts?.speak(text, true) }.onFailure { fail("tts.speak", it) }
    }

    fun stop() {
        if (!ready) return
        runCatching { tts?.stop() }.onFailure { fail("tts.stop", it) }
    }

    fun release() {
        val t = tts ?: return
        ready = false
        tts = null
        runCatching { events?.let { t.removeEventListener(it) } }.onFailure { fail("tts.unlisten", it) }
        events = null
        runCatching { t.release() }.onFailure { fail("tts.release", it) }
    }

    private fun fail(src: String, e: Throwable) = Log.w(TAG, "$src FAILED ${e.javaClass.simpleName}")

    private companion object { const val TAG = "SAFECARGO" }
}

package kr.gildogam.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

/**
 * DJ voice through the Pleos TextToSpeech SDK (on-device mode: no app registration or network needed).
 * SDK callbacks are moved to the main thread.
 */
class PleosSpeaker(context: Context) : Speaker {

    override val label = "Pleos TTS"

    private val main = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private var ready = false
    private var pendingDone: (() -> Unit)? = null

    private fun finishCurrent() {
        val done = pendingDone ?: return
        pendingDone = null
        done()
    }

    private val events = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() {
            main.post { finishCurrent() }
        }
        override fun onStop() {
            main.post { finishCurrent() }
        }
        override fun onError(errorType: ErrorType) {
            Log.w(TAG, "tts error $errorType")
            main.post { finishCurrent() }
        }
    }

    override fun init(onResult: (Boolean) -> Unit) {
        try {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        runCatching { tts.setLocale(Locale.KOREA) }
                        tts.addEventListener(events)
                        ready = true
                        onResult(true)
                    }
                }

                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "tts init failed $errorType")
                    main.post { onResult(false) }
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "tts unavailable", t)
            main.post { onResult(false) }
        }
    }

    override fun speak(text: String, onDone: () -> Unit) {
        if (!ready) {
            main.post(onDone)
            return
        }
        finishCurrent()
        pendingDone = onDone
        try {
            tts.speak(text)
        } catch (t: Throwable) {
            Log.w(TAG, "speak failed", t)
            main.post { finishCurrent() }
        }
    }

    override fun stop() {
        if (ready) runCatching { tts.stop() }
    }

    override fun release() {
        runCatching {
            if (ready) tts.removeEventListener(events)
            tts.release()
        }
        ready = false
    }

    private companion object {
        const val TAG = "PleosSpeaker"
    }
}

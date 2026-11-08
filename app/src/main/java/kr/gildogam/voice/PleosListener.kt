package kr.gildogam.voice

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Passenger voice input through the Pleos SpeechToText SDK.
 * If a recognition ends without a final result, the last partial text is used.
 */
class PleosListener(context: Context) : Listener {

    override val label = "Pleos STT"

    private val main = Handler(Looper.getMainLooper())
    private val stt = PleosStt(context)
    private var active: ListenCallback? = null
    private var lastPartial = ""

    private val timeout = Runnable {
        val cb = active ?: return@Runnable
        active = null
        stt.stop()
        if (lastPartial.isNotBlank()) cb.onFinal(lastPartial) else cb.onError("timeout")
    }

    override fun init(onResult: (Boolean) -> Unit) {
        try {
            stt.initialize(object : PleosStt.Events {
                override fun onInit(ok: Boolean) = onResult(ok)

                override fun onPartial(text: String) {
                    lastPartial = text
                    active?.onPartial(text)
                }

                override fun onFinal(text: String) {
                    val cb = active ?: return
                    active = null
                    main.removeCallbacks(timeout)
                    cb.onFinal(text)
                }

                override fun onError(reason: String) {
                    val cb = active ?: return
                    active = null
                    main.removeCallbacks(timeout)
                    if (lastPartial.isNotBlank()) cb.onFinal(lastPartial) else cb.onError(reason)
                }
            })
        } catch (t: Throwable) {
            main.post { onResult(false) }
        }
    }

    override fun listen(callback: ListenCallback) {
        active = callback
        lastPartial = ""
        main.removeCallbacks(timeout)
        if (!stt.request()) {
            active = null
            callback.onError("not ready")
            return
        }
        main.postDelayed(timeout, LISTEN_LIMIT_MS)
    }

    override fun stop() {
        active = null
        main.removeCallbacks(timeout)
        stt.stop()
    }

    override fun release() {
        stop()
        stt.release()
    }

    private companion object {
        const val LISTEN_LIMIT_MS = 15_000L
    }
}

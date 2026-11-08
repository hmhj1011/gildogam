package kr.gildogam.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/** Fallback DJ voice (platform TTS) for devices without the Pleos TTS service. */
class AndroidSpeaker(context: Context) : Speaker {

    override val label = "Android TTS"

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private val pending = mutableMapOf<String, () -> Unit>()

    override fun init(onResult: (Boolean) -> Unit) {
        try {
            tts = TextToSpeech(app) { status ->
                main.post {
                    val engine = tts
                    if (status != TextToSpeech.SUCCESS || engine == null) {
                        onResult(false)
                        return@post
                    }
                    engine.language = Locale.KOREA
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) = finish(utteranceId)

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) = finish(utteranceId)
                        override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
                        override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)
                    })
                    onResult(true)
                }
            }
        } catch (t: Throwable) {
            main.post { onResult(false) }
        }
    }

    private fun finish(id: String?) {
        main.post { id?.let { pending.remove(it) }?.invoke() }
    }

    override fun speak(text: String, onDone: () -> Unit) {
        val engine = tts
        if (engine == null) {
            main.post(onDone)
            return
        }
        val id = UUID.randomUUID().toString()
        pending[id] = onDone
        if (engine.speak(text, TextToSpeech.QUEUE_ADD, null, id) != TextToSpeech.SUCCESS) finish(id)
    }

    override fun stop() {
        tts?.stop()
    }

    override fun release() {
        tts?.shutdown()
        tts = null
        pending.clear()
    }
}

/** Fallback passenger voice input (platform SpeechRecognizer). */
class AndroidListener(context: Context) : Listener {

    override val label = "Android STT"

    private val app = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var active: ListenCallback? = null

    override fun init(onResult: (Boolean) -> Unit) {
        val ok = try {
            SpeechRecognizer.isRecognitionAvailable(app).also {
                if (it) recognizer = SpeechRecognizer.createSpeechRecognizer(app)
            }
        } catch (t: Throwable) {
            false
        }
        onResult(ok && recognizer != null)
    }

    override fun listen(callback: ListenCallback) {
        val r = recognizer ?: return callback.onError("unavailable")
        active = callback
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                val cb = active ?: return
                active = null
                cb.onError("error $error")
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) active?.onPartial(text)
            }

            override fun onResults(results: Bundle?) {
                val cb = active ?: return
                active = null
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) cb.onError("empty") else cb.onFinal(text)
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        try {
            r.startListening(intent)
        } catch (t: Throwable) {
            active = null
            callback.onError("start failed")
        }
    }

    override fun stop() {
        active = null
        runCatching { recognizer?.cancel() }
    }

    override fun release() {
        stop()
        runCatching { recognizer?.destroy() }
        recognizer = null
    }
}

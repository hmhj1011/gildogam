package kr.gildogam.dex

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.gildogam.car.CarPropertySignals
import kr.gildogam.car.PleosLocation
import kr.gildogam.car.PleosNavi
import kr.gildogam.car.PleosWiper
import kr.gildogam.core.Acquisition
import kr.gildogam.core.CarEvent
import kr.gildogam.core.DemoPlayer
import kr.gildogam.core.DemoTrip
import kr.gildogam.core.Dex
import kr.gildogam.core.Effect
import kr.gildogam.core.EngineConfig
import kr.gildogam.core.EngineState
import kr.gildogam.core.GildogamEngine
import kr.gildogam.core.TextParsing
import kr.gildogam.voice.AndroidListener
import kr.gildogam.voice.AndroidSpeaker
import kr.gildogam.voice.ListenCallback
import kr.gildogam.voice.Listener
import kr.gildogam.voice.PleosListener
import kr.gildogam.voice.PleosSpeaker
import kr.gildogam.voice.Speaker
import java.util.Calendar

/** Which SDK feeds what: shown at the bottom of the screen so the live SDK status is visible. */
data class Sources(
    val tts: String = "연결 중",
    val stt: String = "연결 중",
    val vehicle: String = "연결 중",
    val wiper: String = "연결 중",
    val navi: String = "연결 중",
    val location: String = "연결 중",
)

/** What the microphone is open for. */
sealed interface Ask {
    data object Riders : Ask
    data class Note(val acquisitionId: String, val cardName: String) : Ask
}

/** Demo clock: shift the time of day so night and dawn editions can be shown at any hour. */
enum class DemoClock(val label: String, val hour: Int?) {
    NOW("지금", null),
    NIGHT("밤 10시", 22),
    DAWN("새벽 6시", 6),
}

data class UiState(
    val car: EngineState = EngineState(),
    val demoMode: Boolean = false,
    val clock: DemoClock = DemoClock.NOW,
    val playing: String? = null,
    val asking: Ask? = null,
    val heard: String = "",
    val justAcquired: Acquisition? = null,
    val sources: Sources = Sources(),
    val notice: String? = null,
)

/**
 * Runs the journey book: feeds car, navigation and voice events into [GildogamEngine],
 * speaks its lines, saves the dex, and plays scripted demo drives.
 * Everything happens on the main thread.
 */
class Controller(context: Context) {

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val store = DexStore(app)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var demoMode = false
    private var clock = DemoClock.NOW
    private var clockOffsetMs = 0L
    private var engine = GildogamEngine(EngineConfig(), dex = store.load())

    private var speaker: Speaker? = null
    private var listener: Listener? = null
    private var listenToken = 0
    private var sources = Sources()

    private val carProps = CarPropertySignals(app, ::dispatch)
    private val wiper = PleosWiper(app) { on -> dispatch(CarEvent.Wiper(on)) }
    private val navi = PleosNavi(app, ::dispatch)
    private val location = PleosLocation(app, ::dispatch)

    private var started = false
    private val demoRunnables = mutableListOf<Runnable>()
    private val clearAcquired = Runnable { _ui.value = _ui.value.copy(justAcquired = null) }

    private val ticker = object : Runnable {
        override fun run() {
            dispatch(CarEvent.Tick)
            refreshSources()
            main.postDelayed(this, 1_000)
        }
    }

    private fun now(): Long = System.currentTimeMillis() + clockOffsetMs

    // ------------------------------------------------------------------ lifecycle

    fun start() {
        if (started) return
        started = true
        initSpeaker()
        initListener()
        carProps.start()
        wiper.start()
        navi.start()
        location.start()
        publish()
        main.post(ticker)
    }

    fun onPermissionsChanged() {
        if (!started) return
        carProps.restart()
        location.stop()
        location.start()
    }

    private fun initSpeaker() {
        val pleos = runCatching { PleosSpeaker(app) }.getOrNull()
        if (pleos == null) {
            initAndroidSpeaker()
            return
        }
        pleos.init { ok ->
            if (ok) {
                speaker = pleos
                updateSources { it.copy(tts = pleos.label) }
            } else {
                pleos.release()
                initAndroidSpeaker()
            }
        }
    }

    private fun initAndroidSpeaker() {
        val fallback = AndroidSpeaker(app)
        fallback.init { ok2 ->
            speaker = if (ok2) fallback else null
            updateSources { it.copy(tts = if (ok2) fallback.label + " (대체)" else "음성 없음, 자막만") }
        }
    }

    private fun initListener() {
        val pleos = runCatching { PleosListener(app) }.getOrNull()
        if (pleos == null) {
            initAndroidListener()
            return
        }
        pleos.init { ok ->
            if (ok) {
                listener = pleos
                updateSources { it.copy(stt = pleos.label) }
            } else {
                pleos.release()
                initAndroidListener()
            }
        }
    }

    private fun initAndroidListener() {
        val fallback = AndroidListener(app)
        fallback.init { ok2 ->
            listener = if (ok2) fallback else null
            updateSources { it.copy(stt = if (ok2) fallback.label + " (대체)" else "음성 인식 없음, 입력창 사용") }
        }
    }

    private fun updateSources(change: (Sources) -> Sources) {
        sources = change(sources)
        publish()
    }

    private fun refreshSources() {
        val next = sources.copy(vehicle = carProps.status, wiper = wiper.status(), navi = navi.status, location = location.status)
        if (next != sources) updateSources { next }
    }

    // ------------------------------------------------------------------ events

    fun dispatch(event: CarEvent) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { dispatch(event) }
            return
        }
        val effects = try {
            engine.handle(now(), event)
        } catch (t: Throwable) {
            Log.e(TAG, "engine failed on $event", t)
            emptyList()
        }
        effects.forEach(::apply)
        publish()
    }

    private fun apply(effect: Effect) {
        when (effect) {
            is Effect.Say -> speak(effect.text)
            is Effect.Save -> store.save(effect.dex)
            is Effect.Acquired -> {
                main.removeCallbacks(clearAcquired)
                _ui.value = _ui.value.copy(justAcquired = effect.acquisition)
                main.postDelayed(clearAcquired, 6_000)
            }
        }
    }

    private fun speak(text: String) {
        val s = speaker
        if (s == null) {
            main.postDelayed({ dispatch(CarEvent.SpeechFinished) }, (text.length * 110L).coerceIn(1_500L, 10_000L))
        } else {
            s.speak(text) { dispatch(CarEvent.SpeechFinished) }
        }
    }

    // ------------------------------------------------------------------ voice input

    fun askRiders() = ask(Ask.Riders)

    fun askNote(acquisition: Acquisition) = ask(Ask.Note(acquisition.id, acquisition.cardName))

    private fun ask(what: Ask) {
        val token = ++listenToken
        _ui.value = _ui.value.copy(asking = what, heard = "")
        val l = listener ?: return // no recognizer: the typed box answers instead
        l.listen(object : ListenCallback {
            override fun onPartial(text: String) {
                if (token == listenToken) _ui.value = _ui.value.copy(heard = text)
            }

            override fun onFinal(text: String) {
                if (token == listenToken) answer(text)
            }

            override fun onError(reason: String) {
                if (token != listenToken) return
                _ui.value = _ui.value.copy(asking = null, heard = "", notice = "잘 듣지 못했어요. 다시 눌러 말씀해 주세요.")
            }
        })
    }

    /** Spoken or typed answer to the open question. */
    fun answer(text: String) {
        val what = _ui.value.asking ?: return
        listenToken++
        listener?.stop()
        _ui.value = _ui.value.copy(asking = null, heard = "")
        when (what) {
            is Ask.Riders -> {
                val names = TextParsing.parseNames(text)
                if (names.isEmpty()) _ui.value = _ui.value.copy(notice = "이름을 알아듣지 못했어요. 예) 엄마 아빠 지우")
                else dispatch(CarEvent.Riders(names))
            }
            is Ask.Note -> dispatch(CarEvent.Note(what.acquisitionId, text))
        }
    }

    fun cancelAsk() {
        listenToken++
        listener?.stop()
        _ui.value = _ui.value.copy(asking = null, heard = "")
    }

    // ------------------------------------------------------------------ demo

    fun setDemoMode(enabled: Boolean) {
        if (enabled == demoMode) return
        if (engine.state.journey != null) {
            _ui.value = _ui.value.copy(notice = "여정 기록 중에는 시연 모드를 바꿀 수 없어요")
            return
        }
        demoMode = enabled
        val dex = engine.state.dex
        engine = GildogamEngine(if (enabled) EngineConfig.demo() else EngineConfig(), dex = dex)
        publish()
    }

    fun setClock(c: DemoClock) {
        clock = c
        clockOffsetMs = c.hour?.let { hour ->
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            cal.timeInMillis - System.currentTimeMillis()
        } ?: 0L
        publish()
    }

    /** Plays a scripted drive: the same events the car adapters send, on a timer. */
    fun play(trip: DemoTrip) {
        stopDemo()
        if (!demoMode) setDemoMode(true)
        _ui.value = _ui.value.copy(playing = trip.title)
        val events = DemoPlayer.events(trip)
        for ((t, e) in events) {
            val r = Runnable { dispatch(e) }
            demoRunnables += r
            main.postDelayed(r, t)
        }
        val done = Runnable { _ui.value = _ui.value.copy(playing = null) }
        demoRunnables += done
        main.postDelayed(done, (events.lastOrNull()?.first ?: 0L) + 500)
    }

    fun stopDemo() {
        demoRunnables.forEach(main::removeCallbacks)
        demoRunnables.clear()
        _ui.value = _ui.value.copy(playing = null)
    }

    /** Clears the journey book (demo reset). */
    fun resetDex() {
        if (engine.state.journey != null) {
            _ui.value = _ui.value.copy(notice = "여정 기록 중에는 도감을 비울 수 없어요")
            return
        }
        store.clear()
        engine = GildogamEngine(if (demoMode) EngineConfig.demo() else EngineConfig(), dex = Dex())
        publish()
    }

    fun dismissNotice() {
        _ui.value = _ui.value.copy(notice = null)
    }

    private fun publish() {
        _ui.value = _ui.value.copy(car = engine.state, demoMode = demoMode, clock = clock, sources = sources)
    }

    fun release() {
        stopDemo()
        main.removeCallbacks(ticker)
        speaker?.release()
        listener?.release()
        carProps.stop()
        wiper.stop()
        navi.stop()
        location.stop()
        started = false
    }

    private companion object {
        const val TAG = "Controller"
    }
}

package kr.gildogam.voice

/** Speaks DJ lines. Implementations call [speak]'s onDone exactly once per line, even on error. */
interface Speaker {
    val label: String
    fun init(onResult: (Boolean) -> Unit)
    fun speak(text: String, onDone: () -> Unit)
    fun stop()
    fun release()
}

interface ListenCallback {
    fun onPartial(text: String)
    fun onFinal(text: String)
    fun onError(reason: String)
}

/** Turns passengers' speech into text. */
interface Listener {
    val label: String
    fun init(onResult: (Boolean) -> Unit)
    fun listen(callback: ListenCallback)
    fun stop()
    fun release()
}

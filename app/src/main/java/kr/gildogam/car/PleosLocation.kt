package kr.gildogam.car

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kr.gildogam.core.CarEvent

/**
 * Vehicle position from the Pleos FusedLocation SDK, delivered on the main thread.
 * Positions stay on the device: the engine judges road segments locally.
 */
class PleosLocation(
    context: Context,
    private val emit: (CarEvent) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val app = context.applicationContext
    private var fused: FusedLocation? = null
    private var running = false

    var status: String = "미연결"
        private set

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            val loc = location ?: return
            main.post {
                status = "위치 연결됨"
                emit(CarEvent.Location(loc.latitude, loc.longitude))
            }
        }

        override fun onError(exception: Exception) {
            main.post { status = "위치 오류" }
        }
    }

    fun start() {
        if (running) return
        try {
            val f = FusedLocation(app)
            if (!f.isSupported()) {
                status = "FusedLocation 미지원"
                return
            }
            f.initialize()
            f.registerFusedLocationCallback(listener)
            fused = f
            running = true
            status = "위치 대기"
        } catch (t: Throwable) {
            Log.w(TAG, "fused location unavailable", t)
            status = "FusedLocation 없음"
        }
    }

    fun stop() {
        if (!running) return
        runCatching {
            fused?.unregisterFusedLocationCallback(listener)
            fused?.release()
        }
        running = false
    }

    private companion object {
        const val TAG = "PleosLocation"
    }
}

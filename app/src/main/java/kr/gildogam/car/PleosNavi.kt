package kr.gildogam.car

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kr.gildogam.core.CarEvent

/**
 * Destination, remaining time and the "quiet zone" around turn instructions, from the
 * Pleos NaviHelper SDK. NaviHelper has no "voice guidance is playing" callback, so the
 * quiet zone is predicted from the distance to the next turn (enter ≤ 300 m, leave ≥ 400 m).
 */
class PleosNavi(
    context: Context,
    private val emit: (CarEvent) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val navi = NaviHelper(context.applicationContext)
    private var connected = false
    private var quiet = false
    private var lastDestination: String? = null
    private var lastMinutes: Int? = null

    var status: String = "미연결"
        private set

    private val poller = object : Runnable {
        override fun run() {
            if (!connected) return
            try {
                navi.getTBTInfo()
                navi.getDestinationInfo()
            } catch (e: SecurityException) {
                status = "내비 권한 없음"
            } catch (t: Throwable) {
                Log.w(TAG, "poll failed", t)
            }
            main.postDelayed(this, POLL_MS)
        }
    }

    private val listener = object : NaviHelperEventListener {
        override fun onNavigationStatus(status: NavigationStatus) = Unit

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            main.post { this@PleosNavi.status = "경로 안내 중" }
        }

        override fun onRouteCancelled() {
            main.post {
                lastDestination = null
                lastMinutes = null
                setQuiet(false)
                this@PleosNavi.status = "경로 없음"
                emit(CarEvent.RouteCancelled)
            }
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            main.post {
                reportDestination(destinationInfo.name)
                reportRemain(destinationInfo.remainTime)
            }
        }

        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            main.post { reportRemain(drivingInfo.destination.duration) }
        }

        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
            main.post {
                val next = tbtInfoList.firstOrNull { it.distance > 0.0 }?.distance
                when {
                    next != null && next <= QUIET_START_M -> setQuiet(true)
                    next == null || next >= QUIET_END_M -> setQuiet(false)
                }
            }
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "navi error $errorCode")
        }
    }

    fun start() {
        try {
            navi.initialize(
                onConnected = {
                    main.post {
                        connected = true
                        navi.addListener(listener)
                        status = "내비 연결됨"
                        main.post(poller)
                    }
                },
                onFailed = { t ->
                    main.post {
                        Log.w(TAG, "navi connect failed: $t")
                        status = "내비 연결 실패"
                    }
                },
            )
        } catch (t: Throwable) {
            Log.w(TAG, "navi unavailable", t)
            status = "NaviHelper 없음"
        }
    }

    private fun reportDestination(name: String?) {
        if (name.isNullOrBlank() || name == lastDestination) return
        lastDestination = name
        emit(CarEvent.Destination(name))
    }

    private fun reportRemain(seconds: Int) {
        if (seconds <= 0) return
        val minutes = (seconds + 59) / 60
        if (minutes == lastMinutes) return
        lastMinutes = minutes
        emit(CarEvent.RemainingTime(minutes))
    }

    private fun setQuiet(active: Boolean) {
        if (active == quiet) return
        quiet = active
        emit(CarEvent.NaviQuiet(active))
    }

    fun stop() {
        main.removeCallbacks(poller)
        runCatching {
            if (connected) navi.removeListener(listener)
            navi.release()
        }
        connected = false
    }

    private companion object {
        const val TAG = "PleosNavi"
        const val POLL_MS = 2_000L
        const val QUIET_START_M = 300.0
        const val QUIET_END_M = 400.0
    }
}

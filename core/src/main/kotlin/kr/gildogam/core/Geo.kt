package kr.gildogam.core

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun inside(gate: Gate, lat: Double, lng: Double): Boolean = distanceM(gate.lat, gate.lng, lat, lng) <= gate.radiusM

    /** Points every [stepM] metres along the polyline (for the virtual drive). */
    fun interpolate(waypoints: List<Pair<Double, Double>>, stepM: Double): List<Pair<Double, Double>> {
        if (waypoints.size < 2) return waypoints
        val out = mutableListOf(waypoints.first())
        for (i in 1 until waypoints.size) {
            val (aLat, aLng) = waypoints[i - 1]
            val (bLat, bLng) = waypoints[i]
            val d = distanceM(aLat, aLng, bLat, bLng)
            val steps = (d / stepM).toInt().coerceAtLeast(1)
            for (s in 1..steps) {
                val t = s.toDouble() / steps
                out += (aLat + (bLat - aLat) * t) to (aLng + (bLng - aLng) * t)
            }
        }
        return out
    }
}

/** Time of day for editions, from the sun at that place (NOAA sunrise equation). */
enum class DayPart { DAWN, DAY, NIGHT }

object Sun {
    private const val J1970 = 2440587.5
    private const val J2000 = 2451545.0
    private const val DAY_MS = 86_400_000.0

    /** Twilight allowances: 새벽 = the 90 minutes before sunrise, 야경 = from 20 minutes after sunset. */
    private const val DAWN_MS = 90 * 60_000L
    private const val DUSK_MS = 20 * 60_000L

    data class Times(val sunriseMs: Long, val sunsetMs: Long, val noonMs: Long)

    fun times(nowMs: Long, lat: Double, lng: Double): Times? {
        val jNow = nowMs / DAY_MS + J1970
        val n0 = floor(jNow - J2000 + 0.0008)
        // pick the solar noon nearest to now so night before/after midnight resolves correctly
        return listOf(n0 - 1, n0, n0 + 1)
            .mapNotNull { timesForDay(it, lat, lng) }
            .minByOrNull { kotlin.math.abs(it.noonMs - nowMs) }
    }

    private fun timesForDay(n: Double, lat: Double, lng: Double): Times? {
        val rad = PI / 180
        val jStar = n - lng / 360.0
        val m = ((357.5291 + 0.98560028 * jStar) % 360 + 360) % 360
        val c = 1.9148 * sin(m * rad) + 0.0200 * sin(2 * m * rad) + 0.0003 * sin(3 * m * rad)
        val lambda = ((m + c + 180 + 102.9372) % 360 + 360) % 360
        val jTransit = J2000 + jStar + 0.0053 * sin(m * rad) - 0.0069 * sin(2 * lambda * rad)
        val sinDecl = sin(lambda * rad) * sin(23.4397 * rad)
        val cosDecl = cos(asin(sinDecl))
        val cosOmega = (sin(-0.833 * rad) - sin(lat * rad) * sinDecl) / (cos(lat * rad) * cosDecl)
        if (cosOmega < -1 || cosOmega > 1) return null // polar day or night
        val omega = acos(cosOmega) / rad
        fun toMs(j: Double) = ((j - J1970) * DAY_MS).roundToLong()
        return Times(toMs(jTransit - omega / 360), toMs(jTransit + omega / 360), toMs(jTransit))
    }

    fun dayPart(nowMs: Long, lat: Double, lng: Double): DayPart {
        val t = times(nowMs, lat, lng) ?: return DayPart.DAY
        return when {
            nowMs < t.sunriseMs - DAWN_MS -> DayPart.NIGHT
            nowMs < t.sunriseMs -> DayPart.DAWN
            nowMs <= t.sunsetMs + DUSK_MS -> DayPart.DAY
            else -> DayPart.NIGHT
        }
    }
}

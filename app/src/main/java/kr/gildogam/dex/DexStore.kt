package kr.gildogam.dex

import android.content.Context
import android.util.Log
import kr.gildogam.core.Acquisition
import kr.gildogam.core.Badge
import kr.gildogam.core.CardKind
import kr.gildogam.core.Dex
import kr.gildogam.core.Edition
import kr.gildogam.core.Journey
import kr.gildogam.core.MyRoad
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The journey book, saved as one JSON file in app-private storage. Nothing leaves the device. */
class DexStore(context: Context) {

    private val file = File(context.filesDir, "dex.json")

    fun load(): Dex = try {
        if (file.exists()) fromJson(JSONObject(file.readText(Charsets.UTF_8))) else Dex()
    } catch (t: Throwable) {
        Log.w(TAG, "load failed, starting empty", t)
        Dex()
    }

    fun save(dex: Dex) {
        try {
            val tmp = File(file.parentFile, "dex.json.tmp")
            tmp.writeText(toJson(dex).toString(), Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText(Charsets.UTF_8), Charsets.UTF_8)
                tmp.delete()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "save failed", t)
        }
    }

    fun clear() {
        file.delete()
    }

    // ------------------------------------------------------------------ json

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONObject.optDoubleOrNull(k: String): Double? = if (isNull(k)) null else getDouble(k)
    private fun JSONObject.optLongOrNull(k: String): Long? = if (isNull(k)) null else getLong(k)
    private fun JSONObject.optStringOrNull(k: String): String? = if (isNull(k)) null else getString(k)
    private fun JSONObject.putNullable(k: String, v: Any?) = put(k, v ?: JSONObject.NULL)

    private fun toJson(d: Dex) = JSONObject().apply {
        put("version", 1)
        put("acquisitions", JSONArray().apply {
            d.acquisitions.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id)
                    put("cardId", a.cardId)
                    put("cardName", a.cardName)
                    put("kind", a.kind.name)
                    put("editions", JSONArray(a.editions.map { it.name }))
                    put("badges", JSONArray(a.badges.map { it.name }))
                    put("atMs", a.atMs)
                    put("riders", JSONArray(a.riders))
                    put("journeyId", a.journeyId)
                    putNullable("note", a.note)
                })
            }
        })
        put("journeys", JSONArray().apply {
            d.journeys.forEach { j ->
                put(JSONObject().apply {
                    put("id", j.id)
                    put("startedAtMs", j.startedAtMs)
                    putNullable("endedAtMs", j.endedAtMs)
                    putNullable("destination", j.destination)
                    put("riders", JSONArray(j.riders))
                    put("acquisitionIds", JSONArray(j.acquisitionIds))
                    putNullable("startLat", j.startLat)
                    putNullable("startLng", j.startLng)
                    putNullable("endLat", j.endLat)
                    putNullable("endLng", j.endLng)
                })
            }
        })
        put("myRoads", JSONArray().apply {
            d.myRoads.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("name", r.name)
                    put("startLat", r.startLat)
                    put("startLng", r.startLng)
                    put("endLat", r.endLat)
                    put("endLng", r.endLng)
                    put("trips", r.trips)
                })
            }
        })
    }

    private fun fromJson(o: JSONObject): Dex {
        val acq = o.getJSONArray("acquisitions").let { arr ->
            (0 until arr.length()).map { i ->
                val a = arr.getJSONObject(i)
                Acquisition(
                    id = a.getString("id"),
                    cardId = a.getString("cardId"),
                    cardName = a.getString("cardName"),
                    kind = CardKind.valueOf(a.getString("kind")),
                    editions = a.getJSONArray("editions").strings().map { Edition.valueOf(it) }.toSet(),
                    badges = a.getJSONArray("badges").strings().map { Badge.valueOf(it) }.toSet(),
                    atMs = a.getLong("atMs"),
                    riders = a.getJSONArray("riders").strings(),
                    journeyId = a.getString("journeyId"),
                    note = a.optStringOrNull("note"),
                )
            }
        }
        val journeys = o.getJSONArray("journeys").let { arr ->
            (0 until arr.length()).map { i ->
                val j = arr.getJSONObject(i)
                Journey(
                    id = j.getString("id"),
                    startedAtMs = j.getLong("startedAtMs"),
                    endedAtMs = j.optLongOrNull("endedAtMs"),
                    destination = j.optStringOrNull("destination"),
                    riders = j.getJSONArray("riders").strings(),
                    acquisitionIds = j.getJSONArray("acquisitionIds").strings(),
                    startLat = j.optDoubleOrNull("startLat"),
                    startLng = j.optDoubleOrNull("startLng"),
                    endLat = j.optDoubleOrNull("endLat"),
                    endLng = j.optDoubleOrNull("endLng"),
                )
            }
        }
        val roads = o.getJSONArray("myRoads").let { arr ->
            (0 until arr.length()).map { i ->
                val r = arr.getJSONObject(i)
                MyRoad(
                    r.getString("id"), r.getString("name"),
                    r.getDouble("startLat"), r.getDouble("startLng"),
                    r.getDouble("endLat"), r.getDouble("endLng"),
                    r.getInt("trips"),
                )
            }
        }
        return Dex(acq, journeys, roads)
    }

    private companion object {
        const val TAG = "DexStore"
    }
}

package online.k73.bmwlauncher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import online.k73.bmwlauncher.car.Trip
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Finished trips, newest first, in a small JSON file (last [MAX] kept). */
class TripStore private constructor(context: Context) {
    private val file = File(context.filesDir, "trips.json")
    private val _trips = MutableStateFlow(load())
    val trips: StateFlow<List<Trip>> = _trips

    @Synchronized
    fun add(trip: Trip) {
        val list = (listOf(trip) + _trips.value).take(MAX)
        runCatching { file.writeText(encode(list)) }
        _trips.value = list
    }

    private fun load(): List<Trip> = runCatching { decode(file.readText()) }.getOrDefault(emptyList())

    companion object {
        const val MAX = 200

        fun encode(trips: List<Trip>): String = JSONArray().apply {
            trips.forEach { t ->
                put(JSONObject().apply {
                    put("start", t.startMs); put("end", t.endMs); put("km", t.distanceKm); put("max", t.maxSpeedKmh)
                    t.startOdometerKm?.let { put("odo", it) }
                    t.consumption?.let { put("cons", it) }
                })
            }
        }.toString()

        fun decode(json: String): List<Trip> {
            val a = JSONArray(json)
            return (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Trip(
                    startMs = o.getLong("start"), endMs = o.getLong("end"), distanceKm = o.getDouble("km"),
                    maxSpeedKmh = o.getInt("max"),
                    startOdometerKm = if (o.has("odo")) o.getInt("odo") else null,
                    consumption = if (o.has("cons")) o.getString("cons") else null,
                )
            }
        }

        @Volatile private var instance: TripStore? = null
        fun get(context: Context): TripStore =
            instance ?: synchronized(this) { instance ?: TripStore(context.applicationContext).also { instance = it } }
    }
}

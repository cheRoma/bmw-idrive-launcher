package online.k73.bmwlauncher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import online.k73.bmwlauncher.car.OilChange
import java.time.LocalDate

/**
 * Last oil change and the last odometer reading, kept across restarts. The odometer is saved
 * because the cluster sends it rarely (on wake-up), and the reminder must still work with the
 * engine off. SharedPreferences rather than DataStore so it can be seeded over adb as plain XML.
 */
class OilStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _oil = MutableStateFlow(read())
    val oil: StateFlow<OilChange> = _oil

    private val _odometerKm = MutableStateFlow(prefs.getInt(K_ODO, 0).takeIf { it > 0 })
    val odometerKm: StateFlow<Int?> = _odometerKm

    fun saveOdometer(km: Int) {
        if (km <= 0 || km == _odometerKm.value) return
        prefs.edit().putInt(K_ODO, km).apply()
        _odometerKm.value = km
    }

    /** «Заменил сегодня»: restart the countdown from the current odometer. */
    fun markChanged(odometerKm: Int, today: LocalDate = LocalDate.now()) {
        prefs.edit().putInt(K_LAST_KM, odometerKm).putString(K_LAST_DATE, today.toString()).apply()
        _oil.value = read()
    }

    private fun read() = OilChange(
        lastKm = prefs.getInt(K_LAST_KM, SEED_LAST_KM),
        intervalKm = prefs.getInt(K_INTERVAL_KM, SEED_INTERVAL_KM),
        lastDate = prefs.getString(K_LAST_DATE, null),
    )

    companion object {
        const val FILE = "maintenance"
        private const val K_LAST_KM = "oil_last_km"
        private const val K_INTERVAL_KM = "oil_interval_km"
        private const val K_LAST_DATE = "oil_last_date"
        private const val K_ODO = "odometer_km"

        // This car: oil every 4 000 km, last changed at 280 332 km (Roma, 2026-10-14).
        const val SEED_LAST_KM = 280_332
        const val SEED_INTERVAL_KM = 4_000

        @Volatile private var instance: OilStore? = null
        fun get(context: Context): OilStore =
            instance ?: synchronized(this) { instance ?: OilStore(context.applicationContext).also { instance = it } }
    }
}

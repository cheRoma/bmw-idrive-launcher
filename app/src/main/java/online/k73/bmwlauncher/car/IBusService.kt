package online.k73.bmwlauncher.car

import android.content.Context

/**
 * Process-wide single I-Bus reader. The USB adapter is single-owner, so one shared connection feeds
 * both the home status bar (live outside temperature) and the «Борткомпьютер» screen — and the BC
 * opens instantly because it's already connected. Started once, kept alive for the process.
 */
object IBusService {
    @Volatile private var reader: IBusReader? = null

    fun get(context: Context): IBusReader =
        reader ?: synchronized(this) {
            reader ?: IBusReader(context.applicationContext).also { it.start(); reader = it }
        }

    /** Clear the latched OBC "LIMIT 6 KM/H" from the cluster. Call off the main thread. */
    fun clearSpeedLimit(context: Context): Boolean = get(context).clearSpeedLimit()

    /** Reset the trip average speed. */
    fun resetTrip(context: Context) = get(context).resetTrip()
}

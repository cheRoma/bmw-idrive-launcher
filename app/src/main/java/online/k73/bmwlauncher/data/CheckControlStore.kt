package online.k73.bmwlauncher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** One Check Control message as the cluster showed it, in its original text. */
data class CcEntry(val atMs: Long, val text: String)

/**
 * Check Control messages: the one on the cluster right now ([active], in memory) and the last
 * [MAX] that appeared ([history], newest first, kept in a small text file across restarts).
 */
class CheckControlStore private constructor(context: Context) {
    private val file = File(context.filesDir, "checkcontrol.txt")

    private val _history = MutableStateFlow(load())
    val history: StateFlow<List<CcEntry>> = _history

    private val _active = MutableStateFlow<String?>(null)
    val active: StateFlow<String?> = _active

    // Dead bulbs as the light module last reported them; kept across key-off so a parked car still
    // says which one, until the module reports otherwise.
    private val _faultyLamps = MutableStateFlow<Set<online.k73.bmwlauncher.car.Lamp>>(emptySet())
    val faultyLamps: StateFlow<Set<online.k73.bmwlauncher.car.Lamp>> = _faultyLamps

    fun setFaultyLamps(lamps: Set<online.k73.bmwlauncher.car.Lamp>) {
        _faultyLamps.value = lamps
    }

    @Synchronized
    fun show(text: String, nowMs: Long = System.currentTimeMillis()) {
        _active.value = text
        val list = (listOf(CcEntry(nowMs, text)) + _history.value).take(MAX)
        runCatching { file.writeText(encode(list)) }
        _history.value = list
    }

    fun clear() {
        _active.value = null
    }

    private fun load(): List<CcEntry> = runCatching { decode(file.readText()) }.getOrDefault(emptyList())

    companion object {
        const val MAX = 100

        /** One entry per line, `<epoch ms>\t<text>`; the text is ASCII without tabs or newlines. */
        fun encode(list: List<CcEntry>): String = list.joinToString("\n") { "${it.atMs}\t${it.text}" }

        fun decode(s: String): List<CcEntry> = s.lineSequence().mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@mapNotNull null
            val ms = line.substring(0, tab).toLongOrNull() ?: return@mapNotNull null
            CcEntry(ms, line.substring(tab + 1))
        }.toList()

        @Volatile private var instance: CheckControlStore? = null
        fun get(context: Context): CheckControlStore =
            instance ?: synchronized(this) { instance ?: CheckControlStore(context.applicationContext).also { instance = it } }
    }
}

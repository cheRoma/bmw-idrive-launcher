package online.k73.bmwlauncher.ui.home

import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-wide state of the live map backdrop.
 *
 * Two jobs, both born from the black-screen hunt:
 * - **counting** how many [MapBackground] views this process has created and destroyed. Since v1.6.39
 *   the map is composed once by HomeActivity below the NavHost, so a healthy report reads
 *   `created=1 destroyed=0` no matter how long the drive; anything else means the view is being
 *   rebuilt again and the churn is back.
 * - an **off switch** the black-screen watchdog can pull. The map is the prime suspect, and dropping
 *   it is the cheapest repair we can try; the flag stays off until the process restarts, so a
 *   vanished map on the home screen is the visible sign that a black screen was healed.
 */
object MapRuntime {
    /** Read by [MapBackground]; flipping it recomposes the home screen without the map. */
    val enabled = mutableStateOf(true)

    private val created = AtomicInteger()
    private val destroyed = AtomicInteger()

    @Volatile
    var disabledReason: String? = null
        private set

    fun nextInstance(): Int = created.incrementAndGet()

    fun onDestroyed() {
        destroyed.incrementAndGet()
    }

    /** Call on the main thread — it drives a recomposition. */
    fun disable(reason: String) {
        disabledReason = reason
        enabled.value = false
    }

    fun state(): String = buildString {
        append("created=").append(created.get())
        append(" destroyed=").append(destroyed.get())
        append(" enabled=").append(enabled.value)
        disabledReason?.let { append(" (off: ").append(it).append(')') }
    }
}

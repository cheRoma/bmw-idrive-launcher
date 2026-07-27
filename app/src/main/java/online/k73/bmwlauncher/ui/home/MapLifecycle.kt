package online.k73.bmwlauncher.ui.home

import androidx.compose.runtime.RememberObserver
import online.k73.bmwlauncher.diag.AppLog

/**
 * Holds one [MapBackground] map view together with its teardown, and **is itself** what `remember`
 * returns — Compose only tracks a [RememberObserver] it receives directly, so wrapping one in a pair
 * or a lambda silently disables the whole mechanism.
 *
 * Why not a plain `remember` plus a `DisposableEffect`: a value built inside `remember` is not
 * guaranteed to reach the composition. If the composition is abandoned before it is applied, no
 * effect ever runs and the map view — with its GL context — is leaked with nothing left to close it.
 * The car reported exactly that: `created=2 destroyed=0` in one process, one live context more than
 * the screen ever showed.
 *
 * Compose calls [onAbandoned] for the discarded value and [onForgotten] for the ordinary case, so
 * every created view is destroyed on exactly one of the two paths. [release] is idempotent because
 * both can be reached in odd orders while a screen is being torn down.
 */
class MapLifecycle<T>(
    /** The view, or null when it could not be created at all. */
    val value: T?,
    private val destroy: (T) -> Unit,
) : RememberObserver {
    /** Instance number for the diagnostics report: healthy is `created == destroyed + 1`. */
    val instance: Int = MapRuntime.nextInstance()

    @Volatile
    var released = false
        private set

    override fun onRemembered() = Unit

    override fun onForgotten() = release("экран закрыт")

    override fun onAbandoned() = release("композиция отброшена")

    fun release(reason: String) {
        if (released) return
        released = true
        value?.let { payload ->
            runCatching { destroy(payload) }
                .onFailure { AppLog.w("MAP", "view#$instance не закрылся: ${it.message}") }
        }
        MapRuntime.onDestroyed()
        AppLog.d("MAP", "view#$instance destroyed ($reason)")
    }
}

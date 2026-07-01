package online.k73.bmwlauncher.autostart

import kotlinx.coroutines.delay
import online.k73.bmwlauncher.system.Shell
import online.k73.bmwlauncher.system.ShellCommands

/**
 * Ensures the i-Bus App is running in the background, retrying with backoff until the
 * process appears or the delay schedule is exhausted. Launches via a root shell and then
 * calls [onLaunched] so the caller can re-assert the launcher on top (flash-free background).
 *
 * @param delaysMs backoff schedule; also bounds the number of retries.
 * @param onLaunched invoked right after each launch attempt (e.g. re-foreground HOME).
 */
class AutostartController(
    private val shell: Shell,
    private val delaysMs: LongArray = longArrayOf(3_000, 6_000, 12_000, 20_000, 30_000),
    private val onLaunched: () -> Unit,
) {
    private fun isRunning(pkg: String): Boolean =
        shell.exec(ShellCommands.pidof(pkg)).let { it.ok && it.stdout.isNotBlank() }

    suspend fun ensureRunning(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        if (isRunning(pkg)) return true
        for (delayMs in delaysMs) {
            shell.exec(ShellCommands.startPackage(pkg))
            onLaunched()
            delay(delayMs)
            if (isRunning(pkg)) return true
        }
        return isRunning(pkg)
    }
}

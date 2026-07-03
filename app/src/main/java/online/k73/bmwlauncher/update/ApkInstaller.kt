package online.k73.bmwlauncher.update

import online.k73.bmwlauncher.system.Shell
import online.k73.bmwlauncher.system.ShellCommands
import java.io.File

sealed interface InstallResult {
    data object InstalledSilently : InstallResult
    data object LaunchedInstaller : InstallResult
    data class Failed(val message: String) : InstallResult
}

/**
 * Root-adaptive installer. With root, runs `pm install -r <apk> && am start ...` as one su call.
 * Without root, delegates to [launchInstaller] which fires the system PackageInstaller intent.
 * The intent launch is injected so this class is unit-testable without Android.
 */
class ApkInstaller(
    private val hasRoot: () -> Boolean,
    private val shell: Shell,
    private val component: String,
    private val launchInstaller: (File) -> Unit,
) {
    fun install(apk: File): InstallResult {
        if (hasRoot()) {
            val r = shell.exec(ShellCommands.installAndRelaunch(apk.absolutePath, component))
            return if (r.ok) InstallResult.InstalledSilently
            else InstallResult.Failed(r.stderr.ifBlank { "pm install failed (exit ${r.exitCode})" })
        }
        launchInstaller(apk)
        return InstallResult.LaunchedInstaller
    }
}

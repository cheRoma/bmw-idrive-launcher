package online.k73.bmwlauncher.launch

import android.content.Context
import android.content.Intent

class AppLauncher(private val context: Context) {
    fun isInstalled(pkg: String): Boolean =
        pkg.isNotBlank() && context.packageManager.getLaunchIntentForPackage(pkg) != null

    fun launchIntentFor(pkg: String): Intent? =
        if (pkg.isBlank()) null
        else context.packageManager.getLaunchIntentForPackage(pkg)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    /**
     * Launches the first of [pkgs] that is actually installed. For apps that ship under more than
     * one package id (phone vs Android-TV build) and we cannot check which one the head unit got.
     */
    fun launchFirstInstalled(vararg pkgs: String): Boolean =
        pkgs.firstOrNull { isInstalled(it) }?.let { launch(it) } ?: false

    /** Returns true if the app was launched. */
    fun launch(pkg: String): Boolean {
        val intent = launchIntentFor(pkg) ?: return false
        context.startActivity(intent)
        return true
    }
}

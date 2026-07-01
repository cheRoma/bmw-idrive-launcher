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

    /** Returns true if the app was launched. */
    fun launch(pkg: String): Boolean {
        val intent = launchIntentFor(pkg) ?: return false
        context.startActivity(intent)
        return true
    }
}

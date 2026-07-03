package online.k73.bmwlauncher.system

data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val ok: Boolean get() = exitCode == 0
}

interface Shell {
    /** Runs [command] via a root shell (su -c). Returns exit code + captured output. */
    fun exec(command: String): ShellResult
}

/** Pure builders for the shell commands we run as root. Kept separate so they are unit-testable. */
object ShellCommands {
    fun reboot(): String = "reboot"
    fun pidof(pkg: String): String = "pidof $pkg"
    // monkey with the LAUNCHER category launches a package's main activity headlessly and
    // works reliably from a root shell without knowing the exact component name.
    fun startPackage(pkg: String): String =
        "monkey -p $pkg -c android.intent.category.LAUNCHER 1"
    fun id(): String = "id"
    // Install over the existing app and immediately relaunch, as ONE su invocation so it
    // survives our own process being killed during the reinstall.
    fun installAndRelaunch(apkPath: String, component: String): String =
        "pm install -r $apkPath && am start -n $component"
}

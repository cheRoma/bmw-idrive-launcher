package online.k73.bmwlauncher.update

import online.k73.bmwlauncher.system.Shell
import online.k73.bmwlauncher.system.ShellCommands

class RootDetector(private val shell: Shell) {
    private var cached: Boolean? = null

    /** True if `su -c id` reports uid=0. Result cached for the session. */
    fun hasRoot(): Boolean {
        cached?.let { return it }
        val r = shell.exec(ShellCommands.id())
        val result = r.ok && r.stdout.contains("uid=0")
        cached = result
        return result
    }
}

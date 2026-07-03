package online.k73.bmwlauncher.update

import online.k73.bmwlauncher.system.Shell
import online.k73.bmwlauncher.system.ShellResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private class RecordingShell(val ok: Boolean) : Shell {
    val commands = mutableListOf<String>()
    override fun exec(command: String): ShellResult {
        commands += command
        return if (ok) ShellResult(0, "Success", "") else ShellResult(1, "", "signatures do not match")
    }
}

class ApkInstallerTest {
    private val apk = File("/tmp/update.apk")

    @Test fun root_path_runs_pm_install_and_returns_silent() {
        val shell = RecordingShell(ok = true)
        var intentLaunched = false
        val installer = ApkInstaller(hasRoot = { true }, shell = shell, component = "online.k73.bmwlauncher/.ui.HomeActivity", launchInstaller = { intentLaunched = true })
        val res = installer.install(apk)
        assertEquals(InstallResult.InstalledSilently, res)
        assertTrue(shell.commands.any { it.startsWith("pm install -r /tmp/update.apk") })
        assertTrue(!intentLaunched)
    }

    @Test fun root_install_failure_returns_failed_with_stderr() {
        val shell = RecordingShell(ok = false)
        val installer = ApkInstaller(hasRoot = { true }, shell = shell, component = "c", launchInstaller = { })
        val res = installer.install(apk)
        assertTrue(res is InstallResult.Failed)
        assertTrue((res as InstallResult.Failed).message.contains("signatures"))
    }

    @Test fun no_root_launches_system_installer() {
        val shell = RecordingShell(ok = true)
        var launchedFile: File? = null
        val installer = ApkInstaller(hasRoot = { false }, shell = shell, component = "c", launchInstaller = { launchedFile = it })
        val res = installer.install(apk)
        assertEquals(InstallResult.LaunchedInstaller, res)
        assertEquals(apk, launchedFile)
        assertTrue(shell.commands.isEmpty())
    }
}

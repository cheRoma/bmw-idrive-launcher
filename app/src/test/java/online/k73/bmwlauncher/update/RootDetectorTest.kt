package online.k73.bmwlauncher.update

import online.k73.bmwlauncher.system.Shell
import online.k73.bmwlauncher.system.ShellResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeShell(val result: ShellResult) : Shell {
    var lastCommand: String? = null
    override fun exec(command: String): ShellResult { lastCommand = command; return result }
}

class RootDetectorTest {
    @Test fun uid_zero_means_root() {
        val d = RootDetector(FakeShell(ShellResult(0, "uid=0(root) gid=0(root)", "")))
        assertTrue(d.hasRoot())
    }
    @Test fun non_root_uid_means_no_root() {
        val d = RootDetector(FakeShell(ShellResult(0, "uid=10123(u0_a123)", "")))
        assertFalse(d.hasRoot())
    }
    @Test fun failed_su_means_no_root() {
        val d = RootDetector(FakeShell(ShellResult(127, "", "su: not found")))
        assertFalse(d.hasRoot())
    }
}

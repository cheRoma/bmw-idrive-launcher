package online.k73.bmwlauncher.autostart

import online.k73.bmwlauncher.system.Shell
import online.k73.bmwlauncher.system.ShellResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeShell(
    /** number of pidof calls that return "not running" before it reports running */
    private val notRunningTimes: Int
) : Shell {
    var startCalls = 0
    private var pidofCalls = 0
    val commands = mutableListOf<String>()
    override fun exec(command: String): ShellResult {
        commands += command
        return when {
            command.startsWith("pidof") -> {
                pidofCalls++
                if (pidofCalls > notRunningTimes) ShellResult(0, "1234", "")
                else ShellResult(1, "", "")
            }
            command.startsWith("monkey") -> { startCalls++; ShellResult(0, "ok", "") }
            else -> ShellResult(0, "", "")
        }
    }
}

class AutostartControllerTest {
    @Test fun already_running_does_not_start() = runTest {
        val shell = FakeShell(notRunningTimes = 0)
        val c = AutostartController(shell, delaysMs = longArrayOf(1, 1, 1)) { }
        val ok = c.ensureRunning("de.example.ibus")
        assertTrue(ok)
        assertEquals(0, shell.startCalls)
    }

    @Test fun starts_then_succeeds_after_retries() = runTest {
        val shell = FakeShell(notRunningTimes = 2)
        val c = AutostartController(shell, delaysMs = longArrayOf(1, 1, 1, 1)) { }
        val ok = c.ensureRunning("de.example.ibus")
        assertTrue(ok)
        assertTrue(shell.startCalls >= 1)
        assertTrue(shell.commands.any { it.startsWith("monkey -p de.example.ibus") })
    }

    @Test fun gives_up_after_exhausting_delays() = runTest {
        val shell = FakeShell(notRunningTimes = 999)
        val c = AutostartController(shell, delaysMs = longArrayOf(1, 1)) { }
        val ok = c.ensureRunning("de.example.ibus")
        assertFalse(ok)
    }

    @Test fun blank_package_is_a_noop_failure() = runTest {
        val shell = FakeShell(notRunningTimes = 0)
        val c = AutostartController(shell, delaysMs = longArrayOf(1)) { }
        assertFalse(c.ensureRunning(""))
        assertEquals(0, shell.startCalls)
    }
}

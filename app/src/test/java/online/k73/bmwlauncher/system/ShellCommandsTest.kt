package online.k73.bmwlauncher.system

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellCommandsTest {
    @Test fun reboot_command() {
        assertEquals("reboot", ShellCommands.reboot())
    }
    @Test fun pidof_command() {
        assertEquals("pidof de.example.ibus", ShellCommands.pidof("de.example.ibus"))
    }
    @Test fun start_package_command() {
        assertEquals(
            "monkey -p de.example.ibus -c android.intent.category.LAUNCHER 1",
            ShellCommands.startPackage("de.example.ibus")
        )
    }
}

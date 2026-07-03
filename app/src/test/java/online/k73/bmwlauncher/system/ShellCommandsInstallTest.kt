package online.k73.bmwlauncher.system

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellCommandsInstallTest {
    @Test fun install_and_relaunch_is_a_single_su_command() {
        assertEquals(
            "pm install -r /data/local/tmp/u.apk && am start -n online.k73.bmwlauncher/.ui.HomeActivity",
            ShellCommands.installAndRelaunch("/data/local/tmp/u.apk", "online.k73.bmwlauncher/.ui.HomeActivity")
        )
    }
    @Test fun whoami_is_id() {
        assertEquals("id", ShellCommands.id())
    }
}

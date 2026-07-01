package online.k73.bmwlauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.k73.bmwlauncher.data.LauncherSettings
import online.k73.bmwlauncher.data.ThemeMode
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    onAutostart: (Boolean) -> Unit,
    onBringToFront: (Boolean) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
) {
    val c = LocalLauncherColors.current
    Column(Modifier.fillMaxSize().background(c.background).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Настройки", color = c.text, fontSize = 26.sp, modifier = Modifier.padding(bottom = 12.dp))

        SwitchRow("Автозапуск Борткомпьютера", "i-Bus App", settings.autostartIBus, onAutostart)
        SwitchRow("Выводить на передний план при запуске", null, settings.bringLauncherToFront, onBringToFront)

        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Тема оформления", color = c.text, fontSize = 22.sp, modifier = Modifier.weight(1f))
            listOf(ThemeMode.DAY to "День", ThemeMode.NIGHT to "Ночь", ThemeMode.AUTO to "Авто").forEach { (mode, label) ->
                val selected = settings.themeMode == mode
                Text(
                    label,
                    color = if (selected) c.accent else c.textDim,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(start = 20.dp)
                        .background(androidx.compose.ui.graphics.Color.Transparent)
                        .padding(8.dp)
                        .clickableNoRipple { onThemeMode(mode) },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalLauncherColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 22.sp)
            if (sub != null) Text(sub, color = c.textDim, fontSize = 16.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = androidx.compose.ui.graphics.Color.White),
        )
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null, onClick = onClick,
    )

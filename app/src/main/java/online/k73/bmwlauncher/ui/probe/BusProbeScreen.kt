package online.k73.bmwlauncher.ui.probe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import online.k73.bmwlauncher.car.BusProbeCatalog
import online.k73.bmwlauncher.car.KeyPosition
import online.k73.bmwlauncher.car.ProbeCmd
import online.k73.bmwlauncher.car.ProbeRisk
import online.k73.bmwlauncher.ui.theme.Inter
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors
import online.k73.bmwlauncher.ui.theme.ScreenHeader
import online.k73.bmwlauncher.ui.theme.TypeTokens
import online.k73.bmwlauncher.ui.theme.pressScale

private val Danger = Color(0xFFE5484D)

/**
 * Bus-probe screen — the manual actuator panel for mapping "which telegram does which physical
 * thing" on this car (the physical mirror button isn't on the bus, so we learn by trying). SAFE
 * telegrams (cluster/OBC/aux) are always tappable; the DANGER body-relay family — which includes
 * the mirror candidates AND the windows/locks — is gated behind an explicit "опасный режим" toggle,
 * the ignition being ON (so the physical window switches stay powered), and a per-tap confirm.
 * Every send is logged to the bus log for later correlation.
 */
@Composable
fun BusProbeScreen(
    connected: Boolean,
    keyPosition: KeyPosition?,
    keyRaw: Int?,
    onSend: (ProbeCmd) -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalLauncherColors.current
    val ignitionOn = keyPosition == KeyPosition.IGNITION || keyPosition == KeyPosition.START
    var dangerMode by remember { mutableStateOf(false) }
    var manualOverride by remember { mutableStateOf(false) }
    val unlocked = ignitionOn || manualOverride
    var pending by remember { mutableStateOf<ProbeCmd?>(null) }
    var lastSent by remember { mutableStateOf<String?>(null) }
    val keyRawHex = keyRaw?.let { "0x%02X".format(it) } ?: "—"

    fun fire(cmd: ProbeCmd) {
        onSend(cmd)
        lastSent = "${cmd.label} · ${cmd.hex}"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .padding(horizontal = 28.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader("Пробник шины", onBack)

        // Connection + key state.
        val keyText = when (keyPosition) {
            KeyPosition.OFF -> "выключено"
            KeyPosition.ACC -> "ACC (радио)"
            KeyPosition.IGNITION -> "включено"
            KeyPosition.START -> "стартер"
            null -> "нет данных"
        }
        val statusColor = if (!connected) Danger else if (ignitionOn) c.accent else c.textDim
        StatusLine(
            if (connected) "Адаптер: подключён · зажигание: $keyText · байт ключа: $keyRawHex" else "Адаптер НЕ подключён",
            statusColor,
        )
        lastSent?.let {
            Spacer(Modifier.height(4.dp))
            StatusLine("Последняя отправка: $it", c.textTertiary)
        }
        Spacer(Modifier.height(18.dp))

        // SAFE section — always available while connected.
        SectionTitle("Безопасные · приборка / БК / догреватель", c.accent)
        BusProbeCatalog.safe.forEach { cmd ->
            ProbeButton(cmd, enabled = connected) { fire(cmd) }
        }

        Spacer(Modifier.height(22.dp))

        // DANGER master toggle.
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Опасный режим", color = Danger, fontSize = TypeTokens.title, fontWeight = FontWeight.SemiBold, fontFamily = Inter)
                Text(
                    "Кузовные реле: окна, замки, багажник, зеркала. Только при включённом зажигании.",
                    color = c.textTertiary, fontSize = TypeTokens.caption, fontFamily = Inter,
                )
            }
            Switch(
                checked = dangerMode,
                onCheckedChange = { dangerMode = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Danger,
                    uncheckedThumbColor = c.textTertiary,
                    uncheckedTrackColor = c.surfaceHi,
                ),
            )
        }

        if (dangerMode) {
            Spacer(Modifier.height(8.dp))
            if (!ignitionOn) {
                WarningCard(
                    "Зажигание в шине не определилось (байт ключа $keyRawHex). Убедитесь, что оно " +
                        "включено — тогда штатные кнопки стёкол под питанием и всё можно вернуть. " +
                        "Если уверены, что зажигание включено, разблокируйте вручную ниже — " +
                        "ответственность на вас.",
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Разблокировать вручную", color = Danger, fontSize = TypeTokens.body, fontWeight = FontWeight.Medium, fontFamily = Inter)
                        Text("Подтверждаю: зажигание ВКЛ", color = c.textTertiary, fontSize = TypeTokens.caption, fontFamily = Inter)
                    }
                    Switch(
                        checked = manualOverride,
                        onCheckedChange = { manualOverride = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Danger,
                            uncheckedThumbColor = c.textTertiary,
                            uncheckedTrackColor = c.surfaceHi,
                        ),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            SectionTitle("Кузовные реле · перебор (осторожно)", Danger)
            BusProbeCatalog.danger.forEach { cmd ->
                ProbeButton(cmd, enabled = connected && unlocked) { pending = cmd }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    // Per-tap confirmation for the dangerous sends.
    pending?.let { cmd ->
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = c.tile,
            titleContentColor = c.text,
            textContentColor = c.textDim,
            title = { Text("Отправить в шину?", fontFamily = Inter, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(cmd.label, color = c.text, fontSize = TypeTokens.body, fontFamily = Inter)
                    Spacer(Modifier.height(6.dp))
                    Text(cmd.hex, color = c.accent, fontSize = TypeTokens.body, fontFamily = Inter)
                    if (cmd.note.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(cmd.note, color = Danger, fontSize = TypeTokens.caption, fontFamily = Inter)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { fire(cmd); pending = null }) {
                    Text("Отправить", color = Danger, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) {
                    Text("Отмена", color = c.textDim, fontFamily = Inter)
                }
            },
        )
    }
}

@Composable
private fun StatusLine(text: String, color: Color) {
    Text(text, color = color, fontSize = TypeTokens.caption, fontFamily = Inter)
}

@Composable
private fun SectionTitle(text: String, color: Color) {
    Text(
        text, color = color, fontSize = TypeTokens.caption, fontWeight = FontWeight.SemiBold, fontFamily = Inter,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun WarningCard(text: String) {
    val c = LocalLauncherColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.tile)
            .border(1.dp, Danger, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text("⚠ $text", color = Danger, fontSize = TypeTokens.caption, fontFamily = Inter)
    }
}

@Composable
private fun ProbeButton(cmd: ProbeCmd, enabled: Boolean, onTap: () -> Unit) {
    val c = LocalLauncherColors.current
    val edge = if (cmd.risk == ProbeRisk.DANGER) Danger else c.accent
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.tile)
            .border(1.dp, if (enabled) edge.copy(alpha = 0.55f) else c.hairline, RoundedCornerShape(12.dp))
            .then(if (enabled) Modifier.pressScale(onTap) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                cmd.label,
                color = if (enabled) c.text else c.textTertiary,
                fontSize = TypeTokens.body, fontWeight = FontWeight.Medium, fontFamily = Inter,
            )
            Text(cmd.hex, color = c.textTertiary, fontSize = TypeTokens.caption, fontFamily = Inter)
        }
        if (cmd.note.isNotBlank()) {
            Spacer(Modifier.height(0.dp))
            Text(
                cmd.note,
                color = if (cmd.risk == ProbeRisk.DANGER) Danger else c.textDim,
                fontSize = TypeTokens.caption, fontFamily = Inter,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

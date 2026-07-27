package online.k73.bmwlauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import online.k73.bmwlauncher.data.LauncherSettings
import online.k73.bmwlauncher.data.ThemeMode
import online.k73.bmwlauncher.diag.LogSendState
import online.k73.bmwlauncher.ui.theme.Inter
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors
import online.k73.bmwlauncher.ui.theme.ScreenHeader
import online.k73.bmwlauncher.ui.theme.TypeTokens
import online.k73.bmwlauncher.ui.theme.pressScale
import online.k73.bmwlauncher.update.UpdateUiState

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    onAutostart: (Boolean) -> Unit,
    onBringToFront: (Boolean) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    currentVersion: String,
    hasRoot: Boolean,
    updateState: UpdateUiState,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    isDefaultLauncher: Boolean,
    onSetDefault: () -> Unit,
    logState: LogSendState,
    onSendLogs: () -> Unit,
    /** False when the I-Bus adapter isn't connected — nothing bus-related can work then. */
    ibusConnected: Boolean = false,
    busCapturing: Boolean = false,
    busFrames: Int = 0,
    onToggleBusCapture: () -> Unit = {},
    onOpenProbe: () -> Unit = {},
    onMirrorAutoFold: (Boolean) -> Unit = {},
    /** True when SFA (sing-box) is on the unit — without it there is nothing to hand the profile to. */
    vpnAppInstalled: Boolean = false,
    onSendVpnProfile: () -> Unit = {},
    onBack: () -> Unit,
) {
    val c = LocalLauncherColors.current
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .padding(horizontal = 28.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader("Настройки", onBack)
        Spacer(Modifier.height(12.dp))

        SettingRow(title = "Автозапуск Борткомпьютера", subtitle = "Открывать при включении зажигания") {
            AmberSwitch(settings.autostartIBus, onAutostart)
        }
        RowDivider()
        SettingRow(title = "Выводить на передний план при запуске") {
            AmberSwitch(settings.bringLauncherToFront, onBringToFront)
        }
        RowDivider()
        SettingRow(title = "Тема оформления") {
            ThemeSegments(settings.themeMode, onThemeMode)
        }
        RowDivider()
        SettingRow(
            title = "Приложения по умолчанию",
            subtitle = "Музыка · Яндекс Музыка — Навигация · Яндекс Навигатор",
        ) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(28.dp))
        }
        RowDivider()
        SettingRow(
            title = "Лаунчер по умолчанию",
            subtitle = if (isDefaultLauncher) "Активен" else "Сейчас используется другой",
        ) {
            if (isDefaultLauncher) {
                Text("✓", color = c.accent, fontSize = TypeTokens.title, fontFamily = Inter)
            } else {
                AmberPill("Сделать основным", onSetDefault)
            }
        }
        RowDivider()
        // Read-only bus recorder. This exists because guessing a telegram from documentation put the
        // windows down on a parked car: the only trustworthy source is what the car itself emits
        // when its own button is pressed. Nothing is written to the bus while recording.
        SettingRow(
            title = "Записать шину",
            subtitle = when {
                !ibusConnected -> "Нет связи с I-Bus — проверьте адаптер"
                busCapturing -> "Идёт запись · кадров: $busFrames — нажмите нужную кнопку в машине"
                else -> "Для расшифровки команд: включить, нажать кнопку в машине, выключить"
            },
        ) {
            AmberPill(
                if (busCapturing) "Стоп" else "Записать",
                onClick = onToggleBusCapture,
                enabled = ibusConnected || busCapturing,   // «Стоп» must always be tappable while recording
            )
        }
        RowDivider()
        SettingRow(
            title = "Складывать зеркала",
            subtitle = "Складывать при выключении зажигания, раскладывать при включении",
        ) {
            AmberSwitch(settings.mirrorAutoFold, onMirrorAutoFold, enabled = ibusConnected)
        }
        RowDivider()
        SettingRow(
            title = "Пробник шины",
            subtitle = "Ручная отправка телеграмм в I-Bus — осторожно (окна/замки)",
        ) {
            AmberPill("Открыть", onClick = onOpenProbe, enabled = ibusConnected)
        }
        RowDivider()
        SettingRow(
            title = "VPN для YouTube",
            subtitle = if (vpnAppInstalled) {
                "Передать профиль в sing-box — останется подтвердить импорт"
            } else {
                "Приложение sing-box (SFA) не найдено на устройстве"
            },
        ) {
            AmberPill("Передать", onClick = onSendVpnProfile, enabled = vpnAppInstalled)
        }
        RowDivider()
        UpdateRow(currentVersion, hasRoot, updateState, onCheckUpdate, onInstallUpdate)
        RowDivider()
        DiagnosticsRow(logState, onSendLogs)
        RowDivider()

        Spacer(Modifier.height(12.dp))
        val cfg = androidx.compose.ui.platform.LocalConfiguration.current
        val dm = androidx.compose.ui.platform.LocalContext.current.resources.displayMetrics
        Text(
            "Экран: ${dm.widthPixels}×${dm.heightPixels} px · ${cfg.densityDpi} dpi · ${cfg.screenWidthDp}×${cfg.screenHeightDp} dp",
            color = c.textTertiary, fontSize = TypeTokens.caption, fontFamily = Inter,
        )
    }
}

/** Base 79dp-tall row: title (20sp) + optional subtitle (14sp) on the left, trailing control on the right. */
@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit,
) {
    val c = LocalLauncherColors.current
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 79.dp).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, color = c.text, fontSize = TypeTokens.title, fontWeight = FontWeight.Medium, fontFamily = Inter)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = c.textTertiary, fontSize = TypeTokens.caption, fontFamily = Inter)
            }
        }
        trailing()
    }
}

@Composable
private fun RowDivider() {
    val c = LocalLauncherColors.current
    HorizontalDivider(thickness = 1.dp, color = c.hairline)
}

@Composable
private fun AmberSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = LocalLauncherColors.current
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = c.accent,
            checkedThumbColor = Color.White,
            checkedBorderColor = c.accent,
            uncheckedThumbColor = c.textDim,
            uncheckedTrackColor = c.surfaceHi,
            uncheckedBorderColor = c.hairline,
        ),
    )
}

@Composable
private fun ThemeSegments(selected: ThemeMode, onThemeMode: (ThemeMode) -> Unit) {
    val c = LocalLauncherColors.current
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(c.surfaceHi).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(ThemeMode.DAY to "День", ThemeMode.NIGHT to "Ночь", ThemeMode.AUTO to "Авто").forEach { (mode, label) ->
            val active = selected == mode
            Box(
                Modifier
                    .width(96.dp)
                    .height(39.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .then(if (active) Modifier.background(c.accent.copy(alpha = 0.14f)) else Modifier)
                    .then(if (active) Modifier.border(1.dp, c.accent.copy(alpha = 0.5f), RoundedCornerShape(9.dp)) else Modifier)
                    .pressScale { onThemeMode(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) c.accent else c.textDim,
                    fontSize = TypeTokens.label,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    fontFamily = Inter,
                )
            }
        }
    }
}

@Composable
private fun AmberPill(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val c = LocalLauncherColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (enabled) c.accent else c.surfaceHi)
            .then(if (enabled) Modifier.pressScale(onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) c.background else c.textTertiary,
            fontSize = TypeTokens.label, fontWeight = FontWeight.SemiBold, fontFamily = Inter,
        )
    }
}

@Composable
private fun DiagnosticsRow(logState: LogSendState, onSendLogs: () -> Unit) {
    val label = when (logState) {
        LogSendState.Idle -> "Отправить"
        LogSendState.Sending -> "Отправка…"
        LogSendState.Sent -> "Отправлено ✓"
        LogSendState.Failed -> "Ошибка · ещё раз"
    }
    SettingRow(title = "Диагностика", subtitle = "Отправить логи разработчику") {
        // Ignore taps while a send is in flight.
        AmberPill(label, onClick = { if (logState != LogSendState.Sending) onSendLogs() })
    }
}

@Composable
private fun UpdateRow(
    currentVersion: String,
    hasRoot: Boolean,
    updateState: UpdateUiState,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    val c = LocalLauncherColors.current
    val available = updateState is UpdateUiState.Available
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 79.dp).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text("Обновление", color = c.text, fontSize = TypeTokens.title, fontWeight = FontWeight.Medium, fontFamily = Inter)
            Spacer(Modifier.height(4.dp))
            Text(
                "Версия $currentVersion · root: ${if (hasRoot) "да" else "нет"}",
                color = c.textTertiary, fontSize = TypeTokens.caption, fontFamily = Inter,
            )
            val (stateText, stateAccent) = when (val s = updateState) {
                UpdateUiState.Checking -> "Проверка…" to false
                UpdateUiState.UpToDate -> "Установлена последняя версия" to false
                is UpdateUiState.Available -> "Доступна ${s.versionName}: ${s.notes}" to true
                is UpdateUiState.Downloading -> "Скачивание ${s.percent}%" to false
                UpdateUiState.Installing -> "Установка…" to false
                is UpdateUiState.Failed -> "Ошибка: ${s.message}" to true
                UpdateUiState.Idle -> null to false
            }
            if (stateText != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    stateText,
                    color = if (stateAccent) c.accent else c.textTertiary,
                    fontSize = TypeTokens.caption, fontFamily = Inter,
                )
            }
        }
        AmberPill(
            if (available) "Обновить" else "Проверить",
            onClick = { if (available) onInstallUpdate() else onCheckUpdate() },
        )
    }
}

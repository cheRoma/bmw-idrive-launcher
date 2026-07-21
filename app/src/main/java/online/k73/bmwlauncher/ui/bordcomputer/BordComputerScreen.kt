package online.k73.bmwlauncher.ui.bordcomputer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.k73.bmwlauncher.car.BordData
import online.k73.bmwlauncher.car.IBusService
import online.k73.bmwlauncher.car.PdcStats
import online.k73.bmwlauncher.ui.theme.Inter
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors
import online.k73.bmwlauncher.ui.theme.pressScale

@Composable
fun BordComputerScreen(onBack: () -> Unit = {}) {
    val appCtx = LocalContext.current.applicationContext
    // Shared process-wide reader (also feeds the home status bar) — already connected, no start/stop here.
    val reader = remember { IBusService.get(appCtx) }
    val data by reader.data.collectAsState()
    val pdcOn by reader.pdcCapturing.collectAsState()
    val pdcStats by reader.pdcStats.collectAsState()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    // Hardware/gesture «Назад» also leaves the screen (this unit has a real Back key).
    BackHandler { onBack() }

    BordComputerContent(
        data = data,
        pdcOn = pdcOn,
        pdcStats = pdcStats,
        message = message,
        onBack = onBack,
        onResetTrip = {
            reader.resetTrip()
            message = "Средняя скорость сброшена"
        },
        // One-tap fix for the ">6 km/h gong": clears the OBC "LIMIT 6 KM/H" the removed OEM iBus app
        // latched into the cluster. Sends the exact clear telegram the OEM app used (see IBusReader).
        onClearLimit = {
            message = "Отправляю…"
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val ok = reader.clearSpeedLimit()
                message = if (ok) "Лимит сброшен — проверьте в поездке" else "Нет связи с I-Bus"
            }
        },
        onTogglePdc = { reader.setPdcCapture(!pdcOn) },
    )
}

/**
 * Stateless layout, so it can be rendered in a screenshot test at the unit's real geometry.
 *
 * That geometry is the whole reason this is laid out so tightly: the panel is 1280x720 at 240 dpi,
 * which leaves **853x432 dp** once the system bars are gone — and the column does not scroll. Every
 * element here is on a height budget; adding a row pushes the buttons off the bottom edge.
 */
@Composable
fun BordComputerContent(
    data: BordData,
    pdcOn: Boolean = false,
    pdcStats: PdcStats = PdcStats(),
    message: String? = null,
    onBack: () -> Unit = {},
    onResetTrip: () -> Unit = {},
    onClearLimit: () -> Unit = {},
    onTogglePdc: () -> Unit = {},
) {
    val c = LocalLauncherColors.current
    Column(Modifier.fillMaxSize().background(c.background).padding(horizontal = 40.dp, vertical = 20.dp)) {
        // Top bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Big, comfortable tap target (was 56×44 and rarely registered on this LCD).
            Box(Modifier.size(width = 76.dp, height = 56.dp).pressScale(onBack), contentAlignment = Alignment.CenterStart) {
                androidx.compose.material3.Text("‹", color = c.textDim, fontFamily = Inter, fontSize = 40.sp)
            }
            Spacer(Modifier.size(6.dp))
            androidx.compose.material3.Text("Борткомпьютер", color = c.text, fontFamily = Inter, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            StatusPill(data)
        }

        Spacer(Modifier.height(12.dp))

        if (!data.connected && data.speedKmh == null) {
            WaitState(c.textDim, data.connected)
        } else {
            // Hero speed
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                androidx.compose.material3.Text(
                    data.speedKmh?.toString() ?: "—",
                    color = c.text, fontFamily = Inter, fontSize = 88.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.size(10.dp))
                androidx.compose.material3.Text("км/ч", color = c.textDim, fontFamily = Inter, fontSize = 24.sp, modifier = Modifier.padding(bottom = 18.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatCell("Средняя", data.avgSpeedKmh?.let { "$it" } ?: "—", "км/ч", Modifier.weight(1f))
                StatCell("Обороты", data.rpm?.let { "$it" } ?: "—", "об/мин", Modifier.weight(1f))
                StatCell("Двигатель", data.coolantC?.let { "$it°" } ?: "—", "ОЖ", Modifier.weight(1f))
                StatCell("За бортом", data.outsideC?.let { "$it°" } ?: "—", "C", Modifier.weight(1f))
            }
        }

        Spacer(Modifier.weight(1f))

        // All three actions on ONE row: two rows of pills did not fit the 432 dp and the last one
        // was clipped by the bottom edge.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionPill("Сбросить среднюю", enabled = true, onClick = onResetTrip)
            ActionPill("Сбросить лимит", enabled = data.connected, onClick = onClearLimit)
            ActionPill(
                if (pdcOn) "● Идёт запись — стоп" else "Записать парктроник",
                enabled = data.connected,
                onClick = onTogglePdc,
                active = pdcOn,
            )
        }

        // One reserved line: either a result message, or the live capture verdict. Keeping the
        // counters here instead of in the pill keeps the row from outgrowing the screen width.
        val line = when {
            pdcOn && pdcStats.error != null -> "ошибка записи в шину: ${pdcStats.error}"
            pdcOn -> "запросов ${pdcStats.sent} · эхо ${pdcStats.echo} · ответов ${pdcStats.replies}"
            else -> message
        }
        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.Text(
            line ?: "",
            color = if (pdcOn && pdcStats.replies > 0) c.callGreen else c.textDim,
            fontFamily = Inter, fontSize = 13.sp,
        )
    }
}

/** Pill-shaped action button; [active] tints it while a capture is running. */
@Composable
private fun ActionPill(label: String, enabled: Boolean, onClick: () -> Unit, active: Boolean = false) {
    val c = LocalLauncherColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) c.accent.copy(alpha = 0.16f) else c.tile)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        androidx.compose.material3.Text(
            label,
            color = if (enabled) c.accent else c.textTertiary,
            fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusPill(data: BordData) {
    val c = LocalLauncherColors.current
    val on = data.connected
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(c.tile).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(999.dp)).background(if (on) c.callGreen else c.textTertiary))
        Spacer(Modifier.size(8.dp))
        androidx.compose.material3.Text(if (on) "на связи" else "нет адаптера", color = c.textDim, fontFamily = Inter, fontSize = 13.sp)
    }
}

@Composable
private fun StatCell(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    val c = LocalLauncherColors.current
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(c.tile).padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        androidx.compose.material3.Text(label.uppercase(), color = c.textTertiary, fontFamily = Inter, fontSize = 12.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            // 36sp, not 42: a four-digit rpm squeezed the unit label into three wrapped lines.
            androidx.compose.material3.Text(
                value, color = c.accent, fontFamily = Inter, fontSize = 36.sp,
                fontWeight = FontWeight.Bold, maxLines = 1,
            )
            Spacer(Modifier.size(6.dp))
            androidx.compose.material3.Text(
                unit, color = c.textDim, fontFamily = Inter, fontSize = 13.sp,
                maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun WaitState(dim: androidx.compose.ui.graphics.Color, connected: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text(
            if (connected) "Ожидание данных с I-Bus…" else "Подключите I-Bus адаптер к USB\nи разрешите доступ",
            color = dim, fontFamily = Inter, fontSize = 18.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

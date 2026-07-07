package online.k73.bmwlauncher.ui.bordcomputer

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.k73.bmwlauncher.car.BordData
import online.k73.bmwlauncher.car.IBusService
import online.k73.bmwlauncher.ui.theme.Inter
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors

@Composable
fun BordComputerScreen(onBack: () -> Unit = {}) {
    val c = LocalLauncherColors.current
    val appCtx = LocalContext.current.applicationContext
    // Shared process-wide reader (also feeds the home status bar) — already connected, no start/stop here.
    val reader = remember { IBusService.get(appCtx) }
    val data by reader.data.collectAsState()

    Column(Modifier.fillMaxSize().background(c.background).padding(horizontal = 40.dp, vertical = 24.dp)) {
        // Top bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 56.dp, height = 44.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack), contentAlignment = Alignment.CenterStart) {
                androidx.compose.material3.Text("‹", color = c.textDim, fontFamily = Inter, fontSize = 40.sp)
            }
            Spacer(Modifier.size(6.dp))
            androidx.compose.material3.Text("Борткомпьютер", color = c.text, fontFamily = Inter, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            StatusPill(data)
        }

        Spacer(Modifier.height(28.dp))

        if (!data.connected && data.speedKmh == null) {
            WaitState(c.textDim, data.connected)
        } else {
            // Hero speed
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                androidx.compose.material3.Text(
                    data.speedKmh?.toString() ?: "—",
                    color = c.text, fontFamily = Inter, fontSize = 120.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.size(10.dp))
                androidx.compose.material3.Text("км/ч", color = c.textDim, fontFamily = Inter, fontSize = 26.sp, modifier = Modifier.padding(bottom = 26.dp))
            }
            Spacer(Modifier.height(30.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                StatCell("Обороты", data.rpm?.let { "$it" } ?: "—", "об/мин", Modifier.weight(1f))
                StatCell("Двигатель", data.coolantC?.let { "$it°" } ?: "—", "ОЖ", Modifier.weight(1f))
                StatCell("За бортом", data.outsideC?.let { "$it°" } ?: "—", "C", Modifier.weight(1f))
            }
        }
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
        modifier.clip(RoundedCornerShape(18.dp)).background(c.tile).padding(20.dp),
    ) {
        androidx.compose.material3.Text(label.uppercase(), color = c.textTertiary, fontFamily = Inter, fontSize = 12.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            androidx.compose.material3.Text(value, color = c.accent, fontFamily = Inter, fontSize = 42.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(6.dp))
            androidx.compose.material3.Text(unit, color = c.textDim, fontFamily = Inter, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
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

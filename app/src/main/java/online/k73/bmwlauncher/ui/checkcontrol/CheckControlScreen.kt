package online.k73.bmwlauncher.ui.checkcontrol

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import online.k73.bmwlauncher.car.CheckControl
import online.k73.bmwlauncher.data.CcEntry
import online.k73.bmwlauncher.data.CheckControlStore
import online.k73.bmwlauncher.ui.theme.Inter
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors
import online.k73.bmwlauncher.ui.theme.pressScale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The warning the cluster is showing right now, over whatever launcher screen is open. Tapping it
 * hides this one message; the next one (or the same one coming back later) shows again.
 */
@Composable
fun CheckControlBanner(text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalLauncherColors.current
    val ru = CheckControl.translate(text)
    Row(
        modifier
            .widthIn(max = 640.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.surfaceHi)
            .border(1.5.dp, c.accent, RoundedCornerShape(18.dp))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⚠", color = c.accent, fontFamily = Inter, fontSize = 28.sp)
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(ru, color = c.text, fontFamily = Inter, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            if (ru != text) Text(text, color = c.textDim, fontFamily = Inter, fontSize = 13.sp, letterSpacing = 1.sp, maxLines = 1)
        }
        Spacer(Modifier.size(18.dp))
        Text("✕", color = c.textTertiary, fontFamily = Inter, fontSize = 18.sp)
    }
}

@Composable
fun CheckControlScreen(onBack: () -> Unit = {}) {
    val ctx = LocalContext.current.applicationContext
    val store = remember { CheckControlStore.get(ctx) }
    val history by store.history.collectAsState()
    val active by store.active.collectAsState()
    BackHandler { onBack() }
    CheckControlContent(history, active, onBack)
}

/** Stateless, for the screenshot test. [zone] is a parameter so the golden is stable. */
@Composable
fun CheckControlContent(
    history: List<CcEntry>,
    active: String?,
    onBack: () -> Unit = {},
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val c = LocalLauncherColors.current
    Column(Modifier.fillMaxSize().background(c.background).padding(horizontal = 40.dp, vertical = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 76.dp, height = 56.dp).pressScale(onBack), contentAlignment = Alignment.CenterStart) {
                Text("‹", color = c.textDim, fontFamily = Inter, fontSize = 40.sp)
            }
            Spacer(Modifier.size(6.dp))
            Text("Сообщения щитка", color = c.text, fontFamily = Inter, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                if (active != null) "сейчас горит: ${CheckControl.translate(active)}" else "сейчас предупреждений нет",
                color = if (active != null) c.accent else c.textDim, fontFamily = Inter, fontSize = 15.sp, maxLines = 1,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Щиток ещё ничего не показывал.", color = c.textDim, fontFamily = Inter, fontSize = 18.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) { EntryRow(it, zone) }
            }
        }
    }
}

private val whenFmt = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("ru"))

@Composable
private fun EntryRow(e: CcEntry, zone: ZoneId) {
    val c = LocalLauncherColors.current
    val ru = CheckControl.translate(e.text)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.tile).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            whenFmt.format(Instant.ofEpochMilli(e.atMs).atZone(zone)),
            color = c.textDim, fontFamily = Inter, fontSize = 15.sp,
            modifier = Modifier.size(width = 130.dp, height = 22.dp),
        )
        Text(ru, color = c.accent, fontFamily = Inter, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
        if (ru != e.text) Text(e.text, color = c.textTertiary, fontFamily = Inter, fontSize = 13.sp, maxLines = 1)
    }
}

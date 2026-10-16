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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * opens the full card; the cross hides this one message (the next one, or the same one coming back
 * later, shows again). With the dead bulb known, it says which one instead of the original text.
 */
@Composable
fun CheckControlBanner(
    text: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    faultyLamps: List<String> = emptyList(),
) {
    val c = LocalLauncherColors.current
    val ru = CheckControl.translate(text)
    val stop = online.k73.bmwlauncher.car.CheckControlGuide.severity(text) ==
        online.k73.bmwlauncher.car.CheckControlGuide.Severity.STOP
    val edge = if (stop) StopRed else c.accent
    Row(
        modifier
            .widthIn(max = 680.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.surfaceHi)
            .border(1.5.dp, edge, RoundedCornerShape(18.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f, fill = false).clickable(onClick = onOpen).padding(start = 22.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⚠", color = edge, fontFamily = Inter, fontSize = 28.sp)
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text(ru, color = c.text, fontFamily = Inter, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                val sub = if (faultyLamps.isNotEmpty()) "Не горит: " + faultyLamps.joinToString(", ") else text
                if (sub != ru) Text(sub, color = if (faultyLamps.isNotEmpty()) c.accent else c.textDim, fontFamily = Inter, fontSize = 14.sp, maxLines = 1)
            }
            Spacer(Modifier.size(14.dp))
            Text("подробнее ›", color = c.textDim, fontFamily = Inter, fontSize = 14.sp)
        }
        Box(Modifier.clickable(onClick = onDismiss).padding(horizontal = 18.dp, vertical = 18.dp)) {
            Text("✕", color = c.textTertiary, fontFamily = Inter, fontSize = 18.sp)
        }
    }
}

@Composable
fun CheckControlScreen(onBack: () -> Unit = {}) {
    val ctx = LocalContext.current.applicationContext
    val store = remember { CheckControlStore.get(ctx) }
    val history by store.history.collectAsState()
    val active by store.active.collectAsState()
    val lamps by store.faultyLamps.collectAsState()
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    BackHandler { onBack() }
    CheckControlContent(history, active, onBack, onOpen = { open = it })
    open?.let { text ->
        CheckControlCard(
            text,
            // Live bulb faults belong to what is lit now; an old entry gets the guide without them.
            faultyLamps = if (text == active) online.k73.bmwlauncher.car.CheckControlGuide.faultyLampsFor(text, lamps) else emptyList(),
            onClose = { open = null },
        )
    }
}

/** Stateless, for the screenshot test. [zone] is a parameter so the golden is stable. */
@Composable
fun CheckControlContent(
    history: List<CcEntry>,
    active: String?,
    onBack: () -> Unit = {},
    onOpen: (String) -> Unit = {},
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
                items(history) { EntryRow(it, zone, onOpen) }
            }
        }
    }
}

private val whenFmt = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("ru"))

@Composable
private fun EntryRow(e: CcEntry, zone: ZoneId, onOpen: (String) -> Unit) {
    val c = LocalLauncherColors.current
    val ru = CheckControl.translate(e.text)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.tile).clickable { onOpen(e.text) }
            .padding(horizontal = 18.dp, vertical = 12.dp),
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

private val StopRed = androidx.compose.ui.graphics.Color(0xFFE5484D)

/**
 * The whole story of one message, over the whole screen: how urgent, what it means, what to do,
 * and which bulb the light module reports dead. [faultyLamps] are already filtered to this message.
 */
@Composable
fun CheckControlCard(text: String, faultyLamps: List<String>, onClose: () -> Unit) {
    val c = LocalLauncherColors.current
    val ru = CheckControl.translate(text)
    val guide = online.k73.bmwlauncher.car.CheckControlGuide.forText(text)
    val sev = guide?.severity ?: online.k73.bmwlauncher.car.CheckControlGuide.Severity.INFO
    val (chip, chipColor) = when (sev) {
        online.k73.bmwlauncher.car.CheckControlGuide.Severity.STOP -> "Остановитесь" to StopRed
        online.k73.bmwlauncher.car.CheckControlGuide.Severity.SERVICE -> "Доехать до сервиса" to c.accent
        online.k73.bmwlauncher.car.CheckControlGuide.Severity.INFO -> "Можно ехать" to c.textDim
    }
    BackHandler { onClose() }
    Column(
        Modifier.fillMaxSize().background(c.background)
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 40.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(chipColor.copy(alpha = 0.18f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(chip.uppercase(), color = chipColor, fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }
            Spacer(Modifier.size(14.dp))
            Text(text, color = c.textTertiary, fontFamily = Inter, fontSize = 13.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(c.tile).clickable(onClick = onClose)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) {
                Text("Понятно", color = c.accent, fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(ru, color = if (sev == online.k73.bmwlauncher.car.CheckControlGuide.Severity.STOP) StopRed else c.text,
            fontFamily = Inter, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2)
        if (faultyLamps.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Не горит: " + faultyLamps.joinToString(", "), color = c.accent, fontFamily = Inter, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            online.k73.bmwlauncher.car.CheckControlGuide.hint(online.k73.bmwlauncher.car.Lamp.entries.filter { it.ru in faultyLamps })?.let {
                Text(it, color = c.accent, fontFamily = Inter, fontSize = 15.sp)
            }
        }
        if (guide != null) {
            Spacer(Modifier.height(10.dp))
            Text(guide.meaning, color = c.textDim, fontFamily = Inter, fontSize = 16.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                guide.stepsFor(faultyLamps).forEachIndexed { i, step ->
                    Row {
                        Text("${i + 1}", color = c.accent, fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.size(width = 24.dp, height = 22.dp))
                        Text(step, color = c.text, fontFamily = Inter, fontSize = 16.sp, lineHeight = 21.sp)
                    }
                }
            }
        }
    }
}

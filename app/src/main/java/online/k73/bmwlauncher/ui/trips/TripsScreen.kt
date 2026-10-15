package online.k73.bmwlauncher.ui.trips

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import online.k73.bmwlauncher.car.BordFormat
import online.k73.bmwlauncher.car.Trip
import online.k73.bmwlauncher.data.TripStore
import online.k73.bmwlauncher.ui.theme.Inter
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors
import online.k73.bmwlauncher.ui.theme.pressScale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TripsScreen(onBack: () -> Unit = {}) {
    val ctx = LocalContext.current.applicationContext
    val store = remember { TripStore.get(ctx) }
    val trips by store.trips.collectAsState()
    BackHandler { onBack() }
    TripsContent(trips, onBack)
}

/** Stateless, for the screenshot test. [zone]/[today] are parameters so the golden is stable. */
@Composable
fun TripsContent(
    trips: List<Trip>,
    onBack: () -> Unit = {},
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone),
) {
    val c = LocalLauncherColors.current
    Column(Modifier.fillMaxSize().background(c.background).padding(horizontal = 40.dp, vertical = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 76.dp, height = 56.dp).pressScale(onBack), contentAlignment = Alignment.CenterStart) {
                Text("‹", color = c.textDim, fontFamily = Inter, fontSize = 40.sp)
            }
            Spacer(Modifier.size(6.dp))
            Text("Поездки", color = c.text, fontFamily = Inter, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(summary(trips, zone, today), color = c.textDim, fontFamily = Inter, fontSize = 15.sp)
        }
        Spacer(Modifier.height(12.dp))
        if (trips.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Поездок пока нет. Первая запишется сама, как только машина поедет.", color = c.textDim, fontFamily = Inter, fontSize = 18.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(trips) { TripRow(it, zone) }
            }
        }
    }
}

private val dayFmt = DateTimeFormatter.ofPattern("d MMM, EE", Locale("ru"))
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

private fun summary(trips: List<Trip>, zone: ZoneId, today: LocalDate): String {
    fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val todayKm = trips.filter { day(it.startMs) == today }.sumOf { it.distanceKm }
    val weekKm = trips.filter { !day(it.startMs).isBefore(today.minusDays(6)) }.sumOf { it.distanceKm }
    return "сегодня ${BordFormat.km1(todayKm)} км · за 7 дней ${BordFormat.km1(weekKm)} км"
}

@Composable
private fun TripRow(t: Trip, zone: ZoneId) {
    val c = LocalLauncherColors.current
    val start = Instant.ofEpochMilli(t.startMs).atZone(zone)
    val end = Instant.ofEpochMilli(t.endMs).atZone(zone)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.tile).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(dayFmt.format(start), color = c.textDim, fontFamily = Inter, fontSize = 13.sp)
            Text("${timeFmt.format(start)} – ${timeFmt.format(end)}", color = c.text, fontFamily = Inter, fontSize = 18.sp)
        }
        Cell("${BordFormat.km1(t.distanceKm)} км", 110)
        Cell("${t.durationMin} мин", 90)
        Cell("${t.avgKmh} км/ч", 100)
        Cell(BordFormat.consumption(t.consumption)?.let { "$it л/100" } ?: "—", 120)
    }
}

@Composable
private fun Cell(text: String, widthDp: Int) {
    val c = LocalLauncherColors.current
    Text(
        text, color = c.accent, fontFamily = Inter, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
        maxLines = 1, modifier = Modifier.padding(start = 12.dp).size(width = widthDp.dp, height = 26.dp),
    )
}

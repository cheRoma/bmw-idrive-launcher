package online.k73.bmwlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors

/** iDrive-style top strip. [temp] is null → hidden until the Microntek broadcast is wired. */
@Composable
fun StatusRibbon(time: String, date: String, temp: String?, modifier: Modifier = Modifier) {
    val c = LocalLauncherColors.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(time, color = c.text, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Text(date, color = c.textDim, fontSize = 18.sp, modifier = Modifier.padding(start = 14.dp).weight(1f))
            if (temp != null) Text(temp, color = c.text, fontSize = 24.sp, modifier = Modifier.padding(end = 16.dp))
            RoundelIcon(Modifier.size(34.dp))
        }
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().height(2.dp).background(c.accent)
        )
    }
}

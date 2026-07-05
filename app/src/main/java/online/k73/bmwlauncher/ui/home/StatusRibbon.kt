package online.k73.bmwlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import online.k73.bmwlauncher.ui.theme.Inter
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors
import online.k73.bmwlauncher.ui.theme.TypeTokens

/** iDrive-style top strip (Claude Design). [temp] is null → hidden until the Microntek broadcast is wired. */
@Composable
fun StatusRibbon(time: String, date: String, temp: String?, modifier: Modifier = Modifier) {
    val c = LocalLauncherColors.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                time,
                color = c.text,
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = TypeTokens.clock,
            )
            // thin vertical hairline divider
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .size(width = 1.dp, height = 30.dp)
                    .background(c.hairline),
            )
            Text(
                date,
                color = c.textDim,
                fontFamily = Inter,
                fontSize = TypeTokens.body,
                modifier = Modifier.weight(1f),
            )
            if (temp != null) {
                Text(
                    temp,
                    color = c.text,
                    fontFamily = Inter,
                    fontSize = 17.sp,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
            RoundelIcon(Modifier.size(29.dp))
            Text(
                "X5",
                color = c.text,
                fontFamily = Inter,
                fontSize = TypeTokens.label,
                style = TextStyle(letterSpacing = 0.14.em),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        // amber gradient underline
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(c.accent.copy(alpha = 0.70f), c.accent.copy(alpha = 0.08f)),
                    ),
                ),
        )
    }
}

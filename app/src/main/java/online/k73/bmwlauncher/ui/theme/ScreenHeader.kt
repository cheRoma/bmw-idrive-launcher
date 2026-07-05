package online.k73.bmwlauncher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Shared screen header: back chevron (visual only — screens use system back) + title, with an
 * amber → transparent gradient divider underneath. Matches the Claude Design mockups (clock-less).
 */
@Composable
internal fun ScreenHeader(title: String, onBack: () -> Unit) {
    val c = LocalLauncherColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Comfortable 64dp touch target centered around the visual chevron.
            Box(
                Modifier.size(64.dp).pressScale(onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.ChevronLeft,
                    contentDescription = "Назад",
                    tint = c.textDim,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(Modifier.width(2.dp))
            Text(
                title,
                color = c.text,
                fontSize = TypeTokens.title,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Inter,
            )
        }
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Brush.horizontalGradient(listOf(c.accent, Color.Transparent))),
        )
    }
}

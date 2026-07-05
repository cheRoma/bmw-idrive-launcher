package online.k73.bmwlauncher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import online.k73.bmwlauncher.R

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Claude Design type scale (dp≈sp on this device). */
object TypeTokens {
    val display = 31.sp   // track title
    val clock   = 29.sp
    val title   = 20.sp   // focused tile / screen title
    val body    = 17.sp
    val label   = 16.sp
    val caption = 14.sp   // minimum
}

val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = TypeTokens.display),
    titleLarge   = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = TypeTokens.title),
    bodyLarge    = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = TypeTokens.body),
    labelLarge   = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = TypeTokens.label),
)

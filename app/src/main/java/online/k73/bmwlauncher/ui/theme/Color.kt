package online.k73.bmwlauncher.ui.theme

import androidx.compose.ui.graphics.Color

// BMW instrument-illumination amber (verified RGB 255,126,0)
val BmwAmber = Color(0xFFFF7E00)

// Night-calm palette (default). Pure black base so nothing lifts to grey on the car LCD;
// tiles are defined by a border, not a lighter fill (see TileCell).
val NightBackground = Color(0xFF000000)
val NightTile = Color(0xFF0E0E0E)
val NightText = Color(0xFFD0D0D0)
val NightTextDim = Color(0xFF8A8A8A)
val TileBorder = Color(0xFF2E2E2E)

// Day palette — still dark (premium night-calm look), only slightly lifted vs night so it
// is never a washed-out light theme on the car LCD.
val DayBackground = Color(0xFF0F0F0F)
val DayTile = Color(0xFF1C1C1C)
val DayText = Color(0xFFE6E6E6)
val DayTextDim = Color(0xFF9A9A9A)

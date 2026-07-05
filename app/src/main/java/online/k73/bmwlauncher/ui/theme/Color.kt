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

// ── Claude Design tokens (redesign) ─────────────────────────────────────────
// Night tokens (Claude Design)
val NightBgBase        = Color(0xFF0A0B0D)
val NightSurface       = Color(0xFF15181B)
val NightSurfaceHi     = Color(0xFF1E2226)
val NightTextPrimary   = Color(0xFFC7CCD2)
val NightTextSecondary = Color(0xFF8A9199)
val NightTextTertiary  = Color(0xFF6B7178)
val NightHairline      = Color(0x1AFFFFFF)
val CallGreen          = Color(0xFF1E7A3C)

// Day tokens (still dark, brighter)
val DayAmber           = Color(0xFFFF8A1A)
val DayBgBase          = Color(0xFF1C1F24)
val DaySurface         = Color(0xFF272C32)
val DaySurfaceHi       = Color(0xFF2E343B)
val DayTextPrimary     = Color(0xFFEDEFF2)
val DayTextSecondary   = Color(0xFFA6ADB5)
val DayTextTertiary    = Color(0xFF7E858D)
val DayHairline        = Color(0x29FFFFFF)

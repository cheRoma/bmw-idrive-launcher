# BMW Launcher — iDrive 3D Carousel Home Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the static 3×2 home grid with an iDrive-2016-style infinitely-looping 3D cylindrical tile carousel (centered card front + amber-glowing, side cards turned in perspective + dimmed), plus a premium status ribbon and breathing amber ambient — in our dark/amber palette.

**Architecture:** A `HorizontalPager` with a large modulo-looped page count renders each tile; a pure, unit-tested `CarouselGeometry.transformFor(pageOffset)` drives a per-page `graphicsLayer` (rotationY / scale / alpha / translation + cameraDistance) so the 3D "feel" is tunable without touching Compose. A `StatusRibbon` (time/date/temp/roundel) sits on top, an `AmbientGlow` breathes underneath. Tapping any tile routes through the existing `onTile(TileId)`.

**Tech Stack:** Kotlin, Jetpack Compose (`foundation.pager.HorizontalPager`, `graphicsLayer`, Canvas), material-icons-extended; tests JUnit4 + Paparazzi.

**Package:** `online.k73.bmwlauncher`
**Spec:** `docs/superpowers/specs/2026-07-05-bmw-launcher-idrive-carousel-design.md` · **Mockup:** `docs/mockups/idrive2016-home.png`
**Build env (every gradle call):** `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_SDK_ROOT=$HOME/android-sdk` then `cd /home/roma/Projects/bmw && ./gradlew <task> --no-daemon --console=plain`
**Device:** Qualcomm QCM6125, Android 13, 1280×720 @ 240 dpi. Current app v1.1.1 / versionCode 10.

---

## File Structure

```
app/src/main/java/online/k73/bmwlauncher/ui/home/
  CarouselGeometry.kt        pure: TileTransform + transformFor(pageOffset)   (TDD)
  RibbonClock.kt             pure: time "HH:mm" + Russian date                (TDD)
  RoundelIcon.kt             Canvas-drawn stylized BMW roundel
  StatusRibbon.kt            top strip: time/date/temp/roundel + amber divider
  AmbientGlow.kt             breathing amber radial glow (background)
  TileCard.kt                one carousel card (center vs side visual)
  HomeCarousel.kt            the looping 3D pager assembling the above
app/src/main/java/online/k73/bmwlauncher/ui/HomeActivity.kt   (MODIFY: home route → HomeCarousel)
app/src/test/java/online/k73/bmwlauncher/ui/home/            (unit tests)
app/src/test/java/online/k73/bmwlauncher/screenshot/         (Paparazzi)
```
Existing `Tile`, `TileId`, `defaultTiles`, `TileCell`, `HomeScreen` stay (HomeScreen is simply no longer used on the home route; keep it to avoid touching its Paparazzi golden).

---

### Task 1: `CarouselGeometry` (pure 3D transform math)

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/ui/home/CarouselGeometry.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/ui/home/CarouselGeometryTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package online.k73.bmwlauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselGeometryTest {
    @Test fun center_is_flat_full_scale_opaque() {
        val t = CarouselGeometry.transformFor(0f)
        assertEquals(0f, t.rotationYDeg, 0.001f)
        assertEquals(1f, t.scale, 0.001f)
        assertEquals(1f, t.alpha, 0.001f)
        assertEquals(0f, t.translationXFraction, 0.001f)
    }
    @Test fun neighbor_is_turned_smaller_dimmer() {
        val t = CarouselGeometry.transformFor(1f)
        assertEquals(-CarouselGeometry.MAX_ROTATION, t.rotationYDeg, 0.001f)
        assertEquals(CarouselGeometry.MIN_SCALE, t.scale, 0.001f)
        assertEquals(CarouselGeometry.MIN_ALPHA, t.alpha, 0.001f)
    }
    @Test fun symmetric_in_sign() {
        val a = CarouselGeometry.transformFor(0.5f)
        val b = CarouselGeometry.transformFor(-0.5f)
        assertEquals(a.scale, b.scale, 0.001f)
        assertEquals(a.alpha, b.alpha, 0.001f)
        assertEquals(a.rotationYDeg, -b.rotationYDeg, 0.001f)
        assertEquals(a.translationXFraction, -b.translationXFraction, 0.001f)
    }
    @Test fun scale_and_alpha_decrease_monotonically() {
        assertTrue(CarouselGeometry.transformFor(0.3f).scale > CarouselGeometry.transformFor(0.7f).scale)
        assertTrue(CarouselGeometry.transformFor(0.3f).alpha > CarouselGeometry.transformFor(0.7f).alpha)
    }
    @Test fun clamps_beyond_one_tile() {
        assertEquals(CarouselGeometry.transformFor(1f).scale, CarouselGeometry.transformFor(3f).scale, 0.001f)
        assertEquals(CarouselGeometry.transformFor(1f).rotationYDeg, CarouselGeometry.transformFor(3f).rotationYDeg, 0.001f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*CarouselGeometryTest*' --no-daemon --console=plain`
Expected: FAIL — unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package online.k73.bmwlauncher.ui.home

/** How a carousel card looks given its distance (in pages) from the settled center. */
data class TileTransform(
    val rotationYDeg: Float,
    val scale: Float,
    val alpha: Float,
    val translationXFraction: Float, // fraction of card width, pulls neighbours toward center
)

/**
 * Pure driver of the cylindrical 3D look. Tunable constants live here so the feel can be adjusted
 * (and re-shipped via OTA) without touching Compose. pageOffset 0 = centered; ±1 = one card away.
 */
object CarouselGeometry {
    const val MAX_ROTATION = 55f   // degrees at ±1 card
    const val MIN_SCALE = 0.72f    // side card scale
    const val MIN_ALPHA = 0.45f    // side card opacity
    const val TRANSLATION_PULL = 0.10f

    fun transformFor(pageOffset: Float): TileTransform {
        val clamped = pageOffset.coerceIn(-1f, 1f)
        val t = kotlin.math.abs(clamped)
        return TileTransform(
            rotationYDeg = -clamped * MAX_ROTATION,
            scale = 1f - t * (1f - MIN_SCALE),
            alpha = 1f - t * (1f - MIN_ALPHA),
            translationXFraction = -clamped * TRANSLATION_PULL,
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*CarouselGeometryTest*' --no-daemon --console=plain`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/ui/home/CarouselGeometry.kt app/src/test/java/online/k73/bmwlauncher/ui/home/CarouselGeometryTest.kt
git commit -m "feat(home): add pure CarouselGeometry 3D transform"
```

---

### Task 2: `RibbonClock` (time + Russian date)

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/ui/home/RibbonClock.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/ui/home/RibbonClockTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package online.k73.bmwlauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class RibbonClockTest {
    @Test fun time_is_hh_mm_zero_padded() {
        assertEquals("19:42", RibbonClock.time(LocalDateTime.of(2026, 7, 5, 19, 42)))
        assertEquals("07:05", RibbonClock.time(LocalDateTime.of(2026, 7, 5, 7, 5)))
    }
    @Test fun date_is_russian_short_dow_day_month() {
        // 2026-07-05 is a Sunday
        assertEquals("Вс, 5 июля", RibbonClock.date(LocalDateTime.of(2026, 7, 5, 0, 0)))
        // 2026-01-01 is a Thursday
        assertEquals("Чт, 1 января", RibbonClock.date(LocalDateTime.of(2026, 1, 1, 0, 0)))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*RibbonClockTest*' --no-daemon --console=plain`
Expected: FAIL — unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package online.k73.bmwlauncher.ui.home

import java.time.DayOfWeek
import java.time.LocalDateTime

object RibbonClock {
    private val DOW = mapOf(
        DayOfWeek.MONDAY to "Пн", DayOfWeek.TUESDAY to "Вт", DayOfWeek.WEDNESDAY to "Ср",
        DayOfWeek.THURSDAY to "Чт", DayOfWeek.FRIDAY to "Пт", DayOfWeek.SATURDAY to "Сб",
        DayOfWeek.SUNDAY to "Вс",
    )
    private val MONTHS = arrayOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )

    fun time(dt: LocalDateTime): String = "%02d:%02d".format(dt.hour, dt.minute)
    fun date(dt: LocalDateTime): String = "${DOW[dt.dayOfWeek]}, ${dt.dayOfMonth} ${MONTHS[dt.monthValue - 1]}"
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*RibbonClockTest*' --no-daemon --console=plain`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/ui/home/RibbonClock.kt app/src/test/java/online/k73/bmwlauncher/ui/home/RibbonClockTest.kt
git commit -m "feat(home): add RibbonClock time + Russian date"
```

---

### Task 3: `RoundelIcon` (Canvas-drawn BMW roundel)

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/ui/home/RoundelIcon.kt`
- Test: none (visual; appears in the home golden). Compile-only.

- [ ] **Step 1: Write the composable**

```kotlin
package online.k73.bmwlauncher.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color

/** A small, stylised BMW roundel drawn with Canvas (no image asset needed). */
@Composable
fun RoundelIcon(
    modifier: Modifier,
    ring: Color = Color(0xFF2E2E2E),
    light: Color = Color(0xFFBFC3C8),
    dark: Color = Color(0xFF0E0E0E),
) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        drawCircle(ring, radius = r)
        val inner = r * 0.80f
        drawCircle(dark, radius = inner)
        val box = Rect(center.x - inner, center.y - inner, center.x + inner, center.y + inner)
        // BMW quadrants: top-left and bottom-right light, the other two dark
        drawArc(light, startAngle = 180f, sweepAngle = 90f, useCenter = true,
            topLeft = box.topLeft, size = box.size)
        drawArc(light, startAngle = 0f, sweepAngle = 90f, useCenter = true,
            topLeft = box.topLeft, size = box.size)
        // thin ring separating quadrants from the outer ring
        drawCircle(ring, radius = inner, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.06f))
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :app:compileDebugKotlin --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/ui/home/RoundelIcon.kt
git commit -m "feat(home): add Canvas-drawn roundel icon"
```

---

### Task 4: `StatusRibbon` (top strip)

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/ui/home/StatusRibbon.kt`
- Test: none (visual; in home golden). Compile-only.

- [ ] **Step 1: Write the composable**

```kotlin
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
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :app:compileDebugKotlin --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/ui/home/StatusRibbon.kt
git commit -m "feat(home): add StatusRibbon"
```

---

### Task 5: `AmbientGlow` (breathing amber background)

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/ui/home/AmbientGlow.kt`
- Test: none (visual). Compile-only.

- [ ] **Step 1: Write the composable**

```kotlin
package online.k73.bmwlauncher.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import online.k73.bmwlauncher.ui.theme.BmwAmber

/** A slow breathing amber radial glow low on the screen — E53 instrument-backlight ambiance. */
@Composable
fun AmbientGlow(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "glow")
    val intensity by transition.animateFloat(
        initialValue = 0.10f, targetValue = 0.20f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse), label = "intensity",
    )
    Canvas(modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height * 1.05f)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(BmwAmber.copy(alpha = intensity), Color.Transparent),
                center = center, radius = size.width * 0.7f,
            )
        )
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :app:compileDebugKotlin --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/ui/home/AmbientGlow.kt
git commit -m "feat(home): add breathing AmbientGlow"
```

---

### Task 6: `TileCard` (one carousel card)

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/ui/home/TileCard.kt`
- Test: none (visual). Compile-only.

- [ ] **Step 1: Write the composable**

```kotlin
package online.k73.bmwlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors
import online.k73.bmwlauncher.ui.theme.TileBorder

/** A single carousel card. [center] switches to the amber highlighted state. */
@Composable
fun TileCard(tile: Tile, center: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalLauncherColors.current
    val accent = if (center) c.accent else TileBorder
    val content = if (center) c.accent else c.text
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(c.background)
            .border(if (center) 3.dp else 1.dp, accent, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(tile.icon, tile.label, tint = content, modifier = Modifier.height(if (center) 76.dp else 60.dp))
        Text(
            tile.label, color = content, fontSize = if (center) 24.sp else 20.sp,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            maxLines = 2, softWrap = true,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :app:compileDebugKotlin --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/ui/home/TileCard.kt
git commit -m "feat(home): add carousel TileCard"
```

---

### Task 7: `HomeCarousel` (looping 3D pager) + Paparazzi golden

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/ui/home/HomeCarousel.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/screenshot/HomeCarouselScreenshotTest.kt`
- Reference: `docs/mockups/idrive2016-home.png`

- [ ] **Step 1: Write `HomeCarousel.kt`**

```kotlin
package online.k73.bmwlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors
import java.time.LocalDateTime
import kotlin.math.absoluteValue

/**
 * Infinitely-looping 3D cylindrical tile carousel. Tap any tile to open it. The 3D look comes
 * entirely from CarouselGeometry, so it can be tuned without editing this file.
 */
@Composable
fun HomeCarousel(
    tiles: List<Tile> = defaultTiles,
    now: LocalDateTime,
    temp: String? = null,
    onTile: (TileId) -> Unit = {},
) {
    val c = LocalLauncherColors.current
    val loops = 1000
    val startPage = (loops / 2) * tiles.size
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { loops * tiles.size })

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(c.background)) {
        AmbientGlow()
        Column(Modifier.fillMaxSize()) {
            StatusRibbon(RibbonClock.time(now), RibbonClock.date(now), temp)
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val cardWidth = maxWidth * 0.30f
                val sidePadding = (maxWidth - cardWidth) / 2f
                HorizontalPager(
                    state = pagerState,
                    pageSize = PageSize.Fixed(cardWidth),
                    contentPadding = PaddingValues(horizontal = sidePadding),
                    modifier = Modifier.fillMaxWidth().height(maxHeight * 0.82f),
                ) { page ->
                    val tile = tiles[page % tiles.size]
                    val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val tf = CarouselGeometry.transformFor(offset)
                    TileCard(
                        tile = tile,
                        center = offset.absoluteValue < 0.5f,
                        modifier = Modifier
                            .width(cardWidth)
                            .fillMaxHeightSafe()
                            .graphicsLayer {
                                rotationY = tf.rotationYDeg
                                scaleX = tf.scale
                                scaleY = tf.scale
                                alpha = tf.alpha
                                cameraDistance = 16f * density
                                translationX = tf.translationXFraction * size.width
                            },
                        onClick = { onTile(tile.id) },
                    )
                }
            }
        }
    }
}

private fun Modifier.fillMaxHeightSafe() = this.then(Modifier.fillMaxSize())
```
Note: `pagerState.currentPage`/`currentPageOffsetFraction` are stable reads inside the page lambda; Compose recomposes pages as the pager scrolls, producing the live 3D turn. `PageSize.Fixed(cardWidth)` + symmetric `contentPadding` centers the settled card.

- [ ] **Step 2: Write the Paparazzi test (static settled frame)**

```kotlin
package online.k73.bmwlauncher.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import online.k73.bmwlauncher.ui.home.HomeCarousel
import online.k73.bmwlauncher.ui.theme.BmwLauncherTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

class HomeCarouselScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = 1280, screenHeight = 720,
            orientation = ScreenOrientation.LANDSCAPE, density = Density.MEDIUM,
        ),
    )

    @Test fun carousel_home() {
        paparazzi.snapshot {
            BmwLauncherTheme(isNight = true) {
                HomeCarousel(now = LocalDateTime.of(2026, 7, 5, 19, 42), temp = "+18°")
            }
        }
    }
}
```

- [ ] **Step 3: Record + Read-verify the golden**

Run: `./gradlew :app:recordPaparazziDebug --tests '*HomeCarouselScreenshotTest*' --no-daemon --console=plain`
Then Read `app/src/test/snapshots/images/online.k73.bmwlauncher.screenshot_HomeCarouselScreenshotTest_carousel_home.png` and confirm vs `docs/mockups/idrive2016-home.png`: a centered card with an **amber border + amber icon/label**, side cards **turned in perspective + dimmed grey**, edges partially cut, a top ribbon with **19:42 / Пт, 5 июля / +18° / roundel** and an amber divider, and a warm amber glow low on screen. If the 3D turn isn't visible in the settled frame (center card is flat by design — neighbours should be turned) or something overflows, adjust and re-record.
Then: `./gradlew :app:verifyPaparazziDebug --tests '*HomeCarouselScreenshotTest*' --no-daemon --console=plain` → PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/ui/home/HomeCarousel.kt app/src/test/java/online/k73/bmwlauncher/screenshot/HomeCarouselScreenshotTest.kt app/src/test/snapshots/
git commit -m "feat(home): add looping 3D HomeCarousel with Paparazzi golden"
```

---

### Task 8: Wire into `HomeActivity`, full build + release

**Files:**
- Modify: `app/src/main/java/online/k73/bmwlauncher/ui/HomeActivity.kt`

- [ ] **Step 1: Swap the home route to `HomeCarousel`**

Add import:
```kotlin
import online.k73.bmwlauncher.ui.home.HomeCarousel
```
In `setContent`, the `now` time state already exists (`var now by remember { mutableStateOf(LocalTime.now()) }` drives the theme ticker). Add a full `LocalDateTime` for the ribbon — reuse the same minute ticker by deriving it, or add `var nowDateTime by remember { mutableStateOf(java.time.LocalDateTime.now()) }` updated in the existing `LaunchedEffect` loop. Then replace the `composable("home") { HomeScreen(onTile = { ... }) }` block's **content** with `HomeCarousel`, keeping the exact same `onTile` routing lambda:
```kotlin
                    composable("home") {
                        HomeCarousel(
                            now = nowDateTime,
                            temp = null, // wired to the Microntek broadcast later
                            onTile = { id ->
                                when (id) {
                                    TileId.MUSIC -> nav.navigate("music")
                                    TileId.APPS -> nav.navigate("apps")
                                    TileId.SETTINGS -> nav.navigate("settings")
                                    TileId.NAV -> launcher.launch(settings.navPackage)
                                    TileId.IBUS -> launcher.launch(settings.iBusPackage)
                                    TileId.CARPLAY -> launcher.launch(settings.carplayPackage)
                                }
                            },
                        )
                    }
```
And update the existing minute ticker `LaunchedEffect` so it also refreshes `nowDateTime`:
```kotlin
            var nowDateTime by remember { mutableStateOf(java.time.LocalDateTime.now()) }
            LaunchedEffect(Unit) {
                while (true) {
                    now = LocalTime.now()
                    nowDateTime = java.time.LocalDateTime.now()
                    kotlinx.coroutines.delay(60_000)
                }
            }
```
(Do not remove the existing `now`/theme logic — just add `nowDateTime` alongside it.)

- [ ] **Step 2: Full clean test + Paparazzi + release APK**

Run: `./gradlew clean :app:testDebugUnitTest :app:verifyPaparazziDebug :app:assembleRelease --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`; all unit tests pass; all goldens (Home, Settings, Music×2, Carousel) verify; signed release APK produced.

- [ ] **Step 3: Commit, bump version, release**

```bash
cd /home/roma/Projects/bmw
git add app/src/main/java/online/k73/bmwlauncher/ui/HomeActivity.kt
git commit -m "feat(home): use iDrive 3D carousel on the home route"
sed -i 's/versionCode = 10/versionCode = 11/; s/versionName = "1.1.1"/versionName = "1.2.0"/' app/build.gradle.kts
git commit -am "chore: release v1.2.0 (iDrive 3D carousel home)"
./scripts/release.sh "Главный экран: 3D-карусель плиток в стиле iDrive"
```
Expected: prints `Published versionCode=11 versionName=1.2.0`.

- [ ] **Step 4: On-device tuning checklist (Roma tests, iterate)**

Update via the in-app button. On the head unit, judge: does the carousel swipe smoothly and snap? is the 3D turn tasteful? is the glow subtle enough at night? Report so the coordinator tunes `CarouselGeometry` constants (`MAX_ROTATION`, `MIN_SCALE`, `MIN_ALPHA`, `TRANSLATION_PULL`), `cardWidth` fraction, `cameraDistance`, and glow intensity and re-ships via OTA. (Outside-temp wiring is a separate follow-up once the Microntek broadcast is found.)

---

## Self-Review (spec coverage)

- Looping 3D carousel (spec §2, §3) → Tasks 1 (geometry), 6 (card), 7 (pager+graphicsLayer). ✓
- Center highlight amber / sides dimmed & turned (spec §2) → Tasks 1, 6. ✓
- Snap + tap-any-tile-opens routing (spec §2, §4) → Task 7 (pager fling default) + Task 8 (onTile). ✓
- Extensible tile list (spec §2) → `HomeCarousel(tiles = defaultTiles)` data-driven. ✓
- Status ribbon time/date/temp/roundel + amber divider (spec §3) → Tasks 2, 3, 4, 8 (nowDateTime). ✓
- Breathing amber ambient + reflection (spec §3) → Task 5 (glow). *(Card reflection deferred — the bottom glow gives the ambiance; add a mirrored card later if wanted; noted.)*
- Colors (spec §2) → reuses palette/`TileBorder`/`BmwAmber`. ✓
- Outside temp from Microntek broadcast, hidden until wired (spec §3, §7) → `temp = null` passthrough; ribbon hides when null. ✓
- Testing: geometry + clock unit-tested (Tasks 1,2); carousel Paparazzi golden (Task 7); 3D/perf device-verified (Task 8). ✓

**Type consistency:** `TileTransform(rotationYDeg,scale,alpha,translationXFraction)`, `CarouselGeometry.transformFor` + constants, `RibbonClock.{time,date}`, `RoundelIcon`, `StatusRibbon(time,date,temp)`, `AmbientGlow`, `TileCard(tile,center,onClick)`, `HomeCarousel(tiles,now,temp,onTile)`, existing `Tile`/`TileId`/`defaultTiles`/`LocalLauncherColors`/`TileBorder`/`BmwAmber` — consistent across Tasks 1–8. ✓

**Deferred note:** the mirrored-card floor reflection from the mockup is approximated by the bottom AmbientGlow for v1; a true reflection can be added later if Roma wants it. Flagged, not silently dropped.

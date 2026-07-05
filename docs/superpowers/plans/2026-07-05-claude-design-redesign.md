# BMW Launcher — Claude Design Redesign (cherry-pick, no new screens)

**Goal:** Rebuild the launcher's look to the Claude Design handoff — filled graphite icon-centric carousel cards (fixing "карточки выглядят ужасно"), exact tokens/typography, triple pressed-feedback, refined status ribbon, and the envelope+vinyl Music screen. **No new Phone/Bluetooth screens** (Roma's scope decision).

**References (in repo):** `docs/mockups/claude-design/` — `screen_2a.png` (Home), `screen_3a.png` (Music), `screen_1c.png` (Apps), `screen_1d.png` (Settings), `screen_2e.png` (Day), `screen_1j.png` (component sizes), `HANDOFF.md` (full token spec). Implementers MUST Read the relevant screen PNG and match it.

**Build env (every gradle call):** `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_SDK_ROOT=$HOME/android-sdk` then `cd /home/roma/Projects/bmw && ./gradlew <task> --no-daemon --console=plain`.
**Device:** 1280×720 @ 240 dpi (853×480 dp, density 1.5). Paparazzi goldens use MEDIUM/mdpi so 1280×720 px == dp (existing convention).
**Package:** `online.k73.bmwlauncher`. Current: v1.2.2 / versionCode 13.

## Design tokens (from HANDOFF.md — authoritative)
NightColors: bgBase `#141619`, surface `#1F2329`, surfaceHi `#262B31`, textPrimary `#C7CCD2`, textSecondary `#8A9199`, textTertiary `#6B7178`, accentAmber `#FF7E00`, hairline `#1AFFFFFF`, callGreen `#1E7A3C`.
DayColors (also dark, brighter): bgBase `#1C1F24`, surface `#272C32`, surfaceHi `#2E343B`, textPrimary `#EDEFF2`, textSecondary `#A6ADB5`, accentAmber `#FF8A1A`, hairline `#29FFFFFF`. Day = higher contrast, NO ambient glow/glow-shadows.
Font: **Archivo** (400/500/600/700), tabular digits (`tnum`). Type sizes (dp): display 31, clock 29, title 20, body 17, label 16, caption 14 (min).
Carousel (screen_1j): focus tile **203dp** (radius 18dp = 16+2), side **160dp**, rotY **±26/38/48°**, icon **58dp** focus / **43dp** side, label **20dp**/**15dp**, gap between centres **179dp**, glow `0 0 43dp rgba(255,126,0,0.22)`, row opacity **1 / .55 / .32 / .16**.
States (triple pressed signal, ALL taps): scale 0.97 (in 90ms easeOut, out 220ms spring(0.6,500)) + border amber 1→2dp + fill surface→surfaceHi. Focused: amber 2dp + glow (night) / amber 2.5dp no glow (day). Disabled: alpha 0.45.

---

## Phase A — Foundation

### Task 1: Design tokens + Archivo font + type scale
**Files:** `ui/theme/Color.kt`, `ui/theme/Theme.kt`, new `ui/theme/Type.kt`, `app/src/main/res/font/*` (+ `font/archivo.xml` family), fetch Archivo TTFs.
- Add all night+day token colors to `Color.kt` (names: `NightBgBase, NightSurface, NightSurfaceHi, NightTextPrimary, NightTextSecondary, NightTextTertiary, NightHairline, CallGreen`, and Day equivalents; keep `BmwAmber`, add `DayAmber = Color(0xFFFF8A1A)`).
- Extend `LauncherColors` to a superset while keeping existing field names working: `background`(=bgBase), `tile`(=surface), `surfaceHi`(new), `text`(=textPrimary), `textDim`(=textSecondary), `textTertiary`(new), `accent`, `hairline`(new), `callGreen`(new). Update `BmwLauncherTheme` night/day to the new values. Update `LocalLauncherColors` default.
- Add Archivo font: download the four weights from Google Fonts (github.com/google/fonts, OFL) into `res/font/archivo_regular.ttf`,`_medium`(500),`_semibold`(600),`_bold`(700); create `res/font/archivo.xml` font family; add a `ui/theme/Type.kt` exposing `Archivo` FontFamily + a `TypeTokens` object (displaySp=31, clockSp=29, titleSp=20, bodySp=17, labelSp=16, captionSp=14) and a Material3 `Typography` built on Archivo. Wire `typography` in `BmwLauncherTheme`.
- **Acceptance:** `./gradlew :app:compileDebugKotlin` green; existing Paparazzi goldens will shift (colors/font) — DEFER re-recording to each screen's task, EXCEPT record here only if a golden won't compile. Prefer: after Task 1, run `:app:recordPaparazziDebug` for ALL and Read each to confirm nothing is broken (just restyled), then `verifyPaparazziDebug`.
- Commit `feat(theme): Claude Design tokens + Archivo type scale`.

### Task 2: `Pressable` modifier (triple pressed signal)
**Files:** new `ui/theme/Pressable.kt`, test `ui/theme/PressableStateTest.kt` (pure state calc if any; else compile-only).
- `Modifier.pressable(shape, focused: Boolean = false, onClick)` — uses `MutableInteractionSource`; animates scale 1→0.97 (press.in 90ms, press.out spring), draws border amber (1dp idle→2dp pressed; focused=2dp) and background surface→surfaceHi. Expose also a lighter `Modifier.pressScale(onClick)` for icon-only buttons (scale + no fill). No ripple.
- **Acceptance:** compiles; used by later tasks.
- Commit `feat(theme): pressable triple-feedback modifier`.

---

## Phase B — Home (priority: the cards)

### Task 3: `RibbonClock` full date + `StatusRibbon` redesign
**Files:** `ui/home/RibbonClock.kt` (+test), `ui/home/StatusRibbon.kt`, `ui/home/RoundelIcon.kt` (reuse as neutral quadrant emblem).
- RibbonClock.date → **full lowercase weekday**: «суббота, 5 июля» (was «Вс, 5 июля»). TDD: update `RibbonClockTest` expectations (2026-07-05 → "суббота, 5 июля"; 2026-01-01 → "четверг, 1 января"). Keep `time` HH:mm.
- StatusRibbon per screen_2a: clock 29dp semibold (tabular) · 1px hairline vertical divider · full date 17dp textSecondary · temp 17dp (hidden if null) · right: neutral quadrant emblem (RoundelIcon) 29dp + «X5» label 16dp letterSpacing 0.14em. Under the ribbon an amber line 2dp `linear-gradient(90deg, amber 70% → amber 8%)` (use `Brush.horizontalGradient`).
- **Acceptance:** RibbonClockTest passes; StatusRibbon compiles; appears correctly in the Task 7 home golden.
- Commit `feat(home): full-date ribbon + Claude Design status strip`.

### Task 4: `CarouselGeometry` refit to handoff stops (TDD)
**Files:** `ui/home/CarouselGeometry.kt` (+ rewrite `CarouselGeometryTest`).
- Replace the linear model with the handoff's discrete stops, interpolated for fractional offsets. At |offset| = 0,1,2,3+: scale = 203/203, 160/203, 140/203, 127/203; rotationYDeg = 0, 26, 38, 48 (sign = -offset direction, same convention as now); alpha = 1, .55, .32, .16. `translationX` no longer needed as a pull (gap handled by pager layout) — keep field but 0, or repurpose; simplest: drop pull (return 0f) and let the pager's fixed page width + centre spacing create the 179dp centre gap.
- TDD: rewrite tests — center flat/opaque/scale1; |1| → rot 26, scale ~0.788, alpha .55; symmetry in sign; monotonic decrease; clamp at 3 (offset 4 == offset 3). Keep it a pure function returning `TileTransform`.
- **Acceptance:** new tests pass.
- Commit `feat(home): carousel geometry to Claude Design stops`.

### Task 5: `TileCard` redesign — filled, icon-dominant, pressable
**Files:** `ui/home/TileCard.kt`. Reference: `screen_1j.png` closeup + `screen_2a.png`.
- Card = **filled `surfaceHi`**, radius 18dp, size driven by parent (203dp focus / 160dp side via graphicsLayer scale). Content: **icon dominant** (focus 58dp amber stroke-1.6; side 43dp textSecondary), label BELOW, secondary (focus 20dp textSecondary; side 15dp textTertiary). Focus adds amber 2dp border + amber glow (0 0 43dp amber22% — draw via a blurred shadow/`drawBehind` radialGradient or `Modifier.shadow` amber). Use `pressable` for tap feedback. NO more outlined-on-black look.
- Icons: line-style per tile (music note, navigation arrow, phone, bluetooth, gauge=борткомпьютер, grid=apps, sliders=settings) — from material-icons or the handoff SVGs; keep existing TileId set (MUSIC, NAV, APPS, IBUS, SETTINGS, CARPLAY).
- **Acceptance:** compiles; looks like the 1j closeup (verified in Task 7 golden).
- Commit `feat(home): filled icon-centric TileCard with pressed feedback`.

### Task 6: Indicator strips
**Files:** new `ui/home/PageIndicator.kt`.
- A row of strips (11×3dp), count = number of tiles, active = wider (21dp) amber, others textTertiary. Driven by `pagerState.currentPage % tiles.size`.
- Commit `feat(home): carousel page indicator strips`.

### Task 7: `HomeCarousel` assembly + golden vs screen_2a
**Files:** `ui/home/HomeCarousel.kt`, screenshot test.
- Reassemble: ambient glow (night only) behind, StatusRibbon on top, the pager using the new geometry (fixed page width sized so centres are ~179dp apart, focus card 203dp), TileCard, PageIndicator at the bottom. Keep infinite-loop paging + `onTile` routing.
- Record golden, **Read it and compare to `docs/mockups/claude-design/screen_2a.png`**: filled graphite cards, big amber focus icon, receding dimmed neighbours, ribbon with clock/date/temp/X5 emblem + amber divider, indicator strips, ambient glow. Iterate until it matches. Then verify.
- Commit `feat(home): assemble Claude Design carousel`.

---

## Phase C — remaining screens

### Task 8: Music redesign — envelope + sliding vinyl. Reference: `screen_3a.png` + HANDOFF §2.
Envelope 287dp (dark spine 9dp), vinyl Ø269dp behind sliding out on play (label Ø85dp amber, grooves via repeating-radial-gradient, spins while playing), prev-envelope peek (swipe=prev/next), «● Яндекс Музыка · Моя волна», right column: track 32dp semibold + artist 20dp, progress with 19dp knob (keep tap+drag seek from v1.2.1), transport prev/next 64dp circles + play/pause 85dp surfaceHi amber ring, «В любимом»/«Плейлисты» pill buttons 51dp. Keep the MediaController plumbing + seek/like behavior; this is a VISUAL rebuild of `MusicScreen.kt` (envelope/vinyl are new composables). Re-record Music golden vs screen_3a. Keep «Плейлисты» opening Yandex for now (deferred).
Commit `feat(music): envelope + vinyl now-playing redesign`.

### Task 9: Apps redesign. Reference: `screen_1c.png`.
Header (back chevron + «Приложения» 20dp + clock, amber divider). Grid 4×2 filled `surface` tiles ~197×172dp radius 16dp: icon container 61dp surfaceHi + real app icon + name 16dp. Last tile «Перезагрузка» = dashed amber border + power icon (keep tap→confirm reboot). Pressable. Re-record golden.
Commit `feat(apps): Claude Design app grid`.

### Task 10: Settings redesign. Reference: `screen_1d.png`.
Header + amber divider. Rows 79dp, hairline dividers: switch rows (amber track, 28dp thumb); «Тема оформления» = segment control День/Ночь/Авто (active: amber text + amber14% bg + amber 50% border); «Приложения по умолчанию» row + chevron + subtitle; keep the v1.2.2 «Лаунчер по умолчанию» row + Обновление row (restyle to match). Re-record golden.
Commit `feat(settings): Claude Design settings list`.

### Task 11: Day theme pass. Reference: `screen_2e.png`.
Verify Day palette (DayColors) renders as a distinct brighter-but-dark theme; disable ambient glow + tile glow in day. Add a Paparazzi golden for day Home. Commit `feat(theme): day-mode pass`.

---

## Release
After Phase B (Home) is solid: bump versionCode 13→14, versionName 1.2.2→**1.3.0**, `./scripts/release.sh "Редизайн: заполненные плитки-иконки, статус-лента, отклик на нажатия"`, merge to master. Phase C can ship as 1.3.x increments. Update memory each release.

## Notes / risks
- The handoff bgBase `#141619` is NOT pure black — this reverses the earlier "pure black so the LCD doesn't lift dark fills to grey" decision. Roma explicitly chose this design; validate on the real LCD and tune surface values if they lift.
- Archivo must be OFL and embedded (allowed). If download fails, fall back to the bundled sans and note it.
- Keep every screen's MediaController/settings/launch plumbing intact — this is a visual redesign, not a behavior change (except RibbonClock date format and carousel geometry).

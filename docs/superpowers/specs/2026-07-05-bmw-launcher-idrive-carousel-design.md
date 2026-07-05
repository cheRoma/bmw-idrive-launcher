# BMW Launcher — iDrive 3D carousel home · Design/Spec

**Date:** 2026-07-05
**Status:** approved (mockup + clarifications) — ready for writing-plans
**Feature owner:** Roma

---

## 1. Goal

Replace the static 3×2 tile grid home screen with an **iDrive-2016-style 3D cylindrical carousel**
of app tiles ("cards going around me in a circle"), in our dark + amber palette, giving the launcher
a premium BMW "изюминка". Approved via Higgsfield mockup
(`docs/mockups/idrive2016-home.png` — the amber horizontal-ribbon version).

## 2. Locked decisions

- **Layout:** a horizontal, **infinitely-looping** carousel of tiles rendered with a **cylindrical 3D
  effect** — the centered tile is front-facing, full size, with an amber glow ring + amber icon/label;
  tiles to the sides are rotated in perspective (receding), scaled down, and dimmed grey; tiles are
  partially visible at both screen edges to invite swiping.
- **Interaction:** swipe/drag rotates the carousel and it **snaps** so one tile sits centered; **tapping
  ANY tile opens it immediately** (not just the centered one). Same routing as today (internal screens
  vs external app launch).
- **Tiles:** the existing six for now — Музыка, Навигация, Приложения, Борткомпьютер, Настройки, CarPlay —
  rendered from a **data-driven list** so more can be added later (user tile-config UI is a later feature).
- **Status ribbon** (top): large time + date on the left; outside temperature + a small BMW roundel on
  the right; a thin amber divider under it.
- **Ambient:** a subtle amber glow low on the screen + a soft floor reflection of the tiles; the glow
  **breathes** (slow, low-intensity — must not distract at night).
- **Motion:** carousel snap + center-focus scale/glow; a brief amber "illumination" flash on tap; keep
  transitions into inner screens smooth.
- **Colors:** our existing palette — background `#000000`, accent BMW amber `#FF7E00`, grey borders/text.

## 3. Components

- **HomeCarousel** (Compose) — a `HorizontalPager` with a very large page count (looping via modulo over
  the tile list) so it wraps "around". Each page applies a `graphicsLayer` computed from its offset from
  the settled center: `rotationY` (perspective turn), `scaleX/Y` (center bigger), `alpha`/tint (sides
  dimmer), `cameraDistance` for depth. Snap via the pager's default fling + `snapPosition`. Center index
  derived from `pagerState.currentPage % tiles.size`.
- **CarouselGeometry** (pure) — `fun transformFor(pageOffset: Float): TileTransform` returning
  `rotationYDeg`, `scale`, `alpha`, `translationXFraction` as a function of distance-from-center. This is
  the tunable, unit-tested heart of the 3D look (so the feel can be adjusted without touching Compose).
- **TileCard** (Compose) — one card: monoline icon + label; centered state (amber border+glow+icon)
  vs side state (grey). Reuses `Tile`/`TileId` from the existing home.
- **StatusRibbon** (Compose) — time (updates each minute), date, outside temp, roundel, amber divider.
- **AmbientGlow** (Compose) — a slow breathing amber radial gradient at the bottom + a mirrored, blurred,
  low-alpha reflection of the centered card.
- **OutsideTemp** (source) — read the head unit's outside temperature. The Microntek ROM broadcasts it;
  the exact action/extra is **to be discovered on-device via the remote tunnel** (Roma's unit exposes
  "+18°"-style temp in its own status bar). Until wired, the ribbon shows time/date only (temp hidden).
- **HomeActivity** — replace `HomeScreen` (3×2 grid) usage with the carousel; keep the existing tile→route
  wiring (`onTile`). The old `HomeScreen`/`TileCell` may be kept or removed; the carousel supersedes them
  on the home route.

## 4. Data flow

```
HomeActivity → HomeCarousel(tiles = defaultTiles, onTile)
  pager offset ─per page→ CarouselGeometry.transformFor(offset) ─→ graphicsLayer on TileCard
  tap tile ─→ onTile(id) ─→ (internal nav | AppLauncher.launch)   [unchanged routing]
StatusRibbon ← current time (per-minute tick) + OutsideTemp (Microntek broadcast, when wired)
AmbientGlow  ← slow infinite breathing animation
```

## 5. Testing

- **Unit (headless):** `CarouselGeometry.transformFor` — center (offset 0) → rotationY 0, scale max,
  alpha 1; growing offset → rotationY increases toward a cap, scale/alpha decrease monotonically,
  symmetric for ±offset. Time/date formatting for the ribbon.
- **Paparazzi:** a static frame of the carousel (centered tile + neighbours) + the status ribbon, vs the
  mockup. (Paparazzi captures one frame; the 3D transforms render in that frame.)
- **Device-verified (Roma tests, iterate via OTA):** the 3D perspective feel, snapping, fling physics,
  breathing-glow intensity, and **performance/smoothness on the head unit GPU** (Qualcomm QCM6125). The
  geometry constants live in `CarouselGeometry` so tuning is a small, quick change.

## 6. Out of scope (YAGNI, later)

User tile add/remove/reorder UI; per-tile custom icons; boot/welcome animation; the ambient as a
full-screen video. Outside-temp wiring is in-scope but gated on discovering the Microntek broadcast.

## 7. Open items

1. **Outside-temp broadcast** — find the Microntek action/extra on-device (remote tunnel). Hide temp until then.
2. **3D + performance tuning** — first build ships reasonable defaults; Roma tests on the car and we tune
   `CarouselGeometry` constants (rotation cap, scale range, camera distance) + fling for the right feel.

# BMW iDrive Launcher

**English** · [Русский](README.ru.md)

**A custom Android home-screen launcher that turns a cheap aftermarket head unit into a premium, BMW-iDrive-style car interface.**

Built for a **2005 BMW X5 (E53)** running an **XTRONS** Android head unit, this launcher replaces the sluggish factory desktop with a fast, touch-first UI inspired by modern **iDrive**: a 3D tile carousel over a **live map background**, an instrument-amber accent on near-black graphite, a now-playing screen wired to **Yandex Music**, and a **from-scratch on-board computer that reads the car's BMW I-Bus directly over USB** — plus in-app OTA updates and one-tap reboot. All in **Kotlin + Jetpack Compose**, screenshot-tested, no root required.

<p align="center">
  <img src="screenshots/home.png" alt="Home carousel" width="88%">
</p>

---

## Table of contents

- [Why](#why)
- [Screenshots](#screenshots)
- [Features](#features)
- [The car & the head unit](#the-car--the-head-unit)
- [Design system](#design-system)
- [Architecture](#architecture)
- [Project structure](#project-structure)
- [Build & run](#build--run)
- [Testing](#testing)
- [Over-the-air updates](#over-the-air-updates)
- [Version history](#version-history)
- [Roadmap](#roadmap)
- [Known limitations](#known-limitations)
- [Tech stack](#tech-stack)
- [Credits & license](#credits--license)

---

## Why

The factory launcher shipped on these XTRONS units (an abandoned third-party "iDrive Launcher") is laggy and unmaintained.

This started as a from-scratch replacement whose main job was to **autostart a proprietary third-party i-Bus app** (the car's trip computer) and get out of the way. It has since grown into a full stack: the launcher now **reads the car's I-Bus itself** over the Resler USB adapter — so that third-party app is **gone entirely** — and renders a fast, glanceable home you can operate at 70–90 cm while driving.

**Design goals**

- **Fast & responsive** — no jank on a low-end Qualcomm SoC.
- **Touch-first** — the E53 has no rotary controller, only the XTRONS touchscreen.
- **BMW surface language** — instrument-illumination amber on graphite, iDrive-style depth.
- **Own the whole stack** — trip computer, updates, reboot — so nothing depends on an abandoned third-party app, and it can all be improved over the air without a flash drive.

---

## Screenshots

> These are the app's actual rendered UI, captured by the [Paparazzi](https://github.com/cashapp/paparazzi) screenshot tests at the head unit's native 1280×720.

| Home — iDrive 3D carousel (over a live map) | Music — player v4 (cover bg + calm seek bar) |
|---|---|
| ![Home](screenshots/home.png) | ![Music player v4](screenshots/music-player-v4.png) |

| Apps | Settings |
|---|---|
| ![Apps](screenshots/apps.png) | ![Settings](screenshots/settings.png) |

---

## Features

### Home — a looping 3D tile carousel
- An **infinitely-looping, cylindrical 3D carousel** of large tiles (iDrive-2016 style): the centered tile is flat, focused, amber-bordered and glowing; side tiles turn away in perspective, shrink, and dim.
- **Icon-dominant** filled graphite cards; whole-tile tap.
- The 3D "feel" is driven entirely by a pure, unit-tested `CarouselGeometry` — rotation / scale / opacity per position — so it's tunable and re-shippable over the air without touching Compose.
- A **status ribbon** (clock · full Russian date · **live outside temperature read from the car's I-Bus**) and a breathing **amber ambient glow**.
- **Page-indicator strips** that scale to any number of tiles.
- The carousel floats over a **live map background** (see below).

### Live map background
- The home screen sits over a **live map that follows the car's GPS** — dark land, dark-teal water, amber roads, no labels: styled to match the launcher, not a stock map.
- Rendered by **MapLibre GL** from free **OpenFreeMap** vector tiles — **no API key, no Google Play Services**. The **style is a JSON file hosted on our own server**, so colours are re-tuned without shipping a new build.
- Tiles are **proxied through our own host** (the head unit's ISP can't reach the tile CDN directly, but it can always reach our server), and a dark scrim keeps the tiles/clock legible over the map. Gestures are disabled — it's a backdrop, not a map you touch.

### Music — Yandex Music now-playing (player screen v4)
- Controls **Yandex Music** via the Android `MediaSession` / `MediaController` framework (no reverse-engineering) using a `NotificationListenerService`.
- **v4 player screen:** the **album cover fills the whole background** (scrim + vignette keep text legible); a **calm amber seek bar** shows progress. Transport row is *shuffle · prev · play/pause · next · like*, with a tap-and-drag seek. *(An earlier animated "equalizer" was dropped: the head-unit ROM blocks audio capture, so any analyzer was necessarily synthetic and read as fake.)*
- **Reliable cold-start:** opening Music auto-wakes Yandex; if a fully-killed app won't resume in the background, an explicit **"Включить музыку"** button foreground-launches it so «Моя волна» starts, then drops you back on the now-playing screen.

### YouTube — behind a per-app VPN
- A **YouTube** tile that opens the app. YouTube is throttled/blocked in Russia, so it runs behind a **DPI-bypassing VPN** (VLESS + Reality via [sing-box](https://sing-box.sagernet.org/)) configured as an **always-on, _per-app_ `VpnService`** — only YouTube's traffic egresses through the tunnel; everything else (Yandex, navigation) stays direct. No runtime root needed. Design: [`youtube-vpn-design.md`](docs/superpowers/specs/2026-07-06-youtube-vpn-design.md).

### On-board computer — reads the car's I-Bus directly
- A **from-scratch trip computer**: the launcher talks to the car's **BMW I-Bus** over the Resler **CP210x USB-serial** adapter (9600 8E1, via `usb-serial-for-android`) and decodes the IKE instrument-cluster broadcasts itself — **speed, RPM, coolant & outside temperature, ignition** — and shows them on a native screen in the launcher's own style.
- The I-Bus framing + decode is a **pure, unit-tested** `IBusDecoder`; a single **process-wide reader** feeds both the trip-computer screen and the **live outside-temperature** in the home status bar (the BC opens already connected).
- This **replaced** the old approach of autostarting a proprietary i-Bus app, which was flaky and fought over the single-owner USB port. That app is **uninstalled** — we own the adapter. The reader logs one example of every distinct bus message type, so new gauges (fuel, consumption, trip averages) can be decoded from a real drive.
- Everything USB is **guarded** — no adapter / no permission just shows a "no adapter" state; the HOME app must never crash.

### Apps drawer
- A grid of installed apps (real launcher icons from `PackageManager`) plus a dedicated, cordoned-off **Reboot** tile (tap-to-confirm).

### Settings
- **Day / Night / Auto** theme segment control, **default-app** display, a **"Make default launcher"** action, the **in-app updater**, and one-tap **diagnostic-log upload**.

### Reboot without root
- Reboots this Microntek/HCT ROM via a vendor broadcast (`com.microntek.hctreboot`) — confirmed working from an unprivileged app, no `su` needed.

### Over-the-air self-update
- Checks a JSON manifest, and installs new builds either silently (if root is ever present) or via the system installer (non-root) — see [OTA](#over-the-air-updates).

---

## The car & the head unit

| | |
|---|---|
| **Car** | BMW X5 **E53** (2005) |
| **Head unit** | XTRONS IQ7439BL |
| **ROM** | Microntek / HCT, **Android 13** |
| **SoC** | Qualcomm QCM6125 |
| **Root** | **No** (the entire app is designed to be non-root) |
| **Screen** | 1280×720 px @ 240 dpi → **853×480 dp**, landscape, capacitive touch (no rotary encoder) |
| **Car integration** | our own I-Bus reader over the Resler **CP210x USB→I-Bus** adapter |

> **Design note:** everything is laid out in **dp** for the real 853 dp-wide screen. Screenshot tests deliberately render at 1280 px / mdpi (so `dp == px`) to pin the layout to the mockups.

---

## Enabling ADB on these head units — the `adbon` password

Developing on this class of unit has one nasty gotcha: the normal *"tap Build number 7×"* trick **does nothing**, and *Developer Options* never appears — the About screen only exposes an MCU version like **`if2 - V2`**. The undocumented fix is to open the unit's **Factory Settings** and type **`adbon`** as the password, which unlocks Developer Options / ADB. From there, **Wireless debugging** works with no USB cable, and because the ROM is a `userdebug` build, **`adb root` works**.

It took a full evening to reverse-engineer, so it's written up as a standalone guide for anyone with a **XTRONS / Microntek / HCT / MTCE** unit:

📄 **[Enabling ADB / Developer Options on Chinese Android head units (`adbon`)](docs/ENABLING-ADB-HCT-HEADUNITS.md)**

---

## Design system

The visual language is a curated "night-calm" automotive theme — **authentic BMW instrument-illumination amber `#FF7E00`** dosed over near-black graphite, matte (no gloss/glass — glare kills it in a car).

**Color tokens (night)**

| Token | Value | Use |
|---|---|---|
| `bgBase` | `#0A0B0D` | screen background |
| `surface` | `#15181B` | card fill |
| `surfaceHi` | `#1E2226` | pressed / focused fill |
| `textPrimary` | `#C7CCD2` | primary text (never pure white — glare) |
| `textSecondary` | `#8A9199` | secondary text |
| `accentAmber` | `#FF7E00` | the one accent |

- **Typography:** **Inter** (400/500/600/700), tabular figures. *(The design handoff specified Archivo, but Archivo has no Cyrillic — the UI is Russian — so Inter was substituted.)*
- **Interaction:** every tap gets a "triple pressed signal" — scale down + amber border + fill lift — plus haptics, because on a car screen you must **see and feel** that a press registered.
- **Motion:** carousel spring-snap, breathing ambient glow, no gratuitous animation.

Design references (rendered mockups + full token spec) live in [`docs/mockups/`](docs/mockups/).

---

## Architecture

Plain **Kotlin + Jetpack Compose**, no DI framework, small and legible. Logic that *can* be pure and unit-tested *is* (geometry, time formatting, playback mapping, theme resolution); Compose UI is verified with screenshot goldens.

```
HomeActivity (single Compose Activity, declared as HOME)
 ├─ NavHost: home · music · apps · settings · bordcomputer
 ├─ SettingsStore (DataStore)         — persisted prefs
 ├─ AppLauncher / InstalledApps       — launch + enumerate apps
 ├─ RootShell / ShellCommands         — root-adaptive (works without root)
 ├─ car/    IBusDecoder (pure, unit-tested) · IBusReader (CP210x USB) ·
 │          IBusService (process-wide singleton) · BordData
 ├─ diag/   AppLog · CrashHandler · AnrWatchdog · LogUploader
 ├─ update/ (UpdateChecker, ApkDownloader, ApkInstaller, RootDetector)
 └─ music/ (MediaSessionRepository, MusicController, MusicViewModel,
            MediaNotificationListener, PlaybackMapper, NowPlaying)
ui/
 ├─ home/   CarouselGeometry · HomeCarousel · TileCard · StatusRibbon ·
 │          RibbonClock · AmbientGlow · PageIndicator · MapBackground (MapLibre)
 ├─ bordcomputer/ BordComputerScreen
 ├─ apps/   AppsScreen
 ├─ settings/ SettingsScreen
 └─ theme/  Color · Theme · Type · Pressable · ScreenHeader
```

**Principles**

- Tunable constants (carousel angles, sizes, glow) live in pure objects so the "feel" is adjustable and OTA-shippable without editing view code.
- Everything degrades gracefully with no root and no network.
- The launcher is the HOME app — it **must never crash**; external intents are all guarded.

---

## Project structure

```
app/                     Android app module (Kotlin/Compose)
  src/main/…             source + AndroidManifest + res (adaptive icon, Inter fonts)
  src/test/…             JUnit + Robolectric unit tests + Paparazzi screenshot tests
docs/mockups/            design references (rendered screens + token spec)
scripts/release.sh       build signed release + publish OTA manifest
screenshots/             the images used in this README
```

---

## Build & run

**Requirements:** JDK 17, Android SDK (compileSdk 34), Gradle (wrapper included).

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_SDK_ROOT=/path/to/android-sdk

# debug APK
./gradlew :app:assembleDebug

# unit tests + screenshot verification
./gradlew :app:testDebugUnitTest :app:verifyPaparazziDebug
```

- `applicationId` = `online.k73.bmwlauncher` · `minSdk` 26 · `targetSdk` 33 · `compileSdk` 34.
- **Release signing** reads `keystore.properties` (gitignored) — create your own keystore; the app is unsigned-debug out of the box.

Install on the head unit over ADB (or via the in-app updater):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then set it as the default Home app (Android Settings → Default apps → Home app).

---

## Testing

- **Unit tests** (JUnit4 + Robolectric) cover the pure logic: carousel geometry, clock/date formatting, playback mapping, theme resolution, the update checker, and the autostart state machine.
- **Screenshot tests** ([Paparazzi](https://github.com/cashapp/paparazzi)) render each screen at the head unit's 1280×720 and diff against committed goldens, so a visual regression fails CI. The goldens double as the screenshots in this README.

```bash
./gradlew :app:recordPaparazziDebug   # regenerate goldens
./gradlew :app:verifyPaparazziDebug   # verify against goldens
```

---

## Over-the-air updates

The app self-updates so improvements ship without a flash drive:

1. **Settings → Update → "Check"** fetches a JSON manifest (`latest.json`) describing the newest build.
2. If newer, the button becomes **"Update"** and installs it:
   - **with root** (if ever present): silent `pm install -r` + relaunch,
   - **without root** (the norm here): download + system-installer intent via a `FileProvider`.

`scripts/release.sh` builds the signed release, uploads the APK, and writes the manifest. (The script's upload target is environment-specific — point it at your own host.)

---

## Version history

| Version | Highlights |
|---|---|
| **1.6.10** | On-board computer logs one example of **every distinct I-Bus message type** — groundwork for fuel / consumption / trip averages |
| **1.6.9** | **Live outside temperature** on the home status bar (replaces the X5 emblem); one shared process-wide I-Bus reader |
| **1.6.6–1.6.8** | **Own on-board computer** — reads the BMW I-Bus over the CP210x USB adapter (speed · RPM · coolant · outside temp); i-Bus app autostart removed; map tiles proxied through our own host |
| **1.6.3–1.6.7** | **Live map home background** — MapLibre GL + OpenFreeMap, self-styled dark/amber (dropped Yandex MapKit, whose proprietary style format never applied) |
| **1.6.0–1.6.2** | Audio-reactive equalizer experiments → **removed** for a calm seek bar (ROM blocks audio capture); reboot-durable ANR reports; gray-screen root-cause fixes |
| **1.5.7** | **YouTube tile** (opens behind a per-app DPI-bypassing VPN, provisioned on-device) |
| **1.5.6** | **Player screen v4** — album cover background + **live amber equalizer** progress, new shuffle·prev·play·next·like transport |
| **1.5.5** | Reliable Yandex **cold-start** — auto-wake on entry, bounded nudges, foreground fallback |
| **1.5.0–1.5.4** | In-app diagnostics (event log, crash/ANR capture, one-tap upload), cold-start polish, grey-empty-Music fix |
| **1.4.x** | **Claude-Design redesign** — filled icon-centric carousel cards, envelope album art, working back navigation, reliable "make default launcher", darker palette + Inter typography, triple press-feedback |
| **1.3.x** | iDrive 3D carousel home + on-device tuning (centering on real width, size, contrast) |
| **1.2.x** | Adaptive launcher icon, default-launcher setting, Music seek + like, i-Bus flash removal |
| **1.1.x** | Music now-playing (Yandex `MediaSession`) |
| **1.0.x** | Skeleton launcher, non-root reboot, OTA self-update, autostart |

---

## Roadmap

- **Trip computer** — fuel level, consumption and average speed (each with reset), decoded from the I-Bus.
- In-launcher **playlist browsing** (Yandex `MediaBrowserService`, pending device probe).
- Reflect the **real "liked" state** from the media session.
- A distinct, brighter **Day theme** pass.

*(Done since the roadmap was first written: live map background, our own I-Bus on-board computer, outside temperature in the ribbon.)*

---

## Known limitations

- **Non-root by design.** Some things a rooted launcher could do (fully silent installs, forcing default HOME) are done the polite, user-confirmed way instead.
- The head unit's LCD lifts near-black toward a lit blue-grey — full-dark screens photograph bluer than they render.
- Yandex Music integration is limited to what its `MediaSession` exposes (transport + now-playing are guaranteed; library browsing is not).
- **No _persistent_ on-device root.** The ROM has no `su`/Magisk (and no accessible USB for `fastboot`), so ADB root exists only over a live connection — see the [`adbon` guide](docs/ENABLING-ADB-HCT-HEADUNITS.md). This is why the YouTube VPN uses an always-on `VpnService` rather than a root-managed tunnel.

---

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Navigation-Compose · DataStore · `MediaSessionManager` / `NotificationListenerService` · **MapLibre GL** (OpenFreeMap tiles) · **usb-serial-for-android** (CP210x / BMW I-Bus) · Coroutines · JUnit4 · Robolectric · Paparazzi · Gradle (Kotlin DSL) · adaptive icons · Inter (SIL OFL).

---

## Credits & license

Made by **Roma** for his own E53 — a real, daily-driven car.

Development — **[Ostov ↗](https://ostov.dev/?utm_source=github&utm_medium=readme&utm_campaign=bmw-launcher)**

Released under the **MIT License** — see [`LICENSE`](LICENSE).

> BMW, X5, E53 and iDrive are trademarks of BMW AG. This is an independent, non-commercial enthusiast project and is **not affiliated with or endorsed by BMW**. The launcher icon and status-ribbon emblem are neutral, original graphics — not the BMW roundel.

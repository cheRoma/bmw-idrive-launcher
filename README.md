# BMW iDrive Launcher

**English** · [Русский](README.ru.md)

**A custom Android home-screen launcher that turns a cheap aftermarket head unit into a premium, BMW-iDrive-style car interface.**

Built for a **2005 BMW X5 (E53)** running an **XTRONS** Android head unit, this launcher replaces the sluggish factory desktop with a fast, touch-first UI inspired by modern **iDrive**: a 3D tile carousel, an instrument-amber accent on near-black graphite, a now-playing screen wired to **Yandex Music**, guaranteed autostart of the **i-Bus** trip-computer app, in-app OTA updates, and one-tap reboot — all in **Kotlin + Jetpack Compose**, screenshot-tested, no root required.

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

The factory launcher shipped on these XTRONS units (an abandoned third-party "iDrive Launcher") is laggy, unmaintained, and doesn't reliably do the two things that matter in an E53 retrofit:

1. **Autostart the i-Bus app** on boot, so the car's trip-computer / steering-wheel integration comes up automatically (it talks to the car over a Resler USB→I-Bus adapter).
2. **Get out of the way** — a fast, glanceable home screen you can operate at 70–90 cm while driving.

This project is a from-scratch replacement: own it, maintain it, make it feel like a real BMW.

**Design goals**

- **Fast & responsive** — no jank on a low-end Qualcomm SoC.
- **Touch-first** — the E53 has no rotary controller, only the XTRONS touchscreen.
- **BMW surface language** — instrument-illumination amber on graphite, iDrive-style depth.
- **Rock-solid i-Bus autostart** + a **reboot** button + **self-update** so it can be improved without a flash drive.

---

## Screenshots

> These are the app's actual rendered UI, captured by the [Paparazzi](https://github.com/cashapp/paparazzi) screenshot tests at the head unit's native 1280×720.

| Home — iDrive 3D carousel | Music — player v4 (cover bg + live equalizer) |
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
- A **status ribbon** (clock · full Russian date · outside temp · BMW-style quadrant emblem · "X5") and a breathing **amber ambient glow**.
- **Page-indicator strips** that scale to any number of tiles.

### Music — Yandex Music now-playing (player screen v4)
- Controls **Yandex Music** via the Android `MediaSession` / `MediaController` framework (no reverse-engineering) using a `NotificationListenerService`.
- **v4 player screen:** the **album cover fills the whole background** (scrim + vignette keep text legible); the **elapsed portion of the progress bar is a live amber equalizer** — bars animate while playing and freeze on pause. Transport row is *shuffle · prev · play/pause · next · like*, with a tap-and-drag seek bar.
- **Reliable cold-start:** opening Music auto-wakes Yandex; if a fully-killed app won't resume in the background, an explicit **"Включить музыку"** button foreground-launches it so «Моя волна» starts, then drops you back on the now-playing screen.

### YouTube — behind a per-app VPN
- A **YouTube** tile that opens the app. YouTube is throttled/blocked in Russia, so it runs behind a **DPI-bypassing VPN** (VLESS + Reality via [sing-box](https://sing-box.sagernet.org/)) configured as an **always-on, _per-app_ `VpnService`** — only YouTube's traffic egresses through the tunnel; everything else (Yandex, navigation) stays direct. No runtime root needed. Design: [`youtube-vpn-design.md`](docs/superpowers/specs/2026-07-06-youtube-vpn-design.md).

### Bort-computer (i-Bus) autostart — the killer feature
- On boot the launcher **autostarts the i-Bus app** so it connects to the car, then brings itself back to the front — with animations suppressed so the i-Bus app doesn't flash on screen.
- Fully **non-root** (uses foreground-HOME activity starts + task reordering), with a retry-friendly, once-per-process state machine.

### Apps drawer
- A grid of installed apps (real launcher icons from `PackageManager`) plus a dedicated, cordoned-off **Reboot** tile (tap-to-confirm).

### Settings
- **i-Bus autostart** toggle, **bring-launcher-to-front** toggle, **Day / Night / Auto** theme segment control, **default-app** display, a **"Make default launcher"** action, and the **in-app updater**.

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
| **Car integration** | i-Bus app + Resler USB→I-Bus adapter |

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
 ├─ NavHost: home · music · apps · settings
 ├─ SettingsStore (DataStore)         — persisted prefs
 ├─ AutostartController               — non-root i-Bus autostart state machine
 ├─ AppLauncher / InstalledApps       — launch + enumerate apps
 ├─ RootShell / ShellCommands         — root-adaptive (works without root)
 ├─ update/ (UpdateChecker, ApkDownloader, ApkInstaller, RootDetector)
 └─ music/ (MediaSessionRepository, MusicController, MusicViewModel,
            MediaNotificationListener, PlaybackMapper, NowPlaying)
ui/
 ├─ home/   CarouselGeometry · HomeCarousel · TileCard · StatusRibbon ·
 │          RibbonClock · RoundelIcon · AmbientGlow · PageIndicator
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

- In-launcher **playlist browsing** (Yandex `MediaBrowserService`, pending device probe).
- Reflect the **real "liked" state** from the media session.
- A distinct, brighter **Day theme** pass.
- **Outside-temperature** in the ribbon (from the vendor broadcast).

---

## Known limitations

- **Non-root by design.** Some things a rooted launcher could do (fully silent installs, forcing default HOME) are done the polite, user-confirmed way instead.
- The head unit's LCD lifts near-black toward a lit blue-grey — full-dark screens photograph bluer than they render.
- Yandex Music integration is limited to what its `MediaSession` exposes (transport + now-playing are guaranteed; library browsing is not).
- **No _persistent_ on-device root.** The ROM has no `su`/Magisk (and no accessible USB for `fastboot`), so ADB root exists only over a live connection — see the [`adbon` guide](docs/ENABLING-ADB-HCT-HEADUNITS.md). This is why the YouTube VPN uses an always-on `VpnService` rather than a root-managed tunnel.

---

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Navigation-Compose · DataStore · `MediaSessionManager` / `NotificationListenerService` · Coroutines · JUnit4 · Robolectric · Paparazzi · Gradle (Kotlin DSL) · adaptive icons · Inter (SIL OFL).

---

## Credits & license

Made by **Roma** for his own E53 — a real, daily-driven car.

Development — **[Ostov ↗](https://ostov.dev/?utm_source=github&utm_medium=readme&utm_campaign=bmw-launcher)**

Released under the **MIT License** — see [`LICENSE`](LICENSE).

> BMW, X5, E53 and iDrive are trademarks of BMW AG. This is an independent, non-commercial enthusiast project and is **not affiliated with or endorsed by BMW**. The launcher icon and status-ribbon emblem are neutral, original graphics — not the BMW roundel.

# BMW X5 E53 Launcher — Phase 0+1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a rock-solid HOME launcher skeleton for the XTRONS Android-12 head unit — 3×2 tile grid, external-app launch, background autostart of the i-Bus App with retries, root reboot, and day/night/auto theme — installable and testable in the car.

**Architecture:** Single-module Kotlin + Jetpack Compose app. A HOME `Activity` hosts Compose navigation over four internal screens (Home, Apps, Settings; Music lands in Phase 2); the three external tiles fire `Intent`s. Pure-logic units (settings, theme resolver, launch-intent builder, shell command builder, autostart retry state machine) are isolated behind interfaces and unit-tested headless with Robolectric; Compose screens are verified with Paparazzi screenshot tests against the approved mockups in `docs/mockups/`.

**Tech Stack:** Kotlin 1.9, AGP 8.5, Gradle 8.7, JDK 17, compileSdk 34 / minSdk 26 / targetSdk 33, Jetpack Compose (BOM 2024.06), DataStore-Preferences, Coroutines; tests: JUnit4, Robolectric 4.12, Paparazzi 1.3.3, kotlinx-coroutines-test.

**Package:** `online.k73.bmwlauncher`

**Spec:** `docs/superpowers/specs/2026-07-01-bmw-launcher-design.md`

**Scope note:** This plan covers Phase 0 (toolchain + scaffold) and Phase 1 (skeleton). Phase 2 (custom MediaSession music screen) and Phase 3 (playlist MediaBrowser experiment, polish) get their own plans after Phase 1 is validated on the car, since they depend on on-device answers (does Yandex expose MediaBrowser/like to a 3rd-party client). Auto-theme in Phase 1 uses a fixed night window (default 20:00–07:00, configurable); astronomical sunrise/sunset calculation is deferred to Phase 3 as noted in the spec's "fixed fallback".

---

## File Structure

```
settings.gradle.kts
build.gradle.kts                     (root)
gradle/libs.versions.toml            (version catalog)
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/online/k73/bmwlauncher/
  MainApplication.kt
  ui/HomeActivity.kt                 HOME activity, Compose nav host
  ui/theme/Theme.kt                  night-calm + day color schemes, Theme composable
  ui/theme/Color.kt                  BMW amber #FF7E00, greys, backgrounds
  ui/home/HomeScreen.kt              3×2 tile grid
  ui/home/Tile.kt                    Tile model + composable
  ui/apps/AppsScreen.kt              installed-app grid + reboot tile (long-press)
  ui/settings/SettingsScreen.kt      toggles + theme selector + default packages
  data/SettingsStore.kt             DataStore-backed settings repository
  data/LauncherSettings.kt          settings data class + ThemeMode enum
  theme/ThemeResolver.kt            pure day/night resolver
  launch/AppLauncher.kt             launch-intent builder + isInstalled
  system/Shell.kt                   Shell interface + ShellCommands (pure)
  system/RootShell.kt               su -c implementation of Shell
  autostart/AutostartController.kt  retry state machine
  autostart/BootReceiver.kt         BOOT_COMPLETED → AutostartController
app/src/test/java/online/k73/bmwlauncher/   (Robolectric + pure unit tests)
app/src/test/java/online/k73/bmwlauncher/screenshot/  (Paparazzi tests)
```

---

# PHASE 0 — Toolchain & Scaffold

### Task 0.1: Install JDK 17 + Android SDK on the VPS

**Files:** none (environment setup)

- [ ] **Step 1: Install JDK 17 (headless)**

Run:
```bash
sudo apt-get update && sudo apt-get install -y openjdk-17-jdk unzip wget
java -version
```
Expected: `openjdk version "17...`

- [ ] **Step 2: Install Android command-line tools + SDK packages**

Run:
```bash
export ANDROID_SDK_ROOT="$HOME/android-sdk"
mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
cd /tmp && wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline.zip
unzip -q cmdline.zip -d "$ANDROID_SDK_ROOT/cmdline-tools"
mv "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
yes | "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null
"$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```
Expected: `done` / SDK packages installed under `~/android-sdk`.

- [ ] **Step 3: Persist env vars**

Run:
```bash
{
  echo 'export ANDROID_SDK_ROOT="$HOME/android-sdk"'
  echo 'export ANDROID_HOME="$HOME/android-sdk"'
  echo 'export PATH="$PATH:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin"'
} >> ~/.bashrc
source ~/.bashrc
sdkmanager --version
```
Expected: prints an sdkmanager version.

---

### Task 0.2: Scaffold the Gradle Android project

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `local.properties`, `gradle.properties`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/online/k73/bmwlauncher/MainApplication.kt`

- [ ] **Step 1: Create `local.properties` (SDK path) and `gradle.properties`**

`local.properties`:
```properties
sdk.dir=/home/roma/android-sdk
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2048m
android.useAndroidX=true
kotlin.code.style=official
```

- [ ] **Step 2: Create the Gradle wrapper**

Run:
```bash
cd ~/Projects/bmw
gradle wrapper --gradle-version 8.7 2>/dev/null || \
  (wget -q https://services.gradle.org/distributions/gradle-8.7-bin.zip -O /tmp/g.zip && \
   unzip -q /tmp/g.zip -d /tmp && /tmp/gradle-8.7/bin/gradle wrapper --gradle-version 8.7)
./gradlew --version
```
Expected: Gradle 8.7 banner.

- [ ] **Step 3: Write `gradle/libs.versions.toml` (version catalog)**

```toml
[versions]
agp = "8.5.2"
kotlin = "1.9.24"
composeBom = "2024.06.00"
coreKtx = "1.13.1"
lifecycle = "2.8.3"
activityCompose = "1.9.0"
navCompose = "2.7.7"
datastore = "1.1.1"
coroutines = "1.8.1"
junit = "4.13.2"
robolectric = "4.12.2"
paparazzi = "1.3.3"

[libraries]
core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
lifecycle-runtime = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-graphics = { module = "androidx.compose.ui:ui-graphics" }
compose-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
nav-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navCompose" }
datastore-prefs = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
androidx-test-core = { module = "androidx.test:core", version = "1.6.1" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
paparazzi = { id = "app.cash.paparazzi", version.ref = "paparazzi" }
```

- [ ] **Step 4: Write `settings.gradle.kts` and root `build.gradle.kts`**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "BMWLauncher"
include(":app")
```

Root `build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.paparazzi) apply false
}
```

- [ ] **Step 5: Write `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "online.k73.bmwlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "online.k73.bmwlauncher"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.graphics)
    implementation(libs.compose.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.nav.compose)
    implementation(libs.datastore.prefs)
    implementation(libs.coroutines.android)
    debugImplementation(libs.compose.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui)
}
```

- [ ] **Step 6: Write minimal `AndroidManifest.xml` and `MainApplication.kt`**

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:name=".MainApplication"
        android:label="BMW Launcher"
        android:theme="@android:style/Theme.Material.NoActionBar">
    </application>
</manifest>
```

`app/src/main/java/online/k73/bmwlauncher/MainApplication.kt`:
```kotlin
package online.k73.bmwlauncher

import android.app.Application

class MainApplication : Application()
```

---

### Task 0.3: Prove the toolchain builds + tests run headless

**Files:**
- Test: `app/src/test/java/online/k73/bmwlauncher/SmokeTest.kt`

- [ ] **Step 1: Write a trivial passing test**

```kotlin
package online.k73.bmwlauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test fun toolchain_works() {
        assertEquals(4, 2 + 2)
    }
}
```

- [ ] **Step 2: Run the build + test**

Run: `cd ~/Projects/bmw && ./gradlew :app:testDebugUnitTest --no-daemon`
Expected: `BUILD SUCCESSFUL`, `SmokeTest > toolchain_works PASSED`.

- [ ] **Step 3: Verify the APK assembles**

Run: `./gradlew :app:assembleDebug --no-daemon`
Expected: `BUILD SUCCESSFUL`; APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Commit**

```bash
cd ~/Projects/bmw
printf '\n# Gradle / Android build\n.gradle/\nbuild/\napp/build/\nlocal.properties\n*.hprof\n' >> .gitignore
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle/ gradlew gradlew.bat app/
git commit -m "chore: scaffold Kotlin+Compose Android project with headless test toolchain"
```

---

# PHASE 1 — Skeleton

### Task 1.1: `LauncherSettings` model + `ThemeMode`

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/data/LauncherSettings.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/data/LauncherSettingsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package online.k73.bmwlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherSettingsTest {
    @Test fun defaults_are_sane() {
        val s = LauncherSettings()
        assertTrue(s.autostartIBus)
        assertTrue(s.bringLauncherToFront)
        assertEquals(ThemeMode.AUTO, s.themeMode)
        assertEquals("ru.yandex.yandexnavi", s.navPackage)
        assertEquals("", s.carplayPackage)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*LauncherSettingsTest*' --no-daemon`
Expected: FAIL — `LauncherSettings` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package online.k73.bmwlauncher.data

enum class ThemeMode { DAY, NIGHT, AUTO }

data class LauncherSettings(
    val autostartIBus: Boolean = true,
    val bringLauncherToFront: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val musicPackage: String = "ru.yandex.music",
    val navPackage: String = "ru.yandex.yandexnavi",
    val iBusPackage: String = "",
    val carplayPackage: String = "",
    val nightStartHour: Int = 20,
    val nightEndHour: Int = 7,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*LauncherSettingsTest*' --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/data/LauncherSettings.kt app/src/test/java/online/k73/bmwlauncher/data/LauncherSettingsTest.kt
git commit -m "feat(data): add LauncherSettings model and ThemeMode"
```

---

### Task 1.2: `ThemeResolver` (pure day/night logic)

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/theme/ThemeResolver.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/theme/ThemeResolverTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package online.k73.bmwlauncher.theme

import online.k73.bmwlauncher.data.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ThemeResolverTest {
    @Test fun manual_day_is_never_night() {
        assertFalse(ThemeResolver.isNight(ThemeMode.DAY, LocalTime.of(23, 0), 20, 7))
    }
    @Test fun manual_night_is_always_night() {
        assertTrue(ThemeResolver.isNight(ThemeMode.NIGHT, LocalTime.of(12, 0), 20, 7))
    }
    @Test fun auto_evening_is_night() {
        assertTrue(ThemeResolver.isNight(ThemeMode.AUTO, LocalTime.of(21, 30), 20, 7))
    }
    @Test fun auto_pre_dawn_is_night() {
        assertTrue(ThemeResolver.isNight(ThemeMode.AUTO, LocalTime.of(6, 0), 20, 7))
    }
    @Test fun auto_midday_is_day() {
        assertFalse(ThemeResolver.isNight(ThemeMode.AUTO, LocalTime.of(13, 0), 20, 7))
    }
    @Test fun auto_exact_night_start_is_night() {
        assertTrue(ThemeResolver.isNight(ThemeMode.AUTO, LocalTime.of(20, 0), 20, 7))
    }
    @Test fun auto_exact_night_end_is_day() {
        assertFalse(ThemeResolver.isNight(ThemeMode.AUTO, LocalTime.of(7, 0), 20, 7))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ThemeResolverTest*' --no-daemon`
Expected: FAIL — `ThemeResolver` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package online.k73.bmwlauncher.theme

import online.k73.bmwlauncher.data.ThemeMode
import java.time.LocalTime

object ThemeResolver {
    /**
     * @param nightStartHour hour [0..23] when night theme begins (inclusive)
     * @param nightEndHour   hour [0..23] when night theme ends (exclusive)
     * The window wraps past midnight when start > end (e.g. 20 -> 7).
     */
    fun isNight(mode: ThemeMode, now: LocalTime, nightStartHour: Int, nightEndHour: Int): Boolean =
        when (mode) {
            ThemeMode.DAY -> false
            ThemeMode.NIGHT -> true
            ThemeMode.AUTO -> {
                val start = LocalTime.of(nightStartHour, 0)
                val end = LocalTime.of(nightEndHour, 0)
                if (start <= end) now >= start && now < end
                else now >= start || now < end
            }
        }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*ThemeResolverTest*' --no-daemon`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/theme/ThemeResolver.kt app/src/test/java/online/k73/bmwlauncher/theme/ThemeResolverTest.kt
git commit -m "feat(theme): add pure day/night ThemeResolver with midnight-wrapping window"
```

---

### Task 1.3: `SettingsStore` (DataStore persistence)

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/data/SettingsStore.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/data/SettingsStoreTest.kt`

- [ ] **Step 1: Write the failing test (Robolectric)**

```kotlin
package online.k73.bmwlauncher.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {
    private val store = SettingsStore(ApplicationProvider.getApplicationContext())

    @Test fun reads_defaults_before_any_write() = runTest {
        val s = store.read()
        assertEquals(ThemeMode.AUTO, s.themeMode)
    }

    @Test fun persists_theme_mode_and_toggle() = runTest {
        store.setThemeMode(ThemeMode.NIGHT)
        store.setAutostartIBus(false)
        val s = store.read()
        assertEquals(ThemeMode.NIGHT, s.themeMode)
        assertFalse(s.autostartIBus)
    }

    @Test fun persists_ibus_package() = runTest {
        store.setIBusPackage("de.example.ibus")
        assertEquals("de.example.ibus", store.read().iBusPackage)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*SettingsStoreTest*' --no-daemon`
Expected: FAIL — `SettingsStore` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package online.k73.bmwlauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val autostart = booleanPreferencesKey("autostart_ibus")
        val bringToFront = booleanPreferencesKey("bring_to_front")
        val themeMode = stringPreferencesKey("theme_mode")
        val musicPkg = stringPreferencesKey("music_pkg")
        val navPkg = stringPreferencesKey("nav_pkg")
        val ibusPkg = stringPreferencesKey("ibus_pkg")
        val carplayPkg = stringPreferencesKey("carplay_pkg")
        val nightStart = intPreferencesKey("night_start")
        val nightEnd = intPreferencesKey("night_end")
    }

    val flow: Flow<LauncherSettings> = context.dataStore.data.map { p -> p.toSettings() }

    suspend fun read(): LauncherSettings = context.dataStore.data.first().toSettings()

    suspend fun setThemeMode(mode: ThemeMode) =
        edit { it[Keys.themeMode] = mode.name }
    suspend fun setAutostartIBus(enabled: Boolean) =
        edit { it[Keys.autostart] = enabled }
    suspend fun setBringToFront(enabled: Boolean) =
        edit { it[Keys.bringToFront] = enabled }
    suspend fun setMusicPackage(pkg: String) = edit { it[Keys.musicPkg] = pkg }
    suspend fun setNavPackage(pkg: String) = edit { it[Keys.navPkg] = pkg }
    suspend fun setIBusPackage(pkg: String) = edit { it[Keys.ibusPkg] = pkg }
    suspend fun setCarplayPackage(pkg: String) = edit { it[Keys.carplayPkg] = pkg }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private fun Preferences.toSettings(): LauncherSettings {
        val defaults = LauncherSettings()
        return LauncherSettings(
            autostartIBus = this[Keys.autostart] ?: defaults.autostartIBus,
            bringLauncherToFront = this[Keys.bringToFront] ?: defaults.bringLauncherToFront,
            themeMode = this[Keys.themeMode]?.let { ThemeMode.valueOf(it) } ?: defaults.themeMode,
            musicPackage = this[Keys.musicPkg] ?: defaults.musicPackage,
            navPackage = this[Keys.navPkg] ?: defaults.navPackage,
            iBusPackage = this[Keys.ibusPkg] ?: defaults.iBusPackage,
            carplayPackage = this[Keys.carplayPkg] ?: defaults.carplayPackage,
            nightStartHour = this[Keys.nightStart] ?: defaults.nightStartHour,
            nightEndHour = this[Keys.nightEnd] ?: defaults.nightEndHour,
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*SettingsStoreTest*' --no-daemon`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/data/SettingsStore.kt app/src/test/java/online/k73/bmwlauncher/data/SettingsStoreTest.kt
git commit -m "feat(data): add DataStore-backed SettingsStore"
```

---

### Task 1.4: `Shell` interface + `ShellCommands` (pure command strings)

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/system/Shell.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/system/ShellCommandsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package online.k73.bmwlauncher.system

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellCommandsTest {
    @Test fun reboot_command() {
        assertEquals("reboot", ShellCommands.reboot())
    }
    @Test fun pidof_command() {
        assertEquals("pidof de.example.ibus", ShellCommands.pidof("de.example.ibus"))
    }
    @Test fun start_package_command() {
        assertEquals(
            "monkey -p de.example.ibus -c android.intent.category.LAUNCHER 1",
            ShellCommands.startPackage("de.example.ibus")
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ShellCommandsTest*' --no-daemon`
Expected: FAIL — `ShellCommands` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package online.k73.bmwlauncher.system

data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val ok: Boolean get() = exitCode == 0
}

interface Shell {
    /** Runs [command] via a root shell (su -c). Returns exit code + captured output. */
    fun exec(command: String): ShellResult
}

/** Pure builders for the shell commands we run as root. Kept separate so they are unit-testable. */
object ShellCommands {
    fun reboot(): String = "reboot"
    fun pidof(pkg: String): String = "pidof $pkg"
    // monkey with the LAUNCHER category launches a package's main activity headlessly and
    // works reliably from a root shell without knowing the exact component name.
    fun startPackage(pkg: String): String =
        "monkey -p $pkg -c android.intent.category.LAUNCHER 1"
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*ShellCommandsTest*' --no-daemon`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/system/Shell.kt app/src/test/java/online/k73/bmwlauncher/system/ShellCommandsTest.kt
git commit -m "feat(system): add Shell interface and pure ShellCommands builders"
```

---

### Task 1.5: `RootShell` (su implementation)

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/system/RootShell.kt`
- Test: none (requires a rooted device; validated on-car). Logic under test lives in `ShellCommands` (Task 1.4) and `AutostartController` (Task 1.6) via a fake `Shell`.

- [ ] **Step 1: Write the implementation**

```kotlin
package online.k73.bmwlauncher.system

import java.io.BufferedReader

/** Executes commands through `su -c`. Not unit-tested (needs root); exercised on device. */
class RootShell : Shell {
    override fun exec(command: String): ShellResult {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(false)
                .start()
            val out = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val err = process.errorStream.bufferedReader().use(BufferedReader::readText)
            val code = process.waitFor()
            ShellResult(code, out.trim(), err.trim())
        } catch (t: Throwable) {
            ShellResult(exitCode = 127, stdout = "", stderr = t.message ?: "su failed")
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/system/RootShell.kt
git commit -m "feat(system): add RootShell su -c implementation"
```

---

### Task 1.6: `AutostartController` (retry state machine)

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/autostart/AutostartController.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/autostart/AutostartControllerTest.kt`

- [ ] **Step 1: Write the failing test (fake Shell, no real delays)**

```kotlin
package online.k73.bmwlauncher.autostart

import online.k73.bmwlauncher.system.Shell
import online.k73.bmwlauncher.system.ShellResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeShell(
    /** number of pidof calls that return "not running" before it reports running */
    private val notRunningTimes: Int
) : Shell {
    var startCalls = 0
    private var pidofCalls = 0
    val commands = mutableListOf<String>()
    override fun exec(command: String): ShellResult {
        commands += command
        return when {
            command.startsWith("pidof") -> {
                pidofCalls++
                if (pidofCalls > notRunningTimes) ShellResult(0, "1234", "")
                else ShellResult(1, "", "")
            }
            command.startsWith("monkey") -> { startCalls++; ShellResult(0, "ok", "") }
            else -> ShellResult(0, "", "")
        }
    }
}

class AutostartControllerTest {
    @Test fun already_running_does_not_start() = runTest {
        val shell = FakeShell(notRunningTimes = 0)
        val c = AutostartController(shell, delaysMs = longArrayOf(1, 1, 1)) { }
        val ok = c.ensureRunning("de.example.ibus")
        assertTrue(ok)
        assertEquals(0, shell.startCalls)
    }

    @Test fun starts_then_succeeds_after_retries() = runTest {
        val shell = FakeShell(notRunningTimes = 2)
        val c = AutostartController(shell, delaysMs = longArrayOf(1, 1, 1, 1)) { }
        val ok = c.ensureRunning("de.example.ibus")
        assertTrue(ok)
        assertTrue(shell.startCalls >= 1)
        assertTrue(shell.commands.any { it.startsWith("monkey -p de.example.ibus") })
    }

    @Test fun gives_up_after_exhausting_delays() = runTest {
        val shell = FakeShell(notRunningTimes = 999)
        val c = AutostartController(shell, delaysMs = longArrayOf(1, 1)) { }
        val ok = c.ensureRunning("de.example.ibus")
        assertFalse(ok)
    }

    @Test fun blank_package_is_a_noop_failure() = runTest {
        val shell = FakeShell(notRunningTimes = 0)
        val c = AutostartController(shell, delaysMs = longArrayOf(1)) { }
        assertFalse(c.ensureRunning(""))
        assertEquals(0, shell.startCalls)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*AutostartControllerTest*' --no-daemon`
Expected: FAIL — `AutostartController` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package online.k73.bmwlauncher.autostart

import kotlinx.coroutines.delay
import online.k73.bmwlauncher.system.Shell
import online.k73.bmwlauncher.system.ShellCommands

/**
 * Ensures the i-Bus App is running in the background, retrying with backoff until the
 * process appears or the delay schedule is exhausted. Launches via a root shell and then
 * calls [onLaunched] so the caller can re-assert the launcher on top (flash-free background).
 *
 * @param delaysMs backoff schedule; also bounds the number of retries.
 * @param onLaunched invoked right after each launch attempt (e.g. re-foreground HOME).
 */
class AutostartController(
    private val shell: Shell,
    private val delaysMs: LongArray = longArrayOf(3_000, 6_000, 12_000, 20_000, 30_000),
    private val onLaunched: () -> Unit,
) {
    private fun isRunning(pkg: String): Boolean =
        shell.exec(ShellCommands.pidof(pkg)).let { it.ok && it.stdout.isNotBlank() }

    suspend fun ensureRunning(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        if (isRunning(pkg)) return true
        for (delayMs in delaysMs) {
            shell.exec(ShellCommands.startPackage(pkg))
            onLaunched()
            delay(delayMs)
            if (isRunning(pkg)) return true
        }
        return isRunning(pkg)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*AutostartControllerTest*' --no-daemon`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/autostart/AutostartController.kt app/src/test/java/online/k73/bmwlauncher/autostart/AutostartControllerTest.kt
git commit -m "feat(autostart): add retry state machine for background i-Bus start"
```

---

### Task 1.7: `AppLauncher` (launch-intent builder)

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/launch/AppLauncher.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/launch/AppLauncherTest.kt`

- [ ] **Step 1: Write the failing test (Robolectric shadow PackageManager)**

```kotlin
package online.k73.bmwlauncher.launch

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AppLauncherTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val launcher = AppLauncher(context)

    @Test fun missing_package_is_not_installed() {
        assertFalse(launcher.isInstalled("com.nope.missing"))
        assertNull(launcher.launchIntentFor("com.nope.missing"))
    }

    @Test fun installed_package_yields_launch_intent() {
        val pkg = "ru.yandex.yandexnavi"
        val pm = shadowOf(context.packageManager)
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg)
        pm.addActivityIfNotPresent(android.content.ComponentName(pkg, "$pkg.Main"))
        pm.addIntentFilterForActivity(
            android.content.ComponentName(pkg, "$pkg.Main"),
            android.content.IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        )
        assertTrue(launcher.isInstalled(pkg))
        val intent = launcher.launchIntentFor(pkg)
        assertTrue(intent != null && intent.hasCategory(Intent.CATEGORY_LAUNCHER))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*AppLauncherTest*' --no-daemon`
Expected: FAIL — `AppLauncher` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package online.k73.bmwlauncher.launch

import android.content.Context
import android.content.Intent

class AppLauncher(private val context: Context) {
    fun isInstalled(pkg: String): Boolean =
        pkg.isNotBlank() && context.packageManager.getLaunchIntentForPackage(pkg) != null

    fun launchIntentFor(pkg: String): Intent? =
        if (pkg.isBlank()) null
        else context.packageManager.getLaunchIntentForPackage(pkg)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    /** Returns true if the app was launched. */
    fun launch(pkg: String): Boolean {
        val intent = launchIntentFor(pkg) ?: return false
        context.startActivity(intent)
        return true
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*AppLauncherTest*' --no-daemon`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/launch/AppLauncher.kt app/src/test/java/online/k73/bmwlauncher/launch/AppLauncherTest.kt
git commit -m "feat(launch): add AppLauncher for external-app intents"
```

---

### Task 1.8: Compose theme (colors + Theme composable)

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/ui/theme/Color.kt`
- Create: `app/src/main/java/online/k73/bmwlauncher/ui/theme/Theme.kt`
- Test: none (colors are validated visually by the Paparazzi tests in Tasks 1.9–1.11)

- [ ] **Step 1: Write `Color.kt`**

```kotlin
package online.k73.bmwlauncher.ui.theme

import androidx.compose.ui.graphics.Color

// BMW instrument-illumination amber (verified RGB 255,126,0)
val BmwAmber = Color(0xFFFF7E00)

// Night-calm palette (default)
val NightBackground = Color(0xFF0D0D0D)
val NightTile = Color(0xFF161616)
val NightText = Color(0xFFC8C8C8)
val NightTextDim = Color(0xFF8A8A8A)

// Day palette (higher brightness/contrast)
val DayBackground = Color(0xFF141414)
val DayTile = Color(0xFF232323)
val DayText = Color(0xFFF2F2F2)
val DayTextDim = Color(0xFFB5B5B5)
```

- [ ] **Step 2: Write `Theme.kt`**

```kotlin
package online.k73.bmwlauncher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class LauncherColors(
    val background: Color,
    val tile: Color,
    val text: Color,
    val textDim: Color,
    val accent: Color = BmwAmber,
)

val LocalLauncherColors = staticCompositionLocalOf {
    LauncherColors(NightBackground, NightTile, NightText, NightTextDim)
}

@Composable
fun BmwLauncherTheme(isNight: Boolean, content: @Composable () -> Unit) {
    val colors = if (isNight)
        LauncherColors(NightBackground, NightTile, NightText, NightTextDim)
    else
        LauncherColors(DayBackground, DayTile, DayText, DayTextDim)

    CompositionLocalProvider(LocalLauncherColors provides colors) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                background = colors.background,
                surface = colors.tile,
                primary = colors.accent,
                onBackground = colors.text,
                onSurface = colors.text,
            ),
            typography = Typography(),
            content = content,
        )
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/ui/theme/
git commit -m "feat(ui): add night-calm + day Compose color themes"
```

---

### Task 1.9: Home screen — 3×2 tile grid + Paparazzi test

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/ui/home/Tile.kt`
- Create: `app/src/main/java/online/k73/bmwlauncher/ui/home/HomeScreen.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/screenshot/HomeScreenScreenshotTest.kt`
- Reference mockup: `docs/mockups/home-final-nightcalm.png`

- [ ] **Step 1: Write `Tile.kt` (model + composable)**

```kotlin
package online.k73.bmwlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors

enum class TileId { MUSIC, NAV, APPS, IBUS, SETTINGS, CARPLAY }

data class Tile(val id: TileId, val label: String, val icon: ImageVector, val priority: Boolean = false)

@Composable
fun TileCell(tile: Tile, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalLauncherColors.current
    Column(
        modifier = modifier
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(14.dp))
            .background(c.tile)
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        androidx.compose.material3.Icon(
            imageVector = tile.icon,
            contentDescription = tile.label,
            tint = c.text,
            modifier = Modifier.height(if (tile.priority) 64.dp else 56.dp),
        )
        Text(
            text = tile.label,
            color = c.text,
            fontSize = if (tile.priority) 26.sp else 24.sp,
            fontWeight = FontWeight.Medium,
        )
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().height(3.dp)
                .background(if (tile.priority) c.accent else c.tile)
        )
    }
}
```

- [ ] **Step 2: Write `HomeScreen.kt`**

```kotlin
package online.k73.bmwlauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors

val defaultTiles = listOf(
    Tile(TileId.MUSIC, "Музыка", Icons.Filled.MusicNote, priority = true),
    Tile(TileId.NAV, "Навигация", Icons.Filled.Navigation, priority = true),
    Tile(TileId.APPS, "Приложения", Icons.Filled.GridView),
    Tile(TileId.IBUS, "Борткомпьютер", Icons.Filled.DirectionsCar),
    Tile(TileId.SETTINGS, "Настройки", Icons.Filled.Settings),
    Tile(TileId.CARPLAY, "CarPlay", Icons.Filled.PhoneIphone),
)

@Composable
fun HomeScreen(tiles: List<Tile> = defaultTiles, onTile: (TileId) -> Unit = {}) {
    val c = LocalLauncherColors.current
    Column(
        Modifier.fillMaxSize().background(c.background).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        tiles.chunked(3).forEach { rowTiles ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                rowTiles.forEach { tile ->
                    TileCell(tile, modifier = Modifier.weight(1f)) { onTile(tile.id) }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Write the Paparazzi screenshot test**

```kotlin
package online.k73.bmwlauncher.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import online.k73.bmwlauncher.ui.home.HomeScreen
import online.k73.bmwlauncher.ui.theme.BmwLauncherTheme
import org.junit.Rule
import org.junit.Test

class HomeScreenScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5_LAND.copy(screenWidth = 1280, screenHeight = 720),
    )

    @Test fun home_night() {
        paparazzi.snapshot { BmwLauncherTheme(isNight = true) { HomeScreen() } }
    }
}
```

- [ ] **Step 4: Record the golden screenshot**

Run: `./gradlew :app:recordPaparazziDebug --tests '*HomeScreenScreenshotTest*' --no-daemon`
Expected: `BUILD SUCCESSFUL`; PNG written under `app/src/test/snapshots/`. Open it and compare against `docs/mockups/home-final-nightcalm.png` — same 3×2 layout, amber underline on the two priority tiles.

- [ ] **Step 5: Verify the screenshot test passes on replay**

Run: `./gradlew :app:verifyPaparazziDebug --tests '*HomeScreenScreenshotTest*' --no-daemon`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/ui/home/ app/src/test/java/online/k73/bmwlauncher/screenshot/HomeScreenScreenshotTest.kt app/src/test/snapshots/
git commit -m "feat(ui): add 3x2 Home tile grid with Paparazzi golden"
```

---

### Task 1.10: Apps screen — installed-app grid + long-press reboot tile

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/launch/InstalledApps.kt`
- Create: `app/src/main/java/online/k73/bmwlauncher/ui/apps/AppsScreen.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/launch/InstalledAppsTest.kt`
- Reference mockup: `docs/mockups/apps.png`

- [ ] **Step 1: Write the failing test for the app-list query**

```kotlin
package online.k73.bmwlauncher.launch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InstalledAppsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun returns_launchable_apps_sorted_by_label() {
        val apps = InstalledApps(context).list()
        // Robolectric provides at least the test package; result must be non-null and sorted.
        val labels = apps.map { it.label }
        assertTrue(labels == labels.sortedBy { it.lowercase() })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*InstalledAppsTest*' --no-daemon`
Expected: FAIL — `InstalledApps` unresolved.

- [ ] **Step 3: Write `InstalledApps.kt`**

```kotlin
package online.k73.bmwlauncher.launch

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

data class AppEntry(val packageName: String, val label: String, val icon: Drawable?)

class InstalledApps(private val context: Context) {
    fun list(): List<AppEntry> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, 0)
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null // hide ourselves
                AppEntry(
                    packageName = pkg,
                    label = ri.loadLabel(pm).toString(),
                    icon = runCatching { ri.loadIcon(pm) }.getOrNull(),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*InstalledAppsTest*' --no-daemon`
Expected: PASS.

- [ ] **Step 5: Write `AppsScreen.kt` (grid + reboot tile with long-press + hold confirmation)**

```kotlin
package online.k73.bmwlauncher.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.k73.bmwlauncher.launch.AppEntry
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors

@Composable
fun AppsScreen(
    apps: List<AppEntry>,
    onLaunch: (String) -> Unit,
    onRebootHold: () -> Unit,
) {
    val c = LocalLauncherColors.current
    Column(Modifier.fillMaxSize().background(c.background).padding(24.dp)) {
        Text("Приложения", color = c.text, fontSize = 26.sp, modifier = Modifier.padding(bottom = 16.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(apps, key = { it.packageName }) { app ->
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(c.tile)
                        .pointerInput(app.packageName) { detectTapGestures(onTap = { onLaunch(app.packageName) }) }
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(app.label, color = c.text, fontSize = 18.sp)
                }
            }
            item {
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(c.tile)
                        .pointerInput(Unit) { detectTapGestures(onLongPress = { onRebootHold() }) }
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = "Перезагрузка", tint = c.accent, modifier = Modifier.padding(bottom = 6.dp))
                    Text("Перезагрузка", color = c.text, fontSize = 18.sp)
                    Text("удерживать", color = c.textDim, fontSize = 12.sp)
                }
            }
        }
    }
}
```

- [ ] **Step 6: Compile-check and commit**

Run: `./gradlew :app:compileDebugKotlin --no-daemon`
Expected: `BUILD SUCCESSFUL`.
```bash
git add app/src/main/java/online/k73/bmwlauncher/launch/InstalledApps.kt app/src/main/java/online/k73/bmwlauncher/ui/apps/AppsScreen.kt app/src/test/java/online/k73/bmwlauncher/launch/InstalledAppsTest.kt
git commit -m "feat(ui): add Apps grid with long-press reboot tile"
```

---

### Task 1.11: Settings screen wired to `SettingsStore`

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/ui/settings/SettingsScreen.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/screenshot/SettingsScreenScreenshotTest.kt`
- Reference mockup: `docs/mockups/settings.png`

- [ ] **Step 1: Write `SettingsScreen.kt` (stateless — takes state + callbacks)**

```kotlin
package online.k73.bmwlauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.k73.bmwlauncher.data.LauncherSettings
import online.k73.bmwlauncher.data.ThemeMode
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors

@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    onAutostart: (Boolean) -> Unit,
    onBringToFront: (Boolean) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
) {
    val c = LocalLauncherColors.current
    Column(Modifier.fillMaxSize().background(c.background).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Настройки", color = c.text, fontSize = 26.sp, modifier = Modifier.padding(bottom = 12.dp))

        SwitchRow("Автозапуск Борткомпьютера", "i-Bus App", settings.autostartIBus, onAutostart)
        SwitchRow("Выводить на передний план при запуске", null, settings.bringLauncherToFront, onBringToFront)

        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Тема оформления", color = c.text, fontSize = 22.sp, modifier = Modifier.weight(1f))
            listOf(ThemeMode.DAY to "День", ThemeMode.NIGHT to "Ночь", ThemeMode.AUTO to "Авто").forEach { (mode, label) ->
                val selected = settings.themeMode == mode
                Text(
                    label,
                    color = if (selected) c.accent else c.textDim,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(start = 20.dp)
                        .background(androidx.compose.ui.graphics.Color.Transparent)
                        .padding(8.dp)
                        .clickableNoRipple { onThemeMode(mode) },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalLauncherColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 22.sp)
            if (sub != null) Text(sub, color = c.textDim, fontSize = 16.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = androidx.compose.ui.graphics.Color.White),
        )
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(
        interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null, onClick = onClick,
    ))
```

- [ ] **Step 2: Write the Paparazzi screenshot test**

```kotlin
package online.k73.bmwlauncher.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import online.k73.bmwlauncher.data.LauncherSettings
import online.k73.bmwlauncher.ui.settings.SettingsScreen
import online.k73.bmwlauncher.ui.theme.BmwLauncherTheme
import org.junit.Rule
import org.junit.Test

class SettingsScreenScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5_LAND.copy(screenWidth = 1280, screenHeight = 720),
    )

    @Test fun settings_night() {
        paparazzi.snapshot {
            BmwLauncherTheme(isNight = true) {
                SettingsScreen(LauncherSettings(), {}, {}, {})
            }
        }
    }
}
```

- [ ] **Step 3: Record + verify the golden**

Run: `./gradlew :app:recordPaparazziDebug --tests '*SettingsScreenScreenshotTest*' --no-daemon`
Then: `./gradlew :app:verifyPaparazziDebug --tests '*SettingsScreenScreenshotTest*' --no-daemon`
Expected: golden recorded, then PASS. Compare against `docs/mockups/settings.png` — autostart toggle ON amber, День/Ночь/Авто with Авто amber.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/ui/settings/SettingsScreen.kt app/src/test/java/online/k73/bmwlauncher/screenshot/SettingsScreenScreenshotTest.kt app/src/test/snapshots/
git commit -m "feat(ui): add Settings screen with autostart toggle and theme selector"
```

---

### Task 1.12: `HomeActivity`, navigation, boot wiring, manifest

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/ui/HomeActivity.kt`
- Create: `app/src/main/java/online/k73/bmwlauncher/autostart/BootReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/online/k73/bmwlauncher/ui/HomeActivityTest.kt`

- [ ] **Step 1: Write the failing test (activity launches as HOME without crashing)**

```kotlin
package online.k73.bmwlauncher.ui

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeActivityTest {
    @Test fun home_activity_starts() {
        val controller = Robolectric.buildActivity(HomeActivity::class.java).setup()
        assertNotNull(controller.get())
    }

    @Test fun app_context_available() {
        assertNotNull(ApplicationProvider.getApplicationContext<android.content.Context>())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*HomeActivityTest*' --no-daemon`
Expected: FAIL — `HomeActivity` unresolved.

- [ ] **Step 3: Write `HomeActivity.kt` (nav host + wiring)**

```kotlin
package online.k73.bmwlauncher.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import online.k73.bmwlauncher.autostart.AutostartController
import online.k73.bmwlauncher.data.LauncherSettings
import online.k73.bmwlauncher.data.SettingsStore
import online.k73.bmwlauncher.data.ThemeMode
import online.k73.bmwlauncher.launch.AppLauncher
import online.k73.bmwlauncher.launch.InstalledApps
import online.k73.bmwlauncher.system.RootShell
import online.k73.bmwlauncher.system.ShellCommands
import online.k73.bmwlauncher.theme.ThemeResolver
import online.k73.bmwlauncher.ui.apps.AppsScreen
import online.k73.bmwlauncher.ui.home.HomeScreen
import online.k73.bmwlauncher.ui.home.TileId
import online.k73.bmwlauncher.ui.settings.SettingsScreen
import online.k73.bmwlauncher.ui.theme.BmwLauncherTheme
import java.time.LocalTime

class HomeActivity : ComponentActivity() {
    private val store by lazy { SettingsStore(applicationContext) }
    private val launcher by lazy { AppLauncher(applicationContext) }
    private val shell by lazy { RootShell() }
    private var autostartDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsFlow = store.flow.stateIn(lifecycleScope, SharingStarted.Eagerly, LauncherSettings())
        setContent {
            val settings by settingsFlow.collectAsState()
            val isNight = ThemeResolver.isNight(settings.themeMode, LocalTime.now(), settings.nightStartHour, settings.nightEndHour)
            BmwLauncherTheme(isNight = isNight) {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(onTile = { id ->
                            when (id) {
                                TileId.MUSIC -> nav.navigate("music_stub")
                                TileId.APPS -> nav.navigate("apps")
                                TileId.SETTINGS -> nav.navigate("settings")
                                TileId.NAV -> launcher.launch(settings.navPackage)
                                TileId.IBUS -> launcher.launch(settings.iBusPackage)
                                TileId.CARPLAY -> launcher.launch(settings.carplayPackage)
                            }
                        })
                    }
                    composable("apps") {
                        AppsScreen(
                            apps = InstalledApps(applicationContext).list(),
                            onLaunch = { launcher.launch(it) },
                            onRebootHold = { shell.exec(ShellCommands.reboot()) },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            settings = settings,
                            onAutostart = { lifecycleScope.launch { store.setAutostartIBus(it) } },
                            onBringToFront = { lifecycleScope.launch { store.setBringToFront(it) } },
                            onThemeMode = { lifecycleScope.launch { store.setThemeMode(it) } },
                        )
                    }
                    // Phase 2 replaces this stub with the real Music screen.
                    composable("music_stub") { HomeScreen() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!autostartDone) {
            autostartDone = true
            lifecycleScope.launch {
                val s = store.read()
                if (s.bringLauncherToFront) {
                    startActivity(Intent(this@HomeActivity, HomeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                }
                if (s.autostartIBus && s.iBusPackage.isNotBlank()) {
                    AutostartController(shell, onLaunched = {
                        startActivity(Intent(this@HomeActivity, HomeActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    }).ensureRunning(s.iBusPackage)
                }
            }
        }
    }
}
```

- [ ] **Step 4: Write `BootReceiver.kt`**

```kotlin
package online.k73.bmwlauncher.autostart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import online.k73.bmwlauncher.ui.HomeActivity

/**
 * On boot, bring the launcher HOME to the foreground. The heavy lifting (i-Bus autostart with
 * retries) runs in HomeActivity.onResume so it shares the same coroutine scope and lifecycle.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startActivity(
                Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
```

- [ ] **Step 5: Update `AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"
        tools:ignore="QueryAllPackagesPermission" xmlns:tools="http://schemas.android.com/tools" />

    <application
        android:name=".MainApplication"
        android:label="BMW Launcher"
        android:theme="@android:style/Theme.Material.NoActionBar">

        <activity
            android:name=".ui.HomeActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:stateNotNeeded="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <receiver android:name=".autostart.BootReceiver" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

- [ ] **Step 6: Run the activity test + full build**

Run: `./gradlew :app:testDebugUnitTest --tests '*HomeActivityTest*' --no-daemon`
Expected: PASS.
Run: `./gradlew :app:assembleDebug --no-daemon`
Expected: `BUILD SUCCESSFUL`; `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/ui/HomeActivity.kt app/src/main/java/online/k73/bmwlauncher/autostart/BootReceiver.kt app/src/main/AndroidManifest.xml app/src/test/java/online/k73/bmwlauncher/ui/HomeActivityTest.kt
git commit -m "feat(ui): wire HomeActivity nav, boot receiver, and HOME manifest"
```

---

### Task 1.13: Full Phase-1 test pass + APK for the car

**Files:** none (verification)

- [ ] **Step 1: Run the whole unit-test suite**

Run: `./gradlew :app:testDebugUnitTest --no-daemon`
Expected: all tests PASS (LauncherSettings, ThemeResolver, SettingsStore, ShellCommands, AutostartController, AppLauncher, InstalledApps, HomeActivity).

- [ ] **Step 2: Verify all Paparazzi goldens**

Run: `./gradlew :app:verifyPaparazziDebug --no-daemon`
Expected: PASS (Home, Settings).

- [ ] **Step 3: Assemble the release-candidate debug APK**

Run: `./gradlew :app:assembleDebug --no-daemon`
Expected: `app/build/outputs/apk/debug/app-debug.apk` produced.

- [ ] **Step 4: On-device checklist (manual, in the car — document results)**

Sideload and verify:
1. `adb install -r app-debug.apk`; set as default HOME (Settings → Apps → Default apps → Home app → BMW Launcher).
2. Fill `iBusPackage` and `carplayPackage` in Settings (get real package names via `adb shell pm list packages | grep -i ibus`).
3. Reboot the unit → launcher appears; i-Bus App autostarts in background within ~90s (verify with `adb shell pidof <ibus_pkg>`); no visible flash.
4. Tap Борткомпьютер → i-Bus comes to front. Tap Навигация → Yandex Navigator opens. Tap CarPlay → configured app opens.
5. Apps screen → long-press Перезагрузка → unit reboots (root prompt granted once).
6. Toggle theme День/Ночь/Авто → colors change.

Record findings (esp. the four open risks from spec §11) in the Phase 2 planning notes.

- [ ] **Step 5: Commit any package-name defaults discovered**

```bash
# after learning the real i-Bus package on the car, set it as the default in LauncherSettings.kt
git add -A && git commit -m "chore: set discovered i-Bus/CarPlay package defaults"
```

---

## Self-Review (spec coverage)

- Autostart (spec §4) → Tasks 1.4–1.6, 1.12 (retry state machine + boot wiring + re-foreground). ✓
- Tile behaviors (spec §5) → Task 1.9 (grid) + 1.12 (routing: internal nav vs `AppLauncher`). ✓
- Apps screen + reboot (spec §5, §8) → Task 1.10. ✓
- Theme day/night/auto (spec §7) → Tasks 1.2, 1.8, 1.12; astronomical calc explicitly deferred to Phase 3 (fixed window used, per spec "fixed fallback"). ✓
- Settings (spec §3, §8) → Tasks 1.1, 1.3, 1.11. ✓
- Reboot via root (spec §8) → Tasks 1.4, 1.5, 1.10. ✓
- Default HOME (spec §8) → Task 1.12 manifest + 1.13 on-device step. ✓
- Music (spec §6) → **Phase 2** (stub route present in Task 1.12). Intentionally out of this plan.
- Playlists/MediaBrowser (spec §6) → **Phase 3**.
- Testing (spec §10) → Robolectric unit tests + Paparazzi goldens throughout; on-device checklist in 1.13. ✓

**Type consistency:** `Shell.exec`, `ShellCommands.{reboot,pidof,startPackage}`, `AutostartController.ensureRunning`, `AppLauncher.{isInstalled,launchIntentFor,launch}`, `SettingsStore.{read,flow,set*}`, `ThemeResolver.isNight`, `LauncherSettings` fields, `TileId` — names used consistently across Tasks 1.1–1.13. ✓

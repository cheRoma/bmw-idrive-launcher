# BMW Launcher — In-app OTA Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a manual "Проверить обновление" button in Settings that checks a remote manifest, downloads a newer APK, and installs it — silently via root if available, else through the system installer — so Roma never sideloads via flash drive again.

**Architecture:** Small isolated units behind interfaces: manifest parse, version check, HTTP (over HttpURLConnection), root detection, APK download, and a root-adaptive installer. Pure logic is unit-tested headless with Robolectric/JUnit and fakes; network + real install are device-verified. UI is a new "Обновление" section in the existing `SettingsScreen`, driven by an `UpdateUiState` the `HomeActivity` owns. A dedicated release keystore makes install-over-existing signature-consistent.

**Tech Stack:** Kotlin, Jetpack Compose, HttpURLConnection, org.json, existing `Shell`/`RootShell`/`ShellCommands`; tests JUnit4 + Robolectric 4.12.

**Package:** `online.k73.bmwlauncher`
**Spec:** `docs/superpowers/specs/2026-07-03-bmw-launcher-ota-update-design.md`
**Build env (every gradle call):** `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_SDK_ROOT=$HOME/android-sdk` then `cd /home/roma/Projects/bmw && ./gradlew <task> --no-daemon --console=plain`

---

## File Structure

```
app/src/main/java/online/k73/bmwlauncher/
  update/UpdateManifest.kt        data class + parse(json)
  update/UpdateStatus.kt          sealed result of a version check
  update/HttpClient.kt            interface (getText, download)
  update/HttpUrlClient.kt         HttpURLConnection impl
  update/UpdateChecker.kt         check(currentCode,manifest) + fetch(currentCode)
  update/RootDetector.kt          hasRoot() via Shell
  update/ApkDownloader.kt         download(url,onProgress) -> File
  update/ApkInstaller.kt          install(file): root pm install OR installer intent
  update/UpdateUiState.kt         sealed UI state
  system/Shell.kt                 (MODIFY: add ShellCommands.installAndRelaunch)
  ui/settings/SettingsScreen.kt   (MODIFY: add "Обновление" section)
  ui/HomeActivity.kt              (MODIFY: wire update flow + version + root status)
app/src/main/res/xml/file_paths.xml   FileProvider paths (cacheDir)
app/src/main/AndroidManifest.xml      (MODIFY: INTERNET, REQUEST_INSTALL_PACKAGES, FileProvider)
app/build.gradle.kts                  (MODIFY: buildConfig=true, versionCode=2, signingConfigs.release)
keystore.properties                   (gitignored; VPS only)
scripts/release.sh                    VPS release: bump + build + publish APK + latest.json
```

---

### Task 1: Enable BuildConfig + permissions

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Enable buildConfig and bump versionCode in `app/build.gradle.kts`**

In the `android { ... }` block, change `buildFeatures` and `defaultConfig.versionCode`:
```kotlin
    defaultConfig {
        applicationId = "online.k73.bmwlauncher"
        minSdk = 26
        targetSdk = 33
        versionCode = 2
        versionName = "1.0.1"
    }
```
```kotlin
    buildFeatures { compose = true; buildConfig = true }
```

- [ ] **Step 2: Add permissions + FileProvider to `AndroidManifest.xml`**

Add these two `<uses-permission>` lines after the existing ones (inside `<manifest>`, before `<application>`):
```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```
Add this `<provider>` inside `<application>` (after the `<receiver>`):
```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Step 3: Create `app/src/main/res/xml/file_paths.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="update_cache" path="." />
</paths>
```

- [ ] **Step 4: Verify it builds**

Run: `./gradlew :app:assembleDebug --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/res/xml/file_paths.xml
git commit -m "chore(update): enable BuildConfig, add INTERNET/install permissions + FileProvider"
```

---

### Task 2: `UpdateManifest` + parser

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/update/UpdateManifest.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/update/UpdateManifestTest.kt`

- [ ] **Step 1: Write the failing test (Robolectric — org.json needs Android)**

```kotlin
package online.k73.bmwlauncher.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateManifestTest {
    @Test fun parses_valid_json() {
        val json = """{"versionCode":3,"versionName":"1.0.2","apkUrl":"https://x/y.apk","notes":"hi"}"""
        val m = UpdateManifest.parse(json)
        assertEquals(3, m.versionCode)
        assertEquals("1.0.2", m.versionName)
        assertEquals("https://x/y.apk", m.apkUrl)
        assertEquals("hi", m.notes)
    }
    @Test fun notes_defaults_to_empty_when_missing() {
        val m = UpdateManifest.parse("""{"versionCode":1,"versionName":"1.0","apkUrl":"u"}""")
        assertEquals("", m.notes)
    }
    @Test fun throws_on_malformed_json() {
        assertThrows(Exception::class.java) { UpdateManifest.parse("not json") }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*UpdateManifestTest*' --no-daemon --console=plain`
Expected: FAIL — `UpdateManifest` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package online.k73.bmwlauncher.update

import org.json.JSONObject

data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
) {
    companion object {
        fun parse(json: String): UpdateManifest {
            val o = JSONObject(json)
            return UpdateManifest(
                versionCode = o.getInt("versionCode"),
                versionName = o.getString("versionName"),
                apkUrl = o.getString("apkUrl"),
                notes = o.optString("notes", ""),
            )
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*UpdateManifestTest*' --no-daemon --console=plain`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/update/UpdateManifest.kt app/src/test/java/online/k73/bmwlauncher/update/UpdateManifestTest.kt
git commit -m "feat(update): add UpdateManifest and JSON parser"
```

---

### Task 3: `UpdateStatus` + version check

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/update/UpdateStatus.kt`
- Create: `app/src/main/java/online/k73/bmwlauncher/update/UpdateChecker.kt` (check() only in this task)
- Test: `app/src/test/java/online/k73/bmwlauncher/update/UpdateCheckerCompareTest.kt`

- [ ] **Step 1: Write the failing test (pure)**

```kotlin
package online.k73.bmwlauncher.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerCompareTest {
    private val m = UpdateManifest(versionCode = 5, versionName = "1.0.4", apkUrl = "u", notes = "n")

    @Test fun newer_manifest_is_available() {
        val s = UpdateChecker.compare(currentCode = 3, manifest = m)
        assertTrue(s is UpdateStatus.Available)
        assertEquals("1.0.4", (s as UpdateStatus.Available).versionName)
        assertEquals("u", s.apkUrl)
    }
    @Test fun equal_version_is_up_to_date() {
        assertEquals(UpdateStatus.UpToDate, UpdateChecker.compare(5, m))
    }
    @Test fun older_manifest_is_up_to_date() {
        assertEquals(UpdateStatus.UpToDate, UpdateChecker.compare(9, m))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*UpdateCheckerCompareTest*' --no-daemon --console=plain`
Expected: FAIL — `UpdateStatus`/`UpdateChecker` unresolved.

- [ ] **Step 3: Write `UpdateStatus.kt`**

```kotlin
package online.k73.bmwlauncher.update

sealed interface UpdateStatus {
    data object UpToDate : UpdateStatus
    data class Available(val versionName: String, val apkUrl: String, val notes: String) : UpdateStatus
    data class Error(val reason: String) : UpdateStatus
}
```

- [ ] **Step 4: Write `UpdateChecker.kt` (compare only for now)**

```kotlin
package online.k73.bmwlauncher.update

class UpdateChecker(
    private val http: HttpClient,
    private val manifestUrl: String,
) {
    companion object {
        fun compare(currentCode: Int, manifest: UpdateManifest): UpdateStatus =
            if (manifest.versionCode > currentCode)
                UpdateStatus.Available(manifest.versionName, manifest.apkUrl, manifest.notes)
            else UpdateStatus.UpToDate
    }
}
```
Note: this file references `HttpClient` (created in Task 4). It will not compile until Task 4 adds `HttpClient`. Run the compare test after Task 4, OR temporarily comment the constructor — but simplest is to do Task 4 immediately next and run both tests together. Proceed to Task 4 before running gradle.

- [ ] **Step 5: (deferred) run + commit together with Task 4**

Do not run/commit yet; Task 4 adds `HttpClient` so the module compiles.

---

### Task 4: `HttpClient` interface + `UpdateChecker.fetch`

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/update/HttpClient.kt`
- Modify: `app/src/main/java/online/k73/bmwlauncher/update/UpdateChecker.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/update/UpdateCheckerFetchTest.kt`

- [ ] **Step 1: Write `HttpClient.kt`**

```kotlin
package online.k73.bmwlauncher.update

import java.io.File

interface HttpClient {
    /** Fetch a URL body as text. Throws on network/HTTP error. */
    fun getText(url: String): String

    /** Download a URL to [dest], reporting integer percent [0..100]. Throws on error. */
    fun download(url: String, dest: File, onProgress: (percent: Int) -> Unit)
}
```

- [ ] **Step 2: Write the failing fetch test (fake HttpClient)**

```kotlin
package online.k73.bmwlauncher.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class FakeHttp(val body: String? = null, val boom: Boolean = false) : HttpClient {
    override fun getText(url: String): String {
        if (boom) throw IOException("network down")
        return body!!
    }
    override fun download(url: String, dest: File, onProgress: (Int) -> Unit) {}
}

@RunWith(RobolectricTestRunner::class)
class UpdateCheckerFetchTest {
    private val json = """{"versionCode":5,"versionName":"1.0.4","apkUrl":"u","notes":"n"}"""

    @Test fun fetch_returns_available_when_newer() {
        val checker = UpdateChecker(FakeHttp(body = json), "https://manifest")
        val s = checker.fetch(currentCode = 3)
        assertTrue(s is UpdateStatus.Available)
    }
    @Test fun fetch_returns_up_to_date_when_same() {
        val checker = UpdateChecker(FakeHttp(body = json), "https://manifest")
        assertEquals(UpdateStatus.UpToDate, checker.fetch(currentCode = 5))
    }
    @Test fun fetch_returns_error_on_network_failure() {
        val checker = UpdateChecker(FakeHttp(boom = true), "https://manifest")
        val s = checker.fetch(currentCode = 1)
        assertTrue(s is UpdateStatus.Error)
    }
    @Test fun fetch_returns_error_on_bad_json() {
        val checker = UpdateChecker(FakeHttp(body = "garbage"), "https://manifest")
        assertTrue(checker.fetch(1) is UpdateStatus.Error)
    }
}
```

- [ ] **Step 3: Add `fetch` to `UpdateChecker.kt`**

Replace the file body with:
```kotlin
package online.k73.bmwlauncher.update

class UpdateChecker(
    private val http: HttpClient,
    private val manifestUrl: String,
) {
    /** Fetches the manifest and compares against [currentCode]. Never throws — returns Error. */
    fun fetch(currentCode: Int): UpdateStatus =
        try {
            val manifest = UpdateManifest.parse(http.getText(manifestUrl))
            compare(currentCode, manifest)
        } catch (t: Throwable) {
            UpdateStatus.Error(t.message ?: "update check failed")
        }

    companion object {
        fun compare(currentCode: Int, manifest: UpdateManifest): UpdateStatus =
            if (manifest.versionCode > currentCode)
                UpdateStatus.Available(manifest.versionName, manifest.apkUrl, manifest.notes)
            else UpdateStatus.UpToDate
    }
}
```

- [ ] **Step 4: Run both checker tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*UpdateChecker*' --no-daemon --console=plain`
Expected: PASS (compare: 3, fetch: 4).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/update/UpdateStatus.kt app/src/main/java/online/k73/bmwlauncher/update/UpdateChecker.kt app/src/main/java/online/k73/bmwlauncher/update/HttpClient.kt app/src/test/java/online/k73/bmwlauncher/update/UpdateCheckerCompareTest.kt app/src/test/java/online/k73/bmwlauncher/update/UpdateCheckerFetchTest.kt
git commit -m "feat(update): add HttpClient interface, UpdateStatus, and UpdateChecker"
```

---

### Task 5: `HttpUrlClient` (real HTTP)

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/update/HttpUrlClient.kt`
- Test: none (network I/O; verified on device). Compile-only.

- [ ] **Step 1: Write the implementation**

```kotlin
package online.k73.bmwlauncher.update

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class HttpUrlClient : HttpClient {
    override fun getText(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 10_000; requestMethod = "GET"
        }
        try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }

    override fun download(url: String, dest: File, onProgress: (Int) -> Unit) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 30_000; requestMethod = "GET"
        }
        try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLength.toLong()
            var read = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress(((read * 100) / total).toInt())
                    }
                }
            }
            onProgress(100)
        } finally { conn.disconnect() }
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `./gradlew :app:compileDebugKotlin --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/update/HttpUrlClient.kt
git commit -m "feat(update): add HttpURLConnection-based HttpClient"
```

---

### Task 6: `ShellCommands.installAndRelaunch` (+ RootDetector command)

**Files:**
- Modify: `app/src/main/java/online/k73/bmwlauncher/system/Shell.kt` (add to `ShellCommands`)
- Test: `app/src/test/java/online/k73/bmwlauncher/system/ShellCommandsInstallTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package online.k73.bmwlauncher.system

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellCommandsInstallTest {
    @Test fun install_and_relaunch_is_a_single_su_command() {
        assertEquals(
            "pm install -r /data/local/tmp/u.apk && am start -n online.k73.bmwlauncher/.ui.HomeActivity",
            ShellCommands.installAndRelaunch("/data/local/tmp/u.apk", "online.k73.bmwlauncher/.ui.HomeActivity")
        )
    }
    @Test fun whoami_is_id() {
        assertEquals("id", ShellCommands.id())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ShellCommandsInstallTest*' --no-daemon --console=plain`
Expected: FAIL — `installAndRelaunch`/`id` unresolved.

- [ ] **Step 3: Add to the `ShellCommands` object in `system/Shell.kt`**

Add these functions inside `object ShellCommands { ... }` (alongside `reboot`, `pidof`, `startPackage`):
```kotlin
    fun id(): String = "id"
    // Install over the existing app and immediately relaunch, as ONE su invocation so it
    // survives our own process being killed during the reinstall.
    fun installAndRelaunch(apkPath: String, component: String): String =
        "pm install -r $apkPath && am start -n $component"
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*ShellCommandsInstallTest*' --no-daemon --console=plain`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/system/Shell.kt app/src/test/java/online/k73/bmwlauncher/system/ShellCommandsInstallTest.kt
git commit -m "feat(system): add install-and-relaunch and id shell commands"
```

---

### Task 7: `RootDetector`

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/update/RootDetector.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/update/RootDetectorTest.kt`

- [ ] **Step 1: Write the failing test (fake Shell)**

```kotlin
package online.k73.bmwlauncher.update

import online.k73.bmwlauncher.system.Shell
import online.k73.bmwlauncher.system.ShellResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeShell(val result: ShellResult) : Shell {
    var lastCommand: String? = null
    override fun exec(command: String): ShellResult { lastCommand = command; return result }
}

class RootDetectorTest {
    @Test fun uid_zero_means_root() {
        val d = RootDetector(FakeShell(ShellResult(0, "uid=0(root) gid=0(root)", "")))
        assertTrue(d.hasRoot())
    }
    @Test fun non_root_uid_means_no_root() {
        val d = RootDetector(FakeShell(ShellResult(0, "uid=10123(u0_a123)", "")))
        assertFalse(d.hasRoot())
    }
    @Test fun failed_su_means_no_root() {
        val d = RootDetector(FakeShell(ShellResult(127, "", "su: not found")))
        assertFalse(d.hasRoot())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*RootDetectorTest*' --no-daemon --console=plain`
Expected: FAIL — `RootDetector` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package online.k73.bmwlauncher.update

import online.k73.bmwlauncher.system.Shell
import online.k73.bmwlauncher.system.ShellCommands

class RootDetector(private val shell: Shell) {
    private var cached: Boolean? = null

    /** True if `su -c id` reports uid=0. Result cached for the session. */
    fun hasRoot(): Boolean {
        cached?.let { return it }
        val r = shell.exec(ShellCommands.id())
        val result = r.ok && r.stdout.contains("uid=0")
        cached = result
        return result
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*RootDetectorTest*' --no-daemon --console=plain`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/update/RootDetector.kt app/src/test/java/online/k73/bmwlauncher/update/RootDetectorTest.kt
git commit -m "feat(update): add RootDetector via su id"
```

---

### Task 8: `ApkDownloader`

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/update/ApkDownloader.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/update/ApkDownloaderTest.kt`

- [ ] **Step 1: Write the failing test (fake HttpClient writes a file)**

```kotlin
package online.k73.bmwlauncher.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private class WritingHttp(val bytes: ByteArray) : HttpClient {
    override fun getText(url: String) = ""
    override fun download(url: String, dest: File, onProgress: (Int) -> Unit) {
        dest.writeBytes(bytes); onProgress(100)
    }
}

class ApkDownloaderTest {
    @Test fun downloads_to_cache_and_reports_progress() {
        val tmp = File.createTempFile("cache", "").parentFile
        val http = WritingHttp("APKDATA".toByteArray())
        var lastPercent = -1
        val downloader = ApkDownloader(http, tmp)
        val file = downloader.download("https://x/app.apk") { lastPercent = it }
        assertTrue(file.exists())
        assertEquals("APKDATA", file.readText())
        assertEquals(100, lastPercent)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ApkDownloaderTest*' --no-daemon --console=plain`
Expected: FAIL — `ApkDownloader` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package online.k73.bmwlauncher.update

import java.io.File

class ApkDownloader(private val http: HttpClient, private val cacheDir: File) {
    /** Downloads [url] to cacheDir/update.apk, reporting percent. Returns the file. */
    fun download(url: String, onProgress: (Int) -> Unit): File {
        val dest = File(cacheDir, "update.apk")
        if (dest.exists()) dest.delete()
        http.download(url, dest, onProgress)
        return dest
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*ApkDownloaderTest*' --no-daemon --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/update/ApkDownloader.kt app/src/test/java/online/k73/bmwlauncher/update/ApkDownloaderTest.kt
git commit -m "feat(update): add ApkDownloader"
```

---

### Task 9: `ApkInstaller` (root-adaptive)

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/update/ApkInstaller.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/update/ApkInstallerTest.kt`

- [ ] **Step 1: Write the failing test (root branch via fake Shell; intent branch via injected launcher)**

```kotlin
package online.k73.bmwlauncher.update

import online.k73.bmwlauncher.system.Shell
import online.k73.bmwlauncher.system.ShellResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private class RecordingShell(val ok: Boolean) : Shell {
    val commands = mutableListOf<String>()
    override fun exec(command: String): ShellResult {
        commands += command
        return if (ok) ShellResult(0, "Success", "") else ShellResult(1, "", "signatures do not match")
    }
}

class ApkInstallerTest {
    private val apk = File("/tmp/update.apk")

    @Test fun root_path_runs_pm_install_and_returns_silent() {
        val shell = RecordingShell(ok = true)
        var intentLaunched = false
        val installer = ApkInstaller(hasRoot = { true }, shell = shell, component = "online.k73.bmwlauncher/.ui.HomeActivity", launchInstaller = { intentLaunched = true })
        val res = installer.install(apk)
        assertEquals(InstallResult.InstalledSilently, res)
        assertTrue(shell.commands.any { it.startsWith("pm install -r /tmp/update.apk") })
        assertTrue(!intentLaunched)
    }

    @Test fun root_install_failure_returns_failed_with_stderr() {
        val shell = RecordingShell(ok = false)
        val installer = ApkInstaller(hasRoot = { true }, shell = shell, component = "c", launchInstaller = { })
        val res = installer.install(apk)
        assertTrue(res is InstallResult.Failed)
        assertTrue((res as InstallResult.Failed).message.contains("signatures"))
    }

    @Test fun no_root_launches_system_installer() {
        val shell = RecordingShell(ok = true)
        var launchedFile: File? = null
        val installer = ApkInstaller(hasRoot = { false }, shell = shell, component = "c", launchInstaller = { launchedFile = it })
        val res = installer.install(apk)
        assertEquals(InstallResult.LaunchedInstaller, res)
        assertEquals(apk, launchedFile)
        assertTrue(shell.commands.isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ApkInstallerTest*' --no-daemon --console=plain`
Expected: FAIL — `ApkInstaller`/`InstallResult` unresolved.

- [ ] **Step 3: Write minimal implementation (installer logic; intent-launch injected for testability)**

```kotlin
package online.k73.bmwlauncher.update

import online.k73.bmwlauncher.system.Shell
import online.k73.bmwlauncher.system.ShellCommands
import java.io.File

sealed interface InstallResult {
    data object InstalledSilently : InstallResult
    data object LaunchedInstaller : InstallResult
    data class Failed(val message: String) : InstallResult
}

/**
 * Root-adaptive installer. With root, runs `pm install -r <apk> && am start ...` as one su call.
 * Without root, delegates to [launchInstaller] which fires the system PackageInstaller intent.
 * The intent launch is injected so this class is unit-testable without Android.
 */
class ApkInstaller(
    private val hasRoot: () -> Boolean,
    private val shell: Shell,
    private val component: String,
    private val launchInstaller: (File) -> Unit,
) {
    fun install(apk: File): InstallResult {
        if (hasRoot()) {
            val r = shell.exec(ShellCommands.installAndRelaunch(apk.absolutePath, component))
            return if (r.ok) InstallResult.InstalledSilently
            else InstallResult.Failed(r.stderr.ifBlank { "pm install failed (exit ${r.exitCode})" })
        }
        launchInstaller(apk)
        return InstallResult.LaunchedInstaller
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*ApkInstallerTest*' --no-daemon --console=plain`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/update/ApkInstaller.kt app/src/test/java/online/k73/bmwlauncher/update/ApkInstallerTest.kt
git commit -m "feat(update): add root-adaptive ApkInstaller"
```

---

### Task 10: `UpdateUiState` + Settings "Обновление" section

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/update/UpdateUiState.kt`
- Modify: `app/src/main/java/online/k73/bmwlauncher/ui/settings/SettingsScreen.kt`
- Test: re-record the Settings Paparazzi golden (visual change).

- [ ] **Step 1: Write `UpdateUiState.kt`**

```kotlin
package online.k73.bmwlauncher.update

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val versionName: String, val apkUrl: String, val notes: String) : UpdateUiState
    data class Downloading(val percent: Int) : UpdateUiState
    data object Installing : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}
```

- [ ] **Step 2: Add the "Обновление" section to `SettingsScreen.kt`**

Change the `SettingsScreen` signature and append the update block. Replace the composable's parameter list and add the section after the theme row. New signature + added UI:
```kotlin
@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    onAutostart: (Boolean) -> Unit,
    onBringToFront: (Boolean) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    currentVersion: String,
    hasRoot: Boolean,
    updateState: online.k73.bmwlauncher.update.UpdateUiState,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
```
Add this block just before the closing brace of the root `Column` (after the theme selector Row):
```kotlin
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Обновление", color = c.text, fontSize = 22.sp)
                Text(
                    "Версия $currentVersion · root: ${if (hasRoot) "да" else "нет"}",
                    color = c.textDim, fontSize = 16.sp,
                )
                when (val s = updateState) {
                    online.k73.bmwlauncher.update.UpdateUiState.Checking ->
                        Text("Проверка…", color = c.textDim, fontSize = 16.sp)
                    online.k73.bmwlauncher.update.UpdateUiState.UpToDate ->
                        Text("Установлена последняя версия", color = c.textDim, fontSize = 16.sp)
                    is online.k73.bmwlauncher.update.UpdateUiState.Available ->
                        Text("Доступна ${s.versionName}: ${s.notes}", color = c.accent, fontSize = 16.sp)
                    is online.k73.bmwlauncher.update.UpdateUiState.Downloading ->
                        Text("Скачивание ${s.percent}%", color = c.textDim, fontSize = 16.sp)
                    online.k73.bmwlauncher.update.UpdateUiState.Installing ->
                        Text("Установка…", color = c.textDim, fontSize = 16.sp)
                    is online.k73.bmwlauncher.update.UpdateUiState.Failed ->
                        Text("Ошибка: ${s.message}", color = c.accent, fontSize = 16.sp)
                    online.k73.bmwlauncher.update.UpdateUiState.Idle -> {}
                }
            }
            val available = updateState is online.k73.bmwlauncher.update.UpdateUiState.Available
            Text(
                if (available) "Обновить" else "Проверить",
                color = c.accent, fontSize = 20.sp,
                modifier = Modifier.padding(8.dp).clickableNoRipple {
                    if (available) onInstallUpdate() else onCheckUpdate()
                },
            )
        }
```

- [ ] **Step 3: Update the existing Settings Paparazzi test to pass the new params**

In `app/src/test/java/online/k73/bmwlauncher/screenshot/SettingsScreenScreenshotTest.kt`, update the `SettingsScreen(...)` call:
```kotlin
                SettingsScreen(
                    LauncherSettings(), {}, {}, {},
                    currentVersion = "1.0.1",
                    hasRoot = false,
                    updateState = online.k73.bmwlauncher.update.UpdateUiState.Idle,
                    onCheckUpdate = {},
                    onInstallUpdate = {},
                )
```

- [ ] **Step 4: Re-record + verify the golden, and visually confirm**

Run: `./gradlew :app:recordPaparazziDebug --tests '*SettingsScreenScreenshotTest*' --no-daemon --console=plain`
Then Read `app/src/test/snapshots/images/online.k73.bmwlauncher.screenshot_SettingsScreenScreenshotTest_settings_night.png` — confirm the new "Обновление" row with "Версия 1.0.1 · root: нет" and a "Проверить" action appears, still within 720px.
Then: `./gradlew :app:verifyPaparazziDebug --tests '*SettingsScreenScreenshotTest*' --no-daemon --console=plain` → PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/update/UpdateUiState.kt app/src/main/java/online/k73/bmwlauncher/ui/settings/SettingsScreen.kt app/src/test/java/online/k73/bmwlauncher/screenshot/SettingsScreenScreenshotTest.kt app/src/test/snapshots/
git commit -m "feat(ui): add Обновление section to Settings"
```

---

### Task 11: Wire the update flow in `HomeActivity`

**Files:**
- Modify: `app/src/main/java/online/k73/bmwlauncher/ui/HomeActivity.kt`
- Test: none new (integration; existing HomeActivityTest still runs). Compile + full suite.

- [ ] **Step 1: Add update wiring to `HomeActivity`**

Add these imports:
```kotlin
import androidx.compose.runtime.mutableStateOf as composeMutableStateOf
import androidx.core.content.FileProvider
import online.k73.bmwlauncher.BuildConfig
import online.k73.bmwlauncher.update.ApkDownloader
import online.k73.bmwlauncher.update.ApkInstaller
import online.k73.bmwlauncher.update.HttpUrlClient
import online.k73.bmwlauncher.update.InstallResult
import online.k73.bmwlauncher.update.RootDetector
import online.k73.bmwlauncher.update.UpdateChecker
import online.k73.bmwlauncher.update.UpdateStatus
import online.k73.bmwlauncher.update.UpdateUiState
import android.content.Intent
import android.net.Uri
```
Add fields to the class (near `store`/`launcher`/`shell`):
```kotlin
    private val rootDetector by lazy { RootDetector(shell) }
    private val updateChecker by lazy { UpdateChecker(HttpUrlClient(), MANIFEST_URL) }
    private val downloader by lazy { ApkDownloader(HttpUrlClient(), cacheDir) }
    private val apkInstaller by lazy {
        ApkInstaller(
            hasRoot = { rootDetector.hasRoot() },
            shell = shell,
            component = "$packageName/.ui.HomeActivity",
            launchInstaller = { file -> launchSystemInstaller(file) },
        )
    }
    private val updateState = androidx.compose.runtime.mutableStateOf<UpdateUiState>(UpdateUiState.Idle)

    companion object { const val MANIFEST_URL = "https://k73.online/newBMW/latest.json" }

    private fun launchSystemInstaller(file: java.io.File) {
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun onCheckUpdate() {
        updateState.value = UpdateUiState.Checking
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val status = updateChecker.fetch(BuildConfig.VERSION_CODE)
            updateState.value = when (status) {
                is UpdateStatus.UpToDate -> UpdateUiState.UpToDate
                is UpdateStatus.Available -> UpdateUiState.Available(status.versionName, status.apkUrl, status.notes)
                is UpdateStatus.Error -> UpdateUiState.Failed(status.reason)
            }
        }
    }

    private fun onInstallUpdate() {
        val avail = updateState.value as? UpdateUiState.Available ?: return
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = downloader.download(avail.apkUrl) { p -> updateState.value = UpdateUiState.Downloading(p) }
                updateState.value = UpdateUiState.Installing
                when (val r = apkInstaller.install(file)) {
                    is InstallResult.Failed -> updateState.value = UpdateUiState.Failed(r.message)
                    else -> { /* silent: process restarts; intent: system UI takes over */ }
                }
            } catch (t: Throwable) {
                updateState.value = UpdateUiState.Failed(t.message ?: "download failed")
            }
        }
    }
```
In `setContent`, pass the new params to `SettingsScreen`:
```kotlin
                    composable("settings") {
                        val update by updateState
                        SettingsScreen(
                            settings = settings,
                            onAutostart = { lifecycleScope.launch { store.setAutostartIBus(it) } },
                            onBringToFront = { lifecycleScope.launch { store.setBringToFront(it) } },
                            onThemeMode = { lifecycleScope.launch { store.setThemeMode(it) } },
                            currentVersion = BuildConfig.VERSION_NAME,
                            hasRoot = rootDetector.hasRoot(),
                            updateState = update,
                            onCheckUpdate = { onCheckUpdate() },
                            onInstallUpdate = { onInstallUpdate() },
                        )
                    }
```
Ensure `import androidx.compose.runtime.getValue` is present (it already is from Phase 1).

- [ ] **Step 2: Build + run the full unit suite**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --console=plain`
Expected: all tests PASS (Phase 1 + new update tests).
Run: `./gradlew :app:assembleDebug --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/ui/HomeActivity.kt
git commit -m "feat(update): wire OTA check/download/install into HomeActivity settings"
```

---

### Task 12: Release keystore + signing + release script + latest.json

**Files:**
- Create: `keystore.properties` (VPS only; gitignored)
- Modify: `app/build.gradle.kts` (signingConfigs)
- Modify: `.gitignore`
- Create: `scripts/release.sh`

- [ ] **Step 1: Generate a stable release keystore (VPS, one-time)**

Run:
```bash
mkdir -p ~/keystores
keytool -genkeypair -v -keystore ~/keystores/bmw-release.jks \
  -alias bmw -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass bmwlauncher -keypass bmwlauncher \
  -dname "CN=BMW Launcher, OU=k73, O=k73, L=NA, S=NA, C=RU"
echo "BACK UP ~/keystores/bmw-release.jks — losing it breaks future OTA updates."
```

- [ ] **Step 2: Create `keystore.properties` (repo root, gitignored) and ignore it**

`keystore.properties`:
```properties
storeFile=/home/roma/keystores/bmw-release.jks
storePassword=bmwlauncher
keyAlias=bmw
keyPassword=bmwlauncher
```
Append to `.gitignore`:
```bash
printf '\n# Release signing (never commit)\nkeystore.properties\n*.jks\n' >> .gitignore
```

- [ ] **Step 3: Add `signingConfigs.release` to `app/build.gradle.kts`**

At the top of `app/build.gradle.kts` (after the `plugins {}` block), load the properties:
```kotlin
import java.util.Properties
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) load(f.inputStream())
}
```
Inside `android { }`, add before `buildTypes`:
```kotlin
    signingConfigs {
        create("release") {
            if (keystoreProps.getProperty("storeFile") != null) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
```
Change `buildTypes.release` to use it:
```kotlin
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
```

- [ ] **Step 4: Verify a signed release APK builds**

Run: `./gradlew :app:assembleRelease --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`; APK at `app/build/outputs/apk/release/app-release.apk`.
Verify it is signed: `~/android-sdk/build-tools/34.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk | head -3` → prints a certificate (no "DOES NOT VERIFY").

- [ ] **Step 5: Create `scripts/release.sh` (bump + build + publish + manifest)**

```bash
#!/usr/bin/env bash
set -e
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_SDK_ROOT="$HOME/android-sdk"
cd "$(dirname "$0")/.."

VC=$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts)
VN=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
NOTES="${1:-Обновление}"

./gradlew :app:assembleRelease --no-daemon --console=plain
APK=app/build/outputs/apk/release/app-release.apk

sudo docker cp "$APK" npm-app-1:/data/newBMW/bmw-launcher.apk
cat > /tmp/latest.json <<JSON
{"versionCode":$VC,"versionName":"$VN","apkUrl":"https://k73.online/newBMW/bmw-launcher.apk","notes":"$NOTES"}
JSON
sudo docker cp /tmp/latest.json npm-app-1:/data/newBMW/latest.json
sudo docker exec npm-app-1 sh -c 'chmod 644 /data/newBMW/bmw-launcher.apk /data/newBMW/latest.json'
echo "Published versionCode=$VC versionName=$VN"
curl -sk --resolve k73.online:443:127.0.0.1 https://k73.online/newBMW/latest.json
```
Make it executable: `chmod +x scripts/release.sh`.

- [ ] **Step 6: Publish the first release + manifest**

Run: `./scripts/release.sh "Кнопки главного экрана + OTA-обновление"`
Expected: prints `Published versionCode=2 versionName=1.0.1` and the JSON. Verify:
`curl -sk --resolve k73.online:443:127.0.0.1 https://k73.online/newBMW/latest.json` → the manifest with versionCode 2.

- [ ] **Step 7: Commit (build.gradle + script + gitignore only — NOT the keystore/properties)**

```bash
git add app/build.gradle.kts .gitignore scripts/release.sh
git commit -m "build(update): release signing config + VPS release script"
```

---

### Task 13: Full verification

**Files:** none.

- [ ] **Step 1: Full clean test + both APKs**

Run: `./gradlew clean :app:testDebugUnitTest :app:verifyPaparazziDebug :app:assembleDebug :app:assembleRelease --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`; all unit tests pass; Paparazzi goldens verify.

- [ ] **Step 2: On-device checklist (in the car — document results)**

1. One-time signing migration: `adb uninstall online.k73.bmwlauncher` then `adb install app/build/outputs/apk/release/app-release.apk` (now release-signed, versionCode 2).
2. Open Настройки → the "Обновление" row shows "Версия 1.0.1 · root: да/нет" (this reveals whether the unit is rooted).
3. Tap "Проверить" → should say "Установлена последняя версия" (manifest is also versionCode 2).
4. To test an actual update: bump versionCode to 3 in build.gradle, `./scripts/release.sh "тест обновления"`, then on the unit tap "Проверить" → "Доступна 1.0.2" → tap "Обновить" → root: silent + relaunch; no root: system installer appears, tap Установить.
5. Record whether root path or installer path was taken (answers spec §10 risk 1).

---

## Self-Review (spec coverage)

- Manual trigger button (spec §2) → Task 10 (UI) + Task 11 (wiring). ✓
- Root-adaptive install (spec §2, §4) → Task 7 (RootDetector), Task 9 (ApkInstaller), Task 11 (FileProvider intent). ✓
- Manifest + version compare (spec §3, §4) → Tasks 2, 3, 4. ✓
- HttpClient over HttpURLConnection (spec §4) → Tasks 4, 5. ✓
- Download with progress (spec §4) → Task 8. ✓
- Settings section with version + root status + states (spec §4) → Task 10. ✓
- Permissions INTERNET/REQUEST_INSTALL + FileProvider + buildConfig (spec §4) → Task 1. ✓
- Error handling (spec §6) → UpdateStatus.Error/UpdateUiState.Failed threaded through Tasks 3,4,9,11. ✓
- Signing keystore + release script + latest.json (spec §7) → Task 12. ✓
- Testing (spec §8) → unit tests in Tasks 2,3,4,6,7,8,9; on-device in Task 13. ✓
- `REQUEST_INSTALL_PACKAGES` grant route (spec §6): the system installer intent itself triggers the OS grant flow on first use; no extra screen needed. Noted.

**Type consistency:** `UpdateManifest(versionCode,versionName,apkUrl,notes)`, `UpdateStatus.{UpToDate,Available,Error}`, `UpdateChecker.{compare,fetch}`, `HttpClient.{getText,download}`, `RootDetector.hasRoot`, `ApkDownloader.download`, `ApkInstaller.install`→`InstallResult`, `UpdateUiState.*`, `ShellCommands.{id,installAndRelaunch}`, `SettingsScreen(...currentVersion,hasRoot,updateState,onCheckUpdate,onInstallUpdate)` — used consistently across Tasks 2–13. ✓

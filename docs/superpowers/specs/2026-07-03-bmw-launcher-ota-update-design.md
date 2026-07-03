# BMW Launcher — In-app OTA Update · Design/Spec

**Date:** 2026-07-03
**Status:** approved (ready for writing-plans)
**Feature owner:** Roma

---

## 1. Context & goal

Let the launcher update itself so Roma stops sideloading via flash drive / email + browser.
App `online.k73.bmwlauncher` (Kotlin + Compose), XTRONS Android 12 head unit. The debug APK is
already served at **https://k73.online/newBMW/bmw-launcher.apk**.

**Root status is UNKNOWN** — memory said "rooted" but Roma installed via the normal Android
installer (email link → download → system prompts), not root/adb. So the design **auto-detects root**
and works either way.

## 2. Decisions (locked)

- **Trigger: manual only.** A "Проверить обновление" button in Настройки. No auto-check, no
  background service, no boot scheduling.
- **Install: root-adaptive.**
  - If `su` works → **silent** `pm install -r <apk>` + relaunch. Zero prompts.
  - If no root → download the APK in-app, then launch the **system PackageInstaller** (intent).
    User taps "Установить" (a couple taps) — but no email/browser/flash drive.
- **Signature must match** for install-over-existing. Introduce a **stable release keystore** on the
  VPS (backed up), referenced in `signingConfigs`. **One-time:** Roma reinstalls the current build
  once with the new signing (uninstall old + install); after that, OTA works.
- **Version discipline:** each release bumps `versionCode` and updates `latest.json` (scripted on VPS).

## 3. Update manifest

`https://k73.online/newBMW/latest.json`:
```json
{ "versionCode": 3, "versionName": "1.0.2", "apkUrl": "https://k73.online/newBMW/bmw-launcher.apk", "notes": "Кнопки главного экрана + OTA-обновление" }
```
App reads its own `BuildConfig.VERSION_CODE` (requires `buildFeatures { buildConfig = true }`), compares
to `manifest.versionCode`.

## 4. Components (isolated, testable)

- **`UpdateManifest`** — data class (`versionCode`, `versionName`, `apkUrl`, `notes`) + `parse(json: String)`.
- **`HttpClient`** — thin interface (`getText(url): String`, `download(url, dest, onProgress)`), impl over
  `HttpURLConnection` (no new deps). Faked in tests.
- **`UpdateChecker`** — `check(currentCode: Int, manifest: UpdateManifest): UpdateStatus` (pure) plus a
  `fetch(currentCode)` that pulls the manifest via `HttpClient`. Returns
  `UpdateStatus = UpToDate | Available(versionName, apkUrl, notes) | Error(reason)`.
- **`RootDetector`** — runs `Shell.exec("id")`; `hasRoot = stdout contains "uid=0"`. Uses the existing
  `RootShell`. Cached per session.
- **`ApkDownloader`** — downloads `apkUrl` to `cacheDir/update.apk` with progress callback.
- **`ApkInstaller`** —
  - root: `ShellCommands.installApk(path)` = `"pm install -r <path>"`, run via `RootShell`; on exit 0 →
    relaunch (`am start -n online.k73.bmwlauncher/.ui.HomeActivity`).
  - non-root: build an install `Intent` (`ACTION_VIEW`, data = **FileProvider** `content://` Uri for the
    downloaded APK, type `application/vnd.android.package-archive`, `FLAG_GRANT_READ_URI_PERMISSION` +
    `FLAG_ACTIVITY_NEW_TASK`) → `startActivity` → system installer.
- **`FileProvider`** — declared in manifest with `res/xml/file_paths.xml` exposing `cacheDir` (Android 7+
  forbids `file://` Uris for installs).
- **Settings UI** — new "Обновление" section in `SettingsScreen`: current version (`versionName (code)`),
  a small **root status** line ("root: да/нет"), and the "Проверить обновление" button. States:
  `idle → checking… → (UpToDate: "Установлена последняя версия" | Available: version + notes + "Обновить"
  button) → downloading N% → installing… → error(message)`.
- **Permissions:** `INTERNET`, `REQUEST_INSTALL_PACKAGES`.

## 5. Data flow

```
Настройки → "Проверить обновление"
  → UpdateChecker.fetch(BuildConfig.VERSION_CODE)
      ├─ UpToDate  → "Установлена последняя версия"
      ├─ Error     → show reason (no network / parse / http)
      └─ Available → show versionName + notes + [Обновить]
            → ApkDownloader.download(apkUrl, onProgress)
                 → RootDetector.hasRoot
                      ├─ true  → ApkInstaller.installRoot(file) → relaunch
                      └─ false → ApkInstaller.installViaIntent(file)  (system installer)
```

## 6. Error handling

- No network / timeout → "Не удалось проверить обновление (нет сети)".
- Manifest unparseable / missing fields → "Ошибка манифеста обновления".
- Download failure → error, keep current version installed.
- `pm install` non-zero → show stderr; if it indicates signature mismatch → hint "переустановите с новой
  подписью один раз".
- No root AND `REQUEST_INSTALL_PACKAGES` not granted → route user to the system grant screen
  (`ACTION_MANAGE_UNKNOWN_APP_SOURCES`).

## 7. Signing & release process (VPS)

- **One-time:** generate a release keystore (kept OUTSIDE the repo, e.g. `~/keystores/bmw-release.jks`,
  backed up). Add `signingConfigs.release` reading credentials from a **gitignored** `keystore.properties`.
  Build release variant signed with it.
- **Release script** (`scripts/release.sh` on VPS): bump `versionCode`/`versionName`, build signed release
  APK, copy to container `npm-app-1:/data/newBMW/bmw-launcher.apk`, write `latest.json` with the new
  `versionCode`/`versionName`/`notes`.
- **One-time for Roma:** reinstall the first release-signed build (uninstall old debug-signed + install
  via the current manual flow). After that, updates flow through the in-app button.

## 8. Testing

- **Unit (headless):** `UpdateManifest.parse` (valid/invalid JSON); `UpdateChecker.check` (newer / equal /
  older versionCode); `ShellCommands.installApk` path building; `RootDetector` with a fake `Shell`
  (uid=0 → true, else false); `UpdateChecker.fetch` with a fake `HttpClient` (up-to-date / available /
  network-error).
- **On device:** real check → download → install on BOTH paths (root silent if present, else installer
  intent), relaunch, and the signature-match reinstall.

## 9. Out of scope (YAGNI)

Auto-check / background updates, delta/patch updates, rollback, multiple release channels, in-app
changelog history beyond the single `notes` string.

## 10. Open risks (verify on device)

1. Whether `su` is actually present (auto-detected; drives silent vs installer-intent path).
2. `REQUEST_INSTALL_PACKAGES` UX on this XTRONS ROM (some Chinese ROMs preauthorize it).
3. Keystore migration = one manual reinstall before OTA is usable.

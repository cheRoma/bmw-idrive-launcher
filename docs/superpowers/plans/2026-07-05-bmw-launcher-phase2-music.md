# BMW Launcher — Phase 2: Музыка (now-playing) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the "Музыка — скоро" placeholder with a real now-playing screen that controls Yandex Music (`ru.yandex.music`) via its MediaSession — album art, track/artist, seekable progress, transport, like — in the locked night-calm style.

**Architecture:** A `NotificationListenerService` (user-granted) lets `MediaSessionManager` hand us the active `MediaController` for Yandex Music. Pure logic (metadata→UI mapping, position math, like-action detection, time formatting, state selection, permission check) is isolated and unit-tested headless; the framework binding (session repository, controller callbacks) and the Compose UI are device-verified, with Paparazzi goldens for the two visual states. `HomeActivity` swaps the `music_stub` route for the real screen behind a notification-access gate.

**Tech Stack:** Kotlin, Jetpack Compose, `android.media.session.MediaSessionManager`/`MediaController`, `NotificationListenerService`; tests JUnit4 + Robolectric 4.12 + Paparazzi.

**Package:** `online.k73.bmwlauncher`
**Spec:** `docs/superpowers/specs/2026-07-01-bmw-launcher-design.md` §6 · **Mockup:** `docs/mockups/music.png`
**Build env (every gradle call):** `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_SDK_ROOT=$HOME/android-sdk` then `cd /home/roma/Projects/bmw && ./gradlew <task> --no-daemon --console=plain`
**Device:** XTRONS/Microntek, Android 13, 1280×720 @ 240 dpi. Target music app: `ru.yandex.music`.

---

## File Structure

```
app/src/main/java/online/k73/bmwlauncher/music/
  TimeFormat.kt              mm:ss formatting (pure)
  NowPlaying.kt              UI model
  PlaybackMapper.kt          isPlaying / likeActionName / currentPositionMs (pure)
  MusicUiState.kt            sealed UI state + selectState (pure)
  NotificationAccess.kt      is-granted check + settings intent
  MediaNotificationListener.kt   empty NotificationListenerService (must exist + be enabled)
  MediaSessionRepository.kt  MediaSessionManager → MediaController for the target pkg (device)
  MusicController.kt         transport + like over a MediaController (device)
  MusicViewModel.kt          holds MusicUiState StateFlow; subscribes to controller (device)
  ui/MusicScreen.kt          night-calm now-playing composable (Playing + NoPlayback + NoPermission)
app/src/main/AndroidManifest.xml         (MODIFY: declare the listener service)
app/src/main/java/online/k73/bmwlauncher/ui/HomeActivity.kt  (MODIFY: real music route + wiring)
app/src/test/java/online/k73/bmwlauncher/music/   (unit tests)
app/src/test/java/online/k73/bmwlauncher/screenshot/  (Paparazzi)
```

---

### Task 1: `TimeFormat` (mm:ss)

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/music/TimeFormat.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/music/TimeFormatTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package online.k73.bmwlauncher.music

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test fun formats_minutes_seconds() {
        assertEquals("1:24", TimeFormat.mmss(84_000))
        assertEquals("0:05", TimeFormat.mmss(5_000))
        assertEquals("3:58", TimeFormat.mmss(238_000))
        assertEquals("10:00", TimeFormat.mmss(600_000))
    }
    @Test fun non_positive_is_zero() {
        assertEquals("0:00", TimeFormat.mmss(0))
        assertEquals("0:00", TimeFormat.mmss(-5))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*TimeFormatTest*' --no-daemon --console=plain`
Expected: FAIL — `TimeFormat` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package online.k73.bmwlauncher.music

object TimeFormat {
    /** Milliseconds -> "m:ss". Non-positive -> "0:00". */
    fun mmss(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSec = ms / 1000
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*TimeFormatTest*' --no-daemon --console=plain`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/music/TimeFormat.kt app/src/test/java/online/k73/bmwlauncher/music/TimeFormatTest.kt
git commit -m "feat(music): add TimeFormat mm:ss"
```

---

### Task 2: `NowPlaying` model + `PlaybackMapper` (pure logic)

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/music/NowPlaying.kt`
- Create: `app/src/main/java/online/k73/bmwlauncher/music/PlaybackMapper.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/music/PlaybackMapperTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package online.k73.bmwlauncher.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackMapperTest {
    @Test fun playing_state_is_playing() {
        assertTrue(PlaybackMapper.isPlaying(3))   // PlaybackState.STATE_PLAYING
        assertFalse(PlaybackMapper.isPlaying(2))  // STATE_PAUSED
        assertFalse(PlaybackMapper.isPlaying(0))
    }

    @Test fun finds_like_action_excluding_dislike() {
        assertEquals("ru.yandex.like", PlaybackMapper.likeActionName(listOf("skip", "ru.yandex.like", "ru.yandex.dislike")))
        assertEquals("heart_toggle", PlaybackMapper.likeActionName(listOf("heart_toggle")))
        assertNull(PlaybackMapper.likeActionName(listOf("shuffle", "repeat", "dislike_only")))
    }

    @Test fun computes_current_position_with_speed() {
        // base 10s at update t=1000ms, speed 1.0, now t=4000ms -> 10s + 3s = 13s
        assertEquals(13_000L, PlaybackMapper.currentPositionMs(10_000, 1_000, 1.0f, 4_000))
    }
    @Test fun position_never_negative_and_ignores_zero_update_time() {
        assertEquals(10_000L, PlaybackMapper.currentPositionMs(10_000, 0, 1.0f, 999_999))
        assertEquals(0L, PlaybackMapper.currentPositionMs(-500, 0, 1.0f, 0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*PlaybackMapperTest*' --no-daemon --console=plain`
Expected: FAIL — unresolved.

- [ ] **Step 3: Write `NowPlaying.kt`**

```kotlin
package online.k73.bmwlauncher.music

data class NowPlaying(
    val title: String,
    val artist: String,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val likeActionName: String?,   // null → no like button
) {
    val likeAvailable: Boolean get() = likeActionName != null
}
```

- [ ] **Step 4: Write `PlaybackMapper.kt`**

```kotlin
package online.k73.bmwlauncher.music

object PlaybackMapper {
    const val STATE_PLAYING = 3 // android.media.session.PlaybackState.STATE_PLAYING

    fun isPlaying(stateInt: Int): Boolean = stateInt == STATE_PLAYING

    /** Yandex exposes like/dislike as custom actions; pick the "like" one, never a "dislike". */
    fun likeActionName(customActionNames: List<String>): String? =
        customActionNames.firstOrNull { it.contains("like", true) && !it.contains("dislike", true) }
            ?: customActionNames.firstOrNull { it.contains("heart", true) }

    /** Interpolate the reported position by elapsed wall time * playback speed. */
    fun currentPositionMs(basePositionMs: Long, lastUpdateMs: Long, speed: Float, nowMs: Long): Long {
        if (lastUpdateMs <= 0L) return basePositionMs.coerceAtLeast(0)
        val delta = ((nowMs - lastUpdateMs) * speed).toLong()
        return (basePositionMs + delta).coerceAtLeast(0)
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*PlaybackMapperTest*' --no-daemon --console=plain`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/music/NowPlaying.kt app/src/main/java/online/k73/bmwlauncher/music/PlaybackMapper.kt app/src/test/java/online/k73/bmwlauncher/music/PlaybackMapperTest.kt
git commit -m "feat(music): add NowPlaying model and pure PlaybackMapper"
```

---

### Task 3: `MusicUiState` + `selectState` (pure)

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/music/MusicUiState.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/music/MusicUiStateTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package online.k73.bmwlauncher.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicUiStateTest {
    private val np = NowPlaying("Ночной город", "Дельфин", 84_000, 238_000, true, "like")

    @Test fun no_permission_wins() {
        assertEquals(MusicUiState.NoPermission, MusicUiState.selectState(hasPermission = false, nowPlaying = np))
    }
    @Test fun no_session_is_no_playback() {
        assertEquals(MusicUiState.NoPlayback, MusicUiState.selectState(hasPermission = true, nowPlaying = null))
    }
    @Test fun active_session_is_playing() {
        val s = MusicUiState.selectState(hasPermission = true, nowPlaying = np)
        assertTrue(s is MusicUiState.Playing)
        assertEquals("Ночной город", (s as MusicUiState.Playing).nowPlaying.title)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*MusicUiStateTest*' --no-daemon --console=plain`
Expected: FAIL — unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package online.k73.bmwlauncher.music

sealed interface MusicUiState {
    data object NoPermission : MusicUiState
    data object NoPlayback : MusicUiState
    data class Playing(val nowPlaying: NowPlaying) : MusicUiState

    companion object {
        fun selectState(hasPermission: Boolean, nowPlaying: NowPlaying?): MusicUiState = when {
            !hasPermission -> NoPermission
            nowPlaying == null -> NoPlayback
            else -> Playing(nowPlaying)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*MusicUiStateTest*' --no-daemon --console=plain`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/music/MusicUiState.kt app/src/test/java/online/k73/bmwlauncher/music/MusicUiStateTest.kt
git commit -m "feat(music): add MusicUiState and selectState"
```

---

### Task 4: `NotificationAccess` (permission check)

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/music/NotificationAccess.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/music/NotificationAccessTest.kt`

- [ ] **Step 1: Write the failing test (Robolectric)**

```kotlin
package online.k73.bmwlauncher.music

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationAccessTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun not_granted_when_setting_empty() {
        Settings.Secure.putString(ctx.contentResolver, "enabled_notification_listeners", "")
        assertFalse(NotificationAccess.isGranted(ctx))
    }
    @Test fun granted_when_our_component_listed() {
        val comp = "${ctx.packageName}/${ctx.packageName}.music.MediaNotificationListener"
        Settings.Secure.putString(ctx.contentResolver, "enabled_notification_listeners", "com.other/x:$comp")
        assertTrue(NotificationAccess.isGranted(ctx))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*NotificationAccessTest*' --no-daemon --console=plain`
Expected: FAIL — unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package online.k73.bmwlauncher.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

object NotificationAccess {
    /** True if our NotificationListenerService is enabled in Settings → Notification access. */
    fun isGranted(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?: return false
        return flat.split(":").any {
            ComponentName.unflattenFromString(it)?.packageName == context.packageName
        }
    }

    /** Intent to the system screen where the user grants notification access. */
    fun settingsIntent(): Intent =
        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*NotificationAccessTest*' --no-daemon --console=plain`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/music/NotificationAccess.kt app/src/test/java/online/k73/bmwlauncher/music/NotificationAccessTest.kt
git commit -m "feat(music): add NotificationAccess permission check"
```

---

### Task 5: `MediaNotificationListener` + manifest declaration

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/music/MediaNotificationListener.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: none (framework service; device-verified). Compile-only.

- [ ] **Step 1: Write the (empty) listener service**

```kotlin
package online.k73.bmwlauncher.music

import android.service.notification.NotificationListenerService

/**
 * Exists only so the user can grant "Notification access", which in turn lets
 * MediaSessionManager.getActiveSessions() hand us other apps' MediaControllers.
 * We do not process notifications here.
 */
class MediaNotificationListener : NotificationListenerService()
```

- [ ] **Step 2: Declare it in `AndroidManifest.xml`**

Add inside `<application>` (after the existing `<provider>`):
```xml
        <service
            android:name=".music.MediaNotificationListener"
            android:exported="false"
            android:label="BMW Launcher Media"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
```

- [ ] **Step 3: Verify compile**

Run: `./gradlew :app:compileDebugKotlin --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/music/MediaNotificationListener.kt app/src/main/AndroidManifest.xml
git commit -m "feat(music): add NotificationListenerService for MediaSession access"
```

---

### Task 6: `MediaSessionRepository` + `MusicController` (framework binding)

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/music/MediaSessionRepository.kt`
- Create: `app/src/main/java/online/k73/bmwlauncher/music/MusicController.kt`
- Test: none (needs real MediaSessions; device-verified). Compile-only.

- [ ] **Step 1: Write `MediaSessionRepository.kt`**

```kotlin
package online.k73.bmwlauncher.music

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager

/** Finds the active MediaController for [targetPackage] via our enabled notification listener. */
class MediaSessionRepository(private val context: Context) {
    private val manager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val listener = ComponentName(context, MediaNotificationListener::class.java)

    fun activeController(targetPackage: String): MediaController? =
        runCatching { manager.getActiveSessions(listener) }.getOrNull()
            ?.firstOrNull { it.packageName == targetPackage }

    /** Notifies [onChanged] whenever the set of active sessions changes. */
    fun observeSessions(onChanged: () -> Unit): MediaSessionManager.OnActiveSessionsChangedListener {
        val l = MediaSessionManager.OnActiveSessionsChangedListener { onChanged() }
        runCatching { manager.addOnActiveSessionsChangedListener(l, listener) }
        return l
    }

    fun stopObserving(l: MediaSessionManager.OnActiveSessionsChangedListener) {
        runCatching { manager.removeOnActiveSessionsChangedListener(l) }
    }
}
```

- [ ] **Step 2: Write `MusicController.kt` (transport + like + metadata read)**

```kotlin
package online.k73.bmwlauncher.music

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState

/** Thin wrapper translating a framework MediaController into our NowPlaying + transport actions. */
class MusicController(private val controller: MediaController) {

    fun registerCallback(cb: MediaController.Callback) = controller.registerCallback(cb)
    fun unregisterCallback(cb: MediaController.Callback) = controller.unregisterCallback(cb)

    fun albumArt(): Bitmap? {
        val md = controller.metadata ?: return null
        return md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    }

    fun nowPlaying(nowMs: Long): NowPlaying? {
        val md = controller.metadata ?: return null
        val ps = controller.playbackState ?: return null
        val actionNames = ps.customActions.map { it.action }
        return NowPlaying(
            title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "",
            artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: "",
            durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = PlaybackMapper.currentPositionMs(
                ps.position, ps.lastPositionUpdateTime, ps.playbackSpeed, nowMs
            ),
            isPlaying = PlaybackMapper.isPlaying(ps.state),
            likeActionName = PlaybackMapper.likeActionName(actionNames),
        )
    }

    fun playPause() {
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing) controller.transportControls.pause() else controller.transportControls.play()
    }
    fun next() = controller.transportControls.skipToNext()
    fun prev() = controller.transportControls.skipToPrevious()
    fun seekTo(ms: Long) = controller.transportControls.seekTo(ms)
    fun sendLike(actionName: String) = controller.transportControls.sendCustomAction(actionName, null)
}
```

- [ ] **Step 3: Verify compile**

Run: `./gradlew :app:compileDebugKotlin --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/music/MediaSessionRepository.kt app/src/main/java/online/k73/bmwlauncher/music/MusicController.kt
git commit -m "feat(music): add MediaSessionRepository and MusicController"
```

---

### Task 7: `MusicViewModel` (state holder)

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/music/MusicViewModel.kt`
- Test: none (drives framework callbacks; device-verified). Compile-only.

- [ ] **Step 1: Write `MusicViewModel.kt`**

```kotlin
package online.k73.bmwlauncher.music

import android.content.Context
import android.media.session.MediaController
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the MusicUiState. Binds to Yandex Music's active session, re-reads on callbacks,
 * and ticks the position once a second while playing. Call [start]/[stop] with a scope.
 */
class MusicViewModel(
    private val context: Context,
    private val repo: MediaSessionRepository,
    private val targetPackage: String,
) {
    private val _state = MutableStateFlow<MusicUiState>(MusicUiState.NoPlayback)
    val state: StateFlow<MusicUiState> = _state

    private var controller: MusicController? = null
    private var rawController: MediaController? = null
    private var callback: MediaController.Callback? = null
    private var sessionsListener: android.media.session.MediaSessionManager.OnActiveSessionsChangedListener? = null

    fun start(scope: CoroutineScope) {
        sessionsListener = repo.observeSessions { rebind() }
        rebind()
        scope.launch {
            while (true) { refresh(); delay(1000) }
        }
    }

    fun stop() {
        callback?.let { rawController?.unregisterCallback(it) }
        sessionsListener?.let { repo.stopObserving(it) }
    }

    private fun rebind() {
        callback?.let { rawController?.unregisterCallback(it) }
        val rc = repo.activeController(targetPackage)
        rawController = rc
        controller = rc?.let { MusicController(it) }
        if (rc != null) {
            val cb = object : MediaController.Callback() {
                override fun onMetadataChanged(m: android.media.MediaMetadata?) = refresh()
                override fun onPlaybackStateChanged(s: android.media.session.PlaybackState?) = refresh()
                override fun onSessionDestroyed() = rebind()
            }
            callback = cb
            rc.registerCallback(cb)
        }
        refresh()
    }

    private fun refresh() {
        val np = controller?.nowPlaying(SystemClock.elapsedRealtime())
        _state.value = MusicUiState.selectState(
            hasPermission = NotificationAccess.isGranted(context),
            nowPlaying = np,
        )
    }

    fun albumArt() = controller?.albumArt()
    fun playPause() = controller?.playPause()
    fun next() = controller?.next()
    fun prev() = controller?.prev()
    fun seekTo(ms: Long) = controller?.seekTo(ms)
    fun like() { val n = (state.value as? MusicUiState.Playing)?.nowPlaying?.likeActionName; if (n != null) controller?.sendLike(n) }

    /** Cold-start: nothing playing → launch Yandex Music so a session appears. */
    fun startPlaybackColdStart(launch: (String) -> Unit) = launch(targetPackage)
}
```

Note: `MediaController.Callback.onMetadataChanged/onPlaybackStateChanged` return Unit; `= refresh()` works because `refresh()` returns Unit.

- [ ] **Step 2: Verify compile**

Run: `./gradlew :app:compileDebugKotlin --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/music/MusicViewModel.kt
git commit -m "feat(music): add MusicViewModel state holder"
```

---

### Task 8: `MusicScreen` composable + Paparazzi goldens

**Files:**
- Create: `app/src/main/java/online/k73/bmwlauncher/music/ui/MusicScreen.kt`
- Test: `app/src/test/java/online/k73/bmwlauncher/screenshot/MusicScreenScreenshotTest.kt`
- Reference: `docs/mockups/music.png`

- [ ] **Step 1: Write `MusicScreen.kt` (stateless — takes state + callbacks)**

```kotlin
package online.k73.bmwlauncher.music.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.k73.bmwlauncher.music.MusicUiState
import online.k73.bmwlauncher.music.NowPlaying
import online.k73.bmwlauncher.music.TimeFormat
import online.k73.bmwlauncher.ui.theme.LocalLauncherColors

@Composable
fun MusicScreen(
    state: MusicUiState,
    albumArt: ImageBitmap?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onLike: () -> Unit,
    onPlaylists: () -> Unit,
    onColdStartPlay: () -> Unit,
) {
    val c = LocalLauncherColors.current
    Column(Modifier.fillMaxSize().background(c.background).padding(24.dp)) {
        // top bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Яндекс Музыка", color = c.textDim, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (state is MusicUiState.Playing) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onPlaylists() }.padding(4.dp)) {
                    Icon(Icons.Filled.List, "Плейлисты", tint = c.text)
                    Text("Плейлисты", color = c.textDim, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        when (state) {
            MusicUiState.NoPermission -> Centered(c.textDim, "Дайте доступ к уведомлениям\nв Настройках") { onPlaylists() }
            MusicUiState.NoPlayback -> Centered(c.text, "Нет воспроизведения") { onColdStartPlay() }
            is MusicUiState.Playing -> PlayingBody(state.nowPlaying, albumArt, onPlayPause, onNext, onPrev, onSeek, onLike)
        }
    }
}

@Composable
private fun Centered(color: Color, text: String, onTap: () -> Unit) {
    Box(Modifier.fillMaxSize().clickable { onTap() }, contentAlignment = Alignment.Center) {
        Text(text, color = color, fontSize = 22.sp)
    }
}

@Composable
private fun PlayingBody(
    np: NowPlaying, art: ImageBitmap?, onPlayPause: () -> Unit, onNext: () -> Unit,
    onPrev: () -> Unit, onSeek: (Long) -> Unit, onLike: () -> Unit,
) {
    val c = LocalLauncherColors.current
    Row(Modifier.fillMaxSize()) {
        // album art
        Box(Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
            .background(c.tile), contentAlignment = Alignment.Center) {
            if (art != null) Image(art, "cover", Modifier.fillMaxSize())
            else Icon(Icons.Filled.PlayArrow, null, tint = c.textDim, modifier = Modifier.size(64.dp))
        }
        Spacer(Modifier.width(28.dp))
        // right column
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Text(np.title, color = c.text, fontSize = 40.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(np.artist, color = c.textDim, fontSize = 26.sp, maxLines = 1)
            Spacer(Modifier.height(28.dp))
            // progress
            val frac = if (np.durationMs > 0) (np.positionMs.toFloat() / np.durationMs).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(c.tile)) {
                Box(Modifier.fillMaxWidth(frac).height(6.dp).clip(CircleShape).background(c.accent))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(TimeFormat.mmss(np.positionMs), color = c.textDim, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(TimeFormat.mmss(np.durationMs), color = c.textDim, fontSize = 16.sp)
            }
            Spacer(Modifier.height(24.dp))
            // transport row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SkipPrevious, "prev", tint = c.text,
                    modifier = Modifier.size(48.dp).clickable { onPrev() })
                Box(Modifier.size(72.dp).clip(CircleShape).clickable { onPlayPause() }, contentAlignment = Alignment.Center) {
                    Icon(if (np.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "play/pause",
                        tint = c.accent, modifier = Modifier.size(56.dp))
                }
                Icon(Icons.Filled.SkipNext, "next", tint = c.text,
                    modifier = Modifier.size(48.dp).clickable { onNext() })
                if (np.likeAvailable) {
                    Icon(Icons.Filled.Favorite, "like", tint = c.accent,
                        modifier = Modifier.size(44.dp).clickable { onLike() })
                }
            }
        }
    }
}
```
Note: Material icons `Pause`, `SkipNext`, `SkipPrevious`, `Favorite`, `List` are in `material-icons-extended` (already a dependency from Phase 1).

- [ ] **Step 2: Write the Paparazzi test (Playing + NoPlayback)**

```kotlin
package online.k73.bmwlauncher.screenshot

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import online.k73.bmwlauncher.music.MusicUiState
import online.k73.bmwlauncher.music.NowPlaying
import online.k73.bmwlauncher.music.ui.MusicScreen
import online.k73.bmwlauncher.ui.theme.BmwLauncherTheme
import org.junit.Rule
import org.junit.Test

class MusicScreenScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = 1280, screenHeight = 720,
            orientation = ScreenOrientation.LANDSCAPE, density = Density.MEDIUM,
        ),
    )

    private val np = NowPlaying("Ночной город", "Дельфин", 84_000, 238_000, true, "like")

    @Test fun music_playing() {
        paparazzi.snapshot {
            BmwLauncherTheme(isNight = true) {
                MusicScreen(MusicUiState.Playing(np), null, {}, {}, {}, {}, {}, {}, {})
            }
        }
    }
    @Test fun music_no_playback() {
        paparazzi.snapshot {
            BmwLauncherTheme(isNight = true) {
                MusicScreen(MusicUiState.NoPlayback, null, {}, {}, {}, {}, {}, {}, {})
            }
        }
    }
}
```

- [ ] **Step 3: Record + verify goldens, and Read them**

Run: `./gradlew :app:recordPaparazziDebug --tests '*MusicScreenScreenshotTest*' --no-daemon --console=plain`
Then Read `app/src/test/snapshots/images/online.k73.bmwlauncher.screenshot_MusicScreenScreenshotTest_music_playing.png` and confirm against `docs/mockups/music.png`: album art left, big title "Ночной город" + "Дельфин", amber progress bar with 1:24 / 3:58, transport row (prev · amber play/pause · next · amber heart), "Плейлисты" top-right, "Яндекс Музыка" label. The `music_no_playback` golden shows a centered "Нет воспроизведения".
Then: `./gradlew :app:verifyPaparazziDebug --tests '*MusicScreenScreenshotTest*' --no-daemon --console=plain` → PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/music/ui/MusicScreen.kt app/src/test/java/online/k73/bmwlauncher/screenshot/MusicScreenScreenshotTest.kt app/src/test/snapshots/
git commit -m "feat(music): add night-calm MusicScreen with Paparazzi goldens"
```

---

### Task 9: Wire into `HomeActivity` (replace the stub)

**Files:**
- Modify: `app/src/main/java/online/k73/bmwlauncher/ui/HomeActivity.kt`
- Test: none new (existing HomeActivityTest still runs). Compile + full suite.

- [ ] **Step 1: Add music wiring to `HomeActivity`**

Add imports:
```kotlin
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.asImageBitmap
import online.k73.bmwlauncher.music.MediaSessionRepository
import online.k73.bmwlauncher.music.MusicViewModel
import online.k73.bmwlauncher.music.NotificationAccess
import online.k73.bmwlauncher.music.ui.MusicScreen
```
Add lazy fields near the others:
```kotlin
    private val musicRepo by lazy { MediaSessionRepository(applicationContext) }
    private val musicVm by lazy { MusicViewModel(applicationContext, musicRepo, "ru.yandex.music") }
```
Replace the `composable("music_stub") { MusicPlaceholder() }` route with a real music route (rename to `"music"` and update the tile navigation `TileId.MUSIC -> nav.navigate("music")`):
```kotlin
                    composable("music") {
                        val musicState by musicVm.state.collectAsState()
                        DisposableEffect(Unit) {
                            musicVm.start(lifecycleScope)
                            onDispose { musicVm.stop() }
                        }
                        MusicScreen(
                            state = musicState,
                            albumArt = musicVm.albumArt()?.asImageBitmap(),
                            onPlayPause = { musicVm.playPause() },
                            onNext = { musicVm.next() },
                            onPrev = { musicVm.prev() },
                            onSeek = { musicVm.seekTo(it) },
                            onLike = { musicVm.like() },
                            onPlaylists = {
                                // Phase 3 will try MediaBrowser; for now open Yandex Music (also the
                                // "grant permission" tap target when access is missing).
                                if (!NotificationAccess.isGranted(applicationContext)) {
                                    startActivity(NotificationAccess.settingsIntent())
                                } else {
                                    launcher.launch("ru.yandex.music")
                                }
                            },
                            onColdStartPlay = {
                                launcher.launch("ru.yandex.music")
                            },
                        )
                    }
```
Update the tile routing line: `TileId.MUSIC -> nav.navigate("music")` (was `"music_stub"`). Delete the old `MusicPlaceholder` composable at the bottom of the file (no longer referenced).

- [ ] **Step 2: Build + run the full unit suite**

Run: `./gradlew :app:testDebugUnitTest --no-daemon --console=plain`
Expected: all tests PASS (Phase 1 + OTA + new music unit tests).
Run: `./gradlew :app:assembleDebug --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/online/k73/bmwlauncher/ui/HomeActivity.kt
git commit -m "feat(music): wire real MusicScreen into HomeActivity, drop placeholder"
```

---

### Task 10: Full verification + release + on-device checklist

**Files:** none.

- [ ] **Step 1: Full clean test + Paparazzi + release APK**

Run: `./gradlew clean :app:testDebugUnitTest :app:verifyPaparazziDebug :app:assembleRelease --no-daemon --console=plain`
Expected: `BUILD SUCCESSFUL`; all unit tests pass; Home/Settings/Music goldens verify; signed release APK at `app/build/outputs/apk/release/app-release.apk`.

- [ ] **Step 2: Publish via the release script + bump version**

```bash
cd /home/roma/Projects/bmw
sed -i 's/versionCode = 8/versionCode = 9/; s/versionName = "1.0.7"/versionName = "1.1.0"/' app/build.gradle.kts
./scripts/release.sh "Экран Музыка: плеер Яндекса (обложка, транспорт, лайк, прогресс)"
```
Expected: prints `Published versionCode=9 versionName=1.1.0` and the manifest JSON.

- [ ] **Step 3: Commit the version bump**

```bash
git add app/build.gradle.kts && git commit -m "chore: release v1.1.0 (Phase 2 Music)"
```

- [ ] **Step 4: On-device checklist (in the car — document results)**

1. Update via the in-app button (or `adb install -r`).
2. Grant notification access: tapping "Музыка" with access ungranted shows "Дайте доступ…"; tapping it opens the system screen → enable **BMW Launcher Media** under Notification access.
3. Start playback in Yandex Music, return to launcher → **Музыка** shows the real track/artist/art/progress; play/pause/next/prev/seek work; the amber heart appears **only if** Yandex exposes a like custom action (record whether it does — answers spec §6/§11 like question).
4. With nothing playing, **Музыка** shows "Нет воспроизведения"; tapping it launches Yandex Music.
5. Tap "Плейлисты" → opens Yandex Music (MediaBrowser browse is Phase 3).

Record: does the like action appear? does album art load? — these inform Phase 3.

---

## Self-Review (spec coverage)

- MediaController control of Yandex Music (spec §6) → Tasks 6, 7, 9. ✓
- Notification Listener Access (spec §6) → Tasks 4, 5, 9 (gate + settings intent). ✓
- Metadata + transport + seek (spec §6) → Tasks 6, 7, 8. ✓
- Like via custom action, hidden if unavailable (spec §6) → Tasks 2 (detect), 6 (send), 8 (conditional UI). ✓
- Cold start idle → wait for play; play launches Yandex (spec §6) → Tasks 3, 7 (`onColdStartPlay`), 9. ✓
- Плейлисты → open Yandex (MediaBrowser deferred to Phase 3) (spec §6) → Task 9. ✓
- Night-calm style matching mockup (spec §6, docs/mockups/music.png) → Task 8 + Paparazzi. ✓
- Testing: pure logic unit-tested (Tasks 1–4), UI via Paparazzi (Task 8), framework device-verified (Task 10 checklist). ✓

**Type consistency:** `TimeFormat.mmss`, `NowPlaying(title,artist,positionMs,durationMs,isPlaying,likeActionName)`+`likeAvailable`, `PlaybackMapper.{isPlaying,likeActionName,currentPositionMs}`, `MusicUiState.{NoPermission,NoPlayback,Playing,selectState}`, `NotificationAccess.{isGranted,settingsIntent}`, `MediaSessionRepository.{activeController,observeSessions,stopObserving}`, `MusicController.{nowPlaying,albumArt,playPause,next,prev,seekTo,sendLike,registerCallback,unregisterCallback}`, `MusicViewModel.{state,start,stop,albumArt,playPause,next,prev,seekTo,like,startPlaybackColdStart}`, `MusicScreen(state,albumArt,onPlayPause,onNext,onPrev,onSeek,onLike,onPlaylists,onColdStartPlay)` — consistent across Tasks 1–10. ✓

# Playback Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Android system media notification display for song playback, with title/artist/artwork, play/pause, previous/next, seek, notification click return, and Android 13+ notification permission handling.

**Architecture:** Keep `PlaybackService : MediaSessionService` as the single background playback surface and use Media3 `DefaultMediaNotificationProvider` for the notification. Keep queue navigation in `PlayerController`; notification previous/next buttons send Media3 custom session commands to a player-layer delegate so service code does not depend on app or feature modules.

**Tech Stack:** Kotlin, AndroidX Media3 Session/ExoPlayer, Hilt existing app setup, Jetpack Compose, Activity Result APIs, Robolectric, Android instrumentation tests.

---

## File Structure

- Create `core/src/main/java/com/hank/musicfree/core/permissions/NotificationPermission.kt`
  - Pure SDK helper for the Android 13+ `POST_NOTIFICATIONS` permission name.
- Modify `feature/settings/src/main/java/com/hank/musicfree/feature/settings/PermissionsViewModel.kt`
  - Add notification permission state to the permissions UI model.
- Modify `feature/settings/src/main/java/com/hank/musicfree/feature/settings/PermissionsHelpers.kt`
  - Add notification permission status helper and include it in `readPermissionsUiState`.
- Modify `feature/settings/src/main/java/com/hank/musicfree/feature/settings/PermissionsScreen.kt`
  - Add a request launcher and row for notification permission.
- Modify `feature/settings/src/test/java/com/hank/musicfree/feature/settings/PermissionsHelpersTest.kt`
  - Add unit coverage for notification permission helpers.
- Modify `app/src/main/java/com/hank/musicfree/MainActivity.kt`
  - Request notification permission on Android 13+ during app entry.
- Modify `player/src/main/java/com/hank/musicfree/player/ext/MusicItemMediaExt.kt`
  - Support default artwork URI fallback while preserving existing callers.
- Modify `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`
  - Pass default artwork URI into `toMediaItem`; register/unregister notification queue controls.
- Modify `player/src/test/java/com/hank/musicfree/player/ext/MusicItemMediaExtTest.kt`
  - Add fallback artwork metadata coverage.
- Create `player/src/main/java/com/hank/musicfree/player/service/PlaybackNotificationActions.kt`
  - Define custom session commands and Media3 command buttons for previous/next.
- Create `player/src/main/java/com/hank/musicfree/player/service/PlaybackNotificationCommandHandler.kt`
  - Define the queue-control delegate used by `PlaybackService`.
- Create `player/src/test/java/com/hank/musicfree/player/service/PlaybackNotificationActionsTest.kt`
  - Unit test custom command/button construction.
- Create `player/src/test/java/com/hank/musicfree/player/service/PlaybackNotificationCommandHandlerTest.kt`
  - Unit test attach, no-op, and detach behavior.
- Create `player/src/main/res/values/strings.xml`
  - Add the Media3 notification channel name.
- Modify `player/src/main/java/com/hank/musicfree/player/service/PlaybackService.kt`
  - Configure notification provider, session activity, media button preferences, and custom command callback.
- Modify `player/src/androidTest/java/com/hank/musicfree/player/service/PlaybackServiceTest.kt`
  - Add session activity, media button preference, and notification command queue navigation tests.

---

## Task 1: Notification Permission Helpers and Settings UI

**Files:**
- Create: `core/src/main/java/com/hank/musicfree/core/permissions/NotificationPermission.kt`
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/PermissionsViewModel.kt`
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/PermissionsHelpers.kt`
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/PermissionsScreen.kt`
- Test: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/PermissionsHelpersTest.kt`

- [ ] **Step 1: Add failing permission helper tests**

Ensure these imports are present in `PermissionsHelpersTest.kt`:

```kotlin
import com.hank.musicfree.core.permissions.requiredNotificationPermission
import org.junit.Assert.assertTrue
```

Add these tests inside `PermissionsHelpersTest`:

```kotlin
    @Test
    fun `requiredNotificationPermission returns POST_NOTIFICATIONS on API 33 and above`() {
        assertEquals(
            Manifest.permission.POST_NOTIFICATIONS,
            requiredNotificationPermission(Build.VERSION_CODES.TIRAMISU),
        )
    }

    @Test
    fun `requiredNotificationPermission returns null below API 33`() {
        assertNull(requiredNotificationPermission(Build.VERSION_CODES.TIRAMISU - 1))
    }

    @Test
    fun `hasNotificationPermission returns true below API 33`() {
        val context: Context = RuntimeEnvironment.getApplication()

        assertTrue(hasNotificationPermission(context, Build.VERSION_CODES.TIRAMISU - 1))
    }
```

Update the existing `readPermissionsUiState maps default application context` assertion block to include notification state:

```kotlin
        assertFalse(uiState.overlayGranted)
        assertFalse(uiState.storageAudioGranted)
        assertFalse(uiState.notificationGranted)
```

- [ ] **Step 2: Run the focused failing test**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests "*.PermissionsHelpersTest"
```

Expected: FAIL because `requiredNotificationPermission`, `hasNotificationPermission`, and `notificationGranted` do not exist.

- [ ] **Step 3: Create the core notification permission helper**

Create `core/src/main/java/com/hank/musicfree/core/permissions/NotificationPermission.kt`:

```kotlin
package com.hank.musicfree.core.permissions

import android.Manifest
import android.os.Build

fun requiredNotificationPermission(sdkInt: Int = Build.VERSION.SDK_INT): String? {
    return if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.POST_NOTIFICATIONS
    } else {
        null
    }
}
```

- [ ] **Step 4: Extend permissions state and helpers**

Change `PermissionsUiState` in `PermissionsViewModel.kt` to:

```kotlin
data class PermissionsUiState(
    val overlayGranted: Boolean = false,
    val storageAudioGranted: Boolean = false,
    val notificationGranted: Boolean = false,
)
```

Update `PermissionsHelpers.kt` imports:

```kotlin
import com.hank.musicfree.core.permissions.requiredAudioPermission
import com.hank.musicfree.core.permissions.requiredNotificationPermission
```

Replace `readPermissionsUiState` with:

```kotlin
internal fun readPermissionsUiState(context: Context): PermissionsUiState {
    return PermissionsUiState(
        overlayGranted = AndroidSettings.canDrawOverlays(context),
        storageAudioGranted = hasStorageAudioPermission(context),
        notificationGranted = hasNotificationPermission(context),
    )
}
```

Add this helper below `hasStorageAudioPermission`:

```kotlin
internal fun hasNotificationPermission(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
    val permission = requiredNotificationPermission(sdkInt) ?: return true
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
```

- [ ] **Step 5: Add notification permission row to settings UI**

Update `PermissionsScreen.kt` imports:

```kotlin
import com.hank.musicfree.core.permissions.requiredAudioPermission
import com.hank.musicfree.core.permissions.requiredNotificationPermission
```

After `val storagePermission = requiredAudioPermission()`, add:

```kotlin
    val notificationPermission = requiredNotificationPermission()
```

After `storagePermissionLauncher`, add:

```kotlin
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.updateUiState(readPermissionsUiState(context))
    }
```

Add this `PermissionRowCard` after the storage/audio permission row:

```kotlin
            PermissionRowCard(
                title = "通知权限",
                statusText = permissionStatusText(uiState.notificationGranted),
                actionText = if (uiState.notificationGranted) "已授权" else "请求权限",
                actionEnabled = !uiState.notificationGranted && notificationPermission != null,
                onAction = {
                    notificationPermission?.let(notificationPermissionLauncher::launch)
                },
            )
```

- [ ] **Step 6: Run the focused settings tests**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests "*.PermissionsHelpersTest"
```

Expected: PASS.

- [ ] **Step 7: Compile the settings module**

Run:

```bash
./gradlew :feature:settings:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 8: Commit permission helper and settings UI**

Run:

```bash
git add core/src/main/java/com/hank/musicfree/core/permissions/NotificationPermission.kt \
  feature/settings/src/main/java/com/hank/musicfree/feature/settings/PermissionsViewModel.kt \
  feature/settings/src/main/java/com/hank/musicfree/feature/settings/PermissionsHelpers.kt \
  feature/settings/src/main/java/com/hank/musicfree/feature/settings/PermissionsScreen.kt \
  feature/settings/src/test/java/com/hank/musicfree/feature/settings/PermissionsHelpersTest.kt
git commit -m "feat(settings): surface notification permission"
```

---

## Task 2: App Entry Notification Permission Request

**Files:**
- Modify: `app/src/main/java/com/hank/musicfree/MainActivity.kt`

- [ ] **Step 1: Add runtime permission request imports**

Add these imports to `MainActivity.kt`:

```kotlin
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.hank.musicfree.core.permissions.requiredNotificationPermission
```

- [ ] **Step 2: Request notification permission from Compose entry**

Inside `MusicFreeTheme {`, before `val navController = rememberNavController()`, add:

```kotlin
                val notificationPermission = requiredNotificationPermission()
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) {
                    // The permission state is also visible from Settings > 权限管理.
                }
                LaunchedEffect(notificationPermission) {
                    if (
                        notificationPermission != null &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            notificationPermission,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(notificationPermission)
                    }
                }
```

- [ ] **Step 3: Compile the app module**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 4: Commit app entry permission request**

Run:

```bash
git add app/src/main/java/com/hank/musicfree/MainActivity.kt
git commit -m "feat(app): request notification permission on entry"
```

---

## Task 3: MediaItem Metadata Default Artwork

**Files:**
- Modify: `player/src/main/java/com/hank/musicfree/player/ext/MusicItemMediaExt.kt`
- Modify: `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`
- Test: `player/src/test/java/com/hank/musicfree/player/ext/MusicItemMediaExtTest.kt`

- [ ] **Step 1: Add failing default artwork test**

Add this import to `MusicItemMediaExtTest.kt`:

```kotlin
import android.net.Uri
```

Add this test inside `MusicItemMediaExtTest`:

```kotlin
    @Test
    fun `toMediaItem uses default artwork uri when artwork is blank`() {
        val fallbackArtwork = Uri.parse("android.resource://test.package/123")
        val item = MusicItem(
            id = "3", platform = "local", title = "Song3",
            artist = "Artist", album = null, duration = 0L,
            url = "https://example.com/song.mp3", artwork = " ", qualities = null,
        )

        val mediaItem = item.toMediaItem(defaultArtworkUri = fallbackArtwork)

        assertEquals(fallbackArtwork, mediaItem.mediaMetadata.artworkUri)
    }
```

- [ ] **Step 2: Run the focused failing test**

Run:

```bash
./gradlew :player:testDebugUnitTest --tests "*.MusicItemMediaExtTest"
```

Expected: FAIL because `toMediaItem(defaultArtworkUri = ...)` does not exist.

- [ ] **Step 3: Implement metadata fallback**

Replace `MusicItemMediaExt.kt` with:

```kotlin
package com.hank.musicfree.player.ext

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.R as CoreR

fun MusicItem.toMediaItem(defaultArtworkUri: Uri? = null): MediaItem {
    val mediaUri = url
    require(!mediaUri.isNullOrBlank()) {
        "Cannot create MediaItem without URL for: $title ($platform:$id)"
    }

    val builder = MediaItem.Builder()
        .setMediaId("$platform:$id")
        .setUri(mediaUri)

    val artworkUri = artwork
        ?.takeIf { it.isNotBlank() }
        ?.let(Uri::parse)
        ?: defaultArtworkUri

    builder.setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .apply {
                album?.let { setAlbumTitle(it) }
                artworkUri?.let { setArtworkUri(it) }
            }
            .build()
    )

    return builder.build()
}

fun Context.defaultAlbumArtworkUri(): Uri {
    return Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(packageName)
        .appendPath(CoreR.drawable.album_default.toString())
        .build()
}
```

- [ ] **Step 4: Pass fallback artwork from PlayerController**

Update `PlayerController.kt` imports:

```kotlin
import com.hank.musicfree.player.ext.defaultAlbumArtworkUri
import com.hank.musicfree.player.ext.toMediaItem
```

Add this property near `private var connectJob: Job? = null`:

```kotlin
    private val defaultArtworkUri = context.defaultAlbumArtworkUri()
```

Replace every `toMediaItem()` call in `PlayerController.kt` with:

```kotlin
toMediaItem(defaultArtworkUri)
```

There are three call sites in `playItem`, `skipToPrevious`, and `setMediaItemAndPlay` paths.

- [ ] **Step 5: Run player unit tests**

Run:

```bash
./gradlew :player:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 6: Commit metadata fallback**

Run:

```bash
git add player/src/main/java/com/hank/musicfree/player/ext/MusicItemMediaExt.kt \
  player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt \
  player/src/test/java/com/hank/musicfree/player/ext/MusicItemMediaExtTest.kt
git commit -m "feat(player): add notification artwork fallback"
```

---

## Task 4: Notification Commands and Queue Delegate

**Files:**
- Create: `player/src/main/java/com/hank/musicfree/player/service/PlaybackNotificationActions.kt`
- Create: `player/src/main/java/com/hank/musicfree/player/service/PlaybackNotificationCommandHandler.kt`
- Modify: `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`
- Test: `player/src/test/java/com/hank/musicfree/player/service/PlaybackNotificationActionsTest.kt`
- Test: `player/src/test/java/com/hank/musicfree/player/service/PlaybackNotificationCommandHandlerTest.kt`

- [ ] **Step 1: Write failing action tests**

Create `player/src/test/java/com/hank/musicfree/player/service/PlaybackNotificationActionsTest.kt`:

```kotlin
package com.hank.musicfree.player.service

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(UnstableApi::class)
class PlaybackNotificationActionsTest {

    @Test
    fun `media button preferences expose previous and next custom commands`() {
        val buttons = PlaybackNotificationActions.mediaButtonPreferences()

        assertEquals(2, buttons.size)

        val previous = buttons[0]
        assertEquals(CommandButton.ICON_PREVIOUS, previous.icon)
        assertEquals(CommandButton.SLOT_BACK, previous.slots.get(0))
        assertEquals(Player.COMMAND_INVALID, previous.playerCommand)
        assertNotNull(previous.sessionCommand)
        assertEquals(
            PlaybackNotificationActions.ACTION_SKIP_TO_PREVIOUS,
            previous.sessionCommand?.customAction,
        )

        val next = buttons[1]
        assertEquals(CommandButton.ICON_NEXT, next.icon)
        assertEquals(CommandButton.SLOT_FORWARD, next.slots.get(0))
        assertEquals(Player.COMMAND_INVALID, next.playerCommand)
        assertNotNull(next.sessionCommand)
        assertEquals(
            PlaybackNotificationActions.ACTION_SKIP_TO_NEXT,
            next.sessionCommand?.customAction,
        )
    }
}
```

- [ ] **Step 2: Write failing command handler tests**

Create `player/src/test/java/com/hank/musicfree/player/service/PlaybackNotificationCommandHandlerTest.kt`:

```kotlin
package com.hank.musicfree.player.service

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackNotificationCommandHandlerTest {

    @Test
    fun `skip commands are no-op without attached controls`() {
        val controls = RecordingControls()
        PlaybackNotificationCommandHandler.attach(controls)
        PlaybackNotificationCommandHandler.detachAllForTest()

        PlaybackNotificationCommandHandler.skipToPrevious()
        PlaybackNotificationCommandHandler.skipToNext()

        assertEquals(emptyList<String>(), controls.calls)
    }

    @Test
    fun `skip commands delegate to attached controls`() {
        val controls = RecordingControls()
        PlaybackNotificationCommandHandler.attach(controls)

        PlaybackNotificationCommandHandler.skipToPrevious()
        PlaybackNotificationCommandHandler.skipToNext()

        assertEquals(listOf("previous", "next"), controls.calls)
        PlaybackNotificationCommandHandler.detach(controls)
    }

    @Test
    fun `detach only clears matching controls`() {
        val first = RecordingControls()
        val second = RecordingControls()
        PlaybackNotificationCommandHandler.attach(first)
        PlaybackNotificationCommandHandler.attach(second)

        PlaybackNotificationCommandHandler.detach(first)
        PlaybackNotificationCommandHandler.skipToNext()

        assertEquals(emptyList<String>(), first.calls)
        assertEquals(listOf("next"), second.calls)
        PlaybackNotificationCommandHandler.detach(second)
    }
}

private class RecordingControls : PlaybackNotificationQueueControls {
    val calls = mutableListOf<String>()

    override fun skipToPreviousFromNotification() {
        calls += "previous"
    }

    override fun skipToNextFromNotification() {
        calls += "next"
    }
}
```

- [ ] **Step 3: Run failing player service unit tests**

Run:

```bash
./gradlew :player:testDebugUnitTest --tests "*.PlaybackNotificationActionsTest" --tests "*.PlaybackNotificationCommandHandlerTest"
```

Expected: FAIL because the action and handler classes do not exist.

- [ ] **Step 4: Implement notification actions**

Create `player/src/main/java/com/hank/musicfree/player/service/PlaybackNotificationActions.kt`:

```kotlin
@file:OptIn(UnstableApi::class)

package com.hank.musicfree.player.service

import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand

object PlaybackNotificationActions {
    const val ACTION_SKIP_TO_PREVIOUS =
        "com.hank.musicfree.player.action.SKIP_TO_PREVIOUS"
    const val ACTION_SKIP_TO_NEXT =
        "com.hank.musicfree.player.action.SKIP_TO_NEXT"

    val SkipToPreviousCommand = SessionCommand(ACTION_SKIP_TO_PREVIOUS, Bundle.EMPTY)
    val SkipToNextCommand = SessionCommand(ACTION_SKIP_TO_NEXT, Bundle.EMPTY)

    fun mediaButtonPreferences(): List<CommandButton> {
        return listOf(
            CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setSessionCommand(SkipToPreviousCommand)
                .setDisplayName("上一首")
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            CommandButton.Builder(CommandButton.ICON_NEXT)
                .setSessionCommand(SkipToNextCommand)
                .setDisplayName("下一首")
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
        )
    }
}
```

- [ ] **Step 5: Implement the queue command handler**

Create `player/src/main/java/com/hank/musicfree/player/service/PlaybackNotificationCommandHandler.kt`:

```kotlin
package com.hank.musicfree.player.service

interface PlaybackNotificationQueueControls {
    fun skipToPreviousFromNotification()
    fun skipToNextFromNotification()
}

object PlaybackNotificationCommandHandler {
    @Volatile
    private var controls: PlaybackNotificationQueueControls? = null

    fun attach(nextControls: PlaybackNotificationQueueControls) {
        controls = nextControls
    }

    fun detach(previousControls: PlaybackNotificationQueueControls) {
        if (controls === previousControls) {
            controls = null
        }
    }

    fun skipToPrevious() {
        controls?.skipToPreviousFromNotification()
    }

    fun skipToNext() {
        controls?.skipToNextFromNotification()
    }

    internal fun detachAllForTest() {
        controls = null
    }
}
```

- [ ] **Step 6: Register PlayerController as queue controls**

Update `PlayerController.kt` imports:

```kotlin
import com.hank.musicfree.player.service.PlaybackNotificationCommandHandler
import com.hank.musicfree.player.service.PlaybackNotificationQueueControls
```

Change the class declaration to:

```kotlin
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlaybackNotificationQueueControls {
```

Add this init block after state fields are initialized:

```kotlin
    init {
        PlaybackNotificationCommandHandler.attach(this)
    }
```

Add these overrides near `skipToPrevious()`:

```kotlin
    override fun skipToPreviousFromNotification() {
        skipToPrevious()
    }

    override fun skipToNextFromNotification() {
        skipToNext()
    }
```

At the end of `release()`, after `controller?.release()`, add:

```kotlin
            PlaybackNotificationCommandHandler.detach(this)
```

- [ ] **Step 7: Run player unit tests**

Run:

```bash
./gradlew :player:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 8: Commit notification commands and delegate**

Run:

```bash
git add player/src/main/java/com/hank/musicfree/player/service/PlaybackNotificationActions.kt \
  player/src/main/java/com/hank/musicfree/player/service/PlaybackNotificationCommandHandler.kt \
  player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt \
  player/src/test/java/com/hank/musicfree/player/service/PlaybackNotificationActionsTest.kt \
  player/src/test/java/com/hank/musicfree/player/service/PlaybackNotificationCommandHandlerTest.kt
git commit -m "feat(player): add notification queue commands"
```

---

## Task 5: Media3 PlaybackService Notification Integration

**Files:**
- Create: `player/src/main/res/values/strings.xml`
- Modify: `player/src/main/java/com/hank/musicfree/player/service/PlaybackService.kt`
- Modify: `player/src/androidTest/java/com/hank/musicfree/player/service/PlaybackServiceTest.kt`

- [ ] **Step 1: Add failing PlaybackService notification tests**

Add these imports to `PlaybackServiceTest.kt`:

```kotlin
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.player.controller.PlayerController
```

Annotate the class:

```kotlin
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {
```

Add this helper near `connectController()`:

```kotlin
    private fun runOnAppThread(block: () -> Unit) {
        val latch = CountDownLatch(1)
        context.mainExecutor.execute {
            try {
                block()
            } finally {
                latch.countDown()
            }
        }
        latch.await()
    }

    private fun testItem(id: String) = MusicItem(
        id = id,
        platform = "test",
        title = "Song $id",
        artist = "Artist",
        album = null,
        duration = 1_000L,
        url = "android.resource://${context.packageName}/${
            com.hank.musicfree.player.test.R.raw.test_audio
        }",
        artwork = null,
        qualities = null,
    )

    private fun waitUntil(
        description: String,
        timeoutMs: Long = 3_000L,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        fail("Timed out waiting for $description")
    }
```

Add these tests inside `PlaybackServiceTest`:

```kotlin
    @Test
    fun mediaSessionExposesSessionActivity() {
        assertNotNull(controller!!.sessionActivity)
    }

    @Test
    fun mediaButtonPreferencesExposePreviousAndNextCommands() {
        val preferences = controller!!.mediaButtonPreferences
        val actions = preferences.mapNotNull { it.sessionCommand?.customAction }

        assertTrue(actions.contains(PlaybackNotificationActions.ACTION_SKIP_TO_PREVIOUS))
        assertTrue(actions.contains(PlaybackNotificationActions.ACTION_SKIP_TO_NEXT))
    }

    @Test
    fun notificationSkipNextCommandAdvancesPlayerControllerQueue() {
        val playerController = PlayerController(context)
        try {
            runOnAppThread {
                kotlinx.coroutines.runBlocking {
                    playerController.connect()
                }
            }
            runOnAppThread {
                playerController.playQueue(listOf(testItem("1"), testItem("2")), startIndex = 0)
            }
            waitUntil("controller starts first queued item") {
                playerController.playerState.value.currentItem?.id == "1"
            }

            lateinit var future: com.google.common.util.concurrent.ListenableFuture<SessionResult>
            runOnAppThread {
                future = controller!!.sendCustomCommand(
                    PlaybackNotificationActions.SkipToNextCommand,
                    Bundle.EMPTY,
                )
            }
            assertEquals(SessionResult.RESULT_SUCCESS, future.get(3, TimeUnit.SECONDS).resultCode)

            waitUntil("notification next command advances queue") {
                playerController.playerState.value.currentItem?.id == "2"
            }
        } finally {
            runOnAppThread {
                playerController.release()
            }
        }
    }

    @Test
    fun notificationSkipPreviousCommandMovesPlayerControllerQueueBack() {
        val playerController = PlayerController(context)
        try {
            runOnAppThread {
                kotlinx.coroutines.runBlocking {
                    playerController.connect()
                }
            }
            runOnAppThread {
                playerController.playQueue(listOf(testItem("1"), testItem("2")), startIndex = 1)
            }
            waitUntil("controller starts second queued item") {
                playerController.playerState.value.currentItem?.id == "2"
            }

            lateinit var future: com.google.common.util.concurrent.ListenableFuture<SessionResult>
            runOnAppThread {
                future = controller!!.sendCustomCommand(
                    PlaybackNotificationActions.SkipToPreviousCommand,
                    Bundle.EMPTY,
                )
            }
            assertEquals(SessionResult.RESULT_SUCCESS, future.get(3, TimeUnit.SECONDS).resultCode)

            waitUntil("notification previous command moves queue back") {
                playerController.playerState.value.currentItem?.id == "1"
            }
        } finally {
            runOnAppThread {
                playerController.release()
            }
        }
    }
```

- [ ] **Step 2: Run the focused failing instrumentation test**

Run:

```bash
./gradlew :player:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hank.musicfree.player.service.PlaybackServiceTest
```

Expected: FAIL because session activity, media button preferences, and custom command callbacks are not wired.

- [ ] **Step 3: Add notification channel string**

Create `player/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="playback_notification_channel_name">播放控制</string>
</resources>
```

- [ ] **Step 4: Implement PlaybackService notification integration**

Replace `PlaybackService.kt` with:

```kotlin
@file:OptIn(UnstableApi::class)

package com.hank.musicfree.player.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.hank.musicfree.player.R
import com.hank.musicfree.core.R as CoreR

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setChannelName(R.string.playback_notification_channel_name)
                .setNotificationId(NOTIFICATION_ID)
                .build()
                .apply {
                    setSmallIcon(CoreR.drawable.ic_motion_play)
                },
        )

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setMediaButtonPreferences(PlaybackNotificationActions.mediaButtonPreferences())
            .setCallback(playbackSessionCallback)
            .setSessionActivity(createSessionActivityPendingIntent())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun createSessionActivityPendingIntent(): PendingIntent {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(packageName)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        return PendingIntent.getActivity(
            this,
            SESSION_ACTIVITY_REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private val playbackSessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands: SessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(PlaybackNotificationActions.SkipToPreviousCommand)
                .add(PlaybackNotificationActions.SkipToNextCommand)
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                PlaybackNotificationActions.ACTION_SKIP_TO_PREVIOUS -> {
                    PlaybackNotificationCommandHandler.skipToPrevious()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                PlaybackNotificationActions.ACTION_SKIP_TO_NEXT -> {
                    PlaybackNotificationCommandHandler.skipToNext()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }

            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    private companion object {
        const val NOTIFICATION_CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 1001
        const val SESSION_ACTIVITY_REQUEST_CODE = 100
    }
}
```

- [ ] **Step 5: Run the focused instrumentation test**

Run:

```bash
./gradlew :player:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hank.musicfree.player.service.PlaybackServiceTest
```

Expected: PASS.

- [ ] **Step 6: Run player unit tests**

Run:

```bash
./gradlew :player:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 7: Commit PlaybackService notification integration**

Run:

```bash
git add player/src/main/res/values/strings.xml \
  player/src/main/java/com/hank/musicfree/player/service/PlaybackService.kt \
  player/src/androidTest/java/com/hank/musicfree/player/service/PlaybackServiceTest.kt
git commit -m "feat(player): show media playback notification"
```

---

## Task 6: Final Verification and Runtime Evidence

**Files:**
- No source edits expected.

- [ ] **Step 1: Run full debug build**

Run:

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run focused unit suites**

Run:

```bash
./gradlew :player:testDebugUnitTest
./gradlew :feature:settings:testDebugUnitTest --tests "*.PermissionsHelpersTest"
```

Expected: both commands report BUILD SUCCESSFUL.

- [ ] **Step 3: Run player instrumentation suite**

Run:

```bash
./gradlew :player:connectedDebugAndroidTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Install debug build on a device or emulator**

Run:

```bash
./gradlew :app:installDebug
```

Expected: BUILD SUCCESSFUL and APK installed.

- [ ] **Step 5: Runtime notification verification**

Use the installed app:

1. Launch the app.
2. Grant notification permission when prompted.
3. Play a song with title, artist, URL, and artwork.
4. Pull down the notification shade.
5. Confirm the notification shows the song title, artist, and artwork or default album art.
6. Press pause and play in the notification; confirm mini player or full player state follows.
7. Press next and previous in the notification; confirm the app queue changes to the expected song.
8. Drag the system media seek control; confirm playback position changes.
9. Tap the notification; confirm the app opens.
10. While playing, remove the app from recent tasks; confirm playback continues.

- [ ] **Step 6: Check final diff**

Run:

```bash
git status --short
```

Expected: no uncommitted source changes. If verification exposed a real defect, fix it with a new test-first task before closing the branch.

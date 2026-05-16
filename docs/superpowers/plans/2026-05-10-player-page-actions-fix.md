# Player Page Actions Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to execute task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 4 broken/missing capabilities on the song player page (`PlayerOperationsBar`): favorite crash hardening, quality picker, download, playback rate picker — aligned with RN parity at `../MusicFree/src/pages/musicDetail/components/content/albumCover/operations.tsx`.

**Architecture:** Reuse existing infra: `MediaSourceResolver.resolve(item, qualityWire)` for quality switching, `Downloader.enqueue(items, quality)` for download, Media3 `Player.setPlaybackParameters` for speed, `AppPreferences` for persistence. New surface = 2 ModalBottomSheet composables, 4 new `PlayerController` / `PlayerViewModel` methods, 1 new `AppPreferences` flow, 1 new `PlayerState` field.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Media3 ExoPlayer, Hilt, Coroutines/Flow, Room, DataStore.

**Spec:** [`docs/superpowers/specs/2026-05-10-player-page-actions-fix-design.md`](../specs/2026-05-10-player-page-actions-fix-design.md)

---

## Module Map

| Module | Files Created | Files Modified |
| --- | --- | --- |
| `:core` | `core/src/main/java/com/hank/musicfree/core/model/PlaybackSpeeds.kt` | `core/src/main/java/com/hank/musicfree/core/model/PlayQuality.kt` (add `wireName()` extension if missing — actually already implemented inline; defer) |
| `:data` | none | `data/src/main/java/com/hank/musicfree/data/datastore/AppPreferences.kt` |
| `:player` | none | `player/src/main/java/com/hank/musicfree/player/model/PlayerState.kt`, `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`, new `player/src/test/java/.../controller/PlayerControllerSpeedTest.kt` |
| `:feature:player-ui` | `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/quality/MusicQualitySheet.kt`, `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/rate/PlayRateSheet.kt`, `feature/player-ui/src/test/java/.../component/quality/MusicQualitySheetTest.kt`, `feature/player-ui/src/test/java/.../component/rate/PlayRateSheetTest.kt` | `feature/player-ui/build.gradle.kts`, `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerViewModel.kt`, `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerViewModelTest.kt`, `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt` |

---

## Task 1: core PlaybackSpeeds + data AppPreferences.playRate

**Files:**
- Create: `core/src/main/java/com/hank/musicfree/core/model/PlaybackSpeeds.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/datastore/AppPreferences.kt`
- Test: `data/src/test/java/com/hank/musicfree/data/datastore/AppPreferencesPlayRateTest.kt` (new)

### Steps

- [ ] **Step 1: Create `PlaybackSpeeds.kt`**

```kotlin
package com.hank.musicfree.core.model

object PlaybackSpeeds {
    /** Speeds aligned with RN [50, 75, 100, 125, 150, 175, 200] / 100. */
    val ALL: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    const val DEFAULT: Float = 1.0f
}
```

- [ ] **Step 2: Add `playRate` flow + setter to `AppPreferences.kt`**

In `data/src/main/java/com/hank/musicfree/data/datastore/AppPreferences.kt`:

Add the following imports if not present:

```kotlin
import androidx.datastore.preferences.core.floatPreferencesKey
import com.hank.musicfree.core.model.PlaybackSpeeds
```

Add a new flow + setter just below `setPlayQuality`:

```kotlin
val playRate: Flow<Float> = dataStore.data.map { prefs ->
    prefs[KEY_PLAY_RATE] ?: PlaybackSpeeds.DEFAULT
}

suspend fun setPlayRate(rate: Float) {
    dataStore.edit { it[KEY_PLAY_RATE] = rate }
}
```

In the keys companion section at the bottom of the class, add:

```kotlin
private val KEY_PLAY_RATE = floatPreferencesKey("play_rate")
```

(Locate by searching for `KEY_PLAY_QUALITY` and add adjacent.)

- [ ] **Step 3: Write failing test for AppPreferences.playRate**

Create `data/src/test/java/com/hank/musicfree/data/datastore/AppPreferencesPlayRateTest.kt`:

```kotlin
package com.hank.musicfree.data.datastore

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.hank.musicfree.core.model.PlaybackSpeeds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppPreferencesPlayRateTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var prefs: AppPreferences
    private lateinit var dataStoreFile: File

    @Before
    fun setup() {
        dataStoreFile = tempFolder.newFile("test_prefs.preferences_pb")
        val store = PreferenceDataStoreFactory.create(produceFile = { dataStoreFile })
        prefs = AppPreferences(store)
    }

    @Test
    fun `playRate defaults to 1_0`() = runTest {
        assertEquals(PlaybackSpeeds.DEFAULT, prefs.playRate.first())
    }

    @Test
    fun `setPlayRate persists value across reads`() = runTest {
        prefs.setPlayRate(1.5f)
        assertEquals(1.5f, prefs.playRate.first())
    }
}
```

- [ ] **Step 4: Run tests; expect them to pass (implementation already added)**

```bash
./gradlew :data:testDebugUnitTest --tests "AppPreferencesPlayRateTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`. If FAIL: re-check imports and key name.

- [ ] **Step 5: Run full data + core test suites to confirm no regression**

```bash
./gradlew :core:testDebugUnitTest :data:testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/hank/musicfree/core/model/PlaybackSpeeds.kt \
        data/src/main/java/com/hank/musicfree/data/datastore/AppPreferences.kt \
        data/src/test/java/com/hank/musicfree/data/datastore/AppPreferencesPlayRateTest.kt
git commit -m "$(cat <<'EOF'
feat(core,data): add PlaybackSpeeds + AppPreferences.playRate

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: player module — PlayerState.playbackSpeed, PlayerController.setPlaybackSpeed + changeQuality

**Files:**
- Modify: `player/src/main/java/com/hank/musicfree/player/model/PlayerState.kt`
- Modify: `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`
- Test: `player/src/test/java/com/hank/musicfree/player/controller/PlayerControllerSpeedTest.kt` (new)

### Steps

- [ ] **Step 1: Add `playbackSpeed` field to PlayerState**

Replace `player/src/main/java/com/hank/musicfree/player/model/PlayerState.kt` body:

```kotlin
package com.hank.musicfree.player.model

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlaybackSpeeds
import com.hank.musicfree.core.model.RepeatMode

data class PlayerState(
    val currentItem: MusicItem? = null,
    val isPlaying: Boolean = false,
    val playbackState: PlaybackState = PlaybackState.IDLE,
    val duration: Long = 0L,
    val position: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val playbackSpeed: Float = PlaybackSpeeds.DEFAULT,
) {
    val hasMedia: Boolean get() = currentItem != null

    companion object {
        val EMPTY = PlayerState()
    }
}
```

- [ ] **Step 2: Add backing field + emitState wiring in PlayerController**

In `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`:

Just below the existing private vars `repeatMode` and `shuffleEnabled` (around line 72-73), add:

```kotlin
    private var playbackSpeed: Float = com.hank.musicfree.core.model.PlaybackSpeeds.DEFAULT
```

In `emitState()` (around line 533-543), update the `PlayerState(...)` construction to include `playbackSpeed = playbackSpeed`. The full updated function:

```kotlin
    private fun emitState() {
        val controller = mediaController
        _playerState.value = PlayerState(
            currentItem = playQueue.currentItem,
            isPlaying = controller?.isPlaying == true,
            playbackState = controller?.playbackState.toPlaybackState(),
            duration = controller?.duration?.coerceAtLeast(0L) ?: 0L,
            position = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
            playbackSpeed = playbackSpeed,
        )
    }
```

- [ ] **Step 3: Add `setPlaybackSpeed` method**

Insert this method right above the `release()` function (around line 316):

```kotlin
    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        runOnControllerThread {
            mediaController?.let {
                it.setPlaybackParameters(androidx.media3.common.PlaybackParameters(speed))
            }
            emitState()
        }
    }
```

Add the import at the top of the file (alphabetical order, after `androidx.media3.common.Player`):

```kotlin
import androidx.media3.common.PlaybackParameters
```

- [ ] **Step 4: Add `changeQuality` method**

Insert right after `setPlaybackSpeed`:

```kotlin
    fun changeQuality(quality: com.hank.musicfree.core.model.PlayQuality) {
        val item = playQueue.currentItem ?: return
        val expectedIndex = playQueue.currentIndex
        val savedPosition = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val wasPlaying = mediaController?.isPlaying == true

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val resolution = runCatching {
                mediaSourceResolver.resolve(item, quality.name.lowercase())
            }.getOrNull()
            val playable = resolution?.item
            if (playable == null || playable.url.isNullOrBlank()) {
                _errorEvents.emit("当前歌曲不支持该音质")
                return@launch
            }
            if (!playQueue.isCurrentItem(expectedIndex, item)) return@launch
            playQueue.replaceCurrent(expectedIndex, item, playable)
            emitQueueState()
            withConnectedController { controller ->
                try {
                    val mediaItem = playable.toMediaItem(defaultArtworkUri)
                    controller.setMediaItem(mediaItem)
                    controller.prepare()
                    if (savedPosition > 0L) controller.seekTo(savedPosition)
                    if (wasPlaying) controller.play()
                } catch (e: RuntimeException) {
                    _errorEvents.tryEmit("切换音质失败: ${e.message}")
                }
            }
        }
    }
```

(`isCurrentItem` is already a private extension on `PlayQueue` at line 395 of the file — no import needed.)

- [ ] **Step 5: Write failing tests for setPlaybackSpeed**

Create `player/src/test/java/com/hank/musicfree/player/controller/PlayerControllerSpeedTest.kt`:

```kotlin
package com.hank.musicfree.player.controller

import android.content.Context
import com.hank.musicfree.core.model.PlaybackSpeeds
import com.hank.musicfree.player.service.PlaybackNotificationCommandHandler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerControllerSpeedTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        PlaybackNotificationCommandHandler.detachAllForTest()
    }

    @Test
    fun `default playbackSpeed is 1_0`() {
        val controller = PlayerController(context)
        try {
            assertEquals(PlaybackSpeeds.DEFAULT, controller.playerState.value.playbackSpeed)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `setPlaybackSpeed updates state without connected controller`() {
        val controller = PlayerController(context)
        try {
            controller.setPlaybackSpeed(1.5f)
            assertEquals(1.5f, controller.playerState.value.playbackSpeed)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `setPlaybackSpeed accepts edge values`() {
        val controller = PlayerController(context)
        try {
            controller.setPlaybackSpeed(0.5f)
            assertEquals(0.5f, controller.playerState.value.playbackSpeed)
            controller.setPlaybackSpeed(2.0f)
            assertEquals(2.0f, controller.playerState.value.playbackSpeed)
        } finally {
            controller.release()
        }
    }
}
```

- [ ] **Step 6: Run player tests**

```bash
./gradlew :player:testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL` — all existing tests still pass and new `PlayerControllerSpeedTest` passes.

- [ ] **Step 7: Commit**

```bash
git add player/src/main/java/com/hank/musicfree/player/model/PlayerState.kt \
        player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt \
        player/src/test/java/com/hank/musicfree/player/controller/PlayerControllerSpeedTest.kt
git commit -m "$(cat <<'EOF'
feat(player): add playbackSpeed state + setPlaybackSpeed + changeQuality

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: feature/player-ui PlayerViewModel — favorite hardening + quality/speed/download

**Files:**
- Modify: `feature/player-ui/build.gradle.kts`
- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerViewModel.kt`
- Modify: `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerViewModelTest.kt`

### Steps

- [ ] **Step 1: Add `:downloader` dependency to `:feature:player-ui`**

In `feature/player-ui/build.gradle.kts`, locate the `dependencies { ... }` block and add immediately after `implementation(project(":player"))`:

```kotlin
    implementation(project(":downloader"))
```

- [ ] **Step 2: Modify PlayerViewModel — add downloader injection + new state + methods**

Replace the entire body of `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerViewModel.kt` with:

```kotlin
package com.hank.musicfree.feature.playerui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.PlaybackSpeeds
import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.core.lyric.LyricTiming
import com.hank.musicfree.core.ui.AddToPlaylistSheetState
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.repository.LocalLyricKind
import com.hank.musicfree.data.repository.PlaylistRepository
import com.hank.musicfree.downloader.Downloader
import com.hank.musicfree.downloader.model.MediaKey
import com.hank.musicfree.feature.playerui.lyrics.LyricLoadState
import com.hank.musicfree.feature.playerui.lyrics.LyricSearchGroup
import com.hank.musicfree.feature.playerui.lyrics.PlayerLyricLoader
import com.hank.musicfree.feature.playerui.component.queue.PlayQueueUiModel
import com.hank.musicfree.feature.playerui.lyrics.PlayerLyricsUiState
import com.hank.musicfree.player.controller.PlayerController
import com.hank.musicfree.player.model.PlayerState
import com.hank.musicfree.player.queue.PlayQueueSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val playlistRepository: PlaylistRepository,
    private val playerLyricLoader: PlayerLyricLoader,
    private val appPreferences: AppPreferences,
    private val downloader: Downloader,
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = playerController.playerState

    private val _internalErrorEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errorEvents: SharedFlow<String> =
        merge(playerController.errorEvents, _internalErrorEvents.asSharedFlow())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
            .let { stateFlow ->
                MutableSharedFlow<String>(extraBufferCapacity = 4).also { sink ->
                    viewModelScope.launch {
                        merge(playerController.errorEvents, _internalErrorEvents.asSharedFlow())
                            .collect { sink.tryEmit(it) }
                    }
                }
            }

    val queueUiModel: StateFlow<PlayQueueUiModel> = combine(
        playerController.queueState,
        playerState,
    ) { snapshot, player ->
        PlayQueueUiModel(
            items = snapshot.items,
            currentIndex = snapshot.currentIndex,
            repeatMode = player.repeatMode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayQueueUiModel.EMPTY)

    private val rawLyricLoadState: StateFlow<LyricLoadState> = playerState
        .map { it.currentItem }
        .distinctUntilChangedBy { item -> item?.let { it.platform to it.id } }
        .flatMapLatest { item ->
            playerLyricLoader.observeLyrics(item)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LyricLoadState.NoTrack)

    private val lyricLoadState: StateFlow<LyricLoadState> = rawLyricLoadState
        .scan<LyricLoadState, LyricLoadState?>(null) { previous, next ->
            val previousReady = previous as? LyricLoadState.Ready
            val nextLoading = next as? LyricLoadState.Loading
            if (previousReady != null && nextLoading != null && previousReady.music.sameMusicKey(nextLoading.music)) {
                previousReady
            } else {
                next
            }
        }
        .filterNotNull()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LyricLoadState.NoTrack)

    val lyricsUiState: StateFlow<PlayerLyricsUiState> = combine(
        playerState,
        lyricLoadState,
        appPreferences.lyricShowTranslation,
        appPreferences.lyricDetailFontSize,
    ) { playback, lyricState, showTranslation, fontSize ->
        val ready = lyricState as? LyricLoadState.Ready
        PlayerLyricsUiState(
            loadState = lyricState,
            document = ready?.document,
            currentLineIndex = ready?.document?.let {
                LyricTiming.currentLineIndex(
                    lines = it.lines,
                    playbackPositionMs = playback.position,
                    userOffsetMs = ready.userOffsetMs,
                    metaOffsetMs = it.metaOffsetMs,
                )
            },
            showTranslation = showTranslation,
            fontSizeLevel = fontSize,
            userOffsetMs = ready?.userOffsetMs ?: 0L,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerLyricsUiState())

    // ---- favorite ----

    val isCurrentFavorite: StateFlow<Boolean> = playerState
        .map { it.currentItem }
        .distinctUntilChangedBy { item -> item?.let { it.platform to it.id } }
        .flatMapLatest { item ->
            if (item == null) flowOf(false)
            else playlistRepository.isFavorite(item)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleCurrentFavorite() {
        val item = playerState.value.currentItem ?: return
        viewModelScope.launch {
            try {
                playlistRepository.toggleFavorite(item)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _internalErrorEvents.tryEmit("收藏操作失败: ${error.message ?: error::class.simpleName}")
            }
        }
    }

    // ---- quality ----

    val currentQuality: StateFlow<PlayQuality> =
        appPreferences.playQuality
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayQuality.STANDARD)

    fun setCurrentQuality(quality: PlayQuality) {
        viewModelScope.launch {
            appPreferences.setPlayQuality(quality)
        }
        playerController.changeQuality(quality)
    }

    // ---- playback speed ----

    val currentSpeed: StateFlow<Float> =
        appPreferences.playRate
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaybackSpeeds.DEFAULT)

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            appPreferences.setPlayRate(speed)
        }
        playerController.setPlaybackSpeed(speed)
    }

    // ---- download ----

    val isCurrentDownloaded: StateFlow<Boolean> = combine(
        playerState.map { it.currentItem }.distinctUntilChangedBy { it?.let { item -> item.platform to item.id } },
        downloader.downloadedKeys,
    ) { item, keys ->
        item != null && keys.contains(MediaKey.of(item))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun downloadCurrent(quality: PlayQuality) {
        val item = playerState.value.currentItem ?: return
        downloader.enqueue(listOf(item), quality)
    }

    // ---- add-to-playlist sheet ----

    private val _sheetState = MutableStateFlow(AddToPlaylistSheetState())
    val sheetState: StateFlow<AddToPlaylistSheetState> = _sheetState.asStateFlow()

    val allPlaylists: StateFlow<List<Playlist>> =
        playlistRepository.observeAllPlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _lyricSearchResults = MutableStateFlow<List<LyricSearchGroup>>(emptyList())
    val lyricSearchResults: StateFlow<List<LyricSearchGroup>> = _lyricSearchResults.asStateFlow()

    private val _lyricSearchLoading = MutableStateFlow(false)
    val lyricSearchLoading: StateFlow<Boolean> = _lyricSearchLoading.asStateFlow()

    fun showAddToPlaylistSheet() {
        val item = playerState.value.currentItem ?: return
        _sheetState.value = AddToPlaylistSheetState.single(item)
    }

    fun hideAddToPlaylistSheet() {
        _sheetState.value = AddToPlaylistSheetState()
    }

    fun addPendingToPlaylist(targetPlaylistId: String) {
        val item = _sheetState.value.pendingItem ?: return
        viewModelScope.launch {
            playlistRepository.addMusicToPlaylist(targetPlaylistId, item)
            hideAddToPlaylistSheet()
        }
    }

    fun createPlaylistAndAddPending(name: String) {
        val item = _sheetState.value.pendingItem ?: return
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            playlistRepository.createPlaylist(Playlist(id = newId, name = name, coverUri = null))
            playlistRepository.addMusicToPlaylist(newId, item)
            hideAddToPlaylistSheet()
        }
    }

    // ---- playback controls ----

    fun togglePlayPause() {
        if (playerState.value.isPlaying) {
            playerController.pause()
        } else {
            playerController.play()
        }
    }

    fun skipToNext() = playerController.skipToNext()

    fun skipToPrevious() = playerController.skipToPrevious()

    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)

    fun cycleRepeatMode() = playerController.cycleRepeatMode()

    fun cyclePlaybackMode() = playerController.cyclePlaybackMode()

    fun toggleShuffle() = playerController.toggleShuffle()

    fun playQueueIndex(index: Int) = playerController.skipTo(index)

    fun removeFromQueue(index: Int) = playerController.removeFromQueue(index)

    fun clearQueue() = playerController.reset()

    fun setLyricShowTranslation(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setLyricShowTranslation(enabled) }
    }

    fun setLyricDetailFontSize(level: Int) {
        viewModelScope.launch { appPreferences.setLyricDetailFontSize(level) }
    }

    fun searchLyrics() {
        val item = playerState.value.currentItem ?: return
        viewModelScope.launch {
            _lyricSearchLoading.value = true
            _lyricSearchResults.value = emptyList()
            try {
                _lyricSearchResults.value = playerLyricLoader.searchCandidates(item)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _lyricSearchResults.value = emptyList()
            } finally {
                _lyricSearchLoading.value = false
            }
        }
    }

    fun associateLyric(target: MusicItem) {
        val item = playerState.value.currentItem ?: return
        viewModelScope.launch {
            playerLyricLoader.associateLyric(item, target)
        }
    }

    fun clearAssociatedLyric() {
        val item = playerState.value.currentItem ?: return
        viewModelScope.launch {
            playerLyricLoader.clearAssociatedLyric(item)
        }
    }

    fun setLyricOffset(offsetMs: Long) {
        val item = playerState.value.currentItem ?: return
        viewModelScope.launch {
            playerLyricLoader.setLyricOffset(item, offsetMs)
        }
    }

    fun importLocalLyric(rawText: String, kind: LocalLyricKind) {
        val item = playerState.value.currentItem ?: return
        viewModelScope.launch {
            playerLyricLoader.importLocalLyric(item, rawText, kind)
        }
    }

    fun deleteLocalLyric() {
        val item = playerState.value.currentItem ?: return
        viewModelScope.launch {
            playerLyricLoader.deleteLocalLyric(item)
        }
    }

    fun seekToLyricLine(lineTimeMs: Long) {
        val ready = lyricsUiState.value.loadState as? LyricLoadState.Ready ?: return
        val duration = playerState.value.duration
        val seekMs = LyricTiming.seekPositionForLine(
            lineTimeMs = lineTimeMs,
            userOffsetMs = ready.userOffsetMs,
            metaOffsetMs = ready.document.metaOffsetMs,
            durationMs = duration,
        )
        playerController.seekTo(seekMs)
        playerController.play()
    }
}

private fun MusicItem.sameMusicKey(other: MusicItem): Boolean =
    platform == other.platform && id == other.id
```

**IMPORTANT — `errorEvents` plumbing:** The original ViewModel exposed `playerController.errorEvents` directly. We now need to merge VM-internal errors (e.g. favorite failure) with controller errors. The simplest correct implementation is a single internal SharedFlow with the merge done in a `viewModelScope.launch` that re-emits to a sink. Replace the messy errorEvents property above with:

```kotlin
    private val _errorEventsSink = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errorEvents: SharedFlow<String> = _errorEventsSink.asSharedFlow()

    init {
        viewModelScope.launch {
            playerController.errorEvents.collect { _errorEventsSink.tryEmit(it) }
        }
        viewModelScope.launch {
            _internalErrorEvents.collect { _errorEventsSink.tryEmit(it) }
        }
    }
```

Use this `init {}` block + sink pattern in the actual implementation. (The single-pass merge fragment shown earlier is awkward; this is cleaner. The implementer should use this final form.)

After applying this correction, the `errorEvents` line near the top becomes simply:

```kotlin
    private val _internalErrorEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private val _errorEventsSink = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errorEvents: SharedFlow<String> = _errorEventsSink.asSharedFlow()
```

And add the `init {}` block after the property definitions but before `// ---- favorite ----`.

Remove the unused `import kotlinx.coroutines.flow.merge`.

- [ ] **Step 3: Update PlayerViewModelTest with new mocks + tests**

In `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerViewModelTest.kt`:

Add these imports (after existing imports):

```kotlin
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.PlaybackSpeeds
import com.hank.musicfree.downloader.Downloader
import com.hank.musicfree.downloader.engine.DownloadEvent
import com.hank.musicfree.downloader.model.DownloadTaskUi
import com.hank.musicfree.downloader.model.MediaKey
import kotlinx.coroutines.flow.MutableSharedFlow
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
```

Add a `private val downloader: Downloader = mock()` field below the existing `appPreferences` mock.

Add these `MutableStateFlow` fields next to the existing `lyricShowTranslationFlow`:

```kotlin
    private val playQualityFlow = MutableStateFlow(PlayQuality.STANDARD)
    private val playRateFlow = MutableStateFlow(PlaybackSpeeds.DEFAULT)
    private val downloaderTasksFlow = MutableStateFlow<List<DownloadTaskUi>>(emptyList())
    private val downloaderDownloadedKeysFlow = MutableStateFlow<Set<MediaKey>>(emptySet())
    private val downloaderEventsFlow = MutableSharedFlow<DownloadEvent>()
    private val controllerErrorFlow = MutableSharedFlow<String>(extraBufferCapacity = 4)
```

In the `@Before setup()` method, add (after existing `whenever(...)` lines):

```kotlin
        whenever(appPreferences.playQuality).thenReturn(playQualityFlow)
        whenever(appPreferences.playRate).thenReturn(playRateFlow)
        whenever(downloader.tasks).thenReturn(downloaderTasksFlow)
        whenever(downloader.downloadedKeys).thenReturn(downloaderDownloadedKeysFlow)
        whenever(downloader.events).thenReturn(downloaderEventsFlow)
        whenever(playerController.errorEvents).thenReturn(controllerErrorFlow)
```

Update `createViewModel()` to pass downloader:

```kotlin
    private fun createViewModel() = PlayerViewModel(
        playerController,
        playlistRepository,
        playerLyricLoader,
        appPreferences,
        downloader,
    )
```

Add the following new tests at the end of the class (before the final `}`):

```kotlin
    @Test
    fun `currentQuality reflects appPreferences playQuality`() = runTest {
        playQualityFlow.value = PlayQuality.HIGH
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(PlayQuality.HIGH, viewModel.currentQuality.value)
    }

    @Test
    fun `setCurrentQuality writes prefs and calls controller changeQuality`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setCurrentQuality(PlayQuality.SUPER)
        advanceUntilIdle()

        verify(appPreferences).setPlayQuality(PlayQuality.SUPER)
        verify(playerController).changeQuality(PlayQuality.SUPER)
    }

    @Test
    fun `currentSpeed reflects appPreferences playRate`() = runTest {
        playRateFlow.value = 1.5f
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(1.5f, viewModel.currentSpeed.value)
    }

    @Test
    fun `setPlaybackSpeed writes prefs and calls controller setPlaybackSpeed`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setPlaybackSpeed(1.25f)
        advanceUntilIdle()

        verify(appPreferences).setPlayRate(1.25f)
        verify(playerController).setPlaybackSpeed(1.25f)
    }

    @Test
    fun `downloadCurrent enqueues current item with quality`() = runTest {
        val item = MusicItem(id = "11", platform = "demo", title = "T", artist = "A", album = null, duration = 1L, url = null, artwork = null, qualities = null)
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.downloadCurrent(PlayQuality.HIGH)

        verify(downloader).enqueue(eq(listOf(item)), eq(PlayQuality.HIGH))
    }

    @Test
    fun `downloadCurrent is no-op when no current item`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.downloadCurrent(PlayQuality.HIGH)

        verify(downloader, never()).enqueue(any(), any())
    }

    @Test
    fun `isCurrentDownloaded true when downloadedKeys contains current item key`() = runTest {
        val item = MusicItem(id = "33", platform = "p", title = "X", artist = "A", album = null, duration = 1L, url = null, artwork = null, qualities = null)
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item)
        downloaderDownloadedKeysFlow.value = setOf(MediaKey.of(item))
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.isCurrentDownloaded.value)
    }

    @Test
    fun `toggleCurrentFavorite emits error event on repository failure`() = runTest {
        val item = MusicItem(id = "fav", platform = "p", title = "X", artist = "A", album = null, duration = 1L, url = null, artwork = null, qualities = null)
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item)
        whenever(playlistRepository.toggleFavorite(item)).thenThrow(RuntimeException("boom"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        val errors = mutableListOf<String>()
        val collectJob = launch { viewModel.errorEvents.collect { errors.add(it) } }
        advanceUntilIdle()

        viewModel.toggleCurrentFavorite()
        advanceUntilIdle()

        assertTrue(errors.any { it.contains("收藏") })
        collectJob.cancel()
    }
```

(Mockito's `whenever(suspend...).thenThrow(...)` — if the test runner complains about suspending stub, replace with `whenever(playlistRepository.toggleFavorite(item)).doSuspendableAnswer { throw RuntimeException("boom") }` and add `import org.mockito.kotlin.doSuspendableAnswer`.)

- [ ] **Step 4: Run tests**

```bash
./gradlew :feature:player-ui:testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`. If tests fail, inspect the actual error and adjust the failing test/implementation, NOT both.

- [ ] **Step 5: Commit**

```bash
git add feature/player-ui/build.gradle.kts \
        feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerViewModel.kt \
        feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerViewModelTest.kt
git commit -m "$(cat <<'EOF'
feat(player-ui): harden favorite + add quality/speed/download VM state

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: feature/player-ui — MusicQualitySheet + PlayRateSheet composables

**Files:**
- Create: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/quality/MusicQualitySheet.kt`
- Create: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/rate/PlayRateSheet.kt`
- Create: `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/component/quality/MusicQualitySheetTest.kt`
- Create: `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/component/rate/PlayRateSheetTest.kt`

### Steps

- [ ] **Step 1: Create `MusicQualitySheet.kt`**

```kotlin
package com.hank.musicfree.feature.playerui.component.quality

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.hank.musicfree.core.R
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.QualityInfo
import com.hank.musicfree.core.theme.IconSizes
import com.hank.musicfree.core.theme.rpx

enum class MusicQualitySheetMode { Play, Download }

private val DISPLAY_ORDER: List<PlayQuality> = listOf(
    PlayQuality.LOW, PlayQuality.STANDARD, PlayQuality.HIGH, PlayQuality.SUPER,
)

private fun PlayQuality.label(): String = when (this) {
    PlayQuality.LOW -> "低音质"
    PlayQuality.STANDARD -> "标准音质"
    PlayQuality.HIGH -> "高音质"
    PlayQuality.SUPER -> "超高音质"
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> "%.1f MB".format(mb)
        kb >= 1.0 -> "%.0f KB".format(kb)
        else -> "$bytes B"
    }
}

const val MusicQualitySheetTestTag = "player.quality.sheet"
const val MusicQualitySheetItemTestTagPrefix = "player.quality.sheet.item."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicQualitySheet(
    current: PlayQuality?,
    mode: MusicQualitySheetMode,
    availableQualities: Map<PlayQuality, QualityInfo>?,
    onSelect: (PlayQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(MusicQualitySheetTestTag),
    ) {
        MusicQualitySheetContent(
            current = current,
            mode = mode,
            availableQualities = availableQualities,
            onSelect = {
                onSelect(it)
                onDismiss()
            },
        )
    }
}

@Composable
internal fun MusicQualitySheetContent(
    current: PlayQuality?,
    mode: MusicQualitySheetMode,
    availableQualities: Map<PlayQuality, QualityInfo>?,
    onSelect: (PlayQuality) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rpx(24), vertical = rpx(16)),
    ) {
        val title = when (mode) {
            MusicQualitySheetMode.Play -> "选择播放音质"
            MusicQualitySheetMode.Download -> "选择下载音质"
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = rpx(8)),
        )
        DISPLAY_ORDER.forEach { quality ->
            val sizeText = availableQualities?.get(quality)?.size?.let { " (${formatSize(it)})" }.orEmpty()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rpx(96))
                    .clickable { onSelect(quality) }
                    .testTag(MusicQualitySheetItemTestTagPrefix + quality.name),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = quality.label() + sizeText,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (current == quality) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = "已选中",
                        modifier = Modifier.size(IconSizes.normal),
                    )
                } else {
                    Box(modifier = Modifier.size(IconSizes.normal))
                }
            }
        }
    }
}
```

Verify `R.drawable.ic_check` exists; if not, run `find core -name "ic_check*.xml"` and substitute the available resource. If none exist, fall back to `androidx.compose.material.icons.Icons.Default.Check` and `Icon(imageVector = ...)`.

- [ ] **Step 2: Create `PlayRateSheet.kt`**

```kotlin
package com.hank.musicfree.feature.playerui.component.rate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.hank.musicfree.core.R
import com.hank.musicfree.core.model.PlaybackSpeeds
import com.hank.musicfree.core.theme.IconSizes
import com.hank.musicfree.core.theme.rpx

const val PlayRateSheetTestTag = "player.rate.sheet"
const val PlayRateSheetItemTestTagPrefix = "player.rate.sheet.item."

private fun rateLabel(rate: Float): String = if (rate == rate.toInt().toFloat()) {
    "${rate.toInt()}.0x"
} else {
    "${"%.2f".format(rate).trimEnd('0').trimEnd('.')}x"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayRateSheet(
    current: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(PlayRateSheetTestTag),
    ) {
        PlayRateSheetContent(
            current = current,
            onSelect = {
                onSelect(it)
                onDismiss()
            },
        )
    }
}

@Composable
internal fun PlayRateSheetContent(
    current: Float,
    onSelect: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rpx(24), vertical = rpx(16)),
    ) {
        Text(
            text = "选择播放速度",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = rpx(8)),
        )
        PlaybackSpeeds.ALL.forEach { rate ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rpx(96))
                    .clickable { onSelect(rate) }
                    .testTag(PlayRateSheetItemTestTagPrefix + rate.toString()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = rateLabel(rate),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (current == rate) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = "已选中",
                        modifier = Modifier.size(IconSizes.normal),
                    )
                } else {
                    Box(modifier = Modifier.size(IconSizes.normal))
                }
            }
        }
    }
}
```

- [ ] **Step 3: Write `MusicQualitySheetTest.kt` (Robolectric Compose)**

```kotlin
package com.hank.musicfree.feature.playerui.component.quality

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.QualityInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicQualitySheetTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `lists all four qualities in order`() {
        rule.setContent {
            MusicQualitySheetContent(
                current = PlayQuality.STANDARD,
                mode = MusicQualitySheetMode.Play,
                availableQualities = null,
                onSelect = {},
            )
        }
        rule.onNodeWithText("低音质").assertIsDisplayed()
        rule.onNodeWithText("标准音质").assertIsDisplayed()
        rule.onNodeWithText("高音质").assertIsDisplayed()
        rule.onNodeWithText("超高音质").assertIsDisplayed()
    }

    @Test
    fun `clicking quality calls onSelect with that quality`() {
        var selected: PlayQuality? = null
        rule.setContent {
            MusicQualitySheetContent(
                current = PlayQuality.STANDARD,
                mode = MusicQualitySheetMode.Play,
                availableQualities = null,
                onSelect = { selected = it },
            )
        }
        rule.onNodeWithTag(MusicQualitySheetItemTestTagPrefix + "HIGH").performClick()
        assertEquals(PlayQuality.HIGH, selected)
    }

    @Test
    fun `download mode renders download title`() {
        rule.setContent {
            MusicQualitySheetContent(
                current = null,
                mode = MusicQualitySheetMode.Download,
                availableQualities = null,
                onSelect = {},
            )
        }
        rule.onNodeWithText("选择下载音质").assertIsDisplayed()
    }

    @Test
    fun `shows size hint when availableQualities provides bytes`() {
        rule.setContent {
            MusicQualitySheetContent(
                current = PlayQuality.STANDARD,
                mode = MusicQualitySheetMode.Play,
                availableQualities = mapOf(PlayQuality.HIGH to QualityInfo(url = null, size = 5_242_880L)),
                onSelect = {},
            )
        }
        rule.onNodeWithText("高音质 (5.0 MB)").assertIsDisplayed()
    }
}
```

- [ ] **Step 4: Write `PlayRateSheetTest.kt`**

```kotlin
package com.hank.musicfree.feature.playerui.component.rate

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hank.musicfree.core.model.PlaybackSpeeds
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayRateSheetTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `lists all defined rates`() {
        rule.setContent {
            PlayRateSheetContent(current = 1.0f, onSelect = {})
        }
        PlaybackSpeeds.ALL.forEach { rate ->
            val label = if (rate == rate.toInt().toFloat()) "${rate.toInt()}.0x" else "${rate}x"
            // Use lenient assertion: at least the test tag exists
            rule.onNodeWithTag("player.rate.sheet.item.$rate").assertIsDisplayed()
        }
    }

    @Test
    fun `clicking a rate calls onSelect`() {
        var selected: Float? = null
        rule.setContent {
            PlayRateSheetContent(current = 1.0f, onSelect = { selected = it })
        }
        rule.onNodeWithTag("player.rate.sheet.item.1.5").performClick()
        assertEquals(1.5f, selected)
    }
}
```

- [ ] **Step 5: Run feature/player-ui tests**

```bash
./gradlew :feature:player-ui:testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`. If `R.drawable.ic_check` is missing, switch to material icons (see note in Step 1).

- [ ] **Step 6: Commit**

```bash
git add feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/quality/MusicQualitySheet.kt \
        feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/component/rate/PlayRateSheet.kt \
        feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/component/quality/MusicQualitySheetTest.kt \
        feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/component/rate/PlayRateSheetTest.kt
git commit -m "$(cat <<'EOF'
feat(player-ui): add MusicQualitySheet + PlayRateSheet composables

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: feature/player-ui — Wire PlayerScreen / PlayerOperationsBar

**Files:**
- Modify: `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt`

### Steps

- [ ] **Step 1: Update `PlayerCoverPageContent` signature + `PlayerOperationsBar` to receive new state**

In `PlayerScreen.kt`, modify `PlayerCoverPageContent` (around line 439) signature to:

```kotlin
@Composable
internal fun PlayerCoverPageContent(
    artworkUrl: String?,
    isFav: Boolean,
    hasCurrentItem: Boolean,
    currentQuality: PlayQuality,
    isDownloaded: Boolean,
    currentSpeed: Float,
    onToggleFav: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleLyrics: () -> Unit,
    onQualityClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onSpeedClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Pass the new params through to `PlayerOperationsBar(...)`.

Modify `PlayerOperationsBar` (around line 514-597) signature to:

```kotlin
@Composable
internal fun PlayerOperationsBar(
    isFav: Boolean,
    hasCurrentItem: Boolean,
    currentQuality: PlayQuality,
    isDownloaded: Boolean,
    currentSpeed: Float,
    onToggleFav: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleLyrics: () -> Unit,
    onQualityClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onSpeedClick: () -> Unit,
)
```

In the body of `PlayerOperationsBar`:

Replace the static quality `Text("标准")` block:

```kotlin
        Text(
            text = qualityLabel(currentQuality),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = FontSizes.description,
            modifier = Modifier
                .clickable(enabled = hasCurrentItem) { onQualityClick() }
                .padding(horizontal = rpx(16), vertical = rpx(8)),
        )
```

Replace the empty `IconButton(onClick = {})` (download) block:

```kotlin
        IconButton(
            onClick = onDownloadClick,
            enabled = hasCurrentItem,
        ) {
            Icon(
                painter = painterResource(
                    if (isDownloaded) R.drawable.ic_check_circle else R.drawable.ic_arrow_down_tray,
                ),
                contentDescription = if (isDownloaded) "已下载" else "下载",
                tint = Color.White.copy(alpha = if (hasCurrentItem) 0.7f else 0.3f),
                modifier = Modifier.size(IconSizes.normal),
            )
        }
```

(If `R.drawable.ic_check_circle` is missing, use `R.drawable.ic_check` or fall back to a material icon.)

Replace the static `Text("1.0x")` block:

```kotlin
        Text(
            text = formatSpeedLabel(currentSpeed),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = FontSizes.description,
            modifier = Modifier
                .clickable(enabled = hasCurrentItem) { onSpeedClick() }
                .padding(horizontal = rpx(16), vertical = rpx(8)),
        )
```

Add these private helpers at the bottom of the file (after `formatLyricOffset`):

```kotlin
private fun qualityLabel(quality: PlayQuality): String = when (quality) {
    PlayQuality.LOW -> "低音质"
    PlayQuality.STANDARD -> "标准"
    PlayQuality.HIGH -> "高音质"
    PlayQuality.SUPER -> "超高"
}

private fun formatSpeedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}.0x" else "${speed}x"
```

Add the import at the top:

```kotlin
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.feature.playerui.component.quality.MusicQualitySheet
import com.hank.musicfree.feature.playerui.component.quality.MusicQualitySheetMode
import com.hank.musicfree.feature.playerui.component.rate.PlayRateSheet
```

- [ ] **Step 2: Wire sheet hosts in `PlayerScreen` body**

In `PlayerScreen` composable, add these state holders near the other `var ... by remember` declarations (around lines 91-96):

```kotlin
    var qualitySheetMode by remember { mutableStateOf<MusicQualitySheetMode?>(null) }
    var showRateSheet by remember { mutableStateOf(false) }
```

In the same `PlayerScreen` body, collect the new VM state:

```kotlin
    val currentQuality by viewModel.currentQuality.collectAsStateWithLifecycle()
    val currentSpeed by viewModel.currentSpeed.collectAsStateWithLifecycle()
    val isDownloaded by viewModel.isCurrentDownloaded.collectAsStateWithLifecycle()
```

(Place near line 86-90, after the existing `val isFav by ...` line.)

In the `when (contentPage) { PlayerContentPage.Cover -> { PlayerCoverPageContent(...) } }` call (around line 167), update the call to pass the new params:

```kotlin
                PlayerContentPage.Cover -> {
                    PlayerCoverPageContent(
                        artworkUrl = artworkUrl,
                        isFav = isFav,
                        hasCurrentItem = currentItem != null,
                        currentQuality = currentQuality,
                        isDownloaded = isDownloaded,
                        currentSpeed = currentSpeed,
                        onToggleFav = { viewModel.toggleCurrentFavorite() },
                        onAddToPlaylist = { viewModel.showAddToPlaylistSheet() },
                        onToggleLyrics = { contentPage = PlayerContentPage.Lyrics },
                        onQualityClick = { qualitySheetMode = MusicQualitySheetMode.Play },
                        onDownloadClick = {
                            if (!isDownloaded) qualitySheetMode = MusicQualitySheetMode.Download
                        },
                        onSpeedClick = { showRateSheet = true },
                        modifier = Modifier.weight(1f),
                    )
                }
```

At the bottom of the outer `Box(modifier = Modifier.fillMaxSize()) { ... }` (around line 313, after `if (showQueueSheet) { PlayQueueSheet(...) }`), add:

```kotlin
        qualitySheetMode?.let { mode ->
            MusicQualitySheet(
                current = currentQuality,
                mode = mode,
                availableQualities = currentItem?.qualities,
                onDismiss = { qualitySheetMode = null },
                onSelect = { quality ->
                    when (mode) {
                        MusicQualitySheetMode.Play -> {
                            viewModel.setCurrentQuality(quality)
                            Toast.makeText(context, "切换到${quality.name.lowercase()}音质", Toast.LENGTH_SHORT).show()
                        }
                        MusicQualitySheetMode.Download -> {
                            viewModel.downloadCurrent(quality)
                            Toast.makeText(context, "已加入下载队列", Toast.LENGTH_SHORT).show()
                        }
                    }
                    qualitySheetMode = null
                },
            )
        }

        if (showRateSheet) {
            PlayRateSheet(
                current = currentSpeed,
                onDismiss = { showRateSheet = false },
                onSelect = { rate ->
                    viewModel.setPlaybackSpeed(rate)
                    showRateSheet = false
                },
            )
        }
```

- [ ] **Step 3: Verify resource availability and adjust if needed**

```bash
find core/src/main/res -name "ic_check_circle*" -o -name "ic_check.*" | head
```

If `ic_check_circle` missing, replace `R.drawable.ic_check_circle` with `R.drawable.ic_check` or any existing checkmark icon. Note the substitution in the commit message.

- [ ] **Step 4: Build the module + assemble debug**

```bash
./gradlew :feature:player-ui:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run feature/player-ui tests (existing screen tests still need to pass)**

```bash
./gradlew :feature:player-ui:testDebugUnitTest --no-daemon
```

Existing `PlayerCoverLayoutTest`, `PlayerControlsTest`, `PlayerScreenInsetsTest` may instantiate `PlayerOperationsBar` directly. Update those to pass the new required parameters. The implementer should grep:

```bash
grep -rn "PlayerOperationsBar(" feature/player-ui/src/test/
```

For every test call site, add the new parameters (use `PlayQuality.STANDARD`, `false`, `1.0f`, `{}` defaults).

Similarly for `PlayerCoverPageContent(...)` test call sites.

- [ ] **Step 6: Commit**

```bash
git add feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt \
        feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/
git commit -m "$(cat <<'EOF'
feat(player-ui): wire quality/download/speed actions on PlayerScreen

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Final integration + verification

**Files:** none (verification only)

### Steps

- [ ] **Step 1: Build entire app debug**

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`. If FAIL, fix the compile error and re-run.

- [ ] **Step 2: Run all unit tests in touched modules**

```bash
./gradlew :core:testDebugUnitTest :data:testDebugUnitTest :player:testDebugUnitTest :feature:player-ui:testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run lint to catch unused imports / smell**

```bash
./gradlew :feature:player-ui:lint --no-daemon
```

Expected: No new lint errors above baseline.

- [ ] **Step 4: Verify no regressions across the broader test surface**

```bash
./gradlew :feature:home:testDebugUnitTest :feature:search:testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL` (these depend transitively on `:feature:player-ui` indirectly through navigation).

- [ ] **Step 5: Document follow-ups (no manual action needed unless gaps found)**

Append to spec under `## 验收` only if you discover gaps. Otherwise no changes.

- [ ] **Step 6: No-op final commit (skip if no changes)**

If steps revealed nothing to change, proceed directly to handoff. Otherwise `git commit` the fix.

---

## Out of Scope

- Quality/speed icon assets (text-only sufficient for parity)
- Download list page (`pages/downloading` — separate plan)
- Playing the local downloaded copy
- Multi-track download from this page
- Comments button (already inert in current screen; keep as-is)

## Risks Table

| Risk | Mitigation |
| --- | --- |
| Quality resolver fails for some plugins | toast emits `当前歌曲不支持该音质`, queue position preserved |
| Media3 setPlaybackParameters throws on unsupported codec | wrap in `withConnectedController` try/catch in next iteration if it surfaces |
| Existing `PlayerOperationsBar` tests break due to new params | Step 5 of Task 5 explicitly handles them |
| Compose rule deprecation warnings | Acceptable; existing tests already use the deprecated `createComposeRule` |

package com.hank.musicfree.bootstrap

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlaybackRuntimeSettings
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.repository.PlayQueueRepository
import com.hank.musicfree.player.controller.PlayerController
import com.hank.musicfree.player.model.PlayerState
import com.hank.musicfree.player.queue.PlayQueueSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackStartupCoordinatorTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saves position when isPlaying transitions to false`() {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            val testItem = item("1")
            val queueRepo = mock<PlayQueueRepository> {
                onBlocking { getQueue() } doReturn listOf(testItem)
            }
            val pauseFlushed = CompletableDeferred<Unit>()
            val prefs = mock<AppPreferences> {
                on { currentMusicIndex } doReturn flowOf(0)
                on { currentMusicPositionMs } doReturn flowOf(0L)
                on { currentMusicDurationMs } doReturn flowOf(0L)
                onBlocking { setCurrentMusicPositionMs(12_345L) } doAnswer {
                    pauseFlushed.complete(Unit)
                    Unit
                }
            }
            val runtime = mock<PlaybackRuntimeSettings> {
                onBlocking { autoPlayWhenAppStart() } doReturn false
            }
            val playerStateFlow = MutableStateFlow(
                PlayerState.EMPTY.copy(
                    currentItem = testItem,
                    isPlaying = true,
                    position = 5_000L,
                    duration = 60_000L,
                ),
            )
            val queueStateFlow = MutableStateFlow(PlayQueueSnapshot.EMPTY)
            val controller = mock<PlayerController> {
                on { playerState } doReturn playerStateFlow
                on { queueState } doReturn queueStateFlow
                on { restoreQueue(any(), any(), any(), any(), any()) } doReturn Unit
            }

            PlaybackStartupCoordinator(
                playerController = controller,
                playQueueRepository = queueRepo,
                appPreferences = prefs,
                playbackRuntimeSettings = runtime,
                applicationScope = scope,
            ).start()

            // Allow the launches to mount and subscribe to playerState before we mutate it.
            runBlocking { kotlinx.coroutines.delay(100L) }

            // Simulate pause: isPlaying true → false at 12_345ms
            playerStateFlow.value = playerStateFlow.value.copy(isPlaying = false, position = 12_345L)

            // The load-bearing wait: the doAnswer above completes pauseFlushed when
            // setCurrentMusicPositionMs(12_345L) is invoked by the IO collector.
            runBlocking { withTimeout(2_000L) { pauseFlushed.await() } }

            runBlocking {
                verify(prefs).setCurrentMusicPositionMs(12_345L)
                verify(prefs, org.mockito.kotlin.atLeastOnce()).setCurrentMusicDurationMs(60_000L)
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `start passes saved position and duration to restoreQueue`() {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            val testItem = item("1")
            val queueRepo = mock<PlayQueueRepository> {
                onBlocking { getQueue() } doReturn listOf(testItem)
            }
            val prefs = mock<AppPreferences> {
                on { currentMusicIndex } doReturn flowOf(0)
                on { currentMusicPositionMs } doReturn flowOf(42_000L)
                on { currentMusicDurationMs } doReturn flowOf(180_000L)
            }
            val runtime = mock<PlaybackRuntimeSettings> {
                onBlocking { autoPlayWhenAppStart() } doReturn false
            }
            val restoreCalled = CompletableDeferred<Unit>()
            val controller = mock<PlayerController> {
                on { playerState } doReturn MutableStateFlow(PlayerState.EMPTY)
                on { queueState } doReturn MutableStateFlow(PlayQueueSnapshot.EMPTY)
                on { restoreQueue(any(), any(), any(), any(), any()) } doAnswer {
                    restoreCalled.complete(Unit)
                    Unit
                }
            }

            PlaybackStartupCoordinator(
                playerController = controller,
                playQueueRepository = queueRepo,
                appPreferences = prefs,
                playbackRuntimeSettings = runtime,
                applicationScope = scope,
            ).start()

            runBlocking { withTimeout(5_000L) { restoreCalled.await() } }

            verify(controller).restoreQueue(
                items = eq(listOf(testItem)),
                startIndex = eq(0),
                savedPositionMs = eq(42_000L),
                savedDurationMs = eq(180_000L),
                playWhenRestored = eq(false),
            )
        } finally {
            scope.cancel()
        }
    }

    private fun item(id: String) = MusicItem(
        id = id,
        platform = "test",
        title = "Song $id",
        artist = "Artist",
        album = null,
        duration = 1_000L,
        url = "https://example.test/$id.mp3",
        artwork = null,
        qualities = null,
    )
}

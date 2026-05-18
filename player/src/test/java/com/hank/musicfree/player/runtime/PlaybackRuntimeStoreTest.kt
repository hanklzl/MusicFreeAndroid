package com.hank.musicfree.player.runtime

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.PlaybackRuntimeSettings
import com.hank.musicfree.core.model.QualityFallbackOrder
import com.hank.musicfree.core.runtime.RuntimeRestoreResult
import com.hank.musicfree.core.runtime.RuntimeStoreKey
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.repository.PlayQueueRepository
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import com.hank.musicfree.player.controller.PlayerController
import com.hank.musicfree.player.model.PlayerState
import com.hank.musicfree.player.queue.PlayQueueSnapshot
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

private const val STORE_NAME = "playback"
private const val KEY = "playback:current"

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackRuntimeStoreTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        MfLog.resetForTest()
        Dispatchers.resetMain()
    }

    @Test
    fun restoreQueueSnapshotReturnsSkippedWhenQueueIsEmpty() = runTest {
        val controller = FakePlayerController()
        val store = PlaybackRuntimeStore(
            playQueueRepository = FakePlayQueueRepository(emptyList()).repository,
            appPreferences = FakeAppPreferences(index = 0, positionMs = 0L, durationMs = 0L).preferences,
            playerController = controller.controller,
            playbackRuntimeSettings = FakePlaybackRuntimeSettings(autoPlay = false),
        )

        val result = store.restore()

        assertEquals(RuntimeRestoreResult.Skipped("empty_queue"), result)
        assertEquals(KEY, RuntimeStoreKey.singleton(store.storeName).value)
        assertEquals(STORE_NAME, store.storeName)
        assertEquals(0, store.state.value.queueSize)
        assertFalse(store.state.value.restored)
        assertEquals(0, controller.restoreQueueCalls.get())
    }

    @Test
    fun restoreQueueSnapshotRestoresControllerOnMainSafeBoundary() = runTest {
        val item = sampleMusic("song-1", "demo")
        val controller = FakePlayerController()
        val logger = RecordingLogger()
        MfLog.install(logger)

        val store = PlaybackRuntimeStore(
            playQueueRepository = FakePlayQueueRepository(listOf(item)).repository,
            appPreferences = FakeAppPreferences(
                index = 5,
                positionMs = 12_000L,
                durationMs = 30_000L,
            ).preferences,
            playerController = controller.controller,
            playbackRuntimeSettings = FakePlaybackRuntimeSettings(autoPlay = true),
        )

        val result = store.restore()

        assertEquals(RuntimeRestoreResult.Restored, result)
        assertEquals(0, controller.lastStartIndex)
        assertEquals(12_000L, store.state.value.savedPositionMs)
        assertEquals(30_000L, store.state.value.savedDurationMs)
        assertEquals(1, store.state.value.queueSize)
        assertTrue(store.state.value.restored)
        assertTrue(store.state.value.autoPlayWhenRestored)

        assertEquals(1, controller.restoreQueueCalls.get())
        assertEquals(0, controller.lastStartIndex)
        assertEquals(12_000L, controller.lastSavedPositionMs)
        assertEquals(30_000L, controller.lastSavedDurationMs)
        assertTrue(controller.lastAutoPlay)

        val event = logger.events.firstOrNull { it.event == "playback_runtime_restore_success" }
        assertNotNull(event)
        assertEquals(LogCategory.PLAYER, event?.category)
        assertEquals("success", event?.fields?.get("result"))
        assertEquals(1, event?.fields?.get("queueSize"))
        assertEquals(0, event?.fields?.get("startIndex"))
        assertEquals(12_000L, event?.fields?.get("savedPositionMs"))
        assertEquals(30_000L, event?.fields?.get("savedDurationMs"))
        assertEquals(true, event?.fields?.get("autoPlay"))
        assertEquals(STORE_NAME, event?.fields?.get("store"))
        assertEquals(KEY, event?.fields?.get("key"))
        assertNotNull(event?.fields?.get("durationMs"))
    }

    @Test
    fun restoreQueueSnapshotFailureUpdatesFailureStateAndLogsError() = runTest {
        val item = sampleMusic("song-1", "demo")
        val controller = FakePlayerController()
        val failure = RuntimeException("boom")
        controller.doRestoreFailure(failure)
        val logger = RecordingLogger()
        MfLog.install(logger)

        val store = PlaybackRuntimeStore(
            playQueueRepository = FakePlayQueueRepository(listOf(item)).repository,
            appPreferences = FakeAppPreferences(
                index = 0,
                positionMs = 0L,
                durationMs = 0L,
            ).preferences,
            playerController = controller.controller,
            playbackRuntimeSettings = FakePlaybackRuntimeSettings(autoPlay = false),
        )

        val result = store.restore()
        val failed = result as? RuntimeRestoreResult.Failed

        assertNotNull(failed)
        assertEquals("exception", failed?.reason)
        assertEquals("boom", failed?.error?.message)
        assertEquals("exception", store.state.value.lastFailureReason)
        assertFalse(store.state.value.restored)

        val event = logger.events.firstOrNull { it.event == "playback_runtime_restore_failed" }
        assertNotNull(event)
        assertEquals(LogCategory.PLAYER, event?.category)
        assertEquals("failure", event?.fields?.get("result"))
        assertEquals("exception", event?.fields?.get("reason"))

        assertEquals(1, controller.restoreQueueCalls.get())
    }

    private class FakePlayerController {
        val restoreQueueCalls = AtomicInteger(0)
        var lastStartIndex = -1
        var lastSavedPositionMs = -1L
        var lastSavedDurationMs = -1L
        var lastAutoPlay = false
        private var restoreFailure: Throwable? = null

        val controller: PlayerController = mock {
            on { playerState } doReturn MutableStateFlow(PlayerState.EMPTY)
            on { queueState } doReturn MutableStateFlow(PlayQueueSnapshot.EMPTY)
            on { restoreQueue(any(), any(), any(), any(), any()) } doAnswer {
                restoreQueueCalls.incrementAndGet()
                restoreFailure?.let { throw it }
                lastStartIndex = it.getArgument(1)
                lastSavedPositionMs = it.getArgument(2)
                lastSavedDurationMs = it.getArgument(3)
                lastAutoPlay = it.getArgument(4)
                Unit
            }
        }

        fun doRestoreFailure(error: Throwable) {
            restoreFailure = error
        }
    }

    private class FakePlaybackRuntimeSettings(
        private val autoPlay: Boolean,
    ) : PlaybackRuntimeSettings {
        override suspend fun defaultPlayQuality() = PlayQuality.STANDARD
        override suspend fun playQualityOrder() = QualityFallbackOrder.Asc
        override suspend fun useCellularPlay() = false
        override suspend fun allowConcurrentPlayback() = false
        override suspend fun autoPlayWhenAppStart() = autoPlay
        override suspend fun tryChangeSourceWhenPlayFail() = false
        override suspend fun autoStopWhenError() = false
        override suspend fun audioInterruptionAction() =
            com.hank.musicfree.core.model.AudioInterruptionAction.Pause

        override suspend fun audioInterruptionDuckVolume() = 0.5f
        override suspend fun showExitOnNotification() = false
    }

    private class FakePlayQueueRepository(queue: List<MusicItem>) {
        val repository: PlayQueueRepository = mock {
            onBlocking { getQueue() } doReturn queue
        }
    }

    private class FakeAppPreferences(
        index: Int,
        positionMs: Long,
        durationMs: Long,
    ) {
        val preferences: AppPreferences = mock {
            on { currentMusicIndex } doReturn flowOf(index)
            on { currentMusicPositionMs } doReturn flowOf(positionMs)
            on { currentMusicDurationMs } doReturn flowOf(durationMs)
        }
    }

    private fun sampleMusic(id: String, platform: String) = MusicItem(
        id = id,
        platform = platform,
        title = "Song $id",
        artist = "Artist",
        album = null,
        duration = 1_000L,
        url = "https://example.test/$id.mp3",
        artwork = null,
        qualities = null,
    )

    private data class RecordedLogEvent(
        val category: LogCategory,
        val event: String,
        val fields: Map<String, Any?>,
    )

    private class RecordingLogger : MfLogger {
        val events = CopyOnWriteArrayList<RecordedLogEvent>()

        override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedLogEvent(category, event, fields)
        }

        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedLogEvent(category, event, fields)
        }

        override fun error(
            category: LogCategory,
            event: String,
            throwable: Throwable?,
            fields: Map<String, Any?>,
        ) {
            events += RecordedLogEvent(category, event, fields)
        }

        override fun flush() = Unit
    }
}

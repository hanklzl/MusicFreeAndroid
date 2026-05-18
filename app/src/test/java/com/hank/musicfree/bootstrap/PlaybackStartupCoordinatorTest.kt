package com.hank.musicfree.bootstrap

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.repository.PlayQueueRepository
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import com.hank.musicfree.player.controller.PlayerController
import com.hank.musicfree.player.model.PlayerState
import com.hank.musicfree.player.queue.PlayQueueSnapshot
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.After
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.never
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class PlaybackStartupCoordinatorTest {
    @After
    fun tearDown() {
        MfLog.resetForTest()
    }

    @Test
    fun savesPositionWhenPlaybackPausesOrStops() {
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default + Job())
            try {
                val testItem = item("1")
                val queueRepo = mock<PlayQueueRepository> {
                    onBlocking { saveQueue(any()) } doAnswer { Unit }
                }
                val prefs = mock<AppPreferences> {
                    on { currentMusicIndex } doReturn flowOf(0)
                    on { currentMusicPositionMs } doReturn flowOf(0L)
                    on { currentMusicDurationMs } doReturn flowOf(0L)
                    onBlocking { setCurrentMusicPositionMs(12_345L) } doAnswer { Unit }
                    onBlocking { setCurrentMusicDurationMs(60_000L) } doAnswer { Unit }
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
                }

                PlaybackStartupCoordinator(
                    playerController = controller,
                    playQueueRepository = queueRepo,
                    appPreferences = prefs,
                    applicationScope = scope,
                ).start()

                withTimeout(2_000L) { playerStateFlow.subscriptionCount.first { it > 0 } }
                playerStateFlow.value = playerStateFlow.value.copy(isPlaying = false, position = 12_345L)

                verify(prefs, Mockito.timeout(2_000)).setCurrentMusicPositionMs(12_345L)
                verify(prefs, Mockito.timeout(2_000)).setCurrentMusicDurationMs(60_000L)
            } finally {
                scope.cancel()
            }
        }
    }

    @Test
    fun startPersistsQueueChangesAndDoesNotRestoreQueue() {
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default + Job())
            try {
                val testItem = item("1")
                val secondItem = item("2")
                val queueRepo = mock<PlayQueueRepository> {
                    onBlocking { saveQueue(any()) } doAnswer { Unit }
                }

                val prefs = mock<AppPreferences> {
                    on { currentMusicIndex } doReturn flowOf(0)
                    on { currentMusicPositionMs } doReturn flowOf(0L)
                    on { currentMusicDurationMs } doReturn flowOf(0L)
                    onBlocking { setCurrentMusicPositionMs(any()) } doAnswer { Unit }
                    onBlocking { setCurrentMusicDurationMs(any()) } doAnswer { Unit }
                    onBlocking { setCurrentMusicIndex(any<Int>()) } doAnswer { Unit }
                }

                val queueStateFlow = MutableStateFlow(PlayQueueSnapshot.EMPTY)
                val controller = mock<PlayerController> {
                    on { playerState } doReturn MutableStateFlow(PlayerState.EMPTY)
                    on { queueState } doReturn queueStateFlow
                }

                PlaybackStartupCoordinator(
                    playerController = controller,
                    playQueueRepository = queueRepo,
                    appPreferences = prefs,
                    applicationScope = scope,
                ).start()

                withTimeout(2_000L) { queueStateFlow.subscriptionCount.first { it > 0 } }
                queueStateFlow.value = PlayQueueSnapshot(listOf(testItem, secondItem), 1)

                verify(queueRepo, Mockito.timeout(2_000).atLeastOnce()).saveQueue(any())
                verify(prefs, Mockito.timeout(2_000).atLeastOnce()).setCurrentMusicIndex(1)
                verify(queueRepo).saveQueue(listOf(testItem, secondItem))
                verify(prefs).setCurrentMusicIndex(1)

                verify(controller, never()).restoreQueue(any(), any(), any(), any(), any())
            } finally {
                scope.cancel()
            }
        }
    }

    @Test
    fun queuePersistFailureLogsStructuredError() {
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default + Job())
            val logger = RecordingLogger()
            MfLog.install(logger)
            try {
                val testItem = item("1")
                val failure = RuntimeException("index failed")
                val queueRepo = mock<PlayQueueRepository> {
                    onBlocking { saveQueue(any()) } doAnswer { Unit }
                }
                val prefs = mock<AppPreferences> {
                    on { currentMusicIndex } doReturn flowOf(0)
                    on { currentMusicPositionMs } doReturn flowOf(0L)
                    on { currentMusicDurationMs } doReturn flowOf(0L)
                    onBlocking { setCurrentMusicPositionMs(any()) } doAnswer { Unit }
                    onBlocking { setCurrentMusicDurationMs(any()) } doAnswer { Unit }
                    onBlocking { setCurrentMusicIndex(any<Int>()) } doAnswer { throw failure }
                }
                val queueStateFlow = MutableStateFlow(PlayQueueSnapshot.EMPTY)
                val controller = mock<PlayerController> {
                    on { playerState } doReturn MutableStateFlow(PlayerState.EMPTY)
                    on { queueState } doReturn queueStateFlow
                }

                PlaybackStartupCoordinator(
                    playerController = controller,
                    playQueueRepository = queueRepo,
                    appPreferences = prefs,
                    applicationScope = scope,
                ).start()

                withTimeout(2_000L) { queueStateFlow.subscriptionCount.first { it > 0 } }
                queueStateFlow.value = PlayQueueSnapshot(listOf(testItem), 0)

                withTimeout(2_000L) {
                    while (logger.events.none { it.event == "playback_queue_persist_failed" }) {
                        delay(10L)
                    }
                }

                val event = logger.events.single { it.event == "playback_queue_persist_failed" }
                assertEquals(LogCategory.PLAYER, event.category)
                assertEquals("failure", event.fields["result"])
                assertEquals("exception", event.fields["reason"])
                assertEquals(1, event.fields["queueSize"])
                assertEquals(0, event.fields["currentIndex"])
                assertEquals(failure, event.throwable)
                assertNotNull(event.fields["durationMs"])
            } finally {
                scope.cancel()
            }
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

    private data class RecordedLogEvent(
        val category: LogCategory,
        val event: String,
        val fields: Map<String, Any?>,
        val throwable: Throwable? = null,
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
            events += RecordedLogEvent(category, event, fields, throwable)
        }

        override fun flush() = Unit
    }
}

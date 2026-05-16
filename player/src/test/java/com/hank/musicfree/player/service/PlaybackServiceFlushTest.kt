package com.hank.musicfree.player.service

import com.hank.musicfree.data.datastore.AppPreferences
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.never

class PlaybackServiceFlushTest {

    @Test
    fun `flushLastPositionTo persists position and duration when non-zero`() = runTest {
        val prefs = mock<AppPreferences>()
        runBlocking {
            PlaybackService.flushLastPositionTo(
                prefs = prefs,
                positionMs = 12_345L,
                durationMs = 60_000L,
            )
        }
        verify(prefs).setCurrentMusicPositionMs(12_345L)
        verify(prefs).setCurrentMusicDurationMs(60_000L)
    }

    @Test
    fun `flushLastPositionTo is no-op when both values are zero`() = runTest {
        val prefs = mock<AppPreferences>()
        runBlocking {
            PlaybackService.flushLastPositionTo(
                prefs = prefs,
                positionMs = 0L,
                durationMs = 0L,
            )
        }
        verify(prefs, never()).setCurrentMusicPositionMs(0L)
        verify(prefs, never()).setCurrentMusicDurationMs(0L)
    }
}

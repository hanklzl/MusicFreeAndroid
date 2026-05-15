package com.zili.android.musicfreeandroid.player.listening

import androidx.media3.common.Player
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.data.db.dao.ListenStatsDao
import com.zili.android.musicfreeandroid.data.db.entity.ListenEventArtistEntity
import com.zili.android.musicfreeandroid.data.db.entity.ListenEventEntity
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ListenTrackerTest {

    private val dao: ListenStatsDao = mock()
    private val clock = longArrayOf(0L)
    private fun setNow(v: Long) { clock[0] = v }

    private fun newTracker(scope: TestScope): ListenTracker = ListenTracker(
        dao = dao,
        nowMs = { clock[0] },
        scope = scope,
    )

    private val item = MusicItem(
        id = "m1", platform = "netease", title = "Song",
        artist = "周杰伦 & 林俊杰", album = "Album", artwork = null,
        duration = 240_000L, url = null, qualities = null,
        raw = mapOf("language" to "国语", "genre" to "流行"),
    )

    @Test fun playing_for_60s_then_transition_writesOneEvent() = runTest {
        val tracker = newTracker(this)
        setNow(0); tracker.onMediaItemTransition(item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        setNow(0); tracker.onIsPlayingChanged(true, item)
        setNow(60_000); tracker.onIsPlayingChanged(false, item)
        setNow(60_000); tracker.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        advanceUntilIdle()

        argumentCaptor<ListenEventEntity>().apply {
            verify(dao).insertEventWithArtists(capture(), any())
            assertEquals(60, firstValue.playedSeconds)
            assertEquals("zh-CN", firstValue.language)
            assertEquals("pop", firstValue.genre)
        }
        argumentCaptor<List<ListenEventArtistEntity>>().apply {
            verify(dao).insertEventWithArtists(any(), capture())
            assertEquals(listOf("周杰伦", "林俊杰"), firstValue.map { it.artistName })
        }
    }

    @Test fun played_below_threshold_doesNotWrite() = runTest {
        val tracker = newTracker(this)
        setNow(0); tracker.onMediaItemTransition(item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        setNow(0); tracker.onIsPlayingChanged(true, item)
        setNow(15_000); tracker.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        advanceUntilIdle()
        verify(dao, never()).insertEventWithArtists(any(), any())
    }

    @Test fun seek_doesNotAccumulateExtraTime() = runTest {
        val tracker = newTracker(this)
        setNow(0); tracker.onMediaItemTransition(item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        setNow(0); tracker.onIsPlayingChanged(true, item)
        setNow(30_000); tracker.onPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK)
        // seek 后从 30_000 重起 playing 段
        setNow(60_000); tracker.onTrackEnded(item)
        advanceUntilIdle()

        argumentCaptor<ListenEventEntity>().apply {
            verify(dao).insertEventWithArtists(capture(), any())
            assertEquals(60, firstValue.playedSeconds)  // 30 + 30
            assertTrue(firstValue.completed)
        }
    }

    @Test fun trackEnded_marksCompletedTrue() = runTest {
        val tracker = newTracker(this)
        setNow(0); tracker.onMediaItemTransition(item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        setNow(0); tracker.onIsPlayingChanged(true, item)
        setNow(60_000); tracker.onTrackEnded(item)
        advanceUntilIdle()
        argumentCaptor<ListenEventEntity>().apply {
            verify(dao).insertEventWithArtists(capture(), any())
            assertTrue(firstValue.completed)
        }
    }
}

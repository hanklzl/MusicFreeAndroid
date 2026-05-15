package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.entity.ListenEventArtistEntity
import com.zili.android.musicfreeandroid.data.db.entity.ListenEventEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ListenStatsDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ListenStatsDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.listenStatsDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed(
        playedAtMs: Long,
        musicId: String,
        platform: String = "netease",
        title: String = "T",
        artists: List<String> = listOf("A"),
        playedSeconds: Int = 60,
        completed: Boolean = true,
        language: String? = "zh-CN",
        genre: String? = "pop",
        durationMs: Long = 240_000,
    ) {
        dao.insertEventWithArtists(
            event = ListenEventEntity(
                playedAtMs = playedAtMs, musicId = musicId, platform = platform,
                title = title, artistRaw = artists.joinToString(" & "),
                album = null, artwork = null, durationMs = durationMs,
                playedSeconds = playedSeconds, completed = completed,
                language = language, genre = genre,
            ),
            artists = artists.mapIndexed { i, n ->
                ListenEventArtistEntity(eventId = 0, artistName = n, artistOrder = i)
            },
        )
    }

    @Test
    fun totalSeconds_returnsSumWithinWindow_excludesOutside() = runTest {
        seed(playedAtMs = 1_000, musicId = "m1", playedSeconds = 30)
        seed(playedAtMs = 2_000, musicId = "m2", playedSeconds = 60)
        seed(playedAtMs = 99_999, musicId = "m3", playedSeconds = 999) // out

        val total = dao.totalSecondsFlow(startMs = 0, endMs = 50_000).first()
        assertEquals(90L, total)
    }

    @Test
    fun distinctSongs_artists_GroupCorrectly() = runTest {
        seed(playedAtMs = 1, musicId = "m1", artists = listOf("周杰伦", "林俊杰"))
        seed(playedAtMs = 2, musicId = "m1", artists = listOf("周杰伦", "林俊杰"))   // 同歌
        seed(playedAtMs = 3, musicId = "m2", artists = listOf("周杰伦"))

        assertEquals(2, dao.distinctSongsFlow(0, 100).first())
        assertEquals(2, dao.distinctArtistsFlow(0, 100).first())
    }

    @Test
    fun topSongs_ordersByPlayCountDescTotalSecTiebreak() = runTest {
        // m1: 2 次 × 30s = 60s total; m2: 2 次 × 60s = 120s total; m3: 1 次 100s
        // ORDER BY playCount DESC, totalSec DESC → m2 (2次/120s) > m1 (2次/60s) > m3 (1次/100s)
        seed(1, "m1", playedSeconds = 30)
        seed(2, "m1", playedSeconds = 30)
        seed(3, "m2", playedSeconds = 60)
        seed(4, "m2", playedSeconds = 60)
        seed(5, "m3", playedSeconds = 100)  // 1 次 100 秒

        val top = dao.topSongsFlow(0, 100, limit = 5).first()
        assertEquals(listOf("m2", "m1", "m3"), top.map { it.musicId })
        assertEquals(2, top[0].playCount); assertEquals(120L, top[0].totalSec)
    }

    @Test
    fun topArtists_countsCoFeaturesIndependently() = runTest {
        seed(1, "m1", artists = listOf("A", "B"))
        seed(2, "m2", artists = listOf("A"))
        seed(3, "m3", artists = listOf("B"))

        val top = dao.topArtistsFlow(0, 100, limit = 5).first()
        val byName = top.associateBy { it.artistName }
        assertEquals(2, byName.getValue("A").playCount)
        assertEquals(2, byName.getValue("B").playCount)
    }

    @Test
    fun firstSeen_onlyIncludesMusicFirstAppearingInWindow() = runTest {
        seed(1_000, "old")        // 老歌
        seed(5_000_000, "old")    // 后来又听
        seed(5_001_000, "newDiscovery")

        val firstSeen = dao.firstSeenInWindowFlow(startMs = 4_000_000, endMs = 6_000_000).first()
        assertEquals(listOf("newDiscovery"), firstSeen.map { it.musicId })
    }

    @Test
    fun clearAllEvents_alsoCascadesArtistRows() = runTest {
        seed(1, "m1", artists = listOf("X", "Y"))
        assertEquals(1, dao.clearAllEvents())
        assertEquals(0, dao.distinctArtistsFlow(0, 100).first())
    }
}

package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zili.android.musicfreeandroid.core.util.splitArtists
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
        title: String = musicId,
        artists: List<String> = listOf("A"),
        playedSeconds: Int = 60,
        completed: Boolean = true,
        language: String? = "zh-CN",
        genre: String? = "pop",
        durationMs: Long = 240_000,
    ) {
        val artistRaw = artists.joinToString(" & ")
        val primary = splitArtists(artistRaw).firstOrNull().orEmpty()
        val mergeKey = "${title.trim().lowercase()}|${primary.trim().lowercase()}"
        dao.insertEventWithArtists(
            event = ListenEventEntity(
                playedAtMs = playedAtMs, musicId = musicId, platform = platform,
                title = title, artistRaw = artistRaw,
                album = null, artwork = null, durationMs = durationMs,
                playedSeconds = playedSeconds, completed = completed,
                language = language, genre = genre,
                mergeKey = mergeKey,
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

    @Test
    fun crossPlugin_sameSong_mergedIntoOneRow() = runTest {
        seed(playedAtMs = 1, musicId = "A", platform = "qq",
             title = "情人知己", artists = listOf("叶蒨文"))
        seed(playedAtMs = 2, musicId = "B", platform = "netease",
             title = "情人知己", artists = listOf("叶蒨文", "张学友"))
        seed(playedAtMs = 3, musicId = "C", platform = "qq",
             title = "情人知己", artists = listOf("张学友"))

        assertEquals(2, dao.distinctSongsFlow(0, 100).first())

        val tops = dao.topSongsFlow(0, 100, limit = 10).first()
        assertEquals(2, tops.size)
        val merged = tops.first { it.title == "情人知己" && it.playCount == 2 }
        // MAX(musicId) 字典序最大:"A" vs "B" → "B"
        assertEquals("B", merged.musicId)
        assertEquals(120L, merged.totalSec)
    }

    @Test
    fun crossPlugin_MAX_artwork_picksNonNullUrl() = runTest {
        dao.insertEventWithArtists(
            ListenEventEntity(
                playedAtMs = 1, musicId = "A", platform = "qq", title = "T",
                artistRaw = "X", album = null, artwork = null,
                durationMs = 240_000, playedSeconds = 60, completed = true,
                language = null, genre = null,
                mergeKey = "t|x",
            ),
            listOf(ListenEventArtistEntity(eventId = 0, artistName = "X", artistOrder = 0)),
        )
        dao.insertEventWithArtists(
            ListenEventEntity(
                playedAtMs = 2, musicId = "B", platform = "netease", title = "T",
                artistRaw = "X", album = null, artwork = "https://x/cover.jpg",
                durationMs = 240_000, playedSeconds = 60, completed = true,
                language = null, genre = null,
                mergeKey = "t|x",
            ),
            listOf(ListenEventArtistEntity(eventId = 0, artistName = "X", artistOrder = 0)),
        )
        val tops = dao.topSongsFlow(0, 100, limit = 10).first()
        assertEquals(1, tops.size)
        assertEquals("https://x/cover.jpg", tops[0].artwork)
    }

    @Test
    fun dailyBuckets_withZoneOffsetMs_returnsLocalDayBucket() = runTest {
        // 本地 2026-05-11 02:00 (Asia/Shanghai UTC+8) → UTC 2026-05-10 18:00
        val localDate = java.time.LocalDate.of(2026, 5, 11)
        val localMs = localDate.atTime(2, 0)
            .atZone(java.time.ZoneId.of("Asia/Shanghai"))
            .toInstant().toEpochMilli()
        seed(playedAtMs = localMs, musicId = "m", playedSeconds = 60)
        val startMs = localDate.withDayOfMonth(1).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai"))
            .toInstant().toEpochMilli()
        val endMs = localDate.withDayOfMonth(1).plusMonths(1).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai"))
            .toInstant().toEpochMilli()

        val buckets = dao.dailyBucketsFlow(startMs, endMs, zoneOffsetMs = 8L * 3600 * 1000).first()
        assertEquals(1, buckets.size)
        assertEquals(localDate.toEpochDay(), buckets[0].dayEpochDay)
    }

    @Test
    fun hourBuckets_withZoneOffsetMs_returnsLocalHour() = runTest {
        val localMs = java.time.LocalDateTime.of(2026, 5, 11, 7, 0)
            .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        seed(playedAtMs = localMs, musicId = "m", playedSeconds = 60)

        val buckets = dao.hourBucketsFlow(0, Long.MAX_VALUE, zoneOffsetMs = 8L * 3600 * 1000).first()
        assertEquals(1, buckets.size)
        assertEquals(7, buckets[0].hourOfDay)
    }

    @Test
    fun zone_UTC_regression_zoneOffsetZero_isLegacyBehavior() = runTest {
        val ms = java.time.LocalDateTime.of(2026, 5, 11, 14, 0)
            .atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        seed(playedAtMs = ms, musicId = "m", playedSeconds = 60)

        val hourBuckets = dao.hourBucketsFlow(0, Long.MAX_VALUE, zoneOffsetMs = 0L).first()
        assertEquals(14, hourBuckets[0].hourOfDay)
    }
}

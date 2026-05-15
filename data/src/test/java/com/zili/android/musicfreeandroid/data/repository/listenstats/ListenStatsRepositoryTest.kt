package com.zili.android.musicfreeandroid.data.repository.listenstats

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.entity.ListenEventArtistEntity
import com.zili.android.musicfreeandroid.data.db.entity.ListenEventEntity
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.DetailFilter
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.DetailMode
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.TimeScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ListenStatsRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ListenStatsRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = ListenStatsRepository(db.listenStatsDao(), zoneIdProvider = { ZoneOffset.UTC })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed(
        date: LocalDate,
        musicId: String,
        secs: Int = 60,
        lang: String? = "zh-CN",
        genre: String? = "pop",
    ) {
        val ms = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 10_000
        db.listenStatsDao().insertEventWithArtists(
            ListenEventEntity(
                playedAtMs = ms, musicId = musicId, platform = "p", title = "T",
                artistRaw = "A", album = null, artwork = null, durationMs = 240_000,
                playedSeconds = secs, completed = true, language = lang, genre = genre,
            ),
            listOf(ListenEventArtistEntity(eventId = 0, artistName = "A", artistOrder = 0)),
        )
    }

    @Test
    fun snapshot_week_aggregatesNaturalMondayThroughSunday() = runTest {
        // anchor=2026-05-13(Wed)；自然周一=2026-05-11，周日=2026-05-17
        seed(LocalDate.of(2026, 5, 11), "m1")
        seed(LocalDate.of(2026, 5, 14), "m2")
        seed(LocalDate.of(2026, 5, 18), "out") // 下一周

        val snap = repo.statsForWindow(TimeScope.WEEK, LocalDate.of(2026, 5, 13)).first()
        assertEquals(120L, snap.totalSeconds)
        assertEquals(2, snap.distinctSongs)
    }

    @Test
    fun language_distribution_coverage_excludesNullsFromKnownButCountsTotal() = runTest {
        seed(LocalDate.of(2026, 5, 13), "m1", lang = "zh-CN")
        seed(LocalDate.of(2026, 5, 13), "m2", lang = null)
        seed(LocalDate.of(2026, 5, 13), "m3", lang = null)

        val snap = repo.statsForWindow(TimeScope.DAY, LocalDate.of(2026, 5, 13)).first()
        assertEquals(1f / 3f, snap.languageDistribution.coverage)
    }

    @Test
    fun streak_currentAndMax() = runTest {
        listOf(LocalDate.of(2026, 5, 13), LocalDate.of(2026, 5, 14), LocalDate.of(2026, 5, 15))
            .forEach { seed(it, it.toString()) }
        listOf(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3))
            .forEach { seed(it, it.toString()) }

        val snap = repo.statsForWindow(TimeScope.ALL_TIME, LocalDate.of(2026, 5, 15)).first()
        assertEquals(3, snap.streakDays)
        assertEquals(3, snap.maxStreak)
    }

    @Test
    fun detail_byArtist_filtersListByArtistName() = runTest {
        db.listenStatsDao().insertEventWithArtists(
            ListenEventEntity(
                playedAtMs = 1, musicId = "m1", platform = "p", title = "T",
                artistRaw = "X & Y", album = null, artwork = null,
                durationMs = 100_000, playedSeconds = 60, completed = false,
                language = null, genre = null,
            ),
            listOf(
                ListenEventArtistEntity(eventId = 0, artistName = "X", artistOrder = 0),
                ListenEventArtistEntity(eventId = 0, artistName = "Y", artistOrder = 1),
            ),
        )
        val flow = repo.detail(
            DetailFilter(DetailMode.BY_ARTIST, "X"),
            TimeScope.ALL_TIME,
            LocalDate.of(2026, 5, 15),
        ).first()
        assertEquals(listOf("m1"), flow.map { it.musicId })

        val emptyResult = repo.detail(
            DetailFilter(DetailMode.BY_ARTIST, "Unknown"),
            TimeScope.ALL_TIME,
            LocalDate.of(2026, 5, 15),
        ).first()
        assertTrue(emptyResult.isEmpty())
    }

    @Test
    fun clearAll_emptiesEverything() = runTest {
        seed(LocalDate.of(2026, 5, 14), "m")
        assertEquals(1, repo.clearAll())
        val snap = repo.statsForWindow(TimeScope.WEEK, LocalDate.of(2026, 5, 14)).first()
        assertEquals(0L, snap.totalSeconds)
        assertEquals(0, snap.distinctSongs)
    }
}

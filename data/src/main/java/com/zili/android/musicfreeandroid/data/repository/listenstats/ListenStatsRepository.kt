package com.zili.android.musicfreeandroid.data.repository.listenstats

import com.zili.android.musicfreeandroid.data.db.dao.ListenStatsDao
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.DateBucket
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.DailyBucket
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.DetailFilter
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.DetailMode
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.Distribution
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.DistributionBucket
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.HourBucket
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.ListenStatsSnapshot
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.ListenedSong
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.TimeScope
import com.zili.android.musicfreeandroid.data.repository.listenstats.model.windowFor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@Singleton
class ListenStatsRepository @Inject constructor(
    private val dao: ListenStatsDao,
    private val zoneIdProvider: @JvmSuppressWildcards () -> ZoneId = { ZoneId.systemDefault() },
) {

    fun firstEventDate(): Flow<LocalDate?> =
        dao.firstEventTimestamp().map { ms ->
            ms?.let { Instant.ofEpochMilli(it).atZone(zoneIdProvider()).toLocalDate() }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun statsForWindow(scope: TimeScope, anchor: LocalDate): Flow<ListenStatsSnapshot> {
        return firstEventDate().flatMapLatest { firstDate ->
            val window = windowFor(scope, anchor, zoneIdProvider(), firstDate)
            combine(
                dao.totalSecondsFlow(window.startMs, window.endMs),
                dao.distinctSongsFlow(window.startMs, window.endMs),
                dao.distinctArtistsFlow(window.startMs, window.endMs),
                dao.topSongsFlow(window.startMs, window.endMs, limit = 50),
                dao.topArtistsFlow(window.startMs, window.endMs, limit = 50),
                dao.dailyBucketsFlow(window.startMs, window.endMs),
                dao.hourBucketsFlow(window.startMs, window.endMs),
                dao.languageDistributionFlow(window.startMs, window.endMs),
                dao.genreDistributionFlow(window.startMs, window.endMs),
                dao.heatmapFlow(window.startMs, window.endMs),
                dao.firstSeenInWindowFlow(window.startMs, window.endMs),
            ) { fields ->
                @Suppress("UNCHECKED_CAST")
                val total = fields[0] as Long
                @Suppress("UNCHECKED_CAST")
                val songs = fields[1] as Int
                @Suppress("UNCHECKED_CAST")
                val artists = fields[2] as Int
                @Suppress("UNCHECKED_CAST")
                val tops = fields[3] as List<com.zili.android.musicfreeandroid.data.db.dao.TopSongRow>
                @Suppress("UNCHECKED_CAST")
                val topArtists = fields[4] as List<com.zili.android.musicfreeandroid.data.db.dao.TopArtistRow>
                @Suppress("UNCHECKED_CAST")
                val dailyRows = fields[5] as List<com.zili.android.musicfreeandroid.data.db.dao.DailyBucketRow>
                @Suppress("UNCHECKED_CAST")
                val hourRows = fields[6] as List<com.zili.android.musicfreeandroid.data.db.dao.HourBucketRow>
                @Suppress("UNCHECKED_CAST")
                val langRows = fields[7] as List<com.zili.android.musicfreeandroid.data.db.dao.LanguageBucketRow>
                @Suppress("UNCHECKED_CAST")
                val genreRows = fields[8] as List<com.zili.android.musicfreeandroid.data.db.dao.GenreBucketRow>
                @Suppress("UNCHECKED_CAST")
                val heatmapRows = fields[9] as List<com.zili.android.musicfreeandroid.data.db.dao.DateBucketRow>
                @Suppress("UNCHECKED_CAST")
                val firstSeenRows = fields[10] as List<com.zili.android.musicfreeandroid.data.db.dao.ListenedSongRow>

                val totalEvents = langRows.sumOf { it.count }.coerceAtLeast(1)
                val langKnown = langRows.filter { it.language != null }.sumOf { it.count }
                val genreKnown = genreRows.filter { it.genre != null }.sumOf { it.count }

                val streak = computeStreaks(dailyRows.map { it.dayEpochDay }, anchor.toEpochDay())

                ListenStatsSnapshot(
                    totalSeconds = total,
                    distinctSongs = songs,
                    distinctArtists = artists,
                    dailyBuckets = dailyRows.map { DailyBucket(it.dayEpochDay, it.seconds) },
                    topSongs = tops,
                    topArtists = topArtists,
                    hourBuckets = hourRows.map { HourBucket(it.hourOfDay, it.seconds) },
                    languageDistribution = Distribution(
                        buckets = langRows.map {
                            DistributionBucket(
                                key = it.language,
                                count = it.count,
                                label = it.language?.let(::languageLabel) ?: "未知 / 未归类",
                            )
                        },
                        coverage = langKnown.toFloat() / totalEvents,
                    ),
                    genreDistribution = Distribution(
                        buckets = genreRows.map {
                            DistributionBucket(
                                key = it.genre,
                                count = it.count,
                                label = it.genre?.let(::genreLabel) ?: "未知 / 未归类",
                            )
                        },
                        coverage = genreKnown.toFloat() / totalEvents,
                    ),
                    streakDays = streak.current,
                    maxStreak = streak.max,
                    firstSeenCount = firstSeenRows.size,
                    heatmap = heatmapRows.map { DateBucket(it.dayEpochDay, it.seconds) },
                )
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun detail(filter: DetailFilter, scope: TimeScope, anchor: LocalDate): Flow<List<ListenedSong>> {
        val window = windowFor(scope, anchor, zoneIdProvider())
        val rowFlow = when (filter.mode) {
            DetailMode.ALL_SONGS, DetailMode.TOP_SONGS, DetailMode.ALL_ARTISTS, DetailMode.TOP_ARTISTS ->
                dao.allSongsInWindowFlow(window.startMs, window.endMs)
            DetailMode.FIRST_SEEN ->
                dao.firstSeenInWindowFlow(window.startMs, window.endMs)
            DetailMode.BY_ARTIST ->
                dao.songsByArtistFlow(window.startMs, window.endMs, filter.filterValue.orEmpty())
            DetailMode.BY_LANGUAGE ->
                dao.songsByLanguageFlow(window.startMs, window.endMs, filter.filterValue.orEmpty())
            DetailMode.BY_GENRE ->
                dao.songsByGenreFlow(window.startMs, window.endMs, filter.filterValue.orEmpty())
        }
        return rowFlow.map { rows ->
            rows.map {
                ListenedSong(
                    musicId = it.musicId, platform = it.platform,
                    title = it.title, artistRaw = it.artistRaw,
                    album = it.album, artwork = it.artwork,
                    firstSeenMs = it.firstSeenMs, lastSeenMs = it.lastSeenMs,
                    playCount = it.playCount, totalSec = it.totalSec,
                )
            }
        }
    }

    suspend fun clearAll(): Int = dao.clearAllEvents()

    private data class StreakResult(val current: Int, val max: Int)

    private fun computeStreaks(daysWithListen: List<Long>, todayEpochDay: Long): StreakResult {
        if (daysWithListen.isEmpty()) return StreakResult(0, 0)
        val set = daysWithListen.toSortedSet()
        var max = 0; var cur = 0; var prev: Long? = null
        for (d in set) {
            cur = if (prev != null && d == prev + 1) cur + 1 else 1
            if (cur > max) max = cur
            prev = d
        }
        var current = 0; var probe = todayEpochDay
        while (probe in set) { current++; probe-- }
        return StreakResult(current, max)
    }

    private fun languageLabel(code: String): String = when (code) {
        "zh-CN" -> "国语"
        "en" -> "英语"
        "yue" -> "粤语"
        "ja" -> "日语"
        "ko" -> "韩语"
        else -> code
    }

    private fun genreLabel(code: String): String = when (code) {
        "pop" -> "流行"
        "hip-hop" -> "嘻哈 / Hip-Hop"
        "rnb" -> "R&B"
        "rock" -> "摇滚"
        "folk" -> "民谣"
        else -> code
    }
}

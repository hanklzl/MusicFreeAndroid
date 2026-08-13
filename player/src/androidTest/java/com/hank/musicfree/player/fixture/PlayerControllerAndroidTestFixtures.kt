package com.hank.musicfree.player.fixture

import android.content.Context
import com.hank.musicfree.core.telemetry.CurrentSidProvider
import com.hank.musicfree.core.telemetry.PlayCacheTelemetry
import com.hank.musicfree.data.db.dao.DailyBucketRow
import com.hank.musicfree.data.db.dao.DateBucketRow
import com.hank.musicfree.data.db.dao.GenreBucketRow
import com.hank.musicfree.data.db.dao.HourBucketRow
import com.hank.musicfree.data.db.dao.LanguageBucketRow
import com.hank.musicfree.data.db.dao.ListenStatsDao
import com.hank.musicfree.data.db.dao.ListenedSongRow
import com.hank.musicfree.data.db.dao.TopArtistRow
import com.hank.musicfree.data.db.dao.TopSongRow
import com.hank.musicfree.data.db.entity.ListenEventArtistEntity
import com.hank.musicfree.data.db.entity.ListenEventEntity
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.player.controller.PlayerController
import com.hank.musicfree.player.listening.ListenTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal fun createTestPlayerController(context: Context): PlayerController {
    return PlayerController(
        context = context,
        listenTracker = ListenTracker(NoOpListenStatsDao()),
        currentSidProvider = CurrentSidProvider(),
        playCacheTelemetry = PlayCacheTelemetry(MfLog),
    )
}

private class NoOpListenStatsDao : ListenStatsDao {
    override suspend fun preferenceEvents(
        startMs: Long,
        endMs: Long,
        limit: Int,
    ): List<ListenEventEntity> = emptyList()

    override suspend fun insertEvent(event: ListenEventEntity): Long = 1L

    override suspend fun insertArtists(artists: List<ListenEventArtistEntity>) = Unit

    override suspend fun clearAllEvents(): Int = 0

    override fun firstEventTimestamp(): Flow<Long?> = flowOf(null)

    override fun totalSecondsFlow(startMs: Long, endMs: Long): Flow<Long> = flowOf(0L)

    override fun distinctSongsFlow(startMs: Long, endMs: Long): Flow<Int> = flowOf(0)

    override fun distinctArtistsFlow(startMs: Long, endMs: Long): Flow<Int> = flowOf(0)

    override fun topSongsFlow(
        startMs: Long,
        endMs: Long,
        limit: Int,
    ): Flow<List<TopSongRow>> = flowOf(emptyList())

    override fun topArtistsFlow(
        startMs: Long,
        endMs: Long,
        limit: Int,
    ): Flow<List<TopArtistRow>> = flowOf(emptyList())

    override fun dailyBucketsFlow(
        startMs: Long,
        endMs: Long,
        zoneOffsetMs: Long,
    ): Flow<List<DailyBucketRow>> = flowOf(emptyList())

    override fun hourBucketsFlow(
        startMs: Long,
        endMs: Long,
        zoneOffsetMs: Long,
    ): Flow<List<HourBucketRow>> = flowOf(emptyList())

    override fun languageDistributionFlow(
        startMs: Long,
        endMs: Long,
    ): Flow<List<LanguageBucketRow>> = flowOf(emptyList())

    override fun genreDistributionFlow(
        startMs: Long,
        endMs: Long,
    ): Flow<List<GenreBucketRow>> = flowOf(emptyList())

    override fun heatmapFlow(
        startMs: Long,
        endMs: Long,
        zoneOffsetMs: Long,
    ): Flow<List<DateBucketRow>> = flowOf(emptyList())

    override fun firstSeenInWindowFlow(
        startMs: Long,
        endMs: Long,
    ): Flow<List<ListenedSongRow>> = flowOf(emptyList())

    override fun allSongsInWindowFlow(
        startMs: Long,
        endMs: Long,
    ): Flow<List<ListenedSongRow>> = flowOf(emptyList())

    override fun songsByArtistFlow(
        startMs: Long,
        endMs: Long,
        artistName: String,
    ): Flow<List<ListenedSongRow>> = flowOf(emptyList())

    override fun songsByLanguageFlow(
        startMs: Long,
        endMs: Long,
        language: String,
    ): Flow<List<ListenedSongRow>> = flowOf(emptyList())

    override fun songsByGenreFlow(
        startMs: Long,
        endMs: Long,
        genre: String,
    ): Flow<List<ListenedSongRow>> = flowOf(emptyList())
}

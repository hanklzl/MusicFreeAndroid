package com.zili.android.musicfreeandroid.data.repository.listenstats.model

import com.zili.android.musicfreeandroid.data.db.dao.TopArtistRow
import com.zili.android.musicfreeandroid.data.db.dao.TopSongRow

data class DailyBucket(val dayEpochDay: Long, val seconds: Long)
data class HourBucket(val hourOfDay: Int, val seconds: Long)
data class DateBucket(val dayEpochDay: Long, val seconds: Long)

data class DistributionBucket<T>(val key: T, val count: Int, val label: String)
data class Distribution<T>(val buckets: List<DistributionBucket<T>>, val coverage: Float)

data class ListenStatsSnapshot(
    val totalSeconds: Long,
    val distinctSongs: Int,
    val distinctArtists: Int,
    val dailyBuckets: List<DailyBucket>,
    val topSongs: List<TopSongRow>,
    val topArtists: List<TopArtistRow>,
    val hourBuckets: List<HourBucket>,
    val languageDistribution: Distribution<String?>,
    val genreDistribution: Distribution<String?>,
    val streakDays: Int,
    val maxStreak: Int,
    val firstSeenCount: Int,
    val heatmap: List<DateBucket>,
)

fun emptySnapshot(): ListenStatsSnapshot = ListenStatsSnapshot(
    totalSeconds = 0, distinctSongs = 0, distinctArtists = 0,
    dailyBuckets = emptyList(), topSongs = emptyList(), topArtists = emptyList(),
    hourBuckets = emptyList(),
    languageDistribution = Distribution(emptyList(), 0f),
    genreDistribution = Distribution(emptyList(), 0f),
    streakDays = 0, maxStreak = 0, firstSeenCount = 0, heatmap = emptyList(),
)

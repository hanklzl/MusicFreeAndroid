package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.zili.android.musicfreeandroid.data.db.entity.ListenEventArtistEntity
import com.zili.android.musicfreeandroid.data.db.entity.ListenEventEntity
import kotlinx.coroutines.flow.Flow

data class TopSongRow(
    val musicId: String,
    val platform: String,
    val title: String,
    val artistRaw: String,
    val album: String?,
    val artwork: String?,
    val playCount: Int,
    val totalSec: Long,
)

data class TopArtistRow(
    val artistName: String,
    val playCount: Int,
    val songCount: Int,
    val totalSec: Long,
)

data class DailyBucketRow(val dayEpochDay: Long, val seconds: Long)
data class HourBucketRow(val hourOfDay: Int, val seconds: Long)
data class LanguageBucketRow(val language: String?, val count: Int)
data class GenreBucketRow(val genre: String?, val count: Int)
data class DateBucketRow(val dayEpochDay: Long, val seconds: Long)
data class ListenedSongRow(
    val musicId: String,
    val platform: String,
    val title: String,
    val artistRaw: String,
    val album: String?,
    val artwork: String?,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val playCount: Int,
    val totalSec: Long,
)

@Dao
interface ListenStatsDao {

    @Transaction
    suspend fun insertEventWithArtists(
        event: ListenEventEntity,
        artists: List<ListenEventArtistEntity>,
    ) {
        val id = insertEvent(event)
        if (artists.isNotEmpty()) {
            insertArtists(artists.map { it.copy(eventId = id) })
        }
    }

    @Insert suspend fun insertEvent(event: ListenEventEntity): Long
    @Insert suspend fun insertArtists(artists: List<ListenEventArtistEntity>)

    @Query("DELETE FROM listen_event")
    suspend fun clearAllEvents(): Int

    @Query("SELECT MIN(playedAtMs) FROM listen_event")
    fun firstEventTimestamp(): Flow<Long?>

    @Query("""SELECT IFNULL(SUM(playedSeconds), 0) FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs""")
    fun totalSecondsFlow(startMs: Long, endMs: Long): Flow<Long>

    @Query("""SELECT COUNT(DISTINCT musicId || '||' || platform) FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs""")
    fun distinctSongsFlow(startMs: Long, endMs: Long): Flow<Int>

    @Query("""SELECT COUNT(DISTINCT a.artistName) FROM listen_event_artist a
              JOIN listen_event e ON a.eventId = e.id
              WHERE e.playedAtMs >= :startMs AND e.playedAtMs < :endMs""")
    fun distinctArtistsFlow(startMs: Long, endMs: Long): Flow<Int>

    @Query("""SELECT musicId, platform, title, artistRaw, album, artwork,
                     COUNT(*) AS playCount, SUM(playedSeconds) AS totalSec
              FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
              GROUP BY musicId, platform
              ORDER BY playCount DESC, totalSec DESC
              LIMIT :limit""")
    fun topSongsFlow(startMs: Long, endMs: Long, limit: Int): Flow<List<TopSongRow>>

    @Query("""SELECT a.artistName,
                     COUNT(DISTINCT e.id) AS playCount,
                     COUNT(DISTINCT e.musicId || '||' || e.platform) AS songCount,
                     IFNULL(SUM(e.playedSeconds), 0) AS totalSec
              FROM listen_event_artist a JOIN listen_event e ON a.eventId = e.id
              WHERE e.playedAtMs >= :startMs AND e.playedAtMs < :endMs
              GROUP BY a.artistName
              ORDER BY playCount DESC, totalSec DESC
              LIMIT :limit""")
    fun topArtistsFlow(startMs: Long, endMs: Long, limit: Int): Flow<List<TopArtistRow>>

    @Query("""SELECT CAST((playedAtMs / 86400000) AS INTEGER) AS dayEpochDay,
                     SUM(playedSeconds) AS seconds
              FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
              GROUP BY dayEpochDay
              ORDER BY dayEpochDay ASC""")
    fun dailyBucketsFlow(startMs: Long, endMs: Long): Flow<List<DailyBucketRow>>

    @Query("""SELECT CAST(((playedAtMs / 1000 / 3600) % 24) AS INTEGER) AS hourOfDay,
                     SUM(playedSeconds) AS seconds
              FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
              GROUP BY hourOfDay
              ORDER BY hourOfDay ASC""")
    fun hourBucketsFlow(startMs: Long, endMs: Long): Flow<List<HourBucketRow>>

    @Query("""SELECT language, COUNT(*) AS count FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
              GROUP BY language ORDER BY count DESC""")
    fun languageDistributionFlow(startMs: Long, endMs: Long): Flow<List<LanguageBucketRow>>

    @Query("""SELECT genre, COUNT(*) AS count FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
              GROUP BY genre ORDER BY count DESC""")
    fun genreDistributionFlow(startMs: Long, endMs: Long): Flow<List<GenreBucketRow>>

    @Query("""SELECT CAST((playedAtMs / 86400000) AS INTEGER) AS dayEpochDay,
                     SUM(playedSeconds) AS seconds
              FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
              GROUP BY dayEpochDay
              ORDER BY dayEpochDay ASC""")
    fun heatmapFlow(startMs: Long, endMs: Long): Flow<List<DateBucketRow>>

    @Query("""SELECT e.musicId, e.platform, e.title, e.artistRaw, e.album, e.artwork,
                     MIN(e.playedAtMs) AS firstSeenMs,
                     MAX(e.playedAtMs) AS lastSeenMs,
                     COUNT(*) AS playCount,
                     SUM(e.playedSeconds) AS totalSec
              FROM listen_event e
              WHERE e.playedAtMs >= :startMs AND e.playedAtMs < :endMs
              GROUP BY e.musicId, e.platform
              HAVING MIN(e.playedAtMs) = (
                  SELECT MIN(e2.playedAtMs) FROM listen_event e2
                  WHERE e2.musicId = e.musicId AND e2.platform = e.platform
              )
              ORDER BY firstSeenMs DESC""")
    fun firstSeenInWindowFlow(startMs: Long, endMs: Long): Flow<List<ListenedSongRow>>

    @Query("""SELECT musicId, platform, title, artistRaw, album, artwork,
                     MIN(playedAtMs) AS firstSeenMs,
                     MAX(playedAtMs) AS lastSeenMs,
                     COUNT(*) AS playCount,
                     SUM(playedSeconds) AS totalSec
              FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
              GROUP BY musicId, platform
              ORDER BY playCount DESC, totalSec DESC""")
    fun allSongsInWindowFlow(startMs: Long, endMs: Long): Flow<List<ListenedSongRow>>

    @Query("""SELECT e.musicId, e.platform, e.title, e.artistRaw, e.album, e.artwork,
                     MIN(e.playedAtMs) AS firstSeenMs,
                     MAX(e.playedAtMs) AS lastSeenMs,
                     COUNT(*) AS playCount,
                     SUM(e.playedSeconds) AS totalSec
              FROM listen_event e
              JOIN listen_event_artist a ON a.eventId = e.id
              WHERE e.playedAtMs >= :startMs AND e.playedAtMs < :endMs
                AND a.artistName = :artistName
              GROUP BY e.musicId, e.platform
              ORDER BY playCount DESC""")
    fun songsByArtistFlow(startMs: Long, endMs: Long, artistName: String): Flow<List<ListenedSongRow>>

    @Query("""SELECT musicId, platform, title, artistRaw, album, artwork,
                     MIN(playedAtMs) AS firstSeenMs,
                     MAX(playedAtMs) AS lastSeenMs,
                     COUNT(*) AS playCount,
                     SUM(playedSeconds) AS totalSec
              FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
                AND language = :language
              GROUP BY musicId, platform
              ORDER BY playCount DESC""")
    fun songsByLanguageFlow(startMs: Long, endMs: Long, language: String): Flow<List<ListenedSongRow>>

    @Query("""SELECT musicId, platform, title, artistRaw, album, artwork,
                     MIN(playedAtMs) AS firstSeenMs,
                     MAX(playedAtMs) AS lastSeenMs,
                     COUNT(*) AS playCount,
                     SUM(playedSeconds) AS totalSec
              FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
                AND genre = :genre
              GROUP BY musicId, platform
              ORDER BY playCount DESC""")
    fun songsByGenreFlow(startMs: Long, endMs: Long, genre: String): Flow<List<ListenedSongRow>>
}

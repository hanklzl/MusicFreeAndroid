package com.hank.musicfree.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hank.musicfree.data.db.entity.MediaCacheEntity

data class MediaCacheCatalogRow(
    val platform: String,
    val itemId: String,
    val sourcesJson: String,
    val updatedAt: Long,
    val sourceMetadataBytes: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val artwork: String?,
    val durationMs: Long?,
    val libraryTitle: String?,
    val libraryArtist: String?,
    val libraryAlbum: String?,
    val libraryArtwork: String?,
    val libraryDurationMs: Long?,
    val listenTitle: String?,
    val listenArtist: String?,
    val listenAlbum: String?,
    val listenArtwork: String?,
    val listenDurationMs: Long?,
)

@Dao
interface MediaCacheDao {
    @Query("SELECT * FROM media_cache WHERE platform = :platform AND id = :id")
    suspend fun get(platform: String, id: String): MediaCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaCacheEntity)

    @Query("SELECT COUNT(*) FROM media_cache")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(length(CAST(sourcesJson AS BLOB))), 0) FROM media_cache")
    suspend fun totalSizeBytes(): Long

    @Query("SELECT * FROM media_cache ORDER BY updated_at ASC")
    suspend fun getOldestEntries(): List<MediaCacheEntity>

    @Query(
        """
        SELECT
            mc.platform AS platform,
            mc.id AS itemId,
            mc.sourcesJson AS sourcesJson,
            mc.updated_at AS updatedAt,
            length(CAST(mc.sourcesJson AS BLOB)) AS sourceMetadataBytes,
            mc.title AS title,
            mc.artist AS artist,
            mc.album AS album,
            mc.artwork AS artwork,
            mc.duration_ms AS durationMs,
            mi.title AS libraryTitle,
            mi.artist AS libraryArtist,
            mi.album AS libraryAlbum,
            mi.artwork AS libraryArtwork,
            mi.duration AS libraryDurationMs,
            (
                SELECT le.title
                FROM listen_event le
                WHERE le.platform = mc.platform AND le.musicId = mc.id
                ORDER BY le.playedAtMs DESC, le.id DESC
                LIMIT 1
            ) AS listenTitle,
            (
                SELECT le.artistRaw
                FROM listen_event le
                WHERE le.platform = mc.platform AND le.musicId = mc.id
                ORDER BY le.playedAtMs DESC, le.id DESC
                LIMIT 1
            ) AS listenArtist,
            (
                SELECT le.album
                FROM listen_event le
                WHERE le.platform = mc.platform AND le.musicId = mc.id
                ORDER BY le.playedAtMs DESC, le.id DESC
                LIMIT 1
            ) AS listenAlbum,
            (
                SELECT le.artwork
                FROM listen_event le
                WHERE le.platform = mc.platform AND le.musicId = mc.id
                ORDER BY le.playedAtMs DESC, le.id DESC
                LIMIT 1
            ) AS listenArtwork,
            (
                SELECT le.durationMs
                FROM listen_event le
                WHERE le.platform = mc.platform AND le.musicId = mc.id
                ORDER BY le.playedAtMs DESC, le.id DESC
                LIMIT 1
            ) AS listenDurationMs
        FROM media_cache mc
        LEFT JOIN music_items mi ON mi.platform = mc.platform AND mi.id = mc.id
        ORDER BY mc.updated_at DESC
        """,
    )
    suspend fun listCatalogRows(): List<MediaCacheCatalogRow>

    @Query(
        """
        DELETE FROM media_cache WHERE rowid IN (
            SELECT rowid FROM media_cache ORDER BY updated_at ASC LIMIT :n
        )
        """,
    )
    suspend fun deleteOldest(n: Int)

    @Query("DELETE FROM media_cache WHERE platform = :platform")
    suspend fun deleteByPlatform(platform: String)

    @Query("DELETE FROM media_cache WHERE platform = :platform AND id = :id")
    suspend fun delete(platform: String, id: String)

    @Query("DELETE FROM media_cache")
    suspend fun deleteAll()
}

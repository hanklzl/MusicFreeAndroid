package com.hank.musicfree.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hank.musicfree.data.db.entity.MediaCacheEntity

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

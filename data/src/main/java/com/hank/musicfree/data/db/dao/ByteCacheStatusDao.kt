package com.hank.musicfree.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hank.musicfree.data.db.entity.ByteCacheStatusEntity

@Dao
interface ByteCacheStatusDao {
    @Query(
        """
        SELECT * FROM byte_cache_status
        WHERE platform = :platform AND music_id = :musicId AND quality = :quality
        """,
    )
    suspend fun get(platform: String, musicId: String, quality: String): ByteCacheStatusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ByteCacheStatusEntity)

    @Query(
        """
        DELETE FROM byte_cache_status
        WHERE platform = :platform AND music_id = :musicId AND quality = :quality
        """,
    )
    suspend fun delete(platform: String, musicId: String, quality: String)

    @Query("DELETE FROM byte_cache_status WHERE platform = :platform AND music_id = :musicId")
    suspend fun deleteBySong(platform: String, musicId: String)

    @Query("DELETE FROM byte_cache_status WHERE platform = :platform")
    suspend fun deleteByPlatform(platform: String)

    @Query("DELETE FROM byte_cache_status")
    suspend fun deleteAll()
}

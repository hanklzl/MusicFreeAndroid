package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zili.android.musicfreeandroid.data.db.entity.LyricCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricCacheDao {
    @Query("SELECT * FROM lyric_cache WHERE musicPlatform = :platform AND musicId = :id")
    fun observeByKey(platform: String, id: String): Flow<LyricCacheEntity?>

    @Query("SELECT * FROM lyric_cache WHERE musicPlatform = :platform AND musicId = :id")
    suspend fun getByKey(platform: String, id: String): LyricCacheEntity?

    @Upsert
    suspend fun upsert(entity: LyricCacheEntity)

    @Query(
        """
        UPDATE lyric_cache
        SET associatedMusicJson = NULL, updatedAt = :updatedAt
        WHERE musicPlatform = :platform AND musicId = :id
        """,
    )
    suspend fun clearAssociation(platform: String, id: String, updatedAt: Long)

    @Query(
        """
        UPDATE lyric_cache
        SET localRawLrc = NULL, localTranslation = NULL, updatedAt = :updatedAt
        WHERE musicPlatform = :platform AND musicId = :id
        """,
    )
    suspend fun deleteLocalLyrics(platform: String, id: String, updatedAt: Long)

    @Query(
        """
        UPDATE lyric_cache
        SET userOffsetMs = :offsetMs, updatedAt = :updatedAt
        WHERE musicPlatform = :platform AND musicId = :id
        """,
    )
    suspend fun setOffset(platform: String, id: String, offsetMs: Long, updatedAt: Long)
}

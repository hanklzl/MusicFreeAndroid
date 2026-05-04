package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: LyricCacheEntity): Long

    @Query(
        """
        UPDATE lyric_cache
        SET remoteRawLrc = :remoteRawLrc,
            remoteRawLrcTxt = :remoteRawLrcTxt,
            remoteTranslation = :remoteTranslation,
            remoteSourceType = :remoteSourceType,
            remoteSourcePlatform = :remoteSourcePlatform,
            remoteSourceMusicId = :remoteSourceMusicId,
            remoteSourceTitle = :remoteSourceTitle,
            updatedAt = :updatedAt
        WHERE musicPlatform = :platform AND musicId = :id
        """,
    )
    suspend fun saveRemoteLyric(
        platform: String,
        id: String,
        remoteRawLrc: String?,
        remoteRawLrcTxt: String?,
        remoteTranslation: String?,
        remoteSourceType: String?,
        remoteSourcePlatform: String?,
        remoteSourceMusicId: String?,
        remoteSourceTitle: String?,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE lyric_cache
        SET associatedMusicJson = :associatedMusicJson, updatedAt = :updatedAt
        WHERE musicPlatform = :platform AND musicId = :id
        """,
    )
    suspend fun setAssociation(platform: String, id: String, associatedMusicJson: String?, updatedAt: Long)

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
        SET localRawLrc = :raw, updatedAt = :updatedAt
        WHERE musicPlatform = :platform AND musicId = :id
        """,
    )
    suspend fun setLocalRawLyric(platform: String, id: String, raw: String, updatedAt: Long)

    @Query(
        """
        UPDATE lyric_cache
        SET localTranslation = :translation, updatedAt = :updatedAt
        WHERE musicPlatform = :platform AND musicId = :id
        """,
    )
    suspend fun setLocalTranslation(platform: String, id: String, translation: String, updatedAt: Long)

    @Query(
        """
        UPDATE lyric_cache
        SET userOffsetMs = :offsetMs, updatedAt = :updatedAt
        WHERE musicPlatform = :platform AND musicId = :id
        """,
    )
    suspend fun setOffset(platform: String, id: String, offsetMs: Long, updatedAt: Long)
}

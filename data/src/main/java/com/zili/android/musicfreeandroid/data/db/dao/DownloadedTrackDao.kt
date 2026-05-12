package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zili.android.musicfreeandroid.data.db.entity.DownloadedTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedTrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadedTrackEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_tracks WHERE id = :id AND platform = :platform)")
    suspend fun exists(id: String, platform: String): Boolean

    @Query("SELECT mediaStoreUri FROM downloaded_tracks WHERE id = :id AND platform = :platform")
    suspend fun findUri(id: String, platform: String): String?

    /**
     * Returns the full row for `(id, platform)` or null when the track isn't
     * downloaded. Added in Phase F so [com.zili.android.musicfreeandroid.plugin.engine.MusicItemBridgeProjector]
     * can prefer `relativePath` over `mediaStoreUri` when projecting the local
     * path into the bridge map.
     */
    @Query("SELECT * FROM downloaded_tracks WHERE id = :id AND platform = :platform")
    suspend fun get(id: String, platform: String): DownloadedTrackEntity?

    @Query("DELETE FROM downloaded_tracks WHERE id = :id AND platform = :platform")
    suspend fun deleteByKey(id: String, platform: String)

    @Query("DELETE FROM downloaded_tracks WHERE platform = :platform")
    suspend fun deleteByPlatform(platform: String)

    @Query("SELECT id || '@' || platform FROM downloaded_tracks")
    fun observeKeys(): Flow<List<String>>
}

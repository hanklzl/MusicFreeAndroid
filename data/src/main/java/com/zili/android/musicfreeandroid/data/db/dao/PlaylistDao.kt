package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zili.android.musicfreeandroid.data.db.entity.MusicItemEntity
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistEntity
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistMusicCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observeAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(crossRef: PlaylistMusicCrossRef)

    @Query("DELETE FROM playlist_music WHERE playlistId = :playlistId AND musicId = :musicId AND musicPlatform = :musicPlatform")
    suspend fun removeMusicFromPlaylist(playlistId: String, musicId: String, musicPlatform: String)

    @Query("""
        SELECT m.* FROM music_items m
        INNER JOIN playlist_music pm ON m.id = pm.musicId AND m.platform = pm.musicPlatform
        WHERE pm.playlistId = :playlistId
        ORDER BY pm.sortOrder ASC
    """)
    fun observeMusicInPlaylist(playlistId: String): Flow<List<MusicItemEntity>>

    @Query("SELECT COUNT(*) FROM playlist_music WHERE playlistId = :playlistId")
    suspend fun countMusicInPlaylist(playlistId: String): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM playlist_music WHERE playlistId = :playlistId")
    suspend fun maxSortOrderInPlaylist(playlistId: String): Int
}

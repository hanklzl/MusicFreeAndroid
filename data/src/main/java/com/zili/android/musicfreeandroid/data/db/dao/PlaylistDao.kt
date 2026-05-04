package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zili.android.musicfreeandroid.data.db.entity.MusicItemEntity
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistEntity
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistMusicCrossRef
import kotlinx.coroutines.flow.Flow

data class MusicItemWithAddedAt(
    @Embedded val music: MusicItemEntity,
    @ColumnInfo(name = "pm_addedAt") val addedAt: Long,
)

data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    @ColumnInfo(name = "worksNum") val worksNum: Int,
)

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

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: String): Flow<PlaylistEntity?>

    @Query("""
        SELECT p.*, COALESCE(c.cnt, 0) AS worksNum
        FROM playlists p
        LEFT JOIN (
            SELECT playlistId, COUNT(*) AS cnt FROM playlist_music GROUP BY playlistId
        ) c ON c.playlistId = p.id
        ORDER BY p.updatedAt DESC
    """)
    fun observeAllPlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    @Query("""
        SELECT p.*, COALESCE(c.cnt, 0) AS worksNum
        FROM playlists p
        LEFT JOIN (
            SELECT playlistId, COUNT(*) AS cnt FROM playlist_music WHERE playlistId = :id GROUP BY playlistId
        ) c ON c.playlistId = p.id
        WHERE p.id = :id
    """)
    fun observePlaylistWithCount(id: String): Flow<PlaylistWithCount?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(crossRef: PlaylistMusicCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefIgnore(crossRef: PlaylistMusicCrossRef): Long

    @Query("DELETE FROM playlist_music WHERE playlistId = :playlistId AND musicId = :musicId AND musicPlatform = :musicPlatform")
    suspend fun removeMusicFromPlaylist(playlistId: String, musicId: String, musicPlatform: String)

    @Query("""
        SELECT m.* FROM music_items m
        INNER JOIN playlist_music pm ON m.id = pm.musicId AND m.platform = pm.musicPlatform
        WHERE pm.playlistId = :playlistId
        ORDER BY pm.sortOrder ASC
    """)
    fun observeMusicInPlaylist(playlistId: String): Flow<List<MusicItemEntity>>

    @Query("""
        SELECT m.*, pm.addedAt AS pm_addedAt FROM music_items m
        INNER JOIN playlist_music pm ON m.id = pm.musicId AND m.platform = pm.musicPlatform
        WHERE pm.playlistId = :playlistId
        ORDER BY pm.sortOrder ASC
    """)
    fun observeMusicWithAddedAt(playlistId: String): Flow<List<MusicItemWithAddedAt>>

    @Query("SELECT COUNT(*) FROM playlist_music WHERE playlistId = :playlistId")
    suspend fun countMusicInPlaylist(playlistId: String): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM playlist_music WHERE playlistId = :playlistId")
    suspend fun maxSortOrderInPlaylist(playlistId: String): Int

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM playlist_music
            WHERE playlistId = :playlistId AND musicId = :musicId AND musicPlatform = :musicPlatform
        )
    """)
    suspend fun isInPlaylist(playlistId: String, musicId: String, musicPlatform: String): Boolean

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM playlist_music
            WHERE playlistId = :playlistId AND musicId = :musicId AND musicPlatform = :musicPlatform
        )
    """)
    fun observeIsInPlaylist(playlistId: String, musicId: String, musicPlatform: String): Flow<Boolean>

    @Query("""
        UPDATE playlists SET sortMode = :mode, updatedAt = :updatedAt WHERE id = :id
    """)
    suspend fun setSortMode(id: String, mode: String, updatedAt: Long)

    @Query("""
        UPDATE playlists SET coverUri = :coverUri, updatedAt = :updatedAt WHERE id = :id
    """)
    suspend fun setCoverUri(id: String, coverUri: String?, updatedAt: Long)

    @Query("""
        UPDATE playlists SET name = :name, description = :description, updatedAt = :updatedAt WHERE id = :id
    """)
    suspend fun updateNameDescription(id: String, name: String, description: String?, updatedAt: Long)

    @Query("""
        UPDATE playlist_music SET sortOrder = :sortOrder
        WHERE playlistId = :playlistId AND musicId = :musicId AND musicPlatform = :musicPlatform
    """)
    suspend fun setCrossRefSortOrder(playlistId: String, musicId: String, musicPlatform: String, sortOrder: Int)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: String): Int
}

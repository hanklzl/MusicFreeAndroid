package com.zili.android.musicfreeandroid.data.repository

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.PlaylistDao
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistMusicCrossRef
import com.zili.android.musicfreeandroid.data.mapper.toEntity
import com.zili.android.musicfreeandroid.data.mapper.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val converters: Converters,
) {

    fun observeAllPlaylists(): Flow<List<Playlist>> =
        playlistDao.observeAllPlaylists().map { entities -> entities.map { it.toModel() } }

    suspend fun getPlaylistById(id: String): Playlist? =
        playlistDao.getPlaylistById(id)?.toModel()

    suspend fun createPlaylist(playlist: Playlist) {
        val now = System.currentTimeMillis()
        playlistDao.insertPlaylist(playlist.toEntity(createdAt = now, updatedAt = now))
    }

    suspend fun updatePlaylist(playlist: Playlist) {
        val existing = playlistDao.getPlaylistById(playlist.id) ?: return
        playlistDao.updatePlaylist(
            playlist.toEntity(createdAt = existing.createdAt, updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        val entity = playlistDao.getPlaylistById(playlist.id) ?: return
        playlistDao.deletePlaylist(entity)
    }

    fun observeMusicInPlaylist(playlistId: String): Flow<List<MusicItem>> =
        playlistDao.observeMusicInPlaylist(playlistId).map { entities ->
            entities.map { it.toModel(converters) }
        }

    suspend fun addMusicToPlaylist(playlistId: String, musicItem: MusicItem) {
        val nextOrder = playlistDao.maxSortOrderInPlaylist(playlistId) + 1
        playlistDao.insertCrossRef(
            PlaylistMusicCrossRef(
                playlistId = playlistId,
                musicId = musicItem.id,
                musicPlatform = musicItem.platform,
                sortOrder = nextOrder,
            )
        )
    }

    suspend fun removeMusicFromPlaylist(playlistId: String, musicItem: MusicItem) {
        playlistDao.removeMusicFromPlaylist(playlistId, musicItem.id, musicItem.platform)
    }

    suspend fun countMusicInPlaylist(playlistId: String): Int =
        playlistDao.countMusicInPlaylist(playlistId)
}

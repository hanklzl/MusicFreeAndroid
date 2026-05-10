package com.zili.android.musicfreeandroid.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.model.SortMode
import com.zili.android.musicfreeandroid.data.cover.PlaylistCoverStore
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.MusicDao
import com.zili.android.musicfreeandroid.data.db.dao.PlaylistDao
import com.zili.android.musicfreeandroid.data.db.dao.PlaylistWithCount
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistMusicCrossRef
import com.zili.android.musicfreeandroid.data.mapper.toEntity
import com.zili.android.musicfreeandroid.data.mapper.toModel
import com.zili.android.musicfreeandroid.data.sort.applySort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PlaylistRepository @Inject constructor(
    private val db: AppDatabase,
    private val playlistDao: PlaylistDao,
    private val musicDao: MusicDao,
    private val coverStore: PlaylistCoverStore,
    private val converters: Converters,
) {

    fun observeAllPlaylists(): Flow<List<Playlist>> =
        playlistDao.observeAllPlaylistsWithCount().onStart {
            ensureFavoritePlaylist()
        }.map { rows ->
            rows.map { it.playlist.toModel(worksNum = it.worksNum, legacyCoverResolver = ::resolveLegacyCoverUri) }
        }

    fun observePlaylist(id: String): Flow<Playlist?> =
        playlistDao.observePlaylistWithCount(id).onStart {
            if (id == Playlist.DEFAULT_FAVORITE_ID) ensureFavoritePlaylist()
        }.map { row ->
            row?.playlist?.toModel(worksNum = row.worksNum, legacyCoverResolver = ::resolveLegacyCoverUri)
        }

    suspend fun getPlaylistById(id: String): Playlist? =
        playlistDao.getPlaylistById(id)?.toModel(legacyCoverResolver = ::resolveLegacyCoverUri)

    fun observeFavorite(): Flow<Playlist?> = observePlaylist(Playlist.DEFAULT_FAVORITE_ID)

    fun isFavorite(item: MusicItem): Flow<Boolean> =
        playlistDao.observeIsInPlaylist(Playlist.DEFAULT_FAVORITE_ID, item.id, item.platform).onStart {
            ensureFavoritePlaylist()
        }

    suspend fun toggleFavorite(item: MusicItem) {
        ensureFavoritePlaylist()
        val present = playlistDao.isInPlaylist(Playlist.DEFAULT_FAVORITE_ID, item.id, item.platform)
        if (present) removeMusicFromPlaylist(Playlist.DEFAULT_FAVORITE_ID, item)
        else addMusicToPlaylist(Playlist.DEFAULT_FAVORITE_ID, item)
    }

    suspend fun createPlaylist(playlist: Playlist) {
        val now = System.currentTimeMillis()
        playlistDao.insertPlaylist(playlist.toEntity(createdAt = now, updatedAt = now))
    }

    suspend fun updatePlaylistInfo(id: String, name: String? = null, description: String? = null) {
        if (id == Playlist.DEFAULT_FAVORITE_ID && name != null) {
            throw IllegalArgumentException("Cannot rename the default favorite playlist")
        }
        val entity = playlistDao.getPlaylistById(id) ?: return
        playlistDao.updateNameDescription(
            id = id,
            name = name ?: entity.name,
            description = description ?: entity.description,
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun setSortMode(id: String, mode: SortMode) {
        playlistDao.setSortMode(id, mode.name, System.currentTimeMillis())
    }

    suspend fun applyManualSortOrder(id: String, orderedItems: List<MusicItem>) {
        orderedItems.forEachIndexed { index, item ->
            playlistDao.setCrossRefSortOrder(id, item.id, item.platform, index)
        }
    }

    suspend fun setCover(id: String, sourceUri: Uri?) {
        val rel = if (sourceUri == null) {
            coverStore.delete(id); null
        } else {
            coverStore.saveFromUri(id, sourceUri)
        }
        playlistDao.setCoverUri(id, rel, System.currentTimeMillis())
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        if (playlist.id == Playlist.DEFAULT_FAVORITE_ID) {
            throw IllegalStateException("Cannot delete the default favorite playlist")
        }
        coverStore.delete(playlist.id)
        playlistDao.deletePlaylistById(playlist.id)
    }

    suspend fun addMusicToPlaylist(playlistId: String, item: MusicItem): Boolean =
        addMusicToPlaylistWithCoverSync(playlistId, item)

    suspend fun addMusicsToPlaylist(playlistId: String, items: List<MusicItem>): Int {
        if (items.isEmpty()) return 0
        val insertedItems = mutableListOf<MusicItem>()
        val addedCount = db.withTransaction {
            var addedCount = 0
            for (item in items) {
                if (addMusicToPlaylistNoCoverSync(playlistId, item)) {
                    addedCount++
                    insertedItems.add(item)
                }
            }
            addedCount
        }
        for (item in insertedItems) {
            if (syncPlaylistCoverIfNeeded(playlistId, item)) break
        }
        return addedCount
    }

    private suspend fun addMusicToPlaylistWithCoverSync(playlistId: String, item: MusicItem): Boolean {
        val added = addMusicToPlaylistNoCoverSync(playlistId, item)
        if (added) syncPlaylistCoverIfNeeded(playlistId, item)
        return added
    }

    private suspend fun addMusicToPlaylistNoCoverSync(playlistId: String, item: MusicItem): Boolean {
        // Keep this order (upsert before cross-ref insert) to preserve existing behavior:
        // duplicates should still refresh music metadata, while playlist membership is guarded
        // by the unique cross-ref insert.
        musicDao.upsert(item.toEntity(converters))
        val nextOrder = playlistDao.maxSortOrderInPlaylist(playlistId) + 1
        val now = System.currentTimeMillis()
        val rowId = playlistDao.insertCrossRefIgnore(
            PlaylistMusicCrossRef(
                playlistId = playlistId,
                musicId = item.id,
                musicPlatform = item.platform,
                sortOrder = nextOrder,
                addedAt = now,
            )
        )
        return rowId != -1L
    }

    private suspend fun syncPlaylistCoverIfNeeded(playlistId: String, item: MusicItem): Boolean {
        if (item.artwork.isNullOrBlank()) return false
        val playlist = playlistDao.getPlaylistById(playlistId)
        if (playlist?.coverUri != null) return true
        val rel = coverStore.copyFromArtwork(playlistId, item.artwork)
        if (rel != null) {
            playlistDao.setCoverUri(playlistId, rel, System.currentTimeMillis())
            return true
        }
        return false
    }

    suspend fun removeMusicFromPlaylist(playlistId: String, item: MusicItem) {
        playlistDao.removeMusicFromPlaylist(playlistId, item.id, item.platform)
    }

    fun observeMusicInPlaylist(playlistId: String): Flow<List<MusicItem>> =
        playlistDao.observePlaylist(playlistId).flatMapLatest { entity ->
            val mode = entity?.let {
                runCatching { SortMode.valueOf(it.sortMode) }.getOrDefault(SortMode.Manual)
            } ?: SortMode.Manual
            playlistDao.observeMusicWithAddedAt(playlistId).map { rows ->
                rows.map { it.toModel(converters) }.applySort(mode)
            }
        }

    suspend fun countMusicInPlaylist(playlistId: String): Int =
        playlistDao.countMusicInPlaylist(playlistId)

    private fun resolveLegacyCoverUri(raw: String): String? =
        if (raw.startsWith(LEGACY_COVER_PREFIX)) {
            Uri.fromFile(coverStore.absoluteFile(raw)).toString()
        } else {
            null
        }

    private suspend fun ensureFavoritePlaylist() {
        val now = System.currentTimeMillis()
        playlistDao.insertPlaylistIgnore(
            Playlist(
                id = Playlist.DEFAULT_FAVORITE_ID,
                name = Playlist.DEFAULT_FAVORITE_NAME,
                coverUri = null,
            ).toEntity(createdAt = now, updatedAt = now),
        )
    }

    companion object { private const val LEGACY_COVER_PREFIX = PlaylistCoverStore.BASE_DIR_NAME + "/" }
}

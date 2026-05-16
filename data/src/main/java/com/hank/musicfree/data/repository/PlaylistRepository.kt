package com.hank.musicfree.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.core.model.SortMode
import com.hank.musicfree.data.cover.PlaylistCoverStore
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.db.dao.MusicDao
import com.hank.musicfree.data.db.dao.PlaylistDao
import com.hank.musicfree.data.db.dao.PlaylistWithCount
import com.hank.musicfree.data.db.entity.PlaylistMusicCrossRef
import com.hank.musicfree.data.mapper.toEntity
import com.hank.musicfree.data.mapper.toModel
import com.hank.musicfree.data.sort.applySort
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import kotlinx.coroutines.CancellationException
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
    private val defaultSortProvider: PlaylistDefaultSortProvider = PlaylistDefaultSortProvider.Manual,
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
        logDataWrite(
            operation = "toggle_favorite",
            fields = playlistFields(Playlist.DEFAULT_FAVORITE_ID) + item.logFields(),
        ) {
            ensureFavoritePlaylist()
            val present = playlistDao.isInPlaylist(Playlist.DEFAULT_FAVORITE_ID, item.id, item.platform)
            if (present) removeMusicFromPlaylist(Playlist.DEFAULT_FAVORITE_ID, item)
            else addMusicToPlaylist(Playlist.DEFAULT_FAVORITE_ID, item)
        }
    }

    suspend fun createPlaylist(playlist: Playlist) {
        logDataWrite(
            operation = "create_playlist",
            fields = playlist.logFields(),
        ) {
            val now = System.currentTimeMillis()
            val sortMode = if (playlist.id == Playlist.DEFAULT_FAVORITE_ID || playlist.sortMode != SortMode.Manual) {
                playlist.sortMode
            } else {
                defaultSortProvider.defaultSortMode()
            }
            playlistDao.insertPlaylist(
                playlist.copy(sortMode = sortMode).toEntity(createdAt = now, updatedAt = now),
            )
        }
    }

    suspend fun updatePlaylistInfo(id: String, name: String? = null, description: String? = null) {
        if (id == Playlist.DEFAULT_FAVORITE_ID && name != null) {
            logDataSkipped(
                operation = "update_playlist_info",
                fields = playlistFields(id),
                reason = "protected_default_playlist",
            )
            throw IllegalArgumentException("Cannot rename the default favorite playlist")
        }
        val entity = playlistDao.getPlaylistById(id) ?: run {
            logDataSkipped(
                operation = "update_playlist_info",
                fields = playlistFields(id),
                reason = LogFields.Reason.NOT_FOUND,
            )
            return
        }
        logDataWrite(
            operation = "update_playlist_info",
            fields = playlistFields(id) + mapOf("itemName" to (name ?: entity.name)),
        ) {
            playlistDao.updateNameDescription(
                id = id,
                name = name ?: entity.name,
                description = description ?: entity.description,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    suspend fun setSortMode(id: String, mode: SortMode) {
        logDataWrite(
            operation = "set_playlist_sort_mode",
            fields = playlistFields(id) + mapOf("sortMode" to mode.name),
        ) {
            playlistDao.setSortMode(id, mode.name, System.currentTimeMillis())
        }
    }

    suspend fun applyManualSortOrder(id: String, orderedItems: List<MusicItem>) {
        if (orderedItems.isEmpty()) {
            logDataSkipped(
                operation = "apply_manual_sort_order",
                fields = playlistFields(id) + mapOf("count" to 0),
                reason = LogFields.Reason.EMPTY_INPUT,
            )
            return
        }
        logDataWrite(
            operation = "apply_manual_sort_order",
            fields = playlistFields(id) + mapOf("count" to orderedItems.size),
        ) {
            orderedItems.forEachIndexed { index, item ->
                playlistDao.setCrossRefSortOrder(id, item.id, item.platform, index)
            }
        }
    }

    suspend fun setCover(id: String, sourceUri: Uri?) {
        logDataWrite(
            operation = if (sourceUri == null) "delete_playlist_cover" else "set_playlist_cover",
            fields = playlistFields(id) + mapOf(
                "pathType" to (sourceUri?.playlistPathType() ?: "file_uri"),
                "sourceScheme" to sourceUri?.scheme.orEmpty(),
            ),
        ) {
            val rel = if (sourceUri == null) {
                coverStore.delete(id); null
            } else {
                coverStore.saveFromUri(id, sourceUri)
            }
            playlistDao.setCoverUri(id, rel, System.currentTimeMillis())
        }
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        if (playlist.id == Playlist.DEFAULT_FAVORITE_ID) {
            logDataSkipped(
                operation = "delete_playlist",
                fields = playlist.logFields(),
                reason = "protected_default_playlist",
            )
            throw IllegalStateException("Cannot delete the default favorite playlist")
        }
        logDataWrite(
            operation = "delete_playlist",
            fields = playlist.logFields(),
            resultFields = { deletedRows -> mapOf("count" to deletedRows) },
            skippedReason = { deletedRows -> if (deletedRows == 0) LogFields.Reason.NOT_FOUND else null },
        ) {
            coverStore.delete(playlist.id)
            playlistDao.deletePlaylistById(playlist.id)
        }
    }

    suspend fun addMusicToPlaylist(playlistId: String, item: MusicItem): Boolean =
        logDataWrite(
            operation = "add_music_to_playlist",
            fields = playlistFields(playlistId) + item.logFields(),
            resultFields = { added -> mapOf("count" to if (added) 1 else 0) },
            skippedReason = { added -> if (!added) LogFields.Reason.DUPLICATE else null },
        ) {
            addMusicToPlaylistWithCoverSync(playlistId, item)
        }

    suspend fun addMusicsToPlaylist(playlistId: String, items: List<MusicItem>): Int {
        if (items.isEmpty()) {
            logDataSkipped(
                operation = "add_musics_to_playlist",
                fields = playlistFields(playlistId) + mapOf("count" to 0),
                reason = LogFields.Reason.EMPTY_INPUT,
            )
            return 0
        }
        return logDataWrite(
            operation = "add_musics_to_playlist",
            fields = playlistFields(playlistId) + mapOf("requestedCount" to items.size),
            resultFields = { addedCount ->
                mapOf(
                    "count" to addedCount,
                    "skippedCount" to items.size - addedCount,
                )
            },
            skippedReason = { addedCount -> if (addedCount == 0) LogFields.Reason.DUPLICATE else null },
        ) {
            val insertedItems = mutableListOf<MusicItem>()
            val addedAt = System.currentTimeMillis()
            val addedCount = db.withTransaction {
                var addedCount = 0
                val baseOrder = playlistDao.minSortOrderInPlaylist(playlistId) - items.size
                for ((index, item) in items.withIndex()) {
                    if (
                        addMusicToPlaylistNoCoverSync(
                            playlistId = playlistId,
                            item = item,
                            sortOrder = baseOrder + index,
                            addedAt = addedAt,
                        )
                    ) {
                        addedCount++
                        insertedItems.add(item)
                    }
                }
                addedCount
            }
            for (item in insertedItems) {
                if (syncPlaylistCoverIfNeeded(playlistId, item)) break
            }
            addedCount
        }
    }

    private suspend fun addMusicToPlaylistWithCoverSync(playlistId: String, item: MusicItem): Boolean {
        val added = db.withTransaction {
            addMusicToPlaylistNoCoverSync(
                playlistId = playlistId,
                item = item,
                sortOrder = playlistDao.minSortOrderInPlaylist(playlistId) - 1,
                addedAt = System.currentTimeMillis(),
            )
        }
        if (added) syncPlaylistCoverIfNeeded(playlistId, item)
        return added
    }

    private suspend fun addMusicToPlaylistNoCoverSync(
        playlistId: String,
        item: MusicItem,
        sortOrder: Int,
        addedAt: Long,
    ): Boolean {
        // Keep this order (upsert before cross-ref insert) to preserve existing behavior:
        // duplicates should still refresh music metadata, while playlist membership is guarded
        // by the unique cross-ref insert.
        musicDao.upsert(item.toEntity(converters))
        val rowId = playlistDao.insertCrossRefIgnore(
            PlaylistMusicCrossRef(
                playlistId = playlistId,
                musicId = item.id,
                musicPlatform = item.platform,
                sortOrder = sortOrder,
                addedAt = addedAt,
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
        logDataWrite(
            operation = "remove_music_from_playlist",
            fields = playlistFields(playlistId) + item.logFields(),
        ) {
            playlistDao.removeMusicFromPlaylist(playlistId, item.id, item.platform)
        }
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

    private suspend fun <T> logDataWrite(
        operation: String,
        fields: Map<String, Any?> = emptyMap(),
        resultFields: (T) -> Map<String, Any?> = { emptyMap() },
        skippedReason: (T) -> String? = { null },
        block: suspend () -> T,
    ): T {
        val baseFields = mapOf("operation" to operation) + fields
        MfLog.detail(
            category = LogCategory.DATA,
            event = "data_write_start",
            fields = baseFields,
        )
        val startedAt = System.nanoTime()
        return try {
            val result = block()
            val reason = skippedReason(result)
            val terminalFields = baseFields + resultFields(result) + mapOf(
                "durationMs" to elapsedMs(startedAt),
                "result" to if (reason == null) LogFields.Result.SUCCESS else LogFields.Result.SKIPPED,
            ) + (reason?.let { mapOf("reason" to it) }.orEmpty())
            MfLog.detail(
                category = LogCategory.DATA,
                event = if (reason == null) "data_write_success" else "data_write_skipped",
                fields = terminalFields,
            )
            result
        } catch (error: CancellationException) {
            MfLog.detail(
                category = LogCategory.DATA,
                event = "data_write_cancelled",
                fields = baseFields + mapOf(
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.CANCELLED,
                    "reason" to LogFields.Reason.CANCELLED,
                ),
            )
            throw error
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.DATA,
                event = "data_write_failed",
                throwable = error,
                fields = baseFields + mapOf(
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.FAILURE,
                    "reason" to "exception",
                ),
            )
            throw error
        }
    }

    private fun logDataSkipped(operation: String, fields: Map<String, Any?>, reason: String) {
        MfLog.detail(
            category = LogCategory.DATA,
            event = "data_write_skipped",
            fields = mapOf(
                "operation" to operation,
                "result" to LogFields.Result.SKIPPED,
                "reason" to reason,
            ) + fields,
        )
    }

    private fun Playlist.logFields(): Map<String, Any?> = playlistFields(id) + mapOf(
        "itemName" to name,
        "sortMode" to sortMode.name,
    )

    private fun MusicItem.logFields(): Map<String, Any?> = mapOf(
        "itemId" to id,
        "itemName" to title,
        "platform" to platform,
    )

    private fun playlistFields(id: String): Map<String, Any?> = mapOf("playlistId" to id)

    private fun Uri.playlistPathType(): String = when (scheme?.lowercase()) {
        "content" -> "content_uri"
        "file" -> "file_uri"
        "http", "https" -> "remote_url"
        else -> "unknown"
    }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    companion object { private const val LEGACY_COVER_PREFIX = PlaylistCoverStore.BASE_DIR_NAME + "/" }
}

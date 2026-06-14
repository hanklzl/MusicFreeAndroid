package com.hank.musicfree.feature.settings

import android.content.Context
import coil3.imageLoader
import com.hank.musicfree.core.telemetry.PlayCacheTelemetry
import com.hank.musicfree.data.repository.LyricRepository
import com.hank.musicfree.data.repository.MediaCacheRepository
import com.hank.musicfree.data.repository.MusicRepository
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.player.cache.SimpleCacheHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SongCacheClearResult(
    val platform: String,
    val itemId: String,
    val localAssociationCleared: Boolean,
    val durationMs: Long,
)

class SettingsCacheCleaner @Inject constructor(
    private val mediaCacheRepository: MediaCacheRepository,
    private val simpleCacheHolder: SimpleCacheHolder,
    private val playCacheTelemetry: PlayCacheTelemetry,
    private val lyricRepository: LyricRepository,
    private val musicRepository: MusicRepository,
    @param:ApplicationContext private val context: Context,
) {
    /**
     * Clears the audio-file layer (Media3 SimpleCache on disk).
     * Returns the number of bytes freed.
     */
    suspend fun clearAudioFileCache(): Long {
        val before = simpleCacheHolder.usedBytes()
        simpleCacheHolder.clearCache()
        val freed = (before - simpleCacheHolder.usedBytes()).coerceAtLeast(0L)
        playCacheTelemetry.cacheEvict(scope = "manual", count = 1, freedBytes = freed)
        return freed
    }

    /**
     * Clears the URL/header metadata layer (MediaCacheRepository DB entries).
     * Returns the number of bytes freed.
     */
    suspend fun clearMediaUrlMetadataCache(): Long {
        val before = mediaCacheRepository.estimatedBytes()
        mediaCacheRepository.clearAll()
        val after = mediaCacheRepository.estimatedBytes()
        return (before - after).coerceAtLeast(0L)
    }

    @Deprecated("Use clearAudioFileCache + clearMediaUrlMetadataCache")
    suspend fun clearMusicCache() {
        clearAudioFileCache()
        clearMediaUrlMetadataCache()
    }

    suspend fun clearLyricCache() {
        lyricRepository.clearAll()
    }

    suspend fun clearSongPlaybackCache(platform: String, itemId: String): SongCacheClearResult {
        val sanitizedPlatform = platform.trim()
        val sanitizedItemId = itemId.trim()
        require(sanitizedPlatform.isNotEmpty()) { "platform is required" }
        require(sanitizedItemId.isNotEmpty()) { "itemId is required" }

        val startedAt = System.nanoTime()
        return try {
            mediaCacheRepository.deleteItem(sanitizedPlatform, sanitizedItemId)
            val localAssociationCleared = musicRepository.clearLocalPlaybackAssociation(
                platform = sanitizedPlatform,
                id = sanitizedItemId,
            )
            val durationMs = elapsedMs(startedAt)
            MfLog.detail(
                category = LogCategory.DATA,
                event = "settings_song_cache_clear",
                fields = mapOf(
                    "platform" to sanitizedPlatform,
                    "itemId" to sanitizedItemId,
                    "localAssociationCleared" to localAssociationCleared,
                    "durationMs" to durationMs,
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
            SongCacheClearResult(
                platform = sanitizedPlatform,
                itemId = sanitizedItemId,
                localAssociationCleared = localAssociationCleared,
                durationMs = durationMs,
            )
        } catch (error: CancellationException) {
            MfLog.detail(
                category = LogCategory.DATA,
                event = "settings_song_cache_clear",
                fields = mapOf(
                    "platform" to sanitizedPlatform,
                    "itemId" to sanitizedItemId,
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.CANCELLED,
                    "reason" to LogFields.Reason.CANCELLED,
                ),
            )
            throw error
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.DATA,
                event = "settings_song_cache_clear",
                throwable = error,
                fields = mapOf(
                    "platform" to sanitizedPlatform,
                    "itemId" to sanitizedItemId,
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.FAILURE,
                    "reason" to "exception",
                ),
            )
            throw error
        }
    }

    suspend fun clearImageCache() = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        try {
            val imageLoader = context.imageLoader
            val diskSizeBefore = imageLoader.diskCache?.size
            val memorySizeBefore = imageLoader.memoryCache?.size
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
            MfLog.detail(
                category = LogCategory.DATA,
                event = "clear_image_cache",
                fields = mapOf(
                    "diskSizeBytes" to diskSizeBefore,
                    "memorySizeBytes" to memorySizeBefore,
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.DATA,
                event = "clear_image_cache",
                throwable = error,
                fields = mapOf(
                    "durationMs" to elapsedMs(startedAt),
                    "result" to LogFields.Result.FAILURE,
                    "reason" to "exception",
                ),
            )
            throw error
        }
    }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
}

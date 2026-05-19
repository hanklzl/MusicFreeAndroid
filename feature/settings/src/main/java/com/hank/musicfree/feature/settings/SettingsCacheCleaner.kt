package com.hank.musicfree.feature.settings

import android.content.Context
import coil3.imageLoader
import com.hank.musicfree.core.telemetry.PlayCacheTelemetry
import com.hank.musicfree.data.repository.LyricRepository
import com.hank.musicfree.data.repository.MediaCacheRepository
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.player.cache.SimpleCacheHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SettingsCacheCleaner @Inject constructor(
    private val mediaCacheRepository: MediaCacheRepository,
    private val simpleCacheHolder: SimpleCacheHolder,
    private val playCacheTelemetry: PlayCacheTelemetry,
    private val lyricRepository: LyricRepository,
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

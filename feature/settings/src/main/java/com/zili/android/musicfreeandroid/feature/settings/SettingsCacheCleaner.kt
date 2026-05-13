package com.zili.android.musicfreeandroid.feature.settings

import android.content.Context
import coil3.imageLoader
import com.zili.android.musicfreeandroid.data.repository.LyricRepository
import com.zili.android.musicfreeandroid.data.repository.MediaCacheRepository
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SettingsCacheCleaner @Inject constructor(
    private val mediaCacheRepository: MediaCacheRepository,
    private val lyricRepository: LyricRepository,
    @param:ApplicationContext private val context: Context,
) {
    suspend fun clearMusicCache() {
        mediaCacheRepository.clearAll()
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

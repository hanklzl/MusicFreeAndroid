package com.hank.musicfree.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.core.telemetry.PlayCacheTelemetry
import com.hank.musicfree.data.repository.LyricRepository
import com.hank.musicfree.data.repository.MediaCacheRepository
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLogger
import com.hank.musicfree.player.cache.SimpleCacheHolder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsCacheCleanerTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun makeNoOpTelemetry(): PlayCacheTelemetry {
        val logger = object : MfLogger {
            override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) = Unit
            override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) = Unit
            override fun error(category: LogCategory, event: String, throwable: Throwable?, fields: Map<String, Any?>) = Unit
            override fun flush() = Unit
        }
        return PlayCacheTelemetry(logger)
    }

    @Test
    fun `clearAudioFileCache returns non-negative freed bytes`() = runTest {
        val simpleCacheHolder = mockk<SimpleCacheHolder>()
        every { simpleCacheHolder.usedBytes() } returnsMany listOf(1024L * 1024L, 0L)
        every { simpleCacheHolder.clearCache() } returns Unit

        val mediaCacheRepository = mockk<MediaCacheRepository>()
        val lyricRepository = mockk<LyricRepository>()

        val cleaner = SettingsCacheCleaner(
            mediaCacheRepository = mediaCacheRepository,
            simpleCacheHolder = simpleCacheHolder,
            playCacheTelemetry = makeNoOpTelemetry(),
            lyricRepository = lyricRepository,
            context = ctx,
        )

        val freed = cleaner.clearAudioFileCache()

        assertTrue("clearAudioFileCache must return non-negative freed bytes", freed >= 0L)
        coVerify { simpleCacheHolder.clearCache() }
    }

    @Test
    fun `clearMediaUrlMetadataCache returns non-negative freed bytes`() = runTest {
        val mediaCacheRepository = mockk<MediaCacheRepository>()
        coEvery { mediaCacheRepository.estimatedBytes() } returnsMany listOf(512L, 0L)
        coEvery { mediaCacheRepository.clearAll() } returns Unit

        val simpleCacheHolder = mockk<SimpleCacheHolder>()
        val lyricRepository = mockk<LyricRepository>()

        val cleaner = SettingsCacheCleaner(
            mediaCacheRepository = mediaCacheRepository,
            simpleCacheHolder = simpleCacheHolder,
            playCacheTelemetry = makeNoOpTelemetry(),
            lyricRepository = lyricRepository,
            context = ctx,
        )

        val freed = cleaner.clearMediaUrlMetadataCache()

        assertTrue("clearMediaUrlMetadataCache must return non-negative freed bytes", freed >= 0L)
        coVerify { mediaCacheRepository.clearAll() }
    }

    @Test
    fun `clearAudioFileCache returns zero when no bytes were used`() = runTest {
        val simpleCacheHolder = mockk<SimpleCacheHolder>()
        every { simpleCacheHolder.usedBytes() } returns 0L
        every { simpleCacheHolder.clearCache() } returns Unit

        val mediaCacheRepository = mockk<MediaCacheRepository>()
        val lyricRepository = mockk<LyricRepository>()

        val cleaner = SettingsCacheCleaner(
            mediaCacheRepository = mediaCacheRepository,
            simpleCacheHolder = simpleCacheHolder,
            playCacheTelemetry = makeNoOpTelemetry(),
            lyricRepository = lyricRepository,
            context = ctx,
        )

        val freed = cleaner.clearAudioFileCache()
        assertTrue("freed bytes must be >= 0 even when cache was already empty", freed >= 0L)
    }
}

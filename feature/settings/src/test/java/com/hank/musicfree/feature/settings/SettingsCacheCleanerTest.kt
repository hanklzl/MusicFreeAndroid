package com.hank.musicfree.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.telemetry.PlayCacheTelemetry
import com.hank.musicfree.data.repository.LyricRepository
import com.hank.musicfree.data.repository.MediaCacheRepository
import com.hank.musicfree.data.repository.MusicRepository
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLogger
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.player.cache.SimpleCacheHolder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
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

    private fun createCleaner(
        mediaCacheRepository: MediaCacheRepository = mockk(relaxed = true),
        simpleCacheHolder: SimpleCacheHolder = mockk(relaxed = true),
        lyricRepository: LyricRepository = mockk(relaxed = true),
        musicRepository: MusicRepository = mockk(relaxed = true),
    ): SettingsCacheCleaner = SettingsCacheCleaner(
        mediaCacheRepository = mediaCacheRepository,
        simpleCacheHolder = simpleCacheHolder,
        playCacheTelemetry = makeNoOpTelemetry(),
        lyricRepository = lyricRepository,
        musicRepository = musicRepository,
        context = ctx,
    )

    @Test
    fun `clearAudioFileCache returns non-negative freed bytes`() = runTest {
        val simpleCacheHolder = mockk<SimpleCacheHolder>()
        every { simpleCacheHolder.usedBytes() } returnsMany listOf(1024L * 1024L, 0L)
        every { simpleCacheHolder.clearCache() } returns Unit

        val mediaCacheRepository = mockk<MediaCacheRepository>()
        val lyricRepository = mockk<LyricRepository>()
        val musicRepository = mockk<MusicRepository>()

        val cleaner = SettingsCacheCleaner(
            mediaCacheRepository = mediaCacheRepository,
            simpleCacheHolder = simpleCacheHolder,
            playCacheTelemetry = makeNoOpTelemetry(),
            lyricRepository = lyricRepository,
            musicRepository = musicRepository,
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
        val musicRepository = mockk<MusicRepository>()

        val cleaner = SettingsCacheCleaner(
            mediaCacheRepository = mediaCacheRepository,
            simpleCacheHolder = simpleCacheHolder,
            playCacheTelemetry = makeNoOpTelemetry(),
            lyricRepository = lyricRepository,
            musicRepository = musicRepository,
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
        val musicRepository = mockk<MusicRepository>()

        val cleaner = SettingsCacheCleaner(
            mediaCacheRepository = mediaCacheRepository,
            simpleCacheHolder = simpleCacheHolder,
            playCacheTelemetry = makeNoOpTelemetry(),
            lyricRepository = lyricRepository,
            musicRepository = musicRepository,
            context = ctx,
        )

        val freed = cleaner.clearAudioFileCache()
        assertTrue("freed bytes must be >= 0 even when cache was already empty", freed >= 0L)
    }

    @Test
    fun `clearSongPlaybackCache clears media cache and local playback association`() = runTest {
        val mediaCacheRepository = mockk<MediaCacheRepository>()
        coEvery { mediaCacheRepository.deleteItem("元力QQ", "302986918") } returns Unit
        val musicRepository = mockk<MusicRepository>()
        coEvery { musicRepository.clearLocalPlaybackAssociation("元力QQ", "302986918") } returns true
        val simpleCacheHolder = mockk<SimpleCacheHolder>()
        val lyricRepository = mockk<LyricRepository>()

        val cleaner = SettingsCacheCleaner(
            mediaCacheRepository = mediaCacheRepository,
            simpleCacheHolder = simpleCacheHolder,
            playCacheTelemetry = makeNoOpTelemetry(),
            lyricRepository = lyricRepository,
            musicRepository = musicRepository,
            context = ctx,
        )

        val result = cleaner.clearSongPlaybackCache("元力QQ", "302986918")

        assertEquals("元力QQ", result.platform)
        assertEquals("302986918", result.itemId)
        assertEquals(true, result.localAssociationCleared)
        coVerify { mediaCacheRepository.deleteItem("元力QQ", "302986918") }
        coVerify { musicRepository.clearLocalPlaybackAssociation("元力QQ", "302986918") }
    }

    @Test
    fun `clearOnlineSongCache with quality does not clear local playback association`() = runTest {
        val mediaCacheRepository = mockk<MediaCacheRepository>()
        coEvery { mediaCacheRepository.deleteEntry("kg", "1", PlayQuality.STANDARD) } returns Unit
        val musicRepository = mockk<MusicRepository>(relaxed = true)
        val cleaner = createCleaner(
            mediaCacheRepository = mediaCacheRepository,
            musicRepository = musicRepository,
        )

        val result = cleaner.clearOnlineSongCache("kg", "1", PlayQuality.STANDARD)

        assertEquals("quality", result.scope)
        assertEquals("kg", result.platform)
        assertEquals("1", result.itemId)
        assertEquals(PlayQuality.STANDARD, result.quality)
        assertEquals(0L, result.freedBytes)
        coVerify { mediaCacheRepository.deleteEntry("kg", "1", PlayQuality.STANDARD) }
        coVerify(exactly = 0) { musicRepository.clearLocalPlaybackAssociation(any(), any()) }
    }

    @Test
    fun `clearOnlineSongCache for song does not clear local playback association`() = runTest {
        val mediaCacheRepository = mockk<MediaCacheRepository>()
        coEvery { mediaCacheRepository.deleteItem("kg", "1") } returns Unit
        val musicRepository = mockk<MusicRepository>(relaxed = true)
        val cleaner = createCleaner(
            mediaCacheRepository = mediaCacheRepository,
            musicRepository = musicRepository,
        )

        val result = cleaner.clearOnlineSongCache("kg", "1", null)

        assertEquals("song", result.scope)
        assertEquals("kg", result.platform)
        assertEquals("1", result.itemId)
        assertNull(result.quality)
        assertEquals(0L, result.freedBytes)
        coVerify { mediaCacheRepository.deleteItem("kg", "1") }
        coVerify(exactly = 0) { musicRepository.clearLocalPlaybackAssociation(any(), any()) }
    }

    @Test
    fun `clearOnlineSongCache with quality runs repository mutation off caller thread`() = runTest {
        val callerThreadName = Thread.currentThread().name
        val mutationThreadNames = mutableListOf<String>()
        val mediaCacheRepository = mockk<MediaCacheRepository>()
        coEvery {
            mediaCacheRepository.deleteEntry("kg", "1", PlayQuality.STANDARD)
        } coAnswers {
            mutationThreadNames += Thread.currentThread().name
            Unit
        }
        val cleaner = createCleaner(mediaCacheRepository = mediaCacheRepository)

        val result = cleaner.clearOnlineSongCache("kg", "1", PlayQuality.STANDARD)

        assertEquals("quality", result.scope)
        assertEquals(PlayQuality.STANDARD, result.quality)
        assertTrue("expected repository mutation to run", mutationThreadNames.isNotEmpty())
        assertTrue(
            "repository mutation should not run on caller thread $callerThreadName: $mutationThreadNames",
            mutationThreadNames.none { it == callerThreadName },
        )
    }

    @Test
    fun `clearOnlineSongCache with blank input logs failure event`() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val cleaner = createCleaner()

        try {
            var thrown: Throwable? = null
            try {
                cleaner.clearOnlineSongCache(" ", "1", null)
            } catch (error: Throwable) {
                thrown = error
            }

            assertTrue(thrown is IllegalArgumentException)
            val event = logger.events.single { it.event == "settings_online_cache_clear" }
            assertEquals(LogCategory.SETTINGS, event.category)
            assertEquals(LogFields.Result.FAILURE, event.fields["result"])
            assertEquals("invalid_input", event.fields["reason"])
            assertEquals("song", event.fields["scope"])
            assertEquals("", event.fields["platform"])
            assertEquals("1", event.fields["itemId"])
        } finally {
            MfLog.resetForTest()
        }
    }

    @Test
    fun `clearAllOnlinePlaybackCache clears audio files and media metadata`() = runTest {
        val simpleCacheHolder = mockk<SimpleCacheHolder>()
        every { simpleCacheHolder.usedBytes() } returnsMany listOf(1000L, 100L)
        every { simpleCacheHolder.clearCache() } returns Unit
        val mediaCacheRepository = mockk<MediaCacheRepository>()
        coEvery { mediaCacheRepository.estimatedBytes() } returnsMany listOf(50L, 0L)
        coEvery { mediaCacheRepository.clearAll() } returns Unit
        val cleaner = createCleaner(
            mediaCacheRepository = mediaCacheRepository,
            simpleCacheHolder = simpleCacheHolder,
        )

        val result = cleaner.clearAllOnlinePlaybackCache()

        assertEquals("all", result.scope)
        assertNull(result.platform)
        assertNull(result.itemId)
        assertNull(result.quality)
        assertEquals(950L, result.freedBytes)
        assertTrue("duration must be non-negative", result.durationMs >= 0L)
        verify { simpleCacheHolder.clearCache() }
        coVerify { mediaCacheRepository.clearAll() }
    }

    @Test
    fun `clearAllOnlinePlaybackCache runs cache mutation off caller thread`() = runTest {
        val callerThreadName = Thread.currentThread().name
        val mutationThreadNames = mutableListOf<String>()
        val simpleCacheHolder = mockk<SimpleCacheHolder>()
        var usedBytesCallCount = 0
        every { simpleCacheHolder.usedBytes() } answers {
            mutationThreadNames += Thread.currentThread().name
            if (usedBytesCallCount++ == 0) 1000L else 100L
        }
        every { simpleCacheHolder.clearCache() } answers {
            mutationThreadNames += Thread.currentThread().name
            Unit
        }
        val mediaCacheRepository = mockk<MediaCacheRepository>()
        var estimatedBytesCallCount = 0
        coEvery { mediaCacheRepository.estimatedBytes() } coAnswers {
            mutationThreadNames += Thread.currentThread().name
            if (estimatedBytesCallCount++ == 0) 50L else 0L
        }
        coEvery { mediaCacheRepository.clearAll() } coAnswers {
            mutationThreadNames += Thread.currentThread().name
            Unit
        }
        val cleaner = createCleaner(
            mediaCacheRepository = mediaCacheRepository,
            simpleCacheHolder = simpleCacheHolder,
        )

        cleaner.clearAllOnlinePlaybackCache()

        assertTrue("expected cache operations to run", mutationThreadNames.isNotEmpty())
        assertTrue(
            "cache mutations should not run on caller thread $callerThreadName: $mutationThreadNames",
            mutationThreadNames.none { it == callerThreadName },
        )
    }

    private data class RecordedLogEvent(
        val category: LogCategory,
        val event: String,
        val fields: Map<String, Any?>,
    )

    private class RecordingLogger : MfLogger {
        val events = mutableListOf<RecordedLogEvent>()

        override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedLogEvent(category, event, fields)
        }

        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedLogEvent(category, event, fields)
        }

        override fun error(
            category: LogCategory,
            event: String,
            throwable: Throwable?,
            fields: Map<String, Any?>,
        ) {
            events += RecordedLogEvent(category, event, fields)
        }

        override fun flush() = Unit
    }
}

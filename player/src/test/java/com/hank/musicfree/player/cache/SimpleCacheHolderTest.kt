package com.hank.musicfree.player.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.core.cache.ByteCacheInvalidReason
import com.hank.musicfree.core.cache.ByteCacheKey
import com.hank.musicfree.core.cache.ByteCacheStatus
import com.hank.musicfree.core.cache.ByteCacheStatusStore
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.telemetry.PlayCacheTelemetry
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.logging.MfLogger
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SimpleCacheHolderTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var mockPrefs: AppPreferences
    private lateinit var telemetry: PlayCacheTelemetry
    private lateinit var byteCacheStatusStore: RecordingByteCacheStatusStore
    private lateinit var holder: SimpleCacheHolder

    @Before
    fun setup() {
        mockPrefs = mock()
        whenever(mockPrefs.mediaCacheSchemaVersion).thenReturn(flowOf(1))
        whenever(mockPrefs.maxMusicCacheSizeBytes).thenReturn(flowOf(SimpleCacheHolder.DEFAULT_BYTES))
        val noOpLogger = object : MfLogger {
            override fun trace(category: com.hank.musicfree.logging.LogCategory, event: String, fields: Map<String, Any?>) = Unit
            override fun detail(category: com.hank.musicfree.logging.LogCategory, event: String, fields: Map<String, Any?>) = Unit
            override fun error(category: com.hank.musicfree.logging.LogCategory, event: String, throwable: Throwable?, fields: Map<String, Any?>) = Unit
            override fun flush() = Unit
        }
        telemetry = PlayCacheTelemetry(noOpLogger)
        byteCacheStatusStore = RecordingByteCacheStatusStore()
        holder = SimpleCacheHolder(ctx, mockPrefs, telemetry, byteCacheStatusStore)
    }

    @After fun teardown() { holder.resetForClear() }

    @Test fun lazy_creates_cache_on_first_access() {
        assertNotNull(holder.current)
    }

    @Test fun resetForClear_replaces_instance() {
        val first = holder.current
        val second = holder.resetForClear()
        assertTrue(first !== second)
    }

    @Test fun resetForClear_deletes_byte_cache_statuses() {
        holder.current

        holder.resetForClear()

        assertTrue(byteCacheStatusStore.deleteAllCount > 0)
    }

    @Test fun evictForKey_deletes_matching_byte_cache_status() {
        holder.evictForKey("kg", "1", PlayQuality.STANDARD)

        assertTrue(byteCacheStatusStore.deletedKeys.contains(ByteCacheKey("kg", "1", PlayQuality.STANDARD)))
    }

    private class RecordingByteCacheStatusStore : ByteCacheStatusStore {
        var deleteAllCount = 0
        val deletedKeys = mutableListOf<ByteCacheKey>()

        override suspend fun get(key: ByteCacheKey): ByteCacheStatus? = null
        override suspend fun upsert(status: ByteCacheStatus) = Unit
        override suspend fun markInvalid(
            key: ByteCacheKey,
            reason: ByteCacheInvalidReason,
            updatedAt: Long,
        ) = Unit
        override suspend fun delete(key: ByteCacheKey) {
            deletedKeys += key
        }
        override suspend fun deleteBySong(platform: String, musicId: String) = Unit
        override suspend fun deleteAll() {
            deleteAllCount += 1
        }
    }
}

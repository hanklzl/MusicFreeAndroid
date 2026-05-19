package com.hank.musicfree.player.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.core.telemetry.PlayCacheTelemetry
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLogger
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke tests for dynamic capacity changes in [SimpleCacheHolder].
 *
 * Low-space simulation (where File.usableSpace is mocked) is deferred to Task 8
 * instrumentation tests where a real filesystem can be controlled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SimpleCacheHolderCapacityTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var mockPrefs: AppPreferences
    private lateinit var telemetry: PlayCacheTelemetry
    private lateinit var holder: SimpleCacheHolder

    private val budgetBytes = 256L * 1024 * 1024 // 256 MB test budget

    @Before
    fun setup() {
        mockPrefs = mock()
        whenever(mockPrefs.mediaCacheSchemaVersion).thenReturn(flowOf(1))
        whenever(mockPrefs.maxMusicCacheSizeBytes).thenReturn(flowOf(budgetBytes))

        val noOpLogger = object : MfLogger {
            override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) = Unit
            override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) = Unit
            override fun error(category: LogCategory, event: String, throwable: Throwable?, fields: Map<String, Any?>) = Unit
            override fun flush() = Unit
        }
        telemetry = PlayCacheTelemetry(noOpLogger)
        holder = SimpleCacheHolder(ctx, mockPrefs, telemetry)
    }

    @After
    fun teardown() {
        holder.resetForClear()
    }

    @Test
    fun `holder creates cache successfully with configured budget`() {
        assertNotNull("SimpleCache should be created for the configured budget", holder.current)
    }

    @Test
    fun `usedBytes returns zero for a freshly created cache`() {
        val used = holder.usedBytes()
        assertEquals("Empty cache should report 0 used bytes", 0L, used)
    }

    @Test
    fun `updateMaxBytes is a no-op when budget is larger than used space`() {
        // Fresh cache: 0 bytes used. Calling updateMaxBytes with any positive value
        // must not release/recreate the cache (used <= newBytes condition).
        val cacheBefore = holder.current
        holder.updateMaxBytes(budgetBytes * 2)
        val cacheAfter = holder.current
        // Both references should be the same instance (no recreate happened)
        assertTrue(
            "Cache instance should not be recreated when new budget > used bytes",
            cacheBefore === cacheAfter,
        )
    }

    @Test
    fun `updateMaxBytes does not throw when cache is null`() {
        // Create a holder where init will produce a null current (simulate disabled state
        // by calling resetForClear first so ref is null, then updateMaxBytes on an unaccessed holder).
        val freshHolder = SimpleCacheHolder(ctx, mockPrefs, telemetry)
        // Do NOT call freshHolder.current — ref stays null.
        // updateMaxBytes should handle null current gracefully.
        try {
            freshHolder.updateMaxBytes(1L)
        } finally {
            freshHolder.resetForClear()
        }
    }
}

package com.hank.musicfree.player.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
        holder = SimpleCacheHolder(ctx, mockPrefs, telemetry)
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
}

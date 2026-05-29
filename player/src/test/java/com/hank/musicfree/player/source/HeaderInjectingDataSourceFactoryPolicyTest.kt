package com.hank.musicfree.player.source

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.core.media.MediaSourceCachePolicy
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.telemetry.CurrentSidProvider
import com.hank.musicfree.core.telemetry.PlayCacheTelemetry
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLogger
import com.hank.musicfree.player.cache.CacheDataSourceEventBridge
import com.hank.musicfree.player.cache.SimpleCacheHolder
import kotlinx.coroutines.flow.flowOf
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@UnstableApi
class HeaderInjectingDataSourceFactoryPolicyTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var registry: TrackHeaderRegistry
    private lateinit var holder: SimpleCacheHolder
    private lateinit var factory: HeaderInjectingDataSourceFactory

    @Before fun setup() {
        registry = TrackHeaderRegistry()
        val mockPrefs = mock<AppPreferences>()
        whenever(mockPrefs.mediaCacheSchemaVersion).thenReturn(flowOf(1))
        val logger = object : MfLogger {
            override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) = Unit
            override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) = Unit
            override fun error(category: LogCategory, event: String, throwable: Throwable?, fields: Map<String, Any?>) = Unit
            override fun flush() = Unit
        }
        holder = SimpleCacheHolder(ctx, mockPrefs, PlayCacheTelemetry(logger))
        factory = HeaderInjectingDataSourceFactory(
            context = ctx,
            okHttpClient = OkHttpClient(),
            registry = registry,
            simpleCacheHolder = holder,
            eventBridge = CacheDataSourceEventBridge(PlayCacheTelemetry(logger), CurrentSidProvider()),
        )
    }

    @After fun teardown() {
        holder.resetForClear()
    }

    @Test fun `non-http uri is never cached even when registry has no match`() {
        val decision = factory.resolveOpenDecision(
            DataSpec.Builder().setUri("file:///tmp/song.mp3").build(),
        )
        assertEquals(false, decision.useCache)
        assertEquals("file:///tmp/song.mp3", decision.cacheKey)
        assertNull(decision.cacheBypassReason)
    }

    @Test fun `cache miss http uri can use cache when cache is available`() {
        val uri = "https://cdn.example.com/miss.mp3"
        val decision = factory.resolveOpenDecision(
            DataSpec.Builder().setUri(uri).build(),
        )
        assertEquals(holder.current != null, decision.useCache)
        assertEquals(uri, decision.cacheKey)
        assertNull(decision.cacheBypassReason)
    }

    @Test fun `byteCacheAllowed false forces uncached path with no_store bypass reason`() {
        val uri = "https://cdn.example.com/no-store.mp3"
        registry.put(
            url = uri,
            headers = mapOf("Referer" to "r"),
            userAgent = "UA",
            cacheKey = "policy:nostore",
            quality = PlayQuality.HIGH,
            byteCacheAllowed = false,
            cachePolicy = MediaSourceCachePolicy.NoStore,
        )

        val decision = factory.resolveOpenDecision(
            DataSpec.Builder().setUri(uri).build(),
        )
        assertEquals(false, decision.useCache)
        assertEquals("policy:nostore:high", decision.cacheKey)
        assertEquals("no_store", decision.cacheBypassReason)
    }

    @Test fun `byteCacheAllowed true uses cached path when cache is available`() {
        val uri = "https://cdn.example.com/allow-cache.mp3"
        registry.put(
            url = uri,
            headers = mapOf("Referer" to "r"),
            userAgent = "UA",
            cacheKey = "policy:cached",
            quality = PlayQuality.LOW,
            byteCacheAllowed = true,
            cachePolicy = MediaSourceCachePolicy.Cache,
        )

        val decision = factory.resolveOpenDecision(
            DataSpec.Builder().setUri(uri).build(),
        )
        assertEquals(holder.current != null, decision.useCache)
        assertEquals("policy:cached:low", decision.cacheKey)
        assertNull(decision.cacheBypassReason)
    }

    @Test fun `open failure emits one close event and allows reopen cleanly`() {
        val logger = RecordingLogger()
        val sidProvider = CurrentSidProvider()
        sidProvider.newSession()
        val bridge = CacheDataSourceEventBridge(PlayCacheTelemetry(logger), sidProvider)
        val dataSpec = DataSpec.Builder().setUri("https://cdn.example.com/retry.mp3").build()
        val delegates = mutableListOf<RecordingDataSource>()
        var attempt = 0

        val source = HeaderInjectingDataSourceFactory.InstrumentedSource { _ ->
            attempt++
            val delegate = if (attempt == 1) {
                RecordingDataSource(openFailure = IOException("open failed"), closeFailure = IOException("close failed"))
            } else {
                RecordingDataSource(openResult = 123L)
            }
            delegates += delegate
            Pair(delegate, bridge.newSession("policy:retry:$attempt"))
        }

        val openFailure = assertThrows(IOException::class.java) {
            source.open(dataSpec)
        }
        assertEquals("open failed", openFailure.message)
        assertEquals(1, logger.entryCount("media3_datasource_close"))
        assertEquals(1, delegates[0].closeCount)
        assertEquals(1, openFailure.suppressed.size)
        assertEquals("close failed", openFailure.suppressed.first().message)

        assertEquals(123L, source.open(dataSpec))
        source.close()
        assertEquals(2, logger.entryCount("media3_datasource_close"))
        assertEquals(1, delegates[1].closeCount)
    }

    @Test fun `close after successful open emits one close event even if close called twice`() {
        val logger = RecordingLogger()
        val sidProvider = CurrentSidProvider()
        sidProvider.newSession()
        val bridge = CacheDataSourceEventBridge(PlayCacheTelemetry(logger), sidProvider)
        val dataSpec = DataSpec.Builder().setUri("https://cdn.example.com/success-close.mp3").build()
        val delegate = RecordingDataSource(openResult = 123L)
        val source = HeaderInjectingDataSourceFactory.InstrumentedSource { _ ->
            Pair(delegate, bridge.newSession("policy:close-once"))
        }

        source.open(dataSpec)
        source.close()
        source.close()

        assertEquals(1, logger.entryCount("media3_datasource_close"))
        assertEquals(1, delegate.closeCount)
    }

    @Test fun `transferListener added before open is forwarded to created delegate`() {
        val logger = RecordingLogger()
        val sidProvider = CurrentSidProvider()
        sidProvider.newSession()
        val bridge = CacheDataSourceEventBridge(PlayCacheTelemetry(logger), sidProvider)
        val dataSpec = DataSpec.Builder().setUri("https://cdn.example.com/transfer.mp3").build()
        lateinit var delegate: RecordingDataSource
        val source = HeaderInjectingDataSourceFactory.InstrumentedSource { _ ->
            delegate = RecordingDataSource(openResult = 1L)
            Pair(delegate, bridge.newSession("policy:transfer"))
        }

        val listener = object : TransferListener {
            override fun onTransferInitializing(dataSource: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
            override fun onTransferStart(dataSource: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
            override fun onBytesTransferred(dataSource: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) = Unit
            override fun onTransferEnd(dataSource: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
        }
        source.addTransferListener(listener)
        source.open(dataSpec)

        assertEquals(1, delegate.listeners.size)
        assertEquals(listener, delegate.listeners[0])
    }

    private class RecordingLogger : MfLogger {
        data class Entry(val event: String, val fields: Map<String, Any?>)

        val entries = mutableListOf<Entry>()

        fun entryCount(event: String): Int = entries.count { it.event == event }

        override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
            entries += Entry(event, fields)
        }

        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
            entries += Entry(event, fields)
        }

        override fun error(category: LogCategory, event: String, throwable: Throwable?, fields: Map<String, Any?>) {
            entries += Entry(event, fields)
        }

        override fun flush() = Unit
    }

    private class RecordingDataSource(
        private val openResult: Long = -1L,
        private val openFailure: IOException? = null,
        private val closeFailure: IOException? = null,
    ) : DataSource {
        val listeners = mutableListOf<TransferListener>()
        var openCount = 0
        var closeCount = 0

        override fun addTransferListener(transferListener: TransferListener) {
            listeners += transferListener
        }

        override fun open(dataSpec: DataSpec): Long {
            openCount++
            openFailure?.let { throw it }
            return openResult
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = -1
        override fun getUri() = null
        override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()

        override fun close() {
            closeCount++
            closeFailure?.let { throw it }
        }
    }
}

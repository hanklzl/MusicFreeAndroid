package com.hank.musicfree.player.cache

import android.content.Context
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.core.cache.ByteCacheKey
import com.hank.musicfree.core.cache.ByteCacheValidity
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@AndroidXOptIn(markerClass = [UnstableApi::class])
class ByteCacheInspectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var cache: SimpleCache
    private lateinit var holder: SimpleCacheHolder
    private lateinit var logger: RecordingLogger

    private val key = ByteCacheKey(
        platform = "qq",
        musicId = "song-1",
        quality = PlayQuality.STANDARD,
    )

    @Before
    fun setUp() {
        cache = SimpleCache(
            tempFolder.newFolder("byte-cache-inspector"),
            NoOpCacheEvictor(),
            StandaloneDatabaseProvider(context),
        )
        holder = mock()
        whenever(holder.current).thenReturn(cache)
        logger = RecordingLogger()
        MfLog.install(logger)
    }

    @After
    fun tearDown() {
        MfLog.resetForTest()
        cache.release()
    }

    @Test
    fun `inspect returns none when cache has no spans`() {
        val inspection = ByteCacheInspector(holder).inspect(key)

        assertEquals(ByteCacheValidity.None, inspection.validity)
        assertEquals(0L, inspection.cachedBytes)
        assertNull(inspection.contentLength)
        assertEquals(0, inspection.holeCount)

        val logEvent = logger.events.single()
        assertEquals("byte_cache_inspect", logEvent.event)
        assertEquals("none", logEvent.fields["status"])
        assertEquals(0L, logEvent.fields["cachedBytes"])
        assertEquals(0, logEvent.fields["holeCount"])
        assertEquals(key.stableKey, logEvent.fields["cacheKey"])
    }

    @Test
    fun `inspect returns partial when only prefetched head is cached`() {
        writeSpan(key.stableKey, position = 0L, length = 128)
        setContentLength(key.stableKey, 512L)

        val inspection = ByteCacheInspector(holder).inspect(key)

        assertEquals(ByteCacheValidity.Partial, inspection.validity)
        assertEquals(128L, inspection.cachedBytes)
        assertEquals(512L, inspection.contentLength)
        assertEquals(0, inspection.holeCount)
    }

    @Test
    fun `inspect returns partial when cached spans contain a hole`() {
        writeSpan(key.stableKey, position = 0L, length = 128)
        writeSpan(key.stableKey, position = 256L, length = 128)
        setContentLength(key.stableKey, 384L)

        val inspection = ByteCacheInspector(holder).inspect(key)

        assertEquals(ByteCacheValidity.Partial, inspection.validity)
        assertEquals(256L, inspection.cachedBytes)
        assertEquals(384L, inspection.contentLength)
        assertEquals(1, inspection.holeCount)
    }

    @Test
    fun `inspect returns complete when spans continuously cover content length`() {
        writeSpan(key.stableKey, position = 0L, length = 128)
        writeSpan(key.stableKey, position = 128L, length = 128)
        writeSpan(key.stableKey, position = 256L, length = 128)
        setContentLength(key.stableKey, 384L)

        val inspection = ByteCacheInspector(holder).inspect(key)

        assertEquals(ByteCacheValidity.Complete, inspection.validity)
        assertEquals(384L, inspection.cachedBytes)
        assertEquals(384L, inspection.contentLength)
        assertEquals(0, inspection.holeCount)
    }

    @Test
    fun `inspect returns partial when content length is missing`() {
        writeSpan(key.stableKey, position = 0L, length = 256)

        val inspection = ByteCacheInspector(holder).inspect(key)

        assertEquals(ByteCacheValidity.Partial, inspection.validity)
        assertEquals(256L, inspection.cachedBytes)
        assertNull(inspection.contentLength)
        assertEquals(0, inspection.holeCount)
    }

    private fun writeSpan(cacheKey: String, position: Long, length: Int) {
        val hole = cache.startReadWrite(cacheKey, position, length.toLong())
        val file = cache.startFile(cacheKey, position, length.toLong())
        FileOutputStream(file).use { output ->
            output.write(ByteArray(length) { 1 })
        }
        cache.commitFile(file, length.toLong())
        cache.releaseHoleSpan(hole)
    }

    private fun setContentLength(cacheKey: String, contentLength: Long) {
        val mutations = ContentMetadataMutations()
        ContentMetadataMutations.setContentLength(mutations, contentLength)
        cache.applyContentMetadataMutations(cacheKey, mutations)
    }

    private data class RecordedEvent(
        val event: String,
        val fields: Map<String, Any?>,
        val category: LogCategory,
    )

    private class RecordingLogger : MfLogger {
        val events = mutableListOf<RecordedEvent>()

        override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) = Unit

        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedEvent(event = event, fields = fields, category = category)
        }

        override fun error(
            category: LogCategory,
            event: String,
            throwable: Throwable?,
            fields: Map<String, Any?>,
        ) {
            events += RecordedEvent(event = event, fields = fields, category = category)
        }

        override fun flush() = Unit
    }
}

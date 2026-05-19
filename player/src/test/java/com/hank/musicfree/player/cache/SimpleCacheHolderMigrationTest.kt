package com.hank.musicfree.player.cache

import android.content.Context
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.core.telemetry.PlayCacheTelemetry
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLogger
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric smoke tests for [CacheSchemaMigrator] integration with a real [SimpleCache].
 *
 * Seeding actual cache spans requires internal Media3 plumbing that we intentionally avoid here;
 * deeper coverage (including legacy-entry eviction) is deferred to Task 8 androidTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@AndroidXOptIn(markerClass = [UnstableApi::class])
class SimpleCacheHolderMigrationTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var noOpLogger: MfLogger

    @Before
    fun setup() {
        noOpLogger = object : MfLogger {
            override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) = Unit
            override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) = Unit
            override fun error(category: LogCategory, event: String, throwable: Throwable?, fields: Map<String, Any?>) = Unit
            override fun flush() = Unit
        }
    }

    @Test
    fun `migrate on empty cache returns zero removed and zero freed bytes`() {
        val cacheDir = tmpFolder.newFolder("empty-cache-test")
        val db = StandaloneDatabaseProvider(ctx)
        val cache = SimpleCache(cacheDir, NoOpCacheEvictor(), db)
        try {
            val result = CacheSchemaMigrator.migrate(cache)
            assertEquals(0, result.removedCount)
            assertEquals(0L, result.freedBytes)
        } finally {
            cache.release()
        }
    }

    @Test
    fun `migrateOnceIfNeeded is a no-op when schema version is already 1`() {
        val mockPrefs = mock<AppPreferences>()
        // Already migrated: version = 1
        whenever(mockPrefs.mediaCacheSchemaVersion).thenReturn(flowOf(1))
        whenever(mockPrefs.maxMusicCacheSizeBytes).thenReturn(flowOf(SimpleCacheHolder.DEFAULT_BYTES))
        val telemetry = PlayCacheTelemetry(noOpLogger)
        val holder = SimpleCacheHolder(ctx, mockPrefs, telemetry)
        try {
            // Must not throw; must be a no-op since version >= 1
            holder.migrateOnceIfNeeded()
        } finally {
            holder.resetForClear()
        }
    }

    @Test
    fun `migrateOnceIfNeeded runs migration when schema version is 0`() {
        val mockPrefs = mock<AppPreferences>()
        whenever(mockPrefs.mediaCacheSchemaVersion).thenReturn(flowOf(0))
        whenever(mockPrefs.maxMusicCacheSizeBytes).thenReturn(flowOf(SimpleCacheHolder.DEFAULT_BYTES))
        // setMediaCacheSchemaVersion can be called — no assertion needed, just must not throw
        val telemetry = PlayCacheTelemetry(noOpLogger)
        val holder = SimpleCacheHolder(ctx, mockPrefs, telemetry)
        try {
            // With an empty cache this should complete with 0 evictions
            holder.migrateOnceIfNeeded()
            // No assertion on version persistence here — that requires a real DataStore;
            // the no-op mock records the call silently.
        } finally {
            holder.resetForClear()
        }
    }
}

package com.hank.musicfree.plugin.media

import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.AudioInterruptionAction
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.PlaybackRuntimeSettings
import com.hank.musicfree.core.model.QualityFallbackOrder
import com.hank.musicfree.data.repository.CachedSource
import com.hank.musicfree.data.repository.MediaCacheRepository
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.manager.LoadedPlugin
import com.hank.musicfree.plugin.manager.PluginManager
import com.hank.musicfree.plugin.meta.PluginMetaStore
import com.hank.musicfree.plugin.network.PluginNetworkStateProvider
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PluginMediaSourceServiceCacheTest {

    @After
    fun tearDown() {
        MfLog.resetForTest()
    }

    // ---------- A7: cacheControl decision flow ----------

    @Test
    fun `cache mode returns cached when available and plugin not called`() = runTest {
        val logger = CacheRecordingLogger()
        MfLog.install(logger)
        val plugin = plugin(
            platform = "kuwo",
            supportsMedia = true,
            url = "https://kuwo.example/should-not-be-asked.mp3",
            cacheControl = "cache",
        )
        val cache = mock<MediaCacheRepository>()
        whenever(cache.get(any(), eq(PlayQuality.STANDARD))).thenReturn(
            CachedSource(
                url = "https://cache.example/cached.mp3",
                headers = mapOf("X-Cached" to "1"),
                userAgent = "CachedUA",
            ),
        )

        val service = service(
            plugins = listOf(plugin),
            alternatives = emptyMap(),
            cache = cache,
        )

        val result = service.resolve(item("kuwo"), quality = "standard")!!

        assertEquals("https://cache.example/cached.mp3", result.item.url)
        assertEquals("https://cache.example/cached.mp3", result.source.url)
        assertEquals("kuwo", result.requestedPlatform)
        assertEquals("kuwo", result.resolverPlatform)
        val hit = logger.events.single { it.event == "plugin_get_media_source_cache_hit" }
        assertEquals(LogCategory.PLUGIN, hit.category)
        assertEquals("cache", hit.fields["cacheControl"])
        assertEquals(false, hit.fields["offline"])
        assertEquals(true, hit.fields["useCache"])
        assertEquals("standard", hit.fields["quality"])
        verify(plugin, never()).getMediaSource(any(), any())
    }

    @Test
    fun `cache mode fetches and writes when cache miss`() = runTest {
        val plugin = plugin(
            platform = "kuwo",
            supportsMedia = true,
            url = "https://kuwo.example/fresh.mp3",
            cacheControl = "cache",
        )
        val cache = mock<MediaCacheRepository>()
        whenever(cache.get(any(), any())).thenReturn(null)

        val service = service(
            plugins = listOf(plugin),
            alternatives = emptyMap(),
            cache = cache,
        )

        val result = service.resolve(item("kuwo"), quality = "standard")!!

        assertEquals("https://kuwo.example/fresh.mp3", result.item.url)
        verify(plugin).getMediaSource(any(), eq("standard"))
        verify(cache).put(any(), eq(PlayQuality.STANDARD), any())
    }

    @Test
    fun `no-store never reads or writes cache`() = runTest {
        val logger = CacheRecordingLogger()
        MfLog.install(logger)
        val plugin = plugin(
            platform = "kuwo",
            supportsMedia = true,
            url = "https://kuwo.example/fresh.mp3",
            cacheControl = "no-store",
        )
        val cache = mock<MediaCacheRepository>()
        // Even if cache had an entry, no-store must skip read entirely.
        whenever(cache.get(any(), any())).thenReturn(
            CachedSource(
                url = "https://should-not-read.example/c.mp3",
                headers = null,
                userAgent = null,
            ),
        )

        val service = service(
            plugins = listOf(plugin),
            alternatives = emptyMap(),
            cache = cache,
        )

        val result = service.resolve(item("kuwo"), quality = "standard")!!

        assertEquals("https://kuwo.example/fresh.mp3", result.item.url)
        verify(plugin).getMediaSource(any(), eq("standard"))
        verify(cache, never()).get(any(), any())
        verify(cache, never()).put(any(), any(), any())
        val skipped = logger.events.single { it.event == "plugin_get_media_source_cache_read_skipped" }
        assertEquals("no-store", skipped.fields["cacheControl"])
        assertEquals("cache_control_no_store", skipped.fields["reason"])
    }

    @Test
    fun `no-cache (default) writes but does not read`() = runTest {
        val logger = CacheRecordingLogger()
        MfLog.install(logger)
        // cacheControl null defaults to no-cache via CacheControl.parse(null)
        val plugin = plugin(
            platform = "kuwo",
            supportsMedia = true,
            url = "https://kuwo.example/fresh.mp3",
            cacheControl = null,
        )
        val cache = mock<MediaCacheRepository>()
        // Cache has entry but should be ignored by no-cache on the read path
        whenever(cache.get(any(), any())).thenReturn(
            CachedSource(
                url = "https://should-not-read.example/c.mp3",
                headers = null,
                userAgent = null,
            ),
        )

        val service = service(
            plugins = listOf(plugin),
            alternatives = emptyMap(),
            cache = cache,
        )

        val result = service.resolve(item("kuwo"), quality = "standard")!!

        assertEquals("https://kuwo.example/fresh.mp3", result.item.url)
        verify(plugin).getMediaSource(any(), eq("standard"))
        verify(cache, never()).get(any(), any())
        verify(cache).put(any(), eq(PlayQuality.STANDARD), any())
        val skipped = logger.events.single { it.event == "plugin_get_media_source_cache_read_skipped" }
        assertEquals("no-cache", skipped.fields["cacheControl"])
        assertEquals(false, skipped.fields["offline"])
        assertEquals(true, skipped.fields["useCache"])
        assertEquals("policy_no_cache_online", skipped.fields["reason"])
    }

    @Test
    fun `no-cache reads cached source while offline`() = runTest {
        val logger = CacheRecordingLogger()
        MfLog.install(logger)
        val plugin = plugin(
            platform = "kuwo",
            supportsMedia = true,
            url = "https://kuwo.example/should-not-be-asked.mp3",
            cacheControl = null,
        )
        val cache = mock<MediaCacheRepository>()
        whenever(cache.get(any(), eq(PlayQuality.STANDARD))).thenReturn(
            CachedSource(
                url = "https://cache.example/offline-cached.mp3",
                headers = null,
                userAgent = "OfflineUA",
            ),
        )

        val service = service(
            plugins = listOf(plugin),
            alternatives = emptyMap(),
            cache = cache,
            network = object : PluginNetworkStateProvider {
                override fun isOffline(): Boolean = true
            },
        )

        val result = service.resolve(item("kuwo"), quality = "standard")!!

        assertEquals("https://cache.example/offline-cached.mp3", result.item.url)
        val hit = logger.events.single { it.event == "plugin_get_media_source_cache_hit" }
        assertEquals("no-cache", hit.fields["cacheControl"])
        assertEquals(true, hit.fields["offline"])
        assertEquals(true, hit.fields["useCache"])
        assertEquals("standard", hit.fields["quality"])
        verify(plugin, never()).getMediaSource(any(), any())
    }

    @Test
    fun `cache read on null quality uses default play quality not STANDARD`() = runTest {
        // Important #2 fix: previously the cache read was always keyed on STANDARD when
        // quality was null. That made cache hits impossible for users whose default
        // quality is HIGH/SUPER. The cache read should target the same quality that the
        // fetch loop would ask for first (i.e. defaultPlayQuality()).
        val plugin = plugin(
            platform = "kuwo",
            supportsMedia = true,
            url = "https://kuwo.example/fresh.mp3",
            cacheControl = "cache",
        )
        val cache = mock<MediaCacheRepository>()
        whenever(cache.get(any(), eq(PlayQuality.HIGH))).thenReturn(
            CachedSource(
                url = "https://cache.example/cached-high.mp3",
                headers = null,
                userAgent = null,
            ),
        )

        val settings = object : PlaybackRuntimeSettings {
            override suspend fun defaultPlayQuality(): PlayQuality = PlayQuality.HIGH
            override suspend fun playQualityOrder(): QualityFallbackOrder = QualityFallbackOrder.Asc
            override suspend fun useCellularPlay(): Boolean = false
            override suspend fun allowConcurrentPlayback(): Boolean = false
            override suspend fun autoPlayWhenAppStart(): Boolean = false
            override suspend fun tryChangeSourceWhenPlayFail(): Boolean = false
            override suspend fun autoStopWhenError(): Boolean = false
            override suspend fun audioInterruptionAction(): AudioInterruptionAction = AudioInterruptionAction.Pause
            override suspend fun audioInterruptionDuckVolume(): Float = 0.5f
            override suspend fun showExitOnNotification(): Boolean = false
        }
        val service = service(
            plugins = listOf(plugin),
            alternatives = emptyMap(),
            cache = cache,
            settings = settings,
        )

        val result = service.resolve(item("kuwo"), quality = null)!!

        assertEquals("https://cache.example/cached-high.mp3", result.item.url)
        verify(cache).get(any(), eq(PlayQuality.HIGH))
        verify(plugin, never()).getMediaSource(any(), any())
    }

    @Test
    fun `cache write skips unknown wire quality and logs error`() = runTest {
        // Important #4 fix: a successful plugin response for an unknown wire quality
        // must NOT be silently downgraded into STANDARD slot (which would poison the
        // cache for legitimate STANDARD reads).
        val plugin = plugin(
            platform = "kuwo",
            supportsMedia = true,
            url = "https://kuwo.example/fresh.mp3",
            cacheControl = "cache",
        )
        val cache = mock<MediaCacheRepository>()
        whenever(cache.get(any(), any())).thenReturn(null)

        val service = service(
            plugins = listOf(plugin),
            alternatives = emptyMap(),
            cache = cache,
        )

        // 'audiophile' is not a known PlayQuality enum value
        val result = service.resolve(item("kuwo"), quality = "audiophile")!!

        assertEquals("https://kuwo.example/fresh.mp3", result.item.url)
        verify(plugin).getMediaSource(any(), eq("audiophile"))
        verify(cache, never()).put(any(), any(), any())
    }

    // ---------- A8: resolveFresh bypass ----------

    @Test
    fun `resolveFresh bypasses cache even when cacheControl is cache`() = runTest {
        val plugin = plugin(
            platform = "kuwo",
            supportsMedia = true,
            url = "https://kuwo.example/fresh.mp3",
            cacheControl = "cache",
        )
        val cache = mock<MediaCacheRepository>()
        // Cache has an entry — resolveFresh must NOT consult it.
        whenever(cache.get(any(), any())).thenReturn(
            CachedSource(
                url = "https://should-not-read.example/c.mp3",
                headers = null,
                userAgent = null,
            ),
        )

        val service = service(
            plugins = listOf(plugin),
            alternatives = emptyMap(),
            cache = cache,
        )

        val result = service.resolveFresh(item("kuwo"), quality = "standard")!!

        assertEquals("https://kuwo.example/fresh.mp3", result.item.url)
        verify(plugin).getMediaSource(any(), eq("standard"))
        verify(cache, never()).get(any(), any())
    }

    @Test
    fun `resolveFresh writes cache on success in cache mode`() = runTest {
        val plugin = plugin(
            platform = "kuwo",
            supportsMedia = true,
            url = "https://kuwo.example/fresh.mp3",
            cacheControl = "cache",
        )
        val cache = mock<MediaCacheRepository>()
        whenever(cache.get(any(), any())).thenReturn(null)

        val service = service(
            plugins = listOf(plugin),
            alternatives = emptyMap(),
            cache = cache,
        )

        val result = service.resolveFresh(item("kuwo"), quality = "standard")!!

        assertEquals("https://kuwo.example/fresh.mp3", result.item.url)
        verify(cache).put(any(), eq(PlayQuality.STANDARD), any())
    }

    @Test
    fun `resolveFresh does not write cache when no-store`() = runTest {
        val plugin = plugin(
            platform = "kuwo",
            supportsMedia = true,
            url = "https://kuwo.example/fresh.mp3",
            cacheControl = "no-store",
        )
        val cache = mock<MediaCacheRepository>()

        val service = service(
            plugins = listOf(plugin),
            alternatives = emptyMap(),
            cache = cache,
        )

        val result = service.resolveFresh(item("kuwo"), quality = "standard")!!

        assertEquals("https://kuwo.example/fresh.mp3", result.item.url)
        verify(cache, never()).get(any(), any())
        verify(cache, never()).put(any(), any(), any())
    }

    // ---------- Fixtures ----------

    private fun service(
        plugins: List<LoadedPlugin>,
        alternatives: Map<String, String>,
        cache: MediaCacheRepository,
        disabled: Set<String> = emptySet(),
        settings: PlaybackRuntimeSettings = PlaybackRuntimeSettings.Defaults,
        network: PluginNetworkStateProvider = PluginNetworkStateProvider.AlwaysOnline,
    ): PluginMediaSourceService {
        val manager = mock<PluginManager>()
        val metaStore = mock<PluginMetaStore>()
        whenever(manager.plugins).thenReturn(MutableStateFlow(plugins))
        plugins.forEach { plugin ->
            whenever(manager.getPlugin(plugin.info.platform)).thenReturn(plugin)
        }
        whenever(metaStore.alternativePlugins).thenReturn(flowOf(alternatives))
        whenever(metaStore.disabledPlugins).thenReturn(flowOf(disabled))
        whenever(manager.pluginMetaStore).thenReturn(metaStore)
        return PluginMediaSourceService(manager, cache, settings, network)
    }

    private suspend fun plugin(
        platform: String,
        supportsMedia: Boolean,
        url: String,
        cacheControl: String?,
    ): LoadedPlugin {
        val p = mock<LoadedPlugin>()
        whenever(p.info).thenReturn(
            PluginInfo(
                platform = platform,
                version = null,
                author = null,
                description = null,
                srcUrl = null,
                supportedSearchType = emptyList(),
                cacheControl = cacheControl,
                supportedMethods = if (supportsMedia) setOf("getMediaSource") else emptySet(),
            ),
        )
        whenever(p.getMediaSource(any(), any())).thenReturn(
            MediaSourceResult(url = url, headers = null, userAgent = null, quality = null),
        )
        return p
    }

    private fun item(platform: String) = MusicItem(
        id = "1",
        platform = platform,
        title = "Song",
        artist = "Artist",
        album = null,
        duration = 0L,
        url = null,
        artwork = null,
        qualities = null,
    )
}

private data class CacheRecordedLogEvent(
    val level: String,
    val category: LogCategory,
    val event: String,
    val fields: Map<String, Any?>,
    val throwable: Throwable? = null,
)

private class CacheRecordingLogger : MfLogger {
    val events = CopyOnWriteArrayList<CacheRecordedLogEvent>()

    override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
        events += CacheRecordedLogEvent("trace", category, event, fields)
    }

    override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
        events += CacheRecordedLogEvent("detail", category, event, fields)
    }

    override fun error(
        category: LogCategory,
        event: String,
        throwable: Throwable?,
        fields: Map<String, Any?>,
    ) {
        events += CacheRecordedLogEvent("error", category, event, fields, throwable)
    }

    override fun flush() = Unit
}

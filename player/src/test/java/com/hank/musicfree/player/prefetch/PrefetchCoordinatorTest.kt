package com.hank.musicfree.player.prefetch

import com.hank.musicfree.core.cache.ByteCacheKey
import com.hank.musicfree.core.cache.ByteCacheStatus
import com.hank.musicfree.core.cache.ByteCacheStatusStore
import com.hank.musicfree.core.cache.ByteCacheValidationMethod
import com.hank.musicfree.core.cache.ByteCacheValidity
import com.hank.musicfree.core.media.MediaSourceCachePolicy
import com.hank.musicfree.core.media.MediaSourceResolution
import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.player.source.HeaderInjectingDataSourceFactory
import com.hank.musicfree.player.source.PlaybackCacheKeyRegistrar
import com.hank.musicfree.player.source.TrackHeaderRegistry
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLogger
import com.hank.musicfree.player.cache.ByteCacheInspection
import com.hank.musicfree.player.cache.ByteCacheInspector
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PrefetchCoordinatorTest {

    private val itemA = MusicItem(
        id = "a1", platform = "netease", title = "Track A",
        artist = "Artist", album = null, duration = 200_000L,
        url = null, artwork = null, qualities = null,
    )

    private val itemB = MusicItem(
        id = "b2", platform = "netease", title = "Track B",
        artist = "Artist", album = null, duration = 180_000L,
        url = null, artwork = null, qualities = null,
    )

    private val itemC = MusicItem(
        id = "c3", platform = "netease", title = "Track C",
        artist = "Artist", album = null, duration = 160_000L,
        url = null, artwork = null, qualities = null,
    )

    private fun standardResolution(
        cachePolicy: MediaSourceCachePolicy = MediaSourceCachePolicy.Cache,
        item: MusicItem = itemB,
        quality: PlayQuality? = PlayQuality.STANDARD,
    ) =
        MediaSourceResolution(
            item = item,
            source = MediaSourceResult(
                url = "https://cdn.example.test/audio.mp3",
                headers = mapOf("Origin" to "https://music.free"),
                userAgent = "ua/1.0",
                quality = quality,
            ),
            requestedPlatform = item.platform,
            resolverPlatform = item.platform,
            redirected = false,
            cachePolicy = cachePolicy,
        )

    private class CapturingResolver(
        private val block: suspend (MusicItem, String?, String?) -> MediaSourceResolution?,
    ) : MediaSourceResolver {
        val calls = mutableListOf<Triple<MusicItem, String?, String?>>()

        override suspend fun resolve(
            item: MusicItem,
            quality: String?,
            sid: String?,
        ): MediaSourceResolution? {
            calls.add(Triple(item, quality, sid))
            return block(item, quality, sid)
        }
    }

    private data class RecordedLogEvent(
        val event: String,
        val fields: Map<String, Any?>,
        val category: LogCategory,
    )

    private class RecordingLogger : MfLogger {
        val events = mutableListOf<RecordedLogEvent>()

        override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedLogEvent(event, fields, category)
        }

        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedLogEvent(event, fields, category)
        }

        override fun error(
            category: LogCategory,
            event: String,
            throwable: Throwable?,
            fields: Map<String, Any?>,
        ) {
            events += RecordedLogEvent(event, fields, category)
        }

        override fun flush() = Unit
    }

    private fun TestScope.makeCoordinator(
        resolver: MediaSourceResolver,
        progressFlow: MutableSharedFlow<ProgressTick>,
        nextItemFlow: MutableStateFlow<MusicItem?>,
        isWifiFlow: MutableStateFlow<Boolean>,
        currentQualityFlow: MutableStateFlow<PlayQuality> = MutableStateFlow(PlayQuality.STANDARD),
        cacheKeyRegistrar: PlaybackCacheKeyRegistrar,
        dispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
        headerInjectingDataSourceFactory: HeaderInjectingDataSourceFactory? = null,
    ): PrefetchCoordinator = PrefetchCoordinator(
        resolver = resolver,
        progressFlow = progressFlow,
        nextItemFlow = nextItemFlow,
        isWifiFlow = isWifiFlow,
        currentQualityFlow = currentQualityFlow,
        cacheKeyRegistrar = cacheKeyRegistrar,
        dispatcher = dispatcher,
        headerInjectingDataSourceFactory = headerInjectingDataSourceFactory,
    )

    // 1. progress < 0.6 doesn't trigger
    @Test
    fun `progress below threshold does not trigger prefetch`() = runTest {
        val resolver: MediaSourceResolver = mock()
        val cacheKeyRegistrar: PlaybackCacheKeyRegistrar = mock()
        val progress = MutableSharedFlow<ProgressTick>()
        val nextItem = MutableStateFlow<MusicItem?>(itemB)
        val isWifi = MutableStateFlow(true)
        val qualityFlow = MutableStateFlow(PlayQuality.STANDARD)

        val coordinator = makeCoordinator(resolver, progress, nextItem, isWifi, qualityFlow, cacheKeyRegistrar)
        coordinator.start()
        advanceUntilIdle() // let the collect coroutine start

        progress.emit(ProgressTick(itemA, 60_000L, 200_000L))  // 0.30
        progress.emit(ProgressTick(itemA, 100_000L, 200_000L)) // 0.50
        advanceUntilIdle()

        verify(resolver, never()).resolve(anyOrNull(), eq("standard"), eq(null))
        verify(cacheKeyRegistrar, never()).register(
            platform = anyOrNull(),
            itemId = anyOrNull(),
            url = anyOrNull(),
            headers = anyOrNull(),
            userAgent = anyOrNull(),
            quality = anyOrNull(),
            cachePolicy = anyOrNull(),
            trigger = anyOrNull(),
        )
        coordinator.stop()
    }

    @Test
    fun `playable verified plus complete inspection skips head prefetch`() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        try {
            val resolver: MediaSourceResolver = mock()
            val cacheKeyRegistrar: PlaybackCacheKeyRegistrar = mock()
            val statusStore = object : ByteCacheStatusStore {
                override suspend fun get(key: ByteCacheKey): ByteCacheStatus = ByteCacheStatus(
                    key = key,
                    validity = ByteCacheValidity.PlayableVerified,
                    cachedBytes = 512_000L,
                    contentLength = 512_000L,
                    validationMethod = ByteCacheValidationMethod.PlaybackCompleted,
                    sourceFingerprint = null,
                    invalidReason = null,
                    verifiedAt = 123L,
                    updatedAt = 456L,
                )

                override suspend fun upsert(status: ByteCacheStatus) = Unit

                override suspend fun markInvalid(
                    key: ByteCacheKey,
                    reason: com.hank.musicfree.core.cache.ByteCacheInvalidReason,
                    updatedAt: Long,
                ) = Unit

                override suspend fun delete(key: ByteCacheKey) = Unit

                override suspend fun deleteBySong(platform: String, musicId: String) = Unit
            }
            val inspector: ByteCacheInspector = mock()
            whenever(
                inspector.inspect(
                    ByteCacheKey(
                        platform = itemB.platform,
                        musicId = itemB.id,
                        quality = PlayQuality.STANDARD,
                    ),
                ),
            ).thenReturn(
                ByteCacheInspection(
                    key = ByteCacheKey(
                        platform = itemB.platform,
                        musicId = itemB.id,
                        quality = PlayQuality.STANDARD,
                    ),
                    validity = ByteCacheValidity.Complete,
                    cachedBytes = 512_000L,
                    contentLength = 512_000L,
                    holeCount = 0,
                ),
            )
            val progress = MutableSharedFlow<ProgressTick>()
            val nextItem = MutableStateFlow<MusicItem?>(itemB)
            val isWifi = MutableStateFlow(true)
            val qualityFlow = MutableStateFlow(PlayQuality.STANDARD)

            val coordinator = PrefetchCoordinator(
                resolver = resolver,
                progressFlow = progress,
                nextItemFlow = nextItem,
                isWifiFlow = isWifi,
                currentQualityFlow = qualityFlow,
                cacheKeyRegistrar = cacheKeyRegistrar,
                byteCacheStatusStore = statusStore,
                byteCacheInspector = inspector,
                dispatcher = StandardTestDispatcher(testScheduler),
            )
            coordinator.start()
            advanceUntilIdle()

            progress.emit(ProgressTick(itemA, 150_000L, 200_000L))
            advanceUntilIdle()

            verify(resolver, never()).resolve(anyOrNull(), anyOrNull(), anyOrNull())
            verify(cacheKeyRegistrar, never()).register(
                platform = anyOrNull(),
                itemId = anyOrNull(),
                url = anyOrNull(),
                headers = anyOrNull(),
                userAgent = anyOrNull(),
                quality = anyOrNull(),
                cachePolicy = anyOrNull(),
                trigger = anyOrNull(),
            )
            val skipped = logger.events.single { it.event == "prefetch_head_skipped_verified" }
            assertEquals("netease", skipped.fields["platform"])
            assertEquals("b2", skipped.fields["itemId"])
            assertEquals("standard", skipped.fields["quality"])
            coordinator.stop()
        } finally {
            MfLog.resetForTest()
        }
    }

    @Test
    fun `successful warm head logs prefetch head success with bytes read`() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        try {
            val resolver = CapturingResolver { _, quality, _ ->
                if (quality == "standard") standardResolution(MediaSourceCachePolicy.NoCache) else null
            }
            val cacheKeyRegistrar: PlaybackCacheKeyRegistrar = mock()
            val progress = MutableSharedFlow<ProgressTick>()
            val nextItem = MutableStateFlow<MusicItem?>(itemB)
            val isWifi = MutableStateFlow(true)
            val qualityFlow = MutableStateFlow(PlayQuality.STANDARD)
            val coordinator = object : PrefetchCoordinator(
                resolver = resolver,
                progressFlow = progress,
                nextItemFlow = nextItem,
                isWifiFlow = isWifi,
                currentQualityFlow = qualityFlow,
                cacheKeyRegistrar = cacheKeyRegistrar,
                dispatcher = StandardTestDispatcher(testScheduler),
            ) {
                override fun warmHead(url: String): Long = 4096L
            }
            coordinator.start()
            advanceUntilIdle()

            progress.emit(ProgressTick(itemA, 150_000L, 200_000L))
            advanceUntilIdle()

            val success = logger.events.single { it.event == "prefetch_head_success" }
            assertEquals(4096L, success.fields["bytesRead"])
            assertEquals("netease", success.fields["platform"])
            assertEquals("b2", success.fields["itemId"])
            assertEquals("standard", success.fields["quality"])
            coordinator.stop()
        } finally {
            MfLog.resetForTest()
        }
    }

    // 2. progress >= 0.6 on Wi-Fi triggers exactly once
    @Test
    fun `progress at 60 percent on wifi triggers prefetch exactly once`() = runTest {
        val resolver: MediaSourceResolver = mock()
        val cacheKeyRegistrar: PlaybackCacheKeyRegistrar = mock()
        whenever(
            resolver.resolve(
                eq(itemB),
                eq("standard"),
                eq(null),
            ),
        ).thenReturn(standardResolution(MediaSourceCachePolicy.Cache))
        val progress = MutableSharedFlow<ProgressTick>()
        val nextItem = MutableStateFlow<MusicItem?>(itemB)
        val isWifi = MutableStateFlow(true)
        val qualityFlow = MutableStateFlow(PlayQuality.STANDARD)

        val coordinator = makeCoordinator(resolver, progress, nextItem, isWifi, qualityFlow, cacheKeyRegistrar)
        coordinator.start()
        advanceUntilIdle() // let the collect coroutine start

        progress.emit(ProgressTick(itemA, 120_000L, 200_000L)) // 0.60
        advanceUntilIdle()

        verify(resolver, times(1)).resolve(eq(itemB), eq("standard"), eq(null))
        verify(cacheKeyRegistrar).register(
            platform = "netease",
            itemId = "b2",
            url = "https://cdn.example.test/audio.mp3",
            headers = mapOf("Origin" to "https://music.free"),
            userAgent = "ua/1.0",
            quality = PlayQuality.STANDARD,
            cachePolicy = MediaSourceCachePolicy.Cache,
            trigger = PlaybackCacheKeyRegistrar.Trigger.PREFETCH,
        )
        coordinator.stop()
    }

    // 3. non-Wi-Fi doesn't trigger
    @Test
    fun `non-wifi does not trigger prefetch`() = runTest {
        val resolver: MediaSourceResolver = mock()
        val cacheKeyRegistrar: PlaybackCacheKeyRegistrar = mock()
        val progress = MutableSharedFlow<ProgressTick>()
        val nextItem = MutableStateFlow<MusicItem?>(itemB)
        val isWifi = MutableStateFlow(false)
        val qualityFlow = MutableStateFlow(PlayQuality.STANDARD)

        val coordinator = makeCoordinator(resolver, progress, nextItem, isWifi, qualityFlow, cacheKeyRegistrar)
        coordinator.start()
        advanceUntilIdle() // let the collect coroutine start

        progress.emit(ProgressTick(itemA, 160_000L, 200_000L)) // 0.80 but no wifi
        advanceUntilIdle()

        verify(resolver, never()).resolve(anyOrNull(), eq("standard"), eq(null))
        verify(cacheKeyRegistrar, never()).register(
            platform = anyOrNull(),
            itemId = anyOrNull(),
            url = anyOrNull(),
            headers = anyOrNull(),
            userAgent = anyOrNull(),
            quality = anyOrNull(),
            cachePolicy = anyOrNull(),
            trigger = anyOrNull(),
        )
        coordinator.stop()
    }

    // 4. same next item doesn't prefetch twice with same quality key
    @Test
    fun `same next item is only prefetched once even after multiple progress updates`() = runTest {
        val resolver: MediaSourceResolver = mock()
        val cacheKeyRegistrar: PlaybackCacheKeyRegistrar = mock()
        whenever(
            resolver.resolve(
                eq(itemB),
                eq("standard"),
                eq(null),
            ),
        ).thenReturn(standardResolution(MediaSourceCachePolicy.Cache))
        val progress = MutableSharedFlow<ProgressTick>()
        val nextItem = MutableStateFlow<MusicItem?>(itemB)
        val isWifi = MutableStateFlow(true)
        val qualityFlow = MutableStateFlow(PlayQuality.STANDARD)

        val coordinator = makeCoordinator(resolver, progress, nextItem, isWifi, qualityFlow, cacheKeyRegistrar)
        coordinator.start()
        advanceUntilIdle() // let the collect coroutine start

        progress.emit(ProgressTick(itemA, 140_000L, 200_000L)) // 0.70
        advanceUntilIdle()
        progress.emit(ProgressTick(itemA, 160_000L, 200_000L)) // 0.80
        advanceUntilIdle()

        verify(resolver, times(1)).resolve(eq(itemB), eq("standard"), eq(null))
        verify(cacheKeyRegistrar, times(1)).register(
            platform = "netease",
            itemId = "b2",
            url = "https://cdn.example.test/audio.mp3",
            headers = mapOf("Origin" to "https://music.free"),
            userAgent = "ua/1.0",
            quality = PlayQuality.STANDARD,
            cachePolicy = MediaSourceCachePolicy.Cache,
            trigger = PlaybackCacheKeyRegistrar.Trigger.PREFETCH,
        )
        coordinator.stop()
    }

    // 5. switching next item allows a fresh prefetch
    @Test
    fun `switching next item allows fresh prefetch`() = runTest {
        val resolver: MediaSourceResolver = mock()
        val cacheKeyRegistrar: PlaybackCacheKeyRegistrar = mock()
        whenever(
            resolver.resolve(
                eq(itemB),
                eq("standard"),
                eq(null),
            ),
        ).thenReturn(standardResolution(MediaSourceCachePolicy.Cache))
        whenever(
            resolver.resolve(
                eq(itemC),
                eq("standard"),
                eq(null),
            ),
        ).thenReturn(standardResolution(MediaSourceCachePolicy.Cache))
        val progress = MutableSharedFlow<ProgressTick>()
        val nextItem = MutableStateFlow<MusicItem?>(itemB)
        val isWifi = MutableStateFlow(true)
        val qualityFlow = MutableStateFlow(PlayQuality.STANDARD)

        val coordinator = makeCoordinator(resolver, progress, nextItem, isWifi, qualityFlow, cacheKeyRegistrar)
        coordinator.start()
        advanceUntilIdle() // let the collect coroutine start

        progress.emit(ProgressTick(itemA, 140_000L, 200_000L)) // 0.70
        advanceUntilIdle()

        nextItem.value = itemC
        advanceUntilIdle() // let the StateFlow update propagate
        progress.emit(ProgressTick(itemA, 160_000L, 200_000L)) // 0.80 with nextItem=itemC
        advanceUntilIdle()

        verify(resolver, times(1)).resolve(eq(itemB), eq("standard"), eq(null))
        verify(resolver, times(1)).resolve(eq(itemC), eq("standard"), eq(null))
        verify(cacheKeyRegistrar, times(1)).register(
            platform = "netease",
            itemId = "b2",
            url = "https://cdn.example.test/audio.mp3",
            headers = mapOf("Origin" to "https://music.free"),
            userAgent = "ua/1.0",
            quality = PlayQuality.STANDARD,
            cachePolicy = MediaSourceCachePolicy.Cache,
            trigger = PlaybackCacheKeyRegistrar.Trigger.PREFETCH,
        )
        verify(cacheKeyRegistrar, times(1)).register(
            platform = "netease",
            itemId = "c3",
            url = "https://cdn.example.test/audio.mp3",
            headers = mapOf("Origin" to "https://music.free"),
            userAgent = "ua/1.0",
            quality = PlayQuality.STANDARD,
            cachePolicy = MediaSourceCachePolicy.Cache,
            trigger = PlaybackCacheKeyRegistrar.Trigger.PREFETCH,
        )
        coordinator.stop()
    }

    // 6. current quality becomes super and new key should allow re-prefetch on same track
    @Test
    fun `changing quality changes prefetch key and uses current quality in resolver`() = runTest {
        val resolver: MediaSourceResolver = mock()
        val cacheKeyRegistrar: PlaybackCacheKeyRegistrar = mock()
        whenever(
            resolver.resolve(
                eq(itemB),
                eq("standard"),
                eq(null),
            ),
        ).thenReturn(standardResolution(MediaSourceCachePolicy.Cache))
        whenever(
            resolver.resolve(
                eq(itemB),
                eq("super"),
                eq(null),
            ),
        ).thenReturn(standardResolution(MediaSourceCachePolicy.Cache, quality = PlayQuality.SUPER))
        val progress = MutableSharedFlow<ProgressTick>()
        val nextItem = MutableStateFlow<MusicItem?>(itemB)
        val isWifi = MutableStateFlow(true)
        val qualityFlow = MutableStateFlow(PlayQuality.STANDARD)

        val coordinator = makeCoordinator(resolver, progress, nextItem, isWifi, qualityFlow, cacheKeyRegistrar)
        coordinator.start()
        advanceUntilIdle()

        progress.emit(ProgressTick(itemA, 140_000L, 200_000L))
        advanceUntilIdle()
        qualityFlow.value = PlayQuality.SUPER
        advanceUntilIdle() // ensure quality flow update is observed
        progress.emit(ProgressTick(itemA, 160_000L, 200_000L))
        advanceUntilIdle()

        verify(resolver, times(1)).resolve(eq(itemB), eq("standard"), eq(null))
        verify(resolver, times(1)).resolve(eq(itemB), eq("super"), eq(null))
        verify(cacheKeyRegistrar).register(
            platform = "netease",
            itemId = "b2",
            url = "https://cdn.example.test/audio.mp3",
            headers = mapOf("Origin" to "https://music.free"),
            userAgent = "ua/1.0",
            quality = PlayQuality.SUPER,
            cachePolicy = MediaSourceCachePolicy.Cache,
            trigger = PlaybackCacheKeyRegistrar.Trigger.PREFETCH,
        )
        coordinator.stop()
    }

        // 7. fallback to null quality should reuse source quality and still warm head
    @Test
    fun `fallback resolve without quality uses fallback source quality`() = runTest {
        val resolver = CapturingResolver { _, quality, _ ->
            when (quality) {
                "super" -> null
                null -> standardResolution(quality = PlayQuality.STANDARD)
                else -> null
            }
        }
        val cacheKeyRegistrar: PlaybackCacheKeyRegistrar = mock()
        val progress = MutableSharedFlow<ProgressTick>()
        val nextItem = MutableStateFlow<MusicItem?>(itemB)
        val isWifi = MutableStateFlow(true)
        val qualityFlow = MutableStateFlow(PlayQuality.SUPER)
        var warmHeadCalled = false
        val coordinator = object : PrefetchCoordinator(
            resolver = resolver,
            progressFlow = progress,
            nextItemFlow = nextItem,
            isWifiFlow = isWifi,
            currentQualityFlow = qualityFlow,
            cacheKeyRegistrar = cacheKeyRegistrar,
            dispatcher = StandardTestDispatcher(testScheduler),
        ) {
            override fun warmHead(url: String): Long {
                warmHeadCalled = true
                return 0L
            }
        }
        coordinator.start()
        advanceUntilIdle()

        progress.emit(ProgressTick(itemA, 150_000L, 200_000L))
        advanceUntilIdle()
        advanceUntilIdle()

        assertEquals(2, resolver.calls.size)
        assertEquals(Triple(itemB, "super", null), resolver.calls[0])
        assertEquals(Triple(itemB, null, null), resolver.calls[1])
        verify(cacheKeyRegistrar).register(
            platform = "netease",
            itemId = "b2",
            url = "https://cdn.example.test/audio.mp3",
            headers = mapOf("Origin" to "https://music.free"),
            userAgent = "ua/1.0",
            quality = PlayQuality.STANDARD,
            cachePolicy = MediaSourceCachePolicy.Cache,
            trigger = PlaybackCacheKeyRegistrar.Trigger.PREFETCH,
        )
        assertEquals(true, warmHeadCalled)
        coordinator.stop()
    }

    @Test
    fun `explicit quality resolve throws then fallback quality null uses fallback source`() = runTest {
        val resolver = CapturingResolver { _, quality, _ ->
            when (quality) {
                "super" -> throw IllegalStateException("resolver failure for requested quality")
                null -> standardResolution(quality = PlayQuality.STANDARD)
                else -> null
            }
        }
        val cacheKeyRegistrar: PlaybackCacheKeyRegistrar = mock()
        val progress = MutableSharedFlow<ProgressTick>()
        val nextItem = MutableStateFlow<MusicItem?>(itemB)
        val isWifi = MutableStateFlow(true)
        val qualityFlow = MutableStateFlow(PlayQuality.SUPER)
        var warmHeadCalled = false
        val coordinator = object : PrefetchCoordinator(
            resolver = resolver,
            progressFlow = progress,
            nextItemFlow = nextItem,
            isWifiFlow = isWifi,
            currentQualityFlow = qualityFlow,
            cacheKeyRegistrar = cacheKeyRegistrar,
            dispatcher = StandardTestDispatcher(testScheduler),
        ) {
            override fun warmHead(url: String): Long {
                warmHeadCalled = true
                return 0L
            }
        }
        coordinator.start()
        advanceUntilIdle()

        progress.emit(ProgressTick(itemA, 150_000L, 200_000L))
        advanceUntilIdle()
        advanceUntilIdle()

        assertEquals(2, resolver.calls.size)
        assertEquals(Triple(itemB, "super", null), resolver.calls[0])
        assertEquals(Triple(itemB, null, null), resolver.calls[1])
        verify(cacheKeyRegistrar).register(
            platform = "netease",
            itemId = "b2",
            url = "https://cdn.example.test/audio.mp3",
            headers = mapOf("Origin" to "https://music.free"),
            userAgent = "ua/1.0",
            quality = PlayQuality.STANDARD,
            cachePolicy = MediaSourceCachePolicy.Cache,
            trigger = PlaybackCacheKeyRegistrar.Trigger.PREFETCH,
        )
        assertEquals(true, warmHeadCalled)
        coordinator.stop()
    }

    @Test
    fun `explicit quality cancellation does not fallback or register or warm`() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        try {
            val resolver = CapturingResolver { _, quality, _ ->
                when (quality) {
                    "super" -> throw CancellationException("cancel")
                    else -> null
                }
            }
            val cacheKeyRegistrar: PlaybackCacheKeyRegistrar = mock()
            val progress = MutableSharedFlow<ProgressTick>()
            val nextItem = MutableStateFlow<MusicItem?>(itemB)
            val isWifi = MutableStateFlow(true)
            val qualityFlow = MutableStateFlow(PlayQuality.SUPER)
            var warmHeadCalled = false
            val coordinator = object : PrefetchCoordinator(
                resolver = resolver,
                progressFlow = progress,
                nextItemFlow = nextItem,
                isWifiFlow = isWifi,
                currentQualityFlow = qualityFlow,
                cacheKeyRegistrar = cacheKeyRegistrar,
                dispatcher = StandardTestDispatcher(testScheduler),
            ) {
                override fun warmHead(url: String): Long {
                    warmHeadCalled = true
                    return 0L
                }
            }
            coordinator.start()
            advanceUntilIdle()

            progress.emit(ProgressTick(itemA, 150_000L, 200_000L))
            advanceUntilIdle()

            assertEquals(1, resolver.calls.size)
            assertEquals(Triple(itemB, "super", null), resolver.calls.single())
            assertEquals(0, logger.events.count { it.event == "prefetch_failed" })
            verify(cacheKeyRegistrar, never()).register(
                platform = anyOrNull(),
                itemId = anyOrNull(),
                url = anyOrNull(),
                headers = anyOrNull(),
                userAgent = anyOrNull(),
                quality = anyOrNull(),
                cachePolicy = anyOrNull(),
                trigger = anyOrNull(),
            )
            assertEquals(false, warmHeadCalled)
            coordinator.stop()
        } finally {
            MfLog.resetForTest()
        }
    }

    @Test
    fun `fallback resolve cancellation does not register or warm`() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        try {
            val resolver = CapturingResolver { _, quality, _ ->
                when (quality) {
                    "super" -> null
                    null -> throw CancellationException("fallback cancel")
                    else -> null
                }
            }
            val cacheKeyRegistrar: PlaybackCacheKeyRegistrar = mock()
            val progress = MutableSharedFlow<ProgressTick>()
            val nextItem = MutableStateFlow<MusicItem?>(itemB)
            val isWifi = MutableStateFlow(true)
            val qualityFlow = MutableStateFlow(PlayQuality.SUPER)
            var warmHeadCalled = false
            val coordinator = object : PrefetchCoordinator(
                resolver = resolver,
                progressFlow = progress,
                nextItemFlow = nextItem,
                isWifiFlow = isWifi,
                currentQualityFlow = qualityFlow,
                cacheKeyRegistrar = cacheKeyRegistrar,
                dispatcher = StandardTestDispatcher(testScheduler),
            ) {
                override fun warmHead(url: String): Long {
                    warmHeadCalled = true
                    return 0L
                }
            }
            coordinator.start()
            advanceUntilIdle()

            progress.emit(ProgressTick(itemA, 150_000L, 200_000L))
            advanceUntilIdle()

            assertEquals(2, resolver.calls.size)
            assertEquals(Triple(itemB, "super", null), resolver.calls[0])
            assertEquals(Triple(itemB, null, null), resolver.calls[1])
            assertEquals(0, logger.events.count { it.event == "prefetch_failed" })
            verify(cacheKeyRegistrar, never()).register(
                platform = anyOrNull(),
                itemId = anyOrNull(),
                url = anyOrNull(),
                headers = anyOrNull(),
                userAgent = anyOrNull(),
                quality = anyOrNull(),
                cachePolicy = anyOrNull(),
                trigger = anyOrNull(),
            )
            assertEquals(false, warmHeadCalled)
            coordinator.stop()
        } finally {
            MfLog.resetForTest()
        }
    }

    @Test
    fun `warm head cancellation does not log failure`() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        try {
            val resolver = CapturingResolver { _, quality, _ ->
                if (quality == "standard") standardResolution(MediaSourceCachePolicy.Cache) else null
            }
            val callOrder = mutableListOf<String>()
            val cacheKeyRegistrar: PlaybackCacheKeyRegistrar = mock()
            doAnswer {
                callOrder += "register"
                null
            }.whenever(cacheKeyRegistrar).register(
                platform = anyOrNull(),
                itemId = anyOrNull(),
                url = anyOrNull(),
                headers = anyOrNull(),
                userAgent = anyOrNull(),
                quality = anyOrNull(),
                cachePolicy = anyOrNull(),
                trigger = anyOrNull(),
            )
            val progress = MutableSharedFlow<ProgressTick>()
            val nextItem = MutableStateFlow<MusicItem?>(itemB)
            val isWifi = MutableStateFlow(true)
            val qualityFlow = MutableStateFlow(PlayQuality.STANDARD)
            val coordinator = object : PrefetchCoordinator(
                resolver = resolver,
                progressFlow = progress,
                nextItemFlow = nextItem,
                isWifiFlow = isWifi,
                currentQualityFlow = qualityFlow,
                cacheKeyRegistrar = cacheKeyRegistrar,
                dispatcher = StandardTestDispatcher(testScheduler),
            ) {
                override fun warmHead(url: String): Long {
                    callOrder += "warmHead"
                    throw CancellationException("warm cancel")
                }
            }
            coordinator.start()
            advanceUntilIdle()

            progress.emit(ProgressTick(itemA, 150_000L, 200_000L))
            advanceUntilIdle()

            assertEquals(listOf("register", "warmHead"), callOrder)
            assertEquals(0, logger.events.count { it.event == "prefetch_failed" })
            coordinator.stop()
        } finally {
            MfLog.resetForTest()
        }
    }

    @Test
    fun `fallback null quality resolves to null logs fallback failure fields`() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        try {
            val resolver = CapturingResolver { _, quality, _ ->
                when (quality) {
                    "standard" -> null
                    null -> null
                    else -> null
                }
            }
            val cacheKeyRegistrar: PlaybackCacheKeyRegistrar = mock()
            val progress = MutableSharedFlow<ProgressTick>()
            val nextItem = MutableStateFlow<MusicItem?>(itemB)
            val isWifi = MutableStateFlow(true)
            val qualityFlow = MutableStateFlow(PlayQuality.STANDARD)
            val coordinator = makeCoordinator(
                resolver = resolver,
                progressFlow = progress,
                nextItemFlow = nextItem,
                isWifiFlow = isWifi,
                currentQualityFlow = qualityFlow,
                cacheKeyRegistrar = cacheKeyRegistrar,
                dispatcher = StandardTestDispatcher(testScheduler),
            )
            coordinator.start()
            advanceUntilIdle()

            progress.emit(ProgressTick(itemA, 150_000L, 200_000L))
            advanceUntilIdle()

            val failed = logger.events.single { it.event == "prefetch_failed" }
            assertEquals("resolve_returned_null", failed.fields["reason"])
            assertEquals("standard", failed.fields["requestedQuality"])
            assertEquals("", failed.fields["effectiveQuality"])
            assertEquals("", failed.fields["quality"])
            assertEquals(true, failed.fields["fallback"])
            assertEquals(true, failed.fields["fallback_unknown_quality"])
            assertEquals("", failed.fields["cachePolicy"])
            assertEquals(false, failed.fields.containsKey("url"))
            assertEquals("netease", failed.fields["platform"])

            verify(cacheKeyRegistrar, never()).register(
                platform = anyOrNull(),
                itemId = anyOrNull(),
                url = anyOrNull(),
                headers = anyOrNull(),
                userAgent = anyOrNull(),
                quality = anyOrNull(),
                cachePolicy = anyOrNull(),
                trigger = anyOrNull(),
            )
            coordinator.stop()
        } finally {
            MfLog.resetForTest()
        }
    }

    // 8. no-store should register byte-cache disabled and skip warmHead
    @Test
    fun `no-store policy registers cache key and skips head warm`() = runTest {
        val logger = RecordingLogger()
        MfLog.install(logger)
        try {
            val resolver = CapturingResolver { _, quality, _ ->
                if (quality == "standard") standardResolution(MediaSourceCachePolicy.NoStore) else null
            }
            val trackHeaderRegistry = TrackHeaderRegistry()
            val cacheKeyRegistrar: PlaybackCacheKeyRegistrar = PlaybackCacheKeyRegistrar(trackHeaderRegistry)
            val progress = MutableSharedFlow<ProgressTick>()
            val nextItem = MutableStateFlow<MusicItem?>(itemB)
            val isWifi = MutableStateFlow(true)
            val qualityFlow = MutableStateFlow(PlayQuality.STANDARD)
            var warmHeadCalled = false
            val coordinator = object : PrefetchCoordinator(
                resolver = resolver,
                progressFlow = progress,
                nextItemFlow = nextItem,
                isWifiFlow = isWifi,
                currentQualityFlow = qualityFlow,
                cacheKeyRegistrar = cacheKeyRegistrar,
                dispatcher = StandardTestDispatcher(testScheduler),
            ) {
                override fun warmHead(url: String): Long {
                    warmHeadCalled = true
                    return 0L
                }
            }
            coordinator.start()
            advanceUntilIdle()

            progress.emit(ProgressTick(itemA, 150_000L, 200_000L)) // 0.75
            advanceUntilIdle()
            advanceUntilIdle()

            val headerEntry = trackHeaderRegistry.get("https://cdn.example.test/audio.mp3")
            assertNotNull(headerEntry)
            assertFalse(headerEntry!!.byteCacheAllowed)
            assertEquals(1, resolver.calls.size)
            assertEquals(Triple(itemB, "standard", null), resolver.calls.single())
            assertEquals(false, warmHeadCalled)
            assertEquals(1, logger.events.count { it.event == "prefetch_skipped" })
            assertEquals(0, logger.events.count { it.event == "prefetch_head_success" })
            coordinator.stop()
        } finally {
            MfLog.resetForTest()
        }
    }

    // bonus: verify null next item doesn't trigger
    @Test
    fun `null next item does not trigger prefetch`() = runTest {
        val resolver: MediaSourceResolver = mock()
        val cacheKeyRegistrar: PlaybackCacheKeyRegistrar = mock()
        val progress = MutableSharedFlow<ProgressTick>()
        val nextItem = MutableStateFlow<MusicItem?>(null)
        val isWifi = MutableStateFlow(true)
        val qualityFlow = MutableStateFlow(PlayQuality.STANDARD)

        val coordinator = makeCoordinator(resolver, progress, nextItem, isWifi, qualityFlow, cacheKeyRegistrar)
        coordinator.start()
        advanceUntilIdle() // let the collect coroutine start

        progress.emit(ProgressTick(itemA, 180_000L, 200_000L)) // 0.90
        advanceUntilIdle()

        verify(resolver, never()).resolve(anyOrNull(), eq("standard"), eq(null))
        verify(cacheKeyRegistrar, never()).register(
            platform = anyOrNull(),
            itemId = anyOrNull(),
            url = anyOrNull(),
            headers = anyOrNull(),
            userAgent = anyOrNull(),
            quality = anyOrNull(),
            cachePolicy = anyOrNull(),
            trigger = anyOrNull(),
        )
        coordinator.stop()
    }
}

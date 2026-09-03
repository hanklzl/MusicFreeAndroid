package com.hank.musicfree.player.controller

import android.content.Context
import com.hank.musicfree.core.cache.ByteCacheInvalidReason
import com.hank.musicfree.core.cache.ByteCacheKey
import com.hank.musicfree.core.cache.ByteCacheStatus
import com.hank.musicfree.core.cache.ByteCacheStatusStore
import com.hank.musicfree.core.cache.ByteCacheValidationMethod
import com.hank.musicfree.core.cache.ByteCacheValidity
import com.hank.musicfree.core.media.MediaSourceCachePolicy
import com.hank.musicfree.core.media.MediaSourceResolution
import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.media.StaleUrlRefresher
import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.telemetry.CurrentSidProvider
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import com.hank.musicfree.player.cache.ByteCacheInspection
import com.hank.musicfree.player.cache.ByteCacheInspector
import com.hank.musicfree.player.listening.ListenTracker
import com.hank.musicfree.player.service.PlaybackNotificationCommandHandler
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerControllerByteCacheValidityTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        PlaybackNotificationCommandHandler.detachAllForTest()
        MfLog.resetForTest()
    }

    @Test
    fun `playback ended writes PlayableVerified only when byte cache is complete`() = runBlocking {
        val store = RecordingByteCacheStatusStore()
        val inspector = FakeByteCacheInspector(
            ByteCacheInspection(
                key = key("1"),
                validity = ByteCacheValidity.Complete,
                cachedBytes = 1_024L,
                contentLength = 1_024L,
                holeCount = 0,
            ),
        )
        val controller = controller(
            resolver = RecordingResolver(normalUrl = "https://cdn.example.test/normal.mp3"),
            statusStore = store,
            inspector = inspector,
        )
        try {
            controller.playQueue.setQueue(listOf(item("1", null)), startIndex = 0)
            controller.markCurrentPlaybackSourceForTest(MediaSourceCachePolicy.NoCache)

            controller.handlePlaybackEndedForTest()

            assertEquals(ByteCacheValidity.PlayableVerified, store.upserts.single().validity)
            assertEquals(1_024L, store.upserts.single().contentLength)
            assertEquals(ByteCacheValidationMethod.PlaybackCompleted, store.upserts.single().validationMethod)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `playback ended early invalidates complete byte cache instead of verifying it`() = runBlocking {
        val store = RecordingByteCacheStatusStore()
        val refresher = RecordingStaleUrlRefresher()
        val inspector = FakeByteCacheInspector(
            ByteCacheInspection(
                key = key("short"),
                validity = ByteCacheValidity.Complete,
                cachedBytes = 702_831L,
                contentLength = 702_831L,
                holeCount = 0,
            ),
        )
        val controller = controller(
            resolver = RecordingResolver(normalUrl = "https://cdn.example.test/short.mp3"),
            statusStore = store,
            inspector = inspector,
            staleUrlRefresher = refresher,
        )
        try {
            val item = item("short", null, duration = 180_000L)
            controller.restoreQueue(
                items = listOf(item),
                startIndex = 0,
                savedPositionMs = 76_000L,
                savedDurationMs = 76_000L,
            )
            controller.markCurrentPlaybackSourceForTest(MediaSourceCachePolicy.NoCache)

            controller.handlePlaybackEndedForTest()

            assertEquals(emptyList<ByteCacheStatus>(), store.upserts)
            assertEquals(listOf(key("short") to ByteCacheInvalidReason.BadByteCache), store.invalidations)
            assertEquals(listOf(Triple("test", "short", PlayQuality.STANDARD)), refresher.evictCalls)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `playback ended with unknown item duration keeps complete cache unverified`() = runBlocking {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val store = RecordingByteCacheStatusStore()
        val inspector = FakeByteCacheInspector(
            ByteCacheInspection(
                key = key("unknown"),
                validity = ByteCacheValidity.Complete,
                cachedBytes = 1_665_507L,
                contentLength = 1_665_507L,
                holeCount = 0,
            ),
        )
        val controller = controller(
            resolver = RecordingResolver(normalUrl = "https://cdn.example.test/unknown.mp3"),
            statusStore = store,
            inspector = inspector,
        )
        try {
            controller.playQueue.setQueue(listOf(item("unknown", null, duration = 0L)), startIndex = 0)
            controller.markCurrentPlaybackSourceForTest(MediaSourceCachePolicy.NoCache)

            controller.handlePlaybackEndedForTest()

            assertEquals(ByteCacheValidity.Complete, store.upserts.single().validity)
            assertEquals(ByteCacheValidationMethod.SpanInspection, store.upserts.single().validationMethod)
            assertEquals(null, store.upserts.single().verifiedAt)
            val validation = logger.events.single { it.event == "byte_cache_playback_end_validation" }
            assertEquals("unknown_duration", validation.fields["reason"])
            assertEquals("skipped", validation.fields["result"])
        } finally {
            controller.release()
        }
    }

    @Test
    fun `verified cache with unknown item duration is evicted before normal resolve`() = runBlocking {
        val store = RecordingByteCacheStatusStore(
            existing = ByteCacheStatus(
                key = key("unknown"),
                validity = ByteCacheValidity.PlayableVerified,
                cachedBytes = 1_665_507L,
                contentLength = 1_665_507L,
                validationMethod = ByteCacheValidationMethod.PlaybackCompleted,
                sourceFingerprint = "fp",
                invalidReason = null,
                verifiedAt = 10L,
                updatedAt = 10L,
            ),
        )
        val inspector = FakeByteCacheInspector(
            ByteCacheInspection(
                key = key("unknown"),
                validity = ByteCacheValidity.Complete,
                cachedBytes = 1_665_507L,
                contentLength = 1_665_507L,
                holeCount = 0,
            ),
        )
        val resolver = RecordingResolver(
            normalUrl = "https://cdn.example.test/fresh.mp3",
            cachedUrl = "https://cdn.example.test/cached.mp3",
        )
        val refresher = RecordingStaleUrlRefresher()
        val controller = controller(
            resolver = resolver,
            statusStore = store,
            inspector = inspector,
            staleUrlRefresher = refresher,
        )
        try {
            val playable = controller.resolvePlayableItemForTest(item("unknown", null, duration = 0L), sid = "ps_ended")

            assertEquals("https://cdn.example.test/fresh.mp3", playable?.url)
            assertEquals(listOf("unknown"), resolver.normalResolveIds)
            assertEquals(emptyList<String>(), resolver.cachedResolveIds)
            assertEquals(listOf(key("unknown") to ByteCacheInvalidReason.BadByteCache), store.invalidations)
            assertEquals(listOf(Triple("test", "unknown", PlayQuality.STANDARD)), refresher.evictCalls)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `playback end verification logs captured ended sid after current sid changes`() = runBlocking {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val sidProvider = CurrentSidProvider()
        val endedSid = sidProvider.newSession()
        val store = RecordingByteCacheStatusStore()
        val inspector = FakeByteCacheInspector(
            ByteCacheInspection(
                key = key("sid"),
                validity = ByteCacheValidity.Complete,
                cachedBytes = 2_048L,
                contentLength = 2_048L,
                holeCount = 0,
            ),
        )
        val controller = controller(
            resolver = RecordingResolver(normalUrl = "https://cdn.example.test/sid.mp3"),
            statusStore = store,
            inspector = inspector,
            currentSidProvider = sidProvider,
        )
        try {
            controller.playQueue.setQueue(listOf(item("sid", null, duration = 180_000L)), startIndex = 0)
            controller.markCurrentPlaybackSourceForTest(MediaSourceCachePolicy.NoCache)
            sidProvider.newSession()

            controller.handlePlaybackEndedForTest(sid = endedSid)

            val event = logger.events.single { it.event == "byte_cache_verified" }
            assertEquals(endedSid, event.fields["sid"])
            val validation = logger.events.single { it.event == "byte_cache_playback_end_validation" }
            assertEquals(endedSid, validation.fields["sid"])
            assertEquals(180_000L, validation.fields["itemDurationMs"])
            assertTrue(validation.fields.containsKey("endedPositionMs"))
            assertTrue(validation.fields.containsKey("reportedDurationMs"))
            assertTrue(validation.fields.containsKey("cachedBytes"))
            assertTrue(validation.fields.containsKey("contentLength"))
            assertTrue(validation.fields.containsKey("holeCount"))
            assertTrue(validation.fields.containsKey("hadFatalError"))
            assertTrue(validation.fields.containsKey("durationMs"))
        } finally {
            controller.release()
        }
    }

    @Test
    fun `verified complete cache uses cached source without normal plugin resolve`() = runBlocking {
        val store = RecordingByteCacheStatusStore(
            existing = ByteCacheStatus(
                key = key("1"),
                validity = ByteCacheValidity.PlayableVerified,
                cachedBytes = 2_048L,
                contentLength = 2_048L,
                validationMethod = ByteCacheValidationMethod.PlaybackCompleted,
                sourceFingerprint = "fp",
                invalidReason = null,
                verifiedAt = 10L,
                updatedAt = 10L,
            ),
        )
        val inspector = FakeByteCacheInspector(
            ByteCacheInspection(
                key = key("1"),
                validity = ByteCacheValidity.Complete,
                cachedBytes = 2_048L,
                contentLength = 2_048L,
                holeCount = 0,
            ),
        )
        val resolver = RecordingResolver(
            normalUrl = "https://cdn.example.test/normal.mp3",
            cachedUrl = "https://cdn.example.test/cached.mp3",
        )
        val controller = controller(resolver = resolver, statusStore = store, inspector = inspector)
        try {
            val playable = controller.resolvePlayableItemForTest(item("1", null), sid = "ps_1")

            assertEquals("https://cdn.example.test/cached.mp3", playable?.url)
            assertEquals(emptyList<String>(), resolver.normalResolveIds)
            assertEquals(listOf("1"), resolver.cachedResolveIds)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `partial byte cache keeps online no-cache on normal plugin resolve path`() = runBlocking {
        val store = RecordingByteCacheStatusStore(
            existing = ByteCacheStatus(
                key = key("1"),
                validity = ByteCacheValidity.Partial,
                cachedBytes = 512L,
                contentLength = 2_048L,
                validationMethod = ByteCacheValidationMethod.SpanInspection,
                sourceFingerprint = null,
                invalidReason = null,
                verifiedAt = null,
                updatedAt = 10L,
            ),
        )
        val inspector = FakeByteCacheInspector(
            ByteCacheInspection(
                key = key("1"),
                validity = ByteCacheValidity.Partial,
                cachedBytes = 512L,
                contentLength = 2_048L,
                holeCount = 0,
            ),
        )
        val resolver = RecordingResolver(
            normalUrl = "https://cdn.example.test/normal.mp3",
            cachedUrl = "https://cdn.example.test/cached.mp3",
        )
        val controller = controller(resolver = resolver, statusStore = store, inspector = inspector)
        try {
            val playable = controller.resolvePlayableItemForTest(item("1", null), sid = "ps_1")

            assertEquals("https://cdn.example.test/normal.mp3", playable?.url)
            assertEquals(listOf("1"), resolver.normalResolveIds)
            assertEquals(emptyList<String>(), resolver.cachedResolveIds)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `normal resolve failure falls back to verified byte cache`() = runBlocking {
        val store = RecordingByteCacheStatusStore(
            existing = ByteCacheStatus(
                key = key("1"),
                validity = ByteCacheValidity.PlayableVerified,
                cachedBytes = 2_048L,
                contentLength = 2_048L,
                validationMethod = ByteCacheValidationMethod.PlaybackCompleted,
                sourceFingerprint = "fp",
                invalidReason = null,
                verifiedAt = 10L,
                updatedAt = 10L,
            ),
        )
        val inspector = FakeByteCacheInspector(
            ByteCacheInspection(
                key = key("1"),
                validity = ByteCacheValidity.Complete,
                cachedBytes = 2_048L,
                contentLength = 2_048L,
                holeCount = 0,
            ),
        )
        val resolver = RecordingResolver(
            normalUrl = null,
            cachedUrl = "https://cdn.example.test/cached.mp3",
            cachedMissesBeforeHit = 1,
        )
        val controller = controller(resolver = resolver, statusStore = store, inspector = inspector)
        try {
            val playable = controller.resolvePlayableItemForTest(item("1", null), sid = "ps_1")

            assertEquals("https://cdn.example.test/cached.mp3", playable?.url)
            assertEquals(listOf("1"), resolver.normalResolveIds)
            assertEquals(listOf("1", "1"), resolver.cachedResolveIds)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `no-store playback does not use verified cache or write status`() = runBlocking {
        val store = RecordingByteCacheStatusStore(
            existing = ByteCacheStatus(
                key = key("1"),
                validity = ByteCacheValidity.PlayableVerified,
                cachedBytes = 2_048L,
                contentLength = 2_048L,
                validationMethod = ByteCacheValidationMethod.PlaybackCompleted,
                sourceFingerprint = "fp",
                invalidReason = null,
                verifiedAt = 10L,
                updatedAt = 10L,
            ),
        )
        val inspector = FakeByteCacheInspector(
            ByteCacheInspection(
                key = key("1"),
                validity = ByteCacheValidity.Complete,
                cachedBytes = 2_048L,
                contentLength = 2_048L,
                holeCount = 0,
            ),
        )
        val resolver = RecordingResolver(
            normalUrl = "https://cdn.example.test/normal.mp3",
            cachedUrl = "https://cdn.example.test/cached.mp3",
            cachedPolicy = MediaSourceCachePolicy.NoStore,
        )
        val controller = controller(resolver = resolver, statusStore = store, inspector = inspector)
        try {
            val playable = controller.resolvePlayableItemForTest(item("1", null), sid = "ps_1")
            controller.playQueue.setQueue(listOf(item("1", playable?.url)), startIndex = 0)
            controller.markCurrentPlaybackSourceForTest(MediaSourceCachePolicy.NoStore)
            controller.handlePlaybackEndedForTest()

            assertEquals("https://cdn.example.test/normal.mp3", playable?.url)
            assertEquals(emptyList<ByteCacheStatus>(), store.upserts)
        } finally {
            controller.release()
        }
    }

    private fun controller(
        resolver: MediaSourceResolver,
        statusStore: ByteCacheStatusStore,
        inspector: ByteCacheInspector,
        staleUrlRefresher: StaleUrlRefresher = RecordingStaleUrlRefresher(),
        currentSidProvider: CurrentSidProvider = CurrentSidProvider(),
    ) = PlayerController(
        context = context,
        mediaSourceResolver = resolver,
        staleUrlRefresher = staleUrlRefresher,
        byteCacheStatusStore = statusStore,
        byteCacheInspector = inspector,
        listenTracker = mock<ListenTracker>(),
        currentSidProvider = currentSidProvider,
        playCacheTelemetry = com.hank.musicfree.core.telemetry.PlayCacheTelemetry(com.hank.musicfree.logging.MfLog),
    )

    private fun key(id: String) = ByteCacheKey(
        platform = "test",
        musicId = id,
        quality = PlayQuality.STANDARD,
    )

    private fun item(
        id: String,
        url: String?,
        duration: Long = 1_000L,
    ) = MusicItem(
        id = id,
        platform = "test",
        title = "Song $id",
        artist = "Artist",
        album = null,
        duration = duration,
        url = url,
        artwork = null,
        qualities = null,
    )

    private class RecordingByteCacheStatusStore(
        private val existing: ByteCacheStatus? = null,
    ) : ByteCacheStatusStore {
        val upserts = mutableListOf<ByteCacheStatus>()
        val invalidations = mutableListOf<Pair<ByteCacheKey, ByteCacheInvalidReason>>()

        override suspend fun get(key: ByteCacheKey): ByteCacheStatus? = existing?.takeIf { it.key == key }

        override suspend fun upsert(status: ByteCacheStatus) {
            upserts += status
        }

        override suspend fun markInvalid(
            key: ByteCacheKey,
            reason: ByteCacheInvalidReason,
            updatedAt: Long,
        ) {
            invalidations += key to reason
        }

        override suspend fun delete(key: ByteCacheKey) = Unit

        override suspend fun deleteBySong(platform: String, musicId: String) = Unit
    }

    private class FakeByteCacheInspector(
        private val inspection: ByteCacheInspection,
    ) : ByteCacheInspector() {
        val keys = mutableListOf<ByteCacheKey>()

        override fun inspect(key: ByteCacheKey): ByteCacheInspection {
            keys += key
            return inspection.copy(key = key)
        }
    }

    private class RecordingStaleUrlRefresher : StaleUrlRefresher {
        val evictCalls = mutableListOf<Triple<String, String, PlayQuality>>()

        override suspend fun evictCacheEntry(platform: String, id: String, quality: PlayQuality) {
            evictCalls += Triple(platform, id, quality)
        }

        override suspend fun resolveFresh(
            item: MusicItem,
            quality: String?,
            sid: String?,
        ): MediaSourceResolution? = null
    }

    private class RecordingResolver(
        private val normalUrl: String?,
        private val cachedUrl: String? = null,
        private val cachedPolicy: MediaSourceCachePolicy = MediaSourceCachePolicy.NoCache,
        private val cachedMissesBeforeHit: Int = 0,
    ) : MediaSourceResolver {
        val normalResolveIds = mutableListOf<String>()
        val cachedResolveIds = mutableListOf<String>()

        override suspend fun resolve(
            item: MusicItem,
            quality: String?,
            sid: String?,
        ): MediaSourceResolution? {
            normalResolveIds += item.id
            return normalUrl?.let { resolution(item, it, MediaSourceCachePolicy.NoCache) }
        }

        override suspend fun resolveCachedSourceForVerifiedByteCache(
            item: MusicItem,
            quality: PlayQuality,
            sid: String,
        ): MediaSourceResolution? {
            cachedResolveIds += item.id
            if (cachedResolveIds.size <= cachedMissesBeforeHit) return null
            if (cachedPolicy == MediaSourceCachePolicy.NoStore) return null
            return cachedUrl?.let { resolution(item, it, cachedPolicy) }
        }

        private fun resolution(
            item: MusicItem,
            url: String,
            policy: MediaSourceCachePolicy,
        ) = MediaSourceResolution(
            item = item.copy(url = url),
            source = MediaSourceResult(
                url = url,
                headers = mapOf("Referer" to "https://music.example.test"),
                userAgent = "MusicFreeAndroidTest/1.0",
                quality = PlayQuality.STANDARD,
            ),
            requestedPlatform = item.platform,
            resolverPlatform = item.platform,
            redirected = false,
            cachePolicy = policy,
        )
    }

    private data class RecordedLogEvent(
        val event: String,
        val fields: Map<String, Any?>,
    )

    private class RecordingLogger : MfLogger {
        val events = mutableListOf<RecordedLogEvent>()

        override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedLogEvent(event, fields)
        }

        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
            events += RecordedLogEvent(event, fields)
        }

        override fun error(
            category: LogCategory,
            event: String,
            throwable: Throwable?,
            fields: Map<String, Any?>,
        ) {
            events += RecordedLogEvent(event, fields)
        }

        override fun flush() = Unit
    }
}

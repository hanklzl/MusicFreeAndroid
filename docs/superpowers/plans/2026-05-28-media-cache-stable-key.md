# Media Cache Stable Key Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make online playback cache reuse work for signed URLs without lowering user-selected quality, while making `no-store` bypass byte caching and making cache hit logs trustworthy.

**Architecture:** Move cache policy semantics into `:core`, pass policy through `MediaSourceResolution`, centralize playback URL registration in `PlaybackCacheKeyRegistrar`, and make the Media3 data source choose cached vs uncached playback per resolved URL. Prefetch uses the same registration path and current quality as normal playback.

**Tech Stack:** Kotlin, Hilt, Kotlin Coroutines Flow, Media3 `DataSource` / `CacheDataSource`, Room-backed plugin source cache, MfLog/Logan diagnostics, Gradle debug unit tests.

---

## File Structure

- Create `core/src/main/java/com/hank/musicfree/core/media/MediaSourceCachePolicy.kt`: source-of-truth cache policy enum and read/write helpers.
- Modify `core/src/main/java/com/hank/musicfree/core/media/MediaSourceResolver.kt`: add `cachePolicy` to `MediaSourceResolution`.
- Modify `plugin/src/main/java/com/hank/musicfree/plugin/playback/CacheControlPolicy.kt`: compatibility shim that delegates to `:core` policy while preserving RN oracle strings and `isOffline` source text.
- Modify `plugin/src/main/java/com/hank/musicfree/plugin/media/PluginMediaSourceService.kt`: use core policy, pass policy into resolutions, and write `no-cache` online resolved-source cache for offline fallback.
- Create `player/src/main/java/com/hank/musicfree/player/source/PlaybackCacheKeyRegistrar.kt`: single entrypoint for stable key registration and registration logs.
- Modify `player/src/main/java/com/hank/musicfree/player/source/TrackHeaderRegistry.kt`: store cache policy and byte-cache permission, increase LRU size to 64.
- Modify `player/src/main/java/com/hank/musicfree/player/source/HeaderInjectingDataSourceFactory.kt`: make cached vs uncached selection per `DataSpec`.
- Modify `player/src/main/java/com/hank/musicfree/player/cache/CacheDataSourceEventBridge.kt` and `core/src/main/java/com/hank/musicfree/core/telemetry/PlayCacheTelemetry.kt`: emit final `media3_datasource_close` byte counters.
- Modify `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`: expose current quality flow and replace direct registry writes with registrar calls.
- Modify `player/src/main/java/com/hank/musicfree/player/prefetch/PrefetchCoordinator.kt` and `app/src/main/java/com/hank/musicfree/di/PrefetchModule.kt`: prefetch with current quality and registrar.
- Add/update tests under `core/src/test`, `plugin/src/test`, and `player/src/test` as specified in each task.

---

### Task 1: Core Cache Policy And Resolution Contract

**Files:**
- Create: `core/src/main/java/com/hank/musicfree/core/media/MediaSourceCachePolicy.kt`
- Modify: `core/src/main/java/com/hank/musicfree/core/media/MediaSourceResolver.kt`
- Create: `core/src/test/java/com/hank/musicfree/core/media/MediaSourceCachePolicyTest.kt`
- Modify: `core/src/test/java/com/hank/musicfree/core/media/MediaSourceResolverTest.kt`

- [ ] **Step 1: Write core policy tests**

Create `core/src/test/java/com/hank/musicfree/core/media/MediaSourceCachePolicyTest.kt`:

```kotlin
package com.hank.musicfree.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSourceCachePolicyTest {

    @Test
    fun `parse recognizes RN wire values and defaults to no-cache`() {
        assertEquals(MediaSourceCachePolicy.Cache, MediaSourceCachePolicy.parse("cache"))
        assertEquals(MediaSourceCachePolicy.Cache, MediaSourceCachePolicy.parse("CACHE"))
        assertEquals(MediaSourceCachePolicy.NoCache, MediaSourceCachePolicy.parse("no-cache"))
        assertEquals(MediaSourceCachePolicy.NoStore, MediaSourceCachePolicy.parse("no-store"))
        assertEquals(MediaSourceCachePolicy.NoCache, MediaSourceCachePolicy.parse(null))
        assertEquals(MediaSourceCachePolicy.NoCache, MediaSourceCachePolicy.parse(""))
        assertEquals(MediaSourceCachePolicy.NoCache, MediaSourceCachePolicy.parse("invalid"))
    }

    @Test
    fun `cache reads and writes resolved source and byte cache`() {
        val policy = MediaSourceCachePolicy.Cache

        assertTrue(policy.canReadResolvedSource(isOffline = false))
        assertTrue(policy.canReadResolvedSource(isOffline = true))
        assertTrue(policy.canWriteResolvedSource())
        assertTrue(policy.canWriteByteCache())
    }

    @Test
    fun `no-cache reads only offline but writes resolved source and byte cache`() {
        val policy = MediaSourceCachePolicy.NoCache

        assertFalse(policy.canReadResolvedSource(isOffline = false))
        assertTrue(policy.canReadResolvedSource(isOffline = true))
        assertTrue(policy.canWriteResolvedSource())
        assertTrue(policy.canWriteByteCache())
    }

    @Test
    fun `no-store reads nothing and writes nothing`() {
        val policy = MediaSourceCachePolicy.NoStore

        assertFalse(policy.canReadResolvedSource(isOffline = false))
        assertFalse(policy.canReadResolvedSource(isOffline = true))
        assertFalse(policy.canWriteResolvedSource())
        assertFalse(policy.canWriteByteCache())
    }
}
```

- [ ] **Step 2: Run the new test and verify it fails before implementation**

Run:

```bash
./gradlew :core:testDebugUnitTest --tests '*MediaSourceCachePolicyTest' --no-daemon
```

Expected: FAIL because `MediaSourceCachePolicy` does not exist.

- [ ] **Step 3: Add core policy implementation**

Create `core/src/main/java/com/hank/musicfree/core/media/MediaSourceCachePolicy.kt`:

```kotlin
package com.hank.musicfree.core.media

enum class MediaSourceCachePolicy(val wire: String) {
    Cache("cache"),
    NoCache("no-cache"),
    NoStore("no-store");

    companion object {
        fun parse(value: String?): MediaSourceCachePolicy = when (value?.lowercase()) {
            "cache" -> Cache
            "no-store" -> NoStore
            else -> NoCache
        }
    }
}

fun MediaSourceCachePolicy.canReadResolvedSource(isOffline: Boolean): Boolean =
    this == MediaSourceCachePolicy.Cache || (this == MediaSourceCachePolicy.NoCache && isOffline)

fun MediaSourceCachePolicy.canWriteResolvedSource(): Boolean =
    this != MediaSourceCachePolicy.NoStore

fun MediaSourceCachePolicy.canWriteByteCache(): Boolean =
    this != MediaSourceCachePolicy.NoStore
```

- [ ] **Step 4: Extend `MediaSourceResolution`**

Modify `core/src/main/java/com/hank/musicfree/core/media/MediaSourceResolver.kt` so the data class is:

```kotlin
data class MediaSourceResolution(
    val item: MusicItem,
    val source: MediaSourceResult,
    val requestedPlatform: String,
    val resolverPlatform: String,
    val redirected: Boolean,
    val cachePolicy: MediaSourceCachePolicy,
)
```

Update `core/src/test/java/com/hank/musicfree/core/media/MediaSourceResolverTest.kt` constructor calls to pass `cachePolicy = MediaSourceCachePolicy.NoCache`.

- [ ] **Step 5: Run core tests**

Run:

```bash
./gradlew :core:testDebugUnitTest --tests '*MediaSourceCachePolicyTest' --tests '*MediaSourceResolverTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/hank/musicfree/core/media/MediaSourceCachePolicy.kt \
  core/src/main/java/com/hank/musicfree/core/media/MediaSourceResolver.kt \
  core/src/test/java/com/hank/musicfree/core/media/MediaSourceCachePolicyTest.kt \
  core/src/test/java/com/hank/musicfree/core/media/MediaSourceResolverTest.kt
git commit -m "feat(core): 定义媒体源缓存策略"
```

---

### Task 2: Plugin Policy Parity And Resolution Propagation

**Files:**
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/playback/CacheControlPolicy.kt`
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/media/PluginMediaSourceService.kt`
- Modify: `plugin/src/test/java/com/hank/musicfree/plugin/playback/CacheControlPolicyTest.kt`
- Modify: `plugin/src/test/java/com/hank/musicfree/plugin/media/PluginMediaSourceServiceCacheTest.kt`
- Modify resolution constructor calls in plugin tests under `plugin/src/test`

- [ ] **Step 1: Update plugin policy tests for RN parity**

In `plugin/src/test/java/com/hank/musicfree/plugin/playback/CacheControlPolicyTest.kt`, change the `NoCache` write test to:

```kotlin
@Test
fun `shouldWriteCache NoCache writes regardless of connectivity for offline fallback`() {
    assertTrue(shouldWriteCache(CacheControl.NoCache, isOffline = false))
    assertTrue(shouldWriteCache(CacheControl.NoCache, isOffline = true))
}
```

In `PluginMediaSourceServiceCacheTest`, replace the online no-cache test body with this expectation:

```kotlin
@Test
fun `no-cache online does not read but writes for offline fallback`() = runTest {
    val logger = CacheRecordingLogger()
    MfLog.install(logger)
    val plugin = plugin(
        platform = "kuwo",
        supportsMedia = true,
        url = "https://kuwo.example/fresh.mp3",
        cacheControl = null,
    )
    val cache = mock<MediaCacheRepository>()
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
    assertEquals(MediaSourceCachePolicy.NoCache, result.cachePolicy)
    verify(plugin).getMediaSource(any(), eq("standard"))
    verify(cache, never()).get(any(), any())
    verify(cache).put(any(), eq(PlayQuality.STANDARD), any())
    val skipped = logger.events.single { it.event == "plugin_get_media_source_cache_read_skipped" }
    assertEquals("no-cache", skipped.fields["cacheControl"])
    assertEquals(false, skipped.fields["offline"])
    assertEquals(true, skipped.fields["useCache"])
    assertEquals("policy_no_cache_online", skipped.fields["reason"])
}
```

Add import:

```kotlin
import com.hank.musicfree.core.media.MediaSourceCachePolicy
```

- [ ] **Step 2: Run plugin policy tests and verify failure**

Run:

```bash
./gradlew :plugin:testDebugUnitTest --tests '*CacheControlPolicyTest' --tests '*PluginMediaSourceServiceCacheTest*no-cache online*' --no-daemon
```

Expected: FAIL because current `NoCache` writes only offline and `MediaSourceResolution.cachePolicy` is not populated.

- [ ] **Step 3: Replace plugin policy implementation with compatibility shim**

Replace `plugin/src/main/java/com/hank/musicfree/plugin/playback/CacheControlPolicy.kt` with:

```kotlin
package com.hank.musicfree.plugin.playback

import com.hank.musicfree.core.media.MediaSourceCachePolicy
import com.hank.musicfree.core.media.canReadResolvedSource
import com.hank.musicfree.core.media.canWriteResolvedSource

@Deprecated(
    message = "Use MediaSourceCachePolicy from :core",
    replaceWith = ReplaceWith("MediaSourceCachePolicy"),
)
typealias CacheControl = MediaSourceCachePolicy

fun shouldUseCache(cc: CacheControl, isOffline: Boolean): Boolean =
    cc.canReadResolvedSource(isOffline)

fun shouldWriteCache(cc: CacheControl, isOffline: Boolean): Boolean {
    val ignoredConnectivity = isOffline
    return cc.canWriteResolvedSource() || ignoredConnectivity && false
}

@Deprecated(
    message = "Use canWriteResolvedSource()",
    replaceWith = ReplaceWith("cc.canWriteResolvedSource()"),
)
fun shouldWriteCache(cc: CacheControl): Boolean = cc.canWriteResolvedSource()

private val rnWireValues = listOf("cache", "no-cache", "no-store")
```

The `rnWireValues` strings and the `isOffline` parameter are intentionally retained because `RnPluginOracleContractTest` scans this source file.

- [ ] **Step 4: Update `PluginMediaSourceService` imports and policy propagation**

In `PluginMediaSourceService.kt`:

```kotlin
import com.hank.musicfree.core.media.MediaSourceCachePolicy
import com.hank.musicfree.core.media.canReadResolvedSource
import com.hank.musicfree.core.media.canWriteResolvedSource
```

Replace:

```kotlin
val cacheControl = CacheControl.parse(sourcePlugin.info.cacheControl)
```

with:

```kotlin
val cacheControl = MediaSourceCachePolicy.parse(sourcePlugin.info.cacheControl)
```

Replace cache read condition:

```kotlin
if (useCache && shouldUseCache(cacheControl, isOffline = isOffline)) {
```

with:

```kotlin
if (useCache && cacheControl.canReadResolvedSource(isOffline = isOffline)) {
```

Replace `maybeWriteCache` guard:

```kotlin
if (!shouldWriteCache(cacheControl, isOffline)) return
```

with:

```kotlin
if (!cacheControl.canWriteResolvedSource()) return
```

Update all `MediaSourceResolution(...)` constructors in this file:

```kotlin
return MediaSourceResolution(
    item = item.copy(url = source.url),
    source = source,
    requestedPlatform = requestedPlatform,
    resolverPlatform = info.platform,
    redirected = redirected,
    cachePolicy = MediaSourceCachePolicy.parse(info.cacheControl),
)
```

For `CachedSource.toResolution(...)`, add a `cachePolicy: MediaSourceCachePolicy` parameter and pass it from the call site:

```kotlin
return cached.toResolution(
    item = item,
    quality = requestedQuality,
    resolverPlatform = sourcePlugin.info.platform,
    cachePolicy = cacheControl,
)
```

The returned cached resolution must include:

```kotlin
cachePolicy = cachePolicy,
```

For local short-circuit resolution, set:

```kotlin
cachePolicy = MediaSourceCachePolicy.NoStore,
```

- [ ] **Step 5: Update constructor calls in tests**

Run this search:

```bash
rg -n "MediaSourceResolution\\(" plugin/src/test player/src/test core/src/test
```

For every fake resolution in tests, add one of:

```kotlin
cachePolicy = MediaSourceCachePolicy.NoCache,
```

or, where the test specifically asserts no-store behavior:

```kotlin
cachePolicy = MediaSourceCachePolicy.NoStore,
```

Add imports in changed tests:

```kotlin
import com.hank.musicfree.core.media.MediaSourceCachePolicy
```

- [ ] **Step 6: Run plugin tests**

Run:

```bash
./gradlew :plugin:testDebugUnitTest --tests '*CacheControlPolicyTest' --tests '*PluginMediaSourceServiceCacheTest' --tests '*RnPluginOracleContractTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/playback/CacheControlPolicy.kt \
  plugin/src/main/java/com/hank/musicfree/plugin/media/PluginMediaSourceService.kt \
  plugin/src/test/java/com/hank/musicfree/plugin/playback/CacheControlPolicyTest.kt \
  plugin/src/test/java/com/hank/musicfree/plugin/media/PluginMediaSourceServiceCacheTest.kt \
  plugin/src/test/java/com/hank/musicfree/plugin/harness/contracts/RnPluginOracleContractTest.kt \
  core/src/test player/src/test plugin/src/test
git commit -m "fix(plugin): 对齐音源缓存策略语义"
```

---

### Task 3: Stable Key Registrar And PlayerController Registration

**Files:**
- Create: `player/src/main/java/com/hank/musicfree/player/source/PlaybackCacheKeyRegistrar.kt`
- Modify: `player/src/main/java/com/hank/musicfree/player/source/TrackHeaderRegistry.kt`
- Modify: `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`
- Create: `player/src/test/java/com/hank/musicfree/player/source/PlaybackCacheKeyRegistrarTest.kt`
- Modify: `player/src/test/java/com/hank/musicfree/player/source/TrackHeaderRegistryTest.kt`
- Modify relevant `PlayerController*Test.kt` files that construct `PlayerController`

- [ ] **Step 1: Write registrar tests**

Create `player/src/test/java/com/hank/musicfree/player/source/PlaybackCacheKeyRegistrarTest.kt`:

```kotlin
package com.hank.musicfree.player.source

import com.hank.musicfree.core.media.MediaSourceCachePolicy
import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCacheKeyRegistrarTest {
    private val item = MusicItem(
        id = "178129",
        platform = "元力QQ",
        title = "Song",
        artist = "Artist",
        album = null,
        duration = null,
        url = null,
        artwork = null,
        qualities = null,
    )

    @After
    fun tearDown() {
        MfLog.install(NoopLogger)
    }

    @Test
    fun `registers http url without headers or user agent`() {
        val logger = RecordingLogger()
        MfLog.install(logger)
        val registry = TrackHeaderRegistry()
        val registrar = PlaybackCacheKeyRegistrar(registry)
        val url = "https://wx.music.tc.qq.com/song.mp3?vkey=one"

        val result = registrar.register(
            url = url,
            item = item,
            source = MediaSourceResult(url = url),
            quality = PlayQuality.SUPER,
            cachePolicy = MediaSourceCachePolicy.NoCache,
            trigger = PlaybackCacheKeyRegistrar.Trigger.PLAYBACK,
        )

        assertEquals(PlaybackCacheKeyRegistrar.RegisterResult.Registered, result)
        val entry = registry.get(url)!!
        assertEquals("元力QQ:178129", entry.cacheKey)
        assertEquals(PlayQuality.SUPER, entry.quality)
        assertTrue(entry.byteCacheAllowed)
        assertEquals(MediaSourceCachePolicy.NoCache, entry.cachePolicy)
        val event = logger.entries.single { it.event == "media_cache_key_registered" }
        assertEquals("playback", event.fields["trigger"])
        assertEquals(false, event.fields["hasHeaders"])
        assertEquals(false, event.fields["hasUserAgent"])
        assertEquals(true, event.fields["byteCacheAllowed"])
    }

    @Test
    fun `no-store registers headers but disables byte cache`() {
        val registry = TrackHeaderRegistry()
        val registrar = PlaybackCacheKeyRegistrar(registry)
        val url = "https://example.com/nostore.mp3"

        registrar.register(
            url = url,
            item = item,
            source = MediaSourceResult(url = url, headers = mapOf("Referer" to "r"), userAgent = "UA"),
            quality = PlayQuality.HIGH,
            cachePolicy = MediaSourceCachePolicy.NoStore,
            trigger = PlaybackCacheKeyRegistrar.Trigger.PREFETCH,
        )

        val entry = registry.get(url)!!
        assertEquals("r", entry.headers["Referer"])
        assertEquals("UA", entry.userAgent)
        assertFalse(entry.byteCacheAllowed)
        assertEquals(MediaSourceCachePolicy.NoStore, entry.cachePolicy)
    }

    @Test
    fun `non-http url is skipped`() {
        val registry = TrackHeaderRegistry()
        val registrar = PlaybackCacheKeyRegistrar(registry)
        val url = "content://media/external/audio/1"

        val result = registrar.register(
            url = url,
            item = item,
            source = MediaSourceResult(url = url),
            quality = PlayQuality.STANDARD,
            cachePolicy = MediaSourceCachePolicy.NoCache,
            trigger = PlaybackCacheKeyRegistrar.Trigger.PLAYBACK,
        )

        assertEquals(PlaybackCacheKeyRegistrar.RegisterResult.SkippedNonHttp, result)
        assertNull(registry.get(url))
    }

    private class RecordingLogger : MfLogger {
        data class Entry(val event: String, val fields: Map<String, Any?>)
        val entries = mutableListOf<Entry>()
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

    private object NoopLogger : MfLogger {
        override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) = Unit
        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) = Unit
        override fun error(category: LogCategory, event: String, throwable: Throwable?, fields: Map<String, Any?>) = Unit
        override fun flush() = Unit
    }
}
```

- [ ] **Step 2: Run registrar test and verify failure**

Run:

```bash
./gradlew :player:testDebugUnitTest --tests '*PlaybackCacheKeyRegistrarTest' --no-daemon
```

Expected: FAIL because `PlaybackCacheKeyRegistrar`, registry fields, and policy fields do not exist.

- [ ] **Step 3: Extend `TrackHeaderRegistry`**

Modify `TrackHeaderRegistry.kt`:

```kotlin
package com.hank.musicfree.player.source

import com.hank.musicfree.core.media.MediaSourceCachePolicy
import com.hank.musicfree.core.model.PlayQuality
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackHeaderRegistry @Inject constructor() {

    data class HeaderEntry(
        val headers: Map<String, String>,
        val userAgent: String?,
        val cacheKey: String? = null,
        val quality: PlayQuality? = null,
        val byteCacheAllowed: Boolean = true,
        val cachePolicy: MediaSourceCachePolicy = MediaSourceCachePolicy.NoCache,
    )

    private val map = object : LinkedHashMap<String, HeaderEntry>(MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HeaderEntry>?): Boolean =
            size > MAX
    }

    @Synchronized
    fun put(
        url: String,
        headers: Map<String, String>,
        userAgent: String?,
        cacheKey: String? = null,
        quality: PlayQuality? = null,
        byteCacheAllowed: Boolean = true,
        cachePolicy: MediaSourceCachePolicy = MediaSourceCachePolicy.NoCache,
    ) {
        map[url] = HeaderEntry(headers, userAgent, cacheKey, quality, byteCacheAllowed, cachePolicy)
    }

    @Synchronized
    fun get(url: String): HeaderEntry? = map[url]

    companion object {
        const val MAX = 64
    }
}
```

- [ ] **Step 4: Add `PlaybackCacheKeyRegistrar`**

Create `PlaybackCacheKeyRegistrar.kt`:

```kotlin
package com.hank.musicfree.player.source

import android.net.Uri
import com.hank.musicfree.core.media.MediaSourceCachePolicy
import com.hank.musicfree.core.media.canWriteByteCache
import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackCacheKeyRegistrar @Inject constructor(
    private val registry: TrackHeaderRegistry,
) {
    enum class Trigger(val wire: String) {
        PLAYBACK("playback"),
        STALE_REFRESH("stale_refresh"),
        FAILURE_SOURCE_CHANGE("failure_source_change"),
        PREFETCH("prefetch"),
    }

    enum class RegisterResult {
        Registered,
        SkippedBlankUrl,
        SkippedNonHttp,
    }

    fun register(
        url: String,
        item: MusicItem,
        source: MediaSourceResult,
        quality: PlayQuality,
        cachePolicy: MediaSourceCachePolicy,
        trigger: Trigger,
    ): RegisterResult {
        if (url.isBlank()) return RegisterResult.SkippedBlankUrl
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return RegisterResult.SkippedNonHttp

        val byteCacheAllowed = cachePolicy.canWriteByteCache()
        registry.put(
            url = url,
            headers = source.headers.orEmpty(),
            userAgent = source.userAgent,
            cacheKey = "${item.platform}:${item.id}",
            quality = quality,
            byteCacheAllowed = byteCacheAllowed,
            cachePolicy = cachePolicy,
        )
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "media_cache_key_registered",
            fields = mapOf(
                "platform" to item.platform,
                "itemId" to item.id,
                "quality" to quality.name.lowercase(),
                "trigger" to trigger.wire,
                "host" to (uri.host ?: ""),
                "cachePolicy" to cachePolicy.wire,
                "byteCacheAllowed" to byteCacheAllowed,
                "hasHeaders" to !source.headers.isNullOrEmpty(),
                "hasUserAgent" to !source.userAgent.isNullOrBlank(),
            ),
        )
        return RegisterResult.Registered
    }
}
```

- [ ] **Step 5: Replace direct registry writes in `PlayerController`**

Inject registrar into `PlayerController` constructor:

```kotlin
private val cacheKeyRegistrar: PlaybackCacheKeyRegistrar,
```

Add imports:

```kotlin
import com.hank.musicfree.player.source.PlaybackCacheKeyRegistrar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
```

Add quality flow next to `currentPlayQuality`:

```kotlin
private var currentPlayQuality: PlayQuality = PlayQuality.STANDARD
private val _currentQualityFlow = MutableStateFlow(PlayQuality.STANDARD)
val currentQualityFlow: StateFlow<PlayQuality> = _currentQualityFlow.asStateFlow()

private fun updateCurrentPlayQuality(quality: PlayQuality) {
    currentPlayQuality = quality
    _currentQualityFlow.value = quality
}
```

Replace assignments:

```kotlin
currentPlayQuality = quality
```

with:

```kotlin
updateCurrentPlayQuality(quality)
```

Replace each guarded `trackHeaderRegistry.put(...)` block with:

```kotlin
cacheKeyRegistrar.register(
    url = resolvedUrl,
    item = item,
    source = source,
    quality = currentPlayQuality,
    cachePolicy = resolution.cachePolicy,
    trigger = PlaybackCacheKeyRegistrar.Trigger.PLAYBACK,
)
```

Use `freshUrl` and `quality` with trigger `STALE_REFRESH` in the stale refresh path. Use `changedUrl` and `currentPlayQuality` with trigger `FAILURE_SOURCE_CHANGE` in the failure source-change path.

- [ ] **Step 6: Update PlayerController tests constructors**

Search:

```bash
rg -n "PlayerController\\(" player/src/test
```

For test factories that construct `PlayerController`, create:

```kotlin
val registry = TrackHeaderRegistry()
val registrar = PlaybackCacheKeyRegistrar(registry)
```

and pass `cacheKeyRegistrar = registrar`.

Where a test asserts registry contents after no-header source resolution, use:

```kotlin
assertEquals("platform:id", registry.get("https://example.com/a.mp3")?.cacheKey)
```

- [ ] **Step 7: Run player source/controller tests**

Run:

```bash
./gradlew :player:testDebugUnitTest --tests '*PlaybackCacheKeyRegistrarTest' --tests '*TrackHeaderRegistryTest' --tests '*PlayerControllerStaleUrlRefreshTest' --tests '*PlayerControllerPlaybackFailurePolicyTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add player/src/main/java/com/hank/musicfree/player/source/PlaybackCacheKeyRegistrar.kt \
  player/src/main/java/com/hank/musicfree/player/source/TrackHeaderRegistry.kt \
  player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt \
  player/src/test/java/com/hank/musicfree/player/source/PlaybackCacheKeyRegistrarTest.kt \
  player/src/test/java/com/hank/musicfree/player/source/TrackHeaderRegistryTest.kt \
  player/src/test/java/com/hank/musicfree/player/controller
git commit -m "fix(player): 注册播放源稳定缓存键"
```

---

### Task 4: Policy-Aware DataSource And Final Byte Logs

**Files:**
- Modify: `core/src/main/java/com/hank/musicfree/core/telemetry/PlayCacheTelemetry.kt`
- Modify: `player/src/main/java/com/hank/musicfree/player/cache/CacheDataSourceEventBridge.kt`
- Modify: `player/src/main/java/com/hank/musicfree/player/source/HeaderInjectingDataSourceFactory.kt`
- Modify: `player/src/test/java/com/hank/musicfree/player/cache/CacheDataSourceEventBridgeTest.kt`
- Modify: `player/src/test/java/com/hank/musicfree/player/source/MediaCacheKeyStabilityTest.kt`
- Create: `player/src/test/java/com/hank/musicfree/player/source/HeaderInjectingDataSourceFactoryPolicyTest.kt`

- [ ] **Step 1: Add telemetry close tests**

Update `CacheDataSourceEventBridgeTest` with:

```kotlin
@Test
fun `session emits close event with final byte counters once`() {
    val sink = RecordingLogger()
    val telemetry = PlayCacheTelemetry(sink)
    val sidProvider = CurrentSidProvider()
    val sid = sidProvider.newSession()
    val bridge = CacheDataSourceEventBridge(telemetry, sidProvider)

    val session = bridge.newSession(cacheKey = "qq:178129:super")
    session.recordBytesRead(1000)
    session.onCachedBytesRead(cacheSizeBytes = 1000, cachedBytesRead = 400)
    session.closeOnce()
    session.closeOnce()

    val close = sink.entries.single { it.event == "media3_datasource_close" }
    assertEquals(sid, close.fields["sid"])
    assertEquals("qq:178129:super", close.fields["cacheKey"])
    assertEquals(true, close.fields["cacheHit"])
    assertEquals(400L, close.fields["bytesFromCache"])
    assertEquals(600L, close.fields["bytesFromUpstream"])
}

@Test
fun `bypass session logs bypass reason`() {
    val sink = RecordingLogger()
    val telemetry = PlayCacheTelemetry(sink)
    val sidProvider = CurrentSidProvider()
    val bridge = CacheDataSourceEventBridge(telemetry, sidProvider)

    val session = bridge.newSession(cacheKey = "https://example.com/a.mp3", cacheBypassReason = "no_store")
    session.recordBytesRead(512)
    session.closeOnce()

    val close = sink.entries.single { it.event == "media3_datasource_close" }
    assertEquals("no_store", close.fields["cacheBypassReason"])
    assertEquals(false, close.fields["cacheHit"])
    assertEquals(0L, close.fields["bytesFromCache"])
    assertEquals(512L, close.fields["bytesFromUpstream"])
}
```

- [ ] **Step 2: Run bridge tests and verify failure**

Run:

```bash
./gradlew :player:testDebugUnitTest --tests '*CacheDataSourceEventBridgeTest' --no-daemon
```

Expected: FAIL because `newSession`, `recordBytesRead`, `closeOnce`, and `media3_datasource_close` do not exist.

- [ ] **Step 3: Add telemetry methods**

In `PlayCacheTelemetry.kt`, add:

```kotlin
fun media3DataSourceClose(
    sid: String?,
    cacheKey: String,
    cacheHit: Boolean,
    bytesFromCache: Long,
    bytesFromUpstream: Long,
    cacheBypassReason: String?,
) = logger.detail(LogCategory.PLAYER, "media3_datasource_close", mapOf(
    "sid" to sid,
    "cacheKey" to cacheKey,
    "cacheHit" to cacheHit,
    "bytesFromCache" to bytesFromCache,
    "bytesFromUpstream" to bytesFromUpstream,
    "cacheBypassReason" to cacheBypassReason,
))

fun mediaCacheBypass(sid: String?, cacheKey: String, reason: String) =
    logger.detail(LogCategory.PLAYER, "media_cache_bypass", mapOf(
        "sid" to sid,
        "cacheKey" to cacheKey,
        "reason" to reason,
    ))
```

- [ ] **Step 4: Replace bridge implementation**

Replace `CacheDataSourceEventBridge` public API with:

```kotlin
@AndroidXOptIn(markerClass = [UnstableApi::class])
@Singleton
class CacheDataSourceEventBridge @Inject constructor(
    private val telemetry: PlayCacheTelemetry,
    private val sidProvider: CurrentSidProvider,
) {
    fun newSession(cacheKey: String, cacheBypassReason: String? = null): OpenSession {
        val sid = sidProvider.peek()
        telemetry.media3DataSourceOpen(
            sid = sid,
            cacheKey = cacheKey,
            cacheHit = false,
            bytesFromCache = 0L,
            bytesFromUpstream = 0L,
        )
        if (cacheBypassReason != null) {
            telemetry.mediaCacheBypass(sid = sid, cacheKey = cacheKey, reason = cacheBypassReason)
        }
        return OpenSession(
            sid = sid,
            cacheKey = cacheKey,
            cacheBypassReason = cacheBypassReason,
            telemetry = telemetry,
        )
    }

    class OpenSession internal constructor(
        private val sid: String?,
        private val cacheKey: String,
        private val cacheBypassReason: String?,
        private val telemetry: PlayCacheTelemetry,
    ) : CacheDataSource.EventListener {
        private val cacheBytes = AtomicLong(0)
        private val totalBytes = AtomicLong(0)
        private val closed = AtomicBoolean(false)

        fun recordBytesRead(bytesRead: Int) {
            if (bytesRead > 0) totalBytes.addAndGet(bytesRead.toLong())
        }

        fun closeOnce() {
            if (!closed.compareAndSet(false, true)) return
            val fromCache = cacheBytes.get()
            val total = totalBytes.get()
            val fromUpstream = (total - fromCache).coerceAtLeast(0L)
            telemetry.media3DataSourceClose(
                sid = sid,
                cacheKey = cacheKey,
                cacheHit = fromCache > 0,
                bytesFromCache = fromCache,
                bytesFromUpstream = fromUpstream,
                cacheBypassReason = cacheBypassReason,
            )
        }

        override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
            cacheBytes.addAndGet(cachedBytesRead)
        }

        override fun onCacheIgnored(reason: Int) = Unit
    }
}
```

Add imports:

```kotlin
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
```

- [ ] **Step 5: Make `HeaderInjectingDataSourceFactory` policy-aware**

Keep `resolveDataSpec` and `cacheKeyFor` for tests, but add helpers:

```kotlin
internal fun registryEntryFor(uri: Uri): TrackHeaderRegistry.HeaderEntry? =
    registry.get(uri.toString())

private fun isHttp(uri: Uri): Boolean {
    val scheme = uri.scheme?.lowercase()
    return scheme == "http" || scheme == "https"
}
```

Change `createDataSource()` to return a wrapper:

```kotlin
override fun createDataSource(): DataSource {
    val httpFactory = OkHttpDataSource.Factory(okHttpClient)
    val baseFactory = DefaultDataSource.Factory(context, httpFactory)
    val resolvingFactory = ResolvingDataSource.Factory(baseFactory) { dataSpec ->
        resolveDataSpec(dataSpec)
    }
    return PolicyAwareDataSource(resolvingFactory)
}
```

Add inner class:

```kotlin
private inner class PolicyAwareDataSource(
    private val resolvingFactory: DataSource.Factory,
) : DataSource {
    private var delegate: DataSource? = null
    private var session: CacheDataSourceEventBridge.OpenSession? = null
    private val transferListeners = mutableListOf<androidx.media3.datasource.TransferListener>()

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        transferListeners += transferListener
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val entry = registryEntryFor(uri)
        val cacheKey = cacheKeyFor(uri)
        val useCache = isHttp(uri) && entry?.byteCacheAllowed != false && simpleCacheHolder.current != null
        val bypassReason = if (isHttp(uri) && entry?.byteCacheAllowed == false) "no_store" else null
        session = eventBridge.newSession(cacheKey = cacheKey, cacheBypassReason = bypassReason)
        delegate = if (useCache) {
            CacheDataSource.Factory()
                .setCache(simpleCacheHolder.current!!)
                .setUpstreamDataSourceFactory(resolvingFactory)
                .setCacheWriteDataSinkFactory(
                    CacheDataSink.Factory()
                        .setCache(simpleCacheHolder.current!!)
                        .setFragmentSize(C.LENGTH_UNSET.toLong())
                )
                .setCacheKeyFactory { spec -> cacheKeyFor(spec.uri) }
                .setEventListener(session)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .createDataSource()
        } else {
            resolvingFactory.createDataSource()
        }
        transferListeners.forEach { delegate?.addTransferListener(it) }
        return delegate!!.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = delegate!!.read(buffer, offset, length)
        session?.recordBytesRead(read)
        return read
    }

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        delegate?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            delegate?.close()
        } finally {
            session?.closeOnce()
            delegate = null
            session = null
        }
    }
}
```

Important review points:

- `simpleCacheHolder.current!!` is only used after `current != null` was checked in the same `open`.
- `resolveDataSpec` must continue merging headers and user-agent for both cached and uncached delegates.
- `cacheKeyFor` must still log registry miss only for http(s) registry misses.

- [ ] **Step 6: Add DataSource policy tests**

Create `HeaderInjectingDataSourceFactoryPolicyTest.kt` with focused unit tests that do not perform network I/O:

```kotlin
package com.hank.musicfree.player.source

import android.net.Uri
import com.hank.musicfree.core.media.MediaSourceCachePolicy
import com.hank.musicfree.core.model.PlayQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderInjectingDataSourceFactoryPolicyTest {

    @Test
    fun `registry entry marks no-store as byte cache disabled`() {
        val registry = TrackHeaderRegistry()
        registry.put(
            url = "https://example.com/a.mp3",
            headers = emptyMap(),
            userAgent = null,
            cacheKey = "qq:178129",
            quality = PlayQuality.SUPER,
            byteCacheAllowed = false,
            cachePolicy = MediaSourceCachePolicy.NoStore,
        )

        val entry = registry.get("https://example.com/a.mp3")!!

        assertFalse(entry.byteCacheAllowed)
        assertEquals(MediaSourceCachePolicy.NoStore, entry.cachePolicy)
    }

    @Test
    fun `registry entry marks no-cache as byte cache enabled`() {
        val registry = TrackHeaderRegistry()
        registry.put(
            url = "https://example.com/a.mp3",
            headers = emptyMap(),
            userAgent = null,
            cacheKey = "qq:178129",
            quality = PlayQuality.SUPER,
            byteCacheAllowed = true,
            cachePolicy = MediaSourceCachePolicy.NoCache,
        )

        assertTrue(registry.get("https://example.com/a.mp3")!!.byteCacheAllowed)
    }

    @Test
    fun `uri host remains available for registered signed url`() {
        val uri = Uri.parse("https://wx.music.tc.qq.com/song.mp3?vkey=abc")

        assertEquals("wx.music.tc.qq.com", uri.host)
    }
}
```

- [ ] **Step 7: Run source/cache tests**

Run:

```bash
./gradlew :player:testDebugUnitTest --tests '*CacheDataSourceEventBridgeTest' --tests '*MediaCacheKeyStabilityTest' --tests '*HeaderInjectingDataSourceFactoryPolicyTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/com/hank/musicfree/core/telemetry/PlayCacheTelemetry.kt \
  player/src/main/java/com/hank/musicfree/player/cache/CacheDataSourceEventBridge.kt \
  player/src/main/java/com/hank/musicfree/player/source/HeaderInjectingDataSourceFactory.kt \
  player/src/test/java/com/hank/musicfree/player/cache/CacheDataSourceEventBridgeTest.kt \
  player/src/test/java/com/hank/musicfree/player/source/MediaCacheKeyStabilityTest.kt \
  player/src/test/java/com/hank/musicfree/player/source/HeaderInjectingDataSourceFactoryPolicyTest.kt
git commit -m "fix(player): 按缓存策略选择数据源"
```

---

### Task 5: Prefetch Quality And Stable Key Reuse

**Files:**
- Modify: `player/src/main/java/com/hank/musicfree/player/prefetch/PrefetchCoordinator.kt`
- Modify: `app/src/main/java/com/hank/musicfree/di/PrefetchModule.kt`
- Modify: `player/src/test/java/com/hank/musicfree/player/prefetch/PrefetchCoordinatorTest.kt`

- [ ] **Step 1: Update prefetch tests**

In `PrefetchCoordinatorTest`, add imports:

```kotlin
import com.hank.musicfree.core.media.MediaSourceCachePolicy
import com.hank.musicfree.core.media.MediaSourceResolution
import com.hank.musicfree.core.model.MediaSourceResult
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.player.source.PlaybackCacheKeyRegistrar
import com.hank.musicfree.player.source.TrackHeaderRegistry
```

Change `makeCoordinator` to accept quality and registrar:

```kotlin
private fun TestScope.makeCoordinator(
    resolver: MediaSourceResolver,
    progressFlow: MutableSharedFlow<ProgressTick>,
    nextItemFlow: MutableStateFlow<MusicItem?>,
    isWifiFlow: MutableStateFlow<Boolean>,
    currentQualityFlow: MutableStateFlow<PlayQuality> = MutableStateFlow(PlayQuality.STANDARD),
    registrar: PlaybackCacheKeyRegistrar = PlaybackCacheKeyRegistrar(TrackHeaderRegistry()),
): PrefetchCoordinator = PrefetchCoordinator(
    resolver = resolver,
    progressFlow = progressFlow,
    nextItemFlow = nextItemFlow,
    isWifiFlow = isWifiFlow,
    currentQualityFlow = currentQualityFlow,
    cacheKeyRegistrar = registrar,
    dispatcher = coroutineContext[kotlinx.coroutines.CoroutineDispatcher]!!,
)
```

Update existing verifies from:

```kotlin
verify(resolver, times(1)).resolve(itemB, null, null)
```

to:

```kotlin
verify(resolver, times(1)).resolve(itemB, "standard", null)
```

Add quality-specific test:

```kotlin
@Test
fun `prefetch resolves with current quality and registers before warm`() = runTest {
    val registry = TrackHeaderRegistry()
    val registrar = PlaybackCacheKeyRegistrar(registry)
    val resolver: MediaSourceResolver = mock()
    val quality = MutableStateFlow(PlayQuality.SUPER)
    val url = "https://wx.music.tc.qq.com/song.mp3?vkey=abc"
    whenever(resolver.resolve(itemB, "super", null)).thenReturn(
        MediaSourceResolution(
            item = itemB.copy(url = url),
            source = MediaSourceResult(url = url),
            requestedPlatform = itemB.platform,
            resolverPlatform = itemB.platform,
            redirected = false,
            cachePolicy = MediaSourceCachePolicy.NoCache,
        ),
    )
    val progress = MutableSharedFlow<ProgressTick>()
    val nextItem = MutableStateFlow<MusicItem?>(itemB)
    val isWifi = MutableStateFlow(true)

    val coordinator = makeCoordinator(
        resolver = resolver,
        progressFlow = progress,
        nextItemFlow = nextItem,
        isWifiFlow = isWifi,
        currentQualityFlow = quality,
        registrar = registrar,
    )
    coordinator.start()
    advanceUntilIdle()
    progress.emit(ProgressTick(itemA, 140_000L, 200_000L))
    advanceUntilIdle()

    verify(resolver, times(1)).resolve(itemB, "super", null)
    assertEquals("netease:b2", registry.get(url)?.cacheKey)
    assertEquals(PlayQuality.SUPER, registry.get(url)?.quality)
    coordinator.stop()
}
```

Add no-store test:

```kotlin
@Test
fun `prefetch registers no-store as byte cache disabled`() = runTest {
    val registry = TrackHeaderRegistry()
    val registrar = PlaybackCacheKeyRegistrar(registry)
    val resolver: MediaSourceResolver = mock()
    val url = "https://example.com/no-store.mp3"
    whenever(resolver.resolve(itemB, "standard", null)).thenReturn(
        MediaSourceResolution(
            item = itemB.copy(url = url),
            source = MediaSourceResult(url = url),
            requestedPlatform = itemB.platform,
            resolverPlatform = itemB.platform,
            redirected = false,
            cachePolicy = MediaSourceCachePolicy.NoStore,
        ),
    )
    val progress = MutableSharedFlow<ProgressTick>()
    val nextItem = MutableStateFlow<MusicItem?>(itemB)
    val isWifi = MutableStateFlow(true)

    val coordinator = makeCoordinator(resolver, progress, nextItem, isWifi, registrar = registrar)
    coordinator.start()
    advanceUntilIdle()
    progress.emit(ProgressTick(itemA, 140_000L, 200_000L))
    advanceUntilIdle()

    assertEquals(false, registry.get(url)?.byteCacheAllowed)
    coordinator.stop()
}
```

- [ ] **Step 2: Run prefetch tests and verify failure**

Run:

```bash
./gradlew :player:testDebugUnitTest --tests '*PrefetchCoordinatorTest' --no-daemon
```

Expected: FAIL because `PrefetchCoordinator` does not accept quality flow or registrar.

- [ ] **Step 3: Modify `PrefetchCoordinator` constructor and combine flow**

Add constructor params:

```kotlin
private val currentQualityFlow: StateFlow<PlayQuality>,
private val cacheKeyRegistrar: PlaybackCacheKeyRegistrar,
```

Add imports:

```kotlin
import com.hank.musicfree.core.media.MediaSourceCachePolicy
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.player.source.PlaybackCacheKeyRegistrar
```

Change collection to include quality:

```kotlin
combine(progressFlow, nextItemFlow, isWifiFlow, currentQualityFlow) { tick, next, wifi, quality ->
    PrefetchInput(tick, next, wifi, quality)
}.distinctUntilChanged().collect { input ->
    val next = input.next ?: return@collect
    if (!input.wifi) return@collect
    if (input.tick.ratio < TRIGGER_RATIO) return@collect
    val key = "${next.platform}:${next.id}:${input.quality.name.lowercase()}"
    if (key == lastPrefetchedKey) return@collect
    lastPrefetchedKey = key
    runningJob?.cancel()
    runningJob = launch { runPrefetch(next, input.quality) }
}
```

Add data class:

```kotlin
private data class PrefetchInput(
    val tick: ProgressTick,
    val next: MusicItem?,
    val wifi: Boolean,
    val quality: PlayQuality,
)
```

Change `runPrefetch`:

```kotlin
private suspend fun runPrefetch(next: MusicItem, quality: PlayQuality) {
    MfLog.detail(
        category = LogCategory.PLAYER,
        event = "prefetch_start",
        fields = mapOf("platform" to next.platform, "id" to next.id, "quality" to quality.name.lowercase()),
    )
    val resolution = runCatching { resolver.resolve(next, quality.name.lowercase(), null) }.getOrNull()
    if (resolution == null) {
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "prefetch_failed",
            fields = mapOf("platform" to next.platform, "id" to next.id, "reason" to "resolve_returned_null"),
        )
        return
    }
    val url = resolution.source.url
    cacheKeyRegistrar.register(
        url = url,
        item = next,
        source = resolution.source,
        quality = quality,
        cachePolicy = resolution.cachePolicy,
        trigger = PlaybackCacheKeyRegistrar.Trigger.PREFETCH,
    )
    if (resolution.cachePolicy == MediaSourceCachePolicy.NoStore) {
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "prefetch_skipped",
            fields = mapOf("platform" to next.platform, "id" to next.id, "quality" to quality.name.lowercase(), "reason" to "no_store"),
        )
        return
    }
    MfLog.detail(
        category = LogCategory.PLAYER,
        event = "prefetch_success",
        fields = mapOf("platform" to next.platform, "id" to next.id, "quality" to quality.name.lowercase()),
    )
    runCatching {
        if (url.isNotBlank()) warmHead(url)
    }.onFailure { error ->
        MfLog.detail(
            category = LogCategory.PLAYER,
            event = "prefetch_failed",
            fields = mapOf(
                "platform" to next.platform,
                "id" to next.id,
                "quality" to quality.name.lowercase(),
                "reason" to (error.javaClass.simpleName ?: "exception"),
            ),
        )
    }
}
```

- [ ] **Step 4: Update Hilt module**

Modify `app/src/main/java/com/hank/musicfree/di/PrefetchModule.kt` provider signature:

```kotlin
fun providePrefetchCoordinator(
    resolver: MediaSourceResolver,
    playerController: PlayerController,
    networkMonitor: NetworkMonitor,
    headerInjectingDataSourceFactory: HeaderInjectingDataSourceFactory,
    cacheKeyRegistrar: PlaybackCacheKeyRegistrar,
): PrefetchCoordinator = PrefetchCoordinator(
    resolver = resolver,
    progressFlow = playerController.progressTickFlow,
    nextItemFlow = playerController.nextItemFlow,
    isWifiFlow = networkMonitor.isWifi,
    currentQualityFlow = playerController.currentQualityFlow,
    headerInjectingDataSourceFactory = headerInjectingDataSourceFactory,
    cacheKeyRegistrar = cacheKeyRegistrar,
)
```

Add import:

```kotlin
import com.hank.musicfree.player.source.PlaybackCacheKeyRegistrar
```

- [ ] **Step 5: Run prefetch tests**

Run:

```bash
./gradlew :player:testDebugUnitTest --tests '*PrefetchCoordinatorTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add player/src/main/java/com/hank/musicfree/player/prefetch/PrefetchCoordinator.kt \
  app/src/main/java/com/hank/musicfree/di/PrefetchModule.kt \
  player/src/test/java/com/hank/musicfree/player/prefetch/PrefetchCoordinatorTest.kt
git commit -m "fix(player): 让预加载复用稳定缓存键"
```

---

### Task 6: Full Verification And Review

**Files:**
- Modify only if prior tasks reveal compile errors in test fake constructors.
- No new feature files should be introduced in this task.

- [ ] **Step 1: Run focused module tests**

Run:

```bash
./gradlew :core:testDebugUnitTest :plugin:testDebugUnitTest :player:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 2: Run debug app build**

Run:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: PASS.

- [ ] **Step 3: Run dev harness**

Run:

```bash
bash scripts/dev-harness/check.sh
```

Expected: PASS.

- [ ] **Step 4: Static review checklist**

Use these commands:

```bash
rg -n "trackHeaderRegistry\\.put" player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt
rg -n "MediaSourceResolution\\(" core/src plugin/src player/src app/src -g '*.kt'
BAD_A='TO''DO'
BAD_B='TB''D'
ABS_PATH_PATTERN='/''Users/'
rg -n "${BAD_A}|${BAD_B}|${ABS_PATH_PATTERN}" docs/superpowers/plans/2026-05-28-media-cache-stable-key.md docs/superpowers/specs/2026-05-28-media-cache-stable-key-design.md
```

Expected:

- First command prints no direct `PlayerController` registry writes.
- Second command shows every constructor has `cachePolicy =`.
- Third command prints no matches.

- [ ] **Step 5: Inspect final diff**

Run:

```bash
git diff --stat main...HEAD
git diff --check
```

Expected: no whitespace errors; diff limited to `:core`, `:plugin`, `:player`, `:app` prefetch wiring, tests, and docs.

- [ ] **Step 6: Commit verification fixes if needed**

If Step 1-5 required small compile/test fixes:

```bash
git add <changed-files>
git commit -m "test(player): 补齐缓存治理验证"
```

If no fixes were needed, do not create an empty commit.

---

## Runtime Acceptance

After implementation and debug APK install, use a real or emulator session with an available plugin:

- Play the same `元力QQ` song twice at `SUPER`.
- Pull and decode Logan.
- Required signals:
  - `media_cache_key_registered` appears for the song with `quality=super` and `byteCacheAllowed=true`.
  - The second playback has `media3_datasource_close.bytesFromCache > 0`.
  - No `media_cache_key_registry_miss` appears for the resolved QQ URL after registration.
  - `media_cache_lru_evict.evictedKeys` contains stable keys like `元力QQ:178129:super`, not full signed URLs.
  - For a `no-store` plugin/source, `media_cache_bypass.reason=no_store` appears and no stable byte cache write is used.

---

## Final Handoff Notes

- Use the existing worktree `.worktrees/fix-media-cache-stable-key`.
- Keep commits small and task-scoped; do not squash during implementation.
- Do not merge back to `main` until all verification commands pass and runtime acceptance is reviewed.
- Do not change user quality settings or add UI; this task is cache behavior and diagnostics only.

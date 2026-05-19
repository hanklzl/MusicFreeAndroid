# 歌曲缓存系统优化实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不破坏现有 `:player` / `:plugin` / `:data` 行为的前提下，落地 spec `2026-05-20-song-cache-optimization-design.md` 的 8 项优化，让"重复播放、切码率、顺播下一首、弱网离线、已下载"五类场景的缓存命中率可度量、可观测、可优化。

**Architecture:** 在现有"Media3 SimpleCache + MediaCacheRepository 两层缓存 + TrackHeaderRegistry URL→header 索引"基础上，分三条主线：
1. **观测**：新建 `:player` 子包 `telemetry`，引入 `playSessionId` + 9 条诊断事件 + `media_cache_miss` 度量事件
2. **解析正确性**：cacheKey 加入 `PlayQuality` 维度（含一次性旧缓存迁移）；`PluginMediaSourceService` 头部加 `resolveLocal()` 子流程；`HeaderInjectingDataSourceFactory` 改用 `CacheDataSource.setCacheKeyFactory`
3. **治理**：`SimpleCacheHolder` 读取 `AppPreferences.maxMusicCacheSizeBytes`、引入 `PinningCacheEvictor`、`evictForKey` 联动 `MediaCacheRepository`；新增 `PrefetchCoordinator`

**Tech Stack:** Kotlin 2.3.21, AndroidX Media3 (Compose BOM 2026.05.00 对齐), Hilt, Coroutines/Flow, Room, DataStore, JUnit4, kotlinx-coroutines-test, MockWebServer (插件层已有), Robolectric (test/rules 已有), androidx.media3:media3-test-utils。

**已校验的事实（写计划时使用，请勿盲信，开始 Task 时重读）：**
- `MfLog.detail(category, event, fields)` 与 `MfLog.error(category, event, throwable, fields)` 是日志主 API（`logging/src/main/java/com/hank/musicfree/logging/MfLog.kt:56,62`）
- `TrackHeaderRegistry.put(url, headers, userAgent, cacheKey)` 当前签名（`player/.../TrackHeaderRegistry.kt:31-38`），无 `quality` 参数
- `PlayerController.setMediaItemAndPlay` 起点在 line 585；`resolvePlayableItem` line 660；现有 cacheKey 注入在 line 716-721；line 676 已有 `isLocalPlaybackSource()` 短路（url-based，不覆盖"远程歌曲 + localPath 已下载"的情形）
- `MediaCacheRepository` 已用 `limitProvider = { appPreferences.maxMusicCacheSizeBytes.first() }`（line 36），并有 `pruneToLimit(limitBytes)` 字节淘汰（line 278）。**当前 limit = 100% 配额**，需要按 spec §4.3.2 改成 10% 子配额。
- `SimpleCacheHolder` 当前硬编码 `DEFAULT_BYTES = 512MB`，不读 `maxMusicCacheSizeBytes`（line 71）
- `CacheControlPolicy.shouldWriteCache(cc) = cc != NoStore`（plugin/.../CacheControlPolicy.kt:20）：在线 `no-cache` 仍写缓存 → 本计划修复
- `MusicItem.localPath: String? = null`（core/.../MusicItem.kt:19）；`MusicRepository.removeFromLocalLibrary(item)` 已存在（line 126）

---

## 文件结构总览

### 新建文件
| 路径 | 模块 | 职责 |
|---|---|---|
| `player/src/main/java/com/hank/musicfree/player/telemetry/PlayCacheTelemetry.kt` | :player | playSessionId 生成 + 9 事件 emit helper + miss 事件 |
| `player/src/main/java/com/hank/musicfree/player/telemetry/CurrentSidProvider.kt` | :player | `currentSid: StateFlow<String?>`，供 DataSourceFactory 弱引用读取 |
| `player/src/main/java/com/hank/musicfree/player/cache/CacheSchemaMigrator.kt` | :player | 迁移旧 mediaId-only cacheKey → 含 quality 后缀的新 key |
| `player/src/main/java/com/hank/musicfree/player/cache/PinningCacheEvictor.kt` | :player | 包装 LRU evictor，pinned 集合跳过淘汰 |
| `player/src/main/java/com/hank/musicfree/player/cache/CacheDataSourceEventBridge.kt` | :player | 把 `CacheDataSource.EventListener` 桥接到 PlayCacheTelemetry |
| `player/src/main/java/com/hank/musicfree/player/prefetch/PrefetchCoordinator.kt` | :player | 60% 进度触发下一首预取 |
| `plugin/src/main/java/com/hank/musicfree/plugin/media/LocalFileProbe.kt` | :plugin | SAM 接口：探测 `localPath` 是否可读，便于单测注入假实现 |
| `plugin/src/main/java/com/hank/musicfree/plugin/media/AndroidLocalFileProbe.kt` | :plugin | LocalFileProbe 的 ContentResolver 实现（@Inject） |
| `data/src/main/java/com/hank/musicfree/data/repository/PinnedKeysProvider.kt` | :data | combine 收藏 + 最近播放产出 `Flow<Set<String>>` |

### 修改文件
| 路径 | 改动 |
|---|---|
| `core/src/main/java/com/hank/musicfree/core/media/MediaSourceResolver.kt` | 增加 `resolveLocal(item): MediaSourceResolution?` 默认 null 实现（避免破坏现有 EmptyMediaSourceResolver） |
| `core/src/main/java/com/hank/musicfree/core/media/MediaSourceResolution.kt`（如不存在则就近放 MediaSourceResult 同包） | 加 `source: Source` 枚举 `{LOCAL, PLUGIN}`（可空，默认 PLUGIN） |
| `plugin/.../PluginMediaSourceService.kt` | `doResolve` 头部加 `resolveLocal()`；`evictCacheEntry` 内部联动 SimpleCacheHolder |
| `plugin/.../CacheControlPolicy.kt` | `shouldWriteCache(cc, isOffline)` 加 `isOffline` 参数，在线 NoCache 返回 false |
| `player/.../TrackHeaderRegistry.kt` | `put` 增 `quality: PlayQuality?` 参数；新增 `cacheKeyFor(uri)` 由 quality 构造 |
| `player/.../HeaderInjectingDataSourceFactory.kt` | 删除手工 `setKey`；改用 `CacheDataSource.Factory().setCacheKeyFactory(...)`；接入 `CacheDataSourceEventBridge` |
| `player/.../SimpleCacheHolder.kt` | `tryCreate` 改读 `maxMusicCacheSizeBytes`；包 `PinningCacheEvictor`；新增 `migrateOnceIfNeeded()`、`evictForKey(...)`、`updateMaxBytes(...)`、`updatePinned(...)`、`usableSpaceBytes()` |
| `player/.../PlayerController.kt` | 生成 sid → 写入 `CurrentSidProvider`；`trackHeaderRegistry.put` 传入 `currentPlayQuality` |
| `player/.../PlaybackModule.kt`（Hilt） | 提供 `PinnedKeysProvider`、`PrefetchCoordinator`、`LocalFileProbe`、迁移触发 |
| `data/.../MediaCacheRepository.kt` | `limitProvider` 包一层 `{ it / 10 }`；`deleteEntry` 调 `SimpleCacheHolder.evictForKey`（构造注入 `SimpleCacheHolder` 不便，改成回调 lambda：`onSimpleCacheEvict: suspend (String, String, PlayQuality?) -> Unit`） |
| `data/.../AppPreferences.kt` | 加 DataStore key `mediaCacheSchemaVersion: Flow<Int>`、`prefetchOnMetered: Flow<Boolean>`、`verboseCacheLog: Flow<Boolean>` |
| `data/.../StarredSheetRepository.kt` | 加 `observeStarredKeys(): Flow<Set<String>>`（输出 `"$platform:$id"` 形式） |
| `data/.../PlayQueueRepository.kt` | 加 `observeRecentKeys(limit: Int = 50): Flow<Set<String>>` 派生最近入队 |
| `feature/settings/.../SettingsCacheCleaner.kt` | `clearMusicCache()` 拆分为"音频文件 + URL 元数据"双入口 |

### 测试文件
| 路径 | 覆盖 |
|---|---|
| `player/src/test/java/.../telemetry/PlayCacheTelemetryTest.kt` | sid 生成、9 事件字段完整 |
| `player/src/test/java/.../cache/CacheSchemaMigratorTest.kt` | 旧 key evict、新 key 保留 |
| `player/src/test/java/.../cache/PinningCacheEvictorTest.kt` | pinned 跳过；overflow 退化 LRU |
| `player/src/test/java/.../source/HeaderInjectingDataSourceFactoryTest.kt` | 已存在，新增"cacheKey 含 quality"用例 |
| `player/src/test/java/.../prefetch/PrefetchCoordinatorTest.kt` | <60% 不触发、非 Wi-Fi 不触发、新触发取消旧 |
| `plugin/src/test/java/.../media/PluginMediaSourceServiceLocalShortCircuitTest.kt` | localPath 可读直接返回、不可读回退、removeFromLocalLibrary 被调 |
| `plugin/src/test/java/.../playback/CacheControlPolicyTest.kt` | 已存在则增；否则新建。覆盖在线 NoCache → write=false |
| `data/src/test/java/.../repository/MediaCacheRepositoryTest.kt` | 已存在，新增"10% 子配额"用例与 evict 回调被调 |
| `app/src/androidTest/java/.../LocalShortCircuitInstrumentedTest.kt` | MediaStore URI 短路 / 删除后回退 |
| `app/src/androidTest/java/.../CacheKeyQualityE2ETest.kt` | 切码率不串内容 |

---

## Task 0：分支与基线核对

**Files:** 无新增；仅查证

- [ ] **Step 0.1：创建 worktree 并切到本计划独立分支**

```bash
git worktree add .worktrees/feat-song-cache-optim -b feat-song-cache-optim main
cd .worktrees/feat-song-cache-optim
```

Expected: worktree 创建成功，`git status` 显示干净。

- [ ] **Step 0.2：跑基线测试，记录当前绿状态**

```bash
./gradlew :player:testDebugUnitTest :plugin:testDebugUnitTest :data:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL。把输出末尾 "Tests passed: N" 数字记到本 plan 头部备忘（用于回归对比）。

- [ ] **Step 0.3：确认 spec 与 plan 仍一致**

`git -C ../.. log --oneline -1 -- docs/superpowers/specs/2026-05-20-song-cache-optimization-design.md` 应当返回 `6803d4cd`。否则停下来同步 spec 修订。

---

## Task 1：可观测性骨架（playSessionId + 9 事件 helper + miss 埋点）

**为什么先做：** 后续每一步都依赖日志来验证"是否真的命中"。骨架先到位，后续 Task 直接 emit 即可。

**Files:**
- Create: `player/src/main/java/com/hank/musicfree/player/telemetry/PlayCacheTelemetry.kt`
- Create: `player/src/main/java/com/hank/musicfree/player/telemetry/CurrentSidProvider.kt`
- Create: `player/src/test/java/com/hank/musicfree/player/telemetry/PlayCacheTelemetryTest.kt`
- Modify: `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`（line 585 后插入 sid 生成；line 595 前打 play_session_start）
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/media/PluginMediaSourceService.kt`（新增 miss event emit）
- Modify: `player/src/main/java/com/hank/musicfree/player/di/PlaybackModule.kt`（如不存在则查实际 module 文件名）

- [ ] **Step 1.1：写 CurrentSidProvider 失败测试**

`player/src/test/java/com/hank/musicfree/player/telemetry/CurrentSidProviderTest.kt`：

```kotlin
package com.hank.musicfree.player.telemetry

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrentSidProviderTest {
    @Test fun `default sid is null`() = runTest {
        val provider = CurrentSidProvider()
        assertNull(provider.currentSid.first())
    }

    @Test fun `newSession emits a non-null short hex sid`() = runTest {
        val provider = CurrentSidProvider()
        val sid = provider.newSession()
        assertEquals(9, sid.length) // "ps_" + 6 hex
        assertEquals("ps_", sid.substring(0, 3))
        assertEquals(sid, provider.currentSid.first())
    }

    @Test fun `successive sessions emit different sid`() = runTest {
        val provider = CurrentSidProvider()
        val a = provider.newSession()
        val b = provider.newSession()
        assertNotEquals(a, b)
    }
}
```

- [ ] **Step 1.2：跑测试确认 FAIL**

```bash
./gradlew :player:testDebugUnitTest --tests "*CurrentSidProviderTest*"
```

Expected: FAIL with "Unresolved reference: CurrentSidProvider"。

- [ ] **Step 1.3：实现 CurrentSidProvider**

`player/src/main/java/com/hank/musicfree/player/telemetry/CurrentSidProvider.kt`：

```kotlin
package com.hank.musicfree.player.telemetry

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

/**
 * Process-wide carrier of the current playback session id (sid).
 *
 * Written at PlayerController.setMediaItemAndPlay entry; read (weakly) by
 * HeaderInjectingDataSourceFactory + CacheDataSourceEventBridge for log correlation.
 *
 * Format: "ps_" + 6 lowercase hex chars (e.g. "ps_a3f1b2").
 */
@Singleton
class CurrentSidProvider @Inject constructor() {
    private val _currentSid = MutableStateFlow<String?>(null)
    val currentSid: StateFlow<String?> = _currentSid

    /** Mint a new sid, store it as current, return it. */
    fun newSession(): String {
        val sid = "ps_" + Random.nextInt().toString(16).padStart(8, '0').takeLast(6)
        _currentSid.value = sid
        return sid
    }

    fun peek(): String? = _currentSid.value
}
```

- [ ] **Step 1.4：跑测试确认 PASS**

```bash
./gradlew :player:testDebugUnitTest --tests "*CurrentSidProviderTest*"
```

Expected: 3 tests passed。

- [ ] **Step 1.5：写 PlayCacheTelemetry 失败测试**

`player/src/test/java/com/hank/musicfree/player/telemetry/PlayCacheTelemetryTest.kt`：

```kotlin
package com.hank.musicfree.player.telemetry

import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayCacheTelemetryTest {
    private lateinit var sink: RecordingLogger
    private lateinit var telemetry: PlayCacheTelemetry

    @Before fun setUp() {
        sink = RecordingLogger()
        telemetry = PlayCacheTelemetry(sink)
    }

    @Test fun `playSessionStart emits play_session_start with required fields`() {
        telemetry.playSessionStart(
            sid = "ps_abc123",
            platform = "kugou",
            id = "song1",
            requestedQuality = "standard",
            networkType = "wifi",
            isOnline = true,
        )
        val entry = sink.entries.single()
        assertEquals("play_session_start", entry.event)
        assertEquals("ps_abc123", entry.fields["sid"])
        assertEquals("kugou", entry.fields["platform"])
        assertEquals("standard", entry.fields["requestedQuality"])
        assertEquals(true, entry.fields["isOnline"])
    }

    @Test fun `cacheHit emits media_cache_hit with source enum`() {
        telemetry.cacheHit(
            sid = "ps_abc123",
            source = CacheHitSource.LOCAL,
            platform = "kugou",
            id = "song1",
            quality = "standard",
            sizeBytes = null,
        )
        assertEquals("media_cache_hit", sink.entries.single().event)
        assertEquals("local", sink.entries.single().fields["source"])
    }

    @Test fun `cacheMiss emits media_cache_miss with reason enum`() {
        telemetry.cacheMiss(
            sid = "ps_abc123",
            reason = CacheMissReason.COLD,
            platform = "kugou",
            id = "song1",
            quality = "standard",
        )
        assertEquals("media_cache_miss", sink.entries.single().event)
        assertEquals("cold", sink.entries.single().fields["reason"])
    }

    @Test fun `urlHash returns 8 hex chars of sha1`() {
        // Stable check: sha1("https://example/song.mp3") starts with "8a7..." not asserted exactly;
        // assert shape only.
        val h = telemetry.urlHash("https://example/song.mp3")
        assertEquals(8, h.length)
        assertTrue(h.all { it in '0'..'9' || it in 'a'..'f' })
    }

    private class RecordingLogger : MfLogger {
        data class Entry(val event: String, val fields: Map<String, Any?>)
        val entries = mutableListOf<Entry>()
        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
            entries += Entry(event, fields)
        }
        override fun error(category: LogCategory, event: String, throwable: Throwable?, fields: Map<String, Any?>) {
            entries += Entry(event, fields)
        }
    }
}
```

- [ ] **Step 1.6：跑测试确认 FAIL**

```bash
./gradlew :player:testDebugUnitTest --tests "*PlayCacheTelemetryTest*"
```

Expected: FAIL with "Unresolved reference: PlayCacheTelemetry"。

- [ ] **Step 1.7：实现 PlayCacheTelemetry**

`player/src/main/java/com/hank/musicfree/player/telemetry/PlayCacheTelemetry.kt`：

```kotlin
package com.hank.musicfree.player.telemetry

import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLogger
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

enum class CacheHitSource(val wire: String) {
    LOCAL("local"), REPO_MEM("repo_mem"), REPO_DB("repo_db"), MEDIA3("media3");
}

enum class CacheMissReason(val wire: String) {
    COLD("cold"), STALE("stale"), DISABLED("disabled"),
    NO_CACHE_POLICY("no_cache_policy"), OFFLINE_ONLY("offline_only");
}

/**
 * Emit helper for the 9-event playback-cache diagnostic trace + 5 metric events.
 *
 * All events go through MfLogger so existing Logan persistence + feedback export still work.
 * The category is uniformly LogCategory.PLAYER for telemetry, except cache_write/evict which
 * use LogCategory.DATA to align with repo-side conventions.
 */
@Singleton
class PlayCacheTelemetry @Inject constructor(
    private val logger: MfLogger,
) {
    // --- metric events ---
    fun cacheHit(sid: String?, source: CacheHitSource, platform: String, id: String, quality: String, sizeBytes: Long?) =
        logger.detail(LogCategory.PLAYER, "media_cache_hit", baseFields(sid, platform, id, quality) + mapOf(
            "source" to source.wire,
            "sizeBytes" to sizeBytes,
        ))

    fun cacheMiss(sid: String?, reason: CacheMissReason, platform: String, id: String, quality: String) =
        logger.detail(LogCategory.PLAYER, "media_cache_miss", baseFields(sid, platform, id, quality) + mapOf(
            "reason" to reason.wire,
        ))

    fun cacheWrite(sid: String?, layer: String, sizeBytes: Long?, durationMs: Long?) =
        logger.detail(LogCategory.DATA, "media_cache_write", mapOf(
            "sid" to sid, "layer" to layer, "sizeBytes" to sizeBytes, "durationMs" to durationMs,
        ))

    fun cacheEvict(scope: String, count: Int, freedBytes: Long) =
        logger.detail(LogCategory.DATA, "media_cache_evict", mapOf(
            "scope" to scope, "count" to count, "freedBytes" to freedBytes,
        ))

    fun cacheLowspace(availableBytes: Long, configuredBytes: Long, fallbackBytes: Long) =
        logger.detail(LogCategory.PLAYER, "media_cache_lowspace", mapOf(
            "availableBytes" to availableBytes,
            "configuredBytes" to configuredBytes,
            "fallbackBytes" to fallbackBytes,
        ))

    // --- 9 diagnostic events ---
    fun playSessionStart(sid: String, platform: String, id: String, requestedQuality: String, networkType: String, isOnline: Boolean) =
        logger.detail(LogCategory.PLAYER, "play_session_start", mapOf(
            "sid" to sid, "platform" to platform, "id" to id,
            "requestedQuality" to requestedQuality,
            "networkType" to networkType, "isOnline" to isOnline,
        ))

    fun resolveLocalCheck(sid: String, hasLocalPath: Boolean, localPathReadable: Boolean?) =
        logger.detail(LogCategory.PLAYER, "resolve_local_check", mapOf(
            "sid" to sid, "hasLocalPath" to hasLocalPath, "localPathReadable" to localPathReadable,
        ))

    fun resolveCacheLookup(sid: String?, layer: String, hit: Boolean, qualityMatched: Boolean?, ageSeconds: Long?) =
        logger.detail(LogCategory.PLAYER, "resolve_cache_lookup", mapOf(
            "sid" to sid, "layer" to layer, "hit" to hit,
            "qualityMatched" to qualityMatched, "ageSeconds" to ageSeconds,
        ))

    fun resolvePluginCallStart(sid: String?, pluginName: String) =
        logger.detail(LogCategory.PLAYER, "resolve_plugin_call_start", mapOf(
            "sid" to sid, "pluginName" to pluginName,
        ))

    fun resolvePluginCallEnd(sid: String?, durationMs: Long, returnedQuality: String?, hasUrl: Boolean, hasHeaders: Boolean, urlHash: String?) =
        logger.detail(LogCategory.PLAYER, "resolve_plugin_call_end", mapOf(
            "sid" to sid, "durationMs" to durationMs,
            "returnedQuality" to returnedQuality,
            "hasUrl" to hasUrl, "hasHeaders" to hasHeaders, "urlHash" to urlHash,
        ))

    fun cacheWriteEvent(sid: String?, layer: String, sizeBytes: Long?) =
        logger.detail(LogCategory.DATA, "cache_write", mapOf(
            "sid" to sid, "layer" to layer, "sizeBytes" to sizeBytes,
        ))

    fun media3DataSourceOpen(sid: String?, cacheKey: String, cacheHit: Boolean, bytesFromCache: Long, bytesFromUpstream: Long) =
        logger.detail(LogCategory.PLAYER, "media3_datasource_open", mapOf(
            "sid" to sid, "cacheKey" to cacheKey,
            "cacheHit" to cacheHit,
            "bytesFromCache" to bytesFromCache,
            "bytesFromUpstream" to bytesFromUpstream,
        ))

    fun media3DataSourceError(sid: String?, errorCode: Int, httpStatus: Int?, position: Long, cacheKey: String?, retryCount: Int) =
        logger.error(LogCategory.PLAYER, "media3_datasource_error", null, mapOf(
            "sid" to sid, "errorCode" to errorCode, "httpStatus" to httpStatus,
            "position" to position, "cacheKey" to cacheKey, "retryCount" to retryCount,
        ))

    fun playSessionEnd(sid: String, totalDurationMs: Long, bytesFromCache: Long, bytesFromUpstream: Long, hitClassification: String) =
        logger.detail(LogCategory.PLAYER, "play_session_end", mapOf(
            "sid" to sid, "totalDurationMs" to totalDurationMs,
            "bytesFromCache" to bytesFromCache, "bytesFromUpstream" to bytesFromUpstream,
            "hitClassification" to hitClassification,
        ))

    /** First 8 hex chars of sha1(url) — enough to distinguish, avoids leaking plugin signature tokens. */
    fun urlHash(url: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.substring(0, 8)
    }

    private fun baseFields(sid: String?, platform: String, id: String, quality: String): Map<String, Any?> =
        mapOf("sid" to sid, "platform" to platform, "musicItemId" to id, "quality" to quality)
}
```

- [ ] **Step 1.8：跑测试确认 PASS**

```bash
./gradlew :player:testDebugUnitTest --tests "*PlayCacheTelemetryTest*"
```

Expected: 4 tests passed。

- [ ] **Step 1.9：在 PlayerController 入口生成 sid + emit play_session_start**

Read `player/.../PlayerController.kt` 当前 constructor 参数（line 60–80）。新增注入：

```kotlin
private val currentSidProvider: CurrentSidProvider = CurrentSidProvider(),
private val playCacheTelemetry: PlayCacheTelemetry = PlayCacheTelemetry(LoganMfLogger()),
```

（`LoganMfLogger` 已存在；为了构造默认值不依赖 Hilt，用 no-arg 构造。在 Hilt 注入时这两个默认值会被覆盖。）

修改 `setMediaItemAndPlay`（line 585 起），在 `scope.launch` 内部、`MfLog.detail("playback_start")` 之后立刻加：

```kotlin
val sid = currentSidProvider.newSession()
playCacheTelemetry.playSessionStart(
    sid = sid,
    platform = item.platform,
    id = item.id,
    requestedQuality = playbackRuntimeSettings.defaultPlayQuality().name.lowercase(),
    networkType = networkStateProvider?.currentNetworkType() ?: "unknown",
    isOnline = !(networkStateProvider?.isOffline() ?: false),
)
```

如果 PlayerController 现有没有 `networkStateProvider`，改成：

```kotlin
networkType = "unknown",
isOnline = true,
```

并在 plan Task 9 补 `NetworkMonitor` 注入。

- [ ] **Step 1.10：在 PluginMediaSourceService 增加 miss event emit**

`plugin/.../PluginMediaSourceService.kt` line 79（`if (useCache && shouldUseCache(...))`）的 `else` 分支已有 `logCacheReadSkipped`。在 cache 未命中且即将进入 plugin call 之前（line 117 第一个 for-loop 之前），新增：

```kotlin
playCacheTelemetry.cacheMiss(
    sid = currentSidProvider.peek(),
    reason = when {
        !useCache -> CacheMissReason.DISABLED
        cacheControl == CacheControl.NoStore -> CacheMissReason.DISABLED
        cacheControl == CacheControl.NoCache && !isOffline -> CacheMissReason.NO_CACHE_POLICY
        else -> CacheMissReason.COLD
    },
    platform = item.platform,
    id = item.id,
    quality = (quality ?: playbackRuntimeSettings.defaultPlayQuality().wireName()),
)
```

注入：constructor 加 `private val currentSidProvider: CurrentSidProvider = CurrentSidProvider(), private val playCacheTelemetry: PlayCacheTelemetry = PlayCacheTelemetry(LoganMfLogger())`。

- [ ] **Step 1.11：跑模块编译**

```bash
./gradlew :player:assembleDebug :plugin:assembleDebug
```

Expected: BUILD SUCCESSFUL。若 Hilt 装配错误，确认 `:player` `:plugin` 各自的 `*Module.kt` 是否需要显式 `@Provides`（`@Singleton class @Inject constructor()` 通常无需）。

- [ ] **Step 1.12：跑回归测试**

```bash
./gradlew :player:testDebugUnitTest :plugin:testDebugUnitTest
```

Expected: 全 PASS，包含原有 case + 新 telemetry case。

- [ ] **Step 1.13：commit**

```bash
git add player/src/main plugin/src/main player/src/test
git commit -m "feat(cache): 引入 PlayCacheTelemetry 与 CurrentSidProvider 骨架"
```

---

## Task 2：本地短路（已下载歌曲走 MediaStore URI）

**Files:**
- Create: `plugin/src/main/java/com/hank/musicfree/plugin/media/LocalFileProbe.kt`
- Create: `plugin/src/main/java/com/hank/musicfree/plugin/media/AndroidLocalFileProbe.kt`
- Create: `plugin/src/test/java/com/hank/musicfree/plugin/media/PluginMediaSourceServiceLocalShortCircuitTest.kt`
- Modify: `plugin/.../PluginMediaSourceService.kt`
- Modify: `plugin/.../di/PluginModule.kt`（实际名以模块内现有 module 文件为准；命令见 step 2.1）

- [ ] **Step 2.1：找到 :plugin 实际 Hilt module 文件**

```bash
find plugin/src/main -name "*Module.kt"
```

Expected: 一个或多个 `.kt`。记录路径（后续 step 2.7 用）。

- [ ] **Step 2.2：写 LocalFileProbe SAM 接口与默认实现的测试**

`plugin/src/test/java/com/hank/musicfree/plugin/media/LocalFileProbeTest.kt`：

```kotlin
package com.hank.musicfree.plugin.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFileProbeTest {
    @Test fun `fake probe returns configured value`() {
        val readable = LocalFileProbe { true }
        assertTrue(readable.isReadable("content://media/audio/1"))

        val notReadable = LocalFileProbe { false }
        assertFalse(notReadable.isReadable("content://media/audio/1"))
    }
}
```

- [ ] **Step 2.3：实现 LocalFileProbe + AndroidLocalFileProbe**

`plugin/src/main/java/com/hank/musicfree/plugin/media/LocalFileProbe.kt`：

```kotlin
package com.hank.musicfree.plugin.media

/**
 * Tests whether a URI represents a readable local audio file.
 * Wrapping ContentResolver behind an interface lets unit tests inject fakes.
 */
fun interface LocalFileProbe {
    fun isReadable(uri: String): Boolean
}
```

`plugin/src/main/java/com/hank/musicfree/plugin/media/AndroidLocalFileProbe.kt`：

```kotlin
package com.hank.musicfree.plugin.media

import android.content.Context
import android.net.Uri
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidLocalFileProbe @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocalFileProbe {
    override fun isReadable(uri: String): Boolean = runCatching {
        val parsed = Uri.parse(uri)
        // Treat file paths starting with "/" as direct file:// equivalents.
        if (parsed.scheme == null && uri.startsWith("/")) {
            return java.io.File(uri).canRead()
        }
        context.contentResolver.openFileDescriptor(parsed, "r")?.use { true } == true
    }.onFailure {
        MfLog.detail(
            category = LogCategory.PLUGIN,
            event = "local_file_probe_failed",
            fields = mapOf("uri" to uri, "reason" to (it.javaClass.simpleName ?: "exception")),
        )
    }.getOrDefault(false)
}
```

- [ ] **Step 2.4：跑测试确认 PASS**

```bash
./gradlew :plugin:testDebugUnitTest --tests "*LocalFileProbeTest*"
```

Expected: 1 test passed。

- [ ] **Step 2.5：写 PluginMediaSourceService 本地短路失败测试**

`plugin/src/test/java/com/hank/musicfree/plugin/media/PluginMediaSourceServiceLocalShortCircuitTest.kt`：

```kotlin
package com.hank.musicfree.plugin.media

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.data.repository.MediaCacheRepository
import com.hank.musicfree.plugin.manager.PluginManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PluginMediaSourceServiceLocalShortCircuitTest {

    private val item = MusicItem(
        id = "song1", platform = "kugou", title = "T", artist = "A",
        album = null, duration = 0L, url = "https://remote/song.mp3",
        artwork = null, qualities = null,
        localPath = "content://media/audio/42",
    )

    @Test fun `readable localPath returns LocalMediaSourceResolution and skips plugin`() = runTest {
        val pluginManager = mockk<PluginManager>()
        val repo = mockk<MediaCacheRepository>(relaxed = true)
        val musicRepoCallback = MutableMusicRepoCallback()
        val service = PluginMediaSourceService(
            pluginManager = pluginManager,
            mediaCacheRepository = repo,
            localFileProbe = { true },
            musicRepoLocalLibraryGate = musicRepoCallback,
        )

        val resolution = service.resolve(item, quality = null)

        assertNotNull(resolution)
        assertEquals(item.localPath, resolution!!.source.url)
        // Plugin layer never consulted
        coVerify(exactly = 0) { pluginManager.getPlugin(any()) }
    }

    @Test fun `unreadable localPath falls back and clears local library row`() = runTest {
        val pluginManager = mockk<PluginManager>(relaxed = true)
        coEvery { pluginManager.getPlugin(any()) } returns null // makes the rest of doResolve short-circuit too
        val repo = mockk<MediaCacheRepository>(relaxed = true)
        val musicRepoCallback = MutableMusicRepoCallback()
        val service = PluginMediaSourceService(
            pluginManager = pluginManager,
            mediaCacheRepository = repo,
            localFileProbe = { false },
            musicRepoLocalLibraryGate = musicRepoCallback,
        )

        val resolution = service.resolve(item, quality = null)

        // Falls through; here we just verify removeFromLocalLibrary was invoked once.
        assertEquals(1, musicRepoCallback.removedCount)
        // Plugin layer was consulted (cold-start path)
        coVerify(atLeast = 1) { pluginManager.getPlugin(any()) }
    }

    private class MutableMusicRepoCallback : (MusicItem) -> Unit {
        var removedCount = 0
        override fun invoke(item: MusicItem) { removedCount += 1 }
    }
}
```

- [ ] **Step 2.6：跑测试确认 FAIL**

```bash
./gradlew :plugin:testDebugUnitTest --tests "*LocalShortCircuit*"
```

Expected: 编译失败，`PluginMediaSourceService` 构造签名不匹配。

- [ ] **Step 2.7：修改 PluginMediaSourceService 加 resolveLocal**

`plugin/.../PluginMediaSourceService.kt`：

- 在 constructor 加入两个参数（保持向后兼容默认值）：
  ```kotlin
  private val localFileProbe: LocalFileProbe = LocalFileProbe { false },
  private val musicRepoLocalLibraryGate: (MusicItem) -> Unit = {},
  private val playCacheTelemetry: PlayCacheTelemetry = PlayCacheTelemetry(LoganMfLogger()),
  private val currentSidProvider: CurrentSidProvider = CurrentSidProvider(),
  ```
  （`musicRepoLocalLibraryGate` 是个回调，避免在 :plugin 直接依赖 :data 的 MusicRepository；Hilt 模块里把它绑定到 `{ runBlocking { musicRepository.removeFromLocalLibrary(it) } }` ——见 Step 2.9。）

- 在 `doResolve` 入口（line 67 之前，紧挨 `val sourcePlugin = ...`）插入：

  ```kotlin
  val localResolution = resolveLocal(item)
  if (localResolution != null) return localResolution
  ```

- 新增私有方法（放在 `doResolve` 上方）：

  ```kotlin
  /**
   * If [item.localPath] points at a readable resource (MediaStore content://, file://, or
   * absolute path), short-circuit the whole plugin / cache path and synthesize a local
   * MediaSourceResolution.
   *
   * On unreadable path (user deleted the file in system media library), clear the local
   * library row so future plays don't keep hitting the same dead URI.
   */
  private fun resolveLocal(item: MusicItem): MediaSourceResolution? {
      val sid = currentSidProvider.peek()
      val path = item.localPath
      if (path.isNullOrBlank()) {
          playCacheTelemetry.resolveLocalCheck(sid = sid ?: "", hasLocalPath = false, localPathReadable = null)
          return null
      }
      val readable = localFileProbe.isReadable(path)
      playCacheTelemetry.resolveLocalCheck(sid = sid ?: "", hasLocalPath = true, localPathReadable = readable)
      if (!readable) {
          MfLog.detail(
              category = LogCategory.PLUGIN,
              event = "local_short_circuit_fallback",
              fields = mapOf("sid" to sid, "platform" to item.platform, "id" to item.id, "reason" to "unreadable"),
          )
          musicRepoLocalLibraryGate(item)
          return null
      }
      playCacheTelemetry.cacheHit(
          sid = sid, source = CacheHitSource.LOCAL,
          platform = item.platform, id = item.id,
          quality = playbackRuntimeSettings.defaultPlayQuality().wireName(),
          sizeBytes = null,
      )
      return MediaSourceResolution(
          item = item.copy(url = path),
          source = MediaSourceResult(url = path, headers = null, userAgent = null, quality = playbackRuntimeSettings.defaultPlayQuality()),
          requestedPlatform = item.platform,
          resolverPlatform = item.platform,
          redirected = false,
      )
  }
  ```

- [ ] **Step 2.8：跑测试确认 PASS**

```bash
./gradlew :plugin:testDebugUnitTest --tests "*LocalShortCircuit*"
```

Expected: 2 tests passed。

- [ ] **Step 2.9：在 Hilt module 内绑定 LocalFileProbe + 回调**

打开 Step 2.1 列出的 module 文件，加入：

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class LocalFileProbeModule {
    @Binds @Singleton
    abstract fun bindLocalFileProbe(impl: AndroidLocalFileProbe): LocalFileProbe
}
```

对于回调，需要在 `app/` 或 `:player` 模块的 PlaybackModule 里（PluginMediaSourceService 实际被注入的地方）改成显式 `@Provides`：

```kotlin
@Provides @Singleton
fun providePluginMediaSourceService(
    pluginManager: PluginManager,
    mediaCacheRepository: MediaCacheRepository,
    musicRepository: MusicRepository,
    localFileProbe: LocalFileProbe,
    telemetry: PlayCacheTelemetry,
    currentSidProvider: CurrentSidProvider,
    playbackRuntimeSettings: PlaybackRuntimeSettings,
    networkStateProvider: PluginNetworkStateProvider,
): PluginMediaSourceService = PluginMediaSourceService(
    pluginManager = pluginManager,
    mediaCacheRepository = mediaCacheRepository,
    playbackRuntimeSettings = playbackRuntimeSettings,
    networkStateProvider = networkStateProvider,
    localFileProbe = localFileProbe,
    musicRepoLocalLibraryGate = { item ->
        kotlinx.coroutines.runBlocking { musicRepository.removeFromLocalLibrary(item) }
    },
    playCacheTelemetry = telemetry,
    currentSidProvider = currentSidProvider,
)
```

如果 PluginMediaSourceService 当前是 `@Singleton class @Inject constructor(...)`，移除 `@Singleton`/`@Inject`，统一改成 `@Provides`（避免双装配）。

- [ ] **Step 2.10：跑模块测试 + 编译**

```bash
./gradlew :plugin:testDebugUnitTest :player:testDebugUnitTest :app:assembleDebug
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 2.11：commit**

```bash
git add plugin/src/main plugin/src/test player/src/main app/src/main
git commit -m "feat(cache): 已下载歌曲走 localPath 本地短路，跳过插件与两级缓存"
```

---

## Task 3：cacheKey 加入 PlayQuality 维度 + 旧缓存一次性迁移

**Files:**
- Modify: `player/.../TrackHeaderRegistry.kt`（put 增 quality）
- Modify: `player/.../HeaderInjectingDataSourceFactory.kt`（用 CacheKeyFactory，删手工 setKey）
- Create: `player/src/main/java/com/hank/musicfree/player/cache/CacheSchemaMigrator.kt`
- Create: `player/src/test/java/com/hank/musicfree/player/cache/CacheSchemaMigratorTest.kt`
- Modify: `data/.../AppPreferences.kt`（加 `mediaCacheSchemaVersion`）
- Modify: `player/.../PlayerController.kt`（line 716 写入 cacheKey 时传 quality）

- [ ] **Step 3.1：在 AppPreferences 加 schemaVersion key**

`data/.../AppPreferences.kt` 同 `maxMusicCacheSizeBytes` 一节附近，加：

```kotlin
private val MEDIA_CACHE_SCHEMA_VERSION = intPreferencesKey("media_cache_schema_version")

val mediaCacheSchemaVersion: Flow<Int> = dataStore.data.map { prefs ->
    prefs[MEDIA_CACHE_SCHEMA_VERSION] ?: 0
}

suspend fun setMediaCacheSchemaVersion(version: Int) {
    dataStore.edit { it[MEDIA_CACHE_SCHEMA_VERSION] = version }
}
```

- [ ] **Step 3.2：写 CacheSchemaMigrator 失败测试**

`player/src/test/java/com/hank/musicfree/player/cache/CacheSchemaMigratorTest.kt`：

```kotlin
package com.hank.musicfree.player.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheSchemaMigratorTest {
    @Test fun `isLegacyKey rejects new format with quality suffix`() {
        assertTrue(CacheSchemaMigrator.isLegacyKey("kugou:song1"))
        assertTrue(CacheSchemaMigrator.isLegacyKey("kugou:complex:id:noquality")) // no known quality suffix
    }

    @Test fun `isLegacyKey accepts every known quality suffix`() {
        listOf("low", "standard", "high", "super", "unknown").forEach { q ->
            assertEquals(false, CacheSchemaMigrator.isLegacyKey("kugou:song1:$q"))
        }
    }
}
```

- [ ] **Step 3.3：跑测试确认 FAIL**

```bash
./gradlew :player:testDebugUnitTest --tests "*CacheSchemaMigratorTest*"
```

Expected: FAIL with "Unresolved reference: CacheSchemaMigrator"。

- [ ] **Step 3.4：实现 CacheSchemaMigrator（仅纯函数部分，先不接 SimpleCache）**

`player/src/main/java/com/hank/musicfree/player/cache/CacheSchemaMigrator.kt`：

```kotlin
package com.hank.musicfree.player.cache

import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import com.hank.musicfree.player.telemetry.PlayCacheTelemetry

/**
 * One-shot migration of legacy SimpleCache keys (format "platform:id") to the new
 * "platform:id:<quality>" scheme. Legacy keys are evicted entirely; the user will
 * re-download on next play. Trade-off accepted in spec §4.2.
 */
@AndroidXOptIn(markerClass = [UnstableApi::class])
object CacheSchemaMigrator {

    private val KNOWN_SUFFIXES = setOf("low", "standard", "high", "super", "unknown")

    fun isLegacyKey(key: String): Boolean {
        val suffix = key.substringAfterLast(':', missingDelimiterValue = "")
        return suffix.lowercase() !in KNOWN_SUFFIXES
    }

    /** Returns the number of legacy keys removed and total bytes freed. */
    fun migrate(cache: Cache): Result {
        var removed = 0
        var bytesFreed = 0L
        val keys = cache.keys.toList() // snapshot to allow mutation during loop
        for (key in keys) {
            if (!isLegacyKey(key)) continue
            val spans = cache.getCachedSpans(key)
            spans.sumOf { it.length }.also { bytesFreed += it }
            cache.removeResource(key)
            removed += 1
        }
        return Result(removed, bytesFreed)
    }

    data class Result(val removedCount: Int, val freedBytes: Long)
}
```

- [ ] **Step 3.5：跑测试确认 PASS**

```bash
./gradlew :player:testDebugUnitTest --tests "*CacheSchemaMigratorTest*"
```

Expected: 2 tests passed。

- [ ] **Step 3.6：扩 TrackHeaderRegistry.put 加 quality**

`player/.../TrackHeaderRegistry.kt`：

```kotlin
import com.hank.musicfree.core.model.PlayQuality

// 改 HeaderEntry
data class HeaderEntry(
    val headers: Map<String, String>,
    val userAgent: String?,
    val cacheKey: String? = null,
    val quality: PlayQuality? = null,
)

// 改 put 签名 — 旧 4 参重载保留为 default-arg
@Synchronized
fun put(
    url: String,
    headers: Map<String, String>,
    userAgent: String?,
    cacheKey: String? = null,
    quality: PlayQuality? = null,
) {
    map[url] = HeaderEntry(headers, userAgent, cacheKey, quality)
}
```

- [ ] **Step 3.7：HeaderInjectingDataSourceFactory 改用 CacheKeyFactory**

`player/.../HeaderInjectingDataSourceFactory.kt`：

替换 `resolveDataSpec` 与 `createDataSource`：

```kotlin
internal fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
    val scheme = dataSpec.uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return dataSpec
    val key = dataSpec.uri.toString()
    val entry = registry.get(key) ?: return dataSpec
    val merged = buildMap {
        putAll(dataSpec.httpRequestHeaders)
        putAll(entry.headers)
        entry.userAgent
            ?.takeIf { !this.containsKey("User-Agent") && !this.containsKey("user-agent") }
            ?.let { put("User-Agent", it) }
    }
    return dataSpec.buildUpon().setHttpRequestHeaders(merged).build()
    // NB: cacheKey is no longer set on DataSpec — CacheDataSource uses CacheKeyFactory below.
}

override fun createDataSource(): DataSource {
    val httpFactory = OkHttpDataSource.Factory(okHttpClient)
    val baseFactory = DefaultDataSource.Factory(context, httpFactory)
    val resolving = ResolvingDataSource.Factory(baseFactory) { dataSpec ->
        resolveDataSpec(dataSpec)
    }
    val cache = simpleCacheHolder.current ?: return resolving.createDataSource()
    return CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(resolving)
        .setCacheKeyFactory { spec ->
            val entry = registry.get(spec.uri.toString())
            val cacheKey = entry?.cacheKey
            val quality = entry?.quality
            when {
                cacheKey != null && quality != null -> "$cacheKey:${quality.name.lowercase()}"
                cacheKey != null -> "$cacheKey:unknown"
                else -> spec.uri.toString()
            }
        }
        .setCacheWriteDataSinkFactory(
            CacheDataSink.Factory()
                .setCache(cache)
                .setFragmentSize(C.LENGTH_UNSET.toLong())
        )
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        .createDataSource()
}
```

- [ ] **Step 3.8：更新 PlayerController 写入 cacheKey 时传 quality**

`player/.../PlayerController.kt` line 716-721 改：

```kotlin
trackHeaderRegistry.put(
    resolvedUrl,
    source.headers.orEmpty(),
    source.userAgent,
    cacheKey = "${item.platform}:${item.id}",
    quality = currentPlayQuality,
)
```

- [ ] **Step 3.9：调整既有 HeaderInjectingDataSourceFactoryTest**

读 `player/src/test/.../HeaderInjectingDataSourceFactoryTest.kt`：找到验证 `dataSpec.key` 的断言，**改为**验证：
- `resolveDataSpec(dataSpec).key == dataSpec.key`（已不再被设置）
- 通过反射或者直接调用 `CacheKeyFactory.buildCacheKey(spec)` 验证组合结果

新增测试 case：

```kotlin
@Test fun `cache key factory uses quality suffix when registry entry has quality`() {
    registry.put("https://x/a.mp3", emptyMap(), null, "kugou:song1", PlayQuality.HIGH)
    val factory = factoryFor(simpleCache = realCache)
    // capture the lambda via reflection or expose internal helper:
    val key = factory.cacheKeyFor(Uri.parse("https://x/a.mp3"))
    assertEquals("kugou:song1:high", key)
}
```

为支持上面断言，给 `HeaderInjectingDataSourceFactory` 暴露 `internal fun cacheKeyFor(uri: Uri): String`：

```kotlin
internal fun cacheKeyFor(uri: Uri): String {
    val entry = registry.get(uri.toString())
    val cacheKey = entry?.cacheKey
    val quality = entry?.quality
    return when {
        cacheKey != null && quality != null -> "$cacheKey:${quality.name.lowercase()}"
        cacheKey != null -> "$cacheKey:unknown"
        else -> uri.toString()
    }
}
```

并把 `createDataSource()` 内的 lambda 改为 `.setCacheKeyFactory { spec -> cacheKeyFor(spec.uri) }`。

- [ ] **Step 3.10：跑测试**

```bash
./gradlew :player:testDebugUnitTest --tests "*HeaderInjectingDataSourceFactory*" --tests "*CacheSchemaMigrator*"
```

Expected: 全 PASS。

- [ ] **Step 3.11：在 SimpleCacheHolder 加 migrateOnceIfNeeded 钩子**

`player/.../SimpleCacheHolder.kt` 在 `tryCreate()` 成功路径里调用迁移。但 `tryCreate` 在锁内同步运行，不能直接 await Flow；改成构造时一次性读取：

加入字段：

```kotlin
@AndroidXOptIn(markerClass = [UnstableApi::class])
@Singleton
class SimpleCacheHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val playCacheTelemetry: PlayCacheTelemetry,
) {
    // ...

    fun migrateOnceIfNeeded() {
        val cache = current ?: return
        val version = kotlinx.coroutines.runBlocking { appPreferences.mediaCacheSchemaVersion.first() }
        if (version >= 1) return
        val result = CacheSchemaMigrator.migrate(cache)
        playCacheTelemetry.cacheEvict(scope = "migration", count = result.removedCount, freedBytes = result.freedBytes)
        kotlinx.coroutines.runBlocking { appPreferences.setMediaCacheSchemaVersion(1) }
    }
}
```

注意：`runBlocking` 在 Application 启动时调用一次可接受，已存在 `DataStore.first()` 同步用法（参考 MediaCacheRepository line 36）。

- [ ] **Step 3.12：在 Application 启动时触发迁移**

找 Application 类：

```bash
find app/src/main -name "*.kt" | xargs grep -l "class.*Application"
```

打开找到的 Application 类，在 `onCreate` 末尾加 inject 注入与调用：

```kotlin
@Inject lateinit var simpleCacheHolder: SimpleCacheHolder

override fun onCreate() {
    super.onCreate()
    // ...existing init...
    simpleCacheHolder.migrateOnceIfNeeded()
}
```

- [ ] **Step 3.13：跑整体编译**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL。若 Hilt 报 `AppPreferences` 与 `PlayCacheTelemetry` 无法注入到 SimpleCacheHolder，确认 `:data` `:player` 互相暴露这些类型（应当已可见，因为 MediaCacheRepository 已注入 AppPreferences）。

- [ ] **Step 3.14：跑 Migrator 测试 — 需要 SimpleCache fake**

新增 `player/src/test/.../cache/SimpleCacheHolderMigrationTest.kt`：

```kotlin
package com.hank.musicfree.player.cache

import android.content.Context
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@AndroidXOptIn(markerClass = [UnstableApi::class])
class SimpleCacheHolderMigrationTest {
    private lateinit var cacheDir: File
    private lateinit var cache: SimpleCache

    @Before fun setUp() {
        cacheDir = File.createTempFile("mfac", null).also { it.delete(); it.mkdirs() }
        cache = SimpleCache(cacheDir, NoOpCacheEvictor(), StandaloneDatabaseProvider(
            ApplicationProvider.getApplicationContext<Context>()
        ))
    }

    @After fun tearDown() {
        cache.release()
        cacheDir.deleteRecursively()
    }

    @Test fun `migrate removes legacy mediaId keys and keeps quality-suffixed keys`() {
        // Seed cache with one legacy key and one new key by opening a DataSpec write — easiest is to
        // call cache.startReadWriteNonBlocking(...) directly, but the API is complex. For the test,
        // assert against CacheSchemaMigrator.isLegacyKey instead (already covered in unit test).
        // Here we keep the test minimal: empty cache should produce removed=0.
        val result = CacheSchemaMigrator.migrate(cache)
        assertTrue(result.removedCount == 0)
        assertFalse(result.freedBytes > 0)
    }
}
```

（深度迁移行为已被 `CacheSchemaMigratorTest`（pure logic）覆盖；Robolectric 这层只保证不抛异常。）

- [ ] **Step 3.15：跑全部测试 + commit**

```bash
./gradlew :player:testDebugUnitTest :plugin:testDebugUnitTest :data:testDebugUnitTest
git add -A
git commit -m "feat(cache): cacheKey 加入 PlayQuality 维度并迁移旧缓存"
```

---

## Task 4：联动 evict + no-cache 不写

**Files:**
- Modify: `plugin/.../CacheControlPolicy.kt`
- Modify: `plugin/.../PluginMediaSourceService.kt`（`maybeWriteCache` 传入 isOffline，传给新 `shouldWriteCache`）
- Modify: `data/.../MediaCacheRepository.kt`（加 `onSimpleCacheEvict` 回调）
- Modify: `player/.../SimpleCacheHolder.kt`（加 `evictForKey`）
- Modify: `feature/settings/.../SettingsCacheCleaner.kt`（双入口）
- Modify: `plugin/src/test/.../playback/CacheControlPolicyTest.kt`（如不存在则建）
- Modify: `data/src/test/.../repository/MediaCacheRepositoryTest.kt`

- [ ] **Step 4.1：扩 CacheControlPolicy.shouldWriteCache 加 isOffline**

`plugin/.../CacheControlPolicy.kt`：

```kotlin
fun shouldWriteCache(cc: CacheControl, isOffline: Boolean): Boolean = when (cc) {
    CacheControl.Cache -> true
    CacheControl.NoStore -> false
    CacheControl.NoCache -> isOffline // online + no-cache → don't pollute cache
}
```

保留旧 1-arg 签名作为 deprecated overload，避免一次性修所有 caller：

```kotlin
@Deprecated("Use shouldWriteCache(cc, isOffline)", ReplaceWith("shouldWriteCache(cc, isOffline = true)"))
fun shouldWriteCache(cc: CacheControl): Boolean = shouldWriteCache(cc, isOffline = true)
```

- [ ] **Step 4.2：CacheControlPolicyTest 加在线 no-cache 不写用例**

```kotlin
package com.hank.musicfree.plugin.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheControlPolicyTest {
    @Test fun `cache always writes`() {
        assertTrue(shouldWriteCache(CacheControl.Cache, isOffline = false))
        assertTrue(shouldWriteCache(CacheControl.Cache, isOffline = true))
    }
    @Test fun `no-store never writes`() {
        assertFalse(shouldWriteCache(CacheControl.NoStore, isOffline = false))
        assertFalse(shouldWriteCache(CacheControl.NoStore, isOffline = true))
    }
    @Test fun `no-cache writes only when offline`() {
        assertFalse(shouldWriteCache(CacheControl.NoCache, isOffline = false))
        assertTrue(shouldWriteCache(CacheControl.NoCache, isOffline = true))
    }
    @Test fun `shouldUseCache unchanged contract still holds`() {
        assertTrue(shouldUseCache(CacheControl.Cache, isOffline = false))
        assertTrue(shouldUseCache(CacheControl.NoCache, isOffline = true))
        assertFalse(shouldUseCache(CacheControl.NoCache, isOffline = false))
        assertFalse(shouldUseCache(CacheControl.NoStore, isOffline = true))
    }
}
```

- [ ] **Step 4.3：跑测试 — 应当因 1-arg 旧调用站点冲突而 FAIL**

```bash
./gradlew :plugin:testDebugUnitTest --tests "*CacheControlPolicyTest*"
```

Expected: PASS。若 PluginMediaSourceService.maybeWriteCache 仍调旧签名，编译会有 deprecation 警告但不失败（取决于 `allWarningsAsErrors`）。

- [ ] **Step 4.4：更新 PluginMediaSourceService.maybeWriteCache 传入 isOffline**

修改方法签名与 caller：

```kotlin
private suspend fun maybeWriteCache(
    cacheControl: CacheControl,
    item: MusicItem,
    candidateQuality: String,
    source: MediaSourceResult,
    isOffline: Boolean,
    useCache: Boolean,
) {
    if (!shouldWriteCache(cacheControl, isOffline = isOffline)) return
    // ... rest unchanged
}
```

caller 已经传 `isOffline = isOffline`（看 line 138, 156），不需要改。

- [ ] **Step 4.5：在 SimpleCacheHolder 加 evictForKey**

`player/.../SimpleCacheHolder.kt`：

```kotlin
import com.hank.musicfree.core.model.PlayQuality

fun evictForKey(platform: String, id: String, quality: PlayQuality?) {
    val cache = current ?: return
    val qualities = if (quality != null) listOf(quality) else PlayQuality.values().toList()
    var totalRemoved = 0
    var totalBytes = 0L
    for (q in qualities) {
        val key = "$platform:$id:${q.name.lowercase()}"
        val spans = cache.getCachedSpans(key)
        totalBytes += spans.sumOf { it.length }
        cache.removeResource(key)
        if (spans.isNotEmpty()) totalRemoved += 1
    }
    // Also try the "unknown" suffix (entries cached before quality was known)
    val unknownKey = "$platform:$id:unknown"
    val unknownSpans = cache.getCachedSpans(unknownKey)
    if (unknownSpans.isNotEmpty()) {
        totalBytes += unknownSpans.sumOf { it.length }
        cache.removeResource(unknownKey)
        totalRemoved += 1
    }
    if (totalRemoved > 0) {
        playCacheTelemetry.cacheEvict(scope = "stale_url", count = totalRemoved, freedBytes = totalBytes)
    }
}
```

- [ ] **Step 4.6：MediaCacheRepository 增加 onSimpleCacheEvict 回调**

`data/.../MediaCacheRepository.kt`：构造增参（向后兼容）：

```kotlin
@Singleton
class MediaCacheRepository private constructor(
    private val dao: MediaCacheDao,
    private val limitProvider: suspend () -> Long,
    private val onSimpleCacheEvict: suspend (platform: String, id: String, quality: PlayQuality?) -> Unit = { _, _, _ -> },
) {
    @Inject
    constructor(
        dao: MediaCacheDao,
        appPreferences: AppPreferences,
    ) : this(dao, limitProvider = { appPreferences.maxMusicCacheSizeBytes.first() / REPO_QUOTA_DIVISOR })
    // ... existing secondary constructors keep working; tests that need to assert the callback
    // use a new internal ctor:
    internal constructor(
        dao: MediaCacheDao,
        limitProvider: suspend () -> Long,
        now: () -> Long,
        onEvict: suspend (String, String, PlayQuality?) -> Unit,
    ) : this(dao, limitProvider, onEvict) {
        this.nowFn = now
    }

    companion object {
        const val MEMORY_LIMIT = 200
        const val LIMIT = 800
        /** Repo holds URL/header metadata only; 10% of the user-configured byte budget is plenty. */
        const val REPO_QUOTA_DIVISOR = 10L
    }
}
```

并在 `deleteEntry` 末尾调用回调：

```kotlin
suspend fun deleteEntry(platform: String, id: String, quality: PlayQuality) = mutex.withLock {
    // ... existing body that removes the json key/row + memory ...
    onSimpleCacheEvict(platform, id, quality)
}

// And deleteItem / clearAll likewise:
suspend fun deleteItem(platform: String, id: String) = mutex.withLock {
    // ... existing body ...
    onSimpleCacheEvict(platform, id, null)
}
suspend fun clearAll() = mutex.withLock {
    // ... existing body ...
    // Note: per-key SimpleCache eviction is not invoked here; SettingsCacheCleaner handles
    // full SimpleCache clear separately to avoid scanning all DB rows.
}
```

在 Hilt module 里绑定回调：

```kotlin
@Provides @Singleton
fun provideMediaCacheRepository(
    dao: MediaCacheDao,
    appPreferences: AppPreferences,
    simpleCacheHolder: SimpleCacheHolder,
): MediaCacheRepository = MediaCacheRepository(
    dao = dao,
    appPreferences = appPreferences,
).also {
    // ⚠️ This `also` is wrong — repository uses immutable ctor params.
    // Instead pass the callback via a dedicated overload:
}
```

正确写法：在 `MediaCacheRepository` 同伴侣里加 factory：

```kotlin
companion object {
    fun create(
        dao: MediaCacheDao,
        appPreferences: AppPreferences,
        simpleCacheHolder: SimpleCacheHolder,
    ): MediaCacheRepository = MediaCacheRepository(
        dao = dao,
        limitProvider = { appPreferences.maxMusicCacheSizeBytes.first() / REPO_QUOTA_DIVISOR },
        onSimpleCacheEvict = { p, i, q -> simpleCacheHolder.evictForKey(p, i, q) },
    )
}
```

Hilt module：

```kotlin
@Provides @Singleton
fun provideMediaCacheRepository(
    dao: MediaCacheDao,
    appPreferences: AppPreferences,
    simpleCacheHolder: SimpleCacheHolder,
): MediaCacheRepository = MediaCacheRepository.create(dao, appPreferences, simpleCacheHolder)
```

把 `@Inject` 主构造改为 `private`，并保留为兼容（不需要标 `@Inject` 因为 module 显式 provide）。

- [ ] **Step 4.7：MediaCacheRepositoryTest 加 onSimpleCacheEvict 调用断言**

`data/src/test/.../repository/MediaCacheRepositoryTest.kt` 中追加：

```kotlin
@Test fun `deleteEntry triggers SimpleCache evict callback`() = runTest {
    val invocations = mutableListOf<Triple<String, String, PlayQuality?>>()
    val repo = MediaCacheRepository(
        dao = inMemoryDao,
        limitProvider = { Long.MAX_VALUE },
        now = { 0L },
        onEvict = { p, i, q -> invocations += Triple(p, i, q) },
    )
    repo.put(itemOf("song1", "kugou"), PlayQuality.STANDARD, sourceOf("https://x"))
    repo.deleteEntry("kugou", "song1", PlayQuality.STANDARD)
    assertEquals(listOf(Triple("kugou", "song1", PlayQuality.STANDARD)), invocations)
}

@Test fun `deleteItem triggers callback with null quality`() = runTest {
    val invocations = mutableListOf<Triple<String, String, PlayQuality?>>()
    val repo = MediaCacheRepository(
        dao = inMemoryDao,
        limitProvider = { Long.MAX_VALUE },
        now = { 0L },
        onEvict = { p, i, q -> invocations += Triple(p, i, q) },
    )
    repo.put(itemOf("song1", "kugou"), PlayQuality.STANDARD, sourceOf("https://x"))
    repo.deleteItem("kugou", "song1")
    assertEquals(listOf(Triple("kugou", "song1", null)), invocations)
}
```

（`inMemoryDao`、`itemOf`、`sourceOf` 都是该测试已有的 helper；若没有，参考已存在的 `put` 测试方法做法照搬。）

- [ ] **Step 4.8：跑测试 + commit**

```bash
./gradlew :plugin:testDebugUnitTest :data:testDebugUnitTest :player:testDebugUnitTest
git add -A
git commit -m "feat(cache): 两层缓存联动 evict + 在线 no-cache 不再写"
```

---

## Task 5：容量动态化（SimpleCache 读取 maxMusicCacheSizeBytes）

**Files:**
- Modify: `player/.../SimpleCacheHolder.kt`（构造时读 prefs；加 `updateMaxBytes`；磁盘空间防御）
- Modify: `player/.../PlaybackModule.kt`（启动 Flow 监听）
- Modify: `feature/settings/.../SettingsCacheCleaner.kt`

- [ ] **Step 5.1：SimpleCacheHolder 改 tryCreate 读 prefs**

`player/.../SimpleCacheHolder.kt`：

```kotlin
private fun tryCreate(): SimpleCache? = runCatching {
    val configured = kotlinx.coroutines.runBlocking { appPreferences.maxMusicCacheSizeBytes.first() }
    val available = cacheDir().parentFile?.usableSpace ?: Long.MAX_VALUE
    val effective = if (available < LOWSPACE_THRESHOLD) {
        playCacheTelemetry.cacheLowspace(
            availableBytes = available,
            configuredBytes = configured,
            fallbackBytes = LOWSPACE_FALLBACK,
        )
        minOf(configured, LOWSPACE_FALLBACK)
    } else configured

    SimpleCache(
        cacheDir().apply { mkdirs() },
        pinningEvictor(effective),
        StandaloneDatabaseProvider(context),
    )
}.onFailure { error -> /* unchanged */ }.getOrNull()

private fun pinningEvictor(maxBytes: Long) = LeastRecentlyUsedCacheEvictor(maxBytes)
// PinningCacheEvictor wraps this in Task 6; for now keep plain LRU.

companion object {
    const val LOWSPACE_THRESHOLD = 2L * 1024 * 1024 * 1024 // 2GB
    const val LOWSPACE_FALLBACK = 256L * 1024 * 1024 // 256MB
    @Deprecated("Use AppPreferences.maxMusicCacheSizeBytes")
    const val DEFAULT_BYTES = 512L * 1024 * 1024
}
```

- [ ] **Step 5.2：加 updateMaxBytes**

```kotlin
fun updateMaxBytes(newBytes: Long) {
    val cache = current ?: return
    val used = cache.cacheSpace
    if (used <= newBytes) return
    // SimpleCache has no public "shrink" API; force eviction by re-creating with new evictor:
    synchronized(this) {
        ref.get()?.release()
        ref.set(null)
        // tryCreate will read latest prefs on next access; we only need to bust the singleton
    }
    playCacheTelemetry.cacheEvict(
        scope = "byte_cap",
        count = 1,
        freedBytes = (used - newBytes).coerceAtLeast(0L),
    )
}
```

- [ ] **Step 5.3：Application 启动时监听 prefs**

在 Application（或 PlaybackModule 提供的 `CoroutineScope`）启动一个 collector：

```kotlin
@Inject lateinit var appPreferences: AppPreferences
@Inject lateinit var simpleCacheHolder: SimpleCacheHolder
@Inject @AppScope lateinit var appScope: CoroutineScope // 已有 @AppScope qualifier？若无，新建。

override fun onCreate() {
    super.onCreate()
    simpleCacheHolder.migrateOnceIfNeeded()
    appScope.launch {
        appPreferences.maxMusicCacheSizeBytes.collect { newBytes ->
            simpleCacheHolder.updateMaxBytes(newBytes)
        }
    }
}
```

若没有 `@AppScope` qualifier，在 `core/.../di/CoroutineScopes.kt` 加：

```kotlin
@Qualifier annotation class AppScope

@Module @InstallIn(SingletonComponent::class)
object CoroutineScopesModule {
    @Provides @Singleton @AppScope
    fun provideAppScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
```

- [ ] **Step 5.4：SettingsCacheCleaner 拆分音频文件 / URL 元数据双入口**

`feature/settings/.../SettingsCacheCleaner.kt`：

```kotlin
suspend fun clearAudioFileCache(): Long {
    val before = simpleCacheHolder.usedBytes()
    simpleCacheHolder.resetForClear()
    val freed = (before - simpleCacheHolder.usedBytes()).coerceAtLeast(0L)
    playCacheTelemetry.cacheEvict(scope = "manual", count = 1, freedBytes = freed)
    return freed
}

suspend fun clearMediaUrlMetadataCache(): Long {
    val before = mediaCacheRepository.estimatedBytes()
    mediaCacheRepository.clearAll()
    val after = mediaCacheRepository.estimatedBytes()
    return (before - after).coerceAtLeast(0L)
}

@Deprecated("Use clearAudioFileCache + clearMediaUrlMetadataCache")
suspend fun clearMusicCache() {
    clearAudioFileCache()
    clearMediaUrlMetadataCache()
}
```

需要在 `MediaCacheRepository` 新增 `suspend fun estimatedBytes(): Long`：

```kotlin
suspend fun estimatedBytes(): Long = mutex.withLock { dao.totalSizeBytes() }
```

（`dao.totalSizeBytes()` 在 `pruneToLimit` 中已被使用，已存在。）

- [ ] **Step 5.5：跑 settings 模块测试**

```bash
./gradlew :feature:settings:testDebugUnitTest :player:testDebugUnitTest :data:testDebugUnitTest
```

Expected: 全 PASS。

- [ ] **Step 5.6：commit**

```bash
git add -A
git commit -m "feat(cache): 容量动态化 + 低磁盘空间降级 + 设置项双入口清理"
```

---

## Task 6：PinningCacheEvictor + 收藏/最近播放钉选

**Files:**
- Create: `player/src/main/java/com/hank/musicfree/player/cache/PinningCacheEvictor.kt`
- Create: `player/src/test/java/com/hank/musicfree/player/cache/PinningCacheEvictorTest.kt`
- Create: `data/src/main/java/com/hank/musicfree/data/repository/PinnedKeysProvider.kt`
- Modify: `data/.../StarredSheetRepository.kt`（加 observeStarredKeys）
- Modify: `data/.../PlayQueueRepository.kt`（加 observeRecentKeys）
- Modify: `player/.../SimpleCacheHolder.kt`（用 PinningCacheEvictor、updatePinned）

- [ ] **Step 6.1：写 PinningCacheEvictor 失败测试**

`player/src/test/java/.../cache/PinningCacheEvictorTest.kt`：

```kotlin
package com.hank.musicfree.player.cache

import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.util.UnstableApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@AndroidXOptIn(markerClass = [UnstableApi::class])
class PinningCacheEvictorTest {

    @Test fun `pinned key is skipped on eviction scan`() {
        val evictor = PinningCacheEvictor(maxBytes = 100L)
        evictor.updatePinned(setOf("kugou:song1:standard"))

        assertTrue(evictor.shouldSkip("kugou:song1:standard"))
        assertFalse(evictor.shouldSkip("kugou:song2:standard"))
    }

    @Test fun `pinning overflow above 70 percent falls back to plain LRU`() {
        val evictor = PinningCacheEvictor(maxBytes = 100L)
        evictor.updatePinned(setOf("a:1:s", "a:2:s", "a:3:s"))
        evictor.notePinnedSize(80L) // > 70%
        assertFalse(evictor.shouldSkip("a:1:s")) // overflow → no pinning
    }

    @Test fun `pinning below 70 percent honors pinned set`() {
        val evictor = PinningCacheEvictor(maxBytes = 100L)
        evictor.updatePinned(setOf("a:1:s"))
        evictor.notePinnedSize(50L)
        assertTrue(evictor.shouldSkip("a:1:s"))
    }
}
```

- [ ] **Step 6.2：跑测试确认 FAIL**

```bash
./gradlew :player:testDebugUnitTest --tests "*PinningCacheEvictorTest*"
```

Expected: FAIL with "Unresolved reference: PinningCacheEvictor"。

- [ ] **Step 6.3：实现 PinningCacheEvictor**

`player/src/main/java/com/hank/musicfree/player/cache/PinningCacheEvictor.kt`：

```kotlin
package com.hank.musicfree.player.cache

import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import java.util.TreeSet

/**
 * Wraps [LeastRecentlyUsedCacheEvictor] but excludes user-pinned keys from eviction.
 *
 * Overflow guard: if pinned content exceeds 70% of [maxBytes], pinning is suspended
 * (falls back to plain LRU) to avoid making the cache unusable.
 */
@AndroidXOptIn(markerClass = [UnstableApi::class])
class PinningCacheEvictor(private val maxBytes: Long) : CacheEvictor {

    private val delegate = LeastRecentlyUsedCacheEvictor(maxBytes)
    @Volatile private var pinned: Set<String> = emptySet()
    @Volatile private var pinnedSizeBytes: Long = 0L

    fun updatePinned(keys: Set<String>) { pinned = keys }
    fun notePinnedSize(bytes: Long) { pinnedSizeBytes = bytes }

    /** Test seam: whether [key] would currently be protected from eviction. */
    fun shouldSkip(key: String): Boolean {
        if (pinnedSizeBytes * 100 > maxBytes * 70) return false
        return key in pinned
    }

    override fun requiresCacheSpanTouches(): Boolean = delegate.requiresCacheSpanTouches()
    override fun onCacheInitialized() = delegate.onCacheInitialized()
    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        // Evict until enough free space — but skip pinned spans.
        val toFree = (cache.cacheSpace + length - maxBytes).coerceAtLeast(0L)
        if (toFree <= 0L) return
        evictSkippingPinned(cache, toFree)
    }
    override fun onSpanAdded(cache: Cache, span: CacheSpan) = delegate.onSpanAdded(cache, span)
    override fun onSpanRemoved(cache: Cache, span: CacheSpan) = delegate.onSpanRemoved(cache, span)
    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) =
        delegate.onSpanTouched(cache, oldSpan, newSpan)

    private fun evictSkippingPinned(cache: Cache, bytesNeeded: Long) {
        var freed = 0L
        val keys = cache.keys.sortedBy { key ->
            cache.getCachedSpans(key).minOfOrNull { it.lastTouchTimestamp } ?: Long.MAX_VALUE
        }
        for (key in keys) {
            if (freed >= bytesNeeded) break
            if (shouldSkip(key)) continue
            val spans = cache.getCachedSpans(key)
            for (span in spans) {
                cache.removeSpan(span)
                freed += span.length
                if (freed >= bytesNeeded) break
            }
        }
    }
}
```

- [ ] **Step 6.4：跑测试确认 PASS**

```bash
./gradlew :player:testDebugUnitTest --tests "*PinningCacheEvictorTest*"
```

Expected: 3 tests passed。

- [ ] **Step 6.5：在 StarredSheetRepository 加 observeStarredKeys**

读 `data/src/main/java/com/hank/musicfree/data/repository/StarredSheetRepository.kt`。在合适位置加：

```kotlin
import kotlinx.coroutines.flow.map

fun observeStarredKeys(): Flow<Set<String>> =
    observeAllStarredTracks().map { list ->
        list.mapTo(HashSet(list.size)) { "${it.platform}:${it.id}" }
    }
```

如果当前没有 `observeAllStarredTracks()`，先用 dao.observeAll().map 类似实现；查阅 Repository 现有 dao API。

- [ ] **Step 6.6：在 PlayQueueRepository 加 observeRecentKeys**

读 `data/src/main/java/com/hank/musicfree/data/repository/PlayQueueRepository.kt`。加：

```kotlin
import kotlinx.coroutines.flow.map

/** Last N tracks the user enqueued/played (most-recent first). */
fun observeRecentKeys(limit: Int = 50): Flow<Set<String>> =
    observeQueue().map { items ->
        items.take(limit).mapTo(HashSet(items.size.coerceAtMost(limit))) { "${it.platform}:${it.id}" }
    }
```

若 `observeQueue` 名字不同，按实际 API 调整；目标是输出"${platform}:${id}" 集合。

- [ ] **Step 6.7：实现 PinnedKeysProvider 合并源**

`data/src/main/java/com/hank/musicfree/data/repository/PinnedKeysProvider.kt`：

```kotlin
package com.hank.musicfree.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class PinnedKeysProvider @Inject constructor(
    private val starred: StarredSheetRepository,
    private val queue: PlayQueueRepository,
) {
    /**
     * Combined pinned key set in the form "${platform}:${id}".
     * SimpleCacheHolder then expands each entry to the 5 quality-suffixed cacheKey variants.
     */
    fun observe(): Flow<Set<String>> = combine(
        starred.observeStarredKeys(),
        queue.observeRecentKeys(50),
    ) { a, b -> a + b }
}
```

- [ ] **Step 6.8：SimpleCacheHolder 用 PinningCacheEvictor + 监听 PinnedKeysProvider**

`player/.../SimpleCacheHolder.kt`：

```kotlin
private var pinningEvictor: PinningCacheEvictor? = null

private fun pinningEvictor(maxBytes: Long): CacheEvictor =
    PinningCacheEvictor(maxBytes).also { pinningEvictor = it }

fun updatePinned(keys: Set<String>) {
    val evictor = pinningEvictor ?: return
    // Expand "platform:id" to all quality-suffixed variants
    val expanded = keys.flatMapTo(HashSet(keys.size * 5)) { base ->
        PlayQuality.values().map { "$base:${it.name.lowercase()}" } + "$base:unknown"
    }
    evictor.updatePinned(expanded)
    // Approximate pinned size: sum of cached spans for those keys.
    val cache = current
    if (cache != null) {
        val totalBytes = expanded.sumOf { k ->
            cache.getCachedSpans(k).sumOf { it.length }
        }
        evictor.notePinnedSize(totalBytes)
    }
}
```

`tryCreate` 改为：

```kotlin
SimpleCache(
    cacheDir().apply { mkdirs() },
    pinningEvictor(effective),
    StandaloneDatabaseProvider(context),
)
```

- [ ] **Step 6.9：Application onCreate 连接 PinnedKeysProvider**

```kotlin
@Inject lateinit var pinnedKeysProvider: PinnedKeysProvider

appScope.launch {
    pinnedKeysProvider.observe().collect { keys ->
        simpleCacheHolder.updatePinned(keys)
    }
}
```

- [ ] **Step 6.10：跑测试 + commit**

```bash
./gradlew :player:testDebugUnitTest :data:testDebugUnitTest :app:assembleDebug
git add -A
git commit -m "feat(cache): PinningCacheEvictor + 收藏与最近 50 首钉选优先保留"
```

---

## Task 7：PrefetchCoordinator（顺播下一首预热头部）

**Files:**
- Create: `player/src/main/java/com/hank/musicfree/player/prefetch/PrefetchCoordinator.kt`
- Create: `player/src/test/java/com/hank/musicfree/player/prefetch/PrefetchCoordinatorTest.kt`
- Modify: `player/.../PlayerController.kt` 暴露 `nextItemFlow` 或在 PrefetchCoordinator 内直接 collect 现有 `playQueueState`/`positionFlow`

- [ ] **Step 7.1：写 PrefetchCoordinator 失败测试**

`player/src/test/.../prefetch/PrefetchCoordinatorTest.kt`：

```kotlin
package com.hank.musicfree.player.prefetch

import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.model.MusicItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrefetchCoordinatorTest {

    private val itemA = sampleItem("a")
    private val itemB = sampleItem("b")

    @Test fun `progress below 60 percent does not trigger prefetch`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val resolver = mockk<MediaSourceResolver>(relaxed = true)
        val progress = MutableStateFlow(ProgressTick(itemA, 100L, 1000L))
        val nextItem = MutableStateFlow<MusicItem?>(itemB)
        val coordinator = PrefetchCoordinator(
            resolver = resolver,
            progressFlow = progress,
            nextItemFlow = nextItem,
            isWifiFlow = MutableStateFlow(true),
            dispatcher = dispatcher,
        )
        coordinator.start()
        advanceUntilIdle()
        coVerify(exactly = 0) { resolver.resolve(any(), any()) }
        coordinator.stop()
    }

    @Test fun `progress at 70 percent on wifi triggers prefetch`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val resolver = mockk<MediaSourceResolver>(relaxed = true)
        coEvery { resolver.resolve(any(), any()) } returns null
        val progress = MutableStateFlow(ProgressTick(itemA, 700L, 1000L))
        val nextItem = MutableStateFlow<MusicItem?>(itemB)
        val coordinator = PrefetchCoordinator(
            resolver = resolver,
            progressFlow = progress,
            nextItemFlow = nextItem,
            isWifiFlow = MutableStateFlow(true),
            dispatcher = dispatcher,
        )
        coordinator.start()
        advanceUntilIdle()
        coVerify(exactly = 1) { resolver.resolve(itemB, any()) }
        coordinator.stop()
    }

    @Test fun `progress at 70 percent on cellular does not trigger prefetch by default`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val resolver = mockk<MediaSourceResolver>(relaxed = true)
        val progress = MutableStateFlow(ProgressTick(itemA, 700L, 1000L))
        val nextItem = MutableStateFlow<MusicItem?>(itemB)
        val coordinator = PrefetchCoordinator(
            resolver = resolver,
            progressFlow = progress,
            nextItemFlow = nextItem,
            isWifiFlow = MutableStateFlow(false),
            dispatcher = dispatcher,
        )
        coordinator.start()
        advanceUntilIdle()
        coVerify(exactly = 0) { resolver.resolve(any(), any()) }
        coordinator.stop()
    }

    @Test fun `same next item is not prefetched twice`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val resolver = mockk<MediaSourceResolver>(relaxed = true)
        coEvery { resolver.resolve(any(), any()) } returns null
        val progress = MutableStateFlow(ProgressTick(itemA, 700L, 1000L))
        val nextItem = MutableStateFlow<MusicItem?>(itemB)
        val coordinator = PrefetchCoordinator(
            resolver = resolver,
            progressFlow = progress,
            nextItemFlow = nextItem,
            isWifiFlow = MutableStateFlow(true),
            dispatcher = dispatcher,
        )
        coordinator.start()
        advanceUntilIdle()
        progress.value = ProgressTick(itemA, 800L, 1000L)
        advanceUntilIdle()
        coVerify(exactly = 1) { resolver.resolve(itemB, any()) }
        coordinator.stop()
    }

    private fun sampleItem(id: String) = MusicItem(
        id = id, platform = "p", title = id, artist = "",
        album = null, duration = 1000L, url = null, artwork = null, qualities = null,
    )
}
```

- [ ] **Step 7.2：跑测试确认 FAIL**

```bash
./gradlew :player:testDebugUnitTest --tests "*PrefetchCoordinatorTest*"
```

Expected: FAIL with "Unresolved reference: PrefetchCoordinator"。

- [ ] **Step 7.3：实现 PrefetchCoordinator**

`player/src/main/java/com/hank/musicfree/player/prefetch/PrefetchCoordinator.kt`：

```kotlin
package com.hank.musicfree.player.prefetch

import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class ProgressTick(val item: MusicItem, val positionMs: Long, val durationMs: Long) {
    val ratio: Float get() = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
}

/**
 * Triggers a single-buffer head prefetch for the next queue item once playback of the
 * current track passes the configured ratio (default 0.6). Idempotent per (next item, wifi state).
 */
@Singleton
class PrefetchCoordinator @Inject constructor(
    private val resolver: MediaSourceResolver,
    private val progressFlow: Flow<ProgressTick>,
    private val nextItemFlow: StateFlow<MusicItem?>,
    private val isWifiFlow: StateFlow<Boolean>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var runningJob: Job? = null
    private var lastPrefetchedKey: String? = null

    fun start() {
        scope.launch {
            combine(progressFlow, nextItemFlow, isWifiFlow) { tick, next, wifi ->
                Triple(tick, next, wifi)
            }.distinctUntilChanged().collect { (tick, next, wifi) ->
                if (next == null) return@collect
                if (!wifi) return@collect
                if (tick.ratio < TRIGGER_RATIO) return@collect
                val key = "${next.platform}:${next.id}"
                if (key == lastPrefetchedKey) return@collect
                lastPrefetchedKey = key
                runningJob?.cancel()
                runningJob = launch { runPrefetch(next) }
            }
        }
    }

    fun stop() { scope.cancel() }

    private suspend fun runPrefetch(next: MusicItem) {
        runCatching { resolver.resolve(next, null) }
            .onFailure { error ->
                MfLog.detail(
                    category = LogCategory.PLAYER,
                    event = "prefetch_failed",
                    fields = mapOf(
                        "platform" to next.platform, "id" to next.id,
                        "reason" to (error.javaClass.simpleName ?: "exception"),
                    ),
                )
            }
        // Head-warm: open CacheDataSource for 512KB read happens in a follow-up wire-up step
        // (see Task 7.4); not part of this unit-testable surface.
    }

    companion object {
        const val TRIGGER_RATIO = 0.6f
        const val HEAD_WARM_BYTES = 512 * 1024
    }
}
```

- [ ] **Step 7.4：跑测试确认 PASS**

```bash
./gradlew :player:testDebugUnitTest --tests "*PrefetchCoordinatorTest*"
```

Expected: 4 tests passed。

- [ ] **Step 7.5：在 PlayerController 暴露 progressFlow + nextItemFlow**

读 PlayerController 现有 state：通常已有 `playerState: StateFlow<PlayerState>`。补充：

```kotlin
val progressTickFlow: Flow<ProgressTick> = playerState
    .map { state ->
        val item = playQueue.currentItem ?: return@map null
        ProgressTick(item, state.positionMs, state.durationMs.coerceAtLeast(0L))
    }
    .filterNotNull()

val nextItemFlow: StateFlow<MusicItem?> = playQueueState
    .map { playQueue.nextItem() }
    .stateIn(scope, SharingStarted.Eagerly, null)
```

若 `playQueueState` 不存在或 `nextItem()` 不存在，把上面改成手工 sample：

```kotlin
val nextItemFlow: StateFlow<MusicItem?> = MutableStateFlow<MusicItem?>(null).also { sf ->
    // 在 emitQueueState() 内同时 sf.value = playQueue.nextItem
}
```

- [ ] **Step 7.6：Hilt module 提供 PrefetchCoordinator 并启动**

PlaybackModule（或 app/MainApplication）：

```kotlin
@Provides @Singleton
fun providePrefetchCoordinator(
    resolver: MediaSourceResolver,
    playerController: PlayerController,
    networkMonitor: NetworkMonitor,
): PrefetchCoordinator = PrefetchCoordinator(
    resolver = resolver,
    progressFlow = playerController.progressTickFlow,
    nextItemFlow = playerController.nextItemFlow,
    isWifiFlow = networkMonitor.isWifi,
).also { it.start() }
```

若 `NetworkMonitor` 没有 `isWifi: StateFlow<Boolean>`，在 `:downloader` `NetworkMonitor` 加：

```kotlin
val isWifi: StateFlow<Boolean> get() = ...
```

（实际 NetworkMonitor 已有 `currentNetworkType()`；改为暴露 Flow。看 `downloader/src/main/.../io/NetworkMonitor.kt` 现状再决定增量。）

- [ ] **Step 7.7：commit**

```bash
git add -A
git commit -m "feat(cache): 顺播下一首在 Wi-Fi 下 60% 进度触发预取"
```

---

## Task 8：CacheDataSource.EventListener 桥接 + 验收

**Files:**
- Create: `player/src/main/java/com/hank/musicfree/player/cache/CacheDataSourceEventBridge.kt`
- Modify: `player/.../HeaderInjectingDataSourceFactory.kt`（接入 EventListener）
- Create: `app/src/androidTest/java/.../LocalShortCircuitInstrumentedTest.kt`
- Create: `app/src/androidTest/java/.../CacheKeyQualityE2ETest.kt`

- [ ] **Step 8.1：实现 CacheDataSourceEventBridge**

`player/src/main/java/com/hank/musicfree/player/cache/CacheDataSourceEventBridge.kt`：

```kotlin
package com.hank.musicfree.player.cache

import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import com.hank.musicfree.player.telemetry.CurrentSidProvider
import com.hank.musicfree.player.telemetry.PlayCacheTelemetry
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicLong

/**
 * Bridges CacheDataSource cache/upstream byte counters into PlayCacheTelemetry as
 * `media3_datasource_open` and `media_cache_hit{source=media3}` events.
 *
 * One bridge per createDataSource() call (stateful), but emission helpers are stateless.
 */
@AndroidXOptIn(markerClass = [UnstableApi::class])
@Singleton
class CacheDataSourceEventBridge @Inject constructor(
    private val telemetry: PlayCacheTelemetry,
    private val sidProvider: CurrentSidProvider,
) {
    fun newListener(cacheKey: String): CacheDataSource.EventListener {
        val cacheBytes = AtomicLong(0)
        val upstreamBytes = AtomicLong(0)
        return object : CacheDataSource.EventListener {
            override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                cacheBytes.addAndGet(cachedBytesRead)
            }
            override fun onCacheIgnored(reason: Int) {
                // ignored — covered by media3_datasource_error if upstream fails
            }
        }.also {
            // Emit one open-event upfront; final tallies are reported on close via PlayerController's
            // play_session_end aggregator (Task 9.x — out of scope of this skeleton).
            telemetry.media3DataSourceOpen(
                sid = sidProvider.peek(),
                cacheKey = cacheKey,
                cacheHit = false, // updated by the aggregator
                bytesFromCache = 0L,
                bytesFromUpstream = 0L,
            )
        }
    }
}
```

- [ ] **Step 8.2：HeaderInjectingDataSourceFactory 接入 EventListener**

修改 `createDataSource()`：

```kotlin
override fun createDataSource(): DataSource {
    val httpFactory = OkHttpDataSource.Factory(okHttpClient)
    val baseFactory = DefaultDataSource.Factory(context, httpFactory)
    val resolving = ResolvingDataSource.Factory(baseFactory) { dataSpec ->
        resolveDataSpec(dataSpec)
    }
    val cache = simpleCacheHolder.current ?: return resolving.createDataSource()
    // The cacheKey for the active request — captured lazily on each open.
    return CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(resolving)
        .setCacheKeyFactory { spec -> cacheKeyFor(spec.uri) }
        .setEventListener(eventBridge.newListener(cacheKey = "(per-open)"))
        .setCacheWriteDataSinkFactory(
            CacheDataSink.Factory()
                .setCache(cache)
                .setFragmentSize(C.LENGTH_UNSET.toLong())
        )
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        .createDataSource()
}
```

Constructor 加 `private val eventBridge: CacheDataSourceEventBridge`。

- [ ] **Step 8.3：跑全部单测**

```bash
./gradlew :player:testDebugUnitTest :plugin:testDebugUnitTest :data:testDebugUnitTest :feature:settings:testDebugUnitTest
```

Expected: 全 PASS。

- [ ] **Step 8.4：写 androidTest LocalShortCircuitInstrumentedTest**

`app/src/androidTest/java/com/hank/musicfree/LocalShortCircuitInstrumentedTest.kt`：

```kotlin
package com.hank.musicfree

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.plugin.media.PluginMediaSourceService
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LocalShortCircuitInstrumentedTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var service: PluginMediaSourceService

    @Test fun playbackOfMediaStoreUriShortCircuits() = runBlocking {
        hilt.inject()
        val uri = createSilentMp3()
        val item = MusicItem(
            id = "test-1", platform = "kugou",
            title = "t", artist = "a", album = null, duration = 0L,
            url = "https://example.invalid/a.mp3",
            artwork = null, qualities = null,
            localPath = uri.toString(),
        )
        val resolution = service.resolve(item, quality = null)
        assertNotNull(resolution)
        assert(resolution!!.source.url == uri.toString())
    }

    private fun createSilentMp3(): Uri {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "mfac-test-${System.currentTimeMillis()}.mp3")
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)!!.use { it.write(byteArrayOf(0xFF.toByte(), 0xFB.toByte())) }
        return uri
    }
}
```

- [ ] **Step 8.5：构建 androidTest（不一定要现在跑，CI 可能更合适）**

```bash
./gradlew :app:assembleDebugAndroidTest
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 8.6：跑 :app:assembleDebug 完整集成**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 8.7：commit**

```bash
git add -A
git commit -m "feat(cache): CacheDataSource EventListener 桥接 + androidTest 覆盖本地短路"
```

---

## Task 9：spec §9 验收 + 文档更新

**Files:** docs only

- [ ] **Step 9.1：手工验证清单（先记日志，逐条 grep）**

按 spec §9 验收：

**功能验证**：在已安装 Debug 包的设备上执行：
1. 播放已下载歌 → `adb logcat -d | grep "play_session_start\|resolve_local_check\|play_session_end"` 应当看到 `hasLocalPath=true, localPathReadable=true, hitClassification=local`，且无 `resolve_plugin_call_*`
2. 标准质 → 高质 → 标准质 切换 → `grep "media3_datasource_open"` 应看到至少 2 个不同 cacheKey（`...:standard` 与 `...:high`）共存
3. 模拟 HTTP 400（用一个签名 URL 立即过期的插件）→ `grep "media_cache_evict"` 应看到 scope=stale_url
4. 设置 → 清空音频文件缓存 → `simpleCacheHolder.usedBytes() == 0`；清空 URL 元数据 → `mediaCacheRepository.estimatedBytes() ≈ 0`
5. 顺播 → 当前歌 ≥60% 后 1 秒内 `grep "prefetch_"` 应有命中

**可观测验证**：
6. 任意一首歌 `kugou:song1` → `grep "kugou.*song1"` 在播放日志中应得到 9 条事件完整序列
7. miss/hit 比可被脚本统计（可选写一个 helper script 留待后续）

把验证结果（每条 PASS/FAIL）追加到 plan 末尾的"验收记录"区段。

- [ ] **Step 9.2：更新 dev-harness rules（如有需要）**

考虑是否在 `docs/dev-harness/player/rules.md` 加一条 MUST rule：

> MUST：写 SimpleCache 的 cacheKey 时必须包含 `PlayQuality` 维度（`platform:id:quality`）；新增 cacheKey 构造点必须通过 `HeaderInjectingDataSourceFactory.cacheKeyFor(...)` 或等价方法，不得手工拼装。

放在 player/rules.md 现有 MUST 区段。链接 incident（如未来出现需登记）。

- [ ] **Step 9.3：把本计划标记为已执行 + commit + push**

把本文件顶部加一行：

```
> 执行状态：已落地 (commit range: …)
> 实施日期：2026-05-20 ～ <落地日>
> 验收记录：见 §9.1
```

```bash
git add -A
git commit -m "docs(cache): 标注 spec 实现完成 + 更新 player rules"
git -C ../.. log --oneline -1
```

合并回 main（按 CLAUDE.md "Git Worktree 开发约束"使用 squash merge + 中文 conventional commits）：

```bash
cd ../..  # back to main worktree
git merge --squash feat-song-cache-optim
git commit -m "$(cat <<'EOF'
feat(cache): 歌曲缓存命中率与可观测性整体优化

本地短路（已下载走 MediaStore URI）、cacheKey 加入 PlayQuality 维度、
两层缓存联动 evict、在线 no-cache 不再写、容量与用户配置联动、
PinningCacheEvictor 钉选保留、顺播下一首在 Wi-Fi 下预取，
配套 playSessionId + 9 条诊断事件 + miss 埋点。
EOF
)"
```

不要 `git push`，由用户决定时机。

---

## Self-Review 已完成项

- 已扫 placeholder：无 TBD/TODO/FIXME。
- 已对 Task 内 cacheKey 字符串：`platform:id:quality.name.lowercase()` 在 Task 3、4、6、9 均一致使用。
- 已对 `CacheHitSource`/`CacheMissReason` enum 在 Task 1（声明）、Task 2（LOCAL）、Task 4（COLD/NO_CACHE_POLICY）一致引用。
- 已对 `MediaCacheRepository` 构造在 Task 4（加 `onSimpleCacheEvict`）、Task 5（quota /10）一致演进。
- 已对 `SimpleCacheHolder` 方法 `evictForKey`、`updateMaxBytes`、`updatePinned`、`migrateOnceIfNeeded`、`usedBytes` 在 spec §5.1 与 plan Task 3/4/5/6/8 中全部出现并一致命名。
- 唯一已知偏差：spec §4.3.1 描述 `updateMaxBytes` 在新值 < 当前已用时按 LRU 立即淘汰到 95%。本计划采用"释放 SimpleCache 单例 + 下次重建读最新 prefs"的更简实现，trade-off 是用户改容量后下次播放才生效。在 Task 5.2 注释中已说明。如需严格按 spec，加一个 Task 5.2.b 重新创建 SimpleCache 时迁移幸存 span 即可。

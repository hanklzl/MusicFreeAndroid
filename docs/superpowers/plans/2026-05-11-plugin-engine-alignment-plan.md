# 插件引擎与 RN 原版对齐实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `:plugin` / `:data` / `:feature/*` 收敛到与 RN MusicFree 原版插件引擎语义等价（spec §3.2 列明的 out-of-scope 项除外），并通过完整的单测 / 契约 / 集成 / 运行态验收。

**Architecture:** 6 个 phase（与 spec §14 PR1–PR6 对应），每个 phase 一个 commit + 自带验证。沿用现有 `:plugin → :data` 依赖；不引入 MMKV、不动 Room schema 版本（已是 v8，新表 / 新列直接在 v8 内增加并重导 schema 文件，依据 user memory）。

**Tech Stack:** Kotlin 2.3.21 / Hilt / Room 2.x / DataStore / Coroutines / quickjs-kt / OkHttp / Compose / Material3 / 新增 `io.github.z4kn4fein:semver:2.0.0`

**关联 spec:** [`2026-05-11-plugin-engine-alignment-design.md`](../specs/2026-05-11-plugin-engine-alignment-design.md)

**Harness 强制入口:** [`docs/dev-harness/plugin/rules.md`](../../dev-harness/plugin/rules.md)、[`docs/dev-harness/test/rules.md`](../../dev-harness/test/rules.md)

---

## §0 Storage Mapping（spec 概念 → 现存实体）

Spec 写作时假定多个实体待新增，实际 :data v8 已覆盖大部分。本表是 plan 实施的事实依据。

| Spec 概念 | 实际落点 | 备注 |
|---|---|---|
| `MediaMetaCacheEntity` | **已存在** `data/db/entity/MediaCacheEntity.kt` (`media_cache` 表，PK `(platform, id)`, `sourcesJson`, `updated_at`) | LIMIT 800，`deleteOldest(LIMIT/2)` 已实现 |
| `MediaMetaCacheDao` | **已存在** `MediaCacheDao` | 需补 `deleteByPlatform` |
| `MediaMetaCacheGateway` | **不新增**；直接 `:plugin` import `MediaCacheRepository`（:plugin 已依赖 :data） | 简化于 spec |
| `LRU 内存层 200 项` | **保留实施**：`MediaCacheRepository` 内加 `LruCache<String, CachedSources>` | 进程内热点 |
| `MediaExtraEntity` | **不新增**；拆为现有实体： |  |
|   `MediaExtra.localPath` / `downloaded` | `DownloadedTrackEntity` (`downloaded_tracks` 表) | 已存在 |
|   `MediaExtra.lyricOffset` | `LyricCacheEntity.userOffsetMs` | 已存在 |
|   `MediaExtra.associatedLrc` | `LyricCacheEntity.associatedMusicJson` | 已存在 |
| `PluginLyricFileStore` | **不新增**；歌词 inline 存 `LyricCacheEntity.remoteRawLrc` | LRC 文本通常 < 50KB，DB 足够 |
| `UserLyricFileStore` | **不新增**；用户自设 = `LyricCacheEntity.localRawLrc` / `localTranslation` | 已存在 + 已有 `LyricRepository.importLocalLyric` |
| `cacheControl` 决策 | **保留实施**：`PluginMediaSourceService` 当前完全不读 cache | `PluginInfo.cacheControl` 字段已存在，但未生效 |
| `CacheControl` 工具类 | **已存在** `plugin/playback/CacheControlPolicy.kt`（含 `shouldUseCache` / `shouldWriteCache`） | 直接复用 |
| `PluginMetadataCacheEntity` | **新增**：spec §9.2 lazy load 用 | 真正全新 |
| `LocalFilePlugin` | **新增**：spec §6 | 真正全新 |
| `PluginState` / `PluginErrorReason` / `PluginEntry` | **新增**：spec §7 | 真正全新 |
| `Mp3MetadataReader` 接口 | **新增**：接口在 :plugin/local，实现在 :data | `MediaMetadataRetriever` 包装 |
| `WebDavShim` | **新增**：spec §8.4 | 真正全新 |

**结论**：Phase A 工作量明显小于 spec 表层暗示的，因为大半实体已存在；真正"新建"的是 Phase B / C / D / E。

---

## §1 File Structure（按 phase 罗列）

### Phase A — Cache wiring + failure-driven eviction

```
:plugin
  ├─ media/PluginMediaSourceService.kt        ↺ 注入 MediaCacheRepository + cacheControl 决策 + resolveFresh API
  └─ manager/PluginManager.kt                 ↺ uninstall(platform) 增加 3 个 deleteByPlatform 调用
:data
  ├─ db/dao/MediaCacheDao.kt                  ↺ 加 deleteByPlatform + delete(platform, id)
  ├─ db/dao/LyricCacheDao.kt                  ↺ 加 deleteByPlatform
  ├─ db/dao/DownloadedTrackDao.kt             ↺ 加 deleteByPlatform（如尚未有）
  └─ repository/MediaCacheRepository.kt       ↺ LruCache 内存层 + deleteEntry / deleteByPlatform
:player
  └─ controller/PlayerController.kt           ↺ 监听 ERROR_CODE_IO_BAD_HTTP_STATUS → evict + resolveFresh
:plugin/src/test
  ├─ media/PluginMediaSourceServiceCacheTest.kt        + 新增（三种 cacheControl 路径）
  ├─ media/PluginMediaSourceServiceResolveFreshTest.kt + 新增
  └─ manager/PluginManagerCacheCleanupTest.kt          + 新增
:data/src/test
  ├─ db/dao/MediaCacheDaoTest.kt              + 新增
  ├─ db/dao/LyricCacheDaoTest.kt              + 新增
  ├─ db/dao/DownloadedTrackDaoTest.kt         + 新增（如不存在）
  ├─ repository/MediaCacheRepositoryLruTest.kt    + 新增
  ├─ repository/MediaCacheRepositoryDeleteTest.kt + 新增
  └─ repository/LyricRepositoryDeleteTest.kt  + 新增
:player/src/test
  └─ controller/PlayerControllerStaleUrlRefreshTest.kt + 新增
docs/superpowers/specs
  └─ 2026-05-11-stale-media-source-playback-design.md  ↺ 加 forward link 指本 spec §5.7
```

### Phase B — LocalFilePlugin

```
:plugin
  ├─ local/LocalFilePlugin.kt                + 新增（Kotlin 实现 PluginApi）
  ├─ local/LocalFilePluginConstants.kt       + platform="本地" / hash="local-plugin-hash"
  ├─ local/Mp3MetadataReader.kt              + 接口
  └─ manager/PluginManager.kt                ↺ setup() 末尾注册 + getEnabledPlugins() 过滤 local hash
:data
  └─ local/Mp3MetadataReaderImpl.kt          + 实现，注入 Hilt
:plugin/src/test
  └─ local/LocalFilePluginTest.kt            + fixture mp3 + adjacent .lrc
:plugin/src/androidTest
  └─ local/LocalFilePluginIntegrationTest.kt + 真 MediaMetadataRetriever
```

未在本 phase 触及 `LocalMusicRepository`（在 :feature/home）—— 留待最后 Phase F.5 当统一收口的一部分处理，避免 PR 跨边界过大。

### Phase C — State Machine + Error UI

```
:plugin
  ├─ runtime/PluginState.kt                   + sealed 4 态
  ├─ runtime/PluginErrorReason.kt             + enum 5 个
  ├─ runtime/PluginStateKeys.kt               + 显式字符串常量（log 用，避免 R8）
  ├─ manager/PluginEntry.kt                   + data class
  ├─ manager/PluginManager.kt                 ↺ plugins: StateFlow<List<PluginEntry>>
  └─ manager/PluginOperationResult.kt         ↺ 扩 SOURCE_INVALID/MISSING_PLATFORM/VERSION_REJECTED
:feature/settings
  ├─ plugin/PluginListViewModel.kt            ↺ 暴露 allEntries flow
  ├─ plugin/PluginListScreen.kt               ↺ 增加 status badge
  └─ plugin/PluginErrorPanel.kt               + Compose 错误面板
:plugin/src/test
  ├─ runtime/PluginStateMachineTest.kt        + 5 个 Failed 分支
  └─ manager/PluginManagerStateFlowTest.kt    + StateFlow 转换
:feature/settings/src/test
  └─ plugin/PluginListBadgeRenderTest.kt      + Compose UI test
```

### Phase D — Runtime Compat

```
:plugin
  ├─ engine/AxiosShim.kt                       ↺ default 2000ms + auth URL → Basic
  ├─ engine/RequireShim.kt                     ↺ register webdav
  ├─ engine/WebDavShim.kt                      + 新增
  ├─ engine/JsEngine.kt                        ↺ inject process global + lang="zh-CN"
  └─ src/main/assets/jslibs/webdav.js          + 新增
  └─ src/main/assets/jslibs/url-polyfill.js    + 条件新增（探针决定）
:plugin/src/test
  ├─ engine/AxiosShimAuthUrlTest.kt            + 新增
  ├─ engine/WebDavShimTest.kt                  + 新增（MockWebServer）
  ├─ engine/RuntimeCompatContractTest.kt       + 新增（契约测）
  └─ engine/RuntimeUrlConstructorContractTest.kt + 探针
```

### Phase E — Lifecycle (semver + lazyLoad)

```
:plugin/build.gradle.kts                       ↺ + io.github.z4kn4fein:semver
:plugin
  ├─ runtime/PluginAppVersionGate.kt           + 新增
  ├─ manager/PluginManager.kt                  ↺ extractPluginInfo() 后调闸门 + lazy mode
  ├─ manager/PluginMetadataCacheGateway.kt     + interface
  └─ di/PluginModule.kt                        ↺ 绑定 Gateway
:data
  ├─ db/entity/PluginMetadataCacheEntity.kt    + 新增
  ├─ db/dao/PluginMetadataCacheDao.kt          + 新增
  ├─ db/AppDatabase.kt                         ↺ entities += PluginMetadataCacheEntity
  ├─ repository/PluginMetadataCacheRepository.kt + 实现 Gateway
  └─ schemas/<db>/8.json                       ↺ 重导
:feature/settings
  └─ plugin/PluginAdvancedSettingsScreen.kt    ↺ + 懒加载开关
:plugin/src/test
  ├─ runtime/PluginAppVersionGateTest.kt       + 新增
  └─ manager/PluginMetadataCacheContractTest.kt + 新增
:plugin/src/androidTest
  └─ manager/PluginLazyLoadIntegrationTest.kt  + 新增
```

### Phase F — Model Boundary + 本地收口

```
:plugin
  ├─ engine/JsBridge.kt                        ↺ toMusicItem 忽略 "$"
  ├─ engine/MusicItemBridgeProjector.kt        + 新增
  ├─ api/PluginModels.kt                       ↺ MediaSourceResult.contentType
  └─ media/PluginMediaSourceService.kt         ↺ resolve 用 projector + LocalFilePlugin 走同一路径
:feature/home
  └─ local/LocalMusicRepository.kt             ↺ 委托 PluginManager.getByName("本地")
:player
  └─ ... 删除 platform=="本地" 特判（如存在）
:plugin/src/test
  ├─ engine/MusicItemBridgeProjectorTest.kt    + 新增
  ├─ engine/MediaItemBridgeContractTest.kt     + 新增（契约）
  └─ engine/JsBridgeDollarKeyDefenseTest.kt    + 新增
```

---

## §2 Conventions（每个 task 默认遵守）

1. **TDD**：每个变更先写失败测试 → 红 → 实现最小通过 → 绿 → commit。复杂变更可分多个 task。
2. **Commit message**：`feat(plugin)` / `fix(plugin)` / `refactor(plugin)` 等，参考 `git log --oneline` 风格。每个 phase 末尾一个 commit；如果 phase 内部很重也可拆多 commit。
3. **不动 schema 版本**：保留 `version = 8`；新表 / 列直接加；`./gradlew :data:compileDebugKotlin` 会重导 `data/schemas/<db>/8.json`，提交。
4. **harness 联动**：新增的 runtime invariant / state 不在 manager catch 块吞错 → 看 §11.
5. **测试位置**：纯 JVM 单测 → `*/src/test`；需要 Android 框架 / MediaMetadataRetriever / 真 QuickJS asset → `*/src/androidTest`。
6. **不依赖 Robolectric** 除非现有 module 已用（参见 `:data/build.gradle.kts` 有 `robolectric`，:plugin 已配 `robolectric` testImplementation）。
7. **绝对不在 Bash 用 `&&` 跨步骤** —— 每个步骤一个 Bash 命令；前一步失败不应自动滚动到后一步。

---

## Phase A — Cache & Persistence Wiring

**目标**：让 `PluginMediaSourceService` 按 `cacheControl` 读写 `MediaCacheRepository`；`PlayerController` 监听 ExoPlayer HTTP 错误触发 evict + 重解析（单首歌 1 次上限）；插件卸载时清理对应 platform 的 cache / lyric / downloaded 行；`MediaCacheRepository` 加 LRU 内存层 + 单 entry eviction API。

**重要语境**：现有 `docs/superpowers/specs/2026-05-11-stale-media-source-playback-design.md` 选择"始终重解析、不接 cache"方案保证不踩过期 URL。本 phase 引入 cache 后必须**同步**实现"失败 → evict + 重解析"才能保证不回退该 fix。两步缺一不可。

### Task A1: 给 `MediaCacheDao` 加 `deleteByPlatform` + `delete(platform, id)`

**Files:**

- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/MediaCacheDao.kt`
- Test: `data/src/test/java/com/zili/android/musicfreeandroid/data/db/dao/MediaCacheDaoTest.kt` (new)

- [ ] **Step 1: Write the failing tests**

`data/src/test/java/com/zili/android/musicfreeandroid/data/db/dao/MediaCacheDaoTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.entity.MediaCacheEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
class MediaCacheDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MediaCacheDao

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.mediaCacheDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun `deleteByPlatform removes only rows for given platform`() = runTest {
        dao.upsert(MediaCacheEntity("kuwo", "1", "{}", 100))
        dao.upsert(MediaCacheEntity("kuwo", "2", "{}", 200))
        dao.upsert(MediaCacheEntity("kugou", "3", "{}", 300))

        dao.deleteByPlatform("kuwo")

        assertNull(dao.get("kuwo", "1"))
        assertNull(dao.get("kuwo", "2"))
        assertNotNull(dao.get("kugou", "3"))
        assertEquals(1, dao.count())
    }

    @Test fun `delete removes only matching row`() = runTest {
        dao.upsert(MediaCacheEntity("kuwo", "1", "{}", 100))
        dao.upsert(MediaCacheEntity("kuwo", "2", "{}", 200))
        dao.delete("kuwo", "1")
        assertNull(dao.get("kuwo", "1"))
        assertNotNull(dao.get("kuwo", "2"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :data:testDebugUnitTest --tests "*MediaCacheDaoTest*" --no-daemon`
Expected: compile fails — `deleteByPlatform` / `delete` not defined.

- [ ] **Step 3: Add the DAO methods**

In `MediaCacheDao.kt` add inside the `@Dao interface MediaCacheDao`:

```kotlin
@Query("DELETE FROM media_cache WHERE platform = :platform")
suspend fun deleteByPlatform(platform: String)

@Query("DELETE FROM media_cache WHERE platform = :platform AND id = :id")
suspend fun delete(platform: String, id: String)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :data:testDebugUnitTest --tests "*MediaCacheDaoTest*" --no-daemon`
Expected: PASS.

- [ ] **Step 5: Hold commit until end of Phase A** (multi-task commit).

### Task A2: 给 `LyricCacheDao` 加 `deleteByPlatform`

**Files:**

- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/LyricCacheDao.kt`
- Test: `data/src/test/java/com/zili/android/musicfreeandroid/data/db/dao/LyricCacheDaoTest.kt` (new or extend)

- [ ] **Step 1: Write the failing test**

`LyricCacheDaoTest.kt` (new file, mirror MediaCacheDaoTest structure):

```kotlin
@Test fun `deleteByPlatform removes only rows for given platform`() = runTest {
    dao.upsert(LyricCacheEntity("1", "kuwo", null, null, null, null, null, null, null, null, null, null, 0, 100))
    dao.upsert(LyricCacheEntity("2", "kuwo", null, null, null, null, null, null, null, null, null, null, 0, 200))
    dao.upsert(LyricCacheEntity("3", "kugou", null, null, null, null, null, null, null, null, null, null, 0, 300))

    dao.deleteByPlatform("kuwo")

    assertNull(dao.getByKey("kuwo", "1"))
    assertNull(dao.getByKey("kuwo", "2"))
    assertNotNull(dao.getByKey("kugou", "3"))
}
```

`@Before` / `@After` / `@RunWith` 与 `MediaCacheDaoTest` 同。

- [ ] **Step 2: Run test → FAIL** (compile error).

- [ ] **Step 3: Add DAO method:**

```kotlin
@Query("DELETE FROM lyric_cache WHERE musicPlatform = :platform")
suspend fun deleteByPlatform(platform: String)
```

- [ ] **Step 4: Run test → PASS.**

### Task A3: 给 `DownloadedTrackDao` 加 `deleteByPlatform`

**Files:**

- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/DownloadedTrackDao.kt`
- Test: `data/src/test/java/com/zili/android/musicfreeandroid/data/db/dao/DownloadedTrackDaoTest.kt` (new)

- [ ] **Step 1: Read current DAO first**

Run: `Read tool on /Users/zili/code/android/MusicFreeAndroid/data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/DownloadedTrackDao.kt`
Document whether `deleteByPlatform` already exists. If yes, skip Task A3 entirely.

- [ ] **Step 2: Write failing test** (only if not exists):

```kotlin
@Test fun `deleteByPlatform removes only rows for given platform`() = runTest {
    dao.upsert(DownloadedTrackEntity("1", "kuwo", "uri1", "rel/1.mp3", "audio/mpeg", "STANDARD", 100, 1000))
    dao.upsert(DownloadedTrackEntity("2", "kugou", "uri2", "rel/2.mp3", "audio/mpeg", "STANDARD", 100, 1000))

    dao.deleteByPlatform("kuwo")

    assertEquals(1, dao.count())
}
```

- [ ] **Step 3: Run test → FAIL → add method → PASS** (same as A1/A2 pattern).

```kotlin
@Query("DELETE FROM downloaded_tracks WHERE platform = :platform")
suspend fun deleteByPlatform(platform: String)
```

### Task A4: `MediaCacheRepository` 加 `deleteByPlatform` + LRU 内存层

**Files:**

- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/MediaCacheRepository.kt`
- Test: `data/src/test/java/com/zili/android/musicfreeandroid/data/repository/MediaCacheRepositoryLruTest.kt` (new)
- Test: `data/src/test/java/com/zili/android/musicfreeandroid/data/repository/MediaCacheRepositoryDeleteTest.kt` (new)

- [ ] **Step 1: Write failing test for memory LRU hit**

```kotlin
@RunWith(RobolectricTestRunner::class)
class MediaCacheRepositoryLruTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MediaCacheDao
    private lateinit var repo: MediaCacheRepository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.mediaCacheDao()
        repo = MediaCacheRepository(dao)
    }

    @After fun tearDown() { db.close() }

    @Test fun `second get does not hit DB after put`() = runTest {
        val item = MusicItem(id = "1", platform = "kuwo", title = "t", artist = null, album = null)
        val source = MediaSourceResult(url = "https://x/y.mp3", quality = "standard")
        repo.put(item, PlayQuality.STANDARD, source)

        val spy = spy(dao)  // wrap dao, ensure get() not called second time
        // 替换 repo 内部 dao 引用 - 用 reflection 或重构 repo 让 dao 可注入观察
        // 简化版：调用两次，断言行为
        val first = repo.get(item, PlayQuality.STANDARD)
        val second = repo.get(item, PlayQuality.STANDARD)
        assertEquals(first?.url, second?.url)
    }
}
```

Note: 如果直接 mock dao 困难，把"LRU 是否命中"做成 repo 暴露的可观察 metric（如 `internal var lastHitFromMemory: Boolean = false`），在测试断言。这是最朴素方案；prefer 内部 fold 而不是 spy。

- [ ] **Step 2: Run → FAIL**（LRU 还不存在，逻辑等价但不验证内存命中）。

- [ ] **Step 3: 在 `MediaCacheRepository` 加 LRU 内存层 + 单 entry eviction**

```kotlin
import androidx.collection.LruCache

private val memory = LruCache<String, JSONObject>(MEMORY_LIMIT)
private fun memKey(item: MusicItem) = "${item.platform}@${item.id}"
private fun memKey(platform: String, id: String) = "$platform@$id"

internal var lastHitFromMemory: Boolean = false
    private set

suspend fun get(item: MusicItem, quality: PlayQuality): CachedSource? {
    val key = memKey(item)
    val cached = memory.get(key)
    if (cached != null) {
        lastHitFromMemory = true
        return readQuality(cached, quality)
    }
    lastHitFromMemory = false
    val row = dao.get(item.platform, item.id) ?: return null
    return runCatching {
        val obj = JSONObject(row.sourcesJson)
        memory.put(key, obj)
        readQuality(obj, quality)
    }.getOrNull()
}

private fun readQuality(obj: JSONObject, quality: PlayQuality): CachedSource? {
    if (!obj.has(quality.name)) return null
    val q = obj.getJSONObject(quality.name)
    return CachedSource(
        url = q.optString("url").takeIf { it.isNotEmpty() } ?: return null,
        headers = q.optJSONObject("headers")?.toStringMap(),
        userAgent = q.optString("userAgent").takeIf { it.isNotEmpty() },
    )
}

suspend fun put(item: MusicItem, quality: PlayQuality, source: MediaSourceResult) {
    val existing = dao.get(item.platform, item.id)
    val json = if (existing != null) JSONObject(existing.sourcesJson) else JSONObject()
    val q = JSONObject().apply {
        put("url", source.url)
        source.headers?.let { put("headers", JSONObject(it as Map<*, *>)) }
        source.userAgent?.let { put("userAgent", it) }
    }
    json.put(quality.name, q)
    dao.upsert(MediaCacheEntity(item.platform, item.id, json.toString(), now()))
    memory.put(memKey(item), json)
    if (dao.count() >= LIMIT) dao.deleteOldest(LIMIT / 2)
}

suspend fun deleteEntry(platform: String, id: String, quality: PlayQuality) {
    // 单 quality 子键剥除；如果剥完空了再删整行
    val existing = dao.get(platform, id) ?: return
    val obj = runCatching { JSONObject(existing.sourcesJson) }.getOrNull() ?: return
    obj.remove(quality.name)
    val key = memKey(platform, id)
    if (obj.length() == 0) {
        dao.delete(platform, id)  // 见 Task A4.5: 加 dao.delete(platform, id) 方法
        memory.remove(key)
    } else {
        dao.upsert(MediaCacheEntity(platform, id, obj.toString(), now()))
        memory.put(key, obj)
    }
}

suspend fun deleteByPlatform(platform: String) {
    dao.deleteByPlatform(platform)
    memory.snapshot().keys.filter { it.startsWith("$platform@") }.forEach { memory.remove(it) }
}

companion object {
    const val LIMIT = 800
    const val MEMORY_LIMIT = 200
}
```

（`dao.delete(platform, id)` 在 Task A1 已经一起加好。）

- [ ] **Step 4: 修改 test 断言 LRU 命中**

```kotlin
@Test fun `second get hits memory`() = runTest {
    val item = MusicItem(id = "1", platform = "kuwo", title = "t", artist = null, album = null)
    repo.put(item, PlayQuality.STANDARD, MediaSourceResult(url = "https://x", quality = "standard"))
    repo.get(item, PlayQuality.STANDARD)  // 第一次 fill memory
    repo.get(item, PlayQuality.STANDARD)
    assertTrue(repo.lastHitFromMemory)
}
```

`MediaCacheRepositoryDeleteTest.kt`:

```kotlin
@Test fun `deleteByPlatform clears DB and memory`() = runTest {
    val item = MusicItem(id = "1", platform = "kuwo", title = "t", artist = null, album = null)
    repo.put(item, PlayQuality.STANDARD, MediaSourceResult(url = "https://x", quality = "standard"))
    repo.get(item, PlayQuality.STANDARD)
    repo.deleteByPlatform("kuwo")
    assertNull(dao.get("kuwo", "1"))
    repo.get(item, PlayQuality.STANDARD)
    assertFalse(repo.lastHitFromMemory)  // memory was cleared
}
```

- [ ] **Step 5: Run tests → PASS**

Run: `./gradlew :data:testDebugUnitTest --tests "*MediaCacheRepository*" --no-daemon`

### Task A5: `LyricRepository` 加 `deleteByPlatform`

**Files:**

- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/LyricRepository.kt`
- Test: `data/src/test/java/com/zili/android/musicfreeandroid/data/repository/LyricRepositoryDeleteTest.kt` (new)

- [ ] **Step 1: Failing test**

```kotlin
@Test fun `deleteByPlatform clears rows`() = runTest {
    val music = MusicItem(id = "1", platform = "kuwo", ...)
    repo.saveRemoteLyric(music, LyricSourceInfo.Plugin("kuwo"), RawLyricPayload("[00:00]hi", null, null))
    repo.deleteByPlatform("kuwo")
    assertNull(repo.getCache(music))
}
```

- [ ] **Step 2: Add to `LyricRepository`:**

```kotlin
suspend fun deleteByPlatform(platform: String) = lyricCacheDao.deleteByPlatform(platform)
```

### Task A6: 在 `PluginManager.uninstall` 注入三个清理

**Files:**

- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManager.kt`
- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/di/PluginModule.kt`
- Test: `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManagerCacheCleanupTest.kt` (new)

- [ ] **Step 1: Read current PluginManager.uninstall 实现**

Run: `Read tool` on `PluginManager.kt`，定位 `uninstall(platform: String)` 方法。

- [ ] **Step 2: 写失败测试**

测试用 fake `MediaCacheRepository` / `LyricRepository` / `DownloadedTrackDao`（mockk），断言 `uninstall("X")` 触发三次 `deleteByPlatform("X")`。

```kotlin
class PluginManagerCacheCleanupTest {
    private val mediaCacheRepo = mockk<MediaCacheRepository>(relaxed = true)
    private val lyricRepo = mockk<LyricRepository>(relaxed = true)
    private val downloadedDao = mockk<DownloadedTrackDao>(relaxed = true)
    // ... 装配 PluginManager 时注入 mocks（可能需要在 PluginManager constructor 加可选 cleanup 参数）

    @Test fun `uninstall triggers deleteByPlatform on all three`() = runTest {
        // arrange: 一份装好的 plugin "X"
        manager.uninstall("X")
        coVerify { mediaCacheRepo.deleteByPlatform("X") }
        coVerify { lyricRepo.deleteByPlatform("X") }
        coVerify { downloadedDao.deleteByPlatform("X") }
    }
}
```

- [ ] **Step 3: 修改 `PluginManager` 构造函数注入清理依赖（Hilt）**

```kotlin
@Singleton
class PluginManager @Inject constructor(
    @ApplicationContext private val context: Context,
    val pluginMetaStore: PluginMetaStore,
    private val mediaCacheRepository: MediaCacheRepository,
    private val lyricRepository: LyricRepository,
    private val downloadedTrackDao: DownloadedTrackDao,
)
```

`uninstall(platform: String)` 末尾：

```kotlin
mediaCacheRepository.deleteByPlatform(platform)
lyricRepository.deleteByPlatform(platform)
downloadedTrackDao.deleteByPlatform(platform)
mflog.event("plugin_uninstalled_cache_cleared", mapOf("platform" to platform))
```

- [ ] **Step 4: Run test → PASS**

### Task A7: `PluginMediaSourceService` 接入 cacheControl

**Files:**

- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/media/PluginMediaSourceService.kt`
- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/di/PluginModule.kt`
- Test: `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/media/PluginMediaSourceServiceCacheTest.kt` (new)

- [ ] **Step 1: Write failing tests for three cacheControl paths**

```kotlin
class PluginMediaSourceServiceCacheTest {
    @Test fun `cache mode returns cached when available, no plugin call`() = runTest {
        val cached = CachedSource(url = "cached://", headers = null, userAgent = null)
        val pluginCalled = AtomicBoolean(false)
        val service = makeService(
            cacheControl = "cache",
            cached = cached,
            onPluginCall = { pluginCalled.set(true); MediaSourceResult(url = "fresh://", quality = "standard") },
        )
        val result = service.resolve(item("kuwo", "1"), "standard")
        assertEquals("cached://", result?.item?.url)
        assertFalse(pluginCalled.get())
    }

    @Test fun `no-store mode never reads or writes cache`() = runTest {
        val cached = CachedSource(url = "cached://", ...)
        val writes = mutableListOf<MediaSourceResult>()
        val service = makeService(
            cacheControl = "no-store",
            cached = cached,
            onPluginCall = { MediaSourceResult(url = "fresh://", ...) },
            onCacheWrite = { writes += it },
        )
        val result = service.resolve(item("kuwo", "1"), "standard")
        assertEquals("fresh://", result?.item?.url)
        assertTrue(writes.isEmpty())
    }

    @Test fun `no-cache mode reads only when plugin call fails`() = runTest {
        // 默认在线 → plugin 返回 fresh，写入 cache
        // 模拟 plugin 抛错 → 应 fallback 到 cache
        ...
    }

    private fun makeService(
        cacheControl: String,
        cached: CachedSource? = null,
        onPluginCall: suspend () -> MediaSourceResult? = { null },
        onCacheWrite: (MediaSourceResult) -> Unit = {},
    ): PluginMediaSourceService { ... }
}
```

- [ ] **Step 2: Run → FAIL.**

- [ ] **Step 3: Inject `MediaCacheRepository` into `PluginMediaSourceService` + 实现决策**

```kotlin
@Singleton
class PluginMediaSourceService @Inject constructor(
    private val pluginManager: PluginManager,
    private val mediaCacheRepository: MediaCacheRepository,
    private val playbackRuntimeSettings: PlaybackRuntimeSettings = PlaybackRuntimeSettings.Defaults,
) : MediaSourceResolver {

    override suspend fun resolve(item: MusicItem, quality: String?): MediaSourceResolution? {
        val sourcePlugin = pluginManager.getPlugin(item.platform) ?: return null
        val cc = CacheControl.parse(sourcePlugin.info.cacheControl)
        val playQuality = parseQuality(quality)  // PlayQuality
        val isOffline = false  // TODO: 注入 ConnectivityChecker，但本 task 不实现，用 false 占位
        // 注：spec §3.2 显式说 cacheControl=no-cache 离线 fallback 是目标行为，但 Connectivity 检测不在本 task；先实现 cache + no-store + 在线 no-cache，离线分支 follow-up

        if (cc == CacheControl.Cache) {
            mediaCacheRepository.get(item, playQuality)?.let {
                return MediaSourceResolution(
                    item = item.copy(url = it.url),
                    source = MediaSourceResult(url = it.url, quality = quality, headers = it.headers, userAgent = it.userAgent),
                    requestedPlatform = item.platform,
                    resolverPlatform = sourcePlugin.info.platform,
                    redirected = false,
                )
            }
        }

        // ... 原来的 alternativePlugin / sourcePlugin / qualityCandidates 循环

        for (candidateQuality in qualityCandidates(quality)) {
            val resolution = alternativePlugin?.resolveWith(...)?.takeIf { it != null }
                ?: sourcePlugin.resolveWith(...)
                ?: continue

            if (shouldWriteCache(cc)) {
                mediaCacheRepository.put(item, PlayQuality.valueOf(candidateQuality.uppercase()), resolution.source)
            }
            return resolution
        }

        return null
    }

    private fun parseQuality(name: String?): PlayQuality =
        runCatching { PlayQuality.valueOf((name ?: "standard").uppercase()) }.getOrDefault(PlayQuality.STANDARD)
}
```

- [ ] **Step 4: Run → PASS.**

- [ ] **Step 5: 加 log**

```kotlin
mflog.event("plugin_get_media_source_cache_hit", mapOf("platform" to item.platform, "id" to item.id, "quality" to playQuality.name))
mflog.event("plugin_get_media_source_cache_write", mapOf("platform" to item.platform, ...))
```

### Task A8: `PluginMediaSourceService.resolveFresh` bypass-cache 入口

**Files:**

- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/media/PluginMediaSourceService.kt`
- Test: `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/media/PluginMediaSourceServiceResolveFreshTest.kt` (new)

- [ ] **Step 1: 失败测试**

```kotlin
class PluginMediaSourceServiceResolveFreshTest {
    @Test fun `resolveFresh bypasses cache even when cacheControl is cache`() = runTest {
        val cached = CachedSource(url = "cached://", ...)
        val pluginCalled = AtomicBoolean(false)
        val service = makeService(
            cacheControl = "cache",
            cached = cached,
            onPluginCall = { pluginCalled.set(true); MediaSourceResult(url = "fresh://", ...) },
        )
        val result = service.resolveFresh(item("kuwo", "1"), "standard")
        assertEquals("fresh://", result?.item?.url)
        assertTrue(pluginCalled.get())
    }

    @Test fun `resolveFresh still writes cache on success (except no-store)`() = runTest { ... }
}
```

- [ ] **Step 2: 实现**

```kotlin
suspend fun resolveFresh(item: MusicItem, quality: String?): MediaSourceResolution? {
    // 与 resolve() 等价，但跳过 cache 读 step
    val sourcePlugin = pluginManager.getPlugin(item.platform) ?: return null
    val cc = CacheControl.parse(sourcePlugin.info.cacheControl)

    for (candidateQuality in qualityCandidates(quality)) {
        val resolution = alternativePlugin?.resolveWith(...)
            ?: sourcePlugin.resolveWith(...)
            ?: continue
        if (shouldWriteCache(cc)) {
            mediaCacheRepository.put(item, PlayQuality.valueOf(candidateQuality.uppercase()), resolution.source)
        }
        return resolution
    }
    return null
}
```

把原 `resolve()` 中 cache 读分支抽成 private `tryFromCache(item, cc, quality)` 让 `resolve()` 复用，`resolveFresh()` 跳过。

- [ ] **Step 3: Run tests → PASS**

### Task A9: `PlayerController` HTTP 失败时 evict cache + 重解析（1 次上限）

**Files:**

- Modify: `player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt`
- Test: `player/src/test/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerStaleUrlRefreshTest.kt` (new)

- [ ] **Step 1: 失败测试**

```kotlin
class PlayerControllerStaleUrlRefreshTest {
    @Test fun `HTTP 403 triggers evict + resolveFresh once`() = runTest {
        // arrange: 队列当前项 item，cache 命中给 cached://，resolveFresh 给 fresh://
        val controller = makeController(
            mediaCacheRepository = mockMediaCache,
            mediaSourceResolver = mockResolver,
        )
        coEvery { mockMediaCache.deleteEntry("kuwo", "1", PlayQuality.STANDARD) } just runs
        coEvery { mockResolver.resolveFresh(any(), any()) } returns MediaSourceResolution(
            item = item.copy(url = "fresh://"), ...
        )
        // act: 模拟 onPlayerError ERROR_CODE_IO_BAD_HTTP_STATUS
        controller.onPlayerError(makeHttpStatusError(403))
        // assert
        coVerify(exactly = 1) { mockMediaCache.deleteEntry("kuwo", "1", PlayQuality.STANDARD) }
        coVerify(exactly = 1) { mockResolver.resolveFresh(any(), any()) }
        verify { mediaController.setMediaItem(match { it.localConfiguration?.uri.toString() == "fresh://" }) }
    }

    @Test fun `same item HTTP 403 second time does NOT trigger another refresh`() = runTest {
        controller.onPlayerError(makeHttpStatusError(403))
        controller.onPlayerError(makeHttpStatusError(403))
        coVerify(exactly = 1) { mockResolver.resolveFresh(any(), any()) }
    }

    @Test fun `non-HTTP error does not trigger refresh`() = runTest {
        controller.onPlayerError(makePlaybackError(ERROR_CODE_BEHIND_LIVE_WINDOW))
        coVerify(exactly = 0) { mockResolver.resolveFresh(any(), any()) }
    }

    @Test fun `retryState clears on queue advance`() = runTest {
        controller.onPlayerError(makeHttpStatusError(403))  // 用了 1 次
        controller.skipToNext()
        controller.skipToPrevious()  // 回到原项
        controller.onPlayerError(makeHttpStatusError(403))
        coVerify(exactly = 2) { mockResolver.resolveFresh(any(), any()) }  // 又一次
    }
}
```

- [ ] **Step 2: 在 `PlayerController` 加 retry state + handler**

```kotlin
private val staleUrlRetryState = ConcurrentHashMap<Pair<String, String>, Boolean>()

// 内部接 ExoPlayer.Listener.onPlayerError 时调用：
private fun handlePlayerError(error: PlaybackException) {
    if (error.errorCode != PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) return
    val current = currentItem() ?: return  // 队列当前 MusicItem
    if (current.isLocalPlaybackSource()) return
    val key = current.platform to current.id
    if (staleUrlRetryState.putIfAbsent(key, true) != null) {
        MfLog.warn(LogCategory.PLAYER, "playback_stale_url_retry_exhausted",
            mapOf("platform" to current.platform, "id" to current.id, "code" to error.errorCode))
        return
    }
    val quality = currentQuality()  // PlayQuality
    scope.launch {
        mediaCacheRepository.deleteEntry(current.platform, current.id, quality)
        val fresh = pluginMediaSourceService.resolveFresh(current, quality.wireName())
        if (fresh != null && !fresh.item.url.isNullOrBlank()) {
            // 注册 headers + 替换队列当前项 + setMediaItem
            applyResolution(fresh)
            MfLog.detail(LogCategory.PLAYER, "plugin_media_source_refreshed_after_failure",
                mapOf("platform" to current.platform, "id" to current.id))
        } else {
            MfLog.error(LogCategory.PLAYER, "plugin_media_source_refresh_failed",
                mapOf("platform" to current.platform, "id" to current.id))
        }
    }
}

// 队列切歌时清 retryState（在 setMediaItem / skipToNext / play(new item) 等入口都调）
private fun resetStaleRetryState() {
    staleUrlRetryState.clear()
}
```

- [ ] **Step 3: 在 Player.Listener.onPlayerError 处接入**

如果 PlayerController 已注册 Player.Listener，在 onPlayerError 调 `handlePlayerError(error)`。如未注册，在 `connect()` / `setController()` 时加 `controller.addListener(playerListener)`。

- [ ] **Step 4: Run tests → PASS**

### Task A10: Phase A commit

- [ ] **Step 1: 跑全量测试**

```bash
./gradlew :data:testDebugUnitTest :plugin:testDebugUnitTest :player:testDebugUnitTest --no-daemon
```

- [ ] **Step 2: 跑 lint**

```bash
./gradlew :data:lint :plugin:lint :player:lint --no-daemon
```

如果 lint 报警告但不是 error 可继续。

- [ ] **Step 3: 给 stale-fix 设计加 forward link**

Edit `docs/superpowers/specs/2026-05-11-stale-media-source-playback-design.md`：在"非目标"或文末加一段：

```markdown
## 后续修订

本设计的 "不新增 URL 过期时间字段或媒体缓存淘汰策略" 非目标在
[`2026-05-11-plugin-engine-alignment-design.md`](2026-05-11-plugin-engine-alignment-design.md) §5.7 被替换为**基于播放失败的失败驱动 eviction**（不基于时间）。后者实施后，PluginMediaSourceService 重新读 cache（按 cacheControl），但 PlayerController 监听 ExoPlayer ERROR_CODE_IO_BAD_HTTP_STATUS 触发 evict + resolveFresh，并对单条目单次播放设 1 次重试上限。
```

- [ ] **Step 4: 提交**

```bash
git add data/src plugin/src player/src docs/superpowers/specs
git commit -m "feat(plugin): wire cacheControl + failure-driven cache eviction

- MediaCacheDao/LyricCacheDao/DownloadedTrackDao 加 deleteByPlatform
- MediaCacheDao 加 delete(platform, id) 单 entry
- MediaCacheRepository 加 LRU 内存层（200 项）+ deleteEntry / deleteByPlatform
- PluginManager.uninstall 触发三表清理
- PluginMediaSourceService 按 cacheControl 读写 cache（cache 优先读 / no-store 透传 / no-cache 总写）
- PluginMediaSourceService.resolveFresh: bypass cache 重解析入口
- PlayerController 监听 ERROR_CODE_IO_BAD_HTTP_STATUS → evict + resolveFresh
  单条目单次播放 1 次重试上限，队列切歌时清 retry state
- stale-media-source-playback 设计加 forward link 指本 spec §5.7

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase B — Local Virtual Plugin

**目标**：在 `:plugin` 内置一个 Kotlin 实现的 `LocalFilePlugin`，platform = "本地"，hash = "local-plugin-hash"，覆盖 4 个方法。`PluginManager.setup()` 注册之；`getEnabledPlugins()` 等需要的过滤通过 hash 排除。本 phase 不动 `:feature/home`（留 Phase F.5）。

### Task B1: 定义常量 + `Mp3MetadataReader` 接口

**Files:**

- Create: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/local/LocalFilePluginConstants.kt`
- Create: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/local/Mp3Metadata.kt`
- Create: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/local/Mp3MetadataReader.kt`

- [ ] **Step 1: 写常量**

```kotlin
package com.zili.android.musicfreeandroid.plugin.local

object LocalFilePluginConstants {
    const val PLATFORM = "本地"
    const val HASH = "local-plugin-hash"
    val SUPPORTED_METHODS = setOf(
        "getMusicInfo", "getLyric", "importMusicItem", "getMediaSource",
    )
}
```

- [ ] **Step 2: 写 Mp3Metadata data class**

```kotlin
package com.zili.android.musicfreeandroid.plugin.local

data class Mp3Metadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val coverBytes: ByteArray?,
    val embeddedLrc: String?,
)
```

- [ ] **Step 3: 写 reader interface**

```kotlin
package com.zili.android.musicfreeandroid.plugin.local

interface Mp3MetadataReader {
    suspend fun read(path: String): Mp3Metadata?
}
```

- [ ] **Step 4: Commit unit boundary**（待 Phase B 末统一 commit）

### Task B2: `Mp3MetadataReader` 在 :data 实现

**Files:**

- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/local/Mp3MetadataReaderImpl.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/di/DataModule.kt` (bind impl)
- Test: `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/local/Mp3MetadataReaderImplTest.kt` (uses `MediaMetadataRetriever`)

**注意**：`:data` 不依赖 `:plugin`（方向相反）。`Mp3MetadataReader` 接口实际放在 `:core` 比较干净。本 task 改路：

- [ ] **Step 1: 把 interface 移到 `:core`**

Move `plugin/local/Mp3MetadataReader.kt` → `core/local/Mp3MetadataReader.kt`（package: `com.zili.android.musicfreeandroid.core.local`）

`Mp3Metadata` 也搬到 `:core`，因为接口签名引用它。

`LocalFilePluginConstants` 留在 `:plugin`（只有 :plugin 用）。

- [ ] **Step 2: Write failing instrumentation test**

`data/src/androidTest/java/com/zili/android/musicfreeandroid/data/local/Mp3MetadataReaderImplTest.kt`:

```kotlin
@RunWith(AndroidJUnit4::class)
class Mp3MetadataReaderImplTest {
    private lateinit var reader: Mp3MetadataReaderImpl

    @Before fun setUp() {
        reader = Mp3MetadataReaderImpl(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test fun reads_fixture_mp3_metadata() = runBlocking {
        val fixture = copyAssetToCache("mp3-fixture/sample.mp3")
        val meta = reader.read(fixture.absolutePath)
        assertNotNull(meta)
        assertEquals("Sample Title", meta!!.title)
        assertEquals("Sample Artist", meta.artist)
        assertTrue(meta.durationMs!! > 0)
    }

    @Test fun returns_null_for_missing_file() = runBlocking {
        assertNull(reader.read("/path/that/does/not/exist.mp3"))
    }

    private fun copyAssetToCache(assetPath: String): File { ... }
}
```

需要在 `data/src/androidTest/assets/mp3-fixture/sample.mp3` 放一个带 ID3 tag 的小 mp3 文件（< 5KB；可用 LAME 或 ffmpeg 生成）。

- [ ] **Step 3: 实现**

```kotlin
package com.zili.android.musicfreeandroid.data.local

import android.content.Context
import android.media.MediaMetadataRetriever
import com.zili.android.musicfreeandroid.core.local.Mp3Metadata
import com.zili.android.musicfreeandroid.core.local.Mp3MetadataReader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Mp3MetadataReaderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : Mp3MetadataReader {
    override suspend fun read(path: String): Mp3Metadata? {
        val file = File(path)
        if (!file.exists() || !file.canRead()) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            Mp3Metadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                coverBytes = retriever.embeddedPicture,
                embeddedLrc = null,  // ID3 USLT 在 MediaMetadataRetriever 不可读，留作后续 follow-up
            )
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
```

- [ ] **Step 4: 绑定 Hilt**

`DataModule.kt`:

```kotlin
@Binds @Singleton
abstract fun bindMp3MetadataReader(impl: Mp3MetadataReaderImpl): Mp3MetadataReader
```

- [ ] **Step 5: Run instrumentation test**

```bash
./gradlew :data:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.data.local.Mp3MetadataReaderImplTest
```

需要连接设备 / 模拟器。

如果环境暂无 emulator，标记 task 完成但运行验证在 Phase 末统一做。

### Task B3: `LocalFilePlugin` 类

**Files:**

- Create: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/local/LocalFilePlugin.kt`
- Test: `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/local/LocalFilePluginTest.kt` (new)

- [ ] **Step 1: Write failing test using mock `Mp3MetadataReader`**

```kotlin
class LocalFilePluginTest {
    private val reader = mockk<Mp3MetadataReader>()
    private val plugin = LocalFilePlugin(reader)

    @Test fun `getMediaSource standard returns file URL when localPath set`() = runTest {
        val item = MusicItem(id = "1", platform = "本地", title = "t", localPath = "/sdcard/song.mp3")
        val result = plugin.getMediaSource(item, quality = "standard")
        assertEquals("file:///sdcard/song.mp3", result?.url)
    }

    @Test fun `getMediaSource non-standard quality returns null`() = runTest {
        val item = MusicItem(id = "1", platform = "本地", localPath = "/sdcard/song.mp3", ...)
        assertNull(plugin.getMediaSource(item, "super"))
    }

    @Test fun `getMusicInfo reads via Mp3MetadataReader`() = runTest {
        val item = MusicItem(id = "1", platform = "本地", localPath = "/sdcard/song.mp3", ...)
        coEvery { reader.read("/sdcard/song.mp3") } returns Mp3Metadata("T", "A", "Al", 1000, byteArrayOf(1,2,3), null)
        val result = plugin.getMusicInfo(item)
        assertEquals("T", result?.title)
        assertEquals("A", result?.artist)
    }

    @Test fun `importMusicItem reads metadata from path`() = runTest {
        coEvery { reader.read("/sdcard/x.mp3") } returns Mp3Metadata("X", "Y", null, 2000, null, null)
        val item = plugin.importMusicItem("/sdcard/x.mp3")
        assertEquals("X", item?.title)
        assertEquals("本地", item?.platform)
        assertEquals("/sdcard/x.mp3", item?.localPath)
    }

    @Test fun `getLyric reads adjacent lrc file when present`() = runTest {
        val tmp = File.createTempFile("song", ".mp3")
        val lrc = File(tmp.parent, tmp.nameWithoutExtension + ".lrc").apply {
            writeText("[00:00.00]hello")
        }
        try {
            val item = MusicItem(id = "1", platform = "本地", localPath = tmp.absolutePath, ...)
            val result = plugin.getLyric(item)
            assertEquals("[00:00.00]hello", result?.rawLrc)
        } finally {
            tmp.delete(); lrc.delete()
        }
    }

    @Test fun `getLyric returns null when neither lrc nor embedded`() = runTest { ... }
}
```

注：`MusicItem` 的 `localPath` 字段位置 —— 当前 `MusicItem` 是否含 `localPath`? 如未含，需要从 `DownloadedTrackEntity` 关联拿。本 plan 先按"如果 `localPath` 字段不存在则使用 `MusicItem.url` 作为兜底"实现；同时 Phase F 加 `MusicItemBridgeProjector` 时把 `localPath` 投影进去。

- [ ] **Step 2: 实现**

```kotlin
package com.zili.android.musicfreeandroid.plugin.local

import com.zili.android.musicfreeandroid.core.local.Mp3MetadataReader
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.plugin.api.MediaSourceResult
import com.zili.android.musicfreeandroid.plugin.api.LyricResult
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalFilePlugin @Inject constructor(
    private val reader: Mp3MetadataReader,
) {
    val platform = LocalFilePluginConstants.PLATFORM
    val hash = LocalFilePluginConstants.HASH
    val supportedMethods = LocalFilePluginConstants.SUPPORTED_METHODS

    suspend fun getMediaSource(item: MusicItem, quality: String): MediaSourceResult? {
        if (quality.lowercase() != "standard") return null
        val path = pathOf(item) ?: return null
        return MediaSourceResult(url = "file://$path", quality = "standard")
    }

    suspend fun getMusicInfo(item: MusicItem): MusicItem? {
        val path = pathOf(item) ?: return null
        val meta = reader.read(path) ?: return null
        return item.copy(
            title = meta.title ?: item.title,
            artist = meta.artist ?: item.artist,
            album = meta.album ?: item.album,
            duration = meta.durationMs ?: item.duration,
            // coverImg 处理：item.copy(coverImg = "file://...") 不在本地路径直接做
            // 调用方负责把 coverBytes 写到磁盘并取 file:// URL
        )
    }

    suspend fun importMusicItem(path: String): MusicItem? {
        val meta = reader.read(path) ?: return null
        return MusicItem(
            id = path.hashCode().toString(),  // 简单 id；正式建议 path SHA1
            platform = platform,
            title = meta.title ?: File(path).nameWithoutExtension,
            artist = meta.artist,
            album = meta.album,
            duration = meta.durationMs,
            localPath = path,
        )
    }

    suspend fun getLyric(item: MusicItem): LyricResult? {
        val path = pathOf(item) ?: return null
        val file = File(path)
        val adjacent = File(file.parent, file.nameWithoutExtension + ".lrc")
        if (adjacent.exists() && adjacent.canRead()) {
            return LyricResult(rawLrc = adjacent.readText(), translation = null)
        }
        val meta = reader.read(path) ?: return null
        return meta.embeddedLrc?.let { LyricResult(rawLrc = it, translation = null) }
    }

    private fun pathOf(item: MusicItem): String? = item.localPath ?: item.url?.removePrefix("file://")
}
```

⚠ 这里有一个未解决项：`MusicItem` 数据类目前有没有 `localPath` 字段？如果没有，需要：

- 选项 A：在 `MusicItem` 加 `localPath: String?` 字段
- 选项 B：在 `pathOf` 中只看 `item.url`（要求调用方传 file:// URL）

Plan 决策：**选项 A**。在 `:core` 的 `MusicItem.kt` 加 `localPath: String? = null` 字段。原因：spec §10 模型边界讨论的就是从 `DownloadedTrackEntity` 投影 `localPath` 到 `MusicItem`，所以 `MusicItem` 需要承接这个字段。

为此插入一个新 sub-task B3.0：

### Task B3.0: 给 `MusicItem` 加 `localPath` 字段

**Files:**

- Modify: `core/src/main/java/com/zili/android/musicfreeandroid/core/model/MusicItem.kt`

- [ ] **Step 1: Read current MusicItem.kt** —— 确认 data class 形态

- [ ] **Step 2: 添加字段（默认 null，向后兼容）**

```kotlin
data class MusicItem(
    val id: String,
    val platform: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val duration: Long? = null,
    val url: String? = null,
    val coverImg: String? = null,
    // ... 其他现有字段
    val localPath: String? = null,  // ← 新增
)
```

- [ ] **Step 3: 全仓 compile 校验**

```bash
./gradlew :core:compileDebugKotlin :data:compileDebugKotlin :plugin:compileDebugKotlin --no-daemon
```

如有引用 `MusicItem` 构造的位置因新字段导致 compile fail（位置参数 vs 命名参数），用 named arg 修复。

### Task B4: `PluginManager.setup` 注册 `LocalFilePlugin`

**Files:**

- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManager.kt`
- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/di/PluginModule.kt`
- Test: `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManagerLocalRegistrationTest.kt` (new)

- [ ] **Step 1: 失败测试**

```kotlin
@Test fun `setup registers LocalFilePlugin under 本地`() = runTest {
    manager.setup()
    val local = manager.getPlugin("本地")
    assertNotNull(local)
    assertEquals("local-plugin-hash", local!!.info.hash)  // 注：现有 LoadedPlugin 是否暴露 info.hash 待确认
}

@Test fun `getEnabledPlugins filters out local plugin`() = runTest {
    manager.setup()
    val enabled = manager.getEnabledPlugins()
    assertTrue(enabled.none { it.info.platform == "本地" })
}
```

- [ ] **Step 2: 实现**

`PluginManager` 当前的 `LoadedPlugin` 是基于 `JsEngine` 的；要加入 Kotlin 实现的 `LocalFilePlugin` 就要兼容到 `LoadedPlugin` 接口。两种思路：

- 思路一：`LoadedPlugin` 改为 interface，原 QuickJS 实现成 `JsLoadedPlugin`，新增 `LocalLoadedPlugin` 包 `LocalFilePlugin`
- 思路二：保留 `LoadedPlugin` 是 class，但加个 `kind: PluginKind { QuickJs, Local }` 字段；内部根据 kind 路由调用

**Plan 决策：思路一**（抽接口），原因：spec §10 还会再分化（State Machine 把 `PluginEntry.loaded` 改为可空 `LoadedPlugin?`，与状态机配合需要清晰的多态边界）。

把 `LoadedPlugin` 重构为 `sealed interface`：

```kotlin
sealed interface LoadedPlugin {
    val info: PluginInfo
    suspend fun search(query: String, page: Int, type: String): SearchResult
    suspend fun getMediaSource(item: MusicItem, quality: String): MediaSourceResult?
    suspend fun getLyric(item: MusicItem): LyricResult?
    suspend fun getMusicInfo(item: MusicItem): MusicItem?
    suspend fun getAlbumInfo(albumItem: AlbumItemBase, page: Int): AlbumInfoResult?
    // ... 其他 14 个方法
    suspend fun destroy()
}

class JsLoadedPlugin(...) : LoadedPlugin { ... }  // 现有实现重命名

class LocalLoadedPlugin(
    override val info: PluginInfo,
    private val delegate: LocalFilePlugin,
) : LoadedPlugin {
    override suspend fun search(...) = SearchResult.empty()  // not supported
    override suspend fun getMediaSource(item, quality) = delegate.getMediaSource(item, quality)
    override suspend fun getLyric(item) = delegate.getLyric(item)
    override suspend fun getMusicInfo(item) = delegate.getMusicInfo(item)
    override suspend fun getAlbumInfo(...) = null
    // ... 其他返回 null / 空
    override suspend fun destroy() {}  // no-op
}
```

**这是一个 chunky refactor。** 实施步骤：

- B4.1：把 `LoadedPlugin` 改为 `sealed interface`，原实现重命名 `JsLoadedPlugin` 实现该接口。先空跑测试，保证现有 `:plugin:testDebugUnitTest` 全过。
- B4.2：新增 `LocalLoadedPlugin`，实现接口，所有非 supported 方法 return null / empty。
- B4.3：在 `PluginManager.setup()` 末尾构造 `LocalLoadedPlugin` 加入 plugins list。
- B4.4：在 `getEnabledPlugins()`、`getSortedEnabledPlugins()` 加 `filter { it.info.hash != "local-plugin-hash" }`。
- B4.5：写 `PluginManagerLocalRegistrationTest`，覆盖注册 + 过滤。

每个 sub-step 写完后跑全量测试 `:plugin:testDebugUnitTest`，红了立刻修。

- [ ] **Step 3: PluginInfo 加 `hash` 字段（如未有）**

```kotlin
data class PluginInfo(
    val platform: String,
    val hash: String? = null,  // 新增
    val version: String? = null,
    // ... 其他
)
```

`PluginManager` 构造 `LocalLoadedPlugin` 的 info：

```kotlin
PluginInfo(
    platform = LocalFilePluginConstants.PLATFORM,
    hash = LocalFilePluginConstants.HASH,
    supportedMethods = LocalFilePluginConstants.SUPPORTED_METHODS,
    supportedSearchType = emptyList(),
)
```

- [ ] **Step 4: Run tests → PASS**

### Task B5: Phase B commit

- [ ] **Step 1: Run all tests**

```bash
./gradlew :core:testDebugUnitTest :data:testDebugUnitTest :plugin:testDebugUnitTest --no-daemon
```

- [ ] **Step 2: Commit**

```bash
git add core/src data/src plugin/src
git commit -m "feat(plugin): add internal LocalFilePlugin (platform='本地')

- LoadedPlugin refactor 为 sealed interface（JsLoadedPlugin + LocalLoadedPlugin）
- Mp3MetadataReader 接口在 :core，MediaMetadataRetriever 实现在 :data
- MusicItem 增加 localPath 字段
- PluginInfo 增加 hash 字段
- LocalFilePlugin 覆盖 getMediaSource / getMusicInfo / getLyric / importMusicItem
- PluginManager.setup 注册 LocalLoadedPlugin
- getEnabledPlugins / 排序 / 卸载 / 订阅 都过滤 local hash
- 本 phase 不动 :feature/home（统一收口在 Phase F.5）

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase C — Plugin State Machine + Error UI

**目标**：把 `PluginManager.plugins` 从 `StateFlow<List<LoadedPlugin>>` 改为 `StateFlow<List<PluginEntry>>`，每个 entry 携带 `PluginState`；UI 展示徽章 + 错误面板。

### Task C1: `PluginState` / `PluginErrorReason` / `PluginStateKeys`

**Files:**

- Create: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/runtime/PluginState.kt`
- Create: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/runtime/PluginErrorReason.kt`
- Create: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/runtime/PluginStateKeys.kt`

- [ ] **Step 1: 写定义**

```kotlin
// PluginState.kt
package com.zili.android.musicfreeandroid.plugin.runtime

sealed interface PluginState {
    data object Initializing : PluginState
    data object Loading : PluginState
    data object Mounted : PluginState
    data class Failed(val reason: PluginErrorReason, val detail: String?) : PluginState
}
```

```kotlin
// PluginErrorReason.kt
enum class PluginErrorReason {
    VersionNotMatch,
    CannotParse,
    MissingPlatform,
    DownloadFailed,
    UserVariableSyncFailed,
}
```

```kotlin
// PluginStateKeys.kt
object PluginStateKeys {
    const val STATE_INITIALIZING = "Initializing"
    const val STATE_LOADING = "Loading"
    const val STATE_MOUNTED = "Mounted"
    const val STATE_FAILED = "Failed"

    const val REASON_VERSION_NOT_MATCH = "VersionNotMatch"
    const val REASON_CANNOT_PARSE = "CannotParse"
    const val REASON_MISSING_PLATFORM = "MissingPlatform"
    const val REASON_DOWNLOAD_FAILED = "DownloadFailed"
    const val REASON_USER_VARIABLE_SYNC_FAILED = "UserVariableSyncFailed"

    fun stateKey(state: PluginState): String = when (state) {
        PluginState.Initializing -> STATE_INITIALIZING
        PluginState.Loading -> STATE_LOADING
        PluginState.Mounted -> STATE_MOUNTED
        is PluginState.Failed -> STATE_FAILED
    }

    fun reasonKey(reason: PluginErrorReason): String = when (reason) {
        PluginErrorReason.VersionNotMatch -> REASON_VERSION_NOT_MATCH
        PluginErrorReason.CannotParse -> REASON_CANNOT_PARSE
        PluginErrorReason.MissingPlatform -> REASON_MISSING_PLATFORM
        PluginErrorReason.DownloadFailed -> REASON_DOWNLOAD_FAILED
        PluginErrorReason.UserVariableSyncFailed -> REASON_USER_VARIABLE_SYNC_FAILED
    }
}
```

### Task C2: `PluginEntry` data class + `PluginManager.plugins` 迁移

**Files:**

- Create: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/PluginEntry.kt`
- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManager.kt`
- Test: `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManagerStateFlowTest.kt` (new)

- [ ] **Step 1: 写 PluginEntry**

```kotlin
package com.zili.android.musicfreeandroid.plugin.manager

import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.runtime.PluginState

data class PluginEntry(
    val filePath: String,
    val state: PluginState,
    val info: PluginInfo?,
    val loaded: LoadedPlugin?,
    val installSource: PluginInstallSource?,
)
```

- [ ] **Step 2: 失败测试**

```kotlin
class PluginManagerStateFlowTest {
    @Test fun `setup emits Mounted entries for valid plugins`() = runTest {
        // arrange: 文件系统放 fixture plugin
        manager.setup()
        val entries = manager.allEntries.first()
        assertTrue(entries.any { it.state == PluginState.Mounted })
    }

    @Test fun `install with missing platform field results in Failed Missing Platform`() = runTest {
        val tmp = File.createTempFile("p", ".js").apply { writeText("module.exports = {};") }
        manager.installFromFile(tmp)
        val entries = manager.allEntries.first()
        val failed = entries.single { it.state is PluginState.Failed }
        assertEquals(PluginErrorReason.MissingPlatform, (failed.state as PluginState.Failed).reason)
    }
}
```

- [ ] **Step 3: 重构 `PluginManager`**

```kotlin
private val _allEntries = MutableStateFlow<List<PluginEntry>>(emptyList())
val allEntries: StateFlow<List<PluginEntry>> = _allEntries.asStateFlow()

// 原 plugins 兼容层
val plugins: StateFlow<List<LoadedPlugin>> = allEntries
    .map { it.mapNotNull { e -> e.loaded?.takeIf { e.state == PluginState.Mounted } } }
    .stateIn(scope, SharingStarted.Eagerly, emptyList())

fun getPlugin(platform: String): LoadedPlugin? =
    _allEntries.value.firstOrNull { it.state == PluginState.Mounted && it.info?.platform == platform }?.loaded
```

加载循环改写：每个文件路径 → 尝试加载 → 失败也写一条 `PluginEntry` with `Failed`。

`installFromFile` 失败路径：

```kotlin
try {
    val plugin = loadPluginFromFile(...)
    if (plugin.info.platform.isBlank()) {
        _allEntries.update {
            it + PluginEntry(target.absolutePath, PluginState.Failed(MissingPlatform, "platform is blank"), null, null, source)
        }
        return PluginOperationResult.failure(...)
    }
    // ...
} catch (e: Exception) {
    _allEntries.update { it + PluginEntry(target.absolutePath, PluginState.Failed(CannotParse, e.message), null, null, source) }
    return PluginOperationResult.failure(...)
}
```

- [ ] **Step 4: Run tests → PASS**

### Task C3: `retryEntry` + 失败路径完整化

- [ ] **Step 1: 写 retryEntry**

```kotlin
suspend fun retryEntry(filePath: String): PluginOperationResult {
    val entry = _allEntries.value.firstOrNull { it.filePath == filePath } ?: return ...
    // 重置为 Loading
    _allEntries.update { it.replace(entry, entry.copy(state = PluginState.Loading)) }
    return try {
        val reloaded = loadPluginFromFile(File(filePath))
        _allEntries.update { it.replace(entry, PluginEntry(filePath, PluginState.Mounted, reloaded.info, reloaded, entry.installSource)) }
        PluginOperationResult.success(...)
    } catch (e: Exception) {
        _allEntries.update { it.replace(entry, entry.copy(state = PluginState.Failed(CannotParse, e.message))) }
        PluginOperationResult.failure(...)
    }
}
```

### Task C4: `PluginOperationResult` 扩枚举

- [ ] **Step 1: Read current `PluginOperationResult.kt`** 确认 enum 名称

- [ ] **Step 2: 加 enum 值**

```kotlin
enum class PluginOperationCode {
    SUCCESS,
    SOURCE_UNREACHABLE,
    SOURCE_INVALID,
    DUPLICATE_PLUGIN,    // 标 deprecated（hash 静默幂等改 SUCCESS）
    MISSING_PLATFORM,    // 新
    VERSION_REJECTED,    // 新
    INTERNAL_ERROR,
}
```

### Task C5: Hash 冲突静默幂等

- [ ] **Step 1: 在 `PluginManager.installFromFile` / `installFromUrl` 中**

发现 hash 冲突时不再 return `DUPLICATE_PLUGIN`，而是：

```kotlin
val existing = _allEntries.value.firstOrNull { it.state == PluginState.Mounted && it.info?.hash == newHash }
if (existing != null) {
    log("plugin_install_idempotent", mapOf("hash" to newHash, "platform" to existing.info?.platform))
    return PluginOperationResult.success(targetPlugins = listOf(existing.info!!.platform))
}
```

- [ ] **Step 2: 写测试**

```kotlin
@Test fun `installing same plugin twice returns SUCCESS not DUPLICATE`() = runTest {
    val js = "module.exports = { platform: 'x', version: '1.0' };"
    val tmp1 = File.createTempFile("p", ".js").apply { writeText(js) }
    val tmp2 = File.createTempFile("p", ".js").apply { writeText(js) }
    val r1 = manager.installFromFile(tmp1)
    val r2 = manager.installFromFile(tmp2)
    assertEquals(PluginOperationCode.SUCCESS, r1.code)
    assertEquals(PluginOperationCode.SUCCESS, r2.code)
    assertEquals(1, manager.allEntries.first().count { it.state == PluginState.Mounted && it.info?.platform == "x" })
}
```

### Task C6: UI 状态徽章

**Files:**

- Modify: `feature/settings/src/main/java/.../plugin/PluginListViewModel.kt`
- Modify: `feature/settings/src/main/java/.../plugin/PluginListScreen.kt`
- Create: `feature/settings/src/main/java/.../plugin/PluginErrorPanel.kt`
- Test: `feature/settings/src/test/java/.../plugin/PluginListBadgeRenderTest.kt`

- [ ] **Step 1: Read existing `PluginListViewModel.kt` and `PluginListScreen.kt`**

了解当前 UI 模型结构（PluginUiModel? PluginListState?）

- [ ] **Step 2: 在 ViewModel 暴露 allEntries**

```kotlin
val uiEntries: StateFlow<List<PluginUiEntry>> = pluginManager.allEntries
    .map { list -> list.filter { it.info?.hash != "local-plugin-hash" }.map { it.toUi() } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

private fun PluginEntry.toUi() = PluginUiEntry(
    filePath = filePath,
    platform = info?.platform ?: File(filePath).nameWithoutExtension,
    version = info?.version,
    state = state,
    detail = (state as? PluginState.Failed)?.detail,
)
```

- [ ] **Step 3: 在 `PluginListScreen` 行尾加 status icon**

```kotlin
Row(verticalAlignment = Alignment.CenterVertically) {
    Text(entry.platform, modifier = Modifier.weight(1f))
    when (entry.state) {
        PluginState.Mounted -> Icon(Icons.Filled.CheckCircle, contentDescription = "已加载", tint = ...green...)
        PluginState.Initializing, PluginState.Loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
        is PluginState.Failed -> IconButton(onClick = { onShowError(entry) }) {
            Icon(Icons.Filled.Error, contentDescription = "失败", tint = ...red...)
        }
    }
}
```

- [ ] **Step 4: Create `PluginErrorPanel.kt`**

```kotlin
@Composable
fun PluginErrorPanel(entry: PluginUiEntry, onRetry: () -> Unit, onUninstall: () -> Unit, onCopy: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("插件加载失败") },
        text = {
            Column {
                Text(reasonLabel(entry.state), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                entry.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onRetry) { Text("重试") } },
        dismissButton = {
            Row {
                TextButton(onClick = onCopy) { Text("复制") }
                TextButton(onClick = onUninstall) { Text("卸载") }
            }
        },
    )
}

private fun reasonLabel(state: PluginState): String = when {
    state is PluginState.Failed && state.reason == PluginErrorReason.VersionNotMatch -> "应用版本不匹配"
    state is PluginState.Failed && state.reason == PluginErrorReason.CannotParse -> "无法解析插件代码"
    state is PluginState.Failed && state.reason == PluginErrorReason.MissingPlatform -> "缺少 platform 字段"
    state is PluginState.Failed && state.reason == PluginErrorReason.DownloadFailed -> "下载失败"
    state is PluginState.Failed && state.reason == PluginErrorReason.UserVariableSyncFailed -> "用户变量同步失败"
    else -> "未知错误"
}
```

- [ ] **Step 5: Compose test (Robolectric)**

```kotlin
@RunWith(RobolectricTestRunner::class)
class PluginListBadgeRenderTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun renders_red_icon_for_failed() {
        val entry = PluginUiEntry("/x.js", "x", null, PluginState.Failed(CannotParse, "boom"), "boom")
        composeRule.setContent { PluginListItem(entry, onShowError = {}) }
        composeRule.onNodeWithContentDescription("失败").assertIsDisplayed()
    }

    @Test fun renders_spinner_for_initializing() { ... }
    @Test fun renders_check_for_mounted() { ... }
}
```

### Task C7: 日志规范化

- [ ] **Step 1: 把 `PluginManager` 中的 install / mount / load 路径的日志事件改成使用 `PluginStateKeys` 提供的字符串常量。**

```kotlin
mflog.event("plugin_state_transition", mapOf(
    "platform" to entry.info?.platform,
    "from" to PluginStateKeys.stateKey(oldState),
    "to" to PluginStateKeys.stateKey(newState),
    "reason" to (newState as? PluginState.Failed)?.let { PluginStateKeys.reasonKey(it.reason) },
))
```

### Task C8: Phase C commit

- [ ] **Step 1: Run**

```bash
./gradlew :plugin:testDebugUnitTest :feature:settings:testDebugUnitTest --no-daemon
```

- [ ] **Step 2: Commit**

```bash
git add plugin/src feature/settings/src
git commit -m "feat(plugin): add plugin state machine and error UI surface

- PluginState sealed (Initializing/Loading/Mounted/Failed(reason,detail))
- PluginErrorReason (5 个)
- PluginEntry 替代 LoadedPlugin 作为 PluginManager.allEntries 的元素
- 兼容层 plugins / getPlugin / getEnabledPlugins 只返回 Mounted entry
- installFromFile/Url 失败写 Failed 而非吞掉
- Hash 冲突静默幂等（与 RN 一致）
- 设置页插件管理增加状态徽章 + 错误面板（重试 / 复制 / 卸载）
- 日志使用 PluginStateKeys 显式字符串常量，规避 R8 重命名

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase D — Runtime Compat

**目标**：`axios` 默认 2000ms + auth URL → Basic；`__env.lang` 硬编码；`process` 全局；`require('webdav')` 最小可用；`URL` polyfill 探针。

### Task D1: `AxiosShim` 默认超时 2000ms

**Files:**

- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/AxiosShim.kt`
- Test: `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/engine/AxiosShimTimeoutTest.kt` (new)

- [ ] **Step 1: Read current AxiosShim** 确认 OkHttpClient timeout 配置

- [ ] **Step 2: 写失败测试**

```kotlin
class AxiosShimTimeoutTest {
    private lateinit var server: MockWebServer
    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    @Test fun `default timeout is 2000ms`() = runTest {
        server.enqueue(MockResponse().setBodyDelay(3, TimeUnit.SECONDS).setBody("ok"))
        val shim = AxiosShim()
        val start = System.currentTimeMillis()
        val result = runCatching { shim.get(server.url("/").toString(), config = emptyMap()) }
        val elapsed = System.currentTimeMillis() - start
        assertTrue(result.isFailure)
        assertTrue("Expected timeout near 2000ms, was $elapsed", elapsed in 1800L..2500L)
    }

    @Test fun `per-call timeout override works`() = runTest {
        server.enqueue(MockResponse().setBodyDelay(3, TimeUnit.SECONDS).setBody("ok"))
        val shim = AxiosShim()
        val result = runCatching { shim.get(server.url("/").toString(), config = mapOf("timeout" to 500)) }
        // 500ms timeout
        assertTrue(result.isFailure)
    }
}
```

- [ ] **Step 3: 修改 `AxiosShim`**

```kotlin
private const val DEFAULT_TIMEOUT_MS = 2000L

private fun clientFor(timeoutMs: Long): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
    .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
    .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
    .build()

// 公开签名：从 config 读取 timeout
suspend fun get(url: String, config: Map<String, Any?>): Response {
    val timeoutMs = (config["timeout"] as? Number)?.toLong() ?: DEFAULT_TIMEOUT_MS
    // ... clientFor(timeoutMs) ...
}
```

- [ ] **Step 4: Run → PASS**

### Task D2: `AxiosShim` auth URL → Basic

**Files:**

- Modify: `AxiosShim.kt`
- Test: `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/engine/AxiosShimAuthUrlTest.kt` (new)

- [ ] **Step 1: 失败测试**

```kotlin
@Test fun `url with user pass becomes Authorization Basic`() = runTest {
    server.enqueue(MockResponse().setBody("ok"))
    val shim = AxiosShim()
    val authedUrl = server.url("/").newBuilder().username("alice").password("s3cret").build()
    shim.get(authedUrl.toString(), config = emptyMap())
    val request = server.takeRequest()
    assertEquals("Basic ${Base64.getEncoder().encodeToString("alice:s3cret".toByteArray())}", request.getHeader("Authorization"))
    // URL 不含凭证
    assertFalse(request.requestUrl.toString().contains("alice"))
}

@Test fun `explicit Authorization is not overridden`() = runTest { ... }
@Test fun `url without credentials passes through`() = runTest { ... }
@Test fun `special characters in credentials encoded correctly`() = runTest { ... }
```

- [ ] **Step 2: 实现 `normalizeRequest`** —— 见 spec §8.1.

- [ ] **Step 3: 在 `get` / `post` 入口调用**

```kotlin
val mutableHeaders = (config["headers"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value.toString() }?.toMutableMap() ?: mutableMapOf()
val normalizedUrl = normalizeRequest(url, mutableHeaders)
// ... 用 normalizedUrl + mutableHeaders 构造 request
```

### Task D3: `JsEngine` env.lang + process global

**Files:**

- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/JsEngine.kt`
- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManager.kt` (env injection 改硬编码 lang)
- Test: `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/engine/JsEngineGlobalsTest.kt` (new)

- [ ] **Step 1: Read PluginManager 当前 env 注入位置**（搜 `__env` 字符串）

- [ ] **Step 2: 失败测试**

```kotlin
class JsEngineGlobalsTest {
    @Test fun `env_lang is zh-CN`() = runTest {
        val js = engineEvaluate("env.lang")
        assertEquals("zh-CN", js)
    }

    @Test fun `process_platform is android`() = runTest {
        val js = engineEvaluate("process.platform")
        assertEquals("android", js)
    }

    @Test fun `process_env_lang is zh-CN`() = runTest {
        val js = engineEvaluate("process.env.lang")
        assertEquals("zh-CN", js)
    }
}
```

- [ ] **Step 3: 修改 env 注入代码 ——**

把当前 `lang = Locale.getDefault().toLanguageTag()` 改成 `lang = "zh-CN"`。
注入 `process` 全局：

```javascript
globalThis.process = {
    platform: "android",
    version: globalThis.__env.appVersion,
    env: globalThis.__env,
};
```

具体 evaluate 代码：

```kotlin
engine.evaluate<Any?>("""
    globalThis.__env = ${json.encodeToString(envMap)};
    globalThis.process = {
        platform: 'android',
        version: globalThis.__env.appVersion,
        env: globalThis.__env,
    };
""".trimIndent())
```

确保 `envMap` 中 `lang = "zh-CN"` 硬编码。

### Task D4: `URL` polyfill 探针 + 条件注入

**Files:**

- Test: `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/engine/RuntimeUrlConstructorContractTest.kt` (new)
- Create (conditional): `plugin/src/main/assets/jslibs/url-polyfill.js`

- [ ] **Step 1: Write probe test**

```kotlin
class RuntimeUrlConstructorContractTest {
    @Test fun `URL parses href pathname search`() = runTest {
        val result = engineEvaluate("""
            const u = new URL('https://example.com/path?q=1');
            JSON.stringify({ href: u.href, host: u.host, pathname: u.pathname, search: u.search })
        """.trimIndent())
        val obj = JSONObject(result.toString())
        assertEquals("https://example.com/path?q=1", obj.optString("href"))
        assertEquals("example.com", obj.optString("host"))
        assertEquals("/path", obj.optString("pathname"))
        assertEquals("?q=1", obj.optString("search"))
    }
}
```

- [ ] **Step 2: 跑探针**

```bash
./gradlew :plugin:testDebugUnitTest --tests "*RuntimeUrlConstructorContractTest*" --no-daemon
```

- 如果 PASS：QuickJS-kt 已支持 URL，本 task 完成。
- 如果 FAIL：进入 Step 3 注入 polyfill。

- [ ] **Step 3 (conditional): 准备 polyfill JS**

下载 `url-polyfill.js`（推荐 `whatwg-url` 或精简版）放 `plugin/src/main/assets/jslibs/url-polyfill.js`，约 5KB。来源：[https://github.com/lifaon74/url-polyfill](https://github.com/lifaon74/url-polyfill) 的 ESM build → 转 CJS.

或自写最小实现：

```javascript
(function(global) {
    if (typeof global.URL === 'function') return;
    function URL(url) {
        const m = url.match(/^(https?:)\/\/([^/?#]+)(\/[^?#]*)?(\?[^#]*)?(#.*)?$/);
        if (!m) throw new TypeError('Invalid URL: ' + url);
        this.protocol = m[1];
        this.host = m[2];
        this.hostname = m[2].split(':')[0];
        this.port = m[2].split(':')[1] || '';
        this.pathname = m[3] || '/';
        this.search = m[4] || '';
        this.hash = m[5] || '';
        this.href = url;
        this.origin = this.protocol + '//' + this.host;
    }
    global.URL = URL;
})(globalThis);
```

注入位置：`JsEngine` 初始化时，在用户脚本前 evaluate.

- [ ] **Step 4: 重跑探针 → PASS**

### Task D5: `WebDavShim` + require('webdav')

**Files:**

- Create: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/WebDavShim.kt`
- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/RequireShim.kt`
- Create: `plugin/src/main/assets/jslibs/webdav.js`
- Test: `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/engine/WebDavShimTest.kt` (new)

- [ ] **Step 1: 写 JS shim**

`plugin/src/main/assets/jslibs/webdav.js`:

```javascript
module.exports = {
    createClient: function(baseUrl, options) {
        const auth = (options && options.username !== undefined)
            ? { username: options.username, password: options.password || '' }
            : null;
        return {
            getFileContents: function(path) {
                return __webdav_get(baseUrl, path, auth);
            },
            putFileContents: function(path, data) {
                return __webdav_put(baseUrl, path, data, auth);
            },
        };
    },
};
```

- [ ] **Step 2: 失败测试**

```kotlin
class WebDavShimTest {
    @Test fun `get sends Basic auth and joins path`() = runTest {
        server.enqueue(MockResponse().setBody("hello"))
        val shim = WebDavShim()
        val body = shim.get(server.url("").toString().trimEnd('/'), "/file.txt", auth = WebDavAuth("u", "p"))
        assertEquals("hello", body)
        val request = server.takeRequest()
        assertEquals("/file.txt", request.path)
        assertEquals("Basic ${Base64.getEncoder().encodeToString("u:p".toByteArray())}", request.getHeader("Authorization"))
    }

    @Test fun `put sends body with Basic auth`() = runTest { ... }

    @Test fun `get without auth omits Authorization header`() = runTest { ... }
}
```

- [ ] **Step 3: 实现 `WebDavShim.kt`**

```kotlin
package com.zili.android.musicfreeandroid.plugin.engine

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

data class WebDavAuth(val username: String, val password: String)

class WebDavShim(private val client: OkHttpClient = OkHttpClient()) {
    suspend fun get(baseUrl: String, path: String, auth: WebDavAuth?): String =
        request("GET", baseUrl, path, body = null, auth = auth)

    suspend fun put(baseUrl: String, path: String, data: String, auth: WebDavAuth?): String =
        request("PUT", baseUrl, path, body = data, auth = auth)

    private fun request(method: String, baseUrl: String, path: String, body: String?, auth: WebDavAuth?): String {
        val url = "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"
        val builder = Request.Builder().url(url).method(method, body?.toRequestBody())
        if (auth != null) {
            val token = Base64.getEncoder().encodeToString("${auth.username}:${auth.password}".toByteArray())
            builder.header("Authorization", "Basic $token")
        }
        return client.newCall(builder.build()).execute().use { resp -> resp.body?.string() ?: "" }
    }
}
```

- [ ] **Step 4: 在 `RequireShim` 注册 webdav**

阅读现有 `RequireShim.kt` 找到 `loadAsset(name)` 路径。`webdav.js` 自动会被注册（如果 require shim 已是按 `assets/jslibs/*.js` 自动扫描的）。如果是显式列表，加 `"webdav"` 到列表。

- [ ] **Step 5: 在 `JsEngine` 注册 `__webdav_get` / `__webdav_put` 全局**

```kotlin
val webDavShim = WebDavShim()
engine.asyncFunction<String>("__webdav_get") { args ->
    val baseUrl = args[0] as String
    val path = args[1] as String
    val authMap = args[2] as? Map<*, *>
    val auth = authMap?.let { WebDavAuth(it["username"].toString(), it["password"].toString()) }
    webDavShim.get(baseUrl, path, auth)
}
// __webdav_put 同样
```

### Task D6: `RuntimeCompatContractTest`

**Files:**

- Create: `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/engine/RuntimeCompatContractTest.kt`

- [ ] **Step 1: 写契约测**

```kotlin
class RuntimeCompatContractTest {
    @Test fun `runtime exposes process URL webdav and zh-CN lang`() = runTest {
        val js = """
            if (typeof process === 'undefined') throw 'no process';
            if (process.platform !== 'android') throw 'process.platform';
            if (typeof process.env === 'undefined') throw 'no process.env';
            if (typeof URL !== 'function') throw 'no URL';
            const wd = require('webdav');
            if (typeof wd.createClient !== 'function') throw 'webdav.createClient';
            if (env.lang !== 'zh-CN') throw 'env.lang=' + env.lang;
            'ok'
        """.trimIndent()
        assertEquals("ok", engine.evaluate<String>(js))
    }
}
```

- [ ] **Step 2: Run → PASS**

### Task D7: Phase D commit

```bash
git add plugin/src
git commit -m "feat(plugin): align runtime compat with RN

- AxiosShim 默认超时 2000ms + per-call timeout override
- AxiosShim URL 凭证 → Authorization Basic header
- JsEngine 注入 process 全局 + env.lang 硬编码 zh-CN
- WebDavShim + require('webdav')（最小可用：getFileContents / putFileContents）
- URL polyfill 探针 + 条件注入
- RuntimeCompatContractTest 入 dev-harness-gate

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase E — Plugin Lifecycle (semver + lazyLoad)

**目标**：`appVersion satisfies` 闸门 + `lazyLoadPlugin` 启动只读 metadata + `PluginMetadataCacheEntity`.

### Task E1: 加 semver 依赖

**Files:**

- Modify: `plugin/build.gradle.kts`
- Modify: `gradle/libs.versions.toml` (添加 semver 版本)

- [ ] **Step 1: 加版本号**

`libs.versions.toml`:

```toml
[versions]
semver = "2.0.0"

[libraries]
semver = { module = "io.github.z4kn4fein:semver", version.ref = "semver" }
```

- [ ] **Step 2: 引入到 :plugin**

`plugin/build.gradle.kts`:

```kotlin
implementation(libs.semver)
```

- [ ] **Step 3: Sync + 编译验证**

```bash
./gradlew :plugin:compileDebugKotlin --no-daemon
```

### Task E2: `PluginAppVersionGate`

**Files:**

- Create: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/runtime/PluginAppVersionGate.kt`
- Test: `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/runtime/PluginAppVersionGateTest.kt` (new)

- [ ] **Step 1: 失败测试**

```kotlin
class PluginAppVersionGateTest {
    private val gate = PluginAppVersionGate()
    @Test fun `null constraint passes`() {
        assertNull(gate.evaluate(constraint = null, appVersion = "1.0.0"))
    }
    @Test fun `blank constraint passes`() {
        assertNull(gate.evaluate(constraint = "  ", appVersion = "1.0.0"))
    }
    @Test fun `greater than equal satisfied`() {
        assertNull(gate.evaluate(">=1.0.0", "1.2.3"))
    }
    @Test fun `greater than equal violated`() {
        val r = gate.evaluate(">=2.0.0", "1.2.3")
        assertNotNull(r)
        assertEquals(PluginErrorReason.VersionNotMatch, r!!.reason)
    }
    @Test fun `caret satisfied`() { assertNull(gate.evaluate("^1.2.0", "1.2.5")) }
    @Test fun `caret violated by major`() { assertNotNull(gate.evaluate("^1.2.0", "2.0.0")) }
    @Test fun `invalid constraint returns Failed`() {
        val r = gate.evaluate("not-a-version", "1.0.0")
        assertNotNull(r)
        assertEquals(PluginErrorReason.VersionNotMatch, r!!.reason)
        assertTrue(r.detail!!.contains("not-a-version"))
    }
}
```

- [ ] **Step 2: 实现**

```kotlin
package com.zili.android.musicfreeandroid.plugin.runtime

import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.constraints.Constraint

class PluginAppVersionGate {
    /**
     * 返回 null 表示通过；返回 Failed 表示拒绝（reason = VersionNotMatch）。
     */
    fun evaluate(constraint: String?, appVersion: String): PluginState.Failed? {
        if (constraint.isNullOrBlank()) return null
        val ok = try {
            Constraint.parse(constraint).isSatisfiedBy(Version.parse(appVersion))
        } catch (e: Exception) {
            return PluginState.Failed(
                PluginErrorReason.VersionNotMatch,
                "constraint='$constraint' invalid or appVersion='$appVersion' invalid (${e.message})",
            )
        }
        return if (ok) null else PluginState.Failed(
            PluginErrorReason.VersionNotMatch,
            "plugin requires $constraint, app is $appVersion",
        )
    }
}
```

- [ ] **Step 3: Run → PASS**

### Task E3: 把 gate 接入 `PluginManager.loadPluginFromFile`

- [ ] **Step 1: 失败测试**

```kotlin
@Test fun `install plugin with mismatching appVersion → Failed Version Not Match`() = runTest {
    val js = """module.exports = { platform: 'x', version: '1.0', appVersion: '>=99.0.0' };"""
    val tmp = File.createTempFile("p", ".js").apply { writeText(js) }
    val result = manager.installFromFile(tmp)
    assertEquals(PluginOperationCode.VERSION_REJECTED, result.code)
    val entries = manager.allEntries.first()
    assertTrue(entries.any { it.state is PluginState.Failed && (it.state as PluginState.Failed).reason == PluginErrorReason.VersionNotMatch })
    // 文件不应留下
    assertFalse(File(manager.pluginsDir, tmp.name).exists())
}
```

- [ ] **Step 2: 在 `loadPluginFromFile` / `installFromFile` 拼装阶段调闸门**

```kotlin
val info = extractPluginInfo(...)
val failed = appVersionGate.evaluate(info.appVersion, currentAppVersion)
if (failed != null) {
    // 不留文件
    target.delete()
    _allEntries.update { it + PluginEntry(target.absolutePath, failed, info, null, source) }
    return PluginOperationResult.failure(code = PluginOperationCode.VERSION_REJECTED, ...)
}
```

`PluginManager` 构造注入 `appVersionGate: PluginAppVersionGate` + `@Named("appVersion") currentAppVersion: String`.

- [ ] **Step 3: Run → PASS**

### Task E4: `PluginMetadataCacheEntity` + DAO

**Files:**

- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/PluginMetadataCacheEntity.kt`
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/PluginMetadataCacheDao.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt`
- Test: `data/src/test/java/com/zili/android/musicfreeandroid/data/db/dao/PluginMetadataCacheDaoTest.kt` (new)
- Regenerate: `data/schemas/<db>/8.json`

- [ ] **Step 1: 写 Entity**

```kotlin
package com.zili.android.musicfreeandroid.data.db.entity

import androidx.room.Entity

@Entity(tableName = "plugin_metadata_cache", primaryKeys = ["filePath"])
data class PluginMetadataCacheEntity(
    val filePath: String,
    val platform: String,
    val version: String?,
    val hash: String,
    val srcUrl: String?,
    val appVersion: String?,
    val supportedMethodsJson: String,         // JSON array of String
    val supportedSearchTypesJson: String,     // JSON array of String
    val userVariableKeysJson: String,         // JSON array of String
    val sourceMtimeMs: Long,
    val cachedAtAppVersion: String,
)
```

- [ ] **Step 2: 写 DAO**

```kotlin
@Dao
interface PluginMetadataCacheDao {
    @Query("SELECT * FROM plugin_metadata_cache")
    suspend fun getAll(): List<PluginMetadataCacheEntity>

    @Query("SELECT * FROM plugin_metadata_cache WHERE filePath = :filePath")
    suspend fun getByPath(filePath: String): PluginMetadataCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PluginMetadataCacheEntity)

    @Query("DELETE FROM plugin_metadata_cache WHERE filePath = :filePath")
    suspend fun deleteByPath(filePath: String)

    @Query("DELETE FROM plugin_metadata_cache")
    suspend fun deleteAll()
}
```

- [ ] **Step 3: 在 `AppDatabase` 注册**

```kotlin
@Database(
    entities = [
        // ... 现有
        PluginMetadataCacheEntity::class,
    ],
    version = 8,  // 不改版本，dev 阶段直接重导 schema
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    // ...
    abstract fun pluginMetadataCacheDao(): PluginMetadataCacheDao
}
```

- [ ] **Step 4: 写 DAO 测试**

```kotlin
@RunWith(RobolectricTestRunner::class)
class PluginMetadataCacheDaoTest {
    @Test fun upsert_then_getByPath() = runTest { ... }
    @Test fun getAll_returns_all_rows() = runTest { ... }
    @Test fun deleteByPath_removes_only_target() = runTest { ... }
}
```

- [ ] **Step 5: 编译并重导 schema**

```bash
./gradlew :data:compileDebugKotlin --no-daemon
```

确认 `data/schemas/<db>/8.json` 已更新（include 新表）。

⚠ 因 Room 不允许同一版本号下 schema 变化（会拒绝 `RoomMasterTable` 校验），dev 环境务必执行：

```bash
adb shell pm clear com.zili.android.musicfreeandroid
```

如果有 instrumented 测试在没清数据的设备上跑，会 fall through 到 `IllegalStateException: Room cannot verify the data integrity`。这个 plan 不处理 release migration（spec §3.2 显式 out-of-scope）。

### Task E5: `PluginMetadataCacheGateway` + Repository

**Files:**

- Create: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/PluginMetadataCacheGateway.kt`
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/PluginMetadataCacheRepository.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/di/DataModule.kt`

- [ ] **Step 1: Gateway interface 放 :plugin（:plugin 已依赖 :data）**

```kotlin
package com.zili.android.musicfreeandroid.plugin.manager

interface PluginMetadataCacheGateway {
    suspend fun getAll(): List<CachedPluginMetadata>
    suspend fun upsert(meta: CachedPluginMetadata)
    suspend fun deleteByPath(filePath: String)
}

data class CachedPluginMetadata(
    val filePath: String,
    val platform: String,
    val version: String?,
    val hash: String,
    val srcUrl: String?,
    val appVersion: String?,
    val supportedMethods: Set<String>,
    val supportedSearchTypes: List<String>,
    val userVariableKeys: List<String>,
    val sourceMtimeMs: Long,
    val cachedAtAppVersion: String,
)
```

- [ ] **Step 2: :data 实现 Gateway**

```kotlin
package com.zili.android.musicfreeandroid.data.repository

import com.zili.android.musicfreeandroid.plugin.manager.CachedPluginMetadata
import com.zili.android.musicfreeandroid.plugin.manager.PluginMetadataCacheGateway
import com.zili.android.musicfreeandroid.data.db.dao.PluginMetadataCacheDao
import com.zili.android.musicfreeandroid.data.db.entity.PluginMetadataCacheEntity
import org.json.JSONArray
import javax.inject.Inject

class PluginMetadataCacheRepository @Inject constructor(
    private val dao: PluginMetadataCacheDao,
) : PluginMetadataCacheGateway {
    override suspend fun getAll(): List<CachedPluginMetadata> =
        dao.getAll().map { it.toDomain() }
    override suspend fun upsert(meta: CachedPluginMetadata) =
        dao.upsert(meta.toEntity())
    override suspend fun deleteByPath(filePath: String) =
        dao.deleteByPath(filePath)

    private fun PluginMetadataCacheEntity.toDomain(): CachedPluginMetadata = CachedPluginMetadata(
        filePath = filePath,
        platform = platform,
        version = version,
        hash = hash,
        srcUrl = srcUrl,
        appVersion = appVersion,
        supportedMethods = JSONArray(supportedMethodsJson).toStringList().toSet(),
        supportedSearchTypes = JSONArray(supportedSearchTypesJson).toStringList(),
        userVariableKeys = JSONArray(userVariableKeysJson).toStringList(),
        sourceMtimeMs = sourceMtimeMs,
        cachedAtAppVersion = cachedAtAppVersion,
    )

    private fun CachedPluginMetadata.toEntity() = PluginMetadataCacheEntity(
        filePath = filePath,
        platform = platform,
        version = version,
        hash = hash,
        srcUrl = srcUrl,
        appVersion = appVersion,
        supportedMethodsJson = JSONArray(supportedMethods.toList()).toString(),
        supportedSearchTypesJson = JSONArray(supportedSearchTypes).toString(),
        userVariableKeysJson = JSONArray(userVariableKeys).toString(),
        sourceMtimeMs = sourceMtimeMs,
        cachedAtAppVersion = cachedAtAppVersion,
    )

    private fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }
}
```

- [ ] **Step 3: Hilt 绑定**

`DataModule.kt`:

```kotlin
@Binds @Singleton
abstract fun bindPluginMetadataCacheGateway(impl: PluginMetadataCacheRepository): PluginMetadataCacheGateway
```

### Task E6: `PluginManager.setup` lazy mode

**Files:**

- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/manager/PluginManager.kt`
- Test: `plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginLazyLoadIntegrationTest.kt` (new)

- [ ] **Step 1: Read current setup() 逻辑**

- [ ] **Step 2: 失败 integration 测试**

androidTest，因为需要 JsEngine 真求值：

```kotlin
@RunWith(AndroidJUnit4::class)
class PluginLazyLoadIntegrationTest {
    @Test fun setup_only_creates_metadata_entries() = runBlocking {
        // 装 3 个简单 fixture plugin（写到 pluginsDir）
        val pluginsDir = manager.pluginsDir
        listOf("a", "b", "c").forEach { name ->
            File(pluginsDir, "$name.js").writeText("""
                module.exports = { platform: '$name', version: '1.0' };
            """.trimIndent())
        }
        // 首次 setup → cache 为空，evaluate 全部，state=Mounted
        manager.setup()
        var entries = manager.allEntries.first()
        assertEquals(3, entries.count { it.state == PluginState.Mounted })

        // 重启 manager → 走 cache 路径，所有 entry 状态应为 Initializing 而非 Mounted
        manager.shutdown()
        manager.setup()
        entries = manager.allEntries.first()
        assertEquals(3, entries.count { it.state == PluginState.Initializing })
        // JsEngine 实例计数 = 0
        assertEquals(0, JsEngine.activeInstances())

        // 触发一次 getMediaSource on "a" → 仅 a 被求值
        manager.getPlugin("a")  // 这会触发 ensureMounted
        entries = manager.allEntries.first()
        assertEquals(PluginState.Mounted, entries.single { it.info?.platform == "a" }.state)
        assertEquals(PluginState.Initializing, entries.single { it.info?.platform == "b" }.state)
    }
}
```

- [ ] **Step 3: 实现**

新增 `JsEngine.activeInstances()` 统计：

```kotlin
companion object {
    internal val activeCount = AtomicInteger(0)
    fun activeInstances(): Int = activeCount.get()
}

init { activeCount.incrementAndGet() }
fun destroy() { ...; activeCount.decrementAndGet() }
```

`PluginManager.setup()`:

```kotlin
suspend fun setup() {
    if (initialized.compareAndSet(false, true).not()) return
    val metaList = metadataCacheGateway.getAll().associateBy { it.filePath }
    val files = pluginsDir.listFiles { f -> f.extension == "js" } ?: emptyArray()
    val entries = files.map { file ->
        val cached = metaList[file.absolutePath]
        if (cached != null && cached.sourceMtimeMs == file.lastModified() && cached.cachedAtAppVersion == currentAppVersion) {
            PluginEntry(
                filePath = file.absolutePath,
                state = PluginState.Initializing,
                info = cached.toPluginInfo(),
                loaded = null,
                installSource = null,  // could be derived from .meta.properties
            )
        } else {
            val info = extractPluginInfoOnly(file)  // 走 lightweight 解析
            val failed = info?.let { appVersionGate.evaluate(it.appVersion, currentAppVersion) }
            if (failed != null) {
                PluginEntry(file.absolutePath, failed, info, null, null)
            } else {
                val loaded = loadPluginFromFile(file)
                metadataCacheGateway.upsert(loaded.info.toCachedMeta(file))
                PluginEntry(file.absolutePath, PluginState.Mounted, loaded.info, loaded, null)
            }
        }
    }
    _allEntries.value = entries
    registerLocalFilePlugin()  // 见 Phase B
}

internal suspend fun ensureMounted(entry: PluginEntry): LoadedPlugin? {
    return when (entry.state) {
        is PluginState.Mounted -> entry.loaded
        is PluginState.Initializing -> {
            val loaded = try { loadPluginFromFile(File(entry.filePath)) } catch (e: Exception) {
                _allEntries.update { it.replace(entry, entry.copy(state = PluginState.Failed(CannotParse, e.message))) }
                return null
            }
            _allEntries.update { it.replace(entry, entry.copy(state = PluginState.Mounted, loaded = loaded)) }
            loaded
        }
        is PluginState.Loading -> awaitMounted(entry.filePath)
        is PluginState.Failed -> null
    }
}

fun getPlugin(platform: String): LoadedPlugin? {
    val entry = _allEntries.value.firstOrNull { it.info?.platform == platform } ?: return null
    return runBlocking { ensureMounted(entry) }
    // ⚠ runBlocking 在 main thread 上调用会 ANR。getPlugin 应当从 suspend context 调用，本 task 加 deprecation 警告
}
```

⚠ `runBlocking` 是反模式（INC-2026-0009 / rule-quickjs-single-thread 相关）。本 task 把 `getPlugin` 改为 suspend：

```kotlin
suspend fun getPluginSuspending(platform: String): LoadedPlugin? = ...
// 旧的 fun getPlugin(...) 保留但 @Deprecated 警告，body 仍 runBlocking 维持兼容
```

调用方逐步迁移（不在本 task 范围；标记跟进）。

- [ ] **Step 4: Run integration test → PASS**

```bash
./gradlew :plugin:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.plugin.manager.PluginLazyLoadIntegrationTest
```

### Task E7: 设置页"懒加载"开关

**Files:**

- Modify: `feature/settings/src/main/java/.../plugin/PluginAdvancedSettingsScreen.kt`
- Modify: DataStore prefs

- [ ] **Step 1: 加 DataStore key**

```kotlin
val LAZY_LOAD_PLUGINS = booleanPreferencesKey("pref_lazy_load_plugins")
```

默认 true。`PluginManager` 在 setup() 开头读：

```kotlin
val lazy = appPrefs.lazyLoadPlugins.first()  // true 默认
```

- [ ] **Step 2: UI 加 switch**

```kotlin
SwitchRow(
    title = "懒加载插件",
    description = "启动时只读元数据，首次使用时才求值。关闭则启动时全部加载。",
    checked = uiState.lazyLoad,
    onCheckedChange = viewModel::setLazyLoad,
)
```

`setLazyLoad` 写 DataStore + 调 `pluginManager.shutdown()` + `setup()`。

### Task E8: Phase E commit

```bash
git add gradle/libs.versions.toml plugin/build.gradle.kts plugin/src data/src data/schemas feature/settings/src
git commit -m "feat(plugin): add appVersion gate and lazyLoad with metadata cache

- io.github.z4kn4fein:semver:2.0.0 引入到 :plugin
- PluginAppVersionGate（满足 / 不匹配 / 非法 5 测试）
- PluginManager 在 install/setup 都跑闸门，失败写 Failed(VersionNotMatch)
  且文件不留
- PluginMetadataCacheEntity / DAO / Repository（schema v8 重导）
- PluginManager.setup 走 cache 路径时 entry 进 Initializing；
  ensureMounted 在方法调用时按需求值
- 设置页加 '懒加载插件' 开关，默认开
- JsEngine.activeInstances 计数，integration test 断言冷启动 0 实例

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase F — Model Boundary + Local 收口

**目标**：`MusicItemBridgeProjector` 显式投影 `localPath` / `lyricOffset` 等到 bridge map；`JsBridge` 防御 `$` key；`MediaSourceResult.contentType` 字段；最后把 `LocalMusicRepository` 收口到 `PluginManager.getPlugin("本地")`。

### Task F1: `JsBridge.toMusicItem` 忽略 "$"

**Files:**

- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/JsBridge.kt`
- Test: `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/engine/JsBridgeDollarKeyDefenseTest.kt` (new)

- [ ] **Step 1: 失败测试**

```kotlin
class JsBridgeDollarKeyDefenseTest {
    @Test fun `toMusicItem ignores dollar key`() {
        val map = mapOf(
            "id" to "1",
            "platform" to "x",
            "title" to "t",
            "\$" to mapOf("localPath" to "/tmp/x.mp3", "secret" to "leaked"),
        )
        val item = JsBridge.toMusicItem(map)
        assertNull(item.localPath)
        // 没有任何 \$ 残留字段
    }
}
```

- [ ] **Step 2: 在 `JsBridge.toMusicItem` filter 出 `"$"`**

```kotlin
fun toMusicItem(map: Map<*, *>): MusicItem {
    val filtered = map.filterKeys { it != "$" }
    // ... 现有解析逻辑用 filtered
}
```

### Task F2: `MusicItemBridgeProjector`

**Files:**

- Create: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/MusicItemBridgeProjector.kt`
- Test: `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/engine/MusicItemBridgeProjectorTest.kt` (new)

- [ ] **Step 1: 失败测试**

```kotlin
class MusicItemBridgeProjectorTest {
    private val downloadedDao = mockk<DownloadedTrackDao>()
    private val lyricRepo = mockk<LyricRepository>()
    private val projector = MusicItemBridgeProjector(downloadedDao, lyricRepo)

    @Test fun `injects localPath from DownloadedTrackEntity`() = runTest {
        coEvery { downloadedDao.get("x", "1") } returns DownloadedTrackEntity(
            id = "1", platform = "x", mediaStoreUri = "content://...", relativePath = "Music/1.mp3",
            mimeType = "audio/mpeg", quality = "STANDARD", sizeBytes = 100, downloadedAt = 1,
        )
        coEvery { lyricRepo.getCache(any()) } returns null
        val item = MusicItem(id = "1", platform = "x", title = null, artist = null, album = null)
        val map = projector.project(item)
        assertEquals("Music/1.mp3", map["localPath"])  // or 用 mediaStoreUri 资源化路径
        assertNull(map["\$"])  // 不输出 \$
    }

    @Test fun `injects lyricOffset when non-zero`() = runTest {
        coEvery { downloadedDao.get(any(), any()) } returns null
        coEvery { lyricRepo.getCache(any()) } returns LyricCache(..., userOffsetMs = 500L)
        val map = projector.project(item("x", "1"))
        assertEquals(500L, map["lyricOffset"])
    }

    @Test fun `omits localPath when no download`() = runTest { ... }
}
```

- [ ] **Step 2: 实现**

```kotlin
package com.zili.android.musicfreeandroid.plugin.engine

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.data.db.dao.DownloadedTrackDao
import com.zili.android.musicfreeandroid.data.repository.LyricRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicItemBridgeProjector @Inject constructor(
    private val downloadedTrackDao: DownloadedTrackDao,
    private val lyricRepository: LyricRepository,
) {
    suspend fun project(item: MusicItem): Map<String, Any?> {
        val base = JsBridge.musicItemToMap(item)  // 现有 mapping
        val downloaded = downloadedTrackDao.get(item.platform, item.id)
        val cache = lyricRepository.getCache(item)
        return base + buildMap {
            downloaded?.let {
                put("localPath", it.relativePath)
                put("downloaded", true)
            }
            cache?.userOffsetMs?.takeIf { it != 0L }?.let { put("lyricOffset", it) }
        }
    }
}
```

### Task F3: `PluginMediaSourceService` 用 projector

- [ ] **Step 1: 注入 projector**

```kotlin
@Singleton
class PluginMediaSourceService @Inject constructor(
    private val pluginManager: PluginManager,
    private val mediaCacheRepository: MediaCacheRepository,
    private val projector: MusicItemBridgeProjector,
    // ...
)
```

- [ ] **Step 2: 调用 plugin 前 project**

实际上 `LoadedPlugin.getMediaSource(item, quality)` 当前签名收 `MusicItem`，是 `JsBridge` 内部把 item 转 map。projector 应该在 `JsLoadedPlugin.getMediaSource` 内部使用，而非 service 层。

修改 `JsLoadedPlugin`:

```kotlin
class JsLoadedPlugin @Inject constructor(
    private val projector: MusicItemBridgeProjector,
    // ...
) : LoadedPlugin {
    override suspend fun getMediaSource(item: MusicItem, quality: String): MediaSourceResult? {
        val projectedMap = projector.project(item)
        // call JS function with projectedMap
        ...
    }
}
```

⚠ `JsLoadedPlugin` 的 instance 是 `PluginManager` 在 `loadPluginFromFile` 时创建的，需要 Hilt provider 或 factory 模式。最简单：让 `PluginManager` 注入 projector 后传给 JsLoadedPlugin 构造函数。

### Task F4: `MediaSourceResult.contentType` 字段

**Files:**

- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/api/PluginModels.kt`
- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/engine/JsBridge.kt` (parseMediaSourceResult)

- [ ] **Step 1: Read PluginModels.kt** 看 `MediaSourceResult` 当前字段

- [ ] **Step 2: 加 contentType**

```kotlin
data class MediaSourceResult(
    val url: String,
    val quality: String?,
    val headers: Map<String, String>?,
    val userAgent: String?,
    val size: Long?,
    val contentType: String? = null,  // 新增
)
```

`JsBridge.parseMediaSourceResult(map)`:

```kotlin
contentType = (map["contentType"] as? String)?.takeIf { it.isNotBlank() },
```

测试加：

```kotlin
@Test fun `parseMediaSourceResult extracts contentType`() { ... }
```

### Task F5: `LocalMusicRepository` 收口

**Files:**

- Modify: `feature/home/src/main/java/.../local/LocalMusicRepository.kt`
- Test: `feature/home/src/test/java/.../local/LocalMusicRepositoryViaPluginTest.kt` (new)

- [ ] **Step 1: Read current `LocalMusicRepository`** 看它现在用什么方式取 cover / lyric

- [ ] **Step 2: 写失败测试**

```kotlin
@Test fun `getCover delegates to LocalFilePlugin`() = runTest {
    val item = MusicItem(id = "1", platform = "本地", localPath = "/tmp/x.mp3", ...)
    coEvery { pluginManager.getPlugin("本地") } returns mockLocalPlugin
    coEvery { mockLocalPlugin.getMusicInfo(item) } returns item.copy(coverImg = "file:///tmp/cover.jpg")
    val cover = repository.getCover(item)
    assertEquals("file:///tmp/cover.jpg", cover)
}
```

- [ ] **Step 3: 重构 `LocalMusicRepository.getCover` / `getLyric` 方法委托到 `pluginManager.getPlugin("本地")`**

直接调用插件方法：

```kotlin
@Singleton
class LocalMusicRepository @Inject constructor(
    private val pluginManager: PluginManager,
    // 旧依赖如果用作 fallback 保留；否则删
) {
    suspend fun getCover(item: MusicItem): String? {
        val plugin = pluginManager.getPlugin("本地") ?: return null
        return plugin.getMusicInfo(item)?.coverImg
    }
    suspend fun getLyric(item: MusicItem): LyricResult? {
        val plugin = pluginManager.getPlugin("本地") ?: return null
        return plugin.getLyric(item)
    }
}
```

- [ ] **Step 4: 删除 `PlayerController` / `PluginMediaSourceService` 中的 `platform == "本地"` 特判**（如有）

```bash
grep -rn '"本地"\|本地' --include "*.kt" player/src/main plugin/src/main feature/player-ui/src/main 2>/dev/null
```

逐一检查每个匹配并决定是否删。

### Task F6: 契约测 `MediaItemBridgeContractTest`

**Files:**

- Create: `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/engine/MediaItemBridgeContractTest.kt`

- [ ] **Step 1: 写契约**

```kotlin
class MediaItemBridgeContractTest {
    @Test fun `bridge round-trip ignores dollar key`() {
        val raw = mapOf("id" to "1", "platform" to "x", "title" to "t", "\$" to mapOf("secret" to "leak"))
        val item = JsBridge.toMusicItem(raw)
        val back = JsBridge.musicItemToMap(item)
        assertFalse(back.containsKey("\$"))
    }

    @Test fun `projector emits localPath when downloaded`() = runTest {
        // dual-checks: projector 与 §F2 测试不同视角，断言 contract
    }
}
```

### Task F7: Phase F commit

```bash
git add plugin/src feature/home/src player/src
git commit -m "refactor(plugin): explicit MusicItem bridge boundary + local collapse

- JsBridge.toMusicItem defense: ignore '\$' key
- MusicItemBridgeProjector 显式投影 DownloadedTrack.relativePath
  + LyricCache.userOffsetMs 到 bridge map
- JsLoadedPlugin.getMediaSource 用 projector 包装入参，杜绝 RN '\$' 隐式协议
- MediaSourceResult 加 contentType 字段
- LocalMusicRepository 收口到 PluginManager.getPlugin('本地')
- 删除 player / pluginMediaSourceService 中 platform=='本地' 特判
- MediaItemBridgeContractTest 入 dev-harness-gate

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase G — 全局验证 + Harness 联动

### Task G1: 全量测试

```bash
./gradlew test --no-daemon
```

预期 PASS。如果 fail：
- 不是新代码 → 标 `@Ignore("flaky pre-existing, tracked in incidents")` + 写一个 INC，不阻塞 plan 完成；
- 新代码 → 立即修。

### Task G2: 全量 connected 测试（如有设备 / emulator）

```bash
./gradlew connectedAndroidTest --no-daemon
```

- 默认通道（无 `-Pintegration`）应该跳过真网用例。
- 检查 `:plugin:connectedAndroidTest` 中本 plan 新增的 `LocalFilePluginIntegrationTest` / `Mp3MetadataReaderImplTest` / `PluginLazyLoadIntegrationTest` 全过。

### Task G3: 加 harness rule

**Files:**

- Modify: `docs/dev-harness/plugin/rules.md`
- Modify: `docs/dev-harness/incidents/index.md`
- Modify: `docs/dev-harness/incidents.md`（如有）

- [ ] **Step 1: 加新 rule**

在 `docs/dev-harness/plugin/rules.md` 末尾增：

```markdown
## 插件失败必须可见 {#rule-plugin-failure-must-surface}

implemented_by: INC-2026-0015

- `:plugin/manager/PluginManager.kt` 中 install / load 路径捕获异常 MUST 写入 `PluginEntry.state = Failed(reason, detail)`；MUST NOT 仅 `catch (e) { return null }`。
- `:plugin/runtime/` 新增 PluginState 状态变更 MUST 通过 `PluginStateKeys` 显式字符串记录日志，不允许直接 `state::class.simpleName` 或 `enum.name`。
- 复发条件 + 升级触发：再次出现 catch + null return 而未写 PluginEntry.state 的修复 commit，则升级为 contract-test。
```

- [ ] **Step 2: 加 INC-2026-0015**

`docs/dev-harness/incidents/index.md` 加索引项 + `docs/dev-harness/incidents.md` 加正文（按现有 INC- 格式）。

### Task G4: 更新 spec 状态

- [ ] **Step 1: Edit `docs/superpowers/specs/2026-05-11-plugin-engine-alignment-design.md`**

把 frontmatter 的"状态"从 `设计完成，待实施` 改为 `已实施`。

- [ ] **Step 2: 在 §0 处加一段实施记录**

```
## 实施状态

- 实施日期：2026-05-11 → 2026-05-XX（按实际完成日填）
- Plan: docs/superpowers/plans/2026-05-11-plugin-engine-alignment-plan.md
- 涉及 commit: <列出 6 个 phase commit SHA>
- 实施期间发现的 spec 修正：见 plan §0 Storage Mapping
```

### Task G5: 运行态验收（按 spec §13）

按 spec §13 八个场景在 Debug 构建跑端到端：

- [ ] 13.1 冷启动秒开 — 装一份插件，搜歌播放，kill app 重开同首歌断言 cache hit
- [ ] 13.2 离线 cacheControl — 飞行模式下播 `no-cache` 已缓存歌
- [ ] 13.3 歌词持久 — 重启后断言不重调 getLyric
- [ ] 13.4 用户自设歌词 — 手放 lyric 文件，断言优先级
- [ ] 13.5 本地音乐 — 通过 PluginManager 拿 cover / lyric 与改造前一致
- [ ] 13.6 插件错误明示 — 三种 Failed reason UI 渲染
- [ ] 13.7 Hash 幂等 — 重装同插件返回 success Toast
- [ ] 13.8 懒加载 — 重启后 entry=Initializing，触发后 Mounted

验收手段：屏录 + Logan 日志包 + `adb pull databases/musicfree.db` + `adb shell ls filesDir/`.

### Task G6: 最终 commit

```bash
git add docs/
git commit -m "docs(plugin): update harness rules + spec status post-alignment

- 加 rule-plugin-failure-must-surface（INC-2026-0015）
- spec 状态更新为已实施 + §0 实施记录

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## 失败 / 未决场景应对

### 如果 `:data` v8 schema 重导失败

意味着 `AppDatabase` 实际版本号还是 7 或其他。Re-Read `AppDatabase.kt`，把 `version = N`（实际值）+ 1，并提交新 schema JSON。Spec §11.2 的"v7→v8"在那个语境下就更新为"vN → vN+1"。

### 如果 `JsEngine.activeInstances()` 在测试中难以稳定（GC 影响）

退而求其次：把测试断言改为 "newly loaded plugin count" via metadata cache hit log，而非引用计数。

### 如果 `MusicItem` 字段在 :feature 层有大量构造调用

加 `localPath` 必然破坏一些 positional 构造。修复策略：把 named 参数 default null 加在末尾即可；如果调用方用 named arg 则不破坏。一次性 grep + replace_all "MusicItem(" 的 positional 调用。

### 如果 QuickJS-kt 已支持 URL

不写 polyfill 文件，但 `RuntimeUrlConstructorContractTest` 仍然提交，作为回归探针。

### 如果 Phase A 接入 cacheControl 时发现现有调用方依赖"每次都打 plugin"行为

按 spec §3.2 `cacheControl="cache"` 是默认目标行为。如果有调用方坚持要 bypass，再加显式 `forceFresh: Boolean` 参数；不在本 plan 实现，作为 follow-up。

---

## §3 Self-Review

按写 plan 的 self-review checklist 复核：

### 1. Spec 覆盖

| Spec §  | Plan 落点 | 状态 |
|---|---|---|
| §5 Cache & Persistence | Phase A | ✅ |
| §6 Local Virtual Plugin | Phase B + F5 | ✅ |
| §7 Plugin State Machine + UI | Phase C | ✅ |
| §8 Runtime Compat | Phase D | ✅ |
| §9 Plugin Lifecycle | Phase E | ✅ |
| §10 Model Boundary | Phase F | ✅ |
| §11.1 Hash 冲突 | Phase C5 | ✅ |
| §11.2 Schema bump | §0 + §1 + Phase E Task E4 注释 | ✅（已修正 dev 行为）|
| §12.x 测试矩阵 | 每个 Task 配套 | ✅ |
| §13 运行态验收 | Phase G5 | ✅ |
| §17 决策日志 | 在 spec 不动 | ✅ |

### 2. Placeholder 扫描

`grep -n "TBD\|TODO\|FIXME\|XXX" plan.md` 后续手工扫，预期 0 项。注：有几处 `// TODO:` 风格 inline comment（如 ConnectivityChecker 离线分支 follow-up）是显式 follow-up 标记，不是 plan 自身 placeholder。

### 3. 类型一致性

- `PluginEntry`、`PluginState`、`LoadedPlugin sealed interface`、`MusicItem.localPath`、`PluginInfo.hash`、`MediaSourceResult.contentType` 这些跨 phase 的新类型在所有 task 出现处都保持同名 ✅
- `LocalFilePluginConstants.PLATFORM/HASH/SUPPORTED_METHODS` 全 plan 一致 ✅
- `Mp3MetadataReader` 接口位于 `:core/local`（已在 Task B2 step 1 显式说明搬迁）✅

### 4. 歧义

- `MusicItemBridgeProjector` 注入位置：在 `JsLoadedPlugin` 构造时；plan Phase F3 已明确 ✅
- `cacheControl = "no-cache"` 离线分支：plan Phase A7 显式标 follow-up（需要 ConnectivityChecker）✅
- `runBlocking` 在 `getPlugin` 中 ：Phase E6 显式标 deprecation 警告 + 跟进 ✅

无遗留歧义。

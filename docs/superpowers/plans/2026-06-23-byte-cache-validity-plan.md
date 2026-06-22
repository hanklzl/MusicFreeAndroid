# 在线歌曲字节缓存有效性实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to execute this plan.

> 文档状态：历史记录（implementation plan 快照）
> 适用范围：落实 [在线歌曲字节缓存有效性设计](../specs/2026-06-23-byte-cache-validity-design.md)
> 直接执行：是（本计划对应一次实现任务；执行前仍需重新读取当前 dev-harness rules）
> 最后校验：2026-06-23
> 关联 dev-harness：[player/rules](../../dev-harness/player/rules.md)、[plugin/rules](../../dev-harness/plugin/rules.md)、[test/rules](../../dev-harness/test/rules.md)、[cache-and-logs](../../dev-harness/player/cache-and-logs.md)

**Goal:** 让完整播放过的在线歌曲具备可验证、可持久化、可兜底复用的字节缓存状态。`no-cache` 仍保持在线 fresh resolve 语义，但当本地存在 `PlayableVerified` 字节缓存时，播放启动不再被插件解析超时阻塞。

**Architecture:** 在 `:core` 定义字节缓存状态 contract；`:data` 用 Room 持久化状态；`:player` 用 Media3 `SimpleCache` span 检查完整性并在播放成功/失败时更新状态；`:plugin` 保持常规 `cacheControl` 语义，只增加“verified byte-cache 专用读取历史 source”的窄入口。

**Tech Stack:** Kotlin, Coroutines, Hilt, Room, Media3 SimpleCache, Logan/MfLog, JUnit/Robolectric.

---

## Phase 0：前置确认

### 任务

1. 重新读取当前规则：
   - `docs/dev-harness/player/rules.md`
   - `docs/dev-harness/plugin/rules.md`
   - `docs/dev-harness/test/rules.md`
   - `docs/dev-harness/player/cache-and-logs.md`
2. 确认当前数据库版本与迁移链：
   - `data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt`
   - `data/src/main/java/com/hank/musicfree/data/di/DataModule.kt`
   - `data/src/main/java/com/hank/musicfree/data/db/migration/`
3. 确认当前缓存 key 与 stale 驱逐入口：
   - `player/src/main/java/com/hank/musicfree/player/source/PlaybackCacheKeyRegistrar.kt`
   - `player/src/main/java/com/hank/musicfree/player/source/HeaderInjectingDataSourceFactory.kt`
   - `player/src/main/java/com/hank/musicfree/player/cache/SimpleCacheHolder.kt`
   - `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`
4. 复核 RN 原版缓存有效性口径：
   - `../../../../MusicFree/src/core/mediaCache.ts`
   - `../../../../MusicFree/src/core/pluginManager/plugin.ts`
   - `../../../../MusicFree/src/entry/bootstrap/bootstrap.ts`
   - `../../../../MusicFree/src/utils/fileUtils.ts`

### 验收

- 不改代码。
- 输出当前版本号、迁移链末端、SimpleCache key 生成函数位置。
- 输出 RN 原版结论：source 元数据缓存只做存在性 + policy gate；本地文件只做 `exists()`；TrackPlayer 字节缓存只由底层播放器目录和 `maxCacheSize` 管理，没有业务层 partial/complete/hash 检测。

## Phase 1：Core contract

### 改动文件

- 新增 `core/src/main/java/com/hank/musicfree/core/cache/ByteCacheModels.kt`
- 新增 `core/src/test/java/com/hank/musicfree/core/cache/ByteCacheModelsTest.kt`

### 实现要点

定义稳定模型：

```kotlin
data class ByteCacheKey(
    val platform: String,
    val musicId: String,
    val quality: PlayQuality,
) {
    val stableKey: String = "${platform}:${musicId}:${quality.name.lowercase()}"
}

enum class ByteCacheValidity {
    None,
    Partial,
    Complete,
    PlayableVerified,
    StaleOrInvalid,
}

enum class ByteCacheValidationMethod {
    SpanInspection,
    PlaybackCompleted,
    ManualEvict,
    StaleFailure,
}

interface ByteCacheStatusStore {
    suspend fun get(key: ByteCacheKey): ByteCacheStatus?
    suspend fun upsert(status: ByteCacheStatus)
    suspend fun markInvalid(
        key: ByteCacheKey,
        reason: ByteCacheInvalidReason,
        updatedAt: Long,
    )
    suspend fun delete(key: ByteCacheKey)
    suspend fun deleteBySong(platform: String, musicId: String)
}
```

测试覆盖：

- `ByteCacheKey.stableKey` 与 `HeaderInjectingDataSourceFactory.cacheKeyFor` 口径一致。
- `PlayableVerified` 必须携带 `contentLength > 0`。
- `StaleOrInvalid` 必须携带 `invalidReason`。

## Phase 2：Data persistence

### 改动文件

- 新增 `data/src/main/java/com/hank/musicfree/data/db/entity/ByteCacheStatusEntity.kt`
- 新增 `data/src/main/java/com/hank/musicfree/data/db/dao/ByteCacheStatusDao.kt`
- 新增 `data/src/main/java/com/hank/musicfree/data/repository/RoomByteCacheStatusStore.kt`
- 新增 `data/src/main/java/com/hank/musicfree/data/db/migration/Migration14To15.kt`
- 修改 `data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt`
- 修改 `data/src/main/java/com/hank/musicfree/data/di/DataModule.kt`
- 新增 `data/src/androidTest/java/com/hank/musicfree/data/db/AppDatabaseMigration14To15Test.kt`
- 生成 `data/schemas/com.hank.musicfree.data.db.AppDatabase/15.json`

### 表结构

```sql
CREATE TABLE IF NOT EXISTS byte_cache_status (
  platform TEXT NOT NULL,
  music_id TEXT NOT NULL,
  quality TEXT NOT NULL,
  status TEXT NOT NULL,
  cached_bytes INTEGER NOT NULL DEFAULT 0,
  content_length INTEGER,
  validation_method TEXT NOT NULL,
  source_fingerprint TEXT,
  invalid_reason TEXT,
  verified_at INTEGER,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY(platform, music_id, quality)
);

CREATE INDEX IF NOT EXISTS index_byte_cache_status_updated_at
ON byte_cache_status(updated_at);
```

### 实现要点

- `AppDatabase.version` 从 14 升到 15。
- `DataModule.addMigrations(...)` 增加 `MIGRATION_14_15`。
- `RoomByteCacheStatusStore` 实现 `ByteCacheStatusStore`，只暴露 core 模型，不向 `:player` 泄漏 Room entity。
- `deleteBySong(platform, musicId)` 用于手动清理单曲缓存时同步删除所有音质状态。
- 写入状态时记录 `byte_cache_status_write`。

### 验收

- 迁移测试覆盖 14 -> 15。
- DAO 测试覆盖 upsert、markInvalid、deleteBySong。

## Phase 3：SimpleCache inspector

### 改动文件

- 新增 `player/src/main/java/com/hank/musicfree/player/cache/ByteCacheInspector.kt`
- 新增 `player/src/test/java/com/hank/musicfree/player/cache/ByteCacheInspectorTest.kt`
- 视需要修改 `player/src/main/java/com/hank/musicfree/player/cache/SimpleCacheHolder.kt`

### 实现要点

`ByteCacheInspector.inspect(key)` 返回：

```kotlin
data class ByteCacheInspection(
    val key: ByteCacheKey,
    val validity: ByteCacheValidity,
    val cachedBytes: Long,
    val contentLength: Long?,
    val holeCount: Int,
)
```

span 判断规则：

1. 没有 spans -> `None`。
2. spans 不从 0 开始或中间有洞 -> `Partial`。
3. 无可信 `contentLength` -> `Partial`。
4. 连续覆盖长度 `>= contentLength` -> `Complete`。
5. 其他 -> `Partial`。

日志：

- 每次播放启动前、播放结束验证前和 fallback 前可记录 `byte_cache_inspect`。
- 字段必须包含 `status`, `cachedBytes`, `contentLength`, `holeCount`。

测试覆盖：

- 空 span -> `None`。
- 只预取头部 -> `Partial`。
- span 有洞 -> `Partial`。
- 完整覆盖 content length -> `Complete`。
- content length 未知 -> `Partial`。

## Phase 4：播放完成后写入 PlayableVerified

### 改动文件

- 修改 `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`
- 修改或新增 `player/src/test/java/com/hank/musicfree/player/controller/PlayerControllerByteCacheValidityTest.kt`
- 视需要修改 `player/src/main/java/com/hank/musicfree/player/cache/CacheDataSourceEventBridge.kt`

### 实现要点

在播放自然结束时：

1. 获取当前播放 item、quality、sid、cachePolicy。
2. `cachePolicy == NoStore` 时直接跳过。
3. 调用 `ByteCacheInspector.inspect(key)`。
4. 若结果为 `Complete` 且本 session 没有 fatal `media3_datasource_error`，写 `PlayableVerified`。
5. 否则写或保留 `Partial`，不升级。

日志：

- 成功升级：`byte_cache_verified`。
- 未升级：`byte_cache_fast_path_rejected{reason=partial|error|no_content_length|no_store}`。

测试覆盖：

- 完整播放结束 -> 写 `PlayableVerified`。
- 中途切歌 -> 不写 `PlayableVerified`。
- `no-store` -> 不写状态。
- DataSource error 后 ended 不得升级。

## Phase 5：Verified byte-cache 快路径与 fallback

### 改动文件

- 修改 `plugin/src/main/java/com/hank/musicfree/plugin/media/PluginMediaSourceService.kt`
- 修改对应 resolver contract（按当前代码位置选择最小入口）
- 修改 `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`
- 新增或修改：
  - `plugin/src/test/java/com/hank/musicfree/plugin/media/PluginMediaSourceServiceCacheTest.kt`
  - `player/src/test/java/com/hank/musicfree/player/controller/PlayerControllerByteCacheValidityTest.kt`

### 实现要点

新增“verified byte-cache 专用读取历史 source”入口：

```kotlin
suspend fun resolveCachedSourceForVerifiedByteCache(
    item: MusicItem,
    quality: PlayQuality,
    sid: String,
): MediaSourceResolution?
```

约束：

- 只读 Room source，不调用插件。
- 不改变常规 `no-cache` 在线策略。
- 调用方必须先证明 `PlayableVerified + Complete`，该方法内部也记录调用原因，避免被误用。
- 若找不到对应 quality source，返回 null，并记录 `byte_cache_fast_path_rejected{reason=cached_source_missing}`。

`PlayerController` 播放前流程：

```text
tryVerifiedFastPath()
  ├─ success -> play resolved cached source
  └─ rejected -> normal resolve

normal resolve failed/timeout
  ├─ tryVerifiedFallback()
  └─ else keep existing failure behavior
```

必须保留现有 stale refresh 策略：

- bad HTTP status、invalid content type、remote container parse failure 仍驱逐 source + SimpleCache，并 fresh resolve 一次。
- 本地文件播放失败不走远端 stale refresh。

测试覆盖：

- `no-cache` + `PlayableVerified` + `Complete` + Room source 存在 -> 不调用插件，直接播放。
- `no-cache` + `Partial` -> 必须调用插件。
- 插件 timeout + verified cache 存在 -> fallback 播放。
- 插件 timeout + verified cache 不存在 -> 保持当前失败行为。
- `no-store` 即使有旧状态也不走 fast path/fallback。
- RN parity：在线 `no-cache` 常规路径仍不把 Room source 当作普通命中；只有 verified byte-cache 专用路径可以读取历史 source。

## Phase 6：失效与清理联动

### 改动文件

- 修改 `player/src/main/java/com/hank/musicfree/player/cache/SimpleCacheHolder.kt`
- 修改 `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`
- 修改 `data/src/main/java/com/hank/musicfree/data/repository/MediaCacheRepository.kt` 或当前清理编排入口
- 修改 Settings 单曲缓存清理相关测试（按实际入口定位）

### 实现要点

所有按 song/key 清理的入口同步处理三层：

1. Room source cache：`media_cache`
2. Media3 byte cache：SimpleCache resource
3. Byte cache status：`byte_cache_status`

失效原因枚举至少覆盖：

- `stale_url`
- `http_bad_status`
- `invalid_content_type`
- `container_parse_failure`
- `bad_byte_cache`
- `manual_clear`
- `lru_evict`
- `schema_migration`

日志：

- `byte_cache_invalidated`
- 继续保留现有 `media_cache_evict_for_key`

测试覆盖：

- stale URL refresh 同时 mark invalid。
- 手动清理单曲删除所有音质状态。
- SimpleCache key 被驱逐时状态不再显示 `PlayableVerified`。

## Phase 7：预取语义修正

### 改动文件

- 修改 `player/src/main/java/com/hank/musicfree/player/prefetch/PrefetchCoordinator.kt`
- 修改 `player/src/test/java/com/hank/musicfree/player/prefetch/PrefetchCoordinatorTest.kt`

### 实现要点

- 预取仍只 warm head，不升级 `PlayableVerified`。
- 若目标 key 已是 `PlayableVerified + Complete`，跳过预取并记录 `prefetch_head_skipped_verified`。
- 原 `prefetch_success` 改为 `prefetch_head_success`，字段包含 `bytesRead`，避免误读为整首缓存完成。
- 预取失败不标记 `StaleOrInvalid`，除非明确是现有 stale refresh 规则覆盖的远端错误。

测试覆盖：

- 预取只写 `Partial` 或不写状态。
- verified 歌曲跳过预取。
- 预取日志事件名和字段正确。

## Phase 8：文档与排查 recipe

### 改动文件

- 修改 `docs/dev-harness/player/cache-and-logs.md`
- 视实际落地情况修改 `docs/DOCS_STATUS.md`

### 文档内容

补充：

- 四态/五态有效性模型。
- `no-cache` 常规路径与 verified byte-cache 快路径区别。
- Logan recipe：
  - 查某首歌是否 `PlayableVerified`。
  - 查为什么未使用 verified cache。
  - 查 `bytesFromUpstream == 0` 是否成立。
  - 查某 key 何时被 invalidated/evicted。

验收：

- 文档只用相对路径。
- 不出现 `/Users/` 绝对路径。

## Phase 9：集中验证

### Targeted tests

```bash
./gradlew :core:testDebugUnitTest --tests '*ByteCache*' --no-daemon
./gradlew :data:testDebugUnitTest --tests '*ByteCache*' --no-daemon
./gradlew :player:testDebugUnitTest --tests '*ByteCache*' --tests '*PlayerControllerStaleUrlRefreshTest*' --tests '*PlayerControllerPlaybackFailurePolicyTest*' --tests '*PrefetchCoordinatorTest*' --no-daemon
./gradlew :plugin:testDebugUnitTest --tests '*PluginMediaSourceServiceCacheTest*' --no-daemon
```

### Migration / instrumentation

```bash
./gradlew :data:connectedDebugAndroidTest --tests '*AppDatabaseMigration14To15Test*' --no-daemon
```

若当前环境没有设备/模拟器，记录未运行原因，并至少运行可用的 JVM / Robolectric 数据层测试。

### Harness / build

```bash
bash scripts/dev-harness/check.sh
./gradlew :app:assembleDebug --no-daemon
```

### 运行态验收

用可控 fake plugin 或 MockWebServer 验证：

1. 第一次完整播放 `no-cache` 歌曲，产生 `byte_cache_verified`。
2. 第二次阻断插件解析，播放通过 `playback_resolve_fallback_byte_cache` 启动。
3. 第二次 DataSource close 中 `bytesFromUpstream == 0`。
4. 手动清理后第三次播放不再使用 verified cache。

## Phase 10：风险与回滚

### 风险

- Room schema 变更影响老用户升级，必须有迁移测试。
- 快路径误用 Room source 会悄悄改变 `no-cache` 语义，必须把入口命名和调用条件写窄。
- `Complete` 误判可能导致播放坏缓存，必须保留 stale invalidation 并只让 `PlayableVerified` 直接启动。
- content length 缺失的流媒体不能判定完整，会保持 `Partial`，这是保守行为。

### 回滚

- 可通过关闭 fast path 调用点回到现状，保留状态表不影响常规播放。
- 若状态表有问题，可忽略 `byte_cache_status`，仍保留现有 SimpleCache 拉流能力。
- 不删除或破坏现有 `media_cache` Room source 数据。

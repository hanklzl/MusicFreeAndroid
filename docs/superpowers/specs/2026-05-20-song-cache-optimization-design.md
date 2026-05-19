# 歌曲缓存系统优化设计

> 文档状态：当前规范（跨域：Player / Plugin / Data / Downloader / Feature Settings / Logging）
> 适用范围：音频流缓存命中率与可观测性整体优化（不含歌词缓存、Coil 图片缓存）
> 直接执行：是（作为 implementation plan 输入）
> 最后校验：2026-05-20
> 关联 dev-harness：[player/rules](../../dev-harness/player/rules.md)、[plugin/rules](../../dev-harness/plugin/rules.md)、[runtime/rules](../../dev-harness/runtime/rules.md)、[test/rules](../../dev-harness/test/rules.md)
> 上游依赖：[2026-05-19 流量统计与音频本地缓存设计](2026-05-19-traffic-stats-and-media-cache-design.md)（SimpleCache 基线）

## 1. 背景

仓库已落地音频本地缓存（Media3 `SimpleCache` 512 MB + `MediaCacheRepository` 内存/DB 两级 URL 缓存），上游 spec 见 `2026-05-19-traffic-stats-and-media-cache-design.md`。在该基线之上，本次面向**提升缓存命中率与定位能力**做整体优化。

调研事实（基于当前代码，见报告附录 §A）：

- Media3 `SimpleCache` 的 `cacheKey = "${platform}:${id}"`（`HeaderInjectingDataSourceFactory.kt:61`、`PlayerController.kt:720`），**不区分 PlayQuality** → 切码率会复用同一份缓存文件，存在播放错音/串内容的风险（"假命中"）。
- `MediaCacheRepository` 按 `(platform, id)` 主键存一行 JSON，内部按 PlayQuality 分桶存 URL + Headers；硬限 800 行，超限 prune 一半，**与用户配置 `maxMusicCacheSizeBytes` 不联动**（`MediaCacheRepository.kt:124,251,391`）。
- `SimpleCacheHolder.DEFAULT_BYTES = 512MB` **硬编码**，不读用户配置（`SimpleCacheHolder.kt:71`）。
- `PluginMediaSourceService.doResolve` **不查已下载文件**（grep `localPath` 零命中）→ 已下载歌仍走插件请求 URL，浪费流量、放慢首帧。`MusicRepository.commitDownloadedTrack` 已在写入时把 `localPath = mediaStoreUri` 落到 `MusicItem`（`MusicRepository.kt:112`），下游只是没消费。
- `CacheControlPolicy.shouldWriteCache` 在线 `no-cache` 时仍写缓存（`CacheControlPolicy.kt:17-20`），无效占用 LRU 空间。
- `MediaCacheRepository.evictCacheEntry` 在 HTTP 400 触发时**只清 Repository，不清 Media3 文件**（`PluginMediaSourceService.kt:58-60`）→ 失效 URL 仍指向旧缓存文件。
- 只有 `media_cache_hit` / `media_cache_write` 事件，**没有 miss 事件**（grep `cache_miss` 零命中）→ 当前命中率无法度量。
- 用户报"某首歌一直走网络"时，没有贯穿解析链路的 `traceId`，难以反查根因。

## 2. 目标与非目标

### 2.1 目标

1. **命中率结构性提升**：让"重复播放、切码率、顺播下一首、弱网/离线、已下载"五类场景的命中率都可度量、可优化。
2. **已下载歌曲走本地短路**：完全跳过插件请求和网络。
3. **cacheKey 区分 PlayQuality**：消除假命中。
4. **容量治理与用户配置联动**：Media3 SimpleCache 与 `MediaCacheRepository` 字节配额都从 `AppPreferences.maxMusicCacheSizeBytes` 读取。
5. **可观测性**：补 miss 埋点 + 引入贯穿解析链路的 `playSessionId` + 9 条诊断事件，让任意一首歌的播放链路可被 `grep platform:id` 一次性拉出。
6. **缓存治理统一**：HTTP 400/手动清空/迁移时 Repository 与 Media3 SimpleCache 联动 evict。
7. **顺播预取 + 钉选优先保留**：下一首预热头部、收藏/最近播放优先抗淘汰。

### 2.2 非目标

- 不优化歌词缓存（`LyricRepository`）和图片缓存（Coil）。
- 不引入第三方 trace 框架；`playSessionId` 是本地短哈希，仅用于日志聚合。
- 不引入 Media3 实验 API（如 `PreCacheHelper`）；预取走"打开 CacheDataSource 读前 512 KB"的最小实现。
- 不为 dashboard / BI 上报做改造；统计依赖现有 MfLog/Logan 日志后处理。

## 3. 总体架构

### 3.1 新的解析顺序（`PluginMediaSourceService.doResolve`）

```text
setMediaItemAndPlay(item, quality)
   ├─ play_session_start { sid, platform, id, requestedQuality, networkType, isOnline }
   │
   ├─ 1) Local short-circuit
   │     if item.localPath != null && ContentResolver.openFileDescriptor(uri,"r") OK
   │       → LocalMediaSource(uri = item.localPath)
   │       → emit media_cache_hit { source = local }
   │       → STOP（不走插件、不走两级缓存）
   │
   ├─ 2) MediaCacheRepository.getInMemory(platform, id, quality)
   │     → hit: source = repo_mem
   │
   ├─ 3) MediaCacheRepository.getFromDb(platform, id, quality)
   │     → hit: source = repo_db；promote 到 memory
   │
   ├─ 4) Plugin call
   │     → resolve_plugin_call_start / _end，拿到新 URL + headers + returnedQuality
   │     → emit media_cache_miss { reason = cold | stale | disabled | no_cache_policy | offline_only }
   │     → 按 CacheControlPolicy 决定是否写两级缓存
   │
   └─ 5) Media3 拉流（HeaderInjectingDataSourceFactory + CacheDataSource）
         cacheKey = "${platform}:${id}:${quality.name.lowercase()}"
         → CacheDataSource.EventListener 桥接 media3_datasource_open / _error
         → 命中事件 emit media_cache_hit { source = media3 }
```

四档 `source` 一起算"命中（不走网络）"；Plugin call 才算 miss。

### 3.2 命中率定义

```text
hitRate = count(media_cache_hit) /
          ( count(media_cache_hit) + count(media_cache_miss) )

by source: local / repo_mem / repo_db / media3
```

UI 不展示，仅靠日志后处理统计。

## 4. 详细设计

### 4.1 本地短路（Local short-circuit）

**位置**：`PluginMediaSourceService.doResolve` 开头新增 `resolveLocal(item)` 子流程。

**判定**：
- `item.localPath != null`
- `ContentResolver.openFileDescriptor(uri, "r")` 不抛异常即视为可读（关闭 FD 立即释放）

**返回**：构造 `ResolvedMediaSource(uri = item.localPath, headers = emptyMap(), cacheKey = null, source = LOCAL)`。
- `cacheKey = null` 让 `HeaderInjectingDataSourceFactory` 跳过 `CacheDataSource`，直接走文件 DataSource（避免把本地文件再次写进 SimpleCache）。

**失效兜底**：
- `openFileDescriptor` 抛 `FileNotFoundException` / `SecurityException` → 视作"用户在系统媒体库删了文件"
- 同步调用 `MusicRepository.removeFromLocalLibrary(item)` 清掉 `DownloadedTrackEntity` 行与 `MusicItem.localPath`
- emit `local_short_circuit_fallback { sid, platform, id, reason }`
- 继续走 2)–5) 的常规流程

**测试**：
- 单测：localPath 可读 → 返回 LocalMediaSource；不可读 → 回退、清 DAO 行
- Android 仪器测：从 MediaStore 真正删一首 → 第二次播放回退、Library 中该歌 `downloaded` 字段恢复 false

### 4.2 cacheKey 改造

**新 key**：`"${platform}:${id}:${quality.name.lowercase()}"`（`low | standard | high | super | unknown`）

**改造点**：
1. `TrackHeaderRegistry.put(...)` 签名加 `quality: PlayQuality?`
   - `PlayerController.setMediaItemAndPlay` 传入 `currentPlayQuality`
   - `cacheKey(uri: Uri): String?` 仍按 URI 查（registry 内部按 URI → cacheKey 索引）
2. `HeaderInjectingDataSourceFactory`：
   - **删除手工 `dataSpec.setKey(...)`**
   - 在 `CacheDataSource.Factory()` 注入 `.setCacheKeyFactory { spec -> registry.cacheKey(spec.uri) ?: spec.uri.toString() }`，具备防御性

**旧缓存一次性清零**：
- `SimpleCacheHolder` 新增 `migrateOnceIfNeeded()`：
  - 读 DataStore key `media_cache_schema_version`（默认 0）
  - 若 < 1：遍历 `simpleCache.keys`，凡是不匹配正则 `^[^:]+:[^:]+:(low|standard|high|super|unknown)$` 的 key 一律 `removeResource`，emit `media_cache_evict { scope = migration, count, freedBytes }`
  - 把 schema_version 写为 1
- 进 `SimpleCacheHolder.cache` 第一次访问前同步执行（在 `appliedScope.launch` 内串行化）

**为什么不做兼容期**：
- 旧 key 与新 key 在 LRU 中并存会让命中率指标失真两周
- 旧 key 没有 quality 信息，无法可靠映射到新 key
- 一次性清零代价 = 用户一次重新缓存的流量，明确且短

**`MediaCacheRepository` schema 不动**：本身已按 PlayQuality 分桶存 URL，只是 Media3 那层之前没区分。

### 4.3 容量治理

#### 4.3.1 `SimpleCache` 容量动态化

- `SimpleCacheHolder` 构造改为读 `AppPreferences.maxMusicCacheSizeBytes`
- Evictor：自定义 `PinningCacheEvictor`（见 4.6 B）包装 `LeastRecentlyUsedCacheEvictor(maxBytes)`
- 用户在设置改容量 → DataStore Flow 触发 `SimpleCacheHolder.updateMaxBytes(newBytes)`：
  - 若新值 < 当前已用，按 LRU 立即淘汰至 `newBytes * 0.95`
  - emit `media_cache_evict { scope = byte_cap, ... }`

#### 4.3.2 `MediaCacheRepository` DB 字节估算

- 保留 800 行硬限作为安全阀
- 新增字节估算：`estimatedBytes = sum(sourcesJson.length()) + rows * 256B` （header overhead 估算）
- 字节预算：`maxMusicCacheSizeBytes × 10%`（URL/header 是元数据，10% 已绰绰有余）
- 超限时按 `lastAccessed` 升序删，淘汰到预算的 80%
- emit `media_cache_evict { scope = repo_byte_cap, count, freedBytes }`

#### 4.3.3 磁盘空间防御

- `SimpleCacheHolder.init()` 中 `File.getUsableSpace()` < 2 GB 时临时上限降级到 `min(configured, 256MB)`
- emit `media_cache_lowspace { availableBytes, configuredBytes, fallbackBytes }`

#### 4.3.4 设置页区分展示

`SettingsCacheCleaner` / Settings UI 区分两栏：
- **音频文件缓存**：`simpleCache.cacheSpace`（数百 MB 量级）
- **URL 元数据缓存**：`mediaCacheRepository.estimatedBytes`（数 MB 量级）

各自单独可清。

### 4.4 联动 evict

**入口**：
- `PluginMediaSourceService.handleHttp400` → `MediaCacheRepository.evictCacheEntry(item, quality)` → **同步**调用 `SimpleCacheHolder.evictForKey(platform, id, quality)`
- `SettingsCacheCleaner.clearMusicCache()` → 两层各 `clearAll()` + emit `media_cache_cleared { scope = all }`

**`SimpleCacheHolder.evictForKey(platform, id, quality)`**：
- 若 `quality != null`：`simpleCache.removeResource("$platform:$id:${quality.name.lowercase()}")`
- 若 `quality == null`：遍历 5 个 quality 后缀逐个 remove（API 调用 5 次开销可忽略）
- emit `media_cache_evict { scope = stale_url, key, freedBytes }`

### 4.5 可观测性

#### 4.5.1 度量埋点

| 事件 | 字段 |
|---|---|
| `media_cache_hit` | `source ∈ {local, repo_mem, repo_db, media3}`, `platform`, `musicItemId`, `quality`, `sizeBytes?` |
| `media_cache_miss` | `reason ∈ {cold, stale, disabled, no_cache_policy, offline_only}`, `platform`, `musicItemId`, `quality` |
| `media_cache_write` | `layer ∈ {repo, media3}`, `sizeBytes`, `durationMs` |
| `media_cache_evict` | `scope ∈ {lru, byte_cap, repo_byte_cap, stale_url, manual, migration}`, `count`, `freedBytes` |
| `media_cache_lowspace` | `availableBytes`, `configuredBytes`, `fallbackBytes` |

约束：所有事件 key 用 snake_case，遵循 [logging 规范](../../../CLAUDE.md#日志记录规范)；URL 不进字段，只进 `urlHash`。

#### 4.5.2 诊断日志（`playSessionId` 链路）

`PlayerController.setMediaItemAndPlay` 入口生成 `sid = "ps_" + randomShortHex(6)`，通过 `MediaItem.requestMetadata.extras` 或 `TrackHeaderRegistry` 同步索引传给下游。

| # | 事件 | 字段 |
|---|---|---|
| 1 | `play_session_start` | `sid, platform, id, requestedQuality, networkType, isOnline` |
| 2 | `resolve_local_check` | `sid, hasLocalPath, localPathReadable` |
| 3 | `resolve_cache_lookup` | `sid, layer ∈ {mem, db}, hit, qualityMatched, ageSeconds` |
| 4 | `resolve_plugin_call_start` | `sid, pluginName` |
| 5 | `resolve_plugin_call_end` | `sid, durationMs, returnedQuality, hasUrl, hasHeaders, urlHash` |
| 6 | `cache_write` | `sid, layer ∈ {repo, media3_planned}, sizeBytes?` |
| 7 | `media3_datasource_open` | `sid, cacheKey, cacheHit, bytesFromCache, bytesFromUpstream` |
| 8 | `media3_datasource_error` | `sid, errorCode, httpStatus?, position, cacheKey, retryCount` |
| 9 | `play_session_end` | `sid, totalDurationMs, bytesFromCache, bytesFromUpstream, hitClassification` |

**urlHash 计算**：`sha1(url).take(8)`，足以区分"是否同一 URL"且不泄露插件签名 token。

**典型排查路径**：
- "某首歌每次都加载慢" → `grep platform:id` → 看 #1 networkType、#2 localPath、#3 layer + ageSeconds、#5 plugin durationMs、#7 cacheHit
- "切码率掉缓存" → 比较两次会话 #3 qualityMatched、#7 cacheKey
- "已下载还走网络" → 看 #2 hasLocalPath / Readable、#9 hitClassification

**sid 链路传递**：
- `PlayerController` 暴露 `currentSid: StateFlow<String?>`，每次 `setMediaItemAndPlay` 入口写入新值（旧值被覆盖即终结上一个会话）
- `HeaderInjectingDataSourceFactory` / `CacheDataSource.EventListener` 在写日志时通过弱引用读取最新 sid（避免持有 PlayerController 强引用）
- 并发会话切换瞬间可能出现"上一个会话尾部事件落到新 sid 下"的极少数情况，作为已知限制（不影响命中率统计，只影响极端情况下的单条事件归属）

#### 4.5.3 调试开关

- 默认开启 #1–#4、#6–#9（每次播放 ≈ 6–8 行事件，日志体积可控）
- #5 默认 INFO，仅打 `durationMs / returnedQuality / hasUrl / urlHash`
- Settings 新增"详细缓存日志"开关：开启后 #5 升级到 DEBUG，额外打 headers 集合 hash 与 URL 长度；#3 升级到 DEBUG，额外打每个 quality 桶状态
- 默认不脱敏（遵循 CLAUDE.md 既定策略），导出前提示

### 4.6 预取 + 钉选

#### 4.6.1 顺播预取下一首

**触发**：
- `PrefetchCoordinator`（新类，`:player` 模块）订阅 `PlayerController` 的 `currentPosition` Flow（节流到 1 Hz）
- 当 `position / duration ≥ 0.6` 且 `PlayQueue.next` 非空，且未对该 next 已 prefetch，发起任务

**实现**：
1. `mediaSourceResolver.resolve(nextItem, defaultPlayQuality, useCache = true)` → 灌 URL 进 Repository
2. 用拿到的 URL + headers 打开一次 `CacheDataSource`，`read(buffer, 0, 512 * 1024)` 把头部预热到 SimpleCache
3. close

**约束**：
- 默认仅 Wi-Fi（沿用 `NetworkMonitor`）；Settings 加"流量充足时也预取"开关
- 单任务并发，`Mutex` + 新触发取消旧 Job
- 任何失败 catch → `prefetch_failed { reason, nextPlatform, nextId, sid? }`，主播放不受影响

**为什么不用 `PreCacheHelper`**：实验 API，签名易变；512 KB 头部预热已满足"消除起播间隔"。

#### 4.6.2 钉选优先保留（`PinningCacheEvictor`）

**钉选范围（动态 Flow）**：
- 收藏歌单内的歌：`StarredSheetRepository.observeStarredKeys(): Flow<Set<String>>`（key = `"$platform:$id"`）
- 最近播放 N=50：从 `PlayQueueRepository` 的"曾入队"派生；如不便派生则新增 `RecentPlayedDao`（轻量表）
- 已下载不需要钉（走 4.1 本地短路根本不进 SimpleCache）

**实现**：
- `PinningCacheEvictor : CacheEvictor` 包装 `LeastRecentlyUsedCacheEvictor`
- `onSpanTouched / onSpanAdded` 直接转发到内部 LRU
- 自定义 `onStartFile` 拦截：若需淘汰，先扫一遍 pinned 集合，跳过命中项，落到下一个非 pinned span
- **兜底**：若 `pinnedEstimatedBytes > maxBytes * 0.7` → 临时退化为纯 LRU 并 emit `pinning_overflow_fallback { pinnedBytes, maxBytes }`，避免"全 pinned 无可淘汰"
- pinned 集合通过 `SimpleCacheHolder.updatePinned(set: Set<String>)` 推入；Flow 由 `:player` DI 层 combine `starred + recentPlayed` 产出

## 5. 数据/接口契约变更

### 5.1 关键签名

```kotlin
// TrackHeaderRegistry
fun put(uri: Uri, headers: Map<String,String>, ua: String?, cacheKey: String?, quality: PlayQuality?)

// SimpleCacheHolder
suspend fun migrateOnceIfNeeded()
fun updateMaxBytes(newBytes: Long)
fun updatePinned(keys: Set<String>)
fun evictForKey(platform: String, id: String, quality: PlayQuality?)

// MediaCacheRepository
suspend fun estimatedBytes(): Long
suspend fun evictCacheEntry(item: MusicItem, quality: PlayQuality?) // 已存在，内部新增联动 SimpleCacheHolder

// PluginMediaSourceService
private suspend fun resolveLocal(item: MusicItem): ResolvedMediaSource?

// PlayerController
val currentSid: StateFlow<String?>  // 给 DataSource Factory 弱引用消费

// PrefetchCoordinator（新）
fun start(); fun stop()
```

### 5.2 DataStore key

| key | 类型 | 默认 | 说明 |
|---|---|---|---|
| `media_cache_schema_version` | Int | 0 | 升到 1 触发 4.2 一次性清零 |
| `prefetch_on_metered` | Boolean | false | 4.6.1 |
| `verbose_cache_log` | Boolean | false | 4.5.3 |

### 5.3 Room 变更

- 若选择 4.6.2 新增 `RecentPlayedDao` → 新表 `recent_played(platform, id, last_played_at)`，主键 `(platform, id)`，触发 `version 12 → 13` + 一条 `Migration(12, 13)`（仅 `CREATE TABLE IF NOT EXISTS`），遵循 [CLAUDE.md 数据库迁移规范](../../../CLAUDE.md#数据库迁移规范)
- 若决定从 `PlayQueueRepository` 派生最近播放（无需新表），则不动 Room schema —— **首选方案**，复杂度更低；实现阶段确认 `PlayQueueRepository` 是否保留充足历史

## 6. 兼容与迁移

- **旧 SimpleCache 文件**：4.2 一次性清零；用户一次重新缓存代价可接受
- **`MediaCacheRepository` 数据**：schema 不变，旧 JSON 直接复用；老用户首次启动后字节估算开始工作
- **DataStore 默认值**：所有新 key 默认值保证旧用户首次读不崩
- **已下载短路**：旧用户若已下载过歌曲，`MusicItem.localPath` 已经在 DB 里（`MusicRepository.commitDownloadedTrack:112`），无需迁移；冷启动第一次播放即生效

## 7. 测试策略

### 7.1 单元测试

- `PluginMediaSourceServiceTest`：
  - localPath 可读 → 返回 LocalMediaSource，不调 plugin
  - localPath 不可读 → 回退 + `removeFromLocalLibrary` 被调
  - cache mem hit / db hit / miss 三路径各覆盖
  - HTTP 400 → 同时清 Repository 与 SimpleCache（mock 验证）
- `CacheControlPolicyTest`：在线 `no-cache` → `shouldWriteCache == false`（**回归点：现状是 true**）
- `MediaCacheRepositoryTest`：
  - 字节估算正确
  - 字节配额变化触发淘汰
- `SimpleCacheHolderTest`（JVM，可用 `androidx.media3:media3-test-utils`）：
  - `migrateOnceIfNeeded` 清掉旧 key、保留新 key
  - `updateMaxBytes` 缩容触发淘汰
  - `evictForKey` 各 quality 都能命中
- `PinningCacheEvictorTest`：pinned 跳过、overflow 退化为 LRU
- `PrefetchCoordinatorTest`：progress < 0.6 不触发；非 Wi-Fi 不触发；新触发取消旧
- 日志：每个 emit 走 `MfLogger` mock，验证字段完整

### 7.2 Android 仪器测

- `LocalShortCircuitInstrumentedTest`：写一首到 MediaStore → 播放走 local；删除后再播 → 回退、Library 更新
- `CacheKeyQualityE2ETest`：播标准质→切高质→回标准质，验证 SimpleCache 两个 key 共存、不串内容（用不同 sha1 的模拟文件）

### 7.3 不写的测试

- 不为"诊断日志字段"写覆盖测试（成本高、收益低，靠 code review 守）
- 不为 Coil / 歌词写测试（非本 spec 范围）

## 8. 风险与缓解

| 风险 | 缓解 |
|---|---|
| cacheKey 一次性清零导致用户感知"流量突增" | 文档 + release notes 提示；首次清零事件 emit `migration` scope，可观测 |
| 本地短路误判（MediaStore URI 可读但文件已损坏） | 兜底：Media3 拉流报错时下次播放重新走插件路径；不在 `openFileDescriptor` 阶段做完整性校验（成本太高）|
| `PinningCacheEvictor` bug 导致 SimpleCache 始终不淘汰 → 撑爆磁盘 | 4.6.2 兜底退化到纯 LRU；磁盘空间防御 4.3.3 |
| `playSessionId` 传递路径复杂（跨 Player ↔ DataSourceFactory ↔ CacheDataSource） | 用 `PlayerController.currentSid` `StateFlow` + 弱引用桥接，避免线程安全 / 生命周期问题；测试覆盖"sid 在并发会话切换时不串台" |
| 诊断日志体积过大 | 默认级别保守；详细日志靠 settings 开关；现有 Logan 7 天滚动 |
| 预取在弱网误触发拖慢主播放 | 默认仅 Wi-Fi；任何失败静默；新触发取消旧 Job |
| `RecentPlayedDao` 引入 Room 迁移成本 | 首选方案：从 `PlayQueueRepository` 派生，避免新表 |

## 9. 验收标准

**功能**：
- 重启 app 后播放一首已下载歌：日志 #1 后只见 #2(`hasLocalPath=true, readable=true`) 与 #9(`hitClassification=local`)；无 #4/#5（plugin call）
- 标准质 → 高质切换：SimpleCache 中两个 quality 后缀的 cacheKey 同时存在；切回标准质走 `media3` 命中
- HTTP 400 触发：Repository 与 SimpleCache 同步被清，下一次播放走 plugin
- 设置里清空音频缓存 → SimpleCache `cacheSpace == 0`、Repository `estimatedBytes ≈ 0`
- 顺播下一首：当前歌过 60% 后 1 秒内出现 `prefetch_*` 日志；切到下一首时 `media3_datasource_open.cacheHit = true` 或 `bytesFromCache > 0`

**可观测**：
- 任意一首歌的 `platform:id`（如 `kugou:abc123`）grep 一次得到 #1–#9 完整序列
- `media_cache_hit` 与 `media_cache_miss` 数量比可被日志后处理算出按 source 维度的命中率

**回归**：
- 所有 `:player`、`:plugin`、`:data`、`:downloader` 单测 + 仪器测通过
- `:app:assembleDebug` 通过

**不要求**：
- Release 构建（除非本 spec 触发了 ProGuard 反射保留风险，预估 cacheKey 字符串拼接不涉及）
- 性能微基准（本次以正确性 + 命中率为目标）

## 10. 实施次序提示（给后续 writing-plans）

不强制分阶段交付（per Approach B），但实现内部建议次序，便于逐步联调：

1. 可观测性骨架（`playSessionId` + 9 事件 + miss 埋点）—— 先有日志才能验证后续每一步
2. 本地短路 —— 收益立竿见影
3. cacheKey 改造 + 一次性清零迁移
4. 联动 evict + no-cache 不写
5. 容量动态化 + 字节估算
6. PinningCacheEvictor + 收藏/最近播放钉选
7. PrefetchCoordinator

每一步都跑 §7 对应单测，最后跑 §9 验收清单。

## 附录 A：调研报告原始证据

完整代码事实见会话中 Explore agent 输出。关键代码引用：

- `SimpleCacheHolder.kt:51-71`：512MB LRU、目录策略
- `MediaCacheRepository.kt:67-126,251-275,289-334,391-410`：两级 LRU、800 行 prune、字节估算缺失
- `HeaderInjectingDataSourceFactory.kt:44,48-82`：cacheKey 注释、CacheDataSource 构造
- `PlayerController.kt:585-623,680,720`：setMediaItemAndPlay 链路、TrackHeaderRegistry 写入
- `PluginMediaSourceService.kt:58-60,62-166,79,127-162,169-204`：HTTP 400 evict、缓存命中 Important #1 注释、no-cache 写入策略
- `CacheControlPolicy.kt:17-20`：在线 no-cache 仍写
- `MusicRepository.kt:89-141`：commitDownloadedTrack 写 localPath
- `MusicItemBridgeProjector.kt:28-43`：JS 桥附加 localPath
- `MediaCacheKeyStabilityTest.kt:54-72`：仅测签名稳定，未测 quality 变化
- `docs/superpowers/specs/2026-05-19-traffic-stats-and-media-cache-design.md`：SimpleCache 落地的上游 spec

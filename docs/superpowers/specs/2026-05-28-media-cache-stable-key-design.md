# 在线歌曲缓存稳定 key 与 no-store 治理设计

> 文档状态：当前规范（Player / Plugin / Core / Logging 设计）
> 适用范围：在线播放字节缓存复用、插件 `cacheControl` 语义、预加载缓存复用、Logan 缓存诊断
> 直接执行：是（作为 implementation plan 输入）
> 最后校验：2026-05-28
> 关联 dev-harness：[player/rules](../../dev-harness/player/rules.md)、[plugin/rules](../../dev-harness/plugin/rules.md)、[test/rules](../../dev-harness/test/rules.md)
> 上游参考：[歌曲缓存系统优化设计](2026-05-20-song-cache-optimization-design.md)、[流量统计与音频本地缓存设计](2026-05-19-traffic-stats-and-media-cache-design.md)、[歌曲缓存复用与日志排查手册](../../dev-harness/player/cache-and-logs.md)

## 1. 背景与 review 结论

用户反馈 2026-05 下旬流量消耗明显增大。针对反馈包 `musicfree-feedback-2392984985331850803.zip` 解码 Logan 后确认：

- `play_session_start` 约 140 次，`media_cache_miss` 约 166 次，`media_cache_hit` 约 7 次。
- `media_cache_miss` 主要来自 `元力QQ` 的 `no-cache` 在线策略，其中 `SUPER` 音质占大头。
- 同一首 `元力QQ` 歌曲重复播放时，插件返回的签名 URL hash 多次变化。
- `media_cache_key_registry_miss` 频繁出现，且 `media_cache_lru_evict.evictedKeys` 中可见完整签名 URL，说明 Media3 `SimpleCache` 实际退化成 URL key。
- 本地文件缓存仍然有效，问题集中在在线播放字节缓存复用层。

代码 review 结论：

- `HeaderInjectingDataSourceFactory` 已设计 stable key：registry 命中时使用 `${platform}:${id}:${quality}`，registry miss 时退回 `uri.toString()`。
- `PlayerController` 只有在 `headers/userAgent` 非空时才调用 `TrackHeaderRegistry.put(...)`；无 headers/UA 的 QQ 源不会注册 stable key。
- `PrefetchCoordinator` 会 warm 下一首前 512 KB，但 warm 前没有注册 stable key，也没有固定使用当前播放质量。
- `CacheDataSourceEventBridge` 当前先发 `media3_datasource_open(cacheHit=false, bytes=0)`，且 cache key 是 `(per-open)` 占位，不能作为最终命中证据。
- Android 当前 `shouldWriteCache(no-cache, online)=false`，与 RN MusicFree 的解析结果缓存写入语义不一致。

用户明确指出播放质量是用户选择，不应把降低音质作为修复方案。因此本设计不调整用户音质策略，目标是在 `SUPER` 等高音质下让缓存机制本身生效。

## 2. RN MusicFree 缓存逻辑确认

RN 版 MusicFree 可以作为缓存语义参考，但不能直接照搬播放器字节缓存实现。

可参考的语义：

- `MediaCache` 使用 `platform@id` 作为歌曲唯一 key，解析结果按 `source[quality]` 分桶存储。
- `cacheControl` 默认 `no-cache`。
- 在线播放时，只有 `cache` 读取解析结果缓存；`no-cache` 在线不读，离线可读；`no-store` 不读不写。
- 插件解析成功后，只要不是 `no-store`，RN 会写入解析结果缓存。
- `mergeTrackSource` 会保留原始 `id/platform`，即播放 URL 变化不改变歌曲身份。

不可直接照搬的实现：

- RN 通过 `react-native-track-player` / KotlinAudio 管理播放器缓存，只暴露 `maxCacheSize`，没有业务侧 stable cache key API。
- Android 原生实现已经具备 Media3 `CacheKeyFactory`，必须用 Android 自己的 stable key 机制解决签名 URL 轮换问题。

因此本设计采用 RN 的 `cacheControl` 和歌曲身份语义，同时保留 Android 的 Media3 stable key 增强。

## 3. 目标与非目标

### 3.1 目标

1. 无 headers/UA 的 http(s) 播放 URL 也必须注册 stable key。
2. 同一 `platform/id/quality` 的不同签名 URL 必须复用同一个 SimpleCache key。
3. 预加载写入的前 512 KB 必须能被后续正式播放复用。
4. `no-store` 必须同时禁止解析结果缓存和字节缓存落盘。
5. `no-cache` 在线仍 fresh resolve，但解析成功后写入解析结果缓存，用于离线兜底，保持 RN 语义。
6. Logan 必须能判断一次播放最终从缓存读了多少字节、从上游读了多少字节。
7. 修复后仍尊重用户选择的播放质量，不自动降级。

### 3.2 非目标

- 不修改插件 JS 协议字段。
- 不改变用户配置的默认播放音质、音质回退顺序、移动网络播放策略。
- 不新增 UI 页面或缓存 dashboard。
- 不调整下载、本地音乐、歌词缓存、Coil 图片缓存策略。
- 不把在线 `no-cache` 改成读取解析结果缓存；这会违反 RN 和插件语义。

## 4. 设计方案

### 4.1 共享 cache policy

将插件缓存策略从 `:plugin` 私有类型上移到 `:core`，新增 `MediaSourceCachePolicy`：

```kotlin
enum class MediaSourceCachePolicy(val wire: String) {
    Cache("cache"),
    NoCache("no-cache"),
    NoStore("no-store");

    companion object {
        fun parse(value: String?): MediaSourceCachePolicy
    }
}
```

规则函数同样放到 `:core`：

```kotlin
fun MediaSourceCachePolicy.canReadResolvedSource(isOffline: Boolean): Boolean
fun MediaSourceCachePolicy.canWriteResolvedSource(): Boolean
fun MediaSourceCachePolicy.canWriteByteCache(): Boolean
```

决策：

| policy | 在线读解析结果 | 离线读解析结果 | 写解析结果 | 写字节缓存 |
|---|---:|---:|---:|---:|
| `cache` | 是 | 是 | 是 | 是 |
| `no-cache` | 否 | 是 | 是 | 是 |
| `no-store` | 否 | 否 | 否 | 否 |

`no-cache` 在线 fresh resolve 仍然可能命中 Media3 字节缓存，因为 fresh URL 只用于校验和拉流入口，字节复用按 stable key 判断。

### 4.2 `MediaSourceResolution` 增加 policy

在 `core.media.MediaSourceResolution` 增加：

```kotlin
val cachePolicy: MediaSourceCachePolicy
```

填充规则：

- 本地文件短路：`NoStore`，因为本地文件不进入网络字节缓存。
- `PluginMediaSourceService` 从插件 `info.cacheControl` parse 后透传。
- 从 `MediaCacheRepository` 命中的 cached source 也透传当次插件 policy，而不是缓存写入时的历史 policy。
- alternative plugin 解析时仍以原请求 item 的 source plugin policy 为准，保持当前缓存归属语义。
- 测试 fake resolver 必须显式填 policy，避免默认值掩盖遗漏。

### 4.3 `PlaybackCacheKeyRegistrar`

新增 `:player` 内部单例 `PlaybackCacheKeyRegistrar`，封装 URL 注册逻辑：

```kotlin
class PlaybackCacheKeyRegistrar @Inject constructor(
    private val registry: TrackHeaderRegistry,
) {
    fun register(
        url: String,
        item: MusicItem,
        source: MediaSourceResult,
        quality: PlayQuality,
        cachePolicy: MediaSourceCachePolicy,
        trigger: Trigger,
    ): RegisterResult
}
```

注册规则：

- 仅处理 `http` / `https`。
- `headers/userAgent` 为空也注册。
- `cacheKey = "${item.platform}:${item.id}"`，最终 key 仍由 `HeaderInjectingDataSourceFactory` 组装成 `${platform}:${id}:${quality}`。
- `cachePolicy == NoStore` 时仍注册 headers/UA，但 entry 标记 `byteCacheAllowed=false`。
- file/content/local URL 不注册，并返回 `SkippedNonHttp`。
- 记录 `media_cache_key_registered`，字段：
  - `platform`
  - `itemId`
  - `quality`
  - `trigger`：`playback` / `stale_refresh` / `failure_source_change` / `prefetch`
  - `host`
  - `cachePolicy`
  - `byteCacheAllowed`
  - `hasHeaders`
  - `hasUserAgent`

替换 `PlayerController` 三处直接 `TrackHeaderRegistry.put(...)`：

- 普通解析成功。
- stale URL refresh 后重新装载。
- 播放失败换源后重新装载。

### 4.4 `TrackHeaderRegistry` entry 扩展

`TrackHeaderRegistry.HeaderEntry` 增加：

```kotlin
val byteCacheAllowed: Boolean
val cachePolicy: MediaSourceCachePolicy
```

`MAX` 从 16 调整为 64。理由是当前 registry 同时承载当前播放、下一首预加载、失败刷新和换源 URL。16 对队列播放和预加载容易过早驱逐，64 仍是很小的进程内 map，不涉及落盘。

### 4.5 policy-aware DataSource

`HeaderInjectingDataSourceFactory` 继续负责 headers/UA 注入，但要按 registry entry 决定是否走 SimpleCache。

设计：

- 非 http(s)：直接走 `DefaultDataSource`，不查 registry。
- registry miss：允许播放，走 cached path 但 cache key 退化为 URL，并记录 `media_cache_key_registry_miss`。
- registry hit 且 `byteCacheAllowed=true`：走 `CacheDataSource`，cache key 为 `${cacheKey}:${quality}`。
- registry hit 且 `byteCacheAllowed=false`：走 uncached resolving datasource，仍注入 headers/UA，记录 `media_cache_bypass{reason=no_store}`。

为拿到 per-open 的真实 key，`HeaderInjectingDataSourceFactory.createDataSource()` 不再直接返回固定 `CacheDataSource`。新增一个轻量 `PolicyAwareDataSource`：

- `open(dataSpec)` 时解析 URI、查询 registry、构造本次 delegate。
- cached delegate 用 `CacheDataSource.Factory().setEventListener(eventBridge.newListener(realCacheKey))`。
- uncached delegate 用 resolving upstream datasource。
- `read/close/getUri/getResponseHeaders` 代理给当前 delegate。

这样 `CacheDataSourceEventBridge` 能拿到真实 cache key，不再需要 `(per-open)` 占位。

### 4.6 `CacheDataSourceEventBridge` 改为 close 归档

保留 `media3_datasource_open`，但只作为打开事件；新增 `media3_datasource_close` 表示本次 DataSource 生命周期的最终统计。

字段：

- `sid`
- `cacheKey`
- `cacheHit`：`bytesFromCache > 0`
- `bytesFromCache`
- `bytesFromUpstream`
- `cacheBypassReason`：空或 `no_store`

实现注意：

- `CacheDataSource.EventListener.onCachedBytesRead` 只提供 cache bytes。
- upstream bytes 可以在 `PolicyAwareDataSource.read()` 中按 `CacheDataSource` 的 `onCachedBytesRead` 差值推导，或在 v1 采用 `totalRead - cachedBytes`。
- `close()` 必须只 emit 一次，避免 ExoPlayer 多次 close 导致重复统计。

### 4.7 预加载复用

`PlayerController` 暴露：

```kotlin
val currentQualityFlow: StateFlow<PlayQuality>
```

`PrefetchCoordinator` 构造增加：

```kotlin
private val currentQualityFlow: StateFlow<PlayQuality>
private val cacheKeyRegistrar: PlaybackCacheKeyRegistrar
```

行为：

- `combine(progressFlow, nextItemFlow, isWifiFlow, currentQualityFlow)`。
- `lastPrefetchedKey = "${next.platform}:${next.id}:${quality.name.lowercase()}"`。
- `resolver.resolve(next, quality.wireName(), sid = null)`。
- resolve 成功后先 `register(trigger=prefetch)`，再 `warmHead(url)`。
- `cachePolicy == NoStore` 时不 warmHead，记录 `prefetch_skipped{reason=no_store}`。
- 仍保持 Wi-Fi only 和 60% 进度触发。

### 4.8 RN parity 修正

`PluginMediaSourceService.maybeWriteCache(...)` 改为使用 `cachePolicy.canWriteResolvedSource()`，不再把 `isOffline` 作为 `no-cache` 写入条件。

保持不变：

- `resolve()` 在线 `no-cache` 仍跳过缓存读。
- `resolveFresh()` 仍跳过缓存读。
- `no-store` 仍不读不写。

需要更新测试命名，把当前 `no-cache online does not read and does not write` 改为 `no-cache online does not read but writes for offline fallback`。

## 5. 日志与验收口径

新增或调整事件：

| 事件 | category | 触发 |
|---|---|---|
| `media_cache_key_registered` | PLAYER | resolved URL 注册到 registry |
| `media_cache_key_registry_miss` | PLAYER | http(s) URL 未命中 registry，退化为 URL key |
| `media_cache_bypass` | PLAYER | `no-store` 绕过 SimpleCache |
| `media3_datasource_open` | PLAYER | DataSource 打开 |
| `media3_datasource_close` | PLAYER | DataSource 关闭并输出最终字节统计 |
| `prefetch_skipped` | PLAYER | 预加载因 policy 或 URL 类型跳过 |

反馈包验收时，以这些字段判断：

- 同一首 `元力QQ` `SUPER` 连续播放两次，第二次 `media3_datasource_close.bytesFromCache > 0`。
- 同一 `platform/id/quality` 的不同签名 URL 对应相同 `cacheKey`。
- 不再看到该 URL 的 `media_cache_key_registry_miss`。
- `media_cache_lru_evict.evictedKeys` 不再出现完整签名 URL，而是稳定 key。
- `no-store` 播放出现 `media_cache_bypass`，且不产生 stable SimpleCache 写入。

## 6. 测试计划

### 6.1 Core

- `MediaSourceCachePolicyTest`
  - `parse(null)` 返回 `NoCache`。
  - `cache` 在线/离线可读、可写解析结果、可写字节缓存。
  - `no-cache` 在线不可读、离线可读、可写解析结果、可写字节缓存。
  - `no-store` 不读、不写解析结果、不写字节缓存。

### 6.2 Plugin

- `PluginMediaSourceServiceCacheTest`
  - `cache` 命中时不调用插件。
  - `no-cache online` 不读缓存，但 fresh resolve 后写入缓存。
  - `no-cache offline` 可读缓存。
  - `no-store` 不读不写。
  - cached source 返回的 `MediaSourceResolution.cachePolicy` 正确。
  - alternative plugin resolve 返回的 `cachePolicy` 使用请求源插件 policy。

### 6.3 Player

- `PlaybackCacheKeyRegistrarTest`
  - 无 headers/UA 的 http(s) URL 也注册 stable key。
  - file/content URL 跳过注册。
  - `NoStore` entry 的 `byteCacheAllowed=false`。
  - 日志包含 `media_cache_key_registered` 必需字段。

- `MediaCacheKeyStabilityTest`
  - 两个不同签名 URL、同一 `platform/id/quality` 返回相同 cache key。
  - 不同 quality 返回不同 cache key。
  - registry miss 仍返回 URI 字符串并记录 miss。

- `HeaderInjectingDataSourceFactoryTest`
  - `NoStore` registry hit 走 uncached delegate。
  - cached path 的 listener 收到真实 cache key。
  - registry hit 时 headers/UA 注入不受 byte cache bypass 影响。

- `PrefetchCoordinatorTest`
  - 使用当前 `PlayQuality` 调 resolver。
  - warmHead 前调用 registrar。
  - `NoStore` 时跳过 warmHead。
  - `lastPrefetchedKey` 区分 quality。

- `PlayerController` 相关测试
  - 普通播放解析成功、stale refresh、failure source change 三条路径均注册 stable key。
  - 无 headers/UA 的 source 不再导致 registry miss。

### 6.4 命令

执行阶段默认验证：

```bash
./gradlew :core:testDebugUnitTest :plugin:testDebugUnitTest :player:testDebugUnitTest --no-daemon
./gradlew :app:assembleDebug --no-daemon
bash scripts/dev-harness/check.sh
```

本任务不是发布任务，不要求 release 构建。

## 7. 执行计划输入

推荐拆分为 5 个实现步骤：

1. `:core` 抽出 `MediaSourceCachePolicy`，扩展 `MediaSourceResolution.cachePolicy`，修复 fake resolver 与编译错误。
2. `:plugin` 对齐 RN `cacheControl` 写入语义，并补 cache policy 测试。
3. `:player` 新增 `PlaybackCacheKeyRegistrar`，替换 `PlayerController` 三个注册点，扩展 `TrackHeaderRegistry`。
4. `:player` 重写 `HeaderInjectingDataSourceFactory` 为 policy-aware DataSource，补真实 close 字节统计。
5. `:player` / `:app` 修复 `PrefetchCoordinator` 当前质量与预加载 stable key，并跑完整验证。

每一步都应先补或更新对应单测，再写实现。第 4 步影响 Media3 数据源，必须重点 review 资源关闭、重复 close、registry miss 兜底和 no-store bypass。

## 8. 风险与处理

- **旧 SimpleCache URL key 遗留**：本设计不主动迁移旧 URL key。修复后新播放会写 stable key，旧 URL key 由 LRU 自然淘汰。理由是本次目标是快速修复流量异常，避免额外引入迁移风险。
- **上游同一歌曲同一音质内容变化**：stable key 会复用旧字节。已有 stale URL / 播放失败刷新路径应继续按 `${platform}:${id}:${quality}` evict；实现阶段必须确认 `SimpleCacheHolder.evictForKey` 使用同一 key。
- **`no-store` 插件的预加载**：直接跳过 warmHead，避免把不可存储源写入字节缓存。
- **registry miss 仍可能发生**：外部/历史 MediaItem 或异常路径可能未注册。允许播放但保留 miss 日志，作为后续诊断信号。
- **字节统计精度**：v1 接受按 `totalRead - cachedBytes` 估算 upstream bytes；如果 Media3 内部行为导致偏差，再在后续独立任务中引入更细粒度 DataSource 包装。

## 9. 完成定义

代码实现完成后必须满足：

- 用户选择 `SUPER` 音质时，同一首在线歌曲重复播放能复用字节缓存。
- `no-cache` 在线仍 fresh resolve，不读取解析结果缓存，但会写入解析结果缓存用于离线兜底。
- `no-store` 不写解析结果缓存，也不写 SimpleCache 字节缓存。
- 预加载写入的前 512 KB 与正式播放使用同一个 stable key。
- Logan 中能用 `media_cache_key_registered` 与 `media3_datasource_close` 判断缓存是否真正生效。
- `:core`、`:plugin`、`:player` 单测、`:app:assembleDebug`、dev harness check 均通过。

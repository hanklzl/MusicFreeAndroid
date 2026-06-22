# 在线歌曲字节缓存有效性设计

> 文档状态：当前规范（Player / Plugin / Data / Logging 设计）
> 适用范围：在线播放字节缓存有效性判定、`no-cache` 歌曲复用、插件解析超时兜底、Logan 缓存诊断
> 直接执行：是（作为 implementation plan 输入）
> 最后校验：2026-06-23
> 关联 dev-harness：[player/rules](../../dev-harness/player/rules.md)、[plugin/rules](../../dev-harness/plugin/rules.md)、[test/rules](../../dev-harness/test/rules.md)、[cache-and-logs](../../dev-harness/player/cache-and-logs.md)
> 上游参考：[歌曲缓存系统优化设计](2026-05-20-song-cache-optimization-design.md)、[在线歌曲缓存稳定 key 与 no-store 治理设计](2026-05-28-media-cache-stable-key-design.md)

## 1. 背景

2026-06-22 反馈包排查确认，`元力QQ` 多次播放卡顿主要来自插件 `getMediaSource` 超时重试，而不是 Media3 播放失败自动跳歌。当天在线播放中，`no-cache` 源不会读取 Room 解析结果缓存，因此每次在线播放仍先调用插件；即使 Media3 `SimpleCache` 已经有部分或完整字节，播放启动仍可能被插件解析阻塞。

当前实现已经区分：

- `cache`：在线可读解析结果缓存，成功解析后可写解析结果缓存和字节缓存。
- `no-cache`：在线不读解析结果缓存，离线可读；成功解析后可写解析结果缓存和字节缓存。
- `no-store`：不读不写解析结果缓存，也不写字节缓存。

RN 原版的语义同样是 `no-cache` 在线 fresh resolve、非 `no-store` 仍写解析结果缓存；参考路径为 `../../../../MusicFree/src/core/pluginManager/plugin.ts` 与 `../../../../MusicFree/src/core/mediaCache.ts`。Android 原生额外有 Media3 字节缓存能力，因此本设计在不破坏 RN `cacheControl` 语义的前提下，把“已播放过的完整字节缓存”提升为可复用的播放入口。

用户达成的缓存共识：

> 只要该歌曲播放过，就应该可以缓存，除非缓存被淘汰或失效。

这里的“可以缓存”指字节缓存。解析结果缓存仍尊重插件 `cacheControl`：在线 `no-cache` 不直接当作常规 Room 命中，但当本地已证明存在完整且可播放的字节缓存时，可以用历史解析结果作为“打开本地字节缓存的索引”来避开插件解析超时。

### 1.1 RN 原版缓存有效性口径

原版没有业务层“字节缓存有效性”检测；它的有效性判断集中在解析结果元数据和本地文件存在性上。

#### 解析结果缓存

`../../../../MusicFree/src/core/mediaCache.ts` 的 `MediaCache` 是 MMKV 元数据缓存：

- key 来自 `getMediaUniqueKey(mediaItem)`，即歌曲身份。
- value 是序列化后的 media item，读取时只做 `safeParse`。
- 没有 TTL、过期时间、content length、MD5、SHA1、span 完整性或可播放状态。
- 最多 800 条；达到上限时删除 `getAllKeys()` 返回的前 400 条，并顺手清理关联本地歌词文件。
- 单曲“清除插件缓存”只删除这条 MMKV 元数据。

因此，原版对 source 缓存的“有效”定义很窄：

```text
有 media cache 行
  and source[quality].url 存在
  and cacheControl 允许当前网络状态读取
```

#### `cacheControl` gate

`../../../../MusicFree/src/core/pluginManager/plugin.ts` 的播放解析顺序是：

1. 本地路径存在则直接播放本地文件。
2. 读取 `MediaCache.getMediaCache(musicItem)`。
3. 只有 `cacheControl == "cache"`，或 `cacheControl == "no-cache" && Network.isOffline` 时，才使用 cached source。
4. 在线 `no-cache` 继续调用插件 `getMediaSource()`。
5. 插件解析成功后，只要不是 `no-store` 且 `notUpdateCache == false`，就把 source 写回 `MediaCache`。

也就是说，原版没有把 `no-cache` 理解成“禁止保存”；它只是在线播放时不把解析结果缓存当作 fresh source。

#### 本地文件有效性

原版本地文件只用 `exists(localPath)` 检查：

- `exists(localPath) == true`：返回 `file://` URL。
- `mediaExtra.localPath` 存在但真实文件不存在：把 `localPath` patch 为 `undefined`，然后继续走在线解析。
- 本地插件歌曲文件不存在：直接抛“本地音乐不存在”。

这里也没有 size/hash 校验；存在性就是有效性。

#### 音频字节缓存

原版通过 `react-native-track-player` 初始化 `maxCacheSize`，设置页统计和删除 `CachesDirectoryPath/TrackPlayer` 目录。业务层没有：

- 查询播放器缓存 key。
- 判断某首歌是否完整缓存。
- 区分 partial / complete。
- 在插件超时时用已完整缓存的音频兜底。
- 对音频字节缓存做 MD5 / SHA1 校验。

Android 本设计因此只参考原版的 policy 边界和歌曲身份语义；字节级 `None / Partial / Complete / PlayableVerified / StaleOrInvalid` 是 Android 原生 Media3 能力之上的增强。

## 2. 目标与非目标

### 2.1 目标

1. 明确定义字节缓存有效性状态，避免把 512 KB 预取、半首歌缓存、完整缓存和已验证可播放缓存混为一谈。
2. 对已完整播放且已验证的 `platform/id/quality`，再次播放时可优先使用本地字节缓存，不被 `no-cache` 在线 fresh resolve 阻塞。
3. 当插件解析超时或失败时，若本地存在 `PlayableVerified` 字节缓存，应可兜底播放。
4. 当远端 URL 失效、HTTP 状态异常、container 解析失败或坏字节缓存导致播放失败时，必须标记失效并清理对应 key。
5. `no-store` 继续作为强边界：不写、不读、不用字节缓存兜底。
6. Logan 能直接回答：本次播放有没有完整缓存、是否用验证缓存启动、如果没用原因是什么。
7. 与 RN 原版保持一致：`no-cache` 在线不作为常规解析结果缓存命中，但非 `no-store` 的解析结果仍可写入，用于离线和 Android verified byte-cache 专用路径。

### 2.2 非目标

- 不把所有在线 `no-cache` 改成常规读取 Room 解析结果缓存；这会改变插件语义。
- 不在移动网络下自动后台下载整首歌。预取仍保持轻量，完整缓存由真实播放自然产生。
- 不把 MD5 / SHA1 作为主校验依据。插件协议没有可信 expected hash，事后计算只能证明“本地现在这些字节自洽”，不能证明它们就是正确歌曲。
- 不调整用户选择的播放音质、音质回退策略或移动网络播放策略。
- 不新增缓存 dashboard UI；第一阶段以日志和测试作为验收入口。
- 不照搬 RN 的“存在即有效”作为 Android 字节缓存模型；Android 需要利用 SimpleCache span 和播放成功信号补足完整性证明。

## 3. 术语与状态模型

### 3.1 Key

字节缓存 key 以歌曲身份和音质为准：

```text
ByteCacheKey = "${platform}:${id}:${quality.name.lowercase()}"
```

URL 只能作为拉流入口和 source fingerprint 的输入，不能作为字节缓存身份。签名 URL、token、host 参数变化不应改变同一首歌同一音质的字节缓存 key。

### 3.2 状态

字节缓存状态按可信度递增：

| 状态 | 含义 | 可直接启动播放 |
|---|---|---:|
| `None` | SimpleCache 没有该 key 的可用 span，或状态记录已被淘汰 | 否 |
| `Partial` | 有缓存 span，但不足以覆盖完整内容；典型来源是预取头部或中途切歌 | 否 |
| `Complete` | SimpleCache span 连续覆盖 `0 until contentLength`，且 `contentLength > 0` | 谨慎；优先升级为验证态 |
| `PlayableVerified` | 在 `Complete` 基础上，曾经自然播放到结束或通过等价播放成功信号确认 | 是 |
| `StaleOrInvalid` | 该 key 最近因坏源、坏缓存或远端失效被标记不可用 | 否，必须重新解析或重新拉流 |

状态降级规则：

- LRU 淘汰、手动清理、配额缩小、schema 迁移：删除状态记录，回到 `None`。
- HTTP bad status、invalid content type、Media3 container parse failure、坏字节缓存读失败：标记 `StaleOrInvalid`，同时驱逐 SimpleCache key 和相关 Room source。
- 同一 `platform/id/quality` fresh resolve 得到新 URL 但本地 `PlayableVerified` 仍可读：不降级。URL 变化只更新 fingerprint，不代表本地字节失效。

### 3.3 Fingerprint

`sourceFingerprint` 用于解释“本次验证来自哪次解析结果”，不作为 cache key：

```text
sourceFingerprint = sha1(
  normalizedUrlHost + "\n" +
  urlHash + "\n" +
  headersHash + "\n" +
  contentLength + "\n" +
  resolvedQuality
)
```

日志中只记录 `host`、`urlHash`、`headersHash`、`contentLength`、`resolvedQuality`，不记录完整 URL 和 header 明文。

## 4. 有效性校验模型

### 4.1 主校验：size + contiguous spans

完整字节缓存的主判定依据是 Media3 `SimpleCache` 的 span 覆盖情况：

1. 读取 `cache.getCachedSpans(ByteCacheKey)`。
2. 按 `position` 排序，确认第一个 span 从 0 开始。
3. 连续合并 span，若出现洞则为 `Partial`。
4. 获取可信 `contentLength`：
   - 优先使用 SimpleCache / Media3 metadata 中的 content length。
   - 其次使用最近一次 DataSource open/close 得到的 total bytes，并在播放成功后持久化。
5. 当连续覆盖长度 `>= contentLength` 且 `contentLength > 0`，判定为 `Complete`。

仅有 `cacheSpace > 0`、只读过头部、或 `bytesFromCache > 0` 都不能判定完整。

### 4.2 播放验证：Complete -> PlayableVerified

`Complete` 只能说明“本地有一段看似完整的字节”。它升级为 `PlayableVerified` 需要播放层成功信号：

- 播放自然进入 ended，且当前 item 没有 Media3 fatal error。
- 本次 DataSource 生命周期没有 `media3_datasource_error`。
- 当前 key 的完整性检查仍为 `Complete`。

满足条件后写入：

```text
status = PlayableVerified
verifiedAt = now
validationMethod = playback_completed
contentLength = inspectedContentLength
cachedBytes = inspectedCachedBytes
sourceFingerprint = latestFingerprint
```

暂停、seek、中途切歌、播放到一半应用被杀，都不会升级为 `PlayableVerified`。

### 4.3 Hash 的位置

MD5 / SHA1 可以作为诊断或磁盘腐化探测，但不能作为首要有效性模型：

- 没有插件或服务器提供的 expected hash，无法证明“这首歌应该等于这个 hash”。
- 首次缓存如果就是错误内容，事后 hash 只会稳定记录错误内容。
- 对大音频计算 hash 会增加 IO 和耗电，不应在播放热路径同步执行。

可选扩展：

- 空闲时对 `PlayableVerified` 文件计算 `sha1`，记录为 `localContentHash`。
- 下次检查时若 hash 变化，标记 `StaleOrInvalid(reason=local_hash_changed)`。
- 该 hash 只用于发现本地磁盘内容变化，不用于证明远端内容正确。

## 5. 播放解析策略

### 5.1 常规路径保持 RN 语义

在线 `no-cache` 的常规解析仍然 fresh resolve：

```text
play(item, quality)
  ├─ local file short-circuit
  ├─ cacheControl == cache ? read Room source : skip
  └─ call plugin getMediaSource()
```

插件成功返回后，只要不是 `no-store`，继续写 Room source，并允许 Media3 byte cache 写入。

### 5.2 Verified byte-cache 快路径

播放开始前新增一个极窄的快路径：

```text
if policy != no-store
  and ByteCacheStatusStore(platform,id,quality) == PlayableVerified
  and ByteCacheInspector(platform,id,quality) == Complete
  and Room source has matching quality URL/headers
then use cached source as byte-cache index
```

该路径的关键约束：

- 它不是“在线 no-cache 常规读取 Room source”；只有 `PlayableVerified + Complete` 同时成立才允许。
- Room source 在这里仅用于构造 Media3 `MediaItem`、headers 和 userAgent。实际内容命中由 stable key 指向 SimpleCache。
- DataSource open 后若发现 `bytesFromUpstream > 0`，说明缓存并非完全本地命中，应记录 `byte_cache_verified_network_fallback`，并按错误情况决定是否降级。
- 如果只有 `Complete` 没有 `PlayableVerified`，不走快路径；仍 fresh resolve，等一次自然播放完成后升级。

### 5.3 插件失败兜底

常规 fresh resolve 超时或失败时，可尝试 verified byte-cache fallback：

```text
plugin resolve failed/timeout
  ├─ if PlayableVerified + Complete + cached source exists
  │    → playback_resolve_fallback_byte_cache
  └─ else
       → keep current failure behavior
```

这解决“本地明明已播放过完整歌曲，但插件临时超时导致卡住”的场景。

### 5.4 no-store 强边界

`no-store` 必须满足：

- 不写 Room source。
- 不写 SimpleCache。
- 不写 `ByteCacheStatusStore`。
- 不使用 verified fallback。
- 仅允许为 headers / userAgent 注入注册临时 URL entry，不允许注册 byte-cache key。

## 6. 持久化与模块边界

新增 core contract，避免播放器直接依赖 Room 细节：

```kotlin
interface ByteCacheStatusStore {
    suspend fun get(key: ByteCacheKey): ByteCacheStatus?
    suspend fun upsert(status: ByteCacheStatus)
    suspend fun markInvalid(key: ByteCacheKey, reason: ByteCacheInvalidReason, updatedAt: Long)
    suspend fun delete(key: ByteCacheKey)
    suspend fun deleteBySong(platform: String, id: String)
}
```

实现放在 `:data`，使用 Room 新表 `byte_cache_status`。播放器只依赖 `:core` contract，由 app/Hilt 绑定 `:data` 实现。

表字段建议：

| 字段 | 说明 |
|---|---|
| `platform` | 插件平台 |
| `music_id` | 歌曲 id |
| `quality` | `PlayQuality` wire name |
| `status` | `partial` / `complete` / `playable_verified` / `stale_or_invalid` |
| `cached_bytes` | 最近检查到的连续缓存字节数 |
| `content_length` | 可信总长度，未知为 null |
| `validation_method` | `span_inspection` / `playback_completed` / `manual_evict` / `stale_failure` |
| `source_fingerprint` | 解析结果 fingerprint |
| `invalid_reason` | 失效原因 |
| `verified_at` | 最近验证时间 |
| `updated_at` | 状态更新时间 |

主键：`(platform, music_id, quality)`。

Room schema 变更必须按仓库数据库迁移规则升级版本、生成 schema JSON，并补迁移测试。

## 7. 日志事件

新增或调整事件：

| 事件 | Category | 关键字段 |
|---|---|---|
| `byte_cache_inspect` | PLAYER | `sid`, `platform`, `itemId`, `quality`, `status`, `cachedBytes`, `contentLength`, `holeCount` |
| `byte_cache_status_write` | DATA | `platform`, `itemId`, `quality`, `status`, `validationMethod`, `cachedBytes`, `contentLength` |
| `byte_cache_verified` | PLAYER | `sid`, `platform`, `itemId`, `quality`, `contentLength`, `sourceFingerprint` |
| `playback_resolve_byte_cache_fast_path` | PLAYER | `sid`, `platform`, `itemId`, `quality`, `ageSeconds`, `sourceFingerprint` |
| `playback_resolve_fallback_byte_cache` | PLAYER | `sid`, `platform`, `itemId`, `quality`, `pluginErrorType`, `ageSeconds` |
| `byte_cache_fast_path_rejected` | PLAYER | `sid`, `platform`, `itemId`, `quality`, `reason` |
| `byte_cache_invalidated` | PLAYER/DATA | `platform`, `itemId`, `quality`, `reason`, `freedBytes` |
| `prefetch_head_success` | PLAYER | `sid`, `platform`, `itemId`, `quality`, `bytesRead` |
| `prefetch_head_skipped_verified` | PLAYER | `platform`, `itemId`, `quality` |

已有 `prefetch_success` 语义应收窄或迁移为 `prefetch_head_success`，避免日志读者误以为已缓存完整歌曲。

## 8. 验收场景

1. 同一首 `no-cache` 在线歌曲完整播放到结束后，再次播放时，即使插件解析慢，也能通过 `playback_resolve_byte_cache_fast_path` 或 `playback_resolve_fallback_byte_cache` 启动。
2. 只有预取 512 KB 的下一首不能进入 verified 快路径，日志应为 `byte_cache_fast_path_rejected{reason=partial}`。
3. 插件返回 `no-store` 的歌曲不产生 `byte_cache_status_write`，也不使用 fallback。
4. 手动清理单曲缓存后，对应 `byte_cache_status` 和 SimpleCache key 同步删除。
5. HTTP bad status / invalid content type / container parse failure 后，对应 key 被 `byte_cache_invalidated` 标记并驱逐，下次必须 fresh resolve。
6. 与 RN 原版对齐验证：
   - 在线 `no-cache` 常规路径仍调用插件。
   - 离线 `no-cache` 仍可读取历史 source。
   - 非 `no-store` 解析成功后仍写历史 source。
   - `no-store` 不写 source，也不写字节有效性状态。
7. 反馈包排查 recipe 能区分：
   - source cache miss：`no-cache` 在线策略导致；
   - byte cache partial：只预取或播放未完整；
   - byte cache verified：完整播放后可本地复用；
   - invalidated：被错误或清理淘汰。

## 9. 与当前实现的差距

当前实现已经满足：

- stable byte cache key 使用 `platform/id/quality`。
- `no-cache` 在线不读 Room source，但允许写 Room source 和 SimpleCache。
- `no-store` 禁止字节缓存写入。
- stale URL / bad remote content 的驱逐链路已有基础。

待补齐：

1. 没有 `ByteCacheInspector`，无法证明 SimpleCache 是 partial 还是 complete。
2. 没有持久化 `PlayableVerified` 状态，应用重启后不知道某首歌是否完整可播放。
3. 播放启动总是先经过插件解析，verified 字节缓存无法绕过 `no-cache` 插件超时。
4. 预取日志容易被误读为完整缓存成功。
5. 缓存状态、失效原因、fallback 原因缺少结构化事件。

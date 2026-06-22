# 歌曲缓存复用 & 日志排查手册

> 文档状态：当前规范（Dev Harness — Player / Cache & Logs）
> 适用范围：歌曲缓存命中排查、Logan 日志解密、三层缓存策略说明
> 直接执行：是
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [AGENTS](../../../AGENTS.md)
> 设计来源：v1.2.5 cache-reuse-logs 任务（plan 文件已归档于 `~/.claude/plans/purring-inventing-zebra.md`）
> 最后校验：2026-06-23

---

## 缓存复用策略

### 三层架构图

```
用户点歌
  │
  ▼
PlayerController.playItem / playQueue
  │
  ▼
resolvePlayableItem()
  ├─ item.isLocalPlaybackSource()? ──YES──► 直接返回
  │    (platform=="local" 或 url 以 file:// / content:// 开头)
  │
  └─ NO
      │
      ▼
  mediaSourceResolver.resolve()
      │
      ▼
  PluginMediaSourceService.doResolve()
      │
      ├─ [Layer 1] resolveLocal()
      │    反查 musicDao.getById() 兜底注入 localPath（v1.2.5 bug fix）
      │    LocalFileProbe.isReadable == true
      │    → 命中  event: cache_hit{source=local}
      │    → 失效  event: local_short_circuit_fallback → fallthrough L2
      │
      ├─ [Layer 2] Room media_cache
      │    仅当插件声明 cacheControl=="cache"，
      │    或"offline 模式 + no-cache"
      │    → 命中  event: cache_hit{source=repo_db}
      │    → 未命中 → fallthrough L3
      │
      └─ [Layer 3] 插件 JS getMediaSource()
           无缓存，每次调用
           event: resolve_plugin_call_start / resolve_plugin_call_end
               │
               ▼
           PlayerController.playResolvedItem
               │
               ▼
           ExoPlayer.setMediaItem
               │
               ▼
           HeaderInjectingDataSourceFactory.createDataSource()
               │
               ▼
           CacheDataSource（SimpleCache）
               cache key = "${platform}:${id}:${quality}"
               （TrackHeaderRegistry 未注册时退化为 URL）
```

### 各层命中条件

| 层级 | 命中前提 | 命中事件 |
|------|----------|----------|
| Layer 1 本地文件 | `localPath` 非空且文件可读（v1.2.5 起：path 为空时自动反查 DB） | `cache_hit{source=local}` |
| Layer 2 Room DB | 插件返回 `cacheControl=="cache"`，或离线模式 | `cache_hit{source=repo_db}` |
| Layer 3 SimpleCache | TrackHeaderRegistry 在 `setMediaItem` 前注册了 `url → cacheKey`；签名 URL 变化会导致 key 退化、字节永远无法复用 | ExoPlayer 内部命中，无独立事件 |

**优先级**：Layer 1 > Layer 2 > Layer 3。

### localPath 注入路径（v1.2.5 后全覆盖）

| 播放入口 | v1.2.5 前 | v1.2.5 后 |
|----------|-----------|-----------|
| 本地音乐列表直接点击 | localPath 已在 item 中 | 不变 |
| 插件搜索结果点击已下载歌曲 | localPath 为空，走网络 | `resolveLocal()` 反查 DB 兜底注入，`resolve_local_db_lookup{found=true}` |
| 插件在线歌单点已下载歌曲 | 同上，走网络 | 同上 |

### Layer 3 字节缓存有效性（2026-06-23 起）

SimpleCache 的“存在 spans”不等于可安全播放。播放器额外维护 `byte_cache_status` Room 表，用 `ByteCacheKey = "${platform}:${id}:${quality}"` 记录字节缓存状态：

| 状态 | 含义 | 可作为播放快路径 |
|------|------|------------------|
| `None` | 没有可用 span | 否 |
| `Partial` | 有 span，但不完整或缺 contentLength | 否 |
| `Complete` | SimpleCache span 连续覆盖 contentLength | 否，仅代表文件完整 |
| `PlayableVerified` | 完整播放结束后再次检查仍为 `Complete`，可证明该缓存曾可播放 | 是 |
| `StaleOrInvalid` | URL 过期、HTTP/解析失败或坏字节缓存导致失效 | 否 |

`PlayerController` 在一次在线歌曲播放自然结束时检查 `ByteCacheInspector.inspect(key)`；只有 `cachePolicy != no-store` 且检查为 `Complete`，才写入 `PlayableVerified`。预取只会产生 partial/complete span，不会写 `PlayableVerified`。

### Verified byte-cache 快路径

`no-cache` 的常规解析语义不变：在线时不会把 Room `media_cache` 当作普通命中。但若某首歌已经是 `PlayableVerified`，播放器可以走一个窄入口复用历史 source 作为 SimpleCache index：

1. `ByteCacheStatusStore(key) == PlayableVerified`
2. `ByteCacheInspector(key) == Complete`
3. 插件 `cacheControl != no-store`
4. `PluginMediaSourceService.resolveCachedSourceForVerifiedByteCache()` 从 `media_cache` 读取历史 URL/headers/userAgent，不调用插件 JS，也不触发 `ensurePluginsLoaded()`

快路径失败后回到常规插件解析；常规解析失败或超时时，会再尝试一次 verified byte-cache fallback。若检查发现 partial、缺 contentLength 或坏缓存，会写 `StaleOrInvalid`，避免下次继续快路径。

手动清理、Room media_cache 行数/字节淘汰、SimpleCache LRU 驱逐、SimpleCache 全量清空或配额缩小时，`byte_cache_status` 记录会被删除并回到 `None`，而不是保留 `StaleOrInvalid`。

### 关键设计约束

- **插件 `cacheControl` 默认为 null → NoCache**：绝大多数插件未声明，Layer 2 默认不读不写。
- **SimpleCache cache key 的关键依赖**：`TrackHeaderRegistry` 必须在 `setMediaItem` 之前注册 `url → cacheKey`，否则 key 退化为 URL 字符串。签名 URL 每次变化则字节层永远不复用。
- **`no-store` 禁止 byte-cache 状态升级和 verified fallback**：该策略下不写 `PlayableVerified`，也不从历史 source 走 verified 快路径。

---

## 缓存淘汰策略

### Layer 1：下载文件

| 触发来源 | 描述 | 日志事件 |
|----------|------|----------|
| 用户主动删除 | feature/home `MusicItemOptionsSheet → musicRepository.removeFromLocalLibrary` | — |
| 文件失效自动清 | `resolveLocal` 检测 `isReadable==false`，调 `removeFromLocalLibrary` | `local_short_circuit_fallback` |
| 定期扫描 | 无 | — |

### Layer 2：Room media_cache

| 触发来源 | 阈值 | 日志事件 | 关键字段 |
|----------|------|----------|----------|
| 行数上限 | 800 行 → `pruneOldest` 删一半 | `media_cache_prune` | `totalBefore`, `totalAfter`, `trigger=row_cap` |
| 字节配额 | 用户配置的 1/10 → `pruneToLimit` | `media_cache_trim` | `trigger=byte_cap`, `evictedItems[]`（前 10 条）|
| 内存 LRU | LRU 200 自动驱逐（v1.2.5 新增） | `media_cache_memory_evict` ★ | — |
| 主动删除 | `deleteEntry` / `deleteItem` / `deleteByPlatform` / `clearAll`；Settings → 缓存管理按 `(platform,id)` 清理指定歌曲时走 `deleteItem` | `media_cache_delete_entry` ★ | `trigger`（stale_url / manual / 等）|

主动删除 `deleteEntry` / `deleteItem` / `deleteByPlatform` / `clearAll`，以及 row-cap / byte-cap 淘汰，会同步清理 `byte_cache_status`；stale URL / HTTP / container parse failure 路径会写 `StaleOrInvalid`，避免状态表继续显示 `PlayableVerified`。

### Layer 3：ExoPlayer SimpleCache

| 触发来源 | 描述 | 日志事件 | 关键字段 |
|----------|------|----------|----------|
| LRU 驱逐（PinningCacheEvictor） | 字节不足时最近最少使用（v1.2.5 新增） | `media_cache_lru_evict` ★ | `evictedCount`, `evictedBytes`, `evictedKeys[10]`, `bytesNeeded`, `pinnedSizeBytes`, `maxBytes`, `pinOverflowSuspended`, `durationMs` |
| 用户手动清空 | Settings → SettingsCacheCleaner；Settings → 缓存管理按 `(platform,id)` 清理指定歌曲时也会同步驱逐对应 SimpleCache key | `media_cache_evict{scope=manual}` | — |
| 配额上限调小 | `updateMaxBytes` | `media_cache_evict{scope=byte_cap}` + `media_cache_max_bytes_changed` ★ | `newBytes`, `previousUsed`, `freedBytes` |
| 按 key 驱逐（stale_url 语义） | `SimpleCacheHolder.evictForKey`；覆盖 stale URL、HTTP bad status、invalid content type、远端 container parse failure / bad byte-cache | `media_cache_evict{scope=stale_url}` + `media_cache_evict_for_key` ★ | `platform`, `id`, `quality`, `removedQualityCount`, `freedBytes`, `triggerSource` |
| 低磁盘空间 | 初始化时检测 | `media_cache_lowspace` | — |
| 启动 schema 迁移 | SimpleCache 格式升级 | `media_cache_schema_migration` | — |
| 初始化配置记录 | 冷启动（v1.2.5 新增） | `media_cache_init` ★ | `configuredBytes`, `effectiveCapBytes`, `availableBytes`, `storageScope`, `cacheDirPath` |

SimpleCache span 被移除时，`PinningCacheEvictor` 会按 `${platform}:${id}:${quality}` 解析 key 并删除对应 `byte_cache_status`；全量清空或配额缩小时会删除全部状态。

---

## 埋点字典

★ 标注 v1.2.5 新增或增强的事件。

### LogCategory.PLAYER

| 事件 | 版本 | 关键字段 |
|------|------|----------|
| `cache_hit` | 已有 | `source`（local / repo_db）, `sid`, `platform`, `id`, `quality` |
| `cache_miss` | 已有 | `sid`, `platform`, `id`, `quality`, `cacheControl` |
| `playback_queue_item_updated` | v1.2.17 新增 | `operation=play_item`, `platform`, `itemId`, `queueIndex`, `oldHasLocalPath`, `newHasLocalPath`, `oldUrlScheme`, `newUrlScheme` |
| `playback_media_item_prepare` | v1.2.17 新增 | `platform`, `itemId`, `mediaUriScheme`, `mediaUriHost`, `mediaUriHash`, `mediaUriLocalSource`, `hasLocalPath`, `urlScheme` |
| `resolve_local_db_lookup` | ★ v1.2.5 新增 | `sid`, `platform`, `musicItemId`, `found`（true=被 bug fix 救了） |
| `local_short_circuit_fallback` | ★ v1.2.5 增强 | `localPathTail`, `isContentUri` |
| `resolve_plugin_call_start` | 已有 | `sid`, `pluginName` |
| `resolve_plugin_call_end` | 已有 | `sid`, `durationMs`, `returnedQuality`, `hasUrl`, `hasHeaders`, `urlHash` |
| `plugin_get_media_source_cache_hit` | ★ v1.2.5 增强 | `sid`（新增）, `platform`, `musicItemId`, `quality`, `cacheControl` |
| `plugin_get_media_source_cache_write` | ★ v1.2.5 增强 | `sid`（新增）, `platform`, `musicItemId`, `quality`, `cacheControl` |
| `resolve_local_check` | 已有 | `sid`, `hasLocalPath`, `localPathReadable` |
| `resolve_cache_lookup` | 已有 | `sid`, `layer`（repo_db）, `hit`, `qualityMatched`, `ageSeconds` |
| `media3_datasource_open` | 已有 | `sid`, `cacheKey`, `cacheHit`, `bytesFromCache`, `bytesFromUpstream` |
| `media3_datasource_error` | 已有 | `sid`, `errorCode`, `httpStatus`, `position`, `cacheKey`, `retryCount` |
| `play_session_start` | 已有 | `sid`, `platform`, `id`, `requestedQuality`, `networkType`, `isOnline` |
| `media_cache_lru_evict` | ★ v1.2.5 新增 | `evictedCount`, `evictedBytes`, `evictedKeys[10]`, `bytesNeeded`, `pinnedSizeBytes`, `maxBytes`, `pinOverflowSuspended`, `durationMs` |
| `media_cache_evict` | 已有 | `scope`（manual / byte_cap / stale_url；stale_url 当前也覆盖坏远端字节缓存刷新） |
| `media_cache_evict_for_key` | ★ v1.2.5 新增 | `platform`, `id`, `quality`, `removedQualityCount`, `freedBytes`, `triggerSource`（stale_url 当前也覆盖坏远端字节缓存刷新） |
| `media_cache_init` | ★ v1.2.5 新增 | `configuredBytes`, `effectiveCapBytes`, `availableBytes`, `storageScope`（internal/external）, `cacheDirPath` |
| `media_cache_max_bytes_changed` | ★ v1.2.5 新增 | `newBytes`, `previousUsed`, `freedBytes` |
| `media_cache_schema_migration` | 已有 | — |
| `media_cache_lowspace` | 已有 | `availableBytes`, `requestedBytes` |
| `byte_cache_inspect` | 2026-06-23 新增 | `platform`, `itemId`, `quality`, `cacheKey`, `status`, `cachedBytes`, `contentLength`, `holeCount` |
| `byte_cache_verified` | 2026-06-23 新增 | `sid`, `platform`, `musicItemId`, `quality`, `cachedBytes`, `contentLength`, `holeCount` |
| `byte_cache_fast_path_hit` | 2026-06-23 新增 | `sid`, `platform`, `itemId`, `quality`, `cachedBytes`, `contentLength`, `reason`（fast_path / fallback） |
| `playback_resolve_fallback_byte_cache` | 2026-06-23 新增 | 同 `byte_cache_fast_path_hit`，表示常规解析失败后的 fallback |
| `byte_cache_fast_path_rejected` | 2026-06-23 新增 | `sid`, `platform`, `musicItemId`, `quality`, `reason`（status_missing / partial / no_content_length / no_store / cached_source_missing 等） |
| `byte_cache_invalidated` | 2026-06-23 新增 | `platform`, `itemId`, `quality`, `reason`, `trigger` |
| `prefetch_head_success` | 2026-06-23 调整 | `platform`, `itemId`, `quality`, `bytesRead` |
| `prefetch_head_skipped_verified` | 2026-06-23 新增 | `platform`, `itemId`, `quality` |

### LogCategory.DATA

| 事件 | 版本 | 关键字段 |
|------|------|----------|
| `media_cache_prune` | ★ v1.2.5 增强 | `totalBefore`, `totalAfter`, `trigger`（row_cap）|
| `media_cache_trim` | ★ v1.2.5 增强 | `trigger`（byte_cap）, `evictedItems[]`（前 10 条 id+platform）|
| `media_cache_memory_evict` | ★ v1.2.5 新增 | `platform`, `id`，Room repo 内存 LRU 200 驱逐 |
| `media_cache_delete_entry` | ★ v1.2.5 新增 | `platform`, `id`, `trigger`（stale_url / manual / 等）|
| `clear_local_playback_association` | v1.2.17 新增 | `platform`, `itemId`, `changed`；Settings 指定歌曲清理时用于解除插件歌曲下载 / 本地播放关联 |
| `settings_song_cache_clear` | v1.2.17 新增 | `platform`, `itemId`, `localAssociationCleared`, `durationMs`, `result` |
| `byte_cache_status_write` | 2026-06-23 新增 | `platform`, `musicItemId`, `quality`, `status`, `validationMethod`, `cachedBytes`, `contentLength`, `invalidReason` |

---

## 排查 Recipe

以下 jq 命令假设已完成 Logan 解密，解密输出为 `tools/logan/out/decoded/` 下的 `.txt` 文件，每行一个 JSON。

**重要：所有自定义字段（sid / platform / id / quality / source / found / cacheControl 等）都嵌套在 `.fields.` 下；顶层只有 `level / category / event / timestamp / sessionId / fields`。**

将所有解密文件合并到一个流：

```bash
cat tools/logan/out/decoded/*.txt 2>/dev/null
```

### Recipe 1：用户说"这首歌每次都重新下载"

目标：找出某次播放（sid）的完整缓存链路，确认是否命中 Layer 1/2/3。

```bash
SID="ps_xxxxxx"

# 按 sid 拉出全部相关事件，按时间排序
cat tools/logan/out/decoded/*.txt \
  | jq -c --arg sid "$SID" 'select(.fields.sid == $sid)' \
  | jq -r '[.timestamp, .event, .fields.source, .fields.cacheControl, .fields.found, .fields.quality] | @tsv'
```

**排查要点**：

- `cache_hit` 中 `source` 是什么？
  - `local` → Layer 1 命中（本地文件），但还在重新下载则说明 ExoPlayer 层拿到的 URL 仍是网络地址；检查 `resolvePlayableItem` 是否真正把 `file://` URL 传给了 ExoPlayer。
  - `repo_db` → Layer 2 命中，检查是否 URL 仍是签名 URL 导致 SimpleCache miss。
  - 没有任何 `cache_hit` → 插件未声明 `cacheControl=="cache"`，Layer 2 默认 NoCache，每次都调插件。
- `media3_datasource_open` 的 `cacheKey` 是不是 URL 字符串？如果是说明 `TrackHeaderRegistry` 未在 `setMediaItem` 之前注册（没 headers/userAgent 的源会走这个退化路径），字节层 cache key 每次随签名 URL 变化，永远不复用。

```bash
# 确认 cacheControl
cat tools/logan/out/decoded/*.txt \
  | jq -c --arg sid "$SID" 'select(.fields.sid == $sid and .event == "cache_miss")' \
  | jq '.fields | {reason, platform, musicItemId, quality}'
```

### Recipe 2："已下载但还在走网络"

目标：确认 v1.2.5 的 `resolveLocal` DB 反查 bug fix 是否生效。

```bash
PLATFORM="your_platform"
MUSIC_ID="your_music_id"

# 查 resolve_local_db_lookup，found=true 表示本次 bug fix 救了这次播放
cat tools/logan/out/decoded/*.txt \
  | jq -c --arg p "$PLATFORM" --arg id "$MUSIC_ID" \
      'select(.event == "resolve_local_db_lookup" and .fields.platform == $p and .fields.musicItemId == $id)'
```

**判断**：

- `found=true` 出现 → bug fix 已生效，本次 localPath 通过 DB 反查注入。
- `found=false` 或事件完全不出现 → DB 中无此歌曲记录（未真正下载完成），或本机版本早于 v1.2.5。

```bash
# 同时确认是否有 local_short_circuit_fallback（文件失效情形）
cat tools/logan/out/decoded/*.txt \
  | jq -c --arg p "$PLATFORM" 'select(.event == "local_short_circuit_fallback" and .fields.platform == $p)'
```

### Recipe 3："已经完整播放过，但下次没有用缓存"

目标：确认 byte-cache 是否升级为 `PlayableVerified`，以及快路径为什么被拒绝。

```bash
PLATFORM="your_platform"
MUSIC_ID="your_music_id"

# 看完整播放后是否写入 PlayableVerified
cat tools/logan/out/decoded/*.txt \
  | jq -c --arg p "$PLATFORM" --arg id "$MUSIC_ID" \
      'select(.event == "byte_cache_status_write" and .fields.platform == $p and .fields.musicItemId == $id)'

# 看播放时是否走了快路径 / fallback
cat tools/logan/out/decoded/*.txt \
  | jq -c --arg p "$PLATFORM" --arg id "$MUSIC_ID" \
      'select((.event == "byte_cache_fast_path_hit" or .event == "playback_resolve_fallback_byte_cache") and .fields.platform == $p and (.fields.itemId == $id or .fields.musicItemId == $id))'

# 看快路径拒绝原因
cat tools/logan/out/decoded/*.txt \
  | jq -c --arg p "$PLATFORM" --arg id "$MUSIC_ID" \
      'select(.event == "byte_cache_fast_path_rejected" and .fields.platform == $p and (.fields.itemId == $id or .fields.musicItemId == $id)) | .fields'
```

**判断**：

- `byte_cache_status_write.status=playable_verified` 出现 → 完整播放验证已写入。
- `byte_cache_fast_path_rejected.reason=partial/no_content_length` → 只有预取或半首歌缓存，不能作为播放快路径。
- `reason=no_store` → 插件声明禁止缓存，不能使用 verified fallback。
- `reason=cached_source_missing` → byte-cache 还在，但 Room `media_cache` 的历史 source 已被清理，无法构造 MediaItem/headers。

### Recipe 4："突然缓存全没了"

目标：找出缓存被清空或大幅驱逐的时间点与原因。

```bash
# 搜集所有缓存驱逐 / 变更事件
cat tools/logan/out/decoded/*.txt \
  | jq -c 'select(.event | test(
      "media_cache_lru_evict|media_cache_evict|media_cache_max_bytes_changed|media_cache_prune|media_cache_trim"
    ))' \
  | jq -r '[.timestamp, .event, .fields.scope, .fields.evictedCount, .fields.evictedBytes, .fields.newBytes, .fields.totalBefore, .fields.totalAfter] | @tsv' \
  | sort
```

**排查要点**：

- `media_cache_evict{scope=manual}` → 用户在 Settings 手动清空缓存。
- `media_cache_lru_evict` 高频 + `evictedBytes` 大 → PinningCacheEvictor 频繁 LRU 驱逐，检查 `maxBytes` 是否被设得过小。
- `media_cache_max_bytes_changed{newBytes 远小于 previousUsed}` → 配额被大幅调小，触发批量驱逐。
- `media_cache_prune{trigger=row_cap}` 或 `media_cache_trim{trigger=byte_cap}` → Room Layer 2 因行数/字节超限清理。

### Recipe 4："重启后缓存丢了"

目标：确认 SimpleCache 在重启后是否因 schema 迁移或存储路径切换丢失数据。

```bash
# 查初始化与迁移事件
cat tools/logan/out/decoded/*.txt \
  | jq -c 'select(.event == "media_cache_init" or .event == "media_cache_schema_migration")' \
  | jq -r '[.timestamp, .event, .fields.storageScope, .fields.cacheDirPath, .fields.configuredBytes, .fields.effectiveCapBytes, .fields.availableBytes] | @tsv' \
  | sort
```

**排查要点**：

- `media_cache_schema_migration` 出现 → SimpleCache 格式升级，升级后旧缓存文件被作废，必须重新下载。这是正常行为，但如果频繁出现说明应用版本频繁回滚。
- `media_cache_init{storageScope}` 在相邻两次启动间从 `external` 变成 `internal`（或反向）→ 存储路径切换，上一个路径的缓存对当前路径不可见，表现为"全部 miss"。检查 SD 卡是否被拔出或挂载失败。
- `effectiveCapBytes` 远小于 `configuredBytes` → 磁盘可用空间不足，实际配额被压缩；结合 `media_cache_lowspace` 事件确认。

```bash
# 检查低磁盘事件
cat tools/logan/out/decoded/*.txt \
  | jq -c 'select(.event == "media_cache_lowspace")' \
  | jq '{timestamp, availableBytes: .fields.availableBytes, configuredBytes: .fields.configuredBytes, fallbackBytes: .fields.fallbackBytes}'
```

### Recipe 5："播放中失败后自动跳下一首 / Media3 3003"

目标：区分订阅源 URL 解析失败、本地文件不可解析，以及远端 SimpleCache 已缓存坏字节。

```bash
# 先列出播放失败策略事件，errorCode=3003 表示 Media3 container parse unsupported
cat tools/logan/out/decoded/*.txt \
  | jq -c 'select(.event == "playback_failure_skip_next" or .event == "playback_failure_next_unavailable")' \
  | jq -r '[.timestamp, .event, .fields.sid, .fields.platform, .fields.itemId, .fields.errorCode, .fields.reason] | @tsv'
```

**判断**：

- 同一 sid 附近有 `media3_datasource_open{cacheHit=true, bytesFromCache>0, bytesFromUpstream=0}`，随后 3003 / sniff failure → 优先怀疑远端 SimpleCache 中已有坏字节，必须触发一次 `media_cache_evict_for_key` + `plugin_media_source_refreshed_after_failure`。
- `media3_datasource_open` 指向 `file://` 或 `content://`，并且此前 `cache_hit{source=local}` → 更可能是本地文件不可解析；不要按远端缓存刷新处理。
- 只有 `resolve_plugin_call_end{hasUrl=false}` 或 `plugin_media_source_refresh_failed{reason=no_fresh_url}`，且没有 Media3 datasource 读流证据 → 再回到插件 / 订阅源返回值排查。

```bash
SID="ps_xxxxxx"

# 查看同一个播放会话内的解析、读流、驱逐、刷新和最终失败事件
cat tools/logan/out/decoded/*.txt \
  | jq -c --arg sid "$SID" '
      select(.fields.sid == $sid)
      | select(.event | test(
          "playback_media_item_prepare|resolve_plugin_call_end|media3_datasource_open|media3_datasource_error|media_cache_evict_for_key|plugin_media_source_refreshed_after_failure|playback_failure"
        ))
    ' \
  | jq -r '[.timestamp, .event, .fields.mediaUriScheme, .fields.mediaUriHost, .fields.cacheKey, .fields.cacheHit, .fields.bytesFromCache, .fields.bytesFromUpstream, .fields.errorCode, .fields.reason] | @tsv'
```

### Recipe 6："清理后重新点歌仍播放旧 content://"

目标：确认队列中同一首歌的旧 `localPath` 是否被新的搜索 / 歌单点击结果覆盖，以及用户是否通过 Settings 指定歌曲恢复入口清理过该项。

```bash
PLATFORM="your_platform"
MUSIC_ID="your_music_id"

# 检查 playItem 是否用最新点击项刷新了队列里的旧项
cat tools/logan/out/decoded/*.txt \
  | jq -c --arg p "$PLATFORM" --arg id "$MUSIC_ID" '
      select(.event == "playback_queue_item_updated"
        and .fields.platform == $p
        and .fields.itemId == $id)
    ' \
  | jq -r '[.timestamp, .fields.queueIndex, .fields.oldHasLocalPath, .fields.oldUrlScheme, .fields.newHasLocalPath, .fields.newUrlScheme] | @tsv'

# 检查最终交给 Media3 的 URI 是否还停留在 content://
cat tools/logan/out/decoded/*.txt \
  | jq -c --arg p "$PLATFORM" --arg id "$MUSIC_ID" '
      select(.event == "playback_media_item_prepare"
        and .fields.platform == $p
        and .fields.itemId == $id)
    ' \
  | jq -r '[.timestamp, .fields.mediaUriScheme, .fields.mediaUriHost, .fields.mediaUriHash, .fields.hasLocalPath, .fields.urlScheme] | @tsv'

# 检查用户是否从 Settings → 缓存管理清理指定歌曲
cat tools/logan/out/decoded/*.txt \
  | jq -c --arg p "$PLATFORM" --arg id "$MUSIC_ID" '
      select((.event == "settings_song_cache_clear" or .event == "clear_local_playback_association")
        and .fields.platform == $p
        and .fields.itemId == $id)
    ' \
  | jq -r '[.timestamp, .event, .fields.result, .fields.localAssociationCleared, .fields.changed, .fields.durationMs] | @tsv'
```

---

## 导出 / 解密流程

### 用户侧：导出日志包

1. 打开应用 → 设置（Settings）
2. 滑到底部，点击 **"导出日志"**（FeedbackExportConfirmDialog 会先弹出隐私提示，确认后继续）
3. 系统弹出分享菜单，选择"发送给开发者"或保存到文件
4. 导出包为 `.zip`，内含：
   - `manifest.json`：文件清单 + 设备信息
   - `README-decode.md`：解密说明
   - `logan/<date-files>`：原始加密 Logan 日志文件

### 维护侧：解密

**前置条件**：需要 JDK（`javac` + `java`）。

```bash
# Debug 包：使用仓库内置 key（默认），无需设置环境变量
bash tools/logan/decode-logan.sh path/to/feedback.zip

# Release 包：必须设置正确的 AES key/IV
LOGAN_AES_KEY="<16字符key>" LOGAN_AES_IV="<16字符iv>" \
  bash tools/logan/decode-logan.sh path/to/feedback.zip
```

解密输出在 `tools/logan/out/decoded/`，每行一个 JSON。

**脚本参数**：

```
decode-logan.sh <feedback-zip-or-logan-dir> [output-dir]
```

`output-dir` 默认为 `tools/logan/out`，必须在该目录下（安全限制）。

### jq Recipe 速查

```bash
DECODED="tools/logan/out/decoded"

# 按 sid 过滤：拿出某次播放的所有事件
cat "$DECODED"/*.txt | jq -c --arg sid "$SID" 'select(.fields.sid == $sid)'

# 按 event 过滤：查看所有缓存命中事件
cat "$DECODED"/*.txt | jq -c 'select(.event == "cache_hit")'

# 按 platform 过滤：只看某插件的所有事件
cat "$DECODED"/*.txt | jq -c --arg p "bilibili" 'select(.fields.platform == $p)'

# 按 cache_miss 过滤：列出所有未命中
cat "$DECODED"/*.txt \
  | jq -c 'select(.event == "cache_miss")' \
  | jq -r '[.timestamp, .fields.platform, .fields.musicItemId, .fields.quality, .fields.reason] | @tsv'

# 查看所有 Layer 2 Room 缓存写入（插件声明了 cache）
cat "$DECODED"/*.txt | jq -c 'select(.event == "plugin_get_media_source_cache_write")'

# 查看所有 LRU 驱逐，按时间排序，确认是否频繁
cat "$DECODED"/*.txt \
  | jq -c 'select(.event == "media_cache_lru_evict")' \
  | jq -r '[.timestamp, .fields.evictedCount, .fields.evictedBytes, .fields.bytesNeeded, .fields.maxBytes] | @tsv' \
  | sort

# 查看特定平台+id 的缓存全生命周期
PLATFORM="your_platform"; ID="your_id"
cat "$DECODED"/*.txt \
  | jq -c --arg p "$PLATFORM" --arg id "$ID" \
      'select(.fields.platform == $p and (.fields.musicItemId == $id or .fields.id == $id))' \
  | jq -r '[.timestamp, .event, .fields.source, .fields.quality, .fields.found] | @tsv' \
  | sort
```

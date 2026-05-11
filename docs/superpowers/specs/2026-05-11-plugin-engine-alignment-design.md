# 插件引擎与 RN 原版对齐设计

**日期**: 2026-05-11
**状态**: 设计完成，待实施
**作者**: brainstorming 会话结论
**关联**:

- 上游参考：`../MusicFree/src/core/pluginManager/`、`../MusicFree/src/types/plugin.d.ts`
- 当前实现：`plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/`
- 历史相关：[`2026-04-14-plugin-management-design.md`](2026-04-14-plugin-management-design.md)、[`2026-04-19-quickjs-threading-fix-design.md`](2026-04-19-quickjs-threading-fix-design.md)、[`2026-05-04-test-suite-rehabilitation-design.md`](2026-05-04-test-suite-rehabilitation-design.md)
- Harness 强约束：[`docs/dev-harness/plugin/rules.md`](../../dev-harness/plugin/rules.md)、[`docs/dev-harness/test/rules.md`](../../dev-harness/test/rules.md)

## 1. 背景

`MusicFreeAndroid` 的 `:plugin` 模块已覆盖 RN 原版 14 个核心 `PluginApi` 方法的调用签名，但在持久化、本地音乐统一、运行时兜底、错误状态、生命周期等领域与 RN 存在系统性差异。差异表（见 §2）显示这些缺口会直接造成：

1. 同一首歌每次冷启动都重新解析 `getMediaSource`，体验劣于 RN。
2. 本地音乐链路与插件链路平行，封面 / 歌词 / 元数据各走一套实现。
3. 插件装载失败（版本不匹配 / 解析失败 / 缺 `platform`）全部以 `null` 静默返回，UI 无法定位原因。
4. RN 插件代码迁移到 Android 在 `axios` 默认超时、`process` / `URL` / `webdav` 全局、URL 嵌入式凭证等场景上有偶发 break。

本 spec 不只补功能，而是**一次性对齐到与 RN 行为等价（或显式声明不等价）**，让"这块还差什么"在仓库层面有可枚举的答案。

## 2. 差异盘点（RN ↔ Android）

按"功能域"列出，★ = 影响功能可用性，◆ = 影响一致性 / 质量，◇ = 边缘 / 显式 out of scope。

### 2.1 PluginApi 表层

14 个方法签名已对齐，**无功能性差异**。差异在语义周边：

| 方面 | RN | Android | 标记 |
|---|---|---|---|
| `cacheControl` 三态 | `"cache"` / `"no-cache"` / `"no-store"` 决策路径完整 | 字段被解析但未在 `PluginMediaSourceService` 生效 | ★ |
| 内部 `$` 字段（`internalSerializeKey`） | `$.localPath` 等私有字段跨调用边界 | 未建模 | ◆ |
| `resetMediaItem(...)` | 调用前 / 后清 `$`，防泄漏 | 不存在等价边界 | ◆ |
| `getMediaSource` retry "NOT RETRY" 哨兵 | 有 | 无 | ◇ |

### 2.2 缓存与持久化层

| 方面 | RN | Android | 标记 |
|---|---|---|---|
| `mediaMeta` 跨次启动缓存 | MMKV `cache.MediaCache`，~800 项 LRU | 实体已存（`MediaCacheEntity` LIMIT 800）但 **`PluginMediaSourceService` 当前不读不写**（参 `2026-05-11-stale-media-source-playback-design.md`，为修过期 URL 而禁用） | ★ |
| 播放失败 → cache eviction → 重解析 | `Plugin.getMediaSource` 在缓存命中时使用 ExoPlayer 错误事件触发 evict + retry | 缺。**本 spec 新增**；同时回溯撤销 `stale-media-source-playback` 设计中"不新增媒体缓存淘汰策略"这一非目标 | ★ |
| `MediaExtra.${pluginName}` | per-plugin 用户态：`downloaded` / `localPath` / `lyricOffset` / `associatedLrc` | 实体已存：`DownloadedTrackEntity`（downloaded/localPath） + `LyricCacheEntity.userOffsetMs` + `LyricCacheEntity.associatedMusicJson` | 已对齐 |
| 歌词文件缓存 / 用户自设 | `lrcCachePath/<nanoid>.lrc` + `localLrcPath/<platHash>/<idHash>.lrc` | 实体已存：`LyricCacheEntity.remoteRawLrc`（插件结果）+ `localRawLrc`（用户自设）+ `localTranslation` | 已对齐 |
| 卸载插件时连带清理 | `removeAllMediaExtra(pluginName)` | 缺 `deleteByPlatform` on `MediaCacheDao` / `LyricCacheDao` / `DownloadedTrackDao` | ★ |

### 2.3 本地音乐虚拟插件

| 方面 | RN | Android | 标记 |
|---|---|---|---|
| `localFilePlugin`（platform=`本地`） | 内置单例，复用 `PluginApi` | 缺；本地链路与插件链路并行 | ★ |
| 嵌入封面 / 相邻 `.lrc` 读取 | 通过插件 `getMusicInfo` / `getLyric` | 由本地仓库直读，不走 `PluginApi` | ★ |

### 2.4 运行时 / 沙箱

| 方面 | RN | Android | 标记 |
|---|---|---|---|
| `axios` 默认超时 | 2000ms | 10000ms | ◆ |
| URL 凭证 → Basic | 拦截 `user:pass@host` 转 `Authorization` | 未做 | ★ |
| `process` 全局 | 注入 `{platform, version, env}` | 缺 | ◆ |
| `URL` polyfill | 有（`react-native-url-polyfill`） | 依 QuickJS-kt 内建，未契约化 | ◆ |
| `require('webdav')` | 完整实现 | 未注册 | ◆ |
| `env.lang` | 硬编码 `"zh-CN"` | `Locale.getDefault().toLanguageTag()` | ◆ |
| `set-cookie` 数组归一化 | 有（RN 怪相） | 不需要（OkHttp 原生支持） | ◇ |
| `@react-native-cookies/cookies` | RN deprecated no-op | 不实现 | ◇ |

### 2.5 元数据 / 状态机 / 生命周期

| 方面 | RN | Android | 标记 |
|---|---|---|---|
| `PluginState` | `Initializing` / `Loading` / `Mounted` / `Error` | 二元（成功 / null） | ◆ |
| `PluginErrorReason` | `VersionNotMatch` / `CannotParse` | 缺；错误吞掉 | ★ |
| `appVersion` satisfies | `compare-versions` 闸门 | 字段被读但不生效 | ★ |
| 懒加载 (`basic.lazyLoadPlugin`) | 启动只读 metadata，方法调用才 mount | 启动全量加载 | ◆ |
| 元数据缓存（`plugin.cache`） | MMKV，存 `name/hash/supportedMethods/instance?` | 缺 | ◆ |
| Hash 冲突 dedup | 静默幂等 | 报 `DUPLICATE_PLUGIN` | ◆ |

### 2.6 已对齐项（避免误判遗漏）

- userVariables 读写 + 串行化（INC-2026-0014 已收敛）
- 插件订阅 URL 安装 / 更新（已有 `SubscriptionParser`）
- alternative plugin 解析委托（`PluginMediaSourceService` 已用）
- 插件 `enabled` / 排序持久化（DataStore）
- QuickJS 线程亲和性（INC-2026-0009 已收敛）
- 集成测试网络门控（INC-2026-0010 已收敛）

## 3. 目标 / 非目标

### 3.1 目标

- 将 §2 中所有 ★/◆ 项收敛；§2 中 ◇ 项在文档中显式声明不实现。
- 不破坏现存 incidents 与 harness rules；新引入的约束沉淀为 contract test。
- 在 Debug 构建上实现端到端运行态验收（见 §10）。

### 3.2 非目标

- 不实现 set-cookie 归一化。
- 不实现 `@react-native-cookies/cookies` 等价能力（RN 自己 deprecated）。
- 不实现订阅 URL 自动刷新（保持手动）。
- 不为 RN `cache.MediaCache` 的"满即随机半清"行为做位元复刻；改用更确定性的 LRU。
- 不扩展 `PluginErrorReason` 到 §5.1 列出 5 个以外的细分。
- 不实现 `env.lang` 的国际化（按 RN 行为锁 `"zh-CN"`）。
- 不在本次 spec 涉及 Release 签名 / R8 keep 规则调整以外的发布动作。

## 4. 架构总览

```text
新增 / 调整

:plugin
  ├─ engine/
  │   ├─ JsEngine            ↺ 注入 process / URL polyfill 探针 / __env.lang 硬编码
  │   ├─ AxiosShim           ↺ 默认 2000ms + auth URL → Basic
  │   ├─ RequireShim         ↺ 注册 webdav（getFileContents / putFileContents 最小可用）
  │   └─ WebDavShim          + 新增
  ├─ runtime/
  │   ├─ PluginState         + sealed 4 态
  │   ├─ PluginErrorReason   + 5 enum
  │   └─ PluginAppVersionGate+ semver satisfies
  ├─ manager/
  │   ├─ PluginManager       ↺ PluginEntry(state, loaded?) / lazyLoad / silent-dup
  │   └─ PluginMetadataCache + 启动只读 metadata
  ├─ local/
  │   └─ LocalFilePlugin     + Kotlin 实现的 PluginApi 适配器
  └─ cache/
      └─ MediaMetaCacheGateway + :plugin 暴露接口，:data 实现

:data
  ├─ db/
  │   ├─ MediaExtraEntity        + Room
  │   ├─ MediaMetaCacheEntity    + Room
  │   ├─ PluginMetadataCacheEntity + Room
  │   └─ schema v8（直接改 entity，不写 Migration）
  ├─ dao/
  │   ├─ MediaExtraDao
  │   ├─ MediaMetaCacheDao（含 evictOldest）
  │   └─ PluginMetadataCacheDao
  └─ files/
      ├─ PluginLyricFileStore  + cacheDir/plugin-lyric-cache/<nanoid>.lrc
      └─ UserLyricFileStore    + filesDir/plugin-lyric-user/<platHash>/<idHash>.lrc

:feature/*
  ├─ home/local/LocalMusicRepository  ↺ 委托 "本地" plugin 取 cover/lyric
  ├─ player-ui/PlayerLyricLoader       ↺ 走 LyricGateway（cache → mediaMeta → 文件 → 插件）
  └─ settings/plugin                   ↺ 展示 PluginState 徽章 + 错误面板
```

依赖方向保持 `:app → :feature → :data, :player, :plugin → :core`。`:plugin` 通过 Gateway 接口反向获取 `:data` 实现，由 Hilt 注入；不直接 import `:data` 类型。

## 5. Section 1 — Cache & Persistence Layer

### 5.1 目标

复刻 RN `cache.MediaCache`、`MediaExtra.${plugin}` 与歌词文件双缓存三层。

### 5.2 模型

**`MediaMetaCacheEntity`**（Room v8）

| 列 | 类型 | 说明 |
|---|---|---|
| `key` | `String` (PK) | `${platform}@${id}` |
| `platform` | `String` | 供 `deleteByPlatform` 使用，建索引 |
| `sourceJson` | `String` | `Map<QualityKey, MediaSourcePayload>` 序列化 |
| `lyricRef` | `String?` | 指向 `PluginLyricFileStore` 内的 nanoid |
| `userAgent` | `String?` | 缓存的 UA |
| `headersJson` | `String?` | 缓存的 request headers |
| `lastUsedAt` | `Long` | epoch ms；命中即刷 |
| `sizeBytes` | `Int` | 估算总占用，用于将来全量大小估算 |

**`MediaExtraEntity`**（Room v8）

| 列 | 类型 | 说明 |
|---|---|---|
| `plugin` | `String` | 复合 PK |
| `mediaId` | `String` | 复合 PK |
| `downloaded` | `Boolean` (default false) |  |
| `localPath` | `String?` |  |
| `lyricOffsetMs` | `Long` (default 0) |  |
| `associatedLrcKey` | `String?` | 指向 `UserLyricFileStore` 的 key |

**`PluginLyricFileStore` / `UserLyricFileStore`** —— 两个独立目录、独立 lifecycle：

```
cacheDir/plugin-lyric-cache/<nanoid>.lrc     <- 插件返回的歌词，可被系统清空
filesDir/plugin-lyric-user/<platHash>/<idHash>.lrc  <- 用户自设歌词，永不自动清
```

### 5.3 cacheControl 决策（在 `PluginMediaSourceService`）

```
plugin.cacheControl ∈ {"cache", "no-cache", "no-store"}（未声明默认 "no-cache"，与现存 CacheControl.parse 一致）

  "cache"     → 命中 cache 即返回；不命中 → fetch + 写 cache
  "no-cache"  → 总是 fetch + 写 cache（连通性未接入前等同 always fresh）
  "no-store"  → 永不读写 cache（透传 fetch）
```

写 cache 时机：在 `getMediaSource` 成功返回非 null `url` 后由 `MediaCacheRepository.put()` 落 LRU 内存 + Room。

`PluginMediaSourceService` 暴露两个入口：

- `resolve(item, quality)` —— 默认入口，按 `cacheControl` 决策（cache 模式优先读 cache）
- `resolveFresh(item, quality)` —— 跳过 cache 读，强制走插件；用于 §5.7 失败重解析

### 5.7 Playback failure → cache eviction → 重解析

**目标**：解决 RN cache 模式下"插件返回过期签名 URL"的问题，避免回退到 stale-fix 设计的"始终重解析"（牺牲缓存收益换稳定性）。

**联动 stale-fix 设计**：`docs/superpowers/specs/2026-05-11-stale-media-source-playback-design.md` 当前"非目标"`不新增 URL 过期时间字段或媒体缓存淘汰策略` 被本 spec 替换为"基于播放失败的失败驱动 eviction"。**该设计需在 plan 实施时加 forward link 指向本 spec**。

**决策流程**：

```
PlayerController 监听 Player.Listener.onPlayerError:
  if (error.code == ERROR_CODE_IO_BAD_HTTP_STATUS) {
    val (platform, id, quality) = currentMediaKey ?: return  // 来自队列当前项 + 当前 quality
    val attemptedRefresh = retryState[platform to id] ?: false
    if (attemptedRefresh) {
      // 重试也失败 → 真正报错，不继续 loop
      return
    }
    retryState[platform to id] = true
    mediaCacheRepository.deleteEntry(platform, id, quality)
    val fresh = pluginMediaSourceService.resolveFresh(item, quality)
    if (fresh != null) {
      controller.setMediaItem(...)  // 替换 + 继续播放
      log("plugin_media_source_refreshed_after_failure", ...)
    }
  }

  // 队列切歌时清 retryState（避免 stale flag）
```

**MediaCacheRepository 加入**：

- `suspend fun deleteEntry(platform: String, id: String, quality: PlayQuality)` —— 仅删一条 (platform, id) 行内的某 quality 子键；如果删完整条没有别的 quality 留存则删整行

**retryState 范围 / 重置**：

- 数据结构：`val retryState = ConcurrentHashMap<MediaKey, Boolean>()`（PlayerController 持有，进程内，重启重置）
- 重置时机：队列切歌 / 主动 stop / 用户切歌后回到当前项 / 播放成功后清当前项标志
- 上限：单首歌单次播放过程最多 1 次重解析（任何 HTTP 失败码再次触发即真正报错）

**不在范围**：

- 不监听 `ERROR_CODE_BEHIND_LIVE_WINDOW` / `ERROR_CODE_PARSING_*` 等非 HTTP 状态错误（这些通常不是 URL 过期）
- 不实现"播放期间 N 秒内主动刷新"等主动策略（仅响应失败）

### 5.4 缓存层级

1. 内存层 `androidx.collection.LruCache<String, MediaMeta>(maxSize=200)`
2. 持久层 Room，800 项软上限；超过则 `evictOldest(by lastUsedAt LIMIT 200)`
3. 命中即刷 `lastUsedAt`

### 5.5 歌词缓存路径

```
GET 歌词的优先级（LyricGateway.resolve(plugin, mediaId)）:
  1. UserLyricFileStore（用户自设）
  2. MediaMetaCacheEntity.lyricRef → PluginLyricFileStore（插件结果）
  3. 调用 plugin.getLyric(...) → 写 PluginLyricFileStore + 回填 lyricRef
```

### 5.6 卸载副作用

```
PluginManager.uninstall(platform):
  - MediaExtraDao.deleteByPlugin(platform)        <- 完全清掉
  - MediaMetaCacheDao.deleteByPlatform(platform)  <- 完全清掉
  - PluginLyricFileStore 按 lyricRef 列表删除文件
  - UserLyricFileStore 不动（用户资产，跨插件卸载持久）
```

### 5.7 测试

- `MediaMetaCacheDaoTest`（Room in-memory）：LRU 淘汰、命中刷新、并发写串行化
- `MediaExtraDaoTest`：复合 PK 唯一性、`deleteByPlugin`
- `PluginMediaSourceServiceCacheTest`（单测 + 假 plugin）：三种 `cacheControl` 路径 + 网络异常 fallback
- 契约测 `CacheGatewayContractTest`：`:plugin` 接口签名与 `:data` 实现一致

## 6. Section 2 — Local Virtual Plugin

### 6.1 目标

让本地音乐与远端插件共享同一套 `PluginApi` 调用收口。

### 6.2 实现形态

`LocalFilePlugin : PluginApi`（Kotlin 直实现，不通过 QuickJS）：

```kotlin
object LocalFilePlugin : PluginApi {
    val platform = "本地"
    val hash = "local-plugin-hash"
    val supportedMethods = setOf(
        "getMusicInfo", "getLyric", "importMusicItem", "getMediaSource",
    )

    override suspend fun getMediaSource(musicItem: MusicItem, quality: String): MediaSourceResult? =
        if (quality == "standard" && musicItem.localPath != null)
            MediaSourceResult(url = "file://${musicItem.localPath}", quality = "standard")
        else null

    override suspend fun getLyric(musicItem: MusicItem): LyricResult? =
        readAdjacentLrc(musicItem.localPath) ?: readEmbeddedUslt(musicItem.localPath)

    override suspend fun getMusicInfo(musicItem: MusicItem): MusicItem? =
        Mp3MetadataReader.read(musicItem.localPath)?.toMusicItem(platform)

    override suspend fun importMusicItem(urlLike: String): MusicItem? =
        Mp3MetadataReader.read(urlLike)?.toMusicItem(platform)

    // 其余 PluginApi 方法默认走 PluginApi 接口的 default impl 返回 null
}
```

### 6.3 注册与隐藏

- `PluginManager.setup()` 在常规 `loadAllPlugins()` 之后注册 `LocalFilePlugin`。
- `getByName("本地")` / `getByMedia(item with platform == "本地")` → 命中此实例。
- `LocalFilePlugin.supportedSearchType = emptyList()` → `getSortedSearchablePlugins(...)` 不会返回它（搜索 UI 不调）。
- `getEnabledPlugins()` 显式 **过滤掉** `hash == "local-plugin-hash"` 的 entry —— 插件管理 UI、订阅、排序、用户变量、卸载 / 更新流均不可见。
- 不持久化到 metadata cache，每次 `setup()` 重新注册。

### 6.4 调用收口

- `LocalMusicRepository.getCover(item)` → `PluginManager.getByName("本地")!!.getMusicInfo(item)?.coverImg`
- `LocalMusicRepository.getLyric(item)` → 经 `LyricGateway`（最终调到 LocalFilePlugin）
- `PlayerController` / `PluginMediaSourceService` 删去 `if (platform == "本地")` 特判；统一走 `PluginManager.getByMedia(item)`。

### 6.5 ID3 reader 落点

本地音乐元数据 / 嵌入封面 / USLT 帧的读取统一由 `:data` 暴露 `Mp3MetadataReader`（基于 `MediaMetadataRetriever`）：

- 若 `:data` 已有等价能力（如 `LocalScanner` 内部封装），plan 阶段将其抽出为公开类供 `LocalFilePlugin` 复用，**不再造一个**。
- 否则在 `:data/local/` 新增 `Mp3MetadataReader.kt`，签名 `read(path: String): Mp3Metadata?` 返回 `title / artist / album / durationMs / coverBytes? / embeddedLrc?`。
- `LocalFilePlugin` 依 Hilt 注入此 reader（接口在 `:plugin`，实现在 `:data`，与 `MediaMetaCacheGateway` 同模式）。

### 6.6 测试

- `LocalFilePluginTest`（:plugin/src/test）：fixture 一个本地 mp3 + 相邻 .lrc，断言 4 方法输出
- `LocalMusicRepositoryIntegrationTest`（:feature/home/src/test）：走 `PluginManager.getByName("本地")` 拿到的 cover/lyric 与改造前路径完全一致

## 7. Section 3 — Plugin State Machine & Error Surface

### 7.1 数据模型

```kotlin
sealed interface PluginState {
    data object Initializing : PluginState
    data object Loading : PluginState
    data object Mounted : PluginState
    data class Failed(val reason: PluginErrorReason, val detail: String?) : PluginState
}

enum class PluginErrorReason {
    VersionNotMatch,
    CannotParse,
    MissingPlatform,
    DownloadFailed,
    UserVariableSyncFailed,
}

data class PluginEntry(
    val filePath: String,
    val state: PluginState,
    val attemptedPlatform: String?,  // 来自 metadata cache 或文件名兜底
    val loaded: LoadedPlugin?,        // state == Mounted 时非空
    val installSource: PluginInstallSource,
)
```

### 7.2 `PluginManager` 改造

- `plugins: StateFlow<List<PluginEntry>>` 取代当前 `StateFlow<List<LoadedPlugin>>`
- `getByName(name)` / `getEnabledPlugins()` / `getSortedSearchablePlugins(...)` 默认只看 `Mounted` entry
- 新增 `allEntries: Flow<List<PluginEntry>>` 给 UI
- 新增 `retryEntry(filePath)`：把 `Failed` entry 重新走加载流程

### 7.3 失败路径

| 触发点 | 写入状态 |
|---|---|
| `installFromFile` 解 `platform` 失败 | `Failed(MissingPlatform, "platform field is blank")` |
| `evaluatePlugin` JS 抛错 | `Failed(CannotParse, e.message)` |
| `appVersion satisfies` 不通过 | `Failed(VersionNotMatch, "requires X, app is Y")` |
| `installFromUrl` HTTP 失败 / 404 | `Failed(DownloadFailed, "HTTP $code")` —— 不创建本地文件 |
| `setUserVariables` 注入失败 | `Failed(UserVariableSyncFailed, e.message)`，旧 mounted 状态保留 |

### 7.4 PluginOperationResult 扩展

```
SUCCESS
SOURCE_UNREACHABLE          → DownloadFailed
SOURCE_INVALID              → CannotParse
DUPLICATE_PLUGIN            → 改为 SUCCESS（见 §11.3 hash 冲突）
MISSING_PLATFORM (新)       → MissingPlatform
VERSION_REJECTED (新)       → VersionNotMatch
INTERNAL_ERROR              → 保留
```

### 7.5 UI 曝露（`feature/settings/plugin`）

- 列表项右侧加状态徽章：
  - `Mounted` → 绿勾
  - `Initializing` / `Loading` → 旋转灰圈
  - `Failed` → 红叉
- 点击红叉打开错误面板：
  - 大字 reason（中文文案）
  - 小字 detail（折叠）
  - 按钮：`重试`、`卸载`、`复制错误信息`
- `installFromUrl` 失败 → Toast + 在列表插入 Failed entry 占位（带"删除"按钮）

### 7.6 日志

`MfLogger` 必须记录的事件（参考 AGENTS.md 日志规范）：

- `plugin_mount_succeeded(platform, version, durationMs)`
- `plugin_mount_failed(platform?, reason, detail, durationMs)`
- `plugin_install_failed(url?, reason, detail, durationMs)`
- `plugin_state_transition(platform, from, to)`

`reason` / `from` / `to` 字段必须是 **显式字符串常量**（`"VersionNotMatch"` / `"Mounted"` / …），不能依赖 `enum.name` 或 `Class.simpleName`。这避免 R8 重命名后日志失真，亦不需要为 `PluginState` / `PluginErrorReason` 追加 `@Keep`。

### 7.7 测试

- `PluginStateMachineTest`（:plugin/src/test）：5 个 Failed 分支
- `PluginManagerUiContractTest`：`allEntries` 流在 install / uninstall / retry 触发后包含正确 PluginEntry
- UI 单测：`PluginListScreenStateBadgeTest`（Compose UI test）

### 7.8 Harness 联动

- 新增 plugin rule `rule-plugin-failure-must-surface`：`:plugin/manager/` catch block 不得把异常吞掉而不写 state；`harness-curator-skill` 巡检触发条件 = 出现 `catch (e: Exception) { return null }` 形态而未写入 `PluginEntry.state = Failed`。
- 该规则的 guard 类型起步 manual，再发生一次相关 incident 即升级 contract-test。

## 8. Section 4 — Runtime Compat

### 8.1 `AxiosShim`

**默认超时**：`timeoutMs = 2000`；per-call `config.timeout`（毫秒）可覆盖。

**Auth URL → Basic**：

```kotlin
fun normalizeRequest(originalUrl: String, headers: MutableMap<String, String>): String {
    val parsed = HttpUrl.parse(originalUrl) ?: return originalUrl
    if (parsed.username().isNotEmpty() || parsed.password().isNotEmpty()) {
        if (!headers.containsKey("Authorization")) {
            val basic = Base64.encodeToString(
                "${parsed.username()}:${parsed.password()}".toByteArray(),
                Base64.NO_WRAP,
            )
            headers["Authorization"] = "Basic $basic"
        }
        return parsed.newBuilder().username("").password("").build().toString()
    }
    return originalUrl
}
```

**测试**：`AxiosShimAuthUrlTest` 覆盖：无凭证 / 有凭证 / 已显式 Authorization / 凭证含特殊字符（`@`、`:`、URL 编码）。

### 8.2 `__env` / globals

```js
// __env
{
  os: "android",
  appVersion: "<from PackageManager>",
  lang: "zh-CN",        // 硬编码，与 RN 一致；不读 Locale
  getUserVariables: () => globalThis.__userVariables || {},
  userVariables: ...   // alias getter
}

// process（新增）
{
  platform: "android",
  version: "<from PackageManager>",
  env: <__env above>,
}
```

理由：RN 插件大量使用 `if (env.lang === "zh-CN")` / `process.platform === "android"` 分支；偏离会让插件走错路径。Out of scope（§3.2）显式声明。

### 8.3 `URL` polyfill 探针

在 plan 阶段先写 `RuntimeUrlConstructorContractTest`（:plugin/src/test，调用 `JsEngine` 求值 `new URL("https://a.b/c?d=1").pathname`），若 QuickJS-kt 已支持则不补 polyfill；若失败则在 `JsEngine` 启动注入 `assets/jslibs/url-polyfill.js`（约 5KB，CommonJS）。**spec 不预判结果**，由 plan 阶段实测决定。

### 8.4 `require('webdav')` 最小可用

JS 包装 `assets/jslibs/webdav.js`：

```js
module.exports = {
  createClient(url, { username, password }) {
    const auth = { username, password };
    return {
      getFileContents: (path) => __webdav_get(url, path, auth),
      putFileContents: (path, data) => __webdav_put(url, path, data, auth),
    };
  },
};
```

Kotlin 后端 `WebDavShim.kt`：

- `__webdav_get(baseUrl, path, auth)` → OkHttp `GET ${baseUrl}/${path}` + Basic Auth → 返回 body 字符串
- `__webdav_put(baseUrl, path, data, auth)` → OkHttp `PUT ${baseUrl}/${path}` + Basic Auth + `Content-Type: text/plain`

明确不实现：`PROPFIND` / `MKCOL` / `DELETE` / `MOVE` 等其他方法。插件代码若调用上述方法 → JS shim 不会暴露 → 调用方报 `undefined function`，行为与"明确报错"等价。

**测试**：`WebDavShimTest` 用 `MockWebServer` 桩 `GET` / `PUT`，断言：

- 请求路径正确拼接
- `Authorization: Basic <base64>` header 注入
- body 与 response body 解析

### 8.5 `RuntimeCompatContractTest`

`:plugin/src/test` 新增契约测：

```kotlin
@Test fun `runtime exposes process and URL and webdav`() {
    val js = """
        if (typeof process === 'undefined') throw 'no process';
        if (typeof process.platform === 'undefined') throw 'no process.platform';
        if (typeof process.env === 'undefined') throw 'no process.env';
        if (typeof URL === 'undefined') throw 'no URL';
        const wd = require('webdav');
        if (typeof wd.createClient !== 'function') throw 'no webdav.createClient';
        if (typeof env.lang !== 'string' || env.lang !== 'zh-CN') throw 'env.lang';
        'ok'
    """.trimIndent()
    assertEquals("ok", engine.evaluate<String>(js))
}
```

进 `dev-harness-gate` 作业。

### 8.6 显式 out of scope

- set-cookie 数组归一化（OkHttp 原生支持）
- `@react-native-cookies/cookies`（RN 自己 deprecated no-op）

## 9. Section 5 — Plugin Lifecycle

### 9.1 `appVersion satisfies`

**依赖**：`io.github.z4kn4fein:semver:2.0.0`（纯 Kotlin / multiplatform，~50KB），加在 `:plugin/build.gradle.kts` 的 `implementation`。

**实现**：在 `extractPluginInfo()` 解出 `appVersion: String?` 后立刻判：

```kotlin
val pluginAppVersion = info.appVersion
if (!pluginAppVersion.isNullOrBlank()) {
    val ok = try {
        Constraint.parse(pluginAppVersion).isSatisfiedBy(Version.parse(appVersionName))
    } catch (e: Exception) {
        false
    }
    if (!ok) {
        return PluginEntry(
            filePath, PluginState.Failed(
                PluginErrorReason.VersionNotMatch,
                "plugin requires $pluginAppVersion, app is $appVersionName",
            ),
            attemptedPlatform = info.platform, loaded = null, installSource,
        )
    }
}
```

**`installFromUrl` 路径**：闸门失败 → `PluginOperationResult.VersionRejected` → **不留文件**（避免装失败的插件一直占位）。

**测试**：`PluginAppVersionGateTest` 5 种情况：`">=1.0.0"` / `"^1.2.0"` / `"~1.2.0"` / 空 / 非法。

### 9.2 `lazyLoadPlugin`

**`PluginMetadataCache`**（Room v8 新表 `PluginMetadataCacheEntity`）：

| 列 | 类型 |
|---|---|
| `filePath` | `String` (PK) |
| `name` | `String` |
| `version` | `String?` |
| `hash` | `String` |
| `srcUrl` | `String?` |
| `supportedMethodsJson` | `String` |
| `supportedSearchTypesJson` | `String` |
| `userVariableKeysJson` | `String` |
| `appVersion` | `String?` |
| `sourceMtimeMs` | `Long` |
| `cachedAtAppVersion` | `String` |

**启动流程**：

```
PluginManager.setup() {
    metadataCache = pluginMetadataCacheDao.getAll()
    scanned = pluginDir.listFiles { it.endsWith(".js") }
    for each file:
        if cache hit AND mtime matches AND cachedAtAppVersion matches:
            emit PluginEntry(state = Initializing, info = cached, loaded = null)
        else:
            emit PluginEntry(state = Loading, ...)
            evaluatePluginAsync(file)  -- 异步 mount
    registerLocalFilePlugin()
}
```

**`ensureMounted(entry)`**：在任意公开方法（`getByName(...).search(...)` 等）调用前由 `PluginManager` 包装层调用：

```kotlin
suspend fun <T> withPlugin(name: String, block: suspend (LoadedPlugin) -> T): T? {
    val entry = currentEntry(name) ?: return null
    return when (val s = entry.state) {
        is PluginState.Mounted -> block(entry.loaded!!)
        is PluginState.Initializing -> {
            mountEntryFromCache(entry)?.let { block(it) }
        }
        is PluginState.Loading -> {
            awaitMounted(entry.filePath).let { block(it.loaded!!) }
        }
        is PluginState.Failed -> null
    }
}
```

### 9.3 Cache invalidation

| 触发 | 动作 |
|---|---|
| 文件 mtime 变 | 单 entry 失效 → 重新 evaluate |
| 安装 / 更新 / 卸载 | 同步写入 / 删除 cache 行 |
| 应用 `versionName` 变（`cachedAtAppVersion` 不匹配） | 全量失效（`appVersion` 闸门可能改判） |

### 9.4 Settings 开关

DataStore key `pref_lazy_load_plugins: Boolean = true`。在 `feature/settings` 的"插件高级"分组加一个开关（默认开），用于实验关闭懒加载诊断。开关变更需重启 `PluginManager.setup()`。

### 9.5 测试

- `PluginLazyLoadIntegrationTest`（落 `:plugin/src/androidTest`，无网络依赖；与 `PluginRuntimeLocalIntegrationTest` 同档）：
  - 装 3 个本地 fixture 插件 → 重启 manager → 断言三个 entry 都是 `Initializing` 且未触发 JS evaluate（通过 spy `JsEngine` 创建计数 = 0）
  - 调一次 `getByName("X").search(...)` → 断言只有 X 被真求值
- `PluginAppVersionGateTest`（`:plugin/src/test`，见 §9.1，纯 JVM）
- `PluginMetadataCacheContractTest`（`:plugin/src/test`）：cache schema 字段与 `PluginInfo` 同步（漏字段编译期失败）

## 10. Section 6 — Model Boundary

### 10.1 取消 `$` 私有键协议

RN 在 `MediaItem` 上挂 `internalSerializeKey = "$"` 字典做"跨插件边界但不暴露"的字段。Kotlin 端不复刻该协议，改为显式建模：

| RN | Android 替代 |
|---|---|
| `$.localPath` | `MediaExtraEntity.localPath`（§5.2） |
| `$.contentType` | `MediaSourceResult.contentType: String?`（新字段，可选） |
| `$.headers` / `$.userAgent` | `MediaSourceResult.headers` / `MediaSourceResult.userAgent`（已存在） |
| `$.tagged`（内部 marker） | 不跨边界，不建模 |
| `$.albumImg` 等覆写字段 | 调用方 `copy()` 决策 |

### 10.2 Bridge 防御

`JsBridge.toMusicItem(map: Map<*,*>)`：忽略 `"$"` key（防御性丢弃，避免 RN 插件返回内部状态污染 Kotlin 模型）。

`JsBridge.musicItemToMap(item: MusicItem)`：不输出 `"$"`，但**输出 plugin 需要见到的字段**（如 `localPath`，从 `MediaExtraDao` 查到后投影）。投影逻辑放 `MusicItemBridgeProjector(item, mediaExtra)`：

```kotlin
class MusicItemBridgeProjector(
    private val mediaExtraDao: MediaExtraDao,
) {
    suspend fun project(item: MusicItem): Map<String, Any?> {
        val extra = mediaExtraDao.find(item.platform, item.id)
        return item.toBaseMap() + buildMap {
            extra?.localPath?.let { put("localPath", it) }
            extra?.lyricOffsetMs?.takeIf { it != 0L }?.let { put("lyricOffset", it) }
        }
    }
}
```

### 10.3 `PluginMediaSourceService` 改造

```
resolve(item, quality):
  1. extra = mediaExtraDao.find(item.platform, item.id)
  2. if extra?.localPath != null and quality == "standard" → 返回 file://${localPath}
  3. cacheControl 决策（§5.3）
  4. invoke plugin.getMediaSource(projector.project(item), quality)
  5. 写 MediaMetaCache（除 "no-store"）
```

### 10.4 测试

- `MediaItemBridgeContractTest`：`$` 字段在双向 bridge 都不泄漏；`localPath` 投影到 bridge map 正确
- `MusicItemBridgeProjectorTest`：MediaExtra 不存在时不出现该字段

## 11. 跨域决策

### 11.1 Hash 冲突（同一插件重装）

- 同 hash 重装 → **静默幂等返回 success**（与 RN 一致），UI 给一条"插件已是最新"的轻提示。
- 不同文件同 hash → silently keep 原版本，不报错（与 RN 一致）。
- 同 platform 不同 hash 不同 version → 高 version 替换低 version（已是当前行为）。

### 11.2 数据库迁移

- Room schema v7 → v8，直接修改 entity；**不写 Migration**（依据 user memory：开发期直接改 entity）。
- 开发期需手动 `adb shell pm clear com.zili.android.musicfreeandroid` 或重装 APK。
- Schema 导出文件（`data/schemas/<DatabaseFqcn>/8.json`）随实现一同提交。

### 11.3 DataStore 兼容

- 现有 `PluginMetaStore` 的 DataStore 键（`disabledPlugins`、`order`、`subscriptions`、`userVariables` 前缀）一律保留；本 spec 不重写。
- 新增 key `pref_lazy_load_plugins`、`pref_user_lyric_path_override`（保留位）。

## 12. 测试矩阵

### 12.1 单测层（`:plugin/src/test`、`:data/src/test`）

| 文件 | 覆盖 |
|---|---|
| `MediaMetaCacheDaoTest` | LRU 淘汰、命中刷新 |
| `MediaExtraDaoTest` | 复合 PK / 按 plugin 删除 |
| `PluginMediaSourceServiceCacheTest` | 三种 cacheControl + offline fallback |
| `PluginLyricFileStoreTest` | 读写、key 唯一性 |
| `UserLyricFileStoreTest` | 路径 hash 稳定 |
| `LocalFilePluginTest` | 4 方法路径 |
| `PluginStateMachineTest` | 5 种 Failed 分支 |
| `PluginAppVersionGateTest` | 5 种 satisfies 输入 |
| `PluginLazyLoadIntegrationTest` | mount on demand |
| `PluginMetadataCacheTest` | mtime / appVersion 失效 |
| `AxiosShimAuthUrlTest` | 4 种 URL 凭证 |
| `WebDavShimTest` | GET / PUT + Basic Auth |
| `MusicItemBridgeProjectorTest` | MediaExtra 投影 |

### 12.2 契约测（进 `dev-harness-gate`）

| 文件 | 覆盖 |
|---|---|
| `CacheGatewayContractTest` | :plugin 接口 vs :data 实现签名 |
| `RuntimeCompatContractTest` | process / URL / webdav / env.lang 暴露 |
| `PluginMetadataCacheContractTest` | cache schema vs PluginInfo 字段 |
| `MediaItemBridgeContractTest` | `$` 不泄漏 / localPath 投影 |
| `PluginManagerUiContractTest` | allEntries 流 install/uninstall/retry 行为 |
| `RuntimeUrlConstructorContractTest` | URL 探针（决定是否注入 polyfill） |

### 12.3 集成测（`:plugin/src/androidTest`）

| 文件 | 覆盖 | 网络 |
|---|---|---|
| `LocalFilePluginIntegrationTest` | fixture mp3 + adjacent .lrc | 否 |
| `PluginCacheControlIntegrationTest` | MockWebServer 三种 cacheControl | MockWebServer |
| `PluginRuntimeLocalIntegrationTest` | 现有 + 加入 process / webdav | 否 |
| `PluginRuntimeNetworkIntegrationTest` | 现有保持（Assume 门控） | `-Pintegration` |
| `PluginManagerHttpLifecycleTest` | 现有 + appVersion 拒绝路径 | MockWebServer |

按 plugin rule `rule-network-test-gated`：真域名仅在 `*NetworkIntegrationTest.kt` + `Assume.assumeTrue(pluginNetworkTests == "true")` 中出现。

### 12.4 UI 测

| 文件 | 覆盖 |
|---|---|
| `PluginListScreenStateBadgeTest`（Compose） | 三种状态徽章渲染 |
| `PluginErrorPanelTest`（Compose） | reason 中文文案 / 重试 / 卸载按钮 |

## 13. 运行态验收（Debug 构建）

按 `AGENTS.md`：先做运行态验收再下"完成"结论。

### 13.1 端到端场景

1. **冷启动秒开（cacheControl=cache 插件）**：装一份声明 `cacheControl: "cache"` 的插件 → 搜歌 → 播放 → kill app → 重开 → 同首歌再播放，断言 `PluginMediaSourceService` 直接命中 `MediaCacheRepository`（通过 Logan 日志 `plugin_get_media_source_cache_hit`）。

   **同步验证失败 → 重解析**：装一个**故意 mock URL TTL=10s** 的本地 fixture 插件 → 第一次播放正常 → 等 12s → 再次播放同一条目应：第一次 HTTP 403 → 自动 evict cache → re-resolve → 实际播放仍然成功 → 日志含 `plugin_media_source_refreshed_after_failure`，且 retry counter 不超 1。
2. **离线播**：开飞行模式 → 播放上一步已缓存的歌（`cacheControl="no-cache"` 插件） → 应能命中 cache 播放。
3. **歌词持久**：插件返回歌词 → 落 `PluginLyricFileStore` → 重启 app → 同首歌歌词不再走 `getLyric`（日志 `lyric_resolved_from_cache`）。
4. **用户自设歌词**：人工放 `filesDir/plugin-lyric-user/<platHash>/<idHash>.lrc` → 播放 → 优先级覆盖插件结果。
5. **本地音乐**：扫一首本地 mp3（带嵌入封面与相邻 .lrc） → 列表显示封面 / 播放时显示歌词 → 与改造前对比零功能丢失。
6. **插件错误明示**：
   - 上传一份 `platform` 字段为空的 .js → 列表出现 Failed entry，红叉，点击 reason = "MissingPlatform"
   - 上传一份 `appVersion = ">=99.0.0"` 的 .js → Failed entry，reason = "VersionNotMatch"
   - 上传一份非 .js 文本 → Failed entry，reason = "CannotParse"
7. **Hash 幂等**：重装同一份 .js → Toast "插件已是最新"，列表无重复条目。
8. **懒加载**：装 5 个插件 → 重启 app → 在 Settings 打开"插件高级 / 懒加载状态" → 仅最近用过的 1 个状态显示 `Mounted`，其余 `Initializing`；触发搜索后逐个 `Mounted`。

### 13.2 验收方式

- 屏幕录制（Settings / 播放器顶部 mini）+ Logan 日志包导出
- `adb logcat | grep "category=PLUGIN"` 比对事件 key
- Room DB 通过 `adb pull` 取出 `databases/musicfree.db` 用 sqlite3 抽样
- 文件系统：`adb shell ls filesDir/plugin-lyric-user/...` 抽样
- 不要求 Release 构建；按 AGENTS 的 Debug 默认闸门

## 14. 实施分阶段建议（供 plan 阶段参考）

本 spec 一份；后续 plan 阶段可按 6 个 section 各自切 PR：

```
PR1  :data + :plugin/cache Section 1（缓存层）
PR2  :plugin/local + LocalMusicRepository 收口   Section 2
PR3  PluginEntry + PluginState 重塑 + UI 徽章     Section 3
PR4  AxiosShim + RequireShim + WebDavShim + globals   Section 4
PR5  appVersion + lazyLoad + metadataCache       Section 5
PR6  MusicItemBridgeProjector + $ key 防御        Section 6
```

每个 PR 独立可测、独立 revert。`dev-harness-gate` 在 PR3、PR4、PR6 增加 contract test 覆盖。

## 15. 风险登记

| 风险 | 应对 |
|---|---|
| QuickJS-kt 已暴露 `URL` 但与 RN 行为不一致（如 `pathname` 解析差） | §8.3 的 `RuntimeUrlConstructorContractTest` 起到回归探针作用；若发现行为不一致 → 强制注入 polyfill |
| `MediaMetadataRetriever` 在某些设备上不能读 ID3 USLT 帧 | `LocalFilePlugin.getLyric` 先读相邻 .lrc，失败再读嵌入；都失败返回 null（与 RN 一致） |
| 懒加载首次方法调用 → 首屏延迟 | metadata cache 命中后 mount 路径 < 200ms；可观测 `plugin_mount_succeeded.durationMs` |
| Room schema v8 在内部测试用户机器上 crash（v7 残留） | dev-only 直接 `pm clear` 或 reinstall；本 spec 不为 Release 用户做 migration |
| `lang = "zh-CN"` 硬编码与未来国际化诉求冲突 | 显式 §3.2 out-of-scope；如需国际化新开 spec |

## 16. 未决项

- 无（hash 冲突、UI 范围、`env.lang`、webdav 范围在 brainstorming 中已锁定）。

## 17. 决策日志

- **存储后端**：Room + 文件系统（贴现有栈，不引 MMKV）—— 2026-05-11
- **本地插件落地**：内置 `LocalFilePlugin`，动调用点收口 —— 2026-05-11
- **进阶对齐范围**：状态机 + appVersion + lazyLoad + process/URL/webdav 全收 —— 2026-05-11
- **spec 组织法**：Approach A（按域分 6 section） —— 2026-05-11
- **`env.lang`**：硬编码 `"zh-CN"` —— 2026-05-11
- **webdav**：最小可用（`getFileContents` / `putFileContents`） —— 2026-05-11
- **`PluginState` UI 曝露**：全做（徽章 + 错误面板 + Toast） —— 2026-05-11
- **Hash 冲突**：静默幂等返回 success（RN 行为） —— 2026-05-11
- **音源缓存**：RN 完整语义（按 cacheControl 读 cache + ExoPlayer 失败时 evict + 重解析），单首歌单次播放重试上限 1 次；超过 `2026-05-11-stale-media-source-playback-design.md` 的非目标，需在该设计加 forward link —— 2026-05-11

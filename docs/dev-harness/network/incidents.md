# Network / HTTP 流量基建 Incidents

> 文档状态：当前规范（Dev Harness — Network Incidents）
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [Incidents Index](../incidents/index.md) ｜ [network/rules.md](./rules.md)
> 最后校验：2026-05-19

## INC-2026-0019 — 新建 OkHttpClient 绕过 @BaseOkHttp 派生

- id: INC-2026-0019
- area: network
- date: 2026-05-19
- status: active
- rule_ref: docs/dev-harness/network/rules.md#rule-okhttp-derive-from-base
- guard:
    type: grep
- signature: |
    grep -rEn 'OkHttpClient\.Builder\s*\(\s*\)' \
      --include='*.kt' \
      --exclude-dir=build --exclude-dir=.worktrees --exclude-dir=.gradle . \
      | grep -vE '/test/|/androidTest/|/testFixtures/' \
      | grep -v 'core/src/main/java/com/hank/musicfree/core/network/NetworkModule.kt' \
      | grep -v 'plugin/src/main/java/com/hank/musicfree/plugin/engine/AxiosShim.kt'
- fix_ref: docs/superpowers/specs/2026-05-19-traffic-stats-and-media-cache-design.md

### 根因

历史 5 个模块（`:downloader` / `:updater` / `:plugin` PluginManager / WebDavShim / AxiosShim）各自 `OkHttpClient.Builder().build()` 自建客户端，导致：
- 每个 client 自带独立 dispatcher / connection pool，资源浪费。
- 任何一个未挂 `NetworkTrafficEventListener.Factory` 的 client 都让对应模块的流量从 traffic_daily 中“消失”，traffic stats UI 数据失真。
- Coil 默认 fetcher 走自带 ktor/okhttp 实例，图片流量同样绕过统计。

迁移到 `@BaseOkHttp` 派生（`base.newBuilder()....build()`）后所有模块共享 base 配置与 event listener；任何回到 `OkHttpClient.Builder()` 的写法都会立刻让对应模块从统计盲区里掉队。

### 复发条件

`*.kt` 产线代码（不含 `/test/`、`/androidTest/`、`/testFixtures/`）中除 `core/network/NetworkModule.kt` 和 `plugin/engine/AxiosShim.kt` 外出现 `OkHttpClient.Builder()`。

### 教训

业务模块 MUST 注入 `@BaseOkHttp` 并通过 `base.newBuilder()` 派生；不要因为“只是加一个 interceptor”就 new 一个全新 builder。

### 备注

`AxiosShim.kt` 的 fallback 仅服务于 Application 初始化前的极端窗口与测试场景，由 `MusicFreeApplication.onCreate()` 触发 `setBaseClient(...)` 覆盖。新增代码不可仿造此 pattern；test fixture 内 `OkHttpClient.Builder()` 是契约测试本身的实现细节，不在 grep 范围。

## INC-2026-0020 — Media3 使用 DefaultHttpDataSource 绕过 base client

- id: INC-2026-0020
- area: network
- date: 2026-05-19
- status: active
- rule_ref: docs/dev-harness/network/rules.md#rule-media3-okhttp-data-source
- guard:
    type: grep
- signature: |
    grep -rEn 'DefaultHttpDataSource\.Factory\s*\(' \
      --include='*.kt' \
      --exclude-dir=build --exclude-dir=.worktrees --exclude-dir=.gradle . \
      | grep -vE '/test/|/androidTest/'
- fix_ref: docs/superpowers/specs/2026-05-19-traffic-stats-and-media-cache-design.md

### 根因

Media3 默认音频 HTTP 数据源 `DefaultHttpDataSource.Factory` 内部走 `HttpURLConnection`，与 OkHttp 客户端栈完全独立：
- 不复用 `@BaseOkHttp` 的连接池，每次播放都重新建立 TCP。
- `NetworkTrafficEventListener` 监听不到任何字节，音频流量在 traffic stats 里直接消失（这是迁移前的实际问题）。
- 无法继承 base 配置的 interceptor / DNS / 超时策略。

切到 `OkHttpDataSource.Factory(@BaseOkHttp)` + `HeaderInjectingDataSourceFactory` + `CacheDataSource.Factory` 后，所有播放流量走同一条 OkHttp 链路，traffic 与缓存命中率才一致可观测。

### 复发条件

`*.kt` 产线代码出现 `DefaultHttpDataSource.Factory(`。

### 教训

新增任何 `androidx.media3.datasource.DataSource.Factory` MUST 用 `OkHttpDataSource.Factory(@BaseOkHttp)` 作为根 upstream。`CacheDataSource.Factory` 嵌套时也 MUST 把 upstream 指向 OkHttpDataSource，不要因为方便就用 default。

### 备注

未来若需要本地 / file scheme DataSource，使用 `FileDataSource.Factory()` 或 `DefaultDataSource.Factory(@BaseOkHttp wrapper)`，不要 fall back 到 `DefaultHttpDataSource`。

## INC-2026-0021 — Coil ImageLoader 走默认 fetcher 绕过 base client

- id: INC-2026-0021
- area: network
- date: 2026-05-19
- status: active
- rule_ref: docs/dev-harness/network/rules.md#rule-coil-uses-base-okhttp
- guard:
    type: grep
- signature: |
    grep -rEn 'ImageLoader\.Builder\s*\(' \
      --include='*.kt' \
      --exclude-dir=build --exclude-dir=.worktrees --exclude-dir=.gradle . \
      | grep -vE '/test/|/androidTest/' \
      | grep -v 'core/src/main/java/com/hank/musicfree/core/coil/ImageLoaderModule.kt'
- fix_ref: docs/superpowers/plans/2026-04-21-coil-network-fetcher-fix.md

### 根因

Coil 3 默认 `NetworkFetcher` 自带 ktor/okhttp 客户端实例（与项目 `@BaseOkHttp` 完全无关）：
- 图片流量绕过 `NetworkTrafficEventListener`，traffic stats 缺一大块。
- 连接池与 dispatcher 重复，移动网络下浪费 socket。
- Coil 的 cache header 与 OkHttp 的 cache 配置无法对齐。

`ImageLoaderModule` 通过 `OkHttpNetworkFetcherFactory(@BaseOkHttp)` 显式覆盖 fetcher，并在 `MusicFreeApplication` 实现 `coil3.SingletonImageLoader.Factory` 把 Hilt 提供的 ImageLoader 暴露给 `context.imageLoader` 默认入口。任何额外的 `ImageLoader.Builder(context).build()` 都会再次新建一个 fetcher 链路，让图片流量重新逃出统计。

### 复发条件

`*.kt` 产线代码（除 `core/coil/ImageLoaderModule.kt` 外）出现 `ImageLoader.Builder(`。

### 教训

需要自定义图片加载行为时，扩展 `ImageLoaderModule` 的 builder 链或派生 `imageLoader.newBuilder()`，不要绕过 module 自己 new；自定义 `LocalImageLoader` provider 或 Compose preview 用 fake 应放在 test/preview source set 里。

### 备注

Compose preview / 测试场景使用 Coil fake ImageLoader 时放在 `*/test/` 或 `*/preview/` 路径，被 grep 自动排除。

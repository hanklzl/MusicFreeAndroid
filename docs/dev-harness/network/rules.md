# Dev Harness — Network / HTTP 流量基建规则

> 文档状态：当前规范（network 域）
> 适用范围：所有 OkHttpClient 创建、EventListener 注册、HTTP DataSource 配置
> 直接执行：是
> 当前入口：[Dev Harness INDEX](../INDEX.md) ｜ [AGENTS](../../../AGENTS.md)
> 设计来源：[流量统计与音频本地缓存设计](../../superpowers/specs/2026-05-19-traffic-stats-and-media-cache-design.md)
> 关联实施：[流量统计与音频本地缓存 实施计划](../../superpowers/plans/2026-05-19-traffic-stats-and-media-cache.md)
> 最后校验：2026-05-19

## 设计原则

- 全应用 HTTP 流量必须经过同一条 OkHttp 调用链，挂载同一组 `EventListener.Factory`，从而让 `NetworkTrafficEventListener` 一次性统计所有出网字节（插件、下载、更新、Coil 图片、Media3 音频）。
- 派生 client（`base.newBuilder()....build()`）保留 base 的 dispatcher、connection pool 与 event listener factory；新建 `OkHttpClient.Builder()` 会绕过这些共享设施，是当前最常见的回归形态。

## 强制入口

新增或修改任何发起 HTTP 请求的模块前，必须先读取本文件。

新建 `OkHttpClient` MUST 从 `@BaseOkHttp` 注入的 base 派生；新建 `androidx.media3.datasource.DataSource.Factory` MUST 走 `OkHttpDataSource.Factory(@BaseOkHttp)`；Coil 自定义 `ImageLoader` MUST 通过 `OkHttpNetworkFetcherFactory(@BaseOkHttp)` 接入同一 client。

## OkHttpClient 派生 {#rule-okhttp-derive-from-base}

implemented_by: INC-2026-0019

- 业务代码 MUST 通过 Hilt 注入 `@BaseOkHttp OkHttpClient`。需要自定义 interceptor / timeout 时 MUST 用 `base.newBuilder().<配置>.build()` 派生新 client。
- 业务代码 MUST NOT 在 `:core/network` 与 `AxiosShim` fallback 之外直接 `OkHttpClient.Builder()` 实例化产线 client。
- MUST NOT 在 `:core/network` 之外注册新的 `EventListener.Factory`，统一通过 base provider 接入；额外监听需求 MUST 改 base provider 或开 incident 后调整。
- 测试代码不在本规则范围内（test fixture 内的 `OkHttpClient.Builder()` 用于伪造 sentinel client 验证派生关系，是契约测试本身的实现细节）。

## Media3 HTTP DataSource {#rule-media3-okhttp-data-source}

implemented_by: INC-2026-0020

- `ExoPlayer` / `DefaultMediaSourceFactory` 使用的 `DataSource.Factory` MUST 是 `OkHttpDataSource.Factory(@BaseOkHttp)`（必要时再包一层 `HeaderInjectingDataSourceFactory` 或 `CacheDataSource.Factory`）。
- MUST NOT 使用 `DefaultHttpDataSource.Factory()`：它会走 `HttpURLConnection`，绕过 `NetworkTrafficEventListener`，音频流量在 traffic stats 里完全消失，且无法享用统一连接池。
- 离线缓存场景使用 `CacheDataSource.Factory` 时，upstream MUST 仍指向 `OkHttpDataSource.Factory(@BaseOkHttp)`，不得退回 `DefaultHttpDataSource`。

## Coil ImageLoader {#rule-coil-uses-base-okhttp}

implemented_by: INC-2026-0021

- 自定义 / 注入式 `ImageLoader` MUST 通过 `OkHttpNetworkFetcherFactory(@BaseOkHttp)` 把 fetcher 显式接到 base client。
- MUST NOT 让 Coil 走默认 fetcher 创建独立 `OkHttpClient` 实例：默认 fetcher 是隐式 ktor/okhttp 客户端，图片流量会绕过 traffic event listener。
- `MusicFreeApplication` MUST 实现 `coil3.SingletonImageLoader.Factory` 把 Hilt 提供的 `ImageLoader` 暴露给 Coil singleton 入口；新增 `context.imageLoader` 使用方 MUST NOT 自行 new ImageLoader。

## 已知例外

- `AxiosShim.kt` 在 JVM 类初始化时仍保留一个 `OkHttpClient.Builder()` fallback。`MusicFreeApplication.onCreate()` 会在 Hilt 注入完成后调用 `AxiosShim.setBaseClient(...)` 覆盖，让所有插件 axios 请求最终走 base。此 fallback 仅保护测试和 Application 初始化前的极端窗口，新增代码不可仿照此 pattern。
- `core/src/main/java/com/hank/musicfree/core/network/NetworkModule.kt` 是 base provider 自身的 `OkHttpClient.Builder()` 入口；其他模块禁止复制此模式。

## 关联契约测试

- `:app:test --tests *harness.contracts.BaseOkHttpClientWiringTest`（顶层 wiring）
- `:downloader:test --tests *DownloaderClientContractTest`
- `:updater:test --tests *UpdaterClientContractTest`
- `:plugin:test --tests *PluginManagerClientContractTest`
- `:plugin:test --tests *AxiosShimClientContractTest`

## 检查方式

- 本地一键：`bash scripts/dev-harness/check.sh`
- Grep guard 触发文件：`scripts/dev-harness/grep-check.py`（incident-driven，从 `network/incidents.md` 提取 `guard.type: grep` 块）

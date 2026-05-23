# 流量统计与音频本地缓存设计

> 文档状态：当前规范（跨域：Core 网络基建 / Data / Player / Plugin / Downloader / Updater / Feature Settings / Feature Home）
> 适用范围：统一 OkHttpClient 基建、HTTP 流量按日 × 网络类型采集与可视化、ExoPlayer SimpleCache 引入
> 直接执行：是（作为 implementation plan 输入）
> 最后校验：2026-05-19
> 关联 dev-harness：[player/rules](../../dev-harness/player/rules.md)、[plugin/rules](../../dev-harness/plugin/rules.md)、[test/rules](../../dev-harness/test/rules.md)；新增 [network/rules](../../dev-harness/network/rules.md)

## 1. 背景

用户希望在 app 内提供 HTTP 流量统计（天/周/月/年/总），区分 WiFi 与移动网络，并按本地时区聚合。同时希望确认当前网络访问入口是否统一，必要时引入音频本地缓存（ExoPlayer SimpleCache）以降低重复播放流量。

调研结论（基于当前仓库代码）：

- 项目无 Retrofit / Ktor / HttpURLConnection / Volley，所有非 Media3 的 HTTP 请求都走 OkHttp，但分布在 **5 个独立 `OkHttpClient` 实例**：
  - `plugin/.../AxiosShim.kt` baseClient（2s 超时）
  - `plugin/.../WebDavShim.kt` defaultClient（10s 超时）
  - `plugin/.../PluginManager.kt` httpClient（15s 超时）
  - `downloader/.../DownloaderProvidersModule.kt`
  - `updater/.../UpdaterModule.kt`（`@UpdaterHttp` qualifier，5s/10s 超时）
- **没有任何统一的 OkHttp `EventListener` / `Interceptor` 接入点**。
- **Media3 播放走 `DefaultHttpDataSource`**（`player/source/HeaderInjectingDataSourceFactory.kt`），不经过 OkHttp。
- Coil 图片加载默认使用 `coil-network-okhttp`，走 Coil 内部 OkHttp 实例，不是项目的。
- **ExoPlayer SimpleCache 未启用**：仓库内搜不到 `SimpleCache` / `CacheDataSource` / `CacheDataSink`，每次播放都是完整网络拉流。
- Room 当前 `version = 12`（`data/.../AppDatabase.kt`）。
- 首页 Drawer 已有「我的」section（`feature/home/.../HomeDrawerNavigation.kt`），并已存在「听歌足迹」入口可作风格对齐参考。

要把流量"准确统计"，必须把这些散点收敛到统一的 OkHttp 基础设施上。

## 2. 目标与非目标

### 2.1 目标

1. 提供按 (本地日期 × 网络类型) 拆分的 HTTP 流量统计：天 / 周 / 月 / 年 / 总。
2. 区分 WiFi / 移动 / 其他三种网络类型。
3. 覆盖项目所有 HTTP 流量：插件请求、WebDAV、插件下载、音乐下载、更新检查、APK 下载、Media3 音频拉流、Coil 图片加载。
4. 引入 ExoPlayer SimpleCache（默认 512MB），降低重复播放流量；缓存命中天然不计入流量统计。
5. 把项目内 5 个 OkHttpClient 收敛到 `:core` 统一 `@BaseOkHttp` provider，统一接 `EventListener`。
6. 提供"清空音频缓存"与"清空流量统计记录"两个用户操作。
7. 落地 dev-harness 网络域规则，防止未来新增 client 绕过基建。

### 2.2 非目标

- 不做"按业务类型（音频/插件/图片）拆分流量"。
- 不做"按 host / 按插件名"拆分流量。
- 不做账单日 / 月配额警告 / 通知（YAGNI；可作 v2）。
- 不做 UTC 存储 + 本地展示的复杂时区方案，统一按设备本地时区聚合。
- 不在 `:logging` 默认采样之外新增独立的流量上报通道。

## 3. 范围

### 3.1 本次包含

| 模块 | 改动概要 |
|---|---|
| `:core` | 新增 `network/`：`@BaseOkHttp` qualifier + base `OkHttpClient` provider、`NetworkTrafficEventListener`、`NetworkTypeDetector`、`TrafficSampleSink` 接口、`TrafficSample` domain。`@ApplicationScope` qualifier 从 `:app/di/` 下移到 `:core/di/`。 |
| `:data` | 新增 `traffic/`：`TrafficDailyEntity` + DAO + Repository + `TrafficSampleSinkImpl`。`AppDatabase.version` 12→13，新增 `MIGRATION_12_13`。 |
| `:plugin` | `AxiosShim` / `WebDavShim` / `PluginManager` 三处独立 `OkHttpClient.Builder()` 改为从 `@BaseOkHttp base` 派生（`base.newBuilder()....build()`）。 |
| `:downloader` | `DownloaderProvidersModule` 提供的 client 改为从 base 派生。 |
| `:updater` | `UpdaterModule` 的 `@UpdaterHttp` provider 改为从 base 派生，调用方不变。 |
| `:player` | 新增 `MediaCacheModule`（SimpleCache 单例，512MB，目录 `getExternalFilesDir(null)/media-cache`）。改 `HeaderInjectingDataSourceFactory.kt`：`DefaultHttpDataSource.Factory()` → `OkHttpDataSource.Factory(@BaseOkHttp)`，外层包 `CacheDataSource.Factory`，使用 `dataSpec.key = mediaId` 作为缓存键（不是完整 URL）。 |
| `:feature:settings` | 新增 `traffic/`：`TrafficStatsScreen` + `TrafficStatsViewModel`。 |
| `:feature:home` | `HomeDrawerNavigation` 在「我的」section 加「流量统计」入口；扩展 `HomeDrawerUiModelTest`；新增 `FidelityAnchors.Home.DrawerMeTrafficStats`。 |
| `:app` | `MusicFreeApplication` 实现 `SingletonImageLoader.Factory`，注入项目 `ImageLoader`；新增 `ImageLoaderModule`；`AppNavHost` 挂载 `TrafficStatsRoute`；现有 `com.hank.musicfree.di.ApplicationScope` 引用改 import。 |
| `:core/navigation` | 新增 `TrafficStatsRoute(scope, anchorEpochDay)`。 |
| `:logging` | 仅新增事件名（见 §4.7），不改基建。 |
| `docs/dev-harness/` | 新增 `network/` area，更新 `INDEX.md` 与 `incidents/index.md` 占位。 |
| `scripts/dev-harness/grep-check.py` | 加守门：禁止 `OkHttpClient.Builder()` 在 `:core/network` 之外出现。 |

### 3.2 本次不包含

- 不引入第三方 chart 库，柱状图自绘 Compose Canvas。
- 不实现"小时分布"视图（v1 的"日"tab 只显示当天 WiFi/移动两行明细）。
- 不实现"账单日重置"、"流量阈值警告"、"流量限速保护"。
- 不修改任何插件 JS 行为；仅在 Native 侧统一 HTTP 基建。
- 不改变 MediaSessionService 的进程模型（仍为主进程）。
- 不在统计页放设置项（缓存大小调节延后到 v2）；本期 SimpleCache 容量硬编码 512MB。

## 4. 方案

### 4.1 架构总览

```text
┌──────────────────────────────────────────────────────────────────────┐
│                            UI Layer                                  │
│  HomeDrawer 「流量统计」入口 → TrafficStatsScreen                       │
│  5 tab (日/周/月/年/总) + 堆叠柱状图 + 明细 + 清空按钮                    │
└──────────────────────────────────────────────┬───────────────────────┘
                                               │ StateFlow<TrafficUiState>
┌──────────────────────────────────────────────▼───────────────────────┐
│                  :data → TrafficStatsRepository                      │
│   observeDaily / observeWeekly / observeMonthly /                    │
│   observeYearly / observeTotal / clearAll                            │
└──────────────────────────────────────────────┬───────────────────────┘
                                               │
                ┌──────────────────────────────┴──────────────────────────┐
                ▼ Flow<TrafficDailyEntity>                                ▼ UPSERT-accumulate
┌────────────────────────────────────────┐  ┌──────────────────────────────────────┐
│  Room: traffic_daily                   │  │ TrafficSampleSinkImpl (Singleton)    │
│  PK (local_date, network_type)         │◀─│  - Channel<TrafficSample>(512)       │
│  bytes_received, bytes_sent, updated_at│  │  - worker: 5s window or 64 batch    │
│  Migration 12→13                       │  │  - in-memory aggregate before flush  │
└────────────────────────────────────────┘  └──────────────────────────────────────┘
                                                              ▲
                                                              │ TrafficSample
┌─────────────────────────────────────────────────────────────┴────────┐
│   :core → NetworkTrafficEventListener.Factory (OkHttp EventListener) │
│  callStart: snapshot networkType                                     │
│  requestBodyEnd / responseBodyEnd: accumulate bytes                  │
│  callEnd / callFailed: flush sample                                  │
└─────────────────────────────────────────────────────────────┬────────┘
                                                              ▲
┌─────────────────────────────────────────────────────────────┴────────┐
│         :core → NetworkModule (Hilt) @BaseOkHttp OkHttpClient        │
│  base.newBuilder() 被以下五个调用方使用:                                │
│   AxiosShim / WebDavShim / PluginManager / Downloader / Updater       │
│  + Media3 OkHttpDataSource(@BaseOkHttp)                              │
│  + Coil OkHttpNetworkFetcherFactory(@BaseOkHttp)                     │
└──────────────────────────────────────────────────────────────────────┘
```

数据流（写）：HTTP 请求结束 → EventListener 累加 bytes → `TrafficSample(date, networkType, rx, tx)` 投递 Channel → worker 协程 5s 窗口或 64 条满批聚合 → DAO `UPDATE`-then-`INSERT` 累加到 `traffic_daily`。

数据流（读）：UI ViewModel 订阅 `TrafficStatsRepository.observeXxx(...)` → Room 在表变化时自动 emit 新结果 → UI 重组。

### 4.2 数据模型

**`TrafficDailyEntity`**（`:data/db/entity/`）：

```kotlin
@Entity(
    tableName = "traffic_daily",
    primaryKeys = ["local_date", "network_type"],
)
data class TrafficDailyEntity(
    @ColumnInfo(name = "local_date") val localDate: String,         // ISO yyyy-MM-dd, 本地时区
    @ColumnInfo(name = "network_type") val networkType: String,     // "WIFI" | "CELLULAR" | "OTHER"
    @ColumnInfo(name = "bytes_received") val bytesReceived: Long,
    @ColumnInfo(name = "bytes_sent") val bytesSent: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,           // epoch millis, debug 用
)
```

**设计决策**：

- 复合主键 `(local_date, network_type)`：一天最多 3 行；10 年存量 < 11k 行；天然支持 upsert 累加。
- `local_date` 存字符串而非 epochDay：SQL `substr(local_date, 1, 7)` 直接按月聚合、`substr(.., 1, 4)` 按年。
- `network_type` 存字符串枚举（`WIFI` / `CELLULAR` / `OTHER`），避免 ordinal 持久化问题。
- 不存 `bytes_total` 派生列：查询时 `bytes_received + bytes_sent`。

**DAO 查询返回的辅助类型**（`:data/db/dao/` 内）：

```kotlin
data class TrafficMonthlyRow(
    val yearMonth: String,         // "2026-05"
    val networkType: String,       // "WIFI" / "CELLULAR" / "OTHER"
    val bytesReceived: Long,
    val bytesSent: Long,
)

data class TrafficTotalRow(
    val networkType: String,
    val bytesReceived: Long,
    val bytesSent: Long,
)
```

**`TrafficDailyDao`**（`:data/db/dao/`）：

```kotlin
@Dao
abstract class TrafficDailyDao {

    @Transaction
    open suspend fun upsertAllAccumulating(rows: List<TrafficDailyEntity>) {
        rows.forEach { r ->
            val updated = upsertAccumulate(
                r.localDate, r.networkType, r.bytesReceived, r.bytesSent, r.updatedAt
            )
            if (updated == 0) insertIgnore(r)
        }
    }

    @Query("""
        UPDATE traffic_daily
        SET bytes_received = bytes_received + :rx,
            bytes_sent = bytes_sent + :tx,
            updated_at = :now
        WHERE local_date = :date AND network_type = :type
    """)
    abstract suspend fun upsertAccumulate(
        date: String, type: String, rx: Long, tx: Long, now: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(row: TrafficDailyEntity)

    @Query("""
        SELECT * FROM traffic_daily
        WHERE local_date >= :startDate AND local_date <= :endDate
        ORDER BY local_date ASC
    """)
    abstract fun observeRange(startDate: String, endDate: String): Flow<List<TrafficDailyEntity>>

    @Query("""
        SELECT substr(local_date, 1, 7) AS yearMonth,
               network_type AS networkType,
               SUM(bytes_received) AS bytesReceived,
               SUM(bytes_sent) AS bytesSent
        FROM traffic_daily
        WHERE local_date >= :startInclusive AND local_date < :endExclusive
        GROUP BY yearMonth, network_type
        ORDER BY yearMonth ASC
    """)
    abstract fun observeMonthlyRange(startInclusive: String, endExclusive: String): Flow<List<TrafficMonthlyRow>>

    @Query("""
        SELECT network_type AS networkType,
               SUM(bytes_received) AS bytesReceived,
               SUM(bytes_sent) AS bytesSent
        FROM traffic_daily
        GROUP BY network_type
    """)
    abstract fun observeTotalsByNetwork(): Flow<List<TrafficTotalRow>>

    @Query("SELECT MIN(local_date) FROM traffic_daily")
    abstract fun observeFirstRecordDate(): Flow<String?>

    @Query("DELETE FROM traffic_daily")
    abstract suspend fun clearAll()
}
```

`upsertAllAccumulating` 使用 `UPDATE` 在 SQL 内累加，避免"读-改-写"竞态；`UPDATE` 未命中行时 `INSERT IGNORE`。整体在 `@Transaction` 中执行，并发安全。

**Migration**：`data/db/migration/Migration12To13.kt`，沿用 `MIGRATION_<N>_<N+1>` 命名：

```kotlin
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS traffic_daily (
                local_date TEXT NOT NULL,
                network_type TEXT NOT NULL,
                bytes_received INTEGER NOT NULL DEFAULT 0,
                bytes_sent INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(local_date, network_type)
            )
        """.trimIndent())
    }
}
```

`AppDatabase.kt`：`version = 12 → 13`，`entities` 数组追加 `TrafficDailyEntity::class`，新增抽象 `trafficDailyDao()`。Hilt 提供 Room 实例处把 `MIGRATION_12_13` 追加到现有 `.addMigrations(...)` 调用。

### 4.3 采集核心

**`NetworkTrafficEventListener`**（`:core/network/`）：

```kotlin
class NetworkTrafficEventListener private constructor(
    private val sink: TrafficSampleSink,
    private val networkTypeDetector: NetworkTypeDetector,
    private val clock: Clock,
) : EventListener() {

    private var snapshotType: NetworkType = NetworkType.OTHER
    private var bytesSent = 0L
    private var bytesReceived = 0L

    override fun callStart(call: Call) {
        snapshotType = networkTypeDetector.current()
        bytesSent = 0L
        bytesReceived = 0L
    }
    override fun requestBodyEnd(call: Call, byteCount: Long) { bytesSent += byteCount }
    override fun responseBodyEnd(call: Call, byteCount: Long) { bytesReceived += byteCount }
    override fun callEnd(call: Call) { flush() }
    override fun callFailed(call: Call, ioe: IOException) { flush() }

    private fun flush() {
        if (bytesSent == 0L && bytesReceived == 0L) return
        sink.offer(
            TrafficSample(
                localDate = LocalDate.now(ZoneId.systemDefault()),
                networkType = snapshotType,
                bytesReceived = bytesReceived,
                bytesSent = bytesSent,
                timestampMs = clock.now(),
            )
        )
    }

    class Factory @Inject constructor(
        private val sink: TrafficSampleSink,
        private val detector: NetworkTypeDetector,
        private val clock: Clock,
    ) : EventListener.Factory {
        override fun create(call: Call): EventListener =
            NetworkTrafficEventListener(sink, detector, clock)
    }
}
```

**关键设计**：

- 用 `EventListener.Factory` 而非单例 listener；每个 Call 一个实例，per-call 状态保存在实例字段，无全局簿记。
- `callStart` 一次性快照 `networkType`：避免单次请求跨网络迁移导致计数歧义。
- 失败请求也 `flush`：实际已传输的字节运营商会计费，必须反映。
- 零字节短路：DNS 失败 / 连接前取消等场景不入库。
- listener 本身不依赖 Android Context / Logger，便于纯 JVM 单测。

**`NetworkTypeDetector`**（`:core/network/`）：

```kotlin
@Singleton
class NetworkTypeDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val cachedType = AtomicReference(NetworkType.OTHER)

    init {
        cm.activeNetwork?.let { cachedType.set(classify(cm.getNetworkCapabilities(it))) }
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(req, object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                cachedType.set(classify(caps))
            }
        })
    }

    fun current(): NetworkType = cachedType.get()

    private fun classify(caps: NetworkCapabilities?): NetworkType = when {
        caps == null -> NetworkType.OTHER
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
        else -> NetworkType.OTHER  // 蓝牙 / 以太网 / VPN / 未知
    }
}
```

`NetworkCallback` 长驻应用生命周期，状态变化时主动推送；`current()` 只读 `AtomicReference`，纳秒级开销。`init` 同步采样一次防止首次请求拿到默认 `OTHER`。

**`TrafficSampleSink`** 接口在 `:core`，实现 `TrafficSampleSinkImpl` 在 `:data`：

```kotlin
// :core/network/TrafficSampleSink.kt
interface TrafficSampleSink {
    fun offer(sample: TrafficSample)
}

// :data/traffic/TrafficSampleSinkImpl.kt
@Singleton
class TrafficSampleSinkImpl @Inject constructor(
    private val dao: TrafficDailyDao,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) : TrafficSampleSink {

    private val channel = Channel<TrafficSample>(
        capacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        scope.launch(Dispatchers.IO) {
            val pending = mutableMapOf<Pair<String, String>, Accum>()
            while (isActive) {
                val first = channel.receive()
                aggregate(pending, first)
                val deadline = clock.now() + FLUSH_INTERVAL_MS
                while (pending.size < MAX_BATCH) {
                    val remaining = deadline - clock.now()
                    if (remaining <= 0) break
                    val next = withTimeoutOrNull(remaining) { channel.receive() } ?: break
                    aggregate(pending, next)
                }
                flushPending(pending)
                pending.clear()
            }
        }
    }

    override fun offer(sample: TrafficSample) { channel.trySend(sample) }

    private fun aggregate(pending: MutableMap<Pair<String, String>, Accum>, s: TrafficSample) {
        val key = s.localDate.toString() to s.networkType.name
        val a = pending.getOrPut(key) { Accum() }
        a.rx += s.bytesReceived; a.tx += s.bytesSent
    }

    private suspend fun flushPending(pending: Map<Pair<String, String>, Accum>) {
        if (pending.isEmpty()) return
        val now = clock.now()
        val rows = pending.map { (k, a) ->
            TrafficDailyEntity(k.first, k.second, a.rx, a.tx, now)
        }
        runCatching { dao.upsertAllAccumulating(rows) }
            .onFailure { /* MfLogger.error("traffic_sink_flush_failed", it) */ }
    }

    private class Accum { var rx = 0L; var tx = 0L }

    private companion object {
        const val FLUSH_INTERVAL_MS = 5_000L
        const val MAX_BATCH = 64
    }
}
```

`@ApplicationScope` 是从 `:app` 下移到 `:core/di/` 的现有 qualifier；下移见 §4.4。

**`Clock` 来源**：本设计依赖一个可注入的时间源，便于单测控制。实施时按以下顺序选择：
1. 若 `:core` 已存在 `Clock` / `TimeSource` 接口（实施前先 grep 确认），直接复用。
2. 否则在 `:core/util/` 新增 `interface Clock { fun now(): Long }` + `@Singleton SystemClock : Clock { override fun now() = System.currentTimeMillis() }`，Hilt 绑定。

实施 plan 中作为前置步骤之一。

### 4.4 `@ApplicationScope` qualifier 下移

现状：`@ApplicationScope` 与 `CoroutineModule` 在 `app/di/`，业务模块（`:core` / `:data`）无法注入。

改造：

- 文件迁移：`app/di/ApplicationScope.kt` → `core/di/ApplicationScope.kt`；`app/di/CoroutineModule.kt` → `core/di/CoroutineModule.kt`。
- 更新所有引用方的 import：`com.hank.musicfree.di.ApplicationScope` → `com.hank.musicfree.core.di.ApplicationScope`。当前调用方包括 `PluginAutoUpdateCoordinator`、`PlaybackStartupCoordinator` 等。
- 不改变 provider 实现（`CoroutineScope(SupervisorJob() + Dispatchers.Default)`），不改变绑定语义。

### 4.5 五个 OkHttpClient 收敛到 `@BaseOkHttp`

新增 `:core/network/NetworkModule.kt`：

```kotlin
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class BaseOkHttp

@Module @InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideTrafficListenerFactory(
        sink: TrafficSampleSink,
        detector: NetworkTypeDetector,
        clock: Clock,
    ): NetworkTrafficEventListener.Factory =
        NetworkTrafficEventListener.Factory(sink, detector, clock)

    @Provides @Singleton @BaseOkHttp
    fun provideBaseOkHttpClient(
        factory: NetworkTrafficEventListener.Factory,
    ): OkHttpClient = OkHttpClient.Builder()
        .eventListenerFactory(factory)
        .build()
}
```

五个调用点改造表：

| 当前位置 | 当前实现 | 改造后 |
|---|---|---|
| `plugin/.../AxiosShim.kt:59-62` | `OkHttpClient.Builder()....build()`（2s） | 注入 `@BaseOkHttp OkHttpClient`；`base.newBuilder().callTimeout(2s)....build()` |
| `plugin/.../WebDavShim.kt:120-125` | 同上（10s） | `base.newBuilder().callTimeout(10s)....build()` |
| `plugin/.../PluginManager.kt:153-157` | 同上（15s） | `base.newBuilder().callTimeout(15s)....build()` |
| `downloader/.../DownloaderProvidersModule.kt:44-45` | `@Provides` 独立 client | 改为依赖 `@BaseOkHttp base`，从 base 派生 |
| `updater/.../UpdaterModule.kt:50-54` | `@UpdaterHttp` 独立 client（qualifier 同文件内定义） | 保留 `@UpdaterHttp` qualifier；其 provider 改为 `base.newBuilder().connectTimeout(5s).readTimeout(10s)....build()` |

所有派生 client 自动继承 `eventListenerFactory`；无需各调用方显式接 listener。

### 4.6 Media3 + Coil 改造

**新增依赖**（`gradle/libs.versions.toml`）：

- `androidx.media3:media3-datasource-okhttp` — 让 Media3 走 OkHttp。
- `androidx.media3:media3-database` — `StandaloneDatabaseProvider`，供 SimpleCache 使用。

**`MediaCacheModule`**（`:player/di/`）：

```kotlin
@Module @InstallIn(SingletonComponent::class)
object MediaCacheModule {

    @Provides @Singleton
    fun provideMediaCache(
        @ApplicationContext context: Context,
    ): SimpleCache {
        val cacheDir = context.getExternalFilesDir(null)
            ?.resolve("media-cache")
            ?: context.cacheDir.resolve("media-cache")
        cacheDir.mkdirs()
        return SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(DEFAULT_MEDIA_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }

    const val DEFAULT_MEDIA_CACHE_BYTES = 512L * 1024 * 1024  // 512 MB
}
```

**改造 `HeaderInjectingDataSourceFactory.kt`**（`:player/source/`）：

```kotlin
class HeaderInjectingDataSourceFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    @BaseOkHttp private val okHttpClient: OkHttpClient,
    private val simpleCache: SimpleCache,
    private val trackHeaderRegistry: TrackHeaderRegistry,
    private val userAgentProvider: UserAgentProvider,
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(userAgentProvider.value())
        val baseFactory = DefaultDataSource.Factory(context, httpFactory)
        val resolving = ResolvingDataSource.Factory(baseFactory) { dataSpec ->
            val headers = trackHeaderRegistry.headersForUri(dataSpec.uri).orEmpty()
            val stableKey = trackHeaderRegistry.cacheKeyForUri(dataSpec.uri)
                ?: dataSpec.uri.toString()
            dataSpec.buildUpon()
                .setHttpRequestHeaders(headers)
                .setKey(stableKey)
                .build()
        }
        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(resolving)
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory()
                    .setCache(simpleCache)
                    .setFragmentSize(C.LENGTH_UNSET.toLong())
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .createDataSource()
    }
}
```

**关键 detail**：

- `OkHttpDataSource.Factory(@BaseOkHttp)` 让音频请求经统一 client，自动接 EventListener。
- `dataSpec.setKey(stableKey)` 使缓存命中按业务 ID 而非完整 URL，规避签名 URL（`Expires`/`Signature`/`token`）导致的同曲不同 key。需要在 `TrackHeaderRegistry` 新增 `fun cacheKeyForUri(uri: Uri): String?`，返回 `mediaId`。
- `FLAG_IGNORE_CACHE_ON_ERROR`：缓存文件损坏时降级直走 upstream，不影响播放。
- `setUserAgent` 配合 OkHttpDataSource 与现有 per-track headers 双保险，保持现有插件 UA 行为。

**MediaCacheStore**（`:player/cache/`）封装 SimpleCache 给 UI 调用。由于 `SimpleCache` 是 `@Singleton` 注入到 `HeaderInjectingDataSourceFactory`，热替换实例会让旧引用悬空。采用 **holder 模式**：

```kotlin
@Singleton
class SimpleCacheHolder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val ref = AtomicReference<SimpleCache?>(null)
    private val initFailed = AtomicBoolean(false)

    /** 返回当前可用的 SimpleCache；若初始化失败则永久返回 null，调用方需 fallback */
    val current: SimpleCache? get() = ref.get() ?: synchronized(this) {
        if (initFailed.get()) return null
        ref.get() ?: tryCreate()?.also { ref.set(it) }
    }

    fun resetForClear(): SimpleCache? {
        synchronized(this) {
            ref.get()?.release()
            ref.set(null)
            cacheDir().deleteRecursively()
            return tryCreate()?.also { ref.set(it) }
        }
    }

    private fun tryCreate(): SimpleCache? = runCatching {
        SimpleCache(
            cacheDir().apply { mkdirs() },
            LeastRecentlyUsedCacheEvictor(DEFAULT_MEDIA_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }.onFailure {
        initFailed.set(true)
        /* MfLogger.error("media_cache_init_failed", it) */
    }.getOrNull()

    private fun cacheDir(): File =
        context.getExternalFilesDir(null)?.resolve("media-cache")
            ?: context.cacheDir.resolve("media-cache")
}

interface MediaCacheStore {
    val usedBytesFlow: Flow<Long>   // 每 2s 读 holder.current.cacheSpace
    suspend fun clear()              // 调用 holder.resetForClear()
}
```

移除 §4.6 的 `MediaCacheModule.provideMediaCache(...)` 单例 provider，改由 `HeaderInjectingDataSourceFactory` 直接注入 `SimpleCacheHolder`，并在 `createDataSource()` 内每次拿 `holder.current`（不缓存到字段）。这样 `clear()` 后下一次 `createDataSource()` 自动使用新实例。

`HeaderInjectingDataSourceFactory.createDataSource()` 内 fallback 逻辑：

```kotlin
val cache = simpleCacheHolder.current
return if (cache != null) {
    CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(resolving)
        .setCacheWriteDataSinkFactory(CacheDataSink.Factory().setCache(cache)...)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        .createDataSource()
} else {
    resolving.createDataSource()
}
```

`clear()` 前置条件：要求"未在播放"才允许（UI 端检查 `PlayerController.isPlaying`，播放中按钮 disable + 文本提示「播放中无法清空，请暂停后重试」）。

**Coil 改造**：

新增 `:core/coil/ImageLoaderModule.kt`（或 `:app/di/`，依据 Hilt 模块边界）：

```kotlin
@Module @InstallIn(SingletonComponent::class)
object ImageLoaderModule {

    @Provides @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @BaseOkHttp okHttpClient: OkHttpClient,
    ): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(okHttpClient)) }
        .build()
}
```

`MusicFreeApplication` 实现 `SingletonImageLoader.Factory`：

```kotlin
@HiltAndroidApp
class MusicFreeApplication : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var imageLoader: ImageLoader
    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader
}
```

Coil 3 在首次 `SingletonImageLoader.get(context)` 时调用此 factory；UI 中现有 `AsyncImage` / `rememberAsyncImagePainter` 全部走 singleton，自动接基建。

### 4.7 UI：路由 + Drawer 入口 + 统计页

**路由**（`:core/navigation/Routes.kt`）：

```kotlin
@Serializable
data class TrafficStatsRoute(
    val scope: String = "MONTH",      // "DAY" | "WEEK" | "MONTH" | "YEAR" | "TOTAL"
    val anchorEpochDay: Long = -1L,   // -1 表示"今天"
)
```

风格对齐已有的 `ListenStatsRoute`。

**Drawer 入口**（`:feature:home/.../HomeDrawerNavigation.kt`）：

- `HomeDrawerAction` 新增 `data object OpenTrafficStats`。
- 「我的」section 在「听歌足迹」之后追加「流量统计」`HomeDrawerItemUiModel`。
- `HomeIcons` 新增 `DrawerTrafficStats`（drawable 资源 `ic_home_data_usage`，Material Icons outlined "data usage"）。
- `FidelityAnchors.Home` 新增 `DrawerMeTrafficStats`。
- `HomeScreen` 新增 `onNavigateToTrafficStats: () -> Unit` 参数；`HomeDrawerAction.OpenTrafficStats` 分支调用。
- `:feature:home/navigation/HomeNavigation.kt` 透传至 `:app/navigation/AppNavHost.kt`。

**`TrafficStatsScreen`**（`:feature:settings/.../traffic/`）：

页面结构：

1. `MusicFreeTopAppBar`（标题"流量统计"，左侧返回箭头）。
2. 顶部卡片：当前 tab 总流量大字号 + WiFi/移动副标题。
3. `ScrollableTabRow`：日 / 周 / 月 / 年 / 总。
4. 前后翻页箭头（仅日/周/月/年，按 `anchorEpochDay` shift）。
5. 堆叠柱状图（Compose Canvas 自绘，WiFi 蓝 + 移动橙，tap 显示 tooltip）。
6. 明细 `LazyColumn`（按日期降序，每行 WiFi/移动/合计）。
7. 底部操作区：「清空音频缓存（已用 N MB）」按钮、「清空流量统计记录」按钮。

ViewModel：

```kotlin
@HiltViewModel
class TrafficStatsViewModel @Inject constructor(
    private val repo: TrafficStatsRepository,
    private val cacheStore: MediaCacheStore,
    private val playerController: PlayerController,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route: TrafficStatsRoute = savedStateHandle.toRoute()
    private val tab = MutableStateFlow(TrafficScope.valueOf(route.scope))
    private val anchor = MutableStateFlow(resolveAnchor(route))

    val uiState: StateFlow<TrafficUiState> = combine(tab, anchor) { t, a -> t to a }
        .flatMapLatest { (t, a) -> repo.observeFor(t, a) }
        .map { it.toUi() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrafficUiState.Loading)

    val cacheUsage: StateFlow<Long> =
        cacheStore.usedBytesFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val canClearCache: StateFlow<Boolean> =
        playerController.stateFlow.map { !it.isPlaying }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun selectTab(t: TrafficScope) { tab.value = t }
    fun shiftAnchor(direction: Int) { anchor.update { it.shift(tab.value, direction) } }
    fun clearAllRecords() = viewModelScope.launch { repo.clearAll() }
    fun clearMediaCache() = viewModelScope.launch { cacheStore.clear() }
}
```

Chrome / 状态栏 / `rpx(...)` 适配：全部走 `:core` UI harness（`MusicFreeScreenScaffold` + `MusicFreeTopAppBar` + `MusicFreeStatusBarChrome`），遵守 [ui/rules.md](../../dev-harness/ui/rules.md)。

### 4.8 日志事件（按 `:logging` 模块规范）

| 事件名 | 触发点 | 字段 |
|---|---|---|
| `traffic_sink_started` | `TrafficSampleSinkImpl.init` worker 启动 | — |
| `traffic_sink_flush_succeeded` | `flushPending` 成功 | `rowsCount`, `bytesTotal`, `durationMs` |
| `traffic_sink_flush_failed` | DAO 抛异常 | `error`, `rowsCount` |
| `traffic_sink_buffer_overflow` | Channel 满 DROP_OLDEST | `droppedCount`（节流上报，避免泛滥） |
| `media_cache_init_failed` | SimpleCache 创建失败 | `error`, `cacheDirPath` |
| `media_cache_cleared` | 用户点清空音频缓存 | `bytesFreed`, `durationMs` |
| `traffic_stats_cleared` | 用户点清空流量记录 | `rowsDeleted` |
| `traffic_stats_screen_opened` | 进入统计页 | `scope` |

所有事件名采用 snake_case，与项目现有日志风格对齐。日志默认开启。

## 5. 测试策略

### 5.1 单元测试

| 模块 | 类 | 覆盖 |
|---|---|---|
| `:core` | `NetworkTrafficEventListenerTest` | 单 call 字节累加、callFailed 仍 flush、零字节短路、Factory 每次返回新实例 |
| `:core` | `NetworkTypeDetectorTest` | classify(caps) 分支（WiFi / Cellular / 蓝牙 / null）。Robolectric 注入 fake `ConnectivityManager` |
| `:data` | `TrafficSampleSinkImplTest` | `runTest` + Turbine：sample 投递 → 5s 后 flush → DAO 调用；64 条满批立即 flush；DAO 失败时不 crash |
| `:data` | `TrafficDailyDaoTest` | Room in-memory：upsert 累加正确、并发顺序、空表查询、清空 |
| `:data` | `Migration12To13Test` | `MigrationTestHelper`：12 → 13 真实 sqlite，建表成功，老 row 不丢 |
| `:feature:settings` | `TrafficStatsViewModelTest` | tab/anchor 切换、clear 流程、播放中按钮 disable |
| `:feature:settings` | `TrafficScopeShiftTest` | 日/周/月/年 翻页边界日期计算（含跨年） |
| `:feature:settings` | `TrafficUiMapperTest` | Repository 输出 → UI 模型映射 |
| `:feature:home` | `HomeDrawerUiModelTest`（扩展） | 「我的」section 含「流量统计」项；anchor 正确 |

### 5.2 集成测试（`-Pintegration`，MockWebServer）

| 类 | 覆盖 |
|---|---|
| `BaseOkHttpClientWiringTest` | 注入的 `@BaseOkHttp client.eventListenerFactory` 非 null 且类型正确 |
| `PluginModuleClientContractTest` | AxiosShim / WebDavShim / PluginManager 持有 client 的 eventListenerFactory `===` base |
| `DownloaderClientContractTest` | downloader 的 client eventListenerFactory `===` base |
| `UpdaterClientContractTest` | `@UpdaterHttp` client 的 eventListenerFactory `===` base |
| `Media3TrafficIntegrationTest` | 播放 5MB mock 音频 → traffic_daily 当天 WIFI 行 `bytes_received` ≈ 5MB（±5%） |
| `CoilTrafficIntegrationTest` | 加载 200KB 图 → 当天行多 ≈ 200KB |
| `CacheHitNoTrafficTest` | 播放同一首歌两次 → 第二次 traffic_daily 不增加（SimpleCache 命中） |
| `MediaCacheKeyStabilityTest` | 两个不同签名 URL 但同 mediaId → 第二次命中缓存 |

### 5.3 仪器测试（`connectedAndroidTest`）

| 类 | 覆盖 |
|---|---|
| `HomeDrawerTrafficStatsEntryTest` | Drawer 点「流量统计」跳到 `TrafficStatsRoute` |
| `TrafficStatsScreenSmokeTest` | 5 tab 切换、翻页、清空按钮无 crash；播放中清空按钮 disable |

## 6. dev-harness 规则更新

### 6.1 新增 `network` area

`docs/dev-harness/network/rules.md`：

- **MUST**：所有新创建的 `OkHttpClient` 实例必须从 `@BaseOkHttp` 派生（`base.newBuilder()`），不得直接 `OkHttpClient.Builder()` 实例化。
- **MUST**：所有需要发起 HTTP 的模块通过 Hilt 注入 `@BaseOkHttp OkHttpClient`。
- **MUST NOT**：不得在 `:core/network` 之外注册新的 `EventListener.Factory`（统一通过 base）。
- **MUST**：新增 `DataSource.Factory` 必须使用 `OkHttpDataSource.Factory(@BaseOkHttp)`，不得使用 `DefaultHttpDataSource.Factory`。

`docs/dev-harness/network/incidents.md` 首版仅占位（无历史 incident）。

`docs/dev-harness/INDEX.md` 域规则表追加：

```
| 网络 / HTTP 流量基建 | [network/rules.md](./network/rules.md) | [network/incidents.md](./network/incidents.md) |
```

### 6.2 静态守门

`scripts/dev-harness/grep-check.py` 新增规则：

- 全仓搜 `OkHttpClient\.Builder\(\)`，命中位置必须在 `:core/network/NetworkModule.kt` 或允许列表内，否则 fail。
- 全仓搜 `DefaultHttpDataSource\.Factory\(\)`，命中即 fail（提示改用 `OkHttpDataSource.Factory`）。

允许列表通过文件路径精确匹配，列入 `grep-check.py` 配置。

### 6.3 Contract test 接入 check.sh

`scripts/dev-harness/check.sh` 现有 "Contract tests (JVM)" 步骤已经包含 `:app:testDebugUnitTest :plugin:testDebugUnitTest :feature:player-ui:testDebugUnitTest`，匹配 `*harness.contracts.*`。新增契约测试归位：

- `BaseOkHttpClientWiringTest` → `:app/src/test/.../harness/contracts/`（已覆盖）
- `PluginModuleClientContractTest` → `:plugin/src/test/.../harness/contracts/`（已覆盖）
- `DownloaderClientContractTest` → `:downloader/src/test/.../harness/contracts/`：需在 check.sh "Contract tests (JVM)" 行追加 `:downloader:testDebugUnitTest`；同步在 "Compile-only test sources" 行确认 `:downloader:compileDebugUnitTestKotlin` 已存在（当前已存在）。
- `UpdaterClientContractTest` → `:updater/src/test/.../harness/contracts/`：需在 check.sh "Contract tests (JVM)" 行追加 `:updater:testDebugUnitTest`；同步在 "Compile-only test sources" 行加 `:updater:compileDebugUnitTestKotlin`（实施前先 grep 确认 `:updater` 模块的 build.gradle.kts 是否启用了单测，未启用则先启用）。

实施 plan 中作为接入步骤，按 `:downloader` / `:updater` 实际是否启用单测决定具体顺序。

## 7. 错误处理与回归风险

### 7.1 错误处理

| 故障 | 处理 |
|---|---|
| DAO `upsertAllAccumulating` 抛异常 | catch → `traffic_sink_flush_failed` 日志；当批数据丢弃不重入队 |
| Channel 满 | `DROP_OLDEST` 自动丢；节流上报 `traffic_sink_buffer_overflow` |
| 启动瞬间 networkType 还是 OTHER | `init {}` 同步采样一次将窗口压缩到几个请求；最坏少量请求计入 OTHER 桶 |
| SimpleCache 初始化失败 | `SimpleCacheHolder.createInternal()` catch 异常 → 记录 `media_cache_init_failed` 日志 → holder 内部进入 disabled 状态，`current` 返回 null；`HeaderInjectingDataSourceFactory` 检测到 null 时跳过 `CacheDataSource.Factory` 包装直接返回 `resolving.createDataSource()`（即仅走 OkHttpDataSource）。统计仍生效，仅丧失本地缓存能力。 |
| CacheDataSource 读到损坏文件 | `FLAG_IGNORE_CACHE_ON_ERROR` 自动 fallback upstream |
| EventListener `callFailed` 时部分字节已传输 | 仍计入（与运营商口径一致） |
| 系统时钟回退导致 `local_date` 错位 | 不处理；单次 sample 的 date 在 sample 创建时固定 |

### 7.2 回归风险

| 风险 | 验证 |
|---|---|
| `AxiosShim` 派生 client 后超时行为变化 | `:plugin -Pintegration` 跑全量 MockWebServer 测试 |
| `OkHttpDownloader` 依赖 client 内部字段（`dispatcher` / `connectionPool`） | 改造时审查；不直接引用私有字段 |
| `@UpdaterHttp` 调用点不变 | 仅 provider 内部派生方式变化，调用方 API 兼容 |
| Media3 切 `OkHttpDataSource` 后插件 UA 失效 | 双保险：`OkHttpDataSource.Factory.setUserAgent` + 现有 per-track header 注入；新增集成测试断言出口 UA |
| Coil 切换自定义 ImageLoader 后默认 disk cache 丢失 | 显式在 `ImageLoader.Builder` 配 `.diskCache(DiskCache.Builder(...).build())` 与 Coil 默认值对齐 |
| SimpleCache 缓存命中率低（同曲不同签名 URL） | `dataSpec.setKey(mediaId)`；`MediaCacheKeyStabilityTest` 验证 |
| SimpleCache 单例：另一进程意外开第二个实例 | MediaSessionService 与 app 同进程（`AndroidManifest` 不带 `android:process`），仅一个 Hilt singleton |

### 7.3 回滚策略

每层独立可回滚：

- **流量统计层**：不挂 `eventListenerFactory(...)`，base client 退化为普通 OkHttpClient；统计页隐藏入口。
- **SimpleCache 层**：`HeaderInjectingDataSourceFactory` 移除外层 `CacheDataSource.Factory`，保留 `OkHttpDataSource.Factory` 继续走 OkHttp 统计。
- **OkHttpDataSource 层**：替换回 `DefaultHttpDataSource.Factory()`，仅丢失音频流量统计，其他模块统计不受影响。
- **Client 收敛层**：恢复模块独立 `OkHttpClient.Builder()`，统计仅覆盖未恢复模块。

## 8. 实施建议顺序

1. **基建优先**：`:core/network` + `:core/di` qualifier 下移 + Room migration（不依赖任何业务改造，独立可发版；EventListener 接但仅采集 0 流量）。
2. **Sink + Repository + 数据通路**：能写入数据库、能查询，但 UI 尚未挂载。
3. **五个 client 收敛**：`:plugin` / `:downloader` / `:updater` 改造；契约测试落地；此时已可统计大部分流量。
4. **Coil 改造**：`SingletonImageLoader.Factory` + `ImageLoaderModule`；图片流量计入。
5. **Media3 + SimpleCache**：`OkHttpDataSource` + `MediaCacheModule` + `CacheDataSource` + cacheKey；音频流量计入 + 缓存生效。
6. **UI**：`TrafficStatsScreen` + Drawer 入口 + 翻页柱状图。
7. **dev-harness 守门**：rules.md + grep-check + contract tests 接入 check.sh。

每步独立可验收，独立可回滚。

## 9. 关联

- 项目入口：[AGENTS](../../../AGENTS.md)
- Dev-harness 总入口：[dev-harness/INDEX](../../dev-harness/INDEX.md)
- RN 参考：`../MusicFree/src/`（流量统计 RN 侧不存在等价功能，本设计为 Android 独有）

# Downloading（离线下载）功能设计

- **状态**：草案，待用户最终评审
- **创建日期**：2026-05-04
- **作者**：Claude（与用户 brainstorm 协作）
- **范围**：在 MusicFreeAndroid 实现 RN 侧 `pages/downloading` + `core/downloader.ts` 对应的离线下载能力（v1）
- **目标读者**：本仓库 AI 编码助手与人类工程师

## 1. 背景

RN 版 MusicFree 提供 `core/downloader.ts`（452 行）+ `pages/downloading`（~105 行）以支持插件音源的本地下载。Android 侧目前**完全没有下载基础设施**：搜索结果里出现的 `exo_download_*` 字符串均来自 ExoPlayer 自带资源，并非项目自身实现。

CLAUDE.md "当前优先事项"将 `downloading` 与 `setCustomTheme` 列为 19/20 路由覆盖中仍缺的两页；本设计聚焦 `downloading`，因为它不仅补一个页面，还补齐一整个能力域，并支撑 CLAUDE.md 优先事项 #3（端到端链路：插件 → 搜索 → 播放 → **下载** → 本地）。

`setCustomTheme` 留待下一轮独立设计，不在本设计范围内。

## 2. 决策摘要（brainstorm 结论）

| 维度 | 选择 | 备注 |
|---|---|---|
| 改良幅度 | B：必要项 + 必要的人机救济 | 持久化 + 前台 Service + 取消下载中任务 + 失败重试 + 「全部清空 / 全部重试」；不做暂停/继续 |
| 文件存储位置 | B：公共 `Music/MusicFree/` 目录 + MediaStore | 卸载不丢，本地页天然集成；轻量映射表承担"已下载"判定 |
| 执行引擎 | A：ForegroundService + OkHttp | 单一长生命周期 Service 持有队列、并发、状态机；进度通过 Flow 推送 |
| v1 入口范围 | B：MusicDetail + MusicListEditor + 全局长按菜单（`MusicItemOptionsSheet`） | 不做"下载整张专辑/歌单"，留 v2 |
| 设置项 | C：`maxDownload` + `useCellularDownload` + 独立 `defaultDownloadQuality` | 不做 asc/desc 开关；回退固定 desc |
| Downloading 页入口 | a：仅 LocalScreen AppBar 图标（与 RN 一致） | 不挂抽屉，避免污染 |
| 完成项策略 | x：完成即从队列移除（与 RN 一致） | 已下载项由本地页 + 角标承担显示 |

## 3. 模块划分与依赖方向

新增独立模块 **`:downloader`**，与 `:data / :player / :plugin` 同层：

```
:app
 └── :feature:home (Local 页 / MusicDetail / MusicListEditor / Downloading 页 UI)
       └── :downloader  ← 新增
             ├── :data    (Room: DownloadTaskDao, DownloadedTrackDao)
             ├── :plugin  (PluginApi.getMediaSource for quality fallback)
             └── :core    (rpx, theme, navigation routes, MusicItemOptionsSheet)
```

`:downloader` 对外仅暴露 `Downloader` 接口（Hilt `@Singleton`）：

```kotlin
interface Downloader {
    val tasks: StateFlow<List<DownloadTaskUi>>           // Downloading 页订阅
    val downloadedKeys: StateFlow<Set<MediaKey>>         // 列表角标订阅
    fun enqueue(items: List<MusicItem>, quality: PlayQuality? = null)
    fun cancel(key: MediaKey)
    fun retry(key: MediaKey)
    fun clearFailed()
    fun retryAllFailed()
}
```

**关键约束**：
- Service 是 `Downloader` 实现的私有运行时——调用方只见接口，不见 Service。
- `DownloadingViewModel` 在 `:downloader` 内提供（薄包装），UI 本体在 `:feature:home/downloading`。
- `MediaKey` 复用 `${platform}@${id}` 字符串，与 RN `getMediaUniqueKey` 一致。

## 4. 数据模型与状态机

### 4.1 Room 表（位于 `:data`）

加在 `:data` 而非 `:downloader`，避免每个 feature 模块为查"是否已下载"而强行依赖 `:downloader`。

```kotlin
@Entity(tableName = "download_tasks", primaryKeys = ["id", "platform"])
data class DownloadTaskEntity(
    val id: String,
    val platform: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artwork: String?,
    val durationMs: Long,
    val targetQuality: String,           // "super"/"high"/"standard"/"low"
    val status: String,                  // PENDING/PREPARING/DOWNLOADING/FAILED
    val errorReason: String?,            // FailToFetchSource / NoWritePermission / Unknown / NotAllowToDownloadInCellular
    val resolvedUrl: String?,
    val resolvedHeadersJson: String?,
    val fileSize: Long?,
    val downloadedSize: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "downloaded_tracks", primaryKeys = ["id", "platform"])
data class DownloadedTrackEntity(
    val id: String,
    val platform: String,
    val mediaStoreUri: String,            // content://media/external/audio/media/12345
    val relativePath: String,             // "Music/MusicFree/<filename>.mp3"
    val mimeType: String,
    val quality: String,
    val sizeBytes: Long,
    val downloadedAt: Long,
)
```

DAO 方法（最小集）：

- `DownloadTaskDao`：`observeAll()`, `findNextPending()`, `markPreparing(key)`, `markDownloading(key, fileSize)`, `markFailed(key, reason)`, `updateProgress(key, downloaded)`, `delete(key)`, `deleteAllFailed()`, `resetInflightToPending()`（启动时调用）
- `DownloadedTrackDao`：`observeKeys()`, `exists(key)`, `findUri(key)`, `insert(entity)`, `delete(key)`

**Migration 策略**：开发期直接修改 entity，不写 `Migration` 对象（与项目已确立的开发期 schema 演进策略一致）。

### 4.2 状态机

```
        enqueue
           │
           ▼
       PENDING ──cancel──► (delete)
           │
       slot available
           │
           ▼
      PREPARING ──getMediaSource fallback──┐
           │                                │
           │ all qualities fail             │
           ├──► FAILED (FailToFetchSource) ─┤
           │                                │
           │ url ok                         │
           ▼                                │
     DOWNLOADING ──cancel──► (delete cache, delete row)
           │   │
           │   └─ network IO error / file write fail
           │       │
           │       ▼
           │    FAILED (Unknown / NoWritePermission)
           │
           ▼
       (commit MediaStore + insert downloaded_tracks + delete task row)
```

**与 RN 的差异**：
1. 删除 `Completed` 状态——完成即移除 `download_tasks` 行 + 写 `downloaded_tracks`，由 `downloadedKeys` flow 自然驱动 UI 反馈。
2. `errorReason` 持久到 Room（RN 仅在内存），重启后仍可见。
3. 新增 `NotAllowToDownloadInCellular` 作为 `errorReason`，对应"网络中切换到非授权移动网络时把当前任务退回 PENDING、Toast 提示"的场景（RN 没有这个语义，因为 RN 不在下载中处理网络切换）。

### 4.3 持久化与重启恢复

启动时 `Downloader` 单例初始化做：

1. 调 `taskDao.resetInflightToPending()`：将所有 `PREPARING` / `DOWNLOADING` 行改回 `PENDING`，清空 `resolvedUrl/resolvedHeaders`（解析过的 URL 一般有有效期，不能复用）。
2. 删除 `cacheDir/download/` 下任何残留文件。
3. **不自动启动 Service**——必须由用户行为触发：
   - 用户进入 Downloading 页，看到 PENDING/FAILED 任务，点"全部重试"或单条重试。
   - 用户从 UI 入口新加任务。
4. 这与"重启不偷偷下载"的引擎选择 A 对齐。

## 5. DownloadService、并发、网络、重试

### 5.1 Service

```kotlin
@AndroidEntryPoint
class DownloadService : Service() {
    @Inject lateinit var engine: DownloadEngine
    @Inject lateinit var notifier: DownloadNotifier

    override fun onStartCommand(intent, flags, id): Int {
        startForeground(NOTIF_ID, notifier.buildOngoing(engine.currentSnapshot()))
        return START_NOT_STICKY     // 不让系统在被杀后偷偷重启
    }
}
```

- `START_NOT_STICKY` 与"重启不偷偷下载"策略一致。
- 通知通道 `download_progress`（`IMPORTANCE_LOW`，不打扰）。
- AndroidManifest 声明 `foregroundServiceType="dataSync"`（Android 14+ 必需）。
- `INTERNET`、`POST_NOTIFICATIONS`（Android 13+ 运行时申请）、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC` 权限按需在 `:downloader` 模块的 manifest 声明。

### 5.2 引擎（`DownloadEngine`，Service 共置）

```kotlin
@Singleton
class DownloadEngine @Inject constructor(
    private val taskDao: DownloadTaskDao,
    private val downloadedDao: DownloadedTrackDao,
    private val pluginManager: PluginManager,
    private val mediaStoreWriter: MediaStoreMusicWriter,
    private val networkMonitor: NetworkMonitor,
    private val prefs: DownloadPreferences,
    private val okHttp: OkHttpClient,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    private val inflight = ConcurrentHashMap<MediaKey, Job>()

    val tasks: StateFlow<List<DownloadTaskUi>>       // 由 taskDao.observeAll() + inflight 进度合并
    val downloadedKeys: StateFlow<Set<MediaKey>>     // 来自 downloadedDao.observeKeys()
}
```

### 5.3 调度循环

每次任务变化或槽位释放都触发：

```
acquire mutex
  count = inflight.size
  cap = prefs.maxDownload (default 3, clamp 1..10)
  if count >= cap: return
  next = taskDao.findNextPending() ?: { stopServiceIfIdle(); return }
  taskDao.markPreparing(next.key)
  inflight[next.key] = scope.launch { runOne(next) }
release mutex
```

### 5.4 单任务 `runOne`

1. **质量回退解析**——按 `desc` 顺序从 `targetQuality` 起向下尝试 `super → high → standard → low`，第一个 URL 非空的胜出（细节见 § 7.3）。全部失败则 `FAILED(FailToFetchSource)`。
2. 写 `resolvedUrl + resolvedHeaders`，状态置 `DOWNLOADING`。
3. OkHttp `Request` 带 headers，下载到 `context.cacheDir/download/<nanoid>.<ext>`。
4. 进度回调节流：≥ 250ms 或 ≥ 64 KB 才推送一次 `tasks` flow（避免 UI 重组风暴）。
5. 成功后调用 `MediaStoreMusicWriter.commit(...)` 拿到 `Uri` → 写 `downloaded_tracks` → 删 `download_tasks` 行 → 删 cache 文件。
6. 任意环节抛错：删 cache 文件，DB 写 `FAILED + errorReason`，从 `inflight` 移除，触发下一轮调度。

### 5.5 并发与互斥

- `mutex` 只保护"取下一个任务 + 标 Preparing + 启动 Job"，**不**覆盖整个下载，不阻塞并发。
- 同一 `MediaKey` 永不同时存在两个 Job——`inflight` 是真理。
- `retry()` 内部检查 `inflight.contains(key)`，已在跑就忽略。

### 5.6 网络监听 & 流量闸门

`NetworkMonitor`（`:downloader` 内部）封装 `ConnectivityManager.NetworkCallback`，暴露 `Flow<NetworkState>`：`Offline / Wifi / Cellular`。

闸门时机：

- **`enqueue` 入口**：`Offline` 时拒绝整批，emit `DownloadEvent.Toast(NetworkOffline)`；`Cellular && !prefs.useCellularDownload` 时同样拒绝。
- **下载途中**网络从 WiFi 切到 Cellular 且未授权：**取消**当前 inflight 任务，状态回 `PENDING`（不是 `FAILED`，因为不是这首歌的错），停 Service。Toast 提示"已暂停下载，请在设置中允许移动网络下载"。
- **完全离线**途中：当前下载会自然 IO 异常 → 标 `FAILED(Unknown)`，调度循环遇到 `Offline` 时不取下一首。

### 5.7 取消 / 重试 / 清理

- `cancel(key)`：`inflight[key]?.cancel()` → DB 删行 → 删 cache 文件。`PENDING/FAILED` 直接删行。
- `retry(key)`：`FAILED` 行 reset 为 `PENDING`、清错误原因，触发调度。
- `clearFailed()`：`taskDao.deleteAllFailed()`。
- `retryAllFailed()`：批量 reset 后触发调度。
- 完成 toast：当 `inflight.isEmpty() && pending == 0` 且本次启动至少完成 1 首时，emit `DownloadQueueCompleted` 事件 → 一次性 Toast"下载任务已完成"。

## 6. 文件存储与 MediaStore 集成

### 6.1 写入路径

```kotlin
class MediaStoreMusicWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun commit(
        cacheFile: File,
        displayName: String,
        mimeType: String,
        relativePath: String,
        sizeBytes: Long,
    ): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Audio.Media.SIZE, sizeBytes)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values,
        ) ?: throw IOException("MediaStore.insert returned null")
        resolver.openOutputStream(uri)!!.use { out -> cacheFile.inputStream().use { it.copyTo(out) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        uri
    }
}
```

`IS_PENDING` 两阶段写入：写入期间其它 App（包括 MediaStore 扫描器）看不到半成品文件，避免本地页扫到 0 字节占位。

### 6.2 文件命名

与 RN `escape(platform)@escape(id)@escape(title)@escape(artist)` 同款：先对每个字段应用 `escape`（含 `@`），再用裸 `@` 拼接，使分隔符语义不受字段内容污染。

```kotlin
// 替换 Android 文件名非法字符 + 把 @ 也清掉，避免污染分隔符语义
private val ESCAPE_RE = Regex("""[\\/:*?"<>|@]""")
private fun escape(s: String) = s.replace(ESCAPE_RE, "_")

fun displayName(item: MusicItem, ext: String): String {
    val base = "${escape(item.platform)}@${escape(item.id)}@${escape(item.title)}@${escape(item.artist)}"
        .take(200)
    return "$base.$ext"
}
```

### 6.3 扩展名 & MIME

- 优先从解析后的 URL 末尾匹配（与 RN 同正则）：`mp3 / flac / wma / m4a / aac / ogg / wav / ape`。
- 不在白名单内则回退 `mp3`。
- MIME 用静态映射表：`mp3 → audio/mpeg`、`flac → audio/flac`、`m4a → audio/mp4`、`aac → audio/aac`、`ogg → audio/ogg`、`wav → audio/wav`、`wma → audio/x-ms-wma`、`ape → audio/x-ape`。

### 6.4 "已下载"判定 & 去重

- `enqueue` 时先查 `downloadedDao.exists(key)` → 已存在直接跳过（修掉 RN 那个 TODO："如果已经下载了也应该返回 false"）。
- `downloadedKeys: StateFlow<Set<MediaKey>>` 暴露给所有列表组件。

### 6.5 用户清理外部文件后的同步

边缘场景：用户在系统文件 App 删了某首歌，但 `downloaded_tracks` 表里还有记录——下次 `enqueue` 会被去重逻辑挡掉。

处理：`enqueue` 命中已下载记录时，先 `resolver.openInputStream(uri)` 探测一次；若 `FileNotFoundException` → 删 `downloaded_tracks` 行 + 走正常下载流程。开销可忽略（单次 IO）。

### 6.6 LocalScreen 影响

不需要改本地音乐扫描逻辑——MediaStore 自然把 `Music/MusicFree/` 下的文件作为 audio 提供给 `MediaStore.Audio.Media` 查询。`LocalMusicContent` 仅在已存在的 `viewModel.scanLocalMusic()` 结果上叠加 `downloadedKeys` 角标即可。

## 7. UI 表面

### 7.1 入口点

#### 7.1.1 全局 `MusicItemOptionsSheet`（**新组件**，放 `:core/ui`）

Material3 `ModalBottomSheet`，传入 `MusicItem`，展示操作列表。**v1 只一个动作**：「下载」。结构留出可扩展的 `actions: List<MusicItemAction>` 参数，方便将来加"加入歌单 / 删除 / 分享"。

```kotlin
@Composable
fun MusicItemOptionsSheet(
    item: MusicItem,
    onDismiss: () -> Unit,
    onDownload: (item: MusicItem) -> Unit,    // 内部还会先弹 quality 选择
)
```

挂载点（v1 列表）：`search`、`local`、`playlistDetail`、`albumDetail`、`topListDetail`、`pluginSheetDetail`、`history`。

挂载方式：列表项的 `onLongClick` → `currentOptionsItem = item`（记在 Screen 局部 state），有值就显示 sheet。**不**侵入每个 ViewModel。

#### 7.1.2 MusicDetail 下载按钮

`MusicDetailScreen` 封面下方 operations 区加"下载"图标按钮。点击 → 弹 quality 选择 dialog → `downloader.enqueue(listOf(item), quality)`。

#### 7.1.3 MusicListEditorLite 批量下载

底部操作栏加"下载选中"按钮。点击 → 直接用 `prefs.defaultDownloadQuality` 入队，**不**弹 dialog（批量场景不打扰用户）。

#### 7.1.4 Quality 选择 Dialog（共用）

```
┌──────────────────────────┐
│ 下载音质                   │
│ ──────────────────────── │
│  ◯ 标准                    │
│  ●  高品                   │  ← 默认选中 prefs.defaultDownloadQuality
│  ◯ 超品                    │
│ ──────────────────────── │
│      [ 取消 ]   [ 下载 ]    │
└──────────────────────────┘
```

放 `:core/ui`，单首入口共用。批量下载不调用此 dialog。

### 7.2 LocalScreen AppBar 入口

`LocalScreen` AppBar 加右侧图标按钮：

```
[← 返回]   本地音乐         [↓ 下载]
                              ⓷  ← Badge: pending+failed 总数
```

- 数字徽标来自 `downloader.tasks.collectAsState().filter { active }.size`。
- 点击导航到 `DownloadingRoute`。
- 没有任务时仍展示图标，保持入口稳定。

### 7.3 Downloading 页

新建 `feature/home/downloading/DownloadingScreen.kt` + ViewModel（VM 是 `Downloader.tasks` 的薄包装）。

```
┌─────────────────────────────────┐
│ [←] 下载                  [⋮]    │
├─────────────────────────────────┤
│ ▼ 进行中（2）                     │
│  晴天                            │
│  下载中  3.2MB / 8.1MB     [✕]  │
│  ━━━━━━━━━━━░░░░░░░░░  40%      │
│                                 │
│  夜曲                            │
│  等待中                    [✕]  │
│ ─────────────────────────────── │
│ ▼ 失败（1）                       │
│  七里香                          │
│  下载失败：无法获取源       [↻]  │
└─────────────────────────────────┘
```

- 两段：**进行中**（`PENDING + PREPARING + DOWNLOADING`）+ **失败**（`FAILED`）。
- 行尾按钮：进行中 = 取消 ✕，失败 = 重试 ↻。
- `[⋮]` overflow：「全部重试失败项」「清空失败项」「取消所有进行中」。
- 空状态：两段都为空时居中显示"暂无下载任务"+ 小字"在歌曲长按菜单或歌曲详情页可触发下载"。

### 7.4 Local 列表角标

`LocalMusicContent` 列表项右侧加 ✓ 图标表示"已下载"，不文字化。判定来源 `downloader.downloadedKeys`。

### 7.5 通知

通道 `download_progress`（`IMPORTANCE_LOW`）：

```
┌────────────────────────────────────┐
│ MusicFree                          │
│ 正在下载 3 首歌（1/3 完成）         │
│ ━━━━━━━━━━━━━░░░░░░░░░  40%        │
│ [取消所有]   [打开]                 │
└────────────────────────────────────┘
```

完成时通知不持留——Service 退出，通知 dismiss，配合 § 5.7 的一次性 Toast。

失败不另发通知（避免通知刷屏）；失败状态在 Downloading 页可见。

## 8. 设置项与 Plugin 集成

### 8.1 偏好（`AppPreferences` 扩展）

```kotlin
val maxDownload: Flow<Int>             // default 3, clamp 1..10
val useCellularDownload: Flow<Boolean> // default false
val defaultDownloadQuality: Flow<PlayQuality> // default STANDARD
val downloadDirRelative: Flow<String>  // default "Music/MusicFree/"，预留但 v1 不暴露 UI
```

`DownloadPreferences`（`:downloader` 内部）从 `AppPreferences` 读，向 `DownloadEngine` 提供 `combine(...)` 后的 `DownloadConfig` snapshot。

### 8.2 Settings 页 UI

`SettingsScreen` 加"下载"分区，三项：
- **同时下载数**：`Slider` 1–10，默认 3。
- **使用移动网络下载**：`Switch`，默认关。
- **默认下载音质**：`SegmentedButton` 或 dropdown，4 档（low/standard/high/super），默认 standard。

i18n key 复用 RN 命名空间（`downloading.title`、`setting.downloadQuality` 等），后续语言包对齐 RN。

### 8.3 Plugin 集成与质量回退

**不改 `PluginApi.getMediaSource` 签名**——回退在调用方：

```kotlin
private val DESC = listOf(PlayQuality.SUPER, PlayQuality.HIGH, PlayQuality.STANDARD, PlayQuality.LOW)

// PlayQuality 枚举为 LOW/STANDARD/HIGH/SUPER，wire 形式直接 name.lowercase()
private fun PlayQuality.wireName(): String = name.lowercase()

private suspend fun resolveWithFallback(
    plugin: LoadedPlugin,
    item: MusicItem,
    target: PlayQuality,
): MediaSourceResult? {
    val startIdx = DESC.indexOf(target).coerceAtLeast(0)
    for (q in DESC.subList(startIdx, DESC.size)) {
        runCatching { plugin.getMediaSource(item, q.wireName()) }
            .getOrNull()
            ?.takeIf { !it.url.isNullOrBlank() }
            ?.let { return it }
    }
    return null
}
```

没有插件（`plugin == null`）时，用 `item.url` 作为兜底——和 RN 同样的 fallback。

## 9. 测试策略

### 9.1 单元测试（在 `:downloader` 模块内，纯 JVM）

1. **`DownloadEngineTest`**（最重要）—— fake `Plugin` + MockWebServer + 内存 Room：
   - 单首成功路径 → `download_tasks` 行被删，`downloaded_tracks` 行被建。
   - 质量回退：first quality 失败、second 成功 → 正确解析、正确写入。
   - 全部 quality 失败 → `FAILED + FailToFetchSource`。
   - 并发上限：入队 5 首、cap=2 → 同时 inflight 不超 2，前两首完成后第 3 首才启动。
   - 取消进行中 → `inflight[key]?.cancel()` 被调用，cache 文件被删，DB 行被删。
   - 取消 PENDING → 直接删行。
   - 重启恢复：预置 DB 1 个 `DOWNLOADING` 行，启动后变 `PENDING`。
   - 网络从 WiFi → Cellular（未授权）→ inflight 取消、退回 PENDING、stop service flag。
2. **`MediaStoreMusicWriterTest`** —— Robolectric + ShadowContentResolver。
3. **`FilenameTest`** —— escape 规则、长度截断（>200 截到 200）、扩展名兜底。

### 9.2 集成测试（`app/src/androidTest`）

4. **`DownloadFlowAndroidTest`** —— 真实 `Context.contentResolver` + MockWebServer：
   - enqueue → 等待 `tasks` flow 显示 Completed → 文件出现在 MediaStore 查询里 → `downloaded_tracks` 行存在。
   - 同一首再 enqueue → 被去重逻辑挡住。

### 9.3 UI 测试

5. **`DownloadingScreenTest`** —— fake Downloader 注入 3 个状态，验证渲染与按钮回调。
6. **`LocalScreenBadgeTest`** —— 在已有 LocalScreen 测试上加 `downloadedKeys` 注入，验证角标。

### 9.4 验收闸门（运行态验收）

7. 手动端到端：插件搜索 → 长按列表项 → 选下载 → 通知出现 → 完成后 Toast → 进系统文件 App 看到 `Music/MusicFree/` 下文件 → 重启 App → 本地页角标出现。

## 10. 实施 logistics

### 10.1 Worktree 约束（CLAUDE.md 强制）

本功能必须使用 `git worktree` 开发。建议布局：

```
.worktrees/feat-downloading/   ← 主开发 worktree
```

实施时由 `superpowers:using-git-worktrees` 处理具体创建。worktree 路径需相对于仓库根，`.worktrees/` 已在 `.gitignore`（已确认）。

### 10.2 模块创建顺序建议（待 plan 阶段细化）

1. 新建 `:downloader` 模块骨架（`build.gradle.kts` + 空 manifest + Hilt 入口）。
2. 在 `:data` 加 entity/DAO（按开发期策略：直接改 schema，不写 `Migration` 对象）。
3. `:downloader` 内：`Downloader` 接口 → `DownloadEngine` → `DownloadService` → `MediaStoreMusicWriter` → `NetworkMonitor` → `DownloadNotifier`。
4. 单元测试与引擎并行。
5. UI：`MusicItemOptionsSheet`（核心组件）→ `DownloadingScreen` → 各入口位置接线 → LocalScreen 角标。
6. 设置页接线 + i18n。
7. 端到端运行态验收。

### 10.3 与现有路由系统对齐

- `core/navigation/Routes.kt` 加 `@Serializable data object DownloadingRoute`。
- `app/navigation/AppNavHost.kt` 挂 `downloadingScreen { onBack = navController::popBackStack }`。
- 路由覆盖会从当前 17/19 提升至 18/19（剩 `setCustomTheme`）。

## 11. 范围之外（v2 / 后续）

明确**不**在本次实现：

- 暂停/继续单任务（HTTP Range 断点续传）。
- WiFi 切换自动**继续**（v1 仅做切换时取消回 PENDING）。
- 一键下载整张专辑/歌单。
- 自定义下载子目录 UI（DataStore key 已预留，仅暂不暴露）。
- 下载历史 / 已完成区。
- `setCustomTheme` 页（独立设计）。

## 12. 风险与开放问题

- **Foreground Service 类型限制**（Android 14+）：`dataSync` 是合法选择，但部分 OEM 对前台服务有额外限制（如小米/华为后台运行管理）。运行态验收阶段需在国产 ROM 设备上至少抽测一台。
- **MediaStore Volume**：本设计写入 `VOLUME_EXTERNAL_PRIMARY`，未处理 SD 卡场景。绝大多数现代设备无外置 SD，v1 不处理。
- **POST_NOTIFICATIONS（Android 13+）权限被拒**：用户拒绝后 Service 仍可运行（只是没通知）。可在第一次下载时引导申请，被拒后不再提示。
- **质量回退的 `super` 起点**：若用户选 `super` 而插件真的没有 super 源，会触发 4 次插件调用——单次插件 `getMediaSource` 通常 < 1s，可接受；后续若发现性能问题可在插件返回结果中加 `availableQualities` 元数据来短路。

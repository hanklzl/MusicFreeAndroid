# 听歌统计（Listen Stats）设计

- 状态：草案，待 review
- 日期：2026-05-15
- 范围：Android 端新增"听歌足迹"功能；侧栏新增「我的」section + 入口；新增持久化 listen_event 表与多歌手子表；新增 `:feature:listen-stats` 模块承载统计页与明细页；不修改既有 history 行为
- 关联文档：`../../../AGENTS.md`（数据库迁移规范 / R8 / 日志 / UI Harness）、`../../dev-harness/ui/rules.md`、`../../dev-harness/player/rules.md`、`../../dev-harness/test/rules.md`、`../../dev-harness/data/`（暂无；本 spec 与 AGENTS.md 数据库迁移条款联动）

## 背景

RN 原版 [MusicFree](https://github.com/maotoumao/MusicFree) 没有此功能；这是 Android 端超出 parity 的新增能力。

当前 db 没有任何"播放事件"事实表：`PlayerController.playHistory` 是内存 `MutableStateFlow<List<MusicItem>>`（`player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt:102`），重启即丢；`HistoryRoute` 仅消费这个内存源（`feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/history/HistoryViewModel.kt`）。本 spec 引入持久化的 `listen_event` 事件流，作为统计能力的事实基础；history 仍走原来内存路径，不动。

## 目标

1. 用户可在侧栏「我的 → 听歌足迹」打开统计页，看到 **日 / 周 / 月 / 年 / 总计** 五种时间窗下的：
   - 总收听时长（Hero KPI）
   - 听过的歌曲数 / 歌手数
   - 每日时长柱状图
   - Top 歌曲 / Top 歌手
   - 语言分布 / 音乐风格（best-effort，覆盖率 < 30% 时整张卡片隐藏）
   - 听歌时段分布（按小时）
   - 连续听歌天数 / 新发现歌曲数
   - 听歌日历热力图（仅月 / 年 / 总计视图）
2. 每张可下钻卡片点击进入**统一明细页**，按 mode 过滤同一份歌曲列表数据（Top / 全量 / 首次听到 / 按歌手 / 按语言 / 按风格）
3. 数据采集对播放主链路零侵入：现有 `PlayerController` 仅注入 `ListenTracker` 并在三处现有 listener 回调中加一行；不改播放器行为
4. db 改动遵守 `AGENTS.md > 数据库迁移规范`：Migration(9 → 10) + MigrationTestHelper 测试；老用户升级表为空、不破坏既有数据
5. 用户可通过统计页右上角 ⋯ 菜单**清除全部统计数据**（二次确认）

## 非目标

- v1 不做跨设备 / 云同步；所有数据仅本地存储
- v1 不引入预聚合表（daily_stats / ranking_cache）；前 5 年 SQL 实时聚合即可
- 不动既有 `HistoryRoute` / `HistoryViewModel` / `PlayerController.playHistory`；本 spec 后再单独 spec 决定是否把 history 迁到 listen_event 上
- 不做"年度回顾大屏" / "可分享卡片"等 Wrapped 类视觉；统计页面是常驻入口、非节庆体验
- 不为风格 / 语言归一化提供运行时配置（v1 字典 hardcoded，v2 再考虑）

## §1 关键决策清单

| 决策项 | 取值 | 理由 |
|---|---|---|
| 听过一次的口径 | 累计有效播放 ≥ 30s | 与 Spotify Wrapped / 网易云对齐；跳过 / 试听 1-2s 不入计数，但秒数仍累加进总时长 |
| 歌手归属 | 拆 artist 子表；所有 artist 各计 1 次 | 语义上对应"听过这些歌手"；feat. 贡献不丢失 |
| 时间窗定义 | 自然周（周一起）/ 自然月 / 自然年 | 与"日"焦点一致；支持上一周 / 上一月 / 上一年翻页 |
| 时间窗内卡片 | 总时长 / 双 KPI / 每日柱图 / Top 歌曲 / Top 歌手 / 语言 / 风格 / 时段 / 连续 / 新发现 / 日历热力（仅月-年-总计） | 用户已选；不含「插件 / 平台来源」 |
| 风格 / 语言数据源 | best-effort 从 `music_items.rawJson` 提取 + 归一化字典 | 不要求标准 schema；覆盖率 < 30% 时整张卡隐藏，避免误导 |
| 下钻清单 | 12 项中 10 项有下钻，2 项不下钻；4 种交互（KPI 全量 / Top 全量 / 分类过滤 / 时间窗联动） | 见 §6 路由 mode 列表 |
| 视觉风格 | MD3 + 项目 `MusicFreeColors.primary = #F17D34`，sans-serif，24px 圆角卡片 | 与项目主题色统一，避免引入新色 |
| 侧栏入口位置 | 顶部新增 section「我的」（`sectionKey = "me"`），仅含「听歌足迹」一项 | 与「设置」「其他」「软件」语义不冲突；未来可扩展 |
| AppBar 调色 | 项目惯例：橙底 AppBar + 白底 Hero card，总时长数字用 primary 橙 | 走 `MusicFreeTopAppBar` 默认路径，不登记特殊 Chrome |
| 隐私 / 清除 | 统计页右上角 ⋯ 菜单「清除统计数据」+ 二次确认 dialog | 就近可控，不占用设置页 |
| 数据起点 | 功能首次启用起；首屏顶部小字「开始统计于 YYYY 年 M 月 D 日」 | db 没历史；早期周 / 月加「早期数据较少」提示 |
| history 边界 | 本 spec 不动；继续 in-memory | 与持久化统计独立；未来另一 spec 决定是否合并 |
| db 升级 | `AppDatabase` version 9 → 10；`CREATE TABLE IF NOT EXISTS`；不回填；MigrationTestHelper 覆盖 | 遵循 AGENTS.md 新增的「数据库迁移规范」条款 |

## §2 架构 & 模块归属

### 依赖示意

```
:player                          :data                          :feature:listen-stats (新)
┌──────────────────────┐         ┌──────────────────────┐       ┌──────────────────────┐
│ PlaybackService      │         │ AppDatabase v10      │       │ ListenStatsScreen    │
│ + ExoPlayer          │         │  + listen_event      │       │ ListenStatsViewModel │
│ PlayerController     │         │  + listen_event_artist│←─────│ ListenDetailScreen   │
│ + ListenTracker(新)  │ ──写──▶ │ ListenStatsDao(新)   │       │ ListenDetailViewModel│
│   Player.Listener    │         │ ListenStatsRepository│       │ component/* (卡片)   │
│   委托 ListenTracker │         │ (新)                  │       └──────────────────────┘
└──────────────────────┘         └──────────────────────┘                  ▲
                                                                           │
                                                              ┌──────────────────────┐
                                                              │ :feature:home        │
                                                              │ Drawer「我的」入口   │
                                                              │ → ListenStatsRoute   │
                                                              └──────────────────────┘
```

### 模块归属

| 组件 | 归属模块 | 备注 |
|---|---|---|
| `ListenTracker`、`ListenDimExtractor`、`ArtistSplitter` | `:player` | 与 `PlayerController` 同居；不暴露 public API，仅经 Hilt 注入 |
| `ListenEventEntity` / `ListenEventArtistEntity` / DAO / Repository | `:data` | 沿用现有 Room + Repository 数据层惯例 |
| `MIGRATION_9_10` | `:data` | 放在 `data/db/migration/` 子包 |
| `ListenStatsRoute` / `ListenDetailRoute` | `:core/navigation/Routes.kt` | 与项目其他路由聚合一处 |
| `ListenStatsScreen` / `ListenDetailScreen` / ViewModel / Card composables | `:feature:listen-stats`（新） | 依赖 `:core` `:data`；不依赖 `:player`（数据全经 Repository） |
| Drawer 入口新增 section / icon / anchor | `:feature:home` | 见 §6 |
| NavHost 挂载 | `:app/navigation/AppNavHost.kt` | 加两条 `composable<...>` |

依赖方向单向：`:app → :feature:* → :data, :player → :core`，无循环。`:feature:listen-stats` 与 `:feature:home` 平级、互不依赖。

## §3 数据库 schema & Migration

### `AppDatabase` 改动

`data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt` 当前 `version = 9`，bump 到 `10`；entities 列表追加两项；增加 `migrations = arrayOf(MIGRATION_9_10)` 挂载（如尚未启用 migration array，需要同时改造 DI Provider 调用 `Room.databaseBuilder(...).addMigrations(MIGRATION_9_10).build()`，**不要使用 `fallbackToDestructiveMigration()`**）。

### `ListenEventEntity`（主表，每次有效收听一条）

```kotlin
@Entity(
    tableName = "listen_event",
    indices = [
        Index("playedAtMs"),
        Index(value = ["musicId", "platform"]),
    ],
)
data class ListenEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playedAtMs: Long,          // session 结束时刻 epoch ms；跨午夜归结束日
    val musicId: String,
    val platform: String,
    val title: String,             // 冗余快照：music_items 可能被缓存清理 / 覆盖
    val artistRaw: String,         // 原始 artist 字段（未拆分）
    val album: String?,
    val artwork: String?,
    val durationMs: Long,          // 歌曲总时长，便于完整率判定
    val playedSeconds: Int,        // 本 session 累计有效秒数（≥30 才写）
    val completed: Boolean,        // 自然到尾 或 lastPosition >= durationMs - 5000
    val language: String?,         // 归一化语言："zh-CN" / "en" / "yue" / "ja" / "ko" / null
    val genre: String?,            // 归一化风格："pop" / "hip-hop" / "rock" / "rnb" / "folk" / null
)
```

### `ListenEventArtistEntity`（子表，多歌手归属）

```kotlin
@Entity(
    tableName = "listen_event_artist",
    foreignKeys = [ForeignKey(
        entity = ListenEventEntity::class,
        parentColumns = ["id"], childColumns = ["eventId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("eventId"),
        Index("artistName"),
    ],
)
data class ListenEventArtistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val artistName: String,        // 拆分后的单个艺人名（trim 后）
    val artistOrder: Int,          // 在原 artist 字符串中的位置（0 = 主唱）
)
```

### 设计约束

- **冗余快照**：`title` / `artistRaw` / `album` / `artwork` / `durationMs` 同时存在主表与 `music_items`，统计需要"历史快照感"；估算 ~100B/行 × 10w 行 ≈ 10MB，可接受。
- **artist 拆子表**：多歌手语义直接 GROUP BY；JSON 数组字段聚合不友好。
- **language / genre 主表内存放归一化值**：单值字段，不必再开子表；归一化在写入时一次性完成。
- **不存 rawJson**：rawJson 仍在 `music_items` 表，统计不需要原始 payload。
- **playedAtMs 取 session 结束时刻**：跨午夜的 session 归到结束日，与"听过一次"语义对齐。
- **不写 userId / deviceId**：单设备本地存储。
- **字段命名**：camelCase（项目其他 entity 惯例），列名跟 Room 默认生成的 camelCase。

### `MIGRATION_9_10`

```kotlin
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS listen_event (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                playedAtMs INTEGER NOT NULL,
                musicId TEXT NOT NULL,
                platform TEXT NOT NULL,
                title TEXT NOT NULL,
                artistRaw TEXT NOT NULL,
                album TEXT,
                artwork TEXT,
                durationMs INTEGER NOT NULL,
                playedSeconds INTEGER NOT NULL,
                completed INTEGER NOT NULL,
                language TEXT,
                genre TEXT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_listen_event_playedAtMs ON listen_event(playedAtMs)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_listen_event_musicId_platform ON listen_event(musicId, platform)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS listen_event_artist (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                eventId INTEGER NOT NULL,
                artistName TEXT NOT NULL,
                artistOrder INTEGER NOT NULL,
                FOREIGN KEY(eventId) REFERENCES listen_event(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_listen_event_artist_eventId ON listen_event_artist(eventId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_listen_event_artist_artistName ON listen_event_artist(artistName)")
    }
}
```

老用户升级两张表为空，由功能首次写入填充。不破坏既有数据。

> 注：实际 SQL 应跟 Room 自动生成的 `CREATE TABLE` 语句字段顺序与 `NOT NULL` / 默认值完全一致。落地时以 `data/schemas/...AppDatabase/10.json` 导出文件为准，必要时把上面 SQL 调整为与 Room 生成版本完全等价。

## §4 采集管道

### `ListenTracker` 挂载点

`PlayerController:814` 已 host 一个 `Player.Listener`。新增 `ListenTracker` 由 Hilt 注入到 `PlayerController`，在现有 listener 三个回调中加一行委托，**不再额外挂第二个 listener**：

```kotlin
// player/.../PlayerController.kt
private val playerListener = object : Player.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        listenTracker.onIsPlayingChanged(isPlaying, currentMediaItem)
        emitState(); updatePositionTracking()
    }
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        listenTracker.onMediaItemTransition(currentMediaItem, reason)
        emitState()
    }
    override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_ENDED) {
            listenTracker.onTrackEnded(currentMediaItem)
            handleTrackEnded()
        }
        emitState(); updatePositionTracking()
    }
    override fun onPositionDiscontinuity(oldPos: PositionInfo, newPos: PositionInfo, reason: Int) {
        listenTracker.onPositionDiscontinuity(reason)
    }
    override fun onPlayerError(error: PlaybackException) {
        handlePlaybackError(error)
    }
}
```

### State machine

```
                    ┌─ onIsPlayingChanged(false) ──▶ accumulated += now - resumeWall
                    │                                isCurrentlyPlaying = false
                    │
[NoSession] ──onMediaItemTransition──▶ [Buffering] ──onIsPlayingChanged(true)──▶ [Playing]
                                                                  │   resumeWall = now
                                                                  │   isCurrentlyPlaying = true
                                                                  ▼
                              ┌──── onMediaItemTransition ────────┐
                              │   onTrackEnded                    │
                              │   清除 / 退出 service              │
                              ▼   (flush session)                 │
                       if accumulated_ms ≥ 30_000:                │
                          dao.insertEventWithArtists(             │
                              ListenEventEntity(...),             │
                              parseArtists(...)                   │
                          )                                       │
                       resetSession() ─────────────────────────▶ ┘
```

- 计时按 **wall-clock**，不 poll `Player.currentPosition`：`accumulated += now - resumeWall` 仅在 `isCurrentlyPlaying = true` 段。零开销。
- **seek 不算听**：`onPositionDiscontinuity(reason = DISCONTINUITY_REASON_SEEK)` 收尾当前 chunk，从 seek 终点的 wall-clock 重起。阻止用户来回 seek 刷时长。
- **完整率**：`onPlaybackStateChanged(STATE_ENDED)` 即 `completed = true`；切歌时如果 `lastPosition >= durationMs - 5000` 也算 completed。
- **playedAtMs = session 结束时刻**，与"听过一次"语义对齐。
- **写库 off-main**：`insertEventWithArtists(...)` 是 `suspend`，在 `ListenTracker.scope`（IO dispatcher，Hilt 提供）跑。
- **Service 被杀场景**：未 flush 的 session 丢，可接受。

### `ListenDimExtractor`（风格 / 语言归一化）

```kotlin
object ListenDimExtractor {
    private val LANG_MAP = mapOf(
        "国语" to "zh-CN", "华语" to "zh-CN", "mandarin" to "zh-CN", "zh" to "zh-CN", "中文" to "zh-CN",
        "粤语" to "yue", "cantonese" to "yue", "广东话" to "yue",
        "英语" to "en", "english" to "en",
        "日语" to "ja", "japanese" to "ja",
        "韩语" to "ko", "korean" to "ko",
    )
    private val GENRE_MAP = mapOf(
        "流行" to "pop", "华语流行" to "pop", "c-pop" to "pop", "pop" to "pop",
        "嘻哈" to "hip-hop", "rap" to "hip-hop", "hip hop" to "hip-hop", "hip-hop" to "hip-hop",
        "r&b" to "rnb", "节奏布鲁斯" to "rnb", "rnb" to "rnb",
        "摇滚" to "rock", "rock" to "rock", "金属" to "rock",
        "民谣" to "folk", "folk" to "folk", "乡村" to "folk",
        // … 可扩展，未命中返回 null
    )

    fun extract(rawJson: String?): Pair<String?, String?> {
        if (rawJson.isNullOrBlank()) return null to null
        val obj = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null to null
        val lang = pickFirst(obj, "language", "lang") ?.let { LANG_MAP[it.lowercase().trim()] }
        val genre = pickFirst(obj, "genre", "style", "category")
            ?.let { GENRE_MAP[it.lowercase().trim()] }
            ?: pickFromArray(obj, "tags", "tag")?.let { GENRE_MAP[it.lowercase().trim()] }
        return lang to genre
    }
}
```

未命中 → null → UI "未知 / 未归类" 桶；覆盖率 < 30% 整张卡隐藏。

### `ArtistSplitter`

```kotlin
private val ARTIST_SPLIT_REGEX =
    Regex("""\s*(?:[/&、,]|\sfeat\.?\s|\sft\.?\s|\swith\s)\s*""", RegexOption.IGNORE_CASE)

fun splitArtists(raw: String): List<String> =
    raw.split(ARTIST_SPLIT_REGEX).map { it.trim() }.filter { it.isNotBlank() }.distinct()
```

例：
- `"周杰伦 & 林俊杰"` → `["周杰伦", "林俊杰"]`
- `"Eminem feat. Rihanna"` → `["Eminem", "Rihanna"]`
- `"A / B、C, D feat. E"` → `["A", "B", "C", "D", "E"]`

### 日志

按 `AGENTS.md > 日志记录规范`，新增结构化事件：

- `listen_event_inserted`（fields: `musicId`, `platform`, `playedSeconds`, `completed`, `durationMs`）
- `listen_event_skipped_below_threshold`（fields: `accumulatedMs`, `durationMs`）
- `listen_event_insert_failed`（error）
- `listen_stats_cleared`（fields: `deletedRows`）

## §5 聚合查询 & Repository

### `ListenStatsDao`（节选）

```kotlin
@Dao
interface ListenStatsDao {

    @Transaction
    suspend fun insertEventWithArtists(event: ListenEventEntity, artists: List<ListenEventArtistEntity>) {
        val id = insert(event)
        if (artists.isNotEmpty()) {
            insertArtists(artists.map { it.copy(eventId = id) })
        }
    }

    @Insert suspend fun insert(event: ListenEventEntity): Long
    @Insert suspend fun insertArtists(artists: List<ListenEventArtistEntity>)

    @Query("DELETE FROM listen_event")
    suspend fun clearAllEvents(): Int  // cascade artist 子表自动清

    @Query("SELECT MIN(playedAtMs) FROM listen_event")
    fun firstEventTimestamp(): Flow<Long?>

    @Query("""SELECT IFNULL(SUM(playedSeconds), 0) FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs""")
    fun totalSecondsFlow(startMs: Long, endMs: Long): Flow<Long>

    @Query("""SELECT COUNT(DISTINCT musicId || '||' || platform) FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs""")
    fun distinctSongsFlow(startMs: Long, endMs: Long): Flow<Int>

    @Query("""SELECT COUNT(DISTINCT a.artistName) FROM listen_event_artist a
              JOIN listen_event e ON a.eventId = e.id
              WHERE e.playedAtMs >= :startMs AND e.playedAtMs < :endMs""")
    fun distinctArtistsFlow(startMs: Long, endMs: Long): Flow<Int>

    @Query("""SELECT musicId, platform, title, artistRaw, album, artwork,
                     COUNT(*) AS playCount, SUM(playedSeconds) AS totalSec
              FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
              GROUP BY musicId, platform
              ORDER BY playCount DESC, totalSec DESC
              LIMIT :limit""")
    fun topSongsFlow(startMs: Long, endMs: Long, limit: Int): Flow<List<TopSongRow>>

    @Query("""SELECT a.artistName, COUNT(DISTINCT e.id) AS playCount,
                     COUNT(DISTINCT e.musicId || '||' || e.platform) AS songCount,
                     SUM(e.playedSeconds) AS totalSec
              FROM listen_event_artist a JOIN listen_event e ON a.eventId = e.id
              WHERE e.playedAtMs >= :startMs AND e.playedAtMs < :endMs
              GROUP BY a.artistName
              ORDER BY playCount DESC, totalSec DESC
              LIMIT :limit""")
    fun topArtistsFlow(startMs: Long, endMs: Long, limit: Int): Flow<List<TopArtistRow>>

    // dailyBucketsFlow / hourBucketsFlow / heatmapFlow（按 floor((playedAtMs - originMs) / bucketMs)）
    // languageDistributionFlow / genreDistributionFlow（GROUP BY 字段；附带 coverage 计算用 totalEvents）
    // firstSeenInWindowFlow（子查询：第一次出现的 (musicId, platform) 落在窗内）
    // streakDaysFlow（按日 GROUP BY 后扫连续天数；可由 Repository 在内存里算，避免 SQL 递归）
}
```

### `ListenStatsRepository` 与 `ListenStatsSnapshot`

```kotlin
class ListenStatsRepository @Inject constructor(
    private val dao: ListenStatsDao,
    private val clock: Clock,                 // 测试时可注入 fake
) {
    fun statsForWindow(scope: TimeScope, anchor: LocalDate): Flow<ListenStatsSnapshot> = combine(
        dao.totalSecondsFlow(...),
        dao.distinctSongsFlow(...),
        dao.distinctArtistsFlow(...),
        dao.topSongsFlow(..., limit = 50),
        dao.topArtistsFlow(..., limit = 50),
        // ...
    ) { ... ListenStatsSnapshot(...) }

    fun detail(filter: DetailFilter, scope: TimeScope, anchor: LocalDate): Flow<List<ListenedSong>>

    fun firstEventDate(): Flow<LocalDate?>    // "开始统计于" 文案数据源

    suspend fun clearAll(): Int               // ⋯ 菜单"清除"调用
}

data class ListenStatsSnapshot(
    val totalSeconds: Long,
    val distinctSongs: Int,
    val distinctArtists: Int,
    val dailyBuckets: List<DailyBucket>,
    val topSongs: List<TopSongRow>,
    val topArtists: List<TopArtistRow>,
    val hourBuckets: List<HourBucket>,
    val languageDistribution: Distribution<String?>,
    val genreDistribution: Distribution<String?>,
    val streakDays: Int,
    val maxStreak: Int,
    val firstSeenCount: Int,
    val heatmap: List<DateBucket>,           // 月/年/总计才填，日/周 空 list
)

data class Distribution<T>(val items: List<Bucket<T>>, val coverage: Float)  // coverage = 命中数 / 总数
```

### 时区 / 性能

- 时间窗起止由 ViewModel 用 `ZoneId.systemDefault()` 算（如 `LocalDate.atStartOfDay(zone).toInstant().toEpochMilli()`）；不存 zone 字段。
- 性能估算：100 事件/日 × 365 天 = 36500 行/年，5 年 ~18 万行。带索引的 `GROUP BY` 在十万级行 SQLite 是毫秒级。**v1 不需要预聚合表**；超出范围 v2 再加。

## §6 UI 结构

### 新模块 `:feature:listen-stats`

```
feature/listen-stats/
├── build.gradle.kts                   # 依赖 :core :data；非 :player
└── src/main/java/com/zili/android/musicfreeandroid/feature/liststats/
    ├── ListenStatsScreen.kt
    ├── ListenStatsViewModel.kt
    ├── ListenStatsScreenState.kt
    ├── ListenDetailScreen.kt
    ├── ListenDetailViewModel.kt
    ├── ListenDetailScreenState.kt
    ├── component/
    │   ├── HeroTotalDurationCard.kt
    │   ├── TimeScopeSegmented.kt
    │   ├── TimeScopePager.kt
    │   ├── SecondaryKpiRow.kt
    │   ├── DailyBarsCard.kt
    │   ├── TopSongsCard.kt
    │   ├── TopArtistsCard.kt
    │   ├── LanguageCard.kt
    │   ├── GenreCard.kt
    │   ├── HourCard.kt
    │   ├── StreakDiscoveryRow.kt
    │   ├── HeatmapCard.kt
    │   ├── SongDetailRow.kt
    │   ├── ArtistDetailRow.kt
    │   ├── ClearStatsDialog.kt
    │   └── MoreMenu.kt
    └── navigation/
        └── ListenStatsNavigation.kt   # NavGraphBuilder 扩展，挂 ListenStatsRoute + ListenDetailRoute
```

`:feature:listen-stats` 加进 `settings.gradle.kts` 与 `:app` 依赖；与 `:feature:home` 平级。

### 路由（`:core/navigation/Routes.kt`）

```kotlin
@Serializable
data class ListenStatsRoute(
    val scope: String = "WEEK",            // "DAY" "WEEK" "MONTH" "YEAR" "ALL_TIME"
    val anchorEpochDay: Long = -1L,        // -1 表示"今天"，由 ViewModel 解析
)

@Serializable
data class ListenDetailRoute(
    val mode: String,                      // 见下方 mode 表
    val scope: String,
    val anchorEpochDay: Long,
    val filterValue: String? = null,       // BY_ARTIST=artistName / BY_LANGUAGE="zh-CN" / BY_GENRE="pop"
)
```

故意用 `String` 而非 `enum`，避免 AGENTS.md R8 条款里对 typed route enum 参数的 `@Keep` 要求。ViewModel 内部转 `TimeScope` / `DetailMode` enum 使用。

#### Detail mode 与下钻来源

| Mode | 来源卡片 | Hero summary | 默认排序 |
|---|---|---|---|
| `ALL_SONGS` | 双 KPI「听过的歌曲 N」 | 「{窗口} 听过的 N 首歌」 | 播放次数 ↓ |
| `ALL_ARTISTS` | 双 KPI「听过的歌手 N」 | 「{窗口} 听过的 N 位歌手」 | 播放次数 ↓ |
| `TOP_SONGS` | Top 歌曲「查看全部」 | 「{窗口} 全部排行（Top 50）」 | 播放次数 ↓ |
| `TOP_ARTISTS` | Top 歌手「查看全部」 | 「{窗口} 全部排行（Top 50）」 | 播放次数 ↓ |
| `FIRST_SEEN` | 新发现卡片 | 「{窗口} 首次听到的 N 首」 | 首次听到时间 ↓ |
| `BY_ARTIST` | Top 歌手某行 / 全部歌手某行 | 「{artistName} · {窗口} 听了 N 次」 | 播放次数 ↓ |
| `BY_LANGUAGE` | 语言分布条段 | 「{languageLabel} · {窗口} 听了 N 次」 | 播放次数 ↓ |
| `BY_GENRE` | 风格行 | 「{genreLabel} · {窗口} 听了 N 次」 | 播放次数 ↓ |

不下钻的卡片：Hero 总时长、听歌时段。点击 `每日时长柱图` / `听歌日历热力图` 的单个 bar / cell **不跳页**，直接把 `scope` 切到 `DAY` + `anchor` 设到那一天（原地刷新）。

### 统计页骨架

```kotlin
@Composable
fun ListenStatsScreen(viewModel: ListenStatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MusicFreeScreenScaffold(
        topBar = {
            MusicFreeTopAppBar(
                title = "听歌足迹",
                onBack = viewModel::onBack,
                actions = { MoreMenu(onClear = viewModel::onClearRequested) },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item { OnboardingHint(state.firstEventDate, state.scope) }   // 首屏顶部"开始统计于…"
            item { HeroTotalDurationCard(state.snapshot.totalSeconds, state.scopeLabel) }
            item { TimeScopeSegmented(state.scope, viewModel::onScopeChange) }
            item { TimeScopePager(state.anchor, viewModel::onAnchorChange, state.windowLabel) }
            item { SecondaryKpiRow(state.snapshot, onSongsClick = viewModel::onAllSongs, onArtistsClick = viewModel::onAllArtists) }
            item { DailyBarsCard(state.snapshot.dailyBuckets, onBarClick = viewModel::onDailyBarClick) }
            item { TopSongsCard(state.snapshot.topSongs, onSeeAll = viewModel::onSeeAllSongs) }
            item { TopArtistsCard(state.snapshot.topArtists, onSeeAll = viewModel::onSeeAllArtists, onRowClick = viewModel::onArtistClick) }
            if (state.snapshot.languageDistribution.coverage >= 0.30f) {
                item { LanguageCard(state.snapshot.languageDistribution, onSegmentClick = viewModel::onLanguageClick) }
            }
            if (state.snapshot.genreDistribution.coverage >= 0.30f) {
                item { GenreCard(state.snapshot.genreDistribution, onRowClick = viewModel::onGenreClick) }
            }
            item { HourCard(state.snapshot.hourBuckets) }
            item {
                StreakDiscoveryRow(
                    streakDays = state.snapshot.streakDays,
                    firstSeenCount = state.snapshot.firstSeenCount,
                    onStreakClick = viewModel::onStreakClick,
                    onDiscoveryClick = viewModel::onDiscoveryClick,
                )
            }
            if (state.scope in listOf(TimeScope.MONTH, TimeScope.YEAR, TimeScope.ALL_TIME)) {
                item { HeatmapCard(state.snapshot.heatmap, onCellClick = viewModel::onHeatmapCellClick) }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    if (state.showClearDialog) ClearStatsDialog(...)
}
```

### 明细页骨架

```kotlin
@Composable
fun ListenDetailScreen(viewModel: ListenDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MusicFreeScreenScaffold(
        topBar = { MusicFreeTopAppBar(title = state.titleByMode, onBack = viewModel::onBack) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            DetailHeaderCard(state.summary)              // hero 区按 mode 文案
            TimeScopeSegmented(state.scope, viewModel::onScopeChange)
            TimeScopePager(state.anchor, viewModel::onAnchorChange, state.windowLabel)
            SortChip(state.sort, viewModel::onSortChange)
            LazyColumn { items(state.items, key = { it.stableKey }) { SongDetailRow(it) } }
        }
    }
}
```

底层 `repository.detail(filter, scope, anchor)` 一份返回；mode 决定 hero 文案 / 默认排序 / 空态文案 / 是否显示"出现在 N 首歌中"副信息。

### 侧栏入口（`feature/home/.../HomeDrawerNavigation.kt`）

```kotlin
sealed interface HomeDrawerAction {
    data object OpenListenStats : HomeDrawerAction         // 新增
    data object OpenSettingsRoot : HomeDrawerAction
    // ... 既有
}

fun buildHomeDrawerUiModel(...): HomeDrawerUiModel = HomeDrawerUiModel(
    sections = listOf(
        HomeDrawerSectionUiModel(                          // 新增 section（最顶部）
            sectionKey = "me",
            title = "我的",
            items = listOf(
                HomeDrawerItemUiModel(
                    title = "听歌足迹",
                    iconRes = HomeIcons.DrawerListenStats,
                    anchorTag = FidelityAnchors.Home.DrawerMeListenStats,
                    action = HomeDrawerAction.OpenListenStats,
                ),
            ),
        ),
        // 原"设置" / "其他" / "软件" 三段保持顺序与内容
    ),
    footerActions = emptyList(),
)
```

同时：
- `:feature:home/component/HomeIcons.kt` 加 `DrawerListenStats` 资源（沿用既有 icon res 命名风格）
- `:core/ui/FidelityAnchors.kt` 加 `Home.DrawerMeListenStats` anchor
- `HomeScreenContent` / `HomeScreen` dispatch `OpenListenStats` → `navController.navigate(ListenStatsRoute())`
- `:app/navigation/AppNavHost.kt` 加两条 `composable<ListenStatsRoute> { ListenStatsScreen() }` 与 `composable<ListenDetailRoute> { ListenDetailScreen() }`

### AppBar 调色

跟项目惯例 — `MusicFreeTopAppBar` 默认橙底（`appBar = #F17D34`）+ 白色文字 / icon。**Hero card 是白底 surface，总时长大字用 `primary` 橙色**。不需要 ui-harness 特殊 Chrome 登记。

## §7 错误处理 / 边界

| 场景 | 处理 |
|---|---|
| Service 被系统杀，未 flush 的 session | 丢弃；最多丢一条未达阈值或刚过阈值未持久化的事件 |
| `rawJson` 为空 / 字段缺失 | `language` / `genre` 写 null，UI 计入"未知"桶 |
| `durationMs == 0`（直播 / 未知时长） | `completed = false`（永远不算完成），`playedSeconds` 正常累加 |
| 用户在播放中触发"清除" | 先 flush 当前 session → `DELETE FROM listen_event`（cascade 子表）→ Tracker reset；新 session 自然续写 |
| `isPlaying` 高频抖动 | state machine 按 wall-clock 累加，不依赖事件次数 |
| 用户来回 seek 刷时长 | `onPositionDiscontinuity(SEEK)` 收尾当前 chunk，从新位置 wall-clock 重起 |
| artist 拆分边界 | `ArtistSplitter` 处理 `/`、`&`、`、`、`,`、`feat`/`feat.`/`ft.`/`with`；trim、去重、过滤空 |
| 时区 | `ZoneId.systemDefault()`；跨时区漫游时窗会"看起来错位"，v2 再处理 |
| db 写入失败 | `try { dao.insertEventWithArtists(...) } catch (e) { MfLog.error("listen_event_insert_failed", e) }`；不抛、不影响播放 |
| 首次进入页面（无任何数据） | 顶部 `开始统计于 …` 引导；卡片显示空态文案；不出现"误以为坏了"的全空白 |
| 时间窗内 0 数据 | 每张卡片自带 empty state（"本周还没有听过任何歌"）；保持页面结构稳定 |
| 清除二次确认 | `ClearStatsDialog`：「这将删除所有听歌统计数据，且不可恢复」+ 主按钮 danger 色 |

## §8 测试策略

按 `docs/dev-harness/test/rules.md`：不 mock Room / Repository / DAO；协程测试 `runTest + StandardTestDispatcher + MainDispatcherRule`；DataStore / Room 单测不并发持有多 active instance。

### 单元测试（`test/`）

| 测试类 | 覆盖 |
|---|---|
| `ListenTrackerTest` | state machine：事件序列 → 期望写库次数 / 参数；30s 阈值边界；seek / 切歌 / END 行为；rawJson 为空时 language/genre = null |
| `ListenDimExtractorTest` | rawJson edge cases：标准 genre、tags 数组、language 字段、同义词归一、未命中 null |
| `ArtistSplitterTest` | `"周杰伦 & 林俊杰"` / `"Eminem feat. Rihanna"` / `"a/b、c"` / `"a, b feat. c"` / 空 / null |
| `ListenStatsViewModelTest` | scope / anchor 切换触发新 flow；清除流程；error fallback；窗口 label 格式 |
| `ListenDetailViewModelTest` | mode = `FIRST_SEEN` / `BY_ARTIST` / `TOP_SONGS` 时 summary 文案与过滤；sort 切换；空态 |

### Room 测试（Robolectric in-memory）

| 测试类 | 覆盖 |
|---|---|
| `ListenStatsDaoTest` | 各 Query 的 SQL 行为：时间窗筛选、GROUP BY 正确、artist 子表 JOIN、空表返回；`@Transaction insertEventWithArtists` cascade FK |
| `AppDatabaseMigration9To10Test` | `MigrationTestHelper`：创建 v9 db → 跑 `MIGRATION_9_10` → validate schema → 插入一条 listen_event 验证可写、子表 cascade |
| `ListenStatsRepositoryTest` | `statsForWindow(WEEK, anchor)` 在 in-memory db 上产生预期 snapshot；自然周边界（周一 00:00 起）；`clearAll` 返回删除行数 |

### Compose 测试（Robolectric）

| 测试类 | 覆盖 |
|---|---|
| `ListenStatsScreenTest` | snapshot 不为空时全部卡片 render；空 snapshot 时只显示 onboarding + 必要卡片占位；coverage < 30% 时 language / genre 卡片不渲染 |
| `ListenDetailScreenTest` | mode = `FIRST_SEEN` / `BY_ARTIST` / `TOP_SONGS` 时 hero / 列表正确；sort 切换 reload；空态文案 per-mode |
| `MoreMenuTest` | ⋯ 菜单展开 → 点击「清除」→ ClearStatsDialog 出现 → 确认 dispatch `onClearConfirmed` |

### 契约 / 仪器测试

| 测试 | 覆盖 |
|---|---|
| `HomeAnchorContractTest`（既有，新增一条） | `FidelityAnchors.Home.DrawerMeListenStats` 在抽屉里存在 |
| `HomeDrawerListenStatsEntryTest`（新，AndroidTest） | 打开抽屉 → 点「听歌足迹」 → 当前 destination 为 `ListenStatsRoute` |

### 验证 commands

```bash
./gradlew :data:testDebugUnitTest
./gradlew :player:testDebugUnitTest
./gradlew :feature:listen-stats:testDebugUnitTest
./gradlew :feature:home:testDebugUnitTest
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest --tests "*ListenStats*"
./gradlew :app:assembleDebug
```

## §9 接受标准

实现完成必须同时满足：

1. 抽屉打开 → 顶部出现「我的」section → 点击「听歌足迹」跳到统计页（`ListenStatsRoute`，默认 scope=WEEK）
2. 播放一首歌 ≥ 30 秒后切歌或停止 → db 写入一条 `listen_event` + N 条 `listen_event_artist`（N = 拆分后 artist 数）
3. 在 30 秒前切歌 → 不写入；db 行数不变
4. 统计页五种时间窗切换 → 每张卡片数据按 SQL 聚合正确刷新；时间窗翻页（‹ ›）正确
5. 各卡片下钻按 §6 mode 表跳转明细页，明细页 hero / 排序 / 空态符合 §6 描述
6. ⋯ 菜单「清除」→ 二次确认 dialog → 确认后所有 listen_event 删除（cascade 子表）→ 页面回到空态 + 引导
7. 卸载重装 / 老用户从 v9 升级：app 启动不崩，统计页空态正常显示；不丢任何老表数据
8. `:data:testDebugUnitTest` / `:feature:listen-stats:testDebugUnitTest` / `AppDatabaseMigration9To10Test` 全绿
9. release 构建 (`./gradlew :app:assembleRelease`) 通过 — 验证 R8 不会去掉 `ListenEventEntity` / `ListenEventArtistEntity`（按 AGENTS.md R8 条款，普通 `@Entity` 不需 @Keep，但 release smoke 必须做一次）
10. 真机播放 5 分钟、覆盖切歌 / 暂停 / seek / 杀进程重启场景，统计页数字与日志记录一致

## §10 范围外 / 后续

| 项 | 处理 |
|---|---|
| 跨设备 / 云同步 | 不做；本地 only |
| 历史明细页（与 RN history 合并） | 独立 spec；本次先让 history 维持内存路径 |
| 预聚合表 / 物化视图 | v2；当 listen_event 行数 > 50w 或年视图明显卡顿时再上 |
| 风格 / 语言归一化字典运行时配置 | v2；先 hardcoded |
| 年度回顾大屏 / 可分享卡片 | 单独 spec，不进本次 |
| 跨时区漫游 | v2；目前用 system zone |
| 导出统计 JSON / CSV | v2 |
| MediaCache / DownloadedTrack 也想看"听了多久" | 已通过 `listen_event.platform == "local"` 自然覆盖；UI 不区分 |

## 参考

- 项目主题：`core/src/main/java/com/zili/android/musicfreeandroid/core/theme/MusicFreeColors.kt` — `primary = #F17D34`
- 现有 PlayerController listener 钩子：`player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt:814`
- 现有 history（不动）：`feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/history/HistoryViewModel.kt`
- 现有抽屉 ui model：`feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeDrawerNavigation.kt`
- RN 源类型（参考歌曲字段）：`../MusicFree/src/types/music.d.ts`
- 项目数据库迁移规范：`../../../AGENTS.md` §「数据库迁移规范」
- UI Harness 规则：`../../dev-harness/ui/rules.md`
- 测试稳定性规则：`../../dev-harness/test/rules.md`

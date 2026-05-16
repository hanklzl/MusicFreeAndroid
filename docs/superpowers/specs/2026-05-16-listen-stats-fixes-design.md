# 听歌足迹（Listen Stats）三项修复设计

- 状态：草案，待 review
- 日期：2026-05-16
- 范围：修复已发布 v1.0.1 的"听歌足迹"功能三项缺陷：①明细页 / Top 卡缺封面 ②跨插件同一首歌被计成多首 ③按日 / 按小时 / 热力图分桶按 UTC 切而非用户本地时区
- 关联文档：
  - 原功能设计：`./2026-05-15-listen-stats-design.md`
  - `../../../AGENTS.md`（数据库迁移规范 / 日志规范 / R8 / UI Harness）
  - `../../dev-harness/test/rules.md`

## 背景

`feat(listen-stats)` 在 `24e3ee7d` 落地、`bcf7aca6 v1.0.1` 发布。线上反馈三项问题：

1. **封面缺失**：「Top 歌曲（全部）」明细页每行只有一个浅紫色占位 Box；统计页 Top 歌曲卡更是连占位都没有，仅排名数字 + 文本。
2. **跨插件不合并**：同一首歌从不同 plugin 听过会被计成多首，列表标题尾部会显示 plugin 名（如「叶蒨文 · 元力 QQ」）。用户语义上"听过的是这首歌"，与具体插件来源解耦。
3. **时区错位**：用户在 UTC+8 设备上凌晨 02:00 听的歌被丢进前一天的桶；今早 07:00 听的歌被「听歌时段」放到 23:00 桶。

底层 `playedAtMs` 存的是 `System.currentTimeMillis()`（UTC epoch ms，与时区无关），`windowFor()` 已用 `ZoneId.systemDefault()` 计算窗口边界，问题只在 DAO 三个分桶 SQL 的"除以 86400000"和"除以 3600 取模 24"硬切按 UTC 计算。

## 目标

1. Top 歌曲卡 / 听歌明细页每行显示歌曲封面（artwork URL 已在 `listen_event` 表内）
2. 不同插件来源、同一首歌（按 title + 首位歌手判定）被聚合为同一行；UI 副标题不再展示 plugin 名
3. 按日 / 按小时 / 热力图按用户当前 system zone 切桶；窗口边界本来就正确，保持不动

## 非目标

- 不引入 "title 剥括号 / 去 (Live) / (Karaoke) 变体归并"；v1 只做最直接的 lower+trim
- 不做跨时区漫游：v1 仍按"查看时的 system zone"分桶，不存历史 zone
- 不删 `listen_event.platform` 列；保留诊断价值与未来"按平台筛选"扩展空间
- 不为 TopArtistsCard 加歌手头像（listen_event 表无此字段，超出本次范围）
- 不调整原 listen-stats 设计中其它能力（hero 卡 / KPI / 风格 / 语言 / 连续天数等）

## §1 关键决策清单

| 决策项 | 取值 | 理由 |
|---|---|---|
| 跨插件合并键 | `mergeKey = lower(trim(title)) + "\|" + lower(trim(primaryArtist))` | title+首位歌手最不易误合并；同名不同人不会错合 |
| primaryArtist 来源 | `splitArtists(artistRaw).firstOrNull().orEmpty()` | 复用现有 `:player/listening/ArtistSplitter.kt` 拆分规则 |
| mergeKey 持久化 | 新增 `mergeKey` 列 + 索引 + 写入时计算 + 老数据回填 | SQL 直接 GROUP BY，索引命中；不必每次查询重算 |
| 老数据回填 | `MIGRATION_10_11` 单事务 Kotlin 游标遍历回填 | 10w 行级实测 < 200ms；不需要 background Worker |
| platform 列 | 保留不删，UI 不再展示 | 用户说"或者不展示"；保留有诊断价值 |
| 封面组件 | 复用 `core/.../CoverImage.kt`（SubcomposeAsyncImage） | 已有封装，null artwork 自动 placeholder |
| 封面覆盖范围 | TopSongsCard + SongDetailRow（明细页用） | TopArtistsCard 无头像字段、超出范围 |
| 时区分桶 | DAO 增加 `zoneOffsetMs: Long` 参数；SQL `(playedAtMs + :zoneOffsetMs) / 86400000` | 无 schema 变更；改动收敛在 3 个 query + Repository |
| zone offset 取点 | `zone.rules.getOffset(Instant.ofEpochMilli(window.startMs))` | 窗口起点取一次；DST 跨边界场景接受 ±1h 误差（中国无 DST） |
| db 升级 | `version = 10 → 11`；不回退；MigrationTestHelper 覆盖 | 遵循 AGENTS.md 数据库迁移规范 |

## §2 schema & 迁移

### `ListenEventEntity` 改动

`data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/ListenEventEntity.kt`：

```kotlin
@Entity(
    tableName = "listen_event",
    indices = [
        Index("playedAtMs"),
        Index(value = ["musicId", "platform"]),   // 保留：写入查重 / 诊断
        Index("mergeKey"),                         // 新增：聚合主索引
    ],
)
data class ListenEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playedAtMs: Long,
    val musicId: String,
    val platform: String,
    val title: String,
    val artistRaw: String,
    val album: String?,
    val artwork: String?,
    val durationMs: Long,
    val playedSeconds: Int,
    val completed: Boolean,
    val language: String?,
    val genre: String?,
    val mergeKey: String,                           // 新增
)
```

### `AppDatabase` 改动

`data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt`：

```kotlin
@Database(
    version = 11,                                   // 10 → 11
    entities = [ ... ],
)
abstract class AppDatabase : RoomDatabase() { ... }
```

DI Provider 的 `addMigrations(...)` 追加 `MIGRATION_10_11`。**不得**引入 `fallbackToDestructiveMigration()`。

### `MIGRATION_10_11`

放在 `data/src/main/java/com/zili/android/musicfreeandroid/data/db/migration/`：

```kotlin
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE listen_event ADD COLUMN mergeKey TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_listen_event_mergeKey ON listen_event(mergeKey)")

        // 老数据回填：Kotlin 游标遍历 + 复用 splitArtists，避免 SQLite 写 regex
        db.query("SELECT id, title, artistRaw FROM listen_event").use { c ->
            val update = "UPDATE listen_event SET mergeKey = ? WHERE id = ?"
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val title = c.getString(1) ?: ""
                val artistRaw = c.getString(2) ?: ""
                val primary = splitArtists(artistRaw).firstOrNull().orEmpty()
                val key = "${title.trim().lowercase()}|${primary.trim().lowercase()}"
                db.execSQL(update, arrayOf<Any>(key, id))
            }
        }
    }
}
```

迁移在单事务（Room 自动包裹）里执行；10w 行估算 < 200ms，不阻塞主线程感知。

### `splitArtists` 共享

`splitArtists` 当前在 `player/src/main/java/com/zili/android/musicfreeandroid/player/listening/ArtistSplitter.kt`。:data 无法依赖 :player。处理方式：

**选 A（推荐）**：把 `ArtistSplitter.kt` 整体迁到 `:core`（或新建 `:core/text/ArtistSplitter.kt`），:player 与 :data 都依赖 :core。
- 改动小：一个文件搬位置 + 包名修改 + 两处 import 更新（`:player/listening/ListenTracker.kt` 与 `:player/listening/ListenTrackerTest.kt`）。
- 规则不变。

**选 B**：在 :data 单独复制一份。简单但破坏单一来源原则，不推荐。

落地走 A。

## §3 写入路径

`player/src/main/java/com/zili/android/musicfreeandroid/player/listening/ListenTracker.kt` 现有写入处（约 line 102-115）：

```kotlin
val artists = splitArtists(s.item.artist)          // 已存在
val primary = artists.firstOrNull().orEmpty()      // 新增
val mergeKey =                                     // 新增
    "${s.item.title.trim().lowercase()}|${primary.trim().lowercase()}"
val event = ListenEventEntity(
    playedAtMs = s.lastEventWall,
    musicId = s.item.id,
    platform = s.item.platform,
    title = s.item.title,
    artistRaw = s.item.artist,
    album = s.item.album,
    artwork = s.item.artwork,
    durationMs = durationMs,
    playedSeconds = (s.accumulatedMs / 1000).toInt(),
    completed = completed,
    language = lang,
    genre = genre,
    mergeKey = mergeKey,                           // 新增
)
```

其它逻辑不动；state machine、阈值、completed 计算均不受影响。

## §4 DAO 改造

`data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/ListenStatsDao.kt`：

### 聚合改用 mergeKey

`TopSongRow` / `ListenedSongRow` 数据类**不动**（字段名 / 类型保持），只是来源是 `MAX(...)` 选出来的代表行。

| 函数 | 改动 |
|---|---|
| `distinctSongsFlow` | `COUNT(DISTINCT musicId \|\| '\|\|' \|\| platform)` → `COUNT(DISTINCT mergeKey)` |
| `topSongsFlow` | `GROUP BY musicId, platform` → `GROUP BY mergeKey`；SELECT 列改 `MAX(title)`, `MAX(artistRaw)`, `MAX(album)`, `MAX(artwork)`, `MAX(musicId)`, `MAX(platform)`, `COUNT(*) AS playCount`, `SUM(playedSeconds) AS totalSec` |
| `topArtistsFlow` | `COUNT(DISTINCT e.musicId \|\| '\|\|' \|\| e.platform)` → `COUNT(DISTINCT e.mergeKey)` |
| `firstSeenInWindowFlow` | `GROUP BY` 与 `HAVING` 子查询都换 `mergeKey`；HAVING 子查询的 WHERE 由 `musicId = ... AND platform = ...` 改为 `mergeKey = ...` |
| `allSongsInWindowFlow` / `songsByArtistFlow` / `songsByLanguageFlow` / `songsByGenreFlow` | `GROUP BY mergeKey`；SELECT 列改 `MAX(...)` 选代表行 |

> `MAX(artwork)`：SQLite `MAX` 跳过 NULL，所以 plugin A 无图、plugin B 有图时会选 plugin B 那张。`MAX(title)` 拿字典序最大，仅影响展示选哪份（例如 "情人知己 (Live)" vs "情人知己" 会选前者）。可接受。

示例 — `topSongsFlow` 改后：

```kotlin
@Query("""
    SELECT MAX(musicId) AS musicId,
           MAX(platform) AS platform,
           MAX(title) AS title,
           MAX(artistRaw) AS artistRaw,
           MAX(album) AS album,
           MAX(artwork) AS artwork,
           COUNT(*) AS playCount,
           SUM(playedSeconds) AS totalSec
    FROM listen_event
    WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
    GROUP BY mergeKey
    ORDER BY playCount DESC, totalSec DESC
    LIMIT :limit
""")
fun topSongsFlow(startMs: Long, endMs: Long, limit: Int): Flow<List<TopSongRow>>
```

### 时区分桶参数化

三个分桶查询新增 `zoneOffsetMs: Long` 参数；`WHERE` 子句不动：

```kotlin
@Query("""
    SELECT CAST(((playedAtMs + :zoneOffsetMs) / 86400000) AS INTEGER) AS dayEpochDay,
           SUM(playedSeconds) AS seconds
    FROM listen_event
    WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
    GROUP BY dayEpochDay
    ORDER BY dayEpochDay ASC
""")
fun dailyBucketsFlow(startMs: Long, endMs: Long, zoneOffsetMs: Long): Flow<List<DailyBucketRow>>

@Query("""
    SELECT CAST((((playedAtMs + :zoneOffsetMs) / 1000 / 3600) % 24) AS INTEGER) AS hourOfDay,
           SUM(playedSeconds) AS seconds
    FROM listen_event
    WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
    GROUP BY hourOfDay
    ORDER BY hourOfDay ASC
""")
fun hourBucketsFlow(startMs: Long, endMs: Long, zoneOffsetMs: Long): Flow<List<HourBucketRow>>

@Query("""
    SELECT CAST(((playedAtMs + :zoneOffsetMs) / 86400000) AS INTEGER) AS dayEpochDay,
           SUM(playedSeconds) AS seconds
    FROM listen_event
    WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
    GROUP BY dayEpochDay
    ORDER BY dayEpochDay ASC
""")
fun heatmapFlow(startMs: Long, endMs: Long, zoneOffsetMs: Long): Flow<List<DateBucketRow>>
```

## §5 Repository 改造

`data/src/main/java/com/zili/android/musicfreeandroid/data/repository/listenstats/ListenStatsRepository.kt`：

```kotlin
fun statsForWindow(scope: TimeScope, anchor: LocalDate): Flow<ListenStatsSnapshot> {
    return firstEventDate().flatMapLatest { firstDate ->
        val zone = zoneIdProvider()
        val window = windowFor(scope, anchor, zone, firstDate)
        val zoneOffsetMs = zone.rules
            .getOffset(Instant.ofEpochMilli(window.startMs))
            .totalSeconds * 1000L

        combine(
            dao.totalSecondsFlow(window.startMs, window.endMs),
            dao.distinctSongsFlow(window.startMs, window.endMs),
            dao.distinctArtistsFlow(window.startMs, window.endMs),
            dao.topSongsFlow(window.startMs, window.endMs, limit = 50),
            dao.topArtistsFlow(window.startMs, window.endMs, limit = 50),
            dao.dailyBucketsFlow(window.startMs, window.endMs, zoneOffsetMs),
            dao.hourBucketsFlow(window.startMs, window.endMs, zoneOffsetMs),
            dao.languageDistributionFlow(window.startMs, window.endMs),
            dao.genreDistributionFlow(window.startMs, window.endMs),
            dao.heatmapFlow(window.startMs, window.endMs, zoneOffsetMs),
            dao.firstSeenInWindowFlow(window.startMs, window.endMs),
        ) { fields -> /* unchanged */ }
    }
}
```

`ListenedSong` / `TopSongRow` 等域模型与 ViewModel 完全不感知本次改动。

## §6 UI 改造

### `SongDetailRow.kt`（明细页行）

```kotlin
@Composable
fun SongDetailRow(song: ListenedSong, showFirstSeen: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(                                              // 替换原 Box 占位
            data = song.artwork,
            contentDescription = song.title,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyMedium)
            Text(                                                // 去掉 " · ${song.platform}"
                song.artistRaw,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (showFirstSeen) {
                val date = Instant.ofEpochMilli(song.firstSeenMs)
                    .atZone(ZoneId.systemDefault()).toLocalDate()
                Text("${date.monthValue}/${date.dayOfMonth} 首次", ...)
            }
            Text("${song.playCount} 次", ...)
        }
    }
}
```

### `TopSongsCard.kt`（统计页 Top 卡）

```kotlin
rows.take(5).forEachIndexed { idx, row ->
    val rank = idx + 1
    Row(...) {
        Text("$rank", Modifier.width(28.dp), ...)
        CoverImage(                                              // 新增
            data = row.artwork,
            contentDescription = row.title,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.bodyMedium)
            Text(                                                // 原 "${row.artistRaw} · ${row.album.orEmpty()}"
                row.artistRaw,                                   // 简化为只显示 artistRaw
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Text("${row.playCount} 次", ...)
    }
}
```

> 原 `"${row.artistRaw} · ${row.album.orEmpty()}"` 在 album 为 null 时尾部会留孤立 "·"。顺手只显示 artistRaw（与明细页保持一致）。

### 依赖

`feature/listen-stats/build.gradle.kts` 确认 `implementation(project(":core"))` 已存在（CoverImage 来源）；如未存在则补一行。

### 其它 UI 文件

搜索结果只有 `SongDetailRow.kt:36` 与 `TopSongsCard.kt:55` 两处显式拼 platform / album；其它 component 不渲染 plugin。修这两处即覆盖全部 UI。

## §7 测试策略

按 `docs/dev-harness/test/rules.md`：不 mock Room / DAO / Repository；协程测试用 `runTest + StandardTestDispatcher + MainDispatcherRule`；DataStore / Room 单测避免并发持有多 active instance。

### 新增 / 扩展单测

| 测试类 | 覆盖 |
|---|---|
| `ArtistSplitterTest`（新建或扩展） | `splitArtists("周杰伦 & 林俊杰").first() == "周杰伦"`；空串、单歌手、`"Eminem feat. Rihanna"`、`"a/b、c"` 等首位提取一致 |
| `ListenTrackerTest`（已有，扩展） | 写入 ListenEventEntity 时 `mergeKey == "${title.trim().lowercase()}\|${primary.trim().lowercase()}"`；artistRaw 为空时 mergeKey 末尾为 `"\|"`（不抛） |
| `AppDatabaseMigration10To11Test`（新） | MigrationTestHelper：v10 库塞三条：(A) "情人知己 / 叶蒨文 / qq"、(B) "情人知己 / 叶蒨文 / netease"、(C) "情人知己 / 张学友 / qq" → 跑 MIGRATION_10_11 → 验 (A)/(B) mergeKey 相同；(C) 与之不同；`index_listen_event_mergeKey` 存在；新写入也带 mergeKey |
| `ListenStatsDaoTest`（已有，扩展） | 跨 plugin 同 mergeKey 行：`topSongsFlow` / `distinctSongsFlow` / `firstSeenInWindowFlow` 把 (A)/(B) 当一首；`MAX(artwork)` 在 (A).artwork=null、(B).artwork="url" 时拿到 "url"；`topArtistsFlow` 的 songCount 用 mergeKey 去重后正确 |
| `ListenStatsDaoTest`（时区 case 新增） | 插入一条 `playedAtMs = LocalDateTime.of(2026,5,11,2,0).atZone(Asia/Shanghai).toInstant().toEpochMilli()` → `dailyBucketsFlow(zoneOffsetMs=28_800_000)` 返回 dayEpochDay = `LocalDate.of(2026,5,11).toEpochDay()`；`hourBucketsFlow` 同 zoneOffset 返回 hourOfDay=2；`heatmapFlow` 同理 |
| `ListenStatsDaoTest`（zone=UTC 回归） | `zoneOffsetMs=0` 时分桶等价于改造前行为，证明 SQL 改造无回归 |
| `ListenStatsRepositoryTest`（已有，扩展） | 注入 `zoneIdProvider = { ZoneId.of("Asia/Shanghai") }` → snapshot 的 daily / hour / heatmap 与本地日历一致 |
| `ListenStatsScreenTest`（已有，扩展） | TopSongsCard / SongDetailRow 渲染时含 `CoverImage`（验 contentDescription = title 即可；不必断言 Coil 实际加载）；副标题不再含 plugin 名 |

### 验证 commands

```bash
./gradlew :data:testDebugUnitTest --tests "*ListenStats*" --tests "*Migration10To11*"
./gradlew :player:testDebugUnitTest --tests "*ListenTracker*" --tests "*ArtistSplitter*"
./gradlew :feature:listen-stats:testDebugUnitTest
./gradlew :app:assembleDebug
```

### 人工运行态验收

- UTC+8 设备装 debug 包
- 若同时装多个插件（如 QQ + 网易），分别用两个插件听同一首歌 ≥ 30s × 2 → 「听过的歌曲」+1 而非 +2；Top 歌曲卡显示该歌封面
- 凌晨 02:00 本地听 1 首 ≥ 30s → 切换到「日」视图选今天，日柱图 / 热力图 / 听歌时段 均落到当天 02 点桶
- 老用户升级路径：保留 v10 库快照（拷贝 `/data/data/.../databases/musicfree.db`），重装 v1.0.2 后启动 → 不崩、Top 歌曲表合并正常、老事件 mergeKey 已回填

## §8 错误处理 / 边界

| 场景 | 处理 |
|---|---|
| artistRaw 为空 / null | mergeKey 末尾为 `"\|"`，仍是合法字符串，能正常 GROUP BY |
| title 为空 | mergeKey 头部为 `""`，能 GROUP BY 但语义异常；ListenTracker 已有 `s.item.title` 通常非空，不额外处理 |
| 老事件 platform 同名歌曲在不同插件，artistRaw 拼写差异（如 "周杰伦/林俊杰" vs "周杰伦 & 林俊杰"） | splitArtists 拆分后首位都是 "周杰伦"，mergeKey 一致；可正确合并 |
| 老事件 artistRaw 完全不同译名（如 "Jay Chou" vs "周杰伦"） | mergeKey 不同，仍计为两首；本 v1 不解决（超出 lower+trim 范畴） |
| Migration 10→11 在百万级行（用户长期使用）耗时长 | 估算 10w 行 < 200ms、百万级 ~2s，仍在可接受冷启动延迟内；不预聚合 |
| DST 跨边界窗口（中国不涉及） | 用 window.startMs 时刻取 offset；窗口内 DST 变化导致个别天 ±1h 偏差，v1 可接受 |
| artwork 为 null | `CoverImage` 内部 `SubcomposeAsyncImage` 自带 placeholder |
| Compose 测试断言封面渲染 | 用 `contentDescription = song.title` 定位，不依赖 Coil 真实加载 |

## §9 验收标准

实现完成须同时满足：

1. `:data:testDebugUnitTest --tests "*Migration10To11*" --tests "*ListenStats*"` 全绿
2. `:player:testDebugUnitTest --tests "*ListenTracker*" --tests "*ArtistSplitter*"` 全绿（含 mergeKey 写入断言）
3. `:feature:listen-stats:testDebugUnitTest` 全绿（含 SongDetailRow / TopSongsCard 渲染封面断言）
4. `:app:assembleDebug` 通过
5. 装上 debug 包，UTC+8 设备：
   - Top 歌曲卡每行有封面（artwork 非 null 时显示真实图，null 时 placeholder）
   - 听歌明细页（任意 mode）每行有封面
   - 副标题不再出现 plugin 名（"叶蒨文 · 元力 QQ" → "叶蒨文"）
   - 同一首歌从多插件听过只算 1 首（「听过的歌曲」+1）
   - 凌晨 02:00 本地听的事件落到当天 02 点桶（日柱图 / 热力图 / 听歌时段一致）
6. 老 v1.0.1 用户升级：启动不崩、老事件 mergeKey 已回填、统计页数字合理

## §10 范围外 / 后续

| 项 | 处理 |
|---|---|
| title 剥括号 / live / karaoke 归并 | v2；语义判断需要更复杂规则 |
| 跨译名 / 跨语言艺人合并（"Jay Chou" ↔ "周杰伦"） | v2；需要别名字典 |
| TopArtistsCard 歌手头像 | v2；listen_event 表无此字段，需 schema 调整 |
| 跨时区漫游历史一致性 | v2；需要写入时落地 localDayEpoch / localHourOfDay |
| 删除 platform 列 | 不做；保留诊断价值 |

## 参考

- 原 listen-stats 设计：`./2026-05-15-listen-stats-design.md`
- AGENTS.md 数据库迁移规范：`../../../AGENTS.md`
- 测试稳定性规则：`../../dev-harness/test/rules.md`
- ArtistSplitter 当前位置：`player/src/main/java/com/zili/android/musicfreeandroid/player/listening/ArtistSplitter.kt`
- CoverImage：`core/src/main/java/com/zili/android/musicfreeandroid/core/ui/CoverImage.kt`
- ListenStatsDao：`data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/ListenStatsDao.kt`
- ListenTracker：`player/src/main/java/com/zili/android/musicfreeandroid/player/listening/ListenTracker.kt`
- ListenEventEntity：`data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/ListenEventEntity.kt`
- AppDatabase 当前 version=10：`data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt:45`

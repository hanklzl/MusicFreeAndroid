# Listen-Stats 三项修复 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复已发布 v1.0.1 听歌足迹的三项缺陷:Top 歌曲卡/明细页加封面、跨插件同一首歌归一化、按日/按小时/热力图按本地时区分桶。

**Architecture:** `ListenEventEntity` 加 `mergeKey: String` 列 + `MIGRATION_10_11` 老数据回填;DAO 所有聚合 `GROUP BY mergeKey`、三个分桶 query 新增 `zoneOffsetMs` 参数;Repository 计算 zone offset 下传;UI 用 `CoverImage` 替换占位 Box,副标题去除 plugin 名;`splitArtists` 从 `:player` 迁到 `:core` 供 :data 复用。

**Tech Stack:** Kotlin / Jetpack Compose / Room / Hilt / Coil3 / coroutines runTest / Robolectric / MigrationTestHelper(androidTest)

**Spec:** [`docs/superpowers/specs/2026-05-16-listen-stats-fixes-design.md`](../specs/2026-05-16-listen-stats-fixes-design.md) (commit `c784823a`)

**Spec 偏差需知:** spec §6 示例代码写的 `CoverImage(data = ..., contentDescription = ..., modifier = ...)`,实际 `core/.../CoverImage.kt` 签名是 `CoverImage(uri: String?, modifier: Modifier = Modifier, size: Dp = 48.dp, cornerRadius: Dp = 4.dp)`。本计划用真实签名;另用 `Modifier.testTag(...)` 让 Compose 测试能定位封面节点(CoverImage 内部 contentDescription 是 null,不能用 onNodeWithContentDescription 定位)。

---

## 涉及文件清单

**移动:**
- `player/src/main/java/com/hank/musicfree/player/listening/ArtistSplitter.kt` → `core/src/main/java/com/hank/musicfree/core/util/ArtistSplitter.kt`(改 package)
- `player/src/test/java/com/hank/musicfree/player/listening/ArtistSplitterTest.kt` → `core/src/test/java/com/hank/musicfree/core/util/ArtistSplitterTest.kt`

**修改:**
- `player/src/main/java/com/hank/musicfree/player/listening/ListenTracker.kt` — import 改 :core;写入计算并落 mergeKey
- `player/src/test/java/com/hank/musicfree/player/listening/ListenTrackerTest.kt` — 断言 mergeKey
- `data/src/main/java/com/hank/musicfree/data/db/entity/ListenEventEntity.kt` — 加 mergeKey 列与索引
- `data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt` — version 10→11
- `data/src/main/java/com/hank/musicfree/data/di/DataModule.kt` — addMigrations 追加 MIGRATION_10_11
- `data/src/main/java/com/hank/musicfree/data/db/dao/ListenStatsDao.kt` — 全部聚合切 mergeKey;3 个分桶 query 加 zoneOffsetMs
- `data/src/main/java/com/hank/musicfree/data/repository/listenstats/ListenStatsRepository.kt` — 计算并下传 zoneOffsetMs
- `data/src/test/java/com/hank/musicfree/data/db/dao/ListenStatsDaoTest.kt` — seed 加 mergeKey;新增跨插件 / 时区 case
- `data/src/test/java/com/hank/musicfree/data/repository/listenstats/ListenStatsRepositoryTest.kt` — seed 加 mergeKey;新增 Asia/Shanghai 时区 case
- `feature/listen-stats/src/main/java/com/hank/musicfree/feature/listenstats/component/SongDetailRow.kt` — 占位 Box → CoverImage;去掉 platform
- `feature/listen-stats/src/main/java/com/hank/musicfree/feature/listenstats/component/TopSongsCard.kt` — 新增 CoverImage;副标题只显示 artistRaw
- `feature/listen-stats/src/test/java/com/hank/musicfree/feature/listenstats/component/CardCompositionTest.kt` — 新增 TopSongsCard / SongDetailRow 渲染断言

**新建:**
- `data/src/main/java/com/hank/musicfree/data/db/migration/Migration10To11.kt`
- `data/src/androidTest/java/com/hank/musicfree/data/db/AppDatabaseMigration10To11Test.kt`
- `data/schemas/com.hank.musicfree.data.db.AppDatabase/11.json`(KSP 在 build 时自动生成,提交即可)

---

## Task 1: 创建 worktree + 基线绿验证

**Files:**
- Verify: `.gitignore`(`.worktrees/` 已 ignore)
- 启用: 仓库根目录之外的工作树 `.worktrees/listen-stats-fixes/`

- [ ] **Step 1: 在仓库根目录创建 worktree**

Run:
```bash
git worktree add .worktrees/listen-stats-fixes -b listen-stats-fixes main
cd .worktrees/listen-stats-fixes
```

Expected: 新 worktree 创建在 `.worktrees/listen-stats-fixes/`,基于 `main` 创建 `listen-stats-fixes` 分支。后续所有 step 都在此 worktree 内执行。

- [ ] **Step 2: 跑基线 :data + :player + :feature:listen-stats 单测,确认起点全绿**

Run:
```bash
./gradlew :data:testDebugUnitTest :player:testDebugUnitTest :feature:listen-stats:testDebugUnitTest --no-daemon
```

Expected: BUILD SUCCESSFUL(若不是绿,先停下查根因,任务不能在红色基线上推进)

无需 commit;本任务只做环境准备与基线验证。

---

## Task 2: 把 `ArtistSplitter` 从 `:player` 迁到 `:core`

**Files:**
- Create: `core/src/main/java/com/hank/musicfree/core/util/ArtistSplitter.kt`
- Create: `core/src/test/java/com/hank/musicfree/core/util/ArtistSplitterTest.kt`
- Delete: `player/src/main/java/com/hank/musicfree/player/listening/ArtistSplitter.kt`
- Delete: `player/src/test/java/com/hank/musicfree/player/listening/ArtistSplitterTest.kt`
- Modify: `player/src/main/java/com/hank/musicfree/player/listening/ListenTracker.kt`(仅 import)

- [ ] **Step 1: 在 :core 新建 ArtistSplitter.kt**

Write `core/src/main/java/com/hank/musicfree/core/util/ArtistSplitter.kt`:

```kotlin
package com.hank.musicfree.core.util

private val SPLIT_REGEX = Regex(
    """\s*(?:[/&、,]|\sfeat\.?\s|\sft\.?\s|\swith\s)\s*""",
    RegexOption.IGNORE_CASE,
)

fun splitArtists(raw: String): List<String> =
    raw.split(SPLIT_REGEX).map { it.trim() }.filter { it.isNotBlank() }.distinct()
```

- [ ] **Step 2: 在 :core 新建 ArtistSplitterTest.kt**

Write `core/src/test/java/com/hank/musicfree/core/util/ArtistSplitterTest.kt`:

```kotlin
package com.hank.musicfree.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistSplitterTest {
    @Test fun ampersand_and_chinese_comma() {
        assertEquals(listOf("周杰伦", "林俊杰"), splitArtists("周杰伦 & 林俊杰"))
        assertEquals(listOf("A", "B", "C"), splitArtists("A、B、C"))
    }
    @Test fun feat_variants_caseInsensitive() {
        assertEquals(listOf("Eminem", "Rihanna"), splitArtists("Eminem feat. Rihanna"))
        assertEquals(listOf("Eminem", "Rihanna"), splitArtists("Eminem FEAT Rihanna"))
        assertEquals(listOf("Eminem", "Rihanna"), splitArtists("Eminem ft. Rihanna"))
        assertEquals(listOf("Tom", "Jerry"), splitArtists("Tom with Jerry"))
    }
    @Test fun slash_and_western_comma() {
        assertEquals(listOf("a", "b", "c", "d"), splitArtists("a/b、c, d"))
    }
    @Test fun trim_and_dedup_and_dropEmpty() {
        assertEquals(listOf("A", "B"), splitArtists("  A  &  B  &  A  "))
        assertEquals(emptyList<String>(), splitArtists(""))
        assertEquals(emptyList<String>(), splitArtists("   "))
    }
    @Test fun complex_mix() {
        assertEquals(listOf("A", "B", "C", "D", "E"), splitArtists("A, B feat. C / D、E"))
    }
    // 新增:覆盖 mergeKey 计算依赖的 firstOrNull 语义
    @Test fun firstOrNull_returnsPrimaryArtist_orNull() {
        assertEquals("周杰伦", splitArtists("周杰伦 & 林俊杰").firstOrNull())
        assertEquals(null, splitArtists("").firstOrNull())
        assertEquals(null, splitArtists("   ").firstOrNull())
    }
}
```

- [ ] **Step 3: 删除 :player 旧的 ArtistSplitter.kt 与 ArtistSplitterTest.kt**

Run:
```bash
git rm player/src/main/java/com/hank/musicfree/player/listening/ArtistSplitter.kt
git rm player/src/test/java/com/hank/musicfree/player/listening/ArtistSplitterTest.kt
```

- [ ] **Step 4: 修 ListenTracker.kt 的 import**

Edit `player/src/main/java/com/hank/musicfree/player/listening/ListenTracker.kt`:在文件顶部 import 段加:
```kotlin
import com.hank.musicfree.core.util.splitArtists
```

旧的 `splitArtists` 来自同包,不需要显式 import。新版来自 :core,需要 import。

确认 `:player/build.gradle.kts` 已 `implementation(project(":core"))`(应早已存在;若否需要补)。

- [ ] **Step 5: 跑 :core 与 :player 单测**

Run:
```bash
./gradlew :core:testDebugUnitTest :player:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL。`ArtistSplitterTest`(:core 6 个 case)与 `ListenTrackerTest`(:player 既有 case)全绿。

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/hank/musicfree/core/util/ArtistSplitter.kt \
        core/src/test/java/com/hank/musicfree/core/util/ArtistSplitterTest.kt \
        player/src/main/java/com/hank/musicfree/player/listening/ArtistSplitter.kt \
        player/src/test/java/com/hank/musicfree/player/listening/ArtistSplitterTest.kt \
        player/src/main/java/com/hank/musicfree/player/listening/ListenTracker.kt
git commit -m "refactor(core): 把 ArtistSplitter 迁到 :core,让 :data 也能复用"
```

---

## Task 3: `ListenEventEntity` 加 mergeKey 列 + bump 到 v11 + `MIGRATION_10_11`

**Files:**
- Modify: `data/src/main/java/com/hank/musicfree/data/db/entity/ListenEventEntity.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/di/DataModule.kt`
- Create: `data/src/main/java/com/hank/musicfree/data/db/migration/Migration10To11.kt`
- Create: `data/src/androidTest/java/com/hank/musicfree/data/db/AppDatabaseMigration10To11Test.kt`
- Modify: `data/src/test/java/com/hank/musicfree/data/db/dao/ListenStatsDaoTest.kt`(seed helper)
- Modify: `data/src/test/java/com/hank/musicfree/data/repository/listenstats/ListenStatsRepositoryTest.kt`(seed helper)
- Auto-generate: `data/schemas/com.hank.musicfree.data.db.AppDatabase/11.json`(KSP 在 build 时生成,git add)

- [ ] **Step 1: 写迁移测试(androidTest,先红)**

Write `data/src/androidTest/java/com/hank/musicfree/data/db/AppDatabaseMigration10To11Test.kt`:

```kotlin
package com.hank.musicfree.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hank.musicfree.data.db.migration.MIGRATION_10_11
import com.hank.musicfree.data.db.migration.MIGRATION_9_10
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-10-11-test"

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration10To11Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate10To11_addsMergeKey_andBackfillsByTitlePlusPrimaryArtist() {
        // ── 准备 v10 库,塞 3 条:(A)(B) 同歌不同插件应合并;(C) 同名不同人保留独立
        helper.createDatabase(TEST_DB, 10).use { db ->
            db.execSQL(
                """INSERT INTO listen_event(playedAtMs, musicId, platform, title, artistRaw,
                   album, artwork, durationMs, playedSeconds, completed, language, genre)
                   VALUES(1000, 'A', 'qq', '情人知己', '叶蒨文',
                   NULL, NULL, 240000, 60, 0, NULL, NULL)""".trimIndent(),
            )
            db.execSQL(
                """INSERT INTO listen_event(playedAtMs, musicId, platform, title, artistRaw,
                   album, artwork, durationMs, playedSeconds, completed, language, genre)
                   VALUES(2000, 'B', 'netease', '情人知己', '叶蒨文 & 张学友',
                   NULL, 'https://x/cover.jpg', 240000, 60, 0, NULL, NULL)""".trimIndent(),
            )
            db.execSQL(
                """INSERT INTO listen_event(playedAtMs, musicId, platform, title, artistRaw,
                   album, artwork, durationMs, playedSeconds, completed, language, genre)
                   VALUES(3000, 'C', 'qq', '情人知己', '张学友',
                   NULL, NULL, 240000, 60, 0, NULL, NULL)""".trimIndent(),
            )
        }

        // ── 跑迁移 + Room schema 校验
        helper.runMigrationsAndValidate(TEST_DB, 11, true, MIGRATION_10_11).use { db ->
            // 1. (A) 和 (B) mergeKey 一致(title="情人知己",primaryArtist="叶蒨文")
            val keyA = db.query("SELECT mergeKey FROM listen_event WHERE musicId = 'A'").use { c ->
                c.moveToFirst(); c.getString(0)
            }
            val keyB = db.query("SELECT mergeKey FROM listen_event WHERE musicId = 'B'").use { c ->
                c.moveToFirst(); c.getString(0)
            }
            val keyC = db.query("SELECT mergeKey FROM listen_event WHERE musicId = 'C'").use { c ->
                c.moveToFirst(); c.getString(0)
            }
            assertEquals("情人知己|叶蒨文", keyA)
            assertEquals(keyA, keyB)
            assertEquals("情人知己|张学友", keyC)
            assertTrue("A/B should merge", keyA == keyB)
            assertTrue("C should NOT merge with A/B", keyC != keyA)

            // 2. distinct mergeKey 数 = 2
            db.query("SELECT COUNT(DISTINCT mergeKey) FROM listen_event").use { c ->
                c.moveToFirst()
                assertEquals(2, c.getInt(0))
            }

            // 3. 索引存在
            db.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND name='index_listen_event_mergeKey'",
            ).use { c -> assertTrue("mergeKey index should exist", c.moveToFirst()) }
        }

        // 4. 用最新 schema 打开一次,确认 entity 与 db 完全对齐
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB,
        ).addMigrations(MIGRATION_9_10, MIGRATION_10_11).build().apply {
            openHelper.writableDatabase
            close()
        }
    }
}
```

(此测试在没设备 / 模拟器时不能跑;但 Room 编译期 schema 校验会在 build 时报问题,等价于一道额外闸门)

- [ ] **Step 2: 改 `ListenEventEntity` 加 mergeKey 字段与索引**

Edit `data/src/main/java/com/hank/musicfree/data/db/entity/ListenEventEntity.kt`:

```kotlin
package com.hank.musicfree.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "listen_event",
    indices = [
        Index("playedAtMs"),
        Index(value = ["musicId", "platform"]),
        Index("mergeKey"),
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
    val mergeKey: String,
)
```

- [ ] **Step 3: 改 `AppDatabase` version 10 → 11**

Edit `data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt` 第 45 行附近:
```kotlin
    version = 11,
```

(实体列表不变;现有 entities 注解里已含 ListenEventEntity)

- [ ] **Step 4: 新建 `Migration10To11.kt`**

Write `data/src/main/java/com/hank/musicfree/data/db/migration/Migration10To11.kt`:

```kotlin
package com.hank.musicfree.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hank.musicfree.core.util.splitArtists

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE listen_event ADD COLUMN mergeKey TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_listen_event_mergeKey ON listen_event(mergeKey)")

        // 老数据回填:Kotlin 游标遍历 + 复用 :core splitArtists,避免在 SQLite 写 regex
        val update = "UPDATE listen_event SET mergeKey = ? WHERE id = ?"
        db.query("SELECT id, title, artistRaw FROM listen_event").use { c ->
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

要求 `:data/build.gradle.kts` 已 `implementation(project(":core"))`(已存在;无需改)。

- [ ] **Step 5: 改 `DataModule` 把 MIGRATION_10_11 加进 addMigrations**

Edit `data/src/main/java/com/hank/musicfree/data/di/DataModule.kt`:

加 import:
```kotlin
import com.hank.musicfree.data.db.migration.MIGRATION_10_11
```

把 addMigrations 由:
```kotlin
.addMigrations(MIGRATION_9_10)
```
改为:
```kotlin
.addMigrations(MIGRATION_9_10, MIGRATION_10_11)
```

- [ ] **Step 6: 改 `ListenStatsDaoTest` 的 seed helper 计算 mergeKey**

Edit `data/src/test/java/com/hank/musicfree/data/db/dao/ListenStatsDaoTest.kt`:

在 import 段加:
```kotlin
import com.hank.musicfree.core.util.splitArtists
```

把 `seed(...)` 函数末尾的 `ListenEventEntity(...)` 构造里追加 mergeKey 字段。完整 seed 函数:

```kotlin
private suspend fun seed(
    playedAtMs: Long,
    musicId: String,
    platform: String = "netease",
    title: String = "T",
    artists: List<String> = listOf("A"),
    playedSeconds: Int = 60,
    completed: Boolean = true,
    language: String? = "zh-CN",
    genre: String? = "pop",
    durationMs: Long = 240_000,
) {
    val artistRaw = artists.joinToString(" & ")
    val primary = splitArtists(artistRaw).firstOrNull().orEmpty()
    val mergeKey = "${title.trim().lowercase()}|${primary.trim().lowercase()}"
    dao.insertEventWithArtists(
        event = ListenEventEntity(
            playedAtMs = playedAtMs, musicId = musicId, platform = platform,
            title = title, artistRaw = artistRaw,
            album = null, artwork = null, durationMs = durationMs,
            playedSeconds = playedSeconds, completed = completed,
            language = language, genre = genre,
            mergeKey = mergeKey,
        ),
        artists = artists.mapIndexed { i, n ->
            ListenEventArtistEntity(eventId = 0, artistName = n, artistOrder = i)
        },
    )
}
```

- [ ] **Step 7: 改 `ListenStatsRepositoryTest` 的 seed helper 计算 mergeKey**

Edit `data/src/test/java/com/hank/musicfree/data/repository/listenstats/ListenStatsRepositoryTest.kt`:

在 import 段加:
```kotlin
import com.hank.musicfree.core.util.splitArtists
```

把 `seed(...)` 末尾 `ListenEventEntity(...)` 改为含 mergeKey:

```kotlin
private suspend fun seed(
    date: LocalDate,
    musicId: String,
    secs: Int = 60,
    lang: String? = "zh-CN",
    genre: String? = "pop",
) {
    val ms = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 10_000
    val title = "T"
    val artistRaw = "A"
    val mergeKey = "${title.trim().lowercase()}|${splitArtists(artistRaw).firstOrNull().orEmpty().trim().lowercase()}"
    db.listenStatsDao().insertEventWithArtists(
        ListenEventEntity(
            playedAtMs = ms, musicId = musicId, platform = "p", title = title,
            artistRaw = artistRaw, album = null, artwork = null, durationMs = 240_000,
            playedSeconds = secs, completed = true, language = lang, genre = genre,
            mergeKey = mergeKey,
        ),
        listOf(ListenEventArtistEntity(eventId = 0, artistName = "A", artistOrder = 0)),
    )
}
```

同文件 `detail_byArtist_filtersListByArtistName` 测试中那条手写的 `ListenEventEntity(...)` 构造也要加 `mergeKey`:

```kotlin
val artistRaw = "X & Y"
val mergeKey = "t|${splitArtists(artistRaw).firstOrNull().orEmpty().lowercase()}"
db.listenStatsDao().insertEventWithArtists(
    ListenEventEntity(
        playedAtMs = 1, musicId = "m1", platform = "p", title = "T",
        artistRaw = artistRaw, album = null, artwork = null,
        durationMs = 100_000, playedSeconds = 60, completed = false,
        language = null, genre = null,
        mergeKey = mergeKey,
    ),
    listOf(
        ListenEventArtistEntity(eventId = 0, artistName = "X", artistOrder = 0),
        ListenEventArtistEntity(eventId = 0, artistName = "Y", artistOrder = 1),
    ),
)
```

- [ ] **Step 8: 触发 Room schema 生成,核对 11.json**

Run:
```bash
./gradlew :data:kspDebugKotlin
ls data/schemas/com.hank.musicfree.data.db.AppDatabase/
```

Expected: `data/schemas/.../11.json` 出现,内含 `listen_event.mergeKey` 列与 `index_listen_event_mergeKey` 索引。若没生成,确认 `room.schemaDirectory("$projectDir/schemas")` 已在 `data/build.gradle.kts`(已确认存在)。

- [ ] **Step 9: 跑 :data 单测,确认 mergeKey 字段 + seed 改动不破坏既有测**

Run:
```bash
./gradlew :data:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL。既有的 `ListenStatsDaoTest`、`ListenStatsRepositoryTest` 所有 case 仍然全绿(因为他们的 GROUP BY 行为还没改;实际行为应保持 v10 时一致)。

- [ ] **Step 10: 跑 :data 仪器测(若有设备/模拟器)**

Run(可选,需 device):
```bash
./gradlew :data:connectedDebugAndroidTest --tests "*Migration10To11Test*"
```

Expected: BUILD SUCCESSFUL。若设备不可用,先跳过;Task 6 的 :app:assembleDebug 也会走 KSP schema 校验,可作兜底。

- [ ] **Step 11: Commit**

```bash
git add data/src/main/java/com/hank/musicfree/data/db/entity/ListenEventEntity.kt \
        data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt \
        data/src/main/java/com/hank/musicfree/data/db/migration/Migration10To11.kt \
        data/src/main/java/com/hank/musicfree/data/di/DataModule.kt \
        data/src/androidTest/java/com/hank/musicfree/data/db/AppDatabaseMigration10To11Test.kt \
        data/src/test/java/com/hank/musicfree/data/db/dao/ListenStatsDaoTest.kt \
        data/src/test/java/com/hank/musicfree/data/repository/listenstats/ListenStatsRepositoryTest.kt \
        data/schemas/com.hank.musicfree.data.db.AppDatabase/11.json
git commit -m "feat(data): listen_event 加 mergeKey 列 + MIGRATION_10_11 回填老数据"
```

---

## Task 4: `ListenTracker` 写入时计算并落地 mergeKey

**Files:**
- Modify: `player/src/main/java/com/hank/musicfree/player/listening/ListenTracker.kt`(写入处)
- Modify: `player/src/test/java/com/hank/musicfree/player/listening/ListenTrackerTest.kt`(断言 mergeKey)

- [ ] **Step 1: 在 ListenTrackerTest 既有 case 里加 mergeKey 断言(先红)**

Edit `player/src/test/java/com/hank/musicfree/player/listening/ListenTrackerTest.kt`,把 `playing_for_60s_then_transition_writesOneEvent` 内的 argumentCaptor 断言扩展:

```kotlin
argumentCaptor<ListenEventEntity>().apply {
    verify(dao).insertEventWithArtists(capture(), any())
    assertEquals(60, firstValue.playedSeconds)
    assertEquals("zh-CN", firstValue.language)
    assertEquals("pop", firstValue.genre)
    // 新增:mergeKey = title.lower|primaryArtist.lower
    assertEquals("song|周杰伦", firstValue.mergeKey)
}
```

再新加一个 case 覆盖空 artist:
```kotlin
@Test fun mergeKey_emptyArtistRaw_endsWithPipe() = runTest {
    val tracker = newTracker(this)
    val itemNoArtist = item.copy(artist = "")
    setNow(0); tracker.onMediaItemTransition(itemNoArtist, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
    setNow(0); tracker.onIsPlayingChanged(true, itemNoArtist)
    setNow(60_000); tracker.onTrackEnded(itemNoArtist)
    advanceUntilIdle()
    argumentCaptor<ListenEventEntity>().apply {
        verify(dao).insertEventWithArtists(capture(), any())
        assertEquals("song|", firstValue.mergeKey)   // title="Song" → "song|"(尾部空 primary)
    }
}
```

- [ ] **Step 2: 跑 ListenTrackerTest 看新断言失败**

Run:
```bash
./gradlew :player:testDebugUnitTest --tests "*ListenTrackerTest*"
```

Expected: 编译可能因为 `ListenEventEntity` 缺少 `mergeKey` 必填参数而失败 — 此时构造对象的位置已在 Task 3 改完,但 ListenTracker 还没传 mergeKey;运行时会编译报错或 `mergeKey` 为 default(无 default)→ 必须实现。

(若 Kotlin 编译过了但测试运行时挂在 `assertEquals("song|周杰伦", ...)` 上,也是预期的失败)

- [ ] **Step 3: 改 ListenTracker.kt 写入处计算并传 mergeKey**

Edit `player/src/main/java/com/hank/musicfree/player/listening/ListenTracker.kt`,定位 `flushIfQualifies` 内 `val artists = splitArtists(s.item.artist)` 后面:

```kotlin
val artists = splitArtists(s.item.artist)
val primary = artists.firstOrNull().orEmpty()
val mergeKey = "${s.item.title.trim().lowercase()}|${primary.trim().lowercase()}"
val durationMs = s.item.duration
val completedBoundary = durationMs - COMPLETED_TAIL_TOLERANCE_MS
val completed = s.endedNaturally || (durationMs > 0 && s.accumulatedMs >= completedBoundary)
val event = ListenEventEntity(
    playedAtMs = s.lastEventWall,
    musicId = s.item.id, platform = s.item.platform,
    title = s.item.title, artistRaw = s.item.artist,
    album = s.item.album, artwork = s.item.artwork,
    durationMs = durationMs,
    playedSeconds = (s.accumulatedMs / 1000).toInt(),
    completed = completed,
    language = lang, genre = genre,
    mergeKey = mergeKey,
)
```

(其它逻辑、state machine、阈值、日志均不动)

- [ ] **Step 4: 重跑 ListenTrackerTest,确认通过**

Run:
```bash
./gradlew :player:testDebugUnitTest --tests "*ListenTrackerTest*"
```

Expected: BUILD SUCCESSFUL。两个 mergeKey 断言都通过。

- [ ] **Step 5: 跑全 :player 单测确认无回归**

Run:
```bash
./gradlew :player:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: Commit**

```bash
git add player/src/main/java/com/hank/musicfree/player/listening/ListenTracker.kt \
        player/src/test/java/com/hank/musicfree/player/listening/ListenTrackerTest.kt
git commit -m "feat(player): ListenTracker 写入时计算并落地 mergeKey"
```

---

## Task 5: DAO 聚合切 mergeKey + 3 个分桶 query 加 zoneOffsetMs;Repository 下传 offset

**Files:**
- Modify: `data/src/main/java/com/hank/musicfree/data/db/dao/ListenStatsDao.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/repository/listenstats/ListenStatsRepository.kt`
- Modify: `data/src/test/java/com/hank/musicfree/data/db/dao/ListenStatsDaoTest.kt`(新增跨插件 / 时区 case)
- Modify: `data/src/test/java/com/hank/musicfree/data/repository/listenstats/ListenStatsRepositoryTest.kt`(新增 Asia/Shanghai 时区 case)

- [ ] **Step 1: 在 ListenStatsDaoTest 加跨插件 / 时区 case(先红)**

Edit `data/src/test/java/com/hank/musicfree/data/db/dao/ListenStatsDaoTest.kt`,在文件末加:

```kotlin
@Test
fun crossPlugin_sameSong_mergedIntoOneRow() = runTest {
    // (A) musicId=A from qq + (B) musicId=B from netease,title 与 primaryArtist 一致 → 应合并
    seed(playedAtMs = 1, musicId = "A", platform = "qq",
         title = "情人知己", artists = listOf("叶蒨文"))
    seed(playedAtMs = 2, musicId = "B", platform = "netease",
         title = "情人知己", artists = listOf("叶蒨文", "张学友"))
    // (C) 同名歌但 primary 不同 → 独立
    seed(playedAtMs = 3, musicId = "C", platform = "qq",
         title = "情人知己", artists = listOf("张学友"))

    assertEquals(2, dao.distinctSongsFlow(0, 100).first())

    val tops = dao.topSongsFlow(0, 100, limit = 10).first()
    assertEquals(2, tops.size)
    val merged = tops.first { it.title == "情人知己" && it.playCount == 2 }
    // MAX(musicId) 字典序最大:"A" vs "B" → "B"
    assertEquals("B", merged.musicId)
    assertEquals(120L, merged.totalSec)
}

@Test
fun crossPlugin_MAX_artwork_picksNonNullUrl() = runTest {
    // (A) artwork=null,(B) artwork="https://x" — MAX 跳过 NULL
    dao.insertEventWithArtists(
        ListenEventEntity(
            playedAtMs = 1, musicId = "A", platform = "qq", title = "T",
            artistRaw = "X", album = null, artwork = null,
            durationMs = 240_000, playedSeconds = 60, completed = true,
            language = null, genre = null,
            mergeKey = "t|x",
        ),
        listOf(ListenEventArtistEntity(eventId = 0, artistName = "X", artistOrder = 0)),
    )
    dao.insertEventWithArtists(
        ListenEventEntity(
            playedAtMs = 2, musicId = "B", platform = "netease", title = "T",
            artistRaw = "X", album = null, artwork = "https://x/cover.jpg",
            durationMs = 240_000, playedSeconds = 60, completed = true,
            language = null, genre = null,
            mergeKey = "t|x",
        ),
        listOf(ListenEventArtistEntity(eventId = 0, artistName = "X", artistOrder = 0)),
    )
    val tops = dao.topSongsFlow(0, 100, limit = 10).first()
    assertEquals(1, tops.size)
    assertEquals("https://x/cover.jpg", tops[0].artwork)
}

@Test
fun dailyBuckets_withZoneOffsetMs_returnsLocalDayBucket() = runTest {
    // 本地 2026-05-11 02:00 (Asia/Shanghai UTC+8) → UTC 2026-05-10 18:00
    val localDate = java.time.LocalDate.of(2026, 5, 11)
    val localMs = localDate.atTime(2, 0)
        .atZone(java.time.ZoneId.of("Asia/Shanghai"))
        .toInstant().toEpochMilli()
    seed(playedAtMs = localMs, musicId = "m", playedSeconds = 60)
    // 窗口覆盖整月,offset=8h
    val startMs = localDate.withDayOfMonth(1).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai"))
        .toInstant().toEpochMilli()
    val endMs = localDate.withDayOfMonth(1).plusMonths(1).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai"))
        .toInstant().toEpochMilli()

    val buckets = dao.dailyBucketsFlow(startMs, endMs, zoneOffsetMs = 8L * 3600 * 1000).first()
    assertEquals(1, buckets.size)
    assertEquals(localDate.toEpochDay(), buckets[0].dayEpochDay)
}

@Test
fun hourBuckets_withZoneOffsetMs_returnsLocalHour() = runTest {
    val localMs = java.time.LocalDateTime.of(2026, 5, 11, 7, 0)
        .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
    seed(playedAtMs = localMs, musicId = "m", playedSeconds = 60)

    val buckets = dao.hourBucketsFlow(0, Long.MAX_VALUE, zoneOffsetMs = 8L * 3600 * 1000).first()
    assertEquals(1, buckets.size)
    assertEquals(7, buckets[0].hourOfDay)
}

@Test
fun zone_UTC_regression_zoneOffsetZero_isLegacyBehavior() = runTest {
    // playedAtMs 对应 UTC 14:00,offset=0 应得到小时 14
    val ms = java.time.LocalDateTime.of(2026, 5, 11, 14, 0)
        .atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    seed(playedAtMs = ms, musicId = "m", playedSeconds = 60)

    val hourBuckets = dao.hourBucketsFlow(0, Long.MAX_VALUE, zoneOffsetMs = 0L).first()
    assertEquals(14, hourBuckets[0].hourOfDay)
}
```

- [ ] **Step 2: 跑 ListenStatsDaoTest 看新 case 全失败(compile + assertion)**

Run:
```bash
./gradlew :data:testDebugUnitTest --tests "*ListenStatsDaoTest*"
```

Expected: 编译失败(dailyBucketsFlow/hourBucketsFlow 还没 zoneOffsetMs 参数)或 case 失败。这是预期红色;接下来 Step 3 实现 DAO 改动。

- [ ] **Step 3: 改 `ListenStatsDao.kt`**

Edit `data/src/main/java/com/hank/musicfree/data/db/dao/ListenStatsDao.kt`。逐个替换以下 `@Query`(其它 dao 方法 / TopSongRow / ListenedSongRow 数据类 / insertEventWithArtists / clearAllEvents / firstEventTimestamp / totalSecondsFlow / distinctArtistsFlow / languageDistributionFlow / genreDistributionFlow 不动):

```kotlin
@Query("""SELECT COUNT(DISTINCT mergeKey) FROM listen_event
          WHERE playedAtMs >= :startMs AND playedAtMs < :endMs""")
fun distinctSongsFlow(startMs: Long, endMs: Long): Flow<Int>

@Query("""SELECT MAX(musicId) AS musicId,
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
          LIMIT :limit""")
fun topSongsFlow(startMs: Long, endMs: Long, limit: Int): Flow<List<TopSongRow>>

@Query("""SELECT a.artistName,
                 COUNT(DISTINCT e.id) AS playCount,
                 COUNT(DISTINCT e.mergeKey) AS songCount,
                 IFNULL(SUM(e.playedSeconds), 0) AS totalSec
          FROM listen_event_artist a JOIN listen_event e ON a.eventId = e.id
          WHERE e.playedAtMs >= :startMs AND e.playedAtMs < :endMs
          GROUP BY a.artistName
          ORDER BY playCount DESC, totalSec DESC
          LIMIT :limit""")
fun topArtistsFlow(startMs: Long, endMs: Long, limit: Int): Flow<List<TopArtistRow>>

@Query("""SELECT CAST(((playedAtMs + :zoneOffsetMs) / 86400000) AS INTEGER) AS dayEpochDay,
                 SUM(playedSeconds) AS seconds
          FROM listen_event
          WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
          GROUP BY dayEpochDay
          ORDER BY dayEpochDay ASC""")
fun dailyBucketsFlow(startMs: Long, endMs: Long, zoneOffsetMs: Long): Flow<List<DailyBucketRow>>

@Query("""SELECT CAST((((playedAtMs + :zoneOffsetMs) / 1000 / 3600) % 24) AS INTEGER) AS hourOfDay,
                 SUM(playedSeconds) AS seconds
          FROM listen_event
          WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
          GROUP BY hourOfDay
          ORDER BY hourOfDay ASC""")
fun hourBucketsFlow(startMs: Long, endMs: Long, zoneOffsetMs: Long): Flow<List<HourBucketRow>>

@Query("""SELECT CAST(((playedAtMs + :zoneOffsetMs) / 86400000) AS INTEGER) AS dayEpochDay,
                 SUM(playedSeconds) AS seconds
          FROM listen_event
          WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
          GROUP BY dayEpochDay
          ORDER BY dayEpochDay ASC""")
fun heatmapFlow(startMs: Long, endMs: Long, zoneOffsetMs: Long): Flow<List<DateBucketRow>>

@Query("""SELECT MAX(e.musicId) AS musicId,
                 MAX(e.platform) AS platform,
                 MAX(e.title) AS title,
                 MAX(e.artistRaw) AS artistRaw,
                 MAX(e.album) AS album,
                 MAX(e.artwork) AS artwork,
                 MIN(e.playedAtMs) AS firstSeenMs,
                 MAX(e.playedAtMs) AS lastSeenMs,
                 COUNT(*) AS playCount,
                 SUM(e.playedSeconds) AS totalSec
          FROM listen_event e
          WHERE e.playedAtMs >= :startMs AND e.playedAtMs < :endMs
          GROUP BY e.mergeKey
          HAVING MIN(e.playedAtMs) = (
              SELECT MIN(e2.playedAtMs) FROM listen_event e2
              WHERE e2.mergeKey = e.mergeKey
          )
          ORDER BY firstSeenMs DESC""")
fun firstSeenInWindowFlow(startMs: Long, endMs: Long): Flow<List<ListenedSongRow>>

@Query("""SELECT MAX(musicId) AS musicId,
                 MAX(platform) AS platform,
                 MAX(title) AS title,
                 MAX(artistRaw) AS artistRaw,
                 MAX(album) AS album,
                 MAX(artwork) AS artwork,
                 MIN(playedAtMs) AS firstSeenMs,
                 MAX(playedAtMs) AS lastSeenMs,
                 COUNT(*) AS playCount,
                 SUM(playedSeconds) AS totalSec
          FROM listen_event
          WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
          GROUP BY mergeKey
          ORDER BY playCount DESC, totalSec DESC""")
fun allSongsInWindowFlow(startMs: Long, endMs: Long): Flow<List<ListenedSongRow>>

@Query("""SELECT MAX(e.musicId) AS musicId,
                 MAX(e.platform) AS platform,
                 MAX(e.title) AS title,
                 MAX(e.artistRaw) AS artistRaw,
                 MAX(e.album) AS album,
                 MAX(e.artwork) AS artwork,
                 MIN(e.playedAtMs) AS firstSeenMs,
                 MAX(e.playedAtMs) AS lastSeenMs,
                 COUNT(*) AS playCount,
                 SUM(e.playedSeconds) AS totalSec
          FROM listen_event e
          JOIN listen_event_artist a ON a.eventId = e.id
          WHERE e.playedAtMs >= :startMs AND e.playedAtMs < :endMs
            AND a.artistName = :artistName
          GROUP BY e.mergeKey
          ORDER BY playCount DESC""")
fun songsByArtistFlow(startMs: Long, endMs: Long, artistName: String): Flow<List<ListenedSongRow>>

@Query("""SELECT MAX(musicId) AS musicId,
                 MAX(platform) AS platform,
                 MAX(title) AS title,
                 MAX(artistRaw) AS artistRaw,
                 MAX(album) AS album,
                 MAX(artwork) AS artwork,
                 MIN(playedAtMs) AS firstSeenMs,
                 MAX(playedAtMs) AS lastSeenMs,
                 COUNT(*) AS playCount,
                 SUM(playedSeconds) AS totalSec
          FROM listen_event
          WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
            AND language = :language
          GROUP BY mergeKey
          ORDER BY playCount DESC""")
fun songsByLanguageFlow(startMs: Long, endMs: Long, language: String): Flow<List<ListenedSongRow>>

@Query("""SELECT MAX(musicId) AS musicId,
                 MAX(platform) AS platform,
                 MAX(title) AS title,
                 MAX(artistRaw) AS artistRaw,
                 MAX(album) AS album,
                 MAX(artwork) AS artwork,
                 MIN(playedAtMs) AS firstSeenMs,
                 MAX(playedAtMs) AS lastSeenMs,
                 COUNT(*) AS playCount,
                 SUM(playedSeconds) AS totalSec
          FROM listen_event
          WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
            AND genre = :genre
          GROUP BY mergeKey
          ORDER BY playCount DESC""")
fun songsByGenreFlow(startMs: Long, endMs: Long, genre: String): Flow<List<ListenedSongRow>>
```

- [ ] **Step 4: 改 `ListenStatsRepository.kt` 把 zoneOffsetMs 算出来下传**

Edit `data/src/main/java/com/hank/musicfree/data/repository/listenstats/ListenStatsRepository.kt`:

`statsForWindow` 改为:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
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
        ) { fields ->
            // ── 以下逻辑与改动前完全一致;只是 fields 索引顺序不变 ──
            @Suppress("UNCHECKED_CAST")
            val total = fields[0] as Long
            @Suppress("UNCHECKED_CAST")
            val songs = fields[1] as Int
            @Suppress("UNCHECKED_CAST")
            val artists = fields[2] as Int
            @Suppress("UNCHECKED_CAST")
            val tops = fields[3] as List<com.hank.musicfree.data.db.dao.TopSongRow>
            @Suppress("UNCHECKED_CAST")
            val topArtists = fields[4] as List<com.hank.musicfree.data.db.dao.TopArtistRow>
            @Suppress("UNCHECKED_CAST")
            val dailyRows = fields[5] as List<com.hank.musicfree.data.db.dao.DailyBucketRow>
            @Suppress("UNCHECKED_CAST")
            val hourRows = fields[6] as List<com.hank.musicfree.data.db.dao.HourBucketRow>
            @Suppress("UNCHECKED_CAST")
            val langRows = fields[7] as List<com.hank.musicfree.data.db.dao.LanguageBucketRow>
            @Suppress("UNCHECKED_CAST")
            val genreRows = fields[8] as List<com.hank.musicfree.data.db.dao.GenreBucketRow>
            @Suppress("UNCHECKED_CAST")
            val heatmapRows = fields[9] as List<com.hank.musicfree.data.db.dao.DateBucketRow>
            @Suppress("UNCHECKED_CAST")
            val firstSeenRows = fields[10] as List<com.hank.musicfree.data.db.dao.ListenedSongRow>

            val totalEvents = langRows.sumOf { it.count }.coerceAtLeast(1)
            val langKnown = langRows.filter { it.language != null }.sumOf { it.count }
            val genreKnown = genreRows.filter { it.genre != null }.sumOf { it.count }
            val streak = computeStreaks(dailyRows.map { it.dayEpochDay }, anchor.toEpochDay())

            ListenStatsSnapshot(
                totalSeconds = total,
                distinctSongs = songs,
                distinctArtists = artists,
                dailyBuckets = dailyRows.map { DailyBucket(it.dayEpochDay, it.seconds) },
                topSongs = tops,
                topArtists = topArtists,
                hourBuckets = hourRows.map { HourBucket(it.hourOfDay, it.seconds) },
                languageDistribution = Distribution(
                    buckets = langRows.map {
                        DistributionBucket(
                            key = it.language,
                            count = it.count,
                            label = it.language?.let(::languageLabel) ?: "未知 / 未归类",
                        )
                    },
                    coverage = langKnown.toFloat() / totalEvents,
                ),
                genreDistribution = Distribution(
                    buckets = genreRows.map {
                        DistributionBucket(
                            key = it.genre,
                            count = it.count,
                            label = it.genre?.let(::genreLabel) ?: "未知 / 未归类",
                        )
                    },
                    coverage = genreKnown.toFloat() / totalEvents,
                ),
                streakDays = streak.current,
                maxStreak = streak.max,
                firstSeenCount = firstSeenRows.size,
                heatmap = heatmapRows.map { DateBucket(it.dayEpochDay, it.seconds) },
            )
        }
    }
}
```

- [ ] **Step 5: 在 ListenStatsRepositoryTest 加 Asia/Shanghai 时区 case**

Edit `data/src/test/java/com/hank/musicfree/data/repository/listenstats/ListenStatsRepositoryTest.kt`,在文件末加:

```kotlin
@Test
fun snapshot_inAsiaShanghai_dailyBucketsMatchLocalDate() = runTest {
    // 构造 Asia/Shanghai 的 Repository
    val zone = java.time.ZoneId.of("Asia/Shanghai")
    val tzRepo = ListenStatsRepository(db.listenStatsDao(), zoneIdProvider = { zone })

    // 本地 2026-05-11 02:00 = UTC 2026-05-10 18:00
    val localDate = LocalDate.of(2026, 5, 11)
    val playedAtMs = localDate.atTime(2, 0).atZone(zone).toInstant().toEpochMilli()
    val title = "T"; val artistRaw = "A"
    val mergeKey = "${title.lowercase()}|${artistRaw.lowercase()}"
    db.listenStatsDao().insertEventWithArtists(
        ListenEventEntity(
            playedAtMs = playedAtMs, musicId = "m", platform = "p", title = title,
            artistRaw = artistRaw, album = null, artwork = null, durationMs = 240_000,
            playedSeconds = 60, completed = true, language = null, genre = null,
            mergeKey = mergeKey,
        ),
        listOf(ListenEventArtistEntity(eventId = 0, artistName = "A", artistOrder = 0)),
    )

    // 按 MONTH 取本月窗口
    val snap = tzRepo.statsForWindow(TimeScope.MONTH, localDate).first()
    assertEquals(1, snap.dailyBuckets.size)
    assertEquals(localDate.toEpochDay(), snap.dailyBuckets[0].dayEpochDay)
    assertEquals(1, snap.hourBuckets.size)
    assertEquals(2, snap.hourBuckets[0].hourOfDay)
}
```

- [ ] **Step 6: 跑 :data 全部单测**

Run:
```bash
./gradlew :data:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL。新增的 5 个 DaoTest case + 1 个 RepositoryTest case 全绿;既有 case 也全绿(UTC offset=0 时行为与改造前一致)。

- [ ] **Step 7: Commit**

```bash
git add data/src/main/java/com/hank/musicfree/data/db/dao/ListenStatsDao.kt \
        data/src/main/java/com/hank/musicfree/data/repository/listenstats/ListenStatsRepository.kt \
        data/src/test/java/com/hank/musicfree/data/db/dao/ListenStatsDaoTest.kt \
        data/src/test/java/com/hank/musicfree/data/repository/listenstats/ListenStatsRepositoryTest.kt
git commit -m "feat(data): listen-stats 聚合按 mergeKey + 日/时/热力图按本地时区分桶"
```

---

## Task 6: UI 接 CoverImage + 移除 platform 展示

**Files:**
- Modify: `feature/listen-stats/src/main/java/com/hank/musicfree/feature/listenstats/component/SongDetailRow.kt`
- Modify: `feature/listen-stats/src/main/java/com/hank/musicfree/feature/listenstats/component/TopSongsCard.kt`
- Modify: `feature/listen-stats/src/test/java/com/hank/musicfree/feature/listenstats/component/CardCompositionTest.kt`

- [ ] **Step 1: 在 CardCompositionTest 加 SongDetailRow / TopSongsCard 渲染测试(先红)**

Edit `feature/listen-stats/src/test/java/com/hank/musicfree/feature/listenstats/component/CardCompositionTest.kt`,在 import 段加:
```kotlin
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import com.hank.musicfree.data.db.dao.TopSongRow
import com.hank.musicfree.data.repository.listenstats.model.ListenedSong
```

在文件末加:

```kotlin
// ── Listen-Stats 修复:封面 + 移除 plugin 展示 ──

@Test fun topSongsCard_rowHasCover_andSubtitleNoPlatform() {
    val row = TopSongRow(
        musicId = "m1", platform = "qq",
        title = "情人知己", artistRaw = "叶蒨文",
        album = null, artwork = "https://x/cover.jpg",
        playCount = 3, totalSec = 180,
    )
    composeRule.setContent {
        MusicFreeTheme {
            TopSongsCard(rows = listOf(row), onSeeAll = {}, onRowClick = {})
        }
    }
    composeRule.onAllNodes(hasTestTag("top-song-cover")).assertCountEquals(1)
    composeRule.onNodeWithText("叶蒨文").assertIsDisplayed()
    composeRule.onAllNodesWithText("qq", substring = true).assertCountEquals(0)
    composeRule.onAllNodesWithText("·", substring = true).assertCountEquals(0)
}

@Test fun songDetailRow_hasCover_andSubtitleNoPlatform() {
    val song = ListenedSong(
        musicId = "m1", platform = "qq",
        title = "情人知己", artistRaw = "叶蒨文",
        album = null, artwork = "https://x/cover.jpg",
        firstSeenMs = 0, lastSeenMs = 0,
        playCount = 3, totalSec = 180,
    )
    composeRule.setContent {
        MusicFreeTheme {
            SongDetailRow(song = song)
        }
    }
    composeRule.onAllNodes(hasTestTag("song-cover")).assertCountEquals(1)
    composeRule.onNodeWithText("叶蒨文").assertIsDisplayed()
    composeRule.onAllNodesWithText("qq", substring = true).assertCountEquals(0)
    composeRule.onAllNodesWithText("·", substring = true).assertCountEquals(0)
}
```

- [ ] **Step 2: 跑 CardCompositionTest 看新两条失败**

Run:
```bash
./gradlew :feature:listen-stats:testDebugUnitTest --tests "*CardCompositionTest*"
```

Expected: 两条新测试失败 — `top-song-cover` / `song-cover` testTag 节点找不到(还没加),旧 subtitle 里 "qq" / "·" 仍存在。

- [ ] **Step 3: 改 `SongDetailRow.kt`**

Edit `feature/listen-stats/src/main/java/com/hank/musicfree/feature/listenstats/component/SongDetailRow.kt`:

```kotlin
package com.hank.musicfree.feature.listenstats.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.ui.CoverImage
import com.hank.musicfree.data.repository.listenstats.model.ListenedSong
import java.time.Instant
import java.time.ZoneId

@Composable
fun SongDetailRow(song: ListenedSong, showFirstSeen: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            uri = song.artwork,
            size = 44.dp,
            cornerRadius = 10.dp,
            modifier = Modifier.testTag("song-cover"),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                song.artistRaw,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (showFirstSeen) {
                val date = Instant.ofEpochMilli(song.firstSeenMs).atZone(ZoneId.systemDefault()).toLocalDate()
                Text(
                    "${date.monthValue}/${date.dayOfMonth} 首次",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text("${song.playCount} 次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
```

(对比原文件:删了 Box / background / shape / clip 旧 import 与占位 Box;subtitle 不再拼 `· ${song.platform}`)

- [ ] **Step 4: 改 `TopSongsCard.kt`**

Edit `feature/listen-stats/src/main/java/com/hank/musicfree/feature/listenstats/component/TopSongsCard.kt`:

```kotlin
package com.hank.musicfree.feature.listenstats.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.ui.CoverImage
import com.hank.musicfree.data.db.dao.TopSongRow

@Composable
fun TopSongsCard(
    rows: List<TopSongRow>,
    onSeeAll: () -> Unit,
    onRowClick: (TopSongRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Top 歌曲", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text("按播放次数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            rows.take(5).forEachIndexed { idx, row ->
                val rank = idx + 1
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onRowClick(row) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$rank",
                        Modifier.width(28.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                    CoverImage(
                        uri = row.artwork,
                        size = 40.dp,
                        cornerRadius = 8.dp,
                        modifier = Modifier.testTag("top-song-cover"),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            row.artistRaw,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Text("${row.playCount} 次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            Text(
                "查看全部 Top 50",
                Modifier.fillMaxWidth().padding(8.dp).clickable { onSeeAll() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
```

(对比原文件:加 CoverImage 行;subtitle 从 `"${row.artistRaw} · ${row.album.orEmpty()}"` 简化为 `row.artistRaw`)

- [ ] **Step 5: 重跑 CardCompositionTest,确认两条新断言通过**

Run:
```bash
./gradlew :feature:listen-stats:testDebugUnitTest --tests "*CardCompositionTest*"
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: 跑全 :feature:listen-stats 单测确认无回归**

Run:
```bash
./gradlew :feature:listen-stats:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL。`ListenStatsScreenTest` / `ListenDetailScreenTest` / `ListenStatsViewModelTest` / `ListenDetailViewModelTest` / 其它 CardCompositionTest case 全绿。

- [ ] **Step 7: Commit**

```bash
git add feature/listen-stats/src/main/java/com/hank/musicfree/feature/listenstats/component/SongDetailRow.kt \
        feature/listen-stats/src/main/java/com/hank/musicfree/feature/listenstats/component/TopSongsCard.kt \
        feature/listen-stats/src/test/java/com/hank/musicfree/feature/listenstats/component/CardCompositionTest.kt
git commit -m "feat(listen-stats): Top 卡 / 明细行加封面,去掉副标题里的插件来源"
```

---

## Task 7: 总验收 + 准备合并

**Files:** (no file changes;只是验证 & 文档)

- [ ] **Step 1: 跑 spec §7 的所有验收 command**

Run:
```bash
./gradlew :data:testDebugUnitTest --tests "*ListenStats*" --tests "*Migration10To11*"
./gradlew :player:testDebugUnitTest --tests "*ListenTracker*" --tests "*ArtistSplitter*"
./gradlew :feature:listen-stats:testDebugUnitTest
./gradlew :app:assembleDebug
```

Expected: 全 BUILD SUCCESSFUL。

(注:`Migration10To11Test` 是 androidTest,`:data:testDebugUnitTest --tests "*Migration10To11*"` 不会匹配它 — 这是预期;若有 device,加跑 `./gradlew :data:connectedDebugAndroidTest --tests "*Migration10To11*"`)

- [ ] **Step 2: 跑 :core 单测确认 ArtistSplitter 迁移无回归**

Run:
```bash
./gradlew :core:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 装 debug APK 到 UTC+8 设备做人工验收(若有设备)**

按 spec §9 的 5 大点过一遍:
- Top 歌曲卡 / 明细页每行显示封面(artwork 非 null 时显示真实图,null 时 placeholder 音符 icon)
- 副标题不再出现 plugin 名(如「叶蒨文 · 元力 QQ」→「叶蒨文」)
- 同一首歌从多插件听过只算 1 首(若有 QQ + 网易插件,分别播放同一首 ≥30s,「听过的歌曲」+1)
- 凌晨 02:00 本地听 ≥30s → 切到「日」视图选今天,日柱图 / 热力图 / 听歌时段 均落到当天 02 点桶
- 老 v1.0.1 用户升级:启动不崩,老事件 mergeKey 已回填

(若设备不可用,本步骤记录为"待运行态验收";后续可由人工补)

- [ ] **Step 4: 通报 worktree 内当前状态**

Run:
```bash
git log --oneline main..HEAD
git status --short
```

Expected: 输出本 worktree 上的所有 commit(应为 5 条 task 2-6 的 commit),工作树 clean。

- [ ] **Step 5: 提示用户做 squash merge 回 main**

按 AGENTS.md「worktree 分支合并回 main 时必须使用 `git merge --squash`,提交使用中文」:

```bash
# 在主工作区(不是 worktree 内)执行
cd /Users/zili/code/android/MusicFreeAndroid
git merge --squash listen-stats-fixes
git commit -m "feat(listen-stats): 修复封面、跨插件归并、本地时区三项问题

- ListenEventEntity 加 mergeKey 列与 v10→v11 迁移(回填老数据)
- DAO 全部聚合按 mergeKey;日/时/热力图按 system zone offset 切桶
- ArtistSplitter 从 :player 迁到 :core 复用
- Top 卡 / 明细行接 CoverImage;副标题去掉 plugin 名"

# 清理 worktree(可选)
git worktree remove .worktrees/listen-stats-fixes
git branch -D listen-stats-fixes
```

(用户决定何时执行;本计划到此停止。merge 后再做 release 打包 / 灰度等流程由 spec / RELEASE.md 另行约束)

---

## 自检 checklist (执行前快速过)

- [x] **Spec §1 决策清单**:全部体现在 Task 3-6 — mergeKey 公式(Task 3/4)、ArtistSplitter 共享(Task 2)、CoverImage 范围 SongDetailRow + TopSongsCard(Task 6)、zoneOffsetMs 参数(Task 5)、保留 platform 列(Task 3 entity 保留;Task 5 DAO 仍 SELECT MAX(platform))
- [x] **Spec §2 schema**:Task 3 完整落地
- [x] **Spec §3 写入路径**:Task 4 完整落地
- [x] **Spec §4 DAO 改造**:Task 5 全部 9 个 query 都改了(其中 4 个 NOT 改 — totalSecondsFlow / distinctArtistsFlow / languageDistributionFlow / genreDistributionFlow,符合 spec 表里"不在变更列表"的语义)
- [x] **Spec §5 Repository**:Task 5 step 4
- [x] **Spec §6 UI**:Task 6(用真实 CoverImage 签名,见顶部「Spec 偏差需知」)
- [x] **Spec §7 测试**:覆盖 ArtistSplitter / ListenTracker(含空 artist) / Migration10To11(含三条 case) / Dao(跨插件 + 时区 4 case) / Repository(Asia/Shanghai 1 case) / CardComposition(SongDetailRow + TopSongsCard)
- [x] **Spec §9 验收**:Task 7 step 1 跑全部 command;step 3 列出人工验收清单
- [x] **没有 placeholder / TBD**:每个 step 都有完整代码或完整命令
- [x] **类型一致性**:`mergeKey: String` 在 entity / migration / tracker / dao / test 中名称与类型一致;`zoneOffsetMs: Long` 在 dao / repository / test 中名称与类型一致;`CoverImage(uri, modifier, size, cornerRadius)` 签名在 SongDetailRow / TopSongsCard / CardCompositionTest 中调用一致

---

## 范围外 / 不在本计划中

- title 剥括号、live / karaoke 归并 — spec §10 列为 v2
- 译名 / 跨语言艺人合并 — spec §10 列为 v2
- TopArtistsCard 加歌手头像 — spec §10 列为 v2(需 schema 调整)
- 跨时区漫游历史 — spec §10 列为 v2(需写入时落地 localDayEpoch)
- 删除 platform 列 — spec §10 决定不做
- release 打包 / 灰度 / 发版 — 走 `RELEASE.md`,不在本计划

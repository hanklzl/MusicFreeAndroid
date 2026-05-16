# Listen Stats Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现「听歌足迹」功能 — listen_event 持久化事件表 + 多歌手子表 + 五时间窗统计页 + 通用明细页 + 侧栏「我的」入口。

**Architecture:** `:player` 内 `ListenTracker` 委托 `PlayerController` 现有 `Player.Listener`，把"≥30s 有效收听"按 wall-clock 累计后写入 `:data` 的 `listen_event` 表（v9→v10 迁移）。新模块 `:feature:listen-stats` 提供统计页与通用明细页，所有下钻跑同一 `Repository.detail()` 接口。`:feature:home` 抽屉顶部加「我的」section。

**Tech Stack:** Kotlin + Jetpack Compose Material3、Room 2.x、Hilt、Media3 ExoPlayer、Coroutines Flow、Navigation Compose + `@Serializable` 路由、JUnit4 + Robolectric + Mockito-kotlin。

**Spec reference:** `docs/superpowers/specs/2026-05-15-listen-stats-design.md`（所有 §N 引用都指向该文件）。

**Pre-execution worktree setup**：

```bash
git fetch origin && git checkout main && git pull --ff-only
git worktree add .worktrees/listen-stats -b feat/listen-stats
cd .worktrees/listen-stats
```

后续所有 task 都在 `.worktrees/listen-stats` 下执行。最终合并回 main 时按 `AGENTS.md > Git Worktree 开发约束`：`git merge --squash` 单笔提交，conventional commit + 中文。

---

## File Structure

### 新建文件

| 路径 | 责任 |
|---|---|
| `feature/listen-stats/build.gradle.kts` | 新模块 gradle，照搬 `:feature:search` 模板 |
| `feature/listen-stats/src/main/AndroidManifest.xml` | 空 manifest |
| `core/src/main/java/com/hank/musicfree/core/navigation/Routes.kt`（**改**） | 加 `ListenStatsRoute` / `ListenDetailRoute` |
| `core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt`（**改**） | 加 `Home.DrawerMeListenStats` |
| `data/.../db/entity/ListenEventEntity.kt` | listen_event 主表 |
| `data/.../db/entity/ListenEventArtistEntity.kt` | listen_event_artist 子表 |
| `data/.../db/migration/Migration9To10.kt` | DB 迁移 |
| `data/.../db/AppDatabase.kt`（**改**） | bump version=10, +entities, +migration |
| `data/.../db/di/AppDatabaseModule.kt`（**改**，若存在） | `.addMigrations(MIGRATION_9_10)` |
| `data/.../db/dao/ListenStatsDao.kt` | DAO + query row 数据类 |
| `data/.../repository/listenstats/ListenStatsRepository.kt` | 聚合 + clearAll + firstEventDate |
| `data/.../repository/listenstats/model/*.kt` | `TimeScope`, `DetailMode`, `ListenStatsSnapshot`, `Distribution`, `ListenedSong` 等 |
| `data/src/androidTest/.../AppDatabaseMigration9To10Test.kt` | `MigrationTestHelper` 单测 |
| `data/src/test/.../ListenStatsDaoTest.kt` | Robolectric in-memory Room DAO 测试 |
| `data/src/test/.../ListenStatsRepositoryTest.kt` | Repository 聚合测试 |
| `player/.../listening/ArtistSplitter.kt` | 多歌手拆分 |
| `player/.../listening/ListenDimExtractor.kt` | rawJson 风格/语言归一化 |
| `player/.../listening/ListenTracker.kt` | state machine |
| `player/src/test/.../listening/ArtistSplitterTest.kt` | |
| `player/src/test/.../listening/ListenDimExtractorTest.kt` | |
| `player/src/test/.../listening/ListenTrackerTest.kt` | |
| `player/.../controller/PlayerController.kt`（**改**） | 注入 `ListenTracker` + 三处 listener 委托 |
| `feature/home/.../HomeDrawerNavigation.kt`（**改**） | `OpenListenStats` action + 「我的」section |
| `feature/home/.../component/HomeIcons.kt`（**改**） | `DrawerListenStats` 资源引用 |
| `feature/home/src/main/res/drawable/ic_home_chart_bar_outline.xml` | 新 icon |
| `feature/home/.../HomeScreen.kt` / `HomeScreenContent.kt`（**改**） | dispatch `OpenListenStats` |
| `feature/home/src/test/.../HomeDrawerUiModelTest.kt`（**改**） | 加新 section 断言 |
| `feature/home/src/test/.../HomeAnchorContractTest.kt`（**改**） | 加 `DrawerMeListenStats` 断言 |
| `app/src/androidTest/.../HomeDrawerListenStatsEntryTest.kt` | 仪器测试：抽屉跳转 |
| `feature/listen-stats/src/main/.../ListenStatsScreen.kt` | 统计页骨架 |
| `feature/listen-stats/src/main/.../ListenStatsScreenState.kt` | UI state |
| `feature/listen-stats/src/main/.../ListenStatsViewModel.kt` | scope/anchor + flow + clear |
| `feature/listen-stats/src/main/.../ListenDetailScreen.kt` | 通用明细页 |
| `feature/listen-stats/src/main/.../ListenDetailScreenState.kt` | |
| `feature/listen-stats/src/main/.../ListenDetailViewModel.kt` | mode→filter |
| `feature/listen-stats/src/main/.../component/*.kt` (15 个 composables) | Hero/SegmentedTimeScope/TimeScopePager/SecondaryKpiRow/DailyBarsCard/TopSongsCard/TopArtistsCard/LanguageCard/GenreCard/HourCard/StreakDiscoveryRow/HeatmapCard/SongDetailRow/MoreMenu/ClearStatsDialog |
| `feature/listen-stats/src/main/.../navigation/ListenStatsNavigation.kt` | `NavGraphBuilder` 扩展 |
| `feature/listen-stats/src/test/.../*.kt` | ViewModel + Compose 单测一组 |
| `app/.../navigation/AppNavHost.kt`（**改**） | 挂 `listenStatsScreen()` |
| `app/build.gradle.kts`（**改**） | 加 `:feature:listen-stats` dep |
| `settings.gradle.kts`（**改**） | `include(":feature:listen-stats")` |

每个 task 自含。

---

## Task 1: 新建 `:feature:listen-stats` 空模块

**Files:**
- Create: `feature/listen-stats/build.gradle.kts`
- Create: `feature/listen-stats/src/main/AndroidManifest.xml`
- Modify: `settings.gradle.kts:36`（在 `:feature:search` 后一行加 `include`）
- Modify: `app/build.gradle.kts:141`（dep 列表末尾追加）

- [ ] **Step 1: 创建模块目录与 manifest**

```bash
mkdir -p feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats
mkdir -p feature/listen-stats/src/main/res
mkdir -p feature/listen-stats/src/test/java/com/hank/musicfree/feature/liststats
```

写 `feature/listen-stats/src/main/AndroidManifest.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 2: 写模块 build.gradle.kts（照搬 `:feature:search`）**

写 `feature/listen-stats/build.gradle.kts`：

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.hank.musicfree.feature.liststats"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.all {
            it.jvmArgs("-Dnet.bytebuddy.experimental=true")
        }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    implementation(project(":logging"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.kotlin)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

- [ ] **Step 3: 注册模块到 settings + app**

`settings.gradle.kts` 在 `include(":feature:settings")` 之后加一行：

```kotlin
include(":feature:listen-stats")
```

`app/build.gradle.kts` 在 `implementation(project(":feature:settings"))` 之后加一行：

```kotlin
implementation(project(":feature:listen-stats"))
```

- [ ] **Step 4: 验证模块可被 Gradle 识别**

Run: `./gradlew :feature:listen-stats:assembleDebug`
Expected: BUILD SUCCESSFUL（即使没有 source 代码，AGP 应只产生空 AAR）

- [ ] **Step 5: Commit**

```bash
git add feature/listen-stats settings.gradle.kts app/build.gradle.kts
git commit -m "$(cat <<'EOF'
feat(listen-stats): 新建 :feature:listen-stats 空模块

照搬 :feature:search 的 build 模板；加入 settings.gradle.kts 与 app
依赖图。后续 task 在该模块下填业务代码。
EOF
)"
```

---

## Task 2: 加路由与 FidelityAnchor

**Files:**
- Modify: `core/src/main/java/com/hank/musicfree/core/navigation/Routes.kt:227`（文件末尾追加）
- Modify: `core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt:43`（`Home` object 内追加）
- Test: `app/src/test/java/com/hank/musicfree/RoutesTest.kt`（追加 case）

- [ ] **Step 1: 写失败的路由序列化测试**

`app/src/test/java/com/hank/musicfree/RoutesTest.kt` 末尾加：

```kotlin
@Test
fun `ListenStatsRoute defaults serialize round-trip`() {
    val route = com.hank.musicfree.core.navigation.ListenStatsRoute()
    val json = Json.encodeToString(serializer(), route)
    val decoded = Json.decodeFromString<com.hank.musicfree.core.navigation.ListenStatsRoute>(json)
    assertEquals(route, decoded)
    assertEquals("WEEK", decoded.scope)
    assertEquals(-1L, decoded.anchorEpochDay)
}

@Test
fun `ListenDetailRoute requires mode and propagates filterValue`() {
    val route = com.hank.musicfree.core.navigation.ListenDetailRoute(
        mode = "BY_ARTIST",
        scope = "WEEK",
        anchorEpochDay = 20221L,
        filterValue = "周杰伦",
    )
    val json = Json.encodeToString(serializer(), route)
    val decoded = Json.decodeFromString<com.hank.musicfree.core.navigation.ListenDetailRoute>(json)
    assertEquals(route, decoded)
    assertEquals("周杰伦", decoded.filterValue)
}
```

- [ ] **Step 2: 跑测试，确认未编译 / FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "*RoutesTest*"`
Expected: COMPILATION FAILED — `ListenStatsRoute` / `ListenDetailRoute` 未定义

- [ ] **Step 3: 实现路由**

`core/.../navigation/Routes.kt` 末尾追加：

```kotlin
@Serializable
data class ListenStatsRoute(
    val scope: String = "WEEK",
    val anchorEpochDay: Long = -1L,
)

@Serializable
data class ListenDetailRoute(
    val mode: String,
    val scope: String,
    val anchorEpochDay: Long,
    val filterValue: String? = null,
)
```

- [ ] **Step 4: 实现 FidelityAnchor**

`core/.../ui/FidelityAnchors.kt` 的 `Home` object 内（在 `DrawerSettings` 前一行）追加：

```kotlin
const val DrawerMeListenStats = "home.drawer.me.listenStats"
```

- [ ] **Step 5: 跑测试，确认 PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*RoutesTest*"`
Expected: PASS（含上述 2 个新 case）

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/hank/musicfree/core/navigation/Routes.kt \
        core/src/main/java/com/hank/musicfree/core/ui/FidelityAnchors.kt \
        app/src/test/java/com/hank/musicfree/RoutesTest.kt
git commit -m "feat(core): 新增 ListenStatsRoute / ListenDetailRoute 与 FidelityAnchor"
```

---

## Task 3: 新增 listen_event entity 与 listen_event_artist 子表

**Files:**
- Create: `data/src/main/java/com/hank/musicfree/data/db/entity/ListenEventEntity.kt`
- Create: `data/src/main/java/com/hank/musicfree/data/db/entity/ListenEventArtistEntity.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt`（version 9→10, +entities, +migration）

- [ ] **Step 1: 写 ListenEventEntity**

`data/.../db/entity/ListenEventEntity.kt`：

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
)
```

- [ ] **Step 2: 写 ListenEventArtistEntity**

`data/.../db/entity/ListenEventArtistEntity.kt`：

```kotlin
package com.hank.musicfree.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "listen_event_artist",
    foreignKeys = [ForeignKey(
        entity = ListenEventEntity::class,
        parentColumns = ["id"],
        childColumns = ["eventId"],
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
    val artistName: String,
    val artistOrder: Int,
)
```

- [ ] **Step 3: AppDatabase 加 entity 与 bump version**

`data/.../db/AppDatabase.kt`：
- entities 数组加入 `ListenEventEntity::class, ListenEventArtistEntity::class`
- `version = 9` 改为 `version = 10`
- imports 顶部加：
  ```kotlin
  import com.hank.musicfree.data.db.entity.ListenEventEntity
  import com.hank.musicfree.data.db.entity.ListenEventArtistEntity
  ```

- [ ] **Step 4: 跑 build，让 Room 重新导出 schema**

Run: `./gradlew :data:assembleDebug`
Expected: BUILD SUCCESSFUL；同时新出现 `data/schemas/com.hank.musicfree.data.db.AppDatabase/10.json`

- [ ] **Step 5: 提交 schema 与 entity（不含 migration，留给 Task 4）**

```bash
git add data/src/main/java/com/hank/musicfree/data/db/entity/ListenEventEntity.kt \
        data/src/main/java/com/hank/musicfree/data/db/entity/ListenEventArtistEntity.kt \
        data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt \
        data/schemas/com.hank.musicfree.data.db.AppDatabase/10.json
git commit -m "feat(data): 新增 listen_event 与 listen_event_artist entity，bump DB v9→v10"
```

> 注：此 commit 之后 db 是"无 migration 的 v10"。Task 4 立刻补 migration；不要在此 commit 后做任何会触发 Room 启动的本机运行。

---

## Task 4: Migration 9→10 + DB module 改造

**Files:**
- Create: `data/src/main/java/com/hank/musicfree/data/db/migration/Migration9To10.kt`
- Modify: `data/.../db/AppDatabase.kt` 或 `data/.../db/di/AppDatabaseModule.kt`（视 DI 写法）

- [ ] **Step 1: 查找 Room 实例化点**

Run: `grep -rn "Room.databaseBuilder\|fallbackToDestructiveMigration" data/src/main/java/`
Expected: 找到 `Room.databaseBuilder(...)`. .build() 调用点。**若存在 `fallbackToDestructiveMigration()`，记下位置，下一步删除并替换。**

- [ ] **Step 2: 写 Migration9To10**

`data/.../db/migration/Migration9To10.kt`：

```kotlin
package com.hank.musicfree.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `listen_event` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `playedAtMs` INTEGER NOT NULL,
                `musicId` TEXT NOT NULL,
                `platform` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `artistRaw` TEXT NOT NULL,
                `album` TEXT,
                `artwork` TEXT,
                `durationMs` INTEGER NOT NULL,
                `playedSeconds` INTEGER NOT NULL,
                `completed` INTEGER NOT NULL,
                `language` TEXT,
                `genre` TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_listen_event_playedAtMs` ON `listen_event` (`playedAtMs`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_listen_event_musicId_platform` ON `listen_event` (`musicId`, `platform`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `listen_event_artist` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `eventId` INTEGER NOT NULL,
                `artistName` TEXT NOT NULL,
                `artistOrder` INTEGER NOT NULL,
                FOREIGN KEY(`eventId`) REFERENCES `listen_event`(`id`) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_listen_event_artist_eventId` ON `listen_event_artist` (`eventId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_listen_event_artist_artistName` ON `listen_event_artist` (`artistName`)")
    }
}
```

- [ ] **Step 3: 在 Room builder 链上挂 migration、移除 destructive fallback**

在 Step 1 找到的 `Room.databaseBuilder(...)` 链上：
1. 删除 `.fallbackToDestructiveMigration()`（如有）— 按 `AGENTS.md > 数据库迁移规范`。
2. 加入 `.addMigrations(com.hank.musicfree.data.db.migration.MIGRATION_9_10)`。

例如：

```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, "musicfree.db")
    .addMigrations(MIGRATION_9_10)         // ← 新增
    // .fallbackToDestructiveMigration()    // ← 如存在则删除
    .addCallback(seedFavoriteCallback)
    .build()
```

- [ ] **Step 4: 比对 Room 生成的 SQL 与 migration SQL 一致**

打开 `data/schemas/com.hank.musicfree.data.db.AppDatabase/10.json`，找到 `listen_event` / `listen_event_artist` 的 `createSql`，对照 Migration9To10 内 `execSQL` 的字符串：列顺序、`NOT NULL`、`AUTOINCREMENT`、`FOREIGN KEY` 子句必须完全一致；不一致就以 schema json 为准修改 migration SQL。

- [ ] **Step 5: build 验证**

Run: `./gradlew :data:assembleDebug`
Expected: BUILD SUCCESSFUL，无 Room 警告（Room 编译时会校验 entity 与 schema 是否匹配）。

- [ ] **Step 6: Commit**

```bash
git add data/src/main/java/com/hank/musicfree/data/db/migration/Migration9To10.kt \
        data/src/main/java/com/hank/musicfree/data/db/  # 含被修改的 AppDatabase 或 module
git commit -m "feat(data): 加 Migration 9→10 创建 listen_event 表，移除 destructive fallback"
```

---

## Task 5: MigrationTestHelper 单测覆盖 v9→v10

**Files:**
- Create: `data/src/androidTest/java/com/hank/musicfree/data/db/AppDatabaseMigration9To10Test.kt`
- Modify: `data/build.gradle.kts`（确保 `androidx.room:room-testing` 在 androidTestImplementation；若已有可跳过）

- [ ] **Step 1: 确认 room-testing 依赖**

Run: `grep -n "room-testing\|room.testing" data/build.gradle.kts`
若无，则在 `dependencies { ... }` 中加：

```kotlin
androidTestImplementation(libs.androidx.room.testing)  // 或 androidTestImplementation("androidx.room:room-testing:<roomVersion>")
```

- [ ] **Step 2: 写 migration 测试**

`data/src/androidTest/.../db/AppDatabaseMigration9To10Test.kt`：

```kotlin
package com.hank.musicfree.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hank.musicfree.data.db.migration.MIGRATION_9_10
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

private const val TEST_DB = "migration-test"

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration9To10Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate9To10_createsListenEventTables_andCascadeWorks() {
        helper.createDatabase(TEST_DB, 9).close()

        helper.runMigrationsAndValidate(TEST_DB, 10, true, MIGRATION_9_10).use { db ->
            db.execSQL("""
                INSERT INTO listen_event(playedAtMs, musicId, platform, title, artistRaw,
                  album, artwork, durationMs, playedSeconds, completed, language, genre)
                VALUES(1000, 'm1', 'netease', 'Song', '周杰伦 & 林俊杰',
                  null, null, 240000, 60, 0, 'zh-CN', 'pop')
            """.trimIndent())
            db.execSQL("""
                INSERT INTO listen_event_artist(eventId, artistName, artistOrder)
                VALUES(1, '周杰伦', 0), (1, '林俊杰', 1)
            """.trimIndent())

            db.query("SELECT COUNT(*) FROM listen_event_artist").use { c ->
                c.moveToFirst(); assertEquals(2, c.getInt(0))
            }

            db.execSQL("DELETE FROM listen_event WHERE id = 1")
            db.query("SELECT COUNT(*) FROM listen_event_artist").use { c ->
                c.moveToFirst(); assertEquals("cascade should delete artist rows", 0, c.getInt(0))
            }
        }

        // 跑完 migration 后让 Room 用最新 schema 打开一次，验证 entity 与 db 完全对得上
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB,
        ).addMigrations(MIGRATION_9_10).build().apply {
            openHelper.writableDatabase
            close()
        }
    }
}
```

- [ ] **Step 3: 跑仪器测试**

Run（需要连模拟器或设备）：`./gradlew :data:connectedDebugAndroidTest --tests "*Migration9To10Test*"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add data/src/androidTest/java/com/hank/musicfree/data/db/AppDatabaseMigration9To10Test.kt \
        data/build.gradle.kts
git commit -m "test(data): MigrationTestHelper 覆盖 v9→v10 + cascade FK 行为"
```

---

## Task 6: ListenStatsDao 数据查询接口

**Files:**
- Create: `data/src/main/java/com/hank/musicfree/data/db/dao/ListenStatsDao.kt`
- Modify: `data/.../db/AppDatabase.kt` 加 `abstract fun listenStatsDao(): ListenStatsDao`

- [ ] **Step 1: 写 DAO（含 query row 数据类）**

`data/.../db/dao/ListenStatsDao.kt`：

```kotlin
package com.hank.musicfree.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.hank.musicfree.data.db.entity.ListenEventArtistEntity
import com.hank.musicfree.data.db.entity.ListenEventEntity
import kotlinx.coroutines.flow.Flow

data class TopSongRow(
    val musicId: String,
    val platform: String,
    val title: String,
    val artistRaw: String,
    val album: String?,
    val artwork: String?,
    val playCount: Int,
    val totalSec: Long,
)

data class TopArtistRow(
    val artistName: String,
    val playCount: Int,
    val songCount: Int,
    val totalSec: Long,
)

data class DailyBucketRow(val dayEpochDay: Long, val seconds: Long)
data class HourBucketRow(val hourOfDay: Int, val seconds: Long)
data class LanguageBucketRow(val language: String?, val count: Int)
data class GenreBucketRow(val genre: String?, val count: Int)
data class DateBucketRow(val dayEpochDay: Long, val seconds: Long)
data class ListenedSongRow(
    val musicId: String,
    val platform: String,
    val title: String,
    val artistRaw: String,
    val album: String?,
    val artwork: String?,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val playCount: Int,
    val totalSec: Long,
)

@Dao
interface ListenStatsDao {

    @Transaction
    suspend fun insertEventWithArtists(
        event: ListenEventEntity,
        artists: List<ListenEventArtistEntity>,
    ) {
        val id = insertEvent(event)
        if (artists.isNotEmpty()) {
            insertArtists(artists.map { it.copy(eventId = id) })
        }
    }

    @Insert suspend fun insertEvent(event: ListenEventEntity): Long
    @Insert suspend fun insertArtists(artists: List<ListenEventArtistEntity>)

    @Query("DELETE FROM listen_event")
    suspend fun clearAllEvents(): Int

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

    @Query("""SELECT a.artistName,
                     COUNT(DISTINCT e.id) AS playCount,
                     COUNT(DISTINCT e.musicId || '||' || e.platform) AS songCount,
                     IFNULL(SUM(e.playedSeconds), 0) AS totalSec
              FROM listen_event_artist a JOIN listen_event e ON a.eventId = e.id
              WHERE e.playedAtMs >= :startMs AND e.playedAtMs < :endMs
              GROUP BY a.artistName
              ORDER BY playCount DESC, totalSec DESC
              LIMIT :limit""")
    fun topArtistsFlow(startMs: Long, endMs: Long, limit: Int): Flow<List<TopArtistRow>>

    @Query("""SELECT CAST((playedAtMs / 86400000) AS INTEGER) AS dayEpochDay,
                     SUM(playedSeconds) AS seconds
              FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
              GROUP BY dayEpochDay
              ORDER BY dayEpochDay ASC""")
    fun dailyBucketsFlow(startMs: Long, endMs: Long): Flow<List<DailyBucketRow>>

    @Query("""SELECT CAST(((playedAtMs / 1000 / 3600) % 24) AS INTEGER) AS hourOfDay,
                     SUM(playedSeconds) AS seconds
              FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
              GROUP BY hourOfDay
              ORDER BY hourOfDay ASC""")
    fun hourBucketsFlow(startMs: Long, endMs: Long): Flow<List<HourBucketRow>>

    @Query("""SELECT language, COUNT(*) AS count FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
              GROUP BY language ORDER BY count DESC""")
    fun languageDistributionFlow(startMs: Long, endMs: Long): Flow<List<LanguageBucketRow>>

    @Query("""SELECT genre, COUNT(*) AS count FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
              GROUP BY genre ORDER BY count DESC""")
    fun genreDistributionFlow(startMs: Long, endMs: Long): Flow<List<GenreBucketRow>>

    @Query("""SELECT CAST((playedAtMs / 86400000) AS INTEGER) AS dayEpochDay,
                     SUM(playedSeconds) AS seconds
              FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
              GROUP BY dayEpochDay
              ORDER BY dayEpochDay ASC""")
    fun heatmapFlow(startMs: Long, endMs: Long): Flow<List<DateBucketRow>>

    @Query("""SELECT e.musicId, e.platform, e.title, e.artistRaw, e.album, e.artwork,
                     MIN(e.playedAtMs) AS firstSeenMs,
                     MAX(e.playedAtMs) AS lastSeenMs,
                     COUNT(*) AS playCount,
                     SUM(e.playedSeconds) AS totalSec
              FROM listen_event e
              WHERE e.playedAtMs >= :startMs AND e.playedAtMs < :endMs
              GROUP BY e.musicId, e.platform
              HAVING MIN(e.playedAtMs) = (
                  SELECT MIN(e2.playedAtMs) FROM listen_event e2
                  WHERE e2.musicId = e.musicId AND e2.platform = e.platform
              )
              ORDER BY firstSeenMs DESC""")
    fun firstSeenInWindowFlow(startMs: Long, endMs: Long): Flow<List<ListenedSongRow>>

    @Query("""SELECT musicId, platform, title, artistRaw, album, artwork,
                     MIN(playedAtMs) AS firstSeenMs,
                     MAX(playedAtMs) AS lastSeenMs,
                     COUNT(*) AS playCount,
                     SUM(playedSeconds) AS totalSec
              FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
              GROUP BY musicId, platform
              ORDER BY playCount DESC, totalSec DESC""")
    fun allSongsInWindowFlow(startMs: Long, endMs: Long): Flow<List<ListenedSongRow>>

    @Query("""SELECT e.musicId, e.platform, e.title, e.artistRaw, e.album, e.artwork,
                     MIN(e.playedAtMs) AS firstSeenMs,
                     MAX(e.playedAtMs) AS lastSeenMs,
                     COUNT(*) AS playCount,
                     SUM(e.playedSeconds) AS totalSec
              FROM listen_event e
              JOIN listen_event_artist a ON a.eventId = e.id
              WHERE e.playedAtMs >= :startMs AND e.playedAtMs < :endMs
                AND a.artistName = :artistName
              GROUP BY e.musicId, e.platform
              ORDER BY playCount DESC""")
    fun songsByArtistFlow(startMs: Long, endMs: Long, artistName: String): Flow<List<ListenedSongRow>>

    @Query("""SELECT musicId, platform, title, artistRaw, album, artwork,
                     MIN(playedAtMs) AS firstSeenMs,
                     MAX(playedAtMs) AS lastSeenMs,
                     COUNT(*) AS playCount,
                     SUM(playedSeconds) AS totalSec
              FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
                AND language = :language
              GROUP BY musicId, platform
              ORDER BY playCount DESC""")
    fun songsByLanguageFlow(startMs: Long, endMs: Long, language: String): Flow<List<ListenedSongRow>>

    @Query("""SELECT musicId, platform, title, artistRaw, album, artwork,
                     MIN(playedAtMs) AS firstSeenMs,
                     MAX(playedAtMs) AS lastSeenMs,
                     COUNT(*) AS playCount,
                     SUM(playedSeconds) AS totalSec
              FROM listen_event
              WHERE playedAtMs >= :startMs AND playedAtMs < :endMs
                AND genre = :genre
              GROUP BY musicId, platform
              ORDER BY playCount DESC""")
    fun songsByGenreFlow(startMs: Long, endMs: Long, genre: String): Flow<List<ListenedSongRow>>
}
```

- [ ] **Step 2: AppDatabase 暴露 DAO**

`data/.../db/AppDatabase.kt` 类内追加：

```kotlin
abstract fun listenStatsDao(): ListenStatsDao
```

并 import `ListenStatsDao`。

- [ ] **Step 3: build 验证 DAO 编译 + Room SQL 校验**

Run: `./gradlew :data:assembleDebug`
Expected: BUILD SUCCESSFUL（Room 编译期会校验所有 `@Query` SQL 合法）

- [ ] **Step 4: Commit**

```bash
git add data/src/main/java/com/hank/musicfree/data/db/dao/ListenStatsDao.kt \
        data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt
git commit -m "feat(data): ListenStatsDao 实时聚合 + 8 种明细 mode 查询"
```

---

## Task 7: ListenStatsDao 单测（in-memory Room）

**Files:**
- Create: `data/src/test/java/com/hank/musicfree/data/db/dao/ListenStatsDaoTest.kt`
- Modify: `data/build.gradle.kts` 确保 Robolectric 在 testImplementation

- [ ] **Step 1: 确认 Robolectric 依赖**

```bash
grep -n "robolectric" data/build.gradle.kts
```
若无，加：

```kotlin
testImplementation(libs.robolectric)         // 或 testImplementation("org.robolectric:robolectric:<v>")
testImplementation(libs.androidx.test.core)
```

- [ ] **Step 2: 写 DAO 单测**

`data/src/test/.../dao/ListenStatsDaoTest.kt`：

```kotlin
package com.hank.musicfree.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.entity.ListenEventArtistEntity
import com.hank.musicfree.data.db.entity.ListenEventEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ListenStatsDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ListenStatsDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.listenStatsDao()
    }

    @After
    fun tearDown() = db.close()

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
        dao.insertEventWithArtists(
            event = ListenEventEntity(
                playedAtMs = playedAtMs, musicId = musicId, platform = platform,
                title = title, artistRaw = artists.joinToString(" & "),
                album = null, artwork = null, durationMs = durationMs,
                playedSeconds = playedSeconds, completed = completed,
                language = language, genre = genre,
            ),
            artists = artists.mapIndexed { i, n -> ListenEventArtistEntity(eventId = 0, artistName = n, artistOrder = i) },
        )
    }

    @Test
    fun totalSeconds_returnsSumWithinWindow_excludesOutside() = runTest {
        seed(playedAtMs = 1_000, musicId = "m1", playedSeconds = 30)
        seed(playedAtMs = 2_000, musicId = "m2", playedSeconds = 60)
        seed(playedAtMs = 99_999, musicId = "m3", playedSeconds = 999) // out

        val total = dao.totalSecondsFlow(startMs = 0, endMs = 50_000).first()
        assertEquals(90, total)
    }

    @Test
    fun distinctSongs_artists_GroupCorrectly() = runTest {
        seed(playedAtMs = 1, musicId = "m1", artists = listOf("周杰伦", "林俊杰"))
        seed(playedAtMs = 2, musicId = "m1", artists = listOf("周杰伦", "林俊杰"))   // 同歌
        seed(playedAtMs = 3, musicId = "m2", artists = listOf("周杰伦"))

        assertEquals(2, dao.distinctSongsFlow(0, 100).first())
        assertEquals(2, dao.distinctArtistsFlow(0, 100).first())
    }

    @Test
    fun topSongs_ordersByPlayCountDescTotalSecTiebreak() = runTest {
        seed(1, "m1", playedSeconds = 30)
        seed(2, "m1", playedSeconds = 30)
        seed(3, "m2", playedSeconds = 60)
        seed(4, "m2", playedSeconds = 60)
        seed(5, "m3", playedSeconds = 100)  // 1 次 100 秒，应排第三

        val top = dao.topSongsFlow(0, 100, limit = 5).first()
        assertEquals(listOf("m1", "m2", "m3"), top.map { it.musicId })
        assertEquals(2, top[0].playCount); assertEquals(60, top[0].totalSec)
    }

    @Test
    fun topArtists_countsCoFeaturesIndependently() = runTest {
        seed(1, "m1", artists = listOf("A", "B"))
        seed(2, "m2", artists = listOf("A"))
        seed(3, "m3", artists = listOf("B"))

        val top = dao.topArtistsFlow(0, 100, limit = 5).first()
        val byName = top.associateBy { it.artistName }
        assertEquals(2, byName.getValue("A").playCount)
        assertEquals(2, byName.getValue("B").playCount)
    }

    @Test
    fun firstSeen_onlyIncludesMusicFirstAppearingInWindow() = runTest {
        seed(1_000, "old")        // 老歌
        seed(5_000_000, "old")    // 后来又听
        seed(5_001_000, "newDiscovery")

        val firstSeen = dao.firstSeenInWindowFlow(startMs = 4_000_000, endMs = 6_000_000).first()
        assertEquals(listOf("newDiscovery"), firstSeen.map { it.musicId })
    }

    @Test
    fun clearAllEvents_alsoCascadesArtistRows() = runTest {
        seed(1, "m1", artists = listOf("X", "Y"))
        assertEquals(1, dao.clearAllEvents())
        assertEquals(0, dao.distinctArtistsFlow(0, 100).first())
    }
}
```

- [ ] **Step 3: 跑测试**

Run: `./gradlew :data:testDebugUnitTest --tests "*ListenStatsDaoTest*"`
Expected: 全 PASS

- [ ] **Step 4: Commit**

```bash
git add data/src/test/java/com/hank/musicfree/data/db/dao/ListenStatsDaoTest.kt \
        data/build.gradle.kts
git commit -m "test(data): ListenStatsDao 时间窗 / 分组 / 首次 / 清除 行为覆盖"
```

---

## Task 8: 数据 model 与 TimeScope helper

**Files:**
- Create: `data/src/main/java/com/hank/musicfree/data/repository/listenstats/model/TimeScope.kt`
- Create: `data/.../listenstats/model/DetailMode.kt`
- Create: `data/.../listenstats/model/Snapshot.kt`（含 ListenStatsSnapshot、Distribution、DailyBucket、HourBucket、DateBucket）
- Create: `data/.../listenstats/model/ListenedSong.kt`
- Create: `data/.../listenstats/model/TimeWindow.kt`（计算 startMs/endMs）

- [ ] **Step 1: 写 TimeScope + DetailMode + 数据类**

`data/.../listenstats/model/TimeScope.kt`：

```kotlin
package com.hank.musicfree.data.repository.listenstats.model

enum class TimeScope { DAY, WEEK, MONTH, YEAR, ALL_TIME }

fun parseTimeScope(raw: String): TimeScope = runCatching { TimeScope.valueOf(raw) }.getOrDefault(TimeScope.WEEK)
```

`data/.../listenstats/model/DetailMode.kt`：

```kotlin
package com.hank.musicfree.data.repository.listenstats.model

enum class DetailMode {
    ALL_SONGS, ALL_ARTISTS, TOP_SONGS, TOP_ARTISTS,
    FIRST_SEEN, BY_ARTIST, BY_LANGUAGE, BY_GENRE,
}

fun parseDetailMode(raw: String): DetailMode = DetailMode.valueOf(raw)
```

`data/.../listenstats/model/Snapshot.kt`：

```kotlin
package com.hank.musicfree.data.repository.listenstats.model

import com.hank.musicfree.data.db.dao.TopArtistRow
import com.hank.musicfree.data.db.dao.TopSongRow

data class DailyBucket(val dayEpochDay: Long, val seconds: Long)
data class HourBucket(val hourOfDay: Int, val seconds: Long)
data class DateBucket(val dayEpochDay: Long, val seconds: Long)

data class DistributionBucket<T>(val key: T, val count: Int, val label: String)
data class Distribution<T>(val buckets: List<DistributionBucket<T>>, val coverage: Float)

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
    val heatmap: List<DateBucket>,
)

fun emptySnapshot(): ListenStatsSnapshot = ListenStatsSnapshot(
    totalSeconds = 0, distinctSongs = 0, distinctArtists = 0,
    dailyBuckets = emptyList(), topSongs = emptyList(), topArtists = emptyList(),
    hourBuckets = emptyList(),
    languageDistribution = Distribution(emptyList(), 0f),
    genreDistribution = Distribution(emptyList(), 0f),
    streakDays = 0, maxStreak = 0, firstSeenCount = 0, heatmap = emptyList(),
)
```

`data/.../listenstats/model/ListenedSong.kt`：

```kotlin
package com.hank.musicfree.data.repository.listenstats.model

data class ListenedSong(
    val musicId: String,
    val platform: String,
    val title: String,
    val artistRaw: String,
    val album: String?,
    val artwork: String?,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val playCount: Int,
    val totalSec: Long,
)

data class DetailFilter(val mode: DetailMode, val filterValue: String? = null)
```

`data/.../listenstats/model/TimeWindow.kt`：

```kotlin
package com.hank.musicfree.data.repository.listenstats.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class TimeWindow(val startMs: Long, val endMs: Long, val label: String)

fun windowFor(
    scope: TimeScope,
    anchor: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
    firstEventDate: LocalDate? = null,
): TimeWindow {
    val (startDate, endDateExclusive, label) = when (scope) {
        TimeScope.DAY -> Triple(anchor, anchor.plusDays(1), "${anchor.year} 年 ${anchor.monthValue} 月 ${anchor.dayOfMonth} 日")
        TimeScope.WEEK -> {
            val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val sundayNext = monday.plusDays(7)
            Triple(monday, sundayNext, "${monday.monthValue}/${monday.dayOfMonth} – ${monday.plusDays(6).monthValue}/${monday.plusDays(6).dayOfMonth}")
        }
        TimeScope.MONTH -> {
            val first = anchor.withDayOfMonth(1)
            Triple(first, first.plusMonths(1), "${first.year} 年 ${first.monthValue} 月")
        }
        TimeScope.YEAR -> {
            val first = anchor.withDayOfYear(1)
            Triple(first, first.plusYears(1), "${first.year} 年")
        }
        TimeScope.ALL_TIME -> {
            val first = firstEventDate ?: anchor.minusYears(10)
            Triple(first, anchor.plusDays(1), "总计")
        }
    }
    return TimeWindow(
        startMs = startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
        endMs = endDateExclusive.atStartOfDay(zone).toInstant().toEpochMilli(),
        label = label,
    )
}
```

- [ ] **Step 2: build**

Run: `./gradlew :data:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add data/src/main/java/com/hank/musicfree/data/repository/listenstats/model/
git commit -m "feat(data): listen-stats 数据 model (TimeScope/DetailMode/Snapshot/TimeWindow)"
```

---

## Task 9: ListenStatsRepository

**Files:**
- Create: `data/src/main/java/com/hank/musicfree/data/repository/listenstats/ListenStatsRepository.kt`

- [ ] **Step 1: 写 Repository**

```kotlin
package com.hank.musicfree.data.repository.listenstats

import com.hank.musicfree.data.db.dao.ListenStatsDao
import com.hank.musicfree.data.repository.listenstats.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
class ListenStatsRepository @Inject constructor(
    private val dao: ListenStatsDao,
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
) {

    fun firstEventDate(): Flow<LocalDate?> =
        dao.firstEventTimestamp().map { ms ->
            ms?.let { Instant.ofEpochMilli(it).atZone(zoneIdProvider()).toLocalDate() }
        }

    fun statsForWindow(scope: TimeScope, anchor: LocalDate): Flow<ListenStatsSnapshot> {
        return firstEventDate().flatMapLatest { firstDate ->
            val window = windowFor(scope, anchor, zoneIdProvider(), firstDate)
            combine(
                dao.totalSecondsFlow(window.startMs, window.endMs),
                dao.distinctSongsFlow(window.startMs, window.endMs),
                dao.distinctArtistsFlow(window.startMs, window.endMs),
                dao.topSongsFlow(window.startMs, window.endMs, limit = 50),
                dao.topArtistsFlow(window.startMs, window.endMs, limit = 50),
                dao.dailyBucketsFlow(window.startMs, window.endMs),
                dao.hourBucketsFlow(window.startMs, window.endMs),
                dao.languageDistributionFlow(window.startMs, window.endMs),
                dao.genreDistributionFlow(window.startMs, window.endMs),
                dao.heatmapFlow(window.startMs, window.endMs),
                dao.firstSeenInWindowFlow(window.startMs, window.endMs),
            ) { fields ->
                @Suppress("UNCHECKED_CAST")
                val total = fields[0] as Long
                val songs = fields[1] as Int
                val artists = fields[2] as Int
                val tops = fields[3] as List<com.hank.musicfree.data.db.dao.TopSongRow>
                val topArtists = fields[4] as List<com.hank.musicfree.data.db.dao.TopArtistRow>
                val dailyRows = fields[5] as List<com.hank.musicfree.data.db.dao.DailyBucketRow>
                val hourRows = fields[6] as List<com.hank.musicfree.data.db.dao.HourBucketRow>
                val langRows = fields[7] as List<com.hank.musicfree.data.db.dao.LanguageBucketRow>
                val genreRows = fields[8] as List<com.hank.musicfree.data.db.dao.GenreBucketRow>
                val heatmapRows = fields[9] as List<com.hank.musicfree.data.db.dao.DateBucketRow>
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

    fun detail(filter: DetailFilter, scope: TimeScope, anchor: LocalDate): Flow<List<ListenedSong>> {
        val window = windowFor(scope, anchor, zoneIdProvider())
        val rowFlow = when (filter.mode) {
            DetailMode.ALL_SONGS, DetailMode.TOP_SONGS, DetailMode.ALL_ARTISTS, DetailMode.TOP_ARTISTS ->
                dao.allSongsInWindowFlow(window.startMs, window.endMs)
            DetailMode.FIRST_SEEN ->
                dao.firstSeenInWindowFlow(window.startMs, window.endMs)
            DetailMode.BY_ARTIST ->
                dao.songsByArtistFlow(window.startMs, window.endMs, filter.filterValue.orEmpty())
            DetailMode.BY_LANGUAGE ->
                dao.songsByLanguageFlow(window.startMs, window.endMs, filter.filterValue.orEmpty())
            DetailMode.BY_GENRE ->
                dao.songsByGenreFlow(window.startMs, window.endMs, filter.filterValue.orEmpty())
        }
        return rowFlow.map { rows ->
            rows.map {
                ListenedSong(
                    musicId = it.musicId, platform = it.platform,
                    title = it.title, artistRaw = it.artistRaw,
                    album = it.album, artwork = it.artwork,
                    firstSeenMs = it.firstSeenMs, lastSeenMs = it.lastSeenMs,
                    playCount = it.playCount, totalSec = it.totalSec,
                )
            }
        }
    }

    suspend fun clearAll(): Int = dao.clearAllEvents()

    private data class StreakResult(val current: Int, val max: Int)

    private fun computeStreaks(daysWithListen: List<Long>, todayEpochDay: Long): StreakResult {
        if (daysWithListen.isEmpty()) return StreakResult(0, 0)
        val set = daysWithListen.toSortedSet()
        var max = 0; var cur = 0; var prev: Long? = null
        for (d in set) {
            cur = if (prev != null && d == prev + 1) cur + 1 else 1
            if (cur > max) max = cur
            prev = d
        }
        var current = 0; var probe = todayEpochDay
        while (probe in set) { current++; probe-- }
        return StreakResult(current, max)
    }

    private fun languageLabel(code: String): String = when (code) {
        "zh-CN" -> "国语"
        "en" -> "英语"
        "yue" -> "粤语"
        "ja" -> "日语"
        "ko" -> "韩语"
        else -> code
    }

    private fun genreLabel(code: String): String = when (code) {
        "pop" -> "流行"
        "hip-hop" -> "嘻哈 / Hip-Hop"
        "rnb" -> "R&B"
        "rock" -> "摇滚"
        "folk" -> "民谣"
        else -> code
    }
}
```

- [ ] **Step 2: build**

Run: `./gradlew :data:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add data/src/main/java/com/hank/musicfree/data/repository/listenstats/ListenStatsRepository.kt
git commit -m "feat(data): ListenStatsRepository 聚合 + streak + label + 8 种明细 mode"
```

---

## Task 10: ListenStatsRepository 单测

**Files:**
- Create: `data/src/test/java/com/hank/musicfree/data/repository/listenstats/ListenStatsRepositoryTest.kt`

- [ ] **Step 1: 写 Repository 单测**

```kotlin
package com.hank.musicfree.data.repository.listenstats

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.entity.ListenEventArtistEntity
import com.hank.musicfree.data.db.entity.ListenEventEntity
import com.hank.musicfree.data.repository.listenstats.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ListenStatsRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ListenStatsRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = ListenStatsRepository(db.listenStatsDao(), zoneIdProvider = { ZoneOffset.UTC })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed(date: LocalDate, musicId: String, secs: Int = 60, lang: String? = "zh-CN", genre: String? = "pop") {
        val ms = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 10_000
        db.listenStatsDao().insertEventWithArtists(
            ListenEventEntity(
                playedAtMs = ms, musicId = musicId, platform = "p", title = "T",
                artistRaw = "A", album = null, artwork = null, durationMs = 240_000,
                playedSeconds = secs, completed = true, language = lang, genre = genre,
            ),
            listOf(ListenEventArtistEntity(eventId = 0, artistName = "A", artistOrder = 0)),
        )
    }

    @Test
    fun snapshot_week_aggregatesNaturalMondayThroughSunday() = runTest {
        // 假设锚定 2026-05-13 (Wed)，周一 = 2026-05-11
        seed(LocalDate.of(2026, 5, 11), "m1")
        seed(LocalDate.of(2026, 5, 14), "m2")
        seed(LocalDate.of(2026, 5, 18), "out") // 下一周

        val snap = repo.statsForWindow(TimeScope.WEEK, LocalDate.of(2026, 5, 13)).first()
        assertEquals(120, snap.totalSeconds)
        assertEquals(2, snap.distinctSongs)
    }

    @Test
    fun language_distribution_coverage_excludesNullsFromKnownButCountsTotal() = runTest {
        seed(LocalDate.of(2026, 5, 13), "m1", lang = "zh-CN")
        seed(LocalDate.of(2026, 5, 13), "m2", lang = null)
        seed(LocalDate.of(2026, 5, 13), "m3", lang = null)

        val snap = repo.statsForWindow(TimeScope.DAY, LocalDate.of(2026, 5, 13)).first()
        assertEquals(1f / 3f, snap.languageDistribution.coverage)
    }

    @Test
    fun streak_currentAndMax() = runTest {
        // anchor=2026-05-15；连续 5/13,5/14,5/15；早期一段 4/01..4/03
        listOf(LocalDate.of(2026, 5, 13), LocalDate.of(2026, 5, 14), LocalDate.of(2026, 5, 15)).forEach { seed(it, it.toString()) }
        listOf(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3)).forEach { seed(it, it.toString()) }

        val snap = repo.statsForWindow(TimeScope.ALL_TIME, LocalDate.of(2026, 5, 15)).first()
        assertEquals(3, snap.streakDays)
        assertEquals(3, snap.maxStreak)
    }

    @Test
    fun detail_byArtist_filtersListByArtistName() = runTest {
        db.listenStatsDao().insertEventWithArtists(
            ListenEventEntity(playedAtMs = 1, musicId = "m1", platform = "p", title = "T",
                artistRaw = "X & Y", album = null, artwork = null,
                durationMs = 100_000, playedSeconds = 60, completed = false,
                language = null, genre = null),
            listOf(
                ListenEventArtistEntity(eventId = 0, artistName = "X", artistOrder = 0),
                ListenEventArtistEntity(eventId = 0, artistName = "Y", artistOrder = 1),
            ),
        )
        val flow = repo.detail(
            DetailFilter(DetailMode.BY_ARTIST, "X"),
            TimeScope.ALL_TIME,
            LocalDate.of(2026, 5, 15),
        ).first()
        assertEquals(listOf("m1"), flow.map { it.musicId })

        val emptyResult = repo.detail(
            DetailFilter(DetailMode.BY_ARTIST, "Unknown"),
            TimeScope.ALL_TIME,
            LocalDate.of(2026, 5, 15),
        ).first()
        assertTrue(emptyResult.isEmpty())
    }

    @Test
    fun clearAll_emptiesEverything() = runTest {
        seed(LocalDate.of(2026, 5, 14), "m")
        assertEquals(1, repo.clearAll())
        val snap = repo.statsForWindow(TimeScope.WEEK, LocalDate.of(2026, 5, 14)).first()
        assertEquals(0, snap.totalSeconds)
        assertEquals(0, snap.distinctSongs)
    }
}
```

- [ ] **Step 2: 跑测试**

Run: `./gradlew :data:testDebugUnitTest --tests "*ListenStatsRepositoryTest*"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add data/src/test/java/com/hank/musicfree/data/repository/listenstats/ListenStatsRepositoryTest.kt
git commit -m "test(data): ListenStatsRepository 自然周 / coverage / streak / by-artist / clear 行为"
```

---

## Task 11: ArtistSplitter

**Files:**
- Create: `player/src/main/java/com/hank/musicfree/player/listening/ArtistSplitter.kt`
- Create: `player/src/test/java/com/hank/musicfree/player/listening/ArtistSplitterTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.hank.musicfree.player.listening

import org.junit.Test
import kotlin.test.assertEquals

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
}
```

- [ ] **Step 2: 跑测试，确认 FAIL（未编译）**

Run: `./gradlew :player:testDebugUnitTest --tests "*ArtistSplitterTest*"`
Expected: COMPILATION FAILED

- [ ] **Step 3: 实现 ArtistSplitter**

```kotlin
package com.hank.musicfree.player.listening

private val SPLIT_REGEX = Regex(
    """\s*(?:[/&、,]|\sfeat\.?\s|\sft\.?\s|\swith\s)\s*""",
    RegexOption.IGNORE_CASE,
)

fun splitArtists(raw: String): List<String> =
    raw.split(SPLIT_REGEX).map { it.trim() }.filter { it.isNotBlank() }.distinct()
```

- [ ] **Step 4: 跑测试，确认 PASS**

Run: `./gradlew :player:testDebugUnitTest --tests "*ArtistSplitterTest*"`
Expected: 全 PASS

- [ ] **Step 5: Commit**

```bash
git add player/src/main/java/com/hank/musicfree/player/listening/ArtistSplitter.kt \
        player/src/test/java/com/hank/musicfree/player/listening/ArtistSplitterTest.kt
git commit -m "feat(player): ArtistSplitter 多歌手拆分（&/、/feat/ft/with）"
```

---

## Task 12: ListenDimExtractor（风格 / 语言归一化）

**Files:**
- Create: `player/src/main/java/com/hank/musicfree/player/listening/ListenDimExtractor.kt`
- Create: `player/src/test/java/com/hank/musicfree/player/listening/ListenDimExtractorTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.hank.musicfree.player.listening

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListenDimExtractorTest {
    @Test fun null_or_blank_rawJson_returnsBothNull() {
        assertEquals(null to null, ListenDimExtractor.extract(null))
        assertEquals(null to null, ListenDimExtractor.extract(""))
        assertEquals(null to null, ListenDimExtractor.extract("   "))
    }
    @Test fun standard_genre_field_normalized() {
        val (lang, genre) = ListenDimExtractor.extract("""{"genre":"流行"}""")
        assertEquals("pop", genre)
        assertNull(lang)
    }
    @Test fun language_synonyms_mapped() {
        assertEquals("zh-CN", ListenDimExtractor.extract("""{"language":"国语"}""").first)
        assertEquals("zh-CN", ListenDimExtractor.extract("""{"lang":"Mandarin"}""").first)
        assertEquals("yue", ListenDimExtractor.extract("""{"language":"粤语"}""").first)
        assertEquals("en", ListenDimExtractor.extract("""{"language":"English"}""").first)
    }
    @Test fun tags_array_genre_extraction() {
        val (_, genre) = ListenDimExtractor.extract("""{"tags":["华语流行","R&B"]}""")
        assertEquals("pop", genre)  // tags 数组里第一个命中即取
    }
    @Test fun unknown_words_returnNull() {
        assertEquals(null to null, ListenDimExtractor.extract("""{"genre":"赛博朋克"}"""))
    }
    @Test fun malformed_json_returnsBothNull() {
        assertEquals(null to null, ListenDimExtractor.extract("""{not json"""))
    }
}
```

- [ ] **Step 2: 跑测试，确认 FAIL（未编译）**

Run: `./gradlew :player:testDebugUnitTest --tests "*ListenDimExtractorTest*"`
Expected: COMPILATION FAILED

- [ ] **Step 3: 实现 ListenDimExtractor**

```kotlin
package com.hank.musicfree.player.listening

import org.json.JSONArray
import org.json.JSONObject

object ListenDimExtractor {

    private val LANG_MAP: Map<String, String> = mapOf(
        "国语" to "zh-CN", "华语" to "zh-CN", "中文" to "zh-CN",
        "mandarin" to "zh-CN", "zh" to "zh-CN", "zh-cn" to "zh-CN",
        "粤语" to "yue", "广东话" to "yue", "cantonese" to "yue",
        "英语" to "en", "english" to "en", "en" to "en",
        "日语" to "ja", "japanese" to "ja", "ja" to "ja",
        "韩语" to "ko", "korean" to "ko", "ko" to "ko",
    )

    private val GENRE_MAP: Map<String, String> = mapOf(
        "流行" to "pop", "华语流行" to "pop", "c-pop" to "pop", "pop" to "pop",
        "嘻哈" to "hip-hop", "rap" to "hip-hop", "hip hop" to "hip-hop", "hip-hop" to "hip-hop",
        "r&b" to "rnb", "节奏布鲁斯" to "rnb", "rnb" to "rnb",
        "摇滚" to "rock", "rock" to "rock", "金属" to "rock", "metal" to "rock",
        "民谣" to "folk", "folk" to "folk", "乡村" to "folk", "country" to "folk",
    )

    fun extract(rawJson: String?): Pair<String?, String?> {
        if (rawJson.isNullOrBlank()) return null to null
        val obj = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null to null

        val lang = pickStringField(obj, "language", "lang")?.normLookup(LANG_MAP)

        val genreFromField = pickStringField(obj, "genre", "style", "category")?.normLookup(GENRE_MAP)
        val genreFromTags = if (genreFromField == null) pickFromTags(obj)?.normLookup(GENRE_MAP) else null

        return lang to (genreFromField ?: genreFromTags)
    }

    private fun pickStringField(obj: JSONObject, vararg keys: String): String? {
        for (k in keys) {
            val v = obj.optString(k, "").trim()
            if (v.isNotEmpty()) return v
        }
        return null
    }

    private fun pickFromTags(obj: JSONObject): String? {
        for (key in listOf("tags", "tag")) {
            val arr: JSONArray = obj.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                val v = arr.optString(i, "").trim()
                if (v.isNotEmpty() && GENRE_MAP.containsKey(v.lowercase())) return v
            }
        }
        return null
    }

    private fun String.normLookup(map: Map<String, String>): String? =
        map[this.lowercase().trim()]
}
```

- [ ] **Step 4: 跑测试，确认 PASS**

Run: `./gradlew :player:testDebugUnitTest --tests "*ListenDimExtractorTest*"`
Expected: 全 PASS

- [ ] **Step 5: Commit**

```bash
git add player/src/main/java/com/hank/musicfree/player/listening/ListenDimExtractor.kt \
        player/src/test/java/com/hank/musicfree/player/listening/ListenDimExtractorTest.kt
git commit -m "feat(player): ListenDimExtractor 风格/语言 rawJson best-effort 提取"
```

---

## Task 13: ListenTracker（state machine + 写库）

**Files:**
- Create: `player/src/main/java/com/hank/musicfree/player/listening/ListenTracker.kt`
- Create: `player/src/test/java/com/hank/musicfree/player/listening/ListenTrackerTest.kt`

- [ ] **Step 1: 写失败测试（state machine 主要 case）**

```kotlin
package com.hank.musicfree.player.listening

import androidx.media3.common.Player
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.data.db.dao.ListenStatsDao
import com.hank.musicfree.data.db.entity.ListenEventArtistEntity
import com.hank.musicfree.data.db.entity.ListenEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.Test
import org.mockito.kotlin.*
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ListenTrackerTest {

    private val dao: ListenStatsDao = mock()
    private val clockMs = mutableListOf(0L)
    private fun setNow(v: Long) { clockMs[0] = v }

    private fun newTracker(scope: TestScope): ListenTracker = ListenTracker(
        dao = dao,
        nowMs = { clockMs[0] },
        scope = scope,
    )

    private val item = MusicItem(
        id = "m1", platform = "netease", title = "Song",
        artist = "周杰伦 & 林俊杰", album = "Album", artwork = null,
        duration = 240_000L, url = null, qualitiesJson = null, rawJson = """{"language":"国语","genre":"流行"}""",
    )

    @Test fun playing_for_60s_then_transition_writesOneEvent() = runTest {
        val tracker = newTracker(this)
        setNow(0); tracker.onMediaItemTransition(item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        setNow(0); tracker.onIsPlayingChanged(true, item)
        setNow(60_000); tracker.onIsPlayingChanged(false, item)
        setNow(60_000); tracker.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        advanceUntilIdle()

        argumentCaptor<ListenEventEntity>().apply {
            verify(dao).insertEventWithArtists(capture(), any())
            assertEquals(60, firstValue.playedSeconds)
            assertEquals("zh-CN", firstValue.language)
            assertEquals("pop", firstValue.genre)
        }
        argumentCaptor<List<ListenEventArtistEntity>>().apply {
            verify(dao).insertEventWithArtists(any(), capture())
            assertEquals(listOf("周杰伦", "林俊杰"), firstValue.map { it.artistName })
        }
    }

    @Test fun played_below_threshold_doesNotWrite() = runTest {
        val tracker = newTracker(this)
        setNow(0); tracker.onMediaItemTransition(item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        setNow(0); tracker.onIsPlayingChanged(true, item)
        setNow(15_000); tracker.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        advanceUntilIdle()
        verify(dao, never()).insertEventWithArtists(any(), any())
    }

    @Test fun seek_doesNotAccumulateExtraTime() = runTest {
        val tracker = newTracker(this)
        setNow(0); tracker.onMediaItemTransition(item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        setNow(0); tracker.onIsPlayingChanged(true, item)
        setNow(30_000); tracker.onPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK)
        // seek 后从 30_000 重起 playing 段
        setNow(60_000); tracker.onTrackEnded(item)
        advanceUntilIdle()

        argumentCaptor<ListenEventEntity>().apply {
            verify(dao).insertEventWithArtists(capture(), any())
            assertEquals(60, firstValue.playedSeconds)  // 30 + 30，不是 30 + 60
            assertEquals(true, firstValue.completed)
        }
    }

    @Test fun trackEnded_marksCompletedTrue() = runTest {
        val tracker = newTracker(this)
        setNow(0); tracker.onMediaItemTransition(item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        setNow(0); tracker.onIsPlayingChanged(true, item)
        setNow(60_000); tracker.onTrackEnded(item)
        advanceUntilIdle()
        argumentCaptor<ListenEventEntity>().apply {
            verify(dao).insertEventWithArtists(capture(), any())
            assertEquals(true, firstValue.completed)
        }
    }
}
```

- [ ] **Step 2: 跑测试，确认 FAIL（未编译）**

Run: `./gradlew :player:testDebugUnitTest --tests "*ListenTrackerTest*"`
Expected: COMPILATION FAILED — ListenTracker 未定义

- [ ] **Step 3: 实现 ListenTracker**

```kotlin
package com.hank.musicfree.player.listening

import androidx.media3.common.Player
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.data.db.dao.ListenStatsDao
import com.hank.musicfree.data.db.entity.ListenEventArtistEntity
import com.hank.musicfree.data.db.entity.ListenEventEntity
import com.hank.musicfree.logging.MfLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class ListenTracker @Inject constructor(
    private val dao: ListenStatsDao,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    companion object {
        const val THRESHOLD_MS: Long = 30_000L
        const val COMPLETED_TAIL_TOLERANCE_MS: Long = 5_000L
    }

    private data class Session(
        val item: MusicItem,
        var resumeWall: Long?,           // 当前 playing 段起始（null 表示当前不在播）
        var accumulatedMs: Long,
        var lastEventWall: Long,         // 最近一次任何事件的时间，用于 endedAt 锚定
        var endedNaturally: Boolean = false,
    )

    private var current: Session? = null

    fun onMediaItemTransition(newItem: MusicItem?, @Suppress("UNUSED_PARAMETER") reason: Int) {
        flushIfQualifies(reason = "transition")
        current = newItem?.let { Session(it, resumeWall = null, accumulatedMs = 0, lastEventWall = nowMs()) }
    }

    fun onIsPlayingChanged(isPlaying: Boolean, fallbackItem: MusicItem?) {
        val s = current ?: fallbackItem?.let {
            Session(it, resumeWall = null, accumulatedMs = 0, lastEventWall = nowMs()).also { current = it }
        } ?: return
        val now = nowMs()
        if (isPlaying) {
            if (s.resumeWall == null) s.resumeWall = now
        } else {
            s.resumeWall?.let { s.accumulatedMs += (now - it) }
            s.resumeWall = null
        }
        s.lastEventWall = now
    }

    fun onPositionDiscontinuity(reason: Int) {
        if (reason != Player.DISCONTINUITY_REASON_SEEK) return
        val s = current ?: return
        val now = nowMs()
        s.resumeWall?.let { s.accumulatedMs += (now - it) }
        s.resumeWall = if (s.resumeWall != null) now else null  // 仍处于 playing 则从 now 重起
        s.lastEventWall = now
    }

    fun onTrackEnded(item: MusicItem?) {
        val s = current ?: return
        val now = nowMs()
        s.resumeWall?.let { s.accumulatedMs += (now - it); s.resumeWall = null }
        s.endedNaturally = true
        s.lastEventWall = now
        flushIfQualifies(reason = "ended")
        current = null
    }

    /** 用户在播放中触发"清除"时由调用方先调它，flush 当前 session 然后让 db 清理。 */
    fun flushCurrentSession() {
        flushIfQualifies(reason = "external_flush")
    }

    private fun flushIfQualifies(reason: String) {
        val s = current ?: return
        // 若仍在 playing，收尾 chunk
        val now = nowMs()
        s.resumeWall?.let { s.accumulatedMs += (now - it); s.resumeWall = null }
        s.lastEventWall = now

        if (s.accumulatedMs < THRESHOLD_MS) {
            MfLog.info("listen_event_skipped_below_threshold", mapOf(
                "accumulatedMs" to s.accumulatedMs, "durationMs" to s.item.duration, "reason" to reason,
            ))
            return
        }

        val (lang, genre) = ListenDimExtractor.extract(s.item.rawJson)
        val artists = splitArtists(s.item.artist)
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
        )
        val artistRows = artists.mapIndexed { i, name ->
            ListenEventArtistEntity(eventId = 0, artistName = name, artistOrder = i)
        }
        scope.launch {
            runCatching { dao.insertEventWithArtists(event, artistRows) }
                .onSuccess {
                    MfLog.info("listen_event_inserted", mapOf(
                        "musicId" to event.musicId, "platform" to event.platform,
                        "playedSeconds" to event.playedSeconds, "completed" to event.completed,
                        "durationMs" to event.durationMs,
                    ))
                }
                .onFailure { MfLog.error("listen_event_insert_failed", it) }
        }
    }
}
```

> 若项目 `:logging` 模块的 API 与 `MfLog.info(name, fields)` 签名不同，按实际签名调整。

- [ ] **Step 4: build 与跑测试**

Run: `./gradlew :player:testDebugUnitTest --tests "*ListenTrackerTest*"`
Expected: 4 个 case PASS

- [ ] **Step 5: Commit**

```bash
git add player/src/main/java/com/hank/musicfree/player/listening/ListenTracker.kt \
        player/src/test/java/com/hank/musicfree/player/listening/ListenTrackerTest.kt
git commit -m "feat(player): ListenTracker state machine + 写入 listen_event + 结构化日志"
```

---

## Task 14: 接入 PlayerController

**Files:**
- Modify: `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`
- Modify: `player/build.gradle.kts` 确保依赖 `:data`（PlayerController 直接构造 ListenTracker 注入时需要 dao；若 :player 已依赖 :data 可跳过）

- [ ] **Step 1: 确认 :player 对 :data 依赖**

```bash
grep -n ":data" player/build.gradle.kts
```
若无，在 `dependencies { ... }` 中加 `implementation(project(":data"))`。

- [ ] **Step 2: PlayerController 注入 ListenTracker**

打开 `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`：

1. 顶部 import 加：
   ```kotlin
   import com.hank.musicfree.player.listening.ListenTracker
   ```
2. 构造函数加参数（找现有 `@Inject constructor(...)`）：
   ```kotlin
   private val listenTracker: ListenTracker,
   ```
3. 找 `private val playerListener = object : Player.Listener {` 块，按下面 patch 加 4 处委托：

```kotlin
private val playerListener = object : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            listenTracker.onTrackEnded(currentMusicItem())          // 新增
            handleTrackEnded()
        }
        emitState()
        updatePositionTracking()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        listenTracker.onIsPlayingChanged(isPlaying, currentMusicItem())   // 新增
        emitState()
        updatePositionTracking()
    }

    override fun onMediaItemTransition(
        mediaItem: androidx.media3.common.MediaItem?,
        reason: Int,
    ) {
        listenTracker.onMediaItemTransition(currentMusicItem(), reason)   // 新增
        emitState()
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        listenTracker.onPositionDiscontinuity(reason)                      // 新增
    }

    override fun onPlayerError(error: PlaybackException) {
        handlePlaybackError(error)
    }
}
```

> `currentMusicItem(): MusicItem?` — 在 PlayerController 内找现有"当前曲目"的访问点（通常是 `recordHistory(item)` 调用时拿到的 `item`，或者 `_playerState.value.currentItem`），抽成 private helper 返回 `MusicItem?`。如果该 helper 已存在不同名，替换调用。

- [ ] **Step 3: 暴露 flushCurrentSession 接口给清除按钮**

PlayerController 暴露 public：

```kotlin
fun flushListenTrackerForClear() {
    listenTracker.flushCurrentSession()
}
```

Repository 清除前会调它（Task 17 ViewModel 处理）。

- [ ] **Step 4: build**

Run: `./gradlew :player:assembleDebug :data:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 跑 :player 现有单测，确认未破坏**

Run: `./gradlew :player:testDebugUnitTest`
Expected: 全 PASS（既有用例不受影响；如果有 mock PlayerController 的下游模块测试因构造签名变了挂掉，统一在 ViewModel/Repo mock 处补一个 `listenTracker = mock()` 参数）

- [ ] **Step 6: Commit**

```bash
git add player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt player/build.gradle.kts
git commit -m "feat(player): PlayerController 委托 ListenTracker (transition/isPlaying/seek/ended)"
```

---

## Task 15: 抽屉「我的」section + icon

**Files:**
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/HomeDrawerNavigation.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/component/HomeIcons.kt`
- Create: `feature/home/src/main/res/drawable/ic_home_chart_bar_outline.xml`
- Modify: `feature/home/src/test/java/com/hank/musicfree/feature/home/HomeDrawerUiModelTest.kt`
- Modify: `feature/home/src/test/java/com/hank/musicfree/feature/home/HomeAnchorContractTest.kt`

- [ ] **Step 1: 写失败的 drawer ui model 断言**

`HomeDrawerUiModelTest.kt` 加一条：

```kotlin
@Test
fun `me section is first and contains listen stats item`() {
    val model = buildHomeDrawerUiModel(currentVersion = "1.0", scheduleCloseSummary = "")
    val first = model.sections.first()
    assertEquals("me", first.sectionKey)
    assertEquals("我的", first.title)
    val item = first.items.single()
    assertEquals("听歌足迹", item.title)
    assertEquals(HomeDrawerAction.OpenListenStats, item.action)
    assertEquals(FidelityAnchors.Home.DrawerMeListenStats, item.anchorTag)
}
```

`HomeAnchorContractTest.kt` 加一条：

```kotlin
@Test
fun `DrawerMeListenStats anchor exists in drawer ui model`() {
    val model = buildHomeDrawerUiModel("1.0", "")
    val anchors = model.sections.flatMap { it.items }.map { it.anchorTag }
    assertTrue(FidelityAnchors.Home.DrawerMeListenStats in anchors)
}
```

- [ ] **Step 2: 跑测试，确认 FAIL**

Run: `./gradlew :feature:home:testDebugUnitTest --tests "*HomeDrawerUiModelTest*" --tests "*HomeAnchorContractTest*"`
Expected: FAIL — `OpenListenStats` 未定义 / `DrawerListenStats` 未定义

- [ ] **Step 3: 写 drawable**

`feature/home/src/main/res/drawable/ic_home_chart_bar_outline.xml`：

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path
        android:strokeColor="#000000" android:strokeWidth="1.5"
        android:strokeLineCap="round" android:strokeLineJoin="round"
        android:fillColor="@android:color/transparent"
        android:pathData="M3,3 L3,21 L21,21 M7,17 L7,11 M11,17 L11,7 M15,17 L15,14 M19,17 L19,9" />
</vector>
```

- [ ] **Step 4: HomeIcons 加引用**

`HomeIcons.kt` 加一行（仿现有 `DrawerXxxx` 命名）：

```kotlin
@DrawableRes val DrawerListenStats = R.drawable.ic_home_chart_bar_outline
```

- [ ] **Step 5: HomeDrawerNavigation 加 action + section**

`HomeDrawerNavigation.kt`：

1. 在 `sealed interface HomeDrawerAction { ... }` 内追加：
   ```kotlin
   data object OpenListenStats : HomeDrawerAction
   ```

2. 在 `buildHomeDrawerUiModel(...)` 的 `sections = listOf(...)` 列表 **最前** 插入：
   ```kotlin
   HomeDrawerSectionUiModel(
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
   ```

- [ ] **Step 6: 跑测试，确认 PASS**

Run: `./gradlew :feature:home:testDebugUnitTest --tests "*HomeDrawer*" --tests "*HomeAnchorContract*"`
Expected: 全 PASS

- [ ] **Step 7: Commit**

```bash
git add feature/home/src/main/java/com/hank/musicfree/feature/home/HomeDrawerNavigation.kt \
        feature/home/src/main/java/com/hank/musicfree/feature/home/component/HomeIcons.kt \
        feature/home/src/main/res/drawable/ic_home_chart_bar_outline.xml \
        feature/home/src/test/java/com/hank/musicfree/feature/home/HomeDrawerUiModelTest.kt \
        feature/home/src/test/java/com/hank/musicfree/feature/home/HomeAnchorContractTest.kt
git commit -m "feat(home): 抽屉顶部新增「我的 → 听歌足迹」入口"
```

---

## Task 16: HomeScreenContent dispatch OpenListenStats → 路由

**Files:**
- Modify: `feature/home/.../HomeScreen.kt` 或 `HomeScreenContent.kt`（具体在哪一个由实现者通过 grep 定位 `OpenSettingsRoot` 的 dispatch 点判断）
- Modify: `app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt`

- [ ] **Step 1: 找现有 OpenSettingsRoot 的 dispatch 点**

```bash
grep -rn "OpenSettingsRoot\|HomeDrawerAction\." feature/home/src/main app/src/main
```

定位现有 action → `navController.navigate(...)` 的 mapping 入口（多半在 `HomeScreen.kt` 或父级 NavHost）。

- [ ] **Step 2: 加 OpenListenStats 分支**

在 Step 1 定位的 when/sealed dispatch 处加：

```kotlin
HomeDrawerAction.OpenListenStats -> onNavigateToListenStats()
```

`HomeScreen` / `HomeScreenContent` 在参数表加：

```kotlin
onNavigateToListenStats: () -> Unit,
```

`AppNavHost.kt` 在挂 `HomeScreen` 的 composable 块内传入：

```kotlin
HomeScreen(
    ...
    onNavigateToListenStats = { navController.navigate(ListenStatsRoute()) },
)
```

import：

```kotlin
import com.hank.musicfree.core.navigation.ListenStatsRoute
```

- [ ] **Step 3: build 验证**

Run: `./gradlew :feature:home:assembleDebug :app:assembleDebug`
Expected: BUILD SUCCESSFUL

> 此时 `ListenStatsRoute` 还未挂 destination，运行会找不到 NavGraph 中的 route — 这是预期；Task 24 才挂 NavGraph。

- [ ] **Step 4: Commit**

```bash
git add feature/home/src/main/java/com/hank/musicfree/feature/home/ \
        app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt
git commit -m "feat(home): HomeScreen 分发 OpenListenStats → ListenStatsRoute"
```

---

## Task 17: ListenStatsViewModel + State

**Files:**
- Create: `feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/ListenStatsScreenState.kt`
- Create: `feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/ListenStatsViewModel.kt`
- Create: `feature/listen-stats/src/test/java/com/hank/musicfree/feature/liststats/MainDispatcherRule.kt`（照搬 `:feature:search`）
- Create: `feature/listen-stats/src/test/java/com/hank/musicfree/feature/liststats/ListenStatsViewModelTest.kt`

- [ ] **Step 1: 写 ScreenState**

```kotlin
package com.hank.musicfree.feature.liststats

import com.hank.musicfree.data.repository.listenstats.model.ListenStatsSnapshot
import com.hank.musicfree.data.repository.listenstats.model.TimeScope
import com.hank.musicfree.data.repository.listenstats.model.emptySnapshot
import java.time.LocalDate

data class ListenStatsScreenState(
    val scope: TimeScope = TimeScope.WEEK,
    val anchor: LocalDate = LocalDate.now(),
    val windowLabel: String = "",
    val scopeLabel: String = "本周累计",
    val firstEventDate: LocalDate? = null,
    val snapshot: ListenStatsSnapshot = emptySnapshot(),
    val showClearDialog: Boolean = false,
    val clearingInProgress: Boolean = false,
)
```

- [ ] **Step 2: 写 ViewModel**

```kotlin
package com.hank.musicfree.feature.liststats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.hank.musicfree.core.navigation.ListenStatsRoute
import com.hank.musicfree.data.repository.listenstats.ListenStatsRepository
import com.hank.musicfree.data.repository.listenstats.model.*
import com.hank.musicfree.player.controller.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class ListenStatsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ListenStatsRepository,
    private val playerController: PlayerController,
) : ViewModel() {

    private val route: ListenStatsRoute = savedStateHandle.toRoute()

    private val scopeFlow = MutableStateFlow(parseTimeScope(route.scope))
    private val anchorFlow = MutableStateFlow(
        if (route.anchorEpochDay >= 0) LocalDate.ofEpochDay(route.anchorEpochDay) else LocalDate.now()
    )
    private val showClearDialog = MutableStateFlow(false)
    private val clearingInProgress = MutableStateFlow(false)

    private val snapshotFlow = combine(scopeFlow, anchorFlow) { scope, anchor -> scope to anchor }
        .flatMapLatest { (scope, anchor) -> repository.statsForWindow(scope, anchor) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySnapshot())

    private val firstEventDateFlow = repository.firstEventDate()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<ListenStatsScreenState> = combine(
        scopeFlow, anchorFlow, snapshotFlow, firstEventDateFlow,
        showClearDialog, clearingInProgress,
    ) { fields ->
        val scope = fields[0] as TimeScope
        val anchor = fields[1] as LocalDate
        val snap = fields[2] as ListenStatsSnapshot
        val firstDate = fields[3] as LocalDate?
        val showDialog = fields[4] as Boolean
        val clearing = fields[5] as Boolean
        val window = windowFor(scope, anchor, firstEventDate = firstDate)
        ListenStatsScreenState(
            scope = scope, anchor = anchor,
            windowLabel = window.label,
            scopeLabel = scopeLabel(scope),
            firstEventDate = firstDate,
            snapshot = snap,
            showClearDialog = showDialog,
            clearingInProgress = clearing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListenStatsScreenState())

    fun onScopeChange(scope: TimeScope) { scopeFlow.value = scope }
    fun onAnchorChange(anchor: LocalDate) { anchorFlow.value = anchor }
    fun onPagerPrev() { anchorFlow.value = shiftAnchor(scopeFlow.value, anchorFlow.value, -1) }
    fun onPagerNext() { anchorFlow.value = shiftAnchor(scopeFlow.value, anchorFlow.value, +1) }

    fun onClearRequested() { showClearDialog.value = true }
    fun onClearDismissed() { showClearDialog.value = false }
    fun onClearConfirmed() {
        showClearDialog.value = false
        clearingInProgress.value = true
        viewModelScope.launch {
            runCatching {
                playerController.flushListenTrackerForClear()
                repository.clearAll()
            }
            clearingInProgress.value = false
        }
    }

    private fun shiftAnchor(scope: TimeScope, anchor: LocalDate, delta: Int): LocalDate = when (scope) {
        TimeScope.DAY -> anchor.plusDays(delta.toLong())
        TimeScope.WEEK -> anchor.plusWeeks(delta.toLong())
        TimeScope.MONTH -> anchor.plusMonths(delta.toLong())
        TimeScope.YEAR -> anchor.plusYears(delta.toLong())
        TimeScope.ALL_TIME -> anchor
    }

    private fun scopeLabel(scope: TimeScope): String = when (scope) {
        TimeScope.DAY -> "今日累计"
        TimeScope.WEEK -> "本周累计"
        TimeScope.MONTH -> "本月累计"
        TimeScope.YEAR -> "本年累计"
        TimeScope.ALL_TIME -> "累计听歌"
    }
}
```

- [ ] **Step 3: 写 MainDispatcherRule**

```kotlin
package com.hank.musicfree.feature.liststats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(d: Description) { Dispatchers.setMain(dispatcher) }
    override fun finished(d: Description) { Dispatchers.resetMain() }
}
```

- [ ] **Step 4: 写 ViewModel 单测**

```kotlin
package com.hank.musicfree.feature.liststats

import androidx.lifecycle.SavedStateHandle
import com.hank.musicfree.core.navigation.ListenStatsRoute
import com.hank.musicfree.data.repository.listenstats.ListenStatsRepository
import com.hank.musicfree.data.repository.listenstats.model.*
import com.hank.musicfree.player.controller.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*
import java.time.LocalDate
import kotlin.test.assertEquals

class ListenStatsViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private fun createSavedStateHandle(scope: String = "WEEK", anchor: Long = -1L) =
        SavedStateHandle().also {
            it["scope"] = scope
            it["anchorEpochDay"] = anchor
        }

    private fun newViewModel(
        scope: String = "WEEK",
        snapshot: ListenStatsSnapshot = emptySnapshot(),
    ): Pair<ListenStatsViewModel, ListenStatsRepository> {
        val repo: ListenStatsRepository = mock {
            on { firstEventDate() } doReturn flowOf(null)
            on { statsForWindow(any(), any()) } doReturn flowOf(snapshot)
        }
        val player: PlayerController = mock()
        val vm = ListenStatsViewModel(
            savedStateHandle = createSavedStateHandle(scope),
            repository = repo,
            playerController = player,
        )
        return vm to repo
    }

    @Test fun initial_scope_from_route_param() = runTest {
        val (vm, _) = newViewModel(scope = "MONTH")
        advanceUntilIdle()
        assertEquals(TimeScope.MONTH, vm.state.value.scope)
    }

    @Test fun onPagerNext_advancesAnchorByOneWeek() = runTest {
        val (vm, _) = newViewModel(scope = "WEEK")
        advanceUntilIdle()
        val before = vm.state.value.anchor
        vm.onPagerNext()
        advanceUntilIdle()
        assertEquals(before.plusWeeks(1), vm.state.value.anchor)
    }

    @Test fun onClearConfirmed_flushesTrackerThenClearsRepo() = runTest {
        val (vm, repo) = newViewModel()
        val player: PlayerController = mock()
        // 重做 — 用同一 player 实例
        val vm2 = ListenStatsViewModel(createSavedStateHandle(), repo, player)
        vm2.onClearRequested()
        vm2.onClearConfirmed()
        advanceUntilIdle()
        verify(player).flushListenTrackerForClear()
        verify(repo).clearAll()
    }
}
```

- [ ] **Step 5: 跑测试**

Run: `./gradlew :feature:listen-stats:testDebugUnitTest --tests "*ListenStatsViewModelTest*"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/ListenStatsScreenState.kt \
        feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/ListenStatsViewModel.kt \
        feature/listen-stats/src/test/java/com/hank/musicfree/feature/liststats/MainDispatcherRule.kt \
        feature/listen-stats/src/test/java/com/hank/musicfree/feature/liststats/ListenStatsViewModelTest.kt
git commit -m "feat(listen-stats): ViewModel — scope/anchor flow + 清除流程 + 翻页"
```

---

## Task 18: 卡片 composables（第一批：Hero / Segmented / Pager / SecondaryKpi / DailyBars / Onboarding）

**Files:**
- Create: `feature/listen-stats/.../component/HeroTotalDurationCard.kt`
- Create: `feature/listen-stats/.../component/TimeScopeSegmented.kt`
- Create: `feature/listen-stats/.../component/TimeScopePager.kt`
- Create: `feature/listen-stats/.../component/SecondaryKpiRow.kt`
- Create: `feature/listen-stats/.../component/DailyBarsCard.kt`
- Create: `feature/listen-stats/.../component/OnboardingHint.kt`
- Create: `feature/listen-stats/src/test/.../component/CardCompositionTest.kt`（一组 smoke 渲染测试）

- [ ] **Step 1: 写 HeroTotalDurationCard**

```kotlin
package com.hank.musicfree.feature.liststats.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HeroTotalDurationCard(totalSeconds: Long, scopeLabel: String, modifier: Modifier = Modifier) {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 24.dp)) {
            Text(text = scopeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                Text(text = "$hours", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                Text(text = " 小时 ", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                Text(text = "$minutes", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                Text(text = " 分钟", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
```

- [ ] **Step 2: 写 TimeScopeSegmented**

```kotlin
package com.hank.musicfree.feature.liststats.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hank.musicfree.data.repository.listenstats.model.TimeScope

@Composable
fun TimeScopeSegmented(current: TimeScope, onChange: (TimeScope) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(TimeScope.DAY to "日", TimeScope.WEEK to "周", TimeScope.MONTH to "月", TimeScope.YEAR to "年", TimeScope.ALL_TIME to "总计")
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(4.dp)) {
            items.forEach { (scope, label) ->
                val selected = scope == current
                Surface(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)),
                    color = if (selected) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
                    onClick = { onChange(scope) },
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: 写 TimeScopePager**

```kotlin
package com.hank.musicfree.feature.liststats.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun TimeScopePager(label: String, onPrev: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一个时段")
        }
        Text(text = label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一个时段")
        }
    }
}
```

- [ ] **Step 4: 写 SecondaryKpiRow**

```kotlin
package com.hank.musicfree.feature.liststats.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SecondaryKpiRow(
    distinctSongs: Int,
    distinctArtists: Int,
    onSongsClick: () -> Unit,
    onArtistsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        KpiCell(value = "$distinctSongs", label = "听过的歌曲", modifier = Modifier.weight(1f), onClick = onSongsClick)
        KpiCell(value = "$distinctArtists", label = "听过的歌手", modifier = Modifier.weight(1f), onClick = onArtistsClick)
    }
}

@Composable
private fun KpiCell(value: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = onClick,
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(value, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
```

- [ ] **Step 5: 写 DailyBarsCard**

```kotlin
package com.hank.musicfree.feature.liststats.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hank.musicfree.data.repository.listenstats.model.DailyBucket

@Composable
fun DailyBarsCard(
    daily: List<DailyBucket>,
    onBarClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxSec = (daily.maxOfOrNull { it.seconds } ?: 1L).coerceAtLeast(1L)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("每日时长", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.height(120.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                daily.forEach { b ->
                    val ratio = (b.seconds.toFloat() / maxSec).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(ratio.coerceAtLeast(0.05f))
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 6: 写 OnboardingHint**

```kotlin
package com.hank.musicfree.feature.liststats.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun OnboardingHint(firstEventDate: LocalDate?, modifier: Modifier = Modifier) {
    if (firstEventDate == null) return
    Text(
        text = "开始统计于 ${firstEventDate.year} 年 ${firstEventDate.monthValue} 月 ${firstEventDate.dayOfMonth} 日",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}
```

- [ ] **Step 7: 写 smoke composition 测试（一个文件覆盖全部第一批 card）**

```kotlin
package com.hank.musicfree.feature.liststats.component

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.hank.musicfree.data.repository.listenstats.model.DailyBucket
import com.hank.musicfree.data.repository.listenstats.model.TimeScope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class CardCompositionTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun hero_renders_hours_and_minutes() {
        composeRule.setContent {
            HeroTotalDurationCard(totalSeconds = 3661, scopeLabel = "本周累计")
        }
        composeRule.onNodeWithText("本周累计").assertExists()
        composeRule.onNodeWithText("1").assertExists()   // hours
        composeRule.onNodeWithText("61", substring = false).assertDoesNotExist()
        composeRule.onNodeWithText(" 小时 ").assertExists()
    }

    @Test fun segmented_active_callback_fires() {
        var lastScope: TimeScope? = null
        composeRule.setContent {
            TimeScopeSegmented(current = TimeScope.WEEK, onChange = { lastScope = it })
        }
        composeRule.onNodeWithText("月").performClick()
        assert(lastScope == TimeScope.MONTH)
    }

    @Test fun secondaryKpi_clicks_dispatch_separately() {
        var songsClicked = false
        var artistsClicked = false
        composeRule.setContent {
            SecondaryKpiRow(
                distinctSongs = 147, distinctArtists = 52,
                onSongsClick = { songsClicked = true },
                onArtistsClick = { artistsClicked = true },
            )
        }
        composeRule.onNodeWithText("听过的歌曲").performClick()
        assert(songsClicked && !artistsClicked)
    }

    @Test fun onboarding_renders_when_firstEventDate_present() {
        composeRule.setContent { OnboardingHint(LocalDate.of(2026, 5, 15)) }
        composeRule.onNodeWithText("开始统计于 2026 年 5 月 15 日").assertExists()
    }

    @Test fun onboarding_hidden_when_null() {
        composeRule.setContent { OnboardingHint(null) }
        composeRule.onAllNodes(hasText("开始统计于", substring = true)).assertCountEquals(0)
    }
}
```

- [ ] **Step 8: 跑 compose 单测**

Run: `./gradlew :feature:listen-stats:testDebugUnitTest --tests "*CardCompositionTest*"`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/component/HeroTotalDurationCard.kt \
        feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/component/TimeScopeSegmented.kt \
        feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/component/TimeScopePager.kt \
        feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/component/SecondaryKpiRow.kt \
        feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/component/DailyBarsCard.kt \
        feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/component/OnboardingHint.kt \
        feature/listen-stats/src/test/java/com/hank/musicfree/feature/liststats/component/CardCompositionTest.kt
git commit -m "feat(listen-stats): 卡片组件第一批（Hero/Segmented/Pager/SecondaryKpi/DailyBars/Onboarding）"
```

---

## Task 19: 卡片 composables（第二批：TopSongs / TopArtists / Language / Genre / Hour / StreakDiscovery / Heatmap）

**Files:**
- Create 7 个 component 文件，路径 `feature/listen-stats/.../component/`
- Modify: `CardCompositionTest.kt` 增加这一批的 smoke case

- [ ] **Step 1: 写 TopSongsCard**

```kotlin
package com.hank.musicfree.feature.liststats.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Top 歌曲", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text("按播放次数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            rows.take(5).forEachIndexed { idx, row -> SongLine(rank = idx + 1, row = row, onClick = { onRowClick(row) }) }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "查看全部 Top 50",
                modifier = Modifier.fillMaxWidth().padding(8.dp).clickable { onSeeAll() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SongLine(rank: Int, row: TopSongRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", modifier = Modifier.width(28.dp), style = MaterialTheme.typography.titleMedium,
            color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.bodyMedium)
            Text("${row.artistRaw} · ${row.album.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Text("${row.playCount} 次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

// 修正：缺失的 import
private fun Modifier.clickable(onClick: () -> Unit): Modifier = composed { androidx.compose.foundation.clickable(onClick = onClick) }
```

> 实际写时使用 `import androidx.compose.foundation.clickable` 并用 `Modifier.clickable { onClick() }` 直接写；上面 helper 是为了让代码块自含。

- [ ] **Step 2: 写 TopArtistsCard**

结构与 TopSongsCard 一致，数据类换成 `TopArtistRow`，副信息显示 `出现在 ${songCount} 首歌中`，标题 `Top 歌手`，导航 `onRowClick(artistName)`。

```kotlin
package com.hank.musicfree.feature.liststats.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hank.musicfree.data.db.dao.TopArtistRow

@Composable
fun TopArtistsCard(
    rows: List<TopArtistRow>,
    onSeeAll: () -> Unit,
    onRowClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Top 歌手", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text("按播放次数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            rows.take(5).forEachIndexed { idx, row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onRowClick(row.artistName) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val rank = idx + 1
                    Text("$rank", modifier = Modifier.width(28.dp), style = MaterialTheme.typography.titleMedium,
                        color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    Column(Modifier.weight(1f)) {
                        Text(row.artistName, style = MaterialTheme.typography.bodyMedium)
                        Text("出现在 ${row.songCount} 首歌中", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Text("${row.playCount} 次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            Text(
                text = "查看全部 Top 50",
                modifier = Modifier.fillMaxWidth().padding(8.dp).clickable { onSeeAll() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
```

- [ ] **Step 3: 写 LanguageCard + GenreCard**

```kotlin
package com.hank.musicfree.feature.liststats.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hank.musicfree.data.repository.listenstats.model.Distribution

private val SERIES = listOf(0xFFF17D34, 0xFF5B6236, 0xFF0A95C8, 0xFF765847, 0xFFA5887A).map(::Color)

@Composable
fun LanguageCard(distribution: Distribution<String?>, onSegmentClick: (String?) -> Unit, modifier: Modifier = Modifier) {
    DistributionCard(title = "语言分布", subtitle = "来自插件 tag/language 字段", distribution = distribution, onItemClick = onSegmentClick, modifier = modifier)
}

@Composable
fun GenreCard(distribution: Distribution<String?>, onRowClick: (String?) -> Unit, modifier: Modifier = Modifier) {
    DistributionCard(title = "音乐风格", subtitle = "来自插件 genre/style/tags 字段", distribution = distribution, onItemClick = onRowClick, modifier = modifier)
}

@Composable
private fun DistributionCard(
    title: String,
    subtitle: String,
    distribution: Distribution<String?>,
    onItemClick: (String?) -> Unit,
    modifier: Modifier,
) {
    val total = distribution.buckets.sumOf { it.count }.coerceAtLeast(1)
    val coveragePct = (distribution.coverage * 100).toInt()
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("覆盖 $coveragePct%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(12.dp))
            // stacked bar
            Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
                distribution.buckets.filter { it.key != null }.take(5).forEachIndexed { i, b ->
                    val w = b.count.toFloat() / total
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(w)
                            .background(SERIES[i % SERIES.size])
                            .clickable { onItemClick(b.key) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            distribution.buckets.forEachIndexed { i, b ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onItemClick(b.key) }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(if (b.key == null) Color.LightGray else SERIES[i % SERIES.size]))
                    Spacer(Modifier.width(8.dp))
                    Text(b.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    val pct = (b.count * 100f / total).toInt()
                    Text("$pct%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
```

- [ ] **Step 4: 写 HourCard**

```kotlin
package com.hank.musicfree.feature.liststats.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hank.musicfree.data.repository.listenstats.model.HourBucket

@Composable
fun HourCard(buckets: List<HourBucket>, modifier: Modifier = Modifier) {
    val byHour = (0..23).associateWith { h -> buckets.firstOrNull { it.hourOfDay == h }?.seconds ?: 0L }
    val maxSec = (byHour.values.maxOrNull() ?: 1L).coerceAtLeast(1L)
    val peakHour = byHour.maxByOrNull { it.value }?.key
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("听歌时段", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text("你最常在 ${peakHour ?: "—"}:00 听歌", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (0..23).forEach { h ->
                    val ratio = (byHour[h]!!.toFloat() / maxSec).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = (0.1f + ratio * 0.9f).coerceAtMost(1f))),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 5: 写 StreakDiscoveryRow**

```kotlin
package com.hank.musicfree.feature.liststats.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StreakDiscoveryRow(
    streakDays: Int,
    maxStreak: Int,
    firstSeenCount: Int,
    onStreakClick: () -> Unit,
    onDiscoveryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StreakCell(value = "$streakDays", unit = "天", caption = "最长 $maxStreak 天", title = "连续听歌",
            modifier = Modifier.weight(1f), onClick = onStreakClick)
        StreakCell(value = "$firstSeenCount", unit = "首", caption = "本时段首次听到", title = "新发现",
            modifier = Modifier.weight(1f), onClick = onDiscoveryClick)
    }
}

@Composable
private fun StreakCell(value: String, unit: String, caption: String, title: String, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier = modifier, shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(1.dp), onClick = onClick) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                Text(unit, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(4.dp))
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
```

- [ ] **Step 6: 写 HeatmapCard**

```kotlin
package com.hank.musicfree.feature.liststats.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hank.musicfree.data.repository.listenstats.model.DateBucket

@Composable
fun HeatmapCard(cells: List<DateBucket>, onCellClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    val maxSec = (cells.maxOfOrNull { it.seconds } ?: 1L).coerceAtLeast(1L)
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("听歌日历", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text("仅在「月 / 年 / 总计」视图显示", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(12.dp))
            // 简化网格：20 列；行数动态
            val perRow = 20
            cells.chunked(perRow).forEach { rowCells ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    rowCells.forEach { c ->
                        val ratio = (c.seconds.toFloat() / maxSec).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = ratio.coerceAtLeast(0.05f)))
                                .clickable { onCellClick(c.dayEpochDay) },
                        )
                    }
                    repeat(perRow - rowCells.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}
```

- [ ] **Step 7: 补 smoke 测试**

`CardCompositionTest.kt` 末尾追加 3-4 个 case：

```kotlin
@Test fun topArtists_clickSendsArtistName() {
    var clicked: String? = null
    composeRule.setContent {
        TopArtistsCard(
            rows = listOf(com.hank.musicfree.data.db.dao.TopArtistRow("周杰伦", 87, 28, 90000)),
            onSeeAll = {}, onRowClick = { clicked = it },
        )
    }
    composeRule.onNodeWithText("周杰伦").performClick()
    assert(clicked == "周杰伦")
}

@Test fun streak_renders_unit_suffix() {
    composeRule.setContent {
        StreakDiscoveryRow(streakDays = 14, maxStreak = 42, firstSeenCount = 8, onStreakClick = {}, onDiscoveryClick = {})
    }
    composeRule.onNodeWithText("14").assertExists()
    composeRule.onNodeWithText("最长 42 天").assertExists()
    composeRule.onNodeWithText("8").assertExists()
}
```

- [ ] **Step 8: 跑测试**

Run: `./gradlew :feature:listen-stats:testDebugUnitTest --tests "*CardCompositionTest*"`
Expected: 全 PASS

- [ ] **Step 9: Commit**

```bash
git add feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/component/TopSongsCard.kt \
        feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/component/TopArtistsCard.kt \
        feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/component/LanguageCard.kt \
        feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/component/GenreCard.kt \
        feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/component/HourCard.kt \
        feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/component/StreakDiscoveryRow.kt \
        feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/component/HeatmapCard.kt \
        feature/listen-stats/src/test/java/com/hank/musicfree/feature/liststats/component/CardCompositionTest.kt
git commit -m "feat(listen-stats): 卡片组件第二批（Top歌曲/歌手/语言/风格/时段/连续/热力）"
```

---

## Task 20: MoreMenu + ClearStatsDialog

**Files:**
- Create: `feature/listen-stats/.../component/MoreMenu.kt`
- Create: `feature/listen-stats/.../component/ClearStatsDialog.kt`
- Modify: `CardCompositionTest.kt` 加 2-3 个 case

- [ ] **Step 1: 写 MoreMenu**

```kotlin
package com.hank.musicfree.feature.liststats.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*

@Composable
fun MoreMenu(onClear: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("清除统计数据") },
            onClick = { expanded = false; onClear() },
        )
    }
}
```

- [ ] **Step 2: 写 ClearStatsDialog**

```kotlin
package com.hank.musicfree.feature.liststats.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ClearStatsDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清除统计数据") },
        text = { Text("这将删除所有听歌统计数据，且不可恢复。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("清除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
```

- [ ] **Step 3: 补 smoke 测试**

```kotlin
@Test fun moreMenu_opensAndDispatchesClear() {
    var cleared = false
    composeRule.setContent { MoreMenu(onClear = { cleared = true }) }
    composeRule.onNodeWithContentDescription("更多").performClick()
    composeRule.onNodeWithText("清除统计数据").performClick()
    assert(cleared)
}

@Test fun clearDialog_buttonsDispatch() {
    var confirmed = false; var dismissed = false
    composeRule.setContent { ClearStatsDialog(onConfirm = { confirmed = true }, onDismiss = { dismissed = true }) }
    composeRule.onNodeWithText("清除").performClick()
    assert(confirmed && !dismissed)
}
```

- [ ] **Step 4: 跑测试**

Run: `./gradlew :feature:listen-stats:testDebugUnitTest --tests "*CardCompositionTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/component/MoreMenu.kt \
        feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/component/ClearStatsDialog.kt \
        feature/listen-stats/src/test/java/com/hank/musicfree/feature/liststats/component/CardCompositionTest.kt
git commit -m "feat(listen-stats): MoreMenu + ClearStatsDialog 二次确认"
```

---

## Task 21: ListenStatsScreen 组装 + 整页 smoke

**Files:**
- Create: `feature/listen-stats/.../ListenStatsScreen.kt`
- Create: `feature/listen-stats/src/test/.../ListenStatsScreenTest.kt`

- [ ] **Step 1: 写 ListenStatsScreen**

```kotlin
package com.hank.musicfree.feature.liststats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
import com.hank.musicfree.core.ui.MusicFreeTopAppBar
import com.hank.musicfree.data.repository.listenstats.model.TimeScope
import com.hank.musicfree.feature.liststats.component.*

@Composable
fun ListenStatsScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (mode: String, scope: String, anchorEpochDay: Long, filterValue: String?) -> Unit,
    viewModel: ListenStatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MusicFreeScreenScaffold(
        topBar = {
            MusicFreeTopAppBar(
                title = "听歌足迹",
                onBack = onBack,
                actions = { MoreMenu(onClear = viewModel::onClearRequested) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { OnboardingHint(state.firstEventDate) }
            item { HeroTotalDurationCard(state.snapshot.totalSeconds, state.scopeLabel) }
            item { TimeScopeSegmented(current = state.scope, onChange = viewModel::onScopeChange) }
            item { TimeScopePager(label = state.windowLabel, onPrev = viewModel::onPagerPrev, onNext = viewModel::onPagerNext) }
            item {
                SecondaryKpiRow(
                    distinctSongs = state.snapshot.distinctSongs,
                    distinctArtists = state.snapshot.distinctArtists,
                    onSongsClick = { onNavigateToDetail("ALL_SONGS", state.scope.name, state.anchor.toEpochDay(), null) },
                    onArtistsClick = { onNavigateToDetail("ALL_ARTISTS", state.scope.name, state.anchor.toEpochDay(), null) },
                )
            }
            item { DailyBarsCard(daily = state.snapshot.dailyBuckets, onBarClick = {
                viewModel.onScopeChange(TimeScope.DAY)
                viewModel.onAnchorChange(java.time.LocalDate.ofEpochDay(it))
            }) }
            item {
                TopSongsCard(
                    rows = state.snapshot.topSongs,
                    onSeeAll = { onNavigateToDetail("TOP_SONGS", state.scope.name, state.anchor.toEpochDay(), null) },
                    onRowClick = { onNavigateToDetail("TOP_SONGS", state.scope.name, state.anchor.toEpochDay(), null) },
                )
            }
            item {
                TopArtistsCard(
                    rows = state.snapshot.topArtists,
                    onSeeAll = { onNavigateToDetail("TOP_ARTISTS", state.scope.name, state.anchor.toEpochDay(), null) },
                    onRowClick = { artist -> onNavigateToDetail("BY_ARTIST", state.scope.name, state.anchor.toEpochDay(), artist) },
                )
            }
            if (state.snapshot.languageDistribution.coverage >= 0.30f) {
                item {
                    LanguageCard(
                        distribution = state.snapshot.languageDistribution,
                        onSegmentClick = { key -> if (key != null) onNavigateToDetail("BY_LANGUAGE", state.scope.name, state.anchor.toEpochDay(), key) },
                    )
                }
            }
            if (state.snapshot.genreDistribution.coverage >= 0.30f) {
                item {
                    GenreCard(
                        distribution = state.snapshot.genreDistribution,
                        onRowClick = { key -> if (key != null) onNavigateToDetail("BY_GENRE", state.scope.name, state.anchor.toEpochDay(), key) },
                    )
                }
            }
            item { HourCard(buckets = state.snapshot.hourBuckets) }
            item {
                StreakDiscoveryRow(
                    streakDays = state.snapshot.streakDays,
                    maxStreak = state.snapshot.maxStreak,
                    firstSeenCount = state.snapshot.firstSeenCount,
                    onStreakClick = { /* v1 不下钻打卡日历，留给 v2 */ },
                    onDiscoveryClick = { onNavigateToDetail("FIRST_SEEN", state.scope.name, state.anchor.toEpochDay(), null) },
                )
            }
            if (state.scope in listOf(TimeScope.MONTH, TimeScope.YEAR, TimeScope.ALL_TIME)) {
                item {
                    HeatmapCard(cells = state.snapshot.heatmap, onCellClick = {
                        viewModel.onScopeChange(TimeScope.DAY)
                        viewModel.onAnchorChange(java.time.LocalDate.ofEpochDay(it))
                    })
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    if (state.showClearDialog) {
        ClearStatsDialog(onConfirm = viewModel::onClearConfirmed, onDismiss = viewModel::onClearDismissed)
    }
}
```

- [ ] **Step 2: 写 Screen smoke 测试**

```kotlin
package com.hank.musicfree.feature.liststats

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.hank.musicfree.data.repository.listenstats.model.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ListenStatsScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun empty_snapshot_still_renders_appbar_and_hero() {
        val state = ListenStatsScreenState(
            scope = TimeScope.WEEK, anchor = LocalDate.of(2026, 5, 13),
            windowLabel = "5/11 – 5/17", scopeLabel = "本周累计",
            firstEventDate = LocalDate.of(2026, 5, 10),
            snapshot = emptySnapshot(),
        )
        composeRule.setContent {
            StatelessListenStatsScaffold(
                state = state,
                onBack = {}, onScopeChange = {}, onAnchorPrev = {}, onAnchorNext = {},
                onNavigateToDetail = { _, _, _, _ -> }, onClearRequested = {},
                onClearConfirmed = {}, onClearDismissed = {}, onBarClick = {}, onHeatmapClick = {},
            )
        }
        composeRule.onNodeWithText("听歌足迹").assertExists()
        composeRule.onNodeWithText("本周累计").assertExists()
        composeRule.onNodeWithText("开始统计于 2026 年 5 月 10 日").assertExists()
    }

    @Test fun coverage_below_30_hides_language_and_genre_cards() {
        val snap = emptySnapshot().copy(
            languageDistribution = Distribution(emptyList(), coverage = 0.10f),
            genreDistribution = Distribution(emptyList(), coverage = 0.05f),
        )
        composeRule.setContent {
            StatelessListenStatsScaffold(
                state = ListenStatsScreenState(snapshot = snap, windowLabel = "x", scopeLabel = "y"),
                onBack = {}, onScopeChange = {}, onAnchorPrev = {}, onAnchorNext = {},
                onNavigateToDetail = { _, _, _, _ -> }, onClearRequested = {},
                onClearConfirmed = {}, onClearDismissed = {}, onBarClick = {}, onHeatmapClick = {},
            )
        }
        composeRule.onAllNodesWithText("语言分布").assertCountEquals(0)
        composeRule.onAllNodesWithText("音乐风格").assertCountEquals(0)
    }
}
```

注意：测试用 `StatelessListenStatsScaffold` 把整页骨架抽出来不依赖 ViewModel。需要在 `ListenStatsScreen.kt` 同文件加一个内部 `internal` 版本接收 state + callbacks，主公开版本只是它的 wrapper。把 Step 1 的 `ListenStatsScreen` 内部 LazyColumn 部分抽到 `StatelessListenStatsScaffold(state, callbacks...)`，公开 `ListenStatsScreen(...)` 调它。

- [ ] **Step 3: 跑测试**

Run: `./gradlew :feature:listen-stats:testDebugUnitTest --tests "*ListenStatsScreenTest*"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/ListenStatsScreen.kt \
        feature/listen-stats/src/test/java/com/hank/musicfree/feature/liststats/ListenStatsScreenTest.kt
git commit -m "feat(listen-stats): ListenStatsScreen 组装 + coverage 阈值隐藏卡片"
```

---

## Task 22: ListenDetailViewModel + State

**Files:**
- Create: `feature/listen-stats/.../ListenDetailScreenState.kt`
- Create: `feature/listen-stats/.../ListenDetailViewModel.kt`
- Create: `feature/listen-stats/src/test/.../ListenDetailViewModelTest.kt`

- [ ] **Step 1: 写 ScreenState**

```kotlin
package com.hank.musicfree.feature.liststats

import com.hank.musicfree.data.repository.listenstats.model.*
import java.time.LocalDate

enum class DetailSort { PLAY_COUNT_DESC, TOTAL_SEC_DESC, FIRST_SEEN_DESC, LAST_SEEN_DESC }

data class ListenDetailScreenState(
    val mode: DetailMode = DetailMode.ALL_SONGS,
    val scope: TimeScope = TimeScope.WEEK,
    val anchor: LocalDate = LocalDate.now(),
    val windowLabel: String = "",
    val titleByMode: String = "",
    val summary: String = "",
    val sort: DetailSort = DetailSort.PLAY_COUNT_DESC,
    val items: List<ListenedSong> = emptyList(),
    val filterValue: String? = null,
)
```

- [ ] **Step 2: 写 ViewModel**

```kotlin
package com.hank.musicfree.feature.liststats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.hank.musicfree.core.navigation.ListenDetailRoute
import com.hank.musicfree.data.repository.listenstats.ListenStatsRepository
import com.hank.musicfree.data.repository.listenstats.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.*

@HiltViewModel
class ListenDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ListenStatsRepository,
) : ViewModel() {

    private val route: ListenDetailRoute = savedStateHandle.toRoute()
    private val mode = parseDetailMode(route.mode)
    private val scopeFlow = MutableStateFlow(parseTimeScope(route.scope))
    private val anchorFlow = MutableStateFlow(
        if (route.anchorEpochDay >= 0) LocalDate.ofEpochDay(route.anchorEpochDay) else LocalDate.now()
    )
    private val sortFlow = MutableStateFlow(defaultSortFor(mode))

    private val itemsFlow = combine(scopeFlow, anchorFlow) { s, a -> s to a }
        .flatMapLatest { (s, a) -> repository.detail(DetailFilter(mode, route.filterValue), s, a) }
        .combine(sortFlow) { rows, sort -> applySort(rows, sort) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<ListenDetailScreenState> = combine(scopeFlow, anchorFlow, sortFlow, itemsFlow) { sc, an, srt, items ->
        val window = windowFor(sc, an)
        ListenDetailScreenState(
            mode = mode, scope = sc, anchor = an,
            windowLabel = window.label,
            titleByMode = titleForMode(mode, route.filterValue),
            summary = summaryForMode(mode, items.size, route.filterValue, window.label),
            sort = srt, items = items, filterValue = route.filterValue,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListenDetailScreenState())

    fun onScopeChange(s: TimeScope) { scopeFlow.value = s }
    fun onAnchorChange(d: LocalDate) { anchorFlow.value = d }
    fun onSortChange(s: DetailSort) { sortFlow.value = s }

    private fun applySort(rows: List<ListenedSong>, sort: DetailSort): List<ListenedSong> = when (sort) {
        DetailSort.PLAY_COUNT_DESC -> rows.sortedByDescending { it.playCount }
        DetailSort.TOTAL_SEC_DESC -> rows.sortedByDescending { it.totalSec }
        DetailSort.FIRST_SEEN_DESC -> rows.sortedByDescending { it.firstSeenMs }
        DetailSort.LAST_SEEN_DESC -> rows.sortedByDescending { it.lastSeenMs }
    }

    private fun defaultSortFor(m: DetailMode): DetailSort = when (m) {
        DetailMode.FIRST_SEEN -> DetailSort.FIRST_SEEN_DESC
        else -> DetailSort.PLAY_COUNT_DESC
    }

    private fun titleForMode(m: DetailMode, filterValue: String?): String = when (m) {
        DetailMode.ALL_SONGS -> "听过的歌曲"
        DetailMode.ALL_ARTISTS -> "听过的歌手"
        DetailMode.TOP_SONGS -> "Top 歌曲（全部）"
        DetailMode.TOP_ARTISTS -> "Top 歌手（全部）"
        DetailMode.FIRST_SEEN -> "新发现"
        DetailMode.BY_ARTIST -> filterValue ?: "歌手"
        DetailMode.BY_LANGUAGE -> "${filterValue ?: "语言"}"
        DetailMode.BY_GENRE -> "${filterValue ?: "风格"}"
    }

    private fun summaryForMode(m: DetailMode, count: Int, filterValue: String?, windowLabel: String): String = when (m) {
        DetailMode.ALL_SONGS -> "$windowLabel 听过的 $count 首歌"
        DetailMode.ALL_ARTISTS -> "$windowLabel 听过的 $count 位歌手"
        DetailMode.TOP_SONGS -> "$windowLabel 全部排行（共 $count 首）"
        DetailMode.TOP_ARTISTS -> "$windowLabel 全部排行（共 $count 位）"
        DetailMode.FIRST_SEEN -> "$windowLabel 首次听到的 $count 首"
        DetailMode.BY_ARTIST -> "${filterValue ?: ""} · $windowLabel 共 $count 首"
        DetailMode.BY_LANGUAGE -> "${filterValue ?: ""} · $windowLabel 共 $count 首"
        DetailMode.BY_GENRE -> "${filterValue ?: ""} · $windowLabel 共 $count 首"
    }
}
```

- [ ] **Step 3: 写 ViewModel 单测**

```kotlin
package com.hank.musicfree.feature.liststats

import androidx.lifecycle.SavedStateHandle
import com.hank.musicfree.data.repository.listenstats.ListenStatsRepository
import com.hank.musicfree.data.repository.listenstats.model.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*
import kotlin.test.assertEquals

class ListenDetailViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private fun mkSavedState(mode: String, scope: String = "WEEK", anchor: Long = 20221L, filterValue: String? = null) =
        SavedStateHandle().also {
            it["mode"] = mode; it["scope"] = scope; it["anchorEpochDay"] = anchor; it["filterValue"] = filterValue
        }

    private fun mkSong(musicId: String, playCount: Int, firstSeen: Long): ListenedSong =
        ListenedSong(musicId, "p", musicId, "A", null, null, firstSeen, firstSeen, playCount, playCount * 60L)

    @Test fun firstSeen_defaults_to_firstSeenDescSort() = runTest {
        val songs = listOf(mkSong("old", 2, 1000), mkSong("new", 1, 5000))
        val repo: ListenStatsRepository = mock {
            on { detail(any(), any(), any()) } doReturn flowOf(songs)
        }
        val vm = ListenDetailViewModel(mkSavedState("FIRST_SEEN"), repo)
        advanceUntilIdle()
        assertEquals(listOf("new", "old"), vm.state.value.items.map { it.musicId })
        assertEquals(DetailSort.FIRST_SEEN_DESC, vm.state.value.sort)
    }

    @Test fun byArtist_summary_includesFilterValue() = runTest {
        val repo: ListenStatsRepository = mock {
            on { detail(any(), any(), any()) } doReturn flowOf(listOf(mkSong("m1", 5, 1)))
        }
        val vm = ListenDetailViewModel(mkSavedState("BY_ARTIST", filterValue = "周杰伦"), repo)
        advanceUntilIdle()
        assert(vm.state.value.summary.startsWith("周杰伦 ·"))
        assert(vm.state.value.summary.contains("共 1 首"))
    }

    @Test fun onSortChange_reordersItems() = runTest {
        val songs = listOf(mkSong("a", 1, 1000), mkSong("b", 5, 500))
        val repo: ListenStatsRepository = mock {
            on { detail(any(), any(), any()) } doReturn flowOf(songs)
        }
        val vm = ListenDetailViewModel(mkSavedState("ALL_SONGS"), repo)
        advanceUntilIdle()
        assertEquals(listOf("b", "a"), vm.state.value.items.map { it.musicId })
        vm.onSortChange(DetailSort.FIRST_SEEN_DESC); advanceUntilIdle()
        assertEquals(listOf("a", "b"), vm.state.value.items.map { it.musicId })
    }
}
```

- [ ] **Step 4: 跑测试**

Run: `./gradlew :feature:listen-stats:testDebugUnitTest --tests "*ListenDetailViewModelTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/ListenDetailScreenState.kt \
        feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/ListenDetailViewModel.kt \
        feature/listen-stats/src/test/java/com/hank/musicfree/feature/liststats/ListenDetailViewModelTest.kt
git commit -m "feat(listen-stats): ListenDetailViewModel 通用明细 + mode 派生默认排序与文案"
```

---

## Task 23: ListenDetailScreen + SongDetailRow

**Files:**
- Create: `feature/listen-stats/.../component/SongDetailRow.kt`
- Create: `feature/listen-stats/.../ListenDetailScreen.kt`
- Create: `feature/listen-stats/src/test/.../ListenDetailScreenTest.kt`

- [ ] **Step 1: 写 SongDetailRow**

```kotlin
package com.hank.musicfree.feature.liststats.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hank.musicfree.data.repository.listenstats.model.ListenedSong
import java.time.Instant
import java.time.ZoneId

@Composable
fun SongDetailRow(song: ListenedSong, showFirstSeen: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyMedium)
            Text("${song.artistRaw} · ${song.platform}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (showFirstSeen) {
                val date = Instant.ofEpochMilli(song.firstSeenMs).atZone(ZoneId.systemDefault()).toLocalDate()
                Text("${date.monthValue}/${date.dayOfMonth} 首次", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text("${song.playCount} 次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
```

- [ ] **Step 2: 写 ListenDetailScreen**

```kotlin
package com.hank.musicfree.feature.liststats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
import com.hank.musicfree.core.ui.MusicFreeTopAppBar
import com.hank.musicfree.data.repository.listenstats.model.DetailMode
import com.hank.musicfree.feature.liststats.component.*

@Composable
fun ListenDetailScreen(
    onBack: () -> Unit,
    viewModel: ListenDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MusicFreeScreenScaffold(
        topBar = { MusicFreeTopAppBar(title = state.titleByMode, onBack = onBack) },
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(state.summary, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            TimeScopeSegmented(current = state.scope, onChange = viewModel::onScopeChange)
            Spacer(Modifier.height(8.dp))
            TimeScopePager(
                label = state.windowLabel,
                onPrev = { viewModel.onAnchorChange(state.anchor.minusDays(1)) },
                onNext = { viewModel.onAnchorChange(state.anchor.plusDays(1)) },
            )
            Spacer(Modifier.height(8.dp))
            SortChips(current = state.sort, onChange = viewModel::onSortChange)
            Spacer(Modifier.height(8.dp))
            if (state.items.isEmpty()) {
                EmptyHint(text = emptyTextFor(state.mode))
            } else {
                LazyColumn {
                    items(state.items, key = { "${it.platform}/${it.musicId}" }) { song ->
                        SongDetailRow(song = song, showFirstSeen = state.mode == DetailMode.FIRST_SEEN)
                    }
                }
            }
        }
    }
}

@Composable
private fun SortChips(current: DetailSort, onChange: (DetailSort) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = current == DetailSort.PLAY_COUNT_DESC, onClick = { onChange(DetailSort.PLAY_COUNT_DESC) }, label = { Text("按播放次数") })
        FilterChip(selected = current == DetailSort.TOTAL_SEC_DESC, onClick = { onChange(DetailSort.TOTAL_SEC_DESC) }, label = { Text("按时长") })
        FilterChip(selected = current == DetailSort.FIRST_SEEN_DESC, onClick = { onChange(DetailSort.FIRST_SEEN_DESC) }, label = { Text("按首次") })
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
}

private fun emptyTextFor(m: DetailMode): String = when (m) {
    DetailMode.FIRST_SEEN -> "本时段还没有首次听到的歌"
    DetailMode.BY_ARTIST -> "这位歌手在本时段还没出现"
    DetailMode.BY_LANGUAGE -> "本时段没有这个语言的歌"
    DetailMode.BY_GENRE -> "本时段没有这个风格的歌"
    else -> "本时段还没有听过任何歌"
}
```

- [ ] **Step 3: 写 Screen 单测**

```kotlin
package com.hank.musicfree.feature.liststats

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.hank.musicfree.data.repository.listenstats.model.*
import com.hank.musicfree.feature.liststats.component.SongDetailRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ListenDetailScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun firstSeen_mode_shows_firstSeen_badge_on_row() {
        composeRule.setContent {
            SongDetailRow(
                song = ListenedSong("m1", "p", "起风了", "买辣椒也用券", null, null,
                    firstSeenMs = 1715900000000L, lastSeenMs = 1715900000000L,
                    playCount = 5, totalSec = 300),
                showFirstSeen = true,
            )
        }
        composeRule.onNode(hasText("首次", substring = true)).assertExists()
    }
}
```

- [ ] **Step 4: 跑测试**

Run: `./gradlew :feature:listen-stats:testDebugUnitTest --tests "*ListenDetailScreenTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/component/SongDetailRow.kt \
        feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/ListenDetailScreen.kt \
        feature/listen-stats/src/test/java/com/hank/musicfree/feature/liststats/ListenDetailScreenTest.kt
git commit -m "feat(listen-stats): ListenDetailScreen + SongDetailRow（FIRST_SEEN 副信息）"
```

---

## Task 24: NavGraph 挂载 + 跨页跳转 + 仪器测试

**Files:**
- Create: `feature/listen-stats/.../navigation/ListenStatsNavigation.kt`
- Modify: `app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt`
- Create: `app/src/androidTest/java/com/hank/musicfree/HomeDrawerListenStatsEntryTest.kt`

- [ ] **Step 1: 写 NavGraph 扩展**

```kotlin
package com.hank.musicfree.feature.liststats.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.hank.musicfree.core.navigation.ListenDetailRoute
import com.hank.musicfree.core.navigation.ListenStatsRoute
import com.hank.musicfree.feature.liststats.ListenDetailScreen
import com.hank.musicfree.feature.liststats.ListenStatsScreen

fun NavGraphBuilder.listenStatsScreen(navController: NavHostController) {
    composable<ListenStatsRoute> {
        ListenStatsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToDetail = { mode, scope, anchorEpochDay, filterValue ->
                navController.navigate(
                    ListenDetailRoute(mode = mode, scope = scope, anchorEpochDay = anchorEpochDay, filterValue = filterValue),
                )
            },
        )
    }
    composable<ListenDetailRoute> {
        ListenDetailScreen(onBack = { navController.popBackStack() })
    }
}
```

- [ ] **Step 2: AppNavHost 挂上**

`AppNavHost.kt` 找 `NavHost(...) { ... }` 块，在末尾调用：

```kotlin
listenStatsScreen(navController)
```

import：

```kotlin
import com.hank.musicfree.feature.liststats.navigation.listenStatsScreen
```

- [ ] **Step 3: 写仪器测试（抽屉跳转）**

```kotlin
package com.hank.musicfree

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeDrawerListenStatsEntryTest {

    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun openDrawer_clickListenStats_navigatesToListenStatsScreen() {
        // 打开抽屉（顶部 menu 按钮）
        composeRule.onNodeWithContentDescription("菜单", substring = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("听歌足迹").performClick()
        composeRule.waitForIdle()
        // 验证 ListenStatsScreen 的 AppBar title 出现
        composeRule.onNodeWithText("听歌足迹").assertExists()
        // 验证 segmented "周" 默认选中
        composeRule.onNodeWithText("周").assertExists()
    }
}
```

> 注：实际抽屉打开手势 / content description 以 :feature:home 既有 `HomeNavBar` 实际写法为准；如菜单按钮 contentDescription 不是"菜单"，按现有 `HomeFidelity*Test` 用的方式定位。

- [ ] **Step 4: build + 仪器测试**

Run: `./gradlew :app:assembleDebug && ./gradlew :app:connectedDebugAndroidTest --tests "*HomeDrawerListenStatsEntryTest*"`
Expected: BUILD SUCCESSFUL + 仪器测试 PASS（需要连模拟器或设备）

- [ ] **Step 5: Commit**

```bash
git add feature/listen-stats/src/main/java/com/hank/musicfree/feature/liststats/navigation/ListenStatsNavigation.kt \
        app/src/main/java/com/hank/musicfree/navigation/AppNavHost.kt \
        app/src/androidTest/java/com/hank/musicfree/HomeDrawerListenStatsEntryTest.kt
git commit -m "feat(app): 挂载 ListenStatsRoute / ListenDetailRoute + 抽屉跳转仪器测试"
```

---

## Task 25: 整体验收 + Release smoke + 合并 main

**Files:**（无）

- [ ] **Step 1: 全模块测试合跑**

Run:
```bash
./gradlew :data:testDebugUnitTest \
          :player:testDebugUnitTest \
          :feature:listen-stats:testDebugUnitTest \
          :feature:home:testDebugUnitTest \
          :app:testDebugUnitTest
```
Expected: 全 PASS。

- [ ] **Step 2: 仪器测试合跑**

Run: `./gradlew :data:connectedDebugAndroidTest --tests "*Migration9To10Test*" && ./gradlew :app:connectedDebugAndroidTest --tests "*ListenStats*"`
Expected: 全 PASS。

- [ ] **Step 3: Release 构建 smoke**

Run: `./gradlew :app:assembleDebug` （Debug 是默认验收闸门）
Expected: BUILD SUCCESSFUL。

如果签名环境变量齐备，再补一次 `./gradlew :app:assembleRelease`，并在设备上冷启动安装包，确认无 R8 keep 引起的 `ClassNotFoundException`。

- [ ] **Step 4: 真机手动验收 — 按 spec §9 接受标准逐条勾**

1. 抽屉 → 我的 → 听歌足迹 → 跳转 OK
2. 播放某曲 ≥ 30s 后切歌 → DB 写一条 listen_event（用 `adb shell sqlite3` 或调试日志验证）
3. 30s 前切歌 → 不写
4. 五时间窗切换 → 数据正常刷新
5. 每张卡片下钻 → 进明细页 → 排序 / 空态正常
6. ⋯ 菜单 → 清除 → 二次确认 → 数据清空 → 页面空态
7. 从老版本（v9）升级 → 启动不崩 → 统计页空态正常
8. 全部单测仍绿
9. release 不崩（如签名齐备）
10. 真机 5 分钟混合操作（切歌 / 暂停 / seek / 杀进程重启）→ 统计数字与日志一致

- [ ] **Step 5: squash 合并回 main**

```bash
cd /Users/zili/code/android/MusicFreeAndroid    # 主工作区
git checkout main && git pull --ff-only
git merge --squash feat/listen-stats
git commit -m "$(cat <<'EOF'
feat(listen-stats): 实现听歌足迹（统计页 + 明细页 + 侧栏入口）

- listen_event 持久化事件表 + 多歌手子表（AppDatabase v9→v10 迁移）
- ListenTracker 委托 PlayerController 现有 Player.Listener
- 风格 / 语言 best-effort 提取，覆盖率 < 30% 隐藏整张卡片
- :feature:listen-stats 模块（5 视图 + 11 卡片 + 通用明细页 8 mode）
- 抽屉顶部新增「我的 → 听歌足迹」入口
- 30s 阈值算一次有效收听；wall-clock 计时不轮询 position
EOF
)"
git worktree remove .worktrees/listen-stats
git branch -D feat/listen-stats
```

---

## Self-review

回看 spec → 对照 plan 检查项：

**Spec §1 决策清单 → plan 任务对应**：
- 30s 阈值 → Task 13 ListenTracker `THRESHOLD_MS`
- 多歌手 → Task 11 ArtistSplitter + Task 3 listen_event_artist
- 自然周月年 → Task 8 TimeWindow
- 10 张必含卡片 → Task 18 / 19 / 20
- 下钻清单 12 项 → Task 17 ViewModel + Task 21 ListenStatsScreen 跳转 + Task 22 DetailViewModel mode 表
- 视觉 MD3 + #F17D34 → Task 18-23 卡片 + Task 21 Screen
- 「我的」section → Task 15
- ⋯ 菜单清除 → Task 20 + Task 17 onClearConfirmed
- 数据起点 → Task 18 OnboardingHint + Task 9 firstEventDate
- DB 升级 → Task 4 Migration + Task 5 MigrationTestHelper
- history 边界 → 无 task（按 spec 不动）

**Spec §3 schema**：camelCase 字段、indices、FK、cascade 都在 Task 3-5 落到 entity / SQL / 测试中。

**Spec §8 测试策略**：每个分类都有 task：单测（11,12,13 / 17,22）、Room（5,7,10）、Compose（18-23）、仪器（24）。

**Placeholder scan**：本 plan 不含 TBD / TODO；DAO SQL、entity 字段、ViewModel 方法签名前后一致；所有跨 task 引用的类型（`ListenStatsRepository`, `TimeScope`, `DetailMode`, `ListenedSong`, `Distribution<String?>`, `TopSongRow`, `TopArtistRow`）在 Task 3 / 6 / 8 / 9 全部定义到位，后续 task 直接消费。

**类型一致性**：
- `ListenStatsDao.insertEventWithArtists(...)` 签名（Task 6）→ ListenTracker 调用（Task 13）一致 ✓
- `repository.detail(filter, scope, anchor)`（Task 9 签名）→ ListenDetailViewModel 调用（Task 22）一致 ✓
- `playerController.flushListenTrackerForClear()`（Task 14 暴露）→ ListenStatsViewModel 调用（Task 17）一致 ✓
- `FidelityAnchors.Home.DrawerMeListenStats`（Task 2）→ HomeDrawerNavigation 引用（Task 15）一致 ✓

**Acceptance criteria 覆盖**：spec §9 的 10 条全部出现在 Task 25 Step 4 手动验收清单中。

Plan 完整。

---

## Execution Handoff

Plan 完成并保存到 `docs/superpowers/plans/2026-05-15-listen-stats-plan.md`。两种执行方式：

1. **Subagent-Driven (推荐)** — 每个 task 派一个干净 subagent，task 之间停下来人工 review，迭代快。
2. **Inline Execution** — 在当前会话连续执行，按 checkpoint 暂停 review。

> 我（写 plan 的会话）不会直接进入实现。请由你下一次会话选择 Subagent-Driven 还是 Inline Execution；如选 Subagent-Driven 必须先用 `superpowers:using-git-worktrees` 创建 worktree（plan 顶部已给指令），再用 `superpowers:subagent-driven-development` 接管。


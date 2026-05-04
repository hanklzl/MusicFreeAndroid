# Downloading Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement v1 离线下载（downloading）功能：插件音源解析 → OkHttp 下载到公共 `Music/MusicFree/`（MediaStore）→ 持久化任务/已下载映射 → 前台 Service + 通知 → Downloading 页 + 长按下载入口 + LocalScreen 角标。

**Architecture:** 新建 `:downloader` 模块（与 `:data/:player/:plugin` 同层），暴露 `Downloader` 接口（Hilt @Singleton）。内部 `DownloadEngine` 持有任务队列 + 并发槽 + 状态机，由 `DownloadService`（前台 Service，dataSync 类型）承载运行时；HTTP 通过 `HttpDownloader` 接口注入（生产 OkHttp，测试 fake）。Room 表 `download_tasks` 持久化任务、`downloaded_tracks` 持久化"已下载"映射。文件用 `MediaStore.Audio.Media` 写公共 `Music/MusicFree/`（Min SDK 29 起免存储权限）。质量回退在调用方循环 SUPER→HIGH→STANDARD→LOW，不改 `PluginApi.getMediaSource` 签名。

**Tech Stack:** Kotlin 2.3, AGP 9.2, Compose BOM 2026.04, Hilt, Room, DataStore, OkHttp 5, Coroutines/Flow, ConnectivityManager, MediaStore.Audio, Robolectric 4.16, Turbine, kotlinx-coroutines-test。

**Spec:** [`docs/superpowers/specs/2026-05-04-downloading-design.md`](../specs/2026-05-04-downloading-design.md)

---

## Conventions Used by All Tasks

- 包根：`com.zili.android.musicfreeandroid.downloader.*`
- Min SDK 29、JDK 21 toolchain、JVM target 17（与现有模块一致）
- 提交信息格式：`feat(downloader): <subject>` / `test(downloader): <subject>` / `feat(<module>): <subject>`
- 每个 Task 末尾都做一次 commit；commit 之前确保该 task 涉及的 build/test 通过
- 测试分层：纯 JVM 单测在 `src/test/`；Room/MediaStore/Compose 走 `src/androidTest/`（依赖 Robolectric 的 MediaStore writer 单测除外，Robolectric 跑在 `src/test/`）
- 所有顶层文件路径都相对于 worktree 根目录（即 `.worktrees/feat-downloading/`）

---

## Task 0: Create worktree

**Files:** N/A（仅 git 操作）

- [ ] **Step 1: Verify clean main**

```bash
git -C /Users/zili/code/android/MusicFreeAndroid status --short
```

Expected: 空输出（工作树干净）。若有变更，先处理。

- [ ] **Step 2: Confirm `.worktrees/` is gitignored**

```bash
grep -n "\.worktrees" /Users/zili/code/android/MusicFreeAndroid/.gitignore
```

Expected: 输出包含 `.worktrees/`。

- [ ] **Step 3: Create worktree on a new branch**

```bash
cd /Users/zili/code/android/MusicFreeAndroid
git worktree add .worktrees/feat-downloading -b feat/downloading
```

Expected: `Preparing worktree (new branch 'feat/downloading')` + `HEAD is now at <sha> ...`

- [ ] **Step 4: Verify worktree state**

```bash
git -C .worktrees/feat-downloading status
git -C .worktrees/feat-downloading rev-parse --abbrev-ref HEAD
```

Expected: `On branch feat/downloading` + clean tree。

> **重要**：从 Task 1 起，所有命令、文件路径都在 `.worktrees/feat-downloading/` 内执行。下文 paths 写作 `<root>/...` 含义为 `.worktrees/feat-downloading/...`。

---

## Task 1: Create `:downloader` module skeleton

**Files:**
- Create: `<root>/downloader/build.gradle.kts`
- Create: `<root>/downloader/src/main/AndroidManifest.xml`
- Modify: `<root>/settings.gradle.kts`（在末尾追加 `include(":downloader")`）
- Modify: `<root>/app/build.gradle.kts`（在 dependencies 块追加 `implementation(project(":downloader"))`）

- [ ] **Step 1: Create build.gradle.kts**

`<root>/downloader/build.gradle.kts`：

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.zili.android.musicfreeandroid.downloader"
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
        unitTests {
            isIncludeAndroidResources = true
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
    implementation(project(":plugin"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.okhttp)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation("androidx.test:core:1.6.1")

    // Instrumented tests
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
}
```

> 若 `libs.versions.toml` 缺 `androidx-hilt-navigation-compose` 或 `mockk`，先核对一下；现有 feature/home/build.gradle.kts 已使用同名 alias，直接复用。

- [ ] **Step 2: Create empty manifest**

`<root>/downloader/src/main/AndroidManifest.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- 服务声明在 Task 16 加；此处先留空 manifest 以满足 AGP 要求 -->
</manifest>
```

- [ ] **Step 3: Register module in settings.gradle.kts**

在 `<root>/settings.gradle.kts` 末尾追加（`include(":feature:settings")` 之后）：

```kotlin
include(":downloader")
```

- [ ] **Step 4: App depends on :downloader**

在 `<root>/app/build.gradle.kts` 的 `dependencies { ... }` 块内追加：

```kotlin
    implementation(project(":downloader"))
```

- [ ] **Step 5: Build verification**

```bash
cd <root>
./gradlew :downloader:assembleDebug --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL` 且 `:downloader:assembleDebug` 出现在执行任务里。

```bash
./gradlew :app:assembleDebug --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 6: Commit**

```bash
git add downloader/build.gradle.kts downloader/src/main/AndroidManifest.xml settings.gradle.kts app/build.gradle.kts
git commit -m "feat(downloader): scaffold :downloader module"
```

---

## Task 2: Add DownloadTaskEntity + DAO

**Files:**
- Create: `<root>/data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/DownloadTaskEntity.kt`
- Create: `<root>/data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/DownloadTaskDao.kt`
- Create: `<root>/data/src/androidTest/java/com/zili/android/musicfreeandroid/data/db/dao/DownloadTaskDaoTest.kt`
- Modify: `<root>/data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt`（注册 entity + 抽象方法 + bump version 3→4，按"开发期不写 Migration"策略由 `fallbackToDestructiveMigration` 兜底）
- Modify: `<root>/data/src/main/java/com/zili/android/musicfreeandroid/data/di/DataModule.kt`（提供 `DownloadTaskDao`）

- [ ] **Step 1: Write failing DAO test (instrumented)**

`<root>/data/src/androidTest/java/com/zili/android/musicfreeandroid/data/db/dao/DownloadTaskDaoTest.kt`：

```kotlin
package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.entity.DownloadTaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadTaskDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: DownloadTaskDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.downloadTaskDao()
    }

    @After fun teardown() = db.close()

    private fun task(id: String, status: String = "PENDING", platform: String = "qq") = DownloadTaskEntity(
        id = id, platform = platform, title = "t-$id", artist = "a", album = null,
        artwork = null, durationMs = 0L, targetQuality = "standard",
        status = status, errorReason = null, resolvedUrl = null, resolvedHeadersJson = null,
        fileSize = null, downloadedSize = null, createdAt = 1L, updatedAt = 1L,
    )

    @Test fun upsertThenObserveAll() = runTest {
        dao.upsert(task("1"))
        dao.upsert(task("2", status = "FAILED"))
        val all = dao.observeAll().first()
        assertEquals(2, all.size)
    }

    @Test fun findNextPendingReturnsEarliest() = runTest {
        dao.upsert(task("a", status = "FAILED").copy(createdAt = 1L))
        dao.upsert(task("b", status = "PENDING").copy(createdAt = 3L))
        dao.upsert(task("c", status = "PENDING").copy(createdAt = 2L))
        val next = dao.findNextPending()
        assertEquals("c", next?.id)
    }

    @Test fun resetInflightToPendingMovesPreparingAndDownloading() = runTest {
        dao.upsert(task("p", status = "PREPARING"))
        dao.upsert(task("d", status = "DOWNLOADING"))
        dao.upsert(task("f", status = "FAILED"))
        dao.resetInflightToPending()
        val all = dao.observeAll().first().associateBy { it.id }
        assertEquals("PENDING", all["p"]!!.status)
        assertEquals("PENDING", all["d"]!!.status)
        assertEquals("FAILED", all["f"]!!.status)
    }

    @Test fun deleteAllFailedRemovesOnlyFailedRows() = runTest {
        dao.upsert(task("ok", status = "PENDING"))
        dao.upsert(task("bad", status = "FAILED"))
        dao.deleteAllFailed()
        val all = dao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("ok", all[0].id)
    }

    @Test fun deleteByKeyRemovesRow() = runTest {
        dao.upsert(task("x"))
        dao.deleteByKey("x", "qq")
        assertNull(dao.findByKey("x", "qq"))
    }
}
```

- [ ] **Step 2: Run test to confirm it fails (compile error: entity not found)**

```bash
cd <root>
./gradlew :data:connectedDebugAndroidTest --tests "*DownloadTaskDaoTest*" --no-configuration-cache
```

Expected: 编译失败，提示 `Unresolved reference: DownloadTaskEntity`。

- [ ] **Step 3: Create entity**

`<root>/data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/DownloadTaskEntity.kt`：

```kotlin
package com.zili.android.musicfreeandroid.data.db.entity

import androidx.room.Entity

@Entity(tableName = "download_tasks", primaryKeys = ["id", "platform"])
data class DownloadTaskEntity(
    val id: String,
    val platform: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artwork: String?,
    val durationMs: Long,
    val targetQuality: String,            // "low"/"standard"/"high"/"super"
    val status: String,                   // PENDING / PREPARING / DOWNLOADING / FAILED
    val errorReason: String?,             // FailToFetchSource / NoWritePermission / Unknown / NotAllowToDownloadInCellular
    val resolvedUrl: String?,
    val resolvedHeadersJson: String?,
    val fileSize: Long?,
    val downloadedSize: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)
```

- [ ] **Step 4: Create DAO**

`<root>/data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/DownloadTaskDao.kt`：

```kotlin
package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zili.android.musicfreeandroid.data.db.entity.DownloadTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {

    @Upsert
    suspend fun upsert(task: DownloadTaskEntity)

    @Query("SELECT * FROM download_tasks ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id AND platform = :platform")
    suspend fun findByKey(id: String, platform: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT 1")
    suspend fun findNextPending(): DownloadTaskEntity?

    @Query("UPDATE download_tasks SET status = :status, updatedAt = :now WHERE id = :id AND platform = :platform")
    suspend fun updateStatus(id: String, platform: String, status: String, now: Long)

    @Query("UPDATE download_tasks SET status = :status, errorReason = :reason, updatedAt = :now WHERE id = :id AND platform = :platform")
    suspend fun markFailed(id: String, platform: String, status: String = "FAILED", reason: String, now: Long)

    @Query("UPDATE download_tasks SET resolvedUrl = :url, resolvedHeadersJson = :headers, updatedAt = :now WHERE id = :id AND platform = :platform")
    suspend fun setResolved(id: String, platform: String, url: String?, headers: String?, now: Long)

    @Query("UPDATE download_tasks SET fileSize = :fileSize, downloadedSize = :downloaded, updatedAt = :now WHERE id = :id AND platform = :platform")
    suspend fun updateProgress(id: String, platform: String, fileSize: Long?, downloaded: Long?, now: Long)

    @Query("UPDATE download_tasks SET status = 'PENDING', resolvedUrl = NULL, resolvedHeadersJson = NULL, errorReason = NULL WHERE status IN ('PREPARING','DOWNLOADING')")
    suspend fun resetInflightToPending()

    @Query("UPDATE download_tasks SET status = 'PENDING', errorReason = NULL WHERE status = 'FAILED'")
    suspend fun resetAllFailedToPending()

    @Query("DELETE FROM download_tasks WHERE id = :id AND platform = :platform")
    suspend fun deleteByKey(id: String, platform: String)

    @Query("DELETE FROM download_tasks WHERE status = 'FAILED'")
    suspend fun deleteAllFailed()

    @Query("DELETE FROM download_tasks WHERE status IN ('PENDING','PREPARING','DOWNLOADING')")
    suspend fun deleteAllInflight()
}
```

- [ ] **Step 5: Wire into AppDatabase**

修改 `<root>/data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt`，把 entity 加入 `entities = [...]`、`version` 从 `3` 改成 `4`，类内追加 `abstract fun downloadTaskDao(): DownloadTaskDao`，并 import 对应类。

完整新版本：

```kotlin
package com.zili.android.musicfreeandroid.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.DownloadTaskDao
import com.zili.android.musicfreeandroid.data.db.dao.MusicDao
import com.zili.android.musicfreeandroid.data.db.dao.PlaylistDao
import com.zili.android.musicfreeandroid.data.db.dao.PlayQueueDao
import com.zili.android.musicfreeandroid.data.db.dao.StarredSheetDao
import com.zili.android.musicfreeandroid.data.db.entity.DownloadTaskEntity
import com.zili.android.musicfreeandroid.data.db.entity.MusicItemEntity
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistEntity
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistMusicCrossRef
import com.zili.android.musicfreeandroid.data.db.entity.PlayQueueEntity
import com.zili.android.musicfreeandroid.data.db.entity.StarredSheetEntity

@Database(
    entities = [
        MusicItemEntity::class,
        PlaylistEntity::class,
        PlaylistMusicCrossRef::class,
        PlayQueueEntity::class,
        StarredSheetEntity::class,
        DownloadTaskEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playQueueDao(): PlayQueueDao
    abstract fun starredSheetDao(): StarredSheetDao
    abstract fun downloadTaskDao(): DownloadTaskDao
}
```

- [ ] **Step 6: Provide DAO in DataModule**

在 `<root>/data/src/main/java/com/zili/android/musicfreeandroid/data/di/DataModule.kt` 的 `object DataModule` 内追加：

```kotlin
    @Provides
    fun provideDownloadTaskDao(db: AppDatabase): DownloadTaskDao = db.downloadTaskDao()
```

记得在 import 区追加 `import com.zili.android.musicfreeandroid.data.db.dao.DownloadTaskDao`。

- [ ] **Step 7: Run test to verify it passes**

```bash
cd <root>
./gradlew :data:connectedDebugAndroidTest --tests "*DownloadTaskDaoTest*" --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL` + 5 个测试用例全部 PASS。

> 若没有连接的设备/模拟器，先用 `:data:assembleDebugAndroidTest` 验编译。CI/手动验收时再跑真实测试。

- [ ] **Step 8: Commit**

```bash
git add data/
git commit -m "feat(data): add DownloadTaskEntity and DownloadTaskDao"
```

---

## Task 3: Add DownloadedTrackEntity + DAO

**Files:**
- Create: `<root>/data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/DownloadedTrackEntity.kt`
- Create: `<root>/data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/DownloadedTrackDao.kt`
- Create: `<root>/data/src/androidTest/java/com/zili/android/musicfreeandroid/data/db/dao/DownloadedTrackDaoTest.kt`
- Modify: `<root>/data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt`（追加 entity + abstract dao）
- Modify: `<root>/data/src/main/java/com/zili/android/musicfreeandroid/data/di/DataModule.kt`（提供 DownloadedTrackDao）

- [ ] **Step 1: Write failing DAO test**

`<root>/data/src/androidTest/java/com/zili/android/musicfreeandroid/data/db/dao/DownloadedTrackDaoTest.kt`：

```kotlin
package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.entity.DownloadedTrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadedTrackDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: DownloadedTrackDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.downloadedTrackDao()
    }

    @After fun teardown() = db.close()

    private fun row(id: String, platform: String = "qq") = DownloadedTrackEntity(
        id = id, platform = platform,
        mediaStoreUri = "content://media/external/audio/media/$id",
        relativePath = "Music/MusicFree/", mimeType = "audio/mpeg",
        quality = "standard", sizeBytes = 1024L, downloadedAt = 1L,
    )

    @Test fun insertAndExists() = runTest {
        assertFalse(dao.exists("1", "qq"))
        dao.insert(row("1"))
        assertTrue(dao.exists("1", "qq"))
        assertEquals("content://media/external/audio/media/1", dao.findUri("1", "qq"))
    }

    @Test fun deleteByKeyRemovesRow() = runTest {
        dao.insert(row("1"))
        dao.deleteByKey("1", "qq")
        assertFalse(dao.exists("1", "qq"))
        assertNull(dao.findUri("1", "qq"))
    }

    @Test fun observeKeysEmitsCurrentSet() = runTest {
        dao.insert(row("a"))
        dao.insert(row("b"))
        val keys = dao.observeKeys().first().toSet()
        assertEquals(setOf("a@qq", "b@qq"), keys)
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd <root>
./gradlew :data:assembleDebugAndroidTest --no-configuration-cache 2>&1 | tail -20
```

Expected: `Unresolved reference: DownloadedTrackEntity`。

- [ ] **Step 3: Create entity**

`<root>/data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/DownloadedTrackEntity.kt`：

```kotlin
package com.zili.android.musicfreeandroid.data.db.entity

import androidx.room.Entity

@Entity(tableName = "downloaded_tracks", primaryKeys = ["id", "platform"])
data class DownloadedTrackEntity(
    val id: String,
    val platform: String,
    val mediaStoreUri: String,        // content://media/external/audio/media/<id>
    val relativePath: String,         // e.g., "Music/MusicFree/"
    val mimeType: String,
    val quality: String,
    val sizeBytes: Long,
    val downloadedAt: Long,
)
```

- [ ] **Step 4: Create DAO**

`<root>/data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/DownloadedTrackDao.kt`：

```kotlin
package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zili.android.musicfreeandroid.data.db.entity.DownloadedTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedTrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadedTrackEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_tracks WHERE id = :id AND platform = :platform)")
    suspend fun exists(id: String, platform: String): Boolean

    @Query("SELECT mediaStoreUri FROM downloaded_tracks WHERE id = :id AND platform = :platform")
    suspend fun findUri(id: String, platform: String): String?

    @Query("DELETE FROM downloaded_tracks WHERE id = :id AND platform = :platform")
    suspend fun deleteByKey(id: String, platform: String)

    @Query("SELECT id || '@' || platform FROM downloaded_tracks")
    fun observeKeys(): Flow<List<String>>
}
```

- [ ] **Step 5: Wire into AppDatabase**

修改 `<root>/data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt`，把 `DownloadedTrackEntity::class` 加入 entities 数组，类内追加 `abstract fun downloadedTrackDao(): DownloadedTrackDao`，import 也补上。version 仍为 `4`（同一次开发期演进）。

- [ ] **Step 6: Provide in DataModule**

在 `DataModule` 内追加：

```kotlin
    @Provides
    fun provideDownloadedTrackDao(db: AppDatabase): DownloadedTrackDao = db.downloadedTrackDao()
```

import 增加 `import com.zili.android.musicfreeandroid.data.db.dao.DownloadedTrackDao`。

- [ ] **Step 7: Run test to verify it passes**

```bash
cd <root>
./gradlew :data:connectedDebugAndroidTest --tests "*DownloadedTrackDaoTest*" --no-configuration-cache
```

Expected: 3 个测试 PASS。

- [ ] **Step 8: Commit**

```bash
git add data/
git commit -m "feat(data): add DownloadedTrackEntity and DownloadedTrackDao"
```

---

## Task 4: Extend AppPreferences with download keys

**Files:**
- Modify: `<root>/data/src/main/java/com/zili/android/musicfreeandroid/data/datastore/AppPreferences.kt`
- Create: `<root>/data/src/test/java/com/zili/android/musicfreeandroid/data/datastore/DownloadPreferencesKeysTest.kt`（在 JVM 测试集；如 `:data` 没有 src/test/，先创建对应目录）

> 注：当前 `AppPreferences` 没有单测。本 Task 先补一个最简的"setter 写入 + getter 读到"的回路测试，覆盖 4 个新键（不重写既有键的测试）。

- [ ] **Step 1: Write failing JVM test**

如果 `<root>/data/src/test/java/com/zili/android/musicfreeandroid/data/datastore/` 不存在，先创建目录。然后写：

```kotlin
package com.zili.android.musicfreeandroid.data.datastore

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import java.io.File

class DownloadPreferencesKeysTest {

    @get:Rule val tmp = TemporaryFolder()
    private lateinit var prefs: AppPreferences

    @Before fun setup() {
        val file = File(tmp.newFolder(), "app_preferences.preferences_pb")
        val store = PreferenceDataStoreFactory.create(produceFile = { file })
        prefs = AppPreferences(store)
    }

    @After fun teardown() = Unit

    @Test fun maxDownloadDefaultIs3AndClampedTo1To10() = runTest {
        assertEquals(3, prefs.maxDownload.first())
        prefs.setMaxDownload(0)
        assertEquals(1, prefs.maxDownload.first())
        prefs.setMaxDownload(99)
        assertEquals(10, prefs.maxDownload.first())
        prefs.setMaxDownload(5)
        assertEquals(5, prefs.maxDownload.first())
    }

    @Test fun useCellularDownloadDefaultsToFalse() = runTest {
        assertFalse(prefs.useCellularDownload.first())
        prefs.setUseCellularDownload(true)
        assertTrue(prefs.useCellularDownload.first())
    }

    @Test fun defaultDownloadQualityDefaultsToStandard() = runTest {
        assertEquals(PlayQuality.STANDARD, prefs.defaultDownloadQuality.first())
        prefs.setDefaultDownloadQuality(PlayQuality.HIGH)
        assertEquals(PlayQuality.HIGH, prefs.defaultDownloadQuality.first())
    }

    @Test fun downloadDirRelativeDefaultsToMusicMusicFree() = runTest {
        assertEquals("Music/MusicFree/", prefs.downloadDirRelative.first())
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd <root>
./gradlew :data:testDebugUnitTest --tests "*DownloadPreferencesKeysTest*" --no-configuration-cache
```

Expected: 编译失败（找不到 `prefs.maxDownload` 等）。

- [ ] **Step 3: Add keys + flows + setters**

修改 `<root>/data/src/main/java/com/zili/android/musicfreeandroid/data/datastore/AppPreferences.kt`：

在 `class AppPreferences` 内（`searchHistory` 块下方）追加：

```kotlin
    // ── Download Settings ──

    val maxDownload: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[KEY_MAX_DOWNLOAD] ?: 3).coerceIn(1, 10)
    }

    suspend fun setMaxDownload(value: Int) {
        dataStore.edit { it[KEY_MAX_DOWNLOAD] = value.coerceIn(1, 10) }
    }

    val useCellularDownload: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_USE_CELLULAR_DOWNLOAD] ?: false
    }

    suspend fun setUseCellularDownload(value: Boolean) {
        dataStore.edit { it[KEY_USE_CELLULAR_DOWNLOAD] = value }
    }

    val defaultDownloadQuality: Flow<PlayQuality> = dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_DOWNLOAD_QUALITY]?.let { runCatching { PlayQuality.valueOf(it) }.getOrNull() }
            ?: PlayQuality.STANDARD
    }

    suspend fun setDefaultDownloadQuality(quality: PlayQuality) {
        dataStore.edit { it[KEY_DEFAULT_DOWNLOAD_QUALITY] = quality.name }
    }

    val downloadDirRelative: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_DOWNLOAD_DIR_RELATIVE] ?: "Music/MusicFree/"
    }

    suspend fun setDownloadDirRelative(value: String) {
        dataStore.edit { it[KEY_DOWNLOAD_DIR_RELATIVE] = value }
    }
```

在 `private companion object` 内追加：

```kotlin
        val KEY_MAX_DOWNLOAD = intPreferencesKey("max_download")
        val KEY_USE_CELLULAR_DOWNLOAD = booleanPreferencesKey("use_cellular_download")
        val KEY_DEFAULT_DOWNLOAD_QUALITY = stringPreferencesKey("default_download_quality")
        val KEY_DOWNLOAD_DIR_RELATIVE = stringPreferencesKey("download_dir_relative")
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd <root>
./gradlew :data:testDebugUnitTest --tests "*DownloadPreferencesKeysTest*" --no-configuration-cache
```

Expected: 4 个测试 PASS。

- [ ] **Step 5: Commit**

```bash
git add data/
git commit -m "feat(data): add download-related AppPreferences keys"
```

---

## Task 5: Define core models in :downloader

**Files:**
- Create: `<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/model/MediaKey.kt`
- Create: `<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/model/DownloadStatus.kt`
- Create: `<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/model/DownloadFailReason.kt`
- Create: `<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/model/DownloadTaskUi.kt`
- Create: `<root>/downloader/src/test/java/com/zili/android/musicfreeandroid/downloader/model/MediaKeyTest.kt`

- [ ] **Step 1: Write failing test for MediaKey**

`<root>/downloader/src/test/java/com/zili/android/musicfreeandroid/downloader/model/MediaKeyTest.kt`：

```kotlin
package com.zili.android.musicfreeandroid.downloader.model

import com.zili.android.musicfreeandroid.core.model.MusicItem
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaKeyTest {
    private fun item(id: String, platform: String) = MusicItem(
        id = id, platform = platform, title = "t", artist = "a",
        album = null, duration = 0L, url = null, artwork = null, qualities = null,
    )

    @Test fun fromMusicItemFormatsAsIdAtPlatform() {
        val k = MediaKey.of(item(id = "abc", platform = "qq"))
        assertEquals("abc@qq", k.value)
    }

    @Test fun ofIdAndPlatformMatchesItemFactory() {
        assertEquals(MediaKey.of("x", "wy"), MediaKey.of(item("x", "wy")))
    }

    @Test fun decomposeRoundTrips() {
        val k = MediaKey.of("song-1", "kuwo")
        assertEquals("song-1" to "kuwo", k.id to k.platform)
    }
}
```

> 假设 `MusicItem` 已有这些字段（与 `:core/model/MusicItem` 现状一致）。如签名不同，按现行 `MusicItem` 调整测试构造调用。

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd <root>
./gradlew :downloader:testDebugUnitTest --no-configuration-cache 2>&1 | tail -20
```

Expected: `Unresolved reference: MediaKey`。

- [ ] **Step 3: Create model files**

`<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/model/MediaKey.kt`：

```kotlin
package com.zili.android.musicfreeandroid.downloader.model

import com.zili.android.musicfreeandroid.core.model.MusicItem

@JvmInline
value class MediaKey private constructor(val value: String) {
    val id: String get() = value.substringBefore('@')
    val platform: String get() = value.substringAfter('@')

    companion object {
        fun of(id: String, platform: String): MediaKey = MediaKey("$id@$platform")
        fun of(item: MusicItem): MediaKey = of(item.id, item.platform)
    }
}
```

`<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/model/DownloadStatus.kt`：

```kotlin
package com.zili.android.musicfreeandroid.downloader.model

enum class DownloadStatus { PENDING, PREPARING, DOWNLOADING, FAILED }
```

`<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/model/DownloadFailReason.kt`：

```kotlin
package com.zili.android.musicfreeandroid.downloader.model

enum class DownloadFailReason {
    FailToFetchSource,
    NoWritePermission,
    NotAllowToDownloadInCellular,
    NetworkOffline,
    Unknown,
}
```

`<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/model/DownloadTaskUi.kt`：

```kotlin
package com.zili.android.musicfreeandroid.downloader.model

data class DownloadTaskUi(
    val key: MediaKey,
    val title: String,
    val artist: String,
    val artwork: String?,
    val status: DownloadStatus,
    val targetQuality: String,
    val downloadedBytes: Long?,
    val totalBytes: Long?,
    val errorReason: DownloadFailReason?,
)
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd <root>
./gradlew :downloader:testDebugUnitTest --tests "*MediaKeyTest*" --no-configuration-cache
```

Expected: 3 个测试 PASS。

- [ ] **Step 5: Commit**

```bash
git add downloader/
git commit -m "feat(downloader): add core models (MediaKey, DownloadStatus, DownloadFailReason, DownloadTaskUi)"
```

---

## Task 6: Filename helpers

**Files:**
- Create: `<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/engine/DownloadFilenames.kt`
- Create: `<root>/downloader/src/test/java/com/zili/android/musicfreeandroid/downloader/engine/DownloadFilenamesTest.kt`

- [ ] **Step 1: Write failing test**

`<root>/downloader/src/test/java/com/zili/android/musicfreeandroid/downloader/engine/DownloadFilenamesTest.kt`：

```kotlin
package com.zili.android.musicfreeandroid.downloader.engine

import com.zili.android.musicfreeandroid.core.model.MusicItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFilenamesTest {
    private fun item(title: String = "晴天", artist: String = "周杰伦", platform: String = "qq", id: String = "001") =
        MusicItem(id = id, platform = platform, title = title, artist = artist,
            album = null, duration = 0L, url = null, artwork = null, qualities = null)

    @Test fun escapeReplacesIllegalChars() {
        val s = DownloadFilenames.escape("a/b\\c:d*e?f\"g<h>i|j@k")
        assertEquals("a_b_c_d_e_f_g_h_i_j_k", s)
    }

    @Test fun displayNameJoinsFieldsWithAtAndAppendsExtension() {
        val name = DownloadFilenames.displayName(item(), ext = "mp3")
        assertEquals("qq@001@晴天@周杰伦.mp3", name)
    }

    @Test fun displayNameStripsAtFromFieldValuesToProtectSeparator() {
        val name = DownloadFilenames.displayName(item(title = "a@b", artist = "c@d"), ext = "mp3")
        assertEquals("qq@001@a_b@c_d.mp3", name)
    }

    @Test fun displayNameTruncatesBaseTo200Chars() {
        val long = "x".repeat(300)
        val name = DownloadFilenames.displayName(item(title = long), ext = "mp3")
        assertTrue("base length", name.removeSuffix(".mp3").length == 200)
    }

    @Test fun extensionFromUrlMatchesPathTail() {
        assertEquals("flac", DownloadFilenames.extensionFromUrl("https://x.com/song.flac"))
        assertEquals("m4a", DownloadFilenames.extensionFromUrl("https://x.com/song.m4a?token=abc"))
    }

    @Test fun extensionFromUrlFallsBackToMp3WhenUnknownOrMissing() {
        assertEquals("mp3", DownloadFilenames.extensionFromUrl("https://x.com/song"))
        assertEquals("mp3", DownloadFilenames.extensionFromUrl("https://x.com/song.exe"))
    }

    @Test fun mimeForKnownExtensions() {
        assertEquals("audio/mpeg", DownloadFilenames.mimeFor("mp3"))
        assertEquals("audio/flac", DownloadFilenames.mimeFor("flac"))
        assertEquals("audio/mp4", DownloadFilenames.mimeFor("m4a"))
        assertEquals("audio/aac", DownloadFilenames.mimeFor("aac"))
        assertEquals("audio/ogg", DownloadFilenames.mimeFor("ogg"))
        assertEquals("audio/wav", DownloadFilenames.mimeFor("wav"))
        assertEquals("audio/x-ms-wma", DownloadFilenames.mimeFor("wma"))
        assertEquals("audio/x-ape", DownloadFilenames.mimeFor("ape"))
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd <root>
./gradlew :downloader:testDebugUnitTest --tests "*DownloadFilenamesTest*" --no-configuration-cache 2>&1 | tail -10
```

Expected: `Unresolved reference: DownloadFilenames`.

- [ ] **Step 3: Implement**

`<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/engine/DownloadFilenames.kt`：

```kotlin
package com.zili.android.musicfreeandroid.downloader.engine

import com.zili.android.musicfreeandroid.core.model.MusicItem

object DownloadFilenames {

    private val ESCAPE_RE = Regex("""[\\/:*?"<>|@]""")
    private val SUPPORTED = setOf("mp3", "flac", "wma", "m4a", "aac", "ogg", "wav", "ape")
    private val EXT_RE = Regex("""\.([A-Za-z0-9]{2,5})(?:[?#].*)?$""")

    fun escape(s: String): String = s.replace(ESCAPE_RE, "_")

    fun displayName(item: MusicItem, ext: String): String {
        val base = "${escape(item.platform)}@${escape(item.id)}@${escape(item.title)}@${escape(item.artist)}"
            .take(200)
        return "$base.$ext"
    }

    fun extensionFromUrl(url: String): String {
        val m = EXT_RE.find(url) ?: return "mp3"
        val ext = m.groupValues[1].lowercase()
        return if (ext in SUPPORTED) ext else "mp3"
    }

    fun mimeFor(ext: String): String = when (ext.lowercase()) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "wma" -> "audio/x-ms-wma"
        "ape" -> "audio/x-ape"
        else -> "audio/mpeg"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :downloader:testDebugUnitTest --tests "*DownloadFilenamesTest*" --no-configuration-cache
```

Expected: 7 个测试 PASS。

- [ ] **Step 5: Commit**

```bash
git add downloader/
git commit -m "feat(downloader): add filename/MIME helpers"
```

---

## Task 7: Quality fallback resolver

**Files:**
- Create: `<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/quality/QualityFallback.kt`
- Create: `<root>/downloader/src/test/java/com/zili/android/musicfreeandroid/downloader/quality/QualityFallbackTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.zili.android.musicfreeandroid.downloader.quality

import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QualityFallbackTest {
    private fun item() = MusicItem(
        id = "1", platform = "qq", title = "t", artist = "a",
        album = null, duration = 0L, url = null, artwork = null, qualities = null,
    )

    private class FakeResolver(private val table: Map<String, MediaSourceResult?>) {
        val callOrder = mutableListOf<String>()
        suspend fun resolve(it: MusicItem, q: String): MediaSourceResult? {
            callOrder += q
            return table[q]
        }
    }

    @Test fun startsAtRequestedQualityAndStepsDown() = runTest {
        val fr = FakeResolver(mapOf(
            "super" to null,
            "high" to MediaSourceResult(url = "u-high", headers = null, userAgent = null),
            "standard" to MediaSourceResult(url = "u-std", headers = null, userAgent = null),
        ))
        val (q, src) = QualityFallback.resolve(item(), PlayQuality.SUPER) { it, ql -> fr.resolve(it, ql) }!!
        assertEquals(PlayQuality.HIGH, q)
        assertEquals("u-high", src.url)
        assertEquals(listOf("super", "high"), fr.callOrder)
    }

    @Test fun nullUrlIsSkippedSameAsNullResult() = runTest {
        val fr = FakeResolver(mapOf(
            "high" to MediaSourceResult(url = null, headers = null, userAgent = null),
            "standard" to MediaSourceResult(url = "u-std", headers = null, userAgent = null),
        ))
        val (q, _) = QualityFallback.resolve(item(), PlayQuality.HIGH) { it, ql -> fr.resolve(it, ql) }!!
        assertEquals(PlayQuality.STANDARD, q)
        assertEquals(listOf("high", "standard", "low"), fr.callOrder.also {
            // Note: low only requested if standard returned null too. Here standard succeeds, so call stops.
        })
        // Correct expectation:
        assertEquals(listOf("high", "standard"), fr.callOrder)
    }

    @Test fun returnsNullWhenAllFail() = runTest {
        val fr = FakeResolver(emptyMap())
        val result = QualityFallback.resolve(item(), PlayQuality.LOW) { it, ql -> fr.resolve(it, ql) }
        assertNull(result)
        assertEquals(listOf("low"), fr.callOrder)
    }
}
```

> 上面 `nullUrlIsSkippedSameAsNullResult` 测试中两条 `assertEquals` 的第一条是错误的（注释里也说了），应该删掉它，只保留 `assertEquals(listOf("high", "standard"), fr.callOrder)`。提交前清理。

修正版（直接使用这个）：

```kotlin
    @Test fun nullUrlIsSkippedSameAsNullResult() = runTest {
        val fr = FakeResolver(mapOf(
            "high" to MediaSourceResult(url = null, headers = null, userAgent = null),
            "standard" to MediaSourceResult(url = "u-std", headers = null, userAgent = null),
        ))
        val (q, src) = QualityFallback.resolve(item(), PlayQuality.HIGH) { it, ql -> fr.resolve(it, ql) }!!
        assertEquals(PlayQuality.STANDARD, q)
        assertEquals("u-std", src.url)
        assertEquals(listOf("high", "standard"), fr.callOrder)
    }
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
./gradlew :downloader:testDebugUnitTest --tests "*QualityFallbackTest*" --no-configuration-cache 2>&1 | tail -10
```

Expected: `Unresolved reference: QualityFallback`.

- [ ] **Step 3: Implement**

`<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/quality/QualityFallback.kt`：

```kotlin
package com.zili.android.musicfreeandroid.downloader.quality

import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality

typealias QualityResolver = suspend (MusicItem, qualityWire: String) -> MediaSourceResult?

object QualityFallback {

    private val DESC = listOf(PlayQuality.SUPER, PlayQuality.HIGH, PlayQuality.STANDARD, PlayQuality.LOW)

    private fun PlayQuality.wireName(): String = name.lowercase()

    suspend fun resolve(
        item: MusicItem,
        target: PlayQuality,
        resolver: QualityResolver,
    ): Pair<PlayQuality, MediaSourceResult>? {
        val startIdx = DESC.indexOf(target).coerceAtLeast(0)
        for (q in DESC.subList(startIdx, DESC.size)) {
            val r = runCatching { resolver(item, q.wireName()) }.getOrNull()
            if (r != null && !r.url.isNullOrBlank()) {
                return q to r
            }
        }
        return null
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :downloader:testDebugUnitTest --tests "*QualityFallbackTest*" --no-configuration-cache
```

Expected: 3 tests PASS。

- [ ] **Step 5: Commit**

```bash
git add downloader/
git commit -m "feat(downloader): add quality fallback resolver"
```

---

## Task 8: HttpDownloader interface + OkHttpDownloader

**Files:**
- Create: `<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/io/HttpDownloader.kt`
- Create: `<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/io/OkHttpDownloader.kt`
- Create: `<root>/downloader/src/test/java/com/zili/android/musicfreeandroid/downloader/io/OkHttpDownloaderTest.kt`

`HttpDownloader` 是为了让 `DownloadEngine` 可在 JVM 测试中注入 fake；`OkHttpDownloader` 是生产实现。

- [ ] **Step 1: Write failing test (Robolectric for OkHttp + MockWebServer not required — use real OkHttp + a tiny localhost loopback via `MockWebServer`)**

`libs.versions.toml` 加 mockwebserver（如果未加）：

```toml
mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
```

并在 `:downloader/build.gradle.kts` 的 dependencies 中追加：

```kotlin
    testImplementation(libs.mockwebserver)
```

测试代码 `<root>/downloader/src/test/java/com/zili/android/musicfreeandroid/downloader/io/OkHttpDownloaderTest.kt`：

```kotlin
package com.zili.android.musicfreeandroid.downloader.io

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OkHttpDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var subject: OkHttpDownloader
    private lateinit var workDir: File

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        subject = OkHttpDownloader(OkHttpClient())
        workDir = Files.createTempDirectory("dl").toFile()
    }

    @After fun teardown() {
        server.shutdown()
        workDir.deleteRecursively()
    }

    @Test fun downloadsBodyToTargetFileAndEmitsProgress() = runTest {
        val payload = Buffer().apply { writeUtf8("hello-world".repeat(2048)) }
        val expected = payload.readByteArray()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Length", expected.size.toString())
                .setBody(Buffer().apply { write(expected) }),
        )
        val target = File(workDir, "out.mp3")
        val emissions = mutableListOf<HttpDownloadProgress>()
        subject.download(
            url = server.url("/song.mp3").toString(),
            headers = emptyMap(),
            target = target,
            onProgress = { emissions += it },
        )
        assertTrue(target.exists())
        assertEquals(expected.size.toLong(), target.length())
        assertEquals(expected.size.toLong(), emissions.last().downloaded)
        assertEquals(expected.size.toLong(), emissions.last().total)
    }

    @Test(expected = HttpDownloadException::class)
    fun http500BubblesAsHttpDownloadException() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        subject.download(
            url = server.url("/x").toString(),
            headers = emptyMap(),
            target = File(workDir, "x.mp3"),
            onProgress = {},
        )
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
./gradlew :downloader:testDebugUnitTest --tests "*OkHttpDownloaderTest*" --no-configuration-cache 2>&1 | tail -10
```

Expected: `Unresolved reference: OkHttpDownloader` 等。

- [ ] **Step 3: Implement interface + impl**

`<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/io/HttpDownloader.kt`：

```kotlin
package com.zili.android.musicfreeandroid.downloader.io

import java.io.File

data class HttpDownloadProgress(val downloaded: Long, val total: Long)

class HttpDownloadException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

interface HttpDownloader {
    /**
     * Downloads [url] to [target]; emits progress through [onProgress].
     * Throws [HttpDownloadException] on non-2xx or IO failure.
     * Caller is responsible for cleaning up [target] on cancellation.
     */
    suspend fun download(
        url: String,
        headers: Map<String, String>,
        target: File,
        onProgress: (HttpDownloadProgress) -> Unit,
    )
}
```

`<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/io/OkHttpDownloader.kt`：

```kotlin
package com.zili.android.musicfreeandroid.downloader.io

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File
import java.io.IOException

@Singleton
class OkHttpDownloader @Inject constructor(
    private val client: OkHttpClient,
) : HttpDownloader {

    override suspend fun download(
        url: String,
        headers: Map<String, String>,
        target: File,
        onProgress: (HttpDownloadProgress) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        val response = try {
            client.newCall(builder.build()).execute()
        } catch (e: IOException) {
            throw HttpDownloadException("network error", e)
        }
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw HttpDownloadException("HTTP ${resp.code}")
            }
            val total = resp.header("Content-Length")?.toLongOrNull() ?: -1L
            val body = resp.body ?: throw HttpDownloadException("empty body")
            target.parentFile?.mkdirs()
            val source = body.source()
            target.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var downloaded = 0L
                var lastEmit = 0L
                var lastTimeMs = 0L
                while (true) {
                    val n = source.read(buf)
                    if (n == -1) break
                    out.write(buf, 0, n)
                    downloaded += n
                    val now = System.currentTimeMillis()
                    if (downloaded - lastEmit >= 64 * 1024 || now - lastTimeMs >= 250) {
                        onProgress(HttpDownloadProgress(downloaded, total))
                        lastEmit = downloaded
                        lastTimeMs = now
                    }
                }
                onProgress(HttpDownloadProgress(downloaded, if (total > 0) total else downloaded))
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :downloader:testDebugUnitTest --tests "*OkHttpDownloaderTest*" --no-configuration-cache
```

Expected: 2 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add downloader/ gradle/libs.versions.toml
git commit -m "feat(downloader): add HttpDownloader interface and OkHttp impl"
```

---

## Task 9: MediaStoreMusicWriter

**Files:**
- Create: `<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/engine/MediaStoreMusicWriter.kt`
- Create: `<root>/downloader/src/test/java/com/zili/android/musicfreeandroid/downloader/engine/MediaStoreMusicWriterTest.kt`（Robolectric 单测）

- [ ] **Step 1: Write failing test (Robolectric)**

```kotlin
package com.zili.android.musicfreeandroid.downloader.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaStoreMusicWriterTest {

    @Test fun commitInsertsThenClearsIsPendingAndStreamsBytes() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val tmp = Files.createTempDirectory("mscache").toFile()
        val cacheFile = File(tmp, "cache.mp3").apply { writeBytes(ByteArray(2048) { it.toByte() }) }
        val writer = MediaStoreMusicWriter(ctx)
        val uri = writer.commit(
            cacheFile = cacheFile,
            displayName = "qq@1@title@artist.mp3",
            mimeType = "audio/mpeg",
            relativePath = "Music/MusicFree/",
            sizeBytes = cacheFile.length(),
        )
        assertNotNull(uri)
        // verify openInputStream succeeds (Robolectric ShadowContentResolver tracks the entry)
        ctx.contentResolver.openInputStream(uri).use { input ->
            assertTrue(input!!.readBytes().isNotEmpty())
        }
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
./gradlew :downloader:testDebugUnitTest --tests "*MediaStoreMusicWriterTest*" --no-configuration-cache 2>&1 | tail -10
```

Expected: 找不到 `MediaStoreMusicWriter`。

- [ ] **Step 3: Implement**

```kotlin
package com.zili.android.musicfreeandroid.downloader.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
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
        val initial = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Audio.Media.SIZE, sizeBytes)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            initial,
        ) ?: throw IOException("MediaStore.insert returned null")
        try {
            resolver.openOutputStream(uri).use { out ->
                requireNotNull(out) { "openOutputStream returned null" }
                cacheFile.inputStream().use { it.copyTo(out) }
            }
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
        uri
    }
}
```

- [ ] **Step 4: Run test**

```bash
./gradlew :downloader:testDebugUnitTest --tests "*MediaStoreMusicWriterTest*" --no-configuration-cache
```

Expected: 1 test PASS.

- [ ] **Step 5: Commit**

```bash
git add downloader/
git commit -m "feat(downloader): add MediaStoreMusicWriter"
```

---

## Task 10: NetworkMonitor

**Files:**
- Create: `<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/io/NetworkMonitor.kt`
- Create: `<root>/downloader/src/test/java/com/zili/android/musicfreeandroid/downloader/io/NetworkStateMappingTest.kt`

NetworkMonitor 包两件事：(1) 一个纯函数 `mapToNetworkState(NetworkCapabilities?)`，可纯 JVM 测试；(2) 用 `ConnectivityManager.NetworkCallback` 包装的 Flow（运行时验证）。

- [ ] **Step 1: Write failing test for the pure mapping function**

```kotlin
package com.zili.android.musicfreeandroid.downloader.io

import android.net.NetworkCapabilities
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkStateMappingTest {

    @Test fun nullCapabilitiesMeansOffline() {
        assertEquals(NetworkState.Offline, NetworkMonitor.mapToNetworkState(null))
    }

    @Test fun wifiTransportMapsToWifi() {
        val nc = mockk<NetworkCapabilities> {
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
            every { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
            every { hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
            every { hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        }
        assertEquals(NetworkState.Wifi, NetworkMonitor.mapToNetworkState(nc))
    }

    @Test fun cellularTransportMapsToCellular() {
        val nc = mockk<NetworkCapabilities> {
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns true
            every { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
            every { hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
            every { hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        }
        assertEquals(NetworkState.Cellular, NetworkMonitor.mapToNetworkState(nc))
    }

    @Test fun internetWithoutValidatedStillCountsButNoInternetIsOffline() {
        val nc = mockk<NetworkCapabilities> {
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false
            every { hasTransport(any()) } returns false
        }
        assertEquals(NetworkState.Offline, NetworkMonitor.mapToNetworkState(nc))
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
./gradlew :downloader:testDebugUnitTest --tests "*NetworkStateMappingTest*" --no-configuration-cache 2>&1 | tail -10
```

Expected: `Unresolved reference: NetworkMonitor`.

- [ ] **Step 3: Implement**

```kotlin
package com.zili.android.musicfreeandroid.downloader.io

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class NetworkState { Offline, Wifi, Cellular }

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private val _state = MutableStateFlow(currentState())
    val state: StateFlow<NetworkState> = _state.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { recompute() }
        override fun onLost(network: Network) { recompute() }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { recompute() }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm?.registerNetworkCallback(request, callback)
        recompute()
    }

    fun stop() {
        runCatching { cm?.unregisterNetworkCallback(callback) }
    }

    private fun currentState(): NetworkState {
        val active = cm?.activeNetwork ?: return NetworkState.Offline
        val caps = cm.getNetworkCapabilities(active)
        return mapToNetworkState(caps)
    }

    private fun recompute() {
        _state.value = currentState()
    }

    companion object {
        fun mapToNetworkState(caps: NetworkCapabilities?): NetworkState {
            if (caps == null) return NetworkState.Offline
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NetworkState.Offline
            return when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkState.Wifi
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkState.Wifi
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkState.Cellular
                else -> NetworkState.Offline
            }
        }
    }
}
```

- [ ] **Step 4: Run test**

```bash
./gradlew :downloader:testDebugUnitTest --tests "*NetworkStateMappingTest*" --no-configuration-cache
```

Expected: 4 tests PASS。

- [ ] **Step 5: Commit**

```bash
git add downloader/
git commit -m "feat(downloader): add NetworkMonitor with pure-function state mapping"
```

---

## Task 11: DownloadConfig + plugin facade

**Files:**
- Create: `<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/prefs/DownloadConfig.kt`
- Create: `<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/prefs/DownloadConfigSource.kt`
- Create: `<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/quality/PluginMediaSourceResolver.kt`

> 这个 Task 不引入新行为，只把"DataStore → engine 配置"和"PluginManager 拿插件 → 调 getMediaSource"这两个适配器各包一层，让 engine 测试时可以注入 fake。本 Task 不写测试（纯包装），靠下游 engine 测试覆盖。

- [ ] **Step 1: Create DownloadConfig data class**

```kotlin
package com.zili.android.musicfreeandroid.downloader.prefs

import com.zili.android.musicfreeandroid.core.model.PlayQuality

data class DownloadConfig(
    val maxDownload: Int,
    val useCellularDownload: Boolean,
    val defaultDownloadQuality: PlayQuality,
    val downloadDirRelative: String,
)
```

- [ ] **Step 2: Create DownloadConfigSource (StateFlow facade)**

```kotlin
package com.zili.android.musicfreeandroid.downloader.prefs

import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadConfigSource @Inject constructor(
    private val appPrefs: AppPreferences,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val state: StateFlow<DownloadConfig> = combine(
        appPrefs.maxDownload,
        appPrefs.useCellularDownload,
        appPrefs.defaultDownloadQuality,
        appPrefs.downloadDirRelative,
    ) { maxDl, cellular, quality, dir ->
        DownloadConfig(maxDl, cellular, quality, dir)
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = DownloadConfig(3, false, com.zili.android.musicfreeandroid.core.model.PlayQuality.STANDARD, "Music/MusicFree/"),
    )
}
```

- [ ] **Step 3: Create PluginMediaSourceResolver**

```kotlin
package com.zili.android.musicfreeandroid.downloader.quality

import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginMediaSourceResolver @Inject constructor(
    private val pluginManager: PluginManager,
) {
    /**
     * Returns null if no plugin matches; else delegates to plugin.getMediaSource.
     * Caller should fall back to musicItem.url when this returns null.
     */
    suspend fun resolve(item: MusicItem, qualityWire: String): MediaSourceResult? {
        val plugin = pluginManager.getByName(item.platform) ?: return null
        return runCatching { plugin.getMediaSource(item, qualityWire) }.getOrNull()
    }
}
```

> 假设 `PluginManager` 已有 `getByName(platform: String): LoadedPlugin?`。如名字不同，先 grep `PluginManager` 类定义校对，再调整。

- [ ] **Step 4: Build verify**

```bash
./gradlew :downloader:assembleDebug --no-configuration-cache
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add downloader/
git commit -m "feat(downloader): add DownloadConfig source and plugin resolver facade"
```

---

## Task 12: DownloadEngine — scheduling + success path (TDD core)

**Files:**
- Create: `<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/engine/DownloadEngine.kt`
- Create: `<root>/downloader/src/test/java/com/zili/android/musicfreeandroid/downloader/engine/DownloadEngineSchedulingTest.kt`
- Create: `<root>/downloader/src/test/java/com/zili/android/musicfreeandroid/downloader/engine/FakeHttpDownloader.kt`
- Create: `<root>/downloader/src/test/java/com/zili/android/musicfreeandroid/downloader/engine/FakeMediaStoreWriter.kt`
- Create: `<root>/downloader/src/test/java/com/zili/android/musicfreeandroid/downloader/engine/FakeQualityResolver.kt`

> 这是整个 plan 的核心。Engine 测试用 in-memory Room（Robolectric）+ fake HTTP + fake MediaStore writer + fake plugin resolver。覆盖：① 单首成功，② 并发上限，③ 队列空触发 idle 回调，④ 已下载去重。后续 Task 13/14 加更多场景。

- [ ] **Step 1: Write failing scheduling test**

```kotlin
package com.zili.android.musicfreeandroid.downloader.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.downloader.io.NetworkState
import com.zili.android.musicfreeandroid.downloader.model.DownloadStatus
import com.zili.android.musicfreeandroid.downloader.model.MediaKey
import com.zili.android.musicfreeandroid.downloader.prefs.DownloadConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DownloadEngineSchedulingTest {

    private lateinit var db: AppDatabase
    private lateinit var http: FakeHttpDownloader
    private lateinit var writer: FakeMediaStoreWriter
    private lateinit var resolver: FakeQualityResolver
    private lateinit var configFlow: MutableStateFlow<DownloadConfig>
    private lateinit var network: MutableStateFlow<NetworkState>
    private lateinit var engine: DownloadEngine

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        http = FakeHttpDownloader()
        writer = FakeMediaStoreWriter()
        resolver = FakeQualityResolver()
        configFlow = MutableStateFlow(DownloadConfig(maxDownload = 2, useCellularDownload = false,
            defaultDownloadQuality = PlayQuality.STANDARD, downloadDirRelative = "Music/MusicFree/"))
        network = MutableStateFlow(NetworkState.Wifi)
        engine = DownloadEngine(
            taskDao = db.downloadTaskDao(),
            downloadedDao = db.downloadedTrackDao(),
            http = http,
            writer = writer,
            resolver = resolver::resolve,
            configFlow = configFlow,
            networkFlow = network,
            cacheDir = createTempDir("dlcache"),
        )
        engine.start()
    }

    @After fun teardown() { engine.stop(); db.close() }

    private fun item(id: String, platform: String = "qq") = MusicItem(
        id = id, platform = platform, title = "t-$id", artist = "a",
        album = null, duration = 0L, url = null, artwork = null, qualities = null,
    )

    @Test fun singleEnqueueRunsToCompletion() = runTest {
        resolver.bind(MediaKey.of("1", "qq"), MediaSourceResult(url = "https://x/1.mp3", headers = null, userAgent = null))
        engine.enqueue(listOf(item("1")), PlayQuality.STANDARD)
        advanceUntilIdle()
        // task removed
        assertTrue(db.downloadTaskDao().observeAll().first().isEmpty())
        // downloaded_tracks row present
        assertTrue(db.downloadedTrackDao().exists("1", "qq"))
        // writer.commit called once
        assertEquals(1, writer.commitCount)
    }

    @Test fun concurrencyCapNeverExceeded() = runTest {
        configFlow.value = configFlow.value.copy(maxDownload = 2)
        repeat(5) { i ->
            resolver.bind(MediaKey.of("$i", "qq"), MediaSourceResult(url = "https://x/$i.mp3", headers = null, userAgent = null))
        }
        http.holdNextN(5)            // 让前几个挂起，便于观察并发
        engine.enqueue((0..4).map { item("$it") }, PlayQuality.STANDARD)
        advanceUntilIdle()
        assertTrue("inflight=${http.inflight}", http.inflight <= 2)
        http.releaseAll()
        advanceUntilIdle()
        assertEquals(5, writer.commitCount)
    }

    @Test fun alreadyDownloadedDeduplicated() = runTest {
        // pre-seed downloaded_tracks
        db.downloadedTrackDao().insert(
            com.zili.android.musicfreeandroid.data.db.entity.DownloadedTrackEntity(
                id = "1", platform = "qq",
                mediaStoreUri = "content://media/external/audio/media/1",
                relativePath = "Music/MusicFree/", mimeType = "audio/mpeg",
                quality = "standard", sizeBytes = 1L, downloadedAt = 1L,
            )
        )
        engine.enqueue(listOf(item("1")), PlayQuality.STANDARD)
        advanceUntilIdle()
        assertEquals(0, writer.commitCount)
    }
}
```

需要的 fake 类：

```kotlin
// FakeHttpDownloader.kt
package com.zili.android.musicfreeandroid.downloader.engine

import com.zili.android.musicfreeandroid.downloader.io.HttpDownloadProgress
import com.zili.android.musicfreeandroid.downloader.io.HttpDownloader
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class FakeHttpDownloader : HttpDownloader {
    @Volatile var inflight = 0; private set
    private val maxInflightSeen = AtomicInteger()
    private val gates = ArrayDeque<CompletableDeferred<Unit>>()

    fun holdNextN(n: Int) { repeat(n) { gates += CompletableDeferred() } }
    fun releaseAll() { gates.forEach { it.complete(Unit) }; gates.clear() }

    override suspend fun download(
        url: String,
        headers: Map<String, String>,
        target: File,
        onProgress: (HttpDownloadProgress) -> Unit,
    ) {
        inflight++
        try {
            target.parentFile?.mkdirs()
            target.writeBytes("ok".toByteArray())
            onProgress(HttpDownloadProgress(2, 2))
            gates.removeFirstOrNull()?.await()
        } finally {
            inflight--
        }
    }
}

// FakeMediaStoreWriter.kt
package com.zili.android.musicfreeandroid.downloader.engine

import android.net.Uri
import java.io.File

class FakeMediaStoreWriter {
    var commitCount = 0
    fun asWriter(): suspend (File, String, String, String, Long) -> Uri = { f, name, mime, rel, size ->
        commitCount++
        Uri.parse("content://media/external/audio/media/${f.nameWithoutExtension}")
    }
}

// FakeQualityResolver.kt
package com.zili.android.musicfreeandroid.downloader.engine

import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.downloader.model.MediaKey

class FakeQualityResolver {
    private val table = mutableMapOf<MediaKey, MediaSourceResult?>()
    fun bind(key: MediaKey, result: MediaSourceResult?) { table[key] = result }
    suspend fun resolve(item: MusicItem, qualityWire: String): MediaSourceResult? =
        table[MediaKey.of(item)]
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
./gradlew :downloader:testDebugUnitTest --tests "*DownloadEngineSchedulingTest*" --no-configuration-cache 2>&1 | tail -10
```

Expected: `Unresolved reference: DownloadEngine`.

- [ ] **Step 3: Implement DownloadEngine (success path only — cancel/retry/network in T13/T14)**

```kotlin
package com.zili.android.musicfreeandroid.downloader.engine

import android.net.Uri
import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.data.db.dao.DownloadTaskDao
import com.zili.android.musicfreeandroid.data.db.dao.DownloadedTrackDao
import com.zili.android.musicfreeandroid.data.db.entity.DownloadTaskEntity
import com.zili.android.musicfreeandroid.data.db.entity.DownloadedTrackEntity
import com.zili.android.musicfreeandroid.downloader.io.HttpDownloadException
import com.zili.android.musicfreeandroid.downloader.io.HttpDownloader
import com.zili.android.musicfreeandroid.downloader.io.NetworkState
import com.zili.android.musicfreeandroid.downloader.model.DownloadFailReason
import com.zili.android.musicfreeandroid.downloader.model.DownloadStatus
import com.zili.android.musicfreeandroid.downloader.model.DownloadTaskUi
import com.zili.android.musicfreeandroid.downloader.model.MediaKey
import com.zili.android.musicfreeandroid.downloader.prefs.DownloadConfig
import com.zili.android.musicfreeandroid.downloader.quality.QualityFallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DownloadEngine(
    private val taskDao: DownloadTaskDao,
    private val downloadedDao: DownloadedTrackDao,
    private val http: HttpDownloader,
    private val writer: suspend (cacheFile: File, displayName: String, mime: String, relPath: String, size: Long) -> Uri,
    private val resolver: suspend (MusicItem, qualityWire: String) -> MediaSourceResult?,
    private val configFlow: StateFlow<DownloadConfig>,
    private val networkFlow: StateFlow<NetworkState>,
    private val cacheDir: File,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    private val inflight = ConcurrentHashMap<MediaKey, Job>()
    private val progressCache = ConcurrentHashMap<MediaKey, Pair<Long?, Long?>>()  // (downloaded, total)

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<DownloadEvent> = _events.asSharedFlow()

    val tasks: StateFlow<List<DownloadTaskUi>> = taskDao.observeAll()
        .map { rows -> rows.map { it.toUi(progressCache[MediaKey.of(it.id, it.platform)]) } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val downloadedKeys: StateFlow<Set<MediaKey>> = downloadedDao.observeKeys()
        .map { list -> list.map { keyStr ->
            val (id, plat) = keyStr.split("@", limit = 2).let { it[0] to it[1] }
            MediaKey.of(id, plat)
        }.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    fun start() {
        // hook for restart recovery — implemented in T14
    }

    fun stop() {
        inflight.values.forEach { it.cancel() }
        inflight.clear()
    }

    fun enqueue(items: List<MusicItem>, quality: PlayQuality?) {
        scope.launch {
            val effectiveQuality = quality ?: configFlow.value.defaultDownloadQuality
            val now = System.currentTimeMillis()
            val toAdd = mutableListOf<DownloadTaskEntity>()
            for (item in items) {
                val key = MediaKey.of(item)
                if (downloadedDao.exists(item.id, item.platform)) continue
                if (taskDao.findByKey(item.id, item.platform) != null) continue
                toAdd += DownloadTaskEntity(
                    id = item.id, platform = item.platform, title = item.title, artist = item.artist,
                    album = item.album, artwork = item.artwork, durationMs = item.duration,
                    targetQuality = effectiveQuality.name.lowercase(),
                    status = DownloadStatus.PENDING.name, errorReason = null,
                    resolvedUrl = null, resolvedHeadersJson = null,
                    fileSize = null, downloadedSize = null,
                    createdAt = now, updatedAt = now,
                )
            }
            toAdd.forEach { taskDao.upsert(it) }
            scheduleNext()
        }
    }

    private suspend fun scheduleNext() = mutex.withLock {
        val cap = configFlow.value.maxDownload.coerceIn(1, 10)
        if (inflight.size >= cap) return@withLock
        val net = networkFlow.value
        if (net == NetworkState.Offline) return@withLock
        if (net == NetworkState.Cellular && !configFlow.value.useCellularDownload) return@withLock
        val next = taskDao.findNextPending() ?: return@withLock
        val key = MediaKey.of(next.id, next.platform)
        if (inflight.containsKey(key)) return@withLock
        taskDao.updateStatus(next.id, next.platform, DownloadStatus.PREPARING.name, System.currentTimeMillis())
        val job = scope.launch { runOne(next, key) }
        inflight[key] = job
    }

    private suspend fun runOne(task: DownloadTaskEntity, key: MediaKey) {
        val musicItem = task.toMusicItemSeed()
        val targetQuality = runCatching {
            PlayQuality.valueOf(task.targetQuality.uppercase())
        }.getOrDefault(PlayQuality.STANDARD)

        val resolved = QualityFallback.resolve(musicItem, targetQuality, resolver)
        if (resolved == null) {
            markFailed(key, DownloadFailReason.FailToFetchSource)
            inflight.remove(key)
            scheduleNext()
            return
        }
        val (_, source) = resolved
        taskDao.setResolved(task.id, task.platform, source.url, null, System.currentTimeMillis())
        taskDao.updateStatus(task.id, task.platform, DownloadStatus.DOWNLOADING.name, System.currentTimeMillis())

        val ext = DownloadFilenames.extensionFromUrl(source.url ?: "")
        val mime = DownloadFilenames.mimeFor(ext)
        val displayName = DownloadFilenames.displayName(musicItem, ext)
        val relPath = configFlow.value.downloadDirRelative

        val cacheFile = File(cacheDir, "${UUID.randomUUID()}.$ext").also { it.parentFile?.mkdirs() }
        try {
            http.download(
                url = source.url!!,
                headers = source.headers ?: emptyMap(),
                target = cacheFile,
                onProgress = { p ->
                    progressCache[key] = p.downloaded to p.total
                    scope.launch {
                        taskDao.updateProgress(task.id, task.platform, p.total.takeIf { it > 0 }, p.downloaded, System.currentTimeMillis())
                    }
                },
            )
            val size = cacheFile.length()
            val uri = writer(cacheFile, displayName, mime, relPath, size)
            downloadedDao.insert(
                DownloadedTrackEntity(
                    id = task.id, platform = task.platform,
                    mediaStoreUri = uri.toString(), relativePath = relPath,
                    mimeType = mime, quality = task.targetQuality,
                    sizeBytes = size, downloadedAt = System.currentTimeMillis(),
                ),
            )
            taskDao.deleteByKey(task.id, task.platform)
            progressCache.remove(key)
            _events.tryEmit(DownloadEvent.Completed(key))
        } catch (e: HttpDownloadException) {
            markFailed(key, DownloadFailReason.Unknown)
        } catch (t: Throwable) {
            if (cacheFile.exists()) cacheFile.delete()
            markFailed(key, DownloadFailReason.Unknown)
        } finally {
            if (cacheFile.exists()) cacheFile.delete()
            inflight.remove(key)
            scheduleNext()
            if (inflight.isEmpty() && taskDao.findNextPending() == null) {
                _events.tryEmit(DownloadEvent.QueueIdle)
            }
        }
    }

    private suspend fun markFailed(key: MediaKey, reason: DownloadFailReason) {
        taskDao.markFailed(
            id = key.id, platform = key.platform,
            reason = reason.name, now = System.currentTimeMillis(),
        )
        progressCache.remove(key)
    }

    fun cancel(key: MediaKey) {
        scope.launch {
            inflight[key]?.cancel()
            inflight.remove(key)
            taskDao.deleteByKey(key.id, key.platform)
            progressCache.remove(key)
            scheduleNext()
        }
    }

    fun retry(key: MediaKey) {
        scope.launch {
            val row = taskDao.findByKey(key.id, key.platform) ?: return@launch
            if (row.status != DownloadStatus.FAILED.name) return@launch
            taskDao.updateStatus(key.id, key.platform, DownloadStatus.PENDING.name, System.currentTimeMillis())
            scheduleNext()
        }
    }

    fun clearFailed() {
        scope.launch { taskDao.deleteAllFailed() }
    }

    fun retryAllFailed() {
        scope.launch {
            taskDao.resetAllFailedToPending()
            scheduleNext()
        }
    }

    fun cancelAllInflight() {
        scope.launch {
            inflight.values.forEach { it.cancel() }
            inflight.clear()
            taskDao.deleteAllInflight()
        }
    }
}

private fun DownloadTaskEntity.toUi(progress: Pair<Long?, Long?>?): DownloadTaskUi = DownloadTaskUi(
    key = MediaKey.of(id, platform),
    title = title, artist = artist, artwork = artwork,
    status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.FAILED),
    targetQuality = targetQuality,
    downloadedBytes = progress?.first ?: downloadedSize,
    totalBytes = progress?.second ?: fileSize,
    errorReason = errorReason?.let { runCatching { DownloadFailReason.valueOf(it) }.getOrNull() },
)

private fun DownloadTaskEntity.toMusicItemSeed(): MusicItem = MusicItem(
    id = id, platform = platform, title = title, artist = artist,
    album = album, duration = durationMs, url = null, artwork = artwork, qualities = null,
)

sealed interface DownloadEvent {
    data class Completed(val key: MediaKey) : DownloadEvent
    data object QueueIdle : DownloadEvent
    data class Toast(val reason: DownloadFailReason) : DownloadEvent
}
```

> 注：测试里 engine 的 `writer` 参数是函数式签名，与上面 `FakeMediaStoreWriter.asWriter()` 对齐。生产代码里在 Hilt 模块（Task 17）把 `MediaStoreMusicWriter::commit` 适配进来。

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :downloader:testDebugUnitTest --tests "*DownloadEngineSchedulingTest*" --no-configuration-cache
```

Expected: 3 tests PASS。

- [ ] **Step 5: Commit**

```bash
git add downloader/
git commit -m "feat(downloader): add DownloadEngine with scheduling and success path"
```

---

## Task 13: DownloadEngine — quality fallback + failure paths

**Files:**
- Create: `<root>/downloader/src/test/java/com/zili/android/musicfreeandroid/downloader/engine/DownloadEngineFailurePathsTest.kt`

> Engine 实现已在 T12 完成；本 Task 仅扩充测试覆盖。如发现实现 bug，再回到 T12 文件修。

- [ ] **Step 1: Add tests for failure / fallback paths**

```kotlin
package com.zili.android.musicfreeandroid.downloader.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.downloader.io.NetworkState
import com.zili.android.musicfreeandroid.downloader.model.DownloadFailReason
import com.zili.android.musicfreeandroid.downloader.model.DownloadStatus
import com.zili.android.musicfreeandroid.downloader.model.MediaKey
import com.zili.android.musicfreeandroid.downloader.prefs.DownloadConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DownloadEngineFailurePathsTest {

    private lateinit var db: AppDatabase
    private lateinit var http: FakeHttpDownloader
    private lateinit var writer: FakeMediaStoreWriter
    private lateinit var resolver: FakeQualityResolver
    private lateinit var configFlow: MutableStateFlow<DownloadConfig>
    private lateinit var network: MutableStateFlow<NetworkState>
    private lateinit var engine: DownloadEngine

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        http = FakeHttpDownloader()
        writer = FakeMediaStoreWriter()
        resolver = FakeQualityResolver()
        configFlow = MutableStateFlow(DownloadConfig(2, false, PlayQuality.STANDARD, "Music/MusicFree/"))
        network = MutableStateFlow(NetworkState.Wifi)
        engine = DownloadEngine(
            taskDao = db.downloadTaskDao(),
            downloadedDao = db.downloadedTrackDao(),
            http = http, writer = writer.asWriter(), resolver = resolver::resolve,
            configFlow = configFlow, networkFlow = network, cacheDir = createTempDir("dlcache"),
        )
        engine.start()
    }

    @After fun teardown() { engine.stop(); db.close() }

    private fun item(id: String) = MusicItem(
        id = id, platform = "qq", title = "t", artist = "a",
        album = null, duration = 0L, url = null, artwork = null, qualities = null,
    )

    @Test fun allQualitiesReturnNullProducesFailToFetchSource() = runTest {
        // resolver has no binding → returns null
        engine.enqueue(listOf(item("1")), PlayQuality.SUPER)
        advanceUntilIdle()
        val rows = db.downloadTaskDao().observeAll().first()
        assertEquals(1, rows.size)
        assertEquals(DownloadStatus.FAILED.name, rows[0].status)
        assertEquals(DownloadFailReason.FailToFetchSource.name, rows[0].errorReason)
    }

    @Test fun httpFailureMarksFailedAndCacheFileCleaned() = runTest {
        resolver.bind(MediaKey.of("1", "qq"), MediaSourceResult(url = "https://x/song.mp3", headers = null, userAgent = null))
        http.failNext()
        engine.enqueue(listOf(item("1")), PlayQuality.STANDARD)
        advanceUntilIdle()
        val rows = db.downloadTaskDao().observeAll().first()
        assertEquals(DownloadStatus.FAILED.name, rows.single().status)
        assertEquals(DownloadFailReason.Unknown.name, rows.single().errorReason)
    }
}
```

补 `FakeHttpDownloader.failNext()`：

```kotlin
class FakeHttpDownloader : HttpDownloader {
    @Volatile var inflight = 0; private set
    private val gates = ArrayDeque<CompletableDeferred<Unit>>()
    private var pendingFailure = false

    fun holdNextN(n: Int) { repeat(n) { gates += CompletableDeferred() } }
    fun releaseAll() { gates.forEach { it.complete(Unit) }; gates.clear() }
    fun failNext() { pendingFailure = true }

    override suspend fun download(
        url: String,
        headers: Map<String, String>,
        target: File,
        onProgress: (HttpDownloadProgress) -> Unit,
    ) {
        inflight++
        try {
            if (pendingFailure) { pendingFailure = false; throw HttpDownloadException("simulated") }
            target.parentFile?.mkdirs()
            target.writeBytes("ok".toByteArray())
            onProgress(HttpDownloadProgress(2, 2))
            gates.removeFirstOrNull()?.await()
        } finally {
            inflight--
        }
    }
}
```

- [ ] **Step 2: Run tests**

```bash
./gradlew :downloader:testDebugUnitTest --tests "*DownloadEngineFailurePathsTest*" --no-configuration-cache
```

Expected: 2 tests PASS。

- [ ] **Step 3: Commit**

```bash
git add downloader/
git commit -m "test(downloader): cover engine failure and quality fallback paths"
```

---

## Task 14: DownloadEngine — cancel/retry/clear + restart recovery

**Files:**
- Modify: `<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/engine/DownloadEngine.kt`（在 `start()` 内补 restart recovery）
- Create: `<root>/downloader/src/test/java/com/zili/android/musicfreeandroid/downloader/engine/DownloadEngineCancelRetryTest.kt`
- Create: `<root>/downloader/src/test/java/com/zili/android/musicfreeandroid/downloader/engine/DownloadEngineRecoveryTest.kt`

- [ ] **Step 1: Modify DownloadEngine.start() to do recovery**

将 T12 的 `fun start() { /* hook */ }` 改为：

```kotlin
    fun start() {
        scope.launch {
            // Restart recovery: any PREPARING/DOWNLOADING become PENDING
            taskDao.resetInflightToPending()
            // Wipe stale cache files
            runCatching { cacheDir.listFiles()?.forEach { it.delete() } }
            // Watch network/config: any change retriggers scheduling
            combine(networkFlow, configFlow) { net, _ -> net }
                .onEach { net ->
                    if (net == NetworkState.Cellular && !configFlow.value.useCellularDownload) {
                        // mid-download cellular switch → cancel inflight, push them back to PENDING
                        inflight.values.forEach { it.cancel() }
                        inflight.clear()
                        taskDao.observeAll().first().filter {
                            it.status == DownloadStatus.PREPARING.name || it.status == DownloadStatus.DOWNLOADING.name
                        }.forEach {
                            taskDao.updateStatus(it.id, it.platform, DownloadStatus.PENDING.name, System.currentTimeMillis())
                        }
                    } else {
                        scheduleNext()
                    }
                }
                .launchIn(scope)
        }
    }
```

> 此处用到 `kotlinx.coroutines.flow.first` —— 在文件顶部 import 区追加：`import kotlinx.coroutines.flow.first`。

- [ ] **Step 2: Cancel/retry test**

```kotlin
package com.zili.android.musicfreeandroid.downloader.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zili.android.musicfreeandroid.core.model.MediaSourceResult
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.downloader.io.NetworkState
import com.zili.android.musicfreeandroid.downloader.model.DownloadFailReason
import com.zili.android.musicfreeandroid.downloader.model.DownloadStatus
import com.zili.android.musicfreeandroid.downloader.model.MediaKey
import com.zili.android.musicfreeandroid.downloader.prefs.DownloadConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DownloadEngineCancelRetryTest {

    private lateinit var db: AppDatabase
    private lateinit var engine: DownloadEngine
    private lateinit var http: FakeHttpDownloader
    private lateinit var writer: FakeMediaStoreWriter
    private lateinit var resolver: FakeQualityResolver

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        http = FakeHttpDownloader(); writer = FakeMediaStoreWriter(); resolver = FakeQualityResolver()
        engine = DownloadEngine(
            taskDao = db.downloadTaskDao(), downloadedDao = db.downloadedTrackDao(),
            http = http, writer = writer.asWriter(), resolver = resolver::resolve,
            configFlow = MutableStateFlow(DownloadConfig(1, false, PlayQuality.STANDARD, "Music/MusicFree/")),
            networkFlow = MutableStateFlow(NetworkState.Wifi),
            cacheDir = createTempDir("dlcache"),
        )
        engine.start()
    }

    @After fun teardown() { engine.stop(); db.close() }

    private fun item(id: String) = MusicItem(
        id = id, platform = "qq", title = "t", artist = "a",
        album = null, duration = 0L, url = null, artwork = null, qualities = null,
    )

    @Test fun cancelInflightRemovesRowAndStopsHttp() = runTest {
        resolver.bind(MediaKey.of("1", "qq"), MediaSourceResult(url = "https://x/1.mp3", headers = null, userAgent = null))
        http.holdNextN(1)
        engine.enqueue(listOf(item("1")), PlayQuality.STANDARD)
        advanceUntilIdle()
        engine.cancel(MediaKey.of("1", "qq"))
        advanceUntilIdle()
        assertTrue(db.downloadTaskDao().observeAll().first().isEmpty())
    }

    @Test fun retryFailedResetsToPendingAndRunsAgain() = runTest {
        resolver.bind(MediaKey.of("1", "qq"), MediaSourceResult(url = "https://x/1.mp3", headers = null, userAgent = null))
        http.failNext()
        engine.enqueue(listOf(item("1")), PlayQuality.STANDARD)
        advanceUntilIdle()
        assertEquals(DownloadStatus.FAILED.name, db.downloadTaskDao().observeAll().first().single().status)
        engine.retry(MediaKey.of("1", "qq"))
        advanceUntilIdle()
        // Now should succeed: row removed, downloaded_tracks present
        assertTrue(db.downloadedTrackDao().exists("1", "qq"))
    }

    @Test fun clearFailedDeletesOnlyFailedRows() = runTest {
        // pre-seed via enqueue then force-fail
        resolver.bind(MediaKey.of("1", "qq"), null)
        engine.enqueue(listOf(item("1")), PlayQuality.LOW)
        advanceUntilIdle()
        assertEquals(DownloadStatus.FAILED.name, db.downloadTaskDao().observeAll().first().single().status)
        engine.clearFailed()
        advanceUntilIdle()
        assertTrue(db.downloadTaskDao().observeAll().first().isEmpty())
    }
}
```

- [ ] **Step 3: Restart recovery test**

```kotlin
package com.zili.android.musicfreeandroid.downloader.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.entity.DownloadTaskEntity
import com.zili.android.musicfreeandroid.downloader.io.NetworkState
import com.zili.android.musicfreeandroid.downloader.model.DownloadStatus
import com.zili.android.musicfreeandroid.downloader.prefs.DownloadConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DownloadEngineRecoveryTest {

    private lateinit var db: AppDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun teardown() { db.close() }

    @Test fun startResetsInflightToPending() = runTest {
        val now = System.currentTimeMillis()
        db.downloadTaskDao().upsert(DownloadTaskEntity(
            id = "p", platform = "qq", title = "t", artist = "a", album = null, artwork = null,
            durationMs = 0L, targetQuality = "standard", status = "PREPARING",
            errorReason = null, resolvedUrl = "https://x/p.mp3", resolvedHeadersJson = null,
            fileSize = null, downloadedSize = null, createdAt = now, updatedAt = now,
        ))
        db.downloadTaskDao().upsert(DownloadTaskEntity(
            id = "d", platform = "qq", title = "t", artist = "a", album = null, artwork = null,
            durationMs = 0L, targetQuality = "standard", status = "DOWNLOADING",
            errorReason = null, resolvedUrl = "https://x/d.mp3", resolvedHeadersJson = null,
            fileSize = 100, downloadedSize = 50, createdAt = now, updatedAt = now,
        ))
        val engine = DownloadEngine(
            taskDao = db.downloadTaskDao(), downloadedDao = db.downloadedTrackDao(),
            http = FakeHttpDownloader(), writer = FakeMediaStoreWriter().asWriter(),
            resolver = FakeQualityResolver()::resolve,
            configFlow = MutableStateFlow(DownloadConfig(0, false, PlayQuality.STANDARD, "Music/MusicFree/")),
            // configFlow.maxDownload = 0 → scheduleNext immediately bails, isolating recovery
            networkFlow = MutableStateFlow(NetworkState.Wifi),
            cacheDir = createTempDir("dlrec"),
        )
        engine.start()
        advanceUntilIdle()
        val rows = db.downloadTaskDao().observeAll().first().associateBy { it.id }
        assertEquals(DownloadStatus.PENDING.name, rows["p"]!!.status)
        assertEquals(DownloadStatus.PENDING.name, rows["d"]!!.status)
        assertEquals(null, rows["p"]!!.resolvedUrl)
        assertEquals(null, rows["d"]!!.resolvedUrl)
        engine.stop()
    }
}
```

> `maxDownload = 0` 让 `scheduleNext` 在 cap 检查处提前返回（注意：`coerceIn(1, 10)` 会把 0 抬到 1，所以这条思路不严格生效）。改用：在 `setup()` 里**不**调用 `engine.start()` 之前先 stop？不行——recovery 在 start 内。最朴素方案是把 `networkFlow` 设为 `NetworkState.Offline`，让调度循环在网络判断处停掉。

修正：把 test 里 `networkFlow = MutableStateFlow(NetworkState.Wifi)` 改为 `NetworkState.Offline`：

```kotlin
            networkFlow = MutableStateFlow(NetworkState.Offline),
```

并删掉 `configFlow.maxDownload = 0` 的注释。

- [ ] **Step 4: Run tests**

```bash
./gradlew :downloader:testDebugUnitTest --tests "*CancelRetry*" --tests "*Recovery*" --no-configuration-cache
```

Expected: 4 tests PASS。

- [ ] **Step 5: Commit**

```bash
git add downloader/
git commit -m "feat(downloader): add cancel/retry semantics and restart recovery"
```

---

## Task 15: DownloadNotifier + DownloadService

**Files:**
- Create: `<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/service/DownloadNotifier.kt`
- Create: `<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/service/DownloadService.kt`
- Modify: `<root>/downloader/src/main/AndroidManifest.xml`
- Modify: `<root>/app/src/main/AndroidManifest.xml`（追加 `POST_NOTIFICATIONS` 权限声明）

> Service + Notifier 走 Robolectric 单测略复杂；本 Task 只做编译+人工运行态验收，不写自动化单测。下游 E2E（T22）会真实跑一次。

- [ ] **Step 1: Implement Notifier**

```kotlin
package com.zili.android.musicfreeandroid.downloader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.zili.android.musicfreeandroid.downloader.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "download_progress"
        const val NOTIF_ID = 0xD107L.toInt()
        const val ACTION_CANCEL_ALL = "com.zili.android.musicfreeandroid.downloader.CANCEL_ALL"
        const val ACTION_OPEN = "com.zili.android.musicfreeandroid.downloader.OPEN"
    }

    init { ensureChannel() }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "下载进度", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
    }

    fun buildOngoing(active: Int, total: Int, completed: Int, percent: Int): Notification {
        val openIntent = Intent(ACTION_OPEN).setPackage(context.packageName)
        val cancelIntent = Intent(ACTION_CANCEL_ALL).setPackage(context.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("MusicFree")
            .setContentText("正在下载 $active 首歌（$completed/$total 完成）")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "取消所有", PendingIntent.getBroadcast(context, 1, cancelIntent, flags))
            .addAction(0, "打开", PendingIntent.getBroadcast(context, 2, openIntent, flags))
            .build()
    }
}
```

- [ ] **Step 2: Implement Service**

```kotlin
package com.zili.android.musicfreeandroid.downloader.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import com.zili.android.musicfreeandroid.downloader.engine.DownloadEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var engine: DownloadEngine
    @Inject lateinit var notifier: DownloadNotifier

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == DownloadNotifier.ACTION_CANCEL_ALL) {
                engine.cancelAllInflight()
                stopSelfSafely()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        engine.start()
        registerReceiver(cancelReceiver, IntentFilter(DownloadNotifier.ACTION_CANCEL_ALL),
            Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(DownloadNotifier.NOTIF_ID, notifier.buildOngoing(0, 0, 0, 0))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(cancelReceiver) }
        engine.stop()
        super.onDestroy()
    }

    private fun stopSelfSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }
}
```

- [ ] **Step 3: Manifest entries**

`<root>/downloader/src/main/AndroidManifest.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

    <application>
        <service
            android:name=".service.DownloadService"
            android:exported="false"
            android:foregroundServiceType="dataSync" />
    </application>
</manifest>
```

`<root>/app/src/main/AndroidManifest.xml` 顶部权限块追加：

```xml
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

- [ ] **Step 4: Build verify**

```bash
./gradlew :downloader:assembleDebug :app:assembleDebug --no-configuration-cache
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add downloader/ app/src/main/AndroidManifest.xml
git commit -m "feat(downloader): add DownloadNotifier and DownloadService"
```

---

## Task 16: Hilt wiring + Downloader interface impl

**Files:**
- Create: `<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/Downloader.kt`
- Create: `<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/DownloaderImpl.kt`
- Create: `<root>/downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/di/DownloaderModule.kt`

- [ ] **Step 1: Define public interface**

```kotlin
package com.zili.android.musicfreeandroid.downloader

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.downloader.engine.DownloadEvent
import com.zili.android.musicfreeandroid.downloader.model.DownloadTaskUi
import com.zili.android.musicfreeandroid.downloader.model.MediaKey
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface Downloader {
    val tasks: StateFlow<List<DownloadTaskUi>>
    val downloadedKeys: StateFlow<Set<MediaKey>>
    val events: SharedFlow<DownloadEvent>

    fun enqueue(items: List<MusicItem>, quality: PlayQuality? = null)
    fun cancel(key: MediaKey)
    fun cancelAllInflight()
    fun retry(key: MediaKey)
    fun retryAllFailed()
    fun clearFailed()
}
```

- [ ] **Step 2: Implementation**

```kotlin
package com.zili.android.musicfreeandroid.downloader

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.downloader.engine.DownloadEngine
import com.zili.android.musicfreeandroid.downloader.engine.DownloadEvent
import com.zili.android.musicfreeandroid.downloader.model.DownloadTaskUi
import com.zili.android.musicfreeandroid.downloader.model.MediaKey
import com.zili.android.musicfreeandroid.downloader.service.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloaderImpl @Inject constructor(
    private val engine: DownloadEngine,
    @ApplicationContext private val context: Context,
) : Downloader {

    init { engine.start() }

    override val tasks: StateFlow<List<DownloadTaskUi>> get() = engine.tasks
    override val downloadedKeys: StateFlow<Set<MediaKey>> get() = engine.downloadedKeys
    override val events: SharedFlow<DownloadEvent> get() = engine.events

    override fun enqueue(items: List<MusicItem>, quality: PlayQuality?) {
        engine.enqueue(items, quality)
        startServiceIfNeeded()
    }

    override fun cancel(key: MediaKey) = engine.cancel(key)
    override fun cancelAllInflight() = engine.cancelAllInflight()
    override fun retry(key: MediaKey) { engine.retry(key); startServiceIfNeeded() }
    override fun retryAllFailed() { engine.retryAllFailed(); startServiceIfNeeded() }
    override fun clearFailed() = engine.clearFailed()

    private fun startServiceIfNeeded() {
        val intent = Intent(context, DownloadService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }
}
```

- [ ] **Step 3: Hilt module**

```kotlin
package com.zili.android.musicfreeandroid.downloader.di

import android.content.Context
import com.zili.android.musicfreeandroid.data.db.dao.DownloadTaskDao
import com.zili.android.musicfreeandroid.data.db.dao.DownloadedTrackDao
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.downloader.DownloaderImpl
import com.zili.android.musicfreeandroid.downloader.engine.DownloadEngine
import com.zili.android.musicfreeandroid.downloader.engine.MediaStoreMusicWriter
import com.zili.android.musicfreeandroid.downloader.io.HttpDownloader
import com.zili.android.musicfreeandroid.downloader.io.NetworkMonitor
import com.zili.android.musicfreeandroid.downloader.io.NetworkState
import com.zili.android.musicfreeandroid.downloader.io.OkHttpDownloader
import com.zili.android.musicfreeandroid.downloader.prefs.DownloadConfig
import com.zili.android.musicfreeandroid.downloader.prefs.DownloadConfigSource
import com.zili.android.musicfreeandroid.downloader.quality.PluginMediaSourceResolver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import javax.inject.Singleton
import java.io.File

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloaderBindingsModule {
    @Binds @Singleton
    abstract fun bindDownloader(impl: DownloaderImpl): Downloader

    @Binds @Singleton
    abstract fun bindHttp(impl: OkHttpDownloader): HttpDownloader
}

@Module
@InstallIn(SingletonComponent::class)
object DownloaderProvidersModule {

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides @Singleton
    fun provideConfigFlow(source: DownloadConfigSource): StateFlow<DownloadConfig> = source.state

    @Provides @Singleton
    fun provideNetworkFlow(monitor: NetworkMonitor): StateFlow<NetworkState> {
        monitor.start()
        return monitor.state
    }

    @Provides @Singleton
    fun provideEngine(
        taskDao: DownloadTaskDao,
        downloadedDao: DownloadedTrackDao,
        http: HttpDownloader,
        writer: MediaStoreMusicWriter,
        resolver: PluginMediaSourceResolver,
        configFlow: StateFlow<DownloadConfig>,
        networkFlow: StateFlow<NetworkState>,
        @ApplicationContext context: Context,
    ): DownloadEngine = DownloadEngine(
        taskDao = taskDao,
        downloadedDao = downloadedDao,
        http = http,
        writer = { f, name, mime, rel, size -> writer.commit(f, name, mime, rel, size) },
        resolver = resolver::resolve,
        configFlow = configFlow,
        networkFlow = networkFlow,
        cacheDir = File(context.cacheDir, "download").apply { mkdirs() },
    )
}
```

- [ ] **Step 4: Build verify**

```bash
./gradlew :app:assembleDebug --no-configuration-cache
```

Expected: BUILD SUCCESSFUL。Hilt KSP 不报错。

- [ ] **Step 5: Commit**

```bash
git add downloader/
git commit -m "feat(downloader): add Downloader interface, impl, and Hilt module"
```

---

## Task 17: DownloadingRoute + DownloadQualityDialog

**Files:**
- Modify: `<root>/core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt`（追加 `DownloadingRoute`）
- Create: `<root>/core/src/main/java/com/zili/android/musicfreeandroid/core/ui/DownloadQualityDialog.kt`

- [ ] **Step 1: Add Route**

在 `Routes.kt` 末尾追加：

```kotlin
@Serializable
data object DownloadingRoute
```

- [ ] **Step 2: DownloadQualityDialog (Compose)**

```kotlin
package com.zili.android.musicfreeandroid.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.model.PlayQuality

@Composable
fun DownloadQualityDialog(
    initial: PlayQuality,
    onDismiss: () -> Unit,
    onConfirm: (PlayQuality) -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("下载音质") },
        text = {
            Column(Modifier.selectableGroup()) {
                listOf(PlayQuality.LOW to "低品", PlayQuality.STANDARD to "标准", PlayQuality.HIGH to "高品", PlayQuality.SUPER to "超品").forEach { (q, label) ->
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.selectable(
                            selected = selected == q,
                            role = Role.RadioButton,
                            onClick = { selected = q },
                        ).padding(8.dp),
                    ) {
                        RadioButton(selected = selected == q, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("下载") } },
    )
}
```

- [ ] **Step 3: Build verify**

```bash
./gradlew :core:assembleDebug --no-configuration-cache
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add core/
git commit -m "feat(core): add DownloadingRoute and DownloadQualityDialog"
```

---

## Task 18: MusicItemOptionsSheet (global long-press menu)

**Files:**
- Create: `<root>/core/src/main/java/com/zili/android/musicfreeandroid/core/ui/MusicItemOptionsSheet.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.zili.android.musicfreeandroid.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.model.MusicItem

@Composable
fun MusicItemOptionsSheet(
    item: MusicItem,
    onDismiss: () -> Unit,
    onDownload: (item: MusicItem) -> Unit,
) {
    val state = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(
                text = "${item.title} - ${item.artist}",
                modifier = Modifier.padding(16.dp),
            )
            ListItem(
                headlineContent = { Text("下载") },
                leadingContent = { Icon(Icons.Filled.Download, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickableNoIndicationOrSomething { onDownload(item) },
            )
        }
    }
}

// Tiny helper to keep ListItem clickable without changing M3 default theming
@Composable
private fun Modifier.clickableNoIndicationOrSomething(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable { onClick() }.let { it })
```

> 上面的 `Modifier.clickableNoIndicationOrSomething` 写法是占位 — 用现成的 `Modifier.clickable` 即可。修正实现：

```kotlin
            ListItem(
                headlineContent = { Text("下载") },
                leadingContent = { Icon(Icons.Filled.Download, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .androidx.compose.foundation.clickable { onDownload(item) },
            )
```

最终干净版（用这个）：

```kotlin
package com.zili.android.musicfreeandroid.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.model.MusicItem

@Composable
fun MusicItemOptionsSheet(
    item: MusicItem,
    onDismiss: () -> Unit,
    onDownload: (item: MusicItem) -> Unit,
) {
    val state = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(
                text = "${item.title} - ${item.artist}",
                modifier = Modifier.padding(16.dp),
            )
            ListItem(
                headlineContent = { Text("下载") },
                leadingContent = { Icon(Icons.Filled.Download, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDownload(item); onDismiss() },
            )
        }
    }
}
```

- [ ] **Step 2: Build verify**

```bash
./gradlew :core:assembleDebug --no-configuration-cache
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add core/
git commit -m "feat(core): add MusicItemOptionsSheet (long-press menu)"
```

---

## Task 19: DownloadingViewModel + DownloadingScreen

**Files:**
- Create: `<root>/feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/downloading/DownloadingViewModel.kt`
- Create: `<root>/feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/downloading/DownloadingScreen.kt`
- Create: `<root>/feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/downloading/navigation/DownloadingNavigation.kt`
- Modify: `<root>/feature/home/build.gradle.kts`（追加 `implementation(project(":downloader"))`）

- [ ] **Step 1: Add :downloader dep**

在 `<root>/feature/home/build.gradle.kts` 的 `dependencies { ... }` 块内追加：

```kotlin
    implementation(project(":downloader"))
```

- [ ] **Step 2: ViewModel**

```kotlin
package com.zili.android.musicfreeandroid.feature.home.downloading

import androidx.lifecycle.ViewModel
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.downloader.model.DownloadStatus
import com.zili.android.musicfreeandroid.downloader.model.DownloadTaskUi
import com.zili.android.musicfreeandroid.downloader.model.MediaKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

data class DownloadingUiState(
    val active: List<DownloadTaskUi>,
    val failed: List<DownloadTaskUi>,
)

@HiltViewModel
class DownloadingViewModel @Inject constructor(
    private val downloader: Downloader,
) : ViewModel() {
    val state: StateFlow<DownloadingUiState> = downloader.tasks
        .map { tasks ->
            DownloadingUiState(
                active = tasks.filter { it.status != DownloadStatus.FAILED },
                failed = tasks.filter { it.status == DownloadStatus.FAILED },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DownloadingUiState(emptyList(), emptyList()))

    fun cancel(key: MediaKey) = downloader.cancel(key)
    fun retry(key: MediaKey) = downloader.retry(key)
    fun retryAllFailed() = downloader.retryAllFailed()
    fun clearFailed() = downloader.clearFailed()
    fun cancelAllInflight() = downloader.cancelAllInflight()
}
```

- [ ] **Step 3: Screen**

```kotlin
package com.zili.android.musicfreeandroid.feature.home.downloading

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.downloader.model.DownloadFailReason
import com.zili.android.musicfreeandroid.downloader.model.DownloadStatus
import com.zili.android.musicfreeandroid.downloader.model.DownloadTaskUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadingScreen(
    onBack: () -> Unit,
    viewModel: DownloadingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("下载") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("全部重试失败项") },
                            onClick = { viewModel.retryAllFailed(); menuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text("清空失败项") },
                            onClick = { viewModel.clearFailed(); menuOpen = false },
                        )
                        DropdownMenuItem(
                            text = { Text("取消所有进行中") },
                            onClick = { viewModel.cancelAllInflight(); menuOpen = false },
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.active.isEmpty() && state.failed.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无下载任务")
                    Text("在歌曲长按菜单或歌曲详情页可触发下载",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                if (state.active.isNotEmpty()) {
                    item { SectionHeader("进行中（${state.active.size}）") }
                    items(state.active, key = { "active-${it.key.value}" }) { task ->
                        ActiveRow(task, onCancel = { viewModel.cancel(task.key) })
                    }
                }
                if (state.failed.isNotEmpty()) {
                    item { SectionHeader("失败（${state.failed.size}）") }
                    items(state.failed, key = { "failed-${it.key.value}" }) { task ->
                        FailedRow(task, onRetry = { viewModel.retry(task.key) })
                    }
                }
            }
        }
    }
}

@Composable private fun SectionHeader(text: String) {
    Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
}

@Composable private fun ActiveRow(task: DownloadTaskUi, onCancel: () -> Unit) {
    val pct = task.totalBytes?.takeIf { it > 0 }?.let {
        ((task.downloadedBytes ?: 0L) * 100 / it).toInt()
    } ?: 0
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    when (task.status) {
                        DownloadStatus.PENDING -> "等待中"
                        DownloadStatus.PREPARING -> "准备中"
                        DownloadStatus.DOWNLOADING -> "下载中  ${formatSize(task.downloadedBytes)} / ${formatSize(task.totalBytes)}"
                        DownloadStatus.FAILED -> "失败"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "取消") }
        }
        if (task.status == DownloadStatus.DOWNLOADING) {
            LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

@Composable private fun FailedRow(task: DownloadTaskUi, onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                when (task.errorReason) {
                    DownloadFailReason.FailToFetchSource -> "下载失败：无法获取源"
                    DownloadFailReason.NoWritePermission -> "下载失败：没有写入权限"
                    DownloadFailReason.NotAllowToDownloadInCellular -> "已暂停：未允许移动网络下载"
                    DownloadFailReason.NetworkOffline -> "无网络"
                    else -> "下载失败：未知原因"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onRetry) { Icon(Icons.Filled.Refresh, contentDescription = "重试") }
    }
}

private fun formatSize(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "-"
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1fKB", kb)
    val mb = kb / 1024.0
    return String.format("%.1fMB", mb)
}
```

- [ ] **Step 4: Navigation extension**

```kotlin
package com.zili.android.musicfreeandroid.feature.home.downloading.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.DownloadingRoute
import com.zili.android.musicfreeandroid.feature.home.downloading.DownloadingScreen

fun NavGraphBuilder.downloadingScreen(onBack: () -> Unit) {
    composable<DownloadingRoute> {
        DownloadingScreen(onBack = onBack)
    }
}
```

- [ ] **Step 5: Build verify**

```bash
./gradlew :feature:home:assembleDebug --no-configuration-cache
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: Commit**

```bash
git add feature/home/
git commit -m "feat(home): add DownloadingScreen and ViewModel"
```

---

## Task 20: Wire Downloading into AppNavHost + LocalScreen entry/badge

**Files:**
- Modify: `<root>/app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`
- Modify: `<root>/feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalScreen.kt`
- Modify: `<root>/feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalMusicContent.kt`（叠加"已下载"角标）

- [ ] **Step 1: Add Downloading to AppNavHost**

在 import 区追加：

```kotlin
import com.zili.android.musicfreeandroid.core.navigation.DownloadingRoute
import com.zili.android.musicfreeandroid.feature.home.downloading.navigation.downloadingScreen
```

在 NavHost 内（`localScreen { ... }` 之后）追加：

```kotlin
        downloadingScreen(
            onBack = { navController.popBackStack() },
        )
```

并在 `localScreen(...)` 调用中，传入新参数 `onNavigateToDownloading`（下一步会改 `localScreen` 签名加这个参数）：

```kotlin
        localScreen(
            onNavigateToPlayer = { navController.navigate(PlayerRoute) },
            onNavigateToDownloading = { navController.navigate(DownloadingRoute) },
        )
```

- [ ] **Step 2: Modify LocalScreen to inject downloader, expose AppBar entry, pass to content**

将 `LocalScreen` 签名扩展（保持向后兼容则不必，因为只有一个调用点）：

```kotlin
@Composable
fun LocalScreen(
    onNavigateToPlayer: () -> Unit,
    onNavigateToDownloading: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    downloader: com.zili.android.musicfreeandroid.downloader.Downloader = hiltViewModel<com.zili.android.musicfreeandroid.feature.home.downloading.DownloadingViewModel>().run {
        // We borrow the VM only to read tasks; using DI directly is cleaner — see note below.
        TODO("Inject Downloader directly via Hilt EntryPoint or expose via existing VM")
    },
) {
    // ...
}
```

> 上面这段 TODO 不要原样保留。两种现实做法二选一：
>
> **A.** 让 `HomeViewModel` 暴露一个 `downloadActiveCount: StateFlow<Int>` 和一个 `downloadedKeys: StateFlow<Set<MediaKey>>`，VM 内部 `@Inject lateinit var downloader: Downloader`（更现成的方式：构造注入）。LocalScreen 直接 collect VM 的两个 StateFlow。
>
> **B.** 用 Hilt EntryPoint 在 Composable 内直接拿 `Downloader`。推荐 A（VM 持有，符合既有架构）。

按 A 方案修：

修改 `<root>/feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeViewModel.kt`，注入 `Downloader` 并暴露：

```kotlin
    @Inject lateinit var downloader: com.zili.android.musicfreeandroid.downloader.Downloader  // 改成构造注入

    // 在已有 @HiltViewModel constructor 中追加 downloader: Downloader 参数；将 lateinit var 删掉
```

然后在类体内：

```kotlin
    val downloadActiveCount: StateFlow<Int> = downloader.tasks
        .map { tasks -> tasks.count { it.status != com.zili.android.musicfreeandroid.downloader.model.DownloadStatus.FAILED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val downloadedKeys: StateFlow<Set<com.zili.android.musicfreeandroid.downloader.model.MediaKey>> =
        downloader.downloadedKeys
```

`LocalScreen` 修订：

```kotlin
@Composable
fun LocalScreen(
    onNavigateToPlayer: () -> Unit,
    onNavigateToDownloading: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val activeCount by viewModel.downloadActiveCount.collectAsStateWithLifecycle()
    val downloadedKeys by viewModel.downloadedKeys.collectAsStateWithLifecycle()
    // ... 其余原样

    Column(...) {
        MusicFreeStatusBarChrome(...)
        // 加一个 AppBar，让 AppBar 上有"下载"按钮 + 徽标。如果当前页面已经依赖外部 AppBar，按现行 chrome rule 加；否则在 Column 顶部加自己的 AppBar。
        TopAppBar(  // 仅作示意，按 docs/ui-harness/screen-chrome-rules.md 选合适入口
            title = { Text("本地音乐") },
            actions = {
                BadgedBox(
                    badge = {
                        if (activeCount > 0) Badge { Text(activeCount.toString()) }
                    },
                ) {
                    IconButton(onClick = onNavigateToDownloading) {
                        Icon(Icons.Filled.Download, contentDescription = "下载")
                    }
                }
            },
        )
        LocalMusicContent(
            uiState = localUiState,
            downloadedKeys = downloadedKeys,
            onItemClick = ...,
            onItemLongClick = ...,
            onRetry = ...,
            modifier = Modifier.weight(1f),
        )
    }
}
```

> 注意：当前 LocalScreen 没有自己的 AppBar，依赖 chrome rule 决定。**实施时必读** [`docs/ui-harness/screen-chrome-rules.md`](../../ui-harness/screen-chrome-rules.md)；如规则要求走公共入口，按规则注册"局部 AppBar"或在 chrome 里加图标。

- [ ] **Step 3: Modify LocalMusicContent to render badge**

`LocalMusicContent` 增加 `downloadedKeys: Set<MediaKey>` 参数；列表项渲染时若 `MediaKey.of(item)` 在集合中，则在标题旁显示一个小 ✓ 图标（用 `Icons.Filled.Check` 或自定义）。具体改动尺寸有限：在 `MusicListItem`/`ListItem` 渲染处加 `if (key in downloadedKeys) Icon(Icons.Filled.Check, ...)`。

> 当前 `LocalMusicContent.kt` 文件结构未在本 plan 完整展开。实施时按现状增量改，保留已有 testTag/语义。

- [ ] **Step 4: Build verify**

```bash
./gradlew :app:assembleDebug --no-configuration-cache
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/ feature/home/
git commit -m "feat(home): wire DownloadingRoute into AppNavHost and LocalScreen entry"
```

---

## Task 21: MusicDetail download button + MusicListEditorLite bulk

**Files:**
- Modify: `<root>/feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musicdetail/MusicDetailScreen.kt`
- Modify: `<root>/feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musicdetail/MusicDetailViewModel.kt`
- Modify: `<root>/feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musiclisteditor/MusicListEditorLiteScreen.kt`
- Modify: `<root>/feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/musiclisteditor/MusicListEditorLiteViewModel.kt`（路径以现行项目为准）

- [ ] **Step 1: MusicDetailViewModel — add download trigger**

在构造函数注入 `Downloader` 和 `AppPreferences`。新增方法：

```kotlin
fun download(quality: PlayQuality? = null) {
    val item = currentMusicItem() ?: return  // 用现行 VM 已有的访问方式
    downloader.enqueue(listOf(item), quality)
}

suspend fun preferredDownloadQuality(): PlayQuality = appPreferences.defaultDownloadQuality.first()
```

- [ ] **Step 2: MusicDetailScreen — add download icon button + dialog**

在封面下方 operations 区追加一个 `IconButton(onClick = { showQualityDialog = true })`，点击后弹 `DownloadQualityDialog`，确认 quality 后调 `viewModel.download(quality)`。

- [ ] **Step 3: MusicListEditorLiteScreen — add bulk download**

底部 action 栏追加"下载选中"按钮；点击后调 `viewModel.downloadSelected()`，VM 内部 `downloader.enqueue(selectedItems)`（quality 不传，用默认）。

- [ ] **Step 4: Build verify**

```bash
./gradlew :feature:home:assembleDebug --no-configuration-cache
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add feature/home/
git commit -m "feat(home): add download buttons in MusicDetail and MusicListEditorLite"
```

---

## Task 22: Long-press wiring on key list screens

**Files (modify, one screen at a time):**
- `<root>/feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchScreen.kt`
- `<root>/feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/local/LocalScreen.kt`（如已在 T20 加，跳过）
- `<root>/feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt`
- `<root>/feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailScreen.kt`
- `<root>/feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/toplist/TopListDetailScreen.kt`
- `<root>/feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailScreen.kt`
- `<root>/feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/history/HistoryScreen.kt`

> 每屏改动模式相同：(1) Screen 顶层维护 `var optionsItem by remember { mutableStateOf<MusicItem?>(null) }`；(2) 列表项 `onLongClick` 设置 `optionsItem = item`；(3) 在 Screen 末尾条件渲染 `if (optionsItem != null) MusicItemOptionsSheet(...)`。

- [ ] **Step 1: Search**

```kotlin
import com.zili.android.musicfreeandroid.core.ui.MusicItemOptionsSheet
import com.zili.android.musicfreeandroid.core.ui.DownloadQualityDialog
import com.zili.android.musicfreeandroid.core.model.PlayQuality
// ...

var optionsItem by remember { mutableStateOf<MusicItem?>(null) }
var qualityFor by remember { mutableStateOf<MusicItem?>(null) }
val defaultQuality by viewModel.defaultDownloadQuality.collectAsStateWithLifecycle(PlayQuality.STANDARD)

// In each list item:
//   modifier = Modifier.combinedClickable(
//     onClick = { /* existing play */ },
//     onLongClick = { optionsItem = item },
//   )

optionsItem?.let { item ->
    MusicItemOptionsSheet(
        item = item,
        onDismiss = { optionsItem = null },
        onDownload = { qualityFor = it; optionsItem = null },
    )
}
qualityFor?.let { item ->
    DownloadQualityDialog(
        initial = defaultQuality,
        onDismiss = { qualityFor = null },
        onConfirm = { q -> viewModel.download(item, q); qualityFor = null },
    )
}
```

`SearchViewModel` 增加：

```kotlin
fun download(item: MusicItem, quality: PlayQuality) {
    downloader.enqueue(listOf(item), quality)
}
val defaultDownloadQuality = appPreferences.defaultDownloadQuality
```

- [ ] **Step 2: Repeat for the other 6 screens**

每个 Screen + ViewModel 重复同样模式。**禁止使用"Similar to Step 1"占位**——每个 Screen 都有自己的导入、ViewModel 注入和列表项构造，需要逐个改。

> 在 plan 实际执行时建议拆成 6 个单独 commits，每个 Screen 一个 commit，便于回滚。

- [ ] **Step 3: Build verify**

```bash
./gradlew :app:assembleDebug --no-configuration-cache
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit (per-screen, repeated)**

```bash
git add feature/search/.../SearchScreen.kt feature/search/.../SearchViewModel.kt
git commit -m "feat(search): wire MusicItemOptionsSheet long-press download"
# ... repeat per screen
```

---

## Task 23: Settings — download section

**Files:**
- Modify: `<root>/feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModel.kt`
- Modify: `<root>/feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt`

- [ ] **Step 1: ViewModel — expose 3 settings**

```kotlin
val maxDownload: StateFlow<Int> = appPreferences.maxDownload
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)
val useCellularDownload: StateFlow<Boolean> = appPreferences.useCellularDownload
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
val defaultDownloadQuality: StateFlow<PlayQuality> = appPreferences.defaultDownloadQuality
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayQuality.STANDARD)

fun setMaxDownload(value: Int) = viewModelScope.launch { appPreferences.setMaxDownload(value) }
fun setUseCellularDownload(v: Boolean) = viewModelScope.launch { appPreferences.setUseCellularDownload(v) }
fun setDefaultDownloadQuality(q: PlayQuality) = viewModelScope.launch { appPreferences.setDefaultDownloadQuality(q) }
```

- [ ] **Step 2: Screen — add "下载" 分区 with 3 controls**

在现有设置区块下追加（按现行设计风格，使用 ListItem + Slider/Switch/Dropdown）：

```kotlin
SectionHeader("下载")  // 已有的 section-header composable

ListItem(
    headlineContent = { Text("同时下载数：${maxDownload.value}") },
    supportingContent = {
        Slider(
            value = maxDownload.value.toFloat(),
            valueRange = 1f..10f,
            steps = 8,
            onValueChange = { viewModel.setMaxDownload(it.toInt()) },
        )
    },
)

ListItem(
    headlineContent = { Text("使用移动网络下载") },
    trailingContent = {
        Switch(checked = useCellular.value, onCheckedChange = viewModel::setUseCellularDownload)
    },
)

ListItem(
    headlineContent = { Text("默认下载音质") },
    supportingContent = {
        SegmentedButtonRow {
            listOf(PlayQuality.LOW to "低品", PlayQuality.STANDARD to "标准", PlayQuality.HIGH to "高品", PlayQuality.SUPER to "超品")
                .forEachIndexed { i, (q, label) ->
                    SegmentedButton(
                        selected = defaultQuality.value == q,
                        onClick = { viewModel.setDefaultDownloadQuality(q) },
                        shape = SegmentedButtonDefaults.itemShape(i, 4),
                    ) { Text(label) }
                }
        }
    },
)
```

> 上面的 `SegmentedButtonRow` / `SegmentedButton` 调用对应 Material3 的 `SingleChoiceSegmentedButtonRow`。按当前 BOM 版本核对 API。如使用 dropdown 更省事，也可改 `ExposedDropdownMenuBox`。

- [ ] **Step 3: Build verify**

```bash
./gradlew :feature:settings:assembleDebug --no-configuration-cache
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add feature/settings/
git commit -m "feat(settings): add download settings (max concurrency, cellular, default quality)"
```

---

## Task 24: AndroidTest end-to-end

**Files:**
- Create: `<root>/app/src/androidTest/java/com/zili/android/musicfreeandroid/DownloadFlowAndroidTest.kt`

> 真实设备/模拟器 + MockWebServer。验证：插件 mock 返回 URL → enqueue → 文件出现在 MediaStore → `downloaded_tracks` 行被建。

- [ ] **Step 1: Add mockwebserver to androidTest deps**

`<root>/app/build.gradle.kts` dependencies 块追加：

```kotlin
    androidTestImplementation(libs.mockwebserver)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
```

- [ ] **Step 2: Write E2E test**

```kotlin
package com.zili.android.musicfreeandroid

import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.downloader.model.MediaKey
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DownloadFlowAndroidTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var downloader: Downloader

    private lateinit var server: MockWebServer

    @Before fun setup() {
        hilt.inject()
        server = MockWebServer().apply { start() }
    }

    @After fun teardown() { server.shutdown() }

    @Test fun enqueueDownloadsAndAppearsInMediaStore() = runTest {
        val payload = "fake-mp3-bytes".repeat(4096).toByteArray()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Length", payload.size.toString())
                .setBody(Buffer().apply { write(payload) }),
        )
        val item = MusicItem(
            id = "e2e-1", platform = "test", title = "TestSong", artist = "TestArtist",
            album = null, duration = 0L, url = server.url("/song.mp3").toString(),
            artwork = null, qualities = null,
        )
        downloader.enqueue(listOf(item), PlayQuality.STANDARD)

        // Wait for downloadedKeys to include our key, with 30s timeout
        downloader.downloadedKeys.test(timeoutMs = 30_000) {
            while (true) {
                val keys = awaitItem()
                if (MediaKey.of(item) in keys) break
            }
            cancelAndIgnoreRemainingEvents()
        }

        // Verify MediaStore has an entry under Music/MusicFree/
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val resolver: ContentResolver = ctx.contentResolver
        val cursor = resolver.query(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            arrayOf(MediaStore.Audio.Media.DISPLAY_NAME),
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("Music/MusicFree/%"),
            null,
        )
        cursor.use {
            assertTrue("expected at least one row", it != null && it.moveToFirst())
        }
    }
}
```

- [ ] **Step 3: Build + run**

```bash
./gradlew :app:connectedDebugAndroidTest --tests "*DownloadFlowAndroidTest*" --no-configuration-cache
```

Expected: 1 test PASS（需有连接的 emulator/device）。

> 注：该测试依赖 `Downloader` 真实使用 `PluginManager`，但 `MusicItem.platform = "test"` 没有任何插件，所以 fallback 到 `item.url`。OkHttpDownloader 会 hit MockWebServer。如发现 fallback 路径未被走通（`PluginMediaSourceResolver` 返回 null 后 `QualityFallback` 还要循环 4 个 quality 都返回 null 才会失败），需要在 engine 内的 runOne 末尾失败前再检查 `musicItem.url`——这是与 LoadedPlugin 既有 fallback 行为一致的"无插件直接用 item.url"——请在执行此 task 时核对 `runOne` 中 `if (resolved == null && !task.<seed url>.isNullOrBlank()) { ... }` 的处理，若缺则补一段："无插件且 musicItem.url 非空时，构造一个临时 MediaSourceResult 跳过 plugin 回退"。

- [ ] **Step 4: Commit**

```bash
git add app/
git commit -m "test(app): add end-to-end download flow integration test"
```

---

## Task 25: Manual runtime acceptance

**Files:** N/A — 手工验收

- [ ] **Step 1: Install + run**

```bash
./gradlew :app:installDebug
adb shell am start -n com.zili.android.musicfreeandroid/.MainActivity
```

- [ ] **Step 2: 用真实插件走一次完整链路**

1. 打开抽屉 → 设置 → 插件管理 → 安装一个测试插件
2. 首页 → 搜索 → 输入歌名 → 长按一首 → 弹出 `MusicItemOptionsSheet` → 选下载 → quality dialog 弹出 → 确认
3. 通知栏出现"正在下载 1 首歌（0/1 完成）"前台通知
4. 进入抽屉？不——回到首页 → 本地音乐 → AppBar 右上角下载图标有徽标 ⓵
5. 点下载图标 → 进 Downloading 页 → 看到任务进度
6. 等待完成 → Toast"下载任务已完成" → 通知消失 → Downloading 页变空状态
7. 本地音乐页（MediaStore 扫描后）出现这首歌 → 旁边带 ✓ 角标
8. 用系统"文件"App 打开 `Music/MusicFree/`，确认有新文件
9. 杀进程 → 重新打开 App → 数据仍在（无下载在跑、本地页角标仍在）
10. 多任务并发：长按多首歌依次下载，验证并发上限设置（设置里改 maxDownload=2，再发起 5 任务，观察 inflight 不超 2）
11. 设置开关：关闭"使用移动网络下载"+ adb 切到只剩 cellular（飞行模式 + 数据），enqueue 时 Toast 提示"未允许移动网络下载"
12. 失败重试：拔网络后发起一次下载 → FAILED → 接通后点重试 → 成功

- [ ] **Step 3: 验收 checklist**（每项打勾）

- [ ] 单首下载成功
- [ ] 通知前台显示 + 完成后消失
- [ ] LocalScreen AppBar 徽标计数正确
- [ ] 已下载项 ✓ 角标显示
- [ ] 卸载/重装 App，downloaded 文件**仍在系统** Music/MusicFree/（B 选择的核心承诺）
- [ ] Cellular 拒绝下载（开关关闭时）
- [ ] 失败重试一次后成功
- [ ] 取消进行中任务，cache 文件清掉、DB 行删除
- [ ] App 杀进程重启不丢任务（FAILED 项仍可见）
- [ ] 同一首歌不会重复下载

- [ ] **Step 4: 在 PR/commit 描述里贴出完成的验收 checklist**

---

## Spec Coverage Map

| Spec § | Covered by Task |
|---|---|
| 3 模块划分 | T1 |
| 4.1 Room 表 | T2 + T3 |
| 4.2 状态机 | T12 + T13 + T14 |
| 4.3 持久化与重启恢复 | T14 |
| 5.1 Service | T15 |
| 5.2 Engine | T12 |
| 5.3 调度循环 | T12 |
| 5.4 runOne | T12 + T13 |
| 5.5 并发与互斥 | T12 |
| 5.6 网络监听 + 流量闸门 | T10 + T14 |
| 5.7 取消/重试/清理 | T14 |
| 6.1 MediaStoreMusicWriter | T9 |
| 6.2 文件命名 | T6 |
| 6.3 扩展名 & MIME | T6 |
| 6.4 已下载判定 + 去重 | T12 |
| 6.5 外部文件清理同步 | （**未覆盖** — 见下方 gap） |
| 6.6 LocalScreen 影响 | T20 |
| 7.1 入口点 + Quality dialog | T17 + T18 + T21 + T22 |
| 7.2 LocalScreen AppBar 入口 | T20 |
| 7.3 Downloading 页 | T19 |
| 7.4 Local 列表角标 | T20 |
| 7.5 通知 | T15 |
| 8.1 偏好 | T4 |
| 8.2 Settings 页 UI | T23 |
| 8.3 Plugin 集成 + 质量回退 | T7 + T11 |
| 9 测试策略 | T2/T3/T6-T14/T24 |

**Gap: § 6.5（外部文件清理同步）未在任何 task 中显式实现。**

补丁：在 T12 的 `enqueue` 入口 dedup 检查处追加：

```kotlin
if (downloadedDao.exists(item.id, item.platform)) {
    val uri = downloadedDao.findUri(item.id, item.platform)
    val stillThere = uri?.let {
        runCatching { context.contentResolver.openInputStream(android.net.Uri.parse(it))?.close(); true }
            .getOrDefault(false)
    } ?: false
    if (stillThere) continue
    downloadedDao.deleteByKey(item.id, item.platform)
    // fall through to normal enqueue
}
```

→ 实施时把这段补进 T12 的 `enqueue` 实现里（需要在 DownloadEngine 注入 `Context`）。**T12 实施前请先回看 spec § 6.5，确认这部分写进 enqueue 的 dedup 路径。**

---

## Self-Review Notes

1. **Placeholder scan**：T18 中曾写过 `clickableNoIndicationOrSomething` 作为占位 — 已在最终代码块用 `Modifier.clickable` 替换。T20 的 `TODO("Inject Downloader directly via Hilt EntryPoint or expose via existing VM")` 是临时写法 — plan 已明确选 A 方案，并给出 ViewModel 的具体改动指引。
2. **Type consistency**：`MediaKey.of(...)` 在 T5 / T12 / T20 / T22 / T24 等处签名一致（接受 `MusicItem` 或 `(id, platform)`）。`Downloader` 接口字段 `tasks/downloadedKeys/events` 在 T16 / T19 / T20 一致引用。`DownloadEngine` 构造参数顺序在 T12 / T14 / T16 一致。
3. **Spec coverage**：spec § 6.5 一开始遗漏，已通过补丁说明并入 T12。其余 spec 章节均映射至具体 task。
4. **范围确认**：单 plan，单 feature。无需拆分。

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-04-downloading.md`. Two execution options:

**1. Subagent-Driven (recommended)** — 我每个 task 调度一个新 subagent，task 间做 review，节奏快。

**2. Inline Execution** — 在当前会话内用 `superpowers:executing-plans` 批量执行，关键 checkpoint 让你确认。

哪个？

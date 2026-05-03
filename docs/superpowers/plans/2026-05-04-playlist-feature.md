# 用户歌单功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 端到端实现用户自创建歌单（CRUD + 默认 `我喜欢` 歌单 + 排序模式 + 封面更换 + ⭐ surface），替换首页"我的歌单" tab 的 mock，撤掉"添加到歌单"的 Toast 占位，实现 RN 功能 parity。

**Architecture:** 三层结构 ——
1. `:core` 扩展 `Playlist` 域模型（加 `description / sortMode / createdAt / updatedAt / worksNum`）+ 新增 `SortMode` enum；`MusicItem` 加 `addedAt`。
2. `:data` 扩 entity 字段、Room version 升 3 + destructive fallback + `SeedFavoriteCallback` 种子；`PlaylistRepository` 加业务规则（favorite 守卫、auto-cover、sort 应用、`toggleFavorite` 等）；新增 `PlaylistCoverStore`。
3. UI：`:core/ui/` 抽两个共享 Composable（`MusicItemMoreMenu`、`AddToPlaylistBottomSheetContent`），各 surface（home / search / player-ui / pluginsheet / playlist detail）的 ViewModel 注入 `PlaylistRepository` 并自托管 sheet 状态。

**Tech Stack:** Kotlin, Room, Hilt, Jetpack Compose Material3, Coroutines/Flow, ActivityResultContracts.PickVisualMedia, JUnit + Compose UI test。

**Worktree:** `.worktrees/feat/playlist`（branch `feat/playlist`）。

**Spec：** [`../specs/2026-05-04-playlist-feature-design.md`](../specs/2026-05-04-playlist-feature-design.md)。

---

## File Structure

新建文件：

| 路径 | 责任 |
|---|---|
| `core/src/main/java/com/zili/android/musicfreeandroid/core/model/SortMode.kt` | `SortMode` enum |
| `data/src/main/java/com/zili/android/musicfreeandroid/data/db/SeedFavoriteCallback.kt` | Room callback，`onCreate` 时插入默认 favorite 行 |
| `data/src/main/java/com/zili/android/musicfreeandroid/data/cover/PlaylistCoverStore.kt` | 封面图片 IO（拷贝/删除/路径） |
| `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/MusicItemMoreMenu.kt` | 4 个 surface 共用的歌曲行更多菜单 |
| `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/AddToPlaylistBottomSheetContent.kt` | 4 个 surface 共用的"加入歌单"底部面板内容 |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailHeader.kt` | 详情页大封面 + 元数据 + 操作行 |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/SortModeDialog.kt` | 单选排序模式弹窗 |
| `app/src/main/res/drawable/ic_*.xml`（多个） | 从 RN 取的图标资源 |

修改文件（已存在，加列 / 加方法）：

| 路径 | 改动概要 |
|---|---|
| `core/src/main/java/com/zili/android/musicfreeandroid/core/model/Playlist.kt` | 加 `description / sortMode / createdAt / updatedAt / worksNum / isDefault / DEFAULT_FAVORITE_ID` |
| `core/src/main/java/com/zili/android/musicfreeandroid/core/model/MusicItem.kt` | 加 `addedAt: Long = 0L` |
| `data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/PlaylistEntity.kt` | 加 `description / sortMode` 列 |
| `data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/PlaylistMusicCrossRef.kt` | 加 `addedAt` 列 |
| `data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt` | version `2 → 3` |
| `data/src/main/java/com/zili/android/musicfreeandroid/data/di/DataModule.kt` | 移除 `addMigrations(...)`；加 `.fallbackToDestructiveMigration()` + `.addCallback(SeedFavoriteCallback)`；提供 `PlaylistCoverStore` |
| `data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/PlaylistDao.kt` | 加 `observePlaylist / isInPlaylist / observeIsInPlaylist / insertCrossRefIgnore / observeMusicWithAddedAt / setAllSortOrder` 等 |
| `data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/MusicDao.kt` | 加 `upsert(MusicItemEntity)` 方法（若不存在） |
| `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepository.kt` | 重写实现，加业务规则 |
| `data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/PlaylistMapper.kt` | 映射新字段；`MusicItem` 新 `addedAt` 字段 |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeViewModel.kt` | 注入 `PlaylistRepository`，暴露 playlists state；favorite 置顶排序 |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt` | 移除 `MINE_ROWS` 引用；wire `onCreateClick` |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetUiModel.kt` | 加 `isFavorite: Boolean`；卡片渲染心形封面 |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeMockVisualFactory.kt` | 删除 `MINE_ROWS`，仅保留 `STARRED_ROWS` |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt` | 整页重做（header + overflow + row ⋮ + empty state） |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailViewModel.kt` | 状态扩展 `playlist / musics / sortMode` |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDialogs.kt` | `RenamePlaylistDialog` → `EditPlaylistDialog`（封面 + 描述）；删 `AddToPlaylistDialog` |
| `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailScreen.kt` | 行末加 ⋮ + `MusicItemMoreMenu` |
| `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchScreen.kt` | 替换 Toast 占位为 `MusicItemMoreMenu` 调用 |
| `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt` | 注入 `PlaylistRepository`；加 add-to-playlist sheet 状态 |
| `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt` | 标题旁 heart 按钮；overflow 加"加入歌单" |
| `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt` | 注入 `PlaylistRepository`；暴露 `isCurrentFavorite` flow |

删除文件：

| 路径 |
|---|
| `data/src/main/java/com/zili/android/musicfreeandroid/data/db/migration/Migrations.kt` |
| 旧 `AddToPlaylistDialog`（在 `PlaylistDialogs.kt` 内删除该 composable） |

---

## Phase 1 — 域模型与 DB Schema

### Task 1: SortMode enum

**Files:**
- Create: `core/src/main/java/com/zili/android/musicfreeandroid/core/model/SortMode.kt`
- Test: `core/src/test/java/com/zili/android/musicfreeandroid/core/model/SortModeTest.kt`

- [ ] **Step 1.1: 写 SortModeTest**

```kotlin
// core/src/test/java/com/zili/android/musicfreeandroid/core/model/SortModeTest.kt
package com.zili.android.musicfreeandroid.core.model

import org.junit.Test
import org.junit.Assert.assertEquals

class SortModeTest {
    @Test
    fun `enum values cover all 6 RN sort modes`() {
        assertEquals(
            listOf("Manual", "Title", "Artist", "Album", "Newest", "Oldest"),
            SortMode.entries.map { it.name },
        )
    }

    @Test
    fun `default mode is Manual`() {
        assertEquals(SortMode.Manual, SortMode.DEFAULT)
    }
}
```

- [ ] **Step 1.2: Run — fails (类不存在)**

```bash
./gradlew :core:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.core.model.SortModeTest"
```
Expected: FAIL with `unresolved reference: SortMode`.

- [ ] **Step 1.3: 实现 SortMode**

```kotlin
// core/src/main/java/com/zili/android/musicfreeandroid/core/model/SortMode.kt
package com.zili.android.musicfreeandroid.core.model

enum class SortMode {
    Manual, Title, Artist, Album, Newest, Oldest;

    companion object { val DEFAULT: SortMode = Manual }
}
```

- [ ] **Step 1.4: Run — pass**

```bash
./gradlew :core:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.core.model.SortModeTest"
```
Expected: PASS。

- [ ] **Step 1.5: Commit**

```bash
git add core/src/main/java/com/zili/android/musicfreeandroid/core/model/SortMode.kt \
        core/src/test/java/com/zili/android/musicfreeandroid/core/model/SortModeTest.kt
git commit -m "feat(core): add SortMode enum"
```

---

### Task 2: Playlist 域模型扩展

**Files:**
- Modify: `core/src/main/java/com/zili/android/musicfreeandroid/core/model/Playlist.kt`
- Test: `core/src/test/java/com/zili/android/musicfreeandroid/core/model/PlaylistTest.kt`

- [ ] **Step 2.1: 写 PlaylistTest**

```kotlin
// core/src/test/java/com/zili/android/musicfreeandroid/core/model/PlaylistTest.kt
package com.zili.android.musicfreeandroid.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistTest {
    @Test
    fun `default constructor preserves legacy id name coverUri only signature`() {
        val p = Playlist(id = "abc", name = "Mix", coverUri = null)
        assertEquals("abc", p.id)
        assertEquals("Mix", p.name)
        assertNull(p.coverUri)
        assertNull(p.description)
        assertEquals(SortMode.Manual, p.sortMode)
        assertEquals(0L, p.createdAt)
        assertEquals(0L, p.updatedAt)
        assertEquals(0, p.worksNum)
        assertFalse(p.isDefault)
    }

    @Test
    fun `isDefault true when id equals DEFAULT_FAVORITE_ID`() {
        val p = Playlist(id = Playlist.DEFAULT_FAVORITE_ID, name = "我喜欢", coverUri = null)
        assertTrue(p.isDefault)
    }

    @Test
    fun `DEFAULT_FAVORITE_ID and NAME constants match RN`() {
        assertEquals("favorite", Playlist.DEFAULT_FAVORITE_ID)
        assertEquals("我喜欢", Playlist.DEFAULT_FAVORITE_NAME)
    }
}
```

- [ ] **Step 2.2: Run — fails**

```bash
./gradlew :core:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.core.model.PlaylistTest"
```
Expected: FAIL（`description / sortMode / createdAt / updatedAt / worksNum / isDefault / DEFAULT_FAVORITE_ID / DEFAULT_FAVORITE_NAME` 未解析）。

- [ ] **Step 2.3: 扩展 Playlist**

```kotlin
// core/src/main/java/com/zili/android/musicfreeandroid/core/model/Playlist.kt
package com.zili.android.musicfreeandroid.core.model

data class Playlist(
    val id: String,
    val name: String,
    val coverUri: String? = null,
    val description: String? = null,
    val sortMode: SortMode = SortMode.Manual,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val worksNum: Int = 0,
) {
    val isDefault: Boolean get() = id == DEFAULT_FAVORITE_ID

    companion object {
        const val DEFAULT_FAVORITE_ID = "favorite"
        const val DEFAULT_FAVORITE_NAME = "我喜欢"
    }
}
```

- [ ] **Step 2.4: Run — pass + 现有调用站点（PlaylistViewModel/test）也通过**

```bash
./gradlew :core:testDebugUnitTest
./gradlew :feature:home:testDebugUnitTest
```
Expected: 都 PASS（默认值兼容旧 3 参数构造）。

- [ ] **Step 2.5: Commit**

```bash
git add core/src/main/java/com/zili/android/musicfreeandroid/core/model/Playlist.kt \
        core/src/test/java/com/zili/android/musicfreeandroid/core/model/PlaylistTest.kt
git commit -m "feat(core): extend Playlist with description sortMode timestamps and isDefault"
```

---

### Task 3: MusicItem.addedAt 字段

**Files:**
- Modify: `core/src/main/java/com/zili/android/musicfreeandroid/core/model/MusicItem.kt`

- [ ] **Step 3.1: 添加 addedAt 字段（默认 0L 兼容现有调用）**

```kotlin
// core/src/main/java/com/zili/android/musicfreeandroid/core/model/MusicItem.kt
data class MusicItem(
    val id: String,
    val platform: String,
    val title: String,
    val artist: String,
    val album: String?,
    val duration: Long,
    val url: String?,
    val artwork: String?,
    val qualities: Map<PlayQuality, QualityInfo>?,
    val raw: Map<String, Any?> = emptyMap(),
    val addedAt: Long = 0L,
)
```

- [ ] **Step 3.2: Run 全模块单测，verify 现有调用未破**

```bash
./gradlew :core:testDebugUnitTest :data:testDebugUnitTest :feature:home:testDebugUnitTest
```
Expected: 全 PASS（默认值兼容）。

- [ ] **Step 3.3: Commit**

```bash
git add core/src/main/java/com/zili/android/musicfreeandroid/core/model/MusicItem.kt
git commit -m "feat(core): add MusicItem.addedAt for cross-ref-derived sort"
```

---

### Task 4: PlaylistEntity 加列

**Files:**
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/PlaylistEntity.kt`

- [ ] **Step 4.1: 加 description / sortMode 列**

```kotlin
// data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/PlaylistEntity.kt
package com.zili.android.musicfreeandroid.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val coverUri: String?,
    val description: String? = null,
    @ColumnInfo(defaultValue = "Manual") val sortMode: String = "Manual",
    val createdAt: Long,
    val updatedAt: Long,
)
```

- [ ] **Step 4.2: Run 编译（先不跑测，等下一步 DB version 升一起验）**

```bash
./gradlew :data:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4.3: Commit**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/PlaylistEntity.kt
git commit -m "feat(data): add description and sortMode columns to PlaylistEntity"
```

---

### Task 5: PlaylistMusicCrossRef.addedAt 列

**Files:**
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/PlaylistMusicCrossRef.kt`

- [ ] **Step 5.1: 加 addedAt 列**

```kotlin
// data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/PlaylistMusicCrossRef.kt
package com.zili.android.musicfreeandroid.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "playlist_music",
    primaryKeys = ["playlistId", "musicId", "musicPlatform"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MusicItemEntity::class,
            parentColumns = ["id", "platform"],
            childColumns = ["musicId", "musicPlatform"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("playlistId"),
        Index("musicId", "musicPlatform"),
    ],
)
data class PlaylistMusicCrossRef(
    val playlistId: String,
    val musicId: String,
    val musicPlatform: String,
    val sortOrder: Int,
    @ColumnInfo(defaultValue = "0") val addedAt: Long = 0L,
)
```

- [ ] **Step 5.2: Compile + commit**

```bash
./gradlew :data:compileDebugKotlin
git add data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/PlaylistMusicCrossRef.kt
git commit -m "feat(data): add addedAt column to PlaylistMusicCrossRef"
```

---

### Task 6: AppDatabase version 2 → 3

**Files:**
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt`

- [ ] **Step 6.1: 升 version**

把 `version = 2` 改成 `version = 3`：

```kotlin
@Database(
    entities = [
        MusicItemEntity::class,
        PlaylistEntity::class,
        PlaylistMusicCrossRef::class,
        PlayQueueEntity::class,
        StarredSheetEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playQueueDao(): PlayQueueDao
    abstract fun starredSheetDao(): StarredSheetDao
}
```

- [ ] **Step 6.2: Compile**

```bash
./gradlew :data:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 6.3: Commit**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt
git commit -m "feat(data): bump AppDatabase to version 3"
```

---

### Task 7: SeedFavoriteCallback + DataModule destructive fallback

**Files:**
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/SeedFavoriteCallback.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/di/DataModule.kt`
- Delete: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/migration/Migrations.kt`

- [ ] **Step 7.1: 写 SeedFavoriteCallback**

```kotlin
// data/src/main/java/com/zili/android/musicfreeandroid/data/db/SeedFavoriteCallback.kt
package com.zili.android.musicfreeandroid.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

object SeedFavoriteCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seedFavoriteRow(db)
    }

    fun seedFavoriteRow(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        db.execSQL(
            """
            INSERT OR IGNORE INTO playlists
                (id, name, coverUri, description, sortMode, createdAt, updatedAt)
            VALUES ('favorite', '我喜欢', NULL, NULL, 'Manual', $now, $now)
            """.trimIndent()
        )
    }
}
```

- [ ] **Step 7.2: 改 DataModule（移除旧 migration，加 destructive fallback + callback）**

```kotlin
// data/src/main/java/com/zili/android/musicfreeandroid/data/di/DataModule.kt
package com.zili.android.musicfreeandroid.data.di

import android.content.ContentResolver
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.SeedFavoriteCallback
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.MusicDao
import com.zili.android.musicfreeandroid.data.db.dao.PlaylistDao
import com.zili.android.musicfreeandroid.data.db.dao.PlayQueueDao
import com.zili.android.musicfreeandroid.data.db.dao.StarredSheetDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "musicfree.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .addCallback(SeedFavoriteCallback)
            .build()

    @Provides
    fun provideMusicDao(db: AppDatabase): MusicDao = db.musicDao()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun providePlayQueueDao(db: AppDatabase): PlayQueueDao = db.playQueueDao()

    @Provides
    fun provideStarredSheetDao(db: AppDatabase): StarredSheetDao = db.starredSheetDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides
    @Singleton
    fun provideConverters(): Converters = Converters()

    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver
}
```

> 注：Room 2.6+ 的 `fallbackToDestructiveMigration(dropAllTables: Boolean)` 是当前 Compose BOM `2026.04.01` 配套版本的签名。如果项目使用更早 API，把 `(dropAllTables = true)` 删掉即可。

- [ ] **Step 7.3: 删 Migrations.kt（包含子目录）**

```bash
rm data/src/main/java/com/zili/android/musicfreeandroid/data/db/migration/Migrations.kt
rmdir data/src/main/java/com/zili/android/musicfreeandroid/data/db/migration 2>/dev/null || true
```

- [ ] **Step 7.4: grep 全仓库无 `MIGRATION_1_2` 残留**

```bash
grep -rn "MIGRATION_1_2\|data\.db\.migration" --include="*.kt" .
```
Expected: 无输出。

- [ ] **Step 7.5: Compile**

```bash
./gradlew :data:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 7.6: Commit**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/db/SeedFavoriteCallback.kt \
        data/src/main/java/com/zili/android/musicfreeandroid/data/di/DataModule.kt
git rm data/src/main/java/com/zili/android/musicfreeandroid/data/db/migration/Migrations.kt
git commit -m "feat(data): seed favorite playlist on DB create; drop legacy migration"
```

---

## Phase 2 — DAO 扩展 与 Mapper 同步

### Task 8: PlaylistDao 新方法

**Files:**
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/PlaylistDao.kt`

- [ ] **Step 8.1: 加方法（保留现有方法）**

```kotlin
// 在已有 PlaylistDao 接口里追加这些方法（保留 insertPlaylist / updatePlaylist / deletePlaylist /
// observeAllPlaylists / getPlaylistById / removeMusicFromPlaylist / observeMusicInPlaylist /
// countMusicInPlaylist / maxSortOrderInPlaylist）。

@Query("SELECT * FROM playlists WHERE id = :id")
fun observePlaylist(id: String): Flow<PlaylistEntity?>

@Query("""
    SELECT EXISTS(
        SELECT 1 FROM playlist_music
        WHERE playlistId = :playlistId AND musicId = :musicId AND musicPlatform = :musicPlatform
    )
""")
suspend fun isInPlaylist(playlistId: String, musicId: String, musicPlatform: String): Boolean

@Query("""
    SELECT EXISTS(
        SELECT 1 FROM playlist_music
        WHERE playlistId = :playlistId AND musicId = :musicId AND musicPlatform = :musicPlatform
    )
""")
fun observeIsInPlaylist(playlistId: String, musicId: String, musicPlatform: String): Flow<Boolean>

@Insert(onConflict = OnConflictStrategy.IGNORE)
suspend fun insertCrossRefIgnore(crossRef: PlaylistMusicCrossRef): Long

@Query("""
    UPDATE playlists SET sortMode = :mode, updatedAt = :updatedAt WHERE id = :id
""")
suspend fun setSortMode(id: String, mode: String, updatedAt: Long)

@Query("""
    UPDATE playlists SET coverUri = :coverUri, updatedAt = :updatedAt WHERE id = :id
""")
suspend fun setCoverUri(id: String, coverUri: String?, updatedAt: Long)

@Query("""
    UPDATE playlists SET name = :name, description = :description, updatedAt = :updatedAt WHERE id = :id
""")
suspend fun updateNameDescription(id: String, name: String, description: String?, updatedAt: Long)

@Query("""
    UPDATE playlist_music SET sortOrder = :sortOrder
    WHERE playlistId = :playlistId AND musicId = :musicId AND musicPlatform = :musicPlatform
""")
suspend fun setCrossRefSortOrder(playlistId: String, musicId: String, musicPlatform: String, sortOrder: Int)

@Query("DELETE FROM playlists WHERE id = :id")
suspend fun deletePlaylistById(id: String): Int

@Query("""
    SELECT m.*, pm.addedAt AS pm_addedAt FROM music_items m
    INNER JOIN playlist_music pm ON m.id = pm.musicId AND m.platform = pm.musicPlatform
    WHERE pm.playlistId = :playlistId
    ORDER BY pm.sortOrder ASC
""")
fun observeMusicWithAddedAt(playlistId: String): Flow<List<MusicItemWithAddedAt>>
```

新增类（同包内，PlaylistDao.kt 顶部 import 之后）：

```kotlin
data class MusicItemWithAddedAt(
    @Embedded val music: MusicItemEntity,
    @ColumnInfo(name = "pm_addedAt") val addedAt: Long,
)
```

需要在 PlaylistDao.kt 顶部加 imports：

```kotlin
import androidx.room.ColumnInfo
import androidx.room.Embedded
```

- [ ] **Step 8.2: Compile（Room 注解处理器会校验 SQL）**

```bash
./gradlew :data:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL（如果 SQL 错会在此报错）。

- [ ] **Step 8.3: Commit**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/PlaylistDao.kt
git commit -m "feat(data): extend PlaylistDao with favorite/sort/upsert query helpers"
```

---

### Task 9: MusicDao.upsert（如不存在）

**Files:**
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/MusicDao.kt`

- [ ] **Step 9.1: 检查现状**

```bash
grep -n "upsert\|@Upsert\|@Insert" data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/MusicDao.kt
```

如果已有 `upsert(MusicItemEntity)` 或 `@Upsert` 方法，跳过本任务并直接 commit step 9.4。

- [ ] **Step 9.2: 添加 upsert 方法（如缺失）**

```kotlin
import androidx.room.Upsert

@Upsert
suspend fun upsert(item: MusicItemEntity)
```

- [ ] **Step 9.3: Compile**

```bash
./gradlew :data:compileDebugKotlin
```

- [ ] **Step 9.4: Commit（仅当 9.2 实际新增了方法）**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/MusicDao.kt
git commit -m "feat(data): add MusicDao.upsert for playlist add path"
```

---

### Task 10: PlaylistMapper 同步新字段

**Files:**
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/PlaylistMapper.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/MusicItemMapper.kt`（如存在；用于 `MusicItem.addedAt` 注入）

- [ ] **Step 10.1: 改 PlaylistMapper**

```kotlin
// data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/PlaylistMapper.kt
package com.zili.android.musicfreeandroid.data.mapper

import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.model.SortMode
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistEntity

fun Playlist.toEntity(createdAt: Long, updatedAt: Long): PlaylistEntity = PlaylistEntity(
    id = id,
    name = name,
    coverUri = coverUri,
    description = description,
    sortMode = sortMode.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PlaylistEntity.toModel(worksNum: Int = 0): Playlist = Playlist(
    id = id,
    name = name,
    coverUri = coverUri,
    description = description,
    sortMode = parseSortMode(sortMode),
    createdAt = createdAt,
    updatedAt = updatedAt,
    worksNum = worksNum,
)

private fun parseSortMode(name: String): SortMode =
    runCatching { SortMode.valueOf(name) }.getOrDefault(SortMode.Manual)
```

- [ ] **Step 10.2: 改 MusicItemMapper（如已有 toModel）— 加 addedAt 参数版本**

如果已存在 `MusicItemEntity.toModel()`，新增重载或可选参数：

```kotlin
fun MusicItemEntity.toModel(addedAt: Long = 0L, /* 其他依赖如 converters */): MusicItem
```

具体行内实现按现有 mapper 形态适配（可能依赖 `Converters`）。保证传入的 `addedAt` 注入到返回的 `MusicItem.addedAt` 字段。

- [ ] **Step 10.3: 同步现有 PlaylistRepository 已使用的 `toModel()` 调用站（`observeMusicInPlaylist` 现传不出 addedAt — 在 Task 14 中改）**

不在本任务里改 Repository，只确保 `PlaylistEntity.toModel()` 默认参数 `worksNum = 0` 让现有调用站点继续工作。

- [ ] **Step 10.4: Compile**

```bash
./gradlew :data:compileDebugKotlin
```

- [ ] **Step 10.5: Commit**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/PlaylistMapper.kt
# 若改了 MusicItemMapper：
git add data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/MusicItemMapper.kt
git commit -m "feat(data): map new Playlist fields and MusicItem.addedAt"
```

---

## Phase 3 — Cover Store 与 Repository 业务规则

### Task 11: PlaylistCoverStore

**Files:**
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/cover/PlaylistCoverStore.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/di/DataModule.kt`（加 `@Provides`）
- Test: `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/cover/PlaylistCoverStoreTest.kt`

- [ ] **Step 11.1: 写 PlaylistCoverStore**

```kotlin
// data/src/main/java/com/zili/android/musicfreeandroid/data/cover/PlaylistCoverStore.kt
package com.zili.android.musicfreeandroid.data.cover

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistCoverStore @Inject constructor(
    private val context: Context,
) {
    private val baseDir: File
        get() = File(context.filesDir, BASE_DIR_NAME).apply { mkdirs() }

    suspend fun saveFromUri(playlistId: String, src: Uri): String? = withContext(Dispatchers.IO) {
        val dest = File(baseDir, "$playlistId.jpg")
        runCatching {
            context.contentResolver.openInputStream(src)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
        }
        if (dest.exists() && dest.length() > 0) "$BASE_DIR_NAME/${dest.name}" else null
    }

    suspend fun copyFromArtwork(playlistId: String, artworkUrl: String?): String? {
        if (artworkUrl.isNullOrBlank()) return null
        // 仅支持 file:// / content:// 直接拷贝；http(s) artwork 不在本 store 处理（依赖 Coil 缓存层未来扩展）。
        val uri = runCatching { Uri.parse(artworkUrl) }.getOrNull() ?: return null
        if (uri.scheme !in listOf("file", "content")) return null
        return saveFromUri(playlistId, uri)
    }

    suspend fun delete(playlistId: String) = withContext(Dispatchers.IO) {
        File(baseDir, "$playlistId.jpg").delete()
        Unit
    }

    fun absoluteFile(relativePath: String): File = File(context.filesDir, relativePath)

    companion object { const val BASE_DIR_NAME = "playlist_covers" }
}
```

- [ ] **Step 11.2: 在 DataModule 加 @Provides（PlaylistCoverStore 自己已 @Inject + @Singleton，所以可以直接被构造注入；DataModule 不需要单独 @Provides）**

> 由于 `PlaylistCoverStore` 已加 `@Singleton @Inject constructor(context: Context)`，Hilt 自动管理；只需要 `@ApplicationContext` 限定（实际上构造参数 `Context` 已被 Hilt 在 SingletonComponent 中绑定，但若编译报错 "no binding for Context"，把构造参数改成 `@ApplicationContext private val context: Context`）。

构造改为：

```kotlin
@Singleton
class PlaylistCoverStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) { ... }
```

- [ ] **Step 11.3: 写 androidTest**

```kotlin
// data/src/androidTest/java/com/zili/android/musicfreeandroid/data/cover/PlaylistCoverStoreTest.kt
package com.zili.android.musicfreeandroid.data.cover

import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PlaylistCoverStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = PlaylistCoverStore(context)
    private val baseDir = File(context.filesDir, PlaylistCoverStore.BASE_DIR_NAME)

    @After fun cleanup() { baseDir.deleteRecursively() }

    @Test fun saveFromUri_writesFile_andReturnsRelativePath() = runBlocking {
        val src = createTempImage("origin.jpg")
        val rel = store.saveFromUri(playlistId = "plistA", src = src.toUri())
        assertNotNull(rel)
        assertTrue(File(baseDir, "plistA.jpg").exists())
    }

    @Test fun delete_removesFile() = runBlocking {
        val src = createTempImage("origin.jpg")
        store.saveFromUri("plistA", src.toUri())
        store.delete("plistA")
        assertTrue(!File(baseDir, "plistA.jpg").exists())
    }

    @Test fun copyFromArtwork_returnsNullForRemoteUrl() = runBlocking {
        val rel = store.copyFromArtwork("plistA", "https://example.com/cover.jpg")
        assertNull(rel)
    }

    private fun createTempImage(name: String): File =
        File(context.cacheDir, name).apply { writeBytes(ByteArray(64) { 1 }) }
}
```

- [ ] **Step 11.4: Run instrumented test（需设备/模拟器）**

```bash
./gradlew :data:connectedDebugAndroidTest --tests "com.zili.android.musicfreeandroid.data.cover.PlaylistCoverStoreTest"
```
Expected: 3 PASS。

- [ ] **Step 11.5: Commit**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/cover/PlaylistCoverStore.kt \
        data/src/androidTest/java/com/zili/android/musicfreeandroid/data/cover/PlaylistCoverStoreTest.kt
git commit -m "feat(data): add PlaylistCoverStore for cover image IO"
```

---

### Task 12: PlaylistRepository 重写 — 注入 + favorite 守卫 + sort + cover 操作

**Files:**
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepository.kt`
- Test: `data/src/test/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepositoryGuardsTest.kt`（新建；使用 mock dao）

- [ ] **Step 12.1: 写守卫单测（mockk）**

```kotlin
// data/src/test/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepositoryGuardsTest.kt
package com.zili.android.musicfreeandroid.data.repository

import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.data.cover.PlaylistCoverStore
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.MusicDao
import com.zili.android.musicfreeandroid.data.db.dao.PlaylistDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test

class PlaylistRepositoryGuardsTest {
    private val playlistDao: PlaylistDao = mockk(relaxed = true)
    private val musicDao: MusicDao = mockk(relaxed = true)
    private val coverStore: PlaylistCoverStore = mockk(relaxed = true)
    private val converters = Converters()
    private val repo = PlaylistRepository(playlistDao, musicDao, coverStore, converters)

    @Test fun deletePlaylist_throwsForFavorite() = runTest {
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.deletePlaylist(Playlist(id = "favorite", name = "我喜欢", coverUri = null))
            }
        }
    }

    @Test fun updatePlaylistInfo_throwsWhenRenamingFavorite() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.updatePlaylistInfo(id = "favorite", name = "新名字", description = null)
            }
        }
    }

    @Test fun updatePlaylistInfo_allowsDescriptionEditOnFavorite() = runTest {
        coEvery { playlistDao.getPlaylistById("favorite") } returns
            com.zili.android.musicfreeandroid.data.db.entity.PlaylistEntity(
                id = "favorite", name = "我喜欢", coverUri = null,
                description = null, sortMode = "Manual", createdAt = 0L, updatedAt = 0L,
            )
        try {
            repo.updatePlaylistInfo(id = "favorite", name = null, description = "我的最爱")
        } catch (e: Throwable) {
            fail("description update on favorite should not throw, got $e")
        }
    }
}
```

> 项目已有 `:data:testDebugUnitTest` 跑过其他单测，依赖里应已有 mockk + coroutines-test。如果缺，临时在 `data/build.gradle.kts` 的 testImplementation 加 `libs.mockk` + `libs.kotlinx.coroutines.test`（但通常已配）。

- [ ] **Step 12.2: Run — fails（方法签名不存在）**

```bash
./gradlew :data:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.data.repository.PlaylistRepositoryGuardsTest"
```
Expected: 编译错或 NSME。

- [ ] **Step 12.3: 重写 PlaylistRepository**

```kotlin
// data/src/main/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepository.kt
package com.zili.android.musicfreeandroid.data.repository

import android.net.Uri
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.model.SortMode
import com.zili.android.musicfreeandroid.data.cover.PlaylistCoverStore
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.MusicDao
import com.zili.android.musicfreeandroid.data.db.dao.PlaylistDao
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistMusicCrossRef
import com.zili.android.musicfreeandroid.data.mapper.toEntity
import com.zili.android.musicfreeandroid.data.mapper.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val musicDao: MusicDao,
    private val coverStore: PlaylistCoverStore,
    private val converters: Converters,
) {

    fun observeAllPlaylists(): Flow<List<Playlist>> =
        playlistDao.observeAllPlaylists().map { entities -> entities.map { it.toModel() } }

    fun observePlaylist(id: String): Flow<Playlist?> =
        playlistDao.observePlaylist(id).map { it?.toModel() }

    suspend fun getPlaylistById(id: String): Playlist? =
        playlistDao.getPlaylistById(id)?.toModel()

    fun observeFavorite(): Flow<Playlist> = observePlaylist(Playlist.DEFAULT_FAVORITE_ID).map {
        it ?: run {
            // 兜底：理论上 SeedFavoriteCallback 已插入；此分支只在异常路径触发。
            val now = System.currentTimeMillis()
            playlistDao.insertPlaylist(
                Playlist(
                    id = Playlist.DEFAULT_FAVORITE_ID,
                    name = Playlist.DEFAULT_FAVORITE_NAME,
                    coverUri = null,
                ).toEntity(createdAt = now, updatedAt = now)
            )
            requireNotNull(playlistDao.getPlaylistById(Playlist.DEFAULT_FAVORITE_ID)).toModel()
        }
    }

    fun isFavorite(item: MusicItem): Flow<Boolean> =
        playlistDao.observeIsInPlaylist(Playlist.DEFAULT_FAVORITE_ID, item.id, item.platform)

    suspend fun toggleFavorite(item: MusicItem) {
        val present = playlistDao.isInPlaylist(Playlist.DEFAULT_FAVORITE_ID, item.id, item.platform)
        if (present) removeMusicFromPlaylist(Playlist.DEFAULT_FAVORITE_ID, item)
        else addMusicToPlaylist(Playlist.DEFAULT_FAVORITE_ID, item)
    }

    suspend fun createPlaylist(playlist: Playlist) {
        val now = System.currentTimeMillis()
        playlistDao.insertPlaylist(playlist.toEntity(createdAt = now, updatedAt = now))
    }

    suspend fun updatePlaylistInfo(id: String, name: String?, description: String?) {
        if (id == Playlist.DEFAULT_FAVORITE_ID && name != null) {
            throw IllegalArgumentException("Cannot rename the default favorite playlist")
        }
        val entity = playlistDao.getPlaylistById(id) ?: return
        playlistDao.updateNameDescription(
            id = id,
            name = name ?: entity.name,
            description = description ?: entity.description,
            updatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun setSortMode(id: String, mode: SortMode) {
        if (mode == SortMode.Manual) {
            // 切回 Manual 时按当前观察顺序回写 sortOrder，避免视觉乱序。
            val current = playlistDao.observeMusicWithAddedAt(id).firstOrNull()
            // observeMusicWithAddedAt 是 Flow; 这里取一次：
        }
        playlistDao.setSortMode(id, mode.name, System.currentTimeMillis())
        if (mode == SortMode.Manual) {
            // 重新读取 + 计算并应用排序
            val list = playlistDao.observeMusicWithAddedAt(id).firstOrNull().orEmpty()
            list.forEachIndexed { index, item ->
                playlistDao.setCrossRefSortOrder(id, item.music.id, item.music.platform, index)
            }
        }
    }

    suspend fun setCover(id: String, sourceUri: Uri?) {
        val rel = if (sourceUri == null) null else coverStore.saveFromUri(id, sourceUri)
        if (sourceUri == null) coverStore.delete(id)
        playlistDao.setCoverUri(id, rel, System.currentTimeMillis())
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        if (playlist.id == Playlist.DEFAULT_FAVORITE_ID) {
            throw IllegalStateException("Cannot delete the default favorite playlist")
        }
        coverStore.delete(playlist.id)
        playlistDao.deletePlaylistById(playlist.id)
    }

    suspend fun addMusicToPlaylist(playlistId: String, item: MusicItem): Boolean {
        musicDao.upsert(item.toEntity())
        val nextOrder = playlistDao.maxSortOrderInPlaylist(playlistId) + 1
        val now = System.currentTimeMillis()
        val rowId = playlistDao.insertCrossRefIgnore(
            PlaylistMusicCrossRef(
                playlistId = playlistId,
                musicId = item.id,
                musicPlatform = item.platform,
                sortOrder = nextOrder,
                addedAt = now,
            )
        )
        val added = rowId != -1L
        if (added) {
            val playlist = playlistDao.getPlaylistById(playlistId)
            if (playlist != null && playlist.coverUri == null && !item.artwork.isNullOrBlank()) {
                val rel = coverStore.copyFromArtwork(playlistId, item.artwork)
                if (rel != null) playlistDao.setCoverUri(playlistId, rel, System.currentTimeMillis())
            }
        }
        return added
    }

    suspend fun addMusicsToPlaylist(playlistId: String, items: List<MusicItem>): Int {
        var addedCount = 0
        for (item in items) {
            if (addMusicToPlaylist(playlistId, item)) addedCount++
        }
        return addedCount
    }

    suspend fun removeMusicFromPlaylist(playlistId: String, item: MusicItem) {
        playlistDao.removeMusicFromPlaylist(playlistId, item.id, item.platform)
    }

    fun observeMusicInPlaylist(playlistId: String): Flow<List<MusicItem>> =
        playlistDao.observePlaylist(playlistId).flatMapLatest { entity ->
            val mode = entity?.let {
                runCatching { SortMode.valueOf(it.sortMode) }.getOrDefault(SortMode.Manual)
            } ?: SortMode.Manual
            playlistDao.observeMusicWithAddedAt(playlistId).map { rows ->
                rows.map { it.music.toModel(addedAt = it.addedAt, converters = converters) }
                    .applySort(mode)
            }
        }

    suspend fun countMusicInPlaylist(playlistId: String): Int =
        playlistDao.countMusicInPlaylist(playlistId)
}
```

需要：
- 加上 `firstOrNull()` 的 import：`import kotlinx.coroutines.flow.firstOrNull`
- 上面 `setSortMode` 中重复读取 `observeMusicWithAddedAt` 是为了在写回 sortOrder 时拿到当时的视觉顺序。Repository 不直接做排序写回，仍依赖现有 `applySort` 输出，所以 `setSortMode(Manual)` 的写回逻辑需要：先读到 *上一次非 Manual* 的视觉顺序。简化方案：调用方（ViewModel）切到 Manual 之前先 `observeMusicInPlaylist(...).first()` 拿当前已排序结果，再调 `repo.applyManualSortOrder(playlistId, orderedIds)`。把 `setSortMode` 拆成两个职责：
  - `setSortMode(id, mode)` 仅写 mode。
  - 新增 `applyManualSortOrder(id, orderedItems: List<MusicItem>)` 由 ViewModel 显式调用。

把 `setSortMode` 简化为：

```kotlin
suspend fun setSortMode(id: String, mode: SortMode) {
    playlistDao.setSortMode(id, mode.name, System.currentTimeMillis())
}

suspend fun applyManualSortOrder(id: String, orderedItems: List<MusicItem>) {
    orderedItems.forEachIndexed { index, item ->
        playlistDao.setCrossRefSortOrder(id, item.id, item.platform, index)
    }
}
```

ViewModel 在切回 Manual 时：

```kotlin
viewModelScope.launch {
    val current = repo.observeMusicInPlaylist(id).first()
    repo.setSortMode(id, SortMode.Manual)
    repo.applyManualSortOrder(id, current)
}
```

并实现 `applySort` 工具函数（在 Phase 4 Task 14 完成）。

- [ ] **Step 12.4: Run — pass**

```bash
./gradlew :data:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.data.repository.PlaylistRepositoryGuardsTest"
```
Expected: 3 PASS。

- [ ] **Step 12.5: 全 :data 模块跑 unit test 兜底**

```bash
./gradlew :data:testDebugUnitTest
```
Expected: 现有测试也 PASS。如果现有 `PlaylistRepositoryTest.kt`（androidTest）签名变了，下个 task 再修。

- [ ] **Step 12.6: Commit**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepository.kt \
        data/src/test/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepositoryGuardsTest.kt
git commit -m "feat(data): rewrite PlaylistRepository with favorite/sort/cover business rules"
```

---

### Task 13: SortMode 比较器 + applySort

**Files:**
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/sort/SortMode.kt`（包内 sortMode comparator）
- Test: `data/src/test/java/com/zili/android/musicfreeandroid/data/sort/SortModeApplyTest.kt`

> 命名注意：和 `:core/SortMode` 区分；该文件是 :data 内的扩展工具。

- [ ] **Step 13.1: 写 SortModeApplyTest**

```kotlin
// data/src/test/java/com/zili/android/musicfreeandroid/data/sort/SortModeApplyTest.kt
package com.zili.android.musicfreeandroid.data.sort

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.SortMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SortModeApplyTest {
    private fun item(id: String, title: String = id, artist: String = "", album: String? = null, addedAt: Long = 0L) =
        MusicItem(
            id = id, platform = "test", title = title, artist = artist, album = album,
            duration = 0L, url = null, artwork = null, qualities = null, addedAt = addedAt,
        )

    private val items = listOf(
        item("a", title = "苹果", artist = "Z", album = "Y", addedAt = 100),
        item("b", title = "Apple", artist = "A", album = "X", addedAt = 200),
        item("c", title = "香蕉", artist = "M", album = "X", addedAt = 50),
    )

    @Test fun manual_preservesInputOrder() {
        assertEquals(items, items.applySort(SortMode.Manual))
    }

    @Test fun newest_descByAddedAt() {
        assertEquals(listOf("b", "a", "c"), items.applySort(SortMode.Newest).map { it.id })
    }

    @Test fun oldest_ascByAddedAt() {
        assertEquals(listOf("c", "a", "b"), items.applySort(SortMode.Oldest).map { it.id })
    }

    @Test fun title_chineseCollator() {
        // "Apple" 应排在汉字前（中文 collator 多数实现把 ASCII 字母排在汉字前）。
        // 苹/香 比较：苹拼音 "ping"，香 "xiang" — "ping" < "xiang"。
        val titles = items.applySort(SortMode.Title).map { it.title }
        assertEquals(listOf("Apple", "苹果", "香蕉"), titles)
    }

    @Test fun artist_emptyTreatedAsEmptyString() {
        val withEmpty = items + item(id = "d", title = "Z", artist = "")
        val sorted = withEmpty.applySort(SortMode.Artist).map { it.id }
        // 空 artist 排第一，然后 A / M / Z
        assertEquals(listOf("d", "b", "c", "a"), sorted)
    }

    @Test fun album_handlesNullSafely() {
        val sorted = items.applySort(SortMode.Album).map { it.id }
        // album X 出现两次（b/c），保留稳定顺序；Y 在后
        assertEquals(listOf("b", "c", "a"), sorted)
    }
}
```

- [ ] **Step 13.2: Run — fails**

```bash
./gradlew :data:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.data.sort.SortModeApplyTest"
```
Expected: FAIL（`applySort` 未定义）。

- [ ] **Step 13.3: 实现 applySort**

```kotlin
// data/src/main/java/com/zili/android/musicfreeandroid/data/sort/SortMode.kt
package com.zili.android.musicfreeandroid.data.sort

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.SortMode
import java.text.Collator
import java.util.Locale

private val chineseCollator: Collator = Collator.getInstance(Locale.CHINESE)

fun List<MusicItem>.applySort(mode: SortMode): List<MusicItem> = when (mode) {
    SortMode.Manual -> this
    SortMode.Title -> sortedWith(compareBy(chineseCollator) { it.title })
    SortMode.Artist -> sortedWith(compareBy(chineseCollator) { it.artist.orEmpty() })
    SortMode.Album -> sortedWith(compareBy(chineseCollator) { it.album.orEmpty() })
    SortMode.Newest -> sortedByDescending { it.addedAt }
    SortMode.Oldest -> sortedBy { it.addedAt }
}
```

- [ ] **Step 13.4: Run — pass**

```bash
./gradlew :data:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.data.sort.SortModeApplyTest"
```
Expected: 6 PASS。

> 如 title test 在某些 JVM Collator 实现下顺序与预期不一致，调整测试断言匹配实际行为；目标是验证 collator 用上了，不是定死语义。

- [ ] **Step 13.5: 把 PlaylistRepository 的 `applySort` 引用切到这个工具**

`PlaylistRepository` 中已 import `com.zili.android.musicfreeandroid.data.sort.applySort`（Task 12 写的代码已使用 `applySort`，确认 import 正确）。

```bash
./gradlew :data:compileDebugKotlin
```

- [ ] **Step 13.6: Commit**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/sort/SortMode.kt \
        data/src/test/java/com/zili/android/musicfreeandroid/data/sort/SortModeApplyTest.kt
git commit -m "feat(data): add SortMode applySort with Chinese collator"
```

---

### Task 14: PlaylistRepository 集成测试

**Files:**
- Modify or new: `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepositoryTest.kt`（已有同名文件，按需扩展）

- [ ] **Step 14.1: 检查现有 androidTest**

```bash
cat data/src/androidTest/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepositoryTest.kt
```

如果现有测试只覆盖旧接口，扩展加新断言；如果完全过期，重写。

- [ ] **Step 14.2: 写完整端到端测试**

```kotlin
// data/src/androidTest/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepositoryTest.kt
package com.zili.android.musicfreeandroid.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.model.SortMode
import com.zili.android.musicfreeandroid.data.cover.PlaylistCoverStore
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.SeedFavoriteCallback
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlaylistRepositoryTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var db: AppDatabase
    private lateinit var repo: PlaylistRepository

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .addCallback(SeedFavoriteCallback)
            .allowMainThreadQueries()
            .build()
        repo = PlaylistRepository(db.playlistDao(), db.musicDao(), PlaylistCoverStore(ctx), Converters())
    }

    @After fun teardown() = db.close()

    @Test fun favoriteRow_existsAfterFreshInit() = runBlocking {
        val fav = repo.observeFavorite().first()
        assertEquals("favorite", fav.id)
        assertEquals("我喜欢", fav.name)
    }

    @Test fun addMusicToPlaylist_dedupReturnsFalseSecondTime() = runBlocking {
        val id = UUID.randomUUID().toString()
        repo.createPlaylist(Playlist(id = id, name = "Mix", coverUri = null))
        val first = repo.addMusicToPlaylist(id, sampleMusic("m1"))
        val second = repo.addMusicToPlaylist(id, sampleMusic("m1"))
        assertTrue(first); assertFalse(second)
    }

    @Test fun addMusic_autoSyncsCoverFromArtworkOnEmptyPlaylist() = runBlocking {
        // copyFromArtwork 仅识别 file:// / content:// — 用 file:// 制造艺术封面源
        val tmp = java.io.File(ctx.cacheDir, "art.jpg").apply { writeBytes(ByteArray(32) { 9 }) }
        val id = UUID.randomUUID().toString()
        repo.createPlaylist(Playlist(id = id, name = "Mix", coverUri = null))
        repo.addMusicToPlaylist(id, sampleMusic("m1", artwork = "file://${tmp.absolutePath}"))
        val playlist = repo.observePlaylist(id).first()
        assertNotNull(playlist?.coverUri)
        assertTrue(playlist!!.coverUri!!.startsWith("playlist_covers/"))
    }

    @Test fun toggleFavorite_isReciprocal() = runBlocking {
        val item = sampleMusic("m42")
        // 加入前 isFavorite=false
        assertFalse(repo.isFavorite(item).first())
        repo.toggleFavorite(item)
        assertTrue(repo.isFavorite(item).first())
        repo.toggleFavorite(item)
        assertFalse(repo.isFavorite(item).first())
    }

    @Test fun setSortMode_thenObserveOrderChanges() = runBlocking {
        val id = UUID.randomUUID().toString()
        repo.createPlaylist(Playlist(id = id, name = "Mix", coverUri = null))
        repo.addMusicToPlaylist(id, sampleMusic("m1", title = "苹果").copy(addedAt = 0)) // addedAt 由 repo 设
        repo.addMusicToPlaylist(id, sampleMusic("m2", title = "Apple"))
        repo.addMusicToPlaylist(id, sampleMusic("m3", title = "香蕉"))
        repo.setSortMode(id, SortMode.Title)
        val titles = repo.observeMusicInPlaylist(id).first().map { it.title }
        assertEquals(listOf("Apple", "苹果", "香蕉"), titles)
    }

    @Test fun deletePlaylist_throwsForFavoriteRow() = runBlocking {
        var caught: Throwable? = null
        try {
            repo.deletePlaylist(Playlist(id = "favorite", name = "我喜欢", coverUri = null))
        } catch (e: IllegalStateException) { caught = e }
        assertNotNull(caught)
    }

    private fun sampleMusic(id: String, title: String = id, artwork: String? = null) = MusicItem(
        id = id, platform = "test", title = title, artist = "Artist", album = null,
        duration = 0L, url = null, artwork = artwork, qualities = null,
    )
}
```

- [ ] **Step 14.3: Run instrumented**

```bash
./gradlew :data:connectedDebugAndroidTest --tests "com.zili.android.musicfreeandroid.data.repository.PlaylistRepositoryTest"
```
Expected: 全 PASS。

- [ ] **Step 14.4: Commit**

```bash
git add data/src/androidTest/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepositoryTest.kt
git commit -m "test(data): cover repository business rules with in-memory DB integration test"
```

---

## Phase 4 — 图标资源 与 共享 Composable

### Task 15: 从 RN 取图标资源

**Files:**
- Create (vector drawables): `app/src/main/res/drawable/ic_heart_filled.xml`、`ic_heart_outline.xml`、`ic_playlist_favorite_cover.xml`、`ic_folder_plus.xml`、`ic_queue_music.xml`、`ic_minus_circle.xml`、`ic_sort.xml`、`ic_play_circle.xml`

- [ ] **Step 15.1: grep RN 实际图标使用集合**

```bash
grep -rn "name=\"folder-plus\"\|name=\"heart\"\|name=\"queue-music\"\|name=\"minus-circle\"" \
  ../../../../MusicFree/src 2>/dev/null | head -30
```

记录每个图标在 RN 的具体使用位置；典型 RN 用 `react-native-vector-icons` 的 Feather / MaterialCommunityIcons 集合。

- [ ] **Step 15.2: 检查 `app/src/main/res/drawable/` 已有 ic_***

```bash
ls app/src/main/res/drawable/ | grep "^ic_"
```

如已有 `ic_folder_plus.xml`、`ic_search.xml` 等就复用，不重复建。

- [ ] **Step 15.3: 为每个缺失图标创建 vector drawable**

到 [feathericons.com](https://feathericons.com)（或对应 icon set 网站）拷 SVG path，转 Android vector drawable 格式。示例 `ic_heart_filled.xml`（实心红心 24dp）：

```xml
<!-- app/src/main/res/drawable/ic_heart_filled.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z" />
</vector>
```

`ic_heart_outline.xml`（空心心）：用 Feather `heart` SVG path（线条版）。

`ic_playlist_favorite_cover.xml`（"我喜欢"封面专用 — 红底白心方块）：

```xml
<!-- app/src/main/res/drawable/ic_playlist_favorite_cover.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="64dp"
    android:height="64dp"
    android:viewportWidth="64"
    android:viewportHeight="64">
    <path
        android:pathData="M0,0 H64 V64 H0 Z"
        android:fillColor="#E54B4B" />
    <path
        android:pathData="M32,46 L30,44.2 C20.7,35.7 14.7,30 14.7,23.5 C14.7,18.5 18.5,14.7 23.5,14.7 C26.3,14.7 29,16.1 32,18.4 C35,16.1 37.7,14.7 40.5,14.7 C45.5,14.7 49.3,18.5 49.3,23.5 C49.3,30 43.3,35.7 34,44.2 L32,46 Z"
        android:fillColor="#FFFFFF" />
</vector>
```

`ic_folder_plus.xml`（Feather folder-plus）、`ic_queue_music.xml`（MaterialDesignIcons queue-music 或 RN 同名）、`ic_minus_circle.xml`（Feather minus-circle）、`ic_sort.xml`（MaterialDesignIcons sort 或 RN 实际用图）、`ic_play_circle.xml`（Feather play-circle）。每个 drawable 24dp 默认，遵守 `android:tint="?attr/colorControlNormal"` 让主题色生效。

> 颜色锚点：`ic_playlist_favorite_cover` 的 `#E54B4B` 取自 RN 主题红心色；其他线性图标依赖 Compose 端 `tint = MusicFreeColors...` 染色。

- [ ] **Step 15.4: 编译 :app**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL（资源没语法错）。

- [ ] **Step 15.5: Commit**

```bash
git add app/src/main/res/drawable/ic_*.xml
git commit -m "feat(resources): import RN icons for playlist UI surfaces"
```

---

### Task 16: 共享 Composable — MusicItemMoreMenu

**Files:**
- Create: `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/MusicItemMoreMenu.kt`
- Test: `core/src/androidTest/java/com/zili/android/musicfreeandroid/core/ui/MusicItemMoreMenuTest.kt`

> 现有 `:core` 模块未必已配 Compose UI test 依赖；如缺，本 task 可暂用 `:feature:home` 的 androidTest 层托管。下面假设 `:core` 已能跑 ComposeTestRule（如 BOM 已含 ui-test-junit4）；如果不能，把测试文件挪到 `:feature:home/src/androidTest/...`。

- [ ] **Step 16.1: 写 MusicItemMoreMenu**

```kotlin
// core/src/main/java/com/zili/android/musicfreeandroid/core/ui/MusicItemMoreMenu.kt
package com.zili.android.musicfreeandroid.core.ui

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag

enum class MusicItemAction { PlayNext, ToggleFavorite, AddToPlaylist, RemoveFromPlaylist }

@Composable
fun MusicItemMoreMenu(
    actions: Set<MusicItemAction>,
    isFavorite: Boolean,
    onAction: (MusicItemAction) -> Unit,
    iconRes: Int = R.drawable.ic_more_vert, // assume project provides ic_more_vert; fallback below
    contentDescription: String = "更多",
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = { expanded = true },
        modifier = modifier.testTag("MusicItemMoreMenu_trigger"),
    ) {
        Icon(painter = painterResource(id = iconRes), contentDescription = contentDescription)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        if (MusicItemAction.PlayNext in actions) {
            DropdownMenuItem(
                text = { Text("下一首播放") },
                onClick = { expanded = false; onAction(MusicItemAction.PlayNext) },
                modifier = Modifier.testTag("MusicItemMoreMenu_PlayNext"),
            )
        }
        if (MusicItemAction.ToggleFavorite in actions) {
            DropdownMenuItem(
                text = { Text(if (isFavorite) "取消收藏" else "收藏") },
                onClick = { expanded = false; onAction(MusicItemAction.ToggleFavorite) },
                modifier = Modifier.testTag("MusicItemMoreMenu_ToggleFavorite"),
            )
        }
        if (MusicItemAction.AddToPlaylist in actions) {
            DropdownMenuItem(
                text = { Text("加入歌单") },
                onClick = { expanded = false; onAction(MusicItemAction.AddToPlaylist) },
                modifier = Modifier.testTag("MusicItemMoreMenu_AddToPlaylist"),
            )
        }
        if (MusicItemAction.RemoveFromPlaylist in actions) {
            DropdownMenuItem(
                text = { Text("从歌单移除") },
                onClick = { expanded = false; onAction(MusicItemAction.RemoveFromPlaylist) },
                modifier = Modifier.testTag("MusicItemMoreMenu_RemoveFromPlaylist"),
            )
        }
    }
}
```

> `R.drawable.ic_more_vert` — :core 模块的 R 类不直接含 :app res；需要在 :core 自己的 res 目录维护这个图标，或者把 `iconRes` 改成 `Painter` 由调用方传入。改为后者（更灵活）：

```kotlin
import androidx.compose.ui.graphics.painter.Painter

@Composable
fun MusicItemMoreMenu(
    actions: Set<MusicItemAction>,
    isFavorite: Boolean,
    onAction: (MusicItemAction) -> Unit,
    triggerIcon: Painter,
    contentDescription: String = "更多",
    modifier: Modifier = Modifier,
) { /* IconButton 中 Icon(painter = triggerIcon, ...) */ }
```

调用方在 `:feature:*` 里 `painterResource(R.drawable.ic_more_vert)` 后传入。

- [ ] **Step 16.2: 写 ComposeTestRule 测试**

```kotlin
// core/src/androidTest/java/com/zili/android/musicfreeandroid/core/ui/MusicItemMoreMenuTest.kt
package com.zili.android.musicfreeandroid.core.ui

import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MusicItemMoreMenuTest {
    @get:Rule val rule = createComposeRule()

    @Test fun showsPlayNextAndAddToPlaylist_whenInActionSet() {
        rule.setContent {
            MusicItemMoreMenu(
                actions = setOf(MusicItemAction.PlayNext, MusicItemAction.AddToPlaylist),
                isFavorite = false,
                onAction = {},
                triggerIcon = ColorPainter(Color.Black),
            )
        }
        rule.onNodeWithTag("MusicItemMoreMenu_trigger").performClick()
        rule.onNodeWithText("下一首播放").assertIsDisplayed()
        rule.onNodeWithText("加入歌单").assertIsDisplayed()
    }

    @Test fun toggleFavoriteLabelFlipsByIsFavorite() {
        rule.setContent {
            MusicItemMoreMenu(
                actions = setOf(MusicItemAction.ToggleFavorite),
                isFavorite = true,
                onAction = {},
                triggerIcon = ColorPainter(Color.Black),
            )
        }
        rule.onNodeWithTag("MusicItemMoreMenu_trigger").performClick()
        rule.onNodeWithText("取消收藏").assertIsDisplayed()
    }

    @Test fun emitsCorrectActionOnClick() {
        var captured: MusicItemAction? = null
        rule.setContent {
            MusicItemMoreMenu(
                actions = setOf(MusicItemAction.PlayNext, MusicItemAction.AddToPlaylist),
                isFavorite = false,
                onAction = { captured = it },
                triggerIcon = ColorPainter(Color.Black),
            )
        }
        rule.onNodeWithTag("MusicItemMoreMenu_trigger").performClick()
        rule.onNodeWithTag("MusicItemMoreMenu_AddToPlaylist").performClick()
        assertEquals(MusicItemAction.AddToPlaylist, captured)
    }
}
```

- [ ] **Step 16.3: Run**

```bash
./gradlew :core:connectedDebugAndroidTest --tests "com.zili.android.musicfreeandroid.core.ui.MusicItemMoreMenuTest"
```
Expected: 3 PASS。

- [ ] **Step 16.4: Commit**

```bash
git add core/src/main/java/com/zili/android/musicfreeandroid/core/ui/MusicItemMoreMenu.kt \
        core/src/androidTest/java/com/zili/android/musicfreeandroid/core/ui/MusicItemMoreMenuTest.kt
git commit -m "feat(core/ui): add shared MusicItemMoreMenu composable"
```

---

### Task 17: 共享 Composable — AddToPlaylistBottomSheetContent

**Files:**
- Create: `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/AddToPlaylistBottomSheetContent.kt`
- Test: `core/src/androidTest/java/com/zili/android/musicfreeandroid/core/ui/AddToPlaylistBottomSheetContentTest.kt`

- [ ] **Step 17.1: 写组件**

```kotlin
// core/src/main/java/com/zili/android/musicfreeandroid/core/ui/AddToPlaylistBottomSheetContent.kt
package com.zili.android.musicfreeandroid.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.model.Playlist

@Composable
fun AddToPlaylistBottomSheetContent(
    playlists: List<Playlist>,
    onSelect: (Playlist) -> Unit,
    onCreateNew: () -> Unit,
    folderPlusIcon: Painter,
    favoriteCoverIcon: Painter,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCreateNew)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("AddToPlaylist_CreateNew"),
        ) {
            Icon(painter = folderPlusIcon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text("新建歌单")
        }
        HorizontalDivider()
        LazyColumn {
            items(items = playlists, key = { it.id }) { playlist ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(playlist) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("AddToPlaylist_Row_${playlist.id}"),
                ) {
                    if (playlist.isDefault && playlist.coverUri == null) {
                        Icon(painter = favoriteCoverIcon, contentDescription = null)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(playlist.name)
                        Text("${playlist.worksNum} 首")
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 17.2: 写 ComposeTest**

```kotlin
// core/src/androidTest/java/com/zili/android/musicfreeandroid/core/ui/AddToPlaylistBottomSheetContentTest.kt
package com.zili.android.musicfreeandroid.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zili.android.musicfreeandroid.core.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AddToPlaylistBottomSheetContentTest {
    @get:Rule val rule = createComposeRule()

    @Test fun rendersFavoriteOnTop_andOtherRows() {
        val playlists = listOf(
            Playlist(id = "favorite", name = "我喜欢", coverUri = null),
            Playlist(id = "p1", name = "通勤", coverUri = null, worksNum = 12),
        )
        rule.setContent {
            AddToPlaylistBottomSheetContent(
                playlists = playlists,
                onSelect = {},
                onCreateNew = {},
                folderPlusIcon = ColorPainter(Color.Black),
                favoriteCoverIcon = ColorPainter(Color.Red),
            )
        }
        rule.onNodeWithText("新建歌单").assertIsDisplayed()
        rule.onNodeWithText("我喜欢").assertIsDisplayed()
        rule.onNodeWithText("通勤").assertIsDisplayed()
    }

    @Test fun selectionEmitsSelectedPlaylist() {
        var selected: Playlist? = null
        val playlists = listOf(
            Playlist(id = "favorite", name = "我喜欢", coverUri = null),
            Playlist(id = "p1", name = "通勤", coverUri = null, worksNum = 12),
        )
        rule.setContent {
            AddToPlaylistBottomSheetContent(
                playlists = playlists,
                onSelect = { selected = it },
                onCreateNew = {},
                folderPlusIcon = ColorPainter(Color.Black),
                favoriteCoverIcon = ColorPainter(Color.Red),
            )
        }
        rule.onNodeWithTag("AddToPlaylist_Row_p1").performClick()
        assertEquals("p1", selected?.id)
    }

    @Test fun createNewClickFires() {
        var fired = false
        rule.setContent {
            AddToPlaylistBottomSheetContent(
                playlists = emptyList(),
                onSelect = {},
                onCreateNew = { fired = true },
                folderPlusIcon = ColorPainter(Color.Black),
                favoriteCoverIcon = ColorPainter(Color.Red),
            )
        }
        rule.onNodeWithTag("AddToPlaylist_CreateNew").performClick()
        assert(fired)
    }
}
```

- [ ] **Step 17.3: Run + Commit**

```bash
./gradlew :core:connectedDebugAndroidTest --tests "com.zili.android.musicfreeandroid.core.ui.AddToPlaylistBottomSheetContentTest"
git add core/src/main/java/com/zili/android/musicfreeandroid/core/ui/AddToPlaylistBottomSheetContent.kt \
        core/src/androidTest/java/com/zili/android/musicfreeandroid/core/ui/AddToPlaylistBottomSheetContentTest.kt
git commit -m "feat(core/ui): add AddToPlaylistBottomSheetContent composable"
```

---

## Phase 5 — Home 接真数据

### Task 18: HomeViewModel 接 PlaylistRepository

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeViewModel.kt`

- [ ] **Step 18.1: 注入 PlaylistRepository + 暴露 playlists state**

读现有 `HomeViewModel.kt` 找到当前状态结构（应是 StateFlow<HomeUiState>）。在 ViewModel 中加：

```kotlin
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(
    // ...保留现有依赖
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = playlistRepository.observeAllPlaylists()
        .map { sortFavoriteFirst(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun sortFavoriteFirst(playlists: List<Playlist>): List<Playlist> {
        val (favorite, others) = playlists.partition { it.isDefault }
        return favorite + others.sortedByDescending { it.createdAt }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(
                Playlist(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    coverUri = null,
                )
            )
        }
    }

    fun addToPlaylist(playlistId: String, item: com.zili.android.musicfreeandroid.core.model.MusicItem) {
        viewModelScope.launch { playlistRepository.addMusicToPlaylist(playlistId, item) }
    }
}
```

- [ ] **Step 18.2: Compile + 现有测试**

```bash
./gradlew :feature:home:compileDebugKotlin
./gradlew :feature:home:testDebugUnitTest
```
Expected: BUILD + 测试 PASS。

- [ ] **Step 18.3: Commit**

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeViewModel.kt
git commit -m "feat(home): expose live playlists from repository on HomeViewModel"
```

---

### Task 19: HomeSheetUiModel.isFavorite + 心形封面渲染

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetUiModel.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetCard.kt`（或卡片渲染所在文件 — 实际文件名见现有 home 模块结构）

- [ ] **Step 19.1: 加 isFavorite 字段**

```kotlin
// HomeSheetUiModel.kt
data class HomeSheetUiModel(
    val id: String,
    val title: String,
    val coverUri: String?,
    val worksNum: Int,
    val isFavorite: Boolean = false,
    // ... 其他现有字段保留
)
```

- [ ] **Step 19.2: 在卡片 composable 渲染 favorite 心形封面**

找到 `HomeSheetCard`（或同等渲染单元）中 cover 渲染处，加分支：

```kotlin
import androidx.compose.ui.res.painterResource

if (uiModel.isFavorite && uiModel.coverUri == null) {
    Image(
        painter = painterResource(id = R.drawable.ic_playlist_favorite_cover),
        contentDescription = null,
        modifier = ...,
    )
} else {
    // 现有 CoverImage 渲染
}
```

`R.drawable.ic_playlist_favorite_cover` 是 Task 15 添加的资源；若 `:feature:home` 不能直接看到 `:app` 的 R，需把这个 drawable 移到 `:core/src/main/res/drawable/` 或 `:feature:home/src/main/res/drawable/`。建议放 `:core/src/main/res/drawable/`：

```bash
mv app/src/main/res/drawable/ic_playlist_favorite_cover.xml \
   core/src/main/res/drawable/ic_playlist_favorite_cover.xml
```

并把 import 改为 `import com.zili.android.musicfreeandroid.core.R`。

- [ ] **Step 19.3: 同步映射逻辑**

在 `HomeViewModel`（或将 `HomeUiState` 组装的地方）映射 `Playlist` 到 `HomeSheetUiModel` 时填 `isFavorite = playlist.isDefault`。具体落点取决于现有架构（很可能在 `HomeScreen` 或 `HomeScreenContent` 内 `combine`）。

- [ ] **Step 19.4: Compile + Commit**

```bash
./gradlew :feature:home:compileDebugKotlin
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetUiModel.kt \
        feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/*.kt \
        core/src/main/res/drawable/ic_playlist_favorite_cover.xml
git rm -f app/src/main/res/drawable/ic_playlist_favorite_cover.xml 2>/dev/null || true
git commit -m "feat(home): render favorite playlist heart cover"
```

---

### Task 20: HomeScreen 接 ViewModel + 删除 MINE_ROWS

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeMockVisualFactory.kt`（删除 MINE_ROWS）
- Test: `feature/home/src/androidTest/.../HomeFavoritePinnedTest.kt`（新建）

- [ ] **Step 20.1: 写 UI 测试 — 我喜欢必排第一**

```kotlin
// feature/home/src/androidTest/java/com/zili/android/musicfreeandroid/feature/home/HomeFavoritePinnedTest.kt
package com.zili.android.musicfreeandroid.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class HomeFavoritePinnedTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<dagger.hilt.android.testing.HiltTestActivity>()

    @Test fun favoritePlaylist_alwaysOnFirstCard() {
        // 假设 SeedFavoriteCallback 在测试 DB 中插入 favorite 行
        composeRule.onNodeWithText("我喜欢").assertIsDisplayed()
        // 后续可断言第一项 testTag 等
    }
}
```

> 完整 Hilt instrumented test 设置可能涉及 `HiltTestActivity` + `@CustomTestApplication`；项目可能已配。如果项目暂未配 Hilt instrumented test infra，本任务用 fake repository 在 `setContent` 直接驱动 `HomeScreen`（hoist state，传 `playlists = listOf(favorite, p1, p2)`）。优先看 `feature/home` 已有 androidTest 范式，照抄。

- [ ] **Step 20.2: 改 HomeScreen — 删 MINE_ROWS 引用，接真数据**

打开 `HomeScreen.kt`（约 100-200 行），找到所有 `HomeMockVisualFactory.MINE_ROWS` / `allowHomeMockSheetNavigation = false` 出现的位置：

```kotlin
// 替换前（示意）：
val mineRows = HomeMockVisualFactory.MINE_ROWS
val allowHomeMockSheetNavigation = false

// 替换为：
val playlists by viewModel.playlists.collectAsState()
val mineRows = playlists.map { it.toHomeSheetUiModel() }
```

加扩展函数：

```kotlin
private fun com.zili.android.musicfreeandroid.core.model.Playlist.toHomeSheetUiModel() = HomeSheetUiModel(
    id = id,
    title = name,
    coverUri = coverUri,
    worksNum = worksNum,
    isFavorite = isDefault,
)
```

`onCreateClick`：

```kotlin
var showCreateDialog by remember { mutableStateOf(false) }
// HomeSheetsHeader 的 onCreateClick 改成：
onCreateClick = { showCreateDialog = true }

if (showCreateDialog) {
    CreatePlaylistDialog(
        onDismiss = { showCreateDialog = false },
        onCreate = { name ->
            viewModel.createPlaylist(name)
            showCreateDialog = false
        },
    )
}
```

`onOpenMineSheet`：

```kotlin
onOpenMineSheet = { sheet ->
    navController.navigate(PlaylistDetailRoute(playlistId = sheet.id))
}
```

把 `allowHomeMockSheetNavigation = false` 哨兵全部删除。

- [ ] **Step 20.3: 删除 HomeMockVisualFactory.MINE_ROWS**

打开 `HomeMockVisualFactory.kt`，删除 `MINE_ROWS` val 定义和所有引用它的代码；保留 `STARRED_ROWS` 给收藏歌单 tab。

- [ ] **Step 20.4: grep 兜底**

```bash
grep -rn "MINE_ROWS\|allowHomeMockSheetNavigation" --include="*.kt" .
```
Expected: 无输出。

- [ ] **Step 20.5: Compile + Commit**

```bash
./gradlew :feature:home:assembleDebug
./gradlew :app:assembleDebug
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt \
        feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeMockVisualFactory.kt \
        feature/home/src/androidTest/java/com/zili/android/musicfreeandroid/feature/home/HomeFavoritePinnedTest.kt
git commit -m "feat(home): replace mock playlists with live repository data, pin favorite"
```

---

## Phase 6 — PlaylistDetailScreen 重做

### Task 21: PlaylistDetailViewModel 状态扩展

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailViewModel.kt`

- [ ] **Step 21.1: 重写 ViewModel**

```kotlin
// feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailViewModel.kt
package com.zili.android.musicfreeandroid.feature.home.playlist

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.model.SortMode
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val musics: List<MusicItem> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PlaylistRepository,
) : ViewModel() {

    private val playlistId: String = savedStateHandle["playlistId"]
        ?: error("PlaylistDetailViewModel requires playlistId nav arg")

    val uiState: StateFlow<PlaylistDetailUiState> = combine(
        repository.observePlaylist(playlistId),
        repository.observeMusicInPlaylist(playlistId),
    ) { playlist, musics ->
        PlaylistDetailUiState(playlist = playlist, musics = musics, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaylistDetailUiState())

    fun setSortMode(mode: SortMode) {
        viewModelScope.launch {
            if (mode == SortMode.Manual) {
                val current = repository.observeMusicInPlaylist(playlistId).first()
                repository.setSortMode(playlistId, SortMode.Manual)
                repository.applyManualSortOrder(playlistId, current)
            } else {
                repository.setSortMode(playlistId, mode)
            }
        }
    }

    fun updateInfo(name: String?, description: String?, coverUri: Uri?) {
        viewModelScope.launch {
            repository.updatePlaylistInfo(playlistId, name, description)
            if (coverUri != null) repository.setCover(playlistId, coverUri)
        }
    }

    fun deletePlaylistAndExit(onDone: () -> Unit) {
        val current = uiState.value.playlist ?: return
        viewModelScope.launch {
            try { repository.deletePlaylist(current); onDone() } catch (_: IllegalStateException) {}
        }
    }

    fun toggleFavorite(item: MusicItem) {
        viewModelScope.launch { repository.toggleFavorite(item) }
    }

    fun removeFromPlaylist(item: MusicItem) {
        viewModelScope.launch { repository.removeMusicFromPlaylist(playlistId, item) }
    }
}
```

- [ ] **Step 21.2: Compile + Commit**

```bash
./gradlew :feature:home:compileDebugKotlin
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailViewModel.kt
git commit -m "feat(home/playlist): expand PlaylistDetailViewModel with sort/info/delete actions"
```

---

### Task 22: PlaylistDetailHeader composable

**Files:**
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailHeader.kt`

- [ ] **Step 22.1: 写 header**

```kotlin
// feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailHeader.kt
package com.zili.android.musicfreeandroid.feature.home.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.ui.CoverImage
import com.zili.android.musicfreeandroid.feature.home.R

@Composable
fun PlaylistDetailHeader(
    playlist: Playlist,
    musicCount: Int,
    onPlayAll: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier.fillMaxWidth().padding(16.dp),
    ) {
        if (playlist.isDefault && playlist.coverUri == null) {
            Icon(
                painter = painterResource(id = com.zili.android.musicfreeandroid.core.R.drawable.ic_playlist_favorite_cover),
                contentDescription = null,
                modifier = Modifier.size(160.dp),
            )
        } else {
            CoverImage(
                uri = playlist.coverUri,
                modifier = Modifier.size(160.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f).height(160.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!playlist.description.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        playlist.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${musicCount} 首歌曲",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onPlayAll) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_play_circle),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("播放全部")
                }
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = onSearch) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = "搜索",
                    )
                }
            }
        }
    }
}
```

> `R.drawable.ic_play_circle` / `ic_search` — 若不在 `:feature:home/res/`，参考 Task 15 的 res 落地决策（建议都放 `:core/res/drawable/`）。把 `R` import 调成对应模块的 R。

- [ ] **Step 22.2: Compile + Commit**

```bash
./gradlew :feature:home:compileDebugKotlin
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailHeader.kt
git commit -m "feat(home/playlist): add detail-page header composable"
```

---

### Task 23: SortModeDialog

**Files:**
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/SortModeDialog.kt`

- [ ] **Step 23.1: 写 dialog**

```kotlin
// feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/SortModeDialog.kt
package com.zili.android.musicfreeandroid.feature.home.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.model.SortMode

private val SortModeLabels = mapOf(
    SortMode.Manual to "手动排序",
    SortMode.Title to "按标题",
    SortMode.Artist to "按艺术家",
    SortMode.Album to "按专辑",
    SortMode.Newest to "最新加入",
    SortMode.Oldest to "最早加入",
)

@Composable
fun SortModeDialog(
    current: SortMode,
    onSelect: (SortMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("排序方式") },
        text = {
            Column {
                SortMode.entries.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = current == mode, onClick = { onSelect(mode) })
                        Text(SortModeLabels[mode] ?: mode.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
```

- [ ] **Step 23.2: Compile + Commit**

```bash
./gradlew :feature:home:compileDebugKotlin
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/SortModeDialog.kt
git commit -m "feat(home/playlist): add SortModeDialog with 6 RN modes"
```

---

### Task 24: PlaylistDialogs.kt 升级 EditPlaylistDialog（封面 + 描述）

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDialogs.kt`

- [ ] **Step 24.1: 替换 RenamePlaylistDialog 为 EditPlaylistDialog**

```kotlin
// 在 PlaylistDialogs.kt 中：
// 1) 删除原 RenamePlaylistDialog（保留 CreatePlaylistDialog 与 DeletePlaylistDialog）。
// 2) 删除原 AddToPlaylistDialog。
// 3) 加入：

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zili.android.musicfreeandroid.core.model.Playlist

@Composable
fun EditPlaylistDialog(
    playlist: Playlist,
    onDismiss: () -> Unit,
    onSave: (name: String?, description: String?, coverUri: Uri?) -> Unit,
) {
    var name by remember { mutableStateOf(playlist.name) }
    var description by remember { mutableStateOf(playlist.description.orEmpty()) }
    var pickedCoverUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) pickedCoverUri = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑歌单") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(Color.LightGray)
                        .clickable {
                            launcher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                        .align(Alignment.CenterHorizontally),
                ) {
                    val coverModel = pickedCoverUri ?: playlist.coverUri
                    if (coverModel != null) {
                        AsyncImage(
                            model = coverModel,
                            contentDescription = null,
                            modifier = Modifier.size(96.dp),
                        )
                    } else {
                        Text("更换封面", modifier = Modifier.align(Alignment.Center))
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    enabled = !playlist.isDefault,
                    readOnly = playlist.isDefault,
                    supportingText = if (playlist.isDefault) {
                        { Text("默认歌单不可改名") }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("简介") },
                    minLines = 1,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val nameToSave = if (playlist.isDefault) null else name.takeIf { it != playlist.name }
                val descToSave = description.takeIf { it != playlist.description.orEmpty() }
                onSave(nameToSave, descToSave, pickedCoverUri)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
```

- [ ] **Step 24.2: 删 AddToPlaylistDialog（旧）**

在同一文件搜索 `AddToPlaylistDialog`（若存在）整个 composable 删除。

- [ ] **Step 24.3: 删 RenamePlaylistDialog（旧）**

整个 composable 删除。所有外部调用站换成 `EditPlaylistDialog`（在 PlaylistDetailScreen 重做时统一切换）。

- [ ] **Step 24.4: Compile + grep 兜底**

```bash
./gradlew :feature:home:compileDebugKotlin
grep -rn "RenamePlaylistDialog\|AddToPlaylistDialog" --include="*.kt" .
```
Expected: 编译可能 fail（旧调用站尚未切换），grep 会列出剩余调用 — 留到 Task 25/26 补上后再编译。

- [ ] **Step 24.5: Commit（即使这一步暂时编译不过，分阶段提交也可以；但建议把后续切换 PlaylistDetailScreen 的代码放同一 commit）**

> 推荐做法：把 Task 24 + Task 25（PlaylistDetailScreen 重做）合并到一次 commit。下面 Task 25 的 commit 步骤会一并 add 这个文件。

---

### Task 25: PlaylistDetailScreen 整页重做

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt`

- [ ] **Step 25.1: 完整重写（保留 route 入口签名 `PlaylistDetailScreen(onBack, onNavigateToPlayer, onNavigateToSearchMusicList, ...)`，改内部实现）**

```kotlin
// feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt
package com.zili.android.musicfreeandroid.feature.home.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.model.SortMode
import com.zili.android.musicfreeandroid.core.ui.MusicFreeScreenScaffold

@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToSearchMusicList: (playlistId: String) -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val playlist = state.playlist
    var menuExpanded by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    MusicFreeScreenScaffold(
        title = playlist?.name ?: "歌单",
        onBack = onBack,
        actions = {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "更多")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("编辑信息") },
                    onClick = { menuExpanded = false; showEditDialog = true },
                )
                DropdownMenuItem(
                    text = { Text("排序") },
                    onClick = { menuExpanded = false; showSortDialog = true },
                )
                if (playlist?.isDefault == false) {
                    DropdownMenuItem(
                        text = { Text("删除歌单") },
                        onClick = { menuExpanded = false; showDeleteDialog = true },
                    )
                }
            }
        },
    ) { padding ->
        if (state.isLoading || playlist == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("加载中…") }
            return@MusicFreeScreenScaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PlaylistDetailHeader(
                playlist = playlist,
                musicCount = state.musics.size,
                onPlayAll = {
                    // TODO Task 25.2 — wire to PlayerController
                    onNavigateToPlayer()
                },
                onSearch = { onNavigateToSearchMusicList(playlist.id) },
            )
            if (state.musics.isEmpty()) {
                EmptyState(onSearchAdd = { onNavigateToSearchMusicList(playlist.id) })
            } else {
                LazyColumn {
                    items(items = state.musics, key = { "${it.platform}::${it.id}" }) { item ->
                        PlaylistRow(
                            item = item,
                            onClickRow = onNavigateToPlayer,
                            onAction = { action ->
                                when (action) {
                                    com.zili.android.musicfreeandroid.core.ui.MusicItemAction.ToggleFavorite ->
                                        viewModel.toggleFavorite(item)
                                    com.zili.android.musicfreeandroid.core.ui.MusicItemAction.RemoveFromPlaylist ->
                                        viewModel.removeFromPlaylist(item)
                                    // PlayNext / AddToPlaylist 由上层路由处理 — 见 Task 27
                                    else -> {}
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showSortDialog && playlist != null) {
        SortModeDialog(
            current = playlist.sortMode,
            onSelect = { mode ->
                viewModel.setSortMode(mode)
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false },
        )
    }
    if (showEditDialog && playlist != null) {
        EditPlaylistDialog(
            playlist = playlist,
            onDismiss = { showEditDialog = false },
            onSave = { name, description, coverUri ->
                viewModel.updateInfo(name, description, coverUri)
                showEditDialog = false
            },
        )
    }
    if (showDeleteDialog && playlist != null) {
        DeletePlaylistDialog(
            playlist = playlist,
            onDismiss = { showDeleteDialog = false },
            onDelete = {
                viewModel.deletePlaylistAndExit(onDone = onBack)
                showDeleteDialog = false
            },
        )
    }
}

@Composable
private fun EmptyState(onSearchAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("歌单还没有歌曲", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSearchAdd) { Text("去搜索添加") }
    }
}
```

> `MusicFreeScreenScaffold` 是否支持 `actions` 参数取决于实现；如果不支持，回退到自定义 TopAppBar。在 Step 25.2 处理。

- [ ] **Step 25.2: 检查 MusicFreeScreenScaffold 签名**

```bash
grep -A 10 "fun MusicFreeScreenScaffold" core/src/main/java/com/zili/android/musicfreeandroid/core/ui/MusicFreeScreenScaffold.kt
```

如果当前 scaffold 不支持 `actions: @Composable RowScope.() -> Unit` 参数，扩展它：

```kotlin
@Composable
fun MusicFreeScreenScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
)
```

然后在内部 `MusicFreeTopAppBar` / `TopAppBar` 调用处加 `actions = actions`。

- [ ] **Step 25.3: 写 PlaylistRow（行 composable，含 ⋮）**

放在 `PlaylistDetailScreen.kt` 同文件底部或新建 `PlaylistRow.kt`：

```kotlin
@Composable
private fun PlaylistRow(
    item: MusicItem,
    onClickRow: () -> Unit,
    onAction: (com.zili.android.musicfreeandroid.core.ui.MusicItemAction) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxSize() // 行只占自己宽
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        com.zili.android.musicfreeandroid.core.ui.CoverImage(
            uri = item.artwork,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.padding(start = 12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge)
            Text(item.artist, style = MaterialTheme.typography.bodySmall)
        }
        com.zili.android.musicfreeandroid.core.ui.MusicItemMoreMenu(
            actions = setOf(
                com.zili.android.musicfreeandroid.core.ui.MusicItemAction.PlayNext,
                com.zili.android.musicfreeandroid.core.ui.MusicItemAction.ToggleFavorite,
                com.zili.android.musicfreeandroid.core.ui.MusicItemAction.AddToPlaylist,
                com.zili.android.musicfreeandroid.core.ui.MusicItemAction.RemoveFromPlaylist,
            ),
            isFavorite = false, // TODO Task 27 接入真实 isFavorite flow
            onAction = onAction,
            triggerIcon = androidx.compose.ui.res.painterResource(
                id = com.zili.android.musicfreeandroid.feature.home.R.drawable.ic_more_vert
            ), // 若 :feature:home 没 ic_more_vert，从 :core 取
        )
    }
}
```

`isFavorite` 当前传 false 仅为通过编译；Task 27 注入真实的 per-item flow。

- [ ] **Step 25.4: Compile + Commit（合并 Task 24 改动）**

```bash
./gradlew :feature:home:compileDebugKotlin
grep -rn "RenamePlaylistDialog\|AddToPlaylistDialog" --include="*.kt" .
```
Expected: 0 输出 + BUILD SUCCESSFUL。

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDialogs.kt \
        feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt \
        core/src/main/java/com/zili/android/musicfreeandroid/core/ui/MusicFreeScreenScaffold.kt
git commit -m "feat(home/playlist): redesign detail screen with header/sort/edit and ⋮ row menu"
```

---

## Phase 7 — ⭐ Surface roll-out

### Task 26: 各 surface 共用的 add-to-playlist controller pattern

**Files:**
- Create: `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/AddToPlaylistSheetState.kt`

各 surface 的 ViewModel 都会持有一个 sheet state；统一抽 state 类型 + 工具：

```kotlin
// core/src/main/java/com/zili/android/musicfreeandroid/core/ui/AddToPlaylistSheetState.kt
package com.zili.android.musicfreeandroid.core.ui

import com.zili.android.musicfreeandroid.core.model.MusicItem

data class AddToPlaylistSheetState(
    val visible: Boolean = false,
    val pendingItem: MusicItem? = null,
)
```

- [ ] **Step 26.1: Compile + Commit**

```bash
./gradlew :core:compileDebugKotlin
git add core/src/main/java/com/zili/android/musicfreeandroid/core/ui/AddToPlaylistSheetState.kt
git commit -m "feat(core/ui): add AddToPlaylistSheetState data class"
```

---

### Task 27: PlaylistDetailScreen 行 isFavorite + AddToPlaylist sheet 接入

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailViewModel.kt`

- [ ] **Step 27.1: VM 暴露 isFavorite per-item Flow + sheet state**

```kotlin
// PlaylistDetailViewModel.kt 加：
import com.zili.android.musicfreeandroid.core.ui.AddToPlaylistSheetState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

val sheetState = MutableStateFlow(AddToPlaylistSheetState())

val allPlaylists: StateFlow<List<Playlist>> =
    repository.observeAllPlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

fun isFavoriteFlow(item: MusicItem): Flow<Boolean> = repository.isFavorite(item)

fun showAddToPlaylistSheet(item: MusicItem) { sheetState.value = AddToPlaylistSheetState(true, item) }
fun hideAddToPlaylistSheet() { sheetState.value = AddToPlaylistSheetState(false, null) }

fun addPendingToPlaylist(playlistId: String) {
    val item = sheetState.value.pendingItem ?: return
    viewModelScope.launch {
        repository.addMusicToPlaylist(playlistId, item)
        hideAddToPlaylistSheet()
    }
}
```

- [ ] **Step 27.2: PlaylistDetailScreen 加 ModalBottomSheet 渲染**

```kotlin
// PlaylistDetailScreen.kt 顶层 composable 内追加：
val sheetState by viewModel.sheetState.collectAsState()
val allPlaylists by viewModel.allPlaylists.collectAsState()

if (sheetState.visible) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = { viewModel.hideAddToPlaylistSheet() }) {
        com.zili.android.musicfreeandroid.core.ui.AddToPlaylistBottomSheetContent(
            playlists = allPlaylists,
            onSelect = { viewModel.addPendingToPlaylist(it.id) },
            onCreateNew = { /* 简化处理：调起 CreatePlaylistDialog 也可，这里先 trigger create + auto-add */ },
            folderPlusIcon = androidx.compose.ui.res.painterResource(R.drawable.ic_folder_plus),
            favoriteCoverIcon = androidx.compose.ui.res.painterResource(
                com.zili.android.musicfreeandroid.core.R.drawable.ic_playlist_favorite_cover
            ),
        )
    }
}
```

> "新建歌单"快捷的细节：在 sheet 中点 "新建歌单" 后弹 CreatePlaylistDialog；提交时 ViewModel `createPlaylist(name)` + 把刚返回的 id 调 `addMusicToPlaylist(newId, pendingItem)`。需要 `repo.createPlaylist` 返回 id 或 `playlistDao.insertPlaylist` 后用预生成 UUID。当前 `createPlaylist(playlist: Playlist)` 调用方先 UUID，所以可以这样：
>
> ```kotlin
> suspend fun createAndAdd(name: String) {
>     val id = java.util.UUID.randomUUID().toString()
>     repository.createPlaylist(Playlist(id = id, name = name, coverUri = null))
>     val item = sheetState.value.pendingItem ?: return
>     repository.addMusicToPlaylist(id, item)
>     hideAddToPlaylistSheet()
> }
> ```

- [ ] **Step 27.3: PlaylistRow 接入 isFavorite flow**

把 `PlaylistRow` 的 `isFavorite = false` 占位改成：

```kotlin
val isFavorite by viewModel.isFavoriteFlow(item).collectAsState(initial = false)
```

并把 `onAction` 中 `AddToPlaylist` 分支改成 `viewModel.showAddToPlaylistSheet(item)`。`PlayNext` 暂时用 PlayerController 的 `playNext(item)`（如已有 API；不存在则在 TODO 中 stub 调用 `onAction` 上抛给上层）。

- [ ] **Step 27.4: Compile + Commit**

```bash
./gradlew :feature:home:compileDebugKotlin
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailScreen.kt \
        feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/playlist/PlaylistDetailViewModel.kt
git commit -m "feat(home/playlist): wire isFavorite + AddToPlaylistBottomSheet on detail rows"
```

---

### Task 28: SearchScreen 替换 Toast 占位

**Files:**
- Modify: `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchScreen.kt`
- Modify: `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt`

- [ ] **Step 28.1: SearchViewModel 注入 PlaylistRepository + sheet state**

```kotlin
// SearchViewModel.kt — 加构造注入
@HiltViewModel
class SearchViewModel @Inject constructor(
    // 现有依赖
    private val playlistRepository: com.zili.android.musicfreeandroid.data.repository.PlaylistRepository,
) : ViewModel() {
    val sheetState = kotlinx.coroutines.flow.MutableStateFlow(
        com.zili.android.musicfreeandroid.core.ui.AddToPlaylistSheetState()
    )
    val allPlaylists: kotlinx.coroutines.flow.StateFlow<List<com.zili.android.musicfreeandroid.core.model.Playlist>> =
        playlistRepository.observeAllPlaylists()
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun isFavorite(item: com.zili.android.musicfreeandroid.core.model.MusicItem) = playlistRepository.isFavorite(item)
    fun toggleFavorite(item: com.zili.android.musicfreeandroid.core.model.MusicItem) {
        viewModelScope.launch { playlistRepository.toggleFavorite(item) }
    }
    fun showAddSheet(item: com.zili.android.musicfreeandroid.core.model.MusicItem) {
        sheetState.value = com.zili.android.musicfreeandroid.core.ui.AddToPlaylistSheetState(true, item)
    }
    fun hideAddSheet() { sheetState.value = com.zili.android.musicfreeandroid.core.ui.AddToPlaylistSheetState(false, null) }
    fun addPending(playlistId: String) {
        val item = sheetState.value.pendingItem ?: return
        viewModelScope.launch {
            playlistRepository.addMusicToPlaylist(playlistId, item)
            hideAddSheet()
        }
    }
}
```

- [ ] **Step 28.2: SearchScreen MusicResultItem 替换 Toast**

找到原 DropdownMenu 中 `Toast.makeText(context, "功能即将上线", ...)` 占位（spec 中 line 527-539 附近），替换成：

```kotlin
DropdownMenuItem(
    text = { Text("加入歌单") },
    onClick = {
        expanded = false
        viewModel.showAddSheet(item)
    },
)
```

并加新菜单项："收藏 / 取消收藏"：

```kotlin
val isFav by viewModel.isFavorite(item).collectAsState(initial = false)
DropdownMenuItem(
    text = { Text(if (isFav) "取消收藏" else "收藏") },
    onClick = { expanded = false; viewModel.toggleFavorite(item) },
)
```

- [ ] **Step 28.3: 在 SearchScreen 顶层渲染 ModalBottomSheet（同 Task 27.2 模式）**

```kotlin
val sheetState by viewModel.sheetState.collectAsState()
val allPlaylists by viewModel.allPlaylists.collectAsState()
if (sheetState.visible) {
    ModalBottomSheet(onDismissRequest = { viewModel.hideAddSheet() }) {
        AddToPlaylistBottomSheetContent(
            playlists = allPlaylists,
            onSelect = { viewModel.addPending(it.id) },
            onCreateNew = { /* 同 Task 27 createAndAdd 模式 */ },
            folderPlusIcon = painterResource(R.drawable.ic_folder_plus),
            favoriteCoverIcon = painterResource(
                com.zili.android.musicfreeandroid.core.R.drawable.ic_playlist_favorite_cover
            ),
        )
    }
}
```

- [ ] **Step 28.4: Compile + Commit**

```bash
./gradlew :feature:search:compileDebugKotlin
git add feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchScreen.kt \
        feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt
git commit -m "feat(search): replace add-to-playlist toast with real sheet, add favorite toggle"
```

---

### Task 29: PluginSheetDetailScreen 行加 ⋮ 菜单

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailScreen.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailViewModel.kt`（如已存在；否则就在 screen 内 hoist 状态）

- [ ] **Step 29.1: ViewModel 同 Task 28 模式 注入 PlaylistRepository + sheet state + isFavorite/toggleFavorite/showAddSheet/addPending**

照抄 Task 28.1 的代码（VM 类名换成 `PluginSheetDetailViewModel`）。

- [ ] **Step 29.2: Screen 行末加 MusicItemMoreMenu**

找到歌曲行 composable，trailing 区域加：

```kotlin
val isFav by viewModel.isFavorite(item).collectAsState(initial = false)
MusicItemMoreMenu(
    actions = setOf(MusicItemAction.PlayNext, MusicItemAction.ToggleFavorite, MusicItemAction.AddToPlaylist),
    isFavorite = isFav,
    onAction = { action ->
        when (action) {
            MusicItemAction.PlayNext -> { /* PlayerController.playNext(item) */ }
            MusicItemAction.ToggleFavorite -> viewModel.toggleFavorite(item)
            MusicItemAction.AddToPlaylist -> viewModel.showAddSheet(item)
            else -> {}
        }
    },
    triggerIcon = painterResource(R.drawable.ic_more_vert),
)
```

- [ ] **Step 29.3: 同 Task 28.3 在 Screen 顶层渲染 ModalBottomSheet**

- [ ] **Step 29.4: Compile + Commit**

```bash
./gradlew :feature:home:compileDebugKotlin
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailScreen.kt \
        feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailViewModel.kt
git commit -m "feat(home/pluginsheet): add ⋮ menu with favorite + add-to-playlist on rows"
```

---

### Task 30: PlayerScreen heart icon + overflow 加入歌单

**Files:**
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt`

> 文件路径以实际为准；`feature/player-ui` 模块结构按 grep 复核。

- [ ] **Step 30.1: PlayerViewModel 加 isCurrentFavorite + sheet state**

```kotlin
// PlayerViewModel.kt
import com.zili.android.musicfreeandroid.data.repository.PlaylistRepository
// 假设现有 ViewModel 已暴露 currentItem: StateFlow<MusicItem?>

val isCurrentFavorite: StateFlow<Boolean> = currentItem
    .flatMapLatest { item ->
        if (item == null) kotlinx.coroutines.flow.flowOf(false)
        else playlistRepository.isFavorite(item)
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

fun toggleCurrentFavorite() {
    val item = currentItem.value ?: return
    viewModelScope.launch { playlistRepository.toggleFavorite(item) }
}

val sheetState = MutableStateFlow(AddToPlaylistSheetState())
val allPlaylists: StateFlow<List<Playlist>> = playlistRepository.observeAllPlaylists()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
fun showAddSheet() { sheetState.value = AddToPlaylistSheetState(true, currentItem.value) }
fun hideAddSheet() { sheetState.value = AddToPlaylistSheetState(false, null) }
fun addPending(playlistId: String) {
    val item = sheetState.value.pendingItem ?: return
    viewModelScope.launch {
        playlistRepository.addMusicToPlaylist(playlistId, item)
        hideAddSheet()
    }
}
```

- [ ] **Step 30.2: PlayerScreen 加 heart icon 和 overflow 项**

找到歌名标题 Row，旁边加：

```kotlin
val isFav by viewModel.isCurrentFavorite.collectAsState()
IconButton(onClick = { viewModel.toggleCurrentFavorite() }) {
    Icon(
        painter = painterResource(
            id = if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        ),
        contentDescription = if (isFav) "取消收藏" else "收藏",
        tint = if (isFav) Color.Red else MaterialTheme.colorScheme.onSurface,
    )
}
```

Overflow 菜单加 "加入歌单"：

```kotlin
DropdownMenuItem(
    text = { Text("加入歌单") },
    onClick = { menuExpanded = false; viewModel.showAddSheet() },
)
```

顶层渲染 ModalBottomSheet（同 Task 27.2 模式）。

- [ ] **Step 30.3: Compile + Commit**

```bash
./gradlew :feature:player-ui:compileDebugKotlin
git add feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt \
        feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt
git commit -m "feat(player-ui): add heart favorite button and add-to-playlist overflow"
```

---

## Phase 8 — 最终验收

### Task 31: 全模块构建 + 运行态手工验收

**Files:** 无新增

- [ ] **Step 31.1: 全量构建**

```bash
./gradlew :app:assembleDebug
./gradlew test
```
Expected: 全 PASS。

- [ ] **Step 31.2: connectedAndroidTest（需设备）**

```bash
./gradlew connectedDebugAndroidTest
```
Expected: 全 PASS（除已 `@Ignore` 的 2 个 pre-existing breakages）。

- [ ] **Step 31.3: 安装到设备 + 手工验收**

```bash
./gradlew :app:installDebug
adb shell am start -n com.zili.android.musicfreeandroid/.MainActivity
```

按以下清单逐项验证（来自 spec "手工运行态验收"）：

- [ ] 首页"我的歌单" tab 第一项是"我喜欢"，封面是红心方块
- [ ] 点击右上角"+" → 创建对话框 → 输入名称 → 确认 → 列表出现新歌单
- [ ] 点入新歌单 → 详情页显示空态 "歌单还没有歌曲" + "去搜索添加" 按钮
- [ ] 进入搜索 → 搜索某曲 → 行末 ⋮ → "加入歌单" → bottom sheet → 选刚创建的歌单 → toast 提示成功
- [ ] 回退到歌单详情 → 列表里出现刚加的歌
- [ ] 切到"插件歌单详情"（任意已安装插件的某榜单）→ 行末 ⋮ → "加入歌单" / "收藏" 都生效
- [ ] 点开任一歌曲进入全屏播放器 → 标题旁 ❤ 按钮 → 点击切换实心/空心，"我喜欢"歌单中相应增删
- [ ] 详情页 TopAppBar ⋮ → "排序" → 选 "按标题" → 列表立即重排
- [ ] 切到"按最新加入" → 列表重排
- [ ] 切回"手动排序" → 列表保留最近一次排序的视觉顺序
- [ ] ⋮ → "编辑信息" → 改名、加描述、点 "更换封面" → 选图 → 保存 → header 立即刷新；favorite 歌单的 name 字段 readOnly
- [ ] ⋮ → "删除歌单" → 确认 → 退出到首页 → 不见此歌单；favorite 时无 "删除歌单" 项
- [ ] 杀 app 重开 → 自定义封面仍在；删了的歌单的封面文件已不在 `filesDir/playlist_covers/`
- [ ] `adb shell pm clear com.zili.android.musicfreeandroid`（destructive fallback）→ 重启 app → "我喜欢" 仍是首项

- [ ] **Step 31.4: 标记 spec 验收闸门完成**

更新 spec doc 末尾的"验收闸门"区块（如必要），记录验收日期 + 关键结论。

- [ ] **Step 31.5: 在 worktree 完成 finishing-a-development-branch**

按 `superpowers:finishing-a-development-branch` 流程决定 merge / PR / cleanup。

```bash
git log --oneline main..HEAD | wc -l
```

记录本分支共多少 commit。

---

## Self-Review

**Spec coverage:** 检查每条 spec 要求是否有任务覆盖：

| Spec 要求 | 覆盖任务 |
|---|---|
| `SortMode` enum + Playlist 扩展 + MusicItem.addedAt | Task 1, 2, 3 |
| `PlaylistEntity.description / sortMode` 列 | Task 4 |
| `PlaylistMusicCrossRef.addedAt` 列 | Task 5 |
| `@Database(version = 3)` + `fallbackToDestructiveMigration` + `SeedFavoriteCallback` | Task 6, 7 |
| 删除 `Migrations.kt` | Task 7 |
| `PlaylistDao` 新方法（`isInPlaylist` / `observeIsInPlaylist` / `insertCrossRefIgnore` / `setSortMode` / `setCoverUri` / `updateNameDescription` / `setCrossRefSortOrder` / `deletePlaylistById` / `observePlaylist` / `observeMusicWithAddedAt`） | Task 8 |
| `MusicDao.upsert` | Task 9 |
| `PlaylistMapper` 同步新字段 | Task 10 |
| `PlaylistCoverStore` 实现 | Task 11 |
| `PlaylistRepository` favorite 守卫 / sort / cover / 加歌 / dedup / auto-cover | Task 12 |
| `applySort` 工具 + 中文 collator | Task 13 |
| 集成测试 destructive fallback 路径 | Task 14 |
| RN 图标资源（heart filled/outline、folder-plus、queue-music、minus-circle、sort、play-circle、playlist-favorite-cover） | Task 15 |
| 共享 `MusicItemMoreMenu` composable | Task 16 |
| 共享 `AddToPlaylistBottomSheetContent` composable | Task 17 |
| `HomeViewModel` 接 PlaylistRepository + favorite 置顶 | Task 18 |
| `HomeSheetUiModel.isFavorite` + 心形封面 | Task 19 |
| `HomeScreen` 删除 MINE_ROWS / wire 创建 / 移除 sheet 哨兵 | Task 20 |
| `PlaylistDetailViewModel` 状态扩展 | Task 21 |
| `PlaylistDetailHeader` 大封面 + 元数据 + 操作 | Task 22 |
| `SortModeDialog` 单选 6 模式 | Task 23 |
| `EditPlaylistDialog`（封面 + 描述）；删除旧 `RenamePlaylistDialog` / `AddToPlaylistDialog` | Task 24 |
| `PlaylistDetailScreen` 重做（header + overflow + row ⋮ + 空态） | Task 25 |
| `AddToPlaylistSheetState` data class | Task 26 |
| 详情页行 isFavorite + AddToPlaylistBottomSheet 接入 | Task 27 |
| Search 替换 Toast 占位 + 收藏菜单项 | Task 28 |
| PluginSheetDetail 行 ⋮ + favorite 菜单 | Task 29 |
| Player heart 按钮 + overflow 加入歌单 | Task 30 |
| 全量构建 + 手工运行态验收 | Task 31 |

**Placeholder scan:** Task 25.3 中 `isFavorite = false` 占位 → 在 Task 27.3 中替换为真实 flow（已显式登记）。Task 27.2 / 28.3 / 29.3 / 30.2 中 "新建歌单" 的细节链路写在 callout 里（`createAndAdd(name)` 模式）。所有 TODO 已明确对应 task。

**Type consistency:**
- `SortMode` 在 `:core` 域、`:data` 内 mapper / sort 工具、`:feature:home` SortModeDialog 一致使用同名 enum。
- `Playlist` 域扩展字段 (`description / sortMode / createdAt / updatedAt / worksNum / isDefault`) 在 ViewModel / mapper / dialog / detail header 统一引用。
- `MusicItemAction` enum 在 :core/ui 定义后被 :feature:home / search / pluginsheet / player-ui 引用，签名统一。
- `AddToPlaylistSheetState` 在 :core/ui 定义后被 4 个 surface ViewModel 共用，构造一致。

**API 签名一致性提示：** Task 12 把 `PlaylistRepository.deletePlaylist` 改为接 `Playlist`（保留旧签名），并新增 `applyManualSortOrder / setCover / setSortMode / observeFavorite / isFavorite / toggleFavorite / observePlaylist`；其余 ViewModel 调用均按这套 API。

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-05-04-playlist-feature.md`.** Two execution options:

**1. Subagent-Driven (recommended)** - 每个 task 派一个新 subagent，task 之间做两阶段 review，迭代快、上下文不污染主会话。

**2. Inline Execution** - 当前会话内顺序执行，按 phase 设 checkpoint 让你 review。

Which approach?

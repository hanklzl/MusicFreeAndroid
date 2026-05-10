# 收藏专辑 + 首页收藏 Tab 取消收藏 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让用户在搜索结果点开的 `AlbumDetailScreen` 中通过 heart 按钮收藏 / 取消收藏专辑；在首页“我的收藏” tab 中显示混合的（歌单 + 专辑）收藏列表，trash 图标可弹窗确认后取消收藏；点击 row 按 `kind` 分流到 `PluginSheetDetailRoute` 或 `AlbumDetailRoute`。

**Architecture:** 在已有的 `StarredSheetEntity` 上加 `kind: String` 区分 `"sheet"` / `"album"`；同表同 DAO 同 repo。`AlbumDetailViewModel` 复用 `StarredSheetRepository`。`HomeSheetUiModel` 透传 `kind` 让首页 row 决定导航；`HomeSheetsViewModel` 增加 `unstar(uiModel)`。AppDB 版本 6→7，沿用 destructive fallback。

**Tech Stack:** Kotlin, Jetpack Compose Material3, Room (`fallbackToDestructiveMigration`), Hilt, kotlinx.coroutines Flow/StateFlow, JUnit4 + Mockito-Kotlin + `kotlinx-coroutines-test`, `:logging` (`MfLog` / `MfLogger` / `LogCategory.APP`).

**Spec:** [`docs/superpowers/specs/2026-05-10-favorite-album-and-starred-trash-design.md`](../specs/2026-05-10-favorite-album-and-starred-trash-design.md)

**Branch / Worktree:** `feat/favorite-album-and-starred-trash` 在 `.worktrees/favorite-album-and-starred-trash`

---

## File Map

### Create

- `core/src/main/java/com/zili/android/musicfreeandroid/core/model/StarredKind.kt` — `object StarredKind { SHEET, ALBUM }`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumStarredMapper.kt` — `AlbumItemBase ↔ StarredSheet`
- `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumStarredMapperTest.kt`
- `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailViewModelTest.kt`
- `data/src/test/java/com/zili/android/musicfreeandroid/data/repository/StarredSheetMapperJvmTest.kt`

### Modify

- `core/src/main/java/com/zili/android/musicfreeandroid/core/model/StarredSheet.kt` — add `kind: String = StarredKind.SHEET`
- `data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/StarredSheetEntity.kt` — add `kind: String`
- `data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt` — `version = 7`
- `data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/StarredSheetMapper.kt` — round-trip `kind`
- `data/src/test/java/com/zili/android/musicfreeandroid/data/repository/StarredSheetRepositoryJvmTest.kt` — add `kind` coverage
- `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/db/dao/StarredSheetDaoTest.kt` — add `kind` coverage
- `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/repository/StarredSheetRepositoryTest.kt` — add `kind` end-to-end
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetStarredMapper.kt` — set `kind = StarredKind.SHEET`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailViewModel.kt` — inject `StarredSheetRepository`, expose `isAlbumStarred` + `toggleAlbumStarred()`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailScreen.kt` — heart `IconButton` in scaffold actions
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetUiModel.kt` — add `kind`, add `toAlbumItemBase()`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsList.kt` — branch by `kind`, wire trash click
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsSection.kt` — propagate new callbacks
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsViewModel.kt` — add `unstar(item)`
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt` — provide unstar dialog + onOpenStarredAlbum callback
- `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreenContent.kt` — propagate new callbacks
- `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsViewModelTest.kt` — cover album row + unstar
- `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetUiModelTest.kt` — cover album mapping
- `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetStarredMapperTest.kt` — assert `kind == SHEET`
- `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt` — pass `onNavigateToStarredAlbum`

---

## Task 1: Create worktree

**Files:**
- N/A (workspace setup)

- [ ] **Step 1: Verify on main branch with clean tree**

Run: `git status --porcelain && git branch --show-current`
Expected: empty status, branch `main`

- [ ] **Step 2: Verify `.worktrees/` ignored**

Run: `git check-ignore -v .worktrees/`
Expected: a `.gitignore:N:.worktrees/` line. If missing, append `.worktrees/` to `.gitignore` and commit before proceeding.

- [ ] **Step 3: Create worktree on new branch**

Run from repo root `/Users/zili/code/android/MusicFreeAndroid`:
`git worktree add -b feat/favorite-album-and-starred-trash .worktrees/favorite-album-and-starred-trash`

Expected: `Preparing worktree (new branch 'feat/favorite-album-and-starred-trash')`

- [ ] **Step 4: cd into worktree for all subsequent tasks**

Run: `cd .worktrees/favorite-album-and-starred-trash && git branch --show-current`
Expected: `feat/favorite-album-and-starred-trash`

All file paths in remaining tasks are relative to this worktree root.

---

## Task 2: Add `StarredKind` constant

**Files:**
- Create: `core/src/main/java/com/zili/android/musicfreeandroid/core/model/StarredKind.kt`

- [ ] **Step 1: Create the file**

```kotlin
package com.zili.android.musicfreeandroid.core.model

object StarredKind {
    const val SHEET: String = "sheet"
    const val ALBUM: String = "album"
}
```

- [ ] **Step 2: Compile core module**

Run: `./gradlew :core:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/zili/android/musicfreeandroid/core/model/StarredKind.kt
git commit -m "feat(core): add StarredKind discriminator constants"
```

---

## Task 3: Extend `StarredSheet` and `StarredSheetEntity` with `kind`

**Files:**
- Modify: `core/src/main/java/com/zili/android/musicfreeandroid/core/model/StarredSheet.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/StarredSheetEntity.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/StarredSheetMapper.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt`

- [ ] **Step 1: Add `kind` to `StarredSheet`**

Replace the entire body of `core/src/main/java/com/zili/android/musicfreeandroid/core/model/StarredSheet.kt` with:

```kotlin
package com.zili.android.musicfreeandroid.core.model

data class StarredSheet(
    val id: String,
    val platform: String,
    val title: String,
    val artist: String?,
    val coverUri: String?,
    val sourceUrl: String?,
    val kind: String = StarredKind.SHEET,
    val description: String? = null,
    val artwork: String? = null,
    val worksNum: Int? = null,
    val raw: Map<String, Any?> = emptyMap(),
)
```

(Note: `kind` placed after `sourceUrl` so existing positional callers keep working — all existing callers either use named arguments or stop at `sourceUrl`. Verify the `:feature:home/sheets/HomeSheetsViewModelTest.kt:46-48` constructor still compiles after Step 4.)

- [ ] **Step 2: Add `kind` to `StarredSheetEntity`**

Replace the entire body of `data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/StarredSheetEntity.kt` with:

```kotlin
package com.zili.android.musicfreeandroid.data.db.entity

import androidx.room.Entity
import com.zili.android.musicfreeandroid.core.model.StarredKind

@Entity(
    tableName = "starred_sheets",
    primaryKeys = ["id", "platform"],
)
data class StarredSheetEntity(
    val id: String,
    val platform: String,
    val title: String,
    val artist: String?,
    val coverUri: String?,
    val sourceUrl: String?,
    val kind: String = StarredKind.SHEET,
    val description: String? = null,
    val artwork: String? = null,
    val worksNum: Int? = null,
    val rawJson: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
```

- [ ] **Step 3: Round-trip `kind` in mapper**

Replace `data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/StarredSheetMapper.kt` with:

```kotlin
package com.zili.android.musicfreeandroid.data.mapper

import com.zili.android.musicfreeandroid.core.model.StarredSheet
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.entity.StarredSheetEntity

fun StarredSheet.toEntity(createdAt: Long, updatedAt: Long, converters: Converters): StarredSheetEntity = StarredSheetEntity(
    id = id,
    platform = platform,
    title = title,
    artist = artist,
    coverUri = coverUri,
    sourceUrl = sourceUrl,
    kind = kind,
    description = description,
    artwork = artwork,
    worksNum = worksNum,
    rawJson = converters.rawMapToJson(raw),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun StarredSheetEntity.toModel(converters: Converters): StarredSheet = StarredSheet(
    id = id,
    platform = platform,
    title = title,
    artist = artist,
    coverUri = coverUri,
    sourceUrl = sourceUrl,
    kind = kind,
    description = description,
    artwork = artwork,
    worksNum = worksNum,
    raw = converters.jsonToRawMap(rawJson),
)
```

- [ ] **Step 4: Bump DB version 6 → 7**

Edit `data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt`. Change the line `version = 6,` to `version = 7,` inside `@Database(...)`.

- [ ] **Step 5: Compile data module**

Run: `./gradlew :data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/zili/android/musicfreeandroid/core/model/StarredSheet.kt \
        data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/StarredSheetEntity.kt \
        data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/StarredSheetMapper.kt \
        data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt
git commit -m "feat(data): add kind discriminator to StarredSheet (sheet|album), bump DB v7"
```

---

## Task 4: Mapper unit test (TDD: write the test first)

**Files:**
- Create: `data/src/test/java/com/zili/android/musicfreeandroid/data/repository/StarredSheetMapperJvmTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.zili.android.musicfreeandroid.data.repository

import com.google.gson.Gson
import com.zili.android.musicfreeandroid.core.model.StarredKind
import com.zili.android.musicfreeandroid.core.model.StarredSheet
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.mapper.toEntity
import com.zili.android.musicfreeandroid.data.mapper.toModel
import org.junit.Assert.assertEquals
import org.junit.Test

class StarredSheetMapperJvmTest {

    private val converters = Converters(Gson())

    @Test
    fun `toEntity then toModel preserves all fields including kind`() {
        val source = StarredSheet(
            id = "alb-1",
            platform = "qq",
            title = "专辑 A",
            artist = "Artist",
            coverUri = "cover://a",
            sourceUrl = "https://example.com/a",
            kind = StarredKind.ALBUM,
            description = "desc",
            artwork = "art://a",
            worksNum = 12,
            raw = mapOf("foo" to "bar", "extra" to 7),
        )

        val roundTripped = source
            .toEntity(createdAt = 100L, updatedAt = 200L, converters = converters)
            .toModel(converters)

        assertEquals(source, roundTripped)
    }

    @Test
    fun `kind defaults to sheet when not specified`() {
        val source = StarredSheet(
            id = "sh-1",
            platform = "kuwo",
            title = "歌单 B",
            artist = null,
            coverUri = null,
            sourceUrl = null,
        )

        val entity = source.toEntity(createdAt = 1L, updatedAt = 2L, converters = converters)

        assertEquals(StarredKind.SHEET, entity.kind)
        assertEquals(StarredKind.SHEET, entity.toModel(converters).kind)
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :data:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.data.repository.StarredSheetMapperJvmTest"`
Expected: BUILD SUCCESSFUL with 2 tests passed.

- [ ] **Step 3: Commit**

```bash
git add data/src/test/java/com/zili/android/musicfreeandroid/data/repository/StarredSheetMapperJvmTest.kt
git commit -m "test(data): cover StarredSheet kind round-trip in mapper"
```

---

## Task 5: Update existing repository / DAO tests for `kind`

**Files:**
- Modify: `data/src/test/java/com/zili/android/musicfreeandroid/data/repository/StarredSheetRepositoryJvmTest.kt`
- Modify: `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/db/dao/StarredSheetDaoTest.kt`
- Modify: `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/repository/StarredSheetRepositoryTest.kt`

- [ ] **Step 1: Read each existing test file**

Read each of the three files above completely so you can preserve existing assertions when adding new ones.

- [ ] **Step 2: Add a `kind = ALBUM` test to `StarredSheetRepositoryJvmTest.kt`**

Append a new `@Test` to the end of the existing class (do not overwrite existing tests):

```kotlin
@Test
fun `toggle persists album kind and same identity replaces sheet kind`() = runTest {
    val repo = newRepo()
    val album = StarredSheet(
        id = "id-1", platform = "qq",
        title = "AlbumOne", artist = null, coverUri = null, sourceUrl = null,
        kind = com.zili.android.musicfreeandroid.core.model.StarredKind.ALBUM,
    )
    repo.toggle(album)
    assertEquals(com.zili.android.musicfreeandroid.core.model.StarredKind.ALBUM,
        repo.observeAll().first().single().kind)

    val asSheet = album.copy(kind = com.zili.android.musicfreeandroid.core.model.StarredKind.SHEET)
    repo.toggle(asSheet) // same (id, platform) ⇒ deletes
    assertEquals(0, repo.observeAll().first().size)
}
```

If the existing test file lacks a `newRepo()` helper, mirror whatever construction the existing tests use (e.g., directly construct `StarredSheetRepository(dao, Converters(Gson()))` with the local fake DAO from the file). Reuse the fake DAO already present in the file — do not introduce a new one.

- [ ] **Step 3: Add `kind` round-trip to DAO test**

Append to `StarredSheetDaoTest.kt`:

```kotlin
@Test
fun upsert_storesKindAlbum_andRoundTripsThroughObserveAll() = runTest {
    dao.upsert(entity(id = "alb-1", updatedAt = 1000L).copy(
        kind = com.zili.android.musicfreeandroid.core.model.StarredKind.ALBUM,
    ))
    val stored = dao.observeAll().first().single()
    assertEquals(com.zili.android.musicfreeandroid.core.model.StarredKind.ALBUM, stored.kind)
}
```

- [ ] **Step 4: Add `kind` end-to-end to repository androidTest**

Append to `StarredSheetRepositoryTest.kt` (mirror the file's existing builder pattern; use named arguments):

```kotlin
@Test
fun starringAlbumThenUnstarPreservesAlbumKindThenRemovesRow() = runTest {
    val albumModel = StarredSheet(
        id = "alb-9", platform = "qq",
        title = "AlbumNine", artist = "X", coverUri = null, sourceUrl = null,
        kind = com.zili.android.musicfreeandroid.core.model.StarredKind.ALBUM,
    )
    repository.toggle(albumModel)
    val first = repository.observeAll().first()
    assertEquals(1, first.size)
    assertEquals(com.zili.android.musicfreeandroid.core.model.StarredKind.ALBUM, first.single().kind)

    repository.toggle(albumModel)
    assertEquals(0, repository.observeAll().first().size)
}
```

- [ ] **Step 5: Run JVM tests**

Run: `./gradlew :data:testDebugUnitTest`
Expected: all green.

- [ ] **Step 6: Commit**

```bash
git add data/src/test/java/com/zili/android/musicfreeandroid/data/repository/StarredSheetRepositoryJvmTest.kt \
        data/src/androidTest/java/com/zili/android/musicfreeandroid/data/db/dao/StarredSheetDaoTest.kt \
        data/src/androidTest/java/com/zili/android/musicfreeandroid/data/repository/StarredSheetRepositoryTest.kt
git commit -m "test(data): cover album kind across DAO + repository tests"
```

(Android instrumentation tests in Step 4-5 will be exercised in Task 12's full validation; running them locally now is optional if a device/emulator isn't attached.)

---

## Task 6: Update `PluginSheetStarredMapper` to set `kind = SHEET`

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetStarredMapper.kt`
- Modify: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetStarredMapperTest.kt`

- [ ] **Step 1: Update the test first**

Open `PluginSheetStarredMapperTest.kt`, locate the assertion block for the `MusicSheetItemBase.toStarredSheet()` direction, and add:

```kotlin
import com.zili.android.musicfreeandroid.core.model.StarredKind
// ...
assertEquals(StarredKind.SHEET, starred.kind)
```

If the test doesn't already cover the forward direction, add a small `@Test` that builds a `MusicSheetItemBase` and asserts `.toStarredSheet().kind == StarredKind.SHEET`.

- [ ] **Step 2: Run the test (must fail until Step 3)**

Run: `./gradlew :feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.pluginsheet.PluginSheetStarredMapperTest"`
Expected: failure on the new assertion (default-arg may already be `SHEET`, in which case test passes immediately — that's fine, treat Step 3 as a no-op explicit annotation).

- [ ] **Step 3: Make the mapper explicit**

Edit `PluginSheetStarredMapper.kt`. In the forward function (`MusicSheetItemBase.toStarredSheet()`), set the field explicitly even though the default would suffice:

```kotlin
internal fun MusicSheetItemBase.toStarredSheet(): StarredSheet = StarredSheet(
    id = id,
    platform = platform,
    title = title ?: "歌单",
    artist = artist,
    coverUri = coverImg,
    sourceUrl = raw["sourceUrl"] as? String,
    kind = com.zili.android.musicfreeandroid.core.model.StarredKind.SHEET,
    description = description,
    artwork = artwork,
    worksNum = worksNum,
    raw = raw,
)
```

- [ ] **Step 4: Run the test again**

Same command as Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetStarredMapper.kt \
        feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetStarredMapperTest.kt
git commit -m "feat(home): tag plugin sheet starred mapper with kind=SHEET"
```

---

## Task 7: Add `AlbumStarredMapper` (TDD)

**Files:**
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumStarredMapperTest.kt`
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumStarredMapper.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.zili.android.musicfreeandroid.feature.home.albumdetail

import com.zili.android.musicfreeandroid.core.model.StarredKind
import com.zili.android.musicfreeandroid.plugin.api.AlbumItemBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumStarredMapperTest {

    @Test
    fun `toStarredSheet preserves identity coverArtworkAndKind`() {
        val album = AlbumItemBase(
            id = "alb-7",
            platform = "qq",
            title = "AlbumSeven",
            artist = "ArtistSeven",
            description = "desc",
            artwork = "art://7",
            date = "2026-05-10",
            worksNum = 8,
            raw = mapOf("sourceUrl" to "https://example.com/alb-7", "extra" to "x"),
        )

        val starred = album.toStarredSheet()

        assertEquals("alb-7", starred.id)
        assertEquals("qq", starred.platform)
        assertEquals(StarredKind.ALBUM, starred.kind)
        assertEquals("AlbumSeven", starred.title)
        assertEquals("ArtistSeven", starred.artist)
        assertEquals("art://7", starred.coverUri)
        assertEquals("art://7", starred.artwork)
        assertEquals("desc", starred.description)
        assertEquals(8, starred.worksNum)
        assertEquals("https://example.com/alb-7", starred.sourceUrl)
        assertEquals("x", starred.raw["extra"])
    }

    @Test
    fun `toStarredSheet falls back when title and sourceUrl missing`() {
        val album = AlbumItemBase(
            id = "alb-8",
            platform = "kuwo",
            title = null,
            artist = null,
            description = null,
            artwork = null,
            date = null,
            worksNum = null,
            raw = emptyMap(),
        )

        val starred = album.toStarredSheet()

        assertEquals("专辑", starred.title)
        assertNull(starred.sourceUrl)
        assertEquals(StarredKind.ALBUM, starred.kind)
    }
}
```

- [ ] **Step 2: Run, verify failure**

Run: `./gradlew :feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.albumdetail.AlbumStarredMapperTest"`
Expected: compilation fails (no `toStarredSheet` extension).

- [ ] **Step 3: Implement the mapper**

```kotlin
package com.zili.android.musicfreeandroid.feature.home.albumdetail

import com.zili.android.musicfreeandroid.core.model.StarredKind
import com.zili.android.musicfreeandroid.core.model.StarredSheet
import com.zili.android.musicfreeandroid.plugin.api.AlbumItemBase

internal fun AlbumItemBase.toStarredSheet(): StarredSheet = StarredSheet(
    id = id,
    platform = platform,
    title = title ?: "专辑",
    artist = artist,
    coverUri = artwork,
    sourceUrl = raw["sourceUrl"] as? String,
    kind = StarredKind.ALBUM,
    description = description,
    artwork = artwork,
    worksNum = worksNum,
    raw = raw,
)
```

- [ ] **Step 4: Run again**

Same command as Step 2. Expected: 2 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumStarredMapper.kt \
        feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumStarredMapperTest.kt
git commit -m "feat(home): add AlbumItemBase->StarredSheet mapper with kind=ALBUM"
```

---

## Task 8: `AlbumDetailViewModel` star observation + toggle (TDD)

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailViewModel.kt`
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailViewModelTest.kt`

The harness rule (`docs/dev-harness/test/rules.md` #1) requires `MainDispatcherRule + runTest + advanceUntilIdle` and forbids `runBlocking { ... .first { predicate } }`.

- [ ] **Step 1: Locate the existing `MainDispatcherRule` helper**

Run: `grep -rn "MainDispatcherRule" feature/home/src/test/`
Expected: a `MainDispatcherRule.kt` next to existing tests. Note its package — reuse it. If not present, copy from the closest existing test module (the harness-wide pattern).

- [ ] **Step 2: Write the failing test**

```kotlin
package com.zili.android.musicfreeandroid.feature.home.albumdetail

import androidx.lifecycle.SavedStateHandle
import com.zili.android.musicfreeandroid.core.media.MediaSourceResolver
import com.zili.android.musicfreeandroid.core.model.StarredKind
import com.zili.android.musicfreeandroid.core.model.StarredSheet
import com.zili.android.musicfreeandroid.core.navigation.AlbumDetailRoute
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.repository.StarredSheetRepository
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val pluginManager: PluginManager = mock()
    private val playerController: PlayerController = mock()
    private val appPreferences: AppPreferences = mock()
    private val downloader: Downloader = mock()
    private val mediaSourceResolver: MediaSourceResolver = mock()
    private val starredRepo: StarredSheetRepository = mock()

    private fun route(): SavedStateHandle {
        val route = AlbumDetailRoute(
            pluginPlatform = "qq",
            albumId = "alb-1",
            title = "AlbumOne",
            artist = "ArtistOne",
        )
        return SavedStateHandle().also { handle ->
            handle.set("musicfree.route", route) // not used directly; toRoute uses framework decoding
        }
    }

    private fun newViewModel(starredFlow: MutableStateFlow<Boolean>): AlbumDetailViewModel {
        whenever(starredRepo.observeIsStarred(id = "alb-1", platform = "qq"))
            .thenReturn(starredFlow)
        // Avoid plugin loading inside init: ensurePluginsLoaded suspends; getPlugin returns null ⇒ early-return error path.
        whenever(pluginManager.getPlugin("qq")).thenReturn(null)
        return AlbumDetailViewModel(
            savedStateHandle = SavedStateHandleFor(AlbumDetailRoute(
                pluginPlatform = "qq", albumId = "alb-1",
                title = "AlbumOne", artist = "ArtistOne",
            )),
            pluginManager = pluginManager,
            playerController = playerController,
            appPreferences = appPreferences,
            downloader = downloader,
            mediaSourceResolver = mediaSourceResolver,
            starredSheetRepository = starredRepo,
        )
    }

    @Test
    fun `isAlbumStarred mirrors repository flow`() = runTest(mainRule.dispatcher) {
        val flow = MutableStateFlow(false)
        val vm = newViewModel(flow)
        advanceUntilIdle()
        assertEquals(false, vm.isAlbumStarred.value)

        flow.value = true
        advanceUntilIdle()
        assertEquals(true, vm.isAlbumStarred.value)
    }

    @Test
    fun `toggleAlbumStarred forwards album seed with kind ALBUM`() = runTest(mainRule.dispatcher) {
        val flow = MutableStateFlow(false)
        val vm = newViewModel(flow)
        advanceUntilIdle()

        vm.toggleAlbumStarred()
        advanceUntilIdle()

        val captured = argumentCaptor<StarredSheet>()
        verify(starredRepo).toggle(captured.capture())
        val payload = captured.firstValue
        assertEquals("alb-1", payload.id)
        assertEquals("qq", payload.platform)
        assertEquals(StarredKind.ALBUM, payload.kind)
        assertEquals("AlbumOne", payload.title)
        assertEquals("ArtistOne", payload.artist)
    }
}
```

`SavedStateHandleFor` is a tiny inline helper that constructs a `SavedStateHandle` whose `toRoute<AlbumDetailRoute>()` returns the given route. The simplest approach is to look at an existing ViewModel test in the same module that uses `toRoute` (e.g., `PluginSheetDetailViewModelTest.kt`) and copy whatever constructor pattern it uses (it likely puts the `@Serializable` arguments into the `SavedStateHandle` as serialized strings using `androidx.navigation.SavedStateHandleSavedStateRegistry`-style helpers, or directly constructs the route object via reflection helper). If no helper exists, build one:

```kotlin
import androidx.navigation.NavType
import androidx.navigation.toRoute as navToRoute

@Suppress("FunctionName")
fun SavedStateHandleFor(route: AlbumDetailRoute): SavedStateHandle {
    // Use kotlinx.serialization.json to serialize each route field into the SavedStateHandle.
    val handle = SavedStateHandle()
    handle["pluginPlatform"] = route.pluginPlatform
    handle["albumId"] = route.albumId
    route.title?.let { handle["title"] = it }
    route.artist?.let { handle["artist"] = it }
    route.artwork?.let { handle["artwork"] = it }
    route.date?.let { handle["date"] = it }
    route.description?.let { handle["description"] = it }
    route.worksNum?.let { handle["worksNum"] = it }
    route.seedToken?.let { handle["seedToken"] = it }
    return handle
}
```

If the file `PluginSheetDetailViewModelTest.kt` does not exist, search any test file that injects `SavedStateHandle` for a route and reuse that exact mechanism; do NOT invent a new pattern. If no precedent exists in the codebase, simplify the test by extracting the toggle logic into a small testable function `buildAlbumStarredPayload(route, currentAlbum): StarredSheet` and unit-testing only that helper plus `isAlbumStarred` via a thin wrapper — but only fall back to that if SavedStateHandle construction is genuinely opaque.

- [ ] **Step 3: Run, expect failure**

Run: `./gradlew :feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.albumdetail.AlbumDetailViewModelTest"`
Expected: compile error (`AlbumDetailViewModel` ctor signature mismatch + missing `isAlbumStarred` / `toggleAlbumStarred`).

- [ ] **Step 4: Modify `AlbumDetailViewModel`**

Open `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailViewModel.kt`. Apply these edits:

1. Add imports near the top (group with existing imports):

```kotlin
import com.zili.android.musicfreeandroid.core.model.StarredSheet
import com.zili.android.musicfreeandroid.data.repository.StarredSheetRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
```

2. Add `starredSheetRepository: StarredSheetRepository` to the constructor (after `mediaSourceResolver`):

```kotlin
@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pluginManager: PluginManager,
    private val playerController: PlayerController,
    private val appPreferences: AppPreferences,
    private val downloader: Downloader,
    private val mediaSourceResolver: MediaSourceResolver,
    private val starredSheetRepository: StarredSheetRepository,
) : ViewModel() {
```

3. After the existing `private var currentAlbum: AlbumItemBase? = null` line, add:

```kotlin
val isAlbumStarred: StateFlow<Boolean> = starredSheetRepository
    .observeIsStarred(id = route.albumId, platform = route.pluginPlatform)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

fun toggleAlbumStarred() {
    val seed = currentAlbum ?: initialAlbumSeed
    viewModelScope.launch {
        starredSheetRepository.toggle(seed.toStarredSheet())
    }
}
```

(`seed.toStarredSheet()` is the extension from Task 7. Make sure the import is added.)

- [ ] **Step 5: Compile**

Run: `./gradlew :feature:home:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run the new test**

Run: `./gradlew :feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.albumdetail.AlbumDetailViewModelTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailViewModel.kt \
        feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailViewModelTest.kt
git commit -m "feat(home): wire AlbumDetailViewModel star observation + toggle"
```

---

## Task 9: `AlbumDetailScreen` heart action + log event

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailScreen.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailViewModel.kt`

- [ ] **Step 1: Add log event in ViewModel**

In `AlbumDetailViewModel.kt`, replace the `toggleAlbumStarred()` function with a version that logs:

```kotlin
fun toggleAlbumStarred() {
    val seed = currentAlbum ?: initialAlbumSeed
    val starred = seed.toStarredSheet()
    val wasStarred = isAlbumStarred.value
    viewModelScope.launch {
        starredSheetRepository.toggle(starred)
        com.zili.android.musicfreeandroid.logging.MfLog.detail(
            category = com.zili.android.musicfreeandroid.logging.LogCategory.APP,
            event = if (wasStarred) "starred_removed" else "starred_added",
            fields = mapOf(
                "kind" to com.zili.android.musicfreeandroid.core.model.StarredKind.ALBUM,
                "platform" to starred.platform,
                "id" to starred.id,
                "source" to "detail_album",
            ),
        )
    }
}
```

- [ ] **Step 2: Add the heart `IconButton` to the screen**

Open `AlbumDetailScreen.kt`. Add imports:

```kotlin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.painterResource
import com.zili.android.musicfreeandroid.core.R
```

Inside the `AlbumDetailScreen(...)` composable, before the `MusicFreeScreenScaffold(...)` call, add:

```kotlin
val isStarred by viewModel.isAlbumStarred.collectAsStateWithLifecycle()
```

Replace the current `MusicFreeScreenScaffold(...)` invocation header (currently passing only `title`, `onBack`, `modifier`) with one that adds an `actions = { ... }` slot:

```kotlin
MusicFreeScreenScaffold(
    title = uiState.title,
    onBack = onBack,
    modifier = modifier.fillMaxSize(),
    actions = {
        IconButton(onClick = { viewModel.toggleAlbumStarred() }) {
            Icon(
                painter = painterResource(
                    id = if (isStarred) R.drawable.ic_heart else R.drawable.ic_heart_outline,
                ),
                contentDescription = if (isStarred) "取消收藏专辑" else "收藏专辑",
                tint = if (isStarred) MusicFreeTheme.colors.primary else MusicFreeTheme.colors.appBarText,
            )
        }
    },
) { innerPadding ->
    // ... existing content unchanged
```

Match the existing indentation. Do not change anything inside the inner content lambda.

- [ ] **Step 3: Compile**

Run: `./gradlew :feature:home:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run feature tests**

Run: `./gradlew :feature:home:testDebugUnitTest`
Expected: all green (existing tests + new ones).

- [ ] **Step 5: Commit**

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailScreen.kt \
        feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/albumdetail/AlbumDetailViewModel.kt
git commit -m "feat(home): add heart star button to AlbumDetailScreen with logging"
```

---

## Task 10: `HomeSheetUiModel` carries `kind`, `toAlbumItemBase()`

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetUiModel.kt`
- Modify: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetUiModelTest.kt`

- [ ] **Step 1: Update test (add new assertion + new test for album conversion)**

Open `HomeSheetUiModelTest.kt`. Add to the existing class:

```kotlin
import com.zili.android.musicfreeandroid.core.model.StarredKind
import com.zili.android.musicfreeandroid.plugin.api.AlbumItemBase

@Test
fun `fromStarredSheet propagates kind ALBUM`() {
    val sheet = StarredSheet(
        id = "alb-1", platform = "qq",
        title = "Album", artist = "X", coverUri = null, sourceUrl = null,
        kind = StarredKind.ALBUM,
    )
    val ui = HomeSheetUiModel.fromStarredSheet(sheet)
    assertEquals(StarredKind.ALBUM, ui.kind)
}

@Test
fun `toAlbumItemBase reconstructs identity and merges sourceUrl`() {
    val ui = HomeSheetUiModel(
        id = "alb-2", platform = "qq",
        kind = StarredKind.ALBUM,
        tab = HomeSheetTab.Starred,
        title = "AlbumTwo",
        subtitle = "ArtistTwo",
        coverUri = "art://2",
        artist = "ArtistTwo",
        sourceUrl = "https://example.com/2",
        description = "desc",
        artwork = "art://2",
        worksNum = 5,
        raw = mapOf("foo" to "bar"),
    )
    val album: AlbumItemBase = ui.toAlbumItemBase()
    assertEquals("alb-2", album.id)
    assertEquals("qq", album.platform)
    assertEquals("AlbumTwo", album.title)
    assertEquals("ArtistTwo", album.artist)
    assertEquals("art://2", album.artwork)
    assertEquals(5, album.worksNum)
    assertEquals("https://example.com/2", album.raw["sourceUrl"])
    assertEquals("bar", album.raw["foo"])
}
```

- [ ] **Step 2: Run test, verify failure**

Run: `./gradlew :feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetUiModelTest"`
Expected: compile failure on `kind` parameter / `toAlbumItemBase()`.

- [ ] **Step 3: Modify `HomeSheetUiModel.kt`**

Replace the file content with:

```kotlin
package com.zili.android.musicfreeandroid.feature.home.sheets

import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.model.StarredKind
import com.zili.android.musicfreeandroid.core.model.StarredSheet
import com.zili.android.musicfreeandroid.plugin.api.AlbumItemBase
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase

data class HomeSheetUiModel(
    val id: String,
    val platform: String?,
    val tab: HomeSheetTab,
    val title: String,
    val subtitle: String,
    val coverUri: String?,
    val isDefault: Boolean = false,
    val kind: String = StarredKind.SHEET,
    val artist: String? = null,
    val sourceUrl: String? = null,
    val description: String? = null,
    val artwork: String? = null,
    val worksNum: Int? = null,
    val raw: Map<String, Any?> = emptyMap(),
) {
    companion object {
        fun fromPlaylist(playlist: Playlist, musicCount: Int, isDefault: Boolean = false): HomeSheetUiModel = HomeSheetUiModel(
            id = playlist.id,
            platform = null,
            tab = HomeSheetTab.Mine,
            title = playlist.name,
            subtitle = "${musicCount}首",
            coverUri = playlist.coverUri,
            isDefault = isDefault,
        )

        fun fromStarredSheet(sheet: StarredSheet): HomeSheetUiModel = HomeSheetUiModel(
            id = sheet.id,
            platform = sheet.platform,
            tab = HomeSheetTab.Starred,
            title = sheet.title,
            subtitle = sheet.artist ?: sheet.platform,
            coverUri = sheet.coverUri,
            kind = sheet.kind,
            artist = sheet.artist,
            sourceUrl = sheet.sourceUrl,
            description = sheet.description,
            artwork = sheet.artwork,
            worksNum = sheet.worksNum,
            raw = sheet.raw,
        )
    }
}

fun HomeSheetUiModel.toMusicSheetItemBase(): MusicSheetItemBase {
    val platform = requireNotNull(platform) { "Starred sheet rows require a plugin platform" }
    val mergedRaw = raw.toMutableMap()
    sourceUrl?.let { mergedRaw.putIfAbsent("sourceUrl", it) }
    return MusicSheetItemBase(
        id = id,
        platform = platform,
        title = title,
        artist = artist,
        description = description,
        coverImg = coverUri,
        artwork = artwork,
        worksNum = worksNum,
        raw = mergedRaw,
    )
}

fun HomeSheetUiModel.toAlbumItemBase(): AlbumItemBase {
    val platform = requireNotNull(platform) { "Starred album rows require a plugin platform" }
    val mergedRaw = raw.toMutableMap()
    sourceUrl?.let { mergedRaw.putIfAbsent("sourceUrl", it) }
    return AlbumItemBase(
        id = id,
        platform = platform,
        title = title,
        artist = artist,
        description = description,
        artwork = artwork,
        date = raw["date"] as? String,
        worksNum = worksNum,
        raw = mergedRaw,
    )
}
```

- [ ] **Step 4: Run test, expect PASS**

Run: same as Step 2.

- [ ] **Step 5: Commit**

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetUiModel.kt \
        feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetUiModelTest.kt
git commit -m "feat(home): HomeSheetUiModel carries kind and converts to AlbumItemBase"
```

---

## Task 11: Wire trash + branch nav in `HomeSheetsList`, `HomeSheetsSection`, ViewModel, Screen

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsList.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsSection.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsViewModel.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreenContent.kt`
- Modify: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsViewModelTest.kt`

- [ ] **Step 1: Update `HomeSheetsViewModelTest.kt` to cover `unstar` (TDD)**

Append to the existing test class:

```kotlin
import com.zili.android.musicfreeandroid.core.model.StarredKind
import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetTab
import org.mockito.kotlin.verify

@Test
fun `unstar invokes repository deleteByIdAndPlatform for starred row`() = runTest {
    whenever(playlistRepository.observeAllPlaylists()).thenReturn(flowOf(emptyList()))
    whenever(starredSheetRepository.observeAll()).thenReturn(flowOf(listOf(
        StarredSheet(
            id = "alb-1", platform = "qq",
            title = "AlbumOne", artist = null, coverUri = null, sourceUrl = null,
            kind = StarredKind.ALBUM,
        ),
    )))

    val viewModel = HomeSheetsViewModel(playlistRepository, starredSheetRepository)
    advanceUntilIdle()
    viewModel.selectTab(HomeSheetTab.Starred)
    advanceUntilIdle()

    val row = viewModel.uiState.value.items.single()
    viewModel.unstar(row)
    advanceUntilIdle()

    verify(starredSheetRepository).deleteByIdAndPlatform(id = "alb-1", platform = "qq")
}

@Test
fun `unstar ignores rows without platform`() = runTest {
    whenever(playlistRepository.observeAllPlaylists()).thenReturn(flowOf(emptyList()))
    whenever(starredSheetRepository.observeAll()).thenReturn(flowOf(emptyList()))

    val viewModel = HomeSheetsViewModel(playlistRepository, starredSheetRepository)
    advanceUntilIdle()

    val mineRow = HomeSheetUiModel(
        id = "fav", platform = null, tab = HomeSheetTab.Mine,
        title = "我喜欢", subtitle = "0首", coverUri = null,
    )
    viewModel.unstar(mineRow)
    advanceUntilIdle()

    org.mockito.kotlin.verifyNoInteractions(starredSheetRepository.also { /* keep mock graph clean */ })
    // Note: starredSheetRepository.observeAll() was already called by VM init; we only assert no delete:
    org.mockito.kotlin.verify(starredSheetRepository, org.mockito.kotlin.never())
        .deleteByIdAndPlatform(org.mockito.kotlin.any(), org.mockito.kotlin.any())
}
```

(If `verifyNoInteractions(...)` collides with the earlier observeAll() call, drop that single line and rely on the `verify(..., never()).deleteByIdAndPlatform(...)` assertion; it carries the actual contract.)

- [ ] **Step 2: Run test, expect compile fail (no `unstar` method)**

Run: `./gradlew :feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetsViewModelTest"`
Expected: compile fail.

- [ ] **Step 3: Add `unstar` to `HomeSheetsViewModel.kt`**

Inside the class, after the existing `selectTab` function, add:

```kotlin
fun unstar(item: HomeSheetUiModel) {
    val platform = item.platform ?: return
    viewModelScope.launch {
        starredSheetRepository.deleteByIdAndPlatform(id = item.id, platform = platform)
        com.zili.android.musicfreeandroid.logging.MfLog.detail(
            category = com.zili.android.musicfreeandroid.logging.LogCategory.APP,
            event = "starred_removed",
            fields = mapOf(
                "kind" to item.kind,
                "platform" to platform,
                "id" to item.id,
                "source" to "home_starred_trash",
            ),
        )
    }
}
```

Add `import kotlinx.coroutines.launch` if not already imported.

- [ ] **Step 4: Run test, expect PASS**

Same command as Step 2.

- [ ] **Step 5: Update `HomeSheetsList.kt` to accept `onTrashClick` and branch by `kind`**

Replace the `homeSheetsList` LazyListScope function and `HomeSheetRow` composable:

```kotlin
fun LazyListScope.homeSheetsList(
    uiModel: HomePlaylistSectionUiModel,
    onOpenMineSheet: (String) -> Unit,
    onOpenStarredSheet: (HomeSheetUiModel) -> Unit,
    onOpenStarredAlbum: (HomeSheetUiModel) -> Unit,
    onTrashClick: (HomeSheetUiModel) -> Unit,
) {
    items(
        items = uiModel.rows,
        key = { item -> "${item.tab}:${item.id}" },
    ) { item ->
        HomeSheetRow(
            item = item,
            modifier = Modifier.padding(horizontal = rpx(24)),
            onClick = {
                if (item.tab == HomeSheetTab.Mine) {
                    onOpenMineSheet(item.id)
                } else when (item.kind) {
                    com.zili.android.musicfreeandroid.core.model.StarredKind.ALBUM ->
                        onOpenStarredAlbum(item)
                    else -> onOpenStarredSheet(item)
                }
            },
            onTrashClick = { onTrashClick(item) },
        )
    }
}

@Composable
private fun HomeSheetRow(
    item: HomeSheetUiModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onTrashClick: () -> Unit,
) {
    val rowTag = if (item.tab == HomeSheetTab.Mine) {
        FidelityAnchorPatterns.mineSheetItem(item.id)
    } else {
        FidelityAnchorPatterns.starredSheetItem(item.id)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .homeInteractionStyle(
                onClick = onClick,
                shape = RoundedCornerShape(rpx(18)),
                minHeight = null,
            )
            .testTag(rowTag)
            .semantics { testTagsAsResourceId = true }
            .padding(vertical = rpx(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            CoverImage(
                uri = item.coverUri,
                size = rpx(96),
                cornerRadius = rpx(10),
            )
            if (item.isDefault) {
                Icon(
                    painter = painterResource(R.drawable.ic_home_heart),
                    contentDescription = null,
                    tint = MusicFreeTheme.colors.primary,
                    modifier = Modifier
                        .size(rpx(36))
                        .align(Alignment.Center),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = rpx(20), end = rpx(12)),
        ) {
            Text(
                text = item.title,
                color = MusicFreeTheme.colors.text,
                fontSize = FontSizes.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle,
                color = MusicFreeTheme.colors.textSecondary,
                fontSize = FontSizes.description,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = rpx(6)),
            )
        }
        if (item.tab == HomeSheetTab.Starred && !item.platform.isNullOrBlank()) {
            Text(
                text = item.platform,
                color = MusicFreeTheme.colors.textSecondary,
                fontSize = FontSizes.tag,
                modifier = Modifier
                    .border(
                        border = BorderStroke(1.dp, MusicFreeTheme.colors.placeholder),
                        shape = RoundedCornerShape(rpx(8)),
                    )
                    .padding(horizontal = rpx(10), vertical = rpx(4)),
            )
        }
        if (!item.isDefault && item.tab == HomeSheetTab.Starred) {
            Icon(
                painter = painterResource(R.drawable.ic_home_trash_outline),
                contentDescription = "取消收藏",
                tint = MusicFreeTheme.colors.textSecondary,
                modifier = Modifier
                    .size(rpx(42))
                    .clickable(onClick = onTrashClick),
            )
        }
    }
}
```

Add the missing import: `import androidx.compose.foundation.clickable`. Note the trash icon is now scoped to the Starred tab only, since this PR doesn't define Mine-tab playlist deletion (the spec's non-goals).

- [ ] **Step 6: Update `HomeSheetsSection.kt`**

Open the file and locate the `homeSheetsSection` extension. Add `onOpenStarredAlbum: (HomeSheetUiModel) -> Unit` and `onTrashClick: (HomeSheetUiModel) -> Unit` parameters next to existing `onOpenStarredSheet`. Pass them through to the inner `homeSheetsList(...)` call.

(Read the file first to apply the minimum-correct surface change. Don't reorder existing parameters; append new ones at the end with default values where possible to minimize call-site churn — but Hilt-injected screen wiring will be updated in Step 7 anyway, so explicit parameters with no defaults are also fine.)

- [ ] **Step 7: Update `HomeScreenContent.kt` and `HomeScreen.kt`**

In `HomeScreenContent.kt`, add `onOpenStarredAlbum: (HomeSheetUiModel) -> Unit` and `onTrashClick: (HomeSheetUiModel) -> Unit` parameters to `HomeScreenContent`, threading through to `homeSheetsSection`.

In `HomeScreen.kt`:
- Add `onNavigateToStarredAlbum: (HomeSheetUiModel) -> Unit` to the `HomeScreen(...)` parameter list (place next to `onNavigateToStarredSheet`).
- Inject `homeSheetsViewModel: HomeSheetsViewModel = hiltViewModel()` (if not already; check the file). The current snippet shows only `viewModel: HomeViewModel` — we need access to `unstar(...)`. Either get it via `hiltViewModel<HomeSheetsViewModel>()` or add an `unstar` callback parameter wired by `AppNavHost`. Prefer the local `hiltViewModel` because nav already constructs `HomeSheetsSection` from this scope.
- Add a `var pendingUnstar by remember { mutableStateOf<HomeSheetUiModel?>(null) }` state.
- In the `HomeScreenContent(...)` call, supply:
  - `onOpenStarredAlbum = onNavigateToStarredAlbum`
  - `onTrashClick = { row -> pendingUnstar = row }`
- After the existing `if (showCreateDialog) { ... }` block but before `PlaylistImportRoute(...)`, add:

```kotlin
pendingUnstar?.let { row ->
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { pendingUnstar = null },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                homeSheetsViewModel.unstar(row)
                pendingUnstar = null
            }) {
                androidx.compose.material3.Text("确定")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { pendingUnstar = null }) {
                androidx.compose.material3.Text("取消")
            }
        },
        title = { androidx.compose.material3.Text("取消收藏") },
        text = { androidx.compose.material3.Text("确定要取消收藏「${row.title}」吗？") },
    )
}
```

Add `androidx.compose.runtime.LaunchedEffect`-style imports as needed; if `homeSheetsViewModel` is not in scope, add `val homeSheetsViewModel: HomeSheetsViewModel = hiltViewModel()` near the existing `viewModel` declaration.

Also add a log for the dialog showing — inside the `onTrashClick` lambda set inline:

```kotlin
onTrashClick = { row ->
    pendingUnstar = row
    com.zili.android.musicfreeandroid.logging.MfLog.detail(
        category = com.zili.android.musicfreeandroid.logging.LogCategory.APP,
        event = "starred_unstar_confirm_shown",
        fields = mapOf(
            "kind" to row.kind,
            "platform" to (row.platform ?: ""),
            "id" to row.id,
        ),
    )
},
```

- [ ] **Step 8: Update `AppNavHost.kt` to provide `onNavigateToStarredAlbum`**

Open `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`. Locate the `HomeScreen(...)` invocation around the existing `onNavigateToStarredSheet = { row -> ... PluginSheetSeedStore.put(...) }` block. Right after that callback, add:

```kotlin
onNavigateToStarredAlbum = { row ->
    val album = row.toAlbumItemBase()
    val seedToken = AlbumDetailSeedStore.put(album)
    navController.navigate(
        AlbumDetailRoute(
            pluginPlatform = album.platform,
            albumId = album.id,
            title = album.title,
            artist = album.artist,
            artwork = album.artwork,
            date = album.date,
            description = album.description,
            worksNum = album.worksNum,
            seedToken = seedToken,
        )
    )
},
```

Required imports (verify they are already there from Step `grep -n` earlier — `AlbumDetailRoute`, `AlbumDetailSeedStore` — both exist; just add `import com.zili.android.musicfreeandroid.feature.home.sheets.toAlbumItemBase`).

- [ ] **Step 9: Compile and run all home tests**

Run: `./gradlew :feature:home:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsList.kt \
        feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsSection.kt \
        feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsViewModel.kt \
        feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt \
        feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreenContent.kt \
        feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/sheets/HomeSheetsViewModelTest.kt \
        app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt
git commit -m "feat(home): branch starred row nav by kind, wire unstar dialog with logging"
```

---

## Task 12: Add `starred_added` log to plugin sheet detail toggle

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailViewModel.kt`

The spec calls for symmetric logging on the existing sheet toggle path.

- [ ] **Step 1: Replace `toggleSheetStarred()`**

Replace the existing function body in `PluginSheetDetailViewModel.kt`:

```kotlin
fun toggleSheetStarred() {
    val sheet = (currentSheet ?: seedSheet()).toStarredSheet()
    val wasStarred = isSheetStarred.value
    viewModelScope.launch {
        starredSheetRepository.toggle(sheet)
        com.zili.android.musicfreeandroid.logging.MfLog.detail(
            category = com.zili.android.musicfreeandroid.logging.LogCategory.APP,
            event = if (wasStarred) "starred_removed" else "starred_added",
            fields = mapOf(
                "kind" to com.zili.android.musicfreeandroid.core.model.StarredKind.SHEET,
                "platform" to sheet.platform,
                "id" to sheet.id,
                "source" to "detail_sheet",
            ),
        )
    }
}
```

- [ ] **Step 2: Compile and run feature tests**

Run: `./gradlew :feature:home:testDebugUnitTest`
Expected: green.

- [ ] **Step 3: Commit**

```bash
git add feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/pluginsheet/PluginSheetDetailViewModel.kt
git commit -m "feat(home): log starred_added/removed on plugin sheet detail toggle"
```

---

## Task 13: Full validation

**Files:**
- N/A

- [ ] **Step 1: Compile data + feature + app**

Run: `./gradlew :data:compileDebugKotlin :feature:home:compileDebugKotlin :app:compileDebugKotlin`
Expected: all SUCCESSFUL.

- [ ] **Step 2: Run all unit tests touched modules**

Run: `./gradlew :data:testDebugUnitTest :feature:home:testDebugUnitTest`
Expected: all green.

- [ ] **Step 3: Assemble debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Dev-harness grep check**

Run: `python3 scripts/dev-harness/grep-check.py`
Expected: no violations.

- [ ] **Step 5 (manual, after install on emulator): runtime smoke**

Install the freshly built APK from `app/build/outputs/apk/debug/`. Steps:

1. Install at least one plugin that returns sheet + album results (any of the dev fixtures).
2. Search a query that yields both. Verify:
   - Open a **sheet** result → tap heart in detail header → return to home → "我的收藏" tab shows the sheet row with platform tag → tap trash → confirm dialog → row disappears.
   - Open an **album** result → tap heart in detail header → return to home → "我的收藏" tab shows the album row with platform tag → tap the row → opens `AlbumDetailScreen` (not `PluginSheetDetailScreen`) → tap heart again to unstar → row disappears.
3. Confirm no `AndroidRuntime` crashes in `adb logcat`.

(If no device/emulator is available, skip Step 5 and surface that explicitly in the wrap-up.)

- [ ] **Step 6: Push branch**

Run from worktree:

```bash
git push -u origin feat/favorite-album-and-starred-trash
```

---

## Self-Review

**Spec coverage:**

| Spec section | Implemented in |
|---|---|
| Goal #1: heart in `AlbumDetailScreen` | Tasks 8, 9 |
| Goal #2: collected albums appear in 我的收藏 tab | Tasks 3, 6, 7, 10, 11 (uses same Starred Flow) |
| Goal #3: trash → confirm → unstar | Task 11 (steps 5, 7) |
| Goal #4: kind-aware nav branching | Task 11 (steps 5, 8) |
| Goal #5: existing sheet path preserved | Tasks 6, 12 (mapper kept compatible) |
| Data: `kind` column + bump v7 | Task 3 |
| Mappers: AlbumStarredMapper + PluginSheetStarredMapper kind | Tasks 6, 7 |
| ViewModel: AlbumDetail star observation + toggle | Task 8 |
| ViewModel: HomeSheets unstar | Task 11 |
| UI: heart icon, trash with onClick, AlertDialog | Tasks 9, 11 |
| Logging events | Tasks 9, 11 (step 7), 12 |
| Tests: mapper, repository, dao, viewmodel, ui-model | Tasks 4, 5, 6, 7, 8, 10, 11 |

**Placeholder scan:** No "TBD"/"TODO"/"similar to". Each step has full code. Step 8/Step 7 of Task 11 includes a fallback recipe paragraph if `SavedStateHandle` route construction is opaque — that's an explicit branch, not a placeholder.

**Type consistency:**
- `StarredKind.SHEET` / `StarredKind.ALBUM` consistent everywhere.
- `StarredSheet.kind` and `StarredSheetEntity.kind` field names match.
- `toStarredSheet()` extension exists on both `MusicSheetItemBase` (Task 6) and `AlbumItemBase` (Task 7).
- `HomeSheetUiModel.kind` and `toAlbumItemBase()` consistent across Tasks 10 and 11.
- `HomeSheetsViewModel.unstar(item: HomeSheetUiModel)` signature consistent in Tasks 11 and tests.
- `AlbumDetailViewModel.isAlbumStarred` / `toggleAlbumStarred` names consistent across Tasks 8, 9.
- Log event names `starred_added`, `starred_removed`, `starred_unstar_confirm_shown` consistent across Tasks 9, 11, 12.
- `LogCategory.APP` consistent across Tasks 9, 11, 12.

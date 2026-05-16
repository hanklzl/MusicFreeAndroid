# 歌单封面与详情行展示对齐 RN 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把歌单封面字段语义统一为 Coil-ready 字符串（http(s) URL 透传 + 用户选图存 file:// 绝对路径），并在 `PlaylistDetailScreen` 用新抽出的 `:core/ui/MusicItemRow` 展示 platform tag 与 album 信息，全部对齐 RN 行为。

**Architecture:** 两条独立改动路径，先后落 5 个 commit。**数据层**改 `PlaylistCoverStore`（http/https 直接回传 URL；本地源走 `saveFromUri` 返回 `file://` 绝对 URI）+ `PlaylistMapper` 增加可选的老数据 resolver 把相对路径包成 `file://` + `PlaylistRepository` 在三处 `toModel` 调用站点注入 resolver。**UI 层**新增 `:core/ui/PlatformTag` 与 `:core/ui/MusicItemRow` 两个纯展示 composable，`PlaylistDetailScreen` 删掉私有 `PlaylistRow` 改用 `MusicItemRow`。无 schema 变更，无新跨模块依赖。

**Tech Stack:** Kotlin, Jetpack Compose Material3, Hilt, Room, Coil 3, JUnit4, Compose UI Test, AndroidX Test (instrumented).

**Source spec:** `docs/superpowers/specs/2026-05-05-playlist-cover-and-row-display-design.md`

---

## File Structure

| 路径 | 动作 | 责任 |
|---|---|---|
| `data/src/main/java/com/hank/musicfree/data/cover/PlaylistCoverStore.kt` | Modify | `copyFromArtwork` 加 http(s) 透传分支；`saveFromUri` 返回 file:// 绝对 URI |
| `data/src/androidTest/java/com/hank/musicfree/data/cover/PlaylistCoverStoreTest.kt` | Modify | 现有 2 个 case 断言改 file://；新增 4 个 scheme case |
| `data/src/main/java/com/hank/musicfree/data/mapper/PlaylistMapper.kt` | Modify | `toModel` 增加可选 `legacyCoverResolver` 参数 |
| `data/src/test/java/com/hank/musicfree/data/mapper/PlaylistMapperTest.kt` | Modify | 新增 resolver 行为 case |
| `data/src/main/java/com/hank/musicfree/data/repository/PlaylistRepository.kt` | Modify | 三处 `toModel` 调用站点注入 resolver |
| `data/src/androidTest/java/com/hank/musicfree/data/repository/PlaylistRepositoryTest.kt` | Modify | 改两处 `startsWith("playlist_covers/")` 断言；新增一条 https artwork case |
| `core/src/main/java/com/hank/musicfree/core/ui/PlatformTag.kt` | Create | RN-vibe 描边小药丸 composable |
| `core/src/androidTest/java/com/hank/musicfree/core/ui/PlatformTagTest.kt` | Create | 渲染断言 |
| `core/src/main/java/com/hank/musicfree/core/ui/MusicItemRow.kt` | Create | 标题+platform tag / artist-album / 行末 ⋮ 通用行 |
| `core/src/androidTest/java/com/hank/musicfree/core/ui/MusicItemRowTest.kt` | Create | platform tag、本地映射、artist-album 拼接、actions 透传 |
| `feature/home/src/main/java/com/hank/musicfree/feature/home/playlist/PlaylistDetailScreen.kt` | Modify | 删除私有 `PlaylistRow`，`LazyColumn.items` 改用 `MusicItemRow` |

无 entity / DAO / DB schema 变更。

---

## Task 0：worktree 初始化

**Files:**
- 仓库根 `.worktrees/feat-playlist-cover-and-row/`（自动生成，目录已被 `.gitignore` 忽略）

- [ ] **Step 1: 在主仓库创建 worktree**

```bash
cd /Users/zili/code/android/MusicFreeAndroid
git worktree add -b feat/playlist-cover-and-row .worktrees/feat-playlist-cover-and-row main
```

Expected: `Preparing worktree (new branch 'feat/playlist-cover-and-row')`

- [ ] **Step 2: 切到 worktree 并校验起点**

```bash
cd .worktrees/feat-playlist-cover-and-row
git status
git log --oneline -3
```

Expected: 工作树干净；HEAD 与 `main` 一致（最近一条是 spec commit `1ef7b30 docs(playlist): add cover and row-display alignment spec`）。

- [ ] **Step 3: 健全性构建**

```bash
./gradlew :data:compileDebugKotlin :core:compileDebugKotlin --no-daemon -q
```

Expected: BUILD SUCCESSFUL（确认起点可编译）。如失败，先停下来排查。

> 后续所有 Task 的 `git` 与 `gradle` 命令默认在 `.worktrees/feat-playlist-cover-and-row/` 下执行。

---

## Task 1：`PlaylistCoverStore` http(s) 透传 + file:// 绝对 URI

**Files:**
- Modify: `data/src/androidTest/java/com/hank/musicfree/data/cover/PlaylistCoverStoreTest.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/cover/PlaylistCoverStore.kt`

- [ ] **Step 1: 改写 `PlaylistCoverStoreTest`**

完整覆盖 `data/src/androidTest/java/com/hank/musicfree/data/cover/PlaylistCoverStoreTest.kt`：

```kotlin
package com.hank.musicfree.data.cover

import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

    @Test fun saveFromUri_writesFile_andReturnsFileUri() = runBlocking {
        val src = createTempImage("origin.jpg")
        val out = store.saveFromUri(playlistId = "plistA", src = src.toUri())
        assertNotNull(out)
        val expected = "file://" + File(baseDir, "plistA.jpg").absolutePath
        assertEquals(expected, out)
        assertTrue(File(baseDir, "plistA.jpg").exists())
    }

    @Test fun delete_removesFile() = runBlocking {
        val src = createTempImage("origin.jpg")
        store.saveFromUri("plistA", src.toUri())
        store.delete("plistA")
        assertTrue(!File(baseDir, "plistA.jpg").exists())
    }

    @Test fun copyFromArtwork_passesThroughHttpsUrl() = runBlocking {
        val url = "https://example.com/cover.jpg"
        val out = store.copyFromArtwork("plistA", url)
        assertEquals(url, out)
        assertTrue("no file should be written", !File(baseDir, "plistA.jpg").exists())
    }

    @Test fun copyFromArtwork_passesThroughHttpUrl() = runBlocking {
        val url = "http://example.com/cover.jpg"
        val out = store.copyFromArtwork("plistA", url)
        assertEquals(url, out)
    }

    @Test fun copyFromArtwork_savesFileUriToDisk() = runBlocking {
        val src = createTempImage("art.jpg")
        val out = store.copyFromArtwork("plistA", "file://${src.absolutePath}")
        val expected = "file://" + File(baseDir, "plistA.jpg").absolutePath
        assertEquals(expected, out)
        assertTrue(File(baseDir, "plistA.jpg").exists())
    }

    @Test fun copyFromArtwork_returnsNullForBlankOrUnknownScheme() = runBlocking {
        assertNull(store.copyFromArtwork("plistA", null))
        assertNull(store.copyFromArtwork("plistA", ""))
        assertNull(store.copyFromArtwork("plistA", "asset://x.jpg"))
    }

    private fun createTempImage(name: String): File =
        File(context.cacheDir, name).apply { writeBytes(ByteArray(64) { 1 }) }
}
```

- [ ] **Step 2: 跑测试确认它失败**

```bash
./gradlew :data:connectedDebugAndroidTest --tests "com.hank.musicfree.data.cover.PlaylistCoverStoreTest" --no-daemon
```

Expected: 至少 `saveFromUri_writesFile_andReturnsFileUri`、`copyFromArtwork_passesThroughHttpsUrl`、`copyFromArtwork_passesThroughHttpUrl`、`copyFromArtwork_savesFileUriToDisk` 失败；`copyFromArtwork_returnsNullForBlankOrUnknownScheme` 可能通过（旧实现也返回 null）。

> 需要连一台模拟器/设备。如果机器上无可用 device，临时用 `:data:assembleDebugAndroidTest` 至少确认 testcase 编译通过（语法层 fail-fast），再在拿到 device 后跑实测。

- [ ] **Step 3: 实现新 `PlaylistCoverStore`**

完整覆盖 `data/src/main/java/com/hank/musicfree/data/cover/PlaylistCoverStore.kt`：

```kotlin
package com.hank.musicfree.data.cover

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistCoverStore @Inject constructor(
    @ApplicationContext private val context: Context,
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
        if (dest.exists() && dest.length() > 0) Uri.fromFile(dest).toString() else null
    }

    suspend fun copyFromArtwork(playlistId: String, artworkUrl: String?): String? {
        if (artworkUrl.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(artworkUrl) }.getOrNull() ?: return null
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> artworkUrl
            "file", "content" -> saveFromUri(playlistId, uri)
            else -> null
        }
    }

    suspend fun delete(playlistId: String) = withContext(Dispatchers.IO) {
        File(baseDir, "$playlistId.jpg").delete()
        Unit
    }

    fun absoluteFile(relativePath: String): File = File(context.filesDir, relativePath)

    companion object { const val BASE_DIR_NAME = "playlist_covers" }
}
```

- [ ] **Step 4: 跑测试确认它通过**

```bash
./gradlew :data:connectedDebugAndroidTest --tests "com.hank.musicfree.data.cover.PlaylistCoverStoreTest" --no-daemon
```

Expected: 所有 6 个 case 通过。

- [ ] **Step 5: Commit**

```bash
git add data/src/main/java/com/hank/musicfree/data/cover/PlaylistCoverStore.kt \
        data/src/androidTest/java/com/hank/musicfree/data/cover/PlaylistCoverStoreTest.kt
git commit -m "$(cat <<'EOF'
fix(data): pass through http(s) artwork and emit file:// for picked covers

PlaylistCoverStore.copyFromArtwork now returns http/https URLs as-is so plugin
artwork can populate playlist coverUri directly (matches RN behavior). Local
file/content sources continue to be copied to playlist_covers/, but saveFromUri
now returns the absolute file:// URI so Coil can render it without resolution.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2：`PlaylistMapper` 增加 legacy cover resolver

**Files:**
- Modify: `data/src/test/java/com/hank/musicfree/data/mapper/PlaylistMapperTest.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/mapper/PlaylistMapper.kt`

- [ ] **Step 1: 在 `PlaylistMapperTest` 末尾追加 resolver 行为 case**

把这段加到 `data/src/test/java/com/hank/musicfree/data/mapper/PlaylistMapperTest.kt` 类内（在 `parseSortMode falls back to Manual for unknown value` 之后、闭花括号之前）：

```kotlin
@Test
fun `toModel without resolver preserves coverUri verbatim`() {
    val entity = PlaylistEntity(
        id = "p1", name = "Mix",
        coverUri = "playlist_covers/p1.jpg",
        description = null, sortMode = "Manual",
        createdAt = 0L, updatedAt = 0L,
    )
    assertEquals("playlist_covers/p1.jpg", entity.toModel().coverUri)
}

@Test
fun `toModel applies resolver only when it returns non-null`() {
    val entity = PlaylistEntity(
        id = "p1", name = "Mix",
        coverUri = "playlist_covers/p1.jpg",
        description = null, sortMode = "Manual",
        createdAt = 0L, updatedAt = 0L,
    )
    val resolved = entity.toModel(legacyCoverResolver = { raw ->
        if (raw.startsWith("playlist_covers/")) "file:///abs/$raw" else null
    })
    assertEquals("file:///abs/playlist_covers/p1.jpg", resolved.coverUri)
}

@Test
fun `toModel resolver returning null falls back to raw value`() {
    val entity = PlaylistEntity(
        id = "p1", name = "Mix",
        coverUri = "https://example.com/cover.jpg",
        description = null, sortMode = "Manual",
        createdAt = 0L, updatedAt = 0L,
    )
    val resolved = entity.toModel(legacyCoverResolver = { _ -> null })
    assertEquals("https://example.com/cover.jpg", resolved.coverUri)
}

@Test
fun `toModel preserves null coverUri regardless of resolver`() {
    val entity = PlaylistEntity(
        id = "p1", name = "Empty",
        coverUri = null,
        description = null, sortMode = "Manual",
        createdAt = 0L, updatedAt = 0L,
    )
    val resolved = entity.toModel(legacyCoverResolver = { _ -> "file:///should/not/show" })
    assertEquals(null, resolved.coverUri)
}
```

- [ ] **Step 2: 跑测试确认它失败**

```bash
./gradlew :data:testDebugUnitTest --tests "com.hank.musicfree.data.mapper.PlaylistMapperTest" --no-daemon
```

Expected: 后三个新 case 编译失败（`legacyCoverResolver` 命名参数不存在）。

- [ ] **Step 3: 修改 `PlaylistMapper.kt`**

完整覆盖 `data/src/main/java/com/hank/musicfree/data/mapper/PlaylistMapper.kt`：

```kotlin
package com.hank.musicfree.data.mapper

import com.hank.musicfree.core.model.Playlist
import com.hank.musicfree.core.model.SortMode
import com.hank.musicfree.data.db.entity.PlaylistEntity

fun Playlist.toEntity(createdAt: Long, updatedAt: Long): PlaylistEntity = PlaylistEntity(
    id = id,
    name = name,
    coverUri = coverUri,
    description = description,
    sortMode = sortMode.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PlaylistEntity.toModel(
    worksNum: Int = 0,
    legacyCoverResolver: ((String) -> String?)? = null,
): Playlist = Playlist(
    id = id,
    name = name,
    coverUri = coverUri?.let { raw -> legacyCoverResolver?.invoke(raw) ?: raw },
    description = description,
    sortMode = parseSortMode(sortMode),
    createdAt = createdAt,
    updatedAt = updatedAt,
    worksNum = worksNum,
)

private fun parseSortMode(name: String): SortMode =
    runCatching { SortMode.valueOf(name) }.getOrDefault(SortMode.Manual)
```

> Note: 原有调用方（`PlaylistRepository`、其它测试）不传 `legacyCoverResolver` 时行为完全等价于旧实现，二进制兼容 OK。Task 3 才会在 Repository 注入 resolver。

- [ ] **Step 4: 跑测试确认全部通过**

```bash
./gradlew :data:testDebugUnitTest --tests "com.hank.musicfree.data.mapper.PlaylistMapperTest" --no-daemon
```

Expected: 7 个 case 全过（旧 4 + 新 4 = 8。原来已有 4 个，新增 4 个）。

- [ ] **Step 5: Commit**

```bash
git add data/src/main/java/com/hank/musicfree/data/mapper/PlaylistMapper.kt \
        data/src/test/java/com/hank/musicfree/data/mapper/PlaylistMapperTest.kt
git commit -m "$(cat <<'EOF'
feat(data): add optional legacyCoverResolver to PlaylistMapper.toModel

Allows callers (specifically PlaylistRepository) to translate legacy relative
coverUri strings ('playlist_covers/<id>.jpg') into Coil-ready file:// URIs at
read time. Default null preserves existing behavior so other callers and the
existing test suite stay unchanged.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3：`PlaylistRepository` 注入 legacy cover resolver

**Files:**
- Modify: `data/src/androidTest/java/com/hank/musicfree/data/repository/PlaylistRepositoryTest.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/repository/PlaylistRepository.kt`

- [ ] **Step 1: 改 `PlaylistRepositoryTest` 现有 cover 断言 + 新增 https artwork case**

修改 `data/src/androidTest/java/com/hank/musicfree/data/repository/PlaylistRepositoryTest.kt`：

把第 227 行
```kotlin
        assertTrue(playlist!!.coverUri!!.startsWith("playlist_covers/"))
```
改成
```kotlin
        assertTrue(
            "expected file:// uri, was ${playlist!!.coverUri}",
            playlist.coverUri!!.startsWith("file://"),
        )
```

把第 248 行
```kotlin
        assertTrue(playlist!!.coverUri!!.startsWith("playlist_covers/"))
```
改成
```kotlin
        assertTrue(
            "expected file:// uri, was ${playlist!!.coverUri}",
            playlist.coverUri!!.startsWith("file://"),
        )
```

在文件末尾闭花括号前加一个新 case（紧跟在最后一个 `@Test` 之后）：

```kotlin
@Test
fun addMusic_storesHttpsArtworkUrlVerbatimAsCoverUri() = runBlocking {
    val id = UUID.randomUUID().toString()
    playlistRepo.createPlaylist(Playlist(id = id, name = "Online", coverUri = null))
    val artwork = "https://example.com/cover.jpg"
    playlistRepo.addMusicToPlaylist(id, sampleMusic("m1", artwork = artwork))
    val playlist = playlistRepo.observePlaylist(id).first()
    assertEquals(artwork, playlist?.coverUri)
}
```

> `sampleMusic` 是该文件内已有的 helper，签名为 `sampleMusic(id, title = ..., artwork = ...)`，参考第 220 行附近。

- [ ] **Step 2: 跑测试确认它失败**

```bash
./gradlew :data:connectedDebugAndroidTest --tests "com.hank.musicfree.data.repository.PlaylistRepositoryTest" --no-daemon
```

Expected: 三处断言失败（两处 `startsWith("file://")`，一处 https URL 等值断言）。

> 解释：Task 1 已让 `copyFromArtwork` 在 `file://` 输入时返回 `file://` 绝对 URI；但当前 `PlaylistRepository.toModel` 调用没有 resolver，老数据相对路径不会被翻译。两个旧 case 写入的 artwork 是 `file://...` 形态，Task 1 后 DB coverUri 已是 `file://`，所以这两处 `startsWith("file://")` 在 Task 3 实现完成前其实就能通过；为确保测试在改 Repository 之前先体现合同，新加的 https case 必须 fail。

- [ ] **Step 3: 改 `PlaylistRepository.kt` 的三处 `toModel` 调用**

修改文件 `data/src/main/java/com/hank/musicfree/data/repository/PlaylistRepository.kt`：

在文件靠近底部（class 内部，所有 public 方法之后）添加私有 helper：

```kotlin
private fun resolveLegacyCoverUri(raw: String): String? =
    if (raw.startsWith(LEGACY_COVER_PREFIX)) {
        android.net.Uri.fromFile(coverStore.absoluteFile(raw)).toString()
    } else {
        null
    }

companion object { private const val LEGACY_COVER_PREFIX = "playlist_covers/" }
```

然后把以下三个调用站点改写：

第 36-38 行附近的 `observeAllPlaylists()` 内部：
```kotlin
fun observeAllPlaylists(): Flow<List<Playlist>> =
    playlistDao.observeAllPlaylistsWithCount().map { rows ->
        rows.map { it.playlist.toModel(worksNum = it.worksNum, legacyCoverResolver = ::resolveLegacyCoverUri) }
    }
```

第 40-43 行附近的 `observePlaylist(id)` 内部：
```kotlin
fun observePlaylist(id: String): Flow<Playlist?> =
    playlistDao.observePlaylistWithCount(id).map { row ->
        row?.playlist?.toModel(worksNum = row.worksNum, legacyCoverResolver = ::resolveLegacyCoverUri)
    }
```

第 45-46 行附近的 `getPlaylistById(id)` 内部：
```kotlin
suspend fun getPlaylistById(id: String): Playlist? =
    playlistDao.getPlaylistById(id)?.toModel(legacyCoverResolver = ::resolveLegacyCoverUri)
```

- [ ] **Step 4: 跑测试确认它通过**

```bash
./gradlew :data:connectedDebugAndroidTest --tests "com.hank.musicfree.data.repository.PlaylistRepositoryTest" --no-daemon
```

Expected: 所有 case 通过，包括新加的 https 等值断言。

- [ ] **Step 5: Commit**

```bash
git add data/src/main/java/com/hank/musicfree/data/repository/PlaylistRepository.kt \
        data/src/androidTest/java/com/hank/musicfree/data/repository/PlaylistRepositoryTest.kt
git commit -m "$(cat <<'EOF'
feat(data): wire legacy cover resolver in PlaylistRepository

All three toModel call sites now pass a legacyCoverResolver that wraps any
'playlist_covers/<id>.jpg' relative path with Uri.fromFile(coverStore.absoluteFile(raw)),
producing a file:// URI that Coil can render. New writes continue to use the
formats from PlaylistCoverStore directly (http(s) URL or file://), so the
resolver only kicks in for legacy data on dev machines that escape destructive
fallback.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4：`:core/ui/PlatformTag` composable

**Files:**
- Create: `core/src/androidTest/java/com/hank/musicfree/core/ui/PlatformTagTest.kt`
- Create: `core/src/main/java/com/hank/musicfree/core/ui/PlatformTag.kt`

- [ ] **Step 1: 写 `PlatformTagTest`**

完整新建 `core/src/androidTest/java/com/hank/musicfree/core/ui/PlatformTagTest.kt`：

```kotlin
package com.hank.musicfree.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.hank.musicfree.core.theme.MusicFreeTheme
import org.junit.Rule
import org.junit.Test

class PlatformTagTest {
    @get:Rule val rule = createComposeRule()

    @Test fun rendersGivenText() {
        rule.setContent {
            MusicFreeTheme { PlatformTag(text = "网易云") }
        }
        rule.onNodeWithText("网易云").assertIsDisplayed()
    }

    @Test fun rendersBenDi() {
        rule.setContent {
            MusicFreeTheme { PlatformTag(text = "本地") }
        }
        rule.onNodeWithText("本地").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: 跑测试确认它失败**

```bash
./gradlew :core:connectedDebugAndroidTest --tests "com.hank.musicfree.core.ui.PlatformTagTest" --no-daemon
```

Expected: 编译失败 — `PlatformTag` unresolved。

- [ ] **Step 3: 实现 `PlatformTag.kt`**

完整新建 `core/src/main/java/com/hank/musicfree/core/ui/PlatformTag.kt`：

```kotlin
package com.hank.musicfree.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.theme.MusicFreeTheme

@Composable
fun PlatformTag(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(start = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MusicFreeTheme.colors.card,
        border = BorderStroke(1.dp, MusicFreeTheme.colors.divider),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MusicFreeTheme.colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
```

- [ ] **Step 4: 跑测试确认它通过**

```bash
./gradlew :core:connectedDebugAndroidTest --tests "com.hank.musicfree.core.ui.PlatformTagTest" --no-daemon
```

Expected: 2 个 case 全过。

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/hank/musicfree/core/ui/PlatformTag.kt \
        core/src/androidTest/java/com/hank/musicfree/core/ui/PlatformTagTest.kt
git commit -m "$(cat <<'EOF'
feat(core/ui): add PlatformTag pill matching RN tag styling

Border-only rounded pill with MusicFreeTheme card background and divider
border, used to surface a music item's source plugin (or the localized
literal '本地') next to the title. No business dependency.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5：`:core/ui/MusicItemRow` composable

**Files:**
- Create: `core/src/androidTest/java/com/hank/musicfree/core/ui/MusicItemRowTest.kt`
- Create: `core/src/main/java/com/hank/musicfree/core/ui/MusicItemRow.kt`

- [ ] **Step 1: 写 `MusicItemRowTest`**

完整新建 `core/src/androidTest/java/com/hank/musicfree/core/ui/MusicItemRowTest.kt`：

```kotlin
package com.hank.musicfree.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.theme.MusicFreeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MusicItemRowTest {
    @get:Rule val rule = createComposeRule()

    private fun item(
        id: String = "m1",
        platform: String = "网易云",
        title: String = "夜空中最亮的星",
        artist: String = "逃跑计划",
        album: String? = "世界",
    ) = MusicItem(
        id = id, platform = platform, title = title, artist = artist,
        album = album, duration = 0L, url = null, artwork = null, qualities = null,
    )

    @Test fun rendersTitleAndPlatformTag() {
        rule.setContent {
            MusicFreeTheme {
                MusicItemRow(
                    item = item(platform = "网易云"),
                    isFavorite = false,
                    actions = setOf(MusicItemAction.PlayNext),
                    onClick = {},
                    onAction = {},
                )
            }
        }
        rule.onNodeWithText("夜空中最亮的星").assertIsDisplayed()
        rule.onNodeWithText("网易云").assertIsDisplayed()
    }

    @Test fun mapsLocalPlatformToBenDi() {
        rule.setContent {
            MusicFreeTheme {
                MusicItemRow(
                    item = item(platform = "local"),
                    isFavorite = false,
                    actions = emptySet(),
                    onClick = {},
                    onAction = {},
                )
            }
        }
        rule.onNodeWithText("本地").assertIsDisplayed()
    }

    @Test fun descriptionShowsArtistDashAlbumWhenAlbumPresent() {
        rule.setContent {
            MusicFreeTheme {
                MusicItemRow(
                    item = item(artist = "逃跑计划", album = "世界"),
                    isFavorite = false,
                    actions = emptySet(),
                    onClick = {},
                    onAction = {},
                )
            }
        }
        rule.onNodeWithText("逃跑计划 - 世界").assertIsDisplayed()
    }

    @Test fun descriptionShowsArtistOnlyWhenAlbumBlank() {
        rule.setContent {
            MusicFreeTheme {
                MusicItemRow(
                    item = item(artist = "逃跑计划", album = null),
                    isFavorite = false,
                    actions = emptySet(),
                    onClick = {},
                    onAction = {},
                )
            }
        }
        rule.onNodeWithText("逃跑计划").assertIsDisplayed()
    }

    @Test fun rowClickFiresOnClick() {
        var clicked = false
        rule.setContent {
            MusicFreeTheme {
                MusicItemRow(
                    item = item(),
                    isFavorite = false,
                    actions = emptySet(),
                    onClick = { clicked = true },
                    onAction = {},
                )
            }
        }
        rule.onNodeWithTag("MusicItemRow_root").performClick()
        assertTrue(clicked)
    }

    @Test fun overflowMenuTransparentlyEmitsAction() {
        var captured: MusicItemAction? = null
        rule.setContent {
            MusicFreeTheme {
                MusicItemRow(
                    item = item(),
                    isFavorite = false,
                    actions = setOf(MusicItemAction.PlayNext, MusicItemAction.AddToPlaylist),
                    onClick = {},
                    onAction = { captured = it },
                )
            }
        }
        rule.onNodeWithTag("MusicItemMoreMenu_trigger").performClick()
        rule.onNodeWithTag("MusicItemMoreMenu_AddToPlaylist").performClick()
        assertEquals(MusicItemAction.AddToPlaylist, captured)
    }
}
```

- [ ] **Step 2: 跑测试确认它失败**

```bash
./gradlew :core:connectedDebugAndroidTest --tests "com.hank.musicfree.core.ui.MusicItemRowTest" --no-daemon
```

Expected: 编译失败 — `MusicItemRow` unresolved。

- [ ] **Step 3: 实现 `MusicItemRow.kt`**

完整新建 `core/src/main/java/com/hank/musicfree/core/ui/MusicItemRow.kt`：

```kotlin
package com.hank.musicfree.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.R
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.theme.MusicFreeTheme

@Composable
fun MusicItemRow(
    item: MusicItem,
    isFavorite: Boolean,
    actions: Set<MusicItemAction>,
    onClick: () -> Unit,
    onAction: (MusicItemAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .testTag("MusicItemRow_root")
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        CoverImage(uri = item.artwork, size = 40.dp, cornerRadius = 4.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                PlatformTag(text = displayPlatform(item.platform))
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = descriptionText(item),
                style = MaterialTheme.typography.bodySmall,
                color = MusicFreeTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MusicItemMoreMenu(
            actions = actions,
            isFavorite = isFavorite,
            onAction = onAction,
            triggerIcon = painterResource(id = R.drawable.ic_ellipsis_vertical),
        )
    }
}

private fun displayPlatform(platform: String): String =
    if (platform == "local") "本地" else platform

private fun descriptionText(item: MusicItem): String =
    item.artist + if (!item.album.isNullOrBlank()) " - ${item.album}" else ""
```

- [ ] **Step 4: 跑测试确认它通过**

```bash
./gradlew :core:connectedDebugAndroidTest --tests "com.hank.musicfree.core.ui.MusicItemRowTest" --no-daemon
```

Expected: 6 个 case 全过。

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/hank/musicfree/core/ui/MusicItemRow.kt \
        core/src/androidTest/java/com/hank/musicfree/core/ui/MusicItemRowTest.kt
git commit -m "$(cat <<'EOF'
feat(core/ui): add MusicItemRow with platform tag and album info

Pure presentation row that mirrors RN mediaItem/musicItem.tsx: 40dp cover,
title row with PlatformTag (with 'local' -> '本地' mapping), description
formatted as 'artist[ - album]', and the existing MusicItemMoreMenu trailing
overflow. No repository dependency; calling surfaces hand in item, isFavorite,
actions, and callbacks.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6：`PlaylistDetailScreen` 接入 `MusicItemRow`

**Files:**
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/playlist/PlaylistDetailScreen.kt`

- [ ] **Step 1: 删除文件内私有 `PlaylistRow` composable + 其单独的 `Row(verticalAlignment, modifier...)` 实现**

打开 `feature/home/src/main/java/com/hank/musicfree/feature/home/playlist/PlaylistDetailScreen.kt`，删掉文件末尾从 `@Composable\nprivate fun PlaylistRow(` 起到文件末尾闭花括号的整个函数（行 209-254 区间）。

- [ ] **Step 2: 替换 `LazyColumn.items` 内调用方为 `MusicItemRow`**

把当前文件第 113-134 行附近的 `items(items = items, key = ...) { item -> ... PlaylistRow(...) ... }` 块整段替换为：

```kotlin
items(items = items, key = { "${it.platform}::${it.id}" }) { item ->
    val isFavorite by viewModel.isFavoriteFlow(item)
        .collectAsStateWithLifecycle(initialValue = false)
    MusicItemRow(
        item = item,
        isFavorite = isFavorite,
        actions = setOf(
            MusicItemAction.PlayNext,
            MusicItemAction.ToggleFavorite,
            MusicItemAction.AddToPlaylist,
            MusicItemAction.RemoveFromPlaylist,
        ),
        onClick = {
            val idx = items.indexOf(item)
            viewModel.playAll(startIndex = if (idx >= 0) idx else 0)
            onNavigateToPlayer()
        },
        onAction = { action ->
            when (action) {
                MusicItemAction.ToggleFavorite -> viewModel.toggleFavorite(item)
                MusicItemAction.RemoveFromPlaylist -> viewModel.removeFromPlaylist(item)
                MusicItemAction.PlayNext -> { /* TODO: PlayerController.playNext when API exists */ }
                MusicItemAction.AddToPlaylist -> viewModel.showAddToPlaylistSheet(item)
            }
        },
    )
}
```

- [ ] **Step 3: 清理 import**

`MusicItemRow` 已在 `:core/ui` 包内、与 `MusicItemAction` 同包，确认文件顶部 import 块只保留：
- `import com.hank.musicfree.core.ui.AddToPlaylistBottomSheetContent`
- `import com.hank.musicfree.core.ui.CoverImage`
- `import com.hank.musicfree.core.ui.MusicFreeScreenScaffold`
- `import com.hank.musicfree.core.ui.MusicItemAction`
- `import com.hank.musicfree.core.ui.MusicItemMoreMenu` —— 如果文件里只剩 `MusicItemRow` 用 menu，且不再有 PlaylistDetailScreen 直接使用 `MusicItemMoreMenu` 的地方，删掉这一行

去掉因为 `PlaylistRow` 被删而闲置的 import：
- `androidx.compose.foundation.layout.fillMaxWidth`（如其他地方仍在用，保留）
- `androidx.compose.foundation.layout.padding`（同上）
- `androidx.compose.foundation.layout.width`（同上）
- `androidx.compose.ui.text.style.TextOverflow`
- `androidx.compose.ui.unit.dp`

> 实际未用 import 让 IDE / `./gradlew` 编译告诉你；重点是不留下 dangling reference。如果某个 layout import 还在 `EmptyState` 或别处用就保留。

- [ ] **Step 4: 编译 + 跑既有 `:feature:home` 测试**

```bash
./gradlew :feature:home:assembleDebug :feature:home:testDebugUnitTest --no-daemon
```

Expected: BUILD SUCCESSFUL，所有现有单元测试不受影响（无 PlaylistDetailScreenTest）。

- [ ] **Step 5: Commit**

```bash
git add feature/home/src/main/java/com/hank/musicfree/feature/home/playlist/PlaylistDetailScreen.kt
git commit -m "$(cat <<'EOF'
feat(home): use shared MusicItemRow in PlaylistDetailScreen

Drops the file-private PlaylistRow in favor of :core/ui/MusicItemRow so
playlist detail rows now show the source plugin (PlatformTag) and album
('artist - album') alongside the title, matching RN sheet detail layout.
Action callbacks unchanged.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7：联动验证 + 手工运行态验收

**Files:** 无代码改动；只跑构建 / 测试 / 运行态。

- [ ] **Step 1: 跑全套 `:data` 测试**

```bash
./gradlew :data:testDebugUnitTest :data:connectedDebugAndroidTest --no-daemon
```

Expected: BUILD SUCCESSFUL，全绿。

- [ ] **Step 2: 跑全套 `:core` androidTest**

```bash
./gradlew :core:connectedDebugAndroidTest --no-daemon
```

Expected: BUILD SUCCESSFUL，包含 `PlatformTagTest`、`MusicItemRowTest`、`MusicItemMoreMenuTest`、`AddToPlaylistBottomSheetContentTest`。

- [ ] **Step 3: 整盘 Debug APK 构建**

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL；产物 `app/build/outputs/apk/debug/app-debug.apk` 存在。

- [ ] **Step 4: grep 自检**

```bash
grep -rn '"playlist_covers/' --include="*.kt" -- . \
    | grep -v 'BASE_DIR_NAME' \
    | grep -v 'LEGACY_COVER_PREFIX' \
    | grep -v 'docs/'
```

Expected: 输出为空（除 `BASE_DIR_NAME` 常量、`LEGACY_COVER_PREFIX` 常量与文档外没有任何新写入的相对路径）。

- [ ] **Step 5: 手工运行态验收清单**

把 Debug APK 装到模拟器或真机：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

按以下清单走：

1. **远程封面自动同步**：创建空歌单 → 进入插件搜索结果加一首在线歌曲 → 返回首页"我的歌单"tab → 该歌单卡片显示远程封面（不再为空）。
2. **详情行展示**：进入该歌单详情 → 行内同时可见 title、platform tag（如「网易云」/「QQ音乐」）、`artist - album` 文本。
3. **本地 platform 中文化**：在"本地音乐"列表把一首本地歌曲收藏到"我喜欢" → 进入"我喜欢"详情 → 该行 platform tag 显示"本地"。
4. **用户选图封面**：进入歌单详情 → ⋮ → 编辑信息 → 更换封面 → 从相册选图 → 保存 → 封面立刻显示；杀进程再开仍显示。
5. **老数据兼容**（如果 dev 机上还留着 fix 前的 DB）：原本 coverUri 是 `playlist_covers/<id>.jpg` 相对路径的歌单，更新后封面照常渲染。

任一项失败：**不要 commit 隐藏问题**，根因排查后回到对应 Task。

- [ ] **Step 6: 回主仓库整理 worktree**

> ⚠️ 用户明确指示"开始合回主分支"或类似话术之前不要 push / merge。worktree 在工作期间保留即可。

合回时机由用户决定，参考流程：

```bash
# 在主仓库根
git checkout main
git merge --ff-only feat/playlist-cover-and-row   # 或按用户偏好用 PR 路径
git worktree remove .worktrees/feat-playlist-cover-and-row
git branch -d feat/playlist-cover-and-row
```

---

## Self-Review Checklist Result

- [x] **Spec coverage**
  - §「coverUri 字段语义重定义」→ Task 1（store）+ Task 2（mapper）+ Task 3（repository wiring） ✓
  - §「数据层 — PlaylistCoverStore」→ Task 1 ✓
  - §「数据层 — PlaylistRepository」→ Task 3（无 API 变更，注 resolver） ✓
  - §「数据层 — PlaylistMapper」→ Task 2（采用 lambda resolver fallback 实现，spec 有显式 fallback 允许） ✓
  - §「UI — `:core/ui/MusicItemRow.kt`」→ Task 5 ✓
  - §「UI — `:core/ui/PlatformTag.kt`」→ Task 4 ✓
  - §「UI — PlaylistDetailScreen 接入」→ Task 6 ✓
  - §「错误处理」→ Task 1/2 测试覆盖 null / 不识别 scheme / blank / null coverUri 路径 ✓
  - §「测试策略」单元/DAO/UI/手工验收 → Task 1-5 自动测试 + Task 7.5 手工清单 ✓
  - §「验收闸门」→ Task 7（构建 + grep + 手工） ✓
  - §「实施约束（worktree）」→ Task 0 ✓

- [x] **Placeholder scan**：每个 Task 都给了完整代码块、命令、期望输出；没有 "TBD" / "implement later" / "similar to Task N"。

- [x] **Type consistency**：
  - `legacyCoverResolver: ((String) -> String?)?` 参数命名与签名 Task 2 / Task 3 一致。
  - `MusicItemRow` / `MusicItemAction` / `PlatformTag` 命名 Task 4 / 5 / 6 一致。
  - `BASE_DIR_NAME` / `LEGACY_COVER_PREFIX` 常量名 Task 1 / 3 / 7 一致。

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-05-playlist-cover-and-row-display.md`.

Two execution options:

1. **Subagent-Driven (recommended)** — 用 `superpowers:subagent-driven-development`，每个 Task 派一个新 subagent，task 间做 review。适合本计划：7 个独立 Task，每个 commit 边界清晰。
2. **Inline Execution** — 用 `superpowers:executing-plans`，主对话内逐 Task 执行，按 batch checkpoint review。

请告诉我用哪一种？

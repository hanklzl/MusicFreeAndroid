# Milestone 4: 本地音乐扫描与播放 UI 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现端到端的本地音乐播放体验：MediaStore 扫描 → 首页列表展示 → 全屏播放器 → 迷你播放栏。

**Architecture:** HomeViewModel 通过 LocalMusicScanner 扫描设备音乐（MediaStore API），结果存入 MusicRepository。PlayerViewModel 观察 PlayerController 的 StateFlow 驱动 UI。MiniPlayer 常驻在 App Scaffold 底部，观察播放状态。所有 UI 使用 Jetpack Compose + Material3 + 项目自定义主题。

**Tech Stack:** Jetpack Compose, Hilt, Media3/ExoPlayer, MediaStore API, Kotlin Coroutines/Flow, Coil (图片加载)

---

## 文件结构

### 新建文件

```
feature/home/src/main/java/.../feature/home/
  HomeViewModel.kt              — 首页 ViewModel，管理本地音乐列表和扫描状态
  HomeUiState.kt                — 首页 UI 状态 sealed interface
  scanner/
    LocalMusicScanner.kt        — MediaStore 查询封装，返回 Flow<List<MusicItem>>

feature/player-ui/src/main/java/.../feature/playerui/
  PlayerViewModel.kt            — 播放器 ViewModel，观察 PlayerController 状态
  component/
    MiniPlayer.kt               — 底部迷你播放栏 Composable

core/src/main/java/.../core/ui/
  CoverImage.kt                 — Coil AsyncImage 封装，统一占位图和错误图

feature/home/src/test/java/.../feature/home/
  HomeViewModelTest.kt          — HomeViewModel 单元测试
  scanner/
    LocalMusicScannerTest.kt    — LocalMusicScanner 单元测试

feature/player-ui/src/test/java/.../feature/playerui/
  PlayerViewModelTest.kt        — PlayerViewModel 单元测试
```

### 修改文件

```
feature/home/src/main/java/.../feature/home/
  HomeScreen.kt                 — 从占位符 → 完整本地音乐列表 UI
  navigation/HomeNavigation.kt  — 注入 ViewModel

feature/player-ui/src/main/java/.../feature/playerui/
  PlayerScreen.kt               — 从占位符 → 全屏播放器 UI
  navigation/PlayerNavigation.kt — 注入 ViewModel

feature/home/build.gradle.kts   — 添加 :player, :data, Coil, Lifecycle 依赖
feature/player-ui/build.gradle.kts — 添加 :player, Coil, Lifecycle 依赖

app/src/main/java/.../MainActivity.kt      — Scaffold bottomBar 集成 MiniPlayer
app/src/main/java/.../navigation/AppNavHost.kt — 传递 navController 给 MiniPlayer
app/build.gradle.kts            — 添加 Coil 依赖（如缺失）

gradle/libs.versions.toml       — 添加 Coil 版本和库声明（如缺失）
```

---

## Task 1: 添加 Coil 依赖到版本目录

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `feature/home/build.gradle.kts`
- Modify: `feature/player-ui/build.gradle.kts`

- [ ] **Step 1: 检查 libs.versions.toml 是否已有 Coil**

Run: `grep -i coil gradle/libs.versions.toml`
如果已有 Coil，跳过 Step 2。

- [ ] **Step 2: 添加 Coil 到版本目录**

在 `gradle/libs.versions.toml` 中添加：

```toml
# [versions] 区域添加
coil = "3.2.0"

# [libraries] 区域添加
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
```

- [ ] **Step 3: 更新 feature/home/build.gradle.kts 依赖**

添加以下依赖：
```kotlin
implementation(project(":player"))
implementation(project(":data"))
implementation(libs.coil.compose)
implementation(libs.androidx.lifecycle.runtime.compose)
implementation(libs.androidx.lifecycle.viewmodel.compose)
implementation(libs.hilt.navigation.compose)
```

- [ ] **Step 4: 更新 feature/player-ui/build.gradle.kts 依赖**

添加以下依赖：
```kotlin
implementation(project(":player"))
implementation(libs.coil.compose)
implementation(libs.androidx.lifecycle.runtime.compose)
implementation(libs.androidx.lifecycle.viewmodel.compose)
implementation(libs.hilt.navigation.compose)
```

- [ ] **Step 5: 验证编译**

Run: `./gradlew :feature:home:assembleDebug :feature:player-ui:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml feature/home/build.gradle.kts feature/player-ui/build.gradle.kts
git commit -m "build: add Coil and lifecycle dependencies for feature modules"
```

---

## Task 2: CoverImage 通用组件

**Files:**
- Create: `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/CoverImage.kt`

- [ ] **Step 1: 创建 CoverImage Composable**

```kotlin
package com.zili.android.musicfreeandroid.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme

@Composable
fun CoverImage(
    uri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    cornerRadius: Dp = 4.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    if (uri.isNullOrBlank()) {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MusicFreeTheme.colors.placeholder),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MusicFreeTheme.colors.textSecondary,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    } else {
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(shape),
            contentScale = ContentScale.Crop,
        )
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `./gradlew :core:assembleDebug`
Expected: BUILD SUCCESSFUL（注意：core 模块需要 Coil 依赖，如果 core 不依赖 Coil，则将 CoverImage 放到 feature 公共位置或给 core 加 Coil 依赖）

如果 core 模块没有 Coil 依赖，在 `core/build.gradle.kts` 添加：
```kotlin
implementation(libs.coil.compose)
```

- [ ] **Step 3: Commit**

```bash
git add core/
git commit -m "feat(core): add CoverImage composable with Coil and placeholder"
```

---

## Task 3: LocalMusicScanner — MediaStore 扫描

**Files:**
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/scanner/LocalMusicScanner.kt`
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/scanner/LocalMusicScannerTest.kt`

- [ ] **Step 1: 编写 LocalMusicScanner 测试**

```kotlin
package com.zili.android.musicfreeandroid.feature.home.scanner

import android.content.ContentResolver
import android.database.MatrixCursor
import android.provider.MediaStore
import com.zili.android.musicfreeandroid.core.model.MusicItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LocalMusicScannerTest {

    private val contentResolver: ContentResolver = mock()
    private val scanner = LocalMusicScanner(contentResolver)

    @Test
    fun `scan returns music items from MediaStore`() = runTest {
        val cursor = MatrixCursor(
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID,
            )
        )
        cursor.addRow(arrayOf(1L, "Test Song", "Test Artist", "Test Album", 180_000L, "/storage/test.mp3", 100L))
        cursor.addRow(arrayOf(2L, "Song 2", "<unknown>", "<unknown>", 240_000L, "/storage/song2.flac", 200L))

        whenever(contentResolver.query(any(), any(), any(), any(), any())).thenReturn(cursor)

        val result = scanner.scan().first()

        assertEquals(2, result.size)
        assertEquals("Test Song", result[0].title)
        assertEquals("Test Artist", result[0].artist)
        assertEquals("Test Album", result[0].album)
        assertEquals(180_000L, result[0].duration)
        assertEquals("local", result[0].platform)

        // <unknown> should be replaced with empty string
        assertEquals("", result[1].artist)
        assertEquals("", result[1].album)
    }

    @Test
    fun `scan returns empty list when no audio files`() = runTest {
        val cursor = MatrixCursor(
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID,
            )
        )

        whenever(contentResolver.query(any(), any(), any(), any(), any())).thenReturn(cursor)

        val result = scanner.scan().first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `scan handles null cursor gracefully`() = runTest {
        whenever(contentResolver.query(any(), any(), any(), any(), any())).thenReturn(null)

        val result = scanner.scan().first()
        assertTrue(result.isEmpty())
    }
}
```

- [ ] **Step 2: 确保测试依赖**

在 `feature/home/build.gradle.kts` 添加测试依赖（如缺失）：
```kotlin
testImplementation(libs.junit)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.mockito.kotlin)
```

如果 `libs.mockito.kotlin` 不存在，在 `gradle/libs.versions.toml` 添加：
```toml
# [versions]
mockito-kotlin = "5.4.0"

# [libraries]
mockito-kotlin = { group = "org.mockito.kotlin", name = "mockito-kotlin", version.ref = "mockito-kotlin" }
```

- [ ] **Step 3: 运行测试验证失败**

Run: `./gradlew :feature:home:testDebugUnitTest --tests "*.LocalMusicScannerTest"`
Expected: FAIL（类不存在）

- [ ] **Step 4: 实现 LocalMusicScanner**

```kotlin
package com.zili.android.musicfreeandroid.feature.home.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import com.zili.android.musicfreeandroid.core.model.MusicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMusicScanner @Inject constructor(
    private val contentResolver: ContentResolver,
) {

    fun scan(): Flow<List<MusicItem>> = flow {
        val items = queryMediaStore()
        emit(items)
    }.flowOn(Dispatchers.IO)

    private fun queryMediaStore(): List<MusicItem> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder,
        ) ?: return emptyList()

        val items = mutableListOf<MusicItem>()

        cursor.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (it.moveToNext()) {
                val mediaId = it.getLong(idCol)
                val artist = it.getString(artistCol) ?: ""
                val album = it.getString(albumCol) ?: ""
                val albumId = it.getLong(albumIdCol)

                val artworkUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId,
                ).toString()

                items.add(
                    MusicItem(
                        id = mediaId.toString(),
                        platform = PLATFORM_LOCAL,
                        title = it.getString(titleCol) ?: "Unknown",
                        artist = artist.cleanUnknown(),
                        album = album.cleanUnknown(),
                        duration = it.getLong(durationCol),
                        url = it.getString(dataCol),
                        artwork = artworkUri,
                    )
                )
            }
        }
        return items
    }

    companion object {
        const val PLATFORM_LOCAL = "local"
    }
}

private fun String.cleanUnknown(): String =
    if (this == "<unknown>" || this == "Unknown" || this == "未知歌手" || this == "未知专辑") "" else this
```

- [ ] **Step 5: 运行测试验证通过**

Run: `./gradlew :feature:home:testDebugUnitTest --tests "*.LocalMusicScannerTest"`
Expected: PASS（3 tests）

- [ ] **Step 6: Commit**

```bash
git add feature/home/
git commit -m "feat(home): add LocalMusicScanner with MediaStore query and tests"
```

---

## Task 4: HomeUiState 和 HomeViewModel

**Files:**
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeUiState.kt`
- Create: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeViewModel.kt`
- Create: `feature/home/src/test/java/com/zili/android/musicfreeandroid/feature/home/HomeViewModelTest.kt`

- [ ] **Step 1: 创建 HomeUiState**

```kotlin
package com.zili.android.musicfreeandroid.feature.home

import com.zili.android.musicfreeandroid.core.model.MusicItem

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val musicItems: List<MusicItem>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
```

- [ ] **Step 2: 编写 HomeViewModel 测试**

```kotlin
package com.zili.android.musicfreeandroid.feature.home

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.feature.home.scanner.LocalMusicScanner
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val scanner: LocalMusicScanner = mock()
    private val playerController: PlayerController = mock()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading`() = runTest {
        whenever(scanner.scan()).thenReturn(flowOf(emptyList()))
        val viewModel = HomeViewModel(scanner, playerController)
        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `scan returns music items updates state to Success`() = runTest {
        val items = listOf(
            MusicItem(id = "1", platform = "local", title = "Song 1", artist = "Artist", album = "Album", duration = 180_000L),
        )
        whenever(scanner.scan()).thenReturn(flowOf(items))

        val viewModel = HomeViewModel(scanner, playerController)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is HomeUiState.Success)
        assertEquals(1, (state as HomeUiState.Success).musicItems.size)
    }

    @Test
    fun `playItem calls playerController`() = runTest {
        val items = listOf(
            MusicItem(id = "1", platform = "local", title = "Song 1", artist = "Artist", album = "Album", duration = 180_000L),
        )
        whenever(scanner.scan()).thenReturn(flowOf(items))

        val viewModel = HomeViewModel(scanner, playerController)
        advanceUntilIdle()

        viewModel.playItem(items[0], items)
        verify(playerController).playQueue(items, 0)
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `./gradlew :feature:home:testDebugUnitTest --tests "*.HomeViewModelTest"`
Expected: FAIL

- [ ] **Step 4: 实现 HomeViewModel**

```kotlin
package com.zili.android.musicfreeandroid.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.feature.home.scanner.LocalMusicScanner
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val scanner: LocalMusicScanner,
    private val playerController: PlayerController,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        scanLocalMusic()
    }

    fun scanLocalMusic() {
        _uiState.value = HomeUiState.Loading
        viewModelScope.launch {
            scanner.scan()
                .catch { e ->
                    _uiState.value = HomeUiState.Error(e.message ?: "扫描失败")
                }
                .collect { items ->
                    _uiState.value = HomeUiState.Success(items)
                }
        }
    }

    fun playItem(item: MusicItem, queue: List<MusicItem>) {
        val index = queue.indexOf(item)
        playerController.playQueue(queue, if (index >= 0) index else 0)
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `./gradlew :feature:home:testDebugUnitTest --tests "*.HomeViewModelTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add feature/home/
git commit -m "feat(home): add HomeViewModel with local music scanning and tests"
```

---

## Task 5: HomeScreen UI — 本地音乐列表

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt`
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/navigation/HomeNavigation.kt`

- [ ] **Step 1: 实现 HomeScreen UI**

```kotlin
package com.zili.android.musicfreeandroid.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.theme.Dimensions
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.ui.CoverImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPlayer: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "MusicFree",
                    color = MusicFreeTheme.colors.appBarText,
                    fontSize = Dimensions.FontSize.appBar,
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MusicFreeTheme.colors.appBar,
            ),
            actions = {
                IconButton(onClick = onNavigateToSearch) {
                    Icon(Icons.Default.Search, contentDescription = "搜索", tint = MusicFreeTheme.colors.appBarText)
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "设置", tint = MusicFreeTheme.colors.appBarText)
                }
            },
        )

        when (val state = uiState) {
            is HomeUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MusicFreeTheme.colors.primary)
                }
            }
            is HomeUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, color = MusicFreeTheme.colors.danger)
                        Spacer(Modifier.height(8.dp))
                        IconButton(onClick = { viewModel.scanLocalMusic() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "重试", tint = MusicFreeTheme.colors.primary)
                        }
                    }
                }
            }
            is HomeUiState.Success -> {
                if (state.musicItems.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("没有找到本地音乐", color = MusicFreeTheme.colors.textSecondary)
                    }
                } else {
                    MusicList(
                        items = state.musicItems,
                        onItemClick = { item ->
                            viewModel.playItem(item, state.musicItems)
                            onNavigateToPlayer()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicList(
    items: List<MusicItem>,
    onItemClick: (MusicItem) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "${items.size} 首本地音乐",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = MusicFreeTheme.colors.textSecondary,
                fontSize = Dimensions.FontSize.description,
            )
        }
        itemsIndexed(items, key = { _, item -> "${item.platform}:${item.id}" }) { index, item ->
            MusicListItem(
                item = item,
                index = index + 1,
                onClick = { onItemClick(item) },
            )
            if (index < items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = MusicFreeTheme.colors.divider,
                )
            }
        }
    }
}

@Composable
private fun MusicListItem(
    item: MusicItem,
    index: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            uri = item.artwork,
            size = 48.dp,
            cornerRadius = 4.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = item.title,
                color = MusicFreeTheme.colors.text,
                fontSize = Dimensions.FontSize.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.artist.isNotBlank()) {
                Text(
                    text = item.artist + if (!item.album.isNullOrBlank()) " - ${item.album}" else "",
                    color = MusicFreeTheme.colors.textSecondary,
                    fontSize = Dimensions.FontSize.description,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
```

- [ ] **Step 2: 更新 HomeNavigation.kt**

```kotlin
package com.zili.android.musicfreeandroid.feature.home.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.HomeRoute
import com.zili.android.musicfreeandroid.feature.home.HomeScreen

fun NavGraphBuilder.homeScreen(
    onNavigateToPlayer: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    composable<HomeRoute> {
        HomeScreen(
            onNavigateToPlayer = onNavigateToPlayer,
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToSettings = onNavigateToSettings,
        )
    }
}
```

- [ ] **Step 3: 验证编译**

Run: `./gradlew :feature:home:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add feature/home/
git commit -m "feat(home): implement HomeScreen with local music list UI"
```

---

## Task 6: PlayerViewModel

**Files:**
- Create: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt`
- Create: `feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModelTest.kt`

- [ ] **Step 1: 编写 PlayerViewModel 测试**

```kotlin
package com.zili.android.musicfreeandroid.feature.playerui

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.player.model.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val playerController: PlayerController = mock()
    private val playerStateFlow = MutableStateFlow(PlayerState.EMPTY)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        whenever(playerController.playerState).thenReturn(playerStateFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `playerState reflects controller state`() = runTest {
        val viewModel = PlayerViewModel(playerController)
        advanceUntilIdle()

        assertEquals(PlayerState.EMPTY, viewModel.playerState.value)

        val item = MusicItem(id = "1", platform = "local", title = "Song", artist = "A", album = null, duration = 180_000L)
        playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item, isPlaying = true)
        advanceUntilIdle()

        assertTrue(viewModel.playerState.value.isPlaying)
        assertEquals("Song", viewModel.playerState.value.currentItem?.title)
    }

    @Test
    fun `togglePlayPause calls play when paused`() = runTest {
        playerStateFlow.value = PlayerState.EMPTY.copy(isPlaying = false)
        val viewModel = PlayerViewModel(playerController)
        advanceUntilIdle()

        viewModel.togglePlayPause()
        verify(playerController).play()
    }

    @Test
    fun `togglePlayPause calls pause when playing`() = runTest {
        playerStateFlow.value = PlayerState.EMPTY.copy(isPlaying = true)
        val viewModel = PlayerViewModel(playerController)
        advanceUntilIdle()

        viewModel.togglePlayPause()
        verify(playerController).pause()
    }

    @Test
    fun `skipToNext calls controller`() {
        val viewModel = PlayerViewModel(playerController)
        viewModel.skipToNext()
        verify(playerController).skipToNext()
    }

    @Test
    fun `skipToPrevious calls controller`() {
        val viewModel = PlayerViewModel(playerController)
        viewModel.skipToPrevious()
        verify(playerController).skipToPrevious()
    }

    @Test
    fun `seekTo calls controller`() {
        val viewModel = PlayerViewModel(playerController)
        viewModel.seekTo(5000L)
        verify(playerController).seekTo(5000L)
    }

    @Test
    fun `cycleRepeatMode calls controller`() {
        val viewModel = PlayerViewModel(playerController)
        viewModel.cycleRepeatMode()
        verify(playerController).cycleRepeatMode()
    }

    @Test
    fun `toggleShuffle calls controller`() {
        val viewModel = PlayerViewModel(playerController)
        viewModel.toggleShuffle()
        verify(playerController).toggleShuffle()
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `./gradlew :feature:player-ui:testDebugUnitTest --tests "*.PlayerViewModelTest"`
Expected: FAIL

- [ ] **Step 3: 实现 PlayerViewModel**

```kotlin
package com.zili.android.musicfreeandroid.feature.playerui

import androidx.lifecycle.ViewModel
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.player.model.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = playerController.playerState

    fun togglePlayPause() {
        if (playerState.value.isPlaying) {
            playerController.pause()
        } else {
            playerController.play()
        }
    }

    fun skipToNext() = playerController.skipToNext()

    fun skipToPrevious() = playerController.skipToPrevious()

    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)

    fun cycleRepeatMode() = playerController.cycleRepeatMode()

    fun toggleShuffle() = playerController.toggleShuffle()
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `./gradlew :feature:player-ui:testDebugUnitTest --tests "*.PlayerViewModelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add feature/player-ui/
git commit -m "feat(player-ui): add PlayerViewModel with controller delegation and tests"
```

---

## Task 7: MiniPlayer 组件

**Files:**
- Create: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/component/MiniPlayer.kt`

- [ ] **Step 1: 实现 MiniPlayer Composable**

```kotlin
package com.zili.android.musicfreeandroid.feature.playerui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.theme.Dimensions
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.ui.CoverImage
import com.zili.android.musicfreeandroid.feature.playerui.PlayerViewModel

@Composable
fun MiniPlayer(
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()

    if (!state.hasMedia) return

    val progress = if (state.duration > 0) {
        (state.position.toFloat() / state.duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MusicFreeTheme.colors.musicBar),
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = MusicFreeTheme.colors.primary,
            trackColor = MusicFreeTheme.colors.divider,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToPlayer)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverImage(
                uri = state.currentItem?.artwork,
                size = 48.dp,
                cornerRadius = 24.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.currentItem?.title ?: "",
                    color = MusicFreeTheme.colors.musicBarText,
                    fontSize = Dimensions.FontSize.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.currentItem?.artist ?: "",
                    color = MusicFreeTheme.colors.musicBarText.copy(alpha = 0.6f),
                    fontSize = Dimensions.FontSize.description,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { viewModel.togglePlayPause() }) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    tint = MusicFreeTheme.colors.musicBarText,
                    modifier = Modifier.size(32.dp),
                )
            }
            IconButton(onClick = { viewModel.skipToNext() }) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "下一曲",
                    tint = MusicFreeTheme.colors.musicBarText,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `./gradlew :feature:player-ui:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add feature/player-ui/
git commit -m "feat(player-ui): add MiniPlayer composable with progress bar and controls"
```

---

## Task 8: PlayerScreen — 全屏播放器 UI

**Files:**
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`
- Modify: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/navigation/PlayerNavigation.kt`

- [ ] **Step 1: 实现全屏 PlayerScreen**

```kotlin
package com.zili.android.musicfreeandroid.feature.playerui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import com.zili.android.musicfreeandroid.core.theme.Dimensions
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val currentItem = state.currentItem

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MusicFreeTheme.colors.pageBackground),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            TopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = currentItem?.title ?: "",
                            color = MusicFreeTheme.colors.text,
                            fontSize = Dimensions.FontSize.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        if (currentItem?.artist?.isNotBlank() == true) {
                            Text(
                                text = currentItem.artist,
                                color = MusicFreeTheme.colors.textSecondary,
                                fontSize = Dimensions.FontSize.description,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MusicFreeTheme.colors.text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )

            Spacer(Modifier.weight(1f))

            // Album cover
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = currentItem?.artwork,
                    contentDescription = "专辑封面",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }

            Spacer(Modifier.weight(1f))

            // Seek bar
            SeekBar(
                position = state.position,
                duration = state.duration,
                onSeek = { viewModel.seekTo(it) },
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Spacer(Modifier.height(8.dp))

            // Play controls
            PlayControls(
                isPlaying = state.isPlaying,
                repeatMode = state.repeatMode,
                shuffleEnabled = state.shuffleEnabled,
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onSkipPrevious = { viewModel.skipToPrevious() },
                onSkipNext = { viewModel.skipToNext() },
                onCycleRepeatMode = { viewModel.cycleRepeatMode() },
                onToggleShuffle = { viewModel.toggleShuffle() },
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    val sliderValue = if (isDragging) {
        dragPosition
    } else if (duration > 0) {
        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = sliderValue,
            onValueChange = {
                isDragging = true
                dragPosition = it
            },
            onValueChangeFinished = {
                isDragging = false
                onSeek((dragPosition * duration).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = MusicFreeTheme.colors.primary,
                activeTrackColor = MusicFreeTheme.colors.primary,
                inactiveTrackColor = MusicFreeTheme.colors.divider,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(if (isDragging) (dragPosition * duration).toLong() else position),
                color = MusicFreeTheme.colors.textSecondary,
                fontSize = Dimensions.FontSize.description,
            )
            Text(
                text = formatDuration(duration),
                color = MusicFreeTheme.colors.textSecondary,
                fontSize = Dimensions.FontSize.description,
            )
        }
    }
}

@Composable
private fun PlayControls(
    isPlaying: Boolean,
    repeatMode: RepeatMode,
    shuffleEnabled: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Shuffle
        IconButton(onClick = onToggleShuffle) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "随机播放",
                tint = if (shuffleEnabled) MusicFreeTheme.colors.primary else MusicFreeTheme.colors.textSecondary,
                modifier = Modifier.size(28.dp),
            )
        }
        // Previous
        IconButton(onClick = onSkipPrevious) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "上一曲",
                tint = MusicFreeTheme.colors.text,
                modifier = Modifier.size(36.dp),
            )
        }
        // Play/Pause
        IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(64.dp)) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = MusicFreeTheme.colors.text,
                modifier = Modifier.size(48.dp),
            )
        }
        // Next
        IconButton(onClick = onSkipNext) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "下一曲",
                tint = MusicFreeTheme.colors.text,
                modifier = Modifier.size(36.dp),
            )
        }
        // Repeat
        IconButton(onClick = onCycleRepeatMode) {
            Icon(
                imageVector = when (repeatMode) {
                    RepeatMode.ONE -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                },
                contentDescription = "重复模式",
                tint = if (repeatMode != RepeatMode.OFF) MusicFreeTheme.colors.primary else MusicFreeTheme.colors.textSecondary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
```

- [ ] **Step 2: 更新 PlayerNavigation.kt**

```kotlin
package com.zili.android.musicfreeandroid.feature.playerui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zili.android.musicfreeandroid.core.navigation.PlayerRoute
import com.zili.android.musicfreeandroid.feature.playerui.PlayerScreen

fun NavGraphBuilder.playerScreen(
    onBack: () -> Unit,
) {
    composable<PlayerRoute> {
        PlayerScreen(onBack = onBack)
    }
}
```

- [ ] **Step 3: 验证编译**

Run: `./gradlew :feature:player-ui:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add feature/player-ui/
git commit -m "feat(player-ui): implement full-screen PlayerScreen with controls, seekbar, and album cover"
```

---

## Task 9: App 集成 — Scaffold + MiniPlayer + 导航

**Files:**
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/MainActivity.kt`
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/navigation/AppNavHost.kt`

- [ ] **Step 1: 更新 AppNavHost，传递完整导航回调**

修改 `AppNavHost.kt` 以确保所有导航回调正确连接：

```kotlin
package com.zili.android.musicfreeandroid.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.zili.android.musicfreeandroid.core.navigation.HomeRoute
import com.zili.android.musicfreeandroid.core.navigation.PlayerRoute
import com.zili.android.musicfreeandroid.core.navigation.SearchRoute
import com.zili.android.musicfreeandroid.core.navigation.SettingsRoute
import com.zili.android.musicfreeandroid.feature.home.navigation.homeScreen
import com.zili.android.musicfreeandroid.feature.playerui.navigation.playerScreen
import com.zili.android.musicfreeandroid.feature.search.navigation.searchScreen
import com.zili.android.musicfreeandroid.feature.settings.navigation.settingsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        homeScreen(
            onNavigateToPlayer = { navController.navigate(PlayerRoute) },
            onNavigateToSearch = { navController.navigate(SearchRoute) },
            onNavigateToSettings = { navController.navigate(SettingsRoute) },
        )
        playerScreen(
            onBack = { navController.popBackStack() },
        )
        searchScreen(
            onBack = { navController.popBackStack() },
        )
        settingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
}
```

- [ ] **Step 2: 更新 MainActivity，集成 MiniPlayer 到 Scaffold**

```kotlin
package com.zili.android.musicfreeandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zili.android.musicfreeandroid.core.navigation.HomeRoute
import com.zili.android.musicfreeandroid.core.navigation.PlayerRoute
import com.zili.android.musicfreeandroid.core.navigation.SearchRoute
import com.zili.android.musicfreeandroid.core.navigation.SettingsRoute
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.feature.playerui.component.MiniPlayer
import com.zili.android.musicfreeandroid.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MusicFreeTheme {
                val navController = rememberNavController()
                val currentBackStack by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStack?.destination?.route

                // 在全屏播放器页面不显示 MiniPlayer
                val showMiniPlayer = currentRoute != null &&
                    !currentRoute.contains("PlayerRoute")

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (showMiniPlayer) {
                            MiniPlayer(
                                onNavigateToPlayer = {
                                    navController.navigate(PlayerRoute)
                                },
                            )
                        }
                    },
                ) { innerPadding ->
                    AppNavHost(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: 确保 Hilt ContentResolver 提供**

需要在某个 Hilt Module 中提供 `ContentResolver`。检查 `data/di/DataModule.kt` 是否已提供。如果没有，在 `app` 或 `data` 模块中添加：

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver
}
```

- [ ] **Step 4: 添加存储权限到 AndroidManifest.xml**

在 `app/src/main/AndroidManifest.xml` 添加（如缺失）：
```xml
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<!-- For SDK < 33 fallback -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

- [ ] **Step 5: 验证全项目编译**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/ feature/
git commit -m "feat(app): integrate MiniPlayer into Scaffold and wire up navigation"
```

---

## Task 10: 权限请求处理

**Files:**
- Modify: `feature/home/src/main/java/com/zili/android/musicfreeandroid/feature/home/HomeScreen.kt`

- [ ] **Step 1: 在 HomeScreen 中添加运行时权限请求**

在 `HomeScreen` 顶部添加权限检查逻辑（使用 Accompanist 或原生 `rememberLauncherForActivityResult`）：

```kotlin
// 在 HomeScreen composable 函数体开头添加
val context = LocalContext.current
val permissionState = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted) {
        viewModel.scanLocalMusic()
    }
}

LaunchedEffect(Unit) {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
        // 权限已授予，ViewModel init 已触发扫描
    } else {
        permissionState.launch(permission)
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `./gradlew :feature:home:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add feature/home/
git commit -m "feat(home): add runtime permission request for audio media access"
```

---

## Task 11: 全项目编译与清理

- [ ] **Step 1: 运行全项目编译**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 运行所有单元测试**

Run: `./gradlew test`
Expected: 所有测试通过

- [ ] **Step 3: 修复编译或测试问题**

如有任何编译错误或测试失败，逐一修复。

- [ ] **Step 4: 最终 Commit**

```bash
git add -A
git commit -m "chore: milestone 4 final cleanup and verification"
```

# 播放页歌词实施计划

> **给 agentic worker：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans`，按任务逐项执行本计划。步骤使用 checkbox（`- [ ]`）语法跟踪进度。

**目标：** 实现 Android 原生播放页歌词功能，包括插件歌词加载、默认自动搜索、同步滚动、拖动跳转、翻译、字体大小、偏移、搜索/关联、本地歌词导入。

**架构：** 遵循 `docs/superpowers/specs/2026-05-04-player-lyrics-design.md` 中已确认的规范。保持模块边界正确：`:core` 负责歌词领域模型/解析器，`:plugin` 负责解析插件歌词 payload，`:data` 只负责持久化/偏好，`:feature:player-ui` 负责插件感知的加载逻辑和 Compose UI。

**技术栈：** Kotlin、Jetpack Compose、Material3、Hilt、Room、DataStore、Coroutines Flow、Navigation Compose、QuickJS 插件桥、Media3 播放状态。

---

## 执行前规则

- 只在 `.worktrees/feat-player-lyrics` 中开发。
- 不要在仓库根 checkout 中实现。
- 文档引用保持相对路径。
- 不新增 Gradle module。
- 不实现悬浮窗/桌面歌词。
- 每个任务完成后优先提交小 commit。

实现前运行：

```bash
cd .worktrees/feat-player-lyrics
git status --short
./gradlew test
```

预期：

- 代码改动前 `git status --short` 为空。
- `./gradlew test` 输出 `BUILD SUCCESSFUL`。

## 文件地图

新建：

- `core/src/main/java/com/hank/musicfree/core/lyric/LyricParser.kt`
- `core/src/main/java/com/hank/musicfree/core/lyric/LyricTiming.kt`
- `core/src/main/java/com/hank/musicfree/core/model/ParsedLyricLine.kt`
- `core/src/main/java/com/hank/musicfree/core/model/LyricDocument.kt`
- `core/src/main/java/com/hank/musicfree/core/model/LyricSourceInfo.kt`
- `core/src/main/java/com/hank/musicfree/core/model/RawLyricPayload.kt`
- `core/src/test/java/com/hank/musicfree/core/lyric/LyricParserTest.kt`
- `core/src/test/java/com/hank/musicfree/core/lyric/LyricTimingTest.kt`
- `data/src/main/java/com/hank/musicfree/data/db/entity/LyricCacheEntity.kt`
- `data/src/main/java/com/hank/musicfree/data/db/dao/LyricCacheDao.kt`
- `data/src/main/java/com/hank/musicfree/data/mapper/LyricCacheMapper.kt`
- `data/src/main/java/com/hank/musicfree/data/repository/LyricRepository.kt`
- `data/src/androidTest/java/com/hank/musicfree/data/db/dao/LyricCacheDaoTest.kt`
- `data/src/androidTest/java/com/hank/musicfree/data/repository/LyricRepositoryTest.kt`
- `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/LyricLoadState.kt`
- `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/LyricSearchModels.kt`
- `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricLoader.kt`
- `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricsUiState.kt`
- `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricsContent.kt`
- `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricsOperations.kt`
- `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricSearchSheet.kt`
- `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricLoaderTest.kt`

修改：

- `plugin/src/main/java/com/hank/musicfree/plugin/api/PluginModels.kt`
- `plugin/src/main/java/com/hank/musicfree/plugin/engine/JsBridge.kt`
- `plugin/src/test/java/com/hank/musicfree/plugin/engine/JsBridgeTest.kt`
- `plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt`
- `data/src/main/java/com/hank/musicfree/data/datastore/AppPreferences.kt`
- `data/src/test/java/com/hank/musicfree/data/datastore/AppPreferencesTest.kt`
- `data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt`
- `data/src/main/java/com/hank/musicfree/data/di/DataModule.kt`
- `data/src/main/java/com/hank/musicfree/data/db/converter/Converters.kt`
- `feature/player-ui/build.gradle.kts`
- `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerViewModel.kt`
- `feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt`
- `feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerViewModelTest.kt`
- 仅当实施过程中 plan/spec 状态变化时修改 `docs/DOCS_STATUS.md`。

## 任务 1：Core 歌词领域模型与解析器

**文件：**

- 新建：`core/src/main/java/com/hank/musicfree/core/model/ParsedLyricLine.kt`
- 新建：`core/src/main/java/com/hank/musicfree/core/model/LyricSourceInfo.kt`
- 新建：`core/src/main/java/com/hank/musicfree/core/model/RawLyricPayload.kt`
- 新建：`core/src/main/java/com/hank/musicfree/core/model/LyricDocument.kt`
- 新建：`core/src/main/java/com/hank/musicfree/core/lyric/LyricParser.kt`
- 新建：`core/src/main/java/com/hank/musicfree/core/lyric/LyricTiming.kt`
- 测试：`core/src/test/java/com/hank/musicfree/core/lyric/LyricParserTest.kt`
- 测试：`core/src/test/java/com/hank/musicfree/core/lyric/LyricTimingTest.kt`

- [ ] **步骤 1：编写解析器测试**

新建 `core/src/test/java/com/hank/musicfree/core/lyric/LyricParserTest.kt`：

```kotlin
package com.hank.musicfree.core.lyric

import com.hank.musicfree.core.model.LyricSourceInfo
import com.hank.musicfree.core.model.RawLyricPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricParserTest {

    private val source = LyricSourceInfo.Plugin(platform = "demo")

    @Test
    fun parsesTimestampedLrc() {
        val doc = LyricParser.parse(
            musicId = "m1",
            musicPlatform = "demo",
            payload = RawLyricPayload(rawLrc = "[00:01.00]Hello\n[00:02.50]World"),
            source = source,
        )

        assertEquals(2, doc.lines.size)
        assertEquals(1_000L, doc.lines[0].timeMs)
        assertEquals("Hello", doc.lines[0].text)
        assertEquals(2_500L, doc.lines[1].timeMs)
        assertEquals("World", doc.lines[1].text)
    }

    @Test
    fun parsesMultipleTimestampsOnOneLine() {
        val doc = LyricParser.parse(
            musicId = "m1",
            musicPlatform = "demo",
            payload = RawLyricPayload(rawLrc = "[00:01.00][00:03.00]Repeat"),
            source = source,
        )

        assertEquals(listOf(1_000L, 3_000L), doc.lines.map { it.timeMs })
        assertEquals(listOf("Repeat", "Repeat"), doc.lines.map { it.text })
    }

    @Test
    fun parsesOffsetMetaInMilliseconds() {
        val doc = LyricParser.parse(
            musicId = "m1",
            musicPlatform = "demo",
            payload = RawLyricPayload(rawLrc = "[offset:250]\n[00:01.00]Hello"),
            source = source,
        )

        assertEquals(250L, doc.metaOffsetMs)
    }

    @Test
    fun parsesPlainTextAsStaticLines() {
        val doc = LyricParser.parse(
            musicId = "m1",
            musicPlatform = "demo",
            payload = RawLyricPayload(rawLrcTxt = "Line A\nLine B"),
            source = source,
        )

        assertFalse(doc.isTimed)
        assertEquals(listOf("Line A", "Line B"), doc.lines.map { it.text })
        assertEquals(listOf(0L, 0L), doc.lines.map { it.timeMs })
    }

    @Test
    fun mergesTranslationByTimestamp() {
        val doc = LyricParser.parse(
            musicId = "m1",
            musicPlatform = "demo",
            payload = RawLyricPayload(
                rawLrc = "[00:01.00]Hello\n[00:02.00]World",
                translation = "[00:01.00]你好\n[00:02.00]世界",
            ),
            source = source,
        )

        assertTrue(doc.hasTranslation)
        assertEquals("你好", doc.lines[0].translation)
        assertEquals("世界", doc.lines[1].translation)
    }
}
```

- [ ] **步骤 2：编写时序测试**

新建 `core/src/test/java/com/hank/musicfree/core/lyric/LyricTimingTest.kt`：

```kotlin
package com.hank.musicfree.core.lyric

import com.hank.musicfree.core.model.ParsedLyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricTimingTest {

    private val lines = listOf(
        ParsedLyricLine(index = 0, timeMs = 1_000L, text = "A"),
        ParsedLyricLine(index = 1, timeMs = 2_000L, text = "B"),
        ParsedLyricLine(index = 2, timeMs = 4_000L, text = "C"),
    )

    @Test
    fun currentLineBeforeFirstLineIsNull() {
        assertNull(LyricTiming.currentLineIndex(lines, playbackPositionMs = 500L))
    }

    @Test
    fun currentLineBetweenLinesReturnsPreviousLine() {
        assertEquals(1, LyricTiming.currentLineIndex(lines, playbackPositionMs = 3_000L))
    }

    @Test
    fun positiveUserOffsetAdvancesLyricClock() {
        assertEquals(
            1,
            LyricTiming.currentLineIndex(
                lines = lines,
                playbackPositionMs = 1_500L,
                userOffsetMs = 700L,
                metaOffsetMs = 0L,
            ),
        )
    }

    @Test
    fun seekTargetInvertsDisplayOffset() {
        assertEquals(
            1_300L,
            LyricTiming.seekPositionForLine(
                lineTimeMs = 2_000L,
                userOffsetMs = 700L,
                metaOffsetMs = 0L,
                durationMs = 10_000L,
            ),
        )
    }
}
```

- [ ] **步骤 3：运行测试并确认失败**

运行：

```bash
./gradlew :core:test --tests '*LyricParserTest' --tests '*LyricTimingTest'
```

预期：编译失败，提示 `LyricParser`、`LyricTiming`、`ParsedLyricLine`、`RawLyricPayload`、`LyricSourceInfo` 为 unresolved reference 错误。

- [ ] **步骤 4：新增领域模型**

新建 `core/src/main/java/com/hank/musicfree/core/model/ParsedLyricLine.kt`：

```kotlin
package com.hank.musicfree.core.model

data class ParsedLyricLine(
    val index: Int,
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
)
```

新建 `core/src/main/java/com/hank/musicfree/core/model/LyricSourceInfo.kt`：

```kotlin
package com.hank.musicfree.core.model

sealed interface LyricSourceInfo {
    data class Plugin(val platform: String) : LyricSourceInfo
    data class AutoSearch(val platform: String, val title: String, val id: String) : LyricSourceInfo
    data class Associated(val platform: String, val title: String, val id: String) : LyricSourceInfo
    data object LocalRaw : LyricSourceInfo
    data object LocalTranslation : LyricSourceInfo
    data object Cache : LyricSourceInfo
}
```

新建 `core/src/main/java/com/hank/musicfree/core/model/RawLyricPayload.kt`：

```kotlin
package com.hank.musicfree.core.model

data class RawLyricPayload(
    val rawLrc: String? = null,
    val rawLrcTxt: String? = null,
    val translation: String? = null,
)
```

新建 `core/src/main/java/com/hank/musicfree/core/model/LyricDocument.kt`：

```kotlin
package com.hank.musicfree.core.model

data class LyricDocument(
    val musicId: String,
    val musicPlatform: String,
    val lines: List<ParsedLyricLine>,
    val metaOffsetMs: Long = 0L,
    val source: LyricSourceInfo,
    val rawLrc: String? = null,
    val rawLrcTxt: String? = null,
    val translationRaw: String? = null,
) {
    val hasTranslation: Boolean get() = lines.any { !it.translation.isNullOrBlank() }
    val isTimed: Boolean get() = lines.any { it.timeMs > 0L }
}
```

- [ ] **步骤 5：新增解析器和时序实现**

新建 `core/src/main/java/com/hank/musicfree/core/lyric/LyricParser.kt`：

```kotlin
package com.hank.musicfree.core.lyric

import com.hank.musicfree.core.model.LyricDocument
import com.hank.musicfree.core.model.LyricSourceInfo
import com.hank.musicfree.core.model.ParsedLyricLine
import com.hank.musicfree.core.model.RawLyricPayload

object LyricParser {
    private val timeRegex = Regex("""\[(\d{1,2}:)?(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?]""")
    private val offsetRegex = Regex("""\[offset:([+-]?\d+)]""")

    fun parse(
        musicId: String,
        musicPlatform: String,
        payload: RawLyricPayload,
        source: LyricSourceInfo,
    ): LyricDocument {
        val primaryRaw = payload.rawLrc?.takeIf { it.isNotBlank() }
        val fallbackRaw = payload.rawLrcTxt?.takeIf { it.isNotBlank() }
        val parsed = parseTimedOrPlain(primaryRaw ?: fallbackRaw.orEmpty())
        val translations = payload.translation?.let { parseTimedOrPlain(it).lines } ?: emptyList()
        val translationByTime = translations.groupBy { it.timeMs }.mapValues { it.value.first().text }
        val merged = parsed.lines.map { line ->
            line.copy(translation = translationByTime[line.timeMs])
        }
        return LyricDocument(
            musicId = musicId,
            musicPlatform = musicPlatform,
            lines = merged.mapIndexed { index, line -> line.copy(index = index) },
            metaOffsetMs = parsed.offsetMs,
            source = source,
            rawLrc = payload.rawLrc,
            rawLrcTxt = payload.rawLrcTxt,
            translationRaw = payload.translation,
        )
    }

    private fun parseTimedOrPlain(raw: String): ParsedLyric {
        val text = raw.trim()
        if (text.isBlank()) return ParsedLyric(emptyList(), 0L)
        val offsetMs = offsetRegex.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val timedLines = mutableListOf<ParsedLyricLine>()
        text.lineSequence().forEach { rawLine ->
            val matches = timeRegex.findAll(rawLine).toList()
            if (matches.isEmpty()) return@forEach
            val lyricText = timeRegex.replace(rawLine, "").trim()
            matches.forEach { match ->
                timedLines += ParsedLyricLine(
                    index = timedLines.size,
                    timeMs = parseTimeMs(match.groupValues),
                    text = lyricText,
                )
            }
        }
        if (timedLines.isNotEmpty()) {
            return ParsedLyric(
                lines = timedLines.sortedBy { it.timeMs }.mapIndexed { index, line -> line.copy(index = index) },
                offsetMs = offsetMs,
            )
        }
        return ParsedLyric(
            lines = text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() && !offsetRegex.matches(it) }
                .mapIndexed { index, line -> ParsedLyricLine(index = index, timeMs = 0L, text = line) }
                .toList(),
            offsetMs = offsetMs,
        )
    }

    private fun parseTimeMs(values: List<String>): Long {
        val hourOrMinute = values[1].removeSuffix(":").toLongOrNull()
        val minute: Long
        val second: Long
        val hour: Long
        if (hourOrMinute == null) {
            hour = 0L
            minute = values[2].toLongOrNull() ?: 0L
            second = values[3].toLongOrNull() ?: 0L
        } else {
            hour = hourOrMinute
            minute = values[2].toLongOrNull() ?: 0L
            second = values[3].toLongOrNull() ?: 0L
        }
        val fraction = values[4]
        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> (fraction.toLongOrNull() ?: 0L) * 100L
            2 -> (fraction.toLongOrNull() ?: 0L) * 10L
            else -> fraction.take(3).toLongOrNull() ?: 0L
        }
        return hour * 3_600_000L + minute * 60_000L + second * 1_000L + fractionMs
    }

    private data class ParsedLyric(
        val lines: List<ParsedLyricLine>,
        val offsetMs: Long,
    )
}
```

新建 `core/src/main/java/com/hank/musicfree/core/lyric/LyricTiming.kt`：

```kotlin
package com.hank.musicfree.core.lyric

import com.hank.musicfree.core.model.ParsedLyricLine

object LyricTiming {
    fun currentLineIndex(
        lines: List<ParsedLyricLine>,
        playbackPositionMs: Long,
        userOffsetMs: Long = 0L,
        metaOffsetMs: Long = 0L,
    ): Int? {
        if (lines.isEmpty() || lines.all { it.timeMs == 0L }) return null
        val lyricClockMs = playbackPositionMs + userOffsetMs - metaOffsetMs
        if (lyricClockMs < lines.first().timeMs) return null
        val index = lines.indexOfLast { lyricClockMs >= it.timeMs }
        return index.takeIf { it >= 0 }
    }

    fun seekPositionForLine(
        lineTimeMs: Long,
        userOffsetMs: Long,
        metaOffsetMs: Long,
        durationMs: Long,
    ): Long {
        return (lineTimeMs - userOffsetMs + metaOffsetMs).coerceIn(0L, durationMs.coerceAtLeast(0L))
    }
}
```

- [ ] **步骤 6：运行 core 测试**

运行：

```bash
./gradlew :core:test --tests '*LyricParserTest' --tests '*LyricTimingTest'
```

预期： `BUILD SUCCESSFUL`.

- [ ] **步骤 7：提交**

```bash
git add core/src/main/java/com/hank/musicfree/core/model/ParsedLyricLine.kt \
  core/src/main/java/com/hank/musicfree/core/model/LyricSourceInfo.kt \
  core/src/main/java/com/hank/musicfree/core/model/RawLyricPayload.kt \
  core/src/main/java/com/hank/musicfree/core/model/LyricDocument.kt \
  core/src/main/java/com/hank/musicfree/core/lyric/LyricParser.kt \
  core/src/main/java/com/hank/musicfree/core/lyric/LyricTiming.kt \
  core/src/test/java/com/hank/musicfree/core/lyric/LyricParserTest.kt \
  core/src/test/java/com/hank/musicfree/core/lyric/LyricTimingTest.kt
git commit -m "feat(core): add lyric parser domain"
```

## 任务 2：插件歌词翻译解析

**文件：**

- 修改：`plugin/src/main/java/com/hank/musicfree/plugin/api/PluginModels.kt`
- 修改：`plugin/src/main/java/com/hank/musicfree/plugin/engine/JsBridge.kt`
- 修改：`plugin/src/test/java/com/hank/musicfree/plugin/engine/JsBridgeTest.kt`

- [ ] **步骤 1：扩展插件测试**

在 `JsBridgeTest` 中新增这些测试：

```kotlin
@Test
fun `parseLyricResult parses translation field`() {
    val result = JsBridge.parseLyricResult(
        mapOf(
            "rawLrc" to "[00:01.00]Hello",
            "translation" to "[00:01.00]你好",
        ),
    )

    assertEquals("[00:01.00]你好", result.translation)
}

@Test
fun `parseLyricResult parses trans alias`() {
    val result = JsBridge.parseLyricResult(
        mapOf(
            "rawLrc" to "[00:01.00]Hello",
            "trans" to "[00:01.00]你好",
        ),
    )

    assertEquals("[00:01.00]你好", result.translation)
}
```

- [ ] **步骤 2：运行测试并确认失败**

运行：

```bash
./gradlew :plugin:test --tests '*JsBridgeTest.parseLyricResult*'
```

预期：编译失败，因为 `LyricResult.translation` 尚不存在。

- [ ] **步骤 3：扩展 `LyricResult`**

修改 `plugin/src/main/java/com/hank/musicfree/plugin/api/PluginModels.kt`：

```kotlin
data class LyricResult(
    val rawLrc: String?,
    val rawLrcTxt: String?,
    val translation: String?,
    val lines: List<LyricLine>,
)
```

- [ ] **步骤 4：解析 `translation` 和 `trans`**

修改 `JsBridge.parseLyricResult()`：

```kotlin
fun parseLyricResult(map: Map<String, Any?>): LyricResult {
    val rawLrc = map["rawLrc"]?.toString()
        ?: map["lrc"]?.toString()
        ?: map["lyric"]?.toString()
    val rawLrcTxt = map["rawLrcTxt"]?.toString()
        ?: map["txt"]?.toString()
    val translation = map["translation"]?.toString()
        ?: map["trans"]?.toString()
    val source = rawLrc ?: rawLrcTxt
    return LyricResult(
        rawLrc = rawLrc,
        rawLrcTxt = rawLrcTxt,
        translation = translation,
        lines = parseLrcLines(source.orEmpty()),
    )
}
```

- [ ] **步骤 5：运行 plugin 测试**

运行：

```bash
./gradlew :plugin:test --tests '*JsBridgeTest.parseLyricResult*'
```

预期： `BUILD SUCCESSFUL`.

- [ ] **步骤 6：提交**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/api/PluginModels.kt \
  plugin/src/main/java/com/hank/musicfree/plugin/engine/JsBridge.kt \
  plugin/src/test/java/com/hank/musicfree/plugin/engine/JsBridgeTest.kt
git commit -m "feat(plugin): parse lyric translation payloads"
```

## 任务 3：DataStore 歌词偏好

**文件：**

- 修改：`data/src/main/java/com/hank/musicfree/data/datastore/AppPreferences.kt`
- 修改：`data/src/test/java/com/hank/musicfree/data/datastore/AppPreferencesTest.kt`

- [ ] **步骤 1：新增失败的偏好测试**

追加到 `AppPreferencesTest`：

```kotlin
@Test
fun `default lyric show translation is false`() = testScope.runTest {
    assertFalse(prefs.lyricShowTranslation.first())
}

@Test
fun `set lyric show translation`() = testScope.runTest {
    prefs.setLyricShowTranslation(true)
    assertTrue(prefs.lyricShowTranslation.first())
}

@Test
fun `default lyric detail font size is one`() = testScope.runTest {
    assertEquals(1, prefs.lyricDetailFontSize.first())
}

@Test
fun `set lyric detail font size coerces to supported range`() = testScope.runTest {
    prefs.setLyricDetailFontSize(9)
    assertEquals(3, prefs.lyricDetailFontSize.first())
}

@Test
fun `default lyric auto search is true`() = testScope.runTest {
    assertTrue(prefs.lyricAutoSearchEnabled.first())
}

@Test
fun `set lyric auto search`() = testScope.runTest {
    prefs.setLyricAutoSearchEnabled(false)
    assertFalse(prefs.lyricAutoSearchEnabled.first())
}
```

- [ ] **步骤 2：运行测试并确认失败**

运行：

```bash
./gradlew :data:test --tests '*AppPreferencesTest*'
```

预期：编译失败，因为歌词偏好属性和 setter 尚不存在。

- [ ] **步骤 3：实现偏好项**

在 `AppPreferences` 中新增：

```kotlin
val lyricShowTranslation: Flow<Boolean> = dataStore.data.map { prefs ->
    prefs[KEY_LYRIC_SHOW_TRANSLATION] ?: false
}

suspend fun setLyricShowTranslation(enabled: Boolean) {
    dataStore.edit { it[KEY_LYRIC_SHOW_TRANSLATION] = enabled }
}

val lyricDetailFontSize: Flow<Int> = dataStore.data.map { prefs ->
    (prefs[KEY_LYRIC_DETAIL_FONT_SIZE] ?: 1).coerceIn(0, 3)
}

suspend fun setLyricDetailFontSize(size: Int) {
    dataStore.edit { it[KEY_LYRIC_DETAIL_FONT_SIZE] = size.coerceIn(0, 3) }
}

val lyricAutoSearchEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
    prefs[KEY_LYRIC_AUTO_SEARCH_ENABLED] ?: true
}

suspend fun setLyricAutoSearchEnabled(enabled: Boolean) {
    dataStore.edit { it[KEY_LYRIC_AUTO_SEARCH_ENABLED] = enabled }
}
```

在 companion object 中新增 key：

```kotlin
val KEY_LYRIC_SHOW_TRANSLATION = booleanPreferencesKey("lyric_show_translation")
val KEY_LYRIC_DETAIL_FONT_SIZE = intPreferencesKey("lyric_detail_font_size")
val KEY_LYRIC_AUTO_SEARCH_ENABLED = booleanPreferencesKey("lyric_auto_search_enabled")
```

- [ ] **步骤 4：运行测试**

运行：

```bash
./gradlew :data:test --tests '*AppPreferencesTest*'
```

预期： `BUILD SUCCESSFUL`.

- [ ] **步骤 5：提交**

```bash
git add data/src/main/java/com/hank/musicfree/data/datastore/AppPreferences.kt \
  data/src/test/java/com/hank/musicfree/data/datastore/AppPreferencesTest.kt
git commit -m "feat(data): add lyric preferences"
```

## 任务 4：Room 歌词缓存持久化

**文件：**

- 新建：`data/src/main/java/com/hank/musicfree/data/db/entity/LyricCacheEntity.kt`
- 新建：`data/src/main/java/com/hank/musicfree/data/db/dao/LyricCacheDao.kt`
- 修改：`data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt`
- 修改：`data/src/main/java/com/hank/musicfree/data/di/DataModule.kt`
- 修改：`data/src/main/java/com/hank/musicfree/data/db/converter/Converters.kt`
- 新建：`data/src/androidTest/java/com/hank/musicfree/data/db/dao/LyricCacheDaoTest.kt`
- Schema: `data/schemas/com.hank.musicfree.data.db.AppDatabase/4.json`

- [ ] **步骤 1：新增 DAO 测试**

新建 `LyricCacheDaoTest.kt`：

```kotlin
package com.hank.musicfree.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.entity.LyricCacheEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LyricCacheDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: LyricCacheDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.lyricCacheDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun upsertAndObserveByKey() = runTest {
        dao.upsert(entity("1", "demo", remoteRawLrc = "[00:01.00]Hello"))

        val row = dao.observeByKey("demo", "1").first()

        assertEquals("[00:01.00]Hello", row?.remoteRawLrc)
    }

    @Test
    fun clearAssociationKeepsLyrics() = runTest {
        dao.upsert(entity("1", "demo", remoteRawLrc = "raw", associatedMusicJson = """{"id":"l1"}"""))

        dao.clearAssociation("demo", "1", updatedAt = 200L)
        val row = dao.getByKey("demo", "1")

        assertEquals("raw", row?.remoteRawLrc)
        assertNull(row?.associatedMusicJson)
        assertEquals(200L, row?.updatedAt)
    }

    @Test
    fun deleteLocalLyricsKeepsRemoteCache() = runTest {
        dao.upsert(entity("1", "demo", remoteRawLrc = "remote", localRawLrc = "local", localTranslation = "tran"))

        dao.deleteLocalLyrics("demo", "1", updatedAt = 300L)
        val row = dao.getByKey("demo", "1")

        assertEquals("remote", row?.remoteRawLrc)
        assertNull(row?.localRawLrc)
        assertNull(row?.localTranslation)
    }

    private fun entity(
        id: String,
        platform: String,
        remoteRawLrc: String? = null,
        associatedMusicJson: String? = null,
        localRawLrc: String? = null,
        localTranslation: String? = null,
    ) = LyricCacheEntity(
        musicId = id,
        musicPlatform = platform,
        remoteRawLrc = remoteRawLrc,
        remoteRawLrcTxt = null,
        remoteTranslation = null,
        remoteSourceType = null,
        remoteSourcePlatform = null,
        remoteSourceMusicId = null,
        remoteSourceTitle = null,
        localRawLrc = localRawLrc,
        localTranslation = localTranslation,
        associatedMusicJson = associatedMusicJson,
        userOffsetMs = 0L,
        updatedAt = 100L,
    )
}
```

- [ ] **步骤 2：运行 DAO 测试并确认失败**

运行：

```bash
./gradlew :data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hank.musicfree.data.db.dao.LyricCacheDaoTest
```

预期：编译失败，提示 `LyricCacheEntity`、`LyricCacheDao`、`AppDatabase.lyricCacheDao()` 不存在。若未连接设备，运行 `./gradlew :data:compileDebugAndroidTestKotlin`，预期同样出现这些 unresolved reference 错误。

- [ ] **步骤 3：新增 entity 和 DAO**

新建 `LyricCacheEntity.kt`：

```kotlin
package com.hank.musicfree.data.db.entity

import androidx.room.Entity

@Entity(tableName = "lyric_cache", primaryKeys = ["musicId", "musicPlatform"])
data class LyricCacheEntity(
    val musicId: String,
    val musicPlatform: String,
    val remoteRawLrc: String?,
    val remoteRawLrcTxt: String?,
    val remoteTranslation: String?,
    val remoteSourceType: String?,
    val remoteSourcePlatform: String?,
    val remoteSourceMusicId: String?,
    val remoteSourceTitle: String?,
    val localRawLrc: String?,
    val localTranslation: String?,
    val associatedMusicJson: String?,
    val userOffsetMs: Long,
    val updatedAt: Long,
)
```

新建 `LyricCacheDao.kt`：

```kotlin
package com.hank.musicfree.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hank.musicfree.data.db.entity.LyricCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricCacheDao {
    @Query("SELECT * FROM lyric_cache WHERE musicPlatform = :platform AND musicId = :id")
    fun observeByKey(platform: String, id: String): Flow<LyricCacheEntity?>

    @Query("SELECT * FROM lyric_cache WHERE musicPlatform = :platform AND musicId = :id")
    suspend fun getByKey(platform: String, id: String): LyricCacheEntity?

    @Upsert
    suspend fun upsert(entity: LyricCacheEntity)

    @Query(
        """
        UPDATE lyric_cache
        SET associatedMusicJson = NULL, updatedAt = :updatedAt
        WHERE musicPlatform = :platform AND musicId = :id
        """,
    )
    suspend fun clearAssociation(platform: String, id: String, updatedAt: Long)

    @Query(
        """
        UPDATE lyric_cache
        SET localRawLrc = NULL, localTranslation = NULL, updatedAt = :updatedAt
        WHERE musicPlatform = :platform AND musicId = :id
        """,
    )
    suspend fun deleteLocalLyrics(platform: String, id: String, updatedAt: Long)

    @Query(
        """
        UPDATE lyric_cache
        SET userOffsetMs = :offsetMs, updatedAt = :updatedAt
        WHERE musicPlatform = :platform AND musicId = :id
        """,
    )
    suspend fun setOffset(platform: String, id: String, offsetMs: Long, updatedAt: Long)
}
```

- [ ] **步骤 4：注册 Room 表和 DAO**

修改 `AppDatabase`：

```kotlin
@Database(
    entities = [
        MusicItemEntity::class,
        PlaylistEntity::class,
        PlaylistMusicCrossRef::class,
        PlayQueueEntity::class,
        StarredSheetEntity::class,
        LyricCacheEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playQueueDao(): PlayQueueDao
    abstract fun starredSheetDao(): StarredSheetDao
    abstract fun lyricCacheDao(): LyricCacheDao
}
```

修改 `DataModule`：

```kotlin
@Provides
fun provideLyricCacheDao(db: AppDatabase): LyricCacheDao = db.lyricCacheDao()
```

- [ ] **步骤 5：新增 `MusicItem` JSON converter**

在 `Converters` 中新增：

```kotlin
fun musicItemToJson(item: MusicItem?): String? {
    if (item == null) return null
    val json = JSONObject()
    json.put("id", item.id)
    json.put("platform", item.platform)
    json.put("title", item.title)
    json.put("artist", item.artist)
    json.put("album", item.album ?: JSONObject.NULL)
    json.put("duration", item.duration)
    json.put("url", item.url ?: JSONObject.NULL)
    json.put("artwork", item.artwork ?: JSONObject.NULL)
    json.put("qualities", qualitiesToJson(item.qualities) ?: JSONObject.NULL)
    return json.toString()
}

fun jsonToMusicItem(json: String?): MusicItem? {
    if (json.isNullOrBlank()) return null
    val obj = JSONObject(json)
    return MusicItem(
        id = obj.getString("id"),
        platform = obj.getString("platform"),
        title = obj.optString("title"),
        artist = obj.optString("artist"),
        album = if (obj.isNull("album")) null else obj.getString("album"),
        duration = obj.optLong("duration", 0L),
        url = if (obj.isNull("url")) null else obj.getString("url"),
        artwork = if (obj.isNull("artwork")) null else obj.getString("artwork"),
        qualities = if (obj.isNull("qualities")) null else jsonToQualities(obj.getString("qualities")),
    )
}
```

新增 import：

```kotlin
import com.hank.musicfree.core.model.MusicItem
```

- [ ] **步骤 6：运行 Room 编译并生成 schema**

运行：

```bash
./gradlew :data:kspDebugKotlin
```

预期：

- `BUILD SUCCESSFUL`.
- `data/schemas/com.hank.musicfree.data.db.AppDatabase/4.json` 已生成。

- [ ] **步骤 7：运行 DAO 测试**

有设备或模拟器时运行：

```bash
./gradlew :data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hank.musicfree.data.db.dao.LyricCacheDaoTest
```

没有设备时运行：

```bash
./gradlew :data:compileDebugAndroidTestKotlin
```

预期：

- 有设备路径：`BUILD SUCCESSFUL`。
- 无设备路径：Android test source 编译成功。

- [ ] **步骤 8：提交**

```bash
git add data/src/main/java/com/hank/musicfree/data/db/entity/LyricCacheEntity.kt \
  data/src/main/java/com/hank/musicfree/data/db/dao/LyricCacheDao.kt \
  data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt \
  data/src/main/java/com/hank/musicfree/data/di/DataModule.kt \
  data/src/main/java/com/hank/musicfree/data/db/converter/Converters.kt \
  data/src/androidTest/java/com/hank/musicfree/data/db/dao/LyricCacheDaoTest.kt \
  data/schemas/com.hank.musicfree.data.db.AppDatabase/4.json
git commit -m "feat(data): add lyric cache table"
```

## 任务 5：Data 歌词 Repository

**文件：**

- 新建：`data/src/main/java/com/hank/musicfree/data/mapper/LyricCacheMapper.kt`
- 新建：`data/src/main/java/com/hank/musicfree/data/repository/LyricRepository.kt`
- 新建：`data/src/androidTest/java/com/hank/musicfree/data/repository/LyricRepositoryTest.kt`

- [ ] **步骤 1：新增 repository 测试**

新建 `LyricRepositoryTest.kt`：

```kotlin
package com.hank.musicfree.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hank.musicfree.core.model.LyricSourceInfo
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.RawLyricPayload
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.converter.Converters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LyricRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: LyricRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = LyricRepository(db.lyricCacheDao(), Converters())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun saveRemoteLyricWritesCache() = runTest {
        val music = musicItem("1", "demo")

        repository.saveRemoteLyric(
            music = music,
            source = LyricSourceInfo.Plugin("demo"),
            payload = RawLyricPayload(rawLrc = "[00:01.00]Hello", translation = "[00:01.00]你好"),
        )

        val cache = repository.observeCache(music).first()
        assertEquals("[00:01.00]Hello", cache?.remotePayload?.rawLrc)
        assertEquals("[00:01.00]你好", cache?.remotePayload?.translation)
        assertEquals("demo", cache?.remoteSourcePlatform)
    }

    @Test
    fun importLocalLyricKeepsRemoteCache() = runTest {
        val music = musicItem("1", "demo")
        repository.saveRemoteLyric(music, LyricSourceInfo.Plugin("demo"), RawLyricPayload(rawLrc = "remote"))

        repository.importLocalLyric(music, "local", LocalLyricKind.Raw)

        val cache = repository.getCache(music)
        assertEquals("remote", cache?.remotePayload?.rawLrc)
        assertEquals("local", cache?.localRawLrc)
    }

    @Test
    fun associateAndClearLyric() = runTest {
        val music = musicItem("1", "demo")
        val target = musicItem("lrc-1", "lyric")

        repository.associateLyric(music, target)
        assertEquals(target.id, repository.getCache(music)?.associatedMusic?.id)

        repository.clearAssociatedLyric(music)
        assertNull(repository.getCache(music)?.associatedMusic)
    }

    @Test
    fun setOffsetCreatesCacheRow() = runTest {
        val music = musicItem("1", "demo")

        repository.setLyricOffset(music, 500L)

        assertEquals(500L, repository.getCache(music)?.userOffsetMs)
    }

    private fun musicItem(id: String, platform: String) = MusicItem(
        id = id,
        platform = platform,
        title = "Song $id",
        artist = "Artist",
        album = null,
        duration = 180_000L,
        url = null,
        artwork = null,
        qualities = null,
    )
}
```

- [ ] **步骤 2：运行测试并确认失败**

运行：

```bash
./gradlew :data:compileDebugAndroidTestKotlin
```

预期：编译失败，提示 `LyricRepository`、`LocalLyricKind` 和 cache domain 字段为 unresolved reference 错误。

- [ ] **步骤 3：新增 mapper 和 repository**

新建 `LyricCacheMapper.kt`：

```kotlin
package com.hank.musicfree.data.mapper

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.RawLyricPayload
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.db.entity.LyricCacheEntity

data class LyricCache(
    val musicId: String,
    val musicPlatform: String,
    val remotePayload: RawLyricPayload?,
    val remoteSourceType: String?,
    val remoteSourcePlatform: String?,
    val remoteSourceMusicId: String?,
    val remoteSourceTitle: String?,
    val localRawLrc: String?,
    val localTranslation: String?,
    val associatedMusic: MusicItem?,
    val userOffsetMs: Long,
)

fun LyricCacheEntity.toModel(converters: Converters): LyricCache = LyricCache(
    musicId = musicId,
    musicPlatform = musicPlatform,
    remotePayload = if (remoteRawLrc != null || remoteRawLrcTxt != null || remoteTranslation != null) {
        RawLyricPayload(remoteRawLrc, remoteRawLrcTxt, remoteTranslation)
    } else {
        null
    },
    remoteSourceType = remoteSourceType,
    remoteSourcePlatform = remoteSourcePlatform,
    remoteSourceMusicId = remoteSourceMusicId,
    remoteSourceTitle = remoteSourceTitle,
    localRawLrc = localRawLrc,
    localTranslation = localTranslation,
    associatedMusic = converters.jsonToMusicItem(associatedMusicJson),
    userOffsetMs = userOffsetMs,
)
```

新建 `LyricRepository.kt`：

```kotlin
package com.hank.musicfree.data.repository

import com.hank.musicfree.core.model.LyricSourceInfo
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.RawLyricPayload
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.db.dao.LyricCacheDao
import com.hank.musicfree.data.db.entity.LyricCacheEntity
import com.hank.musicfree.data.mapper.LyricCache
import com.hank.musicfree.data.mapper.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class LocalLyricKind { Raw, Translation }

@Singleton
class LyricRepository @Inject constructor(
    private val lyricCacheDao: LyricCacheDao,
    private val converters: Converters,
) {
    fun observeCache(music: MusicItem): Flow<LyricCache?> =
        lyricCacheDao.observeByKey(music.platform, music.id).map { it?.toModel(converters) }

    suspend fun getCache(music: MusicItem): LyricCache? =
        lyricCacheDao.getByKey(music.platform, music.id)?.toModel(converters)

    suspend fun saveRemoteLyric(music: MusicItem, source: LyricSourceInfo, payload: RawLyricPayload) {
        val current = lyricCacheDao.getByKey(music.platform, music.id)
        lyricCacheDao.upsert(
            baseEntity(music, current).copy(
                remoteRawLrc = payload.rawLrc,
                remoteRawLrcTxt = payload.rawLrcTxt,
                remoteTranslation = payload.translation,
                remoteSourceType = source::class.simpleName,
                remoteSourcePlatform = source.platformOrNull(),
                remoteSourceMusicId = source.idOrNull(),
                remoteSourceTitle = source.titleOrNull(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun associateLyric(music: MusicItem, target: MusicItem) {
        val current = lyricCacheDao.getByKey(music.platform, music.id)
        lyricCacheDao.upsert(
            baseEntity(music, current).copy(
                associatedMusicJson = converters.musicItemToJson(target),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clearAssociatedLyric(music: MusicItem) {
        ensureRow(music)
        lyricCacheDao.clearAssociation(music.platform, music.id, System.currentTimeMillis())
    }

    suspend fun importLocalLyric(music: MusicItem, rawText: String, kind: LocalLyricKind) {
        val current = lyricCacheDao.getByKey(music.platform, music.id)
        val base = baseEntity(music, current)
        lyricCacheDao.upsert(
            when (kind) {
                LocalLyricKind.Raw -> base.copy(localRawLrc = rawText, updatedAt = System.currentTimeMillis())
                LocalLyricKind.Translation -> base.copy(localTranslation = rawText, updatedAt = System.currentTimeMillis())
            },
        )
    }

    suspend fun deleteLocalLyric(music: MusicItem) {
        ensureRow(music)
        lyricCacheDao.deleteLocalLyrics(music.platform, music.id, System.currentTimeMillis())
    }

    suspend fun setLyricOffset(music: MusicItem, offsetMs: Long) {
        ensureRow(music)
        lyricCacheDao.setOffset(music.platform, music.id, offsetMs, System.currentTimeMillis())
    }

    private suspend fun ensureRow(music: MusicItem) {
        if (lyricCacheDao.getByKey(music.platform, music.id) == null) {
            lyricCacheDao.upsert(baseEntity(music, null))
        }
    }

    private fun baseEntity(music: MusicItem, current: LyricCacheEntity?): LyricCacheEntity =
        current ?: LyricCacheEntity(
            musicId = music.id,
            musicPlatform = music.platform,
            remoteRawLrc = null,
            remoteRawLrcTxt = null,
            remoteTranslation = null,
            remoteSourceType = null,
            remoteSourcePlatform = null,
            remoteSourceMusicId = null,
            remoteSourceTitle = null,
            localRawLrc = null,
            localTranslation = null,
            associatedMusicJson = null,
            userOffsetMs = 0L,
            updatedAt = System.currentTimeMillis(),
        )
}

private fun LyricSourceInfo.platformOrNull(): String? = when (this) {
    is LyricSourceInfo.Plugin -> platform
    is LyricSourceInfo.AutoSearch -> platform
    is LyricSourceInfo.Associated -> platform
    LyricSourceInfo.Cache -> null
    LyricSourceInfo.LocalRaw -> null
    LyricSourceInfo.LocalTranslation -> null
}

private fun LyricSourceInfo.idOrNull(): String? = when (this) {
    is LyricSourceInfo.AutoSearch -> id
    is LyricSourceInfo.Associated -> id
    else -> null
}

private fun LyricSourceInfo.titleOrNull(): String? = when (this) {
    is LyricSourceInfo.AutoSearch -> title
    is LyricSourceInfo.Associated -> title
    else -> null
}
```

- [ ] **步骤 4：运行 repository 编译/测试**

运行：

```bash
./gradlew :data:compileDebugAndroidTestKotlin
```

预期： `BUILD SUCCESSFUL`.

若当前有可用设备或模拟器：

```bash
./gradlew :data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hank.musicfree.data.repository.LyricRepositoryTest
```

预期： `BUILD SUCCESSFUL`.

- [ ] **步骤 5：提交**

```bash
git add data/src/main/java/com/hank/musicfree/data/mapper/LyricCacheMapper.kt \
  data/src/main/java/com/hank/musicfree/data/repository/LyricRepository.kt \
  data/src/androidTest/java/com/hank/musicfree/data/repository/LyricRepositoryTest.kt
git commit -m "feat(data): add lyric repository"
```

## 任务 6：PluginManager 歌词搜索插件 Flow

**文件：**

- 修改：`plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt`
- 测试：`plugin/src/test/java/com/hank/musicfree/plugin/manager/PluginManagerUpdateFlowTest.kt`
- 后续使用场景：`feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricLoaderTest.kt` 由任务 7 覆盖。

- [ ] **步骤 1：新增方法**

在 `PluginManager` 的 `getSearchablePlugins()` 附近新增：

```kotlin
fun getLyricSearchablePlugins(): Flow<List<LoadedPlugin>> =
    getSortedEnabledPlugins().map { plugins ->
        plugins.filter { it.info.supportsSearchType("lyric") }
    }
```

这里必须区分“未声明 `supportedSearchType`”和“显式声明 `supportedSearchType: []`”。RN 的 `getSearchablePlugins("lyric")` 会把未声明字段的旧插件纳入候选，但显式空数组会走 `includes("lyric")` 并被排除。因此 Android 侧需要在 `PluginInfo` 中保留 `supportedSearchTypeDeclared` 之类的声明标记，不能只依赖列表是否为空。

不要修改 `getSearchablePlugins()` 的外部语义，因为搜索页依赖它只返回音乐搜索插件的现有行为。若 `extractPluginInfo()` 当前把缺省 `supportedSearchType` 解析成 `listOf("music")`，需改为“空列表 + 未声明标记”，让音乐搜索和歌词搜索都通过同一个 legacy 分支兼容旧插件，同时排除显式声明为空数组的插件。

- [ ] **步骤 2：运行 plugin 编译**

运行：

```bash
./gradlew :plugin:compileDebugKotlin
```

预期： `BUILD SUCCESSFUL`.

- [ ] **步骤 3：提交**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt
git commit -m "feat(plugin): expose lyric-searchable plugins"
```

## 任务 7：播放页歌词 Loader

**文件：**

- 修改：`feature/player-ui/build.gradle.kts`
- 新建：`feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/LyricLoadState.kt`
- 新建：`feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/LyricSearchModels.kt`
- 新建：`feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricLoader.kt`
- 新建：`feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricLoaderTest.kt`

- [ ] **步骤 1：新增 plugin 依赖**

修改 `feature/player-ui/build.gradle.kts`：

```kotlin
implementation(project(":plugin"))
```

- [ ] **步骤 2：新增 loader 测试**

新建 `PlayerLyricLoaderTest.kt`，包含三个初始测试：

```kotlin
package com.hank.musicfree.feature.playerui.lyrics

import com.hank.musicfree.core.model.LyricSourceInfo
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.RawLyricPayload
import com.hank.musicfree.data.mapper.LyricCache
import com.hank.musicfree.data.repository.LyricRepository
import com.hank.musicfree.plugin.api.LyricResult
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.api.SearchResult
import com.hank.musicfree.plugin.manager.LoadedPlugin
import com.hank.musicfree.plugin.manager.PluginManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PlayerLyricLoaderTest {

    private val lyricRepository: LyricRepository = mock()
    private val pluginManager: PluginManager = mock()
    private val lyricPlugins = MutableStateFlow<List<LoadedPlugin>>(emptyList())

    init {
        whenever(pluginManager.getLyricSearchablePlugins()).thenReturn(lyricPlugins)
    }

    @Test
    fun localLyricsWinBeforePluginFetch() = runTest {
        val music = music("1", "demo")
        whenever(lyricRepository.observeCache(music)).thenReturn(
            flowOf(
                LyricCache(
                    musicId = music.id,
                    musicPlatform = music.platform,
                    remotePayload = RawLyricPayload(rawLrc = "[00:01.00]Remote"),
                    remoteSourceType = "Plugin",
                    remoteSourcePlatform = "demo",
                    remoteSourceMusicId = null,
                    remoteSourceTitle = null,
                    localRawLrc = "[00:01.00]Local",
                    localTranslation = null,
                    associatedMusic = null,
                    userOffsetMs = 0L,
                ),
            ),
        )

        val state = PlayerLyricLoader(lyricRepository, pluginManager).observeLyrics(music)
            .firstReady()

        assertEquals("Local", state.document.lines.first().text)
    }

    @Test
    fun pluginLyricsAreCached() = runTest {
        val music = music("1", "demo")
        val plugin = plugin("demo", lyric = LyricResult("[00:01.00]Remote", null, null, emptyList()))
        whenever(pluginManager.getPlugin("demo")).thenReturn(plugin)
        whenever(lyricRepository.observeCache(music)).thenReturn(flowOf(null))

        val state = PlayerLyricLoader(lyricRepository, pluginManager).observeLyrics(music)
            .firstReady()

        assertEquals("Remote", state.document.lines.first().text)
        verify(lyricRepository).saveRemoteLyric(
            music = any(),
            source = any(),
            payload = any(),
        )
    }

    @Test
    fun autoSearchUsesOtherLyricPlugin() = runTest {
        val music = music("1", "demo")
        val lyricCandidate = music("lyric-1", "lyric")
        val demoPlugin = plugin("demo", lyric = null)
        val lyricPlugin = plugin(
            platform = "lyric",
            search = SearchResult(isEnd = true, data = listOf(lyricCandidate)),
            lyric = LyricResult("[00:01.00]Found", null, null, emptyList()),
        )
        whenever(pluginManager.getPlugin("demo")).thenReturn(demoPlugin)
        whenever(pluginManager.getPlugin("lyric")).thenReturn(lyricPlugin)
        lyricPlugins.value = listOf(lyricPlugin)
        whenever(lyricRepository.observeCache(music)).thenReturn(flowOf(null))

        val state = PlayerLyricLoader(lyricRepository, pluginManager).observeLyrics(music)
            .firstReady()

        assertEquals("Found", state.document.lines.first().text)
        assertTrue(state.document.source is LyricSourceInfo.AutoSearch)
    }

    private fun music(id: String, platform: String) = MusicItem(
        id = id,
        platform = platform,
        title = "Song",
        artist = "Artist",
        album = null,
        duration = 180_000L,
        url = null,
        artwork = null,
        qualities = null,
    )
}
```

在同一文件中补充下列 import 和 helper；import 放在文件顶部，helper 放在 `PlayerLyricLoaderTest` class 外：

```kotlin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

private suspend fun Flow<LyricLoadState>.firstReady(): LyricLoadState.Ready {
    return take(10).toList().filterIsInstance<LyricLoadState.Ready>().first()
}

private fun plugin(
    platform: String,
    search: SearchResult = SearchResult(isEnd = true, data = emptyList()),
    lyric: LyricResult?,
): LoadedPlugin {
    val plugin = mock<LoadedPlugin>()
    whenever(plugin.info).thenReturn(
        PluginInfo(
            platform = platform,
            version = null,
            author = null,
            description = null,
            srcUrl = null,
            supportedSearchType = listOf("lyric"),
        ),
    )
    runBlocking {
        whenever(plugin.search(any(), any(), any())).thenReturn(search)
        whenever(plugin.getLyric(any())).thenReturn(lyric)
    }
    return plugin
}
```

- [ ] **步骤 3：运行测试编译并确认失败**

运行：

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerLyricLoaderTest*'
```

预期：编译失败，提示 loader 类为 unresolved reference 错误。

- [ ] **步骤 4：新增状态模型**

新建 `LyricLoadState.kt`：

```kotlin
package com.hank.musicfree.feature.playerui.lyrics

import com.hank.musicfree.core.model.LyricDocument
import com.hank.musicfree.core.model.MusicItem

sealed interface LyricLoadState {
    data object NoTrack : LyricLoadState
    data class Loading(val music: MusicItem) : LyricLoadState
    data class Ready(
        val music: MusicItem,
        val document: LyricDocument,
        val userOffsetMs: Long,
    ) : LyricLoadState
    data class NoLyric(val music: MusicItem) : LyricLoadState
    data class Error(val music: MusicItem, val message: String) : LyricLoadState
}
```

新建 `LyricSearchModels.kt`：

```kotlin
package com.hank.musicfree.feature.playerui.lyrics

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.plugin.api.PluginInfo

data class LyricSearchGroup(
    val plugin: PluginInfo,
    val items: List<MusicItem>,
    val errorMessage: String? = null,
)
```

- [ ] **步骤 5：新增 loader 实现**

新建 `PlayerLyricLoader.kt`：

```kotlin
package com.hank.musicfree.feature.playerui.lyrics

import com.hank.musicfree.core.lyric.LyricParser
import com.hank.musicfree.core.model.LyricSourceInfo
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.model.RawLyricPayload
import com.hank.musicfree.data.mapper.LyricCache
import com.hank.musicfree.data.repository.LyricRepository
import com.hank.musicfree.plugin.api.LyricResult
import com.hank.musicfree.plugin.manager.PluginManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class PlayerLyricLoader @Inject constructor(
    private val lyricRepository: LyricRepository,
    private val pluginManager: PluginManager,
) {
    fun observeLyrics(music: MusicItem?): Flow<LyricLoadState> = flow {
        if (music == null) {
            emit(LyricLoadState.NoTrack)
            return@flow
        }
        emit(LyricLoadState.Loading(music))
        val cache = lyricRepository.observeCache(music).first()
        localDocument(music, cache)?.let {
            emit(LyricLoadState.Ready(music, it, cache?.userOffsetMs ?: 0L))
            return@flow
        }
        cachedDocument(music, cache)?.let {
            emit(LyricLoadState.Ready(music, it, cache?.userOffsetMs ?: 0L))
            return@flow
        }
        val associated = cache?.associatedMusic
        val target = associated ?: music
        val source = associated?.let { LyricSourceInfo.Associated(it.platform, it.title, it.id) }
            ?: LyricSourceInfo.Plugin(target.platform)
        fetchFromPlugin(target, source)?.let { payload ->
            lyricRepository.saveRemoteLyric(music, source, payload)
            emit(LyricLoadState.Ready(music, parse(music, payload, source), cache?.userOffsetMs ?: 0L))
            return@flow
        }
        autoSearch(music)?.let { (payload, autoSource) ->
            lyricRepository.saveRemoteLyric(music, autoSource, payload)
            emit(LyricLoadState.Ready(music, parse(music, payload, autoSource), cache?.userOffsetMs ?: 0L))
            return@flow
        }
        emit(LyricLoadState.NoLyric(music))
    }

    suspend fun searchCandidates(music: MusicItem, query: String = music.title): List<LyricSearchGroup> {
        return pluginManager.getLyricSearchablePlugins().first()
            .filter { it.info.platform != music.platform }
            .map { plugin ->
                runCatching {
                    LyricSearchGroup(plugin.info, plugin.search(query, page = 1, type = "lyric").data)
                }.getOrElse {
                    LyricSearchGroup(plugin.info, emptyList(), it.message ?: "搜索歌词失败")
                }
            }
    }

    suspend fun associateLyric(music: MusicItem, target: MusicItem) = lyricRepository.associateLyric(music, target)
    suspend fun clearAssociatedLyric(music: MusicItem) = lyricRepository.clearAssociatedLyric(music)
    suspend fun importLocalLyric(music: MusicItem, rawText: String, kind: com.hank.musicfree.data.repository.LocalLyricKind) =
        lyricRepository.importLocalLyric(music, rawText, kind)
    suspend fun deleteLocalLyric(music: MusicItem) = lyricRepository.deleteLocalLyric(music)
    suspend fun setLyricOffset(music: MusicItem, offsetMs: Long) = lyricRepository.setLyricOffset(music, offsetMs)

    private fun localDocument(music: MusicItem, cache: LyricCache?) =
        cache?.takeIf { it.localRawLrc != null || it.localTranslation != null }?.let {
            parse(
                music,
                RawLyricPayload(rawLrc = it.localRawLrc, translation = it.localTranslation),
                LyricSourceInfo.LocalRaw,
            )
        }?.takeIf { it.lines.isNotEmpty() }

    private fun cachedDocument(music: MusicItem, cache: LyricCache?) =
        cache?.remotePayload?.let { parse(music, it, LyricSourceInfo.Cache) }?.takeIf { it.lines.isNotEmpty() }

    private suspend fun fetchFromPlugin(target: MusicItem, source: LyricSourceInfo): RawLyricPayload? {
        val plugin = pluginManager.getPlugin(target.platform) ?: return null
        return plugin.getLyric(target)?.toPayload()?.takeIf { it.hasText() }
    }

    private suspend fun autoSearch(music: MusicItem): Pair<RawLyricPayload, LyricSourceInfo>? {
        val groups = searchCandidates(music)
        val candidates = groups.flatMap { group -> group.items.map { group.plugin.platform to it } }
        for ((platform, item) in candidates.take(6)) {
            val plugin = pluginManager.getPlugin(platform) ?: continue
            val payload = plugin.getLyric(item)?.toPayload()?.takeIf { it.hasText() } ?: continue
            return payload to LyricSourceInfo.AutoSearch(platform = platform, title = item.title, id = item.id)
        }
        return null
    }

    private fun parse(music: MusicItem, payload: RawLyricPayload, source: LyricSourceInfo) =
        LyricParser.parse(music.id, music.platform, payload, source)
}

private fun LyricResult.toPayload() = RawLyricPayload(rawLrc = rawLrc, rawLrcTxt = rawLrcTxt, translation = translation)
private fun RawLyricPayload.hasText() = !rawLrc.isNullOrBlank() || !rawLrcTxt.isNullOrBlank() || !translation.isNullOrBlank()
```

- [ ] **步骤 6：运行 loader 测试**

运行：

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerLyricLoaderTest*'
```

预期： `BUILD SUCCESSFUL`.

- [ ] **步骤 7：提交**

```bash
git add feature/player-ui/build.gradle.kts \
  feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/LyricLoadState.kt \
  feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/LyricSearchModels.kt \
  feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricLoader.kt \
  feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricLoaderTest.kt
git commit -m "feat(player-ui): add lyric loader"
```

## 任务 8：PlayerViewModel 歌词状态

**文件：**

- 新建：`feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricsUiState.kt`
- 修改：`feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerViewModel.kt`
- 修改：`feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerViewModelTest.kt`

- [ ] **步骤 1：新增 ViewModel 测试**

在 `PlayerViewModelTest` 中新增测试：

```kotlin
@Test
fun `lyrics ui state updates current line from position`() = runTest {
    val item = MusicItem(id = "1", platform = "demo", title = "Song", artist = "A", album = null, duration = 10_000L, url = null, artwork = null, qualities = null)
    val document = LyricDocument(
        musicId = item.id,
        musicPlatform = item.platform,
        lines = listOf(
            ParsedLyricLine(0, 1_000L, "A"),
            ParsedLyricLine(1, 3_000L, "B"),
        ),
        source = LyricSourceInfo.Plugin("demo"),
    )
    whenever(playerLyricLoader.observeLyrics(item)).thenReturn(
        flowOf(LyricLoadState.Ready(item, document, userOffsetMs = 0L)),
    )
    playerStateFlow.value = PlayerState.EMPTY.copy(currentItem = item, position = 3_500L)

    val viewModel = createViewModel()
    val job = backgroundScope.launch { viewModel.lyricsUiState.collect {} }
    advanceUntilIdle()

    assertEquals(1, viewModel.lyricsUiState.value.currentLineIndex)
    job.cancel()
}
```

用 mock 更新测试 fixture：

```kotlin
private val playerLyricLoader: PlayerLyricLoader = mock()
private val appPreferences: AppPreferences = mock()
```

在 `setup()` 中新增：

```kotlin
whenever(playerLyricLoader.observeLyrics(null)).thenReturn(flowOf(LyricLoadState.NoTrack))
whenever(appPreferences.lyricShowTranslation).thenReturn(flowOf(false))
whenever(appPreferences.lyricDetailFontSize).thenReturn(flowOf(1))
```

更新 `createViewModel()` 的构造调用，把新增依赖传入。

- [ ] **步骤 2：运行 ViewModel 测试并确认失败**

运行：

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerViewModelTest*'
```

预期：编译失败，直到 ViewModel 构造函数和状态更新完成。

- [ ] **步骤 3：新增 UI 状态模型**

新建 `PlayerLyricsUiState.kt`：

```kotlin
package com.hank.musicfree.feature.playerui.lyrics

import com.hank.musicfree.core.model.LyricDocument

data class PlayerLyricsUiState(
    val loadState: LyricLoadState = LyricLoadState.NoTrack,
    val document: LyricDocument? = null,
    val currentLineIndex: Int? = null,
    val showTranslation: Boolean = false,
    val fontSizeLevel: Int = 1,
    val userOffsetMs: Long = 0L,
) {
    val hasLyrics: Boolean get() = document?.lines?.isNotEmpty() == true
    val hasTranslation: Boolean get() = document?.hasTranslation == true
}
```

- [ ] **步骤 4：更新 `PlayerViewModel`**

注入新增依赖：

```kotlin
private val playerLyricLoader: PlayerLyricLoader,
private val appPreferences: AppPreferences,
```

新增状态：

```kotlin
private val lyricLoadState: StateFlow<LyricLoadState> = playerState
    .map { it.currentItem }
    .flatMapLatest { item -> playerLyricLoader.observeLyrics(item) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LyricLoadState.NoTrack)

val lyricsUiState: StateFlow<PlayerLyricsUiState> = combine(
    playerState,
    lyricLoadState,
    appPreferences.lyricShowTranslation,
    appPreferences.lyricDetailFontSize,
) { playback, lyricState, showTranslation, fontSize ->
    val ready = lyricState as? LyricLoadState.Ready
    PlayerLyricsUiState(
        loadState = lyricState,
        document = ready?.document,
        currentLineIndex = ready?.document?.let {
            LyricTiming.currentLineIndex(
                lines = it.lines,
                playbackPositionMs = playback.position,
                userOffsetMs = ready.userOffsetMs,
                metaOffsetMs = it.metaOffsetMs,
            )
        },
        showTranslation = showTranslation,
        fontSizeLevel = fontSize,
        userOffsetMs = ready?.userOffsetMs ?: 0L,
    )
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerLyricsUiState())
```

新增 action：

```kotlin
fun setLyricShowTranslation(enabled: Boolean) {
    viewModelScope.launch { appPreferences.setLyricShowTranslation(enabled) }
}

fun setLyricDetailFontSize(level: Int) {
    viewModelScope.launch { appPreferences.setLyricDetailFontSize(level) }
}

fun seekToLyricLine(lineTimeMs: Long) {
    val ready = lyricsUiState.value.loadState as? LyricLoadState.Ready ?: return
    val duration = playerState.value.duration
    val seekMs = LyricTiming.seekPositionForLine(
        lineTimeMs = lineTimeMs,
        userOffsetMs = ready.userOffsetMs,
        metaOffsetMs = ready.document.metaOffsetMs,
        durationMs = duration,
    )
    playerController.seekTo(seekMs)
    playerController.play()
}
```

- [ ] **步骤 5：运行测试**

运行：

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests '*PlayerViewModelTest*'
```

预期： `BUILD SUCCESSFUL`.

- [ ] **步骤 6：提交**

```bash
git add feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricsUiState.kt \
  feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerViewModel.kt \
  feature/player-ui/src/test/java/com/hank/musicfree/feature/playerui/PlayerViewModelTest.kt
git commit -m "feat(player-ui): expose lyrics state"
```

## 任务 9：Compose 歌词页

**文件：**

- 新建：`feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricsContent.kt`
- 新建：`feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricsOperations.kt`
- 修改：`feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt`

- [ ] **步骤 1：创建 `PlayerLyricsContent`**

实现：

```kotlin
@Composable
fun PlayerLyricsContent(
    state: PlayerLyricsUiState,
    durationMs: Long,
    onBackToCover: () -> Unit,
    onSeekToLine: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentIndex = state.currentLineIndex
    LaunchedEffect(currentIndex, state.loadState) {
        if (currentIndex != null) {
            listState.animateScrollToItem(currentIndex)
        }
    }
    Box(modifier = modifier.pointerInput(Unit) { detectTapGestures(onTap = { onBackToCover() }) }) {
        when (val load = state.loadState) {
            LyricLoadState.NoTrack -> CenterText("暂无播放歌曲")
            is LyricLoadState.Loading -> CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
            is LyricLoadState.NoLyric -> CenterText("暂无歌词\n搜索歌词")
            is LyricLoadState.Error -> CenterText("${load.message}\n重试")
            is LyricLoadState.Ready -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item { Spacer(Modifier.height(rpx(220))) }
                items(load.document.lines, key = { "${it.index}-${it.timeMs}" }) { line ->
                    val highlighted = line.index == state.currentLineIndex
                    Text(
                        text = buildString {
                            append(line.text.ifBlank { "..." })
                            if (state.showTranslation && !line.translation.isNullOrBlank()) {
                                append('\n')
                                append(line.translation)
                            }
                        },
                        color = if (highlighted) MusicFreeTheme.colors.primary else Color.White.copy(alpha = 0.6f),
                        fontSize = lyricFontSize(state.fontSizeLevel),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = rpx(64), vertical = rpx(24)),
                    )
                }
                item { Spacer(Modifier.height(rpx(220))) }
            }
        }
    }
}
```

在同一文件中新增 helper：

```kotlin
@Composable
private fun CenterText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun lyricFontSize(level: Int) = when (level.coerceIn(0, 3)) {
    0 -> rpx(24)
    1 -> rpx(30)
    2 -> rpx(36)
    else -> rpx(42)
}
```

基础版本通过后，在同一文件中补充拖动浮层：

- 使用 `listState.layoutInfo.visibleItemsInfo` 计算可见区域中心行。
- 当 `listState.isScrollInProgress` 为 true 时保存 `draggingIndex`。
- 在中线位置显示时间 pill、水平线和播放图标。
- 点击播放图标时调用 `onSeekToLine(line.timeMs)`。

- [ ] **步骤 2：创建 `PlayerLyricsOperations`**

实现操作行：

```kotlin
@Composable
fun PlayerLyricsOperations(
    state: PlayerLyricsUiState,
    onFontSize: () -> Unit,
    onOffset: () -> Unit,
    onSearch: () -> Unit,
    onToggleTranslation: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rpx(80))
            .padding(horizontal = rpx(48)),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onFontSize) { Text("A", color = Color.White) }
        IconButton(onClick = onOffset) { Text("↔", color = Color.White) }
        IconButton(onClick = onSearch) {
            Icon(painterResource(R.drawable.ic_magnifying_glass), contentDescription = "搜索歌词", tint = Color.White)
        }
        IconButton(onClick = onToggleTranslation, enabled = state.hasTranslation) {
            Text("译", color = if (state.showTranslation && state.hasTranslation) MusicFreeTheme.colors.primary else Color.White.copy(alpha = if (state.hasTranslation) 1f else 0.2f))
        }
        IconButton(onClick = onMore) {
            Icon(painterResource(R.drawable.ic_ellipsis_vertical), contentDescription = "歌词更多", tint = Color.White)
        }
    }
}
```

- [ ] **步骤 3：在 `PlayerScreen` 中接入页面切换**

新增本地页面状态：

```kotlin
enum class PlayerContentPage { Cover, Lyrics }
```

在 `PlayerScreen` 内新增：

```kotlin
var contentPage by remember { mutableStateOf(PlayerContentPage.Cover) }
val lyricsUiState by viewModel.lyricsUiState.collectAsStateWithLifecycle()
```

将中部封面区域替换为：

```kotlin
when (contentPage) {
    PlayerContentPage.Cover -> PlayerCoverArt(
        artworkUrl = artworkUrl,
        modifier = Modifier
            .size(rpx(500))
            .clickable { contentPage = PlayerContentPage.Lyrics },
    )
    PlayerContentPage.Lyrics -> PlayerLyricsContent(
        state = lyricsUiState,
        durationMs = state.duration,
        onBackToCover = { contentPage = PlayerContentPage.Cover },
        onSeekToLine = viewModel::seekToLyricLine,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
    )
}
```

更新 `PlayerOperationsBar` 中现有歌词图标，使其调用 `onToggleLyrics`。

- [ ] **步骤 4：编译 player UI**

运行：

```bash
./gradlew :feature:player-ui:compileDebugKotlin
```

预期： `BUILD SUCCESSFUL`.

- [ ] **步骤 5：提交**

```bash
git add feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricsContent.kt \
  feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricsOperations.kt \
  feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt
git commit -m "feat(player-ui): render playback lyrics"
```

## 任务 10：歌词搜索、关联、偏移和本地导入 UI

**文件：**

- 新建：`feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricSearchSheet.kt`
- 修改：`feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt`
- 修改：`feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerViewModel.kt`

- [ ] **步骤 1：新增 ViewModel action**

Add:

```kotlin
private val _lyricSearchResults = MutableStateFlow<List<LyricSearchGroup>>(emptyList())
val lyricSearchResults: StateFlow<List<LyricSearchGroup>> = _lyricSearchResults.asStateFlow()

fun searchLyrics() {
    val item = playerState.value.currentItem ?: return
    viewModelScope.launch {
        _lyricSearchResults.value = playerLyricLoader.searchCandidates(item)
    }
}

fun associateLyric(target: MusicItem) {
    val item = playerState.value.currentItem ?: return
    viewModelScope.launch { playerLyricLoader.associateLyric(item, target) }
}

fun setLyricOffset(offsetMs: Long) {
    val item = playerState.value.currentItem ?: return
    viewModelScope.launch { playerLyricLoader.setLyricOffset(item, offsetMs) }
}

fun importLocalLyric(rawText: String, kind: LocalLyricKind) {
    val item = playerState.value.currentItem ?: return
    viewModelScope.launch { playerLyricLoader.importLocalLyric(item, rawText, kind) }
}

fun deleteLocalLyric() {
    val item = playerState.value.currentItem ?: return
    viewModelScope.launch { playerLyricLoader.deleteLocalLyric(item) }
}
```

- [ ] **步骤 2：新增搜索 sheet**

新建 `PlayerLyricSearchSheet.kt`：

```kotlin
@Composable
fun PlayerLyricSearchSheet(
    groups: List<LyricSearchGroup>,
    onDismiss: () -> Unit,
    onSelect: (MusicItem) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth()) {
            groups.forEach { group ->
                item {
                    Text(
                        text = group.plugin.platform,
                        color = MusicFreeTheme.colors.text,
                        fontSize = FontSizes.subTitle,
                        modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(12)),
                    )
                }
                if (group.errorMessage != null) {
                    item {
                        Text(group.errorMessage, color = MusicFreeTheme.colors.danger, modifier = Modifier.padding(horizontal = rpx(24)))
                    }
                }
                items(group.items, key = { "${it.platform}:${it.id}" }) { item ->
                    ListItem(
                        headlineContent = { Text(item.title) },
                        supportingContent = { Text("${item.artist} · ${item.platform}") },
                        modifier = Modifier.clickable { onSelect(item) },
                    )
                }
            }
        }
    }
}
```

- [ ] **步骤 3：新增本地导入 launcher**

在 `PlayerScreen` 中新增：

```kotlin
val context = LocalContext.current
var pendingImportKind by remember { mutableStateOf<LocalLyricKind?>(null) }
val openLyricDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    val kind = pendingImportKind ?: return@rememberLauncherForActivityResult
    pendingImportKind = null
    if (uri != null) {
        runCatching {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
                .take(512 * 1024)
        }.onSuccess { text ->
            viewModel.importLocalLyric(text, kind)
        }.onFailure {
            Toast.makeText(context, "读取歌词失败", Toast.LENGTH_SHORT).show()
        }
    }
}
```

用以下代码触发导入：

```kotlin
pendingImportKind = LocalLyricKind.Raw
openLyricDocument.launch(arrayOf("text/*", "application/octet-stream"))
```

- [ ] **步骤 4：新增偏移对话框**

使用简单的 `AlertDialog` 展示当前偏移和操作按钮：

```kotlin
AlertDialog(
    onDismissRequest = { showOffsetDialog = false },
    title = { Text("设置歌词进度") },
    text = { Text("当前：${lyricsUiState.userOffsetMs / 1000f}s") },
    confirmButton = {
        Row {
            TextButton(onClick = { viewModel.setLyricOffset(lyricsUiState.userOffsetMs + 500L) }) { Text("提前0.5s") }
            TextButton(onClick = { viewModel.setLyricOffset(lyricsUiState.userOffsetMs - 500L) }) { Text("延后0.5s") }
            TextButton(onClick = { viewModel.setLyricOffset(0L) }) { Text("重置") }
        }
    },
)
```

- [ ] **步骤 5：编译**

运行：

```bash
./gradlew :feature:player-ui:compileDebugKotlin
```

预期： `BUILD SUCCESSFUL`.

- [ ] **步骤 6：提交**

```bash
git add feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/lyrics/PlayerLyricSearchSheet.kt \
  feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerScreen.kt \
  feature/player-ui/src/main/java/com/hank/musicfree/feature/playerui/PlayerViewModel.kt
git commit -m "feat(player-ui): add lyric operations"
```

## 任务 11：完整验证与运行态验收

**文件：**

- 仅当前序任务暴露覆盖不足时修改测试。
- 除非验证暴露 bug，否则本任务不修改生产代码。

- [ ] **步骤 1：运行目标单元测试**

运行：

```bash
./gradlew :core:test :plugin:test :data:test :feature:player-ui:testDebugUnitTest
```

预期： `BUILD SUCCESSFUL`.

- [ ] **步骤 2：编译 Android 测试**

运行：

```bash
./gradlew :data:compileDebugAndroidTestKotlin
```

预期： `BUILD SUCCESSFUL`.

- [ ] **步骤 3：构建 app**

运行：

```bash
./gradlew :app:build
```

预期： `BUILD SUCCESSFUL`.

- [ ] **步骤 4：设备或模拟器运行态验收**

检查设备：

```bash
adb devices
```

若列出了设备或模拟器，安装并启动：

```bash
./gradlew :app:installDebug
adb shell am start -n com.hank.musicfree/.MainActivity
```

手动验收：

1. 安装或准备一个能返回带时间戳 `rawLrc` 的插件。
2. 搜索一首歌并开始播放。
3. 打开全屏播放页。
4. 点击封面：显示歌词页。
5. 等待歌词加载：播放过程中当前行高亮并滚动。
6. 拖动歌词：出现中线浮层。
7. 点击浮层播放按钮：播放进度跳转到选中歌词行。
8. 导入或加载翻译歌词后切换翻译显示。
9. 修改字体大小并确认歌词行文本尺寸变化。
10. 设置偏移并确认高亮行变化。
11. 搜索歌词，选择候选结果，并确认歌词重新加载。
12. 导入本地 `.lrc`，确认本地歌词覆盖远程歌词。
13. 返回封面页，确认播放/暂停、上一首/下一首、收藏、加入歌单仍可用。

- [ ] **步骤 5：采集运行态证据**

运行：

```bash
adb shell uiautomator dump /sdcard/window.xml
adb pull /sdcard/window.xml /tmp/player-lyrics-window.xml
```

预期：`/tmp/player-lyrics-window.xml` 包含播放页文本，以及 `搜索歌词`、`歌词更多` 或可见歌词文本等歌词操作标签。

- [ ] **步骤 6：最终状态**

运行：

```bash
git status --short
```

预期：工作区干净。

若验证过程需要修复，提交这些修复：

```bash
git add <changed-files>
git commit -m "fix(player-ui): stabilize lyrics verification"
```

## 计划自检清单

- 规范覆盖：
- 解析器/领域模型：任务 1。
  - 插件翻译解析：任务 2。
  - 偏好项：任务 3。
- Room 缓存和 repository：任务 4 和任务 5。
- 符合模块边界的插件感知 loader：任务 7。
  - ViewModel 状态：任务 8。
  - Compose 歌词页和 RN 风格切换：任务 9。
  - 搜索/关联/本地导入/偏移/翻译操作：任务 10。
  - 验证和运行态证据：任务 11。
- 模块边界：
  - `:data` 不依赖 `:plugin`。
  - `:feature:player-ui` 为 loader 行为依赖 `:plugin`。
- 非目标：
  - 不做悬浮窗/桌面歌词。
  - 不新增 Gradle module。
  - 不做横屏专项重设计。
- 交接要求：
  - 每个任务都列出准确文件。
  - 每个任务都有命令和预期结果。
  - 新 session 可从 `.worktrees/feat-player-lyrics` 直接执行本计划。

# 过期音源播放修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复导入/添加/收藏后的插件歌曲因持久化临时 URL 过期而不能播放的问题。

**Architecture:** 将“播放前刷新插件真实源”收敛到 `PlayerController` 最终播放边界；`PluginMediaSourceService` 不再因为 `MusicItem.url` 非空而跳过插件解析。解析失败时仍允许播放器回退原 URL，兼容无 `getMediaSource` 的直链插件歌曲；Room 保存 `raw`，播放器注册解析得到的 headers/userAgent。

**Tech Stack:** Kotlin、Media3、Hilt 注入的 `MediaSourceResolver`、Robolectric/JUnit 单元测试。

---

## File Structure

- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/media/PluginMediaSourceService.kt`
  - Responsibility: 对插件歌曲执行音源重定向、源插件回退和音质 fallback；本次允许已有 URL 的 item 继续刷新。
- Modify: `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/media/PluginMediaSourceServiceTest.kt`
  - Responsibility: 覆盖已有 URL 的歌曲仍调用插件解析。
- Modify: `player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt`
  - Responsibility: 最终播放前刷新非本地歌曲音源，注册 headers/userAgent，并在解析失败时决定是否回退旧 URL。
- Modify: `player/src/test/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerNotificationControlsTest.kt`
  - Responsibility: 覆盖队列/通知播放路径对已有 URL 的歌曲仍刷新。
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/MusicItemEntity.kt`
  - Responsibility: 为 Room 中的音乐条目保存插件扩展 raw 字段。
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/MusicItemMapper.kt`
  - Responsibility: 在模型和 entity 间转换 raw JSON。
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt`
  - Responsibility: 升级 Room schema version。
- Test: `data/src/test/java/com/zili/android/musicfreeandroid/data/mapper/MusicItemMapperTest.kt`
  - Responsibility: 验证 raw round-trip。

## Task 1: Plugin Media Source Service

**Files:**
- Modify: `plugin/src/main/java/com/zili/android/musicfreeandroid/plugin/media/PluginMediaSourceService.kt`
- Test: `plugin/src/test/java/com/zili/android/musicfreeandroid/plugin/media/PluginMediaSourceServiceTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test to `PluginMediaSourceServiceTest`:

```kotlin
@Test
fun `resolves plugin item even when it already has stale url`() = runTest {
    val source = plugin("source", supportsMedia = true, url = "https://source.example/fresh.mp3")
    val service = service(plugins = listOf(source), alternatives = emptyMap())

    val result = service.resolve(item("source").copy(url = "https://source.example/stale.mp3"))!!

    assertEquals("https://source.example/fresh.mp3", result.item.url)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :plugin:testDebugUnitTest --tests com.zili.android.musicfreeandroid.plugin.media.PluginMediaSourceServiceTest --no-daemon
```

Expected: FAIL because `service.resolve(...)` returns `null` when the input item already has `url`.

- [ ] **Step 3: Write minimal implementation**

In `PluginMediaSourceService.resolve`, delete this early return:

```kotlin
if (!item.url.isNullOrBlank()) {
    return null
}
```

Do not change redirect order, disabled-plugin checks, or quality fallback.

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :plugin:testDebugUnitTest --tests com.zili.android.musicfreeandroid.plugin.media.PluginMediaSourceServiceTest --no-daemon
```

Expected: PASS.

## Task 2: Player Controller Playback Boundary

**Files:**
- Modify: `player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt`
- Test: `player/src/test/java/com/zili/android/musicfreeandroid/player/controller/PlayerControllerNotificationControlsTest.kt`

- [ ] **Step 1: Write the failing tests**

Add tests to `PlayerControllerNotificationControlsTest` covering stale URL refresh, queued URL refresh, and header registration. Use `RecordingResolver` with optional `headers` and `userAgent`, and inject a `TrackHeaderRegistry` into `PlayerController`.

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./gradlew :player:testDebugUnitTest --tests com.zili.android.musicfreeandroid.player.controller.PlayerControllerNotificationControlsTest --no-daemon
```

Expected: FAIL because `PlayerController.resolvePlayableItem` returns nonblank-URL items before invoking the resolver, and because `trackHeaderRegistry` is not yet a constructor parameter.

- [ ] **Step 3: Write minimal implementation**

Add `TrackHeaderRegistry` to the constructor with a default value for tests:

```kotlin
private val trackHeaderRegistry: TrackHeaderRegistry = TrackHeaderRegistry()
```

Change `resolvePlayableItem(item)` so non-local items attempt resolver first, use resolved URL if present, register headers before playback, and only then fall back to original URL if resolver cannot provide one.

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
./gradlew :player:testDebugUnitTest --tests com.zili.android.musicfreeandroid.player.controller.PlayerControllerNotificationControlsTest --no-daemon
```

Expected: PASS.

## Task 3: Persist MusicItem raw

**Files:**
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/MusicItemEntity.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/MusicItemMapper.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt`
- Test: `data/src/test/java/com/zili/android/musicfreeandroid/data/mapper/MusicItemMapperTest.kt`

- [ ] **Step 1: Write the failing test**

Add a nested `raw` value to the existing `model to entity and back preserves all fields` test and keep `assertEquals(model, roundTripped)`.

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests com.zili.android.musicfreeandroid.data.mapper.MusicItemMapperTest --no-daemon
```

Expected: FAIL because `MusicItemEntity` does not persist `raw`.

- [ ] **Step 3: Write minimal implementation**

Update `MusicItemEntity`:

```kotlin
val rawJson: String?,
```

Update `MusicItemMapper`:

```kotlin
rawJson = converters.rawMapToJson(raw),
```

and:

```kotlin
raw = converters.jsonToRawMap(rawJson),
```

Update `AppDatabase`:

```kotlin
version = 8,
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests com.zili.android.musicfreeandroid.data.mapper.MusicItemMapperTest --no-daemon
```

Expected: PASS.

## Task 4: Final Verification

**Files:**
- No new files.

- [ ] **Step 1: Run plugin targeted tests**

```bash
./gradlew :plugin:testDebugUnitTest --tests com.zili.android.musicfreeandroid.plugin.media.PluginMediaSourceServiceTest --no-daemon
```

Expected: PASS.

- [ ] **Step 2: Run data mapper tests**

```bash
./gradlew :data:testDebugUnitTest --tests com.zili.android.musicfreeandroid.data.mapper.MusicItemMapperTest --no-daemon
```

Expected: PASS.

- [ ] **Step 3: Run player unit tests**

```bash
./gradlew :player:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 4: Run Debug build gate**

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: PASS.

# Playback Restore Queue Raw Cache Check Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复后台/冷恢复后播放队列曲目丢失插件 raw 字段导致切下一首解析失败的问题，并补齐播放恢复、切歌、解析和缓存正常链路观测。

**Architecture:** 数据层把 `play_queue` 从“播放展示摘要”提升为“可重放解析的曲目快照”，通过 Room `11 -> 12` migration 加入 `rawJson/localPath/addedAt`。播放器和启动恢复只记录字段摘要，不记录 raw 内容；插件缓存路径保持策略不变，只补命中/跳过/写入诊断字段和单测证明。

**Tech Stack:** Kotlin, Room, Coroutines, Media3 PlayerController, QuickJS plugin bridge, MfLog/Logan, JUnit/Robolectric/AndroidX Room migration test.

---

## File Structure

- Modify: `data/src/main/java/com/hank/musicfree/data/db/entity/PlayQueueEntity.kt`
  - 给 `play_queue` 增加 `rawJson`、`localPath`、`addedAt`。
- Modify: `data/src/main/java/com/hank/musicfree/data/mapper/PlayQueueMapper.kt`
  - `MusicItem <-> PlayQueueEntity` 往返保留 raw/localPath/addedAt。
- Modify: `data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt`
  - Room version 从 `11` 提升到 `12`。
- Create: `data/src/main/java/com/hank/musicfree/data/db/migration/Migration11To12.kt`
  - 新增 `MIGRATION_11_12`，对 `play_queue` 做三条 `ALTER TABLE`。
- Modify: `data/src/main/java/com/hank/musicfree/data/di/DataModule.kt`
  - 注册 `MIGRATION_11_12`。
- Create: `data/src/androidTest/java/com/hank/musicfree/data/db/AppDatabaseMigration11To12Test.kt`
  - 验证旧队列升级后新增列存在、默认值正确，并能打开当前 DB。
- Modify: `data/src/test/java/com/hank/musicfree/data/mapper/PlayQueueMapperTest.kt`
  - 测试 raw/localPath/addedAt/qualities 往返。
- Modify: `data/schemas/com.hank.musicfree.data.db.AppDatabase/12.json`
  - 由 Room 任务生成当前 schema。
- Modify: `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`
  - 增加 `MusicItem.diagnosticFields`，扩展 `playback_start`、`player_skip_next`、`playback_resolve_success`、`playback_resolve_failed`。
- Modify: `player/src/test/java/com/hank/musicfree/player/controller/PlayerControllerQueueStateTest.kt`
  - 验证恢复后切下一首目标 item 保留 raw，并记录 `player_skip_next` 诊断字段。
- Modify: `player/src/test/java/com/hank/musicfree/player/controller/PlayerControllerSpeedTest.kt`
  - 复用或迁移 `RecordingLogger`，避免重复 logger fixture 膨胀。
- Modify: `app/src/main/java/com/hank/musicfree/bootstrap/PlaybackStartupCoordinator.kt`
  - 恢复完成日志增加当前曲目诊断字段。
- Modify: `app/src/test/java/com/hank/musicfree/bootstrap/PlaybackStartupCoordinatorTest.kt`
  - 安装 recording logger，验证恢复完成日志包含 raw/qualities/url/localPath 摘要。
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/media/PluginMediaSourceService.kt`
  - 缓存路径日志增加 cacheControl、offline、requestedQuality、useCache、reason 等字段。
- Modify: `plugin/src/test/java/com/hank/musicfree/plugin/media/PluginMediaSourceServiceCacheTest.kt`
  - 强化正常缓存语义测试与日志断言。

## Task 1: Data Queue Persistence

**Files:**
- Modify: `data/src/main/java/com/hank/musicfree/data/db/entity/PlayQueueEntity.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/mapper/PlayQueueMapper.kt`
- Modify: `data/src/test/java/com/hank/musicfree/data/mapper/PlayQueueMapperTest.kt`

- [ ] **Step 1: Write the failing mapper test**

Add a test that currently fails because `PlayQueueEntity` has no raw/localPath/addedAt fields:

```kotlin
@Test
fun `MusicItem with raw payload survives PlayQueueEntity round trip`() {
    val item = MusicItem(
        id = "4930516",
        platform = "yuanliqq",
        title = "Queue Song",
        artist = "Artist",
        album = "Album",
        duration = 180_000L,
        url = null,
        artwork = "https://example.com/art.jpg",
        qualities = mapOf(PlayQuality.HIGH to QualityInfo("quality-id", 1234L)),
        raw = mapOf(
            "songmid" to "003abc",
            "pay" to mapOf("play" to 1),
            "ids" to listOf("4930516", "003abc"),
        ),
        addedAt = 1_778_000_000_000L,
        localPath = "/storage/emulated/0/Music/Queue Song.mp3",
    )

    val entity = item.toPlayQueueEntity(sortOrder = 7, converters = converters)
    val roundTripped = entity.toMusicItem(converters)

    assertEquals(item.raw, roundTripped.raw)
    assertEquals(item.localPath, roundTripped.localPath)
    assertEquals(item.addedAt, roundTripped.addedAt)
    assertEquals(item.qualities, roundTripped.qualities)
    assertEquals(7, entity.sortOrder)
}
```

- [ ] **Step 2: Run the narrow data mapper test and confirm failure**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests 'com.hank.musicfree.data.mapper.PlayQueueMapperTest' --no-daemon
```

Expected before implementation: compile failure or assertion failure around missing `PlayQueueEntity.rawJson/localPath/addedAt`.

- [ ] **Step 3: Implement entity and mapper fields**

Use this shape:

```kotlin
data class PlayQueueEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val musicId: String,
    val musicPlatform: String,
    val title: String,
    val artist: String,
    val album: String?,
    val duration: Long,
    val url: String?,
    val artwork: String?,
    val qualitiesJson: String?,
    val rawJson: String? = null,
    val localPath: String? = null,
    val addedAt: Long = 0L,
    val sortOrder: Int,
)
```

Mapper changes:

```kotlin
rawJson = converters.rawMapToJson(raw),
localPath = localPath,
addedAt = addedAt,
```

and:

```kotlin
raw = converters.jsonToRawMap(rawJson),
addedAt = addedAt,
localPath = localPath,
```

- [ ] **Step 4: Run mapper test and confirm pass**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests 'com.hank.musicfree.data.mapper.PlayQueueMapperTest' --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

## Task 2: Room Migration

**Files:**
- Modify: `data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt`
- Create: `data/src/main/java/com/hank/musicfree/data/db/migration/Migration11To12.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/di/DataModule.kt`
- Create: `data/src/androidTest/java/com/hank/musicfree/data/db/AppDatabaseMigration11To12Test.kt`
- Generate: `data/schemas/com.hank.musicfree.data.db.AppDatabase/12.json`

- [ ] **Step 1: Write migration source**

Create:

```kotlin
package com.hank.musicfree.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE play_queue ADD COLUMN rawJson TEXT")
        db.execSQL("ALTER TABLE play_queue ADD COLUMN localPath TEXT")
        db.execSQL("ALTER TABLE play_queue ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
    }
}
```

- [ ] **Step 2: Register migration and bump DB version**

Change:

```kotlin
version = 12
```

and:

```kotlin
.addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
```

- [ ] **Step 3: Write migration test**

Use `MigrationTestHelper` with a v11 `play_queue` row:

```kotlin
helper.createDatabase(TEST_DB, 11).use { db ->
    db.execSQL(
        """INSERT INTO play_queue(musicId, musicPlatform, title, artist, album, duration, url, artwork, qualitiesJson, sortOrder)
           VALUES('4930516', 'yuanliqq', 'Song', 'Artist', NULL, 180000, NULL, NULL, NULL, 0)""".trimIndent(),
    )
}

helper.runMigrationsAndValidate(TEST_DB, 12, true, MIGRATION_11_12).use { db ->
    db.query("SELECT rawJson, localPath, addedAt FROM play_queue WHERE musicId = '4930516'").use { c ->
        assertTrue(c.moveToFirst())
        assertTrue(c.isNull(0))
        assertTrue(c.isNull(1))
        assertEquals(0L, c.getLong(2))
    }
}
```

Then open current DB with all migrations:

```kotlin
Room.databaseBuilder(
    InstrumentationRegistry.getInstrumentation().targetContext,
    AppDatabase::class.java,
    TEST_DB,
).addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12).build().apply {
    openHelper.writableDatabase
    close()
}
```

- [ ] **Step 4: Generate schema and run migration validation**

Run:

```bash
./gradlew :data:compileDebugKotlin --no-daemon
```

Then, if a device/emulator is available:

```bash
./gradlew :data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hank.musicfree.data.db.AppDatabaseMigration11To12Test --no-daemon
```

Expected: schema `12.json` exists and migration test passes. If no device is available, record that the instrumentation migration test was not executed.

## Task 3: Player Restore And Resolve Diagnostics

**Files:**
- Modify: `player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt`
- Modify: `player/src/test/java/com/hank/musicfree/player/controller/PlayerControllerQueueStateTest.kt`

- [ ] **Step 1: Add logger fixture in queue-state test**

Install a recording logger around tests that assert events:

```kotlin
val logger = RecordingLogger()
MfLog.install(logger)
...
MfLog.resetForTest()
```

The `RecordedLogEvent` shape should match existing `PlayerControllerSpeedTest`:

```kotlin
private data class RecordedLogEvent(
    val level: String,
    val category: LogCategory,
    val event: String,
    val fields: Map<String, Any?>,
    val throwable: Throwable? = null,
)
```

- [ ] **Step 2: Write failing diagnostic test for restored skip**

Add:

```kotlin
@Test
fun `skipToNext logs target diagnostics for restored queue item`() {
    val logger = RecordingLogger()
    MfLog.install(logger)
    val controller = PlayerController(context, listenTracker = mock<ListenTracker>())
    try {
        val next = item("2").copy(
            url = null,
            raw = mapOf("songmid" to "003abc", "pay" to mapOf("play" to 1)),
            localPath = null,
        )
        controller.restoreQueue(listOf(item("1"), next), startIndex = 0, playWhenRestored = false)

        controller.skipToNext()

        val event = logger.events.single { it.event == "player_skip_next" }
        assertEquals(LogCategory.PLAYER, event.category)
        assertEquals(0, event.fields["fromIndex"])
        assertEquals(1, event.fields["toIndex"])
        assertEquals("2", event.fields["toItemId"])
        assertEquals(2, event.fields["toRawKeyCount"])
        assertEquals(false, event.fields["toHasUrl"])
    } finally {
        controller.release()
    }
}
```

- [ ] **Step 3: Implement diagnostic helper and event fields**

In `PlayerController`, add:

```kotlin
private fun MusicItem.diagnosticFields(prefix: String = ""): Map<String, Any?> = mapOf(
    "${prefix}RawKeyCount" to raw.size,
    "${prefix}HasQualities" to !qualities.isNullOrEmpty(),
    "${prefix}HasUrl" to !url.isNullOrBlank(),
    "${prefix}HasLocalPath" to !localPath.isNullOrBlank(),
)
```

Use it in:

```kotlin
"player_skip_next" fields = mapOf(
    "fromIndex" to previousIndex,
    "toIndex" to playQueue.currentIndex,
    "fromItemId" to previousItem?.id,
    "toItemId" to next.id,
    "platform" to next.platform,
) + next.diagnosticFields(prefix = "to")
```

and add unprefixed diagnostics to `playback_start`, `playback_resolve_success`, and both `playback_resolve_failed` branches.

- [ ] **Step 4: Run player diagnostic tests**

Run:

```bash
./gradlew :player:testDebugUnitTest --tests 'com.hank.musicfree.player.controller.PlayerControllerQueueStateTest' --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

## Task 4: Startup Restore Diagnostics

**Files:**
- Modify: `app/src/main/java/com/hank/musicfree/bootstrap/PlaybackStartupCoordinator.kt`
- Modify: `app/src/test/java/com/hank/musicfree/bootstrap/PlaybackStartupCoordinatorTest.kt`

- [ ] **Step 1: Write failing startup log test**

Install a recording logger and wait for `restoreQueue`:

```kotlin
val event = logger.events.single { it.event == "playback_startup_restore_completed" }
assertEquals("test", event.fields["currentPlatform"])
assertEquals("1", event.fields["currentItemId"])
assertEquals(1, event.fields["rawKeyCount"])
assertEquals(false, event.fields["hasQualities"])
assertEquals(true, event.fields["hasUrl"])
assertEquals(true, event.fields["hasLocalPath"])
```

- [ ] **Step 2: Implement startup fields**

After `startIndex` is computed:

```kotlin
val current = queue.getOrNull(startIndex)
...
fields = mapOf(
    "queueSize" to queue.size,
    "startIndex" to startIndex,
    "currentPlatform" to current?.platform,
    "currentItemId" to current?.id,
    "rawKeyCount" to (current?.raw?.size ?: 0),
    "hasQualities" to !current?.qualities.isNullOrEmpty(),
    "hasUrl" to !current?.url.isNullOrBlank(),
    "hasLocalPath" to !current?.localPath.isNullOrBlank(),
)
```

- [ ] **Step 3: Run app startup test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.hank.musicfree.bootstrap.PlaybackStartupCoordinatorTest' --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

## Task 5: Plugin Cache Observability

**Files:**
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/media/PluginMediaSourceService.kt`
- Modify: `plugin/src/test/java/com/hank/musicfree/plugin/media/PluginMediaSourceServiceCacheTest.kt`

- [ ] **Step 1: Write cache log assertions**

For existing cache tests, assert:

```kotlin
val hit = logger.events.single { it.event == "plugin_get_media_source_cache_hit" }
assertEquals("cache", hit.fields["cacheControl"])
assertEquals(false, hit.fields["offline"])
assertEquals("standard", hit.fields["quality"])
```

For default online `no-cache`:

```kotlin
verify(cache, never()).get(any(), any())
val skipped = logger.events.single { it.event == "plugin_get_media_source_cache_read_skipped" }
assertEquals("no-cache", skipped.fields["cacheControl"])
assertEquals("policy_no_cache_online", skipped.fields["reason"])
```

- [ ] **Step 2: Implement cache fields without changing policy**

Add fields to cache hit/write:

```kotlin
"cacheControl" to cacheControl.wireName,
"offline" to isOffline,
"useCache" to useCache,
```

Add a detail event when cache read is skipped because `useCache=false`, `no-store`, or online `no-cache`:

```kotlin
MfLog.detail(
    category = LogCategory.PLUGIN,
    event = "plugin_get_media_source_cache_read_skipped",
    fields = mapOf(
        "platform" to item.platform,
        "musicItemId" to item.id,
        "cacheControl" to cacheControl.wireName,
        "offline" to isOffline,
        "useCache" to useCache,
        "reason" to reason,
    ),
)
```

- [ ] **Step 3: Run plugin cache tests**

Run:

```bash
./gradlew :plugin:testDebugUnitTest --tests 'com.hank.musicfree.plugin.media.PluginMediaSourceServiceCacheTest' --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

## Task 6: Focused And Final Verification

**Files:**
- No direct file edits.

- [ ] **Step 1: Run focused tests**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests '*PlayQueue*' --no-daemon
./gradlew :player:testDebugUnitTest --tests '*PlayerController*' --no-daemon
./gradlew :plugin:testDebugUnitTest --tests '*PluginMediaSourceService*' --no-daemon
./gradlew :app:testDebugUnitTest --tests '*PlaybackStartupCoordinatorTest' --no-daemon
```

Expected: all commands pass.

- [ ] **Step 2: Run guarded checks**

Run:

```bash
bash scripts/dev-harness/check.sh
git diff --check
```

Expected: no harness violations and no whitespace errors.

- [ ] **Step 3: Run debug build**

Run:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Review final diff**

Run:

```bash
git status --short
git diff --stat
git diff -- data/src/main/java/com/hank/musicfree/data/db/entity/PlayQueueEntity.kt
git diff -- player/src/main/java/com/hank/musicfree/player/controller/PlayerController.kt
git diff -- plugin/src/main/java/com/hank/musicfree/plugin/media/PluginMediaSourceService.kt
```

Expected: changes stay within the approved data/player/plugin/app/logging-observation scope.

# RuntimeStore Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a RuntimeStore / SnapshotStore architecture that keeps high-value app state across Activity recreation and restores recoverable snapshots after process death without blocking cold start.

**Architecture:** Runtime contracts live in `:core`, durable generic snapshots live in `:data` backed by Room, and process orchestration lives in `:app`. Feature modules own domain-specific stores and use the shared SnapshotStore protocol to persist only serializable descriptors, while runtime objects such as QuickJS, Media3, coroutines, Android views, repositories, and DAO instances are rebuilt from snapshots instead of being persisted.

**Tech Stack:** Kotlin, Hilt, Coroutines, Flow/StateFlow, Room, kotlinx.serialization, Jetpack Compose, Navigation Compose, MfLog/Logan.

---

## Guardrails Already In Scope

Read these before touching code in this plan:

- `docs/DOCS_STATUS.md`
- `AGENTS.md`
- `docs/dev-harness/INDEX.md`
- `docs/dev-harness/runtime/rules.md`
- `docs/dev-harness/test/rules.md`
- `docs/dev-harness/ui/rules.md` for Activity recreate and Compose state changes
- `docs/dev-harness/plugin/rules.md` for plugin runtime changes
- `docs/dev-harness/player/rules.md` for playback runtime changes
- `docs/superpowers/specs/2026-05-19-runtime-store-architecture-design.md`
- `docs/superpowers/specs/2026-05-05-logging-system-design.md`
- `docs/superpowers/specs/2026-05-10-logging-diagnostics-coverage-design.md`

Hard constraints:

- Cold start restore must not block first screen.
- `Application.onCreate()` must not synchronously read large snapshots, evaluate QuickJS plugins, or wait on search/detail payload restore.
- Use application scope + `SupervisorJob` / `supervisorScope` for independent restore tasks.
- Each restore/persist operation logs start and exactly one terminal result: `success`, `failure`, `skipped`, `stale`, or `cancelled`.
- Logs must include `store`, `operation`, `key`, `result`, and `durationMs` for non-trivial IO. Failures include stable `reason` and `MfLog.error`.
- No business code may add direct `android.util.Log.*` or direct Logan calls.
- Room schema changes must bump the database version and add migration tests.

## File Structure

### Logging

- Modify: `logging/src/main/java/com/hank/musicfree/logging/LogCategory.kt`
  - Add `RUNTIME("runtime")`.
- Create: `logging/src/main/java/com/hank/musicfree/logging/RuntimeLogFields.kt`
  - Centralize runtime log field names and operation/result helpers.
- Test: `logging/src/test/java/com/hank/musicfree/logging/RuntimeLogFieldsTest.kt`
  - Verify category wire name and generated field maps.

### Core Runtime Contracts

- Create: `core/src/main/java/com/hank/musicfree/core/runtime/RuntimeStore.kt`
  - Base store lifecycle and state exposure contracts.
- Create: `core/src/main/java/com/hank/musicfree/core/runtime/RuntimeSnapshot.kt`
  - Generic snapshot metadata model.
- Create: `core/src/main/java/com/hank/musicfree/core/runtime/SnapshotStore.kt`
  - Durable snapshot read/write/prune protocol.
- Create: `core/src/main/java/com/hank/musicfree/core/runtime/RuntimeRestoreResult.kt`
  - Restore result sealed types.
- Create: `core/src/main/java/com/hank/musicfree/core/runtime/RuntimeStoreKey.kt`
  - Stable key builder for `plugin`, `playback`, `download`, `search`, `detail`, `route_seed`, and `ui`.
- Test: `core/src/test/java/com/hank/musicfree/core/runtime/RuntimeStoreKeyTest.kt`
- Test: `core/src/test/java/com/hank/musicfree/core/runtime/RuntimeSnapshotTest.kt`

### Data SnapshotStore

- Create: `data/src/main/java/com/hank/musicfree/data/db/entity/RuntimeSnapshotEntity.kt`
- Create: `data/src/main/java/com/hank/musicfree/data/db/dao/RuntimeSnapshotDao.kt`
- Create: `data/src/main/java/com/hank/musicfree/data/db/migration/Migration12To13.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt`
  - Add entity, DAO, and bump version from 12 to 13.
- Modify: `data/src/main/java/com/hank/musicfree/data/di/DataModule.kt`
  - Add `MIGRATION_12_13`, provide `RuntimeSnapshotDao`, bind SnapshotStore.
- Create: `data/src/main/java/com/hank/musicfree/data/runtime/RoomRuntimeSnapshotStore.kt`
- Test: `data/src/test/java/com/hank/musicfree/data/runtime/RoomRuntimeSnapshotStoreTest.kt`
- Test: `data/src/androidTest/java/com/hank/musicfree/data/db/AppDatabaseMigration12To13Test.kt`

### App Runtime Orchestration

- Create: `app/src/main/java/com/hank/musicfree/runtime/RuntimeStoreRegistry.kt`
- Create: `app/src/main/java/com/hank/musicfree/runtime/RuntimeRestoreCoordinator.kt`
- Modify: `app/src/main/java/com/hank/musicfree/MusicFreeApplication.kt`
  - Inject the existing Hilt `@ApplicationScope` instead of keeping a separate local scope.
  - Start `RuntimeRestoreCoordinator` after logging initialization and before feature bootstrappers that depend on restored indices.
- Modify: `app/src/main/java/com/hank/musicfree/di/CoroutineModule.kt`
  - Add explicit dispatcher qualifiers only if tests need them; keep current default application scope otherwise.
- Test: `app/src/test/java/com/hank/musicfree/runtime/RuntimeRestoreCoordinatorTest.kt`

### Store Implementations

- Plugin:
  - Create: `plugin/src/main/java/com/hank/musicfree/plugin/runtime/PluginRuntimeStore.kt`
  - Modify: `plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt`
  - Test: `plugin/src/test/java/com/hank/musicfree/plugin/runtime/PluginRuntimeStoreTest.kt`
- Playback:
  - Create: `player/src/main/java/com/hank/musicfree/player/runtime/PlaybackRuntimeStore.kt`
  - Modify: `app/src/main/java/com/hank/musicfree/bootstrap/PlaybackStartupCoordinator.kt`
  - Test: `player/src/test/java/com/hank/musicfree/player/runtime/PlaybackRuntimeStoreTest.kt`
  - Test: `app/src/test/java/com/hank/musicfree/bootstrap/PlaybackStartupCoordinatorTest.kt`
- Download:
  - Create: `downloader/src/main/java/com/hank/musicfree/downloader/runtime/DownloadRuntimeStore.kt`
  - Modify: `downloader/src/main/java/com/hank/musicfree/downloader/engine/DownloadEngine.kt`
  - Test: `downloader/src/test/java/com/hank/musicfree/downloader/runtime/DownloadRuntimeStoreTest.kt`
- Route seed:
  - Create: `feature/home/src/main/java/com/hank/musicfree/home/runtime/RouteSeedRuntimeStore.kt`
  - Modify: `feature/home/src/main/java/com/hank/musicfree/home/pluginsheet/navigation/PluginSheetSeedStore.kt`
  - Modify: `feature/home/src/main/java/com/hank/musicfree/home/musicdetail/navigation/MusicDetailSeedStore.kt`
  - Modify: `feature/home/src/main/java/com/hank/musicfree/home/albumdetail/navigation/AlbumDetailSeedStore.kt`
  - Modify: `feature/home/src/main/java/com/hank/musicfree/home/artistdetail/navigation/ArtistDetailSeedStore.kt`
  - Test: `feature/home/src/test/java/com/hank/musicfree/home/runtime/RouteSeedRuntimeStoreTest.kt`
- Search:
  - Create: `feature/search/src/main/java/com/hank/musicfree/search/runtime/SearchSessionStore.kt`
  - Modify: `feature/search/src/main/java/com/hank/musicfree/search/SearchViewModel.kt`
  - Test: `feature/search/src/test/java/com/hank/musicfree/search/runtime/SearchSessionStoreTest.kt`
  - Test: `feature/search/src/test/java/com/hank/musicfree/search/SearchViewModelRuntimeStoreTest.kt`
- Detail:
  - Create: `feature/home/src/main/java/com/hank/musicfree/home/runtime/DetailSessionStore.kt`
  - Modify detail ViewModels:
    - `feature/home/src/main/java/com/hank/musicfree/home/pluginsheet/PluginSheetDetailViewModel.kt`
    - `feature/home/src/main/java/com/hank/musicfree/home/toplist/TopListDetailViewModel.kt`
    - `feature/home/src/main/java/com/hank/musicfree/home/albumdetail/AlbumDetailViewModel.kt`
    - `feature/home/src/main/java/com/hank/musicfree/home/artistdetail/ArtistDetailViewModel.kt`
  - Test: `feature/home/src/test/java/com/hank/musicfree/home/runtime/DetailSessionStoreTest.kt`

### Activity Recreate / Runtime Acceptance

- Test: `app/src/androidTest/java/com/hank/musicfree/runtime/RuntimeStateRecreateTest.kt`
  - Verify non-empty search/detail/route seed UI state survives `ActivityScenario.recreate()` when the store is populated.
- Modify docs only after implementation behavior is verified:
  - `docs/dev-harness/runtime/rules.md` if exact event names or test commands change.
  - `docs/DOCS_STATUS.md` if new runtime docs are added.

---

## Task 1: Add Runtime Logging Primitives

**Files:**
- Modify: `logging/src/main/java/com/hank/musicfree/logging/LogCategory.kt`
- Create: `logging/src/main/java/com/hank/musicfree/logging/RuntimeLogFields.kt`
- Test: `logging/src/test/java/com/hank/musicfree/logging/RuntimeLogFieldsTest.kt`

- [ ] **Step 1: Write failing tests for runtime category and fields**

Create `logging/src/test/java/com/hank/musicfree/logging/RuntimeLogFieldsTest.kt`:

```kotlin
package com.hank.musicfree.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLogFieldsTest {
    @Test
    fun runtimeCategoryUsesStableWireName() {
        assertEquals("runtime", LogCategory.RUNTIME.wireName)
    }

    @Test
    fun restoreTerminalFieldsContainStoreKeyResultAndDuration() {
        val fields = RuntimeLogFields.restoreTerminal(
            store = "search_session",
            key = "search:music:hash",
            result = LogFields.Result.SUCCESS,
            durationMs = 42,
            reason = null,
        )

        assertEquals("runtime_restore", fields["operation"])
        assertEquals("search_session", fields["store"])
        assertEquals("search:music:hash", fields["key"])
        assertEquals("success", fields["result"])
        assertEquals(42L, fields["durationMs"])
        assertTrue("reason should be absent on success", !fields.containsKey("reason"))
    }

    @Test
    fun persistFailureFieldsCarryReason() {
        val fields = RuntimeLogFields.persistTerminal(
            store = "detail_session",
            key = "detail:sheet:demo:1",
            result = LogFields.Result.FAILURE,
            durationMs = 7,
            reason = "json_decode_failed",
        )

        assertEquals("runtime_snapshot_persist", fields["operation"])
        assertEquals("detail_session", fields["store"])
        assertEquals("detail:sheet:demo:1", fields["key"])
        assertEquals("failure", fields["result"])
        assertEquals(7L, fields["durationMs"])
        assertEquals("json_decode_failed", fields["reason"])
    }
}
```

- [ ] **Step 2: Run test and confirm it fails**

Run:

```bash
./gradlew :logging:testDebugUnitTest --tests com.hank.musicfree.logging.RuntimeLogFieldsTest --no-daemon
```

Expected: compilation fails because `LogCategory.RUNTIME` and `RuntimeLogFields` do not exist.

- [ ] **Step 3: Add runtime category**

Modify `logging/src/main/java/com/hank/musicfree/logging/LogCategory.kt`:

```kotlin
enum class LogCategory(val wireName: String) {
    APP("app"),
    PLUGIN("plugin"),
    SEARCH("search"),
    PLAYER("player"),
    PLAYLIST_IMPORT("playlist_import"),
    FEEDBACK("feedback"),
    DATA("data"),
    FILE_IO("file_io"),
    DOWNLOAD("download"),
    SETTINGS("settings"),
    HOME("home"),
    LYRICS("lyrics"),
    UPDATE("update"),
    RUNTIME("runtime"),
}
```

- [ ] **Step 4: Add runtime log field helper**

Create `logging/src/main/java/com/hank/musicfree/logging/RuntimeLogFields.kt`:

```kotlin
package com.hank.musicfree.logging

object RuntimeLogFields {
    const val OP_RESTORE = "runtime_restore"
    const val OP_PERSIST = "runtime_snapshot_persist"

    fun restoreStart(store: String, key: String): Map<String, Any?> = base(
        operation = OP_RESTORE,
        store = store,
        key = key,
        result = null,
        durationMs = null,
        reason = null,
    )

    fun restoreTerminal(
        store: String,
        key: String,
        result: String,
        durationMs: Long,
        reason: String?,
    ): Map<String, Any?> = base(
        operation = OP_RESTORE,
        store = store,
        key = key,
        result = result,
        durationMs = durationMs,
        reason = reason,
    )

    fun persistStart(store: String, key: String): Map<String, Any?> = base(
        operation = OP_PERSIST,
        store = store,
        key = key,
        result = null,
        durationMs = null,
        reason = null,
    )

    fun persistTerminal(
        store: String,
        key: String,
        result: String,
        durationMs: Long,
        reason: String?,
    ): Map<String, Any?> = base(
        operation = OP_PERSIST,
        store = store,
        key = key,
        result = result,
        durationMs = durationMs,
        reason = reason,
    )

    private fun base(
        operation: String,
        store: String,
        key: String,
        result: String?,
        durationMs: Long?,
        reason: String?,
    ): Map<String, Any?> = buildMap {
        put(LogFields.operation(operation).first, operation)
        put("store", store)
        put("key", key)
        if (result != null) put(LogFields.result(result).first, result)
        if (durationMs != null) put("durationMs", durationMs)
        if (reason != null) put(LogFields.reason(reason).first, reason)
    }
}
```

- [ ] **Step 5: Run logging tests**

Run:

```bash
./gradlew :logging:testDebugUnitTest --no-daemon
```

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add logging/src/main/java/com/hank/musicfree/logging/LogCategory.kt \
  logging/src/main/java/com/hank/musicfree/logging/RuntimeLogFields.kt \
  logging/src/test/java/com/hank/musicfree/logging/RuntimeLogFieldsTest.kt
git commit -m "feat(runtime): 添加运行时日志字段约束"
```

---

## Task 2: Add Core Runtime Contracts

**Files:**
- Create: `core/src/main/java/com/hank/musicfree/core/runtime/RuntimeStore.kt`
- Create: `core/src/main/java/com/hank/musicfree/core/runtime/RuntimeSnapshot.kt`
- Create: `core/src/main/java/com/hank/musicfree/core/runtime/SnapshotStore.kt`
- Create: `core/src/main/java/com/hank/musicfree/core/runtime/RuntimeRestoreResult.kt`
- Create: `core/src/main/java/com/hank/musicfree/core/runtime/RuntimeStoreKey.kt`
- Test: `core/src/test/java/com/hank/musicfree/core/runtime/RuntimeStoreKeyTest.kt`
- Test: `core/src/test/java/com/hank/musicfree/core/runtime/RuntimeSnapshotTest.kt`

- [ ] **Step 1: Write key tests**

Create `core/src/test/java/com/hank/musicfree/core/runtime/RuntimeStoreKeyTest.kt`:

```kotlin
package com.hank.musicfree.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeStoreKeyTest {
    @Test
    fun searchKeyIsStableAndNamespaced() {
        assertEquals(
            "search:music:demo-platform:hello-world",
            RuntimeStoreKey.search(
                mediaType = "music",
                platform = "demo-platform",
                queryHash = "hello-world",
            ).value,
        )
    }

    @Test
    fun detailKeyIsStableAndNamespaced() {
        assertEquals(
            "detail:plugin_sheet:demo:id-123",
            RuntimeStoreKey.detail(
                type = "plugin_sheet",
                platform = "demo",
                id = "id-123",
            ).value,
        )
    }

    @Test
    fun routeSeedKeyIsStableAndNamespaced() {
        assertEquals(
            "route_seed:album:demo:id-9",
            RuntimeStoreKey.routeSeed(
                target = "album",
                platform = "demo",
                id = "id-9",
            ).value,
        )
    }
}
```

- [ ] **Step 2: Write snapshot tests**

Create `core/src/test/java/com/hank/musicfree/core/runtime/RuntimeSnapshotTest.kt`:

```kotlin
package com.hank.musicfree.core.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeSnapshotTest {
    @Test
    fun snapshotExpiresWhenUpdatedAtIsOlderThanTtl() {
        val snapshot = RuntimeSnapshot(
            namespace = "search_session",
            key = RuntimeStoreKey.search("music", "demo", "hash").value,
            snapshotVersion = 1,
            sourceSignature = "plugin:v1",
            createdAtEpochMs = 1_000,
            updatedAtEpochMs = 1_000,
            expiresAtEpochMs = 2_000,
            payloadJson = "{}",
        )

        assertFalse(snapshot.isExpired(nowEpochMs = 1_999))
        assertTrue(snapshot.isExpired(nowEpochMs = 2_000))
    }

    @Test
    fun snapshotWithoutExpiryDoesNotExpire() {
        val snapshot = RuntimeSnapshot(
            namespace = "playback",
            key = "playback:current",
            snapshotVersion = 1,
            sourceSignature = "app:1",
            createdAtEpochMs = 1_000,
            updatedAtEpochMs = 1_000,
            expiresAtEpochMs = null,
            payloadJson = "{}",
        )

        assertFalse(snapshot.isExpired(nowEpochMs = Long.MAX_VALUE))
    }
}
```

- [ ] **Step 3: Run tests and confirm they fail**

Run:

```bash
./gradlew :core:testDebugUnitTest --tests '*runtime*' --no-daemon
```

Expected: compilation fails because runtime contracts do not exist.

- [ ] **Step 4: Add runtime contracts**

Create `core/src/main/java/com/hank/musicfree/core/runtime/RuntimeStoreKey.kt`:

```kotlin
package com.hank.musicfree.core.runtime

@JvmInline
value class RuntimeStoreKey(val value: String) {
    companion object {
        fun singleton(namespace: String): RuntimeStoreKey = RuntimeStoreKey("$namespace:current")

        fun search(mediaType: String, platform: String, queryHash: String): RuntimeStoreKey =
            RuntimeStoreKey("search:${mediaType.clean()}:${platform.clean()}:${queryHash.clean()}")

        fun detail(type: String, platform: String, id: String): RuntimeStoreKey =
            RuntimeStoreKey("detail:${type.clean()}:${platform.clean()}:${id.clean()}")

        fun routeSeed(target: String, platform: String, id: String): RuntimeStoreKey =
            RuntimeStoreKey("route_seed:${target.clean()}:${platform.clean()}:${id.clean()}")

        fun plugin(platform: String): RuntimeStoreKey =
            RuntimeStoreKey("plugin:${platform.clean()}")

        fun download(taskId: String): RuntimeStoreKey =
            RuntimeStoreKey("download:${taskId.clean()}")
    }
}

private fun String.clean(): String =
    trim().lowercase().replace(Regex("[^a-z0-9._-]+"), "-").trim('-').ifEmpty { "unknown" }
```

Create `core/src/main/java/com/hank/musicfree/core/runtime/RuntimeSnapshot.kt`:

```kotlin
package com.hank.musicfree.core.runtime

data class RuntimeSnapshot(
    val namespace: String,
    val key: String,
    val snapshotVersion: Int,
    val sourceSignature: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val expiresAtEpochMs: Long?,
    val payloadJson: String,
) {
    fun isExpired(nowEpochMs: Long): Boolean =
        expiresAtEpochMs?.let { nowEpochMs >= it } == true
}
```

Create `core/src/main/java/com/hank/musicfree/core/runtime/RuntimeRestoreResult.kt`:

```kotlin
package com.hank.musicfree.core.runtime

sealed interface RuntimeRestoreResult {
    data object Restored : RuntimeRestoreResult
    data class Skipped(val reason: String) : RuntimeRestoreResult
    data class Stale(val reason: String) : RuntimeRestoreResult
    data class Failed(val reason: String, val error: Throwable? = null) : RuntimeRestoreResult
}
```

Create `core/src/main/java/com/hank/musicfree/core/runtime/SnapshotStore.kt`:

```kotlin
package com.hank.musicfree.core.runtime

interface SnapshotStore {
    suspend fun read(namespace: String, key: String): RuntimeSnapshot?
    suspend fun write(snapshot: RuntimeSnapshot)
    suspend fun delete(namespace: String, key: String)
    suspend fun deleteExpired(namespace: String, nowEpochMs: Long): Int
    suspend fun pruneNamespace(namespace: String, keepLatest: Int): Int
    suspend fun keys(namespace: String, limit: Int): List<String>
}
```

Create `core/src/main/java/com/hank/musicfree/core/runtime/RuntimeStore.kt`:

```kotlin
package com.hank.musicfree.core.runtime

import kotlinx.coroutines.flow.StateFlow

interface RuntimeStore<S> {
    val storeName: String
    val state: StateFlow<S>
    suspend fun restore(): RuntimeRestoreResult
    suspend fun persist()
    suspend fun prune(nowEpochMs: Long)
}
```

- [ ] **Step 5: Run core tests**

Run:

```bash
./gradlew :core:testDebugUnitTest --no-daemon
```

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/hank/musicfree/core/runtime \
  core/src/test/java/com/hank/musicfree/core/runtime
git commit -m "feat(runtime): 定义运行时状态基础契约"
```

---

## Task 3: Implement Room-backed SnapshotStore

**Files:**
- Create: `data/src/main/java/com/hank/musicfree/data/db/entity/RuntimeSnapshotEntity.kt`
- Create: `data/src/main/java/com/hank/musicfree/data/db/dao/RuntimeSnapshotDao.kt`
- Create: `data/src/main/java/com/hank/musicfree/data/db/migration/Migration12To13.kt`
- Create: `data/src/main/java/com/hank/musicfree/data/runtime/RoomRuntimeSnapshotStore.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/di/DataModule.kt`
- Test: `data/src/test/java/com/hank/musicfree/data/runtime/RoomRuntimeSnapshotStoreTest.kt`
- Test: `data/src/androidTest/java/com/hank/musicfree/data/db/AppDatabaseMigration12To13Test.kt`

- [ ] **Step 1: Write SnapshotStore JVM tests**

Create `data/src/test/java/com/hank/musicfree/data/runtime/RoomRuntimeSnapshotStoreTest.kt`:

```kotlin
package com.hank.musicfree.data.runtime

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hank.musicfree.core.runtime.RuntimeSnapshot
import com.hank.musicfree.data.db.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RoomRuntimeSnapshotStoreTest {
    private lateinit var db: AppDatabase
    private lateinit var store: RoomRuntimeSnapshotStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        store = RoomRuntimeSnapshotStore(db.runtimeSnapshotDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun writeThenReadReturnsSnapshot() = runTest {
        val snapshot = RuntimeSnapshot(
            namespace = "search_session",
            key = "search:music:demo:hash",
            snapshotVersion = 1,
            sourceSignature = "plugin:demo:1",
            createdAtEpochMs = 1,
            updatedAtEpochMs = 2,
            expiresAtEpochMs = 3,
            payloadJson = """{"query":"hello"}""",
        )

        store.write(snapshot)

        assertEquals(snapshot, store.read("search_session", "search:music:demo:hash"))
    }

    @Test
    fun deleteExpiredRemovesOnlyExpiredRows() = runTest {
        store.write(sample("a", expiresAt = 100))
        store.write(sample("b", expiresAt = 300))
        store.write(sample("c", expiresAt = null))

        val deleted = store.deleteExpired("search_session", nowEpochMs = 200)

        assertEquals(1, deleted)
        assertNull(store.read("search_session", "a"))
        assertEquals("b", store.read("search_session", "b")?.key)
        assertEquals("c", store.read("search_session", "c")?.key)
    }

    @Test
    fun pruneNamespaceKeepsLatestUpdatedRows() = runTest {
        store.write(sample("a", updatedAt = 1))
        store.write(sample("b", updatedAt = 2))
        store.write(sample("c", updatedAt = 3))

        val deleted = store.pruneNamespace("search_session", keepLatest = 2)

        assertEquals(1, deleted)
        assertNull(store.read("search_session", "a"))
        assertEquals(listOf("c", "b"), store.keys("search_session", limit = 10))
    }

    private fun sample(
        key: String,
        updatedAt: Long = 1,
        expiresAt: Long? = 1_000,
    ): RuntimeSnapshot = RuntimeSnapshot(
        namespace = "search_session",
        key = key,
        snapshotVersion = 1,
        sourceSignature = "source",
        createdAtEpochMs = 1,
        updatedAtEpochMs = updatedAt,
        expiresAtEpochMs = expiresAt,
        payloadJson = "{}",
    )
}
```

- [ ] **Step 2: Write migration instrumented test**

Create `data/src/androidTest/java/com/hank/musicfree/data/db/AppDatabaseMigration12To13Test.kt`:

```kotlin
package com.hank.musicfree.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hank.musicfree.data.db.migration.MIGRATION_12_13
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration12To13Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate12To13CreatesRuntimeSnapshotsTable() {
        helper.createDatabase(TEST_DB, 12).close()

        helper.runMigrationsAndValidate(TEST_DB, 13, true, MIGRATION_12_13).use { db ->
            db.query("SELECT namespace, `key`, payloadJson FROM runtime_snapshots").use { cursor ->
                cursor.count
            }
        }
    }

    private companion object {
        const val TEST_DB = "runtime-snapshot-migration"
    }
}
```

- [ ] **Step 3: Run tests and confirm they fail**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests com.hank.musicfree.data.runtime.RoomRuntimeSnapshotStoreTest --no-daemon
```

Expected: compilation fails because `RuntimeSnapshotDao`, `runtimeSnapshotDao()`, and `RoomRuntimeSnapshotStore` do not exist.

- [ ] **Step 4: Add Room entity and DAO**

Create `data/src/main/java/com/hank/musicfree/data/db/entity/RuntimeSnapshotEntity.kt`:

```kotlin
package com.hank.musicfree.data.db.entity

import androidx.room.Entity

@Entity(
    tableName = "runtime_snapshots",
    primaryKeys = ["namespace", "key"],
)
data class RuntimeSnapshotEntity(
    val namespace: String,
    val key: String,
    val snapshotVersion: Int,
    val sourceSignature: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val expiresAtEpochMs: Long?,
    val payloadJson: String,
)
```

Create `data/src/main/java/com/hank/musicfree/data/db/dao/RuntimeSnapshotDao.kt`:

```kotlin
package com.hank.musicfree.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hank.musicfree.data.db.entity.RuntimeSnapshotEntity

@Dao
interface RuntimeSnapshotDao {
    @Query("SELECT * FROM runtime_snapshots WHERE namespace = :namespace AND `key` = :key")
    suspend fun get(namespace: String, key: String): RuntimeSnapshotEntity?

    @Upsert
    suspend fun upsert(entity: RuntimeSnapshotEntity)

    @Query("DELETE FROM runtime_snapshots WHERE namespace = :namespace AND `key` = :key")
    suspend fun delete(namespace: String, key: String): Int

    @Query(
        """
        DELETE FROM runtime_snapshots
        WHERE namespace = :namespace
          AND expiresAtEpochMs IS NOT NULL
          AND expiresAtEpochMs <= :nowEpochMs
        """,
    )
    suspend fun deleteExpired(namespace: String, nowEpochMs: Long): Int

    @Query(
        """
        DELETE FROM runtime_snapshots
        WHERE namespace = :namespace
          AND `key` NOT IN (
              SELECT `key` FROM runtime_snapshots
              WHERE namespace = :namespace
              ORDER BY updatedAtEpochMs DESC
              LIMIT :keepLatest
          )
        """,
    )
    suspend fun pruneNamespace(namespace: String, keepLatest: Int): Int

    @Query(
        """
        SELECT `key` FROM runtime_snapshots
        WHERE namespace = :namespace
        ORDER BY updatedAtEpochMs DESC
        LIMIT :limit
        """,
    )
    suspend fun keys(namespace: String, limit: Int): List<String>
}
```

- [ ] **Step 5: Add migration**

Create `data/src/main/java/com/hank/musicfree/data/db/migration/Migration12To13.kt`:

```kotlin
package com.hank.musicfree.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `runtime_snapshots` (
                `namespace` TEXT NOT NULL,
                `key` TEXT NOT NULL,
                `snapshotVersion` INTEGER NOT NULL,
                `sourceSignature` TEXT NOT NULL,
                `createdAtEpochMs` INTEGER NOT NULL,
                `updatedAtEpochMs` INTEGER NOT NULL,
                `expiresAtEpochMs` INTEGER,
                `payloadJson` TEXT NOT NULL,
                PRIMARY KEY(`namespace`, `key`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_runtime_snapshots_namespace_updatedAtEpochMs`
            ON `runtime_snapshots` (`namespace`, `updatedAtEpochMs`)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_runtime_snapshots_namespace_expiresAtEpochMs`
            ON `runtime_snapshots` (`namespace`, `expiresAtEpochMs`)
            """.trimIndent(),
        )
    }
}
```

- [ ] **Step 6: Wire database version and DAO**

Modify `data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt`:

```kotlin
// add imports
import com.hank.musicfree.data.db.dao.RuntimeSnapshotDao
import com.hank.musicfree.data.db.entity.RuntimeSnapshotEntity

@Database(
    entities = [
        MusicItemEntity::class,
        PlaylistEntity::class,
        PlaylistMusicCrossRef::class,
        PlayQueueEntity::class,
        StarredSheetEntity::class,
        LyricCacheEntity::class,
        MediaCacheEntity::class,
        DownloadTaskEntity::class,
        DownloadedTrackEntity::class,
        PluginMetadataCacheEntity::class,
        ListenEventEntity::class,
        ListenEventArtistEntity::class,
        RuntimeSnapshotEntity::class,
    ],
    version = 13,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    // existing DAO methods stay unchanged
    abstract fun runtimeSnapshotDao(): RuntimeSnapshotDao
}
```

Modify `data/src/main/java/com/hank/musicfree/data/di/DataModule.kt`:

```kotlin
import com.hank.musicfree.core.runtime.SnapshotStore
import com.hank.musicfree.data.db.dao.RuntimeSnapshotDao
import com.hank.musicfree.data.db.migration.MIGRATION_12_13
import com.hank.musicfree.data.runtime.RoomRuntimeSnapshotStore

Room.databaseBuilder(context, AppDatabase::class.java, "musicfree.db")
    .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
    .addCallback(SeedFavoriteCallback)
    .build()

@Provides
fun provideRuntimeSnapshotDao(db: AppDatabase): RuntimeSnapshotDao = db.runtimeSnapshotDao()

@Provides
@Singleton
fun provideSnapshotStore(impl: RoomRuntimeSnapshotStore): SnapshotStore = impl
```

Also update `DefaultBackupRepository(databaseVersion = 13)` in `DataModule.kt` so backup metadata matches the actual Room database version.

- [ ] **Step 7: Implement RoomRuntimeSnapshotStore**

Create `data/src/main/java/com/hank/musicfree/data/runtime/RoomRuntimeSnapshotStore.kt`:

```kotlin
package com.hank.musicfree.data.runtime

import com.hank.musicfree.core.runtime.RuntimeSnapshot
import com.hank.musicfree.core.runtime.SnapshotStore
import com.hank.musicfree.data.db.dao.RuntimeSnapshotDao
import com.hank.musicfree.data.db.entity.RuntimeSnapshotEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomRuntimeSnapshotStore @Inject constructor(
    private val dao: RuntimeSnapshotDao,
) : SnapshotStore {
    override suspend fun read(namespace: String, key: String): RuntimeSnapshot? =
        dao.get(namespace, key)?.toDomain()

    override suspend fun write(snapshot: RuntimeSnapshot) {
        dao.upsert(snapshot.toEntity())
    }

    override suspend fun delete(namespace: String, key: String) {
        dao.delete(namespace, key)
    }

    override suspend fun deleteExpired(namespace: String, nowEpochMs: Long): Int =
        dao.deleteExpired(namespace, nowEpochMs)

    override suspend fun pruneNamespace(namespace: String, keepLatest: Int): Int =
        dao.pruneNamespace(namespace, keepLatest.coerceAtLeast(0))

    override suspend fun keys(namespace: String, limit: Int): List<String> =
        dao.keys(namespace, limit.coerceAtLeast(0))
}

private fun RuntimeSnapshotEntity.toDomain(): RuntimeSnapshot = RuntimeSnapshot(
    namespace = namespace,
    key = key,
    snapshotVersion = snapshotVersion,
    sourceSignature = sourceSignature,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    expiresAtEpochMs = expiresAtEpochMs,
    payloadJson = payloadJson,
)

private fun RuntimeSnapshot.toEntity(): RuntimeSnapshotEntity = RuntimeSnapshotEntity(
    namespace = namespace,
    key = key,
    snapshotVersion = snapshotVersion,
    sourceSignature = sourceSignature,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    expiresAtEpochMs = expiresAtEpochMs,
    payloadJson = payloadJson,
)
```

- [ ] **Step 8: Run data tests**

Run:

```bash
./gradlew :data:testDebugUnitTest --no-daemon
```

Expected: JVM tests pass.

- [ ] **Step 9: Run migration test on device or emulator**

Run:

```bash
./gradlew :data:connectedDebugAndroidTest --no-daemon
```

Expected: migration tests pass. If no device is available, record this command as not run in the final handoff and do not claim migration runtime validation.

- [ ] **Step 10: Commit**

```bash
git add data/src/main/java/com/hank/musicfree/data/db/entity/RuntimeSnapshotEntity.kt \
  data/src/main/java/com/hank/musicfree/data/db/dao/RuntimeSnapshotDao.kt \
  data/src/main/java/com/hank/musicfree/data/db/migration/Migration12To13.kt \
  data/src/main/java/com/hank/musicfree/data/runtime/RoomRuntimeSnapshotStore.kt \
  data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt \
  data/src/main/java/com/hank/musicfree/data/di/DataModule.kt \
  data/src/test/java/com/hank/musicfree/data/runtime/RoomRuntimeSnapshotStoreTest.kt \
  data/src/androidTest/java/com/hank/musicfree/data/db/AppDatabaseMigration12To13Test.kt \
  data/schemas
git commit -m "feat(runtime): 落地通用快照存储"
```

---

## Task 4: Add Non-blocking Runtime Restore Coordinator

**Files:**
- Create: `app/src/main/java/com/hank/musicfree/runtime/RuntimeStoreRegistry.kt`
- Create: `app/src/main/java/com/hank/musicfree/runtime/RuntimeRestoreCoordinator.kt`
- Modify: `app/src/main/java/com/hank/musicfree/MusicFreeApplication.kt`
- Test: `app/src/test/java/com/hank/musicfree/runtime/RuntimeRestoreCoordinatorTest.kt`

- [ ] **Step 1: Write coordinator test**

Create `app/src/test/java/com/hank/musicfree/runtime/RuntimeRestoreCoordinatorTest.kt`:

```kotlin
package com.hank.musicfree.runtime

import com.hank.musicfree.core.runtime.RuntimeRestoreResult
import com.hank.musicfree.core.runtime.RuntimeStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeRestoreCoordinatorTest {
    @Test
    fun startReturnsImmediatelyAndRestoresStoresInApplicationScope() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val fast = FakeRuntimeStore("playback", RuntimeRestoreResult.Restored)
        val failed = FakeRuntimeStore("plugin", RuntimeRestoreResult.Failed("boom"))
        val slow = FakeRuntimeStore("download", RuntimeRestoreResult.Skipped("empty"))
        val coordinator = RuntimeRestoreCoordinator(
            applicationScope = scope,
            registry = RuntimeStoreRegistry(setOf(fast, failed, slow)),
        )

        coordinator.start()

        assertEquals(0, fast.restoreCount)
        advanceUntilIdle()
        assertEquals(1, fast.restoreCount)
        assertEquals(1, failed.restoreCount)
        assertEquals(1, slow.restoreCount)
    }

    private class FakeRuntimeStore(
        override val storeName: String,
        private val result: RuntimeRestoreResult,
    ) : RuntimeStore<Unit> {
        override val state = MutableStateFlow(Unit)
        var restoreCount = 0

        override suspend fun restore(): RuntimeRestoreResult {
            restoreCount += 1
            return result
        }

        override suspend fun persist() = Unit
        override suspend fun prune(nowEpochMs: Long) = Unit
    }
}
```

- [ ] **Step 2: Run test and confirm it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.hank.musicfree.runtime.RuntimeRestoreCoordinatorTest --no-daemon
```

Expected: compilation fails because coordinator classes do not exist.

- [ ] **Step 3: Implement registry**

Create `app/src/main/java/com/hank/musicfree/runtime/RuntimeStoreRegistry.kt`:

```kotlin
package com.hank.musicfree.runtime

import com.hank.musicfree.core.runtime.RuntimeStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuntimeStoreRegistry @Inject constructor(
    val stores: Set<@JvmSuppressWildcards RuntimeStore<*>>,
)
```

- [ ] **Step 4: Implement coordinator with failure isolation and logs**

Create `app/src/main/java/com/hank/musicfree/runtime/RuntimeRestoreCoordinator.kt`:

```kotlin
package com.hank.musicfree.runtime

import com.hank.musicfree.core.runtime.RuntimeRestoreResult
import com.hank.musicfree.di.ApplicationScope
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.RuntimeLogFields
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

@Singleton
class RuntimeRestoreCoordinator @Inject constructor(
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val registry: RuntimeStoreRegistry,
) {
    fun start() {
        applicationScope.launch {
            supervisorScope {
                registry.stores.forEach { store ->
                    launch {
                        restoreOne(store)
                    }
                }
            }
        }
    }

    private suspend fun restoreOne(store: RuntimeStore<*>) {
        val startedAt = System.nanoTime()
        val key = "${store.storeName}:startup"
        MfLog.detail(
            category = LogCategory.RUNTIME,
            event = "runtime_restore_start",
            fields = RuntimeLogFields.restoreStart(store.storeName, key),
        )
        try {
            val result = withContext(Dispatchers.IO) { store.restore() }
            val (resultValue, reason) = result.toLogResult()
            MfLog.detail(
                category = LogCategory.RUNTIME,
                event = when (result) {
                    RuntimeRestoreResult.Restored -> "runtime_restore_success"
                    is RuntimeRestoreResult.Skipped -> "runtime_restore_skipped"
                    is RuntimeRestoreResult.Stale -> "runtime_restore_stale"
                    is RuntimeRestoreResult.Failed -> "runtime_restore_failed"
                },
                fields = RuntimeLogFields.restoreTerminal(
                    store = store.storeName,
                    key = key,
                    result = resultValue,
                    durationMs = elapsedMs(startedAt),
                    reason = reason,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.RUNTIME,
                event = "runtime_restore_failed",
                throwable = error,
                fields = RuntimeLogFields.restoreTerminal(
                    store = store.storeName,
                    key = key,
                    result = LogFields.Result.FAILURE,
                    durationMs = elapsedMs(startedAt),
                    reason = "exception",
                ),
            )
        }
    }

    private fun RuntimeRestoreResult.toLogResult(): Pair<String, String?> = when (this) {
        RuntimeRestoreResult.Restored -> LogFields.Result.SUCCESS to null
        is RuntimeRestoreResult.Skipped -> LogFields.Result.SKIPPED to reason
        is RuntimeRestoreResult.Stale -> LogFields.Result.STALE to reason
        is RuntimeRestoreResult.Failed -> LogFields.Result.FAILURE to reason
    }

    private fun elapsedMs(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000
}
```

- [ ] **Step 5: Inject coordinator in Application**

Modify `app/src/main/java/com/hank/musicfree/MusicFreeApplication.kt`:

```kotlin
import com.hank.musicfree.di.ApplicationScope
import com.hank.musicfree.runtime.RuntimeRestoreCoordinator
import kotlinx.coroutines.CoroutineScope

@Inject lateinit var runtimeRestoreCoordinator: RuntimeRestoreCoordinator
@Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

// remove the private local applicationScope property

override fun onCreate() {
    super.onCreate()
    LoggingInitializer.initialize(...)
    if (BuildConfig.DEBUG && System.getenv("PARITY_AUDIT") == "1") {
        MfLog.enableParitySink()
    }

    startLoggingPreferenceBridge()
    runtimeRestoreCoordinator.start()
    defaultPluginsBootstrapper.start()
    pluginAutoUpdateCoordinator.start()
    playbackStartupCoordinator.start()
    updateCheckCoordinator.start()
}
```

- [ ] **Step 6: Run app tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.hank.musicfree.runtime.RuntimeRestoreCoordinatorTest --no-daemon
```

Expected: test passes.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/hank/musicfree/runtime \
  app/src/main/java/com/hank/musicfree/MusicFreeApplication.kt \
  app/src/test/java/com/hank/musicfree/runtime/RuntimeRestoreCoordinatorTest.kt
git commit -m "feat(runtime): 并行启动运行时恢复"
```

---

## Task 5: Add PluginRuntimeStore

**Files:**
- Create: `plugin/src/main/java/com/hank/musicfree/plugin/runtime/PluginRuntimeStore.kt`
- Modify: `plugin/src/main/java/com/hank/musicfree/plugin/manager/PluginManager.kt`
- Test: `plugin/src/test/java/com/hank/musicfree/plugin/runtime/PluginRuntimeStoreTest.kt`

- [ ] **Step 1: Write store tests**

Create `plugin/src/test/java/com/hank/musicfree/plugin/runtime/PluginRuntimeStoreTest.kt` with fakes around `PluginMetadataCacheGateway` and a small fake loader. The test must cover:

```kotlin
@Test
fun restoreMetadataPublishesPluginIndexWithoutEvaluatingQuickJs() = runTest {
    val metadata = listOf(
        PluginRuntimeEntry(
            platform = "demo",
            version = "1.0.0",
            filePath = "/plugins/demo.js",
            loaded = false,
            failedReason = null,
        ),
    )
    val store = PluginRuntimeStore(
        metadataGateway = FakePluginMetadataGateway(metadata),
        snapshotStore = NoopSnapshotStore(),
        json = Json,
    )

    assertEquals(RuntimeRestoreResult.Restored, store.restore())

    assertEquals("demo", store.state.value.plugins.single().platform)
    assertEquals(listOf("demo"), store.state.value.plugins.map { it.platform })
}
```

Use this exact behavior: metadata restore is lightweight. The `PluginRuntimeStore` constructor must not accept `PluginManager`, `LoadedPlugin`, `JsEngine`, or any QuickJS runtime type, which makes synchronous QuickJS evaluation impossible inside `restore()`.

- [ ] **Step 2: Run test and confirm it fails**

Run:

```bash
./gradlew :plugin:testDebugUnitTest --tests com.hank.musicfree.plugin.runtime.PluginRuntimeStoreTest --no-daemon
```

Expected: compilation fails because `PluginRuntimeStore` does not exist.

- [ ] **Step 3: Add plugin runtime state models and store**

Create `plugin/src/main/java/com/hank/musicfree/plugin/runtime/PluginRuntimeStore.kt`:

```kotlin
package com.hank.musicfree.plugin.runtime

import com.hank.musicfree.core.runtime.RuntimeRestoreResult
import com.hank.musicfree.core.runtime.RuntimeStore
import com.hank.musicfree.core.runtime.RuntimeStoreKey
import com.hank.musicfree.core.runtime.SnapshotStore
import com.hank.musicfree.data.repository.PluginMetadataCacheGateway
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PluginRuntimeEntry(
    val platform: String,
    val version: String?,
    val filePath: String,
    val loaded: Boolean,
    val failedReason: String?,
)

data class PluginRuntimeState(
    val plugins: List<PluginRuntimeEntry> = emptyList(),
    val restoring: Boolean = false,
    val lastFailureReason: String? = null,
)

@Singleton
class PluginRuntimeStore @Inject constructor(
    private val metadataGateway: PluginMetadataCacheGateway,
    private val snapshotStore: SnapshotStore,
    private val json: Json,
) : RuntimeStore<PluginRuntimeState> {
    override val storeName: String = "plugin_runtime"
    private val mutableState = MutableStateFlow(PluginRuntimeState())
    override val state: StateFlow<PluginRuntimeState> = mutableState.asStateFlow()

    override suspend fun restore(): RuntimeRestoreResult {
        mutableState.value = mutableState.value.copy(restoring = true, lastFailureReason = null)
        return runCatching {
            val cached = metadataGateway.getAll()
            val entries = cached.map {
                PluginRuntimeEntry(
                    platform = it.platform,
                    version = it.version,
                    filePath = it.filePath,
                    loaded = false,
                    failedReason = null,
                )
            }
            mutableState.value = PluginRuntimeState(plugins = entries, restoring = false)
            if (entries.isEmpty()) {
                RuntimeRestoreResult.Skipped("empty_plugin_metadata")
            } else {
                RuntimeRestoreResult.Restored
            }
        }.getOrElse { error ->
            mutableState.value = PluginRuntimeState(restoring = false, lastFailureReason = "metadata_restore_failed")
            MfLog.error(
                category = LogCategory.PLUGIN,
                event = "plugin_runtime_restore_failed",
                throwable = error,
                fields = mapOf(
                    "store" to storeName,
                    "key" to RuntimeStoreKey.singleton(storeName).value,
                    LogFields.result(LogFields.Result.FAILURE),
                    LogFields.reason("metadata_restore_failed"),
                ),
            )
            RuntimeRestoreResult.Failed("metadata_restore_failed", error)
        }
    }

    override suspend fun persist() = Unit

    override suspend fun prune(nowEpochMs: Long) {
        snapshotStore.deleteExpired(storeName, nowEpochMs)
    }
}
```

Use the existing `PluginMetadataCacheGateway.getAll()` suspend API. Do not call `PluginManager.setup()`, `ensurePluginsLoaded()`, or any QuickJS evaluation path from `restore()`.

- [ ] **Step 4: Bind PluginRuntimeStore into the app registry**

Add a Hilt multibinding module in `plugin/src/main/java/com/hank/musicfree/plugin/runtime/PluginRuntimeModule.kt`:

```kotlin
package com.hank.musicfree.plugin.runtime

import com.hank.musicfree.core.runtime.RuntimeStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class PluginRuntimeModule {
    @Binds
    @IntoSet
    abstract fun bindPluginRuntimeStore(store: PluginRuntimeStore): RuntimeStore<*>
}
```

The app registry constructor already uses `Set<@JvmSuppressWildcards RuntimeStore<*>>` from Task 4 so the Hilt multibinding is injected directly.

- [ ] **Step 5: Run plugin tests**

Run:

```bash
./gradlew :plugin:testDebugUnitTest --no-daemon
```

Expected: tests pass.

- [ ] **Step 6: Commit**

```bash
git add plugin/src/main/java/com/hank/musicfree/plugin/runtime \
  plugin/src/test/java/com/hank/musicfree/plugin/runtime/PluginRuntimeStoreTest.kt \
  app/src/main/java/com/hank/musicfree/runtime/RuntimeStoreRegistry.kt \
  app/src/test/java/com/hank/musicfree/runtime/RuntimeRestoreCoordinatorTest.kt
git commit -m "feat(runtime): 接入插件运行时状态"
```

---

## Task 6: Add PlaybackRuntimeStore and Keep Startup Restore Fast

**Files:**
- Create: `player/src/main/java/com/hank/musicfree/player/runtime/PlaybackRuntimeStore.kt`
- Modify: `app/src/main/java/com/hank/musicfree/bootstrap/PlaybackStartupCoordinator.kt`
- Test: `player/src/test/java/com/hank/musicfree/player/runtime/PlaybackRuntimeStoreTest.kt`
- Test: `app/src/test/java/com/hank/musicfree/bootstrap/PlaybackStartupCoordinatorTest.kt`

- [ ] **Step 1: Write playback runtime tests**

Create `player/src/test/java/com/hank/musicfree/player/runtime/PlaybackRuntimeStoreTest.kt` covering:

```kotlin
@Test
fun restoreQueueSnapshotReturnsSkippedWhenQueueIsEmpty() = runTest {
    val store = PlaybackRuntimeStore(
        playQueueRepository = FakePlayQueueRepository(queue = emptyList()),
        appPreferences = FakeAppPreferences(index = 0, positionMs = 0, durationMs = 0),
        playerController = FakePlayerController(),
        playbackRuntimeSettings = FakePlaybackRuntimeSettings(autoPlay = false),
    )

    val result = store.restore()

    assertEquals(RuntimeRestoreResult.Skipped("empty_queue"), result)
    assertEquals(false, store.state.value.restored)
    assertEquals(0, store.playerController.restoreQueueCalls)
}

@Test
fun restoreQueueSnapshotRestoresControllerOnMainSafeBoundary() = runTest {
    val item = sampleMusic(id = "song-1", platform = "demo")
    val store = PlaybackRuntimeStore(
        playQueueRepository = FakePlayQueueRepository(queue = listOf(item)),
        appPreferences = FakeAppPreferences(index = 5, positionMs = 12_000, durationMs = 30_000),
        playerController = FakePlayerController(),
        playbackRuntimeSettings = FakePlaybackRuntimeSettings(autoPlay = true),
    )

    val result = store.restore()

    assertEquals(RuntimeRestoreResult.Restored, result)
    assertEquals(0, store.playerController.lastStartIndex)
    assertEquals(12_000L, store.state.value.savedPositionMs)
    assertEquals(true, store.state.value.autoPlayWhenRestored)
}
```

- [ ] **Step 2: Run tests and confirm they fail**

Run:

```bash
./gradlew :player:testDebugUnitTest --tests com.hank.musicfree.player.runtime.PlaybackRuntimeStoreTest --no-daemon
```

Expected: compilation fails because `PlaybackRuntimeStore` does not exist.

- [ ] **Step 3: Implement PlaybackRuntimeStore by extracting existing restore logic**

Move queue restore logic from `PlaybackStartupCoordinator` into `player/src/main/java/com/hank/musicfree/player/runtime/PlaybackRuntimeStore.kt`. Preserve these existing behaviors:

```kotlin
data class PlaybackRuntimeState(
    val restored: Boolean = false,
    val queueSize: Int = 0,
    val currentIndex: Int = 0,
    val savedPositionMs: Long = 0,
    val savedDurationMs: Long = 0,
    val autoPlayWhenRestored: Boolean = false,
    val lastFailureReason: String? = null,
)
```

Restore contract:

```kotlin
override suspend fun restore(): RuntimeRestoreResult {
    val queue = playQueueRepository.getQueue()
    if (queue.isEmpty()) {
        mutableState.value = PlaybackRuntimeState(restored = false)
        return RuntimeRestoreResult.Skipped("empty_queue")
    }
    val savedIndex = appPreferences.currentMusicIndex.first().coerceIn(0, queue.lastIndex)
    val savedPositionMs = appPreferences.currentMusicPositionMs.first()
    val savedDurationMs = appPreferences.currentMusicDurationMs.first()
    val autoPlay = playbackRuntimeSettings.autoPlayWhenAppStart()
    withContext(Dispatchers.Main.immediate) {
        playerController.restoreQueue(
            items = queue,
            startIndex = savedIndex,
            savedPositionMs = savedPositionMs,
            savedDurationMs = savedDurationMs,
            playWhenRestored = autoPlay,
        )
    }
    mutableState.value = PlaybackRuntimeState(
        restored = true,
        queueSize = queue.size,
        currentIndex = savedIndex,
        savedPositionMs = savedPositionMs,
        savedDurationMs = savedDurationMs,
        autoPlayWhenRestored = autoPlay,
    )
    return RuntimeRestoreResult.Restored
}
```

Use `MfLog.detail(LogCategory.PLAYER, "playback_runtime_restore_success", ...)` and `MfLog.error(LogCategory.PLAYER, "playback_runtime_restore_failed", ...)` inside the store, with fields:

```kotlin
mapOf(
    "store" to storeName,
    "key" to "playback:current",
    "queueSize" to queue.size,
    "startIndex" to savedIndex,
    "savedPositionMs" to savedPositionMs,
    "savedDurationMs" to savedDurationMs,
    "autoPlay" to autoPlay,
    "durationMs" to elapsedMs(startedAt),
    "result" to "success",
)
```

- [ ] **Step 4: Reduce PlaybackStartupCoordinator to persistence collection**

Modify `PlaybackStartupCoordinator` so it no longer performs the initial queue restore. It should only start the position/queue persistence collectors that are already present. Keep existing `playback_position_persist_failed` logging.

- [ ] **Step 5: Bind PlaybackRuntimeStore into the registry**

Create `player/src/main/java/com/hank/musicfree/player/runtime/PlaybackRuntimeModule.kt`:

```kotlin
package com.hank.musicfree.player.runtime

import com.hank.musicfree.core.runtime.RuntimeStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackRuntimeModule {
    @Binds
    @IntoSet
    abstract fun bindPlaybackRuntimeStore(store: PlaybackRuntimeStore): RuntimeStore<*>
}
```

- [ ] **Step 6: Run player and app tests**

Run:

```bash
./gradlew :player:testDebugUnitTest :app:testDebugUnitTest --no-daemon
```

Expected: tests pass.

- [ ] **Step 7: Commit**

```bash
git add player/src/main/java/com/hank/musicfree/player/runtime \
  player/src/test/java/com/hank/musicfree/player/runtime/PlaybackRuntimeStoreTest.kt \
  app/src/main/java/com/hank/musicfree/bootstrap/PlaybackStartupCoordinator.kt \
  app/src/test/java/com/hank/musicfree/bootstrap/PlaybackStartupCoordinatorTest.kt
git commit -m "feat(runtime): 收敛播放冷启动恢复"
```

---

## Task 7: Add DownloadRuntimeStore

**Files:**
- Create: `downloader/src/main/java/com/hank/musicfree/downloader/runtime/DownloadRuntimeStore.kt`
- Modify: `downloader/src/main/java/com/hank/musicfree/downloader/engine/DownloadEngine.kt`
- Test: `downloader/src/test/java/com/hank/musicfree/downloader/runtime/DownloadRuntimeStoreTest.kt`

- [ ] **Step 1: Write download store tests**

Create tests that assert startup restore loads task index from the existing download task repository/DAO and does not restart HTTP calls synchronously:

```kotlin
@Test
fun restoreLoadsTaskIndexWithoutStartingDownloadsSynchronously() = runTest {
    val task = sampleDownloadTask(id = "task-1", status = "downloading")
    val engine = FakeDownloadEngine(tasks = listOf(task))
    val store = DownloadRuntimeStore(engine = engine)

    val result = store.restore()

    assertEquals(RuntimeRestoreResult.Restored, result)
    assertEquals(listOf("task-1"), store.state.value.taskIds)
    assertEquals(0, engine.startedNetworkCalls)
}
```

- [ ] **Step 2: Run tests and confirm they fail**

Run:

```bash
./gradlew :downloader:testDebugUnitTest --tests com.hank.musicfree.downloader.runtime.DownloadRuntimeStoreTest --no-daemon
```

Expected: compilation fails because `DownloadRuntimeStore` does not exist.

- [ ] **Step 3: Add store and bind it**

Implement `DownloadRuntimeStore` as a thin process-level facade over the existing `DownloadEngine` state flows. Persisted task truth remains in Room download tables; the store restores the index and exposes stable state:

```kotlin
data class DownloadRuntimeState(
    val taskIds: List<String> = emptyList(),
    val activeCount: Int = 0,
    val failedCount: Int = 0,
    val restoring: Boolean = false,
    val lastFailureReason: String? = null,
)
```

The store must:

- return `Skipped("empty_download_tasks")` when no task exists.
- log `download_runtime_restore_success` with `count`, `activeCount`, `failedCount`, `durationMs`.
- log `download_runtime_restore_failed` with `MfLog.error(LogCategory.DOWNLOAD, ...)`.
- avoid synchronous network restart inside `restore()`. Restart/resume policy stays owned by `DownloadEngine`.

Create `downloader/src/main/java/com/hank/musicfree/downloader/runtime/DownloadRuntimeModule.kt` with Hilt `@IntoSet` binding for `RuntimeStore<*>`.

- [ ] **Step 4: Run downloader tests**

Run:

```bash
./gradlew :downloader:testDebugUnitTest --no-daemon
```

Expected: tests pass.

- [ ] **Step 5: Commit**

```bash
git add downloader/src/main/java/com/hank/musicfree/downloader/runtime \
  downloader/src/main/java/com/hank/musicfree/downloader/engine/DownloadEngine.kt \
  downloader/src/test/java/com/hank/musicfree/downloader/runtime/DownloadRuntimeStoreTest.kt
git commit -m "feat(runtime): 接入下载任务运行时索引"
```

---

## Task 8: Replace One-shot Route Seeds with Recoverable RouteSeedRuntimeStore

**Files:**
- Create: `feature/home/src/main/java/com/hank/musicfree/home/runtime/RouteSeedRuntimeStore.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/home/pluginsheet/navigation/PluginSheetSeedStore.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/home/musicdetail/navigation/MusicDetailSeedStore.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/home/albumdetail/navigation/AlbumDetailSeedStore.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/home/artistdetail/navigation/ArtistDetailSeedStore.kt`
- Test: `feature/home/src/test/java/com/hank/musicfree/home/runtime/RouteSeedRuntimeStoreTest.kt`

- [ ] **Step 1: Write route seed tests**

Create `RouteSeedRuntimeStoreTest`:

```kotlin
@Test
fun resolveCanBeCalledMultipleTimesBeforePrune() = runTest {
    val store = RouteSeedRuntimeStore(
        snapshotStore = InMemorySnapshotStore(),
        json = Json { ignoreUnknownKeys = true },
        clock = FakeClock(now = 1_000),
    )
    val key = RuntimeStoreKey.routeSeed("album", "demo", "id-1").value

    store.put(key, payloadJson = """{"title":"Album A","raw":{"id":"id-1"}}""", ttlMs = 60_000)

    assertEquals("Album A", store.resolve(key)?.payload?.jsonObject?.get("title")?.jsonPrimitive?.content)
    assertEquals("Album A", store.resolve(key)?.payload?.jsonObject?.get("title")?.jsonPrimitive?.content)
}

@Test
fun restoreSkipsExpiredSeeds() = runTest {
    val snapshotStore = InMemorySnapshotStore()
    snapshotStore.write(expiredRouteSeedSnapshot(key = "route_seed:album:demo:id-1"))
    val store = RouteSeedRuntimeStore(snapshotStore, Json, FakeClock(now = 2_000))

    val result = store.restore()

    assertEquals(RuntimeRestoreResult.Skipped("empty_route_seeds"), result)
    assertNull(store.resolve("route_seed:album:demo:id-1"))
}
```

- [ ] **Step 2: Run tests and confirm they fail**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests com.hank.musicfree.home.runtime.RouteSeedRuntimeStoreTest --no-daemon
```

Expected: compilation fails because the store does not exist.

- [ ] **Step 3: Implement route seed store**

Create a JSON-based store that holds route seed payloads by key:

```kotlin
data class RouteSeedEntry(
    val key: String,
    val payload: JsonElement,
    val updatedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
)

data class RouteSeedRuntimeState(
    val keys: Set<String> = emptySet(),
    val restoring: Boolean = false,
)
```

Required behavior:

- `put(key, payloadJson, ttlMs)` writes in-memory immediately and persists a `RuntimeSnapshot(namespace = "route_seed", ...)`.
- `resolve(key)` returns without consuming.
- `consume(key)` may remain as compatibility wrapper, but it must call `resolve(key)` and not remove the seed.
- `prune(nowEpochMs)` removes expired entries from memory and snapshot store.
- logs:
  - `route_seed_persist_success`
  - `route_seed_restore_success`
  - `route_seed_restore_skipped`
  - `route_seed_restore_failed`

- [ ] **Step 4: Adapt existing SeedStore objects**

For each existing seed store, keep the public API used by navigation code but delegate to `RouteSeedRuntimeStore`.

Example for `PluginSheetSeedStore`:

```kotlin
object PluginSheetSeedStore {
    fun put(seed: PluginSheetSeed): String {
        val key = RuntimeStoreKey.routeSeed(
            target = "plugin_sheet",
            platform = seed.platform,
            id = seed.id,
        ).value
        RouteSeedRuntimeStoreProvider.current.put(
            key = key,
            payloadJson = Json.encodeToString(seed),
            ttlMs = ROUTE_SEED_TTL_MS,
        )
        return key
    }

    fun resolve(key: String): PluginSheetSeed? =
        RouteSeedRuntimeStoreProvider.current.resolveTyped(key)

    fun take(key: String): PluginSheetSeed? = resolve(key)
}
```

If the current object-style seed stores cannot receive Hilt dependencies cleanly, introduce a small injectable navigation helper and migrate callers in the same task. Do not add a service locator unless the existing call sites make constructor injection impossible.

- [ ] **Step 5: Bind RouteSeedRuntimeStore**

Create `feature/home/src/main/java/com/hank/musicfree/home/runtime/HomeRuntimeModule.kt` with Hilt `@IntoSet` binding for `RuntimeStore<*>`.

- [ ] **Step 6: Run home tests**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --no-daemon
```

Expected: tests pass.

- [ ] **Step 7: Commit**

```bash
git add feature/home/src/main/java/com/hank/musicfree/home/runtime \
  feature/home/src/main/java/com/hank/musicfree/home/*/navigation/*SeedStore.kt \
  feature/home/src/test/java/com/hank/musicfree/home/runtime/RouteSeedRuntimeStoreTest.kt
git commit -m "feat(runtime): 让详情路由 seed 可恢复"
```

---

## Task 9: Add SearchSessionStore and Migrate SearchViewModel

**Files:**
- Create: `feature/search/src/main/java/com/hank/musicfree/search/runtime/SearchSessionStore.kt`
- Modify: `feature/search/src/main/java/com/hank/musicfree/search/SearchViewModel.kt`
- Test: `feature/search/src/test/java/com/hank/musicfree/search/runtime/SearchSessionStoreTest.kt`
- Test: `feature/search/src/test/java/com/hank/musicfree/search/SearchViewModelRuntimeStoreTest.kt`

- [ ] **Step 1: Write store tests**

Create tests for:

```kotlin
@Test
fun restoreSnapshotPublishesPreviousResultsWhenSignatureMatches() = runTest {
    val snapshotStore = InMemorySnapshotStore().apply {
        write(searchSnapshot(query = "hello", sourceSignature = "plugin:demo:1"))
    }
    val store = SearchSessionStore(
        snapshotStore = snapshotStore,
        pluginSignatureProvider = { "plugin:demo:1" },
        searchGateway = FakeSearchGateway(),
        json = Json { ignoreUnknownKeys = true },
        clock = FakeClock(now = 1_000),
    )

    assertEquals(RuntimeRestoreResult.Restored, store.restore())

    assertEquals("hello", store.state.value.query)
    assertEquals(1, store.state.value.results.getValue("demo").items.size)
}

@Test
fun staleSearchResultDoesNotOverwriteNewGeneration() = runTest {
    val gateway = ControllableSearchGateway()
    val store = SearchSessionStore(InMemorySnapshotStore(), { "sig" }, gateway, Json, FakeClock(1_000))

    val first = store.search(query = "old", platform = "demo")
    val second = store.search(query = "new", platform = "demo")
    gateway.complete(first, listOf(sampleMusic("old-result")))
    gateway.complete(second, listOf(sampleMusic("new-result")))

    assertEquals("new", store.state.value.query)
    assertEquals("new-result", store.state.value.results.getValue("demo").items.single().id)
}
```

- [ ] **Step 2: Run tests and confirm they fail**

Run:

```bash
./gradlew :feature:search:testDebugUnitTest --tests '*SearchSessionStoreTest' --no-daemon
```

Expected: compilation fails because `SearchSessionStore` does not exist.

- [ ] **Step 3: Implement search state and snapshot schema**

Create `SearchSessionStore` with:

```kotlin
@Serializable
data class SearchSessionSnapshot(
    val query: String,
    val mediaType: String,
    val selectedPlatform: String?,
    val generation: Long,
    val platformResults: Map<String, SearchPlatformSnapshot>,
)

@Serializable
data class SearchPlatformSnapshot(
    val page: Int,
    val isEnd: Boolean,
    val itemCount: Int,
    val payloadJson: String,
)
```

State rules:

- Snapshot namespace: `search_session`.
- Key: `RuntimeStoreKey.search(mediaType, selectedPlatform ?: "all", queryHash).value`.
- TTL: 24 hours for search sessions.
- Capacity: keep latest 10 search sessions.
- Source signature: current plugin capability/version signature.
- If signature mismatches, return `RuntimeRestoreResult.Stale("plugin_signature_changed")`, keep query/tab, and mark results as refreshable instead of trusted.
- Persist only after terminal search success or pagination success, not on every keystroke.

Logs:

- `search_session_restore_success/skipped/stale/failed`
- `search_session_persist_success/failed`
- `search_session_result_stale` when generation mismatch drops old results
- include `query`, `platform`, `mediaType`, `generation`, `page`, `count`, `durationMs`, `result`, `reason`.

- [ ] **Step 4: Migrate SearchViewModel**

`SearchViewModel` becomes a page adapter:

- subscribes to `SearchSessionStore.state`.
- forwards search, tab switch, platform switch, and pagination actions.
- keeps only UI transient state locally, such as active dialog/menu target.
- no longer owns durable search result lists as the only source of truth.

Add a `SearchViewModelRuntimeStoreTest` asserting that:

```kotlin
@Test
fun viewModelUsesStoreStateAfterRecreation() = runTest {
    val store = FakeSearchSessionStore(
        initialState = SearchSessionState(query = "hello", selectedPlatform = "demo")
    )
    val first = SearchViewModel(store = store, ...)
    first.onSearch("hello")

    val recreated = SearchViewModel(store = store, ...)

    assertEquals("hello", recreated.uiState.value.query)
    assertEquals("demo", recreated.uiState.value.selectedPlatform)
}
```

- [ ] **Step 5: Bind SearchSessionStore**

Create a Hilt module in `feature/search/src/main/java/com/hank/musicfree/search/runtime/SearchRuntimeModule.kt` with `@IntoSet` binding for `RuntimeStore<*>`.

- [ ] **Step 6: Run search tests**

Run:

```bash
./gradlew :feature:search:testDebugUnitTest --no-daemon
```

Expected: tests pass.

- [ ] **Step 7: Commit**

```bash
git add feature/search/src/main/java/com/hank/musicfree/search/runtime \
  feature/search/src/main/java/com/hank/musicfree/search/SearchViewModel.kt \
  feature/search/src/test/java/com/hank/musicfree/search/runtime/SearchSessionStoreTest.kt \
  feature/search/src/test/java/com/hank/musicfree/search/SearchViewModelRuntimeStoreTest.kt
git commit -m "feat(runtime): 持久化搜索会话"
```

---

## Task 10: Add DetailSessionStore and Migrate Detail ViewModels

**Files:**
- Create: `feature/home/src/main/java/com/hank/musicfree/home/runtime/DetailSessionStore.kt`
- Modify:
  - `feature/home/src/main/java/com/hank/musicfree/home/pluginsheet/PluginSheetDetailViewModel.kt`
  - `feature/home/src/main/java/com/hank/musicfree/home/toplist/TopListDetailViewModel.kt`
  - `feature/home/src/main/java/com/hank/musicfree/home/albumdetail/AlbumDetailViewModel.kt`
  - `feature/home/src/main/java/com/hank/musicfree/home/artistdetail/ArtistDetailViewModel.kt`
- Test: `feature/home/src/test/java/com/hank/musicfree/home/runtime/DetailSessionStoreTest.kt`

- [ ] **Step 1: Write detail session tests**

Create tests for:

```kotlin
@Test
fun restoreDetailSnapshotPublishesHeaderAndPageResults() = runTest {
    val key = RuntimeStoreKey.detail("plugin_sheet", "demo", "sheet-1").value
    val snapshotStore = InMemorySnapshotStore().apply {
        write(detailSnapshot(key = key, sourceSignature = "plugin:demo:1"))
    }
    val store = DetailSessionStore(
        snapshotStore = snapshotStore,
        pluginSignatureProvider = { "plugin:demo:1" },
        detailGateway = FakeDetailGateway(),
        json = Json { ignoreUnknownKeys = true },
        clock = FakeClock(now = 1_000),
    )

    assertEquals(RuntimeRestoreResult.Restored, store.restore(key))

    val detail = store.state.value.sessions.getValue(key)
    assertEquals("Demo Sheet", detail.header.title)
    assertEquals(20, detail.items.size)
}

@Test
fun pluginSignatureMismatchMarksSnapshotStale() = runTest {
    val key = RuntimeStoreKey.detail("plugin_sheet", "demo", "sheet-1").value
    val snapshotStore = InMemorySnapshotStore().apply {
        write(detailSnapshot(key = key, sourceSignature = "plugin:demo:old"))
    }
    val store = DetailSessionStore(snapshotStore, { "plugin:demo:new" }, FakeDetailGateway(), Json, FakeClock(1_000))

    val result = store.restore(key)

    assertEquals(RuntimeRestoreResult.Stale("plugin_signature_changed"), result)
    assertEquals(true, store.state.value.sessions.getValue(key).needsRefresh)
}
```

- [ ] **Step 2: Run tests and confirm they fail**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests '*DetailSessionStoreTest' --no-daemon
```

Expected: compilation fails because `DetailSessionStore` does not exist.

- [ ] **Step 3: Implement detail session snapshot schema**

Create schema:

```kotlin
@Serializable
data class DetailSessionSnapshot(
    val routeType: String,
    val platform: String,
    val id: String,
    val headerJson: String,
    val rawJson: String?,
    val page: Int,
    val isEnd: Boolean,
    val itemCount: Int,
    val itemsJson: String,
)
```

State rules:

- Snapshot namespace: `detail_session`.
- Key: `RuntimeStoreKey.detail(type, platform, id).value`.
- TTL: 72 hours for detail sessions.
- Capacity: keep latest 20 detail sessions.
- Source signature: plugin version/capability signature.
- If signature mismatches, keep header only and mark `needsRefresh = true`.
- Persist after initial detail success and pagination success.
- Route seed raw fields must be copied into the detail snapshot when they affect plugin requests.

Logs:

- `detail_session_restore_success/skipped/stale/failed`
- `detail_session_persist_success/failed`
- `detail_session_result_stale` on generation mismatch
- fields: `routeType`, `platform`, `itemId`, `page`, `count`, `generation`, `durationMs`, `result`, `reason`.

- [ ] **Step 4: Migrate PluginSheetDetailViewModel**

Use `DetailSessionStore` as the source of header, list, page, loading/error, and stale state. Keep UI transient state such as menu/dialog targets in the ViewModel. Add a test that recreating the ViewModel with the same store does not reload page 1 or drop the current item list.

- [ ] **Step 5: Migrate TopListDetailViewModel**

Use the same `DetailSessionStore` with `routeType = "top_list"`. Preserve RN-aligned click behavior where row click switches playback without navigating to song detail.

- [ ] **Step 6: Migrate AlbumDetailViewModel and ArtistDetailViewModel**

Use `routeType = "album"` and `routeType = "artist"`. Preserve pagination and stale generation behavior.

- [ ] **Step 7: Run home tests**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --no-daemon
```

Expected: tests pass.

- [ ] **Step 8: Commit**

```bash
git add feature/home/src/main/java/com/hank/musicfree/home/runtime/DetailSessionStore.kt \
  feature/home/src/main/java/com/hank/musicfree/home/pluginsheet/PluginSheetDetailViewModel.kt \
  feature/home/src/main/java/com/hank/musicfree/home/toplist/TopListDetailViewModel.kt \
  feature/home/src/main/java/com/hank/musicfree/home/albumdetail/AlbumDetailViewModel.kt \
  feature/home/src/main/java/com/hank/musicfree/home/artistdetail/ArtistDetailViewModel.kt \
  feature/home/src/test/java/com/hank/musicfree/home/runtime/DetailSessionStoreTest.kt
git commit -m "feat(runtime): 持久化插件详情会话"
```

---

## Task 11: Add Activity Recreate and Cold-start Acceptance Coverage

**Files:**
- Create: `app/src/androidTest/java/com/hank/musicfree/runtime/RuntimeStateRecreateTest.kt`
- Modify test fakes as required under the touched modules only.

- [ ] **Step 1: Add Activity recreate test for route seed/detail state**

Create an instrumentation test that:

1. launches `MainActivity`;
2. seeds a detail session or route seed through the injectable store;
3. navigates to the matching detail route;
4. calls `scenario.recreate()`;
5. asserts the header/list is still visible.

Use stable semantic matchers already present in the UI. If the target screen lacks stable tags, add tags that follow the UI harness naming conventions.

Expected test shape:

```kotlin
@Test
fun pluginSheetDetailSurvivesActivityRecreate() {
    val scenario = ActivityScenario.launch(MainActivity::class.java)
    composeRule.onNodeWithText("Demo Sheet").assertIsDisplayed()

    scenario.recreate()

    composeRule.onNodeWithText("Demo Sheet").assertIsDisplayed()
    composeRule.onNodeWithText("Song 1").assertIsDisplayed()
}
```

- [ ] **Step 2: Add cold-start restore smoke test where feasible**

Use a test-only fake SnapshotStore or seeded Room database to verify app startup does not wait on search/detail payload restore. The assertion should be behavioral:

```kotlin
@Test
fun appLaunchDoesNotBlockOnLazySearchSnapshotRestore() {
    fakeSearchSessionStore.delayRestoreUntilReleased()

    ActivityScenario.launch(MainActivity::class.java)

    composeRule.onNodeWithTag("home_screen").assertExists()
    assertEquals(false, fakeSearchSessionStore.largePayloadRestoreStartedSynchronously)
}
```

If the current DI/test setup cannot replace stores in instrumentation cleanly, create a JVM coordinator test that proves lazy stores are not in the startup registry and log the instrumentation gap in the final handoff.

- [ ] **Step 3: Run instrumentation tests on device or emulator**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest --no-daemon
```

Expected: tests pass. If no device is available, record this as not run.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/com/hank/musicfree/runtime/RuntimeStateRecreateTest.kt
git commit -m "test(runtime): 覆盖 Activity 重建恢复"
```

---

## Task 12: Final Guard, Docs, and Review

**Files:**
- Modify only if behavior or commands changed:
  - `docs/dev-harness/runtime/rules.md`
  - `docs/DOCS_STATUS.md`
  - `AGENTS.md`

- [ ] **Step 1: Run focused unit tests**

Run:

```bash
./gradlew :logging:testDebugUnitTest \
  :core:testDebugUnitTest \
  :data:testDebugUnitTest \
  :plugin:testDebugUnitTest \
  :player:testDebugUnitTest \
  :downloader:testDebugUnitTest \
  :feature:search:testDebugUnitTest \
  :feature:home:testDebugUnitTest \
  :app:testDebugUnitTest \
  --no-daemon
```

Expected: all touched unit tests pass.

- [ ] **Step 2: Run harness checks**

Run:

```bash
bash scripts/dev-harness/check.sh
```

Expected: no runtime/plugin/player/ui/test harness violations.

- [ ] **Step 3: Run Debug build**

Run:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: Debug APK builds.

- [ ] **Step 4: Run Android runtime gates when device is available**

Run:

```bash
./gradlew :data:connectedDebugAndroidTest :app:connectedDebugAndroidTest --no-daemon
```

Expected: Room migration and Activity recreate tests pass.

- [ ] **Step 5: Decode logs after one manual runtime smoke**

Manual smoke on Debug build:

1. cold start app;
2. open a plugin-backed search;
3. open a plugin sheet/detail page;
4. background/foreground the app;
5. rotate or trigger Activity recreate if available;
6. export feedback logs or pull Logan logs.

Decode with:

```bash
tools/logan/decode-logan.sh <feedback-zip-or-logan-dir>
```

Required events in decoded logs:

- `runtime_restore_start`
- `runtime_restore_success` or `runtime_restore_skipped`
- `runtime_snapshot_persist_success`
- `search_session_restore_success` or `search_session_restore_skipped`
- `detail_session_restore_success` or `detail_session_restore_skipped`
- `route_seed_restore_success` or `route_seed_restore_skipped`
- `playback_runtime_restore_success` or `playback_runtime_restore_skipped`
- `download_runtime_restore_success` or `download_runtime_restore_skipped`

Each runtime event must include `store`, `operation`, `key`, `result`, and `durationMs` when it performs IO.

- [ ] **Step 6: Check formatting and placeholders**

Run:

```bash
rg -n 'T''BD|TO''DO|implement ''later|/Users''/' docs/superpowers/plans/2026-05-19-runtime-store-architecture.md \
  docs/dev-harness/runtime/rules.md \
  docs/superpowers/specs/2026-05-19-runtime-store-architecture-design.md
git diff --check
```

Expected: no matches and no whitespace errors.

- [ ] **Step 7: Final code review**

Review for:

- No persisted QuickJS, Media3, Coroutine, Android UI, Repository, DAO, OkHttp, or Room DB instances.
- No large snapshot restore in `Application.onCreate()`.
- Search/detail large payloads restore lazily from page subscription.
- Route seed `take()` is no longer destructive for recoverable seeds.
- ViewModels subscribe to stores and forward actions instead of duplicating restore/persist.
- Logs have start and terminal events with stable fields.
- `CancellationException` is rethrown, not swallowed as an error.
- Room migration and `databaseVersion` are aligned at version 13.

- [ ] **Step 8: Commit docs adjustments if any**

```bash
git add docs/dev-harness/runtime/rules.md docs/DOCS_STATUS.md AGENTS.md
git commit -m "docs(runtime): 更新运行时守门验收" || true
```

Use the commit only when those files changed.

---

## Implementation Order

Recommended order:

1. Task 1 logging primitives
2. Task 2 core runtime contracts
3. Task 3 Room SnapshotStore
4. Task 4 non-blocking coordinator
5. Task 6 PlaybackRuntimeStore
6. Task 5 PluginRuntimeStore
7. Task 7 DownloadRuntimeStore
8. Task 8 RouteSeedRuntimeStore
9. Task 9 SearchSessionStore
10. Task 10 DetailSessionStore
11. Task 11 runtime acceptance tests
12. Task 12 final guard and review

This order gives playback and startup the earliest safety net, then moves into higher payload UI stores after the shared persistence and logging contract is proven.

## Parallelization Notes

After Tasks 1-4 land, these task groups can run in parallel with disjoint write scopes:

- Playback: Task 6 (`player/`, `app/bootstrap`)
- Plugin: Task 5 (`plugin/`)
- Download: Task 7 (`downloader/`)
- Route seed and detail: Tasks 8 and 10 (`feature/home/`), but do not run them concurrently unless write ownership is split by files.
- Search: Task 9 (`feature/search/`)

Do not run Task 3 in parallel with other Room schema edits. Do not run Tasks 8 and 10 in parallel if both edit the same detail ViewModels.

## Final Acceptance

The implementation is complete only when:

- `RuntimeStore` / `SnapshotStore` contracts exist and are used by all mandatory stores.
- Plugin, playback, download, route seed, search, and detail runtime states have restore/persist tests.
- Cold-start restore is non-blocking and parallel for independent stores.
- Search/detail large payload restore is lazy.
- Activity recreate coverage proves key UI state does not return to blank.
- Logs provide enough evidence to diagnose restore, persist, stale, skipped, and failed paths from a feedback zip.
- Debug build passes.

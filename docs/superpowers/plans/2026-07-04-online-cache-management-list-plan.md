# Online Cache Management List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current manual platform / song ID cache-management form with a searchable, filterable在线播放缓存 list that lets users clear online playback cache by song or quality without deleting downloads or local music.

**Architecture:** Add a read-only cache catalog in `:data` that merges `media_cache` rows with `byte_cache_status` and lightweight display metadata. Keep cache mutation in `MediaCacheRepository` / `SettingsCacheCleaner`, and make `feature:settings` a thin UI + ViewModel adapter over those data contracts.

**Tech Stack:** Kotlin, Room, Hilt, Coroutines `StateFlow`, Jetpack Compose Material3, Media3 SimpleCache via existing `SimpleCacheHolder`, `MfLog` / `logUiClick`.

---

## File Structure

- Modify `core/src/main/java/com/hank/musicfree/core/cache/ByteCacheModels.kt`: add list methods with safe default implementations to `ByteCacheStatusStore`.
- Modify `data/src/main/java/com/hank/musicfree/data/db/entity/MediaCacheEntity.kt`: add nullable display metadata columns.
- Modify `data/src/main/java/com/hank/musicfree/data/db/dao/MediaCacheDao.kt`: add catalog row query with display fallbacks.
- Modify `data/src/main/java/com/hank/musicfree/data/db/dao/ByteCacheStatusDao.kt`: add list queries for all statuses and one song.
- Modify `data/src/main/java/com/hank/musicfree/data/repository/RoomByteCacheStatusStore.kt`: implement status list methods.
- Modify `data/src/main/java/com/hank/musicfree/data/repository/MediaCacheRepository.kt`: persist display metadata, expose catalog models, and return freed-byte estimates for targeted clears.
- Create `data/src/main/java/com/hank/musicfree/data/db/migration/Migration15To16.kt`: add nullable display columns.
- Modify `data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt`: bump DB version to 16.
- Modify `data/src/main/java/com/hank/musicfree/data/di/DataModule.kt`: register `MIGRATION_15_16`.
- Create `data/src/androidTest/java/com/hank/musicfree/data/db/AppDatabaseMigration15To16Test.kt`: migration contract.
- Modify `data/src/test/java/com/hank/musicfree/data/repository/MediaCacheRepositoryTest.kt`: display metadata, catalog, and clear boundary tests.
- Modify `feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsCacheCleaner.kt`: add online-cache clear APIs that do not clear local playback associations.
- Rewrite `feature/settings/src/main/java/com/hank/musicfree/feature/settings/cachemanagement/CacheManagementViewModel.kt`: load, search, filter, select, and clear catalog rows.
- Rewrite `feature/settings/src/main/java/com/hank/musicfree/feature/settings/cachemanagement/CacheManagementScreen.kt`: replace form with `LazyColumn` list and detail/confirm sheets.
- Modify `feature/settings/src/main/java/com/hank/musicfree/feature/settings/BasicSettingsContent.kt`: change trailing text from `按歌曲清理` to `查看列表`.
- Modify `feature/settings/src/test/java/com/hank/musicfree/feature/settings/cachemanagement/CacheManagementViewModelTest.kt`: ViewModel behavior.
- Create `feature/settings/src/test/java/com/hank/musicfree/feature/settings/cachemanagement/CacheManagementScreenTest.kt`: Compose UI behavior.
- Modify `feature/settings/src/test/java/com/hank/musicfree/feature/settings/SettingsCacheCleanerTest.kt`: clear APIs and local-association boundary.
- Modify `feature/settings/src/test/java/com/hank/musicfree/feature/settings/BasicSettingsContentTest.kt`: settings entry trailing text.

---

### Task 1: Data Schema And Byte Cache Listing

**Files:**
- Modify: `core/src/main/java/com/hank/musicfree/core/cache/ByteCacheModels.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/db/entity/MediaCacheEntity.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/db/dao/ByteCacheStatusDao.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/repository/RoomByteCacheStatusStore.kt`
- Create: `data/src/main/java/com/hank/musicfree/data/db/migration/Migration15To16.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/di/DataModule.kt`
- Create: `data/src/androidTest/java/com/hank/musicfree/data/db/AppDatabaseMigration15To16Test.kt`

- [ ] **Step 1: Write the migration test**

Create `data/src/androidTest/java/com/hank/musicfree/data/db/AppDatabaseMigration15To16Test.kt`:

```kotlin
package com.hank.musicfree.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hank.musicfree.data.db.migration.MIGRATION_15_16
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration15To16Test {
    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        databaseClass = AppDatabase::class.java,
        specs = emptyList(),
        openFactory = FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationAddsNullableMediaCacheDisplayColumnsAndKeepsRows() {
        helper.createDatabase(TEST_DB, 15).use { db ->
            db.execSQL(
                """
                INSERT INTO media_cache(platform, id, sourcesJson, updated_at)
                VALUES ('kg', '1', '{"STANDARD":{"url":"https://example.test/a.mp3"}}', 100)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 16, true, MIGRATION_15_16).use { db ->
            db.query("PRAGMA table_info(media_cache)").use { cursor ->
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
                }
                assertTrue("title column missing", "title" in columns)
                assertTrue("artist column missing", "artist" in columns)
                assertTrue("album column missing", "album" in columns)
                assertTrue("artwork column missing", "artwork" in columns)
                assertTrue("duration_ms column missing", "duration_ms" in columns)
            }
            db.query("SELECT platform, id, title, artist, duration_ms FROM media_cache").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("kg", cursor.getString(0))
                assertEquals("1", cursor.getString(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
                assertTrue(cursor.isNull(4))
            }
        }
    }

    companion object {
        private const val TEST_DB = "migration-15-16-display-cache.db"
    }
}
```

- [ ] **Step 2: Run the migration test and verify it fails**

Run:

```bash
./gradlew :data:connectedDebugAndroidTest --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.class=com.hank.musicfree.data.db.AppDatabaseMigration15To16Test
```

Expected: FAIL because `MIGRATION_15_16` and version 16 schema do not exist.

- [ ] **Step 3: Add DB columns, migration, version bump, and migration registration**

Modify `data/src/main/java/com/hank/musicfree/data/db/entity/MediaCacheEntity.kt` to:

```kotlin
package com.hank.musicfree.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "media_cache",
    primaryKeys = ["platform", "id"],
    indices = [Index("updated_at")],
)
data class MediaCacheEntity(
    val platform: String,
    val id: String,
    val sourcesJson: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artwork: String? = null,
    @ColumnInfo(name = "duration_ms") val durationMs: Long? = null,
)
```

Create `data/src/main/java/com/hank/musicfree/data/db/migration/Migration15To16.kt`:

```kotlin
package com.hank.musicfree.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `media_cache` ADD COLUMN `title` TEXT")
        db.execSQL("ALTER TABLE `media_cache` ADD COLUMN `artist` TEXT")
        db.execSQL("ALTER TABLE `media_cache` ADD COLUMN `album` TEXT")
        db.execSQL("ALTER TABLE `media_cache` ADD COLUMN `artwork` TEXT")
        db.execSQL("ALTER TABLE `media_cache` ADD COLUMN `duration_ms` INTEGER")
    }
}
```

Modify `data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt`:

```kotlin
version = 16,
```

Modify `data/src/main/java/com/hank/musicfree/data/di/DataModule.kt` imports and migration list:

```kotlin
import com.hank.musicfree.data.db.migration.MIGRATION_15_16
```

```kotlin
.addMigrations(
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
    MIGRATION_14_15,
    MIGRATION_15_16,
)
```

- [ ] **Step 4: Add byte-cache status listing APIs**

Modify `core/src/main/java/com/hank/musicfree/core/cache/ByteCacheModels.kt`:

```kotlin
interface ByteCacheStatusStore {
    suspend fun get(key: ByteCacheKey): ByteCacheStatus?

    suspend fun listAll(): List<ByteCacheStatus> = emptyList()

    suspend fun listBySong(platform: String, musicId: String): List<ByteCacheStatus> = emptyList()

    suspend fun upsert(status: ByteCacheStatus)

    suspend fun markInvalid(
        key: ByteCacheKey,
        reason: ByteCacheInvalidReason,
        updatedAt: Long,
    )

    suspend fun delete(key: ByteCacheKey)

    suspend fun deleteBySong(platform: String, musicId: String)

    suspend fun deleteByPlatform(platform: String) = Unit

    suspend fun deleteAll() = Unit
}
```

Modify `data/src/main/java/com/hank/musicfree/data/db/dao/ByteCacheStatusDao.kt`:

```kotlin
@Query("SELECT * FROM byte_cache_status ORDER BY updated_at DESC")
suspend fun listAll(): List<ByteCacheStatusEntity>

@Query(
    """
    SELECT * FROM byte_cache_status
    WHERE platform = :platform AND music_id = :musicId
    ORDER BY updated_at DESC
    """,
)
suspend fun listBySong(platform: String, musicId: String): List<ByteCacheStatusEntity>
```

Modify `data/src/main/java/com/hank/musicfree/data/repository/RoomByteCacheStatusStore.kt`:

```kotlin
override suspend fun listAll(): List<ByteCacheStatus> =
    dao.listAll().map { it.toModel() }

override suspend fun listBySong(platform: String, musicId: String): List<ByteCacheStatus> =
    dao.listBySong(platform, musicId).map { it.toModel() }
```

- [ ] **Step 5: Run migration and data compile checks**

Run:

```bash
./gradlew :data:compileDebugKotlin --no-daemon
./gradlew :data:connectedDebugAndroidTest --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.class=com.hank.musicfree.data.db.AppDatabaseMigration15To16Test
```

Expected: `compileDebugKotlin` PASS and migration test PASS.

- [ ] **Step 6: Commit Task 1**

```bash
git add core/src/main/java/com/hank/musicfree/core/cache/ByteCacheModels.kt \
  data/src/main/java/com/hank/musicfree/data/db/entity/MediaCacheEntity.kt \
  data/src/main/java/com/hank/musicfree/data/db/dao/ByteCacheStatusDao.kt \
  data/src/main/java/com/hank/musicfree/data/repository/RoomByteCacheStatusStore.kt \
  data/src/main/java/com/hank/musicfree/data/db/migration/Migration15To16.kt \
  data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt \
  data/src/main/java/com/hank/musicfree/data/di/DataModule.kt \
  data/src/androidTest/java/com/hank/musicfree/data/db/AppDatabaseMigration15To16Test.kt
git commit -m "feat(data): 支持歌曲缓存展示元数据迁移"
```

---

### Task 2: Cache Catalog Query And Repository Models

**Files:**
- Modify: `data/src/main/java/com/hank/musicfree/data/db/dao/MediaCacheDao.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/repository/MediaCacheRepository.kt`
- Modify: `data/src/test/java/com/hank/musicfree/data/repository/MediaCacheRepositoryTest.kt`

- [ ] **Step 1: Write failing repository catalog tests**

Append these tests to `MediaCacheRepositoryTest`:

```kotlin
@Test
fun `put persists display metadata for cache catalog`() = runTest {
    val capturedEntity = slot<MediaCacheEntity>()
    val dao: MediaCacheDao = mockk {
        coEvery { get("kg", "1") } returns null
        coEvery { upsert(capture(capturedEntity)) } returns Unit
        coEvery { totalSizeBytes() } returns 0L
        coEvery { count() } returns 1
    }
    MediaCacheRepository(dao) { 123L }.put(
        item.copy(album = "Album", artwork = "https://img.test/a.jpg", duration = 180_000L),
        PlayQuality.STANDARD,
        MediaSourceResult("https://audio.test/a.mp3", null, null, PlayQuality.STANDARD),
    )

    assertEquals("T", capturedEntity.captured.title)
    assertEquals("A", capturedEntity.captured.artist)
    assertEquals("Album", capturedEntity.captured.album)
    assertEquals("https://img.test/a.jpg", capturedEntity.captured.artwork)
    assertEquals(180_000L, capturedEntity.captured.durationMs)
}

@Test
fun `listOnlineCacheCatalog merges source qualities and byte cache statuses`() = runTest {
    val json = """{"STANDARD":{"url":"https://audio.test/a.mp3"},"HIGH":{"url":"https://audio.test/a-h.mp3"}}"""
    val dao: MediaCacheDao = mockk {
        coEvery { listCatalogRows() } returns listOf(
            MediaCacheCatalogRow(
                platform = "kg",
                itemId = "1",
                sourcesJson = json,
                updatedAt = 200L,
                sourceMetadataBytes = json.toByteArray().size.toLong(),
                title = "Song",
                artist = "Singer",
                album = "Album",
                artwork = "https://img.test/a.jpg",
                durationMs = 180_000L,
                libraryTitle = null,
                libraryArtist = null,
                libraryAlbum = null,
                libraryArtwork = null,
                libraryDurationMs = null,
                listenTitle = null,
                listenArtist = null,
                listenAlbum = null,
                listenArtwork = null,
                listenDurationMs = null,
            ),
        )
    }
    val statusStore = RecordingByteCacheStatusStore().apply {
        statuses += ByteCacheStatus(
            key = ByteCacheKey("kg", "1", PlayQuality.STANDARD),
            validity = ByteCacheValidity.PlayableVerified,
            cachedBytes = 1024L,
            contentLength = 1024L,
            validationMethod = ByteCacheValidationMethod.PlaybackCompleted,
            sourceFingerprint = "fp",
            invalidReason = null,
            verifiedAt = 300L,
            updatedAt = 300L,
        )
    }

    val catalog = MediaCacheRepository(
        dao = dao,
        now = { 1L },
        limitProvider = { MediaCacheRepository.DEFAULT_MAX_CACHE_SIZE_BYTES },
        byteCacheStatusStore = statusStore,
    ).listOnlineCacheCatalog()

    assertEquals(1, catalog.size)
    val row = catalog.single()
    assertEquals("Song", row.title)
    assertEquals("Singer", row.artist)
    assertEquals(2, row.qualities.size)
    assertEquals(OnlineCacheQualityStatus.Reusable, row.qualities.first { it.quality == PlayQuality.STANDARD }.status)
    assertEquals(OnlineCacheQualityStatus.SourceOnly, row.qualities.first { it.quality == PlayQuality.HIGH }.status)
    assertEquals(1024L + json.toByteArray().size.toLong(), row.totalBytes)
}

@Test
fun `listOnlineCacheCatalog falls back to unknown title for malformed json`() = runTest {
    val dao: MediaCacheDao = mockk {
        coEvery { listCatalogRows() } returns listOf(
            MediaCacheCatalogRow(
                platform = "kg",
                itemId = "broken",
                sourcesJson = "{bad-json",
                updatedAt = 200L,
                sourceMetadataBytes = 9L,
                title = null,
                artist = null,
                album = null,
                artwork = null,
                durationMs = null,
                libraryTitle = null,
                libraryArtist = null,
                libraryAlbum = null,
                libraryArtwork = null,
                libraryDurationMs = null,
                listenTitle = null,
                listenArtist = null,
                listenAlbum = null,
                listenArtwork = null,
                listenDurationMs = null,
            ),
        )
    }

    val catalog = MediaCacheRepository(dao).listOnlineCacheCatalog()

    assertEquals("未知歌曲", catalog.single().title)
    assertEquals("未知歌手", catalog.single().artist)
    assertEquals(OnlineCacheQualityStatus.Invalid, catalog.single().qualities.single().status)
}
```

Also add these mutable fields to `RecordingByteCacheStatusStore`:

```kotlin
val statuses = mutableListOf<ByteCacheStatus>()

override suspend fun listAll(): List<ByteCacheStatus> = statuses

override suspend fun listBySong(platform: String, musicId: String): List<ByteCacheStatus> =
    statuses.filter { it.key.platform == platform && it.key.musicId == musicId }
```

- [ ] **Step 2: Run tests and verify unresolved references**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests '*MediaCacheRepositoryTest' --no-daemon
```

Expected: FAIL with unresolved references for `MediaCacheCatalogRow`, `OnlineCacheQualityStatus`, and `listOnlineCacheCatalog`.

- [ ] **Step 3: Add DAO catalog row query**

Modify `MediaCacheDao.kt` by adding this row type above the DAO:

```kotlin
data class MediaCacheCatalogRow(
    val platform: String,
    val itemId: String,
    val sourcesJson: String,
    val updatedAt: Long,
    val sourceMetadataBytes: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val artwork: String?,
    val durationMs: Long?,
    val libraryTitle: String?,
    val libraryArtist: String?,
    val libraryAlbum: String?,
    val libraryArtwork: String?,
    val libraryDurationMs: Long?,
    val listenTitle: String?,
    val listenArtist: String?,
    val listenAlbum: String?,
    val listenArtwork: String?,
    val listenDurationMs: Long?,
)
```

Add this DAO method:

```kotlin
@Query(
    """
    SELECT
        mc.platform AS platform,
        mc.id AS itemId,
        mc.sourcesJson AS sourcesJson,
        mc.updated_at AS updatedAt,
        length(CAST(mc.sourcesJson AS BLOB)) AS sourceMetadataBytes,
        mc.title AS title,
        mc.artist AS artist,
        mc.album AS album,
        mc.artwork AS artwork,
        mc.duration_ms AS durationMs,
        mi.title AS libraryTitle,
        mi.artist AS libraryArtist,
        mi.album AS libraryAlbum,
        mi.artwork AS libraryArtwork,
        mi.duration AS libraryDurationMs,
        (
            SELECT le.title FROM listen_event le
            WHERE le.platform = mc.platform AND le.musicId = mc.id
            ORDER BY le.playedAtMs DESC, le.id DESC
            LIMIT 1
        ) AS listenTitle,
        (
            SELECT le.artistRaw FROM listen_event le
            WHERE le.platform = mc.platform AND le.musicId = mc.id
            ORDER BY le.playedAtMs DESC, le.id DESC
            LIMIT 1
        ) AS listenArtist,
        (
            SELECT le.album FROM listen_event le
            WHERE le.platform = mc.platform AND le.musicId = mc.id
            ORDER BY le.playedAtMs DESC, le.id DESC
            LIMIT 1
        ) AS listenAlbum,
        (
            SELECT le.artwork FROM listen_event le
            WHERE le.platform = mc.platform AND le.musicId = mc.id
            ORDER BY le.playedAtMs DESC, le.id DESC
            LIMIT 1
        ) AS listenArtwork,
        (
            SELECT le.durationMs FROM listen_event le
            WHERE le.platform = mc.platform AND le.musicId = mc.id
            ORDER BY le.playedAtMs DESC, le.id DESC
            LIMIT 1
        ) AS listenDurationMs
    FROM media_cache mc
    LEFT JOIN music_items mi ON mi.platform = mc.platform AND mi.id = mc.id
    ORDER BY mc.updated_at DESC
    """,
)
suspend fun listCatalogRows(): List<MediaCacheCatalogRow>
```

- [ ] **Step 4: Add repository catalog models and mapping**

Add to `MediaCacheRepository.kt` near `CachedSource`:

```kotlin
data class OnlineCacheSongRow(
    val platform: String,
    val itemId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artwork: String?,
    val durationMs: Long?,
    val updatedAt: Long,
    val sourceMetadataBytes: Long,
    val totalBytes: Long,
    val qualities: List<OnlineCacheQualityRow>,
)

data class OnlineCacheQualityRow(
    val quality: PlayQuality?,
    val status: OnlineCacheQualityStatus,
    val cachedBytes: Long,
    val contentLength: Long?,
    val updatedAt: Long,
    val invalidReason: ByteCacheInvalidReason?,
)

enum class OnlineCacheQualityStatus {
    Reusable,
    Complete,
    Partial,
    SourceOnly,
    Invalid,
}
```

Modify `put()` upsert to include display fields:

```kotlin
dao.upsert(
    MediaCacheEntity(
        platform = item.platform,
        id = item.id,
        sourcesJson = json.toString(),
        updatedAt = now(),
        title = item.title,
        artist = item.artist,
        album = item.album,
        artwork = item.artwork,
        durationMs = item.duration,
    ),
)
```

Add:

```kotlin
suspend fun listOnlineCacheCatalog(): List<OnlineCacheSongRow> = mutex.withLock {
    val statusesBySong = byteCacheStatusStore.listAll().groupBy {
        it.key.platform to it.key.musicId
    }
    dao.listCatalogRows().map { row ->
        row.toOnlineCacheSongRow(statusesBySong[row.platform to row.itemId].orEmpty())
    }
}

private fun MediaCacheCatalogRow.toOnlineCacheSongRow(
    statuses: List<ByteCacheStatus>,
): OnlineCacheSongRow {
    val sourceQualities = parseQualityNames(sourcesJson)
    val statusByQuality = statuses.associateBy { it.key.quality }
    val qualities = when {
        sourceQualities == null -> listOf(
            OnlineCacheQualityRow(
                quality = null,
                status = OnlineCacheQualityStatus.Invalid,
                cachedBytes = 0L,
                contentLength = null,
                updatedAt = updatedAt,
                invalidReason = null,
            ),
        )
        else -> (sourceQualities + statusByQuality.keys).distinct().map { quality ->
            statusByQuality[quality]?.toOnlineCacheQualityRow()
                ?: OnlineCacheQualityRow(
                    quality = quality,
                    status = OnlineCacheQualityStatus.SourceOnly,
                    cachedBytes = 0L,
                    contentLength = null,
                    updatedAt = updatedAt,
                    invalidReason = null,
                )
        }.sortedBy { it.quality?.ordinal ?: Int.MAX_VALUE }
    }
    val displayTitle = firstNonBlank(title, libraryTitle, listenTitle) ?: "未知歌曲"
    val displayArtist = firstNonBlank(artist, libraryArtist, listenArtist) ?: "未知歌手"
    val bytes = sourceMetadataBytes + qualities.sumOf { it.cachedBytes }
    return OnlineCacheSongRow(
        platform = platform,
        itemId = itemId,
        title = displayTitle,
        artist = displayArtist,
        album = firstNonBlank(album, libraryAlbum, listenAlbum),
        artwork = firstNonBlank(artwork, libraryArtwork, listenArtwork),
        durationMs = durationMs ?: libraryDurationMs ?: listenDurationMs,
        updatedAt = listOf(updatedAt, qualities.maxOfOrNull { it.updatedAt } ?: 0L).max(),
        sourceMetadataBytes = sourceMetadataBytes,
        totalBytes = bytes,
        qualities = qualities,
    )
}

private fun ByteCacheStatus.toOnlineCacheQualityRow(): OnlineCacheQualityRow =
    OnlineCacheQualityRow(
        quality = key.quality,
        status = when (validity) {
            ByteCacheValidity.PlayableVerified -> OnlineCacheQualityStatus.Reusable
            ByteCacheValidity.Complete -> OnlineCacheQualityStatus.Complete
            ByteCacheValidity.Partial, ByteCacheValidity.None -> OnlineCacheQualityStatus.Partial
            ByteCacheValidity.StaleOrInvalid -> OnlineCacheQualityStatus.Invalid
        },
        cachedBytes = cachedBytes,
        contentLength = contentLength,
        updatedAt = updatedAt,
        invalidReason = invalidReason,
    )

private fun parseQualityNames(sourcesJson: String): List<PlayQuality>? = try {
    val obj = JSONObject(sourcesJson)
    obj.keys().asSequence().mapNotNull { key ->
        PlayQuality.values().firstOrNull { it.name == key }
    }.toList()
} catch (error: Throwable) {
    null
}

private fun firstNonBlank(vararg values: String?): String? =
    values.firstOrNull { !it.isNullOrBlank() }?.trim()
```

Import `MediaCacheCatalogRow`.

- [ ] **Step 5: Run data tests**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests '*MediaCacheRepositoryTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit Task 2**

```bash
git add data/src/main/java/com/hank/musicfree/data/db/dao/MediaCacheDao.kt \
  data/src/main/java/com/hank/musicfree/data/repository/MediaCacheRepository.kt \
  data/src/test/java/com/hank/musicfree/data/repository/MediaCacheRepositoryTest.kt
git commit -m "feat(data): 提供在线播放缓存目录"
```

---

### Task 3: Online Cache Cleaner And ViewModel

**Files:**
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsCacheCleaner.kt`
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/cachemanagement/CacheManagementViewModel.kt`
- Modify: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/SettingsCacheCleanerTest.kt`
- Modify: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/cachemanagement/CacheManagementViewModelTest.kt`

- [ ] **Step 1: Write failing cleaner tests**

Add to `SettingsCacheCleanerTest`:

```kotlin
@Test
fun `clearOnlineSongCache with quality does not clear local playback association`() = runTest {
    val mediaCacheRepository = mockk<MediaCacheRepository>()
    coEvery { mediaCacheRepository.deleteEntry("kg", "1", PlayQuality.STANDARD) } returns Unit
    val musicRepository = mockk<MusicRepository>(relaxed = true)
    val cleaner = SettingsCacheCleaner(
        mediaCacheRepository = mediaCacheRepository,
        simpleCacheHolder = mockk(relaxed = true),
        playCacheTelemetry = makeNoOpTelemetry(),
        lyricRepository = mockk(relaxed = true),
        musicRepository = musicRepository,
        context = ctx,
    )

    val result = cleaner.clearOnlineSongCache("kg", "1", PlayQuality.STANDARD)

    assertEquals("quality", result.scope)
    assertEquals("kg", result.platform)
    assertEquals("1", result.itemId)
    assertEquals(PlayQuality.STANDARD, result.quality)
    coVerify { mediaCacheRepository.deleteEntry("kg", "1", PlayQuality.STANDARD) }
    coVerify(exactly = 0) { musicRepository.clearLocalPlaybackAssociation(any(), any()) }
}

@Test
fun `clearOnlineSongCache without quality does not clear local playback association`() = runTest {
    val mediaCacheRepository = mockk<MediaCacheRepository>()
    coEvery { mediaCacheRepository.deleteItem("kg", "1") } returns Unit
    val musicRepository = mockk<MusicRepository>(relaxed = true)
    val cleaner = SettingsCacheCleaner(
        mediaCacheRepository = mediaCacheRepository,
        simpleCacheHolder = mockk(relaxed = true),
        playCacheTelemetry = makeNoOpTelemetry(),
        lyricRepository = mockk(relaxed = true),
        musicRepository = musicRepository,
        context = ctx,
    )

    val result = cleaner.clearOnlineSongCache("kg", "1", null)

    assertEquals("song", result.scope)
    coVerify { mediaCacheRepository.deleteItem("kg", "1") }
    coVerify(exactly = 0) { musicRepository.clearLocalPlaybackAssociation(any(), any()) }
}
```

- [ ] **Step 2: Write failing ViewModel tests**

Replace `CacheManagementViewModelTest` with tests covering load, search, filter, select, and clear:

```kotlin
package com.hank.musicfree.feature.settings.cachemanagement

import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.data.repository.MediaCacheRepository
import com.hank.musicfree.data.repository.OnlineCacheQualityRow
import com.hank.musicfree.data.repository.OnlineCacheQualityStatus
import com.hank.musicfree.data.repository.OnlineCacheSongRow
import com.hank.musicfree.feature.settings.MainDispatcherRule
import com.hank.musicfree.feature.settings.OnlineCacheClearResult
import com.hank.musicfree.feature.settings.SettingsCacheCleaner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CacheManagementViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial load exposes cache rows and summary`() = runTest {
        val repo = mockRepo(rows = listOf(row("kg", "1", "Song", OnlineCacheQualityStatus.Reusable)))
        val vm = CacheManagementViewModel(repo, mockk(relaxed = true))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals(1, vm.uiState.value.visibleRows.size)
        assertEquals("Song", vm.uiState.value.visibleRows.single().title)
        assertEquals(1, vm.uiState.value.summary.songCount)
        assertEquals(1, vm.uiState.value.summary.reusableCount)
    }

    @Test
    fun `search filters by title artist and platform`() = runTest {
        val repo = mockRepo(rows = listOf(
            row("kg", "1", "Blue Song", OnlineCacheQualityStatus.Reusable),
            row("qq", "2", "Red Song", OnlineCacheQualityStatus.Partial),
        ))
        val vm = CacheManagementViewModel(repo, mockk(relaxed = true))
        advanceUntilIdle()

        vm.onSearchQueryChange("blue")
        assertEquals(listOf("Blue Song"), vm.uiState.value.visibleRows.map { it.title })

        vm.onSearchQueryChange("qq")
        assertEquals(listOf("Red Song"), vm.uiState.value.visibleRows.map { it.title })
    }

    @Test
    fun `status filter shows reusable rows only`() = runTest {
        val repo = mockRepo(rows = listOf(
            row("kg", "1", "Reusable", OnlineCacheQualityStatus.Reusable),
            row("kg", "2", "Partial", OnlineCacheQualityStatus.Partial),
        ))
        val vm = CacheManagementViewModel(repo, mockk(relaxed = true))
        advanceUntilIdle()

        vm.onFilterChange(CacheManagementFilter.Reusable)

        assertEquals(listOf("Reusable"), vm.uiState.value.visibleRows.map { it.title })
    }

    @Test
    fun `clear quality refreshes rows and message`() = runTest {
        val repo = mockRepo(rows = listOf(row("kg", "1", "Song", OnlineCacheQualityStatus.Reusable)))
        val cleaner = mockk<SettingsCacheCleaner>()
        coEvery { cleaner.clearOnlineSongCache("kg", "1", PlayQuality.STANDARD) } returns
            OnlineCacheClearResult("quality", "kg", "1", PlayQuality.STANDARD, 0L, 8L)
        val vm = CacheManagementViewModel(repo, cleaner)
        advanceUntilIdle()

        vm.selectRow(vm.uiState.value.visibleRows.single())
        vm.clearSelectedQuality(PlayQuality.STANDARD)
        advanceUntilIdle()

        assertNull(vm.uiState.value.selectedRow)
        assertEquals("已清理 Song 的标准音质在线播放缓存", vm.uiState.value.message)
        coVerify { cleaner.clearOnlineSongCache("kg", "1", PlayQuality.STANDARD) }
        coVerify(atLeast = 2) { repo.listOnlineCacheCatalog() }
    }

    private fun mockRepo(rows: List<OnlineCacheSongRow>): MediaCacheRepository = mockk {
        coEvery { listOnlineCacheCatalog() } returns rows
    }

    private fun row(
        platform: String,
        itemId: String,
        title: String,
        status: OnlineCacheQualityStatus,
    ) = OnlineCacheSongRow(
        platform = platform,
        itemId = itemId,
        title = title,
        artist = "Singer",
        album = null,
        artwork = null,
        durationMs = 180_000L,
        updatedAt = 100L,
        sourceMetadataBytes = 10L,
        totalBytes = 1034L,
        qualities = listOf(
            OnlineCacheQualityRow(
                quality = PlayQuality.STANDARD,
                status = status,
                cachedBytes = 1024L,
                contentLength = 1024L,
                updatedAt = 100L,
                invalidReason = null,
            ),
        ),
    )
}
```

- [ ] **Step 3: Run tests and verify they fail**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests '*SettingsCacheCleanerTest' --tests '*CacheManagementViewModelTest' --no-daemon
```

Expected: FAIL with unresolved references for `OnlineCacheClearResult`, `clearOnlineSongCache`, new ViewModel constructor, filters, and state fields.

- [ ] **Step 4: Implement cleaner APIs**

Add to `SettingsCacheCleaner.kt`:

```kotlin
data class OnlineCacheClearResult(
    val scope: String,
    val platform: String?,
    val itemId: String?,
    val quality: PlayQuality?,
    val freedBytes: Long,
    val durationMs: Long,
)

suspend fun clearOnlineSongCache(
    platform: String,
    itemId: String,
    quality: PlayQuality?,
): OnlineCacheClearResult {
    val sanitizedPlatform = platform.trim()
    val sanitizedItemId = itemId.trim()
    require(sanitizedPlatform.isNotEmpty()) { "platform is required" }
    require(sanitizedItemId.isNotEmpty()) { "itemId is required" }
    val startedAt = System.nanoTime()
    return try {
        if (quality == null) {
            mediaCacheRepository.deleteItem(sanitizedPlatform, sanitizedItemId)
        } else {
            mediaCacheRepository.deleteEntry(sanitizedPlatform, sanitizedItemId, quality)
        }
        val result = OnlineCacheClearResult(
            scope = if (quality == null) "song" else "quality",
            platform = sanitizedPlatform,
            itemId = sanitizedItemId,
            quality = quality,
            freedBytes = 0L,
            durationMs = elapsedMs(startedAt),
        )
        logOnlineCacheClear(result, LogFields.Result.SUCCESS, null, null)
        result
    } catch (error: CancellationException) {
        val result = OnlineCacheClearResult(
            scope = if (quality == null) "song" else "quality",
            platform = sanitizedPlatform,
            itemId = sanitizedItemId,
            quality = quality,
            freedBytes = 0L,
            durationMs = elapsedMs(startedAt),
        )
        logOnlineCacheClear(result, LogFields.Result.CANCELLED, LogFields.Reason.CANCELLED, null)
        throw error
    } catch (error: Throwable) {
        val result = OnlineCacheClearResult(
            scope = if (quality == null) "song" else "quality",
            platform = sanitizedPlatform,
            itemId = sanitizedItemId,
            quality = quality,
            freedBytes = 0L,
            durationMs = elapsedMs(startedAt),
        )
        logOnlineCacheClear(result, LogFields.Result.FAILURE, "exception", error)
        throw error
    }
}

suspend fun clearAllOnlinePlaybackCache(): OnlineCacheClearResult {
    val startedAt = System.nanoTime()
    return try {
        val before = simpleCacheHolder.usedBytes() + mediaCacheRepository.estimatedBytes()
        clearAudioFileCache()
        clearMediaUrlMetadataCache()
        val after = simpleCacheHolder.usedBytes() + mediaCacheRepository.estimatedBytes()
        val result = OnlineCacheClearResult(
            scope = "all",
            platform = null,
            itemId = null,
            quality = null,
            freedBytes = (before - after).coerceAtLeast(0L),
            durationMs = elapsedMs(startedAt),
        )
        logOnlineCacheClear(result, LogFields.Result.SUCCESS, null, null)
        result
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        val result = OnlineCacheClearResult("all", null, null, null, 0L, elapsedMs(startedAt))
        logOnlineCacheClear(result, LogFields.Result.FAILURE, "exception", error)
        throw error
    }
}

private fun logOnlineCacheClear(
    result: OnlineCacheClearResult,
    logResult: String,
    reason: String?,
    throwable: Throwable?,
) {
    val fields = mapOf(
        "scope" to result.scope,
        "platform" to result.platform,
        "itemId" to result.itemId,
        "quality" to result.quality?.name?.lowercase(),
        "freedBytes" to result.freedBytes,
        "durationMs" to result.durationMs,
        "result" to logResult,
        "reason" to reason,
    )
    if (throwable == null) {
        MfLog.detail(LogCategory.DATA, "settings_online_cache_clear", fields)
    } else {
        MfLog.error(LogCategory.DATA, "settings_online_cache_clear", throwable, fields)
    }
}
```

Add imports:

```kotlin
import com.hank.musicfree.core.model.PlayQuality
```

- [ ] **Step 5: Implement ViewModel state and operations**

Replace `CacheManagementViewModel.kt` with:

```kotlin
package com.hank.musicfree.feature.settings.cachemanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.data.repository.MediaCacheRepository
import com.hank.musicfree.data.repository.OnlineCacheQualityStatus
import com.hank.musicfree.data.repository.OnlineCacheSongRow
import com.hank.musicfree.feature.settings.SettingsCacheCleaner
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CacheManagementFilter { All, Reusable, Partial, SourceOnly, Invalid }

data class CacheManagementSummary(
    val songCount: Int = 0,
    val qualityCount: Int = 0,
    val reusableCount: Int = 0,
    val totalBytes: Long = 0L,
)

data class CacheManagementUiState(
    val isLoading: Boolean = true,
    val isClearing: Boolean = false,
    val query: String = "",
    val filter: CacheManagementFilter = CacheManagementFilter.All,
    val allRows: List<OnlineCacheSongRow> = emptyList(),
    val visibleRows: List<OnlineCacheSongRow> = emptyList(),
    val selectedRow: OnlineCacheSongRow? = null,
    val summary: CacheManagementSummary = CacheManagementSummary(),
    val message: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class CacheManagementViewModel @Inject constructor(
    private val mediaCacheRepository: MediaCacheRepository,
    private val cacheCleaner: SettingsCacheCleaner,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CacheManagementUiState())
    val uiState: StateFlow<CacheManagementUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val startedAt = System.nanoTime()
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val rows = mediaCacheRepository.listOnlineCacheCatalog()
                MfLog.detail(
                    LogCategory.DATA,
                    "settings_online_cache_load",
                    mapOf(
                        "count" to rows.size,
                        "qualityCount" to rows.sumOf { it.qualities.size },
                        "durationMs" to elapsedMs(startedAt),
                        "result" to LogFields.Result.SUCCESS,
                    ),
                )
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        allRows = rows,
                        visibleRows = applyFilter(rows, current.query, current.filter),
                        summary = rows.summary(),
                    )
                }
            } catch (error: CancellationException) {
                _uiState.update { it.copy(isLoading = false) }
                throw error
            } catch (error: Throwable) {
                MfLog.error(
                    LogCategory.DATA,
                    "settings_online_cache_load",
                    error,
                    mapOf("durationMs" to elapsedMs(startedAt), "result" to LogFields.Result.FAILURE),
                )
                _uiState.update { it.copy(isLoading = false, errorMessage = "缓存列表加载失败") }
            }
        }
    }

    fun onSearchQueryChange(value: String) = _uiState.update {
        it.copy(query = value, visibleRows = applyFilter(it.allRows, value, it.filter))
    }

    fun onFilterChange(filter: CacheManagementFilter) = _uiState.update {
        it.copy(filter = filter, visibleRows = applyFilter(it.allRows, it.query, filter))
    }

    fun selectRow(row: OnlineCacheSongRow?) = _uiState.update { it.copy(selectedRow = row) }

    fun clearSelectedQuality(quality: PlayQuality) {
        val row = _uiState.value.selectedRow ?: return
        runClear(row, quality)
    }

    fun clearSelectedSong() {
        val row = _uiState.value.selectedRow ?: return
        runClear(row, null)
    }

    fun clearAll() {
        viewModelScope.launch {
            if (_uiState.value.isClearing) return@launch
            _uiState.update { it.copy(isClearing = true, message = null, errorMessage = null) }
            try {
                cacheCleaner.clearAllOnlinePlaybackCache()
                _uiState.update { it.copy(isClearing = false, selectedRow = null, message = "已清理全部在线播放缓存") }
                refresh()
            } catch (error: CancellationException) {
                _uiState.update { it.copy(isClearing = false) }
                throw error
            } catch (error: Throwable) {
                _uiState.update { it.copy(isClearing = false, errorMessage = "清理失败") }
            }
        }
    }

    private fun runClear(row: OnlineCacheSongRow, quality: PlayQuality?) {
        viewModelScope.launch {
            if (_uiState.value.isClearing) return@launch
            _uiState.update { it.copy(isClearing = true, message = null, errorMessage = null) }
            try {
                cacheCleaner.clearOnlineSongCache(row.platform, row.itemId, quality)
                val message = if (quality == null) {
                    "已清理 ${row.title} 的在线播放缓存"
                } else {
                    "已清理 ${row.title} 的${quality.label()}音质在线播放缓存"
                }
                _uiState.update { it.copy(isClearing = false, selectedRow = null, message = message) }
                refresh()
            } catch (error: CancellationException) {
                _uiState.update { it.copy(isClearing = false) }
                throw error
            } catch (error: Throwable) {
                _uiState.update { it.copy(isClearing = false, errorMessage = "清理失败") }
            }
        }
    }

    private fun applyFilter(
        rows: List<OnlineCacheSongRow>,
        query: String,
        filter: CacheManagementFilter,
    ): List<OnlineCacheSongRow> {
        val normalized = query.trim().lowercase()
        return rows.filter { row ->
            val matchesQuery = normalized.isEmpty() ||
                row.title.lowercase().contains(normalized) ||
                row.artist.lowercase().contains(normalized) ||
                row.platform.lowercase().contains(normalized)
            val matchesFilter = when (filter) {
                CacheManagementFilter.All -> true
                CacheManagementFilter.Reusable -> row.hasStatus(OnlineCacheQualityStatus.Reusable)
                CacheManagementFilter.Partial -> row.hasStatus(OnlineCacheQualityStatus.Partial)
                CacheManagementFilter.SourceOnly -> row.hasStatus(OnlineCacheQualityStatus.SourceOnly)
                CacheManagementFilter.Invalid -> row.hasStatus(OnlineCacheQualityStatus.Invalid)
            }
            matchesQuery && matchesFilter
        }
    }

    private fun OnlineCacheSongRow.hasStatus(status: OnlineCacheQualityStatus): Boolean =
        qualities.any { it.status == status }

    private fun List<OnlineCacheSongRow>.summary(): CacheManagementSummary =
        CacheManagementSummary(
            songCount = size,
            qualityCount = sumOf { it.qualities.size },
            reusableCount = sumOf { row -> row.qualities.count { it.status == OnlineCacheQualityStatus.Reusable } },
            totalBytes = sumOf { it.totalBytes },
        )

    private fun PlayQuality.label(): String = when (this) {
        PlayQuality.STANDARD -> "标准"
        PlayQuality.HIGH -> "高品"
        PlayQuality.SUPER -> "超高"
        PlayQuality.LOSSLESS -> "无损"
    }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
}
```

- [ ] **Step 6: Run ViewModel and cleaner tests**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests '*SettingsCacheCleanerTest' --tests '*CacheManagementViewModelTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Commit Task 3**

```bash
git add feature/settings/src/main/java/com/hank/musicfree/feature/settings/SettingsCacheCleaner.kt \
  feature/settings/src/main/java/com/hank/musicfree/feature/settings/cachemanagement/CacheManagementViewModel.kt \
  feature/settings/src/test/java/com/hank/musicfree/feature/settings/SettingsCacheCleanerTest.kt \
  feature/settings/src/test/java/com/hank/musicfree/feature/settings/cachemanagement/CacheManagementViewModelTest.kt
git commit -m "feat(settings): 接入在线播放缓存管理状态"
```

---

### Task 4: Cache Management Compose UI

**Files:**
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/cachemanagement/CacheManagementScreen.kt`
- Create: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/cachemanagement/CacheManagementScreenTest.kt`

- [ ] **Step 1: Write failing Compose UI tests**

Create `CacheManagementScreenTest.kt`:

```kotlin
package com.hank.musicfree.feature.settings.cachemanagement

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.data.repository.OnlineCacheQualityRow
import com.hank.musicfree.data.repository.OnlineCacheQualityStatus
import com.hank.musicfree.data.repository.OnlineCacheSongRow
import org.junit.Rule
import org.junit.Test

class CacheManagementScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun listScreenDoesNotShowManualPlatformOrIdInputs() {
        composeRule.setContent {
            CacheManagementContent(
                state = state(rows = listOf(row("Song", OnlineCacheQualityStatus.Reusable))),
                onBack = {},
                onRefresh = {},
                onSearchQueryChange = {},
                onFilterChange = {},
                onSelectRow = {},
                onClearQuality = {},
                onClearSong = {},
                onClearAll = {},
            )
        }

        composeRule.onNodeWithText("平台").assertDoesNotExist()
        composeRule.onNodeWithText("歌曲 ID").assertDoesNotExist()
        composeRule.onNodeWithText("Song").assertIsDisplayed()
        composeRule.onNodeWithText("可复用").assertIsDisplayed()
    }

    @Test
    fun emptyStateIsVisible() {
        composeRule.setContent {
            CacheManagementContent(
                state = state(rows = emptyList()),
                onBack = {},
                onRefresh = {},
                onSearchQueryChange = {},
                onFilterChange = {},
                onSelectRow = {},
                onClearQuality = {},
                onClearSong = {},
                onClearAll = {},
            )
        }

        composeRule.onNodeWithText("暂无在线播放缓存").assertIsDisplayed()
    }

    @Test
    fun rowClickShowsDetailActions() {
        composeRule.setContent {
            CacheManagementContent(
                state = state(
                    rows = listOf(row("Song", OnlineCacheQualityStatus.Reusable)),
                    selected = row("Song", OnlineCacheQualityStatus.Reusable),
                ),
                onBack = {},
                onRefresh = {},
                onSearchQueryChange = {},
                onFilterChange = {},
                onSelectRow = {},
                onClearQuality = {},
                onClearSong = {},
                onClearAll = {},
            )
        }

        composeRule.onNodeWithText("清理该音质").assertIsDisplayed()
        composeRule.onNodeWithText("清理整首歌在线播放缓存").assertIsDisplayed()
    }

    private fun state(
        rows: List<OnlineCacheSongRow>,
        selected: OnlineCacheSongRow? = null,
    ) = CacheManagementUiState(
        isLoading = false,
        allRows = rows,
        visibleRows = rows,
        selectedRow = selected,
        summary = CacheManagementSummary(
            songCount = rows.size,
            qualityCount = rows.sumOf { it.qualities.size },
            reusableCount = rows.sumOf { row -> row.qualities.count { it.status == OnlineCacheQualityStatus.Reusable } },
            totalBytes = rows.sumOf { it.totalBytes },
        ),
    )

    private fun row(title: String, status: OnlineCacheQualityStatus) = OnlineCacheSongRow(
        platform = "kg",
        itemId = "1",
        title = title,
        artist = "Singer",
        album = null,
        artwork = null,
        durationMs = 180_000L,
        updatedAt = 100L,
        sourceMetadataBytes = 10L,
        totalBytes = 1034L,
        qualities = listOf(
            OnlineCacheQualityRow(
                quality = PlayQuality.STANDARD,
                status = status,
                cachedBytes = 1024L,
                contentLength = 1024L,
                updatedAt = 100L,
                invalidReason = null,
            ),
        ),
    )
}
```

- [ ] **Step 2: Run UI tests and verify failure**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests '*CacheManagementScreenTest' --no-daemon
```

Expected: FAIL because `CacheManagementContent` does not exist and old UI still has text fields.

- [ ] **Step 3: Rewrite UI as list content**

In `CacheManagementScreen.kt`, keep the Hilt wrapper and add a stateless content function:

```kotlin
@Composable
fun CacheManagementScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CacheManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CacheManagementContent(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onSelectRow = viewModel::selectRow,
        onClearQuality = viewModel::clearSelectedQuality,
        onClearSong = viewModel::clearSelectedSong,
        onClearAll = viewModel::clearAll,
        modifier = modifier,
    )
}
```

Add the content structure:

```kotlin
@Composable
internal fun CacheManagementContent(
    state: CacheManagementUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (CacheManagementFilter) -> Unit,
    onSelectRow: (OnlineCacheSongRow?) -> Unit,
    onClearQuality: (PlayQuality) -> Unit,
    onClearSong: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmAction by remember { mutableStateOf<ConfirmCacheAction?>(null) }
    MusicFreeScreenScaffold(
        title = "歌曲缓存管理",
        onBack = onBack,
        modifier = modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Screen.CacheManagementRoot)
            .semantics { testTagsAsResourceId = true },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = rpx(24)),
            verticalArrangement = Arrangement.spacedBy(rpx(16)),
        ) {
            item { Spacer(Modifier.height(rpx(16))) }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onSearchQueryChange,
                    label = { Text("搜索歌曲、歌手或来源") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                CacheFilterRow(selected = state.filter, onSelected = onFilterChange)
            }
            item {
                CacheSummaryRow(state.summary)
            }
            item {
                SettingActionRow(
                    title = "清理全部在线播放缓存",
                    enabled = state.visibleRows.isNotEmpty() && !state.isClearing,
                    trailingText = formatBytes(state.summary.totalBytes),
                    onClick = {
                        logUiClick("cache_management.toolbar.clear_all", "cache_management", "清理全部在线播放缓存")
                        confirmAction = ConfirmCacheAction.ClearAll
                    },
                )
            }
            when {
                state.isLoading -> item { Text("加载中", color = MusicFreeTheme.colors.textSecondary) }
                state.errorMessage != null -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(rpx(12))) {
                        Text(state.errorMessage, color = MusicFreeTheme.colors.danger)
                        TextButton(onClick = onRefresh) { Text("重试") }
                    }
                }
                state.visibleRows.isEmpty() -> item {
                    Text(
                        text = if (state.query.isBlank() && state.filter == CacheManagementFilter.All) {
                            "暂无在线播放缓存"
                        } else {
                            "没有匹配的缓存"
                        },
                        color = MusicFreeTheme.colors.textSecondary,
                    )
                }
                else -> items(
                    items = state.visibleRows,
                    key = { "${it.platform}:${it.itemId}" },
                ) { row ->
                    CacheSongRow(row = row, onClick = { onSelectRow(row) })
                }
            }
            item { Spacer(Modifier.height(rpx(16))) }
        }
    }
    CacheDetailSheet(
        row = state.selectedRow,
        isClearing = state.isClearing,
        onDismiss = { onSelectRow(null) },
        onClearQuality = { quality -> confirmAction = ConfirmCacheAction.ClearQuality(quality) },
        onClearSong = { confirmAction = ConfirmCacheAction.ClearSong },
    )
    ConfirmCacheDialog(
        action = confirmAction,
        row = state.selectedRow,
        onDismiss = { confirmAction = null },
        onConfirm = { action ->
            confirmAction = null
            when (action) {
                ConfirmCacheAction.ClearAll -> onClearAll()
                ConfirmCacheAction.ClearSong -> onClearSong()
                is ConfirmCacheAction.ClearQuality -> onClearQuality(action.quality)
            }
        },
    )
}
```

Add helper composables in the same file:

```kotlin
private sealed interface ConfirmCacheAction {
    data object ClearAll : ConfirmCacheAction
    data object ClearSong : ConfirmCacheAction
    data class ClearQuality(val quality: PlayQuality) : ConfirmCacheAction
}

@Composable
private fun CacheSongRow(row: OnlineCacheSongRow, onClick: () -> Unit) {
    SettingSectionCard(row.title) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .loggedClick(
                    targetId = "cache_management.list.song_row",
                    screen = "cache_management",
                    fields = mapOf("platform" to row.platform, "itemId" to row.itemId),
                    onClick = onClick,
                )
                .padding(horizontal = rpx(24), vertical = rpx(16)),
            verticalArrangement = Arrangement.spacedBy(rpx(8)),
        ) {
            Text(row.artist, fontSize = FontSizes.description, color = MusicFreeTheme.colors.textSecondary)
            Text("${row.platform} · ${formatBytes(row.totalBytes)}", fontSize = FontSizes.description)
            Text(row.qualities.joinToString(" / ") { it.status.label() }, fontSize = FontSizes.description)
        }
    }
}

@Composable
private fun CacheFilterRow(
    selected: CacheManagementFilter,
    onSelected: (CacheManagementFilter) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(rpx(8)), modifier = Modifier.fillMaxWidth()) {
        CacheManagementFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = {
                    logUiClick(
                        targetId = "cache_management.filter.${filter.name.lowercase()}",
                        screen = "cache_management",
                        targetLabel = filter.label(),
                    )
                    onSelected(filter)
                },
                label = { Text(filter.label()) },
            )
        }
    }
}

@Composable
private fun CacheSummaryRow(summary: CacheManagementSummary) {
    Text(
        text = "${summary.songCount} 首 · ${summary.qualityCount} 个音质 · 可复用 ${summary.reusableCount} · ${formatBytes(summary.totalBytes)}",
        fontSize = FontSizes.description,
        color = MusicFreeTheme.colors.textSecondary,
    )
}

@Composable
private fun CacheDetailSheet(
    row: OnlineCacheSongRow?,
    isClearing: Boolean,
    onDismiss: () -> Unit,
    onClearQuality: (PlayQuality) -> Unit,
    onClearSong: () -> Unit,
) {
    if (row == null) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rpx(24), vertical = rpx(16)),
            verticalArrangement = Arrangement.spacedBy(rpx(12)),
        ) {
            Text(row.title, fontSize = FontSizes.title)
            Text(row.artist, fontSize = FontSizes.description, color = MusicFreeTheme.colors.textSecondary)
            row.qualities.forEach { quality ->
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(quality.quality?.label().orEmpty())
                    Text("${quality.status.label()} · ${formatBytes(quality.cachedBytes)}")
                }
                quality.quality?.let { q ->
                    TextButton(enabled = !isClearing, onClick = { onClearQuality(q) }) {
                        Text("清理该音质")
                    }
                }
            }
            TextButton(enabled = !isClearing, onClick = onClearSong) {
                Text("清理整首歌在线播放缓存")
            }
        }
    }
}

@Composable
private fun ConfirmCacheDialog(
    action: ConfirmCacheAction?,
    row: OnlineCacheSongRow?,
    onDismiss: () -> Unit,
    onConfirm: (ConfirmCacheAction) -> Unit,
) {
    if (action == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认清理缓存") },
        text = { Text("只会清理在线播放缓存，不会删除已下载歌曲和本地音乐。") },
        confirmButton = {
            TextButton(onClick = { onConfirm(action) }) { Text("清理") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun CacheManagementFilter.label(): String = when (this) {
    CacheManagementFilter.All -> "全部"
    CacheManagementFilter.Reusable -> "可复用"
    CacheManagementFilter.Partial -> "部分缓存"
    CacheManagementFilter.SourceOnly -> "仅解析"
    CacheManagementFilter.Invalid -> "异常"
}

private fun OnlineCacheQualityStatus.label(): String = when (this) {
    OnlineCacheQualityStatus.Reusable -> "可复用"
    OnlineCacheQualityStatus.Complete -> "完整"
    OnlineCacheQualityStatus.Partial -> "部分缓存"
    OnlineCacheQualityStatus.SourceOnly -> "仅解析"
    OnlineCacheQualityStatus.Invalid -> "异常"
}

private fun PlayQuality.label(): String = when (this) {
    PlayQuality.STANDARD -> "标准"
    PlayQuality.HIGH -> "高品"
    PlayQuality.SUPER -> "超高"
    PlayQuality.LOSSLESS -> "无损"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}
```

Use imports from `androidx.compose.foundation.lazy.items`, `ModalBottomSheet`, `FilterChip`, `WindowInsets`, `statusBars`, `windowInsetsPadding`, `loggedClick`, and repository models.

- [ ] **Step 4: Add dialog open/dismiss logs**

In `CacheDetailSheet`, wrap sheet visibility in `LaunchedEffect(row?.platform, row?.itemId)`:

```kotlin
LaunchedEffect(row?.platform, row?.itemId) {
    if (row != null) {
        MfLog.detail(
            LogCategory.UI,
            UiLogEvents.DIALOG_OPEN,
            mapOf(
                UiLogEvents.Fields.DIALOG_ID to "cache_management_detail",
                UiLogEvents.Fields.SCREEN to "cache_management",
                UiLogEvents.Fields.TRIGGER to UiLogEvents.Trigger.UI_CLICK,
            ),
        )
    }
}
```

Change `onDismissRequest = onDismiss` to:

```kotlin
onDismissRequest = {
    MfLog.detail(
        LogCategory.UI,
        UiLogEvents.DIALOG_DISMISS,
        mapOf(
            UiLogEvents.Fields.DIALOG_ID to "cache_management_detail",
            UiLogEvents.Fields.SCREEN to "cache_management",
            UiLogEvents.Fields.OUTCOME to "system",
        ),
    )
    onDismiss()
}
```

- [ ] **Step 5: Run UI tests**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests '*CacheManagementScreenTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit Task 4**

```bash
git add feature/settings/src/main/java/com/hank/musicfree/feature/settings/cachemanagement/CacheManagementScreen.kt \
  feature/settings/src/test/java/com/hank/musicfree/feature/settings/cachemanagement/CacheManagementScreenTest.kt
git commit -m "feat(settings): 展示歌曲缓存管理列表"
```

---

### Task 5: Settings Entry Text And Regression Cleanup

**Files:**
- Modify: `feature/settings/src/main/java/com/hank/musicfree/feature/settings/BasicSettingsContent.kt`
- Modify: `feature/settings/src/test/java/com/hank/musicfree/feature/settings/BasicSettingsContentTest.kt`

- [ ] **Step 1: Write failing settings entry assertion**

In `BasicSettingsContentTest`, update or add:

```kotlin
@Test
fun `cache management entry shows list copy`() {
    setContent()

    scrollToTag(FidelityAnchors.Settings.BasicSectionCache)
    composeRule.onNodeWithText("歌曲缓存管理").assertIsDisplayed()
    composeRule.onNodeWithText("查看列表").assertIsDisplayed()
    composeRule.onNodeWithText("按歌曲清理").assertDoesNotExist()
}
```

Keep the existing `cache section exposes song cache management entry` navigation assertion. This new test only locks the user-visible trailing copy.

- [ ] **Step 2: Run the focused test and verify failure**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests '*BasicSettingsContentTest.cacheManagementEntryShowsListCopy' --no-daemon
```

Expected: FAIL because trailing text is still `按歌曲清理`.

- [ ] **Step 3: Change entry copy**

Modify `BasicSettingsContent.kt`:

```kotlin
SettingActionRow(
    title = "歌曲缓存管理",
    enabled = true,
    testTag = FidelityAnchors.Settings.BasicCacheManagement,
    trailingText = "查看列表",
    onClick = {
        logUiClick("settings.row.cache_management", "settings", "歌曲缓存管理")
        onNavigateToCacheManagement()
    },
)
```

- [ ] **Step 4: Confirm spec still matches implementation**

Read `docs/superpowers/specs/2026-07-04-online-cache-management-list-design.md` section 3.2 and confirm the implementation uses the list-top “清理全部” row. No file edit is needed when it matches.

- [ ] **Step 5: Run settings tests**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests '*BasicSettingsContentTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit Task 5**

```bash
git add feature/settings/src/main/java/com/hank/musicfree/feature/settings/BasicSettingsContent.kt \
  feature/settings/src/test/java/com/hank/musicfree/feature/settings/BasicSettingsContentTest.kt
git commit -m "feat(settings): 更新歌曲缓存管理入口文案"
```

---

### Task 6: Full Verification And Main Integration

**Files:**
- No new source files unless verification exposes a bug.
- Compare and merge from worktree branch `codex/cache-management-list-ui`.

- [ ] **Step 1: Run data tests**

```bash
./gradlew :data:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 2: Run settings tests**

```bash
./gradlew :feature:settings:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 3: Run migration instrumentation test**

```bash
./gradlew :data:connectedDebugAndroidTest --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.class=com.hank.musicfree.data.db.AppDatabaseMigration15To16Test
```

Expected: PASS if a device/emulator is available. If unavailable, record the exact Gradle/device error and keep the test committed.

- [ ] **Step 4: Run dev harness**

```bash
bash scripts/dev-harness/check.sh
```

Expected: PASS.

- [ ] **Step 5: Run Debug build**

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Save branch tree hash**

```bash
branch_tree="$(git rev-parse 'HEAD^{tree}')"
printf '%s\n' "$branch_tree"
```

Expected: prints one tree hash.

- [ ] **Step 7: Squash merge into main checkout**

From the main checkout root:

```bash
git status --short
git merge --squash codex/cache-management-list-ui
git commit -m "feat(settings): 优化歌曲缓存管理列表"
```

Expected: clean squash merge commit on `main`.

- [ ] **Step 8: Compare main tree hash**

```bash
main_tree="$(git rev-parse 'HEAD^{tree}')"
test "$main_tree" = "$branch_tree"
```

Expected: exit code 0. If exit code is non-zero, run `bash scripts/dev-harness/check.sh` and `./gradlew :app:assembleDebug --no-daemon` on `main`.

- [ ] **Step 9: Final status**

```bash
git status --short
```

Expected: no source changes. If untracked build artifacts appear, inspect and remove only generated artifacts created by this task.

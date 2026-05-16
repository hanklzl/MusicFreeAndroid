# Downloaded Local Metadata RN Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Android downloaded tracks match RN semantics: a downloaded track remains the original plugin `MusicItem` with full metadata, plus local downloaded state, and appears in the unified local music library.

**Architecture:** Persist full `MusicItem` seeds in download tasks, then commit downloaded state and the original plugin track into Room after MediaStore write succeeds. Add a repository-level local-library API so local scans and downloaded plugin tracks share one read/removal contract. Keep scanned device files as `platform = local`.

**Tech Stack:** Kotlin, Room, Hilt, Coroutines/Flow, MediaStore, existing `MfLog` structured logging, JUnit/Robolectric/androidTest.

---

## File Structure

- Modify `data/src/main/java/com/hank/musicfree/data/db/converter/Converters.kt`: include `MusicItem.localPath` in JSON round trips.
- Modify `data/src/test/java/com/hank/musicfree/data/db/converter/ConvertersTest.kt`: assert `localPath` survives serialization.
- Modify `data/src/main/java/com/hank/musicfree/data/db/entity/MusicItemEntity.kt`: add nullable `localPath`.
- Modify `data/src/main/java/com/hank/musicfree/data/mapper/MusicItemMapper.kt`: map `localPath` both ways.
- Modify `data/src/main/java/com/hank/musicfree/data/db/entity/DownloadTaskEntity.kt`: add nullable `musicItemJson` seed.
- Modify `data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt`: bump schema version and export the new schema.
- Modify `data/src/main/java/com/hank/musicfree/data/db/dao/MusicDao.kt`: add `observeLocalLibrary(localPlatform)` query.
- Modify `data/src/main/java/com/hank/musicfree/data/repository/MusicRepository.kt`: add `observeLocalLibrary()`, `commitDownloadedTrack()`, and `removeFromLocalLibrary()`.
- Modify `data/src/androidTest/java/com/hank/musicfree/data/repository/MusicRepositoryTest.kt`: cover local library query, commit, and removal semantics.
- Modify `downloader/src/main/java/com/hank/musicfree/downloader/engine/DownloadEngine.kt`: persist full seed and commit downloaded tracks to local library.
- Modify `downloader/src/main/java/com/hank/musicfree/downloader/di/DownloaderModule.kt`: provide new `DownloadEngine` constructor dependencies.
- Modify `downloader/src/test/java/com/hank/musicfree/downloader/engine/*Test.kt`: update constructor fixtures and add preservation tests.
- Modify `feature/home/src/main/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListSourceLoader.kt`: use `MusicRepository.observeLocalLibrary()`.
- Modify `feature/home/src/main/java/com/hank/musicfree/feature/home/local/LocalMusicViewModel.kt`: read/remove via unified local-library API.
- Modify `feature/home/src/main/java/com/hank/musicfree/feature/home/musiclisteditor/MusicListEditorLiteViewModel.kt`: read/remove local-library items through the unified API.
- Modify matching `feature/home/src/test/...` files: update mocks and expected repository calls.

## Task 1: Persist `MusicItem.localPath` And Download Seed Fields

**Files:**
- Modify: `data/src/test/java/com/hank/musicfree/data/db/converter/ConvertersTest.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/db/converter/Converters.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/db/entity/MusicItemEntity.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/mapper/MusicItemMapper.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/db/entity/DownloadTaskEntity.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt`

- [ ] **Step 1: Write the failing converter and mapper assertions**

In `ConvertersTest.musicItemRoundTripPreservesMainFields`, set `localPath` and assert it:

```kotlin
val item = MusicItem(
    id = "1",
    platform = "demo",
    title = "Title",
    artist = "Artist",
    album = "Album",
    duration = 123_000L,
    url = "https://example.test/song.mp3",
    artwork = "https://example.test/art.jpg",
    qualities = mapOf(
        PlayQuality.STANDARD to QualityInfo(
            url = "https://example.test/std.mp3",
            size = 1234L,
        ),
    ),
    raw = mapOf(
        "source" to "plugin",
        "nested" to mapOf("rank" to 1),
        "tags" to listOf("a", "b"),
    ),
    addedAt = 123L,
    localPath = "content://media/external/audio/media/42",
)

assertEquals(item.localPath, restored?.localPath)
```

In `data/src/test/java/com/hank/musicfree/data/mapper/MusicItemMapperTest.kt`, add or extend an existing round-trip test with:

```kotlin
val item = MusicItem(
    id = "local-copy",
    platform = "demo",
    title = "Downloaded",
    artist = "Artist",
    album = "Album",
    duration = 180_000L,
    url = "https://example.test/download.mp3",
    artwork = "https://example.test/cover.jpg",
    qualities = null,
    raw = mapOf("origin" to "plugin"),
    localPath = "content://media/external/audio/media/99",
)

val restored = item.toEntity(converters).toModel(converters)

assertEquals(item.localPath, restored.localPath)
assertEquals(item.raw, restored.raw)
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests '*ConvertersTest' --tests '*MusicItemMapperTest' --no-daemon
```

Expected: FAIL because `localPath` is not serialized or mapped.

- [ ] **Step 3: Implement serialization and entity mapping**

In `Converters.musicItemToJson`, add:

```kotlin
json.put("localPath", item.localPath ?: JSONObject.NULL)
```

In `Converters.jsonToMusicItem`, add the constructor argument:

```kotlin
localPath = if (obj.isNull("localPath")) null else obj.getString("localPath"),
```

In `MusicItemEntity`, add:

```kotlin
val localPath: String? = null,
```

In `MusicItemMapper.toEntity`, pass:

```kotlin
localPath = localPath,
```

In `MusicItemEntity.toModel`, pass:

```kotlin
localPath = localPath,
```

In `DownloadTaskEntity`, add a defaulted seed field at the end to keep test constructors readable:

```kotlin
val musicItemJson: String? = null,
```

In `AppDatabase`, bump:

```kotlin
version = 9,
```

- [ ] **Step 4: Run focused tests**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests '*ConvertersTest' --tests '*MusicItemMapperTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Generate and inspect Room schema**

Run:

```bash
./gradlew :data:compileDebugKotlin --no-daemon
```

Expected: PASS and `data/schemas/com.hank.musicfree.data.db.AppDatabase/9.json` exists. Inspect it and confirm:

```bash
rg -n '"fieldPath": "localPath"|"fieldPath": "musicItemJson"' data/schemas/com.hank.musicfree.data.db.AppDatabase/9.json
```

Expected: both fields are present.

- [ ] **Step 6: Commit**

```bash
git add data/src/main/java/com/hank/musicfree/data/db/converter/Converters.kt \
  data/src/test/java/com/hank/musicfree/data/db/converter/ConvertersTest.kt \
  data/src/test/java/com/hank/musicfree/data/mapper/MusicItemMapperTest.kt \
  data/src/main/java/com/hank/musicfree/data/db/entity/MusicItemEntity.kt \
  data/src/main/java/com/hank/musicfree/data/mapper/MusicItemMapper.kt \
  data/src/main/java/com/hank/musicfree/data/db/entity/DownloadTaskEntity.kt \
  data/src/main/java/com/hank/musicfree/data/db/AppDatabase.kt \
  data/schemas/com.hank.musicfree.data.db.AppDatabase/9.json
git commit -m "feat(data): 持久化下载曲目本地路径和完整 seed"
```

## Task 2: Add Unified Local Library Repository Contract

**Files:**
- Modify: `data/src/main/java/com/hank/musicfree/data/db/dao/MusicDao.kt`
- Modify: `data/src/main/java/com/hank/musicfree/data/repository/MusicRepository.kt`
- Modify: `data/src/androidTest/java/com/hank/musicfree/data/repository/MusicRepositoryTest.kt`

- [ ] **Step 1: Write failing repository tests**

Add imports in `MusicRepositoryTest`:

```kotlin
import com.hank.musicfree.data.db.entity.DownloadedTrackEntity
```

Update setup to pass `AppDatabase` into the repository after the production constructor changes:

```kotlin
repo = MusicRepository(db, db.musicDao(), Converters())
```

Add helpers:

```kotlin
private fun downloadedRow(id: String, platform: String = "demo") = DownloadedTrackEntity(
    id = id,
    platform = platform,
    mediaStoreUri = "content://media/external/audio/media/$id",
    relativePath = "Music/MusicFree/",
    mimeType = "audio/mpeg",
    quality = "standard",
    sizeBytes = 1024L,
    downloadedAt = 123L,
)
```

Add tests:

```kotlin
@Test
fun observeLocalLibraryReturnsScannedLocalAndDownloadedPluginTracks() = runTest {
    val scanned = musicItem("local-1", platform = "local")
    val downloaded = musicItem("plugin-1", platform = "demo").copy(
        album = "Plugin Album",
        artwork = "https://example.test/cover.jpg",
        raw = mapOf("from" to "plugin"),
    )
    val remoteOnly = musicItem("plugin-2", platform = "demo")

    repo.insert(scanned)
    repo.insert(downloaded)
    repo.insert(remoteOnly)
    db.downloadedTrackDao().insert(downloadedRow("plugin-1", "demo"))
    db.downloadedTrackDao().insert(downloadedRow("orphan", "demo"))

    repo.observeLocalLibrary().test {
        val actual = awaitItem()
        assertEquals(listOf("plugin-1@demo", "local-1@local"), actual.map { "${it.id}@${it.platform}" }.sorted())
        assertEquals("https://example.test/cover.jpg", actual.first { it.id == "plugin-1" }.artwork)
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun commitDownloadedTrackWritesDownloadedRowAndFullMusicItem() = runTest {
    val item = musicItem("plugin-1", platform = "demo").copy(
        album = "Album",
        artwork = "https://example.test/art.jpg",
        raw = mapOf("source" to "plugin"),
        localPath = null,
    )
    val row = downloadedRow("plugin-1", "demo")

    repo.commitDownloadedTrack(item, row)

    assertTrue(db.downloadedTrackDao().exists("plugin-1", "demo"))
    val stored = repo.getById("plugin-1", "demo")!!
    assertEquals("Album", stored.album)
    assertEquals("https://example.test/art.jpg", stored.artwork)
    assertEquals(row.mediaStoreUri, stored.localPath)
    assertEquals("plugin", stored.raw["source"])
    assertEquals(true, stored.raw["downloaded"])
    assertEquals("standard", stored.raw["downloadQuality"])
}

@Test
fun removeFromLocalLibraryDeletesScannedLocalButOnlyClearsDownloadedStateForPluginTrack() = runTest {
    val scanned = musicItem("local-1", platform = "local")
    val plugin = musicItem("plugin-1", platform = "demo").copy(localPath = "content://media/external/audio/media/plugin-1")

    repo.insert(scanned)
    repo.commitDownloadedTrack(plugin, downloadedRow("plugin-1", "demo"))

    repo.removeFromLocalLibrary(scanned)
    repo.removeFromLocalLibrary(plugin)

    assertNull(repo.getById("local-1", "local"))
    assertFalse(db.downloadedTrackDao().exists("plugin-1", "demo"))
    assertNull(repo.getById("plugin-1", "demo")!!.localPath)
    assertEquals(emptyList<MusicItem>(), repo.observeLocalLibrary().first())
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./gradlew :data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hank.musicfree.data.repository.MusicRepositoryTest
```

Expected: FAIL at compile time because `observeLocalLibrary`, `commitDownloadedTrack`, and `removeFromLocalLibrary` do not exist.

- [ ] **Step 3: Implement DAO query**

In `MusicDao`, add:

```kotlin
@Query(
    """
    SELECT DISTINCT m.* FROM music_items m
    LEFT JOIN downloaded_tracks d
      ON d.id = m.id AND d.platform = m.platform
    WHERE m.platform = :localPlatform OR d.id IS NOT NULL
    ORDER BY m.title ASC
    """
)
fun observeLocalLibrary(localPlatform: String): Flow<List<MusicItemEntity>>
```

- [ ] **Step 4: Implement repository contract**

Change `MusicRepository` constructor:

```kotlin
class MusicRepository @Inject constructor(
    private val db: AppDatabase,
    private val musicDao: MusicDao,
    private val converters: Converters,
)
```

Add imports:

```kotlin
import androidx.room.withTransaction
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.entity.DownloadedTrackEntity
```

Add methods:

```kotlin
fun observeLocalLibrary(): Flow<List<MusicItem>> =
    musicDao.observeLocalLibrary(LOCAL_PLATFORM).map { entities ->
        entities.map { it.toModel(converters) }
    }

suspend fun commitDownloadedTrack(item: MusicItem, downloaded: DownloadedTrackEntity) =
    logDataWrite(
        operation = "commit_downloaded_track",
        fields = item.logFields() + mapOf(
            "quality" to downloaded.quality,
            "pathType" to "mediastore",
        ),
        resultFields = { mapOf("count" to 1) },
    ) {
        val localItem = item.copy(
            localPath = downloaded.mediaStoreUri,
            raw = item.raw + mapOf(
                "downloaded" to true,
                "downloadQuality" to downloaded.quality,
                "downloadedAt" to downloaded.downloadedAt,
                "mediaStoreUri" to downloaded.mediaStoreUri,
            ),
        )
        db.withTransaction {
            db.downloadedTrackDao().insert(downloaded)
            musicDao.upsert(localItem.toEntity(converters))
        }
    }

suspend fun removeFromLocalLibrary(item: MusicItem) =
    logDataWrite(
        operation = "remove_from_local_library",
        fields = item.logFields(),
        resultFields = { mapOf("count" to 1) },
    ) {
        db.withTransaction {
            if (item.platform == LOCAL_PLATFORM) {
                musicDao.delete(item.toEntity(converters))
            } else {
                db.downloadedTrackDao().deleteByKey(item.id, item.platform)
                val existing = musicDao.getById(item.id, item.platform)?.toModel(converters)
                if (existing != null) {
                    musicDao.update(
                        existing.copy(
                            localPath = null,
                            raw = existing.raw - "downloaded" - "downloadQuality" - "downloadedAt" - "mediaStoreUri",
                        ).toEntity(converters)
                    )
                }
            }
        }
    }

private companion object {
    const val LOCAL_PLATFORM = "local"
}
```

Keep existing `insert`, `replaceByPlatform`, `delete`, and other methods unchanged except for constructor-related test fixture updates.

- [ ] **Step 5: Update manual repository constructors**

Replace manual test construction:

```kotlin
MusicRepository(db.musicDao(), Converters())
```

with:

```kotlin
MusicRepository(db, db.musicDao(), Converters())
```

Use `rg -n "MusicRepository\\(" data feature app downloader plugin player core` to find all direct constructors in tests, then update only files that contain the old two-argument constructor.

- [ ] **Step 6: Run focused data tests**

Run:

```bash
./gradlew :data:testDebugUnitTest :data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hank.musicfree.data.repository.MusicRepositoryTest --no-daemon
```

Expected: PASS. If no device is available, run `:data:testDebugUnitTest` and note that androidTest remains pending for later device validation.

- [ ] **Step 7: Commit**

```bash
git add data/src/main/java/com/hank/musicfree/data/db/dao/MusicDao.kt \
  data/src/main/java/com/hank/musicfree/data/repository/MusicRepository.kt \
  data/src/androidTest/java/com/hank/musicfree/data/repository/MusicRepositoryTest.kt \
  $(git diff --name-only | rg 'MusicRepository.*Test|RepositoryTest|RepositoryJvmTest|MapperJvmTest')
git commit -m "feat(data): 统一本地音乐库读取和移除语义"
```

## Task 3: Preserve Full Download Seeds And Commit Downloads To Local Library

**Files:**
- Modify: `downloader/src/main/java/com/hank/musicfree/downloader/engine/DownloadEngine.kt`
- Modify: `downloader/src/main/java/com/hank/musicfree/downloader/di/DownloaderModule.kt`
- Modify: `downloader/src/test/java/com/hank/musicfree/downloader/engine/DownloadEngineSchedulingTest.kt`
- Modify: `downloader/src/test/java/com/hank/musicfree/downloader/engine/DownloadEngineRecoveryTest.kt`
- Modify: other `downloader/src/test/.../DownloadEngine*Test.kt` constructor fixtures if compile reports them.

- [ ] **Step 1: Write failing downloader tests**

In `DownloadEngineSchedulingTest`, add imports:

```kotlin
import com.hank.musicfree.core.model.QualityInfo
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.repository.MusicRepository
```

Add a fixture field:

```kotlin
private lateinit var converters: Converters
private lateinit var musicRepository: MusicRepository
```

Initialize before `engine = DownloadEngine(...)`:

```kotlin
converters = Converters()
musicRepository = MusicRepository(db, db.musicDao(), converters)
```

Pass the new dependencies:

```kotlin
converters = converters,
musicRepository = musicRepository,
```

Add tests:

```kotlin
@Test fun enqueuePersistsFullMusicItemSeedWhenSchedulerIsBlocked() = runTest {
    network.value = NetworkState.Offline
    val item = item("seed", platform = "demo").copy(
        album = "Seed Album",
        artwork = "https://example.test/seed.jpg",
        qualities = mapOf(PlayQuality.HIGH to QualityInfo("https://example.test/high.mp3", 123L)),
        raw = mapOf("pluginPayload" to mapOf("rank" to 7)),
        localPath = "content://old",
    )

    engine.enqueue(listOf(item), PlayQuality.HIGH)
    advanceUntilIdle()

    val row = db.downloadTaskDao().findByKey("seed", "demo")!!
    val restored = converters.jsonToMusicItem(row.musicItemJson)!!
    assertEquals("Seed Album", restored.album)
    assertEquals("https://example.test/seed.jpg", restored.artwork)
    assertEquals(item.qualities, restored.qualities)
    assertEquals(item.raw, restored.raw)
    assertEquals("content://old", restored.localPath)
}

@Test fun completionWritesFullPluginTrackIntoLocalLibrary() = runTest {
    val item = item("1", platform = "demo").copy(
        album = "Plugin Album",
        artwork = "https://example.test/art.jpg",
        qualities = mapOf(PlayQuality.HIGH to QualityInfo("https://example.test/high.mp3", 777L)),
        raw = mapOf("source" to "plugin", "nested" to mapOf("rank" to 1)),
        url = "https://fallback.test/song.mp3",
    )
    resolver.bind(
        MediaKey.of("1", "demo"),
        MediaSourceResult(url = "https://x/1.mp3", headers = null, userAgent = null, quality = PlayQuality.STANDARD),
    )

    engine.enqueue(listOf(item), PlayQuality.STANDARD)
    advanceUntilIdle()

    assertTrue(db.downloadedTrackDao().exists("1", "demo"))
    val stored = musicRepository.getById("1", "demo")!!
    assertEquals("Plugin Album", stored.album)
    assertEquals("https://example.test/art.jpg", stored.artwork)
    assertEquals(item.qualities, stored.qualities)
    assertEquals("plugin", stored.raw["source"])
    assertEquals(true, stored.raw["downloaded"])
    assertEquals("standard", stored.raw["downloadQuality"])
    assertEquals("content://media/external/audio/media/", stored.localPath!!.substringBeforeLast('/') + "/")
}

@Test fun failedDownloadDoesNotWriteLocalLibrary() = runTest {
    resolver.bind(MediaKey.of("fail", "demo"), MediaSourceResult(url = "https://x/fail.mp3", headers = null, userAgent = null, quality = null))
    http.failNext()

    engine.enqueue(listOf(item("fail", platform = "demo")), PlayQuality.STANDARD)
    advanceUntilIdle()

    assertFalse(db.downloadedTrackDao().exists("fail", "demo"))
    assertEquals(null, musicRepository.getById("fail", "demo"))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
./gradlew :downloader:testDebugUnitTest --tests '*DownloadEngineSchedulingTest' --no-daemon
```

Expected: FAIL because `DownloadEngine` does not accept `Converters` or `MusicRepository`, does not save `musicItemJson`, and does not write to `music_items`.

- [ ] **Step 3: Implement constructor dependencies and seed persistence**

In `DownloadEngine`, add constructor dependencies:

```kotlin
private val converters: Converters,
private val musicRepository: MusicRepository,
```

Add imports:

```kotlin
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.repository.MusicRepository
```

In `enqueue`, set:

```kotlin
musicItemJson = converters.musicItemToJson(item),
```

Replace the private extension with a member function:

```kotlin
private fun DownloadTaskEntity.toMusicItemSeed(): MusicItem =
    converters.jsonToMusicItem(musicItemJson) ?: MusicItem(
        id = id,
        platform = platform,
        title = title,
        artist = artist,
        album = album,
        duration = durationMs,
        url = seedUrl,
        artwork = artwork,
        qualities = null,
    )
```

Remove the old file-level `private fun DownloadTaskEntity.toMusicItemSeed()` at the bottom.

- [ ] **Step 4: Commit downloaded track and original music item together**

In `runOne`, replace the downloaded insert block:

```kotlin
downloadedDao.insert(
    DownloadedTrackEntity(
        id = task.id, platform = task.platform,
        mediaStoreUri = uri.toString(), relativePath = relPath,
        mimeType = mime, quality = task.targetQuality,
        sizeBytes = size, downloadedAt = System.currentTimeMillis(),
    ),
)
taskDao.deleteByKey(task.id, task.platform)
```

with:

```kotlin
val downloadedAt = System.currentTimeMillis()
val downloaded = DownloadedTrackEntity(
    id = task.id,
    platform = task.platform,
    mediaStoreUri = uri.toString(),
    relativePath = relPath,
    mimeType = mime,
    quality = task.targetQuality,
    sizeBytes = size,
    downloadedAt = downloadedAt,
)
musicRepository.commitDownloadedTrack(musicItem, downloaded)
taskDao.deleteByKey(task.id, task.platform)
```

Keep this inside `withContext(NonCancellable)` so the database commit is not cancelled after MediaStore write succeeds.

- [ ] **Step 5: Wire DI and all test fixtures**

In `DownloaderModule.provideEngine`, add parameters:

```kotlin
converters: Converters,
musicRepository: MusicRepository,
```

and pass:

```kotlin
converters = converters,
musicRepository = musicRepository,
```

Add imports:

```kotlin
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.repository.MusicRepository
```

For every `DownloadEngine(...)` test fixture, pass:

```kotlin
converters = Converters(),
musicRepository = MusicRepository(db, db.musicDao(), Converters()),
```

Prefer a single `val converters = Converters()` in each fixture so the engine and repository use the same converter instance.

- [ ] **Step 6: Run downloader tests**

Run:

```bash
./gradlew :downloader:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add downloader/src/main/java/com/hank/musicfree/downloader/engine/DownloadEngine.kt \
  downloader/src/main/java/com/hank/musicfree/downloader/di/DownloaderModule.kt \
  downloader/src/test/java/com/hank/musicfree/downloader/engine \
  data/src/main/java/com/hank/musicfree/data/db/entity/DownloadTaskEntity.kt
git commit -m "feat(downloader): 下载完成写入完整本地曲库"
```

## Task 4: Switch Feature Home To Unified Local Library API

**Files:**
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListSourceLoader.kt`
- Modify: `feature/home/src/test/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListSourceLoaderTest.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/local/LocalMusicViewModel.kt`
- Modify: `feature/home/src/test/java/com/hank/musicfree/feature/home/local/LocalMusicViewModelTest.kt`
- Modify: `feature/home/src/main/java/com/hank/musicfree/feature/home/musiclisteditor/MusicListEditorLiteViewModel.kt`
- Modify: `feature/home/src/test/java/com/hank/musicfree/feature/home/musiclisteditor/MusicListEditorLiteViewModelTest.kt`

- [ ] **Step 1: Write failing source-loader test**

In `SearchMusicListSourceLoaderTest`, change the local-library test to:

```kotlin
@Test
fun `local library source loads unified local library from repository`() = runTest {
    val localItems = listOf(track(id = "local-song", platform = LocalMusicScanner.PLATFORM_LOCAL))
    whenever(musicRepository.observeLocalLibrary())
        .thenReturn(flowOf(localItems))

    val loader = SearchMusicListSourceLoader(playlistRepository, playerController, musicRepository)

    val actual = loader.observe(CollectionSource.LocalLibrary).first()

    assertEquals(localItems, actual)
    verify(musicRepository).observeLocalLibrary()
}
```

- [ ] **Step 2: Write failing ViewModel test updates**

In `LocalMusicViewModelTest.setup`, replace:

```kotlin
whenever(musicRepository.observeByPlatform(LocalMusicScanner.PLATFORM_LOCAL))
    .thenReturn(MutableStateFlow(emptyList()))
```

with:

```kotlin
whenever(musicRepository.observeLocalLibrary())
    .thenReturn(MutableStateFlow(emptyList()))
```

Update repository-list tests to mock and verify `observeLocalLibrary()`. Update remove test:

```kotlin
verify(musicRepository).removeFromLocalLibrary(item)
```

In `MusicListEditorLiteViewModelTest`, update local-library tests to mock and verify:

```kotlin
whenever(musicRepository.observeLocalLibrary()).thenReturn(MutableStateFlow(items))
verify(musicRepository).observeLocalLibrary()
verify(musicRepository).removeFromLocalLibrary(items[0])
```

- [ ] **Step 3: Run feature tests to verify they fail**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests '*SearchMusicListSourceLoaderTest' --tests '*LocalMusicViewModelTest' --tests '*MusicListEditorLiteViewModelTest' --no-daemon
```

Expected: FAIL because production code still calls `observeByPlatform("local")` and `delete(item)`.

- [ ] **Step 4: Implement source changes**

In `SearchMusicListSourceLoader.observe`, replace:

```kotlin
CollectionSource.LocalLibrary -> musicRepository.observeByPlatform(LocalMusicScanner.PLATFORM_LOCAL)
```

with:

```kotlin
CollectionSource.LocalLibrary -> musicRepository.observeLocalLibrary()
```

Remove the unused `LocalMusicScanner` import if it becomes unused.

In `LocalMusicViewModel.init`, replace:

```kotlin
musicRepository.observeByPlatform(LocalMusicScanner.PLATFORM_LOCAL)
```

with:

```kotlin
musicRepository.observeLocalLibrary()
```

In `LocalMusicViewModel.removeFromLocalLibrary`, replace:

```kotlin
musicRepository.delete(item)
```

with:

```kotlin
musicRepository.removeFromLocalLibrary(item)
```

In `MusicListEditorLiteViewModel`, replace the local-library source:

```kotlin
musicRepository.observeByPlatform(LocalMusicScanner.PLATFORM_LOCAL)
```

with:

```kotlin
musicRepository.observeLocalLibrary()
```

and replace local-library save removal:

```kotlin
musicRepository.delete(item)
```

with:

```kotlin
musicRepository.removeFromLocalLibrary(item)
```

- [ ] **Step 5: Run feature tests**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests '*SearchMusicListSourceLoaderTest' --tests '*LocalMusicViewModelTest' --tests '*MusicListEditorLiteViewModelTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add feature/home/src/main/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListSourceLoader.kt \
  feature/home/src/test/java/com/hank/musicfree/feature/home/searchmusiclist/SearchMusicListSourceLoaderTest.kt \
  feature/home/src/main/java/com/hank/musicfree/feature/home/local/LocalMusicViewModel.kt \
  feature/home/src/test/java/com/hank/musicfree/feature/home/local/LocalMusicViewModelTest.kt \
  feature/home/src/main/java/com/hank/musicfree/feature/home/musiclisteditor/MusicListEditorLiteViewModel.kt \
  feature/home/src/test/java/com/hank/musicfree/feature/home/musiclisteditor/MusicListEditorLiteViewModelTest.kt
git commit -m "feat(home): 本地音乐页使用统一曲库口径"
```

## Task 5: Logging, Regression Sweep, And Build Verification

**Files:**
- Modify: `downloader/src/main/java/com/hank/musicfree/downloader/engine/DownloadEngine.kt`
- Inspect: `data/src/main/java/com/hank/musicfree/data/repository/MusicRepository.kt`

- [ ] **Step 1: Add explicit local-library write logging**

In `DownloadEngine.runOne`, keep the downloaded database commit inside `withContext(NonCancellable)` and make the final block use this exact order:

```kotlin
val uri = writer(cacheFile, displayName, mime, relPath, size)
val downloadedAt = System.currentTimeMillis()
val downloaded = DownloadedTrackEntity(
    id = task.id,
    platform = task.platform,
    mediaStoreUri = uri.toString(),
    relativePath = relPath,
    mimeType = mime,
    quality = task.targetQuality,
    sizeBytes = size,
    downloadedAt = downloadedAt,
)
try {
    musicRepository.commitDownloadedTrack(musicItem, downloaded)
    MfLog.detail(
        LogCategory.DOWNLOAD,
        "download_local_library_write_success",
        mapOf(
            "platform" to musicItem.platform,
            "itemId" to musicItem.id,
            "itemName" to musicItem.title,
            "quality" to task.targetQuality,
            "pathType" to "mediastore",
            "mediaStoreUri" to uri.toString(),
            "result" to LogFields.Result.SUCCESS,
        ),
    )
} catch (t: Throwable) {
    MfLog.error(
        LogCategory.DOWNLOAD,
        "download_local_library_write_failed",
        throwable = t,
        fields = mapOf(
            "platform" to musicItem.platform,
            "itemId" to musicItem.id,
            "itemName" to musicItem.title,
            "quality" to task.targetQuality,
            "pathType" to "mediastore",
            "mediaStoreUri" to uri.toString(),
            "result" to LogFields.Result.FAILURE,
            "reason" to "exception",
        ),
    )
    throw t
}
taskDao.deleteByKey(task.id, task.platform)
```

- [ ] **Step 2: Run data tests**

Run:

```bash
./gradlew :data:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 3: Run downloader tests**

Run:

```bash
./gradlew :downloader:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 4: Run feature home tests**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Run harness and debug build**

Run:

```bash
bash scripts/dev-harness/check.sh
./gradlew :app:assembleDebug --no-daemon
```

Expected: both PASS.

- [ ] **Step 6: Optional emulator smoke when a device is available**

Run:

```bash
adb devices
```

If a device is listed, install the Debug APK and manually verify:

```bash
./gradlew :app:installDebug --no-daemon
```

Manual expected behavior:

- Download a plugin track with album/artwork metadata.
- Open 本地音乐.
- The row appears with the original plugin tag, album text, and cover.
- Playing it uses the local downloaded media URI and does not lose plugin context.

- [ ] **Step 7: Final commit**

```bash
git add downloader/src/main/java/com/hank/musicfree/downloader/engine/DownloadEngine.kt \
  data/src/main/java/com/hank/musicfree/data/repository/MusicRepository.kt
git commit -m "fix(download): 补齐本地曲库写入诊断日志"
```

## Final Integration Notes

- Do not merge back to `main` until all required debug verification passes.
- Before merge-back, run:

```bash
git status --short --branch
git log --oneline --decorate --max-count=8
```

- If the user asks to merge back, use squash merge from local `main` per `AGENTS.md`:

```bash
git switch main
git merge --squash feat/downloaded-local-metadata-rn-parity
git commit -m "feat(local): 对齐下载曲目本地元数据"
```

Then rerun at least:

```bash
./gradlew :app:assembleDebug --no-daemon
```

and report any unrelated dirty main-checkout files separately.

## Plan Self-Review

- Spec coverage: Tasks cover full seed persistence, localPath persistence, local-library query, completion commit, removal semantics, feature data-source switching, logging, tests, and Debug build verification.
- Placeholder scan: No unfinished placeholder markers or unspecified edge-case steps remain.
- Type consistency: The plan consistently uses `musicItemJson`, `localPath`, `observeLocalLibrary()`, `commitDownloadedTrack()`, and `removeFromLocalLibrary()` across data, downloader, and feature tasks.

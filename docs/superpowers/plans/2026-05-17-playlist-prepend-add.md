# Playlist Prepend Add Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Change every add-to-playlist path so newly added songs are inserted at the front of the playlist while preserving batch item order and existing dedup behavior.

**Architecture:** Keep the behavior centralized in `PlaylistRepository`; callers continue using `addMusicToPlaylist()` and `addMusicsToPlaylist()` unchanged. `PlaylistDao` exposes the current minimum `sortOrder`, and repository insert helpers allocate new negative/lower sort orders ahead of existing rows.

**Tech Stack:** Kotlin, Room DAO, Android instrumentation tests, Gradle Debug build.

---

## File Structure

- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/PlaylistDao.kt`
  - Add a DAO query for the current minimum playlist `sortOrder`.
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepository.kt`
  - Replace append order allocation with front insertion order allocation.
  - Keep public API, logging events, cover sync, and transaction boundaries intact.
- Modify: `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepositoryTest.kt`
  - Update existing ordering assertions.
  - Add a duplicate-in-batch prepend regression test.
- No schema JSON, entity, ViewModel, UI, or navigation file should change.

## Task 1: Repository Ordering Tests

**Files:**
- Modify: `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepositoryTest.kt`

- [ ] **Step 1: Update single-add ordering test**

Replace the end of `addAndObserveMusicInPlaylist()` so the test proves later single adds appear at the front. Keep the Turbine structure already in the file, but assert the stable observed title order after each add:

```kotlin
playlistRepo.addMusicToPlaylist("pl1", m1)
var current = emptyList<MusicItem>()
while (current.size < 1) current = awaitItem()
assertEquals(listOf("Song m1"), current.map { it.title })

playlistRepo.addMusicToPlaylist("pl1", m2)
while (current.size < 2) current = awaitItem()
assertEquals(listOf("Song m2", "Song m1"), current.map { it.title })
```

- [ ] **Step 2: Update batch ordering test**

Rename `addMusicsToPlaylist_preservesImportOrderForManualSort` to `addMusicsToPlaylist_prependsBatchAndPreservesInputOrderForManualSort`. Seed existing rows first, then add a batch:

```kotlin
playlistRepo.addMusicToPlaylist(id, sampleMusic("existing-1", title = "Existing One"))
playlistRepo.addMusicToPlaylist(id, sampleMusic("existing-2", title = "Existing Two"))
val items = listOf(
    sampleMusic("m3", title = "Third"),
    sampleMusic("m1", title = "First"),
    sampleMusic("m2", title = "Second"),
)

playlistRepo.addMusicsToPlaylist(id, items)

val titles = playlistRepo.observeMusicInPlaylist(id).first().map { it.title }
assertEquals(listOf("Third", "First", "Second", "Existing Two", "Existing One"), titles)
```

This expected existing order accounts for the new single-add rule: `existing-2` is already before `existing-1` before the batch is inserted.

- [ ] **Step 3: Add duplicate regression test**

Add this test near the batch ordering tests:

```kotlin
@Test
fun addMusicsToPlaylist_prependsOnlyNewItemsWhenBatchContainsDuplicates() = runBlocking {
    val id = UUID.randomUUID().toString()
    playlistRepo.createPlaylist(Playlist(id = id, name = "Imported", coverUri = null))
    playlistRepo.addMusicsToPlaylist(
        id,
        listOf(
            sampleMusic("a", title = "Already"),
            sampleMusic("x", title = "Existing X"),
            sampleMusic("y", title = "Existing Y"),
        ),
    )

    val added = playlistRepo.addMusicsToPlaylist(
        id,
        listOf(
            sampleMusic("a", title = "Already Updated"),
            sampleMusic("b", title = "Batch B"),
            sampleMusic("c", title = "Batch C"),
        ),
    )

    val titles = playlistRepo.observeMusicInPlaylist(id).first().map { it.title }
    assertEquals(2, added)
    assertEquals(listOf("Batch B", "Batch C", "Already Updated", "Existing X", "Existing Y"), titles)
}
```

The duplicate row refreshes metadata through `musicDao.upsert()`, but it does not get a new playlist membership row or move ahead of the new batch.

- [ ] **Step 4: Run the targeted test class and verify the red state**

Run:

```bash
./gradlew :data:connectedDebugAndroidTest --no-daemon -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.data.repository.PlaylistRepositoryTest
```

Expected before implementation: at least the changed ordering assertions fail because current code appends with `maxSortOrder + 1`.

If no emulator/device is available, record that the red instrumentation step is blocked and continue with implementation; do not claim red-green runtime proof until a device run succeeds.

## Task 2: Prepend Implementation

**Files:**
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/PlaylistDao.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepository.kt`

- [ ] **Step 1: Add minimum sort-order DAO query**

Add this next to `maxSortOrderInPlaylist()`:

```kotlin
@Query("SELECT COALESCE(MIN(sortOrder), 0) FROM playlist_music WHERE playlistId = :playlistId")
suspend fun minSortOrderInPlaylist(playlistId: String): Int
```

Keep `maxSortOrderInPlaylist()` for any existing callers until code search proves it is unused.

- [ ] **Step 2: Refactor single add to allocate a front sortOrder**

Change `addMusicToPlaylistWithCoverSync()` to compute one front order and pass it into the insert helper:

```kotlin
private suspend fun addMusicToPlaylistWithCoverSync(playlistId: String, item: MusicItem): Boolean {
    val sortOrder = playlistDao.minSortOrderInPlaylist(playlistId) - 1
    val added = addMusicToPlaylistNoCoverSync(
        playlistId = playlistId,
        item = item,
        sortOrder = sortOrder,
        addedAt = System.currentTimeMillis(),
    )
    if (added) syncPlaylistCoverIfNeeded(playlistId, item)
    return added
}
```

- [ ] **Step 3: Refactor batch add to allocate one front range**

Inside `addMusicsToPlaylist()`, keep the transaction and `insertedItems` list, but compute one timestamp and one base order:

```kotlin
val insertedItems = mutableListOf<MusicItem>()
val addedAt = System.currentTimeMillis()
val addedCount = db.withTransaction {
    var addedCount = 0
    val baseOrder = playlistDao.minSortOrderInPlaylist(playlistId) - items.size
    for ((index, item) in items.withIndex()) {
        if (addMusicToPlaylistNoCoverSync(
                playlistId = playlistId,
                item = item,
                sortOrder = baseOrder + index,
                addedAt = addedAt,
            )
        ) {
            addedCount++
            insertedItems.add(item)
        }
    }
    addedCount
}
```

This preserves requested item order among newly inserted rows. Duplicates still consume their index slot, which can leave gaps in `sortOrder`; gaps are acceptable because only relative ordering matters.

- [ ] **Step 4: Update private insert helper signature**

Replace the current helper with:

```kotlin
private suspend fun addMusicToPlaylistNoCoverSync(
    playlistId: String,
    item: MusicItem,
    sortOrder: Int,
    addedAt: Long,
): Boolean {
    // Keep this order (upsert before cross-ref insert) to preserve existing behavior:
    // duplicates should still refresh music metadata, while playlist membership is guarded
    // by the unique cross-ref insert.
    musicDao.upsert(item.toEntity(converters))
    val rowId = playlistDao.insertCrossRefIgnore(
        PlaylistMusicCrossRef(
            playlistId = playlistId,
            musicId = item.id,
            musicPlatform = item.platform,
            sortOrder = sortOrder,
            addedAt = addedAt,
        )
    )
    return rowId != -1L
}
```

- [ ] **Step 5: Check for obsolete max-sort usage**

Run:

```bash
rg -n "maxSortOrderInPlaylist|addMusicToPlaylistNoCoverSync\\(" data/src/main/java data/src/androidTest/java
```

Expected: `maxSortOrderInPlaylist` is either unused outside DAO or only kept for compatibility. Every `addMusicToPlaylistNoCoverSync(` call passes `sortOrder` and `addedAt`.

## Task 3: Verification And Integration

**Files:**
- Read-only verification first. Modify only if a command exposes a real issue in touched code.

- [ ] **Step 1: Run targeted instrumentation test**

Run:

```bash
./gradlew :data:connectedDebugAndroidTest --no-daemon -Pandroid.testInstrumentationRunnerArguments.class=com.zili.android.musicfreeandroid.data.repository.PlaylistRepositoryTest
```

Expected: `PlaylistRepositoryTest` passes. If the command cannot run because no device/emulator is available, capture the exact failure and run the broader non-device checks below.

- [ ] **Step 2: Run dev harness**

Run:

```bash
bash scripts/dev-harness/check.sh
```

Expected: harness exits 0. This covers guarded test-source compilation and grep checks.

- [ ] **Step 3: Run Debug build**

Run:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: build exits 0.

- [ ] **Step 4: Inspect final diff**

Run:

```bash
git diff --check
git diff --stat main...HEAD
git diff main...HEAD -- data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/PlaylistDao.kt data/src/main/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepository.kt data/src/androidTest/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepositoryTest.kt docs/superpowers/specs/2026-05-17-playlist-prepend-add-design.md docs/superpowers/plans/2026-05-17-playlist-prepend-add.md
```

Expected: no whitespace errors; changed files stay within the planned scope plus spec/plan docs.

- [ ] **Step 5: Commit implementation**

Commit all worktree changes with a Chinese conventional commit message:

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/PlaylistDao.kt \
  data/src/main/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepository.kt \
  data/src/androidTest/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepositoryTest.kt \
  docs/superpowers/plans/2026-05-17-playlist-prepend-add.md
git commit -m "fix(playlist): 添加歌曲默认插入歌单首部"
```

## Task 4: Merge Back To Main

**Files:**
- No direct file edits unless resolving merge conflicts.

- [ ] **Step 1: Verify branch status**

Run from the worktree:

```bash
git status --short --branch
```

Expected: clean `feat/playlist-prepend-add`.

- [ ] **Step 2: Protect unrelated main checkout changes**

Run from the main checkout:

```bash
git status --short --branch
```

Expected known unrelated `gradle.properties` remains in the main checkout. Do not include it in the merge commit.

- [ ] **Step 3: Squash merge into main**

From the main checkout:

```bash
git merge --squash feat/playlist-prepend-add
git commit -m "fix(playlist): 添加歌曲默认插入歌单首部"
```

If unrelated main checkout changes block merge or commit staging, stash only those unrelated paths before the squash merge, then restore them after commit.

- [ ] **Step 4: Re-run final Debug verification on main**

Run from main:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: build exits 0.

- [ ] **Step 5: Cleanup**

After confirming main contains the squash merge and unrelated main changes are restored:

```bash
git worktree remove .worktrees/playlist-prepend-add
git branch -d feat/playlist-prepend-add || git branch -D feat/playlist-prepend-add
```

Only force-delete the branch after verifying the squash merge contains the feature diff.

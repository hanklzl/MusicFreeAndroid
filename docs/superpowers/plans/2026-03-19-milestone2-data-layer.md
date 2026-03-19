# Milestone 2: Data Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the complete data persistence layer — core domain models, Room database, DataStore preferences, and Repository interfaces — so upper layers (player, feature modules) have a clean, reactive data API.

**Architecture:** Domain models live in `:core/model/` (no Room dependency). Room entities, DAOs, and the database live in `:data/db/`. Mappers convert between Entity and Model. Repositories in `:data/repository/` expose `Flow<T>` and hide storage details. DataStore handles scalar preferences.

**Tech Stack:** Room (SQLite ORM), DataStore (Preferences), Kotlin Coroutines + Flow, Hilt DI, JUnit + Room in-memory DB for testing.

---

## File Structure

### `:core` module — Domain Models

| File | Responsibility |
|------|---------------|
| `core/model/MusicItem.kt` | Core music item data class (id + platform composite key) |
| `core/model/PlayQuality.kt` | Quality tier enum (LOW, STANDARD, HIGH, SUPER) |
| `core/model/QualityInfo.kt` | Per-quality URL + size |
| `core/model/MediaSourceResult.kt` | Plugin-returned playback source (url, headers, UA) |
| `core/model/Playlist.kt` | Named playlist with cover |
| `core/model/RepeatMode.kt` | Repeat mode enum (OFF, ONE, ALL) |
| `core/model/LyricLine.kt` | Single parsed lyric line (time + text) |

### `:data` module — Persistence

| File | Responsibility |
|------|---------------|
| `data/db/entity/MusicItemEntity.kt` | Room entity for music items |
| `data/db/entity/PlaylistEntity.kt` | Room entity for playlists |
| `data/db/entity/PlaylistMusicCrossRef.kt` | Junction table for playlist ↔ music M:N |
| `data/db/entity/PlayQueueEntity.kt` | Room entity for persisted play queue |
| `data/db/dao/MusicDao.kt` | CRUD operations for music items |
| `data/db/dao/PlaylistDao.kt` | CRUD for playlists + junction queries |
| `data/db/dao/PlayQueueDao.kt` | Play queue persistence |
| `data/db/converter/Converters.kt` | Room TypeConverters (Map<String,String>, Map<PlayQuality,QualityInfo>) |
| `data/db/AppDatabase.kt` | Room database definition |
| `data/mapper/MusicItemMapper.kt` | MusicItemEntity ↔ MusicItem |
| `data/mapper/PlaylistMapper.kt` | PlaylistEntity ↔ Playlist |
| `data/mapper/PlayQueueMapper.kt` | PlayQueueEntity ↔ MusicItem |
| `data/datastore/AppPreferences.kt` | DataStore for user preferences |
| `data/repository/MusicRepository.kt` | Music item data access |
| `data/repository/PlaylistRepository.kt` | Playlist data access |
| `data/repository/PlayQueueRepository.kt` | Play queue persistence |
| `data/di/DataModule.kt` | Hilt module providing DB, DAOs, DataStore, Repositories |

### Test files

| File | Responsibility |
|------|---------------|
| `core/src/test/.../core/model/MusicItemTest.kt` | Model construction & equality |
| `data/src/test/.../data/mapper/MusicItemMapperTest.kt` | Bidirectional mapper tests |
| `data/src/test/.../data/mapper/PlaylistMapperTest.kt` | Bidirectional mapper tests |
| `data/src/test/.../data/mapper/PlayQueueMapperTest.kt` | Bidirectional mapper tests |
| `data/src/test/.../data/datastore/AppPreferencesTest.kt` | DataStore read/write tests |
| `data/src/androidTest/.../data/db/dao/MusicDaoTest.kt` | Room DAO integration tests |
| `data/src/androidTest/.../data/db/dao/PlaylistDaoTest.kt` | Room DAO integration tests |
| `data/src/androidTest/.../data/db/dao/PlayQueueDaoTest.kt` | Room DAO integration tests |
| `data/src/androidTest/.../data/repository/MusicRepositoryTest.kt` | Repository Flow tests |
| `data/src/androidTest/.../data/repository/PlaylistRepositoryTest.kt` | Repository Flow tests |

---

## Task 1: Add Room & DataStore Dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `data/build.gradle.kts`

- [ ] **Step 1: Add Room, DataStore, and Coroutines versions to version catalog**

In `gradle/libs.versions.toml`, add:

```toml
# Under [versions]
room = "2.7.1"
datastore = "1.1.7"
coroutines = "1.10.2"
turbine = "1.2.0"

# Under [libraries]
# Room
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
# DataStore
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
# Coroutines
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
# Turbine (Flow testing)
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

# Under [plugins]
androidx-room = { id = "androidx.room", version.ref = "room" }
```

- [ ] **Step 2: Update data module build.gradle.kts**

Replace `data/build.gradle.kts` with:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.zili.android.musicfreeandroid.data"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
}
```

- [ ] **Step 3: Verify build compiles**

Run: `./gradlew :data:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml data/build.gradle.kts
git commit -m "build: add Room, DataStore, Coroutines dependencies for data module"
```

---

## Task 2: Core Domain Models

**Files:**
- Create: `core/src/main/java/com/zili/android/musicfreeandroid/core/model/PlayQuality.kt`
- Create: `core/src/main/java/com/zili/android/musicfreeandroid/core/model/QualityInfo.kt`
- Create: `core/src/main/java/com/zili/android/musicfreeandroid/core/model/MusicItem.kt`
- Create: `core/src/main/java/com/zili/android/musicfreeandroid/core/model/MediaSourceResult.kt`
- Create: `core/src/main/java/com/zili/android/musicfreeandroid/core/model/Playlist.kt`
- Create: `core/src/main/java/com/zili/android/musicfreeandroid/core/model/RepeatMode.kt`
- Create: `core/src/main/java/com/zili/android/musicfreeandroid/core/model/LyricLine.kt`
- Create: `core/src/test/java/com/zili/android/musicfreeandroid/core/model/MusicItemTest.kt`

- [ ] **Step 1: Write model unit tests**

Create `core/src/test/java/com/zili/android/musicfreeandroid/core/model/MusicItemTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.core.model

import org.junit.Assert.*
import org.junit.Test

class MusicItemTest {

    @Test
    fun `MusicItem construction with required fields`() {
        val item = MusicItem(
            id = "song1",
            platform = "local",
            title = "Test Song",
            artist = "Test Artist",
            album = null,
            duration = 180_000L,
            url = null,
            artwork = null,
            qualities = null,
        )
        assertEquals("song1", item.id)
        assertEquals("local", item.platform)
        assertEquals("Test Song", item.title)
        assertEquals(180_000L, item.duration)
    }

    @Test
    fun `MusicItem equality uses id and platform`() {
        val a = MusicItem("1", "local", "A", "Artist", null, 0, null, null, null)
        val b = MusicItem("1", "local", "B", "Other", "Album", 999, "url", "art", null)
        // data class equality uses all fields
        assertNotEquals(a, b)
        // but same id+platform
        assertEquals(a.id, b.id)
        assertEquals(a.platform, b.platform)
    }

    @Test
    fun `MusicItem with qualities`() {
        val qualities = mapOf(
            PlayQuality.HIGH to QualityInfo(url = "https://example.com/high.mp3", size = 5_000_000L),
            PlayQuality.LOW to QualityInfo(url = "https://example.com/low.mp3", size = 2_000_000L),
        )
        val item = MusicItem("1", "plugin1", "Song", "Artist", null, 240_000, null, null, qualities)
        assertEquals(2, item.qualities!!.size)
        assertEquals("https://example.com/high.mp3", item.qualities!![PlayQuality.HIGH]?.url)
    }

    @Test
    fun `PlayQuality values`() {
        val values = PlayQuality.entries
        assertEquals(4, values.size)
        assertTrue(values.contains(PlayQuality.LOW))
        assertTrue(values.contains(PlayQuality.STANDARD))
        assertTrue(values.contains(PlayQuality.HIGH))
        assertTrue(values.contains(PlayQuality.SUPER))
    }

    @Test
    fun `RepeatMode values`() {
        val values = RepeatMode.entries
        assertEquals(3, values.size)
        assertTrue(values.contains(RepeatMode.OFF))
        assertTrue(values.contains(RepeatMode.ONE))
        assertTrue(values.contains(RepeatMode.ALL))
    }

    @Test
    fun `MediaSourceResult construction`() {
        val result = MediaSourceResult(
            url = "https://example.com/stream.mp3",
            headers = mapOf("Referer" to "https://example.com"),
            userAgent = "MusicFree/1.0",
            quality = PlayQuality.HIGH,
        )
        assertEquals("https://example.com/stream.mp3", result.url)
        assertEquals("MusicFree/1.0", result.userAgent)
        assertEquals(PlayQuality.HIGH, result.quality)
        assertEquals(1, result.headers!!.size)
    }

    @Test
    fun `Playlist construction`() {
        val playlist = Playlist(
            id = "pl1",
            name = "My Favorites",
            coverUri = null,
        )
        assertEquals("pl1", playlist.id)
        assertEquals("My Favorites", playlist.name)
        assertNull(playlist.coverUri)
    }

    @Test
    fun `LyricLine construction`() {
        val line = LyricLine(timeMs = 30_500L, text = "Hello world")
        assertEquals(30_500L, line.timeMs)
        assertEquals("Hello world", line.text)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:testDebugUnitTest --tests "*.MusicItemTest"`
Expected: Compilation failure — model classes don't exist yet.

- [ ] **Step 3: Create all domain model classes**

Create `core/src/main/java/com/zili/android/musicfreeandroid/core/model/PlayQuality.kt`:

```kotlin
package com.zili.android.musicfreeandroid.core.model

enum class PlayQuality {
    LOW, STANDARD, HIGH, SUPER
}
```

Create `core/src/main/java/com/zili/android/musicfreeandroid/core/model/QualityInfo.kt`:

```kotlin
package com.zili.android.musicfreeandroid.core.model

data class QualityInfo(
    val url: String?,
    val size: Long?,
)
```

Create `core/src/main/java/com/zili/android/musicfreeandroid/core/model/MusicItem.kt`:

```kotlin
package com.zili.android.musicfreeandroid.core.model

/**
 * Core music item. The combination of [id] + [platform] uniquely identifies a track.
 * [duration] is in milliseconds (original RN version uses seconds — convert at boundaries).
 */
data class MusicItem(
    val id: String,
    val platform: String,
    val title: String,
    val artist: String,
    val album: String?,
    val duration: Long,
    val url: String?,
    val artwork: String?,
    val qualities: Map<PlayQuality, QualityInfo>?,
)
```

Create `core/src/main/java/com/zili/android/musicfreeandroid/core/model/MediaSourceResult.kt`:

```kotlin
package com.zili.android.musicfreeandroid.core.model

data class MediaSourceResult(
    val url: String,
    val headers: Map<String, String>?,
    val userAgent: String?,
    val quality: PlayQuality?,
)
```

Create `core/src/main/java/com/zili/android/musicfreeandroid/core/model/Playlist.kt`:

```kotlin
package com.zili.android.musicfreeandroid.core.model

data class Playlist(
    val id: String,
    val name: String,
    val coverUri: String?,
)
```

Create `core/src/main/java/com/zili/android/musicfreeandroid/core/model/RepeatMode.kt`:

```kotlin
package com.zili.android.musicfreeandroid.core.model

enum class RepeatMode {
    OFF, ONE, ALL
}
```

Create `core/src/main/java/com/zili/android/musicfreeandroid/core/model/LyricLine.kt`:

```kotlin
package com.zili.android.musicfreeandroid.core.model

data class LyricLine(
    val timeMs: Long,
    val text: String,
)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:testDebugUnitTest --tests "*.MusicItemTest"`
Expected: All 7 tests PASS.

- [ ] **Step 5: Delete the .gitkeep placeholder**

```bash
rm core/src/main/java/com/zili/android/musicfreeandroid/core/model/.gitkeep
```

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/zili/android/musicfreeandroid/core/model/ core/src/test/
git commit -m "feat(core): add domain models — MusicItem, Playlist, PlayQuality, RepeatMode, etc."
```

---

## Task 3: Room Entities

**Files:**
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/MusicItemEntity.kt`
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/PlaylistEntity.kt`
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/PlaylistMusicCrossRef.kt`
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/entity/PlayQueueEntity.kt`

- [ ] **Step 1: Create MusicItemEntity**

```kotlin
package com.zili.android.musicfreeandroid.data.db.entity

import androidx.room.Entity

@Entity(tableName = "music_items", primaryKeys = ["id", "platform"])
data class MusicItemEntity(
    val id: String,
    val platform: String,
    val title: String,
    val artist: String,
    val album: String?,
    val duration: Long,
    val url: String?,
    val artwork: String?,
    val qualitiesJson: String?,
)
```

- [ ] **Step 2: Create PlaylistEntity**

```kotlin
package com.zili.android.musicfreeandroid.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val coverUri: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
```

- [ ] **Step 3: Create PlaylistMusicCrossRef (junction table)**

```kotlin
package com.zili.android.musicfreeandroid.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "playlist_music",
    primaryKeys = ["playlistId", "musicId", "musicPlatform"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MusicItemEntity::class,
            parentColumns = ["id", "platform"],
            childColumns = ["musicId", "musicPlatform"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("playlistId"),
        Index("musicId", "musicPlatform"),
    ],
)
data class PlaylistMusicCrossRef(
    val playlistId: String,
    val musicId: String,
    val musicPlatform: String,
    val sortOrder: Int,
)
```

- [ ] **Step 4: Create PlayQueueEntity**

```kotlin
package com.zili.android.musicfreeandroid.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_queue")
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
    val sortOrder: Int,
)
```

- [ ] **Step 5: Verify build**

Run: `./gradlew :data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/db/
git commit -m "feat(data): add Room entities — MusicItemEntity, PlaylistEntity, PlayQueueEntity, junction table"
```

---

## Task 4: Room TypeConverters

**Files:**
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/converter/Converters.kt`

- [ ] **Step 1: Create Converters**

```kotlin
package com.zili.android.musicfreeandroid.data.db.converter

import androidx.room.TypeConverter
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.QualityInfo
import org.json.JSONObject

class Converters {

    @TypeConverter
    fun qualitiesToJson(qualities: Map<PlayQuality, QualityInfo>?): String? {
        if (qualities == null) return null
        val json = JSONObject()
        qualities.forEach { (quality, info) ->
            val obj = JSONObject()
            obj.put("url", info.url ?: JSONObject.NULL)
            obj.put("size", info.size ?: JSONObject.NULL)
            json.put(quality.name, obj)
        }
        return json.toString()
    }

    @TypeConverter
    fun jsonToQualities(json: String?): Map<PlayQuality, QualityInfo>? {
        if (json == null) return null
        val obj = JSONObject(json)
        val map = mutableMapOf<PlayQuality, QualityInfo>()
        obj.keys().forEach { key ->
            val quality = PlayQuality.valueOf(key)
            val info = obj.getJSONObject(key)
            map[quality] = QualityInfo(
                url = if (info.isNull("url")) null else info.getString("url"),
                size = if (info.isNull("size")) null else info.getLong("size"),
            )
        }
        return map
    }
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew :data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/db/converter/
git commit -m "feat(data): add Room TypeConverters for quality map JSON serialization"
```

---

## Task 5: Entity ↔ Model Mappers

**Files:**
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/MusicItemMapper.kt`
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/PlaylistMapper.kt`
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/PlayQueueMapper.kt`
- Create: `data/src/test/java/com/zili/android/musicfreeandroid/data/mapper/MusicItemMapperTest.kt`
- Create: `data/src/test/java/com/zili/android/musicfreeandroid/data/mapper/PlaylistMapperTest.kt`
- Create: `data/src/test/java/com/zili/android/musicfreeandroid/data/mapper/PlayQueueMapperTest.kt`

- [ ] **Step 1: Write mapper tests**

Create `data/src/test/java/com/zili/android/musicfreeandroid/data/mapper/MusicItemMapperTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.mapper

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.QualityInfo
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.entity.MusicItemEntity
import org.junit.Assert.*
import org.junit.Test

class MusicItemMapperTest {

    private val converters = Converters()

    @Test
    fun `model to entity and back preserves all fields`() {
        val model = MusicItem(
            id = "song1",
            platform = "netease",
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            duration = 240_000L,
            url = "https://example.com/song.mp3",
            artwork = "https://example.com/cover.jpg",
            qualities = mapOf(
                PlayQuality.HIGH to QualityInfo("https://example.com/high.mp3", 8_000_000L),
                PlayQuality.LOW to QualityInfo("https://example.com/low.mp3", 3_000_000L),
            ),
        )
        val entity = model.toEntity(converters)
        val roundTripped = entity.toModel(converters)
        assertEquals(model, roundTripped)
    }

    @Test
    fun `model with null optionals round-trips`() {
        val model = MusicItem(
            id = "song2",
            platform = "local",
            title = "Minimal",
            artist = "Unknown",
            album = null,
            duration = 0L,
            url = null,
            artwork = null,
            qualities = null,
        )
        val entity = model.toEntity(converters)
        val roundTripped = entity.toModel(converters)
        assertEquals(model, roundTripped)
    }

    @Test
    fun `entity fields are correctly set`() {
        val model = MusicItem("id1", "platform1", "Title", "Artist", "Album", 100, "url", "art", null)
        val entity = model.toEntity(converters)
        assertEquals("id1", entity.id)
        assertEquals("platform1", entity.platform)
        assertEquals("Title", entity.title)
        assertEquals("Artist", entity.artist)
        assertEquals("Album", entity.album)
        assertEquals(100L, entity.duration)
        assertEquals("url", entity.url)
        assertEquals("art", entity.artwork)
        assertNull(entity.qualitiesJson)
    }
}
```

Create `data/src/test/java/com/zili/android/musicfreeandroid/data/mapper/PlaylistMapperTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.mapper

import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistEntity
import org.junit.Assert.*
import org.junit.Test

class PlaylistMapperTest {

    @Test
    fun `model to entity and back preserves fields`() {
        val model = Playlist(id = "pl1", name = "Favorites", coverUri = "https://example.com/cover.jpg")
        val now = System.currentTimeMillis()
        val entity = model.toEntity(createdAt = now, updatedAt = now)
        val roundTripped = entity.toModel()
        assertEquals(model, roundTripped)
    }

    @Test
    fun `entity preserves timestamps`() {
        val model = Playlist(id = "pl2", name = "Empty", coverUri = null)
        val entity = model.toEntity(createdAt = 1000L, updatedAt = 2000L)
        assertEquals(1000L, entity.createdAt)
        assertEquals(2000L, entity.updatedAt)
    }
}
```

Create `data/src/test/java/com/zili/android/musicfreeandroid/data/mapper/PlayQueueMapperTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.mapper

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.QualityInfo
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.entity.PlayQueueEntity
import org.junit.Assert.*
import org.junit.Test

class PlayQueueMapperTest {

    private val converters = Converters()

    @Test
    fun `MusicItem to PlayQueueEntity and back`() {
        val item = MusicItem(
            id = "q1",
            platform = "local",
            title = "Queue Song",
            artist = "Artist",
            album = "Album",
            duration = 180_000L,
            url = "https://example.com/song.mp3",
            artwork = "https://example.com/art.jpg",
            qualities = mapOf(PlayQuality.STANDARD to QualityInfo("url", 4_000_000L)),
        )
        val entity = item.toPlayQueueEntity(sortOrder = 3, converters = converters)
        val roundTripped = entity.toMusicItem(converters)
        assertEquals(item, roundTripped)
        assertEquals(3, entity.sortOrder)
    }

    @Test
    fun `MusicItem with nulls to PlayQueueEntity and back`() {
        val item = MusicItem("q2", "local", "Song", "Art", null, 0, null, null, null)
        val entity = item.toPlayQueueEntity(sortOrder = 0, converters = converters)
        val roundTripped = entity.toMusicItem(converters)
        assertEquals(item, roundTripped)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :data:testDebugUnitTest --tests "*.mapper.*"`
Expected: Compilation failure — mapper functions don't exist yet.

- [ ] **Step 3: Implement mappers**

Create `data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/MusicItemMapper.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.mapper

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.entity.MusicItemEntity

fun MusicItem.toEntity(converters: Converters): MusicItemEntity = MusicItemEntity(
    id = id,
    platform = platform,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    url = url,
    artwork = artwork,
    qualitiesJson = converters.qualitiesToJson(qualities),
)

fun MusicItemEntity.toModel(converters: Converters): MusicItem = MusicItem(
    id = id,
    platform = platform,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    url = url,
    artwork = artwork,
    qualities = converters.jsonToQualities(qualitiesJson),
)
```

Create `data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/PlaylistMapper.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.mapper

import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistEntity

fun Playlist.toEntity(createdAt: Long, updatedAt: Long): PlaylistEntity = PlaylistEntity(
    id = id,
    name = name,
    coverUri = coverUri,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PlaylistEntity.toModel(): Playlist = Playlist(
    id = id,
    name = name,
    coverUri = coverUri,
)
```

Create `data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/PlayQueueMapper.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.mapper

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.entity.PlayQueueEntity

fun MusicItem.toPlayQueueEntity(sortOrder: Int, converters: Converters): PlayQueueEntity =
    PlayQueueEntity(
        musicId = id,
        musicPlatform = platform,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        url = url,
        artwork = artwork,
        qualitiesJson = converters.qualitiesToJson(qualities),
        sortOrder = sortOrder,
    )

fun PlayQueueEntity.toMusicItem(converters: Converters): MusicItem = MusicItem(
    id = musicId,
    platform = musicPlatform,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    url = url,
    artwork = artwork,
    qualities = converters.jsonToQualities(qualitiesJson),
)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :data:testDebugUnitTest --tests "*.mapper.*"`
Expected: All 7 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/mapper/ data/src/test/
git commit -m "feat(data): add bidirectional mappers for MusicItem, Playlist, PlayQueue entities"
```

---

## Task 6: Room DAOs

**Files:**
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/MusicDao.kt`
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/PlaylistDao.kt`
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/PlayQueueDao.kt`

- [ ] **Step 1: Create MusicDao**

```kotlin
package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zili.android.musicfreeandroid.data.db.entity.MusicItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MusicItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MusicItemEntity>)

    @Update
    suspend fun update(item: MusicItemEntity)

    @Delete
    suspend fun delete(item: MusicItemEntity)

    @Query("SELECT * FROM music_items WHERE id = :id AND platform = :platform")
    suspend fun getById(id: String, platform: String): MusicItemEntity?

    @Query("SELECT * FROM music_items WHERE platform = :platform ORDER BY title ASC")
    fun observeByPlatform(platform: String): Flow<List<MusicItemEntity>>

    @Query("SELECT * FROM music_items ORDER BY title ASC")
    fun observeAll(): Flow<List<MusicItemEntity>>

    @Query("SELECT COUNT(*) FROM music_items")
    suspend fun count(): Int

    @Query("DELETE FROM music_items WHERE platform = :platform")
    suspend fun deleteByPlatform(platform: String)
}
```

- [ ] **Step 2: Create PlaylistDao**

```kotlin
package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zili.android.musicfreeandroid.data.db.entity.MusicItemEntity
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistEntity
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistMusicCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observeAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(crossRef: PlaylistMusicCrossRef)

    @Query("DELETE FROM playlist_music WHERE playlistId = :playlistId AND musicId = :musicId AND musicPlatform = :musicPlatform")
    suspend fun removeMusicFromPlaylist(playlistId: String, musicId: String, musicPlatform: String)

    @Query("""
        SELECT m.* FROM music_items m
        INNER JOIN playlist_music pm ON m.id = pm.musicId AND m.platform = pm.musicPlatform
        WHERE pm.playlistId = :playlistId
        ORDER BY pm.sortOrder ASC
    """)
    fun observeMusicInPlaylist(playlistId: String): Flow<List<MusicItemEntity>>

    @Query("SELECT COUNT(*) FROM playlist_music WHERE playlistId = :playlistId")
    suspend fun countMusicInPlaylist(playlistId: String): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM playlist_music WHERE playlistId = :playlistId")
    suspend fun maxSortOrderInPlaylist(playlistId: String): Int
}
```

- [ ] **Step 3: Create PlayQueueDao**

```kotlin
package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.zili.android.musicfreeandroid.data.db.entity.PlayQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PlayQueueEntity>)

    @Query("SELECT * FROM play_queue ORDER BY sortOrder ASC")
    suspend fun getAll(): List<PlayQueueEntity>

    @Query("SELECT * FROM play_queue ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<PlayQueueEntity>>

    @Query("DELETE FROM play_queue")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM play_queue")
    suspend fun count(): Int

    @Transaction
    suspend fun replaceAll(items: List<PlayQueueEntity>) {
        clearAll()
        insertAll(items)
    }
}
```

- [ ] **Step 4: Verify build**

Run: `./gradlew :data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/db/dao/
git commit -m "feat(data): add Room DAOs — MusicDao, PlaylistDao, PlayQueueDao"
```

---

## Task 7: AppDatabase

**Files:**
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt`

- [ ] **Step 1: Create AppDatabase**

```kotlin
package com.zili.android.musicfreeandroid.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.MusicDao
import com.zili.android.musicfreeandroid.data.db.dao.PlaylistDao
import com.zili.android.musicfreeandroid.data.db.dao.PlayQueueDao
import com.zili.android.musicfreeandroid.data.db.entity.MusicItemEntity
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistEntity
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistMusicCrossRef
import com.zili.android.musicfreeandroid.data.db.entity.PlayQueueEntity

@Database(
    entities = [
        MusicItemEntity::class,
        PlaylistEntity::class,
        PlaylistMusicCrossRef::class,
        PlayQueueEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playQueueDao(): PlayQueueDao
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew :data:assembleDebug`
Expected: BUILD SUCCESSFUL (Room annotation processor generates implementation).

- [ ] **Step 3: Commit**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/db/AppDatabase.kt
git commit -m "feat(data): add AppDatabase with Room entities, converters, and DAOs"
```

---

## Task 8: DataStore AppPreferences

**Files:**
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/datastore/AppPreferences.kt`
- Create: `data/src/test/java/com/zili/android/musicfreeandroid/data/datastore/AppPreferencesTest.kt`

- [ ] **Step 1: Write AppPreferences test**

Create `data/src/test/java/com/zili/android/musicfreeandroid/data/datastore/AppPreferencesTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AppPreferencesTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: AppPreferences

    @Before
    fun setup() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("test_prefs.preferences_pb") },
        )
        prefs = AppPreferences(dataStore)
    }

    @Test
    fun `default repeat mode is OFF`() = testScope.runTest {
        assertEquals(RepeatMode.OFF, prefs.repeatMode.first())
    }

    @Test
    fun `set and get repeat mode`() = testScope.runTest {
        prefs.setRepeatMode(RepeatMode.ONE)
        assertEquals(RepeatMode.ONE, prefs.repeatMode.first())
    }

    @Test
    fun `default quality is STANDARD`() = testScope.runTest {
        assertEquals(PlayQuality.STANDARD, prefs.playQuality.first())
    }

    @Test
    fun `set and get quality`() = testScope.runTest {
        prefs.setPlayQuality(PlayQuality.SUPER)
        assertEquals(PlayQuality.SUPER, prefs.playQuality.first())
    }

    @Test
    fun `default shuffle is false`() = testScope.runTest {
        assertFalse(prefs.shuffleEnabled.first())
    }

    @Test
    fun `set and get shuffle`() = testScope.runTest {
        prefs.setShuffleEnabled(true)
        assertTrue(prefs.shuffleEnabled.first())
    }

    @Test
    fun `default dark mode is null (follow system)`() = testScope.runTest {
        assertNull(prefs.darkMode.first())
    }

    @Test
    fun `set dark mode explicitly`() = testScope.runTest {
        prefs.setDarkMode(true)
        assertEquals(true, prefs.darkMode.first())
    }

    @Test
    fun `default current music index is -1`() = testScope.runTest {
        assertEquals(-1, prefs.currentMusicIndex.first())
    }

    @Test
    fun `set current music index`() = testScope.runTest {
        prefs.setCurrentMusicIndex(5)
        assertEquals(5, prefs.currentMusicIndex.first())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :data:testDebugUnitTest --tests "*.AppPreferencesTest"`
Expected: Compilation failure — AppPreferences doesn't exist.

- [ ] **Step 3: Implement AppPreferences**

Create `data/src/main/java/com/zili/android/musicfreeandroid/data/datastore/AppPreferences.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val repeatMode: Flow<RepeatMode> = dataStore.data.map { prefs ->
        prefs[KEY_REPEAT_MODE]?.let { RepeatMode.valueOf(it) } ?: RepeatMode.OFF
    }

    suspend fun setRepeatMode(mode: RepeatMode) {
        dataStore.edit { it[KEY_REPEAT_MODE] = mode.name }
    }

    val playQuality: Flow<PlayQuality> = dataStore.data.map { prefs ->
        prefs[KEY_PLAY_QUALITY]?.let { PlayQuality.valueOf(it) } ?: PlayQuality.STANDARD
    }

    suspend fun setPlayQuality(quality: PlayQuality) {
        dataStore.edit { it[KEY_PLAY_QUALITY] = quality.name }
    }

    val shuffleEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SHUFFLE_ENABLED] ?: false
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SHUFFLE_ENABLED] = enabled }
    }

    val darkMode: Flow<Boolean?> = dataStore.data.map { prefs ->
        if (prefs.contains(KEY_DARK_MODE)) prefs[KEY_DARK_MODE] else null
    }

    suspend fun setDarkMode(enabled: Boolean?) {
        dataStore.edit {
            if (enabled == null) it.remove(KEY_DARK_MODE) else it[KEY_DARK_MODE] = enabled
        }
    }

    val currentMusicIndex: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_CURRENT_MUSIC_INDEX] ?: -1
    }

    suspend fun setCurrentMusicIndex(index: Int) {
        dataStore.edit { it[KEY_CURRENT_MUSIC_INDEX] = index }
    }

    private companion object {
        val KEY_REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val KEY_PLAY_QUALITY = stringPreferencesKey("play_quality")
        val KEY_SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
        val KEY_CURRENT_MUSIC_INDEX = intPreferencesKey("current_music_index")
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :data:testDebugUnitTest --tests "*.AppPreferencesTest"`
Expected: All 10 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/datastore/ data/src/test/
git commit -m "feat(data): add AppPreferences DataStore for repeat mode, quality, shuffle, dark mode"
```

---

## Task 9: Repositories

**Files:**
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/MusicRepository.kt`
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepository.kt`
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/repository/PlayQueueRepository.kt`

- [ ] **Step 1: Create MusicRepository**

```kotlin
package com.zili.android.musicfreeandroid.data.repository

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.MusicDao
import com.zili.android.musicfreeandroid.data.mapper.toEntity
import com.zili.android.musicfreeandroid.data.mapper.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val musicDao: MusicDao,
    private val converters: Converters,
) {

    fun observeAll(): Flow<List<MusicItem>> =
        musicDao.observeAll().map { entities -> entities.map { it.toModel(converters) } }

    fun observeByPlatform(platform: String): Flow<List<MusicItem>> =
        musicDao.observeByPlatform(platform).map { entities -> entities.map { it.toModel(converters) } }

    suspend fun getById(id: String, platform: String): MusicItem? =
        musicDao.getById(id, platform)?.toModel(converters)

    suspend fun insert(item: MusicItem) =
        musicDao.insert(item.toEntity(converters))

    suspend fun insertAll(items: List<MusicItem>) =
        musicDao.insertAll(items.map { it.toEntity(converters) })

    suspend fun update(item: MusicItem) =
        musicDao.update(item.toEntity(converters))

    suspend fun delete(item: MusicItem) =
        musicDao.delete(item.toEntity(converters))

    suspend fun deleteByPlatform(platform: String) =
        musicDao.deleteByPlatform(platform)

    suspend fun count(): Int = musicDao.count()
}
```

- [ ] **Step 2: Create PlaylistRepository**

```kotlin
package com.zili.android.musicfreeandroid.data.repository

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.PlaylistDao
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistMusicCrossRef
import com.zili.android.musicfreeandroid.data.mapper.toEntity
import com.zili.android.musicfreeandroid.data.mapper.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val converters: Converters,
) {

    fun observeAllPlaylists(): Flow<List<Playlist>> =
        playlistDao.observeAllPlaylists().map { entities -> entities.map { it.toModel() } }

    suspend fun getPlaylistById(id: String): Playlist? =
        playlistDao.getPlaylistById(id)?.toModel()

    suspend fun createPlaylist(playlist: Playlist) {
        val now = System.currentTimeMillis()
        playlistDao.insertPlaylist(playlist.toEntity(createdAt = now, updatedAt = now))
    }

    suspend fun updatePlaylist(playlist: Playlist) {
        val existing = playlistDao.getPlaylistById(playlist.id) ?: return
        playlistDao.updatePlaylist(
            playlist.toEntity(createdAt = existing.createdAt, updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        val entity = playlistDao.getPlaylistById(playlist.id) ?: return
        playlistDao.deletePlaylist(entity)
    }

    fun observeMusicInPlaylist(playlistId: String): Flow<List<MusicItem>> =
        playlistDao.observeMusicInPlaylist(playlistId).map { entities ->
            entities.map { it.toModel(converters) }
        }

    suspend fun addMusicToPlaylist(playlistId: String, musicItem: MusicItem) {
        val nextOrder = playlistDao.maxSortOrderInPlaylist(playlistId) + 1
        playlistDao.insertCrossRef(
            PlaylistMusicCrossRef(
                playlistId = playlistId,
                musicId = musicItem.id,
                musicPlatform = musicItem.platform,
                sortOrder = nextOrder,
            )
        )
    }

    suspend fun removeMusicFromPlaylist(playlistId: String, musicItem: MusicItem) {
        playlistDao.removeMusicFromPlaylist(playlistId, musicItem.id, musicItem.platform)
    }

    suspend fun countMusicInPlaylist(playlistId: String): Int =
        playlistDao.countMusicInPlaylist(playlistId)
}
```

- [ ] **Step 3: Create PlayQueueRepository**

```kotlin
package com.zili.android.musicfreeandroid.data.repository

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.PlayQueueDao
import com.zili.android.musicfreeandroid.data.mapper.toMusicItem
import com.zili.android.musicfreeandroid.data.mapper.toPlayQueueEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayQueueRepository @Inject constructor(
    private val playQueueDao: PlayQueueDao,
    private val converters: Converters,
) {

    fun observeQueue(): Flow<List<MusicItem>> =
        playQueueDao.observeAll().map { entities ->
            entities.map { it.toMusicItem(converters) }
        }

    suspend fun getQueue(): List<MusicItem> =
        playQueueDao.getAll().map { it.toMusicItem(converters) }

    suspend fun saveQueue(items: List<MusicItem>) {
        val entities = items.mapIndexed { index, item ->
            item.toPlayQueueEntity(sortOrder = index, converters = converters)
        }
        playQueueDao.replaceAll(entities)
    }

    suspend fun clearQueue() = playQueueDao.clearAll()

    suspend fun count(): Int = playQueueDao.count()
}
```

- [ ] **Step 4: Verify build**

Run: `./gradlew :data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/repository/
git commit -m "feat(data): add MusicRepository, PlaylistRepository, PlayQueueRepository"
```

---

## Task 10: Hilt DI Module for Data Layer

**Files:**
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/di/DataModule.kt`

- [ ] **Step 1: Create DataModule**

```kotlin
package com.zili.android.musicfreeandroid.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.MusicDao
import com.zili.android.musicfreeandroid.data.db.dao.PlaylistDao
import com.zili.android.musicfreeandroid.data.db.dao.PlayQueueDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "musicfree.db")
            .build()

    @Provides
    fun provideMusicDao(db: AppDatabase): MusicDao = db.musicDao()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun providePlayQueueDao(db: AppDatabase): PlayQueueDao = db.playQueueDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides
    @Singleton
    fun provideConverters(): Converters = Converters()
}
```

- [ ] **Step 2: Verify full build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/di/
git commit -m "feat(data): add Hilt DataModule providing AppDatabase, DAOs, DataStore, Converters"
```

---

## Task 11: Room DAO Integration Tests

**Files:**
- Create: `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/db/dao/MusicDaoTest.kt`
- Create: `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/db/dao/PlaylistDaoTest.kt`
- Create: `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/db/dao/PlayQueueDaoTest.kt`

- [ ] **Step 1: Create MusicDaoTest**

```kotlin
package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.entity.MusicItemEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: MusicDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.musicDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun entity(id: String = "1", platform: String = "local", title: String = "Song") =
        MusicItemEntity(id, platform, title, "Artist", null, 180_000, null, null, null)

    @Test
    fun insertAndGetById() = runTest {
        val item = entity()
        dao.insert(item)
        val result = dao.getById("1", "local")
        assertNotNull(result)
        assertEquals("Song", result!!.title)
    }

    @Test
    fun insertReplaceOnConflict() = runTest {
        dao.insert(entity(title = "Original"))
        dao.insert(entity(title = "Updated"))
        val result = dao.getById("1", "local")
        assertEquals("Updated", result!!.title)
    }

    @Test
    fun deleteItem() = runTest {
        val item = entity()
        dao.insert(item)
        dao.delete(item)
        assertNull(dao.getById("1", "local"))
    }

    @Test
    fun observeAll() = runTest {
        dao.insert(entity("1", title = "B Song"))
        dao.insert(entity("2", title = "A Song"))
        val items = dao.observeAll().first()
        assertEquals(2, items.size)
        assertEquals("A Song", items[0].title) // sorted ASC
    }

    @Test
    fun observeByPlatform() = runTest {
        dao.insert(entity("1", "local"))
        dao.insert(entity("2", "netease"))
        dao.insert(entity("3", "local"))
        val locals = dao.observeByPlatform("local").first()
        assertEquals(2, locals.size)
    }

    @Test
    fun count() = runTest {
        assertEquals(0, dao.count())
        dao.insert(entity("1"))
        dao.insert(entity("2"))
        assertEquals(2, dao.count())
    }

    @Test
    fun deleteByPlatform() = runTest {
        dao.insert(entity("1", "local"))
        dao.insert(entity("2", "netease"))
        dao.insert(entity("3", "local"))
        dao.deleteByPlatform("local")
        assertEquals(1, dao.count())
        assertNotNull(dao.getById("2", "netease"))
    }

    @Test
    fun insertAll() = runTest {
        dao.insertAll(listOf(entity("1"), entity("2"), entity("3")))
        assertEquals(3, dao.count())
    }
}
```

- [ ] **Step 2: Create PlaylistDaoTest**

```kotlin
package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.entity.MusicItemEntity
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistEntity
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistMusicCrossRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var playlistDao: PlaylistDao
    private lateinit var musicDao: MusicDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        playlistDao = db.playlistDao()
        musicDao = db.musicDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun playlist(id: String = "pl1", name: String = "Test") =
        PlaylistEntity(id, name, null, 1000L, 2000L)

    private fun music(id: String, platform: String = "local") =
        MusicItemEntity(id, platform, "Song $id", "Artist", null, 180_000, null, null, null)

    @Test
    fun insertAndGetPlaylist() = runTest {
        playlistDao.insertPlaylist(playlist())
        val result = playlistDao.getPlaylistById("pl1")
        assertNotNull(result)
        assertEquals("Test", result!!.name)
    }

    @Test
    fun deletePlaylist() = runTest {
        val pl = playlist()
        playlistDao.insertPlaylist(pl)
        playlistDao.deletePlaylist(pl)
        assertNull(playlistDao.getPlaylistById("pl1"))
    }

    @Test
    fun observeAllPlaylists() = runTest {
        playlistDao.insertPlaylist(playlist("pl1", "First"))
        playlistDao.insertPlaylist(playlist("pl2", "Second"))
        val all = playlistDao.observeAllPlaylists().first()
        assertEquals(2, all.size)
    }

    @Test
    fun addAndObserveMusicInPlaylist() = runTest {
        playlistDao.insertPlaylist(playlist("pl1"))
        musicDao.insert(music("m1"))
        musicDao.insert(music("m2"))
        playlistDao.insertCrossRef(PlaylistMusicCrossRef("pl1", "m1", "local", 0))
        playlistDao.insertCrossRef(PlaylistMusicCrossRef("pl1", "m2", "local", 1))

        val items = playlistDao.observeMusicInPlaylist("pl1").first()
        assertEquals(2, items.size)
        assertEquals("Song m1", items[0].title) // order by sortOrder
    }

    @Test
    fun removeMusicFromPlaylist() = runTest {
        playlistDao.insertPlaylist(playlist("pl1"))
        musicDao.insert(music("m1"))
        playlistDao.insertCrossRef(PlaylistMusicCrossRef("pl1", "m1", "local", 0))
        playlistDao.removeMusicFromPlaylist("pl1", "m1", "local")
        assertEquals(0, playlistDao.countMusicInPlaylist("pl1"))
    }

    @Test
    fun cascadeDeletePlaylist_removesCrossRefs() = runTest {
        playlistDao.insertPlaylist(playlist("pl1"))
        musicDao.insert(music("m1"))
        playlistDao.insertCrossRef(PlaylistMusicCrossRef("pl1", "m1", "local", 0))
        playlistDao.deletePlaylist(playlist("pl1"))
        // Music item still exists
        assertNotNull(musicDao.getById("m1", "local"))
        // But cross-ref is gone
        assertEquals(0, playlistDao.countMusicInPlaylist("pl1"))
    }

    @Test
    fun maxSortOrderInPlaylist() = runTest {
        playlistDao.insertPlaylist(playlist("pl1"))
        assertEquals(-1, playlistDao.maxSortOrderInPlaylist("pl1")) // empty
        musicDao.insert(music("m1"))
        musicDao.insert(music("m2"))
        playlistDao.insertCrossRef(PlaylistMusicCrossRef("pl1", "m1", "local", 0))
        playlistDao.insertCrossRef(PlaylistMusicCrossRef("pl1", "m2", "local", 5))
        assertEquals(5, playlistDao.maxSortOrderInPlaylist("pl1"))
    }
}
```

- [ ] **Step 3: Create PlayQueueDaoTest**

```kotlin
package com.zili.android.musicfreeandroid.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.entity.PlayQueueEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayQueueDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PlayQueueDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.playQueueDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun queueEntity(musicId: String, sortOrder: Int) = PlayQueueEntity(
        musicId = musicId,
        musicPlatform = "local",
        title = "Song $musicId",
        artist = "Artist",
        album = null,
        duration = 180_000,
        url = null,
        artwork = null,
        qualitiesJson = null,
        sortOrder = sortOrder,
    )

    @Test
    fun insertAndGetAll() = runTest {
        dao.insertAll(listOf(queueEntity("1", 0), queueEntity("2", 1)))
        val all = dao.getAll()
        assertEquals(2, all.size)
        assertEquals("Song 1", all[0].title)
        assertEquals("Song 2", all[1].title)
    }

    @Test
    fun clearAll() = runTest {
        dao.insertAll(listOf(queueEntity("1", 0)))
        dao.clearAll()
        assertEquals(0, dao.count())
    }

    @Test
    fun observeAll() = runTest {
        dao.insertAll(listOf(queueEntity("b", 1), queueEntity("a", 0)))
        val items = dao.observeAll().first()
        assertEquals(2, items.size)
        assertEquals("Song a", items[0].title) // sorted by sortOrder
    }

    @Test
    fun count() = runTest {
        assertEquals(0, dao.count())
        dao.insertAll(listOf(queueEntity("1", 0), queueEntity("2", 1), queueEntity("3", 2)))
        assertEquals(3, dao.count())
    }
}
```

- [ ] **Step 4: Verify instrumented tests compile**

Run: `./gradlew :data:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL

Note: Running instrumented tests (`./gradlew :data:connectedDebugAndroidTest`) requires a device/emulator. Verify compilation here; run on device when available.

- [ ] **Step 5: Commit**

```bash
git add data/src/androidTest/
git commit -m "test(data): add Room DAO integration tests for MusicDao, PlaylistDao, PlayQueueDao"
```

---

## Task 12: Repository Integration Tests

**Files:**
- Create: `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/repository/MusicRepositoryTest.kt`
- Create: `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/repository/PlaylistRepositoryTest.kt`
- Create: `data/src/androidTest/java/com/zili/android/musicfreeandroid/data/repository/PlayQueueRepositoryTest.kt`

- [ ] **Step 1: Create MusicRepositoryTest**

```kotlin
package com.zili.android.musicfreeandroid.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.QualityInfo
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: MusicRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = MusicRepository(db.musicDao(), Converters())
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun musicItem(id: String, platform: String = "local") = MusicItem(
        id = id, platform = platform, title = "Song $id", artist = "Artist",
        album = null, duration = 180_000, url = null, artwork = null, qualities = null,
    )

    @Test
    fun insertAndGetById() = runTest {
        val item = musicItem("1")
        repo.insert(item)
        val result = repo.getById("1", "local")
        assertEquals(item, result)
    }

    @Test
    fun observeAll_emitsOnChange() = runTest {
        repo.observeAll().test {
            assertEquals(emptyList<MusicItem>(), awaitItem())
            repo.insert(musicItem("1"))
            assertEquals(1, awaitItem().size)
            repo.insert(musicItem("2"))
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteRemovesItem() = runTest {
        val item = musicItem("1")
        repo.insert(item)
        repo.delete(item)
        assertNull(repo.getById("1", "local"))
    }

    @Test
    fun insertPreservesQualities() = runTest {
        val item = musicItem("1").copy(
            qualities = mapOf(PlayQuality.HIGH to QualityInfo("url", 5_000_000L))
        )
        repo.insert(item)
        val result = repo.getById("1", "local")
        assertEquals(1, result!!.qualities!!.size)
        assertEquals("url", result.qualities!![PlayQuality.HIGH]!!.url)
    }
}
```

- [ ] **Step 2: Create PlaylistRepositoryTest**

```kotlin
package com.zili.android.musicfreeandroid.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var playlistRepo: PlaylistRepository
    private lateinit var musicRepo: MusicRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        val converters = Converters()
        playlistRepo = PlaylistRepository(db.playlistDao(), converters)
        musicRepo = MusicRepository(db.musicDao(), converters)
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun playlist(id: String = "pl1", name: String = "Test") =
        Playlist(id = id, name = name, coverUri = null)

    private fun musicItem(id: String) = MusicItem(
        id = id, platform = "local", title = "Song $id", artist = "Artist",
        album = null, duration = 180_000, url = null, artwork = null, qualities = null,
    )

    @Test
    fun createAndGetPlaylist() = runTest {
        playlistRepo.createPlaylist(playlist())
        val result = playlistRepo.getPlaylistById("pl1")
        assertNotNull(result)
        assertEquals("Test", result!!.name)
    }

    @Test
    fun observeAllPlaylists_emitsOnChange() = runTest {
        playlistRepo.observeAllPlaylists().test {
            assertEquals(emptyList<Playlist>(), awaitItem())
            playlistRepo.createPlaylist(playlist("pl1"))
            assertEquals(1, awaitItem().size)
            playlistRepo.createPlaylist(playlist("pl2"))
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun addAndObserveMusicInPlaylist() = runTest {
        playlistRepo.createPlaylist(playlist("pl1"))
        val m1 = musicItem("m1")
        val m2 = musicItem("m2")
        musicRepo.insert(m1)
        musicRepo.insert(m2)

        playlistRepo.observeMusicInPlaylist("pl1").test {
            assertEquals(emptyList<MusicItem>(), awaitItem())
            playlistRepo.addMusicToPlaylist("pl1", m1)
            assertEquals(1, awaitItem().size)
            playlistRepo.addMusicToPlaylist("pl1", m2)
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun removeMusicFromPlaylist() = runTest {
        playlistRepo.createPlaylist(playlist("pl1"))
        val m1 = musicItem("m1")
        musicRepo.insert(m1)
        playlistRepo.addMusicToPlaylist("pl1", m1)
        playlistRepo.removeMusicFromPlaylist("pl1", m1)
        assertEquals(0, playlistRepo.countMusicInPlaylist("pl1"))
    }

    @Test
    fun deletePlaylistCascades() = runTest {
        val pl = playlist("pl1")
        playlistRepo.createPlaylist(pl)
        musicRepo.insert(musicItem("m1"))
        playlistRepo.addMusicToPlaylist("pl1", musicItem("m1"))
        playlistRepo.deletePlaylist(pl)
        assertNull(playlistRepo.getPlaylistById("pl1"))
        // Music item still exists
        assertNotNull(musicRepo.getById("m1", "local"))
    }

    @Test
    fun updatePlaylist() = runTest {
        playlistRepo.createPlaylist(playlist("pl1", "Original"))
        playlistRepo.updatePlaylist(Playlist("pl1", "Renamed", "cover.jpg"))
        val updated = playlistRepo.getPlaylistById("pl1")
        assertEquals("Renamed", updated!!.name)
        assertEquals("cover.jpg", updated.coverUri)
    }
}
```

- [ ] **Step 3: Create PlayQueueRepositoryTest**

```kotlin
package com.zili.android.musicfreeandroid.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayQueueRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: PlayQueueRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = PlayQueueRepository(db.playQueueDao(), Converters())
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun musicItem(id: String) = MusicItem(
        id = id, platform = "local", title = "Song $id", artist = "Artist",
        album = null, duration = 180_000, url = null, artwork = null, qualities = null,
    )

    @Test
    fun saveAndGetQueue() = runTest {
        val items = listOf(musicItem("1"), musicItem("2"), musicItem("3"))
        repo.saveQueue(items)
        val result = repo.getQueue()
        assertEquals(3, result.size)
        assertEquals("Song 1", result[0].title)
        assertEquals("Song 3", result[2].title)
    }

    @Test
    fun saveQueueReplacesExisting() = runTest {
        repo.saveQueue(listOf(musicItem("1"), musicItem("2")))
        repo.saveQueue(listOf(musicItem("3")))
        val result = repo.getQueue()
        assertEquals(1, result.size)
        assertEquals("Song 3", result[0].title)
    }

    @Test
    fun clearQueue() = runTest {
        repo.saveQueue(listOf(musicItem("1")))
        repo.clearQueue()
        assertEquals(0, repo.count())
    }

    @Test
    fun observeQueue_emitsOnChange() = runTest {
        repo.observeQueue().test {
            assertEquals(emptyList<MusicItem>(), awaitItem())
            repo.saveQueue(listOf(musicItem("1"), musicItem("2")))
            val updated = awaitItem()
            assertEquals(2, updated.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 4: Verify instrumented tests compile**

Run: `./gradlew :data:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add data/src/androidTest/java/com/zili/android/musicfreeandroid/data/repository/
git commit -m "test(data): add Repository integration tests with Turbine Flow assertions"
```

---

## Task 13: Clean Up and Final Verification

**Files:**
- Remove: `data/src/main/java/com/zili/android/musicfreeandroid/data/.gitkeep`
- Remove: `data/src/test/java/com/zili/android/musicfreeandroid/data/.gitkeep`

- [ ] **Step 1: Remove .gitkeep placeholders**

```bash
rm -f data/src/main/java/com/zili/android/musicfreeandroid/data/.gitkeep
rm -f data/src/test/java/com/zili/android/musicfreeandroid/data/.gitkeep
```

- [ ] **Step 2: Run full build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run all unit tests**

Run: `./gradlew :core:testDebugUnitTest :data:testDebugUnitTest`
Expected: All tests PASS (model tests, mapper tests, DataStore tests).

- [ ] **Step 4: Run lint**

Run: `./gradlew :data:lint`
Expected: No errors.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore: remove .gitkeep placeholders from data module, verify full build"
```

---

## Summary

| Task | What | Tests |
|------|------|-------|
| 1 | Dependencies (Room, DataStore, Coroutines, Turbine) | Build verification |
| 2 | Core domain models (7 files) | 7 unit tests |
| 3 | Room entities (4 files) | Build verification |
| 4 | TypeConverters | Build verification |
| 5 | Entity ↔ Model mappers (3 files) | 7 unit tests |
| 6 | Room DAOs (3 files) | Build verification |
| 7 | AppDatabase | Build verification |
| 8 | DataStore AppPreferences | 10 unit tests |
| 9 | Repositories (3 files) | Build verification |
| 10 | Hilt DataModule | Full build verification |
| 11 | DAO integration tests | 19 instrumented tests |
| 12 | Repository integration tests | 14 instrumented tests |
| 13 | Cleanup & final verification | Full build + lint |

**Total new production files:** ~22
**Total new test files:** ~9
**Total test cases:** ~57

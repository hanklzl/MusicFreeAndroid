# Package Migration Backup Restore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the old-package migration release backup/restore feature so users can export a `.mfbackup` from `com.zili.android.musicfreeandroid` and later import it into the new package.

**Architecture:** The `:data` module owns backup archive format, file enumeration, restore staging, and pending restore records. `:app` calls a file-only startup applier from `Application.attachBaseContext()` before Hilt can instantiate Room/DataStore. `:feature:settings` owns the `SettingsType.Backup` UI, system document launchers, confirmation dialog, and ViewModel state.

**Tech Stack:** Kotlin, Jetpack Compose, Activity Result APIs, Hilt, Room, DataStore Preferences, kotlinx.serialization JSON, `ZipOutputStream` / `ZipInputStream`, JUnit/Robolectric.

---

## File Structure

- Create `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupManifest.kt`
  - Serializable archive manifest and file entry models.
- Create `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupArchivePaths.kt`
  - Path whitelist, safe entry validation, SHA-256 helpers, and target relative paths.
- Create `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupFileSetProvider.kt`
  - Enumerates `musicfree.db`, `app_preferences.preferences_pb`, `files/plugins`, `files/playlist_covers`, and `theme_background.*`.
- Create `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupArchiveWriter.kt`
  - Writes `.mfbackup` ZIP and manifest.
- Create `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupArchiveReader.kt`
  - Validates and extracts backup ZIP into staging.
- Create `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/PendingRestoreStore.kt`
  - Reads/writes `files/backup_restore/pending.json` and `last-status.json`.
- Create `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/StartupBackupRestore.kt`
  - Static file-only cold-start applier used by `Application.attachBaseContext()`.
- Create `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupRepository.kt`
  - Hilt-facing export/stage/register repository.
- Modify `data/src/main/java/com/zili/android/musicfreeandroid/data/di/DataModule.kt`
  - Provide `BackupRepository` and metadata provider.
- Modify `app/src/main/java/com/zili/android/musicfreeandroid/MusicFreeApplication.kt`
  - Call startup restore in `attachBaseContext()` before `onCreate()`.
- Modify `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModel.kt`
  - Add backup/restore UI state and actions.
- Modify `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt`
  - Replace backup placeholder with real backup screen and system document launchers.
- Modify `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/FidelityAnchors.kt`
  - Add stable backup/restore tags.
- Test files:
  - `data/src/test/java/com/zili/android/musicfreeandroid/data/backup/BackupArchivePathsTest.kt`
  - `data/src/test/java/com/zili/android/musicfreeandroid/data/backup/BackupArchiveWriterTest.kt`
  - `data/src/test/java/com/zili/android/musicfreeandroid/data/backup/BackupArchiveReaderTest.kt`
  - `data/src/test/java/com/zili/android/musicfreeandroid/data/backup/StartupBackupRestoreTest.kt`
  - Extend `feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModelTest.kt`
  - Extend `feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreenTest.kt`

## Task 1: Archive Manifest And Path Safety

**Files:**
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupManifest.kt`
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupArchivePaths.kt`
- Test: `data/src/test/java/com/zili/android/musicfreeandroid/data/backup/BackupArchivePathsTest.kt`

- [ ] **Step 1: Write failing path validation tests**

Create `data/src/test/java/com/zili/android/musicfreeandroid/data/backup/BackupArchivePathsTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class BackupArchivePathsTest {
    @Test
    fun `safe paths include only migration whitelist`() {
        assertTrue(BackupArchivePaths.isAllowedEntry("manifest.json"))
        assertTrue(BackupArchivePaths.isAllowedEntry("db/musicfree.db"))
        assertTrue(BackupArchivePaths.isAllowedEntry("db/musicfree.db-wal"))
        assertTrue(BackupArchivePaths.isAllowedEntry("db/musicfree.db-shm"))
        assertTrue(BackupArchivePaths.isAllowedEntry("datastore/app_preferences.preferences_pb"))
        assertTrue(BackupArchivePaths.isAllowedEntry("files/plugins/wy.js"))
        assertTrue(BackupArchivePaths.isAllowedEntry("files/playlist_covers/cover.jpg"))
        assertTrue(BackupArchivePaths.isAllowedEntry("files/theme_background.jpg"))

        assertFalse(BackupArchivePaths.isAllowedEntry("/absolute"))
        assertFalse(BackupArchivePaths.isAllowedEntry("../musicfree.db"))
        assertFalse(BackupArchivePaths.isAllowedEntry("files/../logan/x"))
        assertFalse(BackupArchivePaths.isAllowedEntry("files/logan/20260517"))
        assertFalse(BackupArchivePaths.isAllowedEntry("cache/updates/app.apk"))
        assertFalse(BackupArchivePaths.isAllowedEntry("files/plugins/sub/wy.js"))
    }

    @Test
    fun `sha256 returns lowercase hex`() {
        val actual = BackupArchivePaths.sha256(ByteArrayInputStream("abc".toByteArray()))

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            actual,
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests 'com.zili.android.musicfreeandroid.data.backup.BackupArchivePathsTest' --no-daemon
```

Expected: FAIL with unresolved reference `BackupArchivePaths`.

- [ ] **Step 3: Add manifest models**

Create `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupManifest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupManifest(
    val schemaVersion: Int,
    val sourcePackageName: String,
    val createdAt: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val databaseVersion: Int,
    val files: List<BackupManifestFile>,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupManifestFile(
    val path: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class BackupAppMetadata(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
)
```

- [ ] **Step 4: Add path whitelist and SHA-256 helper**

Create `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupArchivePaths.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.backup

import java.io.InputStream
import java.security.MessageDigest

object BackupArchivePaths {
    const val MANIFEST = "manifest.json"
    const val DB = "db/musicfree.db"
    const val DB_WAL = "db/musicfree.db-wal"
    const val DB_SHM = "db/musicfree.db-shm"
    const val DATASTORE = "datastore/app_preferences.preferences_pb"
    const val PLUGINS_PREFIX = "files/plugins/"
    const val PLAYLIST_COVERS_PREFIX = "files/playlist_covers/"
    const val THEME_BACKGROUND_PREFIX = "files/theme_background."

    val requiredEntries: Set<String> = setOf(DB)

    fun isAllowedEntry(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.startsWith("/")) return false
        if (path.contains("\\")) return false
        val parts = path.split('/')
        if (parts.any { it == ".." || it == "." || it.isBlank() }) return false

        return path == MANIFEST ||
            path == DB ||
            path == DB_WAL ||
            path == DB_SHM ||
            path == DATASTORE ||
            path.isDirectChildOf(PLUGINS_PREFIX, suffix = ".js") ||
            path.startsWith(PLAYLIST_COVERS_PREFIX) ||
            path.startsWith(THEME_BACKGROUND_PREFIX)
    }

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    private fun String.isDirectChildOf(prefix: String, suffix: String): Boolean {
        if (!startsWith(prefix) || !endsWith(suffix)) return false
        val child = removePrefix(prefix)
        return child.isNotBlank() && '/' !in child
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests 'com.zili.android.musicfreeandroid.data.backup.BackupArchivePathsTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit task 1**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupManifest.kt \
  data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupArchivePaths.kt \
  data/src/test/java/com/zili/android/musicfreeandroid/data/backup/BackupArchivePathsTest.kt
git commit -m "feat(backup): 定义迁移包 manifest 与路径白名单"
```

## Task 2: Export File Set And Archive Writer

**Files:**
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupFileSetProvider.kt`
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupArchiveWriter.kt`
- Test: `data/src/test/java/com/zili/android/musicfreeandroid/data/backup/BackupArchiveWriterTest.kt`

- [ ] **Step 1: Write failing writer tests**

Create `data/src/test/java/com/zili/android/musicfreeandroid/data/backup/BackupArchiveWriterTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipFile

class BackupArchiveWriterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `writer exports only whitelisted migration files`() {
        val root = tmp.newFolder("app-root")
        val filesDir = File(root, "files").apply { mkdirs() }
        val cacheDir = File(root, "cache").apply { mkdirs() }
        val databaseDir = File(root, "databases").apply { mkdirs() }
        File(databaseDir, "musicfree.db").writeText("db")
        File(databaseDir, "musicfree.db-wal").writeText("wal")
        File(filesDir, "datastore").mkdirs()
        File(filesDir, "datastore/app_preferences.preferences_pb").writeText("prefs")
        File(filesDir, "plugins").mkdirs()
        File(filesDir, "plugins/wy.js").writeText("plugin")
        File(filesDir, "playlist_covers").mkdirs()
        File(filesDir, "playlist_covers/cover.jpg").writeText("cover")
        File(filesDir, "theme_background.jpg").writeText("theme")
        File(filesDir, "logan").mkdirs()
        File(filesDir, "logan/20260517").writeText("log")
        File(cacheDir, "updates").mkdirs()
        File(cacheDir, "updates/app.apk").writeText("apk")

        val provider = BackupFileSetProvider(
            databaseFile = File(databaseDir, "musicfree.db"),
            filesDir = filesDir,
        )
        val archive = tmp.newFile("backup.mfbackup")
        FileOutputStream(archive).use { output ->
            BackupArchiveWriter(json).write(
                output = output,
                files = provider.collect(),
                metadata = BackupAppMetadata(
                    packageName = "com.zili.android.musicfreeandroid",
                    versionName = "1.0.2",
                    versionCode = 10002,
                ),
                databaseVersion = 11,
                createdAt = "2026-05-17T00:00:00Z",
            )
        }

        ZipFile(archive).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue(BackupArchivePaths.MANIFEST in names)
            assertTrue(BackupArchivePaths.DB in names)
            assertTrue(BackupArchivePaths.DB_WAL in names)
            assertTrue(BackupArchivePaths.DATASTORE in names)
            assertTrue("files/plugins/wy.js" in names)
            assertTrue("files/playlist_covers/cover.jpg" in names)
            assertTrue("files/theme_background.jpg" in names)
            assertFalse(names.any { it.startsWith("files/logan/") })
            assertFalse(names.any { it.startsWith("cache/") })

            val manifestText = zip.getInputStream(zip.getEntry(BackupArchivePaths.MANIFEST))
                .bufferedReader()
                .readText()
            val manifest = json.decodeFromString<BackupManifest>(manifestText)
            assertEquals(BackupManifest.CURRENT_SCHEMA_VERSION, manifest.schemaVersion)
            assertEquals("com.zili.android.musicfreeandroid", manifest.sourcePackageName)
            assertEquals("1.0.2", manifest.appVersionName)
            assertEquals(10002, manifest.appVersionCode)
            assertEquals(11, manifest.databaseVersion)
            assertNotNull(manifest.files.firstOrNull { it.path == BackupArchivePaths.DB })
            assertTrue(manifest.files.all { BackupArchivePaths.isAllowedEntry(it.path) })
            assertTrue(manifest.files.all { it.sha256.length == 64 })
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests 'com.zili.android.musicfreeandroid.data.backup.BackupArchiveWriterTest' --no-daemon
```

Expected: FAIL with unresolved references `BackupFileSetProvider` and `BackupArchiveWriter`.

- [ ] **Step 3: Add file-set provider**

Create `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupFileSetProvider.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.backup

import java.io.File

data class BackupSourceFile(
    val source: File,
    val archivePath: String,
)

class BackupFileSetProvider(
    private val databaseFile: File,
    private val filesDir: File,
) {
    fun collect(): List<BackupSourceFile> {
        val result = mutableListOf<BackupSourceFile>()
        addIfExists(result, databaseFile, BackupArchivePaths.DB)
        addIfExists(result, File(databaseFile.parentFile, "${databaseFile.name}-wal"), BackupArchivePaths.DB_WAL)
        addIfExists(result, File(databaseFile.parentFile, "${databaseFile.name}-shm"), BackupArchivePaths.DB_SHM)
        addIfExists(
            result,
            File(filesDir, "datastore/app_preferences.preferences_pb"),
            BackupArchivePaths.DATASTORE,
        )
        addDirectChildren(
            result = result,
            directory = File(filesDir, "plugins"),
            archivePrefix = BackupArchivePaths.PLUGINS_PREFIX,
            filter = { it.isFile && it.name.endsWith(".js") },
        )
        addRecursiveChildren(
            result = result,
            directory = File(filesDir, "playlist_covers"),
            archivePrefix = BackupArchivePaths.PLAYLIST_COVERS_PREFIX,
        )
        filesDir.listFiles { file -> file.isFile && file.name.startsWith("theme_background.") }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                addIfExists(result, file, "files/${file.name}")
            }
        return result.filter { BackupArchivePaths.isAllowedEntry(it.archivePath) }
            .sortedBy { it.archivePath }
    }

    private fun addIfExists(result: MutableList<BackupSourceFile>, file: File, archivePath: String) {
        if (file.isFile) result += BackupSourceFile(source = file, archivePath = archivePath)
    }

    private fun addDirectChildren(
        result: MutableList<BackupSourceFile>,
        directory: File,
        archivePrefix: String,
        filter: (File) -> Boolean,
    ) {
        directory.listFiles()
            ?.filter(filter)
            ?.sortedBy { it.name }
            ?.forEach { file -> result += BackupSourceFile(file, archivePrefix + file.name) }
    }

    private fun addRecursiveChildren(
        result: MutableList<BackupSourceFile>,
        directory: File,
        archivePrefix: String,
    ) {
        directory.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.relativeTo(directory).invariantSeparatorsPath }
            .forEach { file ->
                result += BackupSourceFile(
                    source = file,
                    archivePath = archivePrefix + file.relativeTo(directory).invariantSeparatorsPath,
                )
            }
    }
}
```

- [ ] **Step 4: Add archive writer**

Create `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupArchiveWriter.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.FileInputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveWriter(
    private val json: Json,
) {
    fun write(
        output: OutputStream,
        files: List<BackupSourceFile>,
        metadata: BackupAppMetadata,
        databaseVersion: Int,
        createdAt: String,
    ): BackupManifest {
        val manifestFiles = files.map { sourceFile ->
            BackupManifestFile(
                path = sourceFile.archivePath,
                sizeBytes = sourceFile.source.length(),
                sha256 = FileInputStream(sourceFile.source).use(BackupArchivePaths::sha256),
            )
        }
        val manifest = BackupManifest(
            schemaVersion = BackupManifest.CURRENT_SCHEMA_VERSION,
            sourcePackageName = metadata.packageName,
            createdAt = createdAt,
            appVersionName = metadata.versionName,
            appVersionCode = metadata.versionCode,
            databaseVersion = databaseVersion,
            files = manifestFiles,
        )
        ZipOutputStream(output.buffered()).use { zip ->
            files.forEach { file ->
                zip.putNextEntry(ZipEntry(file.archivePath))
                FileInputStream(file.source).use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry(BackupArchivePaths.MANIFEST))
            zip.write(json.encodeToString(manifest).toByteArray())
            zip.closeEntry()
        }
        return manifest
    }
}
```

- [ ] **Step 5: Run tests to verify writer passes**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests 'com.zili.android.musicfreeandroid.data.backup.BackupArchiveWriterTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit task 2**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupFileSetProvider.kt \
  data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupArchiveWriter.kt \
  data/src/test/java/com/zili/android/musicfreeandroid/data/backup/BackupArchiveWriterTest.kt
git commit -m "feat(backup): 导出迁移包文件集"
```

## Task 3: Archive Reader And Restore Staging

**Files:**
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupArchiveReader.kt`
- Test: `data/src/test/java/com/zili/android/musicfreeandroid/data/backup/BackupArchiveReaderTest.kt`

- [ ] **Step 1: Write failing reader validation tests**

Create `data/src/test/java/com/zili/android/musicfreeandroid/data/backup/BackupArchiveReaderTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveReaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `reader extracts valid archive to staging`() {
        val archive = tmp.newFile("valid.mfbackup")
        writeArchive(
            archive,
            mapOf(
                BackupArchivePaths.DB to "db",
                BackupArchivePaths.DATASTORE to "prefs",
                "files/plugins/wy.js" to "plugin",
            ),
        )
        val stagingRoot = tmp.newFolder("staging")

        val staged = BackupArchiveReader(json).extractAndValidate(
            input = FileInputStream(archive),
            stagingRoot = stagingRoot,
            id = "restore-1",
        )

        assertEquals("restore-1", staged.id)
        assertEquals("com.zili.android.musicfreeandroid", staged.manifest.sourcePackageName)
        assertTrue(File(staged.directory, BackupArchivePaths.DB).isFile)
        assertTrue(File(staged.directory, BackupArchivePaths.DATASTORE).isFile)
        assertTrue(File(staged.directory, "files/plugins/wy.js").isFile)
    }

    @Test
    fun `reader rejects path traversal`() {
        val archive = tmp.newFile("unsafe.mfbackup")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("../evil"))
            zip.write("evil".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(BackupArchivePaths.MANIFEST))
            zip.write("{}".toByteArray())
            zip.closeEntry()
        }

        val result = runCatching {
            BackupArchiveReader(json).extractAndValidate(
                input = FileInputStream(archive),
                stagingRoot = tmp.newFolder("staging-unsafe"),
                id = "restore-unsafe",
            )
        }

        assertTrue(result.exceptionOrNull() is BackupArchiveException)
    }

    @Test
    fun `reader rejects hash mismatch`() {
        val archive = tmp.newFile("bad-hash.mfbackup")
        val manifest = BackupManifest(
            schemaVersion = BackupManifest.CURRENT_SCHEMA_VERSION,
            sourcePackageName = "com.zili.android.musicfreeandroid",
            createdAt = "2026-05-17T00:00:00Z",
            appVersionName = "1.0.2",
            appVersionCode = 10002,
            databaseVersion = 11,
            files = listOf(
                BackupManifestFile(
                    path = BackupArchivePaths.DB,
                    sizeBytes = 2,
                    sha256 = "0".repeat(64),
                ),
            ),
        )
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry(BackupArchivePaths.DB))
            zip.write("db".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(BackupArchivePaths.MANIFEST))
            zip.write(json.encodeToString(manifest).toByteArray())
            zip.closeEntry()
        }

        val result = runCatching {
            BackupArchiveReader(json).extractAndValidate(
                input = FileInputStream(archive),
                stagingRoot = tmp.newFolder("staging-bad-hash"),
                id = "restore-bad-hash",
            )
        }

        assertTrue(result.exceptionOrNull() is BackupArchiveException)
    }

    private fun writeArchive(archive: File, entries: Map<String, String>) {
        val manifestFiles = entries.map { (path, value) ->
            val bytes = value.toByteArray()
            BackupManifestFile(
                path = path,
                sizeBytes = bytes.size.toLong(),
                sha256 = BackupArchivePaths.sha256(ByteArrayInputStream(bytes)),
            )
        }
        val manifest = BackupManifest(
            schemaVersion = BackupManifest.CURRENT_SCHEMA_VERSION,
            sourcePackageName = "com.zili.android.musicfreeandroid",
            createdAt = "2026-05-17T00:00:00Z",
            appVersionName = "1.0.2",
            appVersionCode = 10002,
            databaseVersion = 11,
            files = manifestFiles,
        )
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            entries.forEach { (path, value) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(value.toByteArray())
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry(BackupArchivePaths.MANIFEST))
            zip.write(json.encodeToString(manifest).toByteArray())
            zip.closeEntry()
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests 'com.zili.android.musicfreeandroid.data.backup.BackupArchiveReaderTest' --no-daemon
```

Expected: FAIL with unresolved references `BackupArchiveReader` and `BackupArchiveException`.

- [ ] **Step 3: Add reader and staging result**

Create `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupArchiveReader.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class StagedRestore(
    val id: String,
    val directory: File,
    val manifest: BackupManifest,
)

class BackupArchiveException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class BackupArchiveReader(
    private val json: Json,
) {
    fun extractAndValidate(input: InputStream, stagingRoot: File, id: String): StagedRestore {
        val stagingDir = File(stagingRoot, id)
        if (stagingDir.exists()) stagingDir.deleteRecursively()
        stagingDir.mkdirs()

        val seen = mutableSetOf<String>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (!BackupArchivePaths.isAllowedEntry(name)) {
                    stagingDir.deleteRecursively()
                    throw BackupArchiveException("备份包包含非法路径：$name")
                }
                if (!seen.add(name)) {
                    stagingDir.deleteRecursively()
                    throw BackupArchiveException("备份包包含重复路径：$name")
                }
                val target = File(stagingDir, name)
                target.parentFile?.mkdirs()
                target.outputStream().use { output -> zip.copyTo(output) }
                zip.closeEntry()
            }
        }

        val manifestFile = File(stagingDir, BackupArchivePaths.MANIFEST)
        if (!manifestFile.isFile) {
            stagingDir.deleteRecursively()
            throw BackupArchiveException("备份包缺少 manifest.json")
        }
        val manifest = readManifest(manifestFile, stagingDir)
        validateManifest(manifest, stagingDir, seen)
        return StagedRestore(id = id, directory = stagingDir, manifest = manifest)
    }

    private fun readManifest(file: File, stagingDir: File): BackupManifest {
        return try {
            json.decodeFromString<BackupManifest>(file.readText())
        } catch (error: SerializationException) {
            stagingDir.deleteRecursively()
            throw BackupArchiveException("备份包 manifest 无法解析", error)
        }
    }

    private fun validateManifest(manifest: BackupManifest, stagingDir: File, seen: Set<String>) {
        if (manifest.schemaVersion != BackupManifest.CURRENT_SCHEMA_VERSION) {
            stagingDir.deleteRecursively()
            throw BackupArchiveException("不支持的备份格式版本：${manifest.schemaVersion}")
        }
        val manifestPaths = manifest.files.map { it.path }
        if (manifestPaths.toSet().size != manifestPaths.size) {
            stagingDir.deleteRecursively()
            throw BackupArchiveException("manifest 包含重复文件")
        }
        BackupArchivePaths.requiredEntries.forEach { required ->
            if (required !in manifestPaths || required !in seen) {
                stagingDir.deleteRecursively()
                throw BackupArchiveException("备份包缺少必要文件：$required")
            }
        }
        manifest.files.forEach { file ->
            if (!BackupArchivePaths.isAllowedEntry(file.path)) {
                stagingDir.deleteRecursively()
                throw BackupArchiveException("manifest 包含非法路径：${file.path}")
            }
            if (file.path !in seen) {
                stagingDir.deleteRecursively()
                throw BackupArchiveException("manifest 文件未在备份包中找到：${file.path}")
            }
            val actual = File(stagingDir, file.path)
            if (actual.length() != file.sizeBytes) {
                stagingDir.deleteRecursively()
                throw BackupArchiveException("备份文件大小不匹配：${file.path}")
            }
            val hash = FileInputStream(actual).use(BackupArchivePaths::sha256)
            if (!hash.equals(file.sha256, ignoreCase = true)) {
                stagingDir.deleteRecursively()
                throw BackupArchiveException("备份文件校验失败：${file.path}")
            }
        }
    }
}
```

- [ ] **Step 4: Run reader tests**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests 'com.zili.android.musicfreeandroid.data.backup.BackupArchiveReaderTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit task 3**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupArchiveReader.kt \
  data/src/test/java/com/zili/android/musicfreeandroid/data/backup/BackupArchiveReaderTest.kt
git commit -m "feat(backup): 校验并暂存迁移包"
```

## Task 4: Pending Restore Store And Cold Startup Applier

**Files:**
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/PendingRestoreStore.kt`
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/StartupBackupRestore.kt`
- Test: `data/src/test/java/com/zili/android/musicfreeandroid/data/backup/StartupBackupRestoreTest.kt`

- [ ] **Step 1: Write failing cold-start applier tests**

Create `data/src/test/java/com/zili/android/musicfreeandroid/data/backup/StartupBackupRestoreTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.backup

import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class StartupBackupRestoreTest {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Test
    fun `startup restore moves staged files into app private locations`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.filesDir, "startup-test-${UUID.randomUUID()}").apply { mkdirs() }
        val layout = BackupPrivateLayout(
            filesDir = File(root, "files").apply { mkdirs() },
            databaseFile = File(root, "databases/musicfree.db"),
        )
        layout.databaseFile.parentFile?.mkdirs()
        layout.databaseFile.writeText("old-db")
        File(layout.filesDir, "datastore").mkdirs()
        File(layout.filesDir, "datastore/app_preferences.preferences_pb").writeText("old-prefs")

        val staging = File(layout.filesDir, "backup_restore/staging/restore-1").apply { mkdirs() }
        File(staging, BackupArchivePaths.DB).apply { parentFile?.mkdirs(); writeText("new-db") }
        File(staging, BackupArchivePaths.DATASTORE).apply { parentFile?.mkdirs(); writeText("new-prefs") }
        File(staging, "files/plugins/wy.js").apply { parentFile?.mkdirs(); writeText("plugin") }
        PendingRestoreStore(json, layout.filesDir).writePending(
            PendingRestoreRecord(id = "restore-1", stagingRelativePath = "backup_restore/staging/restore-1"),
        )

        val result = StartupBackupRestore.applyIfPending(layout, json)

        assertTrue(result.applied)
        assertEquals("new-db", layout.databaseFile.readText())
        assertEquals("new-prefs", File(layout.filesDir, "datastore/app_preferences.preferences_pb").readText())
        assertEquals("plugin", File(layout.filesDir, "plugins/wy.js").readText())
        assertFalse(PendingRestoreStore(json, layout.filesDir).pendingFile().exists())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests 'com.zili.android.musicfreeandroid.data.backup.StartupBackupRestoreTest' --no-daemon
```

Expected: FAIL with unresolved references `BackupPrivateLayout`, `PendingRestoreStore`, `PendingRestoreRecord`, and `StartupBackupRestore`.

- [ ] **Step 3: Add pending restore store**

Create `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/PendingRestoreStore.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PendingRestoreRecord(
    val id: String,
    val stagingRelativePath: String,
)

@Serializable
data class RestoreStatusRecord(
    val id: String,
    val applied: Boolean,
    val message: String,
)

class PendingRestoreStore(
    private val json: Json,
    private val filesDir: File,
) {
    fun pendingFile(): File = File(rootDir(), "pending.json")

    fun statusFile(): File = File(rootDir(), "last-status.json")

    fun stagingRoot(): File = File(rootDir(), "staging").apply { mkdirs() }

    fun restoreBackupRoot(id: String): File = File(rootDir(), "restore-backup/$id")

    fun writePending(record: PendingRestoreRecord) {
        rootDir().mkdirs()
        pendingFile().writeText(json.encodeToString(PendingRestoreRecord.serializer(), record))
    }

    fun readPending(): PendingRestoreRecord? {
        val file = pendingFile()
        if (!file.isFile) return null
        return runCatching {
            json.decodeFromString(PendingRestoreRecord.serializer(), file.readText())
        }.getOrNull()
    }

    fun clearPending() {
        pendingFile().delete()
    }

    fun writeStatus(status: RestoreStatusRecord) {
        rootDir().mkdirs()
        statusFile().writeText(json.encodeToString(RestoreStatusRecord.serializer(), status))
    }

    private fun rootDir(): File = File(filesDir, "backup_restore")
}
```

- [ ] **Step 4: Add startup applier**

Create `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/StartupBackupRestore.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.backup

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

data class BackupPrivateLayout(
    val filesDir: File,
    val databaseFile: File,
) {
    companion object {
        fun from(context: Context): BackupPrivateLayout =
            BackupPrivateLayout(
                filesDir = context.filesDir,
                databaseFile = context.getDatabasePath("musicfree.db"),
            )
    }
}

data class StartupRestoreResult(
    val applied: Boolean,
    val message: String,
)

object StartupBackupRestore {
    fun applyIfPending(
        layout: BackupPrivateLayout,
        json: Json = Json { ignoreUnknownKeys = true },
    ): StartupRestoreResult {
        val store = PendingRestoreStore(json, layout.filesDir)
        val pending = store.readPending() ?: return StartupRestoreResult(applied = false, message = "no pending restore")
        val staging = File(layout.filesDir, pending.stagingRelativePath)
        if (!staging.isDirectory) {
            val status = RestoreStatusRecord(pending.id, applied = false, message = "staging missing")
            store.writeStatus(status)
            return StartupRestoreResult(applied = false, message = status.message)
        }
        val backupRoot = store.restoreBackupRoot(pending.id)
        return try {
            moveCurrentTargetsToBackup(layout, backupRoot)
            moveStagedTargetsToCurrent(staging, layout)
            store.clearPending()
            backupRoot.deleteRecursively()
            staging.deleteRecursively()
            val status = RestoreStatusRecord(pending.id, applied = true, message = "restore applied")
            store.writeStatus(status)
            StartupRestoreResult(applied = true, message = status.message)
        } catch (error: Throwable) {
            rollback(layout, backupRoot)
            val status = RestoreStatusRecord(pending.id, applied = false, message = error.message ?: "restore failed")
            store.writeStatus(status)
            StartupRestoreResult(applied = false, message = status.message)
        }
    }

    fun applyIfPending(context: Context): StartupRestoreResult =
        applyIfPending(BackupPrivateLayout.from(context))

    private fun moveCurrentTargetsToBackup(layout: BackupPrivateLayout, backupRoot: File) {
        moveIfExists(layout.databaseFile, File(backupRoot, BackupArchivePaths.DB))
        moveIfExists(File(layout.databaseFile.parentFile, "${layout.databaseFile.name}-wal"), File(backupRoot, BackupArchivePaths.DB_WAL))
        moveIfExists(File(layout.databaseFile.parentFile, "${layout.databaseFile.name}-shm"), File(backupRoot, BackupArchivePaths.DB_SHM))
        moveIfExists(File(layout.filesDir, "datastore/app_preferences.preferences_pb"), File(backupRoot, BackupArchivePaths.DATASTORE))
        moveIfExists(File(layout.filesDir, "plugins"), File(backupRoot, "files/plugins"))
        moveIfExists(File(layout.filesDir, "playlist_covers"), File(backupRoot, "files/playlist_covers"))
        layout.filesDir.listFiles { file -> file.isFile && file.name.startsWith("theme_background.") }
            ?.forEach { file -> moveIfExists(file, File(backupRoot, "files/${file.name}")) }
    }

    private fun moveStagedTargetsToCurrent(staging: File, layout: BackupPrivateLayout) {
        moveIfExists(File(staging, BackupArchivePaths.DB), layout.databaseFile)
        moveIfExists(File(staging, BackupArchivePaths.DB_WAL), File(layout.databaseFile.parentFile, "${layout.databaseFile.name}-wal"))
        moveIfExists(File(staging, BackupArchivePaths.DB_SHM), File(layout.databaseFile.parentFile, "${layout.databaseFile.name}-shm"))
        moveIfExists(File(staging, BackupArchivePaths.DATASTORE), File(layout.filesDir, "datastore/app_preferences.preferences_pb"))
        moveIfExists(File(staging, "files/plugins"), File(layout.filesDir, "plugins"))
        moveIfExists(File(staging, "files/playlist_covers"), File(layout.filesDir, "playlist_covers"))
        staging.resolve("files").listFiles { file -> file.isFile && file.name.startsWith("theme_background.") }
            ?.forEach { file -> moveIfExists(file, File(layout.filesDir, file.name)) }
    }

    private fun rollback(layout: BackupPrivateLayout, backupRoot: File) {
        moveIfExists(File(backupRoot, BackupArchivePaths.DB), layout.databaseFile)
        moveIfExists(File(backupRoot, BackupArchivePaths.DATASTORE), File(layout.filesDir, "datastore/app_preferences.preferences_pb"))
        moveIfExists(File(backupRoot, "files/plugins"), File(layout.filesDir, "plugins"))
        moveIfExists(File(backupRoot, "files/playlist_covers"), File(layout.filesDir, "playlist_covers"))
    }

    private fun moveIfExists(source: File, target: File) {
        if (!source.exists()) return
        target.parentFile?.mkdirs()
        if (target.exists()) target.deleteRecursively()
        if (!source.renameTo(target)) {
            source.copyRecursively(target, overwrite = true)
            source.deleteRecursively()
        }
    }
}
```

- [ ] **Step 5: Run cold-start tests**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests 'com.zili.android.musicfreeandroid.data.backup.StartupBackupRestoreTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit task 4**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/backup/PendingRestoreStore.kt \
  data/src/main/java/com/zili/android/musicfreeandroid/data/backup/StartupBackupRestore.kt \
  data/src/test/java/com/zili/android/musicfreeandroid/data/backup/StartupBackupRestoreTest.kt
git commit -m "feat(backup): 支持冷启动恢复待导入数据"
```

## Task 5: Backup Repository, Hilt Wiring, And Startup Hook

**Files:**
- Create: `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupRepository.kt`
- Modify: `data/src/main/java/com/zili/android/musicfreeandroid/data/di/DataModule.kt`
- Modify: `app/src/main/java/com/zili/android/musicfreeandroid/MusicFreeApplication.kt`
- Test: extend data backup tests through repository unit tests in `data/src/test/java/com/zili/android/musicfreeandroid/data/backup/BackupRepositoryTest.kt`

- [ ] **Step 1: Write failing repository test**

Create `data/src/test/java/com/zili/android/musicfreeandroid/data/backup/BackupRepositoryTest.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.backup

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `export writes archive to uri`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = tmp.newFolder("repo-root")
        val filesDir = File(root, "files").apply { mkdirs() }
        val db = File(root, "databases/musicfree.db").apply {
            parentFile?.mkdirs()
            writeText("db")
        }
        val archive = tmp.newFile("repo-backup.mfbackup")
        val repository = DefaultBackupRepository(
            context = context,
            contentResolver = context.contentResolver,
            databaseCheckpoint = { },
            layout = BackupPrivateLayout(filesDir = filesDir, databaseFile = db),
            appMetadata = BackupAppMetadata("com.zili.android.musicfreeandroid", "1.0.2", 10002),
            databaseVersion = 11,
            json = Json { ignoreUnknownKeys = true },
        )

        repository.exportTo(Uri.fromFile(archive))

        assertTrue(archive.length() > 0)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests 'com.zili.android.musicfreeandroid.data.backup.BackupRepositoryTest' --no-daemon
```

Expected: FAIL with unresolved references `DefaultBackupRepository` and `exportTo`.

- [ ] **Step 3: Add repository**

Create `data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupRepository.kt`:

```kotlin
package com.zili.android.musicfreeandroid.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.MfLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

interface BackupRepository {
    suspend fun exportTo(uri: Uri): BackupManifest
    suspend fun stageRestoreFrom(uri: Uri): StagedRestore
    suspend fun registerPendingRestore(stagedRestore: StagedRestore)
}

class DefaultBackupRepository(
    private val context: Context,
    private val contentResolver: ContentResolver,
    private val databaseCheckpoint: () -> Unit,
    private val layout: BackupPrivateLayout,
    private val appMetadata: BackupAppMetadata,
    private val databaseVersion: Int,
    private val json: Json,
) : BackupRepository {
    override suspend fun exportTo(uri: Uri): BackupManifest = withContext(Dispatchers.IO) {
        MfLog.detail(LogCategory.SETTINGS, "backup_export_started")
        try {
            databaseCheckpoint()
            val files = BackupFileSetProvider(
                databaseFile = layout.databaseFile,
                filesDir = layout.filesDir,
            ).collect()
            val manifest = contentResolver.openOutputStream(uri)?.use { output ->
                BackupArchiveWriter(json).write(
                    output = output,
                    files = files,
                    metadata = appMetadata,
                    databaseVersion = databaseVersion,
                    createdAt = OffsetDateTime.now(ZoneOffset.UTC).toString(),
                )
            } ?: throw BackupArchiveException("无法打开备份文件写入位置")
            MfLog.detail(
                LogCategory.SETTINGS,
                "backup_export_succeeded",
                mapOf("fileCount" to manifest.files.size),
            )
            manifest
        } catch (error: Throwable) {
            MfLog.error(LogCategory.SETTINGS, "backup_export_failed", error)
            throw error
        }
    }

    override suspend fun stageRestoreFrom(uri: Uri): StagedRestore = withContext(Dispatchers.IO) {
        MfLog.detail(LogCategory.SETTINGS, "backup_restore_stage_started")
        val id = "restore-${UUID.randomUUID()}"
        val input = contentResolver.openInputStream(uri)
            ?: throw BackupArchiveException("无法打开备份文件")
        try {
            val staged = input.use {
                BackupArchiveReader(json).extractAndValidate(
                    input = it,
                    stagingRoot = PendingRestoreStore(json, layout.filesDir).stagingRoot(),
                    id = id,
                )
            }
            MfLog.detail(
                LogCategory.SETTINGS,
                "backup_restore_stage_succeeded",
                mapOf("fileCount" to staged.manifest.files.size),
            )
            staged
        } catch (error: Throwable) {
            MfLog.error(LogCategory.SETTINGS, "backup_restore_stage_failed", error)
            throw error
        }
    }

    override suspend fun registerPendingRestore(stagedRestore: StagedRestore) = withContext(Dispatchers.IO) {
        PendingRestoreStore(json, layout.filesDir).writePending(
            PendingRestoreRecord(
                id = stagedRestore.id,
                stagingRelativePath = stagedRestore.directory.relativeTo(layout.filesDir).invariantSeparatorsPath,
            ),
        )
        MfLog.detail(LogCategory.SETTINGS, "backup_restore_pending_registered")
    }
}

fun AppDatabase.checkpointWal() {
    openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
}
```

- [ ] **Step 4: Wire Hilt providers**

Modify `data/src/main/java/com/zili/android/musicfreeandroid/data/di/DataModule.kt`.

Add imports:

```kotlin
import com.zili.android.musicfreeandroid.data.backup.BackupAppMetadata
import com.zili.android.musicfreeandroid.data.backup.BackupPrivateLayout
import com.zili.android.musicfreeandroid.data.backup.BackupRepository
import com.zili.android.musicfreeandroid.data.backup.DefaultBackupRepository
import com.zili.android.musicfreeandroid.data.backup.checkpointWal
import kotlinx.serialization.json.Json
```

Add providers inside `object DataModule`:

```kotlin
    @Provides
    @Singleton
    fun provideBackupJson(): Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    @Provides
    @Singleton
    fun provideBackupAppMetadata(@ApplicationContext context: Context): BackupAppMetadata {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        return BackupAppMetadata(
            packageName = context.packageName,
            versionName = packageInfo.versionName ?: "0",
            versionCode = versionCode,
        )
    }

    @Provides
    @Singleton
    fun provideBackupRepository(
        @ApplicationContext context: Context,
        contentResolver: ContentResolver,
        db: AppDatabase,
        metadata: BackupAppMetadata,
        json: Json,
    ): BackupRepository =
        DefaultBackupRepository(
            context = context,
            contentResolver = contentResolver,
            databaseCheckpoint = { db.checkpointWal() },
            layout = BackupPrivateLayout.from(context),
            appMetadata = metadata,
            databaseVersion = 11,
            json = json,
        )
```

- [ ] **Step 5: Add startup hook before Hilt-created data is used**

Modify `app/src/main/java/com/zili/android/musicfreeandroid/MusicFreeApplication.kt`.

Add imports:

```kotlin
import android.content.Context
import com.zili.android.musicfreeandroid.data.backup.StartupBackupRestore
```

Add override before `onCreate()`:

```kotlin
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        StartupBackupRestore.applyIfPending(this)
    }
```

Keep existing `onCreate()` order unchanged.

- [ ] **Step 6: Run repository and app compile checks**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests 'com.zili.android.musicfreeandroid.data.backup.BackupRepositoryTest' --no-daemon
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: both PASS.

- [ ] **Step 7: Commit task 5**

```bash
git add data/src/main/java/com/zili/android/musicfreeandroid/data/backup/BackupRepository.kt \
  data/src/main/java/com/zili/android/musicfreeandroid/data/di/DataModule.kt \
  app/src/main/java/com/zili/android/musicfreeandroid/MusicFreeApplication.kt \
  data/src/test/java/com/zili/android/musicfreeandroid/data/backup/BackupRepositoryTest.kt
git commit -m "feat(backup): 接入备份仓库与启动恢复"
```

## Task 6: Settings ViewModel Backup State

**Files:**
- Modify: `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModel.kt`
- Test: extend `feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Write failing ViewModel tests**

Append tests to `SettingsViewModelTest`:

```kotlin
    @Test
    fun `backup export publishes success state`() = runTest(mainDispatcherRule.dispatcher) {
        val backupRepository = FakeBackupRepository()
        val viewModel = createViewModel(createAppPreferences(), backupRepository = backupRepository)

        viewModel.createBackup(android.net.Uri.parse("file:///tmp/backup.mfbackup"))
        advanceUntilIdle()

        assertEquals(1, backupRepository.exportCalls)
        assertEquals("备份已创建", viewModel.backupRestoreUiState.value.message)
        assertFalse(viewModel.backupRestoreUiState.value.inProgress)
    }

    @Test
    fun `restore validation exposes confirmation state and confirm registers pending restore`() =
        runTest(mainDispatcherRule.dispatcher) {
            val backupRepository = FakeBackupRepository()
            val viewModel = createViewModel(createAppPreferences(), backupRepository = backupRepository)

            viewModel.validateRestore(android.net.Uri.parse("file:///tmp/backup.mfbackup"))
            advanceUntilIdle()

            assertTrue(viewModel.backupRestoreUiState.value.restoreConfirmationVisible)
            assertEquals("com.zili.android.musicfreeandroid", viewModel.backupRestoreUiState.value.restoreSourcePackageName)

            viewModel.confirmRestore()
            advanceUntilIdle()

            assertEquals(1, backupRepository.registerCalls)
            assertEquals("已登记恢复，重启应用后生效", viewModel.backupRestoreUiState.value.message)
            assertFalse(viewModel.backupRestoreUiState.value.restoreConfirmationVisible)
        }
```

Update helper signatures:

```kotlin
    private fun createViewModel(
        appPreferences: AppPreferences,
        exporter: FeedbackLogExporterContract = createFakeExporter(),
        pluginManager: PluginManager = mock(),
        cacheCleaner: SettingsCacheCleaner = mock(),
        backupRepository: BackupRepository = FakeBackupRepository(),
    ): SettingsViewModel {
        return SettingsViewModel(appPreferences, exporter, pluginManager, cacheCleaner, backupRepository)
    }
```

Add fake class:

```kotlin
    private class FakeBackupRepository : BackupRepository {
        var exportCalls = 0
        var stageCalls = 0
        var registerCalls = 0

        private val manifest = BackupManifest(
            schemaVersion = BackupManifest.CURRENT_SCHEMA_VERSION,
            sourcePackageName = "com.zili.android.musicfreeandroid",
            createdAt = "2026-05-17T00:00:00Z",
            appVersionName = "1.0.2",
            appVersionCode = 10002,
            databaseVersion = 11,
            files = listOf(BackupManifestFile(BackupArchivePaths.DB, 2, "0".repeat(64))),
        )

        override suspend fun exportTo(uri: android.net.Uri): BackupManifest {
            exportCalls++
            return manifest
        }

        override suspend fun stageRestoreFrom(uri: android.net.Uri): StagedRestore {
            stageCalls++
            return StagedRestore(
                id = "restore-1",
                directory = java.io.File("restore-1"),
                manifest = manifest,
            )
        }

        override suspend fun registerPendingRestore(stagedRestore: StagedRestore) {
            registerCalls++
        }
    }
```

Add imports:

```kotlin
import com.zili.android.musicfreeandroid.data.backup.BackupArchivePaths
import com.zili.android.musicfreeandroid.data.backup.BackupManifest
import com.zili.android.musicfreeandroid.data.backup.BackupManifestFile
import com.zili.android.musicfreeandroid.data.backup.BackupRepository
import com.zili.android.musicfreeandroid.data.backup.StagedRestore
```

- [ ] **Step 2: Run ViewModel test to verify it fails**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests 'com.zili.android.musicfreeandroid.feature.settings.SettingsViewModelTest' --no-daemon
```

Expected: FAIL with constructor mismatch and unresolved `backupRestoreUiState`.

- [ ] **Step 3: Add backup UI state and ViewModel actions**

Modify `SettingsViewModel.kt`.

Add imports:

```kotlin
import android.net.Uri
import com.zili.android.musicfreeandroid.data.backup.BackupRepository
import com.zili.android.musicfreeandroid.data.backup.StagedRestore
```

Add data class near other UI states:

```kotlin
data class BackupRestoreUiState(
    val inProgress: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
    val restoreConfirmationVisible: Boolean = false,
    val restoreSourcePackageName: String? = null,
    val restoreAppVersionName: String? = null,
    val restoreFileCount: Int = 0,
)
```

Update constructor:

```kotlin
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val feedbackLogExporter: FeedbackLogExporterContract,
    private val pluginManager: PluginManager,
    private val cacheCleaner: SettingsCacheCleaner,
    private val backupRepository: BackupRepository,
) : ViewModel() {
```

Add state:

```kotlin
    private val _backupRestoreUiState = MutableStateFlow(BackupRestoreUiState())
    val backupRestoreUiState: StateFlow<BackupRestoreUiState> = _backupRestoreUiState.asStateFlow()
    private var stagedRestore: StagedRestore? = null
```

Add actions before `private companion object`:

```kotlin
    fun createBackup(uri: Uri) {
        viewModelScope.launch {
            _backupRestoreUiState.update { BackupRestoreUiState(inProgress = true, message = "正在创建备份") }
            try {
                backupRepository.exportTo(uri)
                _backupRestoreUiState.update { BackupRestoreUiState(message = "备份已创建") }
            } catch (error: CancellationException) {
                _backupRestoreUiState.update { BackupRestoreUiState() }
                throw error
            } catch (error: Throwable) {
                MfLog.error(LogCategory.SETTINGS, "backup_export_failed", error)
                _backupRestoreUiState.update {
                    BackupRestoreUiState(errorMessage = error.localizedMessage ?: "创建备份失败")
                }
            }
        }
    }

    fun validateRestore(uri: Uri) {
        viewModelScope.launch {
            _backupRestoreUiState.update { BackupRestoreUiState(inProgress = true, message = "正在校验备份") }
            try {
                val staged = backupRepository.stageRestoreFrom(uri)
                stagedRestore = staged
                _backupRestoreUiState.update {
                    BackupRestoreUiState(
                        restoreConfirmationVisible = true,
                        restoreSourcePackageName = staged.manifest.sourcePackageName,
                        restoreAppVersionName = staged.manifest.appVersionName,
                        restoreFileCount = staged.manifest.files.size,
                    )
                }
            } catch (error: CancellationException) {
                _backupRestoreUiState.update { BackupRestoreUiState() }
                throw error
            } catch (error: Throwable) {
                MfLog.error(LogCategory.SETTINGS, "backup_restore_validate_failed", error)
                _backupRestoreUiState.update {
                    BackupRestoreUiState(errorMessage = error.localizedMessage ?: "备份校验失败")
                }
            }
        }
    }

    fun confirmRestore() {
        val staged = stagedRestore ?: return
        viewModelScope.launch {
            _backupRestoreUiState.update { it.copy(inProgress = true) }
            try {
                backupRepository.registerPendingRestore(staged)
                stagedRestore = null
                _backupRestoreUiState.update {
                    BackupRestoreUiState(message = "已登记恢复，重启应用后生效")
                }
            } catch (error: Throwable) {
                MfLog.error(LogCategory.SETTINGS, "backup_restore_register_failed", error)
                _backupRestoreUiState.update {
                    BackupRestoreUiState(errorMessage = error.localizedMessage ?: "登记恢复失败")
                }
            }
        }
    }

    fun dismissRestoreConfirmation() {
        stagedRestore = null
        _backupRestoreUiState.update { it.copy(restoreConfirmationVisible = false) }
    }

    fun clearBackupRestoreMessage() {
        _backupRestoreUiState.update { it.copy(message = null, errorMessage = null) }
    }
```

- [ ] **Step 4: Run settings ViewModel tests**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests 'com.zili.android.musicfreeandroid.feature.settings.SettingsViewModelTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit task 6**

```bash
git add feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModel.kt \
  feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModelTest.kt
git commit -m "feat(settings): 接入备份恢复状态"
```

## Task 7: Backup Settings UI

**Files:**
- Modify: `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/FidelityAnchors.kt`
- Modify: `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt`
- Test: extend `feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreenTest.kt`

- [ ] **Step 1: Write failing UI test**

Add imports in `SettingsScreenTest.kt`:

```kotlin
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import com.zili.android.musicfreeandroid.data.backup.BackupRepository
import com.zili.android.musicfreeandroid.data.backup.BackupManifest
import com.zili.android.musicfreeandroid.data.backup.BackupManifestFile
import com.zili.android.musicfreeandroid.data.backup.BackupArchivePaths
import com.zili.android.musicfreeandroid.data.backup.StagedRestore
```

Add test:

```kotlin
    @Test
    fun `backup type renders backup restore actions`() {
        setContent(type = SettingsType.Backup)

        composeRule.onNodeWithTag(FidelityAnchors.Settings.BackupRoot).assertIsDisplayed()
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BackupEntry).assertIsDisplayed()
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BackupCreate).assertIsDisplayed()
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BackupRestore).assertIsDisplayed()
        composeRule.onNodeWithText("创建备份/迁移包").assertIsDisplayed()
        composeRule.onNodeWithText("从备份恢复").assertIsDisplayed()
    }
```

Update `setContent()` constructor call to pass `backupRepository = FakeBackupRepository()`.

Add fake:

```kotlin
    private class FakeBackupRepository : BackupRepository {
        private val manifest = BackupManifest(
            schemaVersion = BackupManifest.CURRENT_SCHEMA_VERSION,
            sourcePackageName = "com.zili.android.musicfreeandroid",
            createdAt = "2026-05-17T00:00:00Z",
            appVersionName = "1.0.2",
            appVersionCode = 10002,
            databaseVersion = 11,
            files = listOf(BackupManifestFile(BackupArchivePaths.DB, 2, "0".repeat(64))),
        )

        override suspend fun exportTo(uri: android.net.Uri): BackupManifest = manifest
        override suspend fun stageRestoreFrom(uri: android.net.Uri): StagedRestore =
            StagedRestore("restore-1", java.io.File("restore-1"), manifest)
        override suspend fun registerPendingRestore(stagedRestore: StagedRestore) = Unit
    }
```

- [ ] **Step 2: Run UI test to verify it fails**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests 'com.zili.android.musicfreeandroid.feature.settings.SettingsScreenTest.backup type renders backup restore actions' --no-daemon
```

Expected: FAIL with unresolved tags `BackupCreate` / `BackupRestore` and missing UI.

- [ ] **Step 3: Add stable anchors**

Modify `core/src/main/java/com/zili/android/musicfreeandroid/core/ui/FidelityAnchors.kt` inside `object Settings`:

```kotlin
        const val BackupCreate = "settings.backup.create"
        const val BackupRestore = "settings.backup.restore"
        const val BackupStatus = "settings.backup.status"
```

- [ ] **Step 4: Add backup screen UI**

Modify `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt`.

Add imports:

```kotlin
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
```

Inside `SettingsScreen`, collect backup state:

```kotlin
    val backupRestoreUiState by viewModel.backupRestoreUiState.collectAsStateWithLifecycle()
```

Add launchers after local state:

```kotlin
    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) viewModel.createBackup(uri)
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.validateRestore(uri)
    }
```

Replace the `SettingsType.Backup -> SettingsTypeEntryContent(...)` branch with:

```kotlin
            SettingsType.Backup -> BackupRestoreContent(
                state = backupRestoreUiState,
                onCreateBackup = {
                    createBackupLauncher.launch("MusicFree-backup-${java.time.LocalDate.now()}.mfbackup")
                },
                onRestoreBackup = {
                    restoreBackupLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*"))
                },
                modifier = Modifier.padding(innerPadding),
            )
```

Add composable below `AboutSettingsContent`:

```kotlin
@Composable
private fun BackupRestoreContent(
    state: BackupRestoreUiState,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Settings.BackupRoot)
            .padding(horizontal = rpx(24)),
        verticalArrangement = Arrangement.spacedBy(rpx(16)),
    ) {
        item { Spacer(modifier = Modifier.height(rpx(24))) }
        item {
            SettingSectionCard(
                title = "备份与恢复",
                testTag = FidelityAnchors.Settings.BackupEntry,
            ) {
                Text(
                    text = "创建迁移包后，可在新包名版本中导入。恢复会覆盖当前应用内数据，系统权限需要重新授权。",
                    fontSize = FontSizes.description,
                    color = MusicFreeTheme.colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(rpx(12)))
                Button(
                    onClick = onCreateBackup,
                    enabled = !state.inProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(FidelityAnchors.Settings.BackupCreate),
                ) {
                    Text(text = "创建备份/迁移包")
                }
                Spacer(modifier = Modifier.height(rpx(8)))
                Button(
                    onClick = onRestoreBackup,
                    enabled = !state.inProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(FidelityAnchors.Settings.BackupRestore),
                ) {
                    Text(text = "从备份恢复")
                }
                val status = state.message ?: state.errorMessage
                if (status != null) {
                    Spacer(modifier = Modifier.height(rpx(8)))
                    Text(
                        text = status,
                        fontSize = FontSizes.description,
                        color = MusicFreeTheme.colors.textSecondary,
                        modifier = Modifier.testTag(FidelityAnchors.Settings.BackupStatus),
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(rpx(24))) }
    }
}
```

Below dialog rendering in `SettingsScreen`, add:

```kotlin
    if (backupRestoreUiState.restoreConfirmationVisible) {
        BackupRestoreConfirmDialog(
            state = backupRestoreUiState,
            onDismiss = viewModel::dismissRestoreConfirmation,
            onConfirm = viewModel::confirmRestore,
        )
    }
```

Add dialog:

```kotlin
@Composable
private fun BackupRestoreConfirmDialog(
    state: BackupRestoreUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "确认恢复备份") },
        text = {
            Text(
                text = "恢复会覆盖当前应用内数据，并需要重启应用后生效。\n\n" +
                    "来源：${state.restoreSourcePackageName ?: "未知"} ${state.restoreAppVersionName ?: ""}\n" +
                    "文件数：${state.restoreFileCount}\n\n" +
                    "系统权限不会继承，恢复后需要重新授权通知、媒体读取、悬浮窗和存储目录。",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "确认恢复")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
    )
}
```

- [ ] **Step 5: Run settings screen tests**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests 'com.zili.android.musicfreeandroid.feature.settings.SettingsScreenTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit task 7**

```bash
git add core/src/main/java/com/zili/android/musicfreeandroid/core/ui/FidelityAnchors.kt \
  feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt \
  feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreenTest.kt
git commit -m "feat(settings): 实现备份与恢复入口"
```

## Task 8: Verification, Docs, And Review

**Files:**
- Modify: `docs/DOCS_STATUS.md` only if the plan implementation changes spec status or adds a follow-up reference.
- No production code changes unless verification exposes a concrete bug.

- [ ] **Step 1: Run focused backup tests**

Run:

```bash
./gradlew :data:testDebugUnitTest --tests 'com.zili.android.musicfreeandroid.data.backup.*' --no-daemon
```

Expected: PASS.

- [ ] **Step 2: Run focused settings tests**

Run:

```bash
./gradlew :feature:settings:testDebugUnitTest --tests 'com.zili.android.musicfreeandroid.feature.settings.SettingsViewModelTest' --tests 'com.zili.android.musicfreeandroid.feature.settings.SettingsScreenTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 3: Run app debug build**

Run:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Expected: PASS and debug APK generated under `app/build/outputs/apk/debug/`.

- [ ] **Step 4: Run dev harness and whitespace check**

Run:

```bash
bash scripts/dev-harness/check.sh
git diff --check
```

Expected: both PASS.

- [ ] **Step 5: Inspect diff for package migration scope**

Run:

```bash
git diff --stat main...HEAD
git diff --name-only main...HEAD
rg -n "applicationId\\s*=|namespace\\s*=|com\\.hank\\.musicfree" app data core feature/settings
```

Expected:
- Diff contains backup/restore implementation and docs only.
- `applicationId` and Gradle namespaces remain old-package values.
- No production source uses `com.hank.musicfree` in this iteration.

- [ ] **Step 6: Final implementation commit if verification fixes were needed**

If Step 1-5 required fixes, commit the concrete touched areas:

```bash
git add app/src/main/java/com/zili/android/musicfreeandroid/MusicFreeApplication.kt \
  core/src/main/java/com/zili/android/musicfreeandroid/core/ui/FidelityAnchors.kt \
  data/src/main/java/com/zili/android/musicfreeandroid/data/backup \
  data/src/main/java/com/zili/android/musicfreeandroid/data/di/DataModule.kt \
  data/src/test/java/com/zili/android/musicfreeandroid/data/backup \
  feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt \
  feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModel.kt \
  feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreenTest.kt \
  feature/settings/src/test/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModelTest.kt
git commit -m "fix(backup): 修正备份恢复验收问题"
```

If no files changed after verification, skip this commit.

- [ ] **Step 7: Prepare merge-back**

Run:

```bash
git status --short --branch
```

Expected: clean worktree on branch `package-migration-backup-restore`.

Report passed commands and ask whether to squash-merge back to local `main`, unless the user has already requested automatic merge-back.

package com.hank.musicfree.data.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StartupBackupRestoreTest {
    private val json = Json { ignoreUnknownKeys = true }

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `startup restore moves staged files into app private locations`() {
        val layout = BackupPrivateLayout(
            filesDir = tmpFolder.root,
            databaseFile = tmpFolder.root.resolve("databases/musicfree.db"),
        )
        setupCurrentState(layout)
        val staging = layout.filesDir.resolve("backup_restore/staging/restore-1")
        setupStagedState(staging)

        val store = PendingRestoreStore(json, layout.filesDir)
        store.writePending(
            PendingRestoreRecord(id = "restore-1", stagingRelativePath = "backup_restore/staging/restore-1"),
        )

        val result = StartupBackupRestore.applyIfPending(layout, json)

        assertTrue(result.applied)
        assertEquals("restore applied", result.message)
        assertEquals("db-v2", layout.databaseFile.readText())
        assertEquals("wal-v2", layout.databaseFile.resolveSibling("musicfree.db-wal").readText())
        assertEquals("shm-v2", layout.databaseFile.resolveSibling("musicfree.db-shm").readText())
        assertEquals(
            "prefs-v2",
            layout.filesDir.resolve("datastore/app_preferences.preferences_pb").readText(),
        )
        assertEquals("plugin-v2", layout.filesDir.resolve("plugins/wy.js").readText())
        assertEquals("cover-v2", layout.filesDir.resolve("playlist_covers/a/cover.jpg").readText())
        assertEquals("theme-v2", layout.filesDir.resolve("theme_background.jpg").readText())

        assertFalse(store.pendingFile().exists())
        assertFalse(staging.exists())
        assertFalse(store.restoreBackupRoot("restore-1").exists())

        val status = json.decodeFromString(RestoreStatusRecord.serializer(), store.statusFile().readText())
        assertTrue(status.applied)
        assertEquals("restore-1", status.id)
    }

    @Test
    fun `startup restore no pending does nothing`() {
        val layout = BackupPrivateLayout(
            filesDir = tmpFolder.root,
            databaseFile = tmpFolder.root.resolve("databases/musicfree.db"),
        )
        requireNotNull(layout.databaseFile.parentFile).mkdirs()
        layout.databaseFile.writeText("old-db")

        val result = StartupBackupRestore.applyIfPending(layout, json)

        assertFalse(result.applied)
        assertEquals("no pending restore", result.message)
        assertEquals("old-db", layout.databaseFile.readText())
        assertFalse(PendingRestoreStore(json, layout.filesDir).statusFile().exists())
    }

    @Test
    fun `startup restore with missing staging writes failure status and clear pending`() {
        val layout = BackupPrivateLayout(
            filesDir = tmpFolder.root,
            databaseFile = tmpFolder.root.resolve("databases/musicfree.db"),
        )
        setupCurrentState(layout)

        val store = PendingRestoreStore(json, layout.filesDir)
        store.writePending(
            PendingRestoreRecord(
                id = "restore-missing",
                stagingRelativePath = "backup_restore/staging/missing-dir",
            ),
        )

        val result = StartupBackupRestore.applyIfPending(layout, json)

        assertFalse(result.applied)
        assertFalse(store.pendingFile().exists())
        assertEquals("staging missing: backup_restore/staging/missing-dir", result.message)
        assertEquals("old-db", layout.databaseFile.readText())

        val status = json.decodeFromString(RestoreStatusRecord.serializer(), store.statusFile().readText())
        assertFalse(status.applied)
        assertEquals("restore-missing", status.id)
    }

    @Test
    fun `startup restore rejects unsafe staging path outside app files`() {
        val layout = BackupPrivateLayout(
            filesDir = tmpFolder.root,
            databaseFile = tmpFolder.root.resolve("databases/musicfree.db"),
        )
        val outside = tmpFolder.newFolder("outside")
        val outsideMarker = outside.resolve("marker.txt")
        outsideMarker.writeText("keep-me")

        val store = PendingRestoreStore(json, layout.filesDir)
        store.writePending(
            PendingRestoreRecord(
                id = "restore-outside",
                stagingRelativePath = "../outside/marker.txt",
            ),
        )

        val result = StartupBackupRestore.applyIfPending(layout, json)

        assertFalse(result.applied)
        assertEquals("invalid staging path: ../outside/marker.txt", result.message)
        assertFalse(store.pendingFile().exists())
        assertTrue(outsideMarker.exists())
        assertEquals("keep-me", outsideMarker.readText())
    }

    @Test
    fun `startup restore rejects backup restore root as staging path`() {
        val layout = BackupPrivateLayout(
            filesDir = tmpFolder.root,
            databaseFile = tmpFolder.root.resolve("databases/musicfree.db"),
        )
        val store = PendingRestoreStore(json, layout.filesDir)
        val backupRoot = layout.filesDir.resolve("backup_restore").apply { mkdirs() }
        val marker = backupRoot.resolve("sentinel.txt").apply { writeText("keep-status") }
        store.writePending(
            PendingRestoreRecord(
                id = "restore-root",
                stagingRelativePath = "backup_restore",
            ),
        )

        val result = StartupBackupRestore.applyIfPending(layout, json)

        assertFalse(result.applied)
        assertEquals("invalid staging path: backup_restore", result.message)
        assertTrue(marker.exists())
        assertEquals("keep-status", marker.readText())
    }

    @Test
    fun `startup restore rejects staging root without child restore id`() {
        val layout = BackupPrivateLayout(
            filesDir = tmpFolder.root,
            databaseFile = tmpFolder.root.resolve("databases/musicfree.db"),
        )
        val store = PendingRestoreStore(json, layout.filesDir)
        val stagingRoot = store.stagingRoot()
        val marker = stagingRoot.resolve("marker.txt").apply { writeText("keep-staging-root") }
        store.writePending(
            PendingRestoreRecord(
                id = "restore-staging-root",
                stagingRelativePath = "backup_restore/staging",
            ),
        )

        val result = StartupBackupRestore.applyIfPending(layout, json)

        assertFalse(result.applied)
        assertEquals("invalid staging path: backup_restore/staging", result.message)
        assertTrue(marker.exists())
        assertEquals("keep-staging-root", marker.readText())
    }

    @Test
    fun `startup restore failure does not throw and keeps original database`() {
        val layout = BackupPrivateLayout(
            filesDir = tmpFolder.root,
            databaseFile = tmpFolder.root.resolve("databases/musicfree.db"),
        )
        setupCurrentState(layout)
        val staging = layout.filesDir.resolve("backup_restore/staging/restore-locked")
        setupStagedState(staging)

        layout.filesDir.resolve("datastore").deleteRecursively()
        layout.filesDir.resolve("datastore").writeText("not-a-directory")
        val store = PendingRestoreStore(json, layout.filesDir)
        store.writePending(
            PendingRestoreRecord(
                id = "restore-locked",
                stagingRelativePath = "backup_restore/staging/restore-locked",
            ),
        )

        val result = StartupBackupRestore.applyIfPending(layout, json)

        assertFalse(result.applied)
        assertFalse(store.pendingFile().exists())
        assertEquals("old-db", layout.databaseFile.readText())
        assertFalse(staging.exists())
        val status = json.decodeFromString(RestoreStatusRecord.serializer(), store.statusFile().readText())
        assertFalse(status.applied)
        assertFalse(status.retryable)
        assertTrue(status.rollbackComplete)
        assertEquals("restore-locked", status.id)
    }

    @Test
    fun `startup restore rolls back previous backup root before retrying restore`() {
        val layout = BackupPrivateLayout(
            filesDir = tmpFolder.root,
            databaseFile = tmpFolder.root.resolve("databases/musicfree.db"),
        )
        setupCurrentState(layout)
        layout.databaseFile.writeText("mixed-db")
        val staging = layout.filesDir.resolve("backup_restore/staging/restore-resume")
        setupStagedState(staging)

        val store = PendingRestoreStore(json, layout.filesDir)
        val backupRoot = store.restoreBackupRoot("restore-resume")
        backupRoot.resolve(BackupArchivePaths.DB).parentFile?.mkdirs()
        backupRoot.resolve(BackupArchivePaths.DB).writeText("old-db-before-retry")
        store.writePending(
            PendingRestoreRecord(
                id = "restore-resume",
                stagingRelativePath = "backup_restore/staging/restore-resume",
            ),
        )

        val result = StartupBackupRestore.applyIfPending(layout, json)

        assertTrue(result.applied)
        assertEquals("db-v2", layout.databaseFile.readText())
        assertFalse(store.pendingFile().exists())
        assertFalse(backupRoot.exists())
    }

    @Test
    fun `startup restore keeps pending when previous rollback fails`() {
        val layout = BackupPrivateLayout(
            filesDir = tmpFolder.root,
            databaseFile = tmpFolder.root.resolve("databases/musicfree.db"),
        )
        layout.filesDir.mkdirs()
        requireNotNull(layout.databaseFile.parentFile).writeText("not-a-directory")
        val staging = layout.filesDir.resolve("backup_restore/staging/restore-retry").apply { mkdirs() }

        val store = PendingRestoreStore(json, layout.filesDir)
        val backupRoot = store.restoreBackupRoot("restore-retry")
        backupRoot.resolve(BackupArchivePaths.DB).parentFile?.mkdirs()
        backupRoot.resolve(BackupArchivePaths.DB).writeText("old-db")
        store.writePending(
            PendingRestoreRecord(
                id = "restore-retry",
                stagingRelativePath = "backup_restore/staging/restore-retry",
            ),
        )

        val result = StartupBackupRestore.applyIfPending(layout, json)

        assertFalse(result.applied)
        assertTrue(staging.exists())
        assertTrue(store.pendingFile().exists())
        assertTrue(result.message.startsWith("previous restore rollback failed; rollback failed:"))

        val status = json.decodeFromString(RestoreStatusRecord.serializer(), store.statusFile().readText())
        assertFalse(status.applied)
        assertTrue(status.retryable)
        assertFalse(status.rollbackComplete)
        assertEquals("restore-retry", status.id)
    }

    private fun setupCurrentState(layout: BackupPrivateLayout) {
        requireNotNull(layout.databaseFile.parentFile).mkdirs()
        layout.databaseFile.writeText("old-db")
        layout.databaseFile.resolveSibling("musicfree.db-wal").writeText("old-wal")
        layout.databaseFile.resolveSibling("musicfree.db-shm").writeText("old-shm")

        layout.filesDir.resolve("datastore").mkdirs()
        layout.filesDir.resolve("datastore/app_preferences.preferences_pb").writeText("old-prefs")

        val pluginDir = layout.filesDir.resolve("plugins")
        pluginDir.mkdirs()
        pluginDir.resolve("wy.js").writeText("old-plugin")

        val coverDir = layout.filesDir.resolve("playlist_covers/a")
        coverDir.mkdirs()
        coverDir.resolve("cover.jpg").writeText("old-cover")
        layout.filesDir.resolve("theme_background.jpg").writeText("old-theme")
    }

    private fun setupStagedState(staging: File) {
        staging.resolve(BackupArchivePaths.DB).parentFile?.mkdirs()
        staging.resolve(BackupArchivePaths.DB).writeText("db-v2")
        staging.resolve(BackupArchivePaths.DB_WAL).writeText("wal-v2")
        staging.resolve(BackupArchivePaths.DB_SHM).writeText("shm-v2")

        staging.resolve(BackupArchivePaths.DATASTORE).parentFile?.mkdirs()
        staging.resolve(BackupArchivePaths.DATASTORE).writeText("prefs-v2")

        val pluginDir = staging.resolve("files/plugins")
        pluginDir.mkdirs()
        pluginDir.resolve("wy.js").writeText("plugin-v2")

        val coverDir = staging.resolve("files/playlist_covers/a")
        coverDir.mkdirs()
        coverDir.resolve("cover.jpg").writeText("cover-v2")

        staging.resolve("files/theme_background.jpg").writeText("theme-v2")
    }
}

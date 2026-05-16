package com.hank.musicfree.data.backup

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class BackupArchiveWriterTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `provider only lists allowed app data files in stable order`() {
        val appData = tmpFolder.root
        setupFakeDataRoot(appData, includeDb = true)

        val provider = BackupFileSetProvider(appData)
        val sourceFiles = provider.listBackupSourceFiles()
        val actualArchivePaths = sourceFiles.map { it.archivePath }

        assertEquals(
            listOf(
                "datastore/app_preferences.preferences_pb",
                "db/musicfree.db",
                "db/musicfree.db-shm",
                "db/musicfree.db-wal",
                "files/playlist_covers/a/cover.jpg",
                "files/plugins/wy.js",
                "files/theme_background.jpg",
            ),
            actualArchivePaths,
        )

        assertEquals(7, actualArchivePaths.size)
        assertFalse(actualArchivePaths.contains("files/plugins/readme.txt"))
        assertFalse(actualArchivePaths.contains("files/plugins/sub/wy.js"))
        assertFalse(actualArchivePaths.contains("cache/updates/app.apk"))
        assertFalse(actualArchivePaths.contains("files/logan/xxx"))
    }

    @Test
    fun `writer writes whitelist entries and manifest and excludes disallowed paths`() {
        val appData = tmpFolder.root
        setupFakeDataRoot(appData, includeDb = true)
        val sourceFiles = BackupFileSetProvider(appData).listBackupSourceFiles()

        val out = tmpFolder.newFile("backup.mfbackup")
        val metadata = BackupAppMetadata(
            packageName = "com.hank.musicfree",
            versionName = "1.2.3",
            versionCode = 1234L,
        )
        BackupArchiveWriter().writeArchive(
            outputFile = out,
            sourceFiles = sourceFiles,
            metadata = metadata,
            databaseVersion = 77,
        )

        val zippedFileNames = mutableListOf<String>()
        val manifest = readManifestFromZip(out)

        ZipFile(out).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                zippedFileNames += entries.nextElement().name
            }
        }

        val expectedPaths = sourceFiles.map { it.archivePath }
        assertTrue(zippedFileNames.contains(BackupArchivePaths.MANIFEST))
        assertEquals(expectedPaths, manifest.files.map { it.path })
        assertEquals(BackupManifest.CURRENT_SCHEMA_VERSION, manifest.schemaVersion)
        assertEquals("com.hank.musicfree", manifest.sourcePackageName)
        assertEquals("1.2.3", manifest.appVersionName)
        assertEquals(1234L, manifest.appVersionCode)
        assertEquals(77, manifest.databaseVersion)
        assertNotNull(Instant.parse(manifest.createdAt))
        assertEquals(expectedPaths.size, manifest.files.size)
        assertEquals(expectedPaths.size + 1, zippedFileNames.size)

        val manifestByPath = manifest.files.associateBy { it.path }
        sourceFiles.forEach { source ->
            val entry = manifestByPath[source.archivePath]
            assertNotNull(entry)
            assertEquals(source.source.length(), entry!!.sizeBytes)
            assertEquals(sha256(source.source), entry.sha256)
        }

        assertFalse(zippedFileNames.contains("files/plugins/readme.txt"))
        assertFalse(zippedFileNames.contains("files/plugins/sub/wy.js"))
        assertFalse(zippedFileNames.contains("cache/updates/app.apk"))
        assertFalse(zippedFileNames.contains("files/logan/xxx"))
    }

    @Test
    fun `writer supports direct output stream export`() {
        val appData = tmpFolder.root
        setupFakeDataRoot(appData, includeDb = true)
        val sourceFiles = BackupFileSetProvider(appData).listBackupSourceFiles()
        val metadata = BackupAppMetadata("com.hank.musicfree", "1.2.3", 1234L)
        val output = ByteArrayOutputStream()

        val manifest = BackupArchiveWriter().write(
            output = output,
            files = sourceFiles,
            metadata = metadata,
            databaseVersion = 77,
            createdAt = "2026-05-17T00:00:00Z",
        )

        assertEquals(sourceFiles.map { it.archivePath }, manifest.files.map { it.path })
        val zippedNames = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                zippedNames += entry.name
                zip.closeEntry()
            }
        }
        assertTrue(zippedNames.contains(BackupArchivePaths.MANIFEST))
        assertTrue(zippedNames.contains(BackupArchivePaths.DB))
    }

    @Test
    fun `writer fails when main database file missing`() {
        val appData = tmpFolder.root
        setupFakeDataRoot(appData, includeDb = false)
        val sourceFiles = BackupFileSetProvider(appData).listBackupSourceFiles()

        val out = tmpFolder.newFile("backup.mfbackup")
        out.writeText("existing backup")
        val metadata = BackupAppMetadata("com.hank.musicfree", "1.2.3", 1234L)
        val writer = BackupArchiveWriter()

        assertThrows(IllegalStateException::class.java) {
            writer.writeArchive(out, sourceFiles, metadata, 77)
        }
        assertEquals("existing backup", out.readText())
    }

    @Test
    fun `writer rejects duplicate archive paths and illegal entry`() {
        val appData = tmpFolder.root
        val db = appData.resolve("databases").also { it.mkdirs() }.resolve("musicfree.db")
            .also { it.writeText("db") }
        appData.resolve("databases").resolve("musicfree.db-wal").also { it.writeText("wal") }
        appData.resolve("databases").resolve("musicfree.db-shm").also { it.writeText("shm") }
        appData.resolve("datastore").also { it.mkdirs() }
            .resolve("app_preferences.preferences_pb").also { it.writeText("prefs") }
        val metadata = BackupAppMetadata("com.hank.musicfree", "1.2.3", 1234L)
        val writer = BackupArchiveWriter()
        val out = tmpFolder.newFile("backup.mfbackup")

        assertThrows(IllegalStateException::class.java) {
            writer.writeArchive(
                outputFile = out,
                sourceFiles = listOf(
                    BackupSourceFile(db, BackupArchivePaths.DB),
                    BackupSourceFile(db, BackupArchivePaths.DB),
                ),
                metadata = metadata,
                databaseVersion = 77,
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            writer.writeArchive(
                outputFile = out,
                sourceFiles = listOf(
                    BackupSourceFile(db, BackupArchivePaths.DB),
                    BackupSourceFile(db, BackupArchivePaths.DB_WAL),
                    BackupSourceFile(db, BackupArchivePaths.DB_SHM),
                    BackupSourceFile(
                        appData.resolve("datastore/app_preferences.preferences_pb"),
                        BackupArchivePaths.DATASTORE,
                    ),
                    BackupSourceFile(db, "files/plugins/sub/wy.js"),
                ),
                metadata = metadata,
                databaseVersion = 77,
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            writer.writeArchive(
                outputFile = out,
                sourceFiles = listOf(
                    BackupSourceFile(db, BackupArchivePaths.DB),
                    BackupSourceFile(db, BackupArchivePaths.MANIFEST),
                ),
                metadata = metadata,
                databaseVersion = 77,
            )
        }
    }

    private fun readManifestFromZip(file: File): BackupManifest {
        ZipFile(file).use { zip ->
            val manifestEntry = zip.getEntry(BackupArchivePaths.MANIFEST)
            if (manifestEntry == null) fail("manifest.json missing")
            return zip.getInputStream(manifestEntry).buffered().use { input ->
                val bytes = input.readBytes()
                Json.decodeFromString(bytes.toString(Charsets.UTF_8))
            }
        }
    }

    private fun setupFakeDataRoot(root: File, includeDb: Boolean) {
        root.resolve("databases").mkdirs()
        if (includeDb) {
            root.resolve("databases/musicfree.db").writeText("db")
            root.resolve("databases/musicfree.db-wal").writeText("wal")
            root.resolve("databases/musicfree.db-shm").writeText("shm")
        }
        root.resolve("datastore").mkdirs()
        root.resolve("datastore/app_preferences.preferences_pb").writeText("prefs")

        val pluginDir = root.resolve("files/plugins")
        pluginDir.mkdirs()
        pluginDir.resolve("wy.js").writeText("console.log('plugin')")
        pluginDir.resolve("readme.txt").writeText("skip")
        pluginDir.resolve("sub").mkdirs()
        pluginDir.resolve("sub/wy.js").writeText("nested")

        val coverDir = root.resolve("files/playlist_covers/a")
        coverDir.mkdirs()
        coverDir.resolve("cover.jpg").writeText("cover")
        root.resolve("files/theme_background.jpg").writeText("bg")

        root.resolve("files/logan/xxx").also {
            it.parentFile?.mkdirs()
            it.writeText("logan")
        }
        root.resolve("cache/updates/app.apk").also {
            it.parentFile?.mkdirs()
            it.writeText("apk")
        }
    }

    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(file)).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }
}

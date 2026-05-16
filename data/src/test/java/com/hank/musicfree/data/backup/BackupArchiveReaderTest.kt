package com.hank.musicfree.data.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.CRC32

class BackupArchiveReaderTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `reader extracts valid archive to staging`() {
        val appData = tmpFolder.root
        setupFakeDataRoot(appData, includeDb = true)
        val sourceFiles = BackupFileSetProvider(appData).listBackupSourceFiles()

        val output = ByteArrayOutputStream()
        val metadata = BackupAppMetadata(
            packageName = "com.hank.musicfree",
            versionName = "1.2.3",
            versionCode = 1234L,
        )
        val manifest = BackupArchiveWriter().write(
            output = output,
            files = sourceFiles,
            metadata = metadata,
            databaseVersion = 77,
        )

        val stagingRoot = tmpFolder.root
        val restore = BackupArchiveReader(json = Json).extractAndValidate(
            input = ByteArrayInputStream(output.toByteArray()),
            stagingRoot = stagingRoot,
            id = "restore-id",
        )

        assertEquals("restore-id", restore.id)
        assertEquals(stagingRoot.resolve("restore-id").canonicalFile, restore.directory)
        assertEquals(manifest, restore.manifest)
        assertEquals("com.hank.musicfree", restore.manifest.sourcePackageName)
        assertTrue(restore.directory.resolve(BackupArchivePaths.MANIFEST).exists())
        assertTrue(restore.directory.resolve(BackupArchivePaths.DB).exists())
        assertTrue(restore.directory.resolve(BackupArchivePaths.DATASTORE).exists())

        manifest.files.forEach { file ->
            val staged = restore.directory.resolve(file.path)
            assertTrue(staged.exists())
            assertEquals(file.sizeBytes, staged.length())
        }
    }

    @Test
    fun `reader rejects path traversal`() {
        val stagingRoot = tmpFolder.newFolder("staging")
        val bytes = buildArchiveWithEntries(
            mapOf(
                BackupArchivePaths.DB to "db".toByteArray(),
                "../evil" to "pwned".toByteArray(),
                BackupArchivePaths.MANIFEST to Json.encodeToString(
                    buildManifest(
                    files = listOf(
                        BackupManifestFile(
                            path = BackupArchivePaths.DB,
                            sizeBytes = 2L,
                            sha256 = sha256("db".byteInputStream()),
                        ),
                    ),
                    ),
                ).toByteArray(Charsets.UTF_8),
            ),
        )
        val stagingId = "traversal"
        val stagingDir = stagingRoot.resolve(stagingId)

        assertThrows(BackupArchiveException::class.java) {
            BackupArchiveReader(json = Json).extractAndValidate(
                input = ByteArrayInputStream(bytes),
                stagingRoot = stagingRoot,
                id = stagingId,
            )
        }
        assertFalse(stagingDir.exists())
    }

    @Test
    fun `reader rejects unsafe staging id without touching outside directory`() {
        val stagingRoot = tmpFolder.newFolder("staging")
        val outside = tmpFolder.newFolder("outside")
        val marker = outside.resolve("marker.txt").apply { writeText("keep") }
        val bytes = buildArchiveWithEntries(
            mapOf(
                BackupArchivePaths.DB to "db".toByteArray(),
            ),
        )

        assertThrows(BackupArchiveException::class.java) {
            BackupArchiveReader(json = Json).extractAndValidate(
                input = ByteArrayInputStream(bytes),
                stagingRoot = stagingRoot,
                id = "../outside",
            )
        }
        assertTrue(marker.exists())
        assertEquals("keep", marker.readText())
    }

    @Test
    fun `reader rejects hash mismatch`() {
        val stagingRoot = tmpFolder.newFolder("staging")
        val manifest = buildManifest(
            files = listOf(
                BackupManifestFile(
                    path = BackupArchivePaths.DB,
                    sizeBytes = 2L,
                    sha256 = "000000000000000000000000000000000000000000000000000000000000000000",
                ),
            ),
        )
        val bytes = buildArchiveWithEntries(
            mapOf(
                BackupArchivePaths.DB to "db".toByteArray(),
                BackupArchivePaths.MANIFEST to Json.encodeToString(manifest).toByteArray(Charsets.UTF_8),
            ),
        )
        val stagingId = "hash-mismatch"
        val stagingDir = stagingRoot.resolve(stagingId)

        assertThrows(BackupArchiveException::class.java) {
            BackupArchiveReader(json = Json).extractAndValidate(
                input = ByteArrayInputStream(bytes),
                stagingRoot = stagingRoot,
                id = stagingId,
            )
        }
        assertFalse(stagingDir.exists())
    }

    @Test
    fun `reader rejects oversized manifest`() {
        val stagingRoot = tmpFolder.newFolder("staging")
        val bytes = buildArchiveWithEntries(
            mapOf(
                BackupArchivePaths.DB to "db".toByteArray(),
                BackupArchivePaths.MANIFEST to ByteArray(1024 * 1024 + 1) { '{'.code.toByte() },
            ),
        )
        val stagingId = "oversized-manifest"
        val stagingDir = stagingRoot.resolve(stagingId)

        assertThrows(BackupArchiveException::class.java) {
            BackupArchiveReader(json = Json).extractAndValidate(
                input = ByteArrayInputStream(bytes),
                stagingRoot = stagingRoot,
                id = stagingId,
            )
        }
        assertFalse(stagingDir.exists())
    }

    @Test
    fun `reader rejects missing manifest`() {
        val stagingRoot = tmpFolder.newFolder("staging")
        val bytes = buildArchiveWithEntries(
            mapOf(
                BackupArchivePaths.DB to "db".toByteArray(),
            ),
        )
        val stagingId = "missing-manifest"
        val stagingDir = stagingRoot.resolve(stagingId)

        assertThrows(BackupArchiveException::class.java) {
            BackupArchiveReader(json = Json).extractAndValidate(
                input = ByteArrayInputStream(bytes),
                stagingRoot = stagingRoot,
                id = stagingId,
            )
        }
        assertFalse(stagingDir.exists())
    }

    @Test
    fun `reader rejects missing database entry in manifest`() {
        val stagingRoot = tmpFolder.newFolder("staging")
        val manifest = buildManifest(
            files = listOf(
                BackupManifestFile(
                    path = BackupArchivePaths.DATASTORE,
                    sizeBytes = 5L,
                    sha256 = sha256("prefs".byteInputStream()),
                ),
            ),
        )
        val bytes = buildArchiveWithEntries(
            mapOf(
                BackupArchivePaths.DATASTORE to "prefs".toByteArray(),
                BackupArchivePaths.MANIFEST to Json.encodeToString(manifest).toByteArray(Charsets.UTF_8),
            ),
        )
        val stagingId = "missing-db-manifest"
        val stagingDir = stagingRoot.resolve(stagingId)

        assertThrows(BackupArchiveException::class.java) {
            BackupArchiveReader(json = Json).extractAndValidate(
                input = ByteArrayInputStream(bytes),
                stagingRoot = stagingRoot,
                id = stagingId,
            )
        }
        assertFalse(stagingDir.exists())
    }

    @Test
    fun `reader rejects database declared but missing from archive`() {
        val stagingRoot = tmpFolder.newFolder("staging")
        val manifest = buildManifest(
            files = listOf(
                BackupManifestFile(
                    path = BackupArchivePaths.DB,
                    sizeBytes = 2L,
                    sha256 = sha256("db".byteInputStream()),
                ),
            ),
        )
        val bytes = buildArchiveWithEntries(
            mapOf(
                BackupArchivePaths.MANIFEST to Json.encodeToString(manifest).toByteArray(Charsets.UTF_8),
            ),
        )
        val stagingId = "missing-db-archive"
        val stagingDir = stagingRoot.resolve(stagingId)

        assertThrows(BackupArchiveException::class.java) {
            BackupArchiveReader(json = Json).extractAndValidate(
                input = ByteArrayInputStream(bytes),
                stagingRoot = stagingRoot,
                id = stagingId,
            )
        }
        assertFalse(stagingDir.exists())
    }

    @Test
    fun `reader rejects unsupported schema`() {
        val stagingRoot = tmpFolder.newFolder("staging")
        val manifest = buildManifest(
            schemaVersion = 999,
            files = listOf(
                BackupManifestFile(
                    path = BackupArchivePaths.DB,
                    sizeBytes = 2L,
                    sha256 = sha256("db".byteInputStream()),
                ),
            ),
        )
        val bytes = buildArchiveWithEntries(
            mapOf(
                BackupArchivePaths.DB to "db".toByteArray(),
                BackupArchivePaths.MANIFEST to Json.encodeToString(manifest).toByteArray(Charsets.UTF_8),
            ),
        )
        val stagingId = "unsupported-schema"
        val stagingDir = stagingRoot.resolve(stagingId)

        assertThrows(BackupArchiveException::class.java) {
            BackupArchiveReader(json = Json).extractAndValidate(
                input = ByteArrayInputStream(bytes),
                stagingRoot = stagingRoot,
                id = stagingId,
            )
        }
        assertFalse(stagingDir.exists())
    }

    @Test
    fun `reader rejects manifest with manifest json entry`() {
        val stagingRoot = tmpFolder.newFolder("staging")
        val manifest = buildManifest(
            files = listOf(
                BackupManifestFile(
                    path = BackupArchivePaths.DB,
                    sizeBytes = 2L,
                    sha256 = sha256("db".byteInputStream()),
                ),
                BackupManifestFile(
                    path = BackupArchivePaths.MANIFEST,
                    sizeBytes = 0L,
                    sha256 = "00",
                ),
            ),
        )
        val bytes = buildArchiveWithEntries(
            mapOf(
                BackupArchivePaths.DB to "db".toByteArray(),
                BackupArchivePaths.MANIFEST to Json.encodeToString(manifest).toByteArray(Charsets.UTF_8),
            ),
        )
        val stagingId = "manifest-in-list"
        val stagingDir = stagingRoot.resolve(stagingId)

        assertThrows(BackupArchiveException::class.java) {
            BackupArchiveReader(json = Json).extractAndValidate(
                input = ByteArrayInputStream(bytes),
                stagingRoot = stagingRoot,
                id = stagingId,
            )
        }
        assertFalse(stagingDir.exists())
    }

    @Test
    fun `reader rejects duplicate manifest paths`() {
        val stagingRoot = tmpFolder.newFolder("staging")
        val manifest = buildManifest(
            files = listOf(
                BackupManifestFile(
                    path = BackupArchivePaths.DB,
                    sizeBytes = 2L,
                    sha256 = sha256("db".byteInputStream()),
                ),
                BackupManifestFile(
                    path = BackupArchivePaths.DB,
                    sizeBytes = 2L,
                    sha256 = sha256("db".byteInputStream()),
                ),
            ),
        )
        val bytes = buildArchiveWithEntries(
            mapOf(
                BackupArchivePaths.DB to "db".toByteArray(),
                BackupArchivePaths.MANIFEST to Json.encodeToString(manifest).toByteArray(Charsets.UTF_8),
            ),
        )
        val stagingId = "duplicate-manifest"
        val stagingDir = stagingRoot.resolve(stagingId)

        assertThrows(BackupArchiveException::class.java) {
            BackupArchiveReader(json = Json).extractAndValidate(
                input = ByteArrayInputStream(bytes),
                stagingRoot = stagingRoot,
                id = stagingId,
            )
        }
        assertFalse(stagingDir.exists())
    }

    @Test
    fun `reader rejects duplicate zip entries`() {
        val stagingRoot = tmpFolder.newFolder("staging")
        val manifest = buildManifest(
            files = listOf(
                BackupManifestFile(
                    path = BackupArchivePaths.DB,
                    sizeBytes = 2L,
                    sha256 = sha256("db".byteInputStream()),
                ),
            ),
        )
        val bytes = buildArchiveWithEntries(
            listOf(
                BackupArchivePaths.DB to "db".toByteArray(),
                BackupArchivePaths.DB to "db".toByteArray(),
                BackupArchivePaths.MANIFEST to Json.encodeToString(manifest).toByteArray(Charsets.UTF_8),
            ),
        )
        val stagingId = "duplicate-zip"
        val stagingDir = stagingRoot.resolve(stagingId)

        assertThrows(BackupArchiveException::class.java) {
            BackupArchiveReader(json = Json).extractAndValidate(
                input = ByteArrayInputStream(bytes),
                stagingRoot = stagingRoot,
                id = stagingId,
            )
        }
        assertFalse(stagingDir.exists())
    }

    @Test
    fun `reader rejects archive with undeclared payload files`() {
        val stagingRoot = tmpFolder.newFolder("staging")
        val manifest = buildManifest(
            files = listOf(
                BackupManifestFile(
                    path = BackupArchivePaths.DB,
                    sizeBytes = 2L,
                    sha256 = sha256("db".byteInputStream()),
                ),
            ),
        )
        val bytes = buildArchiveWithEntries(
            mapOf(
                BackupArchivePaths.DB to "db".toByteArray(),
                "files/plugins/extra.js" to "extra".toByteArray(),
                BackupArchivePaths.MANIFEST to Json.encodeToString(manifest).toByteArray(Charsets.UTF_8),
            ),
        )
        val stagingId = "undeclared-entry"
        val stagingDir = stagingRoot.resolve(stagingId)

        assertThrows(BackupArchiveException::class.java) {
            BackupArchiveReader(json = Json).extractAndValidate(
                input = ByteArrayInputStream(bytes),
                stagingRoot = stagingRoot,
                id = stagingId,
            )
        }
        assertFalse(stagingDir.exists())
    }

    private fun buildManifest(
        schemaVersion: Int = BackupManifest.CURRENT_SCHEMA_VERSION,
        files: List<BackupManifestFile>,
    ): BackupManifest {
        return BackupManifest(
            schemaVersion = schemaVersion,
            sourcePackageName = "com.hank.musicfree",
            createdAt = "2026-05-17T00:00:00Z",
            appVersionName = "1.2.3",
            appVersionCode = 1234L,
            databaseVersion = 77,
            files = files,
        )
    }

    private fun setupFakeDataRoot(
        root: File,
        includeDb: Boolean,
    ) {
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

        val coverDir = root.resolve("files/playlist_covers/a")
        coverDir.mkdirs()
        coverDir.resolve("cover.jpg").writeText("cover")
        root.resolve("files/theme_background.jpg").writeText("bg")
    }

    private fun buildArchiveWithEntries(entries: Map<String, ByteArray>): ByteArray {
        return buildArchiveWithEntries(entries.entries.map { it.key to it.value })
    }

    private fun buildArchiveWithEntries(entries: List<Pair<String, ByteArray>>): ByteArray {
        val output = ByteArrayOutputStream()
        val centralDirectory = ByteArrayOutputStream()

        entries.forEach { (name, bytes) ->
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            val crc = CRC32().apply { update(bytes) }.value
            val localHeaderOffset = output.size()

            output.writeIntLe(LOCAL_FILE_HEADER_SIGNATURE)
            output.writeShortLe(VERSION_NEEDED)
            output.writeShortLe(0)
            output.writeShortLe(STORED_METHOD)
            output.writeShortLe(0)
            output.writeShortLe(0)
            output.writeIntLe(crc)
            output.writeIntLe(bytes.size.toLong())
            output.writeIntLe(bytes.size.toLong())
            output.writeShortLe(nameBytes.size)
            output.writeShortLe(0)
            output.write(nameBytes)
            output.write(bytes)

            centralDirectory.writeIntLe(CENTRAL_DIRECTORY_SIGNATURE)
            centralDirectory.writeShortLe(VERSION_NEEDED)
            centralDirectory.writeShortLe(VERSION_NEEDED)
            centralDirectory.writeShortLe(0)
            centralDirectory.writeShortLe(STORED_METHOD)
            centralDirectory.writeShortLe(0)
            centralDirectory.writeShortLe(0)
            centralDirectory.writeIntLe(crc)
            centralDirectory.writeIntLe(bytes.size.toLong())
            centralDirectory.writeIntLe(bytes.size.toLong())
            centralDirectory.writeShortLe(nameBytes.size)
            centralDirectory.writeShortLe(0)
            centralDirectory.writeShortLe(0)
            centralDirectory.writeShortLe(0)
            centralDirectory.writeShortLe(0)
            centralDirectory.writeIntLe(0)
            centralDirectory.writeIntLe(localHeaderOffset.toLong())
            centralDirectory.write(nameBytes)
        }

        val centralDirectoryOffset = output.size()
        val centralDirectoryBytes = centralDirectory.toByteArray()
        output.write(centralDirectoryBytes)
        output.writeIntLe(END_CENTRAL_DIRECTORY_SIGNATURE)
        output.writeShortLe(0)
        output.writeShortLe(0)
        output.writeShortLe(entries.size)
        output.writeShortLe(entries.size)
        output.writeIntLe(centralDirectoryBytes.size.toLong())
        output.writeIntLe(centralDirectoryOffset.toLong())
        output.writeShortLe(0)
        return output.toByteArray()
    }

    private fun sha256(input: InputStream): String {
        return BackupArchivePaths.sha256(input)
    }

    private fun ByteArrayOutputStream.writeShortLe(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeIntLe(value: Int) {
        writeIntLe(value.toLong())
    }

    private fun ByteArrayOutputStream.writeIntLe(value: Long) {
        write((value and 0xFF).toInt())
        write(((value ushr 8) and 0xFF).toInt())
        write(((value ushr 16) and 0xFF).toInt())
        write(((value ushr 24) and 0xFF).toInt())
    }

    private companion object {
        const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50
        const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50
        const val END_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50
        const val VERSION_NEEDED = 20
        const val STORED_METHOD = 0
    }
}

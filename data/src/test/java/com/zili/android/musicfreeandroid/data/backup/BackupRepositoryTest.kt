package com.zili.android.musicfreeandroid.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.logging.MfLogger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var repository: DefaultBackupRepository
    private lateinit var layout: BackupPrivateLayout
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private var checkpointCalled = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        checkpointCalled = 0

        layout = BackupPrivateLayout(
            filesDir = tmpFolder.root.resolve("files"),
            databaseFile = tmpFolder.root.resolve("databases/musicfree.db"),
        )
        setupFakeAppState(layout)
        MfLog.install(NoOpLogger)

        repository = DefaultBackupRepository(
            contentResolver = context.contentResolver,
            databaseCheckpoint = { checkpointCalled += 1 },
            layout = layout,
            appMetadata = BackupAppMetadata(
                packageName = "com.zili.android.musicfreeandroid",
                versionName = "1.2.3",
                versionCode = 123L,
            ),
            databaseVersion = 11,
            json = json,
        )
    }

    @After
    fun tearDown() {
        MfLog.resetForTest()
    }

    @Test
    fun `export writes archive to uri`() = runBlocking {
        val output = tmpFolder.root.resolve("export.mfbackup")
        val manifest = repository.exportTo(Uri.fromFile(output))

        assertTrue(output.length() > 0)
        ZipFile(output).use { zip ->
            val manifestEntry = zip.getEntry(BackupArchivePaths.MANIFEST)
            assertNotNull(manifestEntry)
            val restoredManifest = json.decodeFromString<BackupManifest>(
                zip.getInputStream(manifestEntry).bufferedReader().readText(),
            )
            assertEquals("com.zili.android.musicfreeandroid", restoredManifest.sourcePackageName)
            assertEquals(manifest.files.size, restoredManifest.files.size)
            assertNotNull(zip.getEntry(BackupArchivePaths.DB))
        }
    }

    @Test
    fun `stage restore registers pending`() = runBlocking {
        val output = tmpFolder.root.resolve("restore.mfbackup")
        writeBackupArchive(output)
        val staged = repository.stageRestoreFrom(Uri.fromFile(output))

        assertTrue("staged directory should exist", staged.directory.exists())
        assertTrue("staged id should start with restore-", staged.id.startsWith("restore-"))

        repository.registerPendingRestore(staged)

        val store = PendingRestoreStore(json, layout.filesDir)
        val pending = requireNotNull(store.readPending())
        assertEquals("pending id should match staged id", staged.id, pending.id)
        val stagingRelativePath = pending.stagingRelativePath
        val expectedRelativePath = "backup_restore/staging/${staged.id}"
        assertEquals(expectedRelativePath, stagingRelativePath)
        assertTrue("staging path should end with staged id", stagingRelativePath.endsWith(staged.id))
        assertTrue(
            "staging directory should exist in filesDir",
            layout.filesDir.resolve(stagingRelativePath).exists(),
        )
    }

    @Test
    fun `stage restore rejects newer database version`() {
        val output = tmpFolder.root.resolve("restore-newer-db.mfbackup")
        writeBackupArchive(output, databaseVersion = 12)

        val error = assertThrows(BackupArchiveException::class.java) {
            runBlocking {
                repository.stageRestoreFrom(Uri.fromFile(output))
            }
        }

        assertEquals("Backup database version 12 is newer than supported version 11", error.message)
    }

    @Test
    fun `export should invoke database checkpoint`() = runBlocking {
        val output = tmpFolder.root.resolve("checkpoint-export.mfbackup")
        repository.exportTo(Uri.fromFile(output))

        assertEquals(1, checkpointCalled)
    }

    private fun writeBackupArchive(
        output: File,
        databaseVersion: Int = 11,
    ) {
        val sourceFiles = BackupFileSetProvider(tmpFolder.root).listBackupSourceFiles()
        val metadata = BackupAppMetadata(
            packageName = "com.zili.android.musicfreeandroid",
            versionName = "1.2.3",
            versionCode = 123L,
        )
        output.outputStream().use { outputStream ->
            BackupArchiveWriter().write(
                output = outputStream,
                files = sourceFiles,
                metadata = metadata,
                databaseVersion = databaseVersion,
            )
        }
    }

    private fun setupFakeAppState(layout: BackupPrivateLayout) {
        val appDataRoot = tmpFolder.root
        appDataRoot.resolve("databases").mkdirs()
        appDataRoot.resolve("databases/musicfree.db").writeText("db")
        appDataRoot.resolve("databases/musicfree.db-wal").writeText("wal")
        appDataRoot.resolve("databases/musicfree.db-shm").writeText("shm")
        appDataRoot.resolve("datastore").mkdirs()
        appDataRoot.resolve("datastore/app_preferences.preferences_pb").writeText("prefs")

        val pluginsDir = appDataRoot.resolve("files/plugins")
        pluginsDir.mkdirs()
        pluginsDir.resolve("wy.js").writeText("console.log('plugin')")

        val coverDir = appDataRoot.resolve("files/playlist_covers/a")
        coverDir.mkdirs()
        coverDir.resolve("cover.jpg").writeText("cover")
        appDataRoot.resolve("files/theme_background.jpg").writeText("theme")
    }

    private object NoOpLogger : MfLogger {
        override fun trace(category: com.zili.android.musicfreeandroid.logging.LogCategory, event: String, fields: Map<String, Any?>) {}
        override fun detail(category: com.zili.android.musicfreeandroid.logging.LogCategory, event: String, fields: Map<String, Any?>) {}
        override fun error(
            category: com.zili.android.musicfreeandroid.logging.LogCategory,
            event: String,
            throwable: Throwable?,
            fields: Map<String, Any?>,
        ) {
            Unit
        }

        override fun flush() {}
    }
}

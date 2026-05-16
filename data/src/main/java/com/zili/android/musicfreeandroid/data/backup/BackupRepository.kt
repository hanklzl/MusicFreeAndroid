package com.zili.android.musicfreeandroid.data.backup

import android.content.ContentResolver
import android.net.Uri
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.MfLog
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

interface BackupRepository {
    suspend fun exportTo(uri: Uri): BackupManifest

    suspend fun stageRestoreFrom(uri: Uri): StagedRestore

    suspend fun registerPendingRestore(stagedRestore: StagedRestore)
}

class DefaultBackupRepository(
    private val contentResolver: ContentResolver,
    private val databaseCheckpoint: () -> Unit,
    private val layout: BackupPrivateLayout,
    private val appMetadata: BackupAppMetadata,
    private val databaseVersion: Int,
    private val json: Json,
    private val archiveWriter: BackupArchiveWriter = BackupArchiveWriter(),
    private val archiveReader: BackupArchiveReader = BackupArchiveReader(json),
) : BackupRepository {

    override suspend fun exportTo(uri: Uri): BackupManifest = withContext(Dispatchers.IO) {
        MfLog.detail(
            category = LogCategory.SETTINGS,
            event = "backup_export_started",
            fields = mapOf("uri" to uri.toString()),
        )

        try {
            databaseCheckpoint()

            val files = BackupFileSetProvider(resolveDataRoot()).listBackupSourceFiles()
            val output = contentResolver.openOutputStream(uri)
                ?: throw BackupArchiveException("Cannot open output stream for backup target: ${uri.schemeSpecificPart}")

            val manifest = output.use {
                archiveWriter.write(
                    output = it,
                    files = files,
                    metadata = appMetadata,
                    databaseVersion = databaseVersion,
                    createdAt = OffsetDateTime.now(ZoneOffset.UTC).toString(),
                )
            }

            MfLog.detail(
                category = LogCategory.SETTINGS,
                event = "backup_export_succeeded",
                fields = mapOf(
                    "uri" to uri.toString(),
                    "fileCount" to manifest.files.size,
                    "sourcePackage" to manifest.sourcePackageName,
                ),
            )

            manifest
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.SETTINGS,
                event = "backup_export_failed",
                throwable = error,
                fields = mapOf("uri" to uri.toString()),
            )
            throw error
        }
    }

    override suspend fun stageRestoreFrom(uri: Uri): StagedRestore = withContext(Dispatchers.IO) {
        MfLog.detail(
            category = LogCategory.SETTINGS,
            event = "backup_restore_stage_started",
            fields = mapOf("uri" to uri.toString()),
        )

        val id = "restore-${UUID.randomUUID()}"
        val stagingRoot = PendingRestoreStore(json, layout.filesDir).stagingRoot()

        try {
            val input = contentResolver.openInputStream(uri)
                ?: throw BackupArchiveException("Cannot open input stream for backup source: ${uri.schemeSpecificPart}")
            val staged = input.use { archiveReader.extractAndValidate(it, stagingRoot, id) }
            validateRestoreCompatibility(staged.manifest)
            MfLog.detail(
                category = LogCategory.SETTINGS,
                event = "backup_restore_stage_succeeded",
                fields = mapOf(
                    "id" to staged.id,
                    "fileCount" to staged.manifest.files.size,
                    "stagingRelativePath" to staged.stagingRelativePath(),
                ),
            )
            staged
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.SETTINGS,
                event = "backup_restore_stage_failed",
                throwable = error,
                fields = mapOf("uri" to uri.toString()),
            )
            throw error
        }
    }

    override suspend fun registerPendingRestore(stagedRestore: StagedRestore) = withContext(Dispatchers.IO) {
        MfLog.detail(
            category = LogCategory.SETTINGS,
            event = "backup_restore_pending_register_started",
            fields = mapOf(
                "id" to stagedRestore.id,
            ),
        )

        try {
            val stagingRelativePath = stagedRestore.stagingRelativePath()

            PendingRestoreStore(json, layout.filesDir).writePending(
                PendingRestoreRecord(
                    id = stagedRestore.id,
                    stagingRelativePath = stagingRelativePath,
                ),
            )

            MfLog.detail(
                category = LogCategory.SETTINGS,
                event = "backup_restore_pending_register_succeeded",
                fields = mapOf(
                    "id" to stagedRestore.id,
                    "stagingRelativePath" to stagingRelativePath,
                ),
            )
        } catch (error: Throwable) {
            MfLog.error(
                category = LogCategory.SETTINGS,
                event = "backup_restore_pending_register_failed",
                throwable = error,
                fields = mapOf("id" to stagedRestore.id),
            )
            throw error
        }
    }

    private fun resolveDataRoot(): File = layout.databaseFile.parentFile?.parentFile
        ?: layout.filesDir.parentFile
        ?: run {
            throw IllegalStateException("Cannot resolve app data root for backup provider")
        }

    private fun validateRestoreCompatibility(manifest: BackupManifest) {
        if (manifest.sourcePackageName.isBlank()) {
            throw BackupArchiveException("Backup source package name is empty")
        }
        if (manifest.databaseVersion <= 0) {
            throw BackupArchiveException("Backup database version is invalid: ${manifest.databaseVersion}")
        }
        if (manifest.databaseVersion > databaseVersion) {
            throw BackupArchiveException(
                "Backup database version ${manifest.databaseVersion} is newer than supported version $databaseVersion",
            )
        }
    }

    private fun StagedRestore.stagingRelativePath(): String {
        val filesRoot = layout.filesDir.canonicalFile
        val stagingDir = directory.canonicalFile
        if (!stagingDir.isInside(filesRoot)) {
            throw BackupArchiveException("Staged restore directory is outside app files: ${directory.path}")
        }
        return stagingDir.relativeTo(filesRoot).invariantSeparatorsPath
    }

    private fun File.isInside(parent: File): Boolean {
        val parentPath = parent.path
        return path.startsWith("$parentPath${File.separator}") && path != parentPath
    }
}

fun AppDatabase.checkpointWal() {
    openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
        cursor.moveToFirst()
    }
}

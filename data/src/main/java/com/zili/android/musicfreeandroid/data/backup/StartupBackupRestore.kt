package com.zili.android.musicfreeandroid.data.backup

import android.content.Context
import java.io.File
import java.io.IOException
import kotlinx.serialization.json.Json

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
        val pending = store.readPending() ?: return StartupRestoreResult(
            applied = false,
            message = "no pending restore",
        )

        val stagingDir = resolveStagingDir(layout.filesDir, pending.stagingRelativePath)
        if (stagingDir == null) {
            return failResult(
                pending = pending,
                message = "invalid staging path: ${pending.stagingRelativePath}",
                store = store,
            )
        }

        if (!stagingDir.isDirectory) {
            return failResult(
                pending = pending,
                message = "staging missing: ${pending.stagingRelativePath}",
                store = store,
            )
        }

        val backupRoot = store.restoreBackupRoot(pending.id)
        if (backupRoot.exists()) {
            val rollbackError = runCatching {
                rollbackToBackup(layout, backupRoot)
            }.exceptionOrNull()
            if (rollbackError != null) {
                val message = buildRollbackFailureMessage("previous restore rollback failed", rollbackError)
                recordFailure(
                    store = store,
                    pending = pending,
                    message = message,
                    clearPending = false,
                    rollbackComplete = false,
                )
                return StartupRestoreResult(applied = false, message = message)
            }
            backupRoot.deleteRecursively()
        }

        return try {
            moveCurrentTargetsToBackup(layout, backupRoot)
            moveStagedTargetsToCurrent(stagingDir, layout)

            store.clearPending()
            stagingDir.deleteRecursively()
            backupRoot.deleteRecursively()
            val status = RestoreStatusRecord(pending.id, applied = true, message = "restore applied")
            store.writeStatus(status)
            StartupRestoreResult(applied = true, message = status.message)
        } catch (error: Throwable) {
            val rollbackError = runCatching {
                rollbackToBackup(layout, backupRoot)
            }.exceptionOrNull()

            val rollbackComplete = rollbackError == null
            if (rollbackComplete) {
                backupRoot.deleteRecursively()
                stagingDir.deleteRecursively()
            }
            val statusMessage = if (rollbackError == null) {
                error.message ?: "restore failed"
            } else {
                buildRollbackFailureMessage(error.message ?: "restore failed", rollbackError)
            }

            recordFailure(
                store = store,
                pending = pending,
                message = statusMessage,
                clearPending = rollbackComplete,
                rollbackComplete = rollbackComplete,
            )
            StartupRestoreResult(applied = false, message = statusMessage)
        }
    }

    fun applyIfPending(context: Context): StartupRestoreResult =
        applyIfPending(BackupPrivateLayout.from(context))

    private fun failResult(
        pending: PendingRestoreRecord,
        message: String,
        store: PendingRestoreStore,
    ): StartupRestoreResult {
        recordFailure(store, pending, message, clearPending = true, rollbackComplete = true)
        return StartupRestoreResult(applied = false, message = message)
    }

    private fun recordFailure(
        store: PendingRestoreStore,
        pending: PendingRestoreRecord,
        message: String,
        clearPending: Boolean,
        rollbackComplete: Boolean,
    ) {
        runCatching {
            store.writeStatus(
                RestoreStatusRecord(
                    id = pending.id,
                    applied = false,
                    message = message,
                    retryable = !clearPending,
                    rollbackComplete = rollbackComplete,
                ),
            )
        }
        if (clearPending) {
            runCatching {
                store.clearPending()
            }
        }
    }

    private fun buildRollbackFailureMessage(message: String, rollbackError: Throwable): String =
        "$message; rollback failed: ${rollbackError.message ?: rollbackError::class.java.simpleName}"

    private fun resolveStagingDir(filesDir: File, stagingRelativePath: String): File? {
        if (stagingRelativePath.isBlank()) return null
        if (File(stagingRelativePath).isAbsolute) return null
        if (stagingRelativePath.contains("..")) return null

        val filesDirCanonical = filesDir.canonicalFile
        val stagingRootCanonical = File(filesDirCanonical, "backup_restore/staging").canonicalFile
        val stagingCandidate = File(filesDir, stagingRelativePath).canonicalFile
        if (!stagingCandidate.isInside(filesDirCanonical)) return null
        if (!stagingCandidate.isInside(stagingRootCanonical)) return null

        return stagingCandidate
    }

    private fun moveCurrentTargetsToBackup(layout: BackupPrivateLayout, backupRoot: File) {
        moveSafely(layout.databaseFile, File(backupRoot, BackupArchivePaths.DB))
        moveSafely(
            layout.databaseFile.resolveSibling("${layout.databaseFile.name}-wal"),
            File(backupRoot, BackupArchivePaths.DB_WAL),
        )
        moveSafely(
            layout.databaseFile.resolveSibling("${layout.databaseFile.name}-shm"),
            File(backupRoot, BackupArchivePaths.DB_SHM),
        )
        moveSafely(
            File(layout.filesDir, "datastore/app_preferences.preferences_pb"),
            File(backupRoot, BackupArchivePaths.DATASTORE),
        )
        moveSafely(
            File(layout.filesDir, "plugins"),
            File(backupRoot, "files/plugins"),
        )
        moveSafely(
            File(layout.filesDir, "playlist_covers"),
            File(backupRoot, "files/playlist_covers"),
        )

        val themeBackgroundFiles = layout.filesDir.listFiles { file ->
            file.isFile && file.name.startsWith("theme_background.")
        } ?: emptyArray()
        themeBackgroundFiles.forEach { source ->
            moveSafely(source, File(backupRoot, "files/${source.name}"))
        }
    }

    private fun moveStagedTargetsToCurrent(stagingDir: File, layout: BackupPrivateLayout) {
        moveSafely(
            File(stagingDir, BackupArchivePaths.DB),
            layout.databaseFile,
        )
        moveSafely(
            File(stagingDir, BackupArchivePaths.DB_WAL),
            layout.databaseFile.resolveSibling("${layout.databaseFile.name}-wal"),
        )
        moveSafely(
            File(stagingDir, BackupArchivePaths.DB_SHM),
            layout.databaseFile.resolveSibling("${layout.databaseFile.name}-shm"),
        )
        moveSafely(
            File(stagingDir, BackupArchivePaths.DATASTORE),
            File(layout.filesDir, "datastore/app_preferences.preferences_pb"),
        )
        moveSafely(
            File(stagingDir, "files/plugins"),
            File(layout.filesDir, "plugins"),
        )
        moveSafely(
            File(stagingDir, "files/playlist_covers"),
            File(layout.filesDir, "playlist_covers"),
        )

        val stagedThemeBackgroundFiles = File(stagingDir, "files").listFiles { file ->
            file.isFile && file.name.startsWith("theme_background.")
        } ?: emptyArray()
        stagedThemeBackgroundFiles.forEach { source ->
            moveSafely(source, File(layout.filesDir, source.name))
        }
    }

    private fun rollbackToBackup(layout: BackupPrivateLayout, backupRoot: File) {
        moveSafely(File(backupRoot, BackupArchivePaths.DB), layout.databaseFile)
        moveSafely(
            File(backupRoot, BackupArchivePaths.DB_WAL),
            layout.databaseFile.resolveSibling("${layout.databaseFile.name}-wal"),
        )
        moveSafely(
            File(backupRoot, BackupArchivePaths.DB_SHM),
            layout.databaseFile.resolveSibling("${layout.databaseFile.name}-shm"),
        )
        moveSafely(
            File(backupRoot, BackupArchivePaths.DATASTORE),
            File(layout.filesDir, "datastore/app_preferences.preferences_pb"),
        )
        moveSafely(
            File(backupRoot, "files/plugins"),
            File(layout.filesDir, "plugins"),
        )
        moveSafely(
            File(backupRoot, "files/playlist_covers"),
            File(layout.filesDir, "playlist_covers"),
        )

        val backupThemeBackgroundFiles = File(backupRoot, "files").listFiles { file ->
            file.isFile && file.name.startsWith("theme_background.")
        } ?: emptyArray()
        backupThemeBackgroundFiles.forEach { source ->
            moveSafely(source, File(layout.filesDir, source.name))
        }
    }

    private fun moveSafely(source: File, target: File) {
        if (!source.exists()) return

        val targetParent = target.parentFile
        if (targetParent != null) {
            if (!targetParent.isDirectory && !targetParent.mkdirs() && !targetParent.isDirectory) {
                throw IOException("Failed to create target parent: ${targetParent.path}")
            }
            if (!targetParent.isDirectory) {
                throw IOException("Target parent is not a directory: ${targetParent.path}")
            }
        }
        if (target.exists() && !target.deleteRecursively()) {
            throw IOException("Failed to remove existing target: ${target.path}")
        }
        if (source.renameTo(target)) return

        val moved = if (source.isDirectory) {
            source.copyRecursively(
                target = target,
                overwrite = true,
                onError = { _, error -> throw error },
            )
        } else {
            source.copyTo(target, overwrite = true).run { true }
        }
        if (!moved) throw IOException("Failed to copy source to target: ${source.path}")
        if (!source.deleteRecursively()) {
            throw IOException("Failed to remove source after copy: ${source.path}")
        }
    }

    private fun File.isInside(parent: File): Boolean {
        val parentPath = parent.path
        return path.startsWith("$parentPath${File.separator}") && path != parentPath
    }
}

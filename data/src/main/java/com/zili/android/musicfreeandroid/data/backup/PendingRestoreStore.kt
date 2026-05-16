package com.zili.android.musicfreeandroid.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
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
    val retryable: Boolean = false,
    val rollbackComplete: Boolean = true,
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

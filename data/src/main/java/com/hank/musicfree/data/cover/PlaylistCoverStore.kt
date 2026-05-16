package com.hank.musicfree.data.cover

import android.content.Context
import android.net.Uri
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistCoverStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val baseDir: File
        get() = File(context.filesDir, BASE_DIR_NAME).apply { mkdirs() }

    suspend fun saveFromUri(playlistId: String, src: Uri): String? = withContext(Dispatchers.IO) {
        val dest = File(baseDir, "$playlistId.jpg")
        val startedAt = System.nanoTime()
        val commonFields = mapOf(
            "playlistId" to playlistId,
            "pathType" to src.pathType(),
            "sourceScheme" to src.scheme.orEmpty(),
        )
        MfLog.detail(
            category = LogCategory.FILE_IO,
            event = "playlist_cover_save_start",
            fields = commonFields,
        )
        val copyResult = runCatching {
            context.contentResolver.openInputStream(src)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
                true
            } ?: false
        }
        val sizeBytes = dest.length().takeIf { dest.exists() } ?: 0L
        val savedUri = if (dest.exists() && sizeBytes > 0) Uri.fromFile(dest).toString() else null
        if (copyResult.isSuccess && savedUri != null) {
            MfLog.detail(
                category = LogCategory.FILE_IO,
                event = "playlist_cover_save_success",
                fields = commonFields + mapOf(
                    "sizeBytes" to sizeBytes,
                    "result" to LogFields.Result.SUCCESS,
                    "durationMs" to elapsedMs(startedAt),
                ),
            )
        } else {
            val reason = when {
                copyResult.isFailure -> "copy_failed"
                copyResult.getOrDefault(false).not() -> "open_input_failed"
                else -> "empty_output"
            }
            MfLog.error(
                category = LogCategory.FILE_IO,
                event = "playlist_cover_save_failed",
                throwable = copyResult.exceptionOrNull(),
                fields = commonFields + mapOf(
                    "sizeBytes" to sizeBytes,
                    "result" to LogFields.Result.FAILURE,
                    "reason" to reason,
                    "durationMs" to elapsedMs(startedAt),
                ),
            )
        }
        savedUri
    }

    suspend fun copyFromArtwork(playlistId: String, artworkUrl: String?): String? {
        if (artworkUrl.isNullOrBlank()) {
            logCopySkipped(playlistId, sourceScheme = "", pathType = "unknown", reason = LogFields.Reason.EMPTY_INPUT)
            return null
        }
        val uri = runCatching { Uri.parse(artworkUrl) }.getOrNull() ?: run {
            logCopySkipped(playlistId, sourceScheme = "", pathType = "unknown", reason = LogFields.Reason.INVALID_URL)
            return null
        }
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> {
                logCopySkipped(
                    playlistId = playlistId,
                    sourceScheme = uri.scheme.orEmpty(),
                    pathType = uri.pathType(),
                    reason = "remote_url",
                )
                artworkUrl
            }
            "file", "content" -> saveFromUri(playlistId, uri)
            else -> {
                logCopySkipped(
                    playlistId = playlistId,
                    sourceScheme = uri.scheme.orEmpty(),
                    pathType = uri.pathType(),
                    reason = LogFields.Reason.UNSUPPORTED,
                )
                null
            }
        }
    }

    suspend fun delete(playlistId: String) = withContext(Dispatchers.IO) {
        val file = File(baseDir, "$playlistId.jpg")
        val existed = file.exists()
        val sizeBytes = if (existed) file.length() else 0L
        val result = runCatching { file.delete() }
        val deleted = result.getOrDefault(false)
        val fields = mapOf(
            "playlistId" to playlistId,
            "pathType" to "file_uri",
            "sourceScheme" to "file",
            "sizeBytes" to sizeBytes,
        )
        when {
            result.isFailure -> MfLog.error(
                category = LogCategory.FILE_IO,
                event = "playlist_cover_delete",
                throwable = result.exceptionOrNull(),
                fields = fields + mapOf(
                    "result" to LogFields.Result.FAILURE,
                    "reason" to "delete_failed",
                ),
            )
            deleted -> MfLog.detail(
                category = LogCategory.FILE_IO,
                event = "playlist_cover_delete",
                fields = fields + mapOf(
                    "result" to LogFields.Result.SUCCESS,
                    "reason" to "",
                ),
            )
            existed -> MfLog.error(
                category = LogCategory.FILE_IO,
                event = "playlist_cover_delete",
                fields = fields + mapOf(
                    "result" to LogFields.Result.FAILURE,
                    "reason" to "delete_failed",
                ),
            )
            else -> MfLog.detail(
                category = LogCategory.FILE_IO,
                event = "playlist_cover_delete",
                fields = fields + mapOf(
                    "result" to LogFields.Result.SKIPPED,
                    "reason" to LogFields.Reason.NOT_FOUND,
                ),
            )
        }
        Unit
    }

    fun absoluteFile(relativePath: String): File = File(context.filesDir, relativePath)

    companion object { const val BASE_DIR_NAME = "playlist_covers" }

    private fun logCopySkipped(
        playlistId: String,
        sourceScheme: String,
        pathType: String,
        reason: String,
    ) {
        MfLog.detail(
            category = LogCategory.FILE_IO,
            event = "playlist_cover_copy_skipped",
            fields = mapOf(
                "playlistId" to playlistId,
                "pathType" to pathType,
                "sourceScheme" to sourceScheme,
                "sizeBytes" to 0L,
                "result" to LogFields.Result.SKIPPED,
                "reason" to reason,
            ),
        )
    }

    private fun Uri.pathType(): String = when (scheme?.lowercase()) {
        "content" -> "content_uri"
        "file" -> "file_uri"
        "http", "https" -> "remote_url"
        else -> "unknown"
    }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
}

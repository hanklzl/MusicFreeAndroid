package com.zili.android.musicfreeandroid.data.cover

import android.content.Context
import android.net.Uri
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
        runCatching {
            context.contentResolver.openInputStream(src)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
        }
        if (dest.exists() && dest.length() > 0) Uri.fromFile(dest).toString() else null
    }

    suspend fun copyFromArtwork(playlistId: String, artworkUrl: String?): String? {
        if (artworkUrl.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(artworkUrl) }.getOrNull() ?: return null
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> artworkUrl
            "file", "content" -> saveFromUri(playlistId, uri)
            else -> null
        }
    }

    suspend fun delete(playlistId: String) = withContext(Dispatchers.IO) {
        File(baseDir, "$playlistId.jpg").delete()
        Unit
    }

    fun absoluteFile(relativePath: String): File = File(context.filesDir, relativePath)

    companion object { const val BASE_DIR_NAME = "playlist_covers" }
}

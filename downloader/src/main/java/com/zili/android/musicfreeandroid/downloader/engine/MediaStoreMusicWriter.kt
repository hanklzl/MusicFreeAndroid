package com.zili.android.musicfreeandroid.downloader.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreMusicWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun commit(
        cacheFile: File,
        displayName: String,
        mimeType: String,
        relativePath: String,
        sizeBytes: Long,
    ): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val initial = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Audio.Media.SIZE, sizeBytes)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            initial,
        ) ?: throw IOException("MediaStore.insert returned null")
        try {
            resolver.openOutputStream(uri).use { out ->
                requireNotNull(out) { "openOutputStream returned null" }
                cacheFile.inputStream().use { it.copyTo(out) }
            }
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
        uri
    }
}

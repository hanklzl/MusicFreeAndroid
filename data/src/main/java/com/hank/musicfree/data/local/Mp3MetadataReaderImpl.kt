package com.hank.musicfree.data.local

import android.content.Context
import android.media.MediaMetadataRetriever
import com.hank.musicfree.core.local.Mp3Metadata
import com.hank.musicfree.core.local.Mp3MetadataReader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Mp3MetadataReaderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : Mp3MetadataReader {
    override suspend fun read(path: String): Mp3Metadata? {
        val file = File(path)
        if (!file.exists() || !file.canRead()) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            Mp3Metadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                coverBytes = retriever.embeddedPicture,
                embeddedLrc = null,  // ID3 USLT not exposed via MediaMetadataRetriever
            )
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}

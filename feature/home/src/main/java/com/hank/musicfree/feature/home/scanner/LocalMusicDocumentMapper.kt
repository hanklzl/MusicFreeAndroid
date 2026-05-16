package com.hank.musicfree.feature.home.scanner

import android.content.ContentResolver
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.hank.musicfree.core.model.MusicItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMusicDocumentMapper @Inject constructor(
    private val contentResolver: ContentResolver,
) {

    fun map(
        documentUri: Uri,
        displayName: String,
    ): MusicItem {
        val metadata = runCatching { readMetadata(documentUri) }.getOrDefault(LocalMusicMetadata())
        val fallbackTitle = displayName.substringBeforeLast(".").ifBlank {
            displayName.ifBlank { documentUri.lastPathSegment ?: "Unknown" }
        }

        return MusicItem(
            id = documentUri.toString(),
            platform = LocalMusicScanner.PLATFORM_LOCAL,
            title = metadata.title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
            artist = (metadata.artist ?: "").cleanUnknown(),
            album = (metadata.album ?: "").cleanUnknown().ifBlank { null },
            duration = metadata.durationMs ?: 0L,
            url = documentUri.toString(),
            artwork = null,
            qualities = null,
        )
    }

    private fun readMetadata(documentUri: Uri): LocalMusicMetadata {
        contentResolver.openAssetFileDescriptor(documentUri, "r")?.use { descriptor ->
            val retriever = MediaMetadataRetriever()
            try {
                if (descriptor.declaredLength >= 0) {
                    retriever.setDataSource(
                        descriptor.fileDescriptor,
                        descriptor.startOffset,
                        descriptor.declaredLength,
                    )
                } else {
                    retriever.setDataSource(descriptor.fileDescriptor)
                }

                return LocalMusicMetadata(
                    title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                    album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                )
            } finally {
                retriever.release()
            }
        }

        return LocalMusicMetadata()
    }
}

private data class LocalMusicMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
)

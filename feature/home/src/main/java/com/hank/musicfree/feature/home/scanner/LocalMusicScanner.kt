package com.hank.musicfree.feature.home.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.hank.musicfree.core.model.MusicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMusicScanner @Inject constructor(
    private val contentResolver: ContentResolver,
    private val documentMapper: LocalMusicDocumentMapper,
) {

    fun scan(storageDirectoryUri: String? = null): Flow<List<MusicItem>> = flow {
        emit(
            if (storageDirectoryUri.isNullOrBlank()) {
                queryMediaStore()
            } else {
                queryDocumentTree(storageDirectoryUri)
            }
        )
    }.flowOn(Dispatchers.IO)

    private fun queryMediaStore(): List<MusicItem> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder,
        ) ?: return emptyList()

        val items = mutableListOf<MusicItem>()

        cursor.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (it.moveToNext()) {
                val albumId = it.getLong(albumIdCol)
                val artworkUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId,
                ).toString()

                items.add(
                    MusicItem(
                        id = it.getLong(idCol).toString(),
                        platform = PLATFORM_LOCAL,
                        title = it.getString(titleCol) ?: "Unknown",
                        artist = (it.getString(artistCol) ?: "").cleanUnknown(),
                        album = (it.getString(albumCol) ?: "").cleanUnknown().ifBlank { null },
                        duration = it.getLong(durationCol),
                        url = it.getString(dataCol),
                        artwork = artworkUri,
                        qualities = null,
                    )
                )
            }
        }
        return items
    }

    private fun queryDocumentTree(treeUriString: String): List<MusicItem> {
        val treeUri = Uri.parse(treeUriString)
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)

        return scanDocumentTree(treeUri, rootDocumentId)
            .sortedBy { it.title.lowercase() }
    }

    private fun scanDocumentTree(
        treeUri: Uri,
        documentId: String,
    ): List<MusicItem> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val cursor = contentResolver.query(
            childrenUri,
            DOCUMENT_TREE_PROJECTION,
            null,
            null,
            null,
        ) ?: return emptyList()

        val items = mutableListOf<MusicItem>()

        cursor.use {
            val idCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeTypeCol = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (it.moveToNext()) {
                val childDocumentId = it.getString(idCol) ?: continue
                val displayName = it.getString(nameCol) ?: ""
                val mimeType = it.getString(mimeTypeCol) ?: ""

                when {
                    mimeType == DocumentsContract.Document.MIME_TYPE_DIR -> {
                        items += scanDocumentTree(treeUri, childDocumentId)
                    }

                    mimeType.isAudioMimeType() || displayName.hasAudioExtension() -> {
                        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocumentId)
                        items += documentMapper.map(documentUri, displayName)
                    }
                }
            }
        }

        return items
    }

    companion object {
        const val PLATFORM_LOCAL = "local"
        private val DOCUMENT_TREE_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
    }
}

internal fun String.cleanUnknown(): String =
    if (this == "<unknown>" || this == "Unknown" || this == "未知歌手" || this == "未知专辑") "" else this

private fun String.isAudioMimeType(): Boolean = startsWith("audio/", ignoreCase = true)

private fun String.hasAudioExtension(): Boolean {
    val extension = substringAfterLast('.', "").lowercase()
    return extension in AUDIO_FILE_EXTENSIONS
}

private val AUDIO_FILE_EXTENSIONS = setOf(
    "aac",
    "flac",
    "m4a",
    "mp3",
    "ogg",
    "opus",
    "wav",
    "wma",
)

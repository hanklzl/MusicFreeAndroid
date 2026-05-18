package com.hank.musicfree.core.storage

import android.net.Uri
import androidx.core.net.toUri

data class DocumentTreeDirectory(
    val treeUri: String,
    val displayName: String,
) {
    val uri: Uri
        get() = treeUri.toUri()

    companion object {
        fun fromTreeUri(treeUri: String): DocumentTreeDirectory {
            val uri = treeUri.toUri()
            val displayName = uri.lastPathSegment
                ?.substringAfterLast(':')
                ?.takeIf { it.isNotBlank() }
                ?: treeUri
            return DocumentTreeDirectory(
                treeUri = treeUri,
                displayName = displayName,
            )
        }
    }
}

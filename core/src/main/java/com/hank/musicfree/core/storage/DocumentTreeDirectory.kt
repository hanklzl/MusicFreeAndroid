package com.hank.musicfree.core.storage

import android.net.Uri

data class DocumentTreeDirectory(
    val treeUri: String,
    val displayName: String,
) {
    val uri: Uri
        get() = Uri.parse(treeUri)

    companion object {
        fun fromTreeUri(treeUri: String): DocumentTreeDirectory {
            val uri = Uri.parse(treeUri)
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

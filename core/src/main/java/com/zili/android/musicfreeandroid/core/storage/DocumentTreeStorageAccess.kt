package com.zili.android.musicfreeandroid.core.storage

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

object DocumentTreeStorageAccess {

    private const val PERSISTABLE_PERMISSION_FLAGS =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    fun persistReadWritePermission(
        contentResolver: ContentResolver,
        treeUri: Uri,
    ) {
        contentResolver.takePersistableUriPermission(treeUri, PERSISTABLE_PERMISSION_FLAGS)
    }

    fun releaseReadWritePermission(
        contentResolver: ContentResolver,
        treeUri: Uri,
    ) {
        runCatching {
            contentResolver.releasePersistableUriPermission(treeUri, PERSISTABLE_PERMISSION_FLAGS)
        }
    }
}

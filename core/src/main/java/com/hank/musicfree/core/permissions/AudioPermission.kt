package com.hank.musicfree.core.permissions

import android.os.Build

const val READ_MEDIA_AUDIO_PERMISSION = "android.permission.READ_MEDIA_AUDIO"
const val READ_EXTERNAL_STORAGE_PERMISSION = "android.permission.READ_EXTERNAL_STORAGE"

fun requiredAudioPermission(sdkInt: Int = Build.VERSION.SDK_INT): String {
    return if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
        READ_MEDIA_AUDIO_PERMISSION
    } else {
        READ_EXTERNAL_STORAGE_PERMISSION
    }
}

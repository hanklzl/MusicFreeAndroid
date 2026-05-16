package com.hank.musicfree.core.permissions

import android.Manifest
import android.os.Build

fun requiredAudioPermission(sdkInt: Int = Build.VERSION.SDK_INT): String {
    return if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

package com.hank.musicfree.core.permissions

import android.Manifest
import android.os.Build

fun requiredNotificationPermission(sdkInt: Int = Build.VERSION.SDK_INT): String? {
    return if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.POST_NOTIFICATIONS
    } else {
        null
    }
}

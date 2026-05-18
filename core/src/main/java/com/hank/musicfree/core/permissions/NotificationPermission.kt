package com.hank.musicfree.core.permissions

import android.os.Build

const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"

fun requiredNotificationPermission(sdkInt: Int = Build.VERSION.SDK_INT): String? {
    return if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
        POST_NOTIFICATIONS_PERMISSION
    } else {
        null
    }
}

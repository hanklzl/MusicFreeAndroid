package com.hank.musicfree.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.core.content.ContextCompat
import com.hank.musicfree.core.permissions.requiredAudioPermission
import com.hank.musicfree.core.permissions.requiredNotificationPermission

internal fun readPermissionsUiState(context: Context): PermissionsUiState {
    return PermissionsUiState(
        overlayGranted = AndroidSettings.canDrawOverlays(context),
        storageAudioGranted = hasStorageAudioPermission(context),
        notificationGranted = hasNotificationPermission(context),
    )
}

internal fun hasStorageAudioPermission(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
    return ContextCompat.checkSelfPermission(context, requiredAudioPermission(sdkInt)) ==
        PackageManager.PERMISSION_GRANTED
}

internal fun hasNotificationPermission(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
    val permission = requiredNotificationPermission(sdkInt) ?: return true
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

internal fun buildOverlaySettingsIntent(context: Context): Intent {
    return Intent(
        AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

internal fun openOverlaySettings(context: Context): Boolean {
    return runCatching {
        context.startActivity(buildOverlaySettingsIntent(context))
        true
    }.getOrDefault(false)
}

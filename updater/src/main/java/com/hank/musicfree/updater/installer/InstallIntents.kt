package com.hank.musicfree.updater.installer

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri

object InstallIntents {
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    fun installApk(uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            clipData = ClipData.newRawUri("MusicFree update APK", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    @Suppress("DEPRECATION")
    internal fun grantReadPermissionToInstallers(context: Context, intent: Intent, uri: Uri): Int {
        val targetPackages = linkedSetOf<String>()
        context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .forEach { resolveInfo ->
                resolveInfo.activityInfo?.packageName
                    ?.takeIf { it.isNotBlank() }
                    ?.let(targetPackages::add)
                resolveInfo.resolvePackageName
                    ?.takeIf { it.isNotBlank() }
                    ?.let(targetPackages::add)
            }
        targetPackages.forEach { packageName ->
            context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return targetPackages.size
    }

    fun manageUnknownAppSources(packageName: String): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = "package:$packageName".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}

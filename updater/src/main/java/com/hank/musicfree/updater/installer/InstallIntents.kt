package com.hank.musicfree.updater.installer

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri

object InstallIntents {
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    @Suppress("DEPRECATION")
    fun installApk(uri: Uri): Intent =
        Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            clipData = ClipData.newRawUri("MusicFree update APK", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    @Suppress("DEPRECATION")
    internal fun grantReadPermissionToInstallers(context: Context, intent: Intent, uri: Uri): Int {
        val targetPackages = linkedSetOf<String>()

        fun addTarget(resolveInfo: ResolveInfo?) {
            val activityInfo = resolveInfo?.activityInfo ?: return
            val appFlags = activityInfo.applicationInfo?.flags ?: 0
            val isSystemInstaller =
                appFlags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (!isSystemInstaller) return

            activityInfo.packageName
                ?.takeIf { it.isNotBlank() }
                ?.let(targetPackages::add)
            resolveInfo.resolvePackageName
                ?.takeIf { it.isNotBlank() }
                ?.let(targetPackages::add)
        }

        addTarget(context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY))
        context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .forEach(::addTarget)
        context.packageManager
            .queryIntentActivities(intent, 0)
            .forEach(::addTarget)
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

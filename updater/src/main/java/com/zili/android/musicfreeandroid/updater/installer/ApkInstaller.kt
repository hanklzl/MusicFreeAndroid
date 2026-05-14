package com.zili.android.musicfreeandroid.updater.installer

import android.content.Context
import androidx.core.content.FileProvider
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.updater.checker.UpdateError
import java.io.File
import javax.inject.Singleton

@Singleton
class ApkInstaller(
    private val context: Context,
) {
    sealed interface InstallResult {
        data object Started : InstallResult
        data class Blocked(val cause: UpdateError) : InstallResult
    }

    fun install(apkFile: File): InstallResult {
        MfLog.trace(
            category = LogCategory.UPDATE,
            event = "apk_install_trigger",
            fields = mapOf("fileName" to apkFile.name),
        )
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                InstallIntents.manageUnknownAppSources(context.packageName),
            )
            return InstallResult.Blocked(UpdateError.InstallBlocked)
        }
        val authority = "${context.packageName}.updater-files"
        val uri = FileProvider.getUriForFile(context, authority, apkFile)
        context.startActivity(InstallIntents.installApk(uri))
        return InstallResult.Started
    }
}

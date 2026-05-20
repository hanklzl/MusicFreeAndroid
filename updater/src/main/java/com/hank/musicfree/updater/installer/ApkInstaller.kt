package com.hank.musicfree.updater.installer

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.core.content.FileProvider
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.updater.checker.UpdateError
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
        return try {
            val uri = FileProvider.getUriForFile(context, authority, apkFile)
            val intent = InstallIntents.installApk(uri)
            val grantTargetCount = InstallIntents.grantReadPermissionToInstallers(context, intent, uri)
            MfLog.detail(
                category = LogCategory.UPDATE,
                event = "apk_install_intent_prepared",
                fields = mapOf(
                    "fileName" to apkFile.name,
                    "authority" to authority,
                    "intentAction" to intent.action,
                    "grantTargetCount" to grantTargetCount,
                    "hasClipData" to (intent.clipData != null),
                ),
            )
            context.startActivity(intent)
            InstallResult.Started
        } catch (error: ActivityNotFoundException) {
            logInstallStartFailed(error, apkFile, authority, "activity_not_found")
            InstallResult.Blocked(UpdateError.InstallBlocked)
        } catch (error: SecurityException) {
            logInstallStartFailed(error, apkFile, authority, "security_exception")
            InstallResult.Blocked(UpdateError.InstallBlocked)
        } catch (error: RuntimeException) {
            logInstallStartFailed(error, apkFile, authority, "runtime_exception")
            InstallResult.Blocked(UpdateError.InstallBlocked)
        }
    }

    private fun logInstallStartFailed(
        error: Throwable,
        apkFile: File,
        authority: String,
        reason: String,
    ) {
        MfLog.error(
            category = LogCategory.UPDATE,
            event = "apk_install_start_failed",
            throwable = error,
            fields = mapOf(
                "fileName" to apkFile.name,
                "authority" to authority,
                "reason" to reason,
            ),
        )
    }
}

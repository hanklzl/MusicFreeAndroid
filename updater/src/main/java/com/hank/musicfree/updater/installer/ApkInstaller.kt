package com.hank.musicfree.updater.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import androidx.annotation.VisibleForTesting
import androidx.core.app.PendingIntentCompat
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.updater.checker.UpdateError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Singleton

@Singleton
class ApkInstaller(
    private val context: Context,
) {
    sealed interface InstallResult {
        data object Started : InstallResult
        data class Blocked(val cause: UpdateError) : InstallResult
    }

    suspend fun install(apkFile: File): InstallResult {
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
        return withContext(Dispatchers.IO) {
            try {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL,
                ).apply {
                    setAppPackageName(context.packageName)
                    setInstallReason(PackageManager.INSTALL_REASON_USER)
                }
                val sessionId = installer.createSession(params)
                try {
                    installer.openSession(sessionId).use { session ->
                        apkFile.inputStream().use { input ->
                            session.openWrite("base.apk", 0, apkFile.length()).use { out ->
                                input.copyTo(out)
                                session.fsync(out)
                            }
                        }
                        session.commit(buildStatusPendingIntent(sessionId).intentSender)
                    }
                } catch (t: Throwable) {
                    val abandonOutcome = runCatching { installer.abandonSession(sessionId) }
                    MfLog.detail(
                        category = LogCategory.UPDATE,
                        event = "apk_install_session_abandoned",
                        fields = mapOf(
                            "sessionId" to sessionId,
                            "reason" to (t::class.java.simpleName ?: "Throwable"),
                            "abandonError" to (abandonOutcome.exceptionOrNull()?.javaClass?.simpleName.orEmpty()),
                        ),
                    )
                    throw t
                }
                MfLog.detail(
                    category = LogCategory.UPDATE,
                    event = "apk_install_session_committed",
                    fields = mapOf(
                        "fileName" to apkFile.name,
                        "sessionId" to sessionId,
                        "bytes" to apkFile.length(),
                    ),
                )
                InstallResult.Started
            } catch (error: IOException) {
                logInstallStartFailed(error, apkFile, "package-installer", "io_exception")
                InstallResult.Blocked(UpdateError.InstallFailed)
            } catch (error: SecurityException) {
                logInstallStartFailed(error, apkFile, "package-installer", "security_exception")
                InstallResult.Blocked(UpdateError.InstallFailed)
            } catch (error: RuntimeException) {
                logInstallStartFailed(error, apkFile, "package-installer", "runtime_exception")
                InstallResult.Blocked(UpdateError.InstallFailed)
            }
        }
    }

    @VisibleForTesting
    internal fun buildStatusPendingIntent(sessionId: Int): PendingIntent {
        val intent = Intent(InstallStatusReceiver.ACTION_INSTALL_STATUS).setPackage(context.packageName)
        return PendingIntentCompat.getBroadcast(
            context,
            sessionId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT,
            /* isMutable = */ true,
        )!!
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

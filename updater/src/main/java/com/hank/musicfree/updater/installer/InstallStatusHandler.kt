package com.hank.musicfree.updater.installer

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.updater.checker.UpdateChecker
import com.hank.musicfree.updater.checker.UpdateError
import com.hank.musicfree.updater.checker.UpdateState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstallStatusHandler @Inject constructor(
    private val checker: UpdateChecker,
) {
    fun handle(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirm)
                MfLog.detail(
                    category = LogCategory.UPDATE,
                    event = "apk_install_user_action_pending",
                    fields = mapOf("statusMessage" to statusMessage.orEmpty()),
                )
            }
            PackageInstaller.STATUS_SUCCESS -> {
                val successUpdate = when (val s = checker.state.value) {
                    is UpdateState.ReadyToInstall -> s.update
                    is UpdateState.Downloading -> s.update
                    else -> null
                }
                MfLog.detail(
                    category = LogCategory.UPDATE,
                    event = "apk_install_succeeded",
                    fields = mapOf(
                        "statusMessage" to statusMessage.orEmpty(),
                        "versionCode" to (successUpdate?.info?.versionCode ?: -1L),
                        "abi" to (successUpdate?.abi ?: ""),
                    ),
                )
                // 不动 UpdateState（安装完成系统会重启进程）。
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME).orEmpty()
                MfLog.detail(
                    category = LogCategory.UPDATE,
                    event = "apk_install_user_canceled",
                    fields = mapOf(
                        "statusMessage" to statusMessage.orEmpty(),
                        "packageName" to packageName,
                    ),
                )
                val readyUpdate = (checker.state.value as? UpdateState.ReadyToInstall)?.update
                if (readyUpdate != null) {
                    checker.transitionAvailable(readyUpdate, skipped = false)
                }
            }
            else -> {
                val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME).orEmpty()
                MfLog.error(
                    category = LogCategory.UPDATE,
                    event = "apk_install_failed",
                    fields = mapOf(
                        "status" to status,
                        "statusMessage" to statusMessage.orEmpty(),
                        "packageName" to packageName,
                    ),
                )
                val readyUpdate = (checker.state.value as? UpdateState.ReadyToInstall)?.update
                if (readyUpdate != null) {
                    checker.transitionFailed(readyUpdate, UpdateError.InstallFailed)
                }
            }
        }
    }
}

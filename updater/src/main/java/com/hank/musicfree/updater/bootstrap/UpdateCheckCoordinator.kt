package com.hank.musicfree.updater.bootstrap

import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.updater.checker.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateCheckCoordinator @Inject constructor(
    private val checker: UpdateChecker,
    private val localAppVersion: LocalAppVersion,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        if (localAppVersion.isDebugBuild) {
            MfLog.trace(
                category = LogCategory.UPDATE,
                event = "update_check_skipped_debug",
                fields = mapOf("reason" to "debug_build"),
            )
            return
        }
        scope.launch { checker.checkOnLaunch() }
    }
}

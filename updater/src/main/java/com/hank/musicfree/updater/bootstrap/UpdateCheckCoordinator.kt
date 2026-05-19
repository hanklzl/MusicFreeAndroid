package com.hank.musicfree.updater.bootstrap

import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.updater.checker.UpdateChecker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateCheckCoordinator internal constructor(
    private val checker: UpdateChecker,
    private val localAppVersion: LocalAppVersion,
    dispatcher: CoroutineDispatcher,
) {

    @Inject
    constructor(
        checker: UpdateChecker,
        localAppVersion: LocalAppVersion,
    ) : this(checker, localAppVersion, Dispatchers.IO)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    fun start(startupFields: Map<String, Any?> = emptyMap()) {
        val startedAtNano = System.nanoTime()
        val baseFields = startupFields + ("flowName" to FLOW_NAME)
        MfLog.detail(
            category = LogCategory.UPDATE,
            event = "startup_flow_start",
            fields = baseFields,
        )
        if (localAppVersion.isDebugBuild) {
            MfLog.trace(
                category = LogCategory.UPDATE,
                event = "update_check_skipped_debug",
                fields = mapOf("reason" to "debug_build"),
            )
            MfLog.detail(
                category = LogCategory.UPDATE,
                event = "startup_flow_skipped",
                fields = baseFields + mapOf(
                    "durationMs" to ((System.nanoTime() - startedAtNano) / 1_000_000L).coerceAtLeast(0L),
                    "result" to LogFields.Result.SKIPPED,
                    "reason" to "debug_build",
                ),
            )
            return
        }
        scope.launch { checker.checkOnLaunch(startupFields = baseFields) }
    }

    private companion object {
        const val FLOW_NAME = "update_check_on_launch"
    }
}

package com.hank.musicfree.updater.downloader

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.hank.musicfree.core.di.ApplicationScope
import com.hank.musicfree.core.network.NetworkType
import com.hank.musicfree.core.network.NetworkTypeDetector
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.updater.checker.ResolvedUpdate
import com.hank.musicfree.updater.checker.UpdateCheckSource
import com.hank.musicfree.updater.checker.UpdateChecker
import com.hank.musicfree.updater.checker.UpdateError
import com.hank.musicfree.updater.service.UpdateDownloadService
import com.hank.musicfree.updater.store.UpdatePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Singleton
class UpdateDownloadManager internal constructor(
    private val checker: UpdateChecker,
    private val downloader: ApkDownloader,
    private val prefs: UpdatePreferences,
    private val networkType: () -> NetworkType,
    private val scope: CoroutineScope,
    private val serviceStarter: () -> Unit,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        checker: UpdateChecker,
        downloader: ApkDownloader,
        prefs: UpdatePreferences,
        networkTypeDetector: NetworkTypeDetector,
        @ApplicationScope scope: CoroutineScope,
    ) : this(
        checker = checker,
        downloader = downloader,
        prefs = prefs,
        networkType = networkTypeDetector::current,
        scope = scope,
        serviceStarter = {
            ContextCompat.startForegroundService(
                context,
                Intent(context, UpdateDownloadService::class.java).setAction(UpdateDownloadService.ACTION_START),
            )
        },
    )

    private var activeJob: Job? = null

    fun startSilentIfAllowed(update: ResolvedUpdate) {
        scope.launch {
            if (!prefs.silentUpdateDownloadEnabled.first()) {
                logSkipped(update, "preference_disabled")
                return@launch
            }
            if (networkType() != NetworkType.WIFI) {
                logSkipped(update, "network_not_wifi")
                return@launch
            }
            if (prefs.getSilentDownloadCanceledVersion() == update.info.version) {
                logSkipped(update, "version_canceled")
                return@launch
            }
            start(update, DownloadMode.Silent)
        }
    }

    fun downloadNow(update: ResolvedUpdate) {
        start(update, DownloadMode.Manual)
    }

    fun cancelActiveDownload(reason: String = "user_cancel") {
        MfLog.trace(
            category = LogCategory.UPDATE,
            event = "update_download_cancel_requested",
            fields = mapOf("reason" to reason),
        )
        downloader.cancel()
    }

    private fun start(update: ResolvedUpdate, mode: DownloadMode) {
        val running = activeJob
        if (running?.isActive == true) {
            MfLog.trace(
                category = LogCategory.UPDATE,
                event = "update_download_start_skipped",
                fields = mapOf(
                    "versionCode" to update.info.versionCode,
                    "mode" to mode.logName,
                    "reason" to "already_running",
                    "result" to LogFields.Result.SKIPPED,
                ),
            )
            return
        }
        runCatching { serviceStarter() }.getOrElse { error ->
            MfLog.error(
                category = LogCategory.UPDATE,
                event = "update_download_service_start_failed",
                throwable = error,
                fields = mapOf(
                    "versionCode" to update.info.versionCode,
                    "mode" to mode.logName,
                    "result" to LogFields.Result.FAILURE,
                ),
            )
            checker.transitionFailed(update, UpdateError.Network)
            return
        }
        activeJob = scope.launch {
            if (mode == DownloadMode.Manual) {
                prefs.clearSilentDownloadCanceledVersion()
            }
            MfLog.detail(
                category = LogCategory.UPDATE,
                event = "update_download_start",
                fields = mapOf(
                    "versionCode" to update.info.versionCode,
                    "version" to update.info.version,
                    "abi" to update.abi,
                    "mode" to mode.logName,
                ),
            )
            checker.transitionDownloading(update, 0f, 0L, update.variant.size)
            val result = downloader.download(update) { bytes, total, fraction ->
                checker.transitionDownloading(update, fraction, bytes, total)
            }
            when (result) {
                is ApkDownloader.Result.Success -> {
                    prefs.clearSilentDownloadCanceledVersion()
                    checker.transitionReady(update, result.apkFile)
                }
                is ApkDownloader.Result.Failure -> {
                    if (result.cause == UpdateError.Canceled) {
                        if (mode == DownloadMode.Silent) {
                            prefs.setSilentDownloadCanceledVersion(update.info.version)
                        }
                        checker.transitionAvailable(
                            update = update,
                            skipped = false,
                            source = if (mode == DownloadMode.Silent) {
                                UpdateCheckSource.Launch
                            } else {
                                UpdateCheckSource.Manual
                            },
                        )
                    } else {
                        checker.transitionFailed(update, result.cause)
                    }
                }
            }
        }
    }

    private fun logSkipped(update: ResolvedUpdate, reason: String) {
        MfLog.trace(
            category = LogCategory.UPDATE,
            event = "update_silent_download_skipped",
            fields = mapOf(
                "versionCode" to update.info.versionCode,
                "version" to update.info.version,
                "reason" to reason,
                "networkType" to networkType().name,
                "result" to LogFields.Result.SKIPPED,
            ),
        )
    }

    private enum class DownloadMode(val logName: String) {
        Manual("manual"),
        Silent("silent"),
    }
}

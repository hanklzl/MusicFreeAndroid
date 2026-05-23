package com.hank.musicfree.updater.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.updater.checker.UpdateChecker
import com.hank.musicfree.updater.checker.UpdateState
import com.hank.musicfree.updater.downloader.UpdateDownloadManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class UpdateDownloadService : Service() {

    @Inject
    lateinit var checker: UpdateChecker

    @Inject
    lateinit var manager: UpdateDownloadManager

    @Inject
    lateinit var notifier: UpdateDownloadNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        notifier.ensureChannel()
        scope.launch {
            checker.state.collectLatest { state ->
                when (state) {
                    is UpdateState.Downloading -> {
                        notifySafely(
                            notifier.buildOngoing(
                                update = state.update,
                                bytes = state.bytes,
                                total = state.total,
                            ),
                        )
                    }
                    is UpdateState.ReadyToInstall -> {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        notifySafely(notifier.buildReady(state.update))
                        stopSelf()
                    }
                    is UpdateState.Failed -> {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    else -> Unit
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                manager.cancelActiveDownload("notification_cancel")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
            else -> {
                startForeground(
                    UpdateDownloadNotifier.NOTIFICATION_ID,
                    notifier.buildOngoing(update = null, bytes = 0L, total = 0L),
                )
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notifySafely(notification: android.app.Notification) {
        runCatching {
            notificationManager.notify(UpdateDownloadNotifier.NOTIFICATION_ID, notification)
        }.onFailure {
            MfLog.error(
                category = LogCategory.UPDATE,
                event = "update_download_notification_failed",
                throwable = it,
            )
        }
    }

    companion object {
        const val ACTION_START = "com.hank.musicfree.updater.action.START_UPDATE_DOWNLOAD"
        const val ACTION_CANCEL = "com.hank.musicfree.updater.action.CANCEL_UPDATE_DOWNLOAD"
    }
}

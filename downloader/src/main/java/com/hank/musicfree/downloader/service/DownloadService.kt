package com.hank.musicfree.downloader.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.hank.musicfree.downloader.engine.DownloadEngine
import com.hank.musicfree.downloader.model.DownloadStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var engine: DownloadEngine
    @Inject lateinit var notifier: DownloadNotifier

    private var serviceScope: CoroutineScope? = null

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == DownloadNotifier.ACTION_CANCEL_ALL) {
                engine.cancelAllInflight()
                stopSelfSafely()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            cancelReceiver,
            IntentFilter(DownloadNotifier.ACTION_CANCEL_ALL),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Spin up a scope tied to Service lifetime that reissues the notification
        // whenever engine.tasks changes.
        val scope = CoroutineScope(
            Dispatchers.Main.immediate + SupervisorJob()
        )
        serviceScope = scope
        scope.launch {
            engine.tasks.collect { tasks ->
                if (tasks.isEmpty()) {
                    stopSelfSafely()
                } else {
                    val active = tasks.count {
                        it.status != DownloadStatus.FAILED
                    }
                    val total = tasks.size
                    val completed = total - active
                    val totalBytes = tasks.sumOf { it.totalBytes ?: 0L }
                    val downloadedBytes = tasks.sumOf { it.downloadedBytes ?: 0L }
                    val percent = if (totalBytes > 0L) ((downloadedBytes * 100 / totalBytes).toInt()) else 0
                    val notif = notifier.buildOngoing(active, total, completed, percent)
                    val nm = applicationContext.getSystemService(android.app.NotificationManager::class.java)
                    nm?.notify(DownloadNotifier.NOTIF_ID, notif)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(DownloadNotifier.NOTIF_ID, notifier.buildOngoing(0, 0, 0, 0))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(cancelReceiver) }
        serviceScope?.cancel()
        serviceScope = null
        super.onDestroy()
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}

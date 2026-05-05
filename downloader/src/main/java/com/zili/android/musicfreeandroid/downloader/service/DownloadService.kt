package com.zili.android.musicfreeandroid.downloader.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.zili.android.musicfreeandroid.downloader.engine.DownloadEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var engine: DownloadEngine
    @Inject lateinit var notifier: DownloadNotifier

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
        engine.start()
        ContextCompat.registerReceiver(
            this,
            cancelReceiver,
            IntentFilter(DownloadNotifier.ACTION_CANCEL_ALL),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(DownloadNotifier.NOTIF_ID, notifier.buildOngoing(0, 0, 0, 0))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(cancelReceiver) }
        engine.stop()
        super.onDestroy()
    }

    private fun stopSelfSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
    }
}

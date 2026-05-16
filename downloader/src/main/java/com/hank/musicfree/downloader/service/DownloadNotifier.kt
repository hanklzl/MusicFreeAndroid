package com.hank.musicfree.downloader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "download_progress"
        const val NOTIF_ID = 0xD107
        const val ACTION_CANCEL_ALL = "com.hank.musicfree.downloader.CANCEL_ALL"
        const val ACTION_OPEN = "com.hank.musicfree.downloader.OPEN"
    }

    init { ensureChannel() }

    private fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "下载进度", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    fun buildOngoing(active: Int, total: Int, completed: Int, percent: Int): Notification {
        val cancelIntent = Intent(ACTION_CANCEL_ALL).setPackage(context.packageName)
        val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            ?: Intent()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("MusicFree")
            .setContentText("正在下载 $active 首歌（$completed/$total 完成）")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(PendingIntent.getActivity(context, 2, openIntent, flags))
            .addAction(0, "取消所有", PendingIntent.getBroadcast(context, 1, cancelIntent, flags))
            .build()
    }
}

package com.hank.musicfree.updater.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hank.musicfree.updater.checker.ResolvedUpdate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateDownloadNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val notificationManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "软件更新下载",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    fun buildOngoing(update: ResolvedUpdate?, bytes: Long, total: Long): Notification {
        ensureChannel()
        val progressMax = if (total > 0L) 100 else 0
        val progress = if (total > 0L) {
            ((bytes.coerceIn(0L, total) * 100L) / total).toInt()
        } else {
            0
        }
        return baseBuilder()
            .setContentTitle("正在下载软件更新")
            .setContentText(update?.info?.version?.let { "版本 $it" } ?: "准备下载")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(progressMax, progress, total <= 0L)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "取消",
                cancelIntent(),
            )
            .build()
    }

    fun buildReady(update: ResolvedUpdate): Notification {
        ensureChannel()
        return baseBuilder()
            .setContentTitle("软件更新已下载")
            .setContentText("版本 ${update.info.version} 已准备安装")
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
    }

    private fun baseBuilder(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(launchIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

    private fun launchIntent(): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(context.packageName)
        return PendingIntent.getActivity(
            context,
            REQUEST_LAUNCH,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelIntent(): PendingIntent =
        PendingIntent.getService(
            context,
            REQUEST_CANCEL,
            Intent(context, UpdateDownloadService::class.java).setAction(UpdateDownloadService.ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val CHANNEL_ID = "update_download"
        const val NOTIFICATION_ID = 0xD108

        private const val REQUEST_LAUNCH = 1
        private const val REQUEST_CANCEL = 2
    }
}

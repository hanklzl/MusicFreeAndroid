package com.zili.android.musicfreeandroid.logging

import java.io.File
import java.time.Clock
import java.time.Duration

object LogPruner {
    fun prune(
        logDir: File,
        retentionDays: Int,
        maxTotalBytes: Long,
        clock: Clock = Clock.systemDefaultZone(),
    ) {
        val allFiles = logDir.listFiles()?.filter { it.isFile } ?: return

        val cutoffMs = clock.millis() - Duration.ofDays(retentionDays.toLong()).toMillis()
        allFiles.filter { it.lastModified() < cutoffMs }.forEach { it.delete() }

        val remaining = logDir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var totalBytes = remaining.sumOf { it.length() }
        for (file in remaining) {
            if (totalBytes <= maxTotalBytes) {
                return
            }
            val fileSize = file.length()
            if (file.delete()) {
                totalBytes -= fileSize
            }
        }
    }
}

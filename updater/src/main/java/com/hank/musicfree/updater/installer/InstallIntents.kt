package com.hank.musicfree.updater.installer

import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

object InstallIntents {
    fun manageUnknownAppSources(packageName: String): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = "package:$packageName".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}

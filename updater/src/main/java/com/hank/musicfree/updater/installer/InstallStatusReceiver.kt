package com.hank.musicfree.updater.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class InstallStatusReceiver : BroadcastReceiver() {

    @Inject
    lateinit var handler: InstallStatusHandler

    override fun onReceive(context: Context, intent: Intent) {
        handler.handle(context, intent)
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.hank.musicfree.updater.INSTALL_STATUS"
    }
}

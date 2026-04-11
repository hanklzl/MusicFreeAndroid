package com.zili.android.musicfreeandroid.navigation

import androidx.activity.ComponentActivity
import com.zili.android.musicfreeandroid.feature.home.HomeSystemActionHandler
import com.zili.android.musicfreeandroid.player.controller.PlayerController

class AndroidHomeSystemActionHandler(
    private val activity: ComponentActivity,
    private val playerController: PlayerController,
) : HomeSystemActionHandler {

    override fun backToDesktop() {
        activity.moveTaskToBack(true)
    }

    override suspend fun exitApp() {
        playerController.reset()
        activity.finishAffinity()
    }
}

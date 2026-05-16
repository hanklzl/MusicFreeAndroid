package com.hank.musicfree.navigation

import androidx.activity.ComponentActivity
import com.hank.musicfree.feature.home.HomeSystemActionHandler
import com.hank.musicfree.player.controller.PlayerController

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

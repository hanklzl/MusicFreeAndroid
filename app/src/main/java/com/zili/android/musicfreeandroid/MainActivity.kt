package com.zili.android.musicfreeandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zili.android.musicfreeandroid.core.navigation.PlayerRoute
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.feature.playerui.component.MiniPlayer
import com.zili.android.musicfreeandroid.navigation.AndroidHomeSystemActionHandler
import com.zili.android.musicfreeandroid.navigation.AppNavHost
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var playerController: PlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MusicFreeTheme {
                val navController = rememberNavController()
                val homeSystemActionHandler = remember(this, playerController) {
                    AndroidHomeSystemActionHandler(
                        activity = this,
                        playerController = playerController,
                    )
                }
                val currentBackStack by navController.currentBackStackEntryAsState()
                val destination = currentBackStack?.destination

                val isPlayerRoute = destination?.hasRoute<PlayerRoute>() == true
                val showMiniPlayer = destination != null && !isPlayerRoute

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                    bottomBar = {
                        if (showMiniPlayer) {
                            MiniPlayer(
                                onNavigateToPlayer = {
                                    navController.navigate(PlayerRoute)
                                },
                            )
                        }
                    },
                ) { innerPadding ->
                    AppNavHost(
                        navController = navController,
                        homeSystemActionHandler = homeSystemActionHandler,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

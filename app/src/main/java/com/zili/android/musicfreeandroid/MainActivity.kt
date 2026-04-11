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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zili.android.musicfreeandroid.core.navigation.HomeRoute
import com.zili.android.musicfreeandroid.core.navigation.PlayerRoute
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.feature.playerui.component.MiniPlayer
import com.zili.android.musicfreeandroid.feature.playerui.component.MiniPlayerContent
import com.zili.android.musicfreeandroid.feature.playerui.component.MiniPlayerUiModel
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
                var isHomeMockPlaying by rememberSaveable { mutableStateOf(true) }

                val isHomeRoute = destination?.hasRoute<HomeRoute>() == true
                val isPlayerRoute = destination?.hasRoute<PlayerRoute>() == true
                val showRealMiniPlayer = destination != null && !isHomeRoute && !isPlayerRoute
                val applyHomeTopSafeInset = isHomeRoute

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                    bottomBar = {
                        when {
                            isHomeRoute -> MiniPlayerContent(
                                uiModel = MiniPlayerUiModel(
                                    coverUri = null,
                                    title = "In the End",
                                    subtitle = "Linkin Park",
                                    isPlaying = isHomeMockPlaying,
                                    showQueueButton = true,
                                ),
                                onOpenPlayer = {},
                                onTogglePlayPause = {
                                    isHomeMockPlaying = !isHomeMockPlaying
                                },
                                onOpenQueue = {},
                            )

                            showRealMiniPlayer -> {
                                MiniPlayer(
                                    onNavigateToPlayer = {
                                        navController.navigate(PlayerRoute)
                                    },
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    AppNavHost(
                        navController = navController,
                        homeSystemActionHandler = homeSystemActionHandler,
                        modifier = Modifier
                            .padding(innerPadding)
                            .then(
                                if (applyHomeTopSafeInset) {
                                    Modifier.windowInsetsPadding(
                                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
    }
}

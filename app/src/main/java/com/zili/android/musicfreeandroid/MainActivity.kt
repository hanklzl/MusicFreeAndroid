package com.zili.android.musicfreeandroid

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zili.android.musicfreeandroid.core.navigation.PlayerRoute
import com.zili.android.musicfreeandroid.core.permissions.requiredNotificationPermission
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.downloader.engine.DownloadEvent
import com.zili.android.musicfreeandroid.feature.playerui.component.MiniPlayer
import com.zili.android.musicfreeandroid.navigation.AndroidHomeSystemActionHandler
import com.zili.android.musicfreeandroid.navigation.AppNavHost
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var playerController: PlayerController

    @Inject
    lateinit var downloader: Downloader

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                downloader.events.collect { event ->
                    when (event) {
                        is DownloadEvent.QueueIdle -> {
                            Toast.makeText(
                                this@MainActivity,
                                "下载任务已完成",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        else -> Unit
                    }
                }
            }
        }
        enableEdgeToEdge()
        setContent {
            MusicFreeTheme {
                val notificationPermission = requiredNotificationPermission()
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) {
                    // The permission state is also visible from Settings > 权限管理.
                }
                LaunchedEffect(notificationPermission) {
                    if (
                        notificationPermission != null &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            notificationPermission,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(notificationPermission)
                    }
                }
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

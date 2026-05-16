package com.zili.android.musicfreeandroid

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zili.android.musicfreeandroid.core.navigation.PlayerRoute
import com.zili.android.musicfreeandroid.core.permissions.requiredNotificationPermission
import com.zili.android.musicfreeandroid.core.theme.DarkMusicFreeColors
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.theme.runtime.SelectedTheme
import com.zili.android.musicfreeandroid.core.theme.runtime.ThemeRepository
import com.zili.android.musicfreeandroid.core.theme.runtime.ThemeUiState
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.downloader.engine.DownloadEvent
import com.zili.android.musicfreeandroid.feature.playerui.component.MiniPlayer
import com.zili.android.musicfreeandroid.navigation.AppNavHost
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.updater.checker.UpdateChecker
import com.zili.android.musicfreeandroid.updater.downloader.ApkDownloader
import com.zili.android.musicfreeandroid.updater.installer.ApkInstaller
import com.zili.android.musicfreeandroid.updater.ui.UpdateDialogHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var downloader: Downloader

    @Inject
    lateinit var updateChecker: UpdateChecker

    @Inject
    lateinit var apkDownloader: ApkDownloader

    @Inject
    lateinit var apkInstaller: ApkInstaller

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var themeRepository: ThemeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        MfLog.trace(LogCategory.APP, "main_activity_create_start")
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
        MfLog.trace(LogCategory.APP, "edge_to_edge_enabled")
        setContent {
            val systemDark = isSystemInDarkTheme()
            val themeState by themeRepository.state.collectAsStateWithLifecycle(
                initialValue = ThemeUiState(
                    selected = SelectedTheme.P_DARK,
                    effectiveColors = DarkMusicFreeColors,
                    background = null,
                    followSystem = false,
                    isLoading = true,
                ),
            )
            LaunchedEffect(systemDark, themeState.followSystem) {
                if (themeState.followSystem) {
                    val target = if (systemDark) SelectedTheme.P_DARK else SelectedTheme.P_LIGHT
                    if (themeState.selected != target) {
                        themeRepository.selectTheme(target)
                    }
                }
            }
            MusicFreeTheme(themeState = themeState) {
                UpdateDialogHost(
                    checker = updateChecker,
                    downloader = apkDownloader,
                    installer = apkInstaller,
                )
                val notificationPermission = requiredNotificationPermission()
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) {
                    MfLog.trace(
                        LogCategory.APP,
                        "notification_permission_result",
                        mapOf(
                            "permission" to (notificationPermission ?: "none"),
                            "granted" to it,
                        ),
                    )
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
                    } else {
                        MfLog.trace(
                            LogCategory.APP,
                            "notification_permission_result",
                            mapOf(
                                "permission" to (notificationPermission ?: "none"),
                                "granted" to (notificationPermission == null ||
                                    ContextCompat.checkSelfPermission(
                                        this@MainActivity,
                                        notificationPermission,
                                    ) == PackageManager.PERMISSION_GRANTED),
                            ),
                        )
                    }
                }
                val navController = rememberNavController()
                val currentBackStack by navController.currentBackStackEntryAsState()
                val destination = currentBackStack?.destination
                var debugPanelEnabled by remember { mutableStateOf(false) }

                LaunchedEffect(appPreferences) {
                    appPreferences.debugDevLogEnabled.collect { enabled ->
                        debugPanelEnabled = enabled
                    }
                }

                val isPlayerRoute = destination?.hasRoute<PlayerRoute>() == true
                val showMiniPlayer = destination != null && !isPlayerRoute

                Box(modifier = Modifier.fillMaxSize()) {
                    ThemeBackgroundLayer(themeState.background)
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
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                    if (debugPanelEnabled) {
                        DebugPanelOverlay(modifier = Modifier.align(Alignment.TopEnd))
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun DebugPanelOverlay(modifier: Modifier = Modifier) {
    val switches = MfLog.currentSwitches()
    Column(
        modifier = modifier
            .padding(top = rpx(56), end = rpx(16))
            .background(Color.Black.copy(alpha = 0.68f))
            .padding(horizontal = rpx(16), vertical = rpx(10)),
    ) {
        Text(
            text = "Debug",
            color = Color.White,
        )
        Text(
            text = "error=${switches.errorEnabled} detail=${switches.detailEnabled}",
            color = Color.White.copy(alpha = 0.82f),
        )
    }
}

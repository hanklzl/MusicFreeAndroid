package com.hank.musicfree

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
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
import com.hank.musicfree.core.navigation.PlayerRoute
import com.hank.musicfree.core.permissions.requiredNotificationPermission
import com.hank.musicfree.core.theme.DarkMusicFreeColors
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.theme.runtime.SelectedTheme
import com.hank.musicfree.core.theme.runtime.ThemeRepository
import com.hank.musicfree.core.theme.runtime.ThemeUiState
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.downloader.Downloader
import com.hank.musicfree.downloader.engine.DownloadEvent
import com.hank.musicfree.feature.playerui.component.MiniPlayer
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.navigation.AppNavHost
import com.hank.musicfree.startup.StartupTelemetry
import com.hank.musicfree.startup.StartupTelemetry.StartupActivitySession
import com.hank.musicfree.updater.checker.UpdateChecker
import com.hank.musicfree.updater.downloader.ApkDownloader
import com.hank.musicfree.updater.installer.ApkInstaller
import com.hank.musicfree.updater.ui.UpdateDialogHost
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

    private var startupSession: StartupActivitySession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val startup = StartupTelemetry.beginActivityCreate()
        startupSession = startup
        startup.logInstant(
            event = "app_startup_activity_create_start",
            phase = "activity_create_start",
            result = LogFields.Result.SUCCESS,
        )
        val splashPhase = startup.startPhase("splash_installed")
        installSplashScreen()
        startup.completePhase(
            event = "app_startup_splash_installed",
            phase = "splash_installed",
            span = splashPhase,
            result = LogFields.Result.SUCCESS,
        )
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
        val edgeToEdgePhase = startup.startPhase("edge_to_edge_enabled")
        enableEdgeToEdge()
        startup.completePhase(
            event = "app_startup_edge_to_edge_enabled",
            phase = "edge_to_edge_enabled",
            span = edgeToEdgePhase,
            result = LogFields.Result.SUCCESS,
        )
        MfLog.trace(LogCategory.APP, "edge_to_edge_enabled")
        val contentSetPhase = startup.startPhase("activity_content_set")
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
        StartupTelemetry.markContentSet(
            session = startup,
            span = contentSetPhase,
            result = LogFields.Result.SUCCESS,
        )
        reportFirstFrame(window.decorView, startup)
    }

    override fun onResume() {
        super.onResume()
        startupSession?.let(StartupTelemetry::recordActivityResume)
    }
}

private fun reportFirstFrame(rootView: View, startup: StartupActivitySession) {
    val firstFramePhase = startup.startPhase("first_frame")
    val observer = rootView.viewTreeObserver
    val listener = object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            val currentObserver = rootView.viewTreeObserver
            if (currentObserver.isAlive) {
                currentObserver.removeOnPreDrawListener(this)
            } else if (observer.isAlive) {
                observer.removeOnPreDrawListener(this)
            }
            StartupTelemetry.markFirstFrame(
                session = startup,
                span = firstFramePhase,
                result = LogFields.Result.SUCCESS,
            )
            return true
        }
    }
    observer.addOnPreDrawListener(listener)
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

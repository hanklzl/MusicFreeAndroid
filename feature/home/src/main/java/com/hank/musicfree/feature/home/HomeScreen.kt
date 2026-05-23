package com.hank.musicfree.feature.home

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.navigation.SettingsType
import com.hank.musicfree.core.runtime.rememberUiRuntimeStore
import com.hank.musicfree.core.ui.logUiClick
import com.hank.musicfree.feature.home.playlist.CreatePlaylistDialog
import com.hank.musicfree.feature.home.playlistimport.PlaylistImportRoute
import com.hank.musicfree.feature.home.playlistimport.PlaylistImportViewModel
import com.hank.musicfree.feature.home.sheets.HomeSheetTab
import com.hank.musicfree.feature.home.sheets.HomeSheetUiModel
import com.hank.musicfree.feature.home.sheets.HomeSheetsViewModel
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.updater.checker.UpdateState

@Composable
fun HomeScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToRecommendSheets: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToListenStats: () -> Unit,
    onNavigateToTrafficStats: () -> Unit,
    onNavigateToLocal: () -> Unit,
    onNavigateToSettings: (SettingsType) -> Unit,
    onNavigateToPluginList: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onOpenFeedback: () -> Unit,
    onNavigateToTopList: () -> Unit,
    onNavigateToPlaylistDetail: (String) -> Unit,
    onNavigateToStarredSheet: (HomeSheetUiModel) -> Unit,
    onNavigateToStarredAlbum: (HomeSheetUiModel) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    importViewModel: PlaylistImportViewModel = hiltViewModel(),
    homeSheetsViewModel: HomeSheetsViewModel = hiltViewModel(),
    updateBadgeViewModel: UpdateBadgeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state = remember { HomeScreenState() }
    val uiRuntimeStore = rememberUiRuntimeStore()
    val uiRuntimeState by uiRuntimeStore.state.collectAsStateWithLifecycle()
    val selectedTab = remember(uiRuntimeState.homeTab) {
        uiRuntimeState.homeTab.toHomeSheetTabOrDefault()
    }
    val playlists by viewModel.playlists.collectAsState()
    val starredSheets by viewModel.starredSheets.collectAsState()
    val currentVersion = remember(context) {
        context.packageManager.versionNameForPackage(context.packageName).orEmpty()
    }
    val scheduleCloseSummary = ""

    val updateState by updateBadgeViewModel.checker.state.collectAsState()
    val updateTrailingText = when (val s = updateState) {
        is UpdateState.Available -> "v${s.update.info.version} 可用"
        is UpdateState.Checking -> "检查中…"
        is UpdateState.UpToDate -> currentVersion
        is UpdateState.Failed -> "检查失败"
        else -> currentVersion
    }
    val hasUpdateBadge = updateState.hasUnreadAvailableUpdate

    val mineRows = remember(playlists) {
        playlists.map { p ->
            HomeSheetUiModel.fromPlaylist(
                playlist = p,
                musicCount = p.worksNum,
                isDefault = p.isDefault,
            )
        }
    }

    val starredRows = remember(starredSheets) {
        starredSheets.map(HomeSheetUiModel.Companion::fromStarredSheet)
    }

    val visualUiModel = remember(selectedTab, mineRows, starredRows) {
        buildHomeVisualUiModel(selectedTab, mineRows, starredRows)
    }

    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var pendingUnstar by remember { mutableStateOf<HomeSheetUiModel?>(null) }

    val drawerUiModel = remember(currentVersion, scheduleCloseSummary, updateTrailingText, hasUpdateBadge) {
        buildHomeDrawerUiModel(
            currentVersion = currentVersion,
            scheduleCloseSummary = scheduleCloseSummary,
            updateTrailingText = updateTrailingText,
            hasUpdateBadge = hasUpdateBadge,
        )
    }

    HomeScreenContent(
        state = state,
        visualUiModel = visualUiModel,
        drawerUiModel = drawerUiModel,
        currentVersion = currentVersion,
        scheduleCloseSummary = scheduleCloseSummary,
        checker = updateBadgeViewModel.checker,
        downloadManager = updateBadgeViewModel.downloadManager,
        installer = updateBadgeViewModel.installer,
        onDrawerEntryClick = { action ->
            val targetId = when (action) {
                HomeDrawerAction.OpenListenStats -> "home.drawer.listen_stats"
                HomeDrawerAction.OpenTrafficStats -> "home.drawer.traffic_stats"
                HomeDrawerAction.OpenSettingsRoot -> "home.drawer.settings_basic"
                HomeDrawerAction.OpenPluginManagement -> "home.drawer.plugin_list"
                HomeDrawerAction.OpenThemeSettings -> "home.drawer.settings_theme"
                HomeDrawerAction.OpenBackup -> "home.drawer.settings_backup"
                HomeDrawerAction.OpenAbout -> "home.drawer.settings_about"
                HomeDrawerAction.OpenPermissions -> "home.drawer.permissions"
                HomeDrawerAction.OpenFeedback -> "home.drawer.feedback"
                HomeDrawerAction.ShowScheduleClosePanel -> "home.drawer.schedule_close"
                HomeDrawerAction.TriggerManualUpdateCheck -> "home.drawer.check_update"
            }
            logUiClick(targetId, screen = "home")
            when (action) {
                HomeDrawerAction.OpenListenStats -> onNavigateToListenStats()
                HomeDrawerAction.OpenTrafficStats -> onNavigateToTrafficStats()
                HomeDrawerAction.OpenSettingsRoot -> onNavigateToSettings(SettingsType.Basic)
                HomeDrawerAction.OpenPluginManagement -> onNavigateToPluginList()
                HomeDrawerAction.OpenThemeSettings -> onNavigateToSettings(SettingsType.Theme)
                HomeDrawerAction.OpenBackup -> onNavigateToSettings(SettingsType.Backup)
                HomeDrawerAction.OpenAbout -> onNavigateToSettings(SettingsType.About)
                HomeDrawerAction.OpenPermissions -> onNavigateToPermissions()
                HomeDrawerAction.OpenFeedback -> onOpenFeedback()
                HomeDrawerAction.ShowScheduleClosePanel,
                HomeDrawerAction.TriggerManualUpdateCheck -> Unit
            }
        },
        onNavigateToSearch = {
            logUiClick("home.search_bar", screen = "home", targetLabel = "搜索")
            onNavigateToSearch()
        },
        onNavigateToRecommendSheets = {
            logUiClick("home.recommend_sheets", screen = "home")
            onNavigateToRecommendSheets()
        },
        onNavigateToTopList = {
            logUiClick("home.top_list", screen = "home")
            onNavigateToTopList()
        },
        onNavigateToHistory = {
            logUiClick("home.history", screen = "home")
            onNavigateToHistory()
        },
        onNavigateToLocal = {
            logUiClick("home.local", screen = "home")
            onNavigateToLocal()
        },
        onSelectTab = {
            logUiClick("home.sheet_tab.${it.name.lowercase()}", screen = "home")
            uiRuntimeStore.setHomeTab(it.name)
        },
        onCreateClick = {
            logUiClick("home.sheet.create", screen = "home")
            showCreateDialog = true
        },
        onImportClick = {
            logUiClick("home.sheet.import", screen = "home")
            importViewModel.openImportSheet()
        },
        onOpenMineSheet = { sheetId ->
            logUiClick("home.sheet.mine_row", screen = "home", extra = mapOf("sheetId" to sheetId))
            onNavigateToPlaylistDetail(sheetId)
        },
        onOpenStarredSheet = {
            logUiClick("home.sheet.starred_sheet_row", screen = "home", extra = mapOf("id" to it.id))
            onNavigateToStarredSheet(it)
        },
        onOpenStarredAlbum = {
            logUiClick("home.sheet.starred_album_row", screen = "home", extra = mapOf("id" to it.id))
            onNavigateToStarredAlbum(it)
        },
        onTrashClick = { row ->
            pendingUnstar = row
            MfLog.detail(
                category = LogCategory.APP,
                event = "starred_unstar_confirm_shown",
                fields = mapOf(
                    "kind" to row.kind,
                    "platform" to (row.platform ?: ""),
                    "id" to row.id,
                ),
            )
        },
    )

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            },
        )
    }

    pendingUnstar?.let { row ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingUnstar = null },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    homeSheetsViewModel.unstar(row)
                    pendingUnstar = null
                }) {
                    androidx.compose.material3.Text("确定")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingUnstar = null }) {
                    androidx.compose.material3.Text("取消")
                }
            },
            title = { androidx.compose.material3.Text("取消收藏") },
            text = { androidx.compose.material3.Text("确定要取消收藏「${row.title}」吗？") },
        )
    }

    PlaylistImportRoute(
        modifier = Modifier,
        viewModel = importViewModel,
    )
}

private fun PackageManager.versionNameForPackage(packageName: String): String? {
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(packageName, 0)
    }
    return packageInfo.versionName
}

private fun String?.toHomeSheetTabOrDefault(): HomeSheetTab =
    HomeSheetTab.entries.firstOrNull { it.name == this } ?: HomeSheetTab.Mine

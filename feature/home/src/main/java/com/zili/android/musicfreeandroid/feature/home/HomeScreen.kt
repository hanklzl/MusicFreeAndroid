package com.zili.android.musicfreeandroid.feature.home

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
import com.zili.android.musicfreeandroid.core.navigation.SettingsType
import com.zili.android.musicfreeandroid.feature.home.playlist.CreatePlaylistDialog
import com.zili.android.musicfreeandroid.feature.home.playlistimport.PlaylistImportRoute
import com.zili.android.musicfreeandroid.feature.home.playlistimport.PlaylistImportViewModel
import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetTab
import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetUiModel
import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetsViewModel
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.MfLog

@Composable
fun HomeScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToRecommendSheets: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToListenStats: () -> Unit,
    onNavigateToLocal: () -> Unit,
    onNavigateToSettings: (SettingsType) -> Unit,
    onNavigateToPluginList: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToTopList: () -> Unit,
    onNavigateToPlaylistDetail: (String) -> Unit,
    onNavigateToStarredSheet: (HomeSheetUiModel) -> Unit,
    onNavigateToStarredAlbum: (HomeSheetUiModel) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    importViewModel: PlaylistImportViewModel = hiltViewModel(),
    homeSheetsViewModel: HomeSheetsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state = remember { HomeScreenState() }
    var selectedTab by rememberSaveable { mutableStateOf(HomeSheetTab.Mine) }
    val playlists by viewModel.playlists.collectAsState()
    val starredSheets by viewModel.starredSheets.collectAsState()

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

    val currentVersion = remember(context) {
        context.packageManager.versionNameForPackage(context.packageName).orEmpty()
    }
    val scheduleCloseSummary = ""
    val drawerUiModel = remember(currentVersion, scheduleCloseSummary) {
        buildHomeDrawerUiModel(
            currentVersion = currentVersion,
            scheduleCloseSummary = scheduleCloseSummary,
        )
    }

    HomeScreenContent(
        state = state,
        visualUiModel = visualUiModel,
        drawerUiModel = drawerUiModel,
        currentVersion = currentVersion,
        scheduleCloseSummary = scheduleCloseSummary,
        onDrawerEntryClick = { action ->
            when (action) {
                HomeDrawerAction.OpenListenStats -> onNavigateToListenStats()
                HomeDrawerAction.OpenSettingsRoot -> onNavigateToSettings(SettingsType.Basic)
                HomeDrawerAction.OpenPluginManagement -> onNavigateToPluginList()
                HomeDrawerAction.OpenThemeSettings -> onNavigateToSettings(SettingsType.Theme)
                HomeDrawerAction.OpenBackup -> onNavigateToSettings(SettingsType.Backup)
                HomeDrawerAction.OpenAbout -> onNavigateToSettings(SettingsType.About)
                HomeDrawerAction.OpenPermissions -> onNavigateToPermissions()
                HomeDrawerAction.ShowScheduleClosePanel,
                HomeDrawerAction.ShowUpdateCheckDialog -> Unit
            }
        },
        onNavigateToSearch = onNavigateToSearch,
        onNavigateToRecommendSheets = onNavigateToRecommendSheets,
        onNavigateToTopList = onNavigateToTopList,
        onNavigateToHistory = onNavigateToHistory,
        onNavigateToLocal = onNavigateToLocal,
        onSelectTab = { selectedTab = it },
        onCreateClick = { showCreateDialog = true },
        onImportClick = { importViewModel.openImportSheet() },
        onOpenMineSheet = { sheetId -> onNavigateToPlaylistDetail(sheetId) },
        onOpenStarredSheet = onNavigateToStarredSheet,
        onOpenStarredAlbum = onNavigateToStarredAlbum,
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

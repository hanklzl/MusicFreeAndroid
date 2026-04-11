package com.zili.android.musicfreeandroid.feature.home

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetTab
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun HomeScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToRecommendSheets: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToLocal: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToTopList: () -> Unit,
    onNavigateToPlaylistDetail: (String) -> Unit,
    homeSystemActionHandler: HomeSystemActionHandler,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val state = remember { HomeScreenState() }
    var selectedTab by rememberSaveable { mutableStateOf(HomeSheetTab.Mine) }
    val visualUiModel = remember(selectedTab) { buildHomeVisualUiModel(selectedTab) }
    val currentLanguage = remember {
        Locale.getDefault().getDisplayLanguage(Locale.getDefault())
    }
    val currentVersion = remember(context) {
        context.packageManager.versionNameForPackage(context.packageName).orEmpty()
    }
    val scheduleCloseSummary = ""
    val drawerUiModel = remember(currentLanguage, currentVersion, scheduleCloseSummary) {
        buildHomeDrawerUiModel(
            currentLanguage = currentLanguage,
            currentVersion = currentVersion,
            scheduleCloseSummary = scheduleCloseSummary,
        )
    }

    HomeScreenContent(
        state = state,
        visualUiModel = visualUiModel,
        drawerUiModel = drawerUiModel,
        currentLanguage = currentLanguage,
        currentVersion = currentVersion,
        scheduleCloseSummary = scheduleCloseSummary,
        onDrawerEntryClick = { action ->
            when (action) {
                HomeDrawerAction.OpenSettingsRoot,
                HomeDrawerAction.OpenPluginManagement,
                HomeDrawerAction.OpenThemeSettings,
                HomeDrawerAction.OpenBackup,
                HomeDrawerAction.OpenAbout -> onNavigateToSettings()

                HomeDrawerAction.OpenPermissions -> onNavigateToPermissions()
                HomeDrawerAction.BackToDesktop -> homeSystemActionHandler.backToDesktop()
                HomeDrawerAction.ExitApp -> {
                    coroutineScope.launch {
                        homeSystemActionHandler.exitApp()
                    }
                }

                HomeDrawerAction.ShowScheduleClosePanel,
                HomeDrawerAction.ShowLanguageDialog,
                HomeDrawerAction.ShowUpdateCheckDialog -> Unit
            }
        },
        onNavigateToSearch = onNavigateToSearch,
        onNavigateToRecommendSheets = onNavigateToRecommendSheets,
        onNavigateToTopList = onNavigateToTopList,
        onNavigateToHistory = onNavigateToHistory,
        onNavigateToLocal = onNavigateToLocal,
        onSelectTab = { selectedTab = it },
        onCreateClick = {},
        onImportClick = {},
        onOpenMineSheet = onNavigateToPlaylistDetail,
        onOpenStarredSheet = {},
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

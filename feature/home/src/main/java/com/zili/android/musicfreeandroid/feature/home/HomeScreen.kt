package com.zili.android.musicfreeandroid.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.feature.home.playlist.PlaylistViewModel
import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetsViewModel

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
    homeSheetsViewModel: HomeSheetsViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
) {
    val sheetsUiState by homeSheetsViewModel.uiState.collectAsStateWithLifecycle()
    val state = remember { HomeScreenState() }
    val drawerUiModel = remember { buildHomeDrawerUiModel() }

    HomeScreenContent(
        state = state,
        sheetsUiState = sheetsUiState,
        drawerUiModel = drawerUiModel,
        onDrawerEntryClick = { action ->
            when (action) {
                HomeDrawerAction.OpenSettings -> onNavigateToSettings()
                HomeDrawerAction.OpenPluginManagement -> onNavigateToSettings()
                HomeDrawerAction.OpenPermissions -> onNavigateToPermissions()
            }
        },
        onNavigateToSearch = onNavigateToSearch,
        onNavigateToRecommendSheets = onNavigateToRecommendSheets,
        onNavigateToTopList = onNavigateToTopList,
        onNavigateToHistory = onNavigateToHistory,
        onNavigateToLocal = onNavigateToLocal,
        onSelectTab = homeSheetsViewModel::selectTab,
        onCreateSheet = playlistViewModel::createPlaylist,
        onImportSheet = {},
        onOpenMineSheet = onNavigateToPlaylistDetail,
        onOpenStarredSheet = {},
    )
}

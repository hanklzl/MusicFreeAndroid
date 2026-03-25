package com.zili.android.musicfreeandroid.feature.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.feature.home.component.HomeDrawerContent
import com.zili.android.musicfreeandroid.feature.home.component.HomeNavBar
import com.zili.android.musicfreeandroid.feature.home.component.HomeOperations
import com.zili.android.musicfreeandroid.feature.home.playlist.PlaylistViewModel
import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetsSection
import com.zili.android.musicfreeandroid.feature.home.sheets.HomeSheetsViewModel
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerDestinations = remember(onNavigateToSettings, onNavigateToPermissions) {
        buildHomeDrawerDestinations(
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToPermissions = onNavigateToPermissions,
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            HomeDrawerContent(
                destinations = drawerDestinations,
                onDestinationClick = { destination ->
                    scope.launch {
                        runHomeDrawerNavigation(
                            navigate = destination.navigate,
                            closeDrawer = { drawerState.close() },
                        )
                    }
                },
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag(FidelityAnchors.Screen.HomeRoot)
                .semantics { testTagsAsResourceId = true },
            contentPadding = PaddingValues(bottom = rpx(160)),
        ) {
            item {
                HomeNavBar(
                    onOpenMenu = { scope.launch { drawerState.open() } },
                    onOpenSearch = onNavigateToSearch,
                )
            }
            item {
                HomeOperations(
                    onRecommendClick = onNavigateToRecommendSheets,
                    onTopListClick = onNavigateToTopList,
                    onHistoryClick = onNavigateToHistory,
                    onLocalMusicClick = onNavigateToLocal,
                )
            }
            item {
                HomeSheetsSection(
                    uiState = sheetsUiState,
                    onSelectTab = homeSheetsViewModel::selectTab,
                    onCreateSheet = playlistViewModel::createPlaylist,
                    onImportSheet = {},
                    onOpenMineSheet = onNavigateToPlaylistDetail,
                )
            }
        }
    }
}

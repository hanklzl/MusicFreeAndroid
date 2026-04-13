package com.zili.android.musicfreeandroid.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.zili.android.musicfreeandroid.core.navigation.AlbumDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.ArtistDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.FileSelectorRoute
import com.zili.android.musicfreeandroid.core.navigation.HistoryRoute
import com.zili.android.musicfreeandroid.core.navigation.HomeRoute
import com.zili.android.musicfreeandroid.core.navigation.LocalRoute
import com.zili.android.musicfreeandroid.core.navigation.MusicDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.MusicListEditorLiteRoute
import com.zili.android.musicfreeandroid.core.navigation.PermissionsRoute
import com.zili.android.musicfreeandroid.core.navigation.PlaylistDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.PlayerRoute
import com.zili.android.musicfreeandroid.core.navigation.PluginSheetDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.RecommendSheetsRoute
import com.zili.android.musicfreeandroid.core.navigation.SearchRoute
import com.zili.android.musicfreeandroid.core.navigation.SearchMusicListRoute
import com.zili.android.musicfreeandroid.core.navigation.SettingsRoute
import com.zili.android.musicfreeandroid.core.navigation.TopListDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.TopListRoute
import com.zili.android.musicfreeandroid.feature.home.navigation.homeScreen
import com.zili.android.musicfreeandroid.feature.home.HomeSystemActionHandler
import com.zili.android.musicfreeandroid.feature.home.albumdetail.navigation.albumDetailScreen
import com.zili.android.musicfreeandroid.feature.home.artistdetail.navigation.artistDetailScreen
import com.zili.android.musicfreeandroid.feature.home.musicdetail.navigation.musicDetailScreen
import com.zili.android.musicfreeandroid.feature.home.musicdetail.navigation.MusicDetailSeedStore
import com.zili.android.musicfreeandroid.feature.home.musiclisteditor.navigation.musicListEditorLiteScreen
import com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation.pluginSheetDetailScreen
import com.zili.android.musicfreeandroid.feature.home.playlist.playlistDetailScreen
import com.zili.android.musicfreeandroid.feature.home.recommendsheets.navigation.recommendSheetsScreen
import com.zili.android.musicfreeandroid.feature.home.searchmusiclist.navigation.searchMusicListScreen
import com.zili.android.musicfreeandroid.feature.home.history.navigation.historyScreen
import com.zili.android.musicfreeandroid.feature.home.local.navigation.localScreen
import com.zili.android.musicfreeandroid.feature.home.toplist.navigation.topListDetailScreen
import com.zili.android.musicfreeandroid.feature.home.toplist.navigation.topListScreen
import com.zili.android.musicfreeandroid.feature.playerui.navigation.playerScreen
import com.zili.android.musicfreeandroid.feature.search.navigation.searchScreen
import com.zili.android.musicfreeandroid.feature.settings.fileselector.navigation.fileSelectorLiteScreen
import com.zili.android.musicfreeandroid.feature.settings.navigation.permissionsScreen
import com.zili.android.musicfreeandroid.feature.settings.navigation.settingsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    homeSystemActionHandler: HomeSystemActionHandler,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(100),
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(100),
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(100),
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(100),
            )
        },
    ) {
        homeScreen(
            onNavigateToSearch = { navController.navigate(SearchRoute) },
            onNavigateToRecommendSheets = { navController.navigate(RecommendSheetsRoute) },
            onNavigateToHistory = { navController.navigate(HistoryRoute) },
            onNavigateToLocal = { navController.navigate(LocalRoute) },
            onNavigateToSettings = { navController.navigate(SettingsRoute) },
            onNavigateToPermissions = { navController.navigate(PermissionsRoute) },
            onNavigateToTopList = { navController.navigate(TopListRoute) },
            onNavigateToPlaylistDetail = { playlistId ->
                navController.navigate(PlaylistDetailRoute(playlistId))
            },
            homeSystemActionHandler = homeSystemActionHandler,
        )
        localScreen(
            onNavigateToPlayer = { navController.navigate(PlayerRoute) },
        )
        playerScreen(
            onBack = { navController.popBackStack() },
        )
        playlistDetailScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPlayer = { navController.navigate(PlayerRoute) },
            onNavigateToSearchMusicList = { playlistId ->
                navController.navigate(SearchMusicListRoute.playlist(playlistId))
            },
            onNavigateToMusicListEditorLite = { playlistId ->
                navController.navigate(MusicListEditorLiteRoute(playlistId))
            },
        )
        musicListEditorLiteScreen(
            onBack = { navController.popBackStack() },
        )
        searchScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPlayer = { navController.navigate(PlayerRoute) },
        )
        historyScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPlayer = { navController.navigate(PlayerRoute) },
            onNavigateToSearchMusicList = {
                navController.navigate(SearchMusicListRoute.history())
            },
        )
        searchMusicListScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPlayer = { navController.navigate(PlayerRoute) },
        )
        settingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPermissions = { navController.navigate(PermissionsRoute) },
            onNavigateToFileSelector = { navController.navigate(FileSelectorRoute) },
        )
        fileSelectorLiteScreen(
            onBack = { navController.popBackStack() },
        )
        permissionsScreen(
            onBack = { navController.popBackStack() },
        )
        topListScreen(
            onBack = { navController.popBackStack() },
            onOpenTopListDetail = { pluginPlatform, topListId ->
                navController.navigate(
                    TopListDetailRoute(
                        pluginPlatform = pluginPlatform,
                        topListId = topListId,
                    ),
                )
            },
        )
        topListDetailScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPlayer = { navController.navigate(PlayerRoute) },
            onOpenMusicDetail = { item ->
                val seedToken = MusicDetailSeedStore.put(item)
                navController.navigate(
                    MusicDetailRoute(
                        pluginPlatform = item.platform,
                        musicId = item.id,
                        title = item.title,
                        artist = item.artist,
                        album = item.album,
                        artwork = item.artwork,
                        durationMs = item.duration,
                        seedToken = seedToken,
                    ),
                )
            },
        )
        recommendSheetsScreen(
            onBack = { navController.popBackStack() },
            onOpenSheetDetail = { pluginPlatform, sheet ->
                navController.navigate(
                    PluginSheetDetailRoute(
                        pluginPlatform = pluginPlatform,
                        sheetId = sheet.id,
                        title = sheet.title,
                        artist = sheet.artist,
                        coverImg = sheet.coverImg,
                        artwork = sheet.artwork,
                    ),
                )
            },
        )
        pluginSheetDetailScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPlayer = { navController.navigate(PlayerRoute) },
            onOpenMusicDetail = { item ->
                val seedToken = MusicDetailSeedStore.put(item)
                navController.navigate(
                    MusicDetailRoute(
                        pluginPlatform = item.platform,
                        musicId = item.id,
                        title = item.title,
                        artist = item.artist,
                        album = item.album,
                        artwork = item.artwork,
                        durationMs = item.duration,
                        seedToken = seedToken,
                    ),
                )
            },
        )
        musicDetailScreen(
            onBack = { navController.popBackStack() },
            onOpenAlbumDetail = { item ->
                val albumId = item.album?.takeIf { it.isNotBlank() } ?: item.id
                navController.navigate(
                    AlbumDetailRoute(
                        pluginPlatform = item.platform,
                        albumId = albumId,
                        title = item.album,
                        artist = item.artist,
                        artwork = item.artwork,
                    ),
                )
            },
            onOpenArtistDetail = { item ->
                val artistId = item.artist.takeIf { it.isNotBlank() } ?: item.id
                navController.navigate(
                    ArtistDetailRoute(
                        pluginPlatform = item.platform,
                        artistId = artistId,
                        name = item.artist.ifBlank { "未知歌手" },
                        avatar = item.artwork,
                    ),
                )
            },
        )
        albumDetailScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPlayer = { navController.navigate(PlayerRoute) },
        )
        artistDetailScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPlayer = { navController.navigate(PlayerRoute) },
        )
    }
}

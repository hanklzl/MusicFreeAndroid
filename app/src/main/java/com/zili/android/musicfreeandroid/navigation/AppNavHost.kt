package com.zili.android.musicfreeandroid.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.zili.android.musicfreeandroid.core.navigation.AlbumDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.ArtistDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.DownloadingRoute
import com.zili.android.musicfreeandroid.core.navigation.FileSelectorRoute
import com.zili.android.musicfreeandroid.core.navigation.HistoryRoute
import com.zili.android.musicfreeandroid.core.navigation.HomeRoute
import com.zili.android.musicfreeandroid.core.navigation.LocalRoute
import com.zili.android.musicfreeandroid.core.navigation.MusicDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.MusicListEditorLiteRoute
import com.zili.android.musicfreeandroid.core.navigation.PermissionsRoute
import com.zili.android.musicfreeandroid.core.navigation.PlaylistDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.PluginListRoute
import com.zili.android.musicfreeandroid.core.navigation.PluginSheetDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.PluginSortRoute
import com.zili.android.musicfreeandroid.core.navigation.PluginSubscriptionRoute
import com.zili.android.musicfreeandroid.core.navigation.RecommendSheetsRoute
import com.zili.android.musicfreeandroid.core.navigation.SearchRoute
import com.zili.android.musicfreeandroid.core.navigation.SearchMusicListRoute
import com.zili.android.musicfreeandroid.core.navigation.SettingsRoute
import com.zili.android.musicfreeandroid.core.navigation.SettingsType
import com.zili.android.musicfreeandroid.core.navigation.TopListDetailRoute
import com.zili.android.musicfreeandroid.core.navigation.TopListRoute
import com.zili.android.musicfreeandroid.feature.home.navigation.homeScreen
import com.zili.android.musicfreeandroid.feature.home.HomeSystemActionHandler
import com.zili.android.musicfreeandroid.feature.home.albumdetail.navigation.AlbumDetailSeedStore
import com.zili.android.musicfreeandroid.feature.home.albumdetail.navigation.albumDetailScreen
import com.zili.android.musicfreeandroid.feature.home.artistdetail.navigation.ArtistDetailSeedStore
import com.zili.android.musicfreeandroid.feature.home.artistdetail.navigation.artistDetailScreen
import com.zili.android.musicfreeandroid.feature.home.musicdetail.navigation.musicDetailScreen
import com.zili.android.musicfreeandroid.feature.home.musicdetail.navigation.MusicDetailSeedStore
import com.zili.android.musicfreeandroid.feature.home.musiclisteditor.navigation.musicListEditorLiteScreen
import com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation.PluginSheetSeedStore
import com.zili.android.musicfreeandroid.feature.home.pluginsheet.navigation.pluginSheetDetailScreen
import com.zili.android.musicfreeandroid.feature.home.playlist.playlistDetailScreen
import com.zili.android.musicfreeandroid.feature.home.recommendsheets.navigation.recommendSheetsScreen
import com.zili.android.musicfreeandroid.feature.home.searchmusiclist.navigation.searchMusicListScreen
import com.zili.android.musicfreeandroid.feature.home.sheets.toAlbumItemBase
import com.zili.android.musicfreeandroid.feature.home.sheets.toMusicSheetItemBase
import com.zili.android.musicfreeandroid.feature.home.history.navigation.historyScreen
import com.zili.android.musicfreeandroid.feature.home.downloading.navigation.downloadingScreen
import com.zili.android.musicfreeandroid.feature.home.local.navigation.localScreen
import com.zili.android.musicfreeandroid.feature.home.toplist.navigation.topListDetailScreen
import com.zili.android.musicfreeandroid.feature.home.toplist.navigation.topListScreen
import com.zili.android.musicfreeandroid.feature.playerui.navigation.playerScreen
import com.zili.android.musicfreeandroid.feature.search.navigation.searchScreen
import com.zili.android.musicfreeandroid.feature.settings.fileselector.navigation.fileSelectorLiteScreen
import com.zili.android.musicfreeandroid.feature.settings.navigation.permissionsScreen
import com.zili.android.musicfreeandroid.feature.settings.navigation.settingsScreen
import com.zili.android.musicfreeandroid.feature.settings.pluginlist.navigation.pluginListScreen
import com.zili.android.musicfreeandroid.feature.settings.pluginsort.navigation.pluginSortScreen
import com.zili.android.musicfreeandroid.feature.settings.pluginsub.navigation.pluginSubscriptionScreen

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
        enterTransition = { musicFreeEnterTransition() },
        exitTransition = { musicFreeExitTransition() },
        popEnterTransition = { musicFreePopEnterTransition() },
        popExitTransition = { musicFreePopExitTransition() },
    ) {
        homeScreen(
            onNavigateToSearch = { navController.navigate(SearchRoute) },
            onNavigateToRecommendSheets = { navController.navigate(RecommendSheetsRoute) },
            onNavigateToHistory = { navController.navigate(HistoryRoute) },
            onNavigateToLocal = { navController.navigate(LocalRoute) },
            onNavigateToSettings = { type: SettingsType -> navController.navigate(SettingsRoute(type)) },
            onNavigateToPluginList = { navController.navigate(PluginListRoute) },
            onNavigateToPermissions = { navController.navigate(PermissionsRoute) },
            onNavigateToTopList = { navController.navigate(TopListRoute) },
            onNavigateToPlaylistDetail = { playlistId ->
                navController.navigate(PlaylistDetailRoute(playlistId))
            },
            onNavigateToStarredSheet = { row ->
                val sheet = row.toMusicSheetItemBase()
                val seedToken = PluginSheetSeedStore.put(sheet)
                navController.navigate(
                    PluginSheetDetailRoute(
                        pluginPlatform = sheet.platform,
                        sheetId = sheet.id,
                        title = sheet.title,
                        artist = sheet.artist,
                        description = sheet.description,
                        coverImg = sheet.coverImg,
                        artwork = sheet.artwork,
                        worksNum = sheet.worksNum,
                        seedToken = seedToken,
                    ),
                )
            },
            onNavigateToStarredAlbum = { row ->
                val album = row.toAlbumItemBase()
                val seedToken = AlbumDetailSeedStore.put(album)
                navController.navigate(
                    AlbumDetailRoute(
                        pluginPlatform = album.platform,
                        albumId = album.id,
                        title = album.title,
                        artist = album.artist,
                        artwork = album.artwork,
                        date = album.date,
                        description = album.description,
                        worksNum = album.worksNum,
                        seedToken = seedToken,
                    ),
                )
            },
            homeSystemActionHandler = homeSystemActionHandler,
        )
        localScreen(
            onBack = { navController.popBackStack() },
            onNavigateToSearchMusicList = {
                navController.navigate(SearchMusicListRoute.localLibrary())
            },
            onNavigateToMusicListEditor = {
                navController.navigate(MusicListEditorLiteRoute.localLibrary())
            },
            onNavigateToDownloading = { navController.navigate(DownloadingRoute) },
        )
        downloadingScreen(
            onBack = { navController.popBackStack() },
        )
        playerScreen(
            onBack = { navController.popBackStack() },
        )
        playlistDetailScreen(
            onBack = { navController.popBackStack() },
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
            onOpenAlbumDetail = { album ->
                val seedToken = AlbumDetailSeedStore.put(album)
                navController.navigate(
                    AlbumDetailRoute(
                        pluginPlatform = album.platform,
                        albumId = album.id,
                        title = album.title,
                        artist = album.artist,
                        artwork = album.artwork,
                        date = album.date,
                        description = album.description,
                        worksNum = album.worksNum,
                        seedToken = seedToken,
                    ),
                )
            },
            onOpenArtistDetail = { artist ->
                val seedToken = ArtistDetailSeedStore.put(artist)
                navController.navigate(
                    ArtistDetailRoute(
                        pluginPlatform = artist.platform,
                        artistId = artist.id,
                        name = artist.name.orEmpty().ifBlank { "未知歌手" },
                        avatar = artist.avatar,
                        description = artist.description,
                        fans = artist.fans,
                        worksNum = artist.worksNum,
                        seedToken = seedToken,
                    ),
                )
            },
            onOpenSheetDetail = { sheet ->
                val seedToken = PluginSheetSeedStore.put(sheet)
                navController.navigate(
                    PluginSheetDetailRoute(
                        pluginPlatform = sheet.platform,
                        sheetId = sheet.id,
                        title = sheet.title,
                        artist = sheet.artist,
                        description = sheet.description,
                        coverImg = sheet.coverImg,
                        artwork = sheet.artwork,
                        worksNum = sheet.worksNum,
                        seedToken = seedToken,
                    ),
                )
            },
        )
        historyScreen(
            onBack = { navController.popBackStack() },
            onNavigateToSearchMusicList = {
                navController.navigate(SearchMusicListRoute.history())
            },
        )
        searchMusicListScreen(
            onBack = { navController.popBackStack() },
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
        pluginListScreen(
            onBack = { navController.popBackStack() },
            onNavigateToPluginSort = { navController.navigate(PluginSortRoute) },
            onNavigateToPluginSubscription = { navController.navigate(PluginSubscriptionRoute) },
            onNavigateToFileSelector = { navController.navigate(FileSelectorRoute) },
        )
        pluginSortScreen(
            onBack = { navController.popBackStack() },
        )
        pluginSubscriptionScreen(
            onBack = { navController.popBackStack() },
        )
        topListScreen(
            onBack = { navController.popBackStack() },
            onOpenTopListDetail = { pluginPlatform, topList ->
                val seedToken = PluginSheetSeedStore.put(topList)
                navController.navigate(
                    TopListDetailRoute(
                        pluginPlatform = pluginPlatform,
                        topListId = topList.id,
                        title = topList.title,
                        artist = topList.artist,
                        description = topList.description,
                        coverImg = topList.coverImg,
                        artwork = topList.artwork,
                        worksNum = topList.worksNum,
                        seedToken = seedToken,
                    ),
                )
            },
        )
        topListDetailScreen(
            onBack = { navController.popBackStack() },
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
                val seedToken = PluginSheetSeedStore.put(sheet)
                navController.navigate(
                    PluginSheetDetailRoute(
                        pluginPlatform = pluginPlatform,
                        sheetId = sheet.id,
                        title = sheet.title,
                        artist = sheet.artist,
                        description = sheet.description,
                        coverImg = sheet.coverImg,
                        artwork = sheet.artwork,
                        worksNum = sheet.worksNum,
                        seedToken = seedToken,
                    ),
                )
            },
        )
        pluginSheetDetailScreen(
            onBack = { navController.popBackStack() },
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
        )
        artistDetailScreen(
            onBack = { navController.popBackStack() },
        )
    }
}

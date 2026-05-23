package com.hank.musicfree.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.hank.musicfree.core.feedback.FeedbackIssueLinks
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.UiLogEvents
import com.hank.musicfree.core.navigation.AlbumDetailRoute
import com.hank.musicfree.core.navigation.ArtistDetailRoute
import com.hank.musicfree.core.navigation.DownloadingRoute
import com.hank.musicfree.core.navigation.FileSelectorRoute
import com.hank.musicfree.core.navigation.HistoryRoute
import com.hank.musicfree.core.navigation.HomeRoute
import com.hank.musicfree.core.navigation.ListenStatsRoute
import com.hank.musicfree.core.navigation.LocalRoute
import com.hank.musicfree.core.navigation.MusicDetailRoute
import com.hank.musicfree.core.navigation.MusicListEditorLiteRoute
import com.hank.musicfree.core.navigation.PermissionsRoute
import com.hank.musicfree.core.navigation.PlaylistDetailRoute
import com.hank.musicfree.core.navigation.PluginListRoute
import com.hank.musicfree.core.navigation.PluginSheetDetailRoute
import com.hank.musicfree.core.navigation.PluginSortRoute
import com.hank.musicfree.core.navigation.PluginSubscriptionRoute
import com.hank.musicfree.core.navigation.RecommendSheetsRoute
import com.hank.musicfree.core.navigation.SearchRoute
import com.hank.musicfree.core.navigation.SearchMusicListRoute
import com.hank.musicfree.core.navigation.SetCustomThemeRoute
import com.hank.musicfree.core.navigation.SettingsRoute
import com.hank.musicfree.core.navigation.SettingsType
import com.hank.musicfree.core.navigation.TopListDetailRoute
import com.hank.musicfree.core.navigation.TopListRoute
import com.hank.musicfree.core.navigation.TrafficStatsRoute
import com.hank.musicfree.feature.home.navigation.homeScreen
import com.hank.musicfree.feature.home.albumdetail.navigation.AlbumDetailSeedStore
import com.hank.musicfree.feature.home.albumdetail.navigation.albumDetailScreen
import com.hank.musicfree.feature.home.artistdetail.navigation.ArtistDetailSeedStore
import com.hank.musicfree.feature.home.artistdetail.navigation.artistDetailScreen
import com.hank.musicfree.feature.home.musicdetail.navigation.musicDetailScreen
import com.hank.musicfree.feature.home.musiclisteditor.navigation.musicListEditorLiteScreen
import com.hank.musicfree.feature.home.pluginsheet.navigation.PluginSheetSeedStore
import com.hank.musicfree.feature.home.pluginsheet.navigation.pluginSheetDetailScreen
import com.hank.musicfree.feature.home.playlist.playlistDetailScreen
import com.hank.musicfree.feature.home.recommendsheets.navigation.recommendSheetsScreen
import com.hank.musicfree.feature.home.searchmusiclist.navigation.searchMusicListScreen
import com.hank.musicfree.feature.home.sheets.toAlbumItemBase
import com.hank.musicfree.feature.home.sheets.toMusicSheetItemBase
import com.hank.musicfree.feature.home.history.navigation.historyScreen
import com.hank.musicfree.feature.home.downloading.navigation.downloadingScreen
import com.hank.musicfree.feature.home.local.navigation.localScreen
import com.hank.musicfree.feature.home.toplist.navigation.topListDetailScreen
import com.hank.musicfree.feature.home.toplist.navigation.topListScreen
import com.hank.musicfree.feature.playerui.navigation.playerScreen
import com.hank.musicfree.feature.search.navigation.searchScreen
import com.hank.musicfree.feature.settings.fileselector.navigation.fileSelectorLiteScreen
import com.hank.musicfree.feature.settings.navigation.permissionsScreen
import com.hank.musicfree.feature.settings.navigation.settingsScreen
import com.hank.musicfree.feature.settings.pluginlist.navigation.pluginListScreen
import com.hank.musicfree.feature.settings.pluginsort.navigation.pluginSortScreen
import com.hank.musicfree.feature.listenstats.navigation.listenStatsScreen
import com.hank.musicfree.feature.settings.pluginsub.navigation.pluginSubscriptionScreen
import com.hank.musicfree.feature.settings.setcustomtheme.navigation.setCustomThemeScreen
import com.hank.musicfree.feature.settings.traffic.navigation.trafficStatsRoute

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    NavigationLogger(navController)
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
            onNavigateToListenStats = { navController.navigate(ListenStatsRoute()) },
            onNavigateToTrafficStats = { navController.navigate(TrafficStatsRoute()) },
            onNavigateToLocal = { navController.navigate(LocalRoute) },
            onNavigateToSettings = { type: SettingsType -> navController.navigate(SettingsRoute(type)) },
            onNavigateToPluginList = { navController.navigate(PluginListRoute) },
            onNavigateToPermissions = { navController.navigate(PermissionsRoute) },
            onOpenFeedback = { openFeedbackIssueH5(context) },
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
            onNavigateToSetCustomTheme = { navController.navigate(SetCustomThemeRoute) },
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
        listenStatsScreen(navController)
        setCustomThemeScreen(
            onBack = { navController.popBackStack() },
        )
        trafficStatsRoute(onBack = { navController.popBackStack() })
    }
}

private fun openFeedbackIssueH5(context: Context) {
    val url = FeedbackIssueLinks.newIssueUrl()
    MfLog.detail(
        category = LogCategory.FEEDBACK,
        event = "feedback_issue_h5_open",
        fields = mapOf("url" to url),
    )
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (error: ActivityNotFoundException) {
        MfLog.error(LogCategory.FEEDBACK, "feedback_issue_h5_open_failed", error)
        Toast.makeText(context, "无法打开 GitHub 反馈页面", Toast.LENGTH_SHORT).show()
    } catch (error: RuntimeException) {
        MfLog.error(LogCategory.FEEDBACK, "feedback_issue_h5_open_failed", error)
        Toast.makeText(context, "打开 GitHub 反馈页面失败", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 把 NavController 的目的地变更映射为 [UiLogEvents.SCREEN_ENTER] / [UiLogEvents.SCREEN_EXIT]。
 * 在每次目的地切换时，先给上一个 route 发 screen_exit（带停留时长），再给新 route 发 screen_enter。
 * 这是兜底机制，覆盖所有路由切换；具体 Screen 想附带 params 时也可以直接调用 MfLog。
 */
@Composable
private fun NavigationLogger(navController: NavHostController) {
    DisposableEffect(navController) {
        var previousRoute: String? = null
        var enteredAtMs: Long = 0L
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val now = System.currentTimeMillis()
            val nextRoute = destination.route?.substringBefore('/')?.substringAfterLast('.') ?: "unknown"
            val prev = previousRoute
            if (prev != null) {
                MfLog.detail(
                    LogCategory.NAVIGATION,
                    UiLogEvents.SCREEN_EXIT,
                    mapOf(
                        UiLogEvents.Fields.ROUTE to prev,
                        UiLogEvents.Fields.DURATION_MS to (now - enteredAtMs).coerceAtLeast(0L),
                    ),
                )
            }
            MfLog.detail(
                LogCategory.NAVIGATION,
                UiLogEvents.SCREEN_ENTER,
                mapOf(
                    UiLogEvents.Fields.ROUTE to nextRoute,
                    UiLogEvents.Fields.SOURCE to UiLogEvents.Trigger.NAV_LISTENER,
                ),
            )
            previousRoute = nextRoute
            enteredAtMs = now
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            val prev = previousRoute
            if (prev != null) {
                val now = System.currentTimeMillis()
                MfLog.detail(
                    LogCategory.NAVIGATION,
                    UiLogEvents.SCREEN_EXIT,
                    mapOf(
                        UiLogEvents.Fields.ROUTE to prev,
                        UiLogEvents.Fields.DURATION_MS to (now - enteredAtMs).coerceAtLeast(0L),
                    ),
                )
            }
            navController.removeOnDestinationChangedListener(listener)
        }
    }
}

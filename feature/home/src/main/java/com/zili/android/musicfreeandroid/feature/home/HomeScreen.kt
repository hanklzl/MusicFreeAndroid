package com.zili.android.musicfreeandroid.feature.home

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.permissions.requiredAudioPermission
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.IconSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.feature.home.playlist.AddToPlaylistDialog
import com.zili.android.musicfreeandroid.feature.home.playlist.PlaylistSection
import com.zili.android.musicfreeandroid.feature.home.playlist.PlaylistViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onNavigateToPlayer: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToRecommendSheets: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToTopList: () -> Unit,
    onNavigateToPlaylistDetail: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playlists by playlistViewModel.playlists.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var addToPlaylistItem by remember { mutableStateOf<MusicItem?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.scanLocalMusic()
    }

    LaunchedEffect(Unit) {
        val permission = requiredAudioPermission()
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            viewModel.scanLocalMusic()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    addToPlaylistItem?.let { item ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { addToPlaylistItem = null },
            onSelect = { playlist ->
                viewModel.addToPlaylist(playlist.id, item)
            },
        )
    }

    val tabs = listOf("本地音乐", "播放列表")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val drawerState = androidx.compose.material3.rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerItems = remember(onNavigateToSettings, onNavigateToPermissions) {
        listOf(
            HomeDrawerItem(
                title = "基础设置",
                icon = Icons.Default.Settings,
                onClick = onNavigateToSettings,
            ),
            HomeDrawerItem(
                title = "插件管理",
                icon = Icons.Default.Extension,
                onClick = onNavigateToSettings,
            ),
            HomeDrawerItem(
                title = "权限管理",
                icon = Icons.Default.Security,
                onClick = onNavigateToPermissions,
            ),
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MusicFreeTheme.colors.card,
                drawerContentColor = MusicFreeTheme.colors.text,
            ) {
                Text(
                    text = "更多功能",
                    modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(32)),
                    color = MusicFreeTheme.colors.text,
                    fontSize = FontSizes.title,
                )
                drawerItems.forEach { item ->
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = item.title,
                                fontSize = FontSizes.subTitle,
                            )
                        },
                        selected = false,
                        onClick = {
                            scope.launch {
                                runHomeDrawerNavigation(
                                    navigate = item.onClick,
                                    closeDrawer = { drawerState.close() },
                                )
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                            )
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = MusicFreeTheme.colors.card,
                            unselectedTextColor = MusicFreeTheme.colors.text,
                            unselectedIconColor = MusicFreeTheme.colors.text,
                        ),
                        modifier = Modifier.padding(horizontal = rpx(12)),
                    )
                }
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HomeHeader(
                onOpenMenu = { scope.launch { drawerState.open() } },
                onOpenSearch = onNavigateToSearch,
            )

            HomeOperations(
                onRecommendClick = onNavigateToRecommendSheets,
                onTopListClick = onNavigateToTopList,
                onHistoryClick = onNavigateToHistory,
                onLocalMusicClick = { scope.launch { pagerState.animateScrollToPage(0) } },
            )

            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MusicFreeTheme.colors.pageBackground,
                contentColor = MusicFreeTheme.colors.text,
                indicator = { tabPositions ->
                    SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                        color = MusicFreeTheme.colors.primary,
                    )
                },
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                title,
                                color = if (pagerState.currentPage == index) MusicFreeTheme.colors.primary else MusicFreeTheme.colors.textSecondary,
                            )
                        },
                    )
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> LocalMusicPage(
                        uiState = uiState,
                        onItemClick = { item, items ->
                            viewModel.playItem(item, items)
                            onNavigateToPlayer()
                        },
                        onItemLongClick = { item -> addToPlaylistItem = item },
                        onRetry = { viewModel.scanLocalMusic() },
                    )

                    1 -> PlaylistSection(
                        playlists = playlists,
                        onPlaylistClick = { onNavigateToPlaylistDetail(it.id) },
                        onCreate = { playlistViewModel.createPlaylist(it) },
                        onRename = { playlist, name -> playlistViewModel.renamePlaylist(playlist, name) },
                        onDelete = { playlistViewModel.deletePlaylist(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rpx(88))
            .padding(start = rpx(24), end = rpx(24)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onOpenMenu,
            modifier = Modifier.size(rpx(40)),
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "菜单",
                tint = MusicFreeTheme.colors.text,
                modifier = Modifier.size(IconSizes.normal),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = rpx(24))
                .height(rpx(64))
                .background(
                    color = MusicFreeTheme.colors.placeholder,
                    shape = RoundedCornerShape(999.dp),
                )
                .clickable(onClick = onOpenSearch)
                .padding(horizontal = rpx(20)),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MusicFreeTheme.colors.textSecondary,
                    modifier = Modifier.size(IconSizes.small),
                )
                Text(
                    text = "点击这里开始搜索",
                    modifier = Modifier.padding(start = rpx(12)),
                    color = MusicFreeTheme.colors.textSecondary,
                    fontSize = FontSizes.subTitle,
                )
            }
        }
    }
}

@Composable
private fun HomeOperations(
    onRecommendClick: () -> Unit,
    onTopListClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLocalMusicClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rpx(24), vertical = rpx(32)),
        horizontalArrangement = Arrangement.spacedBy(rpx(24)),
    ) {
        OperationCard(
            modifier = Modifier.weight(1f),
            title = "推荐歌单",
            icon = Icons.Default.Whatshot,
            onClick = onRecommendClick,
        )
        OperationCard(
            modifier = Modifier.weight(1f),
            title = "榜单",
            icon = Icons.Default.EmojiEvents,
            onClick = onTopListClick,
        )
        OperationCard(
            modifier = Modifier.weight(1f),
            title = "播放历史",
            icon = Icons.Default.History,
            onClick = onHistoryClick,
        )
        OperationCard(
            modifier = Modifier.weight(1f),
            title = "本地音乐",
            icon = Icons.Default.LibraryMusic,
            onClick = onLocalMusicClick,
        )
    }
}

@Composable
private fun OperationCard(
    modifier: Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(rpx(160))
            .background(
                color = MusicFreeTheme.colors.card,
                shape = RoundedCornerShape(rpx(18)),
            )
            .clickable(onClick = onClick)
            .padding(vertical = rpx(18)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MusicFreeTheme.colors.text,
            modifier = Modifier.size(IconSizes.normal),
        )
        Text(
            text = title,
            color = MusicFreeTheme.colors.text,
            fontSize = FontSizes.description,
            modifier = Modifier.padding(top = rpx(12)),
        )
    }
}

private data class HomeDrawerItem(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun LocalMusicPage(
    uiState: HomeUiState,
    onItemClick: (MusicItem, List<MusicItem>) -> Unit,
    onItemLongClick: (MusicItem) -> Unit,
    onRetry: () -> Unit,
) {
    when (uiState) {
        is HomeUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MusicFreeTheme.colors.primary)
            }
        }

        is HomeUiState.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(uiState.message, color = MusicFreeTheme.colors.danger)
                    Spacer(Modifier.height(8.dp))
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = "重试", tint = MusicFreeTheme.colors.primary)
                    }
                }
            }
        }

        is HomeUiState.Success -> {
            if (uiState.musicItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有找到本地音乐", color = MusicFreeTheme.colors.textSecondary)
                }
            } else {
                MusicList(
                    items = uiState.musicItems,
                    onItemClick = { item -> onItemClick(item, uiState.musicItems) },
                    onItemLongClick = onItemLongClick,
                )
            }
        }
    }
}

@Composable
private fun MusicList(
    items: List<MusicItem>,
    onItemClick: (MusicItem) -> Unit,
    onItemLongClick: (MusicItem) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "${items.size} 首本地音乐",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = MusicFreeTheme.colors.textSecondary,
                fontSize = FontSizes.description,
            )
        }
        itemsIndexed(items, key = { _, item -> "${item.platform}:${item.id}" }) { index, item ->
            MusicListItem(
                item = item,
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) },
            )
            if (index < items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = MusicFreeTheme.colors.divider,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MusicListItem(
    item: MusicItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.zili.android.musicfreeandroid.core.ui.CoverImage(
            uri = item.artwork,
            size = 48.dp,
            cornerRadius = 4.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = item.title,
                color = MusicFreeTheme.colors.text,
                fontSize = FontSizes.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.artist.isNotBlank()) {
                Text(
                    text = item.artist + if (!item.album.isNullOrBlank()) " - ${item.album}" else "",
                    color = MusicFreeTheme.colors.textSecondary,
                    fontSize = FontSizes.description,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

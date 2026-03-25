package com.zili.android.musicfreeandroid.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.IconSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.feature.home.playlist.PlaylistSection
import com.zili.android.musicfreeandroid.feature.home.playlist.PlaylistViewModel
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
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
) {
    val playlists by playlistViewModel.playlists.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
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
                onLocalMusicClick = onNavigateToLocal,
            )

            PlaylistSection(
                playlists = playlists,
                onPlaylistClick = { onNavigateToPlaylistDetail(it.id) },
                onCreate = { playlistViewModel.createPlaylist(it) },
                onRename = { playlist, name -> playlistViewModel.renamePlaylist(playlist, name) },
                onDelete = { playlistViewModel.deletePlaylist(it) },
            )
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

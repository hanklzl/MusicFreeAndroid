package com.zili.android.musicfreeandroid.feature.search

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.R
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.IconSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.CoverImage
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.core.ui.MusicFreeStatusBarChrome

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val colors = MusicFreeTheme.colors
    val searchablePlugins by viewModel.searchablePlugins.collectAsStateWithLifecycle()
    val selectedPlatform by viewModel.selectedPlatform.collectAsStateWithLifecycle()
    val selectedMediaType by viewModel.selectedMediaType.collectAsStateWithLifecycle()
    val pageStatus by viewModel.pageStatus.collectAsStateWithLifecycle()
    val currentPluginState by viewModel.currentPluginState.collectAsStateWithLifecycle()
    val history by viewModel.searchHistory.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var query by remember { mutableStateOf(viewModel.currentQuery.value) }
    // 如果 ViewModel 已有查询（如从历史恢复），同步初始化本地状态
    LaunchedEffect(Unit) {
        val initialQuery = viewModel.currentQuery.value
        if (initialQuery.isNotBlank() && query.isBlank()) {
            query = initialQuery
        }
    }

    // Observe play events from ViewModel (runs in ViewModel scope, survives navigation)
    LaunchedEffect(Unit) {
        viewModel.playEvent.onEach { event ->
            when (event) {
                is SearchViewModel.PlayEvent.NavigateToPlayer -> onNavigateToPlayer()
                is SearchViewModel.PlayEvent.Failed ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }.launchIn(this)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .testTag(FidelityAnchors.Screen.SearchRoot)
            .semantics { testTagsAsResourceId = true },
    ) {
        // ── SearchNavBar（沉浸式：橙色延伸到状态栏后方）────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.appBar),
        ) {
            MusicFreeStatusBarChrome(color = colors.appBar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rpx(88))
                    .padding(horizontal = rpx(24)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        if (pageStatus == SearchPageStatus.EDITING) onBack()
                        else viewModel.backToEditing()
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_left),
                        contentDescription = "返回",
                        tint = colors.appBarText,
                        modifier = Modifier.size(IconSizes.normal),
                    )
                }

                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(rpx(64))
                        .background(colors.pageBackground, RoundedCornerShape(rpx(64))),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (query.isNotBlank()) viewModel.searchAll(query.trim())
                        },
                    ),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = rpx(24)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.weight(1f)) { innerTextField() }
                            if (query.isNotBlank()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_x_mark),
                                        contentDescription = "清空",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(rpx(36)),
                                    )
                                }
                            }
                        }
                    },
                )

                TextButton(
                    onClick = {
                        if (query.isNotBlank()) viewModel.searchAll(query.trim())
                    },
                ) {
                    Text(
                        text = "搜索",
                        color = colors.appBarText,
                        fontSize = FontSizes.content,
                    )
                }
            } // Row
        } // Column (沉浸式 NavBar)

        // ── 内容区域 ──────────────────────────────────────────────────
        when (pageStatus) {
            SearchPageStatus.EDITING -> {
                SearchHistoryPanel(
                    history = history,
                    onItemClick = { item ->
                        query = item
                        viewModel.searchAll(item)
                    },
                    onClearHistory = viewModel::clearHistory,
                )
            }

            SearchPageStatus.NO_PLUGIN -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "请先在设置中安装插件",
                        color = colors.textSecondary,
                        fontSize = FontSizes.content,
                    )
                }
            }

            SearchPageStatus.SEARCHING -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.primary)
                }
            }

            SearchPageStatus.RESULT -> {
                SearchResultPanel(
                    searchablePlugins = searchablePlugins,
                    selectedMediaType = selectedMediaType,
                    selectedPlatform = selectedPlatform,
                    currentPluginState = currentPluginState,
                    onSelectMediaType = { viewModel.selectMediaType(it) },
                    onSelectPlatform = { viewModel.selectPlatform(it) },
                    onLoadMore = { viewModel.loadMore() },
                    onMusicClick = { music, items ->
                        viewModel.resolveAndPlay(music, items)
                    },
                    onPlayNext = { music -> viewModel.playNext(music) },
                )
            }
        }
    }
}

// ── SearchHistoryPanel ────────────────────────────────────────────────────────

@Composable
private fun SearchHistoryPanel(
    history: List<String>,
    onItemClick: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    val colors = MusicFreeTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rpx(24)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = rpx(24)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "搜索历史",
                color = colors.text,
                fontSize = FontSizes.content,
                fontWeight = FontWeight.Medium,
            )
            TextButton(onClick = onClearHistory) {
                Text(
                    text = "清空",
                    color = colors.textSecondary,
                    fontSize = FontSizes.subTitle,
                )
            }
        }

        if (history.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rpx(16)),
                verticalArrangement = Arrangement.spacedBy(rpx(16)),
            ) {
                history.forEach { item ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(rpx(32)))
                            .background(colors.backdrop)
                            .clickable { onItemClick(item) }
                            .padding(horizontal = rpx(24), vertical = rpx(12)),
                    ) {
                        Text(
                            text = item,
                            color = colors.text,
                            fontSize = FontSizes.subTitle,
                            maxLines = 1,
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = rpx(48)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无搜索历史",
                    color = colors.textSecondary,
                    fontSize = FontSizes.subTitle,
                )
            }
        }
    }
}

// ── SearchResultPanel ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchResultPanel(
    searchablePlugins: List<com.zili.android.musicfreeandroid.plugin.api.PluginInfo>,
    selectedMediaType: SearchMediaType,
    selectedPlatform: String?,
    currentPluginState: PluginSearchState,
    onSelectMediaType: (SearchMediaType) -> Unit,
    onSelectPlatform: (String) -> Unit,
    onLoadMore: () -> Unit,
    onMusicClick: (MusicItem, List<MusicItem>) -> Unit,
    onPlayNext: (MusicItem) -> Unit,
) {
    val colors = MusicFreeTheme.colors

    Column(modifier = Modifier.fillMaxSize()) {
        // 媒体类型 Tab
        ScrollableTabRow(
            selectedTabIndex = SearchMediaType.entries.indexOf(selectedMediaType).coerceAtLeast(0),
            edgePadding = 0.dp,
            containerColor = colors.pageBackground,
            contentColor = colors.primary,
        ) {
            SearchMediaType.entries.forEach { type ->
                Tab(
                    selected = type == selectedMediaType,
                    onClick = { onSelectMediaType(type) },
                    text = { Text(type.label, fontSize = FontSizes.subTitle) },
                    modifier = Modifier.width(rpx(160)),
                )
            }
        }

        // 插件 Tab
        if (searchablePlugins.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = searchablePlugins.indexOfFirst { it.platform == selectedPlatform }.coerceAtLeast(0),
                edgePadding = 0.dp,
                indicator = {},
                containerColor = colors.pageBackground,
            ) {
                searchablePlugins.forEach { plugin ->
                    Tab(
                        selected = plugin.platform == selectedPlatform,
                        onClick = { onSelectPlatform(plugin.platform) },
                        text = {
                            Text(
                                text = plugin.platform,
                                fontSize = FontSizes.subTitle,
                                color = if (plugin.platform == selectedPlatform) colors.primary else colors.textSecondary,
                            )
                        },
                        modifier = Modifier.width(rpx(140)),
                    )
                }
            }
        }

        // 结果区域
        when (val state = currentPluginState) {
            is PluginSearchState.Idle -> { /* 空 */ }

            is PluginSearchState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.primary)
                }
            }

            is PluginSearchState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.message,
                        color = colors.textSecondary,
                        fontSize = FontSizes.subTitle,
                    )
                }
            }

            is PluginSearchState.Success -> {
                val items = state.items
                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "暂无搜索结果",
                            color = colors.textSecondary,
                            fontSize = FontSizes.content,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = items,
                            key = { item -> "${item.platform}_${item.id}" },
                        ) { music ->
                            MusicResultItem(
                                item = music,
                                onClick = { onMusicClick(music, items) },
                                onPlayNext = { onPlayNext(music) },
                            )
                        }

                        if (!state.isEnd) {
                            item {
                                LaunchedEffect(state.page) { onLoadMore() }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(rpx(24)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(rpx(48)),
                                        color = colors.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── MusicResultItem ───────────────────────────────────────────────────────────

@Composable
private fun MusicResultItem(
    item: MusicItem,
    onClick: () -> Unit,
    onPlayNext: () -> Unit = {},
) {
    val colors = MusicFreeTheme.colors
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rpx(120))
            .clickable(onClick = onClick)
            .padding(horizontal = rpx(24)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            uri = item.artwork,
            size = rpx(80),
            cornerRadius = rpx(16),
        )

        Spacer(Modifier.width(rpx(24)))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = colors.text,
                fontSize = FontSizes.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.artist,
                color = colors.textSecondary,
                fontSize = FontSizes.subTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = { showMenu = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_ellipsis_vertical),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(IconSizes.normal),
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("下一首播放") },
                onClick = {
                    showMenu = false
                    onPlayNext()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_motion_play),
                        contentDescription = null,
                        modifier = Modifier.size(IconSizes.small),
                    )
                },
            )
            DropdownMenuItem(
                text = { Text("添加到歌单") },
                onClick = {
                    showMenu = false
                    Toast.makeText(context, "添加到歌单功能即将上线", Toast.LENGTH_SHORT).show()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_folder_plus),
                        contentDescription = null,
                        modifier = Modifier.size(IconSizes.small),
                    )
                },
            )
        }
    }
}

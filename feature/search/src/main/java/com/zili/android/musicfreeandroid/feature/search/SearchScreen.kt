package com.zili.android.musicfreeandroid.feature.search

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.IconSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.AddToPlaylistBottomSheetContent
import com.zili.android.musicfreeandroid.core.ui.CoverImage
import com.zili.android.musicfreeandroid.core.ui.DownloadQualityDialog
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.core.ui.MusicFreeStatusBarChrome
import com.zili.android.musicfreeandroid.core.ui.MusicItemOptionsSheet
import com.zili.android.musicfreeandroid.plugin.api.AlbumItemBase
import com.zili.android.musicfreeandroid.plugin.api.ArtistItemBase
import com.zili.android.musicfreeandroid.plugin.api.MusicSheetItemBase
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.api.PluginSearchItem
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onOpenAlbumDetail: (AlbumItemBase) -> Unit,
    onOpenArtistDetail: (ArtistItemBase) -> Unit,
    onOpenSheetDetail: (MusicSheetItemBase) -> Unit,
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

    val sheetState by viewModel.sheetState.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()

    val context = LocalContext.current
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf(viewModel.currentQuery.value) }
    var optionsItem by remember { mutableStateOf<MusicItem?>(null) }
    var qualityFor by remember { mutableStateOf<MusicItem?>(null) }
    val defaultQuality by viewModel.defaultDownloadQuality.collectAsStateWithLifecycle(initialValue = PlayQuality.STANDARD)
    // 如果 ViewModel 已有查询（如从历史恢复），同步初始化本地状态
    LaunchedEffect(Unit) {
        val initialQuery = viewModel.currentQuery.value
        if (initialQuery.isNotBlank() && query.isBlank()) {
            query = initialQuery
        }
    }
    LaunchedEffect(Unit) {
        if (!viewModel.consumeInitialAutofocusRequest()) return@LaunchedEffect
        withFrameNanos { }
        searchFocusRequester.requestFocus()
        keyboardController?.show()
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
                        .focusRequester(searchFocusRequester)
                        .testTag(FidelityAnchors.Search.Input)
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
                    onAlbumClick = onOpenAlbumDetail,
                    onArtistClick = onOpenArtistDetail,
                    onSheetClick = onOpenSheetDetail,
                    onPlayNext = { music -> viewModel.playNext(music) },
                    onAddToPlaylist = { music -> viewModel.showAddToPlaylistSheet(music) },
                    onToggleFavorite = { music -> viewModel.toggleFavorite(music) },
                    isFavoriteFlow = viewModel::isFavoriteFlow,
                    onLongClick = { music -> optionsItem = music },
                )
            }
        }
    }

    if (sheetState.visible) {
        var showCreateInSheet by remember { mutableStateOf(false) }
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideAddToPlaylistSheet() },
        ) {
            AddToPlaylistBottomSheetContent(
                playlists = allPlaylists,
                onSelect = { viewModel.addPendingToPlaylist(it.id) },
                onCreateNew = { showCreateInSheet = true },
                folderPlusIcon = painterResource(id = R.drawable.ic_folder_plus),
                favoriteCoverIcon = painterResource(id = R.drawable.ic_playlist_favorite_cover),
            )
        }
        if (showCreateInSheet) {
            InlineCreatePlaylistDialog(
                onDismiss = { showCreateInSheet = false },
                onCreate = { name ->
                    viewModel.createPlaylistAndAddPending(name)
                    showCreateInSheet = false
                },
            )
        }
    }

    optionsItem?.let { item ->
        MusicItemOptionsSheet(
            item = item,
            onDismiss = { optionsItem = null },
            onDownload = { qualityFor = it; optionsItem = null },
        )
    }
    qualityFor?.let { item ->
        DownloadQualityDialog(
            initial = defaultQuality,
            onDismiss = { qualityFor = null },
            onConfirm = { q -> viewModel.download(item, q); qualityFor = null },
        )
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
    searchablePlugins: List<PluginInfo>,
    selectedMediaType: SearchMediaType,
    selectedPlatform: String?,
    currentPluginState: PluginSearchState,
    onSelectMediaType: (SearchMediaType) -> Unit,
    onSelectPlatform: (String) -> Unit,
    onLoadMore: () -> Unit,
    onMusicClick: (MusicItem, List<MusicItem>) -> Unit,
    onAlbumClick: (AlbumItemBase) -> Unit,
    onArtistClick: (ArtistItemBase) -> Unit,
    onSheetClick: (MusicSheetItemBase) -> Unit,
    onPlayNext: (MusicItem) -> Unit,
    onAddToPlaylist: (MusicItem) -> Unit,
    onToggleFavorite: (MusicItem) -> Unit,
    isFavoriteFlow: (MusicItem) -> kotlinx.coroutines.flow.Flow<Boolean>,
    onLongClick: (MusicItem) -> Unit = {},
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
                } else if (selectedMediaType == SearchMediaType.SHEET) {
                    val sheets = items.filterIsInstance<PluginSearchItem.Sheet>()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(rpx(24)),
                        horizontalArrangement = Arrangement.spacedBy(rpx(16)),
                        verticalArrangement = Arrangement.spacedBy(rpx(24)),
                    ) {
                        gridItems(
                            items = sheets,
                            key = { item -> searchResultKey(item) },
                        ) { sheet ->
                            SheetResultItem(
                                item = sheet.item,
                                onClick = { onSheetClick(sheet.item) },
                            )
                        }

                        if (!state.isEnd) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SearchLoadingFooter(state.page, onLoadMore)
                            }
                        }
                    }
                } else {
                    val musicQueue = items.filterIsInstance<PluginSearchItem.Music>().map { it.item }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = items,
                            key = { item -> searchResultKey(item) },
                        ) { result ->
                            when (result) {
                                is PluginSearchItem.Music -> {
                                    val music = result.item
                                    MusicResultItem(
                                        item = music,
                                        onClick = { onMusicClick(music, musicQueue) },
                                        onLongClick = { onLongClick(music) },
                                        onPlayNext = { onPlayNext(music) },
                                        onAddToPlaylist = { onAddToPlaylist(music) },
                                        onToggleFavorite = { onToggleFavorite(music) },
                                        isFavoriteFlow = isFavoriteFlow,
                                    )
                                }
                                is PluginSearchItem.Album -> MediaResultItem(
                                    coverUri = result.item.artwork,
                                    title = result.item.title.orEmpty().ifBlank { "未命名专辑" },
                                    tag = result.item.platform,
                                    description = albumDescription(result.item),
                                    onClick = { onAlbumClick(result.item) },
                                )
                                is PluginSearchItem.Artist -> MediaResultItem(
                                    coverUri = result.item.avatar,
                                    title = result.item.name.orEmpty().ifBlank { "未知歌手" },
                                    tag = result.item.platform,
                                    description = artistDescription(result.item),
                                    onClick = { onArtistClick(result.item) },
                                )
                                is PluginSearchItem.Sheet -> MediaResultItem(
                                    coverUri = result.item.artwork ?: result.item.coverImg,
                                    title = result.item.title.orEmpty().ifBlank { "未命名歌单" },
                                    tag = result.item.platform,
                                    description = result.item.description.orEmpty(),
                                    onClick = { onSheetClick(result.item) },
                                )
                            }
                        }

                        if (!state.isEnd) {
                            item { SearchLoadingFooter(state.page, onLoadMore) }
                        }
                    }
                }
            }
        }
    }
}

private fun searchResultKey(item: PluginSearchItem): String = when (item) {
    is PluginSearchItem.Music -> "music_${item.item.platform}_${item.item.id}"
    is PluginSearchItem.Album -> "album_${item.item.platform}_${item.item.id}"
    is PluginSearchItem.Artist -> "artist_${item.item.platform}_${item.item.id}"
    is PluginSearchItem.Sheet -> "sheet_${item.item.platform}_${item.item.id}"
}

private fun albumDescription(item: AlbumItemBase): String =
    listOfNotNull(item.artist, item.date)
        .filter { it.isNotBlank() }
        .joinToString("    ")

private fun artistDescription(item: ArtistItemBase): String = when {
    !item.description.isNullOrBlank() -> item.description.orEmpty()
    item.worksNum != null -> "作品 ${item.worksNum}"
    else -> ""
}

@Composable
private fun SearchLoadingFooter(page: Int, onLoadMore: () -> Unit) {
    val colors = MusicFreeTheme.colors
    LaunchedEffect(page) { onLoadMore() }
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

@Composable
private fun MediaResultItem(
    coverUri: String?,
    title: String,
    tag: String,
    description: String,
    onClick: () -> Unit,
) {
    val colors = MusicFreeTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rpx(120))
            .clickable(onClick = onClick)
            .testTag(FidelityAnchors.Search.ResultMediaRow)
            .padding(horizontal = rpx(24)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            uri = coverUri,
            size = rpx(80),
            cornerRadius = rpx(16),
        )
        Spacer(Modifier.width(rpx(24)))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = colors.text,
                    fontSize = FontSizes.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (tag.isNotBlank()) {
                    Spacer(Modifier.width(rpx(12)))
                    Text(
                        text = tag,
                        color = colors.primary,
                        fontSize = FontSizes.subTitle,
                        maxLines = 1,
                    )
                }
            }
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    color = colors.textSecondary,
                    fontSize = FontSizes.subTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SheetResultItem(
    item: MusicSheetItemBase,
    onClick: () -> Unit,
) {
    val colors = MusicFreeTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(FidelityAnchors.Search.ResultSheetItem),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverImage(
            uri = item.artwork ?: item.coverImg,
            size = rpx(210),
            cornerRadius = rpx(12),
        )
        Spacer(Modifier.height(rpx(12)))
        Text(
            text = item.title.orEmpty().ifBlank { "未命名歌单" },
            color = colors.text,
            fontSize = FontSizes.subTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── MusicResultItem ───────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MusicResultItem(
    item: MusicItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onPlayNext: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    isFavoriteFlow: ((MusicItem) -> kotlinx.coroutines.flow.Flow<Boolean>)? = null,
) {
    val colors = MusicFreeTheme.colors
    var showMenu by remember { mutableStateOf(false) }
    val isFav by (isFavoriteFlow?.invoke(item) ?: kotlinx.coroutines.flow.flowOf(false))
        .collectAsStateWithLifecycle(initialValue = false)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rpx(120))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag(FidelityAnchors.Search.ResultMusicRow)
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
                    onAddToPlaylist()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_folder_plus),
                        contentDescription = null,
                        modifier = Modifier.size(IconSizes.small),
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(if (isFav) "取消收藏" else "收藏") },
                onClick = {
                    showMenu = false
                    onToggleFavorite()
                },
            )
        }
    }
}

// ── InlineCreatePlaylistDialog ────────────────────────────────────────────────

@Composable
private fun InlineCreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建歌单") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onCreate(name.trim())
            }) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

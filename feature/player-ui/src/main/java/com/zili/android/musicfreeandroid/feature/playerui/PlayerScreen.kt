package com.zili.android.musicfreeandroid.feature.playerui

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.zili.android.musicfreeandroid.core.R
import com.zili.android.musicfreeandroid.core.model.PlaybackMode
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.IconSizes
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.AddToPlaylistBottomSheetContent
import com.zili.android.musicfreeandroid.data.repository.LocalLyricKind
import com.zili.android.musicfreeandroid.feature.playerui.component.queue.PlayQueueSheet
import com.zili.android.musicfreeandroid.feature.playerui.lyrics.PlayerLyricMoreDialog
import com.zili.android.musicfreeandroid.feature.playerui.lyrics.PlayerLyricSearchSheet
import com.zili.android.musicfreeandroid.feature.playerui.lyrics.PlayerLyricsContent
import com.zili.android.musicfreeandroid.feature.playerui.lyrics.PlayerLyricsOperations
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val currentItem = state.currentItem
    val lyricsUiState by viewModel.lyricsUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isFav by viewModel.isCurrentFavorite.collectAsStateWithLifecycle()
    val sheetState by viewModel.sheetState.collectAsStateWithLifecycle()
    val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val lyricSearchResults by viewModel.lyricSearchResults.collectAsStateWithLifecycle()
    val lyricSearchLoading by viewModel.lyricSearchLoading.collectAsStateWithLifecycle()
    var contentPage by remember { mutableStateOf(PlayerContentPage.Cover) }
    var showLyricSearchSheet by remember { mutableStateOf(false) }
    var showLyricOffsetDialog by remember { mutableStateOf(false) }
    var showLyricMoreDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var pendingImportKind by remember { mutableStateOf<LocalLyricKind?>(null) }
    val openLyricDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val kind = pendingImportKind
        pendingImportKind = null
        if (kind != null && uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
                    .take(512 * 1024)
            }.onSuccess { text ->
                viewModel.importLocalLyric(text, kind)
                Toast.makeText(context, "已导入歌词", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "读取歌词失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: 纯黑背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        )

        // Layer 2: 封面模糊背景
        val artworkUrl = currentItem?.artwork
        if (!artworkUrl.isNullOrBlank()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(50.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
                        .alpha(0.5f),
                    contentScale = ContentScale.Crop,
                )
            } else {
                // API 29-30: 简单半透明遮罩
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                )
            }
        }

        // Layer 3: 内容
        PlayerContentLayer {
            PlayerNavBar(
                title = currentItem?.title ?: "",
                artist = currentItem?.artist ?: "",
                platform = currentItem?.platform,
                onBack = onBack,
                onShare = {},
            )

            when (contentPage) {
                PlayerContentPage.Cover -> {
                    PlayerCoverPageContent(
                        artworkUrl = artworkUrl,
                        isFav = isFav,
                        hasCurrentItem = currentItem != null,
                        onToggleFav = { viewModel.toggleCurrentFavorite() },
                        onAddToPlaylist = { viewModel.showAddToPlaylistSheet() },
                        onToggleLyrics = { contentPage = PlayerContentPage.Lyrics },
                        modifier = Modifier.weight(1f),
                    )
                }

                PlayerContentPage.Lyrics -> {
                    PlayerLyricsContent(
                        state = lyricsUiState,
                        durationMs = state.duration,
                        isPlaying = state.isPlaying,
                        onBackToCover = { contentPage = PlayerContentPage.Cover },
                        onSeekToLine = viewModel::seekToLyricLine,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )

                    PlayerLyricsOperations(
                        state = lyricsUiState,
                        onFontSize = {
                            val nextFontLevel = (lyricsUiState.fontSizeLevel + 1).mod(4)
                            viewModel.setLyricDetailFontSize(nextFontLevel)
                        },
                        onOffset = { showLyricOffsetDialog = true },
                        onSearch = {
                            showLyricSearchSheet = true
                            viewModel.searchLyrics()
                        },
                        onToggleTranslation = {
                            viewModel.setLyricShowTranslation(!lyricsUiState.showTranslation)
                        },
                        onMore = { showLyricMoreDialog = true },
                    )

                    PlayerLyricsOperationsBottomSpacer()
                }
            }

            PlayerSeekBar(
                position = state.position,
                duration = state.duration,
                onSeek = { viewModel.seekTo(it) },
                modifier = Modifier
                    .padding(horizontal = rpx(48))
                    .testTag(PlayerSeekBarTestTag),
            )

            PlayerControls(
                isPlaying = state.isPlaying,
                playbackMode = PlaybackMode.from(
                    shuffleEnabled = state.shuffleEnabled,
                    repeatMode = state.repeatMode,
                ),
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onSkipPrevious = { viewModel.skipToPrevious() },
                onSkipNext = { viewModel.skipToNext() },
                onCyclePlaybackMode = { viewModel.cyclePlaybackMode() },
                onOpenQueue = { showQueueSheet = true },
            )

            Spacer(Modifier.height(rpx(48)))
        }

        // Add-to-playlist bottom sheet
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
                InlinePlayerCreatePlaylistDialog(
                    onDismiss = { showCreateInSheet = false },
                    onCreate = { name ->
                        viewModel.createPlaylistAndAddPending(name)
                        showCreateInSheet = false
                    },
                )
            }
        }

        if (showLyricSearchSheet) {
            PlayerLyricSearchSheet(
                groups = lyricSearchResults,
                loading = lyricSearchLoading,
                onDismiss = { showLyricSearchSheet = false },
                onSelect = { target ->
                    viewModel.associateLyric(target)
                    showLyricSearchSheet = false
                    Toast.makeText(context, "已关联歌词", Toast.LENGTH_SHORT).show()
                },
            )
        }

        if (showLyricOffsetDialog) {
            LyricOffsetDialog(
                currentOffsetMs = lyricsUiState.userOffsetMs,
                onDismiss = { showLyricOffsetDialog = false },
                onSetOffset = viewModel::setLyricOffset,
            )
        }

        if (showLyricMoreDialog) {
            PlayerLyricMoreDialog(
                onDismiss = { showLyricMoreDialog = false },
                onImportRaw = {
                    showLyricMoreDialog = false
                    pendingImportKind = LocalLyricKind.Raw
                    openLyricDocument.launch(arrayOf("text/*", "application/octet-stream"))
                },
                onImportTranslation = {
                    showLyricMoreDialog = false
                    pendingImportKind = LocalLyricKind.Translation
                    openLyricDocument.launch(arrayOf("text/*", "application/octet-stream"))
                },
                onDeleteLocal = {
                    viewModel.deleteLocalLyric()
                    showLyricMoreDialog = false
                    Toast.makeText(context, "已删除本地歌词", Toast.LENGTH_SHORT).show()
                },
                onClearAssociated = {
                    viewModel.clearAssociatedLyric()
                    showLyricMoreDialog = false
                    Toast.makeText(context, "已解除关联歌词", Toast.LENGTH_SHORT).show()
                },
            )
        }

        if (showQueueSheet) {
            PlayQueueSheet(
                viewModel = viewModel,
                onDismiss = { showQueueSheet = false },
            )
        }
    }
}

private enum class PlayerContentPage {
    Cover,
    Lyrics,
}

internal const val PlayerModeButtonTestTag = "player.controls.mode"
internal const val PlayerCoverBottomClusterTestTag = "player.cover.bottomCluster"
internal const val PlayerOperationsBarTestTag = "player.operations.bar"
internal const val PlayerOperationSlotTestTag = "player.operations.slot"
internal const val PlayerOperationIconVisualTestTag = "player.operations.iconVisual"
internal const val PlayerOperationImageVisualTestTag = "player.operations.imageVisual"
internal const val PlayerLyricsOperationsBottomSpacerTestTag = "player.lyrics.operations.bottomSpacer"
internal const val PlayerSeekBarTestTag = "player.seekBar"

@DrawableRes
internal fun playerModeIcon(playbackMode: PlaybackMode): Int = when (playbackMode) {
    PlaybackMode.Shuffle -> R.drawable.ic_shuffle
    PlaybackMode.Single -> R.drawable.ic_repeat_song
    PlaybackMode.Queue -> R.drawable.ic_repeat_song_1
}

internal fun playerModeDescription(playbackMode: PlaybackMode): String = when (playbackMode) {
    PlaybackMode.Shuffle -> "随机播放"
    PlaybackMode.Single -> "单曲循环"
    PlaybackMode.Queue -> "列表循环"
}

@Composable
internal fun PlayerContentLayer(
    modifier: Modifier = Modifier,
    statusBarInsets: WindowInsets = WindowInsets.statusBars,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(statusBarInsets.only(WindowInsetsSides.Top)),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun PlayerNavBar(
    title: String,
    artist: String,
    platform: String?,
    onBack: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rpx(150)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(start = rpx(24)),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_left),
                contentDescription = "返回",
                tint = Color.White,
                modifier = Modifier.size(IconSizes.normal),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = FontSizes.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (artist.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(top = rpx(12)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = FontSizes.subTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!platform.isNullOrBlank()) {
                        Spacer(Modifier.size(rpx(8)))
                        Text(
                            text = platform,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = FontSizes.tag,
                            modifier = Modifier
                                .background(
                                    color = Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(rpx(4)),
                                )
                                .padding(horizontal = rpx(8), vertical = rpx(2)),
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onShare,
            modifier = Modifier.padding(end = rpx(24)),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_share),
                contentDescription = "分享",
                tint = Color.White,
                modifier = Modifier.size(IconSizes.normal),
            )
        }
    }
}

@Composable
internal fun PlayerCoverPageContent(
    artworkUrl: String?,
    isFav: Boolean,
    hasCurrentItem: Boolean,
    onToggleFav: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            PlayerCoverArt(
                artworkUrl = artworkUrl,
                modifier = Modifier
                    .size(rpx(500))
                    .clickable { onToggleLyrics() },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PlayerCoverBottomClusterTestTag),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlayerOperationsBar(
                isFav = isFav,
                hasCurrentItem = hasCurrentItem,
                onToggleFav = onToggleFav,
                onAddToPlaylist = onAddToPlaylist,
                onToggleLyrics = onToggleLyrics,
            )
            Spacer(Modifier.height(rpx(24)))
        }
    }
}

@Composable
private fun PlayerCoverArt(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
) {
    val coverShape = RoundedCornerShape(12.dp)
    if (artworkUrl.isNullOrBlank()) {
        Box(
            modifier = modifier
                .clip(coverShape)
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_play),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(rpx(120)),
            )
        }
    } else {
        AsyncImage(
            model = artworkUrl,
            contentDescription = "专辑封面",
            modifier = modifier.clip(coverShape),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
internal fun PlayerLyricsOperationsBottomSpacer() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(rpx(24))
            .testTag(PlayerLyricsOperationsBottomSpacerTestTag),
    )
}

@Composable
internal fun PlayerOperationsBar(
    isFav: Boolean,
    hasCurrentItem: Boolean,
    onToggleFav: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleLyrics: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rpx(80))
            .testTag(PlayerOperationsBarTestTag),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerOperationSlot(
            onClick = onToggleFav,
            enabled = hasCurrentItem,
            contentDescription = if (isFav) "取消收藏" else "收藏",
        ) {
            PlayerOperationIcon(
                icon = if (isFav) R.drawable.ic_heart else R.drawable.ic_heart_outline,
                tint = if (isFav) Color(0xFFE54B4B) else Color.White.copy(alpha = 0.7f),
            )
        }
        PlayerOperationSlot(
            onClick = {},
            contentDescription = "音质",
        ) {
            PlayerOperationImage(
                image = R.drawable.ic_quality_standard,
            )
        }
        PlayerOperationSlot(
            onClick = {},
            contentDescription = "下载",
        ) {
            PlayerOperationIcon(
                icon = R.drawable.ic_arrow_down_tray,
                tint = Color.White.copy(alpha = 0.7f),
            )
        }
        PlayerOperationSlot(
            onClick = {},
            contentDescription = "倍速",
        ) {
            PlayerOperationImage(
                image = R.drawable.ic_rate_100,
            )
        }
        PlayerOperationSlot(
            onClick = onToggleLyrics,
            enabled = hasCurrentItem,
            contentDescription = "歌词",
        ) {
            PlayerOperationIcon(
                icon = R.drawable.ic_chat_bubble,
                tint = Color.White.copy(alpha = 0.7f),
            )
        }
        Box {
            PlayerOperationSlot(
                onClick = { menuExpanded = true },
                contentDescription = "更多",
            ) {
                PlayerOperationIcon(
                    icon = R.drawable.ic_ellipsis_vertical,
                    tint = Color.White.copy(alpha = 0.7f),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("加入歌单") },
                    onClick = {
                        menuExpanded = false
                        onAddToPlaylist()
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayerOperationSlot(
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(rpx(64))
            .testTag(PlayerOperationSlotTestTag)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun PlayerOperationIcon(
    @DrawableRes icon: Int,
    tint: Color,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(IconSizes.normal)
            .testTag(PlayerOperationIconVisualTestTag),
    )
}

@Composable
private fun PlayerOperationImage(
    @DrawableRes image: Int,
) {
    Image(
        painter = painterResource(image),
        contentDescription = null,
        modifier = Modifier
            .size(rpx(52))
            .testTag(PlayerOperationImageVisualTestTag),
    )
}

@Composable
internal fun PlayerSeekBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    // seekTarget：seek 发出后到播放器回传新 position 之前，用此值防止进度条回弹
    var seekTarget by remember { mutableFloatStateOf(-1f) }

    val sliderValue = when {
        isDragging -> dragPosition
        seekTarget >= 0f && duration > 0 && (position.toFloat() / duration.toFloat()) < seekTarget - 0.01f -> seekTarget
        else -> {
            if (seekTarget >= 0f) seekTarget = -1f  // 播放器已追上，清除 seekTarget
            if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatDuration(if (isDragging) (dragPosition * duration).toLong() else position),
            color = Color(0xFFCCCCCC),
            fontSize = FontSizes.description,
        )
        Slider(
            value = sliderValue,
            onValueChange = {
                isDragging = true
                dragPosition = it
            },
            onValueChangeFinished = {
                isDragging = false
                seekTarget = dragPosition
                onSeek((dragPosition * duration).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFDDDDDD),
                activeTrackColor = Color(0xFFCCCCCC),
                inactiveTrackColor = Color(0xFF999999),
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatDuration(duration),
            color = Color(0xFFCCCCCC),
            fontSize = FontSizes.description,
        )
    }
}

@Composable
internal fun PlayerControls(
    isPlaying: Boolean,
    playbackMode: PlaybackMode,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rpx(100))
            .padding(top = rpx(36), start = rpx(24), end = rpx(24)),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 随机/循环模式图标
        val modeIcon = playerModeIcon(playbackMode)
        val modeDescription = playerModeDescription(playbackMode)
        IconButton(
            onClick = onCyclePlaybackMode,
            modifier = Modifier.testTag(PlayerModeButtonTestTag),
        ) {
            Icon(
                painter = painterResource(modeIcon),
                contentDescription = modeDescription,
                tint = Color.White,
                modifier = Modifier.size(rpx(56)),
            )
        }

        IconButton(onClick = onSkipPrevious) {
            Icon(
                painter = painterResource(R.drawable.ic_skip_left),
                contentDescription = "上一曲",
                tint = Color.White,
                modifier = Modifier.size(rpx(56)),
            )
        }

        IconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(rpx(96)),
        ) {
            Icon(
                painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = Color.White,
                modifier = Modifier.size(rpx(72)),
            )
        }

        IconButton(onClick = onSkipNext) {
            Icon(
                painter = painterResource(R.drawable.ic_skip_right),
                contentDescription = "下一曲",
                tint = Color.White,
                modifier = Modifier.size(rpx(56)),
            )
        }

        IconButton(onClick = onOpenQueue) {
            Icon(
                painter = painterResource(R.drawable.ic_playlist),
                contentDescription = "播放列表",
                tint = Color.White,
                modifier = Modifier.size(rpx(56)),
            )
        }
    }
}

@Composable
private fun LyricOffsetDialog(
    currentOffsetMs: Long,
    onDismiss: () -> Unit,
    onSetOffset: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置歌词进度") },
        text = {
            Column {
                Text("当前：${formatLyricOffset(currentOffsetMs)}")
                Spacer(Modifier.height(rpx(8)))
                TextButton(
                    onClick = { onSetOffset(currentOffsetMs + 500L) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("提前0.5s")
                }
                TextButton(
                    onClick = { onSetOffset(currentOffsetMs - 500L) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("延后0.5s")
                }
                TextButton(
                    onClick = { onSetOffset(0L) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("重置")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun InlinePlayerCreatePlaylistDialog(
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

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatLyricOffset(offsetMs: Long): String {
    val seconds = String.format(Locale.US, "%.1f", kotlin.math.abs(offsetMs) / 1000f)
    return when {
        offsetMs > 0L -> "提前 ${seconds}s"
        offsetMs < 0L -> "延后 ${seconds}s"
        else -> "0.0s"
    }
}

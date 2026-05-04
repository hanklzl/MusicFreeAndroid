package com.zili.android.musicfreeandroid.feature.playerui

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.zili.android.musicfreeandroid.core.R
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.IconSizes
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.AddToPlaylistBottomSheetContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val currentItem = state.currentItem
    val context = LocalContext.current
    val isFav by viewModel.isCurrentFavorite.collectAsStateWithLifecycle()
    val sheetState by viewModel.sheetState.collectAsStateWithLifecycle()
    val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()

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

            Spacer(Modifier.weight(1f))

            PlayerCoverArt(
                artworkUrl = artworkUrl,
                modifier = Modifier.size(rpx(500)),
            )

            PlayerOperationsBar(
                isFav = isFav,
                hasCurrentItem = currentItem != null,
                onToggleFav = { viewModel.toggleCurrentFavorite() },
                onAddToPlaylist = { viewModel.showAddToPlaylistSheet() },
            )

            Spacer(Modifier.weight(1f))

            PlayerSeekBar(
                position = state.position,
                duration = state.duration,
                onSeek = { viewModel.seekTo(it) },
                modifier = Modifier.padding(horizontal = rpx(48)),
            )

            PlayerControls(
                isPlaying = state.isPlaying,
                repeatMode = state.repeatMode,
                shuffleEnabled = state.shuffleEnabled,
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onSkipPrevious = { viewModel.skipToPrevious() },
                onSkipNext = { viewModel.skipToNext() },
                onCycleRepeatMode = { viewModel.cycleRepeatMode() },
                onToggleShuffle = { viewModel.toggleShuffle() },
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
    }
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
private fun PlayerOperationsBar(
    isFav: Boolean,
    hasCurrentItem: Boolean,
    onToggleFav: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rpx(80))
            .padding(horizontal = rpx(48)),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onToggleFav,
            enabled = hasCurrentItem,
        ) {
            Icon(
                painter = painterResource(
                    id = if (isFav) R.drawable.ic_heart else R.drawable.ic_heart_outline,
                ),
                contentDescription = if (isFav) "取消收藏" else "收藏",
                tint = if (isFav) Color(0xFFE54B4B) else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(IconSizes.normal),
            )
        }
        Text(
            text = "标准",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = FontSizes.description,
        )
        IconButton(onClick = {}) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_down_tray),
                contentDescription = "下载",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(IconSizes.normal),
            )
        }
        Text(
            text = "1.0x",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = FontSizes.description,
        )
        IconButton(onClick = {}) {
            Icon(
                painter = painterResource(R.drawable.ic_chat_bubble),
                contentDescription = "歌词",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(IconSizes.normal),
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_ellipsis_vertical),
                    contentDescription = "更多",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(IconSizes.normal),
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
private fun PlayerSeekBar(
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
private fun PlayerControls(
    isPlaying: Boolean,
    repeatMode: RepeatMode,
    shuffleEnabled: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
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
        val modeIcon = when {
            shuffleEnabled -> R.drawable.ic_shuffle
            repeatMode == RepeatMode.ONE -> R.drawable.ic_repeat_song_1
            else -> R.drawable.ic_repeat_song
        }
        IconButton(
            onClick = if (shuffleEnabled) onToggleShuffle else onCycleRepeatMode,
        ) {
            Icon(
                painter = painterResource(modeIcon),
                contentDescription = "播放模式",
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

        IconButton(onClick = { /* TODO: 弹出队列 */ }) {
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

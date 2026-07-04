package com.hank.musicfree.feature.settings.cachemanagement

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.shortLabel
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.ui.FidelityAnchors
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
import com.hank.musicfree.core.ui.logUiClick
import com.hank.musicfree.core.ui.loggedClick
import com.hank.musicfree.data.repository.OnlineCacheQualityRow
import com.hank.musicfree.data.repository.OnlineCacheQualityStatus
import com.hank.musicfree.data.repository.OnlineCacheSongRow
import com.hank.musicfree.feature.settings.components.SettingActionRow
import com.hank.musicfree.feature.settings.components.SettingSectionCard
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.UiLogEvents

@Composable
fun CacheManagementScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CacheManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CacheManagementContent(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onSelectRow = viewModel::selectRow,
        onClearQuality = viewModel::clearSelectedQuality,
        onClearSong = viewModel::clearSelectedSong,
        onClearAll = viewModel::clearAll,
        modifier = modifier,
    )
}

@Composable
internal fun CacheManagementContent(
    state: CacheManagementUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (CacheManagementFilter) -> Unit,
    onSelectRow: (OnlineCacheSongRow?) -> Unit,
    onClearQuality: (PlayQuality) -> Unit,
    onClearSong: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmAction by remember { mutableStateOf<ConfirmCacheAction?>(null) }
    var pendingDetailDismissOutcome by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.selectedRow) {
        if (state.selectedRow == null) {
            pendingDetailDismissOutcome?.let { outcome ->
                logDialogDismiss(DETAIL_DIALOG_ID, outcome)
                pendingDetailDismissOutcome = null
            }
        }
    }

    MusicFreeScreenScaffold(
        title = "歌曲缓存管理",
        onBack = onBack,
        modifier = modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Screen.CacheManagementRoot)
            .semantics { testTagsAsResourceId = true },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = rpx(24)),
            verticalArrangement = Arrangement.spacedBy(rpx(16)),
        ) {
            item { Spacer(modifier = Modifier.height(rpx(16))) }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onSearchQueryChange,
                    label = { Text("搜索歌曲、歌手或来源") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                CacheFilterRow(
                    selected = state.filter,
                    onSelected = onFilterChange,
                )
            }
            item { CacheSummaryRow(summary = state.summary) }
            item {
                SettingActionRow(
                    title = "清理全部在线播放缓存",
                    enabled = state.allRows.isNotEmpty() && !state.isClearing,
                    trailingText = formatBytes(state.summary.totalBytes),
                    onClick = {
                        logUiClick(
                            targetId = "cache_management.clear_all",
                            screen = SCREEN_NAME,
                            targetLabel = "清理全部在线播放缓存",
                            extra = mapOf("songCount" to state.summary.songCount),
                        )
                        confirmAction = ConfirmCacheAction.ClearAll
                    },
                )
            }
            state.message?.let { message ->
                item {
                    Text(
                        text = message,
                        fontSize = FontSizes.description,
                        color = MusicFreeTheme.colors.primary,
                    )
                }
            }
            when {
                state.isLoading -> item {
                    Text(
                        text = "加载中",
                        fontSize = FontSizes.description,
                        color = MusicFreeTheme.colors.textSecondary,
                    )
                }
                state.errorMessage != null -> item {
                    CacheErrorState(
                        message = state.errorMessage,
                        onRefresh = onRefresh,
                    )
                }
                state.visibleRows.isEmpty() -> item {
                    Text(
                        text = if (state.query.isBlank() && state.filter == CacheManagementFilter.All) {
                            "暂无在线播放缓存"
                        } else {
                            "没有匹配的缓存"
                        },
                        fontSize = FontSizes.content,
                        color = MusicFreeTheme.colors.textSecondary,
                    )
                }
                else -> items(
                    items = state.visibleRows,
                    key = { it.identityKey() },
                ) { row ->
                    CacheSongRow(
                        row = row,
                        onClick = { onSelectRow(row) },
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(rpx(16))) }
        }
    }

    CacheDetailSheet(
        row = state.selectedRow,
        isClearing = state.isClearing,
        onDismiss = {
            pendingDetailDismissOutcome = null
            onSelectRow(null)
        },
        onClearQuality = { quality ->
            logUiClick(
                targetId = "cache_management.clear_quality",
                screen = SCREEN_NAME,
                targetLabel = "清理该音质",
                extra = mapOf("quality" to quality.name.lowercase()),
            )
            confirmAction = ConfirmCacheAction.ClearQuality(quality)
        },
        onClearSong = {
            logUiClick(
                targetId = "cache_management.clear_song",
                screen = SCREEN_NAME,
                targetLabel = "清理整首歌在线播放缓存",
                extra = mapOf("qualityCount" to (state.selectedRow?.qualities?.size ?: 0)),
            )
            confirmAction = ConfirmCacheAction.ClearSong
        },
    )
    ConfirmCacheDialog(
        action = confirmAction,
        onSystemDismiss = {
            logDialogDismiss(CONFIRM_DIALOG_ID, UiLogEvents.Outcome.SYSTEM)
            confirmAction = null
        },
        onCancel = {
            logDialogDismiss(CONFIRM_DIALOG_ID, UiLogEvents.Outcome.CANCEL)
            confirmAction = null
        },
        onConfirm = { action ->
            logDialogDismiss(CONFIRM_DIALOG_ID, UiLogEvents.Outcome.CONFIRM)
            confirmAction = null
            when (action) {
                ConfirmCacheAction.ClearAll -> onClearAll()
                ConfirmCacheAction.ClearSong -> {
                    pendingDetailDismissOutcome = UiLogEvents.Outcome.CONFIRM
                    onClearSong()
                }
                is ConfirmCacheAction.ClearQuality -> {
                    pendingDetailDismissOutcome = UiLogEvents.Outcome.CONFIRM
                    onClearQuality(action.quality)
                }
            }
        },
    )
}

@Composable
private fun CacheErrorState(
    message: String,
    onRefresh: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(rpx(8)),
    ) {
        Text(
            text = message,
            fontSize = FontSizes.description,
            color = MusicFreeTheme.colors.danger,
        )
        TextButton(
            onClick = {
                logUiClick(
                    targetId = "cache_management.retry",
                    screen = SCREEN_NAME,
                    targetLabel = "重试",
                )
                onRefresh()
            },
        ) {
            Text("重试")
        }
    }
}

@Composable
private fun CacheFilterRow(
    selected: CacheManagementFilter,
    onSelected: (CacheManagementFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(rpx(8)),
    ) {
        CacheManagementFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = {
                    logUiClick(
                        targetId = "cache_management.filter.${filter.name.lowercase()}",
                        screen = SCREEN_NAME,
                        targetLabel = filter.label(),
                    )
                    onSelected(filter)
                },
                label = { Text(filter.label()) },
            )
        }
    }
}

@Composable
private fun CacheSummaryRow(summary: CacheManagementSummary) {
    Text(
        text = "${summary.songCount} 首 · ${summary.qualityCount} 个音质 · 可复用 ${summary.reusableCount} · ${formatBytes(summary.totalBytes)}",
        fontSize = FontSizes.description,
        color = MusicFreeTheme.colors.textSecondary,
    )
}

@Composable
private fun CacheSongRow(
    row: OnlineCacheSongRow,
    onClick: () -> Unit,
) {
    SettingSectionCard(title = row.platform) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(cacheRowTag(row))
                .loggedClick(
                    targetId = "cache_management.list.song_row",
                    screen = SCREEN_NAME,
                    targetLabel = "缓存歌曲",
                    fields = mapOf(
                        "platform" to row.platform,
                        "itemId" to row.itemId,
                        "qualityCount" to row.qualities.size,
                    ),
                    onClick = onClick,
                )
                .padding(horizontal = rpx(24), vertical = rpx(8)),
            verticalArrangement = Arrangement.spacedBy(rpx(8)),
        ) {
            Text(
                text = row.displayTitle(),
                fontSize = FontSizes.title,
                color = MusicFreeTheme.colors.text,
            )
            Text(
                text = row.displayArtist(),
                fontSize = FontSizes.description,
                color = MusicFreeTheme.colors.textSecondary,
            )
            Text(
                text = "${row.platform} · ${formatBytes(row.totalBytes)}",
                fontSize = FontSizes.description,
                color = MusicFreeTheme.colors.textSecondary,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(rpx(8)),
            ) {
                row.qualities.take(4).forEach { quality ->
                    Text(
                        text = quality.status.label(),
                        fontSize = FontSizes.description,
                        color = quality.status.color(),
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CacheDetailSheet(
    row: OnlineCacheSongRow?,
    isClearing: Boolean,
    onDismiss: () -> Unit,
    onClearQuality: (PlayQuality) -> Unit,
    onClearSong: () -> Unit,
) {
    LaunchedEffect(row?.platform, row?.itemId) {
        if (row != null) {
            logDialogOpen(DETAIL_DIALOG_ID)
        }
    }
    if (row == null) return

    ModalBottomSheet(
        onDismissRequest = {
            logDialogDismiss(DETAIL_DIALOG_ID, UiLogEvents.Outcome.SYSTEM)
            onDismiss()
        },
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = rpx(24), vertical = rpx(16)),
            verticalArrangement = Arrangement.spacedBy(rpx(12)),
        ) {
            Text(
                text = row.displayTitle(),
                fontSize = FontSizes.title,
                color = MusicFreeTheme.colors.text,
            )
            Text(
                text = "${row.displayArtist()} · ${row.platform}",
                fontSize = FontSizes.description,
                color = MusicFreeTheme.colors.textSecondary,
            )
            row.qualities.forEach { quality ->
                CacheQualityDetailRow(
                    quality = quality,
                    isClearing = isClearing,
                    onClearQuality = onClearQuality,
                )
            }
            TextButton(
                enabled = !isClearing,
                onClick = onClearSong,
            ) {
                Text("清理整首歌在线播放缓存")
            }
        }
    }
}

@Composable
private fun CacheQualityDetailRow(
    quality: OnlineCacheQualityRow,
    isClearing: Boolean,
    onClearQuality: (PlayQuality) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rpx(4)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = quality.quality?.shortLabel() ?: "未知音质",
                fontSize = FontSizes.content,
                color = MusicFreeTheme.colors.text,
            )
            Text(
                text = "${quality.status.label()} · ${formatBytes(quality.cachedBytes)}",
                fontSize = FontSizes.description,
                color = quality.status.color(),
            )
        }
        quality.quality?.let { playQuality ->
            TextButton(
                enabled = !isClearing,
                onClick = { onClearQuality(playQuality) },
            ) {
                Text("清理该音质")
            }
        }
    }
}

@Composable
private fun ConfirmCacheDialog(
    action: ConfirmCacheAction?,
    onSystemDismiss: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: (ConfirmCacheAction) -> Unit,
) {
    LaunchedEffect(action?.dialogKey()) {
        if (action != null) {
            logDialogOpen(CONFIRM_DIALOG_ID)
        }
    }
    if (action == null) return

    AlertDialog(
        onDismissRequest = onSystemDismiss,
        title = { Text("确认清理缓存") },
        text = {
            Text(
                text = "只会清理在线播放缓存，不会删除已下载歌曲和本地音乐。",
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(action) }) {
                Text("清理")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("取消")
            }
        },
    )
}

private sealed interface ConfirmCacheAction {
    data object ClearAll : ConfirmCacheAction
    data object ClearSong : ConfirmCacheAction
    data class ClearQuality(val quality: PlayQuality) : ConfirmCacheAction
}

private fun ConfirmCacheAction.dialogKey(): String = when (this) {
    ConfirmCacheAction.ClearAll -> "all"
    ConfirmCacheAction.ClearSong -> "song"
    is ConfirmCacheAction.ClearQuality -> "quality:${quality.name}"
}

private fun OnlineCacheSongRow.displayTitle(): String = title.ifBlank { "未知歌曲" }

private fun OnlineCacheSongRow.displayArtist(): String = artist.ifBlank { "未知歌手" }

private fun OnlineCacheSongRow.identityKey(): String =
    "${platform.length}:$platform|${itemId.length}:$itemId"

internal fun cacheRowTag(row: OnlineCacheSongRow): String =
    "cache_management.row.${row.platform.cacheTagPart()}.${row.itemId.cacheTagPart()}.${row.identityKey().hashCode().toUInt().toString(16)}"

private fun String.cacheTagPart(): String {
    val sanitized = lowercase()
        .map { char ->
            if (char in 'a'..'z' || char in '0'..'9' || char == '_' || char == '-') {
                char
            } else {
                '_'
            }
        }
        .joinToString("")
        .trim('_')
        .take(CACHE_TAG_PART_MAX_LENGTH)
    return sanitized.ifBlank { "blank" }
}

private fun CacheManagementFilter.label(): String = when (this) {
    CacheManagementFilter.All -> "全部"
    CacheManagementFilter.Reusable -> "可复用"
    CacheManagementFilter.Partial -> "部分缓存"
    CacheManagementFilter.SourceOnly -> "仅解析"
    CacheManagementFilter.Invalid -> "异常"
}

private fun OnlineCacheQualityStatus.label(): String = when (this) {
    OnlineCacheQualityStatus.Reusable -> "可复用"
    OnlineCacheQualityStatus.Complete -> "完整"
    OnlineCacheQualityStatus.Partial -> "部分缓存"
    OnlineCacheQualityStatus.SourceOnly -> "仅解析"
    OnlineCacheQualityStatus.Invalid -> "异常"
}

@Composable
private fun OnlineCacheQualityStatus.color() = when (this) {
    OnlineCacheQualityStatus.Reusable,
    OnlineCacheQualityStatus.Complete,
    -> MusicFreeTheme.colors.success
    OnlineCacheQualityStatus.Partial,
    OnlineCacheQualityStatus.SourceOnly,
    -> MusicFreeTheme.colors.info
    OnlineCacheQualityStatus.Invalid -> MusicFreeTheme.colors.danger
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= BYTES_PER_MB -> "${bytes / BYTES_PER_MB} MB"
    bytes >= BYTES_PER_KB -> "${bytes / BYTES_PER_KB} KB"
    else -> "$bytes B"
}

private fun logDialogOpen(dialogId: String) {
    MfLog.detail(
        category = LogCategory.UI,
        event = UiLogEvents.DIALOG_OPEN,
        fields = mapOf(
            UiLogEvents.Fields.DIALOG_ID to dialogId,
            UiLogEvents.Fields.SCREEN to SCREEN_NAME,
            UiLogEvents.Fields.TRIGGER to UiLogEvents.Trigger.UI_CLICK,
        ),
    )
}

private fun logDialogDismiss(dialogId: String, outcome: String) {
    MfLog.detail(
        category = LogCategory.UI,
        event = UiLogEvents.DIALOG_DISMISS,
        fields = mapOf(
            UiLogEvents.Fields.DIALOG_ID to dialogId,
            UiLogEvents.Fields.SCREEN to SCREEN_NAME,
            UiLogEvents.Fields.OUTCOME to outcome,
        ),
    )
}

private const val SCREEN_NAME = "cache_management"
private const val DETAIL_DIALOG_ID = "cache_management_detail"
private const val CONFIRM_DIALOG_ID = "cache_management_confirm"
private const val BYTES_PER_KB = 1024L
private const val BYTES_PER_MB = 1024L * 1024L
private const val CACHE_TAG_PART_MAX_LENGTH = 24

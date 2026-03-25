package com.zili.android.musicfreeandroid.feature.home.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.IconSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.CoverImage
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchorPatterns
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.feature.home.playlist.CreatePlaylistDialog

fun LazyListScope.homeSheetsSection(
    uiState: HomeSheetsUiState,
    onSelectTab: (HomeSheetTab) -> Unit,
    onCreateSheet: (String) -> Unit,
    onImportSheet: () -> Unit,
    onOpenMineSheet: (String) -> Unit,
    onOpenStarredSheet: (HomeSheetUiModel) -> Unit = {},
) {
    item(key = FidelityAnchors.Home.SheetsRoot) {
        HomeSheetsSectionHeader(
            uiState = uiState,
            onSelectTab = onSelectTab,
            onCreateSheet = onCreateSheet,
            onImportSheet = onImportSheet,
        )
    }

    if (uiState.items.isNotEmpty()) {
        items(
            items = uiState.items,
            key = { item -> "${item.tab}:${item.id}" },
        ) { item ->
            HomeSheetRow(
                item = item,
                modifier = Modifier.padding(horizontal = rpx(24)),
                onClick = {
                    if (item.tab == HomeSheetTab.Mine) {
                        onOpenMineSheet(item.id)
                    } else {
                        onOpenStarredSheet(item)
                    }
                },
            )
        }
    }
}

@Composable
private fun HomeSheetsSectionHeader(
    uiState: HomeSheetsUiState,
    onSelectTab: (HomeSheetTab) -> Unit,
    onCreateSheet: (String) -> Unit,
    onImportSheet: () -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = onCreateSheet,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(FidelityAnchors.Home.SheetsRoot)
            .semantics { testTagsAsResourceId = true }
            .padding(horizontal = rpx(24)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HomeSheetTabText(
                    title = "我的歌单",
                    count = uiState.mineCount,
                    selected = uiState.selectedTab == HomeSheetTab.Mine,
                    onClick = { onSelectTab(HomeSheetTab.Mine) },
                    anchorTag = FidelityAnchors.Home.SheetsMineTab,
                )
                Spacer(modifier = Modifier.size(rpx(32)))
                HomeSheetTabText(
                    title = "收藏歌单",
                    count = uiState.starredCount,
                    selected = uiState.selectedTab == HomeSheetTab.Starred,
                    onClick = { onSelectTab(HomeSheetTab.Starred) },
                    anchorTag = FidelityAnchors.Home.SheetsStarredTab,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag(FidelityAnchors.Home.SheetsCreate),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "新建歌单",
                        tint = MusicFreeTheme.colors.text,
                        modifier = Modifier.size(IconSizes.light),
                    )
                }
                IconButton(
                    onClick = onImportSheet,
                    modifier = Modifier.testTag(FidelityAnchors.Home.SheetsImport),
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "导入歌单",
                        tint = MusicFreeTheme.colors.text,
                        modifier = Modifier.size(IconSizes.light),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(rpx(12)))

        if (uiState.items.isEmpty()) {
            Text(
                text = "暂无歌单",
                color = MusicFreeTheme.colors.textSecondary,
                fontSize = FontSizes.subTitle,
                modifier = Modifier.padding(vertical = rpx(24)),
            )
        }
    }
}

@Composable
private fun HomeSheetTabText(
    title: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    anchorTag: String,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(anchorTag)
            .padding(bottom = rpx(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = if (selected) MusicFreeTheme.colors.text else MusicFreeTheme.colors.textSecondary,
            fontSize = FontSizes.title,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            text = "($count)",
            color = MusicFreeTheme.colors.textSecondary,
            fontSize = FontSizes.subTitle,
            modifier = Modifier.padding(start = rpx(6)),
        )
    }
}

@Composable
private fun HomeSheetRow(
    item: HomeSheetUiModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val rowTag = if (item.tab == HomeSheetTab.Mine) {
        FidelityAnchorPatterns.mineSheetItem(item.id)
    } else {
        FidelityAnchorPatterns.starredSheetItem(item.id)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(rowTag)
            .padding(vertical = rpx(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            uri = item.coverUri,
            size = rpx(96),
            cornerRadius = rpx(10),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = rpx(20), end = rpx(12)),
        ) {
            Text(
                text = item.title,
                color = MusicFreeTheme.colors.text,
                fontSize = FontSizes.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle,
                color = MusicFreeTheme.colors.textSecondary,
                fontSize = FontSizes.description,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = rpx(6)),
            )
        }
        if (item.tab == HomeSheetTab.Starred && !item.platform.isNullOrBlank()) {
            Text(
                text = item.platform,
                color = MusicFreeTheme.colors.textSecondary,
                fontSize = FontSizes.tag,
                modifier = Modifier
                    .border(
                        border = BorderStroke(1.dp, MusicFreeTheme.colors.placeholder),
                        shape = RoundedCornerShape(rpx(8)),
                    )
                    .padding(horizontal = rpx(10), vertical = rpx(4)),
            )
        }
    }
}

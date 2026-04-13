package com.zili.android.musicfreeandroid.feature.home.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.CoverImage
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchorPatterns
import com.zili.android.musicfreeandroid.feature.home.component.homeInteractionStyle
import com.zili.android.musicfreeandroid.feature.home.HomePlaylistSectionUiModel
import com.zili.android.musicfreeandroid.feature.home.R

fun LazyListScope.homeSheetsList(
    uiModel: HomePlaylistSectionUiModel,
    onOpenMineSheet: (String) -> Unit,
    onOpenStarredSheet: (HomeSheetUiModel) -> Unit,
) {
    items(
        items = uiModel.rows,
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
            .homeInteractionStyle(
                onClick = onClick,
                shape = RoundedCornerShape(rpx(18)),
                minHeight = null,
            )
            .testTag(rowTag)
            .semantics { testTagsAsResourceId = true }
            .padding(vertical = rpx(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            CoverImage(
                uri = item.coverUri,
                size = rpx(96),
                cornerRadius = rpx(10),
            )
            if (item.isDefault) {
                Icon(
                    painter = painterResource(R.drawable.ic_home_heart),
                    contentDescription = null,
                    tint = MusicFreeTheme.colors.primary,
                    modifier = Modifier
                        .size(rpx(36))
                        .align(Alignment.Center),
                )
            }
        }
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
        if (!item.isDefault) {
            Icon(
                painter = painterResource(R.drawable.ic_home_trash_outline),
                contentDescription = null,
                tint = MusicFreeTheme.colors.textSecondary,
                modifier = Modifier.size(rpx(42)),
            )
        }
    }
}

package com.zili.android.musicfreeandroid.feature.home.sheets

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.IconSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.feature.home.component.HomeIcons
import com.zili.android.musicfreeandroid.feature.home.component.homeIconButtonInteractionStyle
import com.zili.android.musicfreeandroid.feature.home.component.homeInteractionStyle
import com.zili.android.musicfreeandroid.feature.home.HomePlaylistSectionUiModel

@Composable
fun HomeSheetsHeader(
    uiModel: HomePlaylistSectionUiModel,
    onSelectTab: (HomeSheetTab) -> Unit,
    onCreateSheetClick: () -> Unit,
    onImportSheetClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(FidelityAnchors.Home.SheetsRoot)
            .semantics { testTagsAsResourceId = true }
            .padding(horizontal = rpx(24)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            HomeSheetTabButton(
                title = "我的歌单",
                count = uiModel.mineCount,
                selected = uiModel.selectedTab == HomeSheetTab.Mine,
                onClick = { onSelectTab(HomeSheetTab.Mine) },
                anchorTag = FidelityAnchors.Home.SheetsMineTab,
            )
            Spacer(modifier = Modifier.size(rpx(32)))
            HomeSheetTabButton(
                title = "收藏歌单",
                count = uiModel.starredCount,
                selected = uiModel.selectedTab == HomeSheetTab.Starred,
                onClick = { onSelectTab(HomeSheetTab.Starred) },
                anchorTag = FidelityAnchors.Home.SheetsStarredTab,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeSheetActionButton(
                iconRes = HomeIcons.SheetsCreate,
                anchorTag = FidelityAnchors.Home.SheetsCreate,
                contentDescription = "新建歌单",
                onClick = onCreateSheetClick,
            )
            Spacer(modifier = Modifier.size(rpx(24)))
            HomeSheetActionButton(
                iconRes = HomeIcons.SheetsImport,
                anchorTag = FidelityAnchors.Home.SheetsImport,
                contentDescription = "导入歌单",
                onClick = onImportSheetClick,
            )
        }
    }
}

@Composable
private fun HomeSheetTabButton(
    title: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    anchorTag: String,
) {
    Row(
        modifier = Modifier
            .homeInteractionStyle(
                onClick = onClick,
                shape = RoundedCornerShape(rpx(12)),
                minHeight = null,
            )
            .testTag(anchorTag)
            .padding(bottom = rpx(8)),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier.width(IntrinsicSize.Max),
        ) {
            Text(
                text = title,
                color = if (selected) MusicFreeTheme.colors.text else MusicFreeTheme.colors.textSecondary,
                fontSize = FontSizes.title,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
            Box(
                modifier = Modifier
                    .padding(top = rpx(2))
                    .width(rpx(72))
                    .height(rpx(6))
                    .background(
                        color = if (selected) MusicFreeTheme.colors.primary else Color.Transparent,
                        shape = RoundedCornerShape(rpx(999)),
                    ),
            )
        }
        Text(
            text = "($count)",
            color = MusicFreeTheme.colors.textSecondary,
            fontSize = FontSizes.subTitle,
            modifier = Modifier.padding(start = rpx(6), bottom = rpx(6)),
        )
    }
}

@Composable
private fun HomeSheetActionButton(
    @DrawableRes iconRes: Int,
    anchorTag: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .homeIconButtonInteractionStyle(
                onClick = onClick,
                shape = CircleShape,
            )
            .testTag(anchorTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = MusicFreeTheme.colors.text,
            modifier = Modifier.size(IconSizes.light),
        )
    }
}

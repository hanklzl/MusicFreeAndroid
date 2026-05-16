package com.hank.musicfree.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Dp
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.IconSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.ui.FidelityAnchorPatterns
import com.hank.musicfree.core.ui.FidelityAnchors
import com.hank.musicfree.core.ui.UpdateBadgeDot
import com.hank.musicfree.feature.home.HomeDrawerAction
import com.hank.musicfree.feature.home.HomeDrawerItemUiModel
import com.hank.musicfree.feature.home.HomeDrawerSectionUiModel
import com.hank.musicfree.feature.home.HomeDrawerUiModel

@Composable
fun HomeDrawerContent(
    uiModel: HomeDrawerUiModel,
    onEntryClick: (HomeDrawerAction) -> Unit,
    modifier: Modifier = Modifier,
    statusBarTopPadding: Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.8f)
            .widthIn(max = rpx(560))
            .background(MusicFreeTheme.colors.pageBackground)
            .testTag(FidelityAnchors.Home.DrawerRoot)
            .semantics { testTagsAsResourceId = true },
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(top = statusBarTopPadding + rpx(12), bottom = rpx(24)),
        ) {
            Text(
                text = context.applicationInfo.loadLabel(context.packageManager).toString(),
                modifier = Modifier
                    .padding(horizontal = rpx(24), vertical = rpx(28))
                    .testTag(FidelityAnchors.Home.DrawerTitle),
                color = MusicFreeTheme.colors.text,
                fontSize = FontSizes.appBar,
            )

            uiModel.sections.forEach { section ->
                DrawerSection(
                    section = section,
                    onEntryClick = onEntryClick,
                )
            }

            if (uiModel.footerActions.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(12)),
                    color = MusicFreeTheme.colors.divider,
                )

                uiModel.footerActions.forEach { item ->
                    DrawerRow(
                        item = item,
                        onClick = { onEntryClick(item.action) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerSection(
    section: HomeDrawerSectionUiModel,
    onEntryClick: (HomeDrawerAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = rpx(20))
            .testTag(FidelityAnchorPatterns.drawerSection(section.sectionKey)),
    ) {
        Text(
            text = section.title,
            modifier = Modifier
                .padding(horizontal = rpx(24), vertical = rpx(12))
                .semantics { heading() },
            color = MusicFreeTheme.colors.text,
            fontSize = FontSizes.subTitle,
        )
        section.items.forEach { item ->
            DrawerRow(
                item = item,
                onClick = { onEntryClick(item.action) },
            )
        }
    }
}

@Composable
private fun DrawerRow(
    item: HomeDrawerItemUiModel,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rpx(12), vertical = rpx(2))
            .homeInteractionStyle(onClick = onClick)
            .testTag(item.anchorTag)
            .padding(horizontal = rpx(12), vertical = rpx(18)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rpx(16)),
    ) {
        Icon(
            painter = painterResource(item.iconRes),
            contentDescription = null,
            tint = MusicFreeTheme.colors.text,
            modifier = Modifier.size(IconSizes.normal),
        )
        Text(
            text = item.title,
            color = MusicFreeTheme.colors.text,
            fontSize = FontSizes.subTitle,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (item.hasBadge) {
            UpdateBadgeDot()
        } else {
            item.trailingText?.takeIf { it.isNotBlank() }?.let { trailingText ->
                Text(
                    text = trailingText,
                    color = MusicFreeTheme.colors.textSecondary,
                    fontSize = FontSizes.subTitle,
                )
            }
        }
    }
}

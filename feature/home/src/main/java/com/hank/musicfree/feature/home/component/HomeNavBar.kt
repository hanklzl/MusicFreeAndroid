package com.hank.musicfree.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.IconSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.ui.FidelityAnchors

@Composable
fun HomeNavBar(
    searchPlaceholder: String,
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rpx(88))
            .testTag(FidelityAnchors.Home.NavBarRoot)
            .semantics { testTagsAsResourceId = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(start = rpx(24))
                .homeIconButtonInteractionStyle(
                    onClick = onOpenMenu,
                    shape = CircleShape,
                )
                .testTag(FidelityAnchors.Home.NavBarMenu),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(HomeIcons.NavMenu),
                contentDescription = "菜单",
                tint = MusicFreeTheme.colors.text,
                modifier = Modifier.size(IconSizes.normal),
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = rpx(24))
                .height(rpx(64))
                .homeInteractionStyle(
                    onClick = onOpenSearch,
                    shape = RoundedCornerShape(999.dp),
                    minHeight = null,
                )
                .background(
                    color = MusicFreeTheme.colors.placeholder,
                    shape = RoundedCornerShape(999.dp),
                )
                .testTag(FidelityAnchors.Home.NavBarSearch)
                .padding(horizontal = rpx(18)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(HomeIcons.NavSearch),
                contentDescription = null,
                tint = MusicFreeTheme.colors.textSecondary,
                modifier = Modifier.size(IconSizes.small),
            )
            Text(
                text = searchPlaceholder,
                modifier = Modifier.padding(start = rpx(12)),
                color = MusicFreeTheme.colors.textSecondary,
                fontSize = FontSizes.subTitle,
            )
        }
    }
}

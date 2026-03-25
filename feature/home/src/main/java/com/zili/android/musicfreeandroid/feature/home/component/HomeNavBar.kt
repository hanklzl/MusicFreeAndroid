package com.zili.android.musicfreeandroid.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.IconSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors

@Composable
fun HomeNavBar(
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rpx(88))
            .testTag(FidelityAnchors.Home.NavBarRoot)
            .semantics { testTagsAsResourceId = true }
            .padding(horizontal = rpx(24)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onOpenMenu,
            modifier = Modifier
                .size(rpx(40))
                .testTag(FidelityAnchors.Home.NavBarMenu),
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "菜单",
                tint = MusicFreeTheme.colors.text,
                modifier = Modifier.size(IconSizes.normal),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = rpx(24))
                .height(rpx(64))
                .background(
                    color = MusicFreeTheme.colors.placeholder,
                    shape = RoundedCornerShape(999.dp),
                )
                .clickable(onClick = onOpenSearch)
                .testTag(FidelityAnchors.Home.NavBarSearch)
                .padding(horizontal = rpx(20)),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MusicFreeTheme.colors.textSecondary,
                    modifier = Modifier.size(IconSizes.small),
                )
                Text(
                    text = "点击这里开始搜索",
                    modifier = Modifier.padding(start = rpx(12)),
                    color = MusicFreeTheme.colors.textSecondary,
                    fontSize = FontSizes.subTitle,
                )
            }
        }
    }
}

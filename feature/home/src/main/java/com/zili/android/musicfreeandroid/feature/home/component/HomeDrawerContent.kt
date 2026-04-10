package com.zili.android.musicfreeandroid.feature.home.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.feature.home.HomeDrawerAction
import com.zili.android.musicfreeandroid.feature.home.HomeDrawerUiModel

@Composable
fun HomeDrawerContent(
    uiModel: HomeDrawerUiModel,
    onEntryClick: (HomeDrawerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier
            .testTag(FidelityAnchors.Home.DrawerRoot)
            .semantics { testTagsAsResourceId = true },
        drawerContainerColor = MusicFreeTheme.colors.card,
        drawerContentColor = MusicFreeTheme.colors.text,
    ) {
        Text(
            text = uiModel.title,
            modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(32)),
            color = MusicFreeTheme.colors.text,
            fontSize = FontSizes.title,
        )
        uiModel.entries.forEach { entry ->
            NavigationDrawerItem(
                label = {
                    Text(
                        text = entry.title,
                        fontSize = FontSizes.subTitle,
                    )
                },
                selected = false,
                onClick = { onEntryClick(entry.action) },
                icon = {
                    androidx.compose.material3.Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                    )
                },
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = MusicFreeTheme.colors.card,
                    unselectedTextColor = MusicFreeTheme.colors.text,
                    unselectedIconColor = MusicFreeTheme.colors.text,
                ),
                modifier = Modifier
                    .padding(horizontal = rpx(12))
                    .testTag(entry.anchorTag),
            )
        }
    }
}

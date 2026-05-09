package com.zili.android.musicfreeandroid.feature.home.pluginfeature

import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme

@Composable
fun PluginCapabilityTabs(
    plugins: List<PluginCapabilityUiModel>,
    selectedPlatform: String?,
    onSelectPlugin: (String) -> Unit,
) {
    if (plugins.isEmpty()) {
        return
    }
    val selectedTabIndex = plugins.indexOfFirst { it.platform == selectedPlatform }
        .takeIf { it >= 0 }
        ?: 0
    ScrollableTabRow(
        selectedTabIndex = selectedTabIndex,
        containerColor = MusicFreeTheme.colors.background,
        contentColor = MusicFreeTheme.colors.primary,
        edgePadding = 12.dp,
    ) {
        plugins.forEachIndexed { index, plugin ->
            val selected = index == selectedTabIndex
            Tab(
                selected = selected,
                onClick = { onSelectPlugin(plugin.platform) },
                selectedContentColor = MusicFreeTheme.colors.primary,
                unselectedContentColor = MusicFreeTheme.colors.text,
                text = {
                    Text(
                        text = plugin.label,
                        fontSize = FontSizes.subTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

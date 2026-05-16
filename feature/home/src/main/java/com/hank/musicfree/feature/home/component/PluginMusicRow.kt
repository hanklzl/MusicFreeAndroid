package com.hank.musicfree.feature.home.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import com.hank.musicfree.core.R
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.ui.CoverImage
import com.hank.musicfree.core.ui.MusicItemAction
import com.hank.musicfree.core.ui.MusicItemMoreMenu
import com.hank.musicfree.core.ui.PlatformTag

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PluginMusicRow(
    index: Int,
    item: MusicItem,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAction: (MusicItemAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = rpx(24), vertical = rpx(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${index + 1}",
            color = MusicFreeTheme.colors.textSecondary,
            fontSize = FontSizes.description,
            modifier = Modifier.padding(end = rpx(12)),
        )
        CoverImage(
            uri = item.artwork,
            size = rpx(88),
            cornerRadius = rpx(8),
        )
        Column(
            modifier = Modifier
                .padding(start = rpx(18))
                .weight(1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val tagText = pluginPlatformTagText(item.platform)
                Text(
                    text = item.title,
                    color = MusicFreeTheme.colors.text,
                    fontSize = FontSizes.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (tagText != null) {
                    PlatformTag(
                        text = tagText,
                        modifier = Modifier.padding(start = rpx(12)),
                    )
                }
            }
            Text(
                text = pluginRowDescription(item),
                color = MusicFreeTheme.colors.textSecondary,
                fontSize = FontSizes.description,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MusicItemMoreMenu(
            actions = setOf(
                MusicItemAction.PlayNext,
                MusicItemAction.ToggleFavorite,
                MusicItemAction.AddToPlaylist,
            ),
            isFavorite = isFavorite,
            onAction = onAction,
            triggerIcon = painterResource(id = R.drawable.ic_ellipsis_vertical),
        )
    }
}

internal fun pluginPlatformTagText(platform: String): String? {
    val normalized = platform.trim()
    if (normalized.isBlank()) return null
    return if (normalized == "local") "本地" else normalized
}

internal fun pluginRowDescription(item: MusicItem): String =
    item.artist + if (!item.album.isNullOrBlank()) " - ${item.album}" else ""

package com.zili.android.musicfreeandroid.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.R
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme

@Composable
fun MusicItemRow(
    item: MusicItem,
    isFavorite: Boolean,
    actions: Set<MusicItemAction>,
    onClick: () -> Unit,
    onAction: (MusicItemAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .testTag("MusicItemRow_root")
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        CoverImage(uri = item.artwork, size = 40.dp, cornerRadius = 4.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                PlatformTag(
                    text = displayPlatform(item.platform),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = descriptionText(item),
                style = MaterialTheme.typography.bodySmall,
                color = MusicFreeTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MusicItemMoreMenu(
            actions = actions,
            isFavorite = isFavorite,
            onAction = onAction,
            triggerIcon = painterResource(id = R.drawable.ic_ellipsis_vertical),
        )
    }
}

private fun displayPlatform(platform: String): String =
    if (platform == "local") "本地" else platform

private fun descriptionText(item: MusicItem): String =
    item.artist + if (!item.album.isNullOrBlank()) " - ${item.album}" else ""

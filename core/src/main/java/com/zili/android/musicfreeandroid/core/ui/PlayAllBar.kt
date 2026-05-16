package com.zili.android.musicfreeandroid.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.zili.android.musicfreeandroid.core.R
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.IconSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx

private val StarredRed = Color(0xFFE31639)

@Composable
fun PlayAllBar(
    onPlayAll: () -> Unit,
    onAddToPlaylist: () -> Unit,
    starred: Boolean? = null,
    onToggleStarred: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    showAddToPlaylist: Boolean = true,
) {
    val colors = MusicFreeTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rpx(84))
            .padding(horizontal = rpx(24)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onPlayAll),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_play_circle),
                contentDescription = null,
                tint = colors.text,
                modifier = Modifier.size(IconSizes.normal),
            )
            Spacer(Modifier.width(rpx(12)))
            Text(
                text = "播放全部",
                color = colors.text,
                fontSize = FontSizes.content,
                fontWeight = FontWeight.Bold,
            )
        }
        if (starred != null) {
            IconButton(
                onClick = { onToggleStarred?.invoke() },
                modifier = Modifier.padding(start = rpx(36)),
            ) {
                Icon(
                    painter = painterResource(
                        id = if (starred) R.drawable.ic_heart else R.drawable.ic_heart_outline,
                    ),
                    contentDescription = if (starred) "已收藏" else "收藏",
                    tint = if (starred) StarredRed else colors.text,
                    modifier = Modifier.size(IconSizes.normal),
                )
            }
        }
        if (showAddToPlaylist) {
            IconButton(
                onClick = onAddToPlaylist,
                modifier = Modifier.padding(start = rpx(36)),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_folder_plus),
                    contentDescription = "添加到歌单",
                    tint = colors.text,
                    modifier = Modifier.size(IconSizes.normal),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlayAllBarPreview() {
    MusicFreeTheme {
        PlayAllBar(
            onPlayAll = {},
            onAddToPlaylist = {},
            starred = true,
            onToggleStarred = {},
        )
    }
}

package com.hank.musicfree.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.hank.musicfree.core.theme.FontSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx

@Composable
fun MusicSheetPageHeader(
    cover: String?,
    title: String?,
    worksNum: Int?,
    musicListSize: Int,
    description: String?,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MusicFreeTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.card),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rpx(24)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverImage(
                    uri = cover,
                    size = rpx(210),
                    cornerRadius = rpx(24),
                )
                Column(
                    modifier = Modifier
                        .padding(start = rpx(36))
                        .height(rpx(140)),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = title.orEmpty(),
                        color = colors.text,
                        fontSize = FontSizes.content,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val countText = when {
                        worksNum != null -> "共${worksNum}首"
                        musicListSize > 0 -> "共${musicListSize}首"
                        else -> "共 - 首"
                    }
                    Text(
                        text = countText,
                        color = colors.textSecondary,
                        fontSize = FontSizes.description,
                    )
                }
            }
            if (!description.isNullOrBlank()) {
                var expanded by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = rpx(28))
                        .clickable { expanded = !expanded },
                ) {
                    Text(
                        text = description,
                        color = colors.textSecondary,
                        fontSize = FontSizes.description,
                        maxLines = if (expanded) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        actions()
    }
}

@Preview(showBackground = true)
@Composable
private fun MusicSheetPageHeaderPreview() {
    MusicFreeTheme {
        MusicSheetPageHeader(
            cover = null,
            title = "KPOP 韩团超燃歌单 · 精选合集",
            worksNum = 40,
            musicListSize = 0,
            description = "收录了近年来 KPOP 各大韩团的高燃舞曲精选,适合健身和派对场景使用。",
            actions = {},
        )
    }
}

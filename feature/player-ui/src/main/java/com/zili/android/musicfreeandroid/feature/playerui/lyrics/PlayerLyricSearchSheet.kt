package com.zili.android.musicfreeandroid.feature.playerui.lyrics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerLyricSearchSheet(
    groups: List<LyricSearchGroup>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (MusicItem) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = "搜索歌词",
                color = MusicFreeTheme.colors.text,
                fontSize = FontSizes.title,
                modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(12)),
            )

            when {
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rpx(180)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                groups.isEmpty() -> {
                    Text(
                        text = "暂无歌词搜索结果",
                        color = MusicFreeTheme.colors.textSecondary,
                        fontSize = FontSizes.content,
                        modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(32)),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = rpx(24)),
                    ) {
                        groups.forEach { group ->
                            item(key = "header:${group.plugin.platform}") {
                                Text(
                                    text = group.plugin.platform,
                                    color = MusicFreeTheme.colors.textSecondary,
                                    fontSize = FontSizes.subTitle,
                                    modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(12)),
                                )
                            }

                            group.errorMessage?.let { message ->
                                item(key = "error:${group.plugin.platform}") {
                                    Text(
                                        text = message,
                                        color = MusicFreeTheme.colors.danger,
                                        fontSize = FontSizes.description,
                                        modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(8)),
                                    )
                                }
                            }

                            items(
                                items = group.items,
                                key = { item -> "${item.platform}:${item.id}" },
                            ) { item ->
                                ListItem(
                                    headlineContent = { Text(item.title) },
                                    supportingContent = {
                                        Text(listOf(item.artist, item.platform).filter { it.isNotBlank() }.joinToString(" · "))
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(item) },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(rpx(12)))
        }
    }
}

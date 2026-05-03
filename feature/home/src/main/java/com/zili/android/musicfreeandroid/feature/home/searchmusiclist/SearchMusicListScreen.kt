package com.zili.android.musicfreeandroid.feature.home.searchmusiclist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.ui.CoverImage
import com.zili.android.musicfreeandroid.core.ui.MusicFreeScreenScaffold

@Composable
fun SearchMusicListScreen(
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchMusicListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MusicFreeScreenScaffold(
        titleContent = {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::updateQuery,
                placeholder = {
                    Text(
                        text = "搜索音乐",
                        color = MusicFreeTheme.colors.textSecondary,
                        fontSize = FontSizes.content,
                    )
                },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MusicFreeTheme.colors.textSecondary,
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MusicFreeTheme.colors.text,
                    unfocusedTextColor = MusicFreeTheme.colors.text,
                    cursorColor = MusicFreeTheme.colors.primary,
                    focusedBorderColor = MusicFreeTheme.colors.primary,
                    unfocusedBorderColor = MusicFreeTheme.colors.divider,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        onBack = onBack,
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
            uiState.sourceItems.isEmpty() -> {
                SearchMusicListEmptyState("暂无可搜索歌曲")
            }

            uiState.filteredItems.isEmpty() -> {
                SearchMusicListEmptyState("暂无匹配结果")
            }

            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(
                        items = uiState.filteredItems,
                        key = { _, item -> "${item.platform}:${item.id}" },
                    ) { index, item ->
                        SearchMusicListItem(
                            item = item,
                            onClick = {
                                if (viewModel.playFilteredItem(index)) {
                                    onNavigateToPlayer()
                                }
                            },
                        )
                        if (index < uiState.filteredItems.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                color = MusicFreeTheme.colors.divider,
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun SearchMusicListItem(
    item: MusicItem,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoverImage(uri = item.artwork, size = 48.dp, cornerRadius = 4.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title.ifBlank { "未知歌曲" },
                color = MusicFreeTheme.colors.text,
                fontSize = FontSizes.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.artist.ifBlank { "未知歌手" },
                color = MusicFreeTheme.colors.textSecondary,
                fontSize = FontSizes.description,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
    }
}

@Composable
private fun SearchMusicListEmptyState(
    message: String,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MusicFreeTheme.colors.textSecondary,
            fontSize = FontSizes.content,
        )
    }
}

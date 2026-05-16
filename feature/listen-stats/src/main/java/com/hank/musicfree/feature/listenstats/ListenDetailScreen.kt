package com.hank.musicfree.feature.listenstats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
import com.hank.musicfree.data.repository.listenstats.model.DetailMode
import com.hank.musicfree.feature.listenstats.component.SongDetailRow
import com.hank.musicfree.feature.listenstats.component.TimeScopePager
import com.hank.musicfree.feature.listenstats.component.TimeScopeSegmented

@Composable
fun ListenDetailScreen(
    onBack: () -> Unit,
    viewModel: ListenDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MusicFreeScreenScaffold(
        title = state.titleByMode,
        onBack = onBack,
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(state.summary, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            TimeScopeSegmented(current = state.scope, onChange = viewModel::onScopeChange)
            Spacer(Modifier.height(8.dp))
            TimeScopePager(
                label = state.windowLabel,
                onPrev = { viewModel.onAnchorChange(state.anchor.minusDays(1)) },
                onNext = { viewModel.onAnchorChange(state.anchor.plusDays(1)) },
            )
            Spacer(Modifier.height(8.dp))
            SortChips(current = state.sort, onChange = viewModel::onSortChange)
            Spacer(Modifier.height(8.dp))
            if (state.items.isEmpty()) {
                EmptyHint(text = emptyTextFor(state.mode))
            } else {
                LazyColumn {
                    items(state.items, key = { "${it.platform}/${it.musicId}" }) { song ->
                        SongDetailRow(song = song, showFirstSeen = state.mode == DetailMode.FIRST_SEEN)
                    }
                }
            }
        }
    }
}

@Composable
private fun SortChips(current: DetailSort, onChange: (DetailSort) -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = current == DetailSort.PLAY_COUNT_DESC,
            onClick = { onChange(DetailSort.PLAY_COUNT_DESC) },
            label = { Text("按播放次数") },
        )
        FilterChip(
            selected = current == DetailSort.TOTAL_SEC_DESC,
            onClick = { onChange(DetailSort.TOTAL_SEC_DESC) },
            label = { Text("按时长") },
        )
        FilterChip(
            selected = current == DetailSort.FIRST_SEEN_DESC,
            onClick = { onChange(DetailSort.FIRST_SEEN_DESC) },
            label = { Text("按首次") },
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
}

private fun emptyTextFor(m: DetailMode): String = when (m) {
    DetailMode.FIRST_SEEN -> "本时段还没有首次听到的歌"
    DetailMode.BY_ARTIST -> "这位歌手在本时段还没出现"
    DetailMode.BY_LANGUAGE -> "本时段没有这个语言的歌"
    DetailMode.BY_GENRE -> "本时段没有这个风格的歌"
    else -> "本时段还没有听过任何歌"
}

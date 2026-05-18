package com.hank.musicfree.core.ui

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag

enum class MusicItemAction { PlayNext, ToggleFavorite, AddToPlaylist, RemoveFromPlaylist }

@Composable
fun MusicItemMoreMenu(
    actions: Set<MusicItemAction>,
    isFavorite: Boolean,
    onAction: (MusicItemAction) -> Unit,
    triggerIcon: Painter,
    modifier: Modifier = Modifier,
    contentDescription: String = "更多",
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = { expanded = true },
        modifier = modifier.testTag("MusicItemMoreMenu_trigger"),
    ) {
        Icon(painter = triggerIcon, contentDescription = contentDescription)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        if (MusicItemAction.PlayNext in actions) {
            DropdownMenuItem(
                text = { Text("下一首播放") },
                onClick = { expanded = false; onAction(MusicItemAction.PlayNext) },
                modifier = Modifier.testTag("MusicItemMoreMenu_PlayNext"),
            )
        }
        if (MusicItemAction.ToggleFavorite in actions) {
            DropdownMenuItem(
                text = { Text(if (isFavorite) "取消收藏" else "收藏") },
                onClick = { expanded = false; onAction(MusicItemAction.ToggleFavorite) },
                modifier = Modifier.testTag("MusicItemMoreMenu_ToggleFavorite"),
            )
        }
        if (MusicItemAction.AddToPlaylist in actions) {
            DropdownMenuItem(
                text = { Text("加入歌单") },
                onClick = { expanded = false; onAction(MusicItemAction.AddToPlaylist) },
                modifier = Modifier.testTag("MusicItemMoreMenu_AddToPlaylist"),
            )
        }
        if (MusicItemAction.RemoveFromPlaylist in actions) {
            DropdownMenuItem(
                text = { Text("从歌单移除") },
                onClick = { expanded = false; onAction(MusicItemAction.RemoveFromPlaylist) },
                modifier = Modifier.testTag("MusicItemMoreMenu_RemoveFromPlaylist"),
            )
        }
    }
}

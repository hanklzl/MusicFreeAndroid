package com.zili.android.musicfreeandroid.feature.playerui.lyrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import com.zili.android.musicfreeandroid.core.R
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx

@Composable
fun PlayerLyricsOperations(
    state: PlayerLyricsUiState,
    onFontSize: () -> Unit,
    onOffset: () -> Unit,
    onSearch: () -> Unit,
    onToggleTranslation: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val translationEnabledColor = if (state.hasTranslation) {
        if (state.showTranslation) MusicFreeTheme.colors.primary else Color.White
    } else {
        Color.White.copy(alpha = 0.35f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rpx(80))
            .padding(horizontal = rpx(48)),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onFontSize,
            modifier = Modifier.semantics { contentDescription = "调整歌词字号" },
        ) {
            Text(
                text = "A",
                color = Color.White,
                fontSize = FontSizes.description,
            )
        }

        IconButton(
            onClick = onOffset,
            modifier = Modifier.semantics { contentDescription = "调整歌词进度" },
        ) {
            Text(
                text = "↔",
                color = Color.White,
                fontSize = FontSizes.description,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }

        IconButton(onClick = onSearch) {
            Icon(
                painter = painterResource(id = R.drawable.ic_magnifying_glass),
                contentDescription = "搜索歌词",
                tint = Color.White,
            )
        }

        IconButton(
            onClick = onToggleTranslation,
            enabled = state.hasTranslation,
            modifier = Modifier.semantics { contentDescription = "切换歌词翻译" },
        ) {
            Text(
                text = "译",
                color = translationEnabledColor,
                fontSize = FontSizes.description,
            )
        }

        IconButton(onClick = onMore) {
            Icon(
                painter = painterResource(id = R.drawable.ic_ellipsis_vertical),
                contentDescription = "更多",
                tint = Color.White,
            )
        }
    }
}

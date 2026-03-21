package com.zili.android.musicfreeandroid.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme

@Composable
fun CoverImage(
    uri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    cornerRadius: Dp = 4.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    var hasLoadError by remember(uri) { mutableStateOf(false) }

    if (uri.isNullOrBlank() || hasLoadError) {
        CoverPlaceholder(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MusicFreeTheme.colors.placeholder),
            iconSize = size * 0.5f,
        )
    } else {
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(shape),
            contentScale = ContentScale.Crop,
            onError = { hasLoadError = true },
        )
    }
}

@Composable
private fun CoverPlaceholder(
    modifier: Modifier,
    iconSize: Dp,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MusicFreeTheme.colors.textSecondary,
            modifier = Modifier.size(iconSize),
        )
    }
}

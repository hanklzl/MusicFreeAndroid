package com.zili.android.musicfreeandroid

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zili.android.musicfreeandroid.core.theme.runtime.BackgroundInfo

/**
 * App-level background drawn beneath all chrome when a custom theme background is configured.
 *
 * Modifier.blur is a no-op on API 29-30 (minSdk=29) and only takes effect from API 31+;
 * the call is still safe and we accept "no blur" as a graceful fallback on older devices.
 */
@Composable
fun ThemeBackgroundLayer(info: BackgroundInfo?, modifier: Modifier = Modifier) {
    val url = info?.url ?: return
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxSize()
            .blur(info.blur.dp)
            .graphicsLayer { alpha = info.opacity },
    )
}

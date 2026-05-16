package com.zili.android.musicfreeandroid.feature.settings.themesetting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx

@Composable
fun ThemeCard(
    selected: Boolean,
    title: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    previewColor: Color? = null,
    previewImageUrl: String? = null,
) {
    val outerShape = RoundedCornerShape(rpx(22))
    val innerShape = RoundedCornerShape(rpx(12))
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(rpx(160))
                .clip(outerShape)
                .then(
                    if (selected) Modifier.border(2.dp, MusicFreeTheme.colors.primary, outerShape)
                    else Modifier,
                )
                .clickable(onClick = onClick)
                .testTag(testTag),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(rpx(136))
                    .clip(innerShape)
                    // 优先 previewImageUrl；否则 previewColor；都没有时用 LightGray 占位。
                    .background(previewColor ?: Color.LightGray),
            ) {
                if (previewImageUrl != null) {
                    AsyncImage(
                        model = previewImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(rpx(12)))
        Text(
            text = title,
            fontSize = FontSizes.subTitle,
            color = if (selected) MusicFreeTheme.colors.primary else MusicFreeTheme.colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(rpx(160)),
        )
    }
}

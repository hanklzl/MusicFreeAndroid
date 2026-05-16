package com.hank.musicfree.feature.playerui.lyrics

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.hank.musicfree.core.R
import com.hank.musicfree.core.theme.IconSizes
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx

internal const val PlayerLyricsOperationsBarTestTag = "player.lyrics.operations.bar"
internal const val PlayerLyricsOperationSlotTestTag = "player.lyrics.operations.slot"
internal const val PlayerLyricsOperationIconVisualTestTag = "player.lyrics.operations.iconVisual"

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
            .testTag(PlayerLyricsOperationsBarTestTag),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LyricOperationSlot(
            onClick = onFontSize,
            contentDescription = "调整歌词字号",
        ) {
            LyricOperationIcon(
                icon = R.drawable.ic_font_size,
                tint = Color.White,
            )
        }

        LyricOperationSlot(
            onClick = onOffset,
            contentDescription = "调整歌词进度",
        ) {
            LyricOperationIcon(
                icon = R.drawable.ic_arrows_left_right,
                tint = Color.White,
            )
        }

        LyricOperationSlot(
            onClick = onSearch,
            contentDescription = "搜索歌词",
        ) {
            LyricOperationIcon(
                icon = R.drawable.ic_magnifying_glass,
                tint = Color.White,
            )
        }

        LyricOperationSlot(
            onClick = onToggleTranslation,
            enabled = state.hasTranslation,
            contentDescription = "切换歌词翻译",
        ) {
            LyricOperationIcon(
                icon = R.drawable.ic_translation,
                tint = translationEnabledColor,
            )
        }

        LyricOperationSlot(
            onClick = onMore,
            contentDescription = "歌词更多",
        ) {
            LyricOperationIcon(
                icon = R.drawable.ic_ellipsis_vertical,
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun LyricOperationSlot(
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(rpx(64))
            .testTag(PlayerLyricsOperationSlotTestTag)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun LyricOperationIcon(
    @DrawableRes icon: Int,
    tint: Color,
) {
    Icon(
        painter = painterResource(id = icon),
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(IconSizes.normal)
            .testTag(PlayerLyricsOperationIconVisualTestTag),
    )
}

package com.hank.musicfree.feature.playerui.component.quality

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.QualityInfo
import com.hank.musicfree.core.model.fullLabel
import com.hank.musicfree.core.theme.IconSizes
import com.hank.musicfree.core.theme.rpx

enum class MusicQualitySheetMode { Play, Download }

private val DISPLAY_ORDER: List<PlayQuality> = listOf(
    PlayQuality.LOW, PlayQuality.STANDARD, PlayQuality.HIGH, PlayQuality.SUPER,
)

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    return when {
        kb >= 1024.0 -> "%.1f MB".format(kb / 1024.0)
        kb >= 1.0 -> "%.0f KB".format(kb)
        else -> "$bytes B"
    }
}

const val MusicQualitySheetTestTag = "player.quality.sheet"
const val MusicQualitySheetItemTestTagPrefix = "player.quality.sheet.item."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicQualitySheet(
    current: PlayQuality?,
    mode: MusicQualitySheetMode,
    availableQualities: Map<PlayQuality, QualityInfo>?,
    onSelect: (PlayQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(MusicQualitySheetTestTag),
    ) {
        MusicQualitySheetContent(
            current = current,
            mode = mode,
            availableQualities = availableQualities,
            onSelect = onSelect,
        )
    }
}

@Composable
internal fun MusicQualitySheetContent(
    current: PlayQuality?,
    mode: MusicQualitySheetMode,
    availableQualities: Map<PlayQuality, QualityInfo>?,
    onSelect: (PlayQuality) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rpx(24), vertical = rpx(16)),
    ) {
        val title = when (mode) {
            MusicQualitySheetMode.Play -> "选择播放音质"
            MusicQualitySheetMode.Download -> "选择下载音质"
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = rpx(8)),
        )
        DISPLAY_ORDER.forEach { quality ->
            val sizeText = availableQualities?.get(quality)?.size?.let { " (${formatSize(it)})" }.orEmpty()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rpx(96))
                    .clickable { onSelect(quality) }
                    .testTag(MusicQualitySheetItemTestTagPrefix + quality.name),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = quality.fullLabel() + sizeText,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (current == quality) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "已选中",
                        modifier = Modifier.size(IconSizes.normal),
                    )
                } else {
                    Box(modifier = Modifier.size(IconSizes.normal))
                }
            }
        }
    }
}

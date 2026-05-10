package com.zili.android.musicfreeandroid.feature.playerui.component.rate

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
import com.zili.android.musicfreeandroid.core.model.PlaybackSpeeds
import com.zili.android.musicfreeandroid.core.theme.IconSizes
import com.zili.android.musicfreeandroid.core.theme.rpx

const val PlayRateSheetTestTag = "player.rate.sheet"
const val PlayRateSheetItemTestTagPrefix = "player.rate.sheet.item."

private fun rateLabel(rate: Float): String = if (rate == rate.toInt().toFloat()) {
    "${rate.toInt()}.0x"
} else {
    "${"%.2f".format(rate).trimEnd('0').trimEnd('.')}x"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayRateSheet(
    current: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(PlayRateSheetTestTag),
    ) {
        PlayRateSheetContent(
            current = current,
            onSelect = onSelect,
        )
    }
}

@Composable
internal fun PlayRateSheetContent(
    current: Float,
    onSelect: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rpx(24), vertical = rpx(16)),
    ) {
        Text(
            text = "选择播放速度",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = rpx(8)),
        )
        PlaybackSpeeds.ALL.forEach { rate ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rpx(96))
                    .clickable { onSelect(rate) }
                    .testTag(PlayRateSheetItemTestTagPrefix + rate.toString()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = rateLabel(rate),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (current == rate) {
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

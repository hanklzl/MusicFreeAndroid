package com.zili.android.musicfreeandroid.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.UpdateBadgeDot
import com.zili.android.musicfreeandroid.updater.checker.UpdateState

@Composable
fun CheckUpdateRow(viewModel: CheckUpdateViewModel = hiltViewModel()) {
    val state by viewModel.checker.state.collectAsState()
    val hasRedDot = state.hasUnreadAvailableUpdate
    val trailingText = when (state) {
        is UpdateState.Available -> "v${(state as UpdateState.Available).update.info.version} 可用"
        is UpdateState.Checking -> "检查中…"
        is UpdateState.UpToDate -> "已是最新版本"
        is UpdateState.Failed -> "检查失败，点击重试"
        else -> "前往检查"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rpx(96))
            .clickable { viewModel.checkNow() }
            .padding(horizontal = rpx(24)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "检查更新",
            fontSize = FontSizes.content,
            color = MusicFreeTheme.colors.text,
            modifier = Modifier.weight(1f),
        )
        if (hasRedDot) {
            UpdateBadgeDot()
        } else {
            Text(
                text = trailingText,
                fontSize = FontSizes.description,
                color = MusicFreeTheme.colors.textSecondary,
            )
        }
    }
}

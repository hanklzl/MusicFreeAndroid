package com.hank.musicfree.feature.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.ui.loggedClick

@Composable
fun TodayRecommendationHomeCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = rpx(24), vertical = rpx(12))
            .semantics { role = Role.Button }
            .loggedClick(
                targetId = "home.today_recommendation.entry",
                screen = "home",
                targetLabel = "今日推荐",
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.large,
        color = MusicFreeTheme.colors.card,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = rpx(28), vertical = rpx(24)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rpx(20)),
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MusicFreeTheme.colors.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "今日推荐",
                    style = MaterialTheme.typography.titleMedium,
                    color = MusicFreeTheme.colors.text,
                )
                Text(
                    text = "根据最近听歌偏好，每天为你整理",
                    style = MaterialTheme.typography.bodySmall,
                    color = MusicFreeTheme.colors.textSecondary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MusicFreeTheme.colors.textSecondary,
            )
        }
    }
}

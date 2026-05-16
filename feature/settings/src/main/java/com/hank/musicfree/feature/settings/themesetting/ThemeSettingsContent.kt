package com.hank.musicfree.feature.settings.themesetting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.theme.runtime.SelectedTheme
import com.hank.musicfree.core.theme.runtime.ThemeUiState
import com.hank.musicfree.core.ui.FidelityAnchors
import com.hank.musicfree.feature.settings.components.SettingSectionCard
import com.hank.musicfree.feature.settings.components.SettingSwitchRow

@Composable
fun ThemeSettingsContent(
    state: ThemeUiState,
    onFollowSystemToggle: (Boolean) -> Unit,
    onSelectLight: () -> Unit,
    onSelectDark: () -> Unit,
    onSelectCustom: () -> Unit,
    onNavigateToSetCustomTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Settings.ThemeRoot)
            .padding(horizontal = rpx(24)),
        verticalArrangement = Arrangement.spacedBy(rpx(16)),
    ) {
        item { Spacer(modifier = Modifier.height(rpx(8))) }
        item {
            SettingSectionCard(
                title = "显示样式",
                testTag = FidelityAnchors.Settings.ThemeSectionMode,
            ) {
                SettingSwitchRow(
                    title = "跟随系统主题",
                    checked = state.followSystem,
                    enabled = true,
                    testTag = FidelityAnchors.Settings.ThemeFollowSystemSwitch,
                    onCheckedChange = onFollowSystemToggle,
                )
            }
        }
        item {
            SettingSectionCard(
                title = "主题",
                testTag = FidelityAnchors.Settings.ThemeSectionTheme,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rpx(24), vertical = rpx(16)),
                    horizontalArrangement = Arrangement.spacedBy(rpx(24)),
                ) {
                    ThemeCard(
                        selected = state.selected == SelectedTheme.P_LIGHT,
                        title = "亮色",
                        previewColor = Color.White,
                        testTag = FidelityAnchors.Settings.ThemeCardLight,
                        onClick = onSelectLight,
                    )
                    ThemeCard(
                        selected = state.selected == SelectedTheme.P_DARK,
                        title = "暗色",
                        previewColor = Color(0xFF131313),
                        testTag = FidelityAnchors.Settings.ThemeCardDark,
                        onClick = onSelectDark,
                    )
                    ThemeCard(
                        selected = state.selected == SelectedTheme.CUSTOM,
                        title = "自定义",
                        previewImageUrl = state.background?.url,
                        testTag = FidelityAnchors.Settings.ThemeCardCustom,
                        onClick = {
                            // 未选中时先切到 CUSTOM 再进入子页，避免子页打开时 selected 仍是
                            // 旧值，导致背景层和颜色覆写不一致。
                            if (state.selected != SelectedTheme.CUSTOM) onSelectCustom()
                            onNavigateToSetCustomTheme()
                        },
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(rpx(8))) }
    }
}

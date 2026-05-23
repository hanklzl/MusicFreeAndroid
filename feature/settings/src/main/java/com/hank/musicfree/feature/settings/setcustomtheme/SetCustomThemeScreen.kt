package com.hank.musicfree.feature.settings.setcustomtheme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.ui.FidelityAnchors
import com.hank.musicfree.core.ui.MusicFreeScreenScaffold
import com.hank.musicfree.core.ui.logUiClick

@Composable
fun SetCustomThemeScreen(
    onBack: () -> Unit,
    viewModel: SetCustomThemeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activeKey by viewModel.activeColorKey.collectAsStateWithLifecycle()

    MusicFreeScreenScaffold(
        title = "自定义主题",
        onBack = onBack,
        actions = {
            TextButton(onClick = {
                logUiClick("settings.custom_theme.done", "set_custom_theme", "完成")
                onBack()
            }) {
                Text(text = "完成", color = MusicFreeTheme.colors.appBarText)
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag(FidelityAnchors.Screen.SetCustomThemeRoot)
            .semantics { testTagsAsResourceId = true },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
        ) {
            BackgroundPickerSection(
                currentUrl = state.background?.url,
                onImagePicked = viewModel::onImagePicked,
            )
            BlurOpacitySliders(
                blur = state.background?.blur ?: 20f,
                opacity = state.background?.opacity ?: 0.7f,
                onBlurChange = viewModel::onBlurChanged,
                onOpacityChange = viewModel::onOpacityChanged,
            )
            ConfigurableColorGrid(
                colors = state.effectiveColors,
                onColorClicked = viewModel::openColorPicker,
            )
            Spacer(modifier = Modifier.height(rpx(48)))
        }
    }

    val key = activeKey
    if (key != null) {
        ColorPickerBottomSheet(
            initialColor = state.effectiveColors.byKey(key),
            onDismiss = viewModel::dismissColorPicker,
            onConfirm = { hex -> viewModel.onColorConfirmed(key, hex) },
        )
    }
}

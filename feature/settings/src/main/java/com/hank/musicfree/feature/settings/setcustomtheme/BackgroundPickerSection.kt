package com.hank.musicfree.feature.settings.setcustomtheme

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImage
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.theme.rpx
import com.hank.musicfree.core.ui.FidelityAnchors
import com.hank.musicfree.core.ui.logUiClick

@Composable
fun BackgroundPickerSection(
    currentUrl: String?,
    onImagePicked: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onImagePicked(uri)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = rpx(36)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = rpx(460), height = rpx(690))
                .clip(RoundedCornerShape(rpx(12)))
                .background(MusicFreeTheme.colors.placeholder)
                .clickable {
                    logUiClick("settings.custom_theme.pick_background", "set_custom_theme", "选择背景图")
                    launcher.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        )
                    )
                }
                .testTag(FidelityAnchors.SetCustomTheme.Image),
            contentAlignment = Alignment.Center,
        ) {
            if (currentUrl != null) {
                AsyncImage(
                    model = currentUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "选择背景图",
                    tint = MusicFreeTheme.colors.textSecondary,
                    modifier = Modifier.size(rpx(96)),
                )
            }
        }
    }
}

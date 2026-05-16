package com.zili.android.musicfreeandroid.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.window.Dialog
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.updater.checker.UpdateChecker
import com.zili.android.musicfreeandroid.updater.downloader.ApkDownloader
import com.zili.android.musicfreeandroid.updater.installer.ApkInstaller
import com.zili.android.musicfreeandroid.updater.ui.ManualUpdateDialog

@Composable
fun HomeDrawerDialogs(
    isTimingCloseVisible: Boolean,
    isUpdateCheckVisible: Boolean,
    currentVersion: String,
    scheduleCloseSummary: String,
    checker: UpdateChecker,
    downloader: ApkDownloader,
    installer: ApkInstaller,
    onDismissTimingClose: () -> Unit,
    onDismissUpdateCheck: () -> Unit,
) {
    if (isTimingCloseVisible) {
        TimingClosePanel(
            scheduleCloseSummary = scheduleCloseSummary,
            onDismiss = onDismissTimingClose,
        )
    }

    if (isUpdateCheckVisible) {
        Box(
            modifier = Modifier
                .testTag(FidelityAnchors.Dialog.UpdateCheckRoot)
                .semantics { testTagsAsResourceId = true },
        ) {
            ManualUpdateDialog(
                checker = checker,
                downloader = downloader,
                installer = installer,
                localVersionName = currentVersion,
                onDismiss = onDismissUpdateCheck,
            )
        }
    }
}

@Composable
private fun TimingClosePanel(
    scheduleCloseSummary: String,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MusicFreeTheme.colors.mask),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rpx(20), vertical = rpx(24))
                    .testTag(FidelityAnchors.Panel.TimingCloseRoot)
                    .semantics { testTagsAsResourceId = true },
                shape = RoundedCornerShape(rpx(24)),
                color = MusicFreeTheme.colors.pageBackground,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(20)),
                    verticalArrangement = Arrangement.spacedBy(rpx(16)),
                ) {
                    Text(
                        text = "定时关闭",
                        color = MusicFreeTheme.colors.text,
                        fontSize = FontSizes.title,
                    )
                    Text(
                        text = scheduleCloseSummary.ifBlank { "暂未设置倒计时" },
                        color = MusicFreeTheme.colors.textSecondary,
                        fontSize = FontSizes.subTitle,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("关闭")
                        }
                    }
                }
            }
        }
    }
}

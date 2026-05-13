package com.zili.android.musicfreeandroid.feature.playerui.component.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.IconSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx

internal const val PlayerMoreOptionsSheetContentTestTag = "player.more.sheet.content"
internal const val PlayerMoreOptionsRowTestTag = "player.more.sheet.row"
internal const val PlayerMoreOptionsHeaderTestTag = "player.more.sheet.header"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerMoreOptionsSheet(
    item: MusicItem,
    desktopLyricEnabled: Boolean,
    onDismiss: () -> Unit,
    onToggleDesktopLyric: () -> Unit,
    onImportRawLyric: () -> Unit,
    onImportTranslatedLyric: () -> Unit,
    onDeleteLocalLyric: () -> Unit,
    onInfoCopied: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        shape = RoundedCornerShape(topStart = rpx(28), topEnd = rpx(28)),
        containerColor = MusicFreeTheme.colors.backdrop,
    ) {
        PlayerMoreOptionsSheetContent(
            item = item,
            desktopLyricEnabled = desktopLyricEnabled,
            onToggleDesktopLyric = onToggleDesktopLyric,
            onImportRawLyric = onImportRawLyric,
            onImportTranslatedLyric = onImportTranslatedLyric,
            onDeleteLocalLyric = onDeleteLocalLyric,
            onInfoCopied = onInfoCopied,
        )
    }
}

@Composable
internal fun PlayerMoreOptionsSheetContent(
    item: MusicItem,
    desktopLyricEnabled: Boolean,
    onToggleDesktopLyric: () -> Unit,
    onImportRawLyric: () -> Unit,
    onImportTranslatedLyric: () -> Unit,
    onDeleteLocalLyric: () -> Unit,
    modifier: Modifier = Modifier,
    onInfoCopied: () -> Unit = {},
) {
    val clipboardManager = LocalClipboardManager.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = rpx(30))
            .testTag(PlayerMoreOptionsSheetContentTestTag),
    ) {
        PlayerMoreOptionsHeader(item = item)
        HorizontalDivider(color = MusicFreeTheme.colors.divider)
        Column(modifier = Modifier.padding(top = rpx(12))) {
            PlayerMoreOptionsRow(
                text = "ID: ${item.platform}@${item.id}",
                icon = Icons.Outlined.Badge,
                onClick = {
                    clipboardManager.setText(AnnotatedString(item.mediaKeyJson()))
                    onInfoCopied()
                },
            )
            PlayerMoreOptionsRow(
                text = "作者: ${item.artist}",
                icon = Icons.Outlined.Person,
                onClick = {
                    clipboardManager.setText(AnnotatedString(item.artist))
                    onInfoCopied()
                },
            )
            val album = item.album
            if (!album.isNullOrBlank()) {
                PlayerMoreOptionsRow(
                    text = "专辑: $album",
                    icon = Icons.Outlined.Album,
                    onClick = {
                        clipboardManager.setText(AnnotatedString(album))
                        onInfoCopied()
                    },
                )
            }
            PlayerMoreOptionsRow(
                text = if (desktopLyricEnabled) "关闭桌面歌词" else "开启桌面歌词",
                icon = Icons.Outlined.Lyrics,
                onClick = onToggleDesktopLyric,
            )
            PlayerMoreOptionsRow(
                text = "上传本地歌词",
                icon = Icons.Outlined.FileUpload,
                onClick = onImportRawLyric,
            )
            PlayerMoreOptionsRow(
                text = "上传本地歌词翻译",
                icon = Icons.Outlined.FileUpload,
                onClick = onImportTranslatedLyric,
            )
            PlayerMoreOptionsRow(
                text = "删除本地歌词",
                icon = Icons.Outlined.DeleteOutline,
                onClick = onDeleteLocalLyric,
            )
        }
    }
}

@Composable
private fun PlayerMoreOptionsHeader(item: MusicItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rpx(200))
            .padding(rpx(24))
            .testTag(PlayerMoreOptionsHeaderTestTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val coverShape = RoundedCornerShape(rpx(16))
        if (item.artwork.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(rpx(140))
                    .clip(coverShape)
                    .background(MusicFreeTheme.colors.placeholder),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MusicFreeTheme.colors.textSecondary,
                    modifier = Modifier.size(IconSizes.big),
                )
            }
        } else {
            AsyncImage(
                model = item.artwork,
                contentDescription = null,
                modifier = Modifier
                    .size(rpx(140))
                    .clip(coverShape),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.width(rpx(36)))
        Column(
            modifier = Modifier
                .weight(1f)
                .height(rpx(140)),
            verticalArrangement = Arrangement.SpaceAround,
        ) {
            Text(
                text = item.title,
                color = MusicFreeTheme.colors.text,
                fontSize = FontSizes.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitleText(),
                color = MusicFreeTheme.colors.textSecondary,
                fontSize = FontSizes.description,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlayerMoreOptionsRow(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rpx(96))
            .clickable(onClick = onClick)
            .padding(horizontal = rpx(24))
            .testTag(PlayerMoreOptionsRowTestTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MusicFreeTheme.colors.text,
            modifier = Modifier
                .width(rpx(48))
                .size(IconSizes.light),
        )
        Spacer(Modifier.width(rpx(24)))
        Text(
            text = text,
            color = MusicFreeTheme.colors.text,
            fontSize = FontSizes.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun MusicItem.subtitleText(): String =
    if (album.isNullOrBlank()) artist else "$artist - $album"

private fun MusicItem.mediaKeyJson(): String =
    """{"platform":"$platform","id":"$id"}"""

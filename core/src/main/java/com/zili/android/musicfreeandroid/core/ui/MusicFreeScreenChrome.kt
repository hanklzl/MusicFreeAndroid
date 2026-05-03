package com.zili.android.musicfreeandroid.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.IconSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx

@Composable
fun MusicFreeStatusBarChrome(
    modifier: Modifier = Modifier,
    color: Color = MusicFreeTheme.colors.appBar,
) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(color),
    )
}

@Composable
fun MusicFreeTopAppBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    MusicFreeTopAppBar(
        titleContent = {
            Text(
                text = title,
                color = MusicFreeTheme.colors.appBarText,
                fontSize = FontSizes.appBar,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onBack = onBack,
        modifier = modifier,
        actions = actions,
    )
}

@Composable
fun MusicFreeTopAppBar(
    titleContent: @Composable RowScope.() -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = MusicFreeTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.appBar),
    ) {
        MusicFreeStatusBarChrome(color = colors.appBar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rpx(88))
                .padding(horizontal = rpx(24)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = colors.appBarText,
                    modifier = Modifier.size(IconSizes.normal),
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = rpx(24)),
                verticalAlignment = Alignment.CenterVertically,
                content = titleContent,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@Composable
fun MusicFreeScreenScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    containerColor: Color = MusicFreeTheme.colors.pageBackground,
    content: @Composable (PaddingValues) -> Unit,
) {
    MusicFreeScreenScaffold(
        titleContent = {
            Text(
                text = title,
                color = MusicFreeTheme.colors.appBarText,
                fontSize = FontSizes.appBar,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onBack = onBack,
        modifier = modifier,
        actions = actions,
        floatingActionButton = floatingActionButton,
        containerColor = containerColor,
        content = content,
    )
}

@Composable
fun MusicFreeScreenScaffold(
    titleContent: @Composable RowScope.() -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    containerColor: Color = MusicFreeTheme.colors.pageBackground,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MusicFreeTopAppBar(
                titleContent = titleContent,
                onBack = onBack,
                actions = actions,
            )
        },
        floatingActionButton = floatingActionButton,
        containerColor = containerColor,
        contentWindowInsets = WindowInsets(0.dp),
        content = content,
    )
}

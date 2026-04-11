package com.zili.android.musicfreeandroid.feature.home.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.zili.android.musicfreeandroid.core.theme.FontSizes
import com.zili.android.musicfreeandroid.core.theme.IconSizes
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import com.zili.android.musicfreeandroid.core.theme.rpx
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import com.zili.android.musicfreeandroid.feature.home.HomeOperationAction
import com.zili.android.musicfreeandroid.feature.home.HomeOperationUiModel

@Composable
fun HomeOperations(
    operations: List<HomeOperationUiModel>,
    onRecommendClick: () -> Unit,
    onTopListClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLocalMusicClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(FidelityAnchors.Home.OperationsRoot)
            .semantics { testTagsAsResourceId = true }
            .padding(horizontal = rpx(24), vertical = rpx(24)),
        horizontalArrangement = Arrangement.spacedBy(rpx(16)),
    ) {
        operations.forEach { operation ->
            val chrome = operationChrome(
                action = operation.action,
                onRecommendClick = onRecommendClick,
                onTopListClick = onTopListClick,
                onHistoryClick = onHistoryClick,
                onLocalMusicClick = onLocalMusicClick,
            )
            OperationCard(
                modifier = Modifier.weight(1f),
                title = operation.title,
                iconRes = chrome.iconRes,
                anchorTag = chrome.anchorTag,
                onClick = chrome.onClick,
            )
        }
    }
}

private data class OperationChrome(
    @param:DrawableRes
    @field:DrawableRes
    val iconRes: Int,
    val anchorTag: String,
    val onClick: () -> Unit,
)

private fun operationChrome(
    action: HomeOperationAction,
    onRecommendClick: () -> Unit,
    onTopListClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLocalMusicClick: () -> Unit,
): OperationChrome = when (action) {
    HomeOperationAction.RecommendSheets -> OperationChrome(
        iconRes = HomeIcons.OperationRecommend,
        anchorTag = FidelityAnchors.Home.OperationsRecommendSheets,
        onClick = onRecommendClick,
    )
    HomeOperationAction.TopList -> OperationChrome(
        iconRes = HomeIcons.OperationTopList,
        anchorTag = FidelityAnchors.Home.OperationsTopList,
        onClick = onTopListClick,
    )
    HomeOperationAction.History -> OperationChrome(
        iconRes = HomeIcons.OperationHistory,
        anchorTag = FidelityAnchors.Home.OperationsHistory,
        onClick = onHistoryClick,
    )
    HomeOperationAction.LocalMusic -> OperationChrome(
        iconRes = HomeIcons.OperationLocal,
        anchorTag = FidelityAnchors.Home.OperationsLocalMusic,
        onClick = onLocalMusicClick,
    )
}

@Composable
private fun OperationCard(
    modifier: Modifier,
    title: String,
    @DrawableRes iconRes: Int,
    anchorTag: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(rpx(148))
            .homeInteractionStyle(
                onClick = onClick,
                minHeight = null,
                shape = RoundedCornerShape(rpx(18)),
            )
            .background(
                color = MusicFreeTheme.colors.card,
                shape = RoundedCornerShape(rpx(18)),
            )
            .testTag(anchorTag)
            .padding(vertical = rpx(16)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MusicFreeTheme.colors.text,
            modifier = Modifier.size(IconSizes.normal),
        )
        Text(
            text = title,
            color = MusicFreeTheme.colors.text,
            fontSize = FontSizes.subTitle,
            modifier = Modifier.padding(top = rpx(10)),
        )
    }
}

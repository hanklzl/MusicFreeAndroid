package com.hank.musicfree.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabIndicatorScope
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class ScenePagerPage<K : Any>(
    val key: K,
    val label: String,
)

internal data class ScenePagerTabMetrics(
    val left: Dp,
    val width: Dp,
)

internal data class ScenePagerIndicatorMetrics(
    val left: Dp,
    val width: Dp,
)

@Composable
fun <K : Any> MusicFreeScenePagerTabs(
    pages: List<ScenePagerPage<K>>,
    selectedKey: K?,
    onSelectedKeyChange: (K) -> Unit,
    modifier: Modifier = Modifier,
    edgePadding: Dp = 0.dp,
    beyondViewportPageCount: Int = 0,
    tabLabel: @Composable (page: ScenePagerPage<K>, selected: Boolean) -> Unit = { page, _ ->
        Text(page.label)
    },
    pageContent: @Composable ColumnScope.(page: ScenePagerPage<K>) -> Unit,
) {
    if (pages.isEmpty()) return

    val selectedIndex = resolveScenePagerSelectedIndex(pages, selectedKey)
    val pagerState = rememberPagerState(
        initialPage = selectedIndex,
        pageCount = { pages.size },
    )
    val latestSelectedKey by rememberUpdatedState(selectedKey)
    val currentOnSelectedKeyChange by rememberUpdatedState(onSelectedKeyChange)
    val scope = rememberCoroutineScope()
    val lastDispatchedKey = remember { mutableStateOf<K?>(null) }

    LaunchedEffect(selectedKey) {
        lastDispatchedKey.value = selectedKey
    }

    LaunchedEffect(selectedIndex, pages.size) {
        if (pagerState.currentPage != selectedIndex && selectedIndex in pages.indices) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }

    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { pageIndex ->
                val page = pages.getOrNull(pageIndex) ?: return@collect
                if (shouldDispatchScenePagerSelection(
                        pageKey = page.key,
                        selectedKey = latestSelectedKey,
                        lastDispatchedKey = lastDispatchedKey.value,
                    )
                ) {
                    currentOnSelectedKeyChange(page.key)
                    lastDispatchedKey.value = page.key
                }
            }
    }

    Column(modifier = modifier) {
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            edgePadding = edgePadding,
            indicator = {
                ScenePagerIndicator(
                    currentPage = pagerState.currentPage,
                    currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
                )
            },
        ) {
            pages.forEachIndexed { index, page ->
                val selected = index == selectedIndex
                Tab(
                    selected = selected,
                    onClick = {
                        if (
                            shouldDispatchScenePagerSelection(
                                pageKey = page.key,
                                selectedKey = latestSelectedKey,
                                lastDispatchedKey = lastDispatchedKey.value,
                            )
                        ) {
                            currentOnSelectedKeyChange(page.key)
                            lastDispatchedKey.value = page.key
                        }
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    text = { tabLabel(page, selected) },
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = beyondViewportPageCount,
            key = { index -> pages[index].key },
        ) { index ->
            Column(modifier = Modifier.fillMaxSize()) {
                pageContent(pages[index])
            }
        }
    }
}

@Composable
private fun TabIndicatorScope.ScenePagerIndicator(
    currentPage: Int,
    currentPageOffsetFraction: Float,
) {
    TabRowDefaults.SecondaryIndicator(
        modifier = Modifier.tabIndicatorLayout { measurable, constraints, tabPositions ->
            val indicatorMetrics = calculateScenePagerIndicatorMetricsFromTabPositions(
                tabPositions = tabPositions,
                currentPage = currentPage,
                currentPageOffsetFraction = currentPageOffsetFraction,
            ) ?: return@tabIndicatorLayout layout(0, 0) {}

            val indicatorWidth = indicatorMetrics.width.roundToPx()
            val placeable = measurable.measure(
                constraints.copy(
                    minWidth = indicatorWidth,
                    maxWidth = indicatorWidth,
                ),
            )
            layout(constraints.maxWidth, placeable.height) {
                placeable.placeRelative(indicatorMetrics.left.roundToPx(), 0)
            }
        },
    )
}

internal fun <K : Any> resolveScenePagerSelectedIndex(
    pages: List<ScenePagerPage<K>>,
    selectedKey: K?,
): Int {
    if (pages.isEmpty()) return 0
    return pages.indexOfFirst { it.key == selectedKey }
        .takeIf { it >= 0 }
        ?: 0
}

internal fun <K : Any> shouldDispatchScenePagerSelection(
    pageKey: K,
    selectedKey: K?,
    lastDispatchedKey: K?,
): Boolean {
    return pageKey != selectedKey && pageKey != lastDispatchedKey
}

internal fun calculateScenePagerIndicatorMetrics(
    tabMetrics: List<ScenePagerTabMetrics>,
    currentPage: Int,
    currentPageOffsetFraction: Float,
): ScenePagerIndicatorMetrics? {
    if (tabMetrics.isEmpty()) return null

    val interpolation = resolveScenePagerIndicatorInterpolation(
        lastIndex = tabMetrics.lastIndex,
        currentPage = currentPage,
        currentPageOffsetFraction = currentPageOffsetFraction,
    )
    val start = tabMetrics[interpolation.startIndex]
    val end = tabMetrics[interpolation.endIndex]
    return ScenePagerIndicatorMetrics(
        left = interpolateDp(start.left, end.left, interpolation.fraction),
        width = interpolateDp(start.width, end.width, interpolation.fraction),
    )
}

private fun calculateScenePagerIndicatorMetricsFromTabPositions(
    tabPositions: List<TabPosition>,
    currentPage: Int,
    currentPageOffsetFraction: Float,
): ScenePagerIndicatorMetrics? {
    if (tabPositions.isEmpty()) return null

    val interpolation = resolveScenePagerIndicatorInterpolation(
        lastIndex = tabPositions.lastIndex,
        currentPage = currentPage,
        currentPageOffsetFraction = currentPageOffsetFraction,
    )
    val start = tabPositions[interpolation.startIndex]
    val end = tabPositions[interpolation.endIndex]
    return ScenePagerIndicatorMetrics(
        left = interpolateDp(start.left, end.left, interpolation.fraction),
        width = interpolateDp(start.width, end.width, interpolation.fraction),
    )
}

private data class ScenePagerIndicatorInterpolation(
    val startIndex: Int,
    val endIndex: Int,
    val fraction: Float,
)

private fun resolveScenePagerIndicatorInterpolation(
    lastIndex: Int,
    currentPage: Int,
    currentPageOffsetFraction: Float,
): ScenePagerIndicatorInterpolation {
    val rawPagePosition = (currentPage + currentPageOffsetFraction)
        .coerceIn(0f, lastIndex.toFloat())
    val startIndex = rawPagePosition.toInt().coerceIn(0, lastIndex)
    return ScenePagerIndicatorInterpolation(
        startIndex = startIndex,
        endIndex = (startIndex + 1).coerceAtMost(lastIndex),
        fraction = rawPagePosition - startIndex,
    )
}

private fun interpolateDp(start: Dp, stop: Dp, fraction: Float): Dp {
    return start + (stop - start) * fraction
}

package com.hank.musicfree.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            edgePadding = edgePadding,
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

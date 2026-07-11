package com.hank.musicfree.feature.home.todayrecommendation

import com.hank.musicfree.data.repository.recommendation.model.PreferenceProfile
import com.hank.musicfree.data.repository.recommendation.model.ProfileConfidence
import com.hank.musicfree.data.repository.recommendation.model.WeightedPreference
import com.hank.musicfree.plugin.api.MusicSheetItemBase
import com.hank.musicfree.plugin.api.PaginationResult
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.api.PluginSearchItem
import com.hank.musicfree.plugin.api.RecommendSheetTagsResult
import com.hank.musicfree.plugin.api.SearchResult
import com.hank.musicfree.plugin.manager.LoadedPlugin
import com.hank.musicfree.plugin.manager.PluginManager
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PluginCandidateFetcherTest {

    @Test
    fun `matched tag and sheet search both contribute candidates`() = runTest {
        val manager = mock<PluginManager>()
        val plugin = plugin(
            platform = "qq",
            methods = setOf("getRecommendSheetTags", "getRecommendSheetsByTag", "search"),
            searchTypes = listOf("sheet"),
        )
        val tag = sheet("rock-tag", "qq", "摇滚")
        whenever(manager.getSortedEnabledPlugins()).thenReturn(flowOf(listOf(plugin)))
        whenever(plugin.getRecommendSheetTags()).thenReturn(
            RecommendSheetTagsResult(pinned = listOf(tag), data = emptyList()),
        )
        whenever(plugin.getRecommendSheetsByTag(any(), eq(1))).thenReturn(
            PaginationResult(isEnd = true, data = listOf(sheet("tag-result", "qq", "摇滚精选"))),
        )
        whenever(plugin.search(eq("摇滚 歌单"), eq(1), eq("sheet"))).thenReturn(
            SearchResult(isEnd = true, data = listOf(PluginSearchItem.Sheet(sheet("search-result", "qq", "摇滚现场")))),
        )
        val fetcher = PluginCandidateFetcher(manager, perCallTimeoutMs = 5_000L)

        val result = fetcher.fetch(
            profile = profile(),
            queries = listOf(RecommendationQuery("摇滚 歌单", WeightedPreference("摇滚", 10.0))),
        )

        assertEquals(setOf("tag-result", "search-result"), result.candidates.map { it.sheet.id }.toSet())
        assertEquals(1, result.availablePluginCount)
        assertTrue(result.pluginSignature.isNotBlank())
        verify(plugin).getRecommendSheetsByTag(
            eq(mapOf("id" to "rock-tag", "title" to "摇滚")),
            eq(1),
        )
    }

    @Test
    fun `one failing plugin does not cancel successful peer`() = runTest {
        val manager = mock<PluginManager>()
        val failing = plugin("bad", setOf("search"), listOf("sheet"))
        val working = plugin("good", setOf("search"), listOf("sheet"))
        whenever(manager.getSortedEnabledPlugins()).thenReturn(flowOf(listOf(failing, working)))
        whenever(failing.search(any(), eq(1), eq("sheet"))).thenThrow(IllegalStateException("boom"))
        whenever(working.search(any(), eq(1), eq("sheet"))).thenReturn(
            SearchResult(true, listOf(PluginSearchItem.Sheet(sheet("ok", "good", "热门歌单")))),
        )

        val result = PluginCandidateFetcher(manager, perCallTimeoutMs = 5_000L).fetch(
            profile = profile(),
            queries = listOf(RecommendationQuery("热门歌单")),
        )

        assertEquals(listOf("ok"), result.candidates.map { it.sheet.id })
        assertEquals(2, result.availablePluginCount)
    }

    private fun profile() = PreferenceProfile(
        artists = emptyList(),
        genres = listOf(WeightedPreference("摇滚", 10.0)),
        languages = emptyList(),
        platforms = emptyList(),
        distinctSongCount = 20,
        confidence = ProfileConfidence.ESTABLISHED,
        signature = "profile",
    )

    private fun plugin(
        platform: String,
        methods: Set<String>,
        searchTypes: List<String> = emptyList(),
    ): LoadedPlugin = mock {
        on { info }.thenReturn(
            PluginInfo(
                platform = platform,
                version = "1.0.0",
                author = null,
                description = null,
                srcUrl = null,
                supportedSearchType = searchTypes,
                supportedSearchTypeDeclared = searchTypes.isNotEmpty(),
                supportedMethods = methods,
                hash = "hash-$platform",
            ),
        )
    }

    private fun sheet(id: String, platform: String, title: String) = MusicSheetItemBase(
        id = id,
        platform = platform,
        title = title,
        artist = null,
        description = null,
        coverImg = null,
        artwork = null,
        worksNum = 20,
        raw = mapOf("id" to id),
    )
}

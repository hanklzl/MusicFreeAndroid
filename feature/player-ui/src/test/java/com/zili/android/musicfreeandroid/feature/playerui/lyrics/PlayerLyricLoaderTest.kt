package com.zili.android.musicfreeandroid.feature.playerui.lyrics

import com.zili.android.musicfreeandroid.core.model.LyricSourceInfo
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RawLyricPayload
import com.zili.android.musicfreeandroid.data.mapper.LyricCache
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.repository.LyricRepository
import com.zili.android.musicfreeandroid.plugin.api.LyricResult
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo
import com.zili.android.musicfreeandroid.plugin.api.SearchResult
import com.zili.android.musicfreeandroid.plugin.manager.LoadedPlugin
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PlayerLyricLoaderTest {

    private val lyricRepository: LyricRepository = mock()
    private val pluginManager: PluginManager = mock()
    private val appPreferences: AppPreferences = mock()
    private val lyricPlugins = MutableStateFlow<List<LoadedPlugin>>(emptyList())
    private val lyricAutoSearchEnabledFlow = MutableStateFlow(true)

    init {
        whenever(pluginManager.getLyricSearchablePlugins()).thenReturn(lyricPlugins)
        whenever(appPreferences.lyricAutoSearchEnabled).thenReturn(lyricAutoSearchEnabledFlow)
    }

    private fun loader() = PlayerLyricLoader(lyricRepository, pluginManager, appPreferences)

    @Test
    fun nullTrackEmitsNoTrack() = runTest {
        val loader = loader()

        val states = loader.observeLyrics(null).toList()

        assertEquals(1, states.size)
        assertEquals(LyricLoadState.NoTrack, states[0])
    }

    @Test
    fun localLyricsWinBeforePluginFetch() = runTest {
        val loader = loader()
        val music = music("local-primary", "demo")
        val cache = lyricCache(
            music = music,
            localRawLrc = "[00:01.00]Local Lyrics",
            localTranslation = "Should ignore",
        )
        val demoPlugin = plugin(platform = "demo", lyric = lyricResult("Remote Lyrics"))

        whenever(lyricRepository.observeCache(music)).thenReturn(flowOf(cache))
        whenever(pluginManager.getPlugin("demo")).thenReturn(demoPlugin)

        val state = loader.observeLyrics(music).firstReady()

        assertEquals("Local Lyrics", state.document.lines.first().text)
        assertEquals(LyricSourceInfo.LocalRaw, state.document.source)
        verify(pluginManager, never()).getPlugin(any())
    }

    @Test
    fun localTranslationOnlyBuildsStaticDocument() = runTest {
        val loader = loader()
        val music = music("local-translation", "demo")
        val cache = lyricCache(
            music = music,
            localRawLrc = null,
            localTranslation = "静态翻译",
        )

        whenever(lyricRepository.observeCache(music)).thenReturn(flowOf(cache))

        val state = loader.observeLyrics(music)
            .firstReady()

        assertEquals("静态翻译", state.document.lines.first().text)
        assertEquals(0L, state.document.lines.first().timeMs)
        assertEquals(LyricSourceInfo.LocalTranslation, state.document.source)
    }

    @Test
    fun matchingRemoteCacheIsUsedBeforePluginFetch() = runTest {
        val loader = loader()
        val music = music("cached", "demo")
        val cache = lyricCache(
            music = music,
            remotePayload = RawLyricPayload(rawLrc = "[00:01.00]Cached Lyric"),
            remoteSourcePlatform = null,
            remoteSourceMusicId = null,
        )
        val demoPlugin = plugin(platform = "demo", lyric = lyricResult("Should Not Fetch"))

        whenever(lyricRepository.observeCache(music)).thenReturn(flowOf(cache))
        whenever(pluginManager.getPlugin("demo")).thenReturn(demoPlugin)

        val state = loader.observeLyrics(music).firstReady()

        assertEquals("Cached Lyric", state.document.lines.first().text)
        assertEquals(LyricSourceInfo.Cache, state.document.source)
        verify(pluginManager, never()).getPlugin(any())
        verify(lyricRepository, never()).saveRemoteLyric(any(), any(), any())
    }

    @Test
    fun cacheOffsetUpdatesReemitReadyState() = runTest {
        val loader = loader()
        val music = music("offset-live", "demo")
        val cacheFlow = MutableStateFlow(
            lyricCache(
                music = music,
                remotePayload = RawLyricPayload(rawLrc = "[00:01.00]Cached Lyric"),
                userOffsetMs = 100L,
            ),
        )
        whenever(lyricRepository.observeCache(music)).thenReturn(cacheFlow)

        val states = mutableListOf<LyricLoadState>()
        val job = launch {
            loader.observeLyrics(music).toList(states)
        }
        advanceUntilIdle()

        assertEquals(100L, states.filterIsInstance<LyricLoadState.Ready>().last().userOffsetMs)

        cacheFlow.value = lyricCache(
            music = music,
            remotePayload = RawLyricPayload(rawLrc = "[00:01.00]Cached Lyric"),
            userOffsetMs = 1_200L,
        )
        advanceUntilIdle()

        assertEquals(1_200L, states.filterIsInstance<LyricLoadState.Ready>().last().userOffsetMs)
        job.cancel()
    }

    @Test
    fun mismatchedRemoteCacheFallsThroughToPlugin() = runTest {
        val loader = loader()
        val music = music("cache-miss", "demo")
        val cache = lyricCache(
            music = music,
            remotePayload = RawLyricPayload(rawLrc = "[00:01.00]Wrong Cached Lyric"),
            remoteSourcePlatform = "other-platform",
            remoteSourceMusicId = "other-id",
        )
        val demoPlugin = plugin(platform = "demo", lyric = lyricResult("Plugin Lyric"))

        whenever(lyricRepository.observeCache(music)).thenReturn(flowOf(cache))
        whenever(pluginManager.getPlugin("demo")).thenReturn(demoPlugin)

        val state = loader.observeLyrics(music).firstReady()

        assertEquals("Plugin Lyric", state.document.lines.first().text)
        assertEquals(LyricSourceInfo.Plugin("demo"), state.document.source)
        verify(lyricRepository).saveRemoteLyric(any(), any(), any())
    }

    @Test
    fun associatedLyricFetchIsCachedForCurrentMusic() = runTest {
        val loader = loader()
        val music = music("now", "demo")
        val target = music("associated", "assoc", title = "Assoc Song", artist = "Assoc Artist")
        val cache = lyricCache(music = music, associatedMusic = target)
        val assocPlugin = plugin(platform = "assoc", lyric = lyricResult("Associated Lyric"))

        whenever(lyricRepository.observeCache(music)).thenReturn(flowOf(cache))
        whenever(pluginManager.getPlugin("assoc")).thenReturn(assocPlugin)

        val state = loader.observeLyrics(music).firstReady()

        assertEquals("Associated Lyric", state.document.lines.first().text)
        val sourceCaptor = argumentCaptor<LyricSourceInfo>()
        val payloadCaptor = argumentCaptor<RawLyricPayload>()
        verify(lyricRepository).saveRemoteLyric(
            music = eq(music),
            source = sourceCaptor.capture(),
            payload = payloadCaptor.capture(),
        )
        assertEquals(LyricSourceInfo.Associated("assoc", "Assoc Song", "associated"), sourceCaptor.firstValue)
        assertEquals("[00:01.00]Associated Lyric", payloadCaptor.firstValue.rawLrc)
    }

    @Test
    fun pluginLyricsAreCached() = runTest {
        val loader = loader()
        val music = music("plugin-cache", "demo")
        val demoPlugin = plugin(platform = "demo", lyric = lyricResult("Plugin Lyric"))

        whenever(lyricRepository.observeCache(music)).thenReturn(flowOf(null))
        whenever(pluginManager.getPlugin("demo")).thenReturn(demoPlugin)

        val state = loader.observeLyrics(music).firstReady()

        assertEquals("Plugin Lyric", state.document.lines.first().text)
        assertEquals(LyricSourceInfo.Plugin("demo"), state.document.source)
        verify(lyricRepository).saveRemoteLyric(music = eq(music), source = any(), payload = any())
    }

    @Test
    fun autoSearchUsesOtherLyricPlugin() = runTest {
        val loader = loader()
        val music = music("auto-demo", "demo")
        val candidate = music("auto-result", "lyric", title = "Search Result", artist = "Search Artist")
        val demoPlugin = plugin(platform = "demo", lyric = null)
        val lyricPlugin = plugin(
            platform = "lyric",
            search = SearchResult(
                isEnd = true,
                data = listOf(candidate),
            ),
            lyric = lyricResult("Found by search"),
        )

        whenever(pluginManager.getPlugin("demo")).thenReturn(demoPlugin)
        whenever(pluginManager.getPlugin("lyric")).thenReturn(lyricPlugin)
        lyricPlugins.value = listOf(lyricPlugin)
        whenever(lyricRepository.observeCache(music)).thenReturn(flowOf(null))

        val state = loader.observeLyrics(music).firstReady()

        assertEquals("Found by search", state.document.lines.first().text)
        assertTrue(state.document.source is LyricSourceInfo.AutoSearch)
        assertEquals("lyric", (state.document.source as LyricSourceInfo.AutoSearch).platform)
    }

    @Test
    fun autoSearchSkipsCurrentPlatformSearchCandidates() = runTest {
        val loader = loader()
        val music = music("auto-skip-current", "demo", title = "Song", artist = "Artist")
        val currentCandidate = music("current-candidate", "demo", title = "Song", artist = "Artist")
        val otherCandidate = music("other-candidate", "lyric", title = "Song", artist = "Artist")
        val currentPlugin = plugin(
            platform = "demo",
            search = SearchResult(
                isEnd = true,
                data = listOf(currentCandidate),
            ),
            lyric = null,
        )
        val lyricPlugin = plugin(
            platform = "lyric",
            search = SearchResult(
                isEnd = true,
                data = listOf(otherCandidate),
            ),
            lyric = lyricResult("Other Platform Lyric"),
        )
        runBlocking {
            whenever(currentPlugin.getLyric(any())).thenAnswer { invocation ->
                val target = invocation.arguments[0] as MusicItem
                if (target.id == currentCandidate.id) lyricResult("Current Platform Candidate") else null
            }
        }

        whenever(pluginManager.getPlugin("demo")).thenReturn(currentPlugin)
        whenever(pluginManager.getPlugin("lyric")).thenReturn(lyricPlugin)
        lyricPlugins.value = listOf(currentPlugin, lyricPlugin)
        whenever(lyricRepository.observeCache(music)).thenReturn(flowOf(null))

        val state = loader.observeLyrics(music).firstReady()

        assertEquals("Other Platform Lyric", state.document.lines.first().text)
        assertTrue(state.document.source is LyricSourceInfo.AutoSearch)
        assertEquals("lyric", (state.document.source as LyricSourceInfo.AutoSearch).platform)
    }

    @Test
    fun autoSearchPrefersExactTitleAndArtist() = runTest {
        val loader = loader()
        val music = music("match", "demo", title = "Song A", artist = "Artist A")
        val exactCandidate = music("exact", "lyric", title = "Song A", artist = "Artist A")
        val looseCandidate = music("loose", "lyric", title = "Song", artist = "Someone")
        val lyricPlugin = plugin(
            platform = "lyric",
            search = SearchResult(
                isEnd = true,
                data = listOf(looseCandidate, exactCandidate),
            ),
            lyric = null,
        )
        val demoPlugin = plugin(platform = "demo", lyric = null)
        runBlocking {
            whenever(lyricPlugin.getLyric(any())).thenAnswer { invocation ->
                val target = invocation.arguments[0] as MusicItem
                when {
                    target.title == "Song A" && target.artist == "Artist A" -> lyricResult("Exact")
                    target.title == "Song" && target.artist == "Someone" -> lyricResult("Loose")
                    else -> null
                }
            }
        }

        whenever(pluginManager.getPlugin("demo")).thenReturn(demoPlugin)
        whenever(pluginManager.getPlugin("lyric")).thenReturn(lyricPlugin)
        lyricPlugins.value = listOf(lyricPlugin)
        whenever(lyricRepository.observeCache(music)).thenReturn(flowOf(null))

        val state = loader.observeLyrics(music).firstReady()

        assertEquals("Exact", state.document.lines.first().text)
    }

    @Test
    fun pluginFailureFallsThroughToNoLyric() = runTest {
        val loader = loader()
        val music = music("failure", "demo")
        val currentPlugin = plugin(platform = "demo", lyric = null)
        val lyricPlugin = plugin(
            platform = "lyric",
            search = SearchResult(isEnd = true, data = listOf(music("found", "lyric", title = "Found", artist = "Artist"))),
            lyric = null,
        )

        whenever(pluginManager.getPlugin("demo")).thenReturn(currentPlugin)
        whenever(pluginManager.getPlugin("lyric")).thenReturn(lyricPlugin)
        lyricPlugins.value = listOf(lyricPlugin)
        whenever(lyricRepository.observeCache(music)).thenReturn(flowOf(null))

        runBlocking {
            whenever(currentPlugin.getLyric(any())).thenThrow(RuntimeException("current failed"))
            whenever(lyricPlugin.getLyric(any())).thenThrow(RuntimeException("search candidate failed"))
        }

        val states = loader.observeLyrics(music).toList()

        assertTrue(states.any { it is LyricLoadState.NoLyric })
    }

    @Test
    fun cancellationIsRethrownFromPluginFetch() = runTest {
        val loader = loader()
        val music = music("cancelled", "demo")
        val cancelPlugin = mock<LoadedPlugin>()
        runBlocking {
            whenever(cancelPlugin.info).thenReturn(
                PluginInfo(
                    platform = "demo",
                    version = null,
                    author = null,
                    description = null,
                    srcUrl = null,
                    supportedSearchType = listOf("lyric"),
                ),
            )
            whenever(cancelPlugin.getLyric(any())).thenThrow(CancellationException("cancel"))
        }

        whenever(lyricRepository.observeCache(music)).thenReturn(flowOf(null))
        whenever(pluginManager.getPlugin("demo")).thenReturn(cancelPlugin)

        assertThrows(CancellationException::class.java) {
            runBlocking {
                loader.observeLyrics(music).toList()
            }
        }
    }

    @Test
    fun remoteTranslationOnlyDoesNotEmitBlankReady() = runTest {
        val loader = loader()
        val music = music("remote-translation", "demo")
        val cache = lyricCache(
            music = music,
            remotePayload = RawLyricPayload(translation = "纯翻译"),
        )

        whenever(lyricRepository.observeCache(music)).thenReturn(flowOf(cache))

        val states = loader.observeLyrics(music).toList()
        assertTrue(states.any { it is LyricLoadState.NoLyric })
    }

    @Test
    fun autoSearchDisabledSkipsLyricPlugins() = runTest {
        val loader = loader()
        val music = music("disabled-search", "demo")
        val searchOnlyPlugin = plugin(platform = "lyric")

        lyricAutoSearchEnabledFlow.value = false
        lyricPlugins.value = listOf(searchOnlyPlugin)
        whenever(lyricRepository.observeCache(music)).thenReturn(flowOf(null))

        val states = loader.observeLyrics(music).toList()
        assertTrue(states.any { it is LyricLoadState.NoLyric })
        verify(pluginManager, never()).getLyricSearchablePlugins()
    }

    @Test
    fun searchCandidatesThrowsOnCancellation() = runTest {
        val loader = loader()
        val music = music("query", "demo")
        val badPlugin = plugin(platform = "bad")
        runBlocking {
            whenever(badPlugin.search(any(), any(), any())).thenThrow(CancellationException("cancel"))
        }

        lyricPlugins.value = listOf(badPlugin)

        assertThrows(CancellationException::class.java) {
            runBlocking {
                loader.searchCandidates(music)
            }
        }
    }

    @Test
    fun searchCandidatesIncludesCurrentPlatformForManualSearch() = runTest {
        val loader = loader()
        val music = music("manual-query", "demo")
        val currentCandidate = music("manual-current", "demo")
        val otherCandidate = music("manual-other", "lyric")
        val currentPlugin = plugin(
            platform = "demo",
            search = SearchResult(isEnd = true, data = listOf(currentCandidate)),
        )
        val otherPlugin = plugin(
            platform = "lyric",
            search = SearchResult(isEnd = true, data = listOf(otherCandidate)),
        )

        lyricPlugins.value = listOf(currentPlugin, otherPlugin)

        val groups = loader.searchCandidates(music)

        assertEquals(listOf("demo", "lyric"), groups.map { it.plugin.platform })
        assertEquals(listOf(currentCandidate), groups[0].items)
        assertEquals(listOf(otherCandidate), groups[1].items)
    }

    @Test
    fun searchCandidatesReturnsErrorGroupForPluginFailure() = runTest {
        val loader = loader()
        val music = music("query", "demo")
        val badPlugin = plugin(platform = "bad")
        runBlocking {
            whenever(badPlugin.search(any(), any(), any())).thenThrow(RuntimeException("search failed"))
        }

        lyricPlugins.value = listOf(badPlugin)

        val groups = loader.searchCandidates(music)

        assertEquals(1, groups.size)
        assertEquals("bad", groups[0].plugin.platform)
        assertEquals(emptyList<MusicItem>(), groups[0].items)
        assertNotNull(groups[0].errorMessage)
        assertTrue(groups[0].errorMessage!!.contains("搜索歌词失败"))
    }

    @Test
    fun autoSearchInProgressDoesNotEmitNoLyricBeforeFinalFailure() = runTest {
        val loader = loader()
        val music = music("no-early-empty", "demo")
        val currentPlugin = plugin(platform = "demo", lyric = null)
        val lyricPlugin = plugin(
            platform = "lyric",
            search = SearchResult(
                isEnd = true,
                data = listOf(music("candidate", "lyric", title = "Song", artist = "Artist")),
            ),
            lyric = lyricResult("Found Later"),
        )

        whenever(pluginManager.getPlugin("demo")).thenReturn(currentPlugin)
        whenever(pluginManager.getPlugin("lyric")).thenReturn(lyricPlugin)
        whenever(lyricRepository.observeCache(music)).thenReturn(flowOf(null))
        lyricPlugins.value = listOf(lyricPlugin)

        val states = loader.observeLyrics(music).toList()

        assertEquals(LyricLoadState.Loading(music), states.first())
        assertTrue(states.last() is LyricLoadState.Ready)
        assertFalse(states.dropLast(1).any { it is LyricLoadState.NoLyric })
    }

    @Test
    fun cacheReemissionAfterReadyDoesNotEmitNoLyricBetweenReadyStates() = runTest {
        val loader = loader()
        val music = music("cache-reemit-ready", "demo")
        val cacheFlow = MutableStateFlow<LyricCache?>(null)
        val demoPlugin = plugin(platform = "demo", lyric = lyricResult("Remote Lyric"))

        whenever(pluginManager.getPlugin("demo")).thenReturn(demoPlugin)
        whenever(lyricRepository.observeCache(music)).thenReturn(cacheFlow)
        whenever(lyricRepository.saveRemoteLyric(eq(music), any(), any())).thenAnswer {
            cacheFlow.value = lyricCache(
                music = music,
                remotePayload = RawLyricPayload(rawLrc = "[00:01.00]Remote Lyric"),
                remoteSourcePlatform = "demo",
                remoteSourceMusicId = music.id,
            )
            Unit
        }

        val states = mutableListOf<LyricLoadState>()
        val job = launch {
            loader.observeLyrics(music).toList(states)
        }
        advanceUntilIdle()
        job.cancel()

        assertTrue(states.filterIsInstance<LyricLoadState.Ready>().size >= 1)
        assertFalse(states.any { it is LyricLoadState.NoLyric })
    }
}

private suspend fun Flow<LyricLoadState>.firstReady(): LyricLoadState.Ready =
    filterIsInstance<LyricLoadState.Ready>().first()

private fun music(id: String, platform: String, title: String = "Song", artist: String = "Artist"): MusicItem =
    MusicItem(
        id = id,
        platform = platform,
        title = title,
        artist = artist,
        album = null,
        duration = 180_000L,
        url = null,
        artwork = null,
        qualities = null,
    )

private fun lyricResult(text: String): LyricResult =
    LyricResult(rawLrc = "[00:01.00]$text", rawLrcTxt = null, translation = null, lines = emptyList())

private fun lyricCache(
    music: MusicItem,
    remotePayload: RawLyricPayload? = null,
    remoteSourcePlatform: String? = null,
    remoteSourceMusicId: String? = null,
    localRawLrc: String? = null,
    localTranslation: String? = null,
    associatedMusic: MusicItem? = null,
    userOffsetMs: Long = 0L,
): LyricCache = LyricCache(
    musicId = music.id,
    musicPlatform = music.platform,
    remotePayload = remotePayload,
    remoteSourceType = remotePayload?.let { "plugin" },
    remoteSourcePlatform = remoteSourcePlatform,
    remoteSourceMusicId = remoteSourceMusicId,
    remoteSourceTitle = null,
    localRawLrc = localRawLrc,
    localTranslation = localTranslation,
    associatedMusic = associatedMusic,
    userOffsetMs = userOffsetMs,
)

private fun plugin(
    platform: String,
    search: SearchResult = SearchResult(isEnd = true, data = emptyList()),
    lyric: LyricResult? = null,
): LoadedPlugin {
    val plugin = mock<LoadedPlugin>()
    runBlocking {
        whenever(plugin.info).thenReturn(
            PluginInfo(
                platform = platform,
                version = null,
                author = null,
                description = null,
                srcUrl = null,
                supportedSearchType = listOf("lyric"),
            ),
        )
        whenever(plugin.search(any(), any(), any())).thenReturn(search)
        whenever(plugin.getLyric(any())).thenReturn(lyric)
    }
    return plugin
}

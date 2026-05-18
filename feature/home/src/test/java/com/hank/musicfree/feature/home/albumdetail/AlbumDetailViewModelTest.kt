package com.hank.musicfree.feature.home.albumdetail

import androidx.lifecycle.SavedStateHandle
import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.core.runtime.RuntimeSnapshot
import com.hank.musicfree.core.runtime.SnapshotStore
import com.hank.musicfree.core.model.StarredKind
import com.hank.musicfree.core.model.StarredSheet
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.repository.StarredSheetRepository
import com.hank.musicfree.downloader.Downloader
import com.hank.musicfree.feature.home.runtime.DetailPluginSignatureProvider
import com.hank.musicfree.feature.home.runtime.DetailSessionClock
import com.hank.musicfree.feature.home.runtime.DetailSessionStore
import com.hank.musicfree.feature.home.runtime.PluginManagerDetailSessionGateway
import com.hank.musicfree.player.controller.PlayerController
import com.hank.musicfree.plugin.api.AlbumInfoResult
import com.hank.musicfree.plugin.api.AlbumItemBase
import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.manager.LoadedPlugin
import com.hank.musicfree.plugin.manager.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val pluginManager: PluginManager = mock()
    private val playerController: PlayerController = mock()
    private val appPreferences: AppPreferences = mock()
    private val downloader: Downloader = mock()
    private val mediaSourceResolver: MediaSourceResolver = mock()
    private val starredRepo: StarredSheetRepository = mock()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // Plugin manager not used for star observation — make it a no-op:
        runBlocking { whenever(pluginManager.ensurePluginsLoaded()).thenReturn(Unit) }
        whenever(pluginManager.getPlugin("qq")).thenReturn(null) // forces early-return error path in loadInitial
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun savedStateHandle() = SavedStateHandle(
        mapOf(
            "pluginPlatform" to "qq",
            "albumId" to "alb-1",
            "title" to "AlbumOne",
            "artist" to "ArtistOne",
        )
    )

    private fun newViewModel(
        starredFlow: MutableStateFlow<Boolean>,
        detailSessionStore: DetailSessionStore = newDetailSessionStore(),
    ): AlbumDetailViewModel {
        whenever(starredRepo.observeIsStarred(id = "alb-1", platform = "qq"))
            .thenReturn(starredFlow)
        return AlbumDetailViewModel(
            savedStateHandle = savedStateHandle(),
            pluginManager = pluginManager,
            playerController = playerController,
            appPreferences = appPreferences,
            downloader = downloader,
            mediaSourceResolver = mediaSourceResolver,
            starredSheetRepository = starredRepo,
            detailSessionStore = detailSessionStore,
        )
    }

    private fun newDetailSessionStore(): DetailSessionStore = DetailSessionStore(
        snapshotStore = InMemorySnapshotStore(),
        gateway = PluginManagerDetailSessionGateway(pluginManager),
        signatureProvider = DetailPluginSignatureProvider { "sig" },
        json = Json { ignoreUnknownKeys = true },
        clock = DetailSessionClock { 1_000L },
    )

    @Test
    fun `isAlbumStarred mirrors repository flow`() = runTest(testDispatcher) {
        val flow = MutableStateFlow(false)
        val vm = newViewModel(flow)
        val observed = mutableListOf<Boolean>()
        val job = launch { vm.isAlbumStarred.collect { observed.add(it) } }
        advanceUntilIdle()
        assertEquals(false, vm.isAlbumStarred.value)

        flow.value = true
        advanceUntilIdle()
        assertEquals(true, vm.isAlbumStarred.value)

        job.cancel()
    }

    @Test
    fun `toggleAlbumStarred forwards album seed with kind ALBUM`() = runTest(testDispatcher) {
        val flow = MutableStateFlow(false)
        val vm = newViewModel(flow)
        val job = launch { vm.isAlbumStarred.collect {} }
        advanceUntilIdle()

        vm.toggleAlbumStarred()
        advanceUntilIdle()

        val captured = argumentCaptor<StarredSheet>()
        verify(starredRepo).toggle(captured.capture())
        val payload = captured.firstValue
        assertEquals("alb-1", payload.id)
        assertEquals("qq", payload.platform)
        assertEquals(StarredKind.ALBUM, payload.kind)
        assertEquals("AlbumOne", payload.title)
        assertEquals("ArtistOne", payload.artist)

        job.cancel()
    }

    @Test
    fun `loadInitial exposes resolved album item for page header`() = runTest(testDispatcher) {
        val plugin = albumPlugin(
            album = AlbumItemBase(
                id = "alb-1",
                platform = "qq",
                title = "Resolved Album",
                date = "2026",
                artist = "Resolved Artist",
                description = "Album intro",
                artwork = "https://example.com/album.jpg",
                worksNum = 12,
                raw = mapOf("coverImg" to "https://example.com/cover.jpg"),
            ),
            musicList = listOf(musicItem("song-1")),
        )
        whenever(pluginManager.getPlugin("qq")).thenReturn(plugin)

        val vm = newViewModel(MutableStateFlow(false))
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.loading)
        assertEquals("Resolved Album", vm.uiState.value.title)
        assertEquals("Resolved Album", vm.uiState.value.albumItem?.title)
        assertEquals("Album intro", vm.uiState.value.albumItem?.description)
        assertEquals(12, vm.uiState.value.albumItem?.worksNum)
        assertEquals(listOf(musicItem("song-1")), vm.uiState.value.musicList)
    }

    @Test
    fun `recreated view model reuses detail session without reloading first page`() = runTest(testDispatcher) {
        val plugin = albumPlugin(
            album = AlbumItemBase(
                id = "alb-1",
                platform = "qq",
                title = "Resolved Album",
                date = "2026",
                artist = "Resolved Artist",
                description = "Album intro",
                artwork = "https://example.com/album.jpg",
                worksNum = 12,
                raw = emptyMap(),
            ),
            musicList = listOf(musicItem("song-1")),
        )
        whenever(pluginManager.getPlugin("qq")).thenReturn(plugin)
        val store = newDetailSessionStore()

        val first = newViewModel(MutableStateFlow(false), detailSessionStore = store)
        advanceUntilIdle()
        assertEquals(listOf(musicItem("song-1")), first.uiState.value.musicList)

        val recreated = newViewModel(MutableStateFlow(false), detailSessionStore = store)
        advanceUntilIdle()

        assertEquals(listOf(musicItem("song-1")), recreated.uiState.value.musicList)
        runBlocking {
            verify(plugin, times(1)).getAlbumInfo(org.mockito.kotlin.any(), org.mockito.kotlin.eq(1))
        }
    }

    private fun albumPlugin(
        album: AlbumItemBase,
        musicList: List<MusicItem>,
    ): LoadedPlugin {
        val plugin = mock<LoadedPlugin>()
        whenever(plugin.info).thenReturn(
            PluginInfo(
                platform = "qq",
                version = "1.0.0",
                author = null,
                description = null,
                srcUrl = null,
                supportedSearchType = listOf("music"),
                supportedMethods = setOf("getAlbumInfo"),
            ),
        )
        runBlocking {
            whenever(plugin.getAlbumInfo(org.mockito.kotlin.any(), org.mockito.kotlin.eq(1)))
                .thenReturn(AlbumInfoResult(isEnd = true, albumItem = album, musicList = musicList))
        }
        return plugin
    }

    private fun musicItem(id: String): MusicItem = MusicItem(
        id = id,
        platform = "qq",
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        duration = 180_000,
        url = "https://example.com/$id.mp3",
        artwork = null,
        qualities = null,
    )

    private class InMemorySnapshotStore : SnapshotStore {
        override suspend fun read(namespace: String, key: String): RuntimeSnapshot? = null
        override suspend fun write(snapshot: RuntimeSnapshot) = Unit
        override suspend fun delete(namespace: String, key: String) = Unit
        override suspend fun deleteExpired(namespace: String, nowEpochMs: Long): Int = 0
        override suspend fun pruneNamespace(namespace: String, keepLatest: Int): Int = 0
        override suspend fun keys(namespace: String, limit: Int): List<String> = emptyList()
    }
}

package com.zili.android.musicfreeandroid.feature.home.albumdetail

import androidx.lifecycle.SavedStateHandle
import com.zili.android.musicfreeandroid.core.media.MediaSourceResolver
import com.zili.android.musicfreeandroid.core.model.StarredKind
import com.zili.android.musicfreeandroid.core.model.StarredSheet
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.data.repository.StarredSheetRepository
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.player.controller.PlayerController
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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

    private fun newViewModel(starredFlow: MutableStateFlow<Boolean>): AlbumDetailViewModel {
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
        )
    }

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
}

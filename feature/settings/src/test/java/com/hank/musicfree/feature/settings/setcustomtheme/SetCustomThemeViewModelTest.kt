package com.hank.musicfree.feature.settings.setcustomtheme

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.hank.musicfree.core.theme.DarkMusicFreeColors
import com.hank.musicfree.core.theme.runtime.BackgroundInfo
import com.hank.musicfree.core.theme.runtime.SelectedTheme
import com.hank.musicfree.core.theme.runtime.ThemeRepository
import com.hank.musicfree.core.theme.runtime.ThemeUiState
import com.hank.musicfree.feature.settings.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SetCustomThemeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(
        repo: FakeThemeRepository = FakeThemeRepository(),
        loader: FakeImageAndPaletteLoader = FakeImageAndPaletteLoader(),
    ): Triple<SetCustomThemeViewModel, FakeThemeRepository, FakeImageAndPaletteLoader> {
        val context = mock<Context>()
        val vm = SetCustomThemeViewModel(
            context = context,
            themeRepository = repo,
            loader = loader,
        )
        return Triple(vm, repo, loader)
    }

    @Test
    fun `openColorPicker stores active key`() = runTest(mainDispatcherRule.dispatcher) {
        val (vm, _, _) = createViewModel()

        vm.openColorPicker("primary")

        assertEquals("primary", vm.activeColorKey.value)
    }

    @Test
    fun `dismissColorPicker clears active key`() = runTest(mainDispatcherRule.dispatcher) {
        val (vm, _, _) = createViewModel()
        vm.openColorPicker("appBar")

        vm.dismissColorPicker()

        assertNull(vm.activeColorKey.value)
    }

    @Test
    fun `onColorConfirmed patches single key and clears active`() = runTest(mainDispatcherRule.dispatcher) {
        val (vm, repo, _) = createViewModel()
        vm.openColorPicker("primary")

        vm.onColorConfirmed("primary", "#FFFF0000")
        advanceUntilIdle()

        assertEquals(listOf(mapOf("primary" to "#FFFF0000")), repo.patchCalls)
        assertNull(vm.activeColorKey.value)
    }

    @Test
    fun `onBlurChanged forwards blur only to setBackground`() = runTest(mainDispatcherRule.dispatcher) {
        val (vm, repo, _) = createViewModel()

        vm.onBlurChanged(5f)
        advanceUntilIdle()

        assertEquals(1, repo.setBackgroundCalls.size)
        val call = repo.setBackgroundCalls.first()
        assertEquals(5f, call.blur)
        assertNull(call.url)
        assertNull(call.opacity)
    }

    @Test
    fun `onOpacityChanged forwards opacity only to setBackground`() = runTest(mainDispatcherRule.dispatcher) {
        val (vm, repo, _) = createViewModel()

        vm.onOpacityChanged(0.4f)
        advanceUntilIdle()

        assertEquals(1, repo.setBackgroundCalls.size)
        val call = repo.setBackgroundCalls.first()
        assertEquals(0.4f, call.opacity)
        assertNull(call.url)
        assertNull(call.blur)
    }

    @Test
    fun `onImagePicked drives replaceCustomColors then setBackground then selectTheme CUSTOM`() =
        runTest(mainDispatcherRule.dispatcher) {
            val red = Color(red = 230, green = 30, blue = 30, alpha = 255)
            val loader = FakeImageAndPaletteLoader(
                copiedUri = Uri.parse("file:///mock/bg.jpg"),
                palette = PaletteColors(red, red, red),
            )
            val (vm, repo, _) = createViewModel(loader = loader)

            vm.onImagePicked(Uri.parse("content://input/source.jpg"))
            advanceUntilIdle()

            // Order matters: colours apply before background URL so the first
            // render after select uses the new palette.
            assertEquals(3, repo.callOrder.size)
            assertEquals("replaceCustomColors", repo.callOrder[0])
            assertEquals("setBackground", repo.callOrder[1])
            assertEquals("selectTheme", repo.callOrder[2])

            assertEquals(1, repo.replaceCalls.size)
            val replace = repo.replaceCalls.first()
            assertTrue(replace.containsKey("appBar"))
            assertEquals("#33000000", replace["card"])

            val bg = repo.setBackgroundCalls.first()
            assertEquals("file:///mock/bg.jpg", bg.url)
            assertNotNull(bg.url)
            assertEquals(SelectedTheme.CUSTOM, repo.selectCalls.first())
        }

    @Test
    fun `onImagePicked aborts silently when copy fails`() = runTest(mainDispatcherRule.dispatcher) {
        val loader = FakeImageAndPaletteLoader(copiedUri = null)
        val (vm, repo, _) = createViewModel(loader = loader)

        vm.onImagePicked(Uri.parse("content://input/source.jpg"))
        advanceUntilIdle()

        assertTrue(repo.callOrder.isEmpty())
    }
}

private data class SetBackgroundCall(val url: String?, val blur: Float?, val opacity: Float?)

private class FakeThemeRepository : ThemeRepository {

    val stateFlow: MutableStateFlow<ThemeUiState> = MutableStateFlow(
        ThemeUiState(
            selected = SelectedTheme.CUSTOM,
            effectiveColors = DarkMusicFreeColors,
            background = null,
            followSystem = false,
            isLoading = false,
        ),
    )

    override val state: Flow<ThemeUiState> = stateFlow

    val selectCalls = mutableListOf<SelectedTheme>()
    val followSystemCalls = mutableListOf<Pair<Boolean, Boolean>>()
    val setBackgroundCalls = mutableListOf<SetBackgroundCall>()
    val patchCalls = mutableListOf<Map<String, String>>()
    val replaceCalls = mutableListOf<Map<String, String>>()
    val callOrder = mutableListOf<String>()

    override suspend fun selectTheme(theme: SelectedTheme) {
        selectCalls += theme
        callOrder += "selectTheme"
    }

    override suspend fun setFollowSystem(enabled: Boolean, currentSystemDark: Boolean) {
        followSystemCalls += enabled to currentSystemDark
        callOrder += "setFollowSystem"
    }

    override suspend fun setBackground(url: String?, blur: Float?, opacity: Float?) {
        setBackgroundCalls += SetBackgroundCall(url, blur, opacity)
        callOrder += "setBackground"
    }

    override suspend fun patchCustomColors(patch: Map<String, String>) {
        patchCalls += patch
        callOrder += "patchCustomColors"
    }

    override suspend fun replaceCustomColors(colors: Map<String, String>) {
        replaceCalls += colors
        callOrder += "replaceCustomColors"
    }
}

private class FakeImageAndPaletteLoader(
    private val copiedUri: Uri? = Uri.parse("file:///mock/bg.jpg"),
    private val palette: PaletteColors? = PaletteColors(
        primary = Color(red = 230, green = 30, blue = 30, alpha = 255),
        average = Color(red = 230, green = 30, blue = 30, alpha = 255),
        vibrant = Color(red = 230, green = 30, blue = 30, alpha = 255),
    ),
) : ImageAndPaletteLoader {
    override suspend fun copyImageToInternal(context: Context, uri: Uri): Uri? = copiedUri
    override suspend fun extractPalette(context: Context, uri: Uri): PaletteColors? = palette
}

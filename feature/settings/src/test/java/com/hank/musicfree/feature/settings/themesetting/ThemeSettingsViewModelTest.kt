package com.hank.musicfree.feature.settings.themesetting

import com.hank.musicfree.core.theme.DarkMusicFreeColors
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(
        repo: FakeThemeRepository = FakeThemeRepository(),
    ): Pair<ThemeSettingsViewModel, FakeThemeRepository> {
        val vm = ThemeSettingsViewModel(themeRepository = repo)
        return vm to repo
    }

    @Test
    fun `onFollowSystemToggle true with system dark forwards to repo`() =
        runTest(mainDispatcherRule.dispatcher) {
            val (vm, repo) = createViewModel()

            vm.onFollowSystemToggle(enabled = true, systemDark = true)
            advanceUntilIdle()

            assertEquals(listOf(true to true), repo.followSystemCalls)
            assertTrue(repo.selectCalls.isEmpty())
        }

    @Test
    fun `onFollowSystemToggle false with system light forwards to repo`() =
        runTest(mainDispatcherRule.dispatcher) {
            val (vm, repo) = createViewModel()

            vm.onFollowSystemToggle(enabled = false, systemDark = false)
            advanceUntilIdle()

            assertEquals(listOf(false to false), repo.followSystemCalls)
            assertTrue(repo.selectCalls.isEmpty())
        }

    @Test
    fun `onSelectTheme P_LIGHT forwards to repo selectTheme`() =
        runTest(mainDispatcherRule.dispatcher) {
            val (vm, repo) = createViewModel()

            vm.onSelectTheme(SelectedTheme.P_LIGHT)
            advanceUntilIdle()

            assertEquals(listOf(SelectedTheme.P_LIGHT), repo.selectCalls)
        }

    @Test
    fun `onSelectTheme CUSTOM forwards to repo selectTheme`() =
        runTest(mainDispatcherRule.dispatcher) {
            val (vm, repo) = createViewModel()

            vm.onSelectTheme(SelectedTheme.CUSTOM)
            advanceUntilIdle()

            assertEquals(listOf(SelectedTheme.CUSTOM), repo.selectCalls)
        }
}

private class FakeThemeRepository : ThemeRepository {

    val stateFlow: MutableStateFlow<ThemeUiState> = MutableStateFlow(
        ThemeUiState(
            selected = SelectedTheme.P_DARK,
            effectiveColors = DarkMusicFreeColors,
            background = null,
            followSystem = false,
            isLoading = false,
        ),
    )

    override val state: Flow<ThemeUiState> = stateFlow

    val selectCalls = mutableListOf<SelectedTheme>()
    val followSystemCalls = mutableListOf<Pair<Boolean, Boolean>>()
    val setBackgroundCalls = mutableListOf<Triple<String?, Float?, Float?>>()
    val patchCalls = mutableListOf<Map<String, String>>()
    val replaceCalls = mutableListOf<Map<String, String>>()

    override suspend fun selectTheme(theme: SelectedTheme) {
        selectCalls += theme
    }

    override suspend fun setFollowSystem(enabled: Boolean, currentSystemDark: Boolean) {
        followSystemCalls += enabled to currentSystemDark
    }

    override suspend fun setBackground(url: String?, blur: Float?, opacity: Float?) {
        setBackgroundCalls += Triple(url, blur, opacity)
    }

    override suspend fun patchCustomColors(patch: Map<String, String>) {
        patchCalls += patch
    }

    override suspend fun replaceCustomColors(colors: Map<String, String>) {
        replaceCalls += colors
    }
}

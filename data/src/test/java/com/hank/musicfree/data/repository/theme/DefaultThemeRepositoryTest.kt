package com.hank.musicfree.data.repository.theme

import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.hank.musicfree.core.theme.DarkMusicFreeColors
import com.hank.musicfree.core.theme.LightMusicFreeColors
import com.hank.musicfree.core.theme.runtime.SelectedTheme
import com.hank.musicfree.data.datastore.AppPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultThemeRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: AppPreferences
    private lateinit var repo: DefaultThemeRepository

    @Before
    fun setup() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = {
                tmpFolder.newFile("theme-${UUID.randomUUID()}.preferences_pb")
            },
        )
        prefs = AppPreferences(dataStore)
        repo = DefaultThemeRepository(prefs)
    }

    @After
    fun teardown() {
        // testScope's underlying scope owns the DataStore scope; nothing to
        // cancel explicitly because TestScope tears down at end of runTest.
    }

    @Test
    fun `default state is P_DARK with dark palette and no background`() = testScope.runTest {
        advanceUntilIdle()
        val state = repo.state.first()
        assertEquals(SelectedTheme.P_DARK, state.selected)
        assertEquals(DarkMusicFreeColors, state.effectiveColors)
        assertNull(state.background)
        assertFalse(state.followSystem)
        assertFalse(state.isLoading)
    }

    @Test
    fun `selectTheme P_LIGHT switches to light palette`() = testScope.runTest {
        repo.selectTheme(SelectedTheme.P_LIGHT)
        advanceUntilIdle()
        val state = repo.state.first()
        assertEquals(SelectedTheme.P_LIGHT, state.selected)
        assertEquals(LightMusicFreeColors, state.effectiveColors)
        assertNull(state.background)
    }

    @Test
    fun `CUSTOM with patched primary color overlays dark base`() = testScope.runTest {
        repo.selectTheme(SelectedTheme.CUSTOM)
        repo.patchCustomColors(mapOf("primary" to "#FFFF0000"))
        advanceUntilIdle()
        val state = repo.state.first()
        assertEquals(SelectedTheme.CUSTOM, state.selected)
        assertEquals(Color(0xFFFF0000), state.effectiveColors.primary)
        // Other colors must remain from dark base.
        assertEquals(DarkMusicFreeColors.text, state.effectiveColors.text)
    }

    @Test
    fun `setBackground then CUSTOM exposes background info`() = testScope.runTest {
        repo.setBackground(url = "file:///a.jpg", blur = 10f, opacity = 0.5f)
        repo.selectTheme(SelectedTheme.CUSTOM)
        advanceUntilIdle()
        val state = repo.state.first()
        val bg = state.background
        assertTrue(bg != null)
        assertEquals("file:///a.jpg", bg!!.url)
        assertEquals(10f, bg.blur)
        assertEquals(0.5f, bg.opacity)
    }

    @Test
    fun `P_DARK ignores persisted background url`() = testScope.runTest {
        repo.setBackground(url = "file:///a.jpg", blur = 10f, opacity = 0.5f)
        repo.selectTheme(SelectedTheme.P_DARK)
        advanceUntilIdle()
        val state = repo.state.first()
        assertEquals(SelectedTheme.P_DARK, state.selected)
        assertNull(state.background)
    }

    @Test
    fun `setFollowSystem enabled with dark system selects P_DARK`() = testScope.runTest {
        repo.setFollowSystem(enabled = true, currentSystemDark = true)
        advanceUntilIdle()
        val state = repo.state.first()
        assertTrue(state.followSystem)
        assertEquals(SelectedTheme.P_DARK, state.selected)
    }

    @Test
    fun `setFollowSystem enabled with light system selects P_LIGHT`() = testScope.runTest {
        repo.setFollowSystem(enabled = true, currentSystemDark = false)
        advanceUntilIdle()
        val state = repo.state.first()
        assertTrue(state.followSystem)
        assertEquals(SelectedTheme.P_LIGHT, state.selected)
    }

    @Test
    fun `replaceCustomColors overwrites whole map`() = testScope.runTest {
        repo.patchCustomColors(mapOf("primary" to "#FFFF0000", "text" to "#FF0000FF"))
        repo.replaceCustomColors(mapOf("primary" to "#FF00FF00"))
        repo.selectTheme(SelectedTheme.CUSTOM)
        advanceUntilIdle()
        val state = repo.state.first()
        assertEquals(Color(0xFF00FF00), state.effectiveColors.primary)
        // text override removed, falls back to dark base.
        assertEquals(DarkMusicFreeColors.text, state.effectiveColors.text)
    }
}

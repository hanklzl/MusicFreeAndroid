package com.zili.android.musicfreeandroid.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.RepeatMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AppPreferencesTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: AppPreferences

    @Before
    fun setup() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("test_prefs.preferences_pb") },
        )
        prefs = AppPreferences(dataStore)
    }

    @Test
    fun `default repeat mode is OFF`() = testScope.runTest {
        assertEquals(RepeatMode.OFF, prefs.repeatMode.first())
    }

    @Test
    fun `set and get repeat mode`() = testScope.runTest {
        prefs.setRepeatMode(RepeatMode.ONE)
        assertEquals(RepeatMode.ONE, prefs.repeatMode.first())
    }

    @Test
    fun `default quality is STANDARD`() = testScope.runTest {
        assertEquals(PlayQuality.STANDARD, prefs.playQuality.first())
    }

    @Test
    fun `set and get quality`() = testScope.runTest {
        prefs.setPlayQuality(PlayQuality.SUPER)
        assertEquals(PlayQuality.SUPER, prefs.playQuality.first())
    }

    @Test
    fun `default shuffle is false`() = testScope.runTest {
        assertFalse(prefs.shuffleEnabled.first())
    }

    @Test
    fun `set and get shuffle`() = testScope.runTest {
        prefs.setShuffleEnabled(true)
        assertTrue(prefs.shuffleEnabled.first())
    }

    @Test
    fun `default dark mode is null (follow system)`() = testScope.runTest {
        assertNull(prefs.darkMode.first())
    }

    @Test
    fun `set dark mode explicitly`() = testScope.runTest {
        prefs.setDarkMode(true)
        assertEquals(true, prefs.darkMode.first())
    }

    @Test
    fun `default current music index is -1`() = testScope.runTest {
        assertEquals(-1, prefs.currentMusicIndex.first())
    }

    @Test
    fun `set current music index`() = testScope.runTest {
        prefs.setCurrentMusicIndex(5)
        assertEquals(5, prefs.currentMusicIndex.first())
    }
}

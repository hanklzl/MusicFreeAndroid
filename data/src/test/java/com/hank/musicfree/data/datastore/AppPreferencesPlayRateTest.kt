package com.hank.musicfree.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.hank.musicfree.core.model.PlaybackSpeeds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AppPreferencesPlayRateTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: AppPreferences

    @Before
    fun setup() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tempFolder.newFile("test_prefs_play_rate.preferences_pb") },
        )
        prefs = AppPreferences(dataStore)
    }

    @Test
    fun `playRate defaults to 1_0`() = testScope.runTest {
        assertEquals(PlaybackSpeeds.DEFAULT, prefs.playRate.first())
    }

    @Test
    fun `setPlayRate persists value across reads`() = testScope.runTest {
        prefs.setPlayRate(1.5f)
        assertEquals(1.5f, prefs.playRate.first())
    }
}

package com.hank.musicfree.updater.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdatePreferencesTest {

    private lateinit var store: DataStore<Preferences>
    private lateinit var prefs: UpdatePreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        store = PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("updater_prefs_test") },
        )
        prefs = UpdatePreferences(store)
    }

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.preferencesDataStoreFile("updater_prefs_test").delete()
    }

    @Test
    fun `skip version write then read`() = runTest {
        assertNull(prefs.getSkipVersion())
        prefs.setSkipVersion("1.2.3")
        assertEquals("1.2.3", prefs.getSkipVersion())
    }

    @Test
    fun `clear skip version writes null`() = runTest {
        prefs.setSkipVersion("1.2.3")
        prefs.clearSkipVersion()
        assertNull(prefs.getSkipVersion())
    }

    @Test
    fun `last checked at round trip`() = runTest {
        prefs.setLastCheckedAt(1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, prefs.getLastCheckedAt())
    }

    @Test
    fun `last seen version round trip`() = runTest {
        prefs.setLastSeenVersion("1.2.3")
        assertEquals("1.2.3", prefs.getLastSeenVersion())
    }
}

package com.hank.musicfree.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.QualityFallbackOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadPreferencesKeysTest {

    @get:Rule val tmp = TemporaryFolder()
    private lateinit var prefs: AppPreferences

    @Before fun setup() {
        val file = File(tmp.newFolder(), "app_preferences.preferences_pb")
        val store = PreferenceDataStoreFactory.create(produceFile = { file })
        prefs = AppPreferences(store)
    }

    @After fun teardown() = Unit

    @Test fun maxDownloadDefaultIs3AndClampedTo1To10() = runTest {
        assertEquals(3, prefs.maxDownload.first())
        prefs.setMaxDownload(0)
        assertEquals(1, prefs.maxDownload.first())
        prefs.setMaxDownload(99)
        assertEquals(10, prefs.maxDownload.first())
        prefs.setMaxDownload(5)
        assertEquals(5, prefs.maxDownload.first())
    }

    @Test fun useCellularDownloadDefaultsToFalse() = runTest {
        assertFalse(prefs.useCellularDownload.first())
        prefs.setUseCellularDownload(true)
        assertTrue(prefs.useCellularDownload.first())
    }

    @Test fun defaultDownloadQualityDefaultsToStandard() = runTest {
        assertEquals(PlayQuality.STANDARD, prefs.defaultDownloadQuality.first())
        prefs.setDefaultDownloadQuality(PlayQuality.HIGH)
        assertEquals(PlayQuality.HIGH, prefs.defaultDownloadQuality.first())
    }

    @Test fun downloadQualityOrderDefaultsToAsc() = runTest {
        assertEquals(QualityFallbackOrder.Asc, prefs.downloadQualityOrder.first())
        prefs.setDownloadQualityOrder(QualityFallbackOrder.Desc)
        assertEquals(QualityFallbackOrder.Desc, prefs.downloadQualityOrder.first())
    }

    @Test fun downloadDirRelativeDefaultsToMusicMusicFree() = runTest {
        assertEquals("Music/MusicFree/", prefs.downloadDirRelative.first())
    }
}

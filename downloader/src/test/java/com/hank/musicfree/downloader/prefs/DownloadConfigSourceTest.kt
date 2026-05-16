package com.hank.musicfree.downloader.prefs

import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.QualityFallbackOrder
import com.hank.musicfree.data.datastore.AppPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadConfigSourceTest {

    @Test
    fun stateIncludesDownloadQualityOrderFromPreferences() = runTest {
        val appPreferences = mockk<AppPreferences>()
        every { appPreferences.maxDownload } returns flowOf(4)
        every { appPreferences.useCellularDownload } returns flowOf(true)
        every { appPreferences.defaultDownloadQuality } returns flowOf(PlayQuality.HIGH)
        every { appPreferences.downloadQualityOrder } returns flowOf(QualityFallbackOrder.Desc)
        every { appPreferences.downloadDirRelative } returns flowOf("Music/Custom/")

        val config = withTimeout(5_000L) {
            DownloadConfigSource(appPreferences, backgroundScope).state.first {
                it.downloadQualityOrder == QualityFallbackOrder.Desc
            }
        }

        assertEquals(4, config.maxDownload)
        assertEquals(true, config.useCellularDownload)
        assertEquals(PlayQuality.HIGH, config.defaultDownloadQuality)
        assertEquals(QualityFallbackOrder.Desc, config.downloadQualityOrder)
        assertEquals("Music/Custom/", config.downloadDirRelative)
    }
}

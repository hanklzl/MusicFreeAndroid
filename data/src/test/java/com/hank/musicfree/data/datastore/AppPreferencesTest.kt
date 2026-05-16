package com.hank.musicfree.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.hank.musicfree.core.model.AlbumMusicClickAction
import com.hank.musicfree.core.model.AudioInterruptionAction
import com.hank.musicfree.core.model.MusicDetailDefaultPage
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.QualityFallbackOrder
import com.hank.musicfree.core.model.RepeatMode
import com.hank.musicfree.core.model.SearchResultClickAction
import com.hank.musicfree.core.model.SortMode
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.LogFields
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
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
    private lateinit var logger: CapturingLogger

    @Before
    fun setup() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("test_prefs.preferences_pb") },
        )
        prefs = AppPreferences(dataStore)
        logger = CapturingLogger()
        MfLog.install(logger)
    }

    @After
    fun teardown() {
        MfLog.resetForTest()
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

    @Test
    fun `default storage directory uri is null`() = testScope.runTest {
        assertNull(prefs.storageDirectoryUri.first())
    }

    @Test
    fun `set and get storage directory uri`() = testScope.runTest {
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusicFree"

        prefs.setStorageDirectoryUri(treeUri)

        assertEquals(treeUri, prefs.storageDirectoryUri.first())
    }

    @Test
    fun `clearing storage directory uri removes persisted value`() = testScope.runTest {
        prefs.setStorageDirectoryUri("content://com.android.externalstorage.documents/tree/primary%3AMusicFree")

        prefs.setStorageDirectoryUri(null)

        assertNull(prefs.storageDirectoryUri.first())
    }

    @Test
    fun `runtime setting writes emit settings_write diagnostics`() = testScope.runTest {
        prefs.setMaxDownload(20)

        val event = logger.events.single { it.event == "settings_write" }
        assertEquals(LogCategory.SETTINGS, event.category)
        assertEquals("max_download", event.fields["key"])
        assertEquals(10, event.fields["value"])
        assertEquals(LogFields.Result.SUCCESS, event.fields["result"])
    }

    @Test
    fun `default lyric show translation is false`() = testScope.runTest {
        assertFalse(prefs.lyricShowTranslation.first())
    }

    @Test
    fun `set lyric show translation`() = testScope.runTest {
        prefs.setLyricShowTranslation(true)
        assertTrue(prefs.lyricShowTranslation.first())
    }

    @Test
    fun `default lyric detail font size is one`() = testScope.runTest {
        assertEquals(1, prefs.lyricDetailFontSize.first())
    }

    @Test
    fun `set lyric detail font size coerces to supported range`() = testScope.runTest {
        prefs.setLyricDetailFontSize(9)
        assertEquals(3, prefs.lyricDetailFontSize.first())

        prefs.setLyricDetailFontSize(-1)
        assertEquals(0, prefs.lyricDetailFontSize.first())
    }

    @Test
    fun `lyric detail font size read coerces persisted out of range value`() = testScope.runTest {
        dataStore.edit { it[intPreferencesKey("lyric_detail_font_size")] = 9 }

        assertEquals(3, prefs.lyricDetailFontSize.first())
    }

    @Test
    fun `default lyric auto search is true`() = testScope.runTest {
        assertTrue(prefs.lyricAutoSearchEnabled.first())
    }

    @Test
    fun `set lyric auto search`() = testScope.runTest {
        prefs.setLyricAutoSearchEnabled(false)
        assertFalse(prefs.lyricAutoSearchEnabled.first())
    }

    @Test
    fun `default basic runtime settings match RN defaults`() = testScope.runTest {
        assertEquals(50, prefs.maxSearchHistoryLength.first())
        assertEquals(MusicDetailDefaultPage.Album, prefs.musicDetailDefaultPage.first())
        assertFalse(prefs.musicDetailAwake.first())
        assertEquals(SearchResultClickAction.PlayMusic, prefs.clickMusicInSearch.first())
        assertEquals(AlbumMusicClickAction.PlayAlbum, prefs.clickMusicInAlbum.first())
        assertEquals(SortMode.Manual, prefs.musicOrderInLocalSheet.first())
        assertEquals(PlayQuality.STANDARD, prefs.defaultPlayQuality.first())
        assertEquals(QualityFallbackOrder.Asc, prefs.playQualityOrder.first())
        assertFalse(prefs.useCellularPlay.first())
        assertFalse(prefs.allowConcurrentPlayback.first())
        assertFalse(prefs.autoPlayWhenAppStart.first())
        assertFalse(prefs.tryChangeSourceWhenPlayFail.first())
        assertFalse(prefs.autoStopWhenError.first())
        assertEquals(AudioInterruptionAction.Pause, prefs.audioInterruptionAction.first())
        assertEquals(0.5f, prefs.audioInterruptionDuckVolume.first())
        assertEquals(512L * 1024L * 1024L, prefs.maxMusicCacheSizeBytes.first())
        assertFalse(prefs.autoUpdatePlugins.first())
        assertFalse(prefs.skipPluginVersionCheck.first())
        assertFalse(prefs.lazyLoadPlugins.first())
        assertEquals(0L, prefs.pluginAutoUpdateLastAtEpochMs.first())
    }

    @Test
    fun `set and get basic runtime settings`() = testScope.runTest {
        prefs.setMaxSearchHistoryLength(100)
        prefs.setMusicDetailDefaultPage(MusicDetailDefaultPage.Lyric)
        prefs.setMusicDetailAwake(true)
        prefs.setClickMusicInSearch(SearchResultClickAction.PlayMusicAndReplace)
        prefs.setClickMusicInAlbum(AlbumMusicClickAction.PlayMusic)
        prefs.setMusicOrderInLocalSheet(SortMode.Title)
        prefs.setDefaultPlayQuality(PlayQuality.SUPER)
        prefs.setPlayQualityOrder(QualityFallbackOrder.Desc)
        prefs.setUseCellularPlay(true)
        prefs.setAllowConcurrentPlayback(true)
        prefs.setAutoPlayWhenAppStart(true)
        prefs.setTryChangeSourceWhenPlayFail(true)
        prefs.setAutoStopWhenError(true)
        prefs.setAudioInterruptionAction(AudioInterruptionAction.LowerVolume)
        prefs.setAudioInterruptionDuckVolume(0.8f)
        prefs.setMaxMusicCacheSizeBytes(1024L * 1024L * 1024L)
        prefs.setAutoUpdatePlugins(true)
        prefs.setSkipPluginVersionCheck(true)
        prefs.setLazyLoadPlugins(true)
        prefs.setPluginAutoUpdateLastAtEpochMs(1234L)

        assertEquals(100, prefs.maxSearchHistoryLength.first())
        assertEquals(MusicDetailDefaultPage.Lyric, prefs.musicDetailDefaultPage.first())
        assertTrue(prefs.musicDetailAwake.first())
        assertEquals(SearchResultClickAction.PlayMusicAndReplace, prefs.clickMusicInSearch.first())
        assertEquals(AlbumMusicClickAction.PlayMusic, prefs.clickMusicInAlbum.first())
        assertEquals(SortMode.Title, prefs.musicOrderInLocalSheet.first())
        assertEquals(PlayQuality.SUPER, prefs.defaultPlayQuality.first())
        assertEquals(QualityFallbackOrder.Desc, prefs.playQualityOrder.first())
        assertTrue(prefs.useCellularPlay.first())
        assertTrue(prefs.allowConcurrentPlayback.first())
        assertTrue(prefs.autoPlayWhenAppStart.first())
        assertTrue(prefs.tryChangeSourceWhenPlayFail.first())
        assertTrue(prefs.autoStopWhenError.first())
        assertEquals(AudioInterruptionAction.LowerVolume, prefs.audioInterruptionAction.first())
        assertEquals(0.8f, prefs.audioInterruptionDuckVolume.first())
        assertEquals(1024L * 1024L * 1024L, prefs.maxMusicCacheSizeBytes.first())
        assertTrue(prefs.autoUpdatePlugins.first())
        assertTrue(prefs.skipPluginVersionCheck.first())
        assertTrue(prefs.lazyLoadPlugins.first())
        assertEquals(1234L, prefs.pluginAutoUpdateLastAtEpochMs.first())
    }

    @Test
    fun `cache and duck volume settings coerce to supported range`() = testScope.runTest {
        prefs.setMaxMusicCacheSizeBytes(10L * 1024L * 1024L)
        assertEquals(100L * 1024L * 1024L, prefs.maxMusicCacheSizeBytes.first())

        prefs.setMaxMusicCacheSizeBytes(9000L * 1024L * 1024L)
        assertEquals(8192L * 1024L * 1024L, prefs.maxMusicCacheSizeBytes.first())

        prefs.setAudioInterruptionDuckVolume(2f)
        assertEquals(1f, prefs.audioInterruptionDuckVolume.first())

        prefs.setAudioInterruptionDuckVolume(0f)
        assertEquals(0.1f, prefs.audioInterruptionDuckVolume.first())
    }

    @Test
    fun `search history is trimmed by configured max history length`() = testScope.runTest {
        prefs.setMaxSearchHistoryLength(3)

        repeat(5) { index ->
            prefs.addSearchQuery("query-$index")
        }

        assertEquals(
            listOf("query-4", "query-3", "query-2"),
            prefs.searchHistory.first(),
        )
    }

    @Test
    fun `default currentMusicPositionMs is 0`() = testScope.runTest {
        assertEquals(0L, prefs.currentMusicPositionMs.first())
    }

    @Test
    fun `set and get currentMusicPositionMs`() = testScope.runTest {
        prefs.setCurrentMusicPositionMs(123_456L)
        assertEquals(123_456L, prefs.currentMusicPositionMs.first())
    }

    @Test
    fun `default currentMusicDurationMs is 0`() = testScope.runTest {
        assertEquals(0L, prefs.currentMusicDurationMs.first())
    }

    @Test
    fun `set and get currentMusicDurationMs`() = testScope.runTest {
        prefs.setCurrentMusicDurationMs(987_654L)
        assertEquals(987_654L, prefs.currentMusicDurationMs.first())
    }
}

private data class CapturedLogEvent(
    val category: LogCategory,
    val event: String,
    val fields: Map<String, Any?>,
)

private class CapturingLogger : MfLogger {
    val events = mutableListOf<CapturedLogEvent>()

    override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
        events += CapturedLogEvent(category, event, fields)
    }

    override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
        events += CapturedLogEvent(category, event, fields)
    }

    override fun error(
        category: LogCategory,
        event: String,
        throwable: Throwable?,
        fields: Map<String, Any?>,
    ) {
        events += CapturedLogEvent(category, event, fields + ("throwable" to throwable))
    }

    override fun flush() = Unit
}

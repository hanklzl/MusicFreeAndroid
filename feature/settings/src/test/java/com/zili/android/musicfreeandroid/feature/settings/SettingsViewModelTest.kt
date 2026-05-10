package com.zili.android.musicfreeandroid.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zili.android.musicfreeandroid.core.model.AlbumMusicClickAction
import com.zili.android.musicfreeandroid.core.model.MusicDetailDefaultPage
import com.zili.android.musicfreeandroid.core.model.PlayQuality
import com.zili.android.musicfreeandroid.core.model.QualityFallbackOrder
import com.zili.android.musicfreeandroid.core.model.SearchResultClickAction
import com.zili.android.musicfreeandroid.core.model.SortMode
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.logging.FeedbackLogExporterContract
import com.zili.android.musicfreeandroid.logging.FeedbackPackage
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.logging.MfLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val dataStoreScopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        dataStoreScopes.forEach { scope ->
            scope.cancel()
        }
        dataStoreScopes.clear()
        MfLog.resetForTest()
    }

    @Test
    fun `storage access state is unconfigured by default`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(createAppPreferences())

        val state = viewModel.storageAccessState.value

        assertTrue(!state.isConfigured)
        assertNull(state.selectedDirectory)
    }

    @Test
    fun `set storage directory persists selected tree uri`() = runTest(mainDispatcherRule.dispatcher) {
        val appPreferences = createAppPreferences()
        val viewModel = createViewModel(appPreferences)
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusicFree"

        viewModel.setStorageDirectory(treeUri)
        advanceUntilIdle()

        val persisted = appPreferences.storageDirectoryUri.first()
        assertEquals(treeUri, persisted)
    }

    @Test
    fun `basic settings state exposes default runtime-backed preferences`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(createAppPreferences())

        val state = viewModel.basicSettingsUiState.value

        assertEquals(50, state.maxSearchHistoryLength)
        assertEquals(MusicDetailDefaultPage.Album, state.musicDetailDefaultPage)
        assertEquals(false, state.musicDetailAwake)
        assertEquals(SearchResultClickAction.PlayMusic, state.clickMusicInSearch)
        assertEquals(AlbumMusicClickAction.PlayAlbum, state.clickMusicInAlbum)
        assertEquals(SortMode.Manual, state.musicOrderInLocalSheet)
        assertEquals(PlayQuality.STANDARD, state.defaultPlayQuality)
        assertEquals(QualityFallbackOrder.Asc, state.playQualityOrder)
        assertEquals(3, state.maxDownload)
        assertEquals(PlayQuality.STANDARD, state.defaultDownloadQuality)
        assertEquals(QualityFallbackOrder.Asc, state.downloadQualityOrder)
        assertEquals(false, state.useCellularPlay)
        assertEquals(false, state.useCellularDownload)
        assertEquals(true, state.lyricAutoSearchEnabled)
        assertTrue(!state.storageAccessState.isConfigured)
    }

    @Test
    fun `basic settings setters update collected runtime-backed state and preferences`() =
        runTest(mainDispatcherRule.dispatcher) {
            val appPreferences = createAppPreferences()
            val viewModel = createViewModel(appPreferences)
            val job = backgroundScope.launch { viewModel.basicSettingsUiState.collect {} }
            advanceUntilIdle()

            viewModel.setMaxSearchHistoryLength(100)
            viewModel.setMusicDetailDefaultPage(MusicDetailDefaultPage.Lyric)
            viewModel.setMusicDetailAwake(true)
            viewModel.setClickMusicInSearch(SearchResultClickAction.PlayMusicAndReplace)
            viewModel.setClickMusicInAlbum(AlbumMusicClickAction.PlayMusic)
            viewModel.setMusicOrderInLocalSheet(SortMode.Title)
            viewModel.setDefaultPlayQuality(PlayQuality.HIGH)
            viewModel.setPlayQualityOrder(QualityFallbackOrder.Desc)
            viewModel.setMaxDownload(7)
            viewModel.setDefaultDownloadQuality(PlayQuality.SUPER)
            viewModel.setDownloadQualityOrder(QualityFallbackOrder.Desc)
            viewModel.setUseCellularPlay(true)
            viewModel.setUseCellularDownload(true)
            viewModel.setLyricAutoSearchEnabled(false)
            advanceUntilIdle()

            val state = viewModel.basicSettingsUiState.value
            assertEquals(100, state.maxSearchHistoryLength)
            assertEquals(MusicDetailDefaultPage.Lyric, state.musicDetailDefaultPage)
            assertEquals(true, state.musicDetailAwake)
            assertEquals(SearchResultClickAction.PlayMusicAndReplace, state.clickMusicInSearch)
            assertEquals(AlbumMusicClickAction.PlayMusic, state.clickMusicInAlbum)
            assertEquals(SortMode.Title, state.musicOrderInLocalSheet)
            assertEquals(PlayQuality.HIGH, state.defaultPlayQuality)
            assertEquals(QualityFallbackOrder.Desc, state.playQualityOrder)
            assertEquals(7, state.maxDownload)
            assertEquals(PlayQuality.SUPER, state.defaultDownloadQuality)
            assertEquals(QualityFallbackOrder.Desc, state.downloadQualityOrder)
            assertEquals(true, state.useCellularPlay)
            assertEquals(true, state.useCellularDownload)
            assertEquals(false, state.lyricAutoSearchEnabled)
            assertTrue(!state.storageAccessState.isConfigured)
            assertEquals(100, appPreferences.maxSearchHistoryLength.first())
            assertEquals(MusicDetailDefaultPage.Lyric, appPreferences.musicDetailDefaultPage.first())
            assertEquals(true, appPreferences.musicDetailAwake.first())
            assertEquals(SearchResultClickAction.PlayMusicAndReplace, appPreferences.clickMusicInSearch.first())
            assertEquals(AlbumMusicClickAction.PlayMusic, appPreferences.clickMusicInAlbum.first())
            assertEquals(SortMode.Title, appPreferences.musicOrderInLocalSheet.first())
            assertEquals(PlayQuality.HIGH, appPreferences.defaultPlayQuality.first())
            assertEquals(QualityFallbackOrder.Desc, appPreferences.playQualityOrder.first())
            assertEquals(7, appPreferences.maxDownload.first())
            assertEquals(PlayQuality.SUPER, appPreferences.defaultDownloadQuality.first())
            assertEquals(QualityFallbackOrder.Desc, appPreferences.downloadQualityOrder.first())
            assertEquals(true, appPreferences.useCellularPlay.first())
            assertEquals(true, appPreferences.useCellularDownload.first())
            assertEquals(false, appPreferences.lyricAutoSearchEnabled.first())
            job.cancel()
        }

    @Test
    fun `create feedback package updates pending package`() = runTest(mainDispatcherRule.dispatcher) {
        val expectedPackage = createFeedbackPackage("generated-feedback.zip")
        val exporter = createFakeExporter(
            feedbackPackage = expectedPackage,
        )
        val viewModel = createViewModel(createAppPreferences(), exporter)

        viewModel.createFeedbackPackage()
        advanceUntilIdle()

        assertSame(expectedPackage, viewModel.feedbackExportUiState.value.pendingPackage)
        assertEquals(1, exporter.createCalls)
        assertFalse(viewModel.feedbackExportUiState.value.isOperationInProgress)
    }

    @Test
    fun `shared package can be consumed`() = runTest(mainDispatcherRule.dispatcher) {
        val expectedPackage = createFeedbackPackage("generated-feedback.zip")
        val exporter = createFakeExporter(feedbackPackage = expectedPackage)
        val viewModel = createViewModel(createAppPreferences(), exporter)

        viewModel.createFeedbackPackage()
        advanceUntilIdle()

        assertEquals(expectedPackage, viewModel.feedbackExportUiState.value.pendingPackage)
        viewModel.onFeedbackPackageShared()

        assertNull(viewModel.feedbackExportUiState.value.pendingPackage)
    }

    @Test
    fun `create feedback package ignores concurrent clicks`() = runTest(mainDispatcherRule.dispatcher) {
        val exporter = createFakeExporter(createDelayMs = 100)
        val viewModel = createViewModel(createAppPreferences(), exporter)

        viewModel.createFeedbackPackage()
        viewModel.createFeedbackPackage()
        advanceUntilIdle()

        assertEquals(1, exporter.createCalls)
        assertEquals(1, exporter.maxConcurrentCreateCalls)
    }

    @Test
    fun `clear logs is blocked by create in progress`() = runTest(mainDispatcherRule.dispatcher) {
        val exporter = createFakeExporter(createDelayMs = 100)
        val viewModel = createViewModel(createAppPreferences(), exporter)

        viewModel.createFeedbackPackage()
        viewModel.clearLogs()
        advanceUntilIdle()

        assertEquals(1, exporter.createCalls)
        assertEquals(0, exporter.clearCalls)
        assertEquals(1, exporter.maxConcurrentCreateCalls)
        assertEquals(0, exporter.maxConcurrentClearCalls)
    }

    @Test
    fun `clear logs calls exporter`() = runTest(mainDispatcherRule.dispatcher) {
        val exporter = createFakeExporter()
        val viewModel = createViewModel(createAppPreferences(), exporter)

        viewModel.clearLogs()
        advanceUntilIdle()

        assertEquals(1, exporter.clearCalls)
    }

    @Test
    fun `clear logs ignores concurrent clicks`() = runTest(mainDispatcherRule.dispatcher) {
        val exporter = createFakeExporter(clearDelayMs = 100)
        val viewModel = createViewModel(createAppPreferences(), exporter)

        viewModel.clearLogs()
        viewModel.clearLogs()
        advanceUntilIdle()

        assertEquals(1, exporter.clearCalls)
        assertEquals(1, exporter.maxConcurrentClearCalls)
    }

    @Test
    fun `create feedback package logs error when exporter throws`() = runTest(mainDispatcherRule.dispatcher) {
        val error = RuntimeException("feedback export failed")
        val exporter = createFakeExporter(exception = error)
        val logger = RecordingLogger()
        MfLog.install(logger)

        val viewModel = createViewModel(createAppPreferences(), exporter)
        viewModel.createFeedbackPackage()
        advanceUntilIdle()

        assertEquals(error.message, viewModel.feedbackExportUiState.value.errorMessage)
        assertTrue(
            logger.errorEvents.any {
                it.category == LogCategory.FEEDBACK &&
                    it.event == "feedback_package_create_failed" &&
                    it.throwable == error
            },
        )
    }

    private fun createAppPreferences(): AppPreferences {
        val scope = CoroutineScope(SupervisorJob() + mainDispatcherRule.dispatcher)
        dataStoreScopes += scope
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tmpFolder.newFile("settings-test.preferences_pb") },
        )
        return AppPreferences(dataStore)
    }

    private fun createViewModel(
        appPreferences: AppPreferences,
        exporter: FeedbackLogExporterContract = createFakeExporter(),
    ): SettingsViewModel {
        return SettingsViewModel(appPreferences, exporter)
    }

    private fun createFeedbackPackage(fileName: String): FeedbackPackage {
        val file = tmpFolder.newFile(fileName)
        return FeedbackPackage(file = file, fileName = fileName, sizeBytes = file.length())
    }

    private fun createFakeExporter(
        exception: Throwable? = null,
        feedbackPackage: FeedbackPackage = createFeedbackPackage("feedback.zip"),
        createDelayMs: Long = 0,
        clearDelayMs: Long = 0,
    ): FakeFeedbackLogExporter {
        return FakeFeedbackLogExporter(
            feedbackPackage = feedbackPackage,
            exception = exception,
            createDelayMs = createDelayMs,
            clearDelayMs = clearDelayMs,
        )
    }

    private class FakeFeedbackLogExporter(
        private val feedbackPackage: FeedbackPackage,
        private val exception: Throwable? = null,
        private val createDelayMs: Long = 0,
        private val clearDelayMs: Long = 0,
    ) : FeedbackLogExporterContract {
        var createCalls = 0
        var clearCalls = 0
        var maxConcurrentCreateCalls = 0
        var maxConcurrentClearCalls = 0

        private val createConcurrent = AtomicInteger(0)
        private val clearConcurrent = AtomicInteger(0)
        private val concurrentLock = Any()

        override suspend fun createPackage(): FeedbackPackage {
            val currentConcurrent = createConcurrent.incrementAndGet()
            synchronized(concurrentLock) {
                if (currentConcurrent > maxConcurrentCreateCalls) {
                    maxConcurrentCreateCalls = currentConcurrent
                }
            }
            try {
                createCalls++
                if (createDelayMs > 0) {
                    delay(createDelayMs)
                }
                if (exception != null) {
                    throw exception
                }
                return feedbackPackage
            } finally {
                createConcurrent.decrementAndGet()
            }
        }

        override suspend fun clearLogs() {
            val currentConcurrent = clearConcurrent.incrementAndGet()
            synchronized(concurrentLock) {
                if (currentConcurrent > maxConcurrentClearCalls) {
                    maxConcurrentClearCalls = currentConcurrent
                }
            }
            try {
                clearCalls++
                if (clearDelayMs > 0) {
                    delay(clearDelayMs)
                }
            } finally {
                clearConcurrent.decrementAndGet()
            }
        }

        override suspend fun pruneLogs() {
            // no-op
        }
    }

    private data class FeedbackPackageLog(
        val category: LogCategory,
        val event: String,
        val throwable: Throwable?,
    )

    private class RecordingLogger : MfLogger {
        val errorEvents = mutableListOf<FeedbackPackageLog>()

        override fun trace(category: LogCategory, event: String, fields: Map<String, Any?>) {
            // Not used in tests
        }

        override fun detail(category: LogCategory, event: String, fields: Map<String, Any?>) {
            // Not used in tests
        }

        override fun error(
            category: LogCategory,
            event: String,
            throwable: Throwable?,
            fields: Map<String, Any?>,
        ) {
            errorEvents.add(FeedbackPackageLog(category, event, throwable))
        }

        override fun flush() {
            // Not used in tests
        }
    }
}

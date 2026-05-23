package com.hank.musicfree.feature.settings

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.hank.musicfree.core.model.AlbumMusicClickAction
import com.hank.musicfree.core.model.AudioInterruptionAction
import com.hank.musicfree.core.model.DesktopLyricAlignment
import com.hank.musicfree.core.model.LyricAssociationType
import com.hank.musicfree.core.model.MusicDetailDefaultPage
import com.hank.musicfree.core.model.PlayQuality
import com.hank.musicfree.core.model.QualityFallbackOrder
import com.hank.musicfree.core.model.SearchResultClickAction
import com.hank.musicfree.core.model.SortMode
import com.hank.musicfree.data.backup.BackupArchivePaths
import com.hank.musicfree.data.backup.BackupManifest
import com.hank.musicfree.data.backup.BackupManifestFile
import com.hank.musicfree.data.backup.BackupRepository
import com.hank.musicfree.data.backup.StagedRestore
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.logging.FeedbackLogExporterContract
import com.hank.musicfree.logging.FeedbackPackage
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import com.hank.musicfree.logging.MfLogger
import com.hank.musicfree.logging.ReadableLogStore
import com.hank.musicfree.plugin.manager.PluginManager
import com.hank.musicfree.updater.store.UpdatePreferences
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
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
        assertEquals(LyricAssociationType.Search, state.lyricAssociationType)
        assertEquals(false, state.showExitOnNotification)
        assertEquals(SearchResultClickAction.PlayMusic, state.clickMusicInSearch)
        assertEquals(AlbumMusicClickAction.PlayAlbum, state.clickMusicInAlbum)
        assertEquals(SortMode.Manual, state.musicOrderInLocalSheet)
        assertEquals(PlayQuality.STANDARD, state.defaultPlayQuality)
        assertEquals(QualityFallbackOrder.Asc, state.playQualityOrder)
        assertEquals(false, state.allowConcurrentPlayback)
        assertEquals(false, state.autoPlayWhenAppStart)
        assertEquals(false, state.tryChangeSourceWhenPlayFail)
        assertEquals(false, state.autoStopWhenError)
        assertEquals(AudioInterruptionAction.Pause, state.audioInterruptionAction)
        assertEquals(0.5f, state.audioInterruptionDuckVolume)
        assertEquals(3, state.maxDownload)
        assertEquals(PlayQuality.STANDARD, state.defaultDownloadQuality)
        assertEquals(QualityFallbackOrder.Asc, state.downloadQualityOrder)
        assertEquals(false, state.useCellularPlay)
        assertEquals(false, state.useCellularDownload)
        assertEquals(true, state.silentUpdateDownloadEnabled)
        assertEquals(true, state.lyricAutoSearchEnabled)
        assertEquals(false, state.desktopLyricEnabled)
        assertEquals(DesktopLyricAlignment.Center, state.desktopLyricAlignment)
        assertEquals(0.08f, state.desktopLyricTopPercent)
        assertEquals(0.08f, state.desktopLyricLeftPercent)
        assertEquals(0.84f, state.desktopLyricWidthPercent)
        assertEquals(18, state.desktopLyricFontSizeSp)
        assertEquals("#FFFFFFFF", state.desktopLyricTextColor)
        assertEquals("#66000000", state.desktopLyricBackgroundColor)
        assertEquals(false, state.autoUpdatePlugins)
        assertEquals(false, state.skipPluginVersionCheck)
        assertEquals(false, state.lazyLoadPlugins)
        assertEquals(512, state.maxMusicCacheSizeMb)
        assertEquals(true, state.debugErrorLogEnabled)
        assertEquals(true, state.debugTraceLogEnabled)
        assertEquals(false, state.debugDevLogEnabled)
        assertTrue(!state.storageAccessState.isConfigured)
    }

    @Test
    fun `basic settings setters update collected runtime-backed state and preferences`() =
        runTest(mainDispatcherRule.dispatcher) {
            val appPreferences = createAppPreferences()
            val updatePreferences = createUpdatePreferences()
            val pluginManager = mock<PluginManager>()
            val viewModel = createViewModel(
                appPreferences = appPreferences,
                updatePreferences = updatePreferences,
                pluginManager = pluginManager,
            )
            val job = backgroundScope.launch { viewModel.basicSettingsUiState.collect {} }
            advanceUntilIdle()

            viewModel.setMaxSearchHistoryLength(100)
            viewModel.setMusicDetailDefaultPage(MusicDetailDefaultPage.Lyric)
            viewModel.setMusicDetailAwake(true)
            viewModel.setLyricAssociationType(LyricAssociationType.Input)
            viewModel.setShowExitOnNotification(true)
            viewModel.setClickMusicInSearch(SearchResultClickAction.PlayMusicAndReplace)
            viewModel.setClickMusicInAlbum(AlbumMusicClickAction.PlayMusic)
            viewModel.setMusicOrderInLocalSheet(SortMode.Title)
            viewModel.setDefaultPlayQuality(PlayQuality.HIGH)
            viewModel.setPlayQualityOrder(QualityFallbackOrder.Desc)
            viewModel.setAllowConcurrentPlayback(true)
            viewModel.setAutoPlayWhenAppStart(true)
            viewModel.setTryChangeSourceWhenPlayFail(true)
            viewModel.setAutoStopWhenError(true)
            viewModel.setAudioInterruptionAction(AudioInterruptionAction.LowerVolume)
            viewModel.setAudioInterruptionDuckVolume(0.8f)
            viewModel.setAutoUpdatePlugins(true)
            viewModel.setSkipPluginVersionCheck(true)
            viewModel.setLazyLoadPlugins(true)
            viewModel.setMaxMusicCacheSizeMb(1024)
            viewModel.setMaxDownload(7)
            viewModel.setDefaultDownloadQuality(PlayQuality.SUPER)
            viewModel.setDownloadQualityOrder(QualityFallbackOrder.Desc)
            viewModel.setUseCellularPlay(true)
            viewModel.setUseCellularDownload(true)
            viewModel.setSilentUpdateDownloadEnabled(false)
            viewModel.setLyricAutoSearchEnabled(false)
            viewModel.setDesktopLyricEnabled(true)
            viewModel.setDesktopLyricAlignment(DesktopLyricAlignment.Right)
            viewModel.setDesktopLyricTopPercent(0.16f)
            viewModel.setDesktopLyricLeftPercent(0.24f)
            viewModel.setDesktopLyricWidthPercent(0.66f)
            viewModel.setDesktopLyricFontSizeSp(24)
            viewModel.setDesktopLyricTextColor("#FFFFD54F")
            viewModel.setDesktopLyricBackgroundColor("#00000000")
            viewModel.setDebugErrorLogEnabled(false)
            viewModel.setDebugTraceLogEnabled(false)
            viewModel.setDebugDevLogEnabled(true)
            advanceUntilIdle()

            val state = viewModel.basicSettingsUiState.value
            assertEquals(100, state.maxSearchHistoryLength)
            assertEquals(MusicDetailDefaultPage.Lyric, state.musicDetailDefaultPage)
            assertEquals(true, state.musicDetailAwake)
            assertEquals(LyricAssociationType.Input, state.lyricAssociationType)
            assertEquals(true, state.showExitOnNotification)
            assertEquals(SearchResultClickAction.PlayMusicAndReplace, state.clickMusicInSearch)
            assertEquals(AlbumMusicClickAction.PlayMusic, state.clickMusicInAlbum)
            assertEquals(SortMode.Title, state.musicOrderInLocalSheet)
            assertEquals(PlayQuality.HIGH, state.defaultPlayQuality)
            assertEquals(QualityFallbackOrder.Desc, state.playQualityOrder)
            assertEquals(true, state.allowConcurrentPlayback)
            assertEquals(true, state.autoPlayWhenAppStart)
            assertEquals(true, state.tryChangeSourceWhenPlayFail)
            assertEquals(true, state.autoStopWhenError)
            assertEquals(AudioInterruptionAction.LowerVolume, state.audioInterruptionAction)
            assertEquals(0.8f, state.audioInterruptionDuckVolume)
            assertEquals(7, state.maxDownload)
            assertEquals(PlayQuality.SUPER, state.defaultDownloadQuality)
            assertEquals(QualityFallbackOrder.Desc, state.downloadQualityOrder)
            assertEquals(true, state.useCellularPlay)
            assertEquals(true, state.useCellularDownload)
            assertEquals(false, state.silentUpdateDownloadEnabled)
            assertEquals(false, state.lyricAutoSearchEnabled)
            assertEquals(true, state.desktopLyricEnabled)
            assertEquals(DesktopLyricAlignment.Right, state.desktopLyricAlignment)
            assertEquals(0.16f, state.desktopLyricTopPercent)
            assertEquals(0.24f, state.desktopLyricLeftPercent)
            assertEquals(0.66f, state.desktopLyricWidthPercent)
            assertEquals(24, state.desktopLyricFontSizeSp)
            assertEquals("#FFFFD54F", state.desktopLyricTextColor)
            assertEquals("#00000000", state.desktopLyricBackgroundColor)
            assertEquals(true, state.autoUpdatePlugins)
            assertEquals(true, state.skipPluginVersionCheck)
            assertEquals(true, state.lazyLoadPlugins)
            assertEquals(1024, state.maxMusicCacheSizeMb)
            assertEquals(false, state.debugErrorLogEnabled)
            assertEquals(false, state.debugTraceLogEnabled)
            assertEquals(true, state.debugDevLogEnabled)
            assertTrue(!state.storageAccessState.isConfigured)
            assertEquals(100, appPreferences.maxSearchHistoryLength.first())
            assertEquals(MusicDetailDefaultPage.Lyric, appPreferences.musicDetailDefaultPage.first())
            assertEquals(true, appPreferences.musicDetailAwake.first())
            assertEquals(LyricAssociationType.Input, appPreferences.lyricAssociationType.first())
            assertEquals(true, appPreferences.showExitOnNotification.first())
            assertEquals(SearchResultClickAction.PlayMusicAndReplace, appPreferences.clickMusicInSearch.first())
            assertEquals(AlbumMusicClickAction.PlayMusic, appPreferences.clickMusicInAlbum.first())
            assertEquals(SortMode.Title, appPreferences.musicOrderInLocalSheet.first())
            assertEquals(PlayQuality.HIGH, appPreferences.defaultPlayQuality.first())
            assertEquals(QualityFallbackOrder.Desc, appPreferences.playQualityOrder.first())
            assertEquals(true, appPreferences.allowConcurrentPlayback.first())
            assertEquals(true, appPreferences.autoPlayWhenAppStart.first())
            assertEquals(true, appPreferences.tryChangeSourceWhenPlayFail.first())
            assertEquals(true, appPreferences.autoStopWhenError.first())
            assertEquals(AudioInterruptionAction.LowerVolume, appPreferences.audioInterruptionAction.first())
            assertEquals(0.8f, appPreferences.audioInterruptionDuckVolume.first())
            assertEquals(false, updatePreferences.silentUpdateDownloadEnabled.first())
            assertEquals(true, appPreferences.autoUpdatePlugins.first())
            assertEquals(true, appPreferences.skipPluginVersionCheck.first())
            assertEquals(true, appPreferences.lazyLoadPlugins.first())
            assertEquals(1024L * 1024L * 1024L, appPreferences.maxMusicCacheSizeBytes.first())
            assertEquals(7, appPreferences.maxDownload.first())
            assertEquals(PlayQuality.SUPER, appPreferences.defaultDownloadQuality.first())
            assertEquals(QualityFallbackOrder.Desc, appPreferences.downloadQualityOrder.first())
            assertEquals(true, appPreferences.useCellularPlay.first())
            assertEquals(true, appPreferences.useCellularDownload.first())
            assertEquals(false, appPreferences.lyricAutoSearchEnabled.first())
            assertEquals(true, appPreferences.desktopLyricEnabled.first())
            assertEquals(DesktopLyricAlignment.Right, appPreferences.desktopLyricAlignment.first())
            assertEquals(0.16f, appPreferences.desktopLyricTopPercent.first())
            assertEquals(0.24f, appPreferences.desktopLyricLeftPercent.first())
            assertEquals(0.66f, appPreferences.desktopLyricWidthPercent.first())
            assertEquals(24, appPreferences.desktopLyricFontSizeSp.first())
            assertEquals("#FFFFD54F", appPreferences.desktopLyricTextColor.first())
            assertEquals("#00000000", appPreferences.desktopLyricBackgroundColor.first())
            assertEquals(false, appPreferences.debugErrorLogEnabled.first())
            assertEquals(false, appPreferences.debugTraceLogEnabled.first())
            assertEquals(true, appPreferences.debugDevLogEnabled.first())
            verify(pluginManager).reload()
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
    fun `show error log reads readable log store`() = runTest(mainDispatcherRule.dispatcher) {
        val logFile = tmpFolder.newFile("readable-errors.log")
        ReadableLogStore.install(logFile)
        ReadableLogStore.appendError("settings_failed", "line payload")
        val viewModel = createViewModel(createAppPreferences())

        viewModel.showErrorLog()
        val state = viewModel.errorLogUiState.first { it.visible }

        assertTrue(state.visible)
        assertTrue(state.content.contains("settings_failed"))
        assertTrue(state.content.contains("line payload"))

        viewModel.dismissErrorLog()

        assertFalse(viewModel.errorLogUiState.value.visible)
    }

    @Test
    fun `cache clear actions call cleaner and publish result`() = runTest(mainDispatcherRule.dispatcher) {
        val cleaner = mock<SettingsCacheCleaner>()
        val viewModel = createViewModel(createAppPreferences(), cacheCleaner = cleaner)
        val job = backgroundScope.launch { viewModel.basicSettingsUiState.collect {} }
        advanceUntilIdle()

        viewModel.clearMusicCache()
        advanceUntilIdle()
        verify(cleaner).clearMusicCache()
        assertEquals("音乐缓存已清理", viewModel.basicSettingsUiState.value.cacheActionMessage)

        viewModel.clearLyricCache()
        advanceUntilIdle()
        verify(cleaner).clearLyricCache()
        assertEquals("歌词缓存已清理", viewModel.basicSettingsUiState.value.cacheActionMessage)

        viewModel.clearImageCache()
        advanceUntilIdle()
        verify(cleaner).clearImageCache()
        assertEquals("图片缓存已清理", viewModel.basicSettingsUiState.value.cacheActionMessage)

        job.cancel()
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

    @Test
    fun `backup export publishes success state`() = runTest(mainDispatcherRule.dispatcher) {
        val exportManifest = createBackupManifest(fileCount = 4)
        val backupRepository = createFakeBackupRepository(exportManifest = exportManifest)
        val viewModel = createViewModel(
            appPreferences = createAppPreferences(),
            backupRepository = backupRepository,
        )

        viewModel.createBackup(Uri.parse("content://com.hank.musicfree/backup-test"))
        advanceUntilIdle()

        assertEquals(1, backupRepository.exportCalls)
        assertEquals("备份已创建", viewModel.backupRestoreUiState.value.message)
        assertEquals(false, viewModel.backupRestoreUiState.value.inProgress)
    }

    @Test
    fun `restore validation exposes confirmation state and confirm registers pending restore`() = runTest(mainDispatcherRule.dispatcher) {
        val manifest = createBackupManifest(fileCount = 3)
        val stagedRestore = createStagedRestore(manifest = manifest)
        val backupRepository = createFakeBackupRepository(
            stagedRestoreToReturn = stagedRestore,
        )
        val viewModel = createViewModel(
            appPreferences = createAppPreferences(),
            backupRepository = backupRepository,
        )

        viewModel.validateRestore(Uri.parse("content://com.hank.musicfree/restore"))
        advanceUntilIdle()

        val validateState = viewModel.backupRestoreUiState.value
        assertTrue(validateState.restoreConfirmationVisible)
        assertEquals(manifest.sourcePackageName, validateState.restoreSourcePackageName)
        assertEquals(manifest.appVersionName, validateState.restoreAppVersionName)
        assertEquals(manifest.files.size, validateState.restoreFileCount)
        assertEquals(1, backupRepository.validateCalls)

        viewModel.confirmRestore()
        advanceUntilIdle()

        assertEquals(1, backupRepository.registerCalls)
        assertEquals("已登记恢复，重启应用后生效", viewModel.backupRestoreUiState.value.message)
        assertFalse(viewModel.backupRestoreUiState.value.restoreConfirmationVisible)
        assertEquals(stagedRestore.id, backupRepository.lastRegisteredRestoreId)
    }

    @Test
    fun `backup export failure sets error state and logs`() = runTest(mainDispatcherRule.dispatcher) {
        val error = RuntimeException("backup export failed")
        val logger = RecordingLogger()
        MfLog.install(logger)
        val backupRepository = createFakeBackupRepository(exportError = error)
        val viewModel = createViewModel(
            appPreferences = createAppPreferences(),
            backupRepository = backupRepository,
        )

        viewModel.createBackup(Uri.parse("content://com.hank.musicfree/backup-failed"))
        advanceUntilIdle()

        assertEquals(1, backupRepository.exportCalls)
        assertEquals(error.message, viewModel.backupRestoreUiState.value.errorMessage)
        assertEquals("backup_export_failed", logger.errorEvents.singleOrNull()?.event)
        assertFalse(viewModel.backupRestoreUiState.value.inProgress)
    }

    @Test
    fun `restore validation failure sets error state and logs`() = runTest(mainDispatcherRule.dispatcher) {
        val error = RuntimeException("restore validation failed")
        val logger = RecordingLogger()
        MfLog.install(logger)
        val backupRepository = createFakeBackupRepository(validateError = error)
        val viewModel = createViewModel(
            appPreferences = createAppPreferences(),
            backupRepository = backupRepository,
        )

        viewModel.validateRestore(Uri.parse("content://com.hank.musicfree/restore-failed"))
        advanceUntilIdle()

        val state = viewModel.backupRestoreUiState.value
        assertEquals(1, backupRepository.validateCalls)
        assertEquals(error.message, state.errorMessage)
        assertFalse(state.inProgress)
        assertFalse(state.restoreConfirmationVisible)
        assertNull(state.restoreSourcePackageName)
        assertEquals("backup_restore_validate_failed", logger.errorEvents.singleOrNull()?.event)
    }

    @Test
    fun `restore registration failure keeps confirmation state and logs`() = runTest(mainDispatcherRule.dispatcher) {
        val error = RuntimeException("restore registration failed")
        val logger = RecordingLogger()
        MfLog.install(logger)
        val stagedRestore = createStagedRestore()
        val backupRepository = createFakeBackupRepository(
            stagedRestoreToReturn = stagedRestore,
            registerError = error,
        )
        val viewModel = createViewModel(
            appPreferences = createAppPreferences(),
            backupRepository = backupRepository,
        )

        viewModel.validateRestore(Uri.parse("content://com.hank.musicfree/restore"))
        advanceUntilIdle()
        viewModel.confirmRestore()
        advanceUntilIdle()

        val state = viewModel.backupRestoreUiState.value
        assertEquals(1, backupRepository.registerCalls)
        assertEquals(error.message, state.errorMessage)
        assertFalse(state.inProgress)
        assertTrue(state.restoreConfirmationVisible)
        assertEquals(stagedRestore.id, backupRepository.lastRegisteredRestoreId)
        assertEquals("backup_restore_register_failed", logger.errorEvents.singleOrNull()?.event)
    }

    @Test
    fun `backup actions ignore concurrent requests while operation is active`() = runTest(mainDispatcherRule.dispatcher) {
        val backupRepository = createFakeBackupRepository(exportDelayMs = 1_000)
        val viewModel = createViewModel(
            appPreferences = createAppPreferences(),
            backupRepository = backupRepository,
        )

        viewModel.createBackup(Uri.parse("content://com.hank.musicfree/backup-one"))
        viewModel.validateRestore(Uri.parse("content://com.hank.musicfree/restore-ignored"))

        assertTrue(viewModel.backupRestoreUiState.value.inProgress)
        advanceUntilIdle()

        assertEquals(1, backupRepository.exportCalls)
        assertEquals(0, backupRepository.validateCalls)
        assertEquals("备份已创建", viewModel.backupRestoreUiState.value.message)
    }

    @Test
    fun `dismiss restore confirmation clears staged restore metadata`() = runTest(mainDispatcherRule.dispatcher) {
        val manifest = createBackupManifest(fileCount = 3)
        val backupRepository = createFakeBackupRepository(
            stagedRestoreToReturn = createStagedRestore(manifest = manifest),
        )
        val viewModel = createViewModel(
            appPreferences = createAppPreferences(),
            backupRepository = backupRepository,
        )

        viewModel.validateRestore(Uri.parse("content://com.hank.musicfree/restore"))
        advanceUntilIdle()
        viewModel.dismissRestoreConfirmation()

        val state = viewModel.backupRestoreUiState.value
        assertFalse(state.restoreConfirmationVisible)
        assertNull(state.restoreSourcePackageName)
        assertNull(state.restoreAppVersionName)
        assertEquals(0, state.restoreFileCount)
    }

    @Test
    fun `clear backup restore message clears success and error messages`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(appPreferences = createAppPreferences())

        viewModel.createBackup(Uri.parse("content://com.hank.musicfree/backup"))
        advanceUntilIdle()
        assertEquals("备份已创建", viewModel.backupRestoreUiState.value.message)

        viewModel.clearBackupRestoreMessage()

        assertNull(viewModel.backupRestoreUiState.value.message)
        assertNull(viewModel.backupRestoreUiState.value.errorMessage)
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

    private fun createUpdatePreferences(): UpdatePreferences {
        val scope = CoroutineScope(SupervisorJob() + mainDispatcherRule.dispatcher)
        dataStoreScopes += scope
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tmpFolder.newFile("updater-settings-test-${System.nanoTime()}.preferences_pb") },
        )
        return UpdatePreferences(dataStore)
    }

    private fun createViewModel(
        appPreferences: AppPreferences,
        exporter: FeedbackLogExporterContract = createFakeExporter(),
        pluginManager: PluginManager = mock(),
        cacheCleaner: SettingsCacheCleaner = mock(),
        backupRepository: BackupRepository = createFakeBackupRepository(),
        updatePreferences: UpdatePreferences = createUpdatePreferences(),
    ): SettingsViewModel {
        return SettingsViewModel(
            appPreferences = appPreferences,
            updatePreferences = updatePreferences,
            feedbackLogExporter = exporter,
            pluginManager = pluginManager,
            cacheCleaner = cacheCleaner,
            backupRepository = backupRepository,
        )
    }

    private fun createBackupManifest(
        sourcePackageName: String = "com.hank.musicfree",
        appVersionName: String = "1.0.0",
        fileCount: Int = 2,
    ): BackupManifest {
        return BackupManifest(
            schemaVersion = BackupManifest.CURRENT_SCHEMA_VERSION,
            sourcePackageName = sourcePackageName,
            createdAt = "2026-01-01T00:00:00Z",
            appVersionName = appVersionName,
            appVersionCode = 1L,
            databaseVersion = 1,
            files = (0 until fileCount).map { index ->
                BackupManifestFile(
                    path = if (index == 0) {
                        BackupArchivePaths.DB
                    } else {
                        "${BackupArchivePaths.PLAYLIST_COVERS_PREFIX}file-$index.bin"
                    },
                    sizeBytes = 100L,
                    sha256 = "0".repeat(64),
                )
            },
        )
    }

    private fun createStagedRestore(
        manifest: BackupManifest = createBackupManifest(),
        directory: File = tmpFolder.newFolder(),
    ): StagedRestore {
        return StagedRestore(
            id = "staged-restore-id",
            directory = directory,
            manifest = manifest,
        )
    }

    private fun createFakeBackupRepository(
        exportManifest: BackupManifest = createBackupManifest(),
        stagedRestoreToReturn: StagedRestore = createStagedRestore(exportManifest),
        exportError: Throwable? = null,
        validateError: Throwable? = null,
        registerError: Throwable? = null,
        exportDelayMs: Long = 0,
    ): FakeBackupRepository {
        return FakeBackupRepository(
            exportManifest = exportManifest,
            stagedRestoreToReturn = stagedRestoreToReturn,
            exportError = exportError,
            validateError = validateError,
            registerError = registerError,
            exportDelayMs = exportDelayMs,
        )
    }

    private class FakeBackupRepository(
        private val exportManifest: BackupManifest,
        private val stagedRestoreToReturn: StagedRestore,
        private val exportError: Throwable? = null,
        private val validateError: Throwable? = null,
        private val registerError: Throwable? = null,
        private val exportDelayMs: Long = 0,
    ) : BackupRepository {
        var exportCalls = 0
        var validateCalls = 0
        var registerCalls = 0
        var lastRegisteredRestoreId: String? = null

        override suspend fun exportTo(uri: Uri): BackupManifest {
            exportCalls++
            if (exportDelayMs > 0) {
                delay(exportDelayMs)
            }
            if (exportError != null) throw exportError
            return exportManifest
        }

        override suspend fun stageRestoreFrom(uri: Uri): StagedRestore {
            validateCalls++
            if (validateError != null) throw validateError
            return stagedRestoreToReturn
        }

        override suspend fun registerPendingRestore(stagedRestore: StagedRestore) {
            registerCalls++
            lastRegisteredRestoreId = stagedRestore.id
            if (registerError != null) throw registerError
        }
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

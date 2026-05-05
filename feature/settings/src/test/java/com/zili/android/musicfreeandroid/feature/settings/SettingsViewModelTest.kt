package com.zili.android.musicfreeandroid.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.logging.FeedbackLogExporterContract
import com.zili.android.musicfreeandroid.logging.FeedbackPackage
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.logging.MfLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import java.io.File

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
        val viewModel = createViewModel(createAppPreferences(), createFakeExporter())

        val state = viewModel.storageAccessState.value

        assertTrue(!state.isConfigured)
        assertNull(state.selectedDirectory)
    }

    @Test
    fun `set storage directory persists selected tree uri`() = runTest(mainDispatcherRule.dispatcher) {
        val appPreferences = createAppPreferences()
        val viewModel = createViewModel(appPreferences, createFakeExporter())
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusicFree"

        viewModel.setStorageDirectory(treeUri)
        // Drains scheduler-tracked work; with UnconfinedTestDispatcher launches
        // already run eagerly, but the explicit drain documents the intent.
        advanceUntilIdle()

        val persisted = appPreferences.storageDirectoryUri.first()
        assertEquals(treeUri, persisted)
    }

    @Test
    fun `create feedback package emits created package`() = runTest(mainDispatcherRule.dispatcher) {
        val appPreferences = createAppPreferences()
        val expectedPackage = createFeedbackPackage("generated-feedback.zip")
        val exporter = createFakeExporter(
            feedbackPackage = expectedPackage,
        )
        val viewModel = createViewModel(appPreferences, exporter)

        val emittedPackage = async { viewModel.feedbackPackage.first() }

        viewModel.createFeedbackPackage()
        advanceUntilIdle()

        assertSame(expectedPackage, emittedPackage.await())
        assertEquals(1, exporter.createCalls)
    }

    @Test
    fun `clear logs calls exporter`() = runTest(mainDispatcherRule.dispatcher) {
        val appPreferences = createAppPreferences()
        val exporter = createFakeExporter()
        val viewModel = createViewModel(appPreferences, exporter)

        viewModel.clearLogs()
        advanceUntilIdle()

        assertEquals(1, exporter.clearCalls)
    }

    @Test
    fun `create feedback package logs error when exporter throws`() = runTest(mainDispatcherRule.dispatcher) {
        val appPreferences = createAppPreferences()
        val error = RuntimeException("feedback export failed")
        val exporter = createFakeExporter(exception = error)
        val logger = RecordingLogger()
        MfLog.install(logger)

        val viewModel = createViewModel(appPreferences, exporter)
        viewModel.createFeedbackPackage()
        advanceUntilIdle()

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
        exporter: FeedbackLogExporterContract,
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
    ): FakeFeedbackLogExporter {
        return FakeFeedbackLogExporter(
            feedbackPackage = feedbackPackage,
            exception = exception,
        )
    }

    private class FakeFeedbackLogExporter(
        private val feedbackPackage: FeedbackPackage,
        private val exception: Throwable? = null,
    ) : FeedbackLogExporterContract {
        var createCalls = 0
        var clearCalls = 0

        override suspend fun createPackage(): FeedbackPackage {
            createCalls++
            if (exception != null) {
                throw exception
            }
            return feedbackPackage
        }

        override suspend fun clearLogs() {
            clearCalls++
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

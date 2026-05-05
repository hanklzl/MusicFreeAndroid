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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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
    }

    @Test
    fun `storage access state is unconfigured by default`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(createAppPreferences(), createExporter())

        val state = viewModel.storageAccessState.value

        assertTrue(!state.isConfigured)
        assertNull(state.selectedDirectory)
    }

    @Test
    fun `set storage directory persists selected tree uri`() = runTest(mainDispatcherRule.dispatcher) {
        val appPreferences = createAppPreferences()
        val viewModel = createViewModel(appPreferences, createExporter())
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
        val exporter = createExporter(expectedPackage)
        val viewModel = createViewModel(appPreferences, exporter)

        val emittedPackage = async { viewModel.feedbackPackage.first() }

        viewModel.createFeedbackPackage()
        advanceUntilIdle()

        assertSame(expectedPackage, emittedPackage.await())
        verify(exporter).createPackage()
    }

    @Test
    fun `clear logs calls exporter`() = runTest(mainDispatcherRule.dispatcher) {
        val appPreferences = createAppPreferences()
        val exporter = createExporter()
        val viewModel = createViewModel(appPreferences, exporter)

        viewModel.clearLogs()
        advanceUntilIdle()

        verify(exporter).clearLogs()
    }

    @Test
    fun `create feedback package logs error when exporter throws`() = runTest(mainDispatcherRule.dispatcher) {
        val appPreferences = createAppPreferences()
        val error = RuntimeException("feedback export failed")
        val exporter = mock<FeedbackLogExporterContract> {
            on { createPackage() } doThrow error
        }
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

        MfLog.resetForTest()
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

    private fun createExporter(
        feedbackPackage: FeedbackPackage = createFeedbackPackage("feedback.zip"),
    ): FeedbackLogExporterContract {
        return mock {
            on { createPackage() } doReturn feedbackPackage
            on { clearLogs() } doReturn Unit
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

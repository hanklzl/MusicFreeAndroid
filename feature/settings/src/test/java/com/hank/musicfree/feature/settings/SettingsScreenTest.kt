package com.hank.musicfree.feature.settings

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.hank.musicfree.core.navigation.SettingsType
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.ui.FidelityAnchors
import com.hank.musicfree.data.backup.BackupArchivePaths
import com.hank.musicfree.data.backup.BackupManifest
import com.hank.musicfree.data.backup.BackupManifestFile
import com.hank.musicfree.data.backup.BackupRepository
import com.hank.musicfree.data.backup.StagedRestore
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.logging.FeedbackLogExporterContract
import com.hank.musicfree.logging.FeedbackPackage
import com.hank.musicfree.plugin.manager.PluginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val dataStoreScopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        dataStoreScopes.forEach { scope -> scope.cancel() }
        dataStoreScopes.clear()
    }

    @Test
    fun `basic type renders basic settings root`() {
        setContent(type = SettingsType.Basic)

        composeRule.onNodeWithTag(FidelityAnchors.Screen.SettingsRoot).assertIsDisplayed()
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BasicRoot).assertIsDisplayed()
    }

    @Test
    fun `backup type renders backup restore actions`() {
        setContent(type = SettingsType.Backup)

        composeRule.onNodeWithTag(FidelityAnchors.Settings.BackupRoot).assertIsDisplayed()
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BackupEntry).assertIsDisplayed()
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BackupCreate).assertIsDisplayed()
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BackupRestore).assertIsDisplayed()
        composeRule.onNodeWithText("创建备份/迁移包").assertIsDisplayed()
        composeRule.onNodeWithText("从备份恢复").assertIsDisplayed()
    }

    @Test
    fun `backup restore confirmation can register pending restore`() {
        val viewModel = setContent(type = SettingsType.Backup)

        composeRule.runOnIdle {
            viewModel.validateRestore(Uri.parse("content://com.hank.musicfree/restore"))
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("确认恢复备份").assertIsDisplayed()
        composeRule.onNodeWithText("确认恢复").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("确认恢复备份").assertDoesNotExist()
        composeRule.onNodeWithTag(FidelityAnchors.Settings.BackupStatus).assertIsDisplayed()
        composeRule.onNodeWithText("已登记恢复，重启应用后生效").assertIsDisplayed()
    }

    private fun setContent(
        type: SettingsType,
        backupRepository: BackupRepository = FakeBackupRepository(),
    ): SettingsViewModel {
        val viewModel = SettingsViewModel(
            appPreferences = createAppPreferences(),
            feedbackLogExporter = FakeFeedbackLogExporter(),
            pluginManager = mock<PluginManager>(),
            cacheCleaner = mock<SettingsCacheCleaner>(),
            backupRepository = backupRepository,
        )
        composeRule.setContent {
            MusicFreeTheme {
                SettingsScreen(
                    type = type,
                    onBack = {},
                    onNavigateToPermissions = {},
                    onNavigateToFileSelector = {},
                    onNavigateToSetCustomTheme = {},
                    viewModel = viewModel,
                )
            }
        }
        return viewModel
    }

    private fun createAppPreferences(): AppPreferences {
        val scope = CoroutineScope(SupervisorJob() + mainDispatcherRule.dispatcher)
        dataStoreScopes += scope
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tmpFolder.newFile("settings-screen-test.preferences_pb") },
        )
        return AppPreferences(dataStore)
    }

    private inner class FakeFeedbackLogExporter : FeedbackLogExporterContract {
        override suspend fun createPackage(): FeedbackPackage {
            val file = tmpFolder.newFile("settings-screen-feedback.zip")
            return FeedbackPackage(file = file, fileName = file.name, sizeBytes = file.length())
        }

        override suspend fun clearLogs() {
            // no-op
        }

        override suspend fun pruneLogs() {
            // no-op
        }
    }

    private inner class FakeBackupRepository : BackupRepository {
        private var restoreIndex = 0
        private val manifest = BackupManifest(
            schemaVersion = BackupManifest.CURRENT_SCHEMA_VERSION,
            sourcePackageName = "com.hank.musicfree",
            createdAt = "2026-05-17T00:00:00Z",
            appVersionName = "1.0.2",
            appVersionCode = 10002,
            databaseVersion = 11,
            files = listOf(
                BackupManifestFile(
                    path = BackupArchivePaths.DB,
                    sizeBytes = 2,
                    sha256 = "0".repeat(64),
                ),
            ),
        )

        override suspend fun exportTo(uri: android.net.Uri): BackupManifest = manifest

        override suspend fun stageRestoreFrom(uri: android.net.Uri): StagedRestore {
            val id = "restore-${restoreIndex++}"
            return StagedRestore(id, tmpFolder.newFolder(id), manifest)
        }

        override suspend fun registerPendingRestore(stagedRestore: StagedRestore) {
            // no-op
        }
    }
}

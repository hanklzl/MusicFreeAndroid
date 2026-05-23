package com.hank.musicfree

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hank.musicfree.core.network.NetworkTypeDetector
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.core.ui.FidelityAnchors
import com.hank.musicfree.feature.home.HomeScreenContent
import com.hank.musicfree.feature.home.HomeScreenState
import com.hank.musicfree.feature.home.buildHomeDrawerUiModel
import com.hank.musicfree.feature.home.buildHomeVisualUiModel
import com.hank.musicfree.feature.home.sheets.HomeSheetTab
import com.hank.musicfree.updater.api.UpdateClient
import com.hank.musicfree.updater.checker.AbiResolver
import com.hank.musicfree.updater.checker.UpdateChecker
import com.hank.musicfree.updater.downloader.ApkDownloader
import com.hank.musicfree.updater.downloader.UpdateDownloadManager
import com.hank.musicfree.updater.installer.ApkInstaller
import com.hank.musicfree.updater.store.UpdatePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HomeDrawerBehaviorTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var state: HomeScreenState
    private lateinit var stubChecker: UpdateChecker
    private lateinit var stubDownloadManager: UpdateDownloadManager
    private lateinit var stubInstaller: ApkInstaller
    private lateinit var testScope: CoroutineScope

    @Before
    fun setUp() {
        state = HomeScreenState()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        testScope = CoroutineScope(Dispatchers.IO)
        val tmpFile = File.createTempFile("update_prefs_test_", ".preferences_pb", context.cacheDir)
        tmpFile.delete()
        val dataStore = PreferenceDataStoreFactory.create(scope = testScope) { tmpFile }
        val stubPrefs = UpdatePreferences(dataStore)
        stubChecker = UpdateChecker(
            client = object : UpdateClient {
                override suspend fun fetchLatest() = null
            },
            prefs = stubPrefs,
            abiResolver = AbiResolver { emptyList() },
            localCode = 0L,
            localName = "test",
            scope = testScope,
        )
        val stubDownloader = object : ApkDownloader {
            override suspend fun download(
                update: com.hank.musicfree.updater.checker.ResolvedUpdate,
                onProgress: (Long, Long, Float) -> Unit,
            ): ApkDownloader.Result = ApkDownloader.Result.Failure(
                com.hank.musicfree.updater.checker.UpdateError.Canceled,
            )
            override fun cancel() = Unit
        }
        stubDownloadManager = UpdateDownloadManager(
            context = context,
            checker = stubChecker,
            downloader = stubDownloader,
            prefs = stubPrefs,
            networkTypeDetector = NetworkTypeDetector(context),
            scope = testScope,
        )
        stubInstaller = ApkInstaller(context)

        composeRule.setContent {
            MusicFreeTheme {
                HomeScreenContent(
                    state = state,
                    visualUiModel = buildHomeVisualUiModel(selectedTab = HomeSheetTab.Mine, mineRows = emptyList()),
                    drawerUiModel = buildHomeDrawerUiModel(
                        currentVersion = "1.0.0-test",
                        scheduleCloseSummary = "30 分钟后关闭",
                    ),
                    currentVersion = "1.0.0-test",
                    scheduleCloseSummary = "30 分钟后关闭",
                    checker = stubChecker,
                    downloadManager = stubDownloadManager,
                    installer = stubInstaller,
                    onDrawerEntryClick = {},
                    onNavigateToSearch = {},
                    onNavigateToRecommendSheets = {},
                    onNavigateToTopList = {},
                    onNavigateToHistory = {},
                    onNavigateToLocal = {},
                    onSelectTab = {},
                    onCreateClick = {},
                    onImportClick = {},
                    onOpenMineSheet = {},
                    onOpenStarredSheet = {},
                    onOpenStarredAlbum = {},
                    onTrashClick = {},
                )
            }
        }
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun update_entry_opens_dialog() {
        clickDrawerEntry(FidelityAnchors.Home.DrawerSoftwareCheckUpdate)

        assertTagExists(FidelityAnchors.Dialog.UpdateCheckRoot)
        assertTrue(state.isUpdateCheckVisible)
    }

    @Test
    fun scheduleClose_entry_opens_panel() {
        clickDrawerEntry(FidelityAnchors.Home.DrawerOtherScheduleClose)

        assertTagExists(FidelityAnchors.Panel.TimingCloseRoot)
        assertTrue(state.isTimingCloseVisible)
    }

    @Test
    fun scrim_click_closes_drawer() {
        openDrawer()

        composeRule.onRoot().performTouchInput {
            click(centerRight)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            !state.isDrawerOpen
        }
        assertFalse(state.isDrawerOpen)
    }

    @Test
    fun back_closes_drawer() {
        openDrawer()

        pressBack()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            !state.isDrawerOpen
        }
        assertFalse(state.isDrawerOpen)
    }

    @Test
    fun left_swipe_closes_drawer() {
        openDrawer()

        composeRule.onNodeWithTag(FidelityAnchors.Home.DrawerRoot).performTouchInput {
            swipeLeft()
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            !state.isDrawerOpen
        }
        assertFalse(state.isDrawerOpen)
    }

    private fun clickDrawerEntry(tag: String) {
        openDrawer()
        composeRule.onNodeWithTag(tag).performClick()
    }

    private fun openDrawer() {
        composeRule.onNodeWithTag(FidelityAnchors.Home.NavBarMenu).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            state.isDrawerOpen
        }
        assertTagExists(FidelityAnchors.Home.DrawerRoot)
    }

    private fun assertTagExists(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).fetchSemanticsNode()
                true
            }.getOrElse { false }
        }
        composeRule.onNodeWithTag(tag).assertExists()
    }

}

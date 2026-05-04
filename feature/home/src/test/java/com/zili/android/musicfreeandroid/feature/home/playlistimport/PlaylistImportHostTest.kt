package com.zili.android.musicfreeandroid.feature.home.playlistimport

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.Playlist
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlaylistImportHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleMusic(id: String): MusicItem {
        return MusicItem(
            id = id,
            platform = "demo",
            title = "Song $id",
            artist = "Artist",
            album = "Album",
            duration = 12_000L,
            url = null,
            artwork = null,
            qualities = null,
        )
    }

    @Test
    fun `choose plugin empty list shows no plugin tag and empty-state message`() {
        composeRule.setContent {
            MusicFreeTheme {
                PlaylistImportHost(
                    importState = PlaylistImportState.ChoosePlugin(emptyList()),
                    sheetVisible = false,
                    playlists = emptyList(),
                    onSelectPlugin = {},
                    onSubmit = {},
                    onDismiss = {},
                    onConfirmFound = {},
                    onSelectTarget = {},
                    onCreateTarget = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("PlaylistImport_NoPlugin").assertExists()
        composeRule.onAllNodesWithText("暂无支持导入歌单的插件").assertCountEquals(1)
    }

    @Test
    fun `choose plugin tap demo plugin dispatches platform callback`() {
        var selectedPlugin: String? = null

        composeRule.setContent {
            MusicFreeTheme {
                PlaylistImportHost(
                    importState = PlaylistImportState.ChoosePlugin(
                        listOf(ImportCapablePlugin(platform = "demo", name = "Demo")),
                    ),
                    sheetVisible = false,
                    playlists = emptyList(),
                    onSelectPlugin = { platform -> selectedPlugin = platform },
                    onSubmit = {},
                    onDismiss = {},
                    onConfirmFound = {},
                    onSelectTarget = {},
                    onCreateTarget = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("PlaylistImport_Plugin_demo").performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals("demo", selectedPlugin)
        }
    }

    @Test
    fun `input url confirm dispatches entered url`() {
        val captured = mutableListOf<String>()

        composeRule.setContent {
            MusicFreeTheme {
                PlaylistImportHost(
                    importState = PlaylistImportState.InputUrl(
                        plugin = ImportCapablePlugin(platform = "demo", name = "Demo"),
                    ),
                    sheetVisible = false,
                    playlists = emptyList(),
                    onSelectPlugin = {},
                    onSubmit = { url -> captured += url },
                    onDismiss = {},
                    onConfirmFound = {},
                    onSelectTarget = {},
                    onCreateTarget = {},
                )
            }
        }
        composeRule.waitForIdle()

        val importUrl = "https://music.example.com/playlist"
        composeRule.onNodeWithTag("PlaylistImport_Input").performTextInput(importUrl)
        composeRule.onNodeWithText("确定").performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(listOf(importUrl), captured)
        }
    }

    @Test
    fun `confirm found shows count and confirm dispatches confirm callback`() {
        var confirmed = false

        composeRule.setContent {
            MusicFreeTheme {
                PlaylistImportHost(
                    importState = PlaylistImportState.ConfirmFound(
                        plugin = ImportCapablePlugin(platform = "demo", name = "Demo"),
                        items = listOf(sampleMusic("1"), sampleMusic("2")),
                    ),
                    sheetVisible = false,
                    playlists = emptyList(),
                    onSelectPlugin = {},
                    onSubmit = {},
                    onDismiss = {},
                    onConfirmFound = { confirmed = true },
                    onSelectTarget = {},
                    onCreateTarget = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("发现 2 首歌曲! 现在开始导入吗?").assertExists()
        composeRule.onNodeWithText("确定").performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(true, confirmed)
        }
    }

    @Test
    fun `choose target shows target sheet and dispatches selected playlist id`() {
        var selectedPlaylistId: String? = null

        composeRule.setContent {
            MusicFreeTheme {
                PlaylistImportHost(
                    importState = PlaylistImportState.ChooseTarget(
                        items = listOf(sampleMusic("1"), sampleMusic("2")),
                    ),
                    sheetVisible = true,
                    playlists = listOf(
                        Playlist(
                            id = "p1",
                            name = "我喜欢",
                            worksNum = 2,
                        ),
                        Playlist(
                            id = "p2",
                            name = "收藏",
                            worksNum = 0,
                        ),
                    ),
                    onSelectPlugin = {},
                    onSubmit = {},
                    onDismiss = {},
                    onConfirmFound = {},
                    onSelectTarget = { playlistId -> selectedPlaylistId = playlistId },
                    onCreateTarget = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("PlaylistImport_TargetSheet").assertExists()
        composeRule.onNodeWithTag("PlaylistImport_TargetContent").assertExists()
        composeRule.onNodeWithText("我喜欢").assertExists()
        composeRule.onNodeWithTag("AddToPlaylist_Row_p1").performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals("p1", selectedPlaylistId)
        }
    }
}

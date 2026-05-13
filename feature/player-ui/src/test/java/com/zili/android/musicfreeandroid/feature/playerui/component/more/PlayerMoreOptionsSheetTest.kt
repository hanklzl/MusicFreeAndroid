package com.zili.android.musicfreeandroid.feature.playerui.component.more

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlayerMoreOptionsSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `content shows RN lyric options in order`() {
        setContent(item = ocean())

        listOf(
            "孤单北半球",
            "欧得洋 - 北半球有欧得洋",
            "ID: 元力 KW@150571",
            "作者: 欧得洋",
            "专辑: 北半球有欧得洋",
            "开启桌面歌词",
            "上传本地歌词",
            "上传本地歌词翻译",
            "删除本地歌词",
        ).forEach { text ->
            composeRule.onNodeWithText(text).assertIsDisplayed()
        }
    }

    @Test
    fun `album row is hidden when album is missing`() {
        setContent(item = ocean(album = null))

        composeRule.onNodeWithText("专辑: 北半球有欧得洋").assertDoesNotExist()
        composeRule.onNodeWithText("欧得洋").assertIsDisplayed()
    }

    @Test
    fun `actions invoke callbacks`() {
        var desktopClicks = 0
        var rawClicks = 0
        var translationClicks = 0
        var deleteClicks = 0

        setContent(
            item = ocean(),
            onToggleDesktopLyric = { desktopClicks++ },
            onImportRawLyric = { rawClicks++ },
            onImportTranslatedLyric = { translationClicks++ },
            onDeleteLocalLyric = { deleteClicks++ },
        )

        composeRule.onNodeWithText("开启桌面歌词").performClick()
        composeRule.onNodeWithText("上传本地歌词").performClick()
        composeRule.onNodeWithText("上传本地歌词翻译").performClick()
        composeRule.onNodeWithText("删除本地歌词").performClick()

        composeRule.runOnIdle {
            assertEquals(1, desktopClicks)
            assertEquals(1, rawClicks)
            assertEquals(1, translationClicks)
            assertEquals(1, deleteClicks)
        }
    }

    private fun setContent(
        item: MusicItem,
        onToggleDesktopLyric: () -> Unit = {},
        onImportRawLyric: () -> Unit = {},
        onImportTranslatedLyric: () -> Unit = {},
        onDeleteLocalLyric: () -> Unit = {},
    ) {
        composeRule.setContent {
            MusicFreeTheme {
                PlayerMoreOptionsSheetContent(
                    item = item,
                    desktopLyricEnabled = false,
                    onToggleDesktopLyric = onToggleDesktopLyric,
                    onImportRawLyric = onImportRawLyric,
                    onImportTranslatedLyric = onImportTranslatedLyric,
                    onDeleteLocalLyric = onDeleteLocalLyric,
                )
            }
        }
    }

    private fun ocean(album: String? = "北半球有欧得洋"): MusicItem =
        MusicItem(
            id = "150571",
            platform = "元力 KW",
            title = "孤单北半球",
            artist = "欧得洋",
            album = album,
            duration = 248_000L,
            url = null,
            artwork = null,
            qualities = null,
        )
}

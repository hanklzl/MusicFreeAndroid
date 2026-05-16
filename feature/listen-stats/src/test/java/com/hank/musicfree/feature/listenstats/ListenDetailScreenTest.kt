package com.hank.musicfree.feature.listenstats

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import com.hank.musicfree.core.theme.MusicFreeTheme
import com.hank.musicfree.data.repository.listenstats.model.ListenedSong
import com.hank.musicfree.feature.listenstats.component.SongDetailRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ListenDetailScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun firstSeen_mode_shows_firstSeen_badge_on_row() {
        composeRule.setContent {
            MusicFreeTheme {
                SongDetailRow(
                    song = ListenedSong(
                        musicId = "m1", platform = "p",
                        title = "起风了", artistRaw = "买辣椒也用券",
                        album = null, artwork = null,
                        firstSeenMs = 1715900000000L, lastSeenMs = 1715900000000L,
                        playCount = 5, totalSec = 300,
                    ),
                    showFirstSeen = true,
                )
            }
        }
        composeRule.onNode(hasText("首次", substring = true)).assertIsDisplayed()
    }
}

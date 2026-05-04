package com.zili.android.musicfreeandroid.feature.playerui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.zili.android.musicfreeandroid.core.theme.MusicFreeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PlayerScreenInsetsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `player content layer applies top status bar inset only`() {
        composeRule.setContent {
            MusicFreeTheme {
                Box(Modifier.size(200.dp)) {
                    PlayerContentLayer(
                        statusBarInsets = WindowInsets(left = 12.dp, top = 24.dp, right = 8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .testTag(FIRST_CONTENT_TAG),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(FIRST_CONTENT_TAG)
            .assertTopPositionInRootIsEqualTo(24.dp)
        composeRule.onNodeWithTag(FIRST_CONTENT_TAG)
            .assertLeftPositionInRootIsEqualTo(0.dp)
        composeRule.onNodeWithTag(FIRST_CONTENT_TAG)
            .assertWidthIsEqualTo(200.dp)
    }

    private companion object {
        const val FIRST_CONTENT_TAG = "player-content-first-child"
    }
}

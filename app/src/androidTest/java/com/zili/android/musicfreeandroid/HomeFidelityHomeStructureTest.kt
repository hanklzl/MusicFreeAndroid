package com.zili.android.musicfreeandroid

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zili.android.musicfreeandroid.core.ui.FidelityAnchors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HomeFidelityHomeStructureTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun home_exposes_singleScrollAnchors_andDrawerOpens() {
        composeRule.onNodeWithTag(FidelityAnchors.Screen.HomeRoot).assertExists()
        composeRule.onNodeWithTag(FidelityAnchors.Home.NavBarRoot).assertExists()
        composeRule.onNodeWithTag(FidelityAnchors.Home.OperationsRoot).assertExists()
        composeRule.onNodeWithTag(FidelityAnchors.Home.SheetsRoot).assertExists()

        composeRule.onNodeWithTag(FidelityAnchors.Home.NavBarMenu).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(FidelityAnchors.Home.DrawerRoot).assertIsDisplayed()
            }.isSuccess
        }
        composeRule.onNodeWithTag(FidelityAnchors.Home.DrawerRoot).assertIsDisplayed()
    }

    @Test
    fun home_content_remains_visible_above_existingMiniPlayer() {
        composeRule.onNodeWithTag(FidelityAnchors.Home.SheetsRoot).assertIsDisplayed()
    }
}

package com.zili.android.musicfreeandroid

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityStartupTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun appLaunchesWithoutNavigationCrash() {
        hiltRule.inject()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
            }
        }
    }
}

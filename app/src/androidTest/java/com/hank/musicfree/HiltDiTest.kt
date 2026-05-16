package com.hank.musicfree

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HiltDiTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Test
    fun hiltDependencyGraphIsComplete() {
        // If this test runs without crashing, the DI graph is valid.
        // Hilt validates all bindings at compile time via KSP,
        // and this test verifies runtime initialization works.
        hiltRule.inject()
    }
}

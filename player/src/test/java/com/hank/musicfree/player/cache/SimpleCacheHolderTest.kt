package com.hank.musicfree.player.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SimpleCacheHolderTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val holder = SimpleCacheHolder(ctx)

    @After fun teardown() { holder.resetForClear() }

    @Test fun lazy_creates_cache_on_first_access() {
        assertNotNull(holder.current)
    }

    @Test fun resetForClear_replaces_instance() {
        val first = holder.current
        val second = holder.resetForClear()
        assertTrue(first !== second)
    }
}

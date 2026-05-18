package com.hank.musicfree.updater.installer

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstallIntentsTest {

    @Test
    fun `build install intent has correct action data flags`() {
        val uri = Uri.parse("content://com.hank.musicfree.updater-files/updates/x.apk")
        val intent = InstallIntents.installApk(uri)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(uri, intent.data)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertTrue((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun `build unknown sources intent points to package settings`() {
        val intent = InstallIntents.manageUnknownAppSources("com.hank.musicfree")
        assertEquals(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
        assertEquals(Uri.parse("package:com.hank.musicfree"), intent.data)
    }
}

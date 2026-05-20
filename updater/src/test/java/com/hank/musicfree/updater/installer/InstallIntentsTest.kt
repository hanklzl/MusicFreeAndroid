package com.hank.musicfree.updater.installer

import android.content.Intent
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 31, 34])
class InstallIntentsTest {

    @Suppress("DEPRECATION")
    @Test
    fun `build install intent has correct action data flags`() {
        val uri = Uri.parse("content://com.hank.musicfree.updater-files/updates/x.apk")
        val intent = InstallIntents.installApk(uri)
        assertEquals(Intent.ACTION_INSTALL_PACKAGE, intent.action)
        assertEquals(uri, intent.data)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertTrue((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        val clipData = requireNotNull(intent.clipData)
        assertEquals(1, clipData.itemCount)
        assertEquals(uri, clipData.getItemAt(0).uri)
    }

    @Test
    fun `grant read permission grants every resolved installer package once for all query paths`() {
        val uri = Uri.parse("content://com.hank.musicfree.updater-files/updates/x.apk")
        val intent = InstallIntents.installApk(uri)
        val packageManager = mockk<PackageManager>()
        val context = mockk<Context>(relaxed = true)
        every { context.packageManager } returns packageManager
        every {
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        } returns resolveInfo("com.google.android.packageinstaller")
        every {
            packageManager.queryIntentActivities(any(), PackageManager.MATCH_DEFAULT_ONLY)
        } returns listOf(
            resolveInfo("com.miui.packageinstaller"),
            resolveInfo("com.google.android.packageinstaller"),
        )
        every {
            packageManager.queryIntentActivities(intent, 0)
        } returns listOf(
            resolveInfo("com.android.packageinstaller"),
            resolveInfo("com.miui.packageinstaller"),
            resolveInfo("com.example.thirdparty.viewer", flags = 0),
        )

        val count = InstallIntents.grantReadPermissionToInstallers(context, intent, uri)

        assertEquals(3, count)
        verify(exactly = 1) {
            context.grantUriPermission(
                "com.google.android.packageinstaller",
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        verify(exactly = 1) {
            context.grantUriPermission(
                "com.miui.packageinstaller",
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        verify(exactly = 1) {
            context.grantUriPermission(
                "com.android.packageinstaller",
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        verify(exactly = 0) {
            context.grantUriPermission(
                "com.example.thirdparty.viewer",
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    @Test
    fun `build unknown sources intent points to package settings`() {
        val intent = InstallIntents.manageUnknownAppSources("com.hank.musicfree")
        assertEquals(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
        assertEquals(Uri.parse("package:com.hank.musicfree"), intent.data)
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    private fun resolveInfo(packageName: String): ResolveInfo =
        ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName
                applicationInfo = ApplicationInfo().apply {
                    this.packageName = packageName
                    flags = ApplicationInfo.FLAG_SYSTEM
                }
            }
        }

    private fun resolveInfo(packageName: String, flags: Int): ResolveInfo =
        ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName
                applicationInfo = ApplicationInfo().apply {
                    this.packageName = packageName
                    this.flags = flags
                }
            }
        }
}

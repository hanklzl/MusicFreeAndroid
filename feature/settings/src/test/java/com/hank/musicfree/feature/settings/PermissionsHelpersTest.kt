package com.hank.musicfree.feature.settings

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.hank.musicfree.core.permissions.requiredAudioPermission
import com.hank.musicfree.core.permissions.requiredNotificationPermission
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PermissionsHelpersTest {

    @Test
    fun `requiredAudioPermission returns READ_MEDIA_AUDIO on API 33 and above`() {
        assertEquals(
            Manifest.permission.READ_MEDIA_AUDIO,
            requiredAudioPermission(Build.VERSION_CODES.TIRAMISU),
        )
    }

    @Test
    fun `requiredAudioPermission returns READ_EXTERNAL_STORAGE below API 33`() {
        assertEquals(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            requiredAudioPermission(Build.VERSION_CODES.TIRAMISU - 1),
        )
    }

    @Test
    fun `requiredNotificationPermission returns POST_NOTIFICATIONS on API 33 and above`() {
        assertEquals(
            Manifest.permission.POST_NOTIFICATIONS,
            requiredNotificationPermission(Build.VERSION_CODES.TIRAMISU),
        )
    }

    @Test
    fun `requiredNotificationPermission returns null below API 33`() {
        assertNull(requiredNotificationPermission(Build.VERSION_CODES.TIRAMISU - 1))
    }

    @Test
    fun `hasNotificationPermission returns true below API 33`() {
        val context: Context = RuntimeEnvironment.getApplication()

        assertTrue(hasNotificationPermission(context, Build.VERSION_CODES.TIRAMISU - 1))
    }

    @Test
    fun `openOverlaySettings returns false when intent cannot resolve`() {
        val packageManager = mock<PackageManager>()
        whenever(packageManager.resolveActivity(any(), any<Int>())).thenReturn(null)
        val context = OverlaySettingsTestContext(
            baseContext = RuntimeEnvironment.getApplication(),
            packageNameValue = "com.example.app",
            packageManagerValue = packageManager,
        )

        assertFalse(openOverlaySettings(context))
        assertNull(context.startedIntent)
    }

    @Test
    fun `readPermissionsUiState maps default application context`() {
        val context: Context = RuntimeEnvironment.getApplication()

        val uiState = readPermissionsUiState(context)

        assertFalse(uiState.overlayGranted)
        assertFalse(uiState.storageAudioGranted)
        assertFalse(uiState.notificationGranted)
    }
}

private class OverlaySettingsTestContext(
    baseContext: Context,
    private val packageNameValue: String,
    private val packageManagerValue: PackageManager,
) : ContextWrapper(baseContext) {
    var startedIntent: Intent? = null
        private set

    override fun getPackageName(): String = packageNameValue

    override fun getPackageManager(): PackageManager = packageManagerValue

    override fun startActivity(intent: Intent) {
        startedIntent = intent
    }
}

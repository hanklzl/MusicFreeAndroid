package com.zili.android.musicfreeandroid.plugin.manager

import android.content.Context
import com.zili.android.musicfreeandroid.plugin.meta.PluginMetaStore
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PluginManagerUpdateFlowTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createManager(): PluginManager {
        val context = mock<Context>()
        whenever(context.filesDir).thenReturn(tempFolder.root)
        val pluginMetaStore = mock<PluginMetaStore>()
        whenever(pluginMetaStore.getUserVariables(any())).thenReturn(flowOf(emptyMap()))
        return PluginManager(context, pluginMetaStore)
    }

    @Test
    fun `updatePlugin returns internal error when plugin is missing`() = runTest {
        val manager = createManager()

        val result = manager.updatePlugin("missing-plugin")

        assertEquals(PluginOperationType.UPDATE_SINGLE, result.operationType)
        assertEquals(0, result.successCount)
        assertEquals(1, result.failureCount)
        assertEquals(PluginOperationErrorCode.INTERNAL_ERROR, result.failures.first().errorCode)
    }

    @Test
    fun `updateFromSubscriptionUrl returns source invalid when url is blank`() = runTest {
        val manager = createManager()

        val result = manager.updateFromSubscriptionUrl("   ")

        assertEquals(PluginOperationType.UPDATE_SUBSCRIPTION, result.operationType)
        assertEquals(0, result.successCount)
        assertEquals(1, result.failureCount)
        assertEquals(PluginOperationErrorCode.SOURCE_INVALID, result.failures.first().errorCode)
    }

    @Test
    fun `updateAllPlugins succeeds with empty target set`() = runTest {
        val manager = createManager()

        val result = manager.updateAllPlugins()

        assertEquals(PluginOperationType.UPDATE_ALL, result.operationType)
        assertTrue(result.targetPlugins.isEmpty())
        assertEquals(0, result.successCount)
        assertEquals(0, result.failureCount)
        assertTrue(result.isSuccess)
    }
}

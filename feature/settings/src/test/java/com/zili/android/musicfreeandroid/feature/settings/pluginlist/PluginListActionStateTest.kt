package com.zili.android.musicfreeandroid.feature.settings.pluginlist

import com.zili.android.musicfreeandroid.feature.settings.MainDispatcherRule
import com.zili.android.musicfreeandroid.plugin.manager.PluginOperationErrorCode
import com.zili.android.musicfreeandroid.plugin.manager.PluginOperationFailure
import com.zili.android.musicfreeandroid.plugin.manager.PluginOperationResult
import com.zili.android.musicfreeandroid.plugin.manager.PluginOperationType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PluginListActionStateTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `partial failures expose detail rows`() {
        val state = PluginOperationUiState.fromResult(
            successMessage = "更新完成",
            partialMessage = "部分插件更新失败",
            failureMessage = "全部插件更新失败",
            result = PluginOperationResult(
                operationType = PluginOperationType.UPDATE_ALL,
                targetPlugins = listOf("a", "b"),
                successCount = 1,
                failureCount = 1,
                failures = listOf(
                    PluginOperationFailure(
                        targetPlugin = "b",
                        sourceRef = "https://example.com/b.js",
                        errorCode = PluginOperationErrorCode.SOURCE_UNREACHABLE,
                        message = "下载失败",
                    ),
                ),
                startedAtEpochMs = 1,
                finishedAtEpochMs = 2,
            ),
        )

        val partial = state as PluginOperationUiState.PartialFailure
        assertEquals("部分插件更新失败", partial.message)
        assertEquals("b", partial.failures.first().pluginName)
        assertEquals("https://example.com/b.js", partial.failures.first().source)
        assertEquals("下载失败", partial.failures.first().message)
    }

    @Test
    fun `zero success with failures maps to failure`() {
        val state = PluginOperationUiState.fromResult(
            successMessage = "安装成功",
            partialMessage = "部分插件安装失败",
            failureMessage = "安装失败",
            result = PluginOperationResult(
                operationType = PluginOperationType.ADD,
                targetPlugins = listOf("https://example.com/plugins.json"),
                successCount = 0,
                failureCount = 1,
                failures = listOf(
                    PluginOperationFailure(
                        sourceRef = "https://example.com/plugins.json",
                        errorCode = PluginOperationErrorCode.SOURCE_INVALID,
                        message = "订阅条目缺少插件地址",
                    ),
                ),
                startedAtEpochMs = 1,
                finishedAtEpochMs = 2,
            ),
        )

        val failure = state as PluginOperationUiState.Failure
        assertEquals("安装失败", failure.message)
        assertEquals("订阅条目缺少插件地址", failure.failures.first().message)
    }
}

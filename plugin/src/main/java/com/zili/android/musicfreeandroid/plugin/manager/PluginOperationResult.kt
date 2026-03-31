package com.zili.android.musicfreeandroid.plugin.manager

enum class PluginOperationType {
    ADD,
    UPDATE_SINGLE,
    UPDATE_ALL,
    UPDATE_SUBSCRIPTION,
}

enum class PluginOperationErrorCode {
    SOURCE_UNREACHABLE,
    SOURCE_INVALID,
    MISSING_UPDATE_SOURCE,
    VERSION_NOT_UPGRADABLE,
    DUPLICATE_PLUGIN,
    INTERNAL_ERROR,
}

data class PluginOperationFailure(
    val targetPlugin: String? = null,
    val sourceRef: String? = null,
    val errorCode: PluginOperationErrorCode,
    val message: String,
)

data class PluginOperationResult(
    val operationType: PluginOperationType,
    val targetPlugins: List<String>,
    val successCount: Int,
    val failureCount: Int,
    val failures: List<PluginOperationFailure> = emptyList(),
    val startedAtEpochMs: Long,
    val finishedAtEpochMs: Long,
) {
    val isSuccess: Boolean
        get() = failureCount == 0
}

internal data class PluginInstallMetadata(
    val sourceType: PluginInstallSourceType,
    val sourceValue: String? = null,
)

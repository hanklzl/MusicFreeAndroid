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

    /**
     * Hash collision with an already-installed plugin.
     *
     * As of Phase C5 the install pipeline NO LONGER emits this code: hash
     * collisions are now silently idempotent and report a structured
     * "success" result, matching RN behaviour. The enum value is preserved
     * for backwards-compatible deserialization of any persisted operation
     * logs and for downstream code that still pattern-matches on it.
     */
    @Deprecated(
        message = "Hash collisions are now silently idempotent; pipeline emits success instead.",
        level = DeprecationLevel.WARNING,
    )
    DUPLICATE_PLUGIN,

    /** Plugin missing required `platform` field on `module.exports`. */
    MISSING_PLATFORM,

    /** Plugin `appVersion` does not satisfy the host (Phase E will populate). */
    VERSION_REJECTED,

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

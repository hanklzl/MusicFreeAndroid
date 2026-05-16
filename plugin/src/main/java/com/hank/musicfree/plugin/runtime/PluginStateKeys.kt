package com.hank.musicfree.plugin.runtime

/**
 * Stable string constants for [PluginState] / [PluginErrorReason] used in
 * structured logs. Decoupled from `name`/`simpleName` so R8 minification doesn't
 * change emitted values.
 */
object PluginStateKeys {
    const val STATE_INITIALIZING = "Initializing"
    const val STATE_LOADING = "Loading"
    const val STATE_MOUNTED = "Mounted"
    const val STATE_FAILED = "Failed"

    const val REASON_VERSION_NOT_MATCH = "VersionNotMatch"
    const val REASON_CANNOT_PARSE = "CannotParse"
    const val REASON_MISSING_PLATFORM = "MissingPlatform"
    const val REASON_DOWNLOAD_FAILED = "DownloadFailed"
    const val REASON_USER_VARIABLE_SYNC_FAILED = "UserVariableSyncFailed"

    fun stateKey(state: PluginState): String = when (state) {
        PluginState.Initializing -> STATE_INITIALIZING
        PluginState.Loading -> STATE_LOADING
        PluginState.Mounted -> STATE_MOUNTED
        is PluginState.Failed -> STATE_FAILED
    }

    fun reasonKey(reason: PluginErrorReason): String = when (reason) {
        PluginErrorReason.VersionNotMatch -> REASON_VERSION_NOT_MATCH
        PluginErrorReason.CannotParse -> REASON_CANNOT_PARSE
        PluginErrorReason.MissingPlatform -> REASON_MISSING_PLATFORM
        PluginErrorReason.DownloadFailed -> REASON_DOWNLOAD_FAILED
        PluginErrorReason.UserVariableSyncFailed -> REASON_USER_VARIABLE_SYNC_FAILED
    }
}

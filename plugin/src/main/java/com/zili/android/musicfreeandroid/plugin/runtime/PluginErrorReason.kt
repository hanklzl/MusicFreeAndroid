package com.zili.android.musicfreeandroid.plugin.runtime

/**
 * Structured reason for a plugin in [PluginState.Failed] state. Reasons are
 * stable enough to drive UI error panels and structured logs; surface text is
 * mapped in the UI layer (`feature/settings/.../plugin/PluginErrorPanel.kt`).
 *
 * Use [PluginStateKeys.reasonKey] when emitting a stable string identifier so
 * R8 minification cannot rename the value.
 */
enum class PluginErrorReason {
    /** Plugin declared `appVersion` does not satisfy the host app version. */
    VersionNotMatch,

    /** JS evaluation / metadata extraction threw or returned an unusable value. */
    CannotParse,

    /** Required `platform` field was blank/missing on `module.exports`. */
    MissingPlatform,

    /** HTTP download for `installFromUrl` failed (non-2xx or IO error). */
    DownloadFailed,

    /** Runtime push of saved user variables into the JS sandbox failed. */
    UserVariableSyncFailed,
}

package com.hank.musicfree.plugin.runtime

/**
 * Lifecycle state of a single plugin entry inside `PluginManager.allEntries`.
 *
 * Modelled as a `sealed interface` (not enum) so the [Failed] variant can carry
 * the structured [PluginErrorReason] plus a free-form detail string for UI
 * surfacing. The set of value-typed states is closed; new states must be added
 * here AND in [PluginStateKeys.stateKey].
 */
sealed interface PluginState {
    /** Entry was discovered (file on disk, install just enqueued) but loading hasn't started. */
    data object Initializing : PluginState

    /** Engine is currently evaluating the plugin source / extracting metadata. */
    data object Loading : PluginState

    /** Plugin is loaded and exposes [com.hank.musicfree.plugin.api.PluginApi]. */
    data object Mounted : PluginState

    /** Terminal failure state; detail is best-effort human-readable context. */
    data class Failed(val reason: PluginErrorReason, val detail: String?) : PluginState
}

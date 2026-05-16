package com.hank.musicfree.plugin.manager

import com.hank.musicfree.plugin.api.PluginInfo
import com.hank.musicfree.plugin.runtime.PluginState

/**
 * One row of [PluginManager.allEntries]: identifies a plugin file on disk (or
 * the built-in local entry, when [filePath] is null) plus its current runtime
 * state and (when mounted) the corresponding [LoadedPlugin].
 *
 * Invariants:
 *  - When [state] is [PluginState.Mounted], [loaded] and [info] MUST be non-null
 *    and [info.platform] is the canonical identifier for the entry.
 *  - When [state] is [PluginState.Failed], [loaded] MUST be null. [info] /
 *    [attemptedPlatform] are best-effort: they may be non-null if the failure
 *    happened after metadata extraction (e.g. user-variable sync failure).
 *  - The built-in 本地 plugin entry uses [filePath] = null and [installSource]
 *    = null; it always appears as [PluginState.Mounted].
 */
data class PluginEntry(
    val filePath: String?,
    val state: PluginState,
    val info: PluginInfo?,
    val loaded: LoadedPlugin?,
    val installSource: PluginInstallSource?,
    val attemptedPlatform: String?,
)

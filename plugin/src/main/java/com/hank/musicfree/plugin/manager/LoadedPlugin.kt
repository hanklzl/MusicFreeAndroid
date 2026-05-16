package com.hank.musicfree.plugin.manager

import com.hank.musicfree.plugin.api.PluginApi

enum class PluginInstallSourceType {
    LOCAL_FILE,
    PLUGIN_URL,
    SUBSCRIPTION_URL,
    UPDATE_SINGLE,
    UPDATE_ALL,
    UPDATE_SUBSCRIPTION,
}

data class PluginInstallSource(
    val type: PluginInstallSourceType,
    val value: String? = null,
)

/**
 * A loaded plugin that exposes the project's [PluginApi]. Two implementations:
 *
 * - [JsLoadedPlugin]: QuickJS-backed; loaded from a `.js` file on disk.
 * - [LocalLoadedPlugin]: in-process Kotlin implementation that serves the
 *   built-in "本地" platform (local audio files).
 *
 * Kept as a plain `interface` (not `sealed`) so the existing Mockito-based test
 * suites can continue to `mock<LoadedPlugin>()`. Mockito 5 refuses to mock
 * sealed interfaces. The set of legitimate implementations is bounded by
 * convention; new implementations should live in this same package so they are
 * easy to enumerate.
 *
 * [filePath] is null for non-disk plugins; [installSource] is null when the
 * plugin is not produced by the install/update pipeline (i.e., the built-in
 * local plugin).
 */
interface LoadedPlugin : PluginApi {
    val filePath: String?
    val installSource: PluginInstallSource?

    suspend fun updateUserVariables(values: Map<String, String>)
    suspend fun destroy()
}

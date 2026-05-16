package com.hank.musicfree.feature.home.playlistimport

import com.hank.musicfree.core.model.MusicItem

data class ImportCapablePlugin(
    val platform: String,
    val name: String,
    val hints: List<String> = emptyList(),
)

sealed interface PlaylistImportState {
    data object Idle : PlaylistImportState
    data object LoadingPlugins : PlaylistImportState
    data class ChoosePlugin(val plugins: List<ImportCapablePlugin>) : PlaylistImportState
    data class InputUrl(
        val plugin: ImportCapablePlugin,
        val errorMessage: String? = null,
    ) : PlaylistImportState

    data class Parsing(val pluginName: String) : PlaylistImportState
    data class ConfirmFound(
        val plugin: ImportCapablePlugin,
        val items: List<MusicItem>,
    ) : PlaylistImportState

    data class ChooseTarget(val items: List<MusicItem>) : PlaylistImportState
    data class Completed(
        val added: Int,
        val skipped: Int,
    ) : PlaylistImportState

    data class Error(val message: String) : PlaylistImportState
}

sealed interface PlaylistImportEvent {
    data class Toast(val message: String) : PlaylistImportEvent
}

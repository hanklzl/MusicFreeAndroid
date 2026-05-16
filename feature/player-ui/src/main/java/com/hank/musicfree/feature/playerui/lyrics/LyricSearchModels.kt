package com.hank.musicfree.feature.playerui.lyrics

import com.hank.musicfree.core.model.MusicItem
import com.hank.musicfree.plugin.api.PluginInfo

data class LyricSearchGroup(
    val plugin: PluginInfo,
    val items: List<MusicItem>,
    val errorMessage: String? = null,
)

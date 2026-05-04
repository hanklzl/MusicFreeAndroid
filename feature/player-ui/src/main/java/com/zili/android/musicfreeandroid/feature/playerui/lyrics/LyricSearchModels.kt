package com.zili.android.musicfreeandroid.feature.playerui.lyrics

import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.plugin.api.PluginInfo

data class LyricSearchGroup(
    val plugin: PluginInfo,
    val items: List<MusicItem>,
    val errorMessage: String? = null,
)

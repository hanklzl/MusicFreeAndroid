package com.hank.musicfree.plugin.api

data class PluginUserVariable(
    val key: String,
    val name: String? = null,
    val hint: String? = null,
)

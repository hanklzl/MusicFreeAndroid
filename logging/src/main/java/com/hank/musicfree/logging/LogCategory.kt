package com.hank.musicfree.logging

enum class LogCategory(val wireName: String) {
    APP("app"),
    PLUGIN("plugin"),
    SEARCH("search"),
    PLAYER("player"),
    PLAYLIST_IMPORT("playlist_import"),
    FEEDBACK("feedback"),
    DATA("data"),
    FILE_IO("file_io"),
    DOWNLOAD("download"),
    SETTINGS("settings"),
    HOME("home"),
    LYRICS("lyrics"),
    UPDATE("update"),
    RUNTIME("runtime"),
}

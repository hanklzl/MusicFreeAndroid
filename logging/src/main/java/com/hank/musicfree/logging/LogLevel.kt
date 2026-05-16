package com.hank.musicfree.logging

enum class LogLevel(val wireName: String, val loganType: Int) {
    TRACE("trace", 1),
    DETAIL("detail", 2),
    ERROR("error", 3),
}

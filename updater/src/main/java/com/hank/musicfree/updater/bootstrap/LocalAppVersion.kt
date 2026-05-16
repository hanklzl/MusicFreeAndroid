package com.hank.musicfree.updater.bootstrap

interface LocalAppVersion {
    val versionCode: Long
    val versionName: String
    val isDebugBuild: Boolean
}

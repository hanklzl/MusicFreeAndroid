package com.zili.android.musicfreeandroid.updater.bootstrap

interface LocalAppVersion {
    val versionCode: Long
    val versionName: String
    val isDebugBuild: Boolean
}

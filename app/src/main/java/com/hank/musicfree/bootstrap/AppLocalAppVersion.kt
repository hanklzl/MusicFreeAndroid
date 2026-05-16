package com.hank.musicfree.bootstrap

import com.hank.musicfree.BuildConfig
import com.hank.musicfree.updater.bootstrap.LocalAppVersion
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLocalAppVersion @Inject constructor() : LocalAppVersion {
    override val versionCode: Long = BuildConfig.VERSION_CODE.toLong()
    override val versionName: String = BuildConfig.VERSION_NAME
    override val isDebugBuild: Boolean = BuildConfig.DEBUG
}

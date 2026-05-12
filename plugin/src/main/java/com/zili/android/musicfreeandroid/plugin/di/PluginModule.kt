package com.zili.android.musicfreeandroid.plugin.di

import android.content.Context
import com.zili.android.musicfreeandroid.core.media.MediaSourceResolver
import com.zili.android.musicfreeandroid.core.media.StaleUrlRefresher
import com.zili.android.musicfreeandroid.plugin.media.PluginMediaSourceService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PluginModule {
    @Binds
    @Singleton
    abstract fun bindMediaSourceResolver(
        impl: PluginMediaSourceService,
    ): MediaSourceResolver

    @Binds
    @Singleton
    abstract fun bindStaleUrlRefresher(
        impl: PluginMediaSourceService,
    ): StaleUrlRefresher

    companion object {
        /**
         * Provides the host app's `versionName` as a string for plugin lifecycle
         * components (e.g. [com.zili.android.musicfreeandroid.plugin.runtime.PluginAppVersionGate]
         * and the metadata cache freshness check). Resolved at injection time
         * via `PackageManager.getPackageInfo(...)`; falls back to "0.0.0" when
         * the lookup throws (defensive — should never happen in practice).
         */
        @Provides
        @Singleton
        @Named(APP_VERSION_NAMED)
        fun provideAppVersionName(@ApplicationContext context: Context): String {
            return try {
                context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .versionName
                    ?: "0.0.0"
            } catch (e: Exception) {
                "0.0.0"
            }
        }

        /** Hilt qualifier name for [provideAppVersionName]. */
        const val APP_VERSION_NAMED = "appVersion"
    }
}

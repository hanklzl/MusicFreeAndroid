package com.zili.android.musicfreeandroid.plugin.di

import com.zili.android.musicfreeandroid.core.media.MediaSourceResolver
import com.zili.android.musicfreeandroid.plugin.media.PluginMediaSourceService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PluginModule {
    @Binds
    @Singleton
    abstract fun bindMediaSourceResolver(
        impl: PluginMediaSourceService,
    ): MediaSourceResolver
}

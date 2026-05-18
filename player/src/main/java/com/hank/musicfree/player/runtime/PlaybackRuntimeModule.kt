package com.hank.musicfree.player.runtime

import com.hank.musicfree.core.runtime.RuntimeStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackRuntimeModule {
    @Binds
    @IntoSet
    @Singleton
    abstract fun bindPlaybackRuntimeStore(store: PlaybackRuntimeStore): RuntimeStore<*>
}

package com.zili.android.musicfreeandroid.player.di

import com.zili.android.musicfreeandroid.player.network.AndroidPlaybackNetworkStateProvider
import com.zili.android.musicfreeandroid.player.network.PlaybackNetworkStateProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {
    // PlayerController is provided via @Inject constructor.
    // Additional player-related bindings can be added here in the future.

    @Provides
    @Singleton
    fun providePlaybackNetworkStateProvider(
        provider: AndroidPlaybackNetworkStateProvider,
    ): PlaybackNetworkStateProvider = provider
}

package com.hank.musicfree.player.di

import com.hank.musicfree.data.db.dao.ListenStatsDao
import com.hank.musicfree.player.listening.ListenTracker
import com.hank.musicfree.player.network.AndroidPlaybackNetworkStateProvider
import com.hank.musicfree.player.network.PlaybackNetworkStateProvider
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

    /**
     * Provide [ListenTracker] with its private defaults (nowMs = wall clock,
     * scope = IO-backed SupervisorJob). Tests construct [ListenTracker] directly
     * with a [TestScope] for deterministic time control.
     */
    @Provides
    @Singleton
    fun provideListenTracker(dao: ListenStatsDao): ListenTracker = ListenTracker(dao)
}

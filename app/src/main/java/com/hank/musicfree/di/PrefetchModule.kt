package com.hank.musicfree.di

import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.downloader.io.NetworkMonitor
import com.hank.musicfree.player.controller.PlayerController
import com.hank.musicfree.player.prefetch.PrefetchCoordinator
import com.hank.musicfree.player.source.HeaderInjectingDataSourceFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires [PrefetchCoordinator] in :app because it depends on both [PlayerController] (:player)
 * and [NetworkMonitor] (:downloader). Neither :player nor :downloader may depend on the other
 * given the module dep direction (:app → :feature:* → :data, :player, :plugin → :core).
 * :app sees both modules, so the cross-module binding lives here.
 */
@Module
@InstallIn(SingletonComponent::class)
object PrefetchModule {

    @Provides
    @Singleton
    fun providePrefetchCoordinator(
        resolver: MediaSourceResolver,
        playerController: PlayerController,
        networkMonitor: NetworkMonitor,
        headerInjectingDataSourceFactory: HeaderInjectingDataSourceFactory,
    ): PrefetchCoordinator = PrefetchCoordinator(
        resolver = resolver,
        progressFlow = playerController.progressTickFlow,
        nextItemFlow = playerController.nextItemFlow,
        isWifiFlow = networkMonitor.isWifi,
        headerInjectingDataSourceFactory = headerInjectingDataSourceFactory,
    )
}

package com.hank.musicfree.di

import com.hank.musicfree.core.cache.ByteCacheStatusStore
import com.hank.musicfree.core.media.MediaSourceResolver
import com.hank.musicfree.downloader.io.NetworkMonitor
import com.hank.musicfree.player.controller.PlayerController
import com.hank.musicfree.player.cache.ByteCacheInspector
import com.hank.musicfree.player.prefetch.PrefetchCoordinator
import com.hank.musicfree.player.source.HeaderInjectingDataSourceFactory
import com.hank.musicfree.player.source.PlaybackCacheKeyRegistrar
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
        cacheKeyRegistrar: PlaybackCacheKeyRegistrar,
        headerInjectingDataSourceFactory: HeaderInjectingDataSourceFactory,
        byteCacheStatusStore: ByteCacheStatusStore,
        byteCacheInspector: ByteCacheInspector,
    ): PrefetchCoordinator = PrefetchCoordinator(
        resolver = resolver,
        progressFlow = playerController.progressTickFlow,
        nextItemFlow = playerController.nextItemFlow,
        isWifiFlow = networkMonitor.isWifi,
        currentQualityFlow = playerController.currentQualityFlow,
        cacheKeyRegistrar = cacheKeyRegistrar,
        headerInjectingDataSourceFactory = headerInjectingDataSourceFactory,
        byteCacheStatusStore = byteCacheStatusStore,
        byteCacheInspector = byteCacheInspector,
    )
}

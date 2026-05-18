package com.hank.musicfree.player.cache

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaCacheStoreBindings {
    @Binds @Singleton abstract fun bind(impl: MediaCacheStoreImpl): MediaCacheStore
}

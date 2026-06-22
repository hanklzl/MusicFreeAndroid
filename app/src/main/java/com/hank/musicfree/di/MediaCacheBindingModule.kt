package com.hank.musicfree.di

import com.hank.musicfree.core.cache.ByteCacheStatusStore
import com.hank.musicfree.data.datastore.AppPreferences
import com.hank.musicfree.data.db.dao.MediaCacheDao
import com.hank.musicfree.data.repository.MediaCacheRepository
import com.hank.musicfree.player.cache.SimpleCacheHolder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides [MediaCacheRepository] with its [SimpleCacheHolder] eviction callback wired in.
 *
 * This binding lives in :app (rather than :data) because [MediaCacheRepository] is in :data
 * and [SimpleCacheHolder] is in :player — :data cannot depend on :player without inverting
 * the module dependency direction. :app sees both, so it performs the cross-module wiring.
 *
 * [MediaCacheRepository] does NOT carry an @Inject constructor; all production
 * instantiation goes through this provider.
 */
@Module
@InstallIn(SingletonComponent::class)
object MediaCacheBindingModule {

    @Provides
    @Singleton
    fun provideMediaCacheRepository(
        dao: MediaCacheDao,
        appPreferences: AppPreferences,
        simpleCacheHolder: SimpleCacheHolder,
        byteCacheStatusStore: ByteCacheStatusStore,
    ): MediaCacheRepository = MediaCacheRepository.create(
        dao = dao,
        appPreferences = appPreferences,
        evictor = simpleCacheHolder,
        byteCacheStatusStore = byteCacheStatusStore,
    )
}

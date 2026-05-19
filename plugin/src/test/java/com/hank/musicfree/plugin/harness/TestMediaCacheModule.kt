package com.hank.musicfree.plugin.harness

import com.hank.musicfree.data.db.dao.MediaCacheDao
import com.hank.musicfree.data.repository.MediaCacheRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.mockito.kotlin.mock

/**
 * Hilt test module that provides a no-op [MediaCacheRepository] for :plugin unit tests.
 *
 * [MediaCacheRepository] no longer carries an @Inject constructor — production wiring
 * is done in :app (MediaCacheBindingModule) to allow cross-module SimpleCacheHolder
 * wiring. :plugin unit tests that use HiltAndroidTest (e.g. PluginManagerClientContractTest)
 * need this stub to satisfy the dependency graph without pulling in :player or :app.
 */
@Module
@InstallIn(SingletonComponent::class)
object TestMediaCacheModule {

    @Provides
    @Singleton
    fun provideMediaCacheRepository(): MediaCacheRepository {
        val dao = mock<MediaCacheDao>()
        return MediaCacheRepository(dao)
    }
}

package com.hank.musicfree.feature.search.runtime

import com.hank.musicfree.core.runtime.RuntimeStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SearchRuntimeModule {
    @Binds
    @IntoSet
    @Singleton
    abstract fun bindSearchSessionStore(store: SearchSessionStore): RuntimeStore<*>

    @Binds
    @Singleton
    abstract fun bindSearchSessionGateway(gateway: PluginManagerSearchSessionGateway): SearchSessionGateway

    @Binds
    @Singleton
    abstract fun bindSearchPluginSignatureProvider(
        gateway: PluginManagerSearchSessionGateway,
    ): SearchPluginSignatureProvider
}

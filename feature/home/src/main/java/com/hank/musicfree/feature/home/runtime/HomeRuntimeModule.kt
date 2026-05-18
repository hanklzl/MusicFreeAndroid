package com.hank.musicfree.feature.home.runtime

import com.hank.musicfree.core.runtime.RuntimeStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeRuntimeModule {
    @Binds
    @IntoSet
    @Singleton
    abstract fun bindRouteSeedRuntimeStore(store: RouteSeedRuntimeStore): RuntimeStore<*>

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindDetailSessionStore(store: DetailSessionStore): RuntimeStore<*>

    @Binds
    @Singleton
    abstract fun bindDetailSessionGateway(gateway: PluginManagerDetailSessionGateway): DetailSessionGateway

    @Binds
    @Singleton
    abstract fun bindDetailPluginSignatureProvider(
        gateway: PluginManagerDetailSessionGateway,
    ): DetailPluginSignatureProvider
}

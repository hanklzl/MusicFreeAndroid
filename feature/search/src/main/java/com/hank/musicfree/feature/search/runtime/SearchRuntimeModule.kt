package com.hank.musicfree.feature.search.runtime

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SearchRuntimeModule {
    // SearchSessionStore is intentionally NOT bound as a process-level RuntimeStore:
    // it is page-scoped (one instance per SearchViewModel, injected via its @Inject
    // constructor) so search state resets on screen exit. See
    // docs/dev-harness/runtime/rules.md#rule-runtime-state-classification.

    @Binds
    @Singleton
    abstract fun bindSearchSessionGateway(gateway: PluginManagerSearchSessionGateway): SearchSessionGateway

    @Binds
    @Singleton
    abstract fun bindSearchPluginSignatureProvider(
        gateway: PluginManagerSearchSessionGateway,
    ): SearchPluginSignatureProvider
}

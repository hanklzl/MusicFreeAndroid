package com.hank.musicfree.runtime

import com.hank.musicfree.core.runtime.RuntimeStore
import com.hank.musicfree.core.runtime.UiRuntimeStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RuntimeStoreBindingsModule {

    @Multibinds
    abstract fun runtimeStores(): Set<@JvmSuppressWildcards RuntimeStore<*>>

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindUiRuntimeStore(store: UiRuntimeStore): RuntimeStore<*>
}

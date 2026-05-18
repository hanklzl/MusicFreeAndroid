package com.hank.musicfree.data.traffic

import com.hank.musicfree.core.network.TrafficSampleSink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TrafficSinkBindings {
    @Binds
    @Singleton
    abstract fun bindSink(impl: TrafficSampleSinkImpl): TrafficSampleSink
}

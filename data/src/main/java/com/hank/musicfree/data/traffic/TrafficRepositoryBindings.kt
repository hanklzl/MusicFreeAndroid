package com.hank.musicfree.data.traffic

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TrafficRepositoryBindings {
    @Binds
    @Singleton
    abstract fun bindRepo(impl: TrafficStatsRepositoryImpl): TrafficStatsRepository
}

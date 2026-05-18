package com.hank.musicfree.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    @BaseOkHttp
    fun provideBaseOkHttpClient(
        factory: NetworkTrafficEventListener.Factory,
    ): OkHttpClient = OkHttpClient.Builder()
        .eventListenerFactory(factory)
        .build()
}

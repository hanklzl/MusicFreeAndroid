package com.hank.musicfree.plugin.engine

import com.hank.musicfree.core.network.BaseOkHttp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Provides the [WebDavShim] singleton wired to a `@BaseOkHttp`-derived
 * [OkHttpClient] so every WebDAV request is observed by
 * [com.hank.musicfree.core.network.NetworkTrafficEventListener.Factory] and
 * accounted in `traffic_daily`.
 *
 * The WebDAV client overrides only timeout profile (10s vs. axios' 2000ms);
 * connection pool / dispatcher / event listener factory are inherited from the
 * shared base client.
 */
@Module
@InstallIn(SingletonComponent::class)
object WebDavShimModule {
    @Provides
    @Singleton
    fun provideWebDavShim(@BaseOkHttp base: OkHttpClient): WebDavShim =
        WebDavShim(
            base.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        )
}

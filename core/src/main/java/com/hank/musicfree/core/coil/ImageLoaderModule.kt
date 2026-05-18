package com.hank.musicfree.core.coil

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.hank.musicfree.core.network.BaseOkHttp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * 项目统一 ImageLoader,把 Coil 的 HTTP fetcher 接到 @BaseOkHttp 派生 client,
 * 让所有图片加载流量经过 NetworkTrafficEventListener 进入 traffic_daily。
 *
 * 应用启动时 [com.hank.musicfree.MusicFreeApplication] 实现
 * [coil3.SingletonImageLoader.Factory] 把这个实例提供给 Coil singleton 入口,
 * 之后 `context.imageLoader` / `AsyncImage` 等默认入口都会复用同一个 client。
 *
 * Coil 3.4 的 Kotlin API 入口是顶层函数 [OkHttpNetworkFetcherFactory]
 * (JVM 名为 `OkHttpNetworkFetcher.factory(...)`),返回 `NetworkFetcher.Factory`,
 * 直接放进 `components { add(...) }` 即可覆盖默认的 ktor/okhttp fetcher。
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @BaseOkHttp okHttpClient: OkHttpClient,
    ): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(okHttpClient)) }
        .build()
}

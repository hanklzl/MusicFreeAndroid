package com.zili.android.musicfreeandroid.downloader.di

import android.content.Context
import com.zili.android.musicfreeandroid.data.db.dao.DownloadTaskDao
import com.zili.android.musicfreeandroid.data.db.dao.DownloadedTrackDao
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.repository.MusicRepository
import com.zili.android.musicfreeandroid.downloader.Downloader
import com.zili.android.musicfreeandroid.downloader.DownloaderImpl
import com.zili.android.musicfreeandroid.downloader.engine.DownloadEngine
import com.zili.android.musicfreeandroid.downloader.engine.MediaStoreMusicWriter
import com.zili.android.musicfreeandroid.downloader.io.HttpDownloader
import com.zili.android.musicfreeandroid.downloader.io.NetworkMonitor
import com.zili.android.musicfreeandroid.downloader.io.NetworkState
import com.zili.android.musicfreeandroid.downloader.io.OkHttpDownloader
import com.zili.android.musicfreeandroid.downloader.prefs.DownloadConfig
import com.zili.android.musicfreeandroid.downloader.prefs.DownloadConfigSource
import com.zili.android.musicfreeandroid.downloader.quality.PluginMediaSourceResolver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloaderBindingsModule {
    @Binds @Singleton
    abstract fun bindDownloader(impl: DownloaderImpl): Downloader

    @Binds @Singleton
    abstract fun bindHttp(impl: OkHttpDownloader): HttpDownloader
}

@Module
@InstallIn(SingletonComponent::class)
object DownloaderProvidersModule {

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides @Singleton
    fun provideConfigFlow(source: DownloadConfigSource): @JvmSuppressWildcards StateFlow<DownloadConfig> = source.state

    @Provides @Singleton
    fun provideNetworkFlow(monitor: NetworkMonitor): @JvmSuppressWildcards StateFlow<NetworkState> {
        monitor.start()
        return monitor.state
    }

    @Provides @Singleton
    fun provideEngine(
        taskDao: DownloadTaskDao,
        downloadedDao: DownloadedTrackDao,
        http: HttpDownloader,
        writer: MediaStoreMusicWriter,
        resolver: PluginMediaSourceResolver,
        converters: Converters,
        musicRepository: MusicRepository,
        configFlow: @JvmSuppressWildcards StateFlow<DownloadConfig>,
        networkFlow: @JvmSuppressWildcards StateFlow<NetworkState>,
        @ApplicationContext context: Context,
    ): DownloadEngine = DownloadEngine(
        taskDao = taskDao,
        downloadedDao = downloadedDao,
        http = http,
        writer = { f, name, mime, rel, size -> writer.commit(f, name, mime, rel, size) },
        resolver = resolver::resolve,
        converters = converters,
        musicRepository = musicRepository,
        configFlow = configFlow,
        networkFlow = networkFlow,
        cacheDir = File(context.cacheDir, "download").apply { mkdirs() },
    )
}

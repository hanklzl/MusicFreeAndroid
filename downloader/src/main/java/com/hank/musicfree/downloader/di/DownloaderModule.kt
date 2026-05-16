package com.hank.musicfree.downloader.di

import android.content.Context
import com.hank.musicfree.data.db.dao.DownloadTaskDao
import com.hank.musicfree.data.db.dao.DownloadedTrackDao
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.repository.MusicRepository
import com.hank.musicfree.downloader.Downloader
import com.hank.musicfree.downloader.DownloaderImpl
import com.hank.musicfree.downloader.engine.DownloadEngine
import com.hank.musicfree.downloader.engine.MediaStoreMusicWriter
import com.hank.musicfree.downloader.io.HttpDownloader
import com.hank.musicfree.downloader.io.NetworkMonitor
import com.hank.musicfree.downloader.io.NetworkState
import com.hank.musicfree.downloader.io.OkHttpDownloader
import com.hank.musicfree.downloader.prefs.DownloadConfig
import com.hank.musicfree.downloader.prefs.DownloadConfigSource
import com.hank.musicfree.downloader.quality.PluginMediaSourceResolver
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

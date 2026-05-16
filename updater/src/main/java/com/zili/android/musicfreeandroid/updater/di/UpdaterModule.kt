package com.zili.android.musicfreeandroid.updater.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.zili.android.musicfreeandroid.updater.api.OkHttpUpdateClient
import com.zili.android.musicfreeandroid.updater.api.UpdateClient
import com.zili.android.musicfreeandroid.updater.bootstrap.LocalAppVersion
import com.zili.android.musicfreeandroid.updater.checker.AbiResolver
import com.zili.android.musicfreeandroid.updater.checker.UpdateChecker
import com.zili.android.musicfreeandroid.updater.downloader.ApkDownloader
import com.zili.android.musicfreeandroid.updater.downloader.OkHttpApkDownloader
import com.zili.android.musicfreeandroid.updater.installer.ApkInstaller
import com.zili.android.musicfreeandroid.updater.store.UpdatePreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UpdaterDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UpdaterHttp

private val Context.updaterDataStore: DataStore<Preferences> by preferencesDataStore(name = "updater_prefs")

@Module
@InstallIn(SingletonComponent::class)
object UpdaterModule {

    @Provides
    @Singleton
    @UpdaterDataStore
    fun provideUpdaterDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.updaterDataStore

    @Provides
    @Singleton
    @UpdaterHttp
    fun provideUpdaterOkHttp(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideUpdateClient(@UpdaterHttp http: OkHttpClient): UpdateClient =
        OkHttpUpdateClient(http = http, mirrors = UpdaterMirrors.VERSION_JSON_MIRRORS)

    @Provides
    @Singleton
    fun provideApkDownloader(
        @UpdaterHttp http: OkHttpClient,
        @ApplicationContext context: Context,
    ): ApkDownloader =
        OkHttpApkDownloader(http = http, cacheRoot = { File(context.cacheDir, "updates") })

    @Provides
    @Singleton
    fun provideApkInstaller(@ApplicationContext context: Context): ApkInstaller =
        ApkInstaller(context)

    @Provides
    @Singleton
    fun provideAbiResolver(): AbiResolver = AbiResolver()

    @Provides
    @Singleton
    fun provideUpdateChecker(
        client: UpdateClient,
        prefs: UpdatePreferences,
        abiResolver: AbiResolver,
        localAppVersion: LocalAppVersion,
    ): UpdateChecker = UpdateChecker(
        client = client,
        prefs = prefs,
        abiResolver = abiResolver,
        localCode = localAppVersion.versionCode,
        localName = localAppVersion.versionName,
    )
}

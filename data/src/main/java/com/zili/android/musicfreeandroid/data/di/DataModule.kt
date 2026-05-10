package com.zili.android.musicfreeandroid.data.di

import android.content.ContentResolver
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.zili.android.musicfreeandroid.core.model.PlaybackRuntimeSettings
import com.zili.android.musicfreeandroid.data.db.AppDatabase
import com.zili.android.musicfreeandroid.data.db.SeedFavoriteCallback
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.LyricCacheDao
import com.zili.android.musicfreeandroid.data.db.dao.MediaCacheDao
import com.zili.android.musicfreeandroid.data.db.dao.MusicDao
import com.zili.android.musicfreeandroid.data.db.dao.PlaylistDao
import com.zili.android.musicfreeandroid.data.db.dao.PlayQueueDao
import com.zili.android.musicfreeandroid.data.db.dao.DownloadTaskDao
import com.zili.android.musicfreeandroid.data.db.dao.DownloadedTrackDao
import com.zili.android.musicfreeandroid.data.db.dao.StarredSheetDao
import com.zili.android.musicfreeandroid.data.datastore.AppPlaybackRuntimeSettings
import com.zili.android.musicfreeandroid.data.repository.AppPlaylistDefaultSortProvider
import com.zili.android.musicfreeandroid.data.repository.PlaylistDefaultSortProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "musicfree.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .addCallback(SeedFavoriteCallback)
            .build()

    @Provides
    fun provideMusicDao(db: AppDatabase): MusicDao = db.musicDao()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun providePlayQueueDao(db: AppDatabase): PlayQueueDao = db.playQueueDao()

    @Provides
    fun provideStarredSheetDao(db: AppDatabase): StarredSheetDao = db.starredSheetDao()

    @Provides
    fun provideLyricCacheDao(db: AppDatabase): LyricCacheDao = db.lyricCacheDao()

    @Provides
    fun provideMediaCacheDao(db: AppDatabase): MediaCacheDao = db.mediaCacheDao()

    @Provides
    fun provideDownloadTaskDao(db: AppDatabase): DownloadTaskDao = db.downloadTaskDao()

    @Provides
    fun provideDownloadedTrackDao(db: AppDatabase): DownloadedTrackDao = db.downloadedTrackDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides
    @Singleton
    fun provideConverters(): Converters = Converters()

    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver

    @Provides
    @Singleton
    fun providePlaybackRuntimeSettings(
        settings: AppPlaybackRuntimeSettings,
    ): PlaybackRuntimeSettings = settings

    @Provides
    @Singleton
    fun providePlaylistDefaultSortProvider(
        provider: AppPlaylistDefaultSortProvider,
    ): PlaylistDefaultSortProvider = provider
}

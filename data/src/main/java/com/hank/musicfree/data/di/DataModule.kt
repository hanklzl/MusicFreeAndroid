package com.hank.musicfree.data.di

import android.content.ContentResolver
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.hank.musicfree.core.local.Mp3MetadataReader
import com.hank.musicfree.core.model.PlaybackRuntimeSettings
import com.hank.musicfree.data.backup.BackupAppMetadata
import com.hank.musicfree.data.backup.BackupPrivateLayout
import com.hank.musicfree.data.backup.BackupRepository
import com.hank.musicfree.data.backup.DefaultBackupRepository
import com.hank.musicfree.data.backup.checkpointWal
import com.hank.musicfree.data.db.AppDatabase
import com.hank.musicfree.data.db.SeedFavoriteCallback
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.db.migration.MIGRATION_10_11
import com.hank.musicfree.data.db.migration.MIGRATION_11_12
import com.hank.musicfree.data.db.migration.MIGRATION_12_13
import com.hank.musicfree.data.db.migration.MIGRATION_9_10
import com.hank.musicfree.data.db.dao.LyricCacheDao
import com.hank.musicfree.data.db.dao.MediaCacheDao
import com.hank.musicfree.data.db.dao.MusicDao
import com.hank.musicfree.data.db.dao.PlaylistDao
import com.hank.musicfree.data.db.dao.PlayQueueDao
import com.hank.musicfree.data.db.dao.DownloadTaskDao
import com.hank.musicfree.data.db.dao.DownloadedTrackDao
import com.hank.musicfree.data.db.dao.PluginMetadataCacheDao
import com.hank.musicfree.data.db.dao.ListenStatsDao
import com.hank.musicfree.data.db.dao.StarredSheetDao
import com.hank.musicfree.data.db.dao.TrafficDailyDao
import com.hank.musicfree.data.datastore.AppPlaybackRuntimeSettings
import com.hank.musicfree.data.local.Mp3MetadataReaderImpl
import com.hank.musicfree.data.repository.AppPlaylistDefaultSortProvider
import com.hank.musicfree.data.repository.PlaylistDefaultSortProvider
import com.hank.musicfree.data.repository.PluginMetadataCacheGateway
import com.hank.musicfree.data.repository.PluginMetadataCacheRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import kotlinx.serialization.json.Json
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "musicfree.db")
            .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
            .addCallback(SeedFavoriteCallback)
            .build()

    @Provides
    fun provideMusicDao(db: AppDatabase): MusicDao = db.musicDao()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun providePlayQueueDao(db: AppDatabase): PlayQueueDao = db.playQueueDao()

    @Provides
    fun provideListenStatsDao(db: AppDatabase): ListenStatsDao = db.listenStatsDao()

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
    fun providePluginMetadataCacheDao(db: AppDatabase): PluginMetadataCacheDao =
        db.pluginMetadataCacheDao()

    @Provides
    fun provideTrafficDailyDao(db: AppDatabase): TrafficDailyDao = db.trafficDailyDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore

    @Provides
    @Singleton
    fun provideConverters(): Converters = Converters()

    @Provides
    @Singleton
    fun provideJsonForBackup(): Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    @Provides
    @Singleton
    fun provideBackupAppMetadata(@ApplicationContext context: Context): BackupAppMetadata {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return BackupAppMetadata(
            packageName = context.packageName,
            versionName = packageInfo.versionName ?: "0",
            versionCode = packageInfo.longVersionCode,
        )
    }

    @Provides
    @Singleton
    fun provideBackupPrivateLayout(@ApplicationContext context: Context): BackupPrivateLayout =
        BackupPrivateLayout.from(context)

    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver

    @Provides
    @Singleton
    fun provideBackupRepository(
        contentResolver: ContentResolver,
        appDatabase: AppDatabase,
        layout: BackupPrivateLayout,
        appMetadata: BackupAppMetadata,
        json: Json,
    ): BackupRepository = DefaultBackupRepository(
        contentResolver = contentResolver,
        databaseCheckpoint = appDatabase::checkpointWal,
        layout = layout,
        appMetadata = appMetadata,
        databaseVersion = 11,
        json = json,
    )

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

    @Provides
    @Singleton
    fun provideMp3MetadataReader(
        impl: Mp3MetadataReaderImpl,
    ): Mp3MetadataReader = impl

    @Provides
    @Singleton
    fun providePluginMetadataCacheGateway(
        impl: PluginMetadataCacheRepository,
    ): PluginMetadataCacheGateway = impl

    @Provides
    @Singleton
    fun provideZoneIdProvider(): () -> ZoneId = { ZoneId.systemDefault() }

}

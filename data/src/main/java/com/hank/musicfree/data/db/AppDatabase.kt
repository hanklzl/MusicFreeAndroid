package com.hank.musicfree.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hank.musicfree.data.db.converter.Converters
import com.hank.musicfree.data.db.dao.DownloadTaskDao
import com.hank.musicfree.data.db.dao.DownloadedTrackDao
import com.hank.musicfree.data.db.dao.LyricCacheDao
import com.hank.musicfree.data.db.dao.MediaCacheDao
import com.hank.musicfree.data.db.dao.MusicDao
import com.hank.musicfree.data.db.dao.PlaylistDao
import com.hank.musicfree.data.db.dao.PlayQueueDao
import com.hank.musicfree.data.db.dao.ListenStatsDao
import com.hank.musicfree.data.db.dao.PluginMetadataCacheDao
import com.hank.musicfree.data.db.dao.StarredSheetDao
import com.hank.musicfree.data.db.entity.DownloadTaskEntity
import com.hank.musicfree.data.db.entity.DownloadedTrackEntity
import com.hank.musicfree.data.db.entity.LyricCacheEntity
import com.hank.musicfree.data.db.entity.MediaCacheEntity
import com.hank.musicfree.data.db.entity.MusicItemEntity
import com.hank.musicfree.data.db.entity.PlaylistEntity
import com.hank.musicfree.data.db.entity.PlaylistMusicCrossRef
import com.hank.musicfree.data.db.entity.PlayQueueEntity
import com.hank.musicfree.data.db.entity.PluginMetadataCacheEntity
import com.hank.musicfree.data.db.entity.StarredSheetEntity
import com.hank.musicfree.data.db.entity.ListenEventEntity
import com.hank.musicfree.data.db.entity.ListenEventArtistEntity

@Database(
    entities = [
        MusicItemEntity::class,
        PlaylistEntity::class,
        PlaylistMusicCrossRef::class,
        PlayQueueEntity::class,
        StarredSheetEntity::class,
        LyricCacheEntity::class,
        MediaCacheEntity::class,
        DownloadTaskEntity::class,
        DownloadedTrackEntity::class,
        PluginMetadataCacheEntity::class,
        ListenEventEntity::class,
        ListenEventArtistEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playQueueDao(): PlayQueueDao
    abstract fun starredSheetDao(): StarredSheetDao
    abstract fun lyricCacheDao(): LyricCacheDao
    abstract fun mediaCacheDao(): MediaCacheDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun downloadedTrackDao(): DownloadedTrackDao
    abstract fun pluginMetadataCacheDao(): PluginMetadataCacheDao
    abstract fun listenStatsDao(): ListenStatsDao
}

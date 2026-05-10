package com.zili.android.musicfreeandroid.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zili.android.musicfreeandroid.data.db.converter.Converters
import com.zili.android.musicfreeandroid.data.db.dao.DownloadTaskDao
import com.zili.android.musicfreeandroid.data.db.dao.DownloadedTrackDao
import com.zili.android.musicfreeandroid.data.db.dao.LyricCacheDao
import com.zili.android.musicfreeandroid.data.db.dao.MediaCacheDao
import com.zili.android.musicfreeandroid.data.db.dao.MusicDao
import com.zili.android.musicfreeandroid.data.db.dao.PlaylistDao
import com.zili.android.musicfreeandroid.data.db.dao.PlayQueueDao
import com.zili.android.musicfreeandroid.data.db.dao.StarredSheetDao
import com.zili.android.musicfreeandroid.data.db.entity.DownloadTaskEntity
import com.zili.android.musicfreeandroid.data.db.entity.DownloadedTrackEntity
import com.zili.android.musicfreeandroid.data.db.entity.LyricCacheEntity
import com.zili.android.musicfreeandroid.data.db.entity.MediaCacheEntity
import com.zili.android.musicfreeandroid.data.db.entity.MusicItemEntity
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistEntity
import com.zili.android.musicfreeandroid.data.db.entity.PlaylistMusicCrossRef
import com.zili.android.musicfreeandroid.data.db.entity.PlayQueueEntity
import com.zili.android.musicfreeandroid.data.db.entity.StarredSheetEntity

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
    ],
    version = 7,
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
}

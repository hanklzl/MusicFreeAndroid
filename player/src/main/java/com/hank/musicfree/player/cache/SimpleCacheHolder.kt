package com.hank.musicfree.player.cache

import android.content.Context
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.hank.musicfree.logging.LogCategory
import com.hank.musicfree.logging.MfLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SimpleCache 单例 holder，支持 `current` 懒加载和 `resetForClear` 重置。
 *
 * 初始化失败时进入 disabled 状态（永久返回 null），调用方应 fallback 到非缓存路径。
 *
 * 缓存目录优先 `getExternalFilesDir(null)/media-cache`，外部不可用时回退到内部 cacheDir。
 * 上限 512MB（LRU evictor），数据库走 `StandaloneDatabaseProvider`，不污染 Room。
 */
@Singleton
@AndroidXOptIn(markerClass = [UnstableApi::class])
class SimpleCacheHolder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val ref = AtomicReference<SimpleCache?>(null)
    private val initFailed = AtomicBoolean(false)

    val current: SimpleCache?
        get() = ref.get() ?: synchronized(this) {
            if (initFailed.get()) return null
            ref.get() ?: tryCreate()?.also { ref.set(it) }
        }

    fun resetForClear(): SimpleCache? = synchronized(this) {
        ref.get()?.release()
        ref.set(null)
        cacheDir().deleteRecursively()
        tryCreate()?.also { ref.set(it) }
    }

    fun cacheDirPath(): String = cacheDir().absolutePath
    fun usedBytes(): Long = current?.cacheSpace ?: 0L

    private fun tryCreate(): SimpleCache? = runCatching {
        SimpleCache(
            cacheDir().apply { mkdirs() },
            LeastRecentlyUsedCacheEvictor(DEFAULT_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }.onFailure { error ->
        initFailed.set(true)
        MfLog.error(
            category = LogCategory.PLAYER,
            event = "media_cache_init_failed",
            throwable = error,
            fields = mapOf("cacheDirPath" to cacheDirPath()),
        )
    }.getOrNull()

    private fun cacheDir(): File =
        context.getExternalFilesDir(null)?.resolve("media-cache")
            ?: context.cacheDir.resolve("media-cache")

    companion object {
        const val DEFAULT_BYTES = 512L * 1024 * 1024
    }
}

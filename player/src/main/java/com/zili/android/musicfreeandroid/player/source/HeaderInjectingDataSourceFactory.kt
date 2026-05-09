package com.zili.android.musicfreeandroid.player.source

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataSource.Factory that injects per-track HTTP headers + user agent into outgoing requests.
 *
 * Lookup key is the dataSpec.uri (the resolved playback url). PlayerController writes
 * (url, headers, ua) into [registry] just before setMediaItem; here we read it during
 * dataSpec resolution and merge into [androidx.media3.datasource.DataSpec.httpRequestHeaders].
 *
 * For non-http(s) schemes (e.g. file://) we pass the dataSpec through unchanged.
 */
@Singleton
class HeaderInjectingDataSourceFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: TrackHeaderRegistry,
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        val httpFactory = DefaultHttpDataSource.Factory()
        val baseFactory = DefaultDataSource.Factory(context, httpFactory)
        return ResolvingDataSource.Factory(baseFactory) { dataSpec ->
            val scheme = dataSpec.uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return@Factory dataSpec
            val key = dataSpec.uri.toString()
            val entry = registry.get(key) ?: return@Factory dataSpec
            val merged = buildMap {
                putAll(dataSpec.httpRequestHeaders)
                putAll(entry.headers)
                entry.userAgent
                    ?.takeIf { !this.containsKey("User-Agent") && !this.containsKey("user-agent") }
                    ?.let { put("User-Agent", it) }
            }
            dataSpec.buildUpon().setHttpRequestHeaders(merged).build()
        }.createDataSource()
    }
}

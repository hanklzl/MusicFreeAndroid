package com.zili.android.musicfreeandroid.feature.playerui.lyrics

import com.zili.android.musicfreeandroid.core.lyric.LyricParser
import com.zili.android.musicfreeandroid.core.model.LyricSourceInfo
import com.zili.android.musicfreeandroid.core.model.MusicItem
import com.zili.android.musicfreeandroid.core.model.RawLyricPayload
import com.zili.android.musicfreeandroid.data.mapper.LyricCache
import com.zili.android.musicfreeandroid.data.repository.LyricRepository
import com.zili.android.musicfreeandroid.data.datastore.AppPreferences
import com.zili.android.musicfreeandroid.logging.LogCategory
import com.zili.android.musicfreeandroid.logging.LogFields
import com.zili.android.musicfreeandroid.logging.MfLog
import com.zili.android.musicfreeandroid.plugin.api.LyricResult
import com.zili.android.musicfreeandroid.plugin.api.musicItems
import com.zili.android.musicfreeandroid.plugin.manager.PluginManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlin.math.max
import javax.inject.Inject

class PlayerLyricLoader @Inject constructor(
    private val lyricRepository: LyricRepository,
    private val pluginManager: PluginManager,
    private val appPreferences: AppPreferences,
) {

    fun observeLyrics(music: MusicItem?): Flow<LyricLoadState> = flow {
        if (music == null) {
            emit(LyricLoadState.NoTrack)
            return@flow
        }

        MfLog.detail(
            category = LogCategory.LYRICS,
            event = "lyric_load_start",
            fields = lyricFields(music, operation = "load"),
        )
        var firstEmission = true
        lyricRepository.observeCache(music).collect { cache ->
            if (firstEmission) {
                emit(LyricLoadState.Loading(music))
            }
            emit(resolveLyrics(music, cache))
            firstEmission = false
        }
    }.catch { e ->
        if (e is CancellationException || (e is IllegalStateException && e.message?.contains("Flow exception transparency is violated") == true)) {
            throw e
        }
        val fallbackMusic = music ?: return@catch
        emit(LyricLoadState.Error(fallbackMusic, e.message ?: "歌词加载失败"))
    }

    private suspend fun resolveLyrics(music: MusicItem, cache: LyricCache?): LyricLoadState {
        val userOffsetMs = cache?.userOffsetMs ?: 0L

        localDocument(music, cache)?.let {
            return LyricLoadState.Ready(music, it, userOffsetMs)
        }

        val target = cache?.associatedMusic ?: music

        cachedDocument(music, target, cache)?.let {
            MfLog.detail(
                category = LogCategory.LYRICS,
                event = "lyric_cache_hit",
                fields = lyricFields(music, operation = "load") + mapOf(
                    "result" to LogFields.Result.SUCCESS,
                ),
            )
            return LyricLoadState.Ready(music, it, userOffsetMs)
        }

        val sourceForMusic = cache?.associatedMusic?.let {
            LyricSourceInfo.Associated(it.platform, it.title, it.id)
        } ?: LyricSourceInfo.Plugin(music.platform)

        fetchFromPlugin(target, sourceForMusic)?.let { payload ->
            lyricRepository.saveRemoteLyric(music, sourceForMusic, payload)
            return LyricLoadState.Ready(music, parse(music, payload, sourceForMusic), userOffsetMs)
        }

        val autoSearchEnabled = appPreferences.lyricAutoSearchEnabled.first()
        if (autoSearchEnabled) {
            autoSearch(music)
                ?.let { (payload, source) ->
                    lyricRepository.saveRemoteLyric(music, source, payload)
                    return LyricLoadState.Ready(music, parse(music, payload, source), userOffsetMs)
                }
        }

        MfLog.detail(
            category = LogCategory.LYRICS,
            event = "lyric_no_lyric",
            fields = lyricFields(music, operation = "load") + mapOf(
                "result" to LogFields.Result.FAILURE,
                "reason" to LogFields.Reason.NOT_FOUND,
            ),
        )
        return LyricLoadState.NoLyric(music)
    }

    suspend fun searchCandidates(
        music: MusicItem,
        query: String = music.title,
        includeCurrentPlatform: Boolean = true,
    ): List<LyricSearchGroup> =
        pluginManager.getLyricSearchablePlugins().first()
            .filter { includeCurrentPlatform || it.info.platform != music.platform }
            .map { plugin ->
                try {
                    val candidates = plugin.search(query = query, page = 1, type = "lyric")
                        .musicItems()
                        .take(2)
                    LyricSearchGroup(plugin.info, candidates)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    MfLog.error(
                        category = LogCategory.LYRICS,
                        event = "lyric_search_plugin_failed",
                        throwable = error,
                        fields = lyricFields(music, operation = "search_candidates") + mapOf(
                            "sourcePlatform" to plugin.info.platform,
                            "result" to LogFields.Result.FAILURE,
                            "reason" to LogFields.Reason.UNKNOWN,
                        ),
                    )
                    LyricSearchGroup(
                        plugin.info,
                        emptyList(),
                        "搜索歌词失败: ${error.message ?: "未知错误"}",
                    )
                }
            }

    suspend fun associateLyric(music: MusicItem, target: MusicItem) {
        lyricRepository.associateLyric(music, target)
    }

    suspend fun clearAssociatedLyric(music: MusicItem) {
        lyricRepository.clearAssociatedLyric(music)
    }

    suspend fun importLocalLyric(music: MusicItem, rawText: String, kind: com.zili.android.musicfreeandroid.data.repository.LocalLyricKind) {
        lyricRepository.importLocalLyric(music, rawText, kind)
    }

    suspend fun deleteLocalLyric(music: MusicItem) {
        lyricRepository.deleteLocalLyric(music)
    }

    suspend fun setLyricOffset(music: MusicItem, offsetMs: Long) {
        lyricRepository.setLyricOffset(music, offsetMs)
    }

    private fun localDocument(music: MusicItem, cache: LyricCache?): com.zili.android.musicfreeandroid.core.model.LyricDocument? {
        if (cache == null) return null
        val rawLrc = cache.localRawLrc
        return when {
            !rawLrc.isNullOrBlank() -> parse(
                music,
                RawLyricPayload(
                    rawLrc = rawLrc,
                    translation = cache.localTranslation,
                ),
                LyricSourceInfo.LocalRaw,
            )
            !cache.localTranslation.isNullOrBlank() -> parse(
                music,
                RawLyricPayload(rawLrcTxt = cache.localTranslation),
                LyricSourceInfo.LocalTranslation,
            )
            else -> null
        }?.takeIf { it.lines.isNotEmpty() }
    }

    private fun cachedDocument(music: MusicItem, target: MusicItem, cache: LyricCache?): com.zili.android.musicfreeandroid.core.model.LyricDocument? {
        val payload = cache?.remotePayload ?: return null
        if (!payload.isUsableRemoteLyric()) return null

        val sourceMatches = when {
            cache.associatedMusic == null -> target == music && isCompatibleHistoryOrCurrentSource(target, cache)
            else -> cache.remoteSourcePlatform == target.platform &&
                cache.remoteSourceMusicId == target.id
        }

        return if (sourceMatches) {
            parse(music, payload, LyricSourceInfo.Cache).takeIf { it.lines.isNotEmpty() }
        } else {
            null
        }
    }

    private fun isCompatibleHistoryOrCurrentSource(target: MusicItem, cache: LyricCache): Boolean {
        val sourcePlatform = cache.remoteSourcePlatform
        val sourceMusicId = cache.remoteSourceMusicId

        return (sourcePlatform == null && sourceMusicId == null) ||
            (sourcePlatform == target.platform && sourceMusicId == null) ||
            (sourcePlatform == target.platform && sourceMusicId == target.id)
    }

    private suspend fun fetchFromPlugin(
        target: MusicItem,
        source: LyricSourceInfo,
    ): RawLyricPayload? {
        val plugin = pluginManager.getPlugin(target.platform) ?: return null
        return try {
            plugin.getLyric(target)
                ?.toPayload()
                ?.takeIf { it.isUsableRemoteLyric() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            MfLog.error(
                category = LogCategory.LYRICS,
                event = "lyric_plugin_fetch_failed",
                throwable = error,
                fields = lyricFields(target, operation = "plugin_fetch") + mapOf(
                    "source" to source::class.simpleName.orEmpty(),
                    "result" to LogFields.Result.FAILURE,
                    "reason" to LogFields.Reason.UNKNOWN,
                ),
            )
            null
        }
    }

    private suspend fun autoSearch(music: MusicItem): Pair<RawLyricPayload, LyricSourceInfo>? {
        val candidates = searchCandidates(music, includeCurrentPlatform = false)
            .flatMap { it.items.take(2).map { candidate -> candidate to it } }
            .mapNotNull { (candidate, group) ->
                val exactMatch =
                    candidate.title == music.title && candidate.artist == music.artist
                val distance = if (exactMatch) {
                    0.0
                } else {
                    normalizedDistance(
                        normalizeText(candidate.title),
                        normalizeText(candidate.artist),
                        normalizeText(music.title),
                        normalizeText(music.artist),
                    )
                }
                SearchCandidate(candidate, group.plugin.platform, exactMatch, distance)
            }
            .sortedWith(
                compareByDescending<SearchCandidate> { it.exactMatch }
                    .thenBy { it.distance },
            )

        for (candidate in candidates) {
            val plugin = pluginManager.getPlugin(candidate.platform) ?: continue
            val payload = try {
                plugin.getLyric(candidate.music)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                MfLog.error(
                    category = LogCategory.LYRICS,
                    event = "lyric_auto_search_fetch_failed",
                    throwable = error,
                    fields = lyricFields(candidate.music, operation = "auto_search_fetch") + mapOf(
                        "sourcePlatform" to candidate.platform,
                        "result" to LogFields.Result.FAILURE,
                        "reason" to LogFields.Reason.UNKNOWN,
                    ),
                )
                null
            }?.toPayload()
                ?.takeIf { it.isUsableRemoteLyric() }

            if (payload != null) {
                return payload to
                    LyricSourceInfo.AutoSearch(
                        platform = candidate.platform,
                        title = candidate.music.title,
                        id = candidate.music.id,
                    )
            }
        }

        return null
    }

    private data class SearchCandidate(
        val music: MusicItem,
        val platform: String,
        val exactMatch: Boolean,
        val distance: Double,
    )

    private fun normalizeText(text: String?): String = text
        ?.lowercase()
        ?.trim()
        ?.replace(Regex("\\s+"), "")
        .orEmpty()

    private fun normalizedDistance(candidateTitle: String, candidateArtist: String, targetTitle: String, targetArtist: String): Double {
        val target = "$targetTitle $targetArtist"
        val candidate = "$candidateTitle $candidateArtist"
        if (target.isBlank() && candidate.isBlank()) return 0.0

        return levenshtein(candidate, target).toDouble() / max(candidate.length, target.length).coerceAtLeast(1)
    }

    private fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        val matrix = Array(left.length + 1) { IntArray(right.length + 1) }

        for (i in 0..left.length) {
            matrix[i][0] = i
        }

        for (j in 0..right.length) {
            matrix[0][j] = j
        }

        for (i in 1..left.length) {
            for (j in 1..right.length) {
                val substitution = if (left[i - 1] == right[j - 1]) 0 else 1
                matrix[i][j] = minOf(
                    matrix[i - 1][j] + 1,
                    matrix[i][j - 1] + 1,
                    matrix[i - 1][j - 1] + substitution,
                )
            }
        }

        return matrix[left.length][right.length]
    }

    private fun parse(music: MusicItem, payload: RawLyricPayload, source: LyricSourceInfo) =
        LyricParser.parse(
            musicId = music.id,
            musicPlatform = music.platform,
            payload = payload,
            source = source,
        )

    private fun lyricFields(music: MusicItem, operation: String): Map<String, Any?> = mapOf(
        "screen" to "player",
        "operation" to operation,
        "itemId" to music.id,
        "itemName" to music.title,
        "platform" to music.platform,
    )
}

private fun LyricResult.toPayload(): RawLyricPayload = RawLyricPayload(
    rawLrc = rawLrc,
    rawLrcTxt = rawLrcTxt,
    translation = translation,
)

private fun RawLyricPayload.isUsableRemoteLyric(): Boolean = !rawLrc.isNullOrBlank() ||
    !rawLrcTxt.isNullOrBlank()

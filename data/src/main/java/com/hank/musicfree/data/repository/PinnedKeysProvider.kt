package com.hank.musicfree.data.repository

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Combined pinned-key set in the form "${platform}:${id}".
 * SimpleCacheHolder expands each entry to the 5 quality-suffixed cacheKey variants.
 *
 * TODO(spec-deviation): [starred] currently emits **sheet** ids
 * (`StarredSheetRepository.observeStarredKeys`), not track ids. Until项目里出现"按歌曲收藏"的
 * 数据源（或者把 starred sheet 展开成内部 track ids），这条 pinning 通道实际上是 no-op，
 * 真正起作用的是 [queue.observeRecentKeys(50)]. 详见 spec §4.6.2 + 实施记录 commit 82ea21b5.
 */
@Singleton
class PinnedKeysProvider @Inject constructor(
    private val starred: StarredSheetRepository,
    private val queue: PlayQueueRepository,
) {
    fun observe(): Flow<Set<String>> = combine(
        starred.observeStarredKeys(),
        queue.observeRecentKeys(50),
    ) { a, b -> a + b }
}

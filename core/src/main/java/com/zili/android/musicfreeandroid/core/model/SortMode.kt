package com.zili.android.musicfreeandroid.core.model

/**
 * Sort modes for user-created playlists. Aliased to RN's `SortType` (see
 * `../MusicFree/src/constants/commonConst.ts`):
 * - `Manual` ↔ RN `None` (renamed for readability — matches the "手动排序" UI label)
 * - `Title / Artist / Album / Newest / Oldest` ↔ RN same names (case-aligned)
 */
enum class SortMode {
    Manual, Title, Artist, Album, Newest, Oldest;

    companion object { val DEFAULT: SortMode = Manual }
}

package com.zili.android.musicfreeandroid.core.model

/**
 * Sort modes for user-created playlists. Aliased to RN's `SortType` (see
 * `../MusicFree/src/constants/commonConst.ts`):
 * - `Manual` ↔ RN `None` (renamed for readability — semantically: no active sort, preserve insertion order)
 * - `Title / Artist / Album / Newest / Oldest` ↔ RN same names (case-aligned)
 */
enum class SortMode {
    Manual, Title, Artist, Album, Newest, Oldest;

    companion object { val DEFAULT: SortMode = Manual }
}

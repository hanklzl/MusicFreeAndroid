# J-PLUGIN-SEARCH-PLAY RN Mapping

## RN Sources
- Settings plugin entry: `/Users/zili/code/android/MusicFree/src/pages/setting/index.tsx`
- Plugin settings stack and subscription install flow: `/Users/zili/code/android/MusicFree/src/pages/setting/settingTypes/pluginSetting/index.tsx`
- Subscription URL management UI: `/Users/zili/code/android/MusicFree/src/pages/setting/settingTypes/pluginSetting/views/pluginSubscribe.tsx`
- Search page entry and result surfaces: `/Users/zili/code/android/MusicFree/src/pages/searchPage/index.tsx`
- Search query/result behavior: `/Users/zili/code/android/MusicFree/src/pages/searchPage/hooks/useSearch.ts`
- Player page and transport controls: `/Users/zili/code/android/MusicFree/src/pages/musicDetail/index.tsx`
- Player transport implementation: `/Users/zili/code/android/MusicFree/src/pages/musicDetail/components/bottom/playControl.tsx`
- Playback engine handoff and pause/resume: `/Users/zili/code/android/MusicFree/src/core/trackPlayer/index.ts`

## Android Sources
- Settings entry and default subscription install UI: `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt`
- Settings install orchestration: `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModel.kt`
- Search page entry and result list: `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchScreen.kt`
- Search execution and media-source resolution: `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt`
- Player page and transport controls: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`
- Player pause/resume orchestration: `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt`

## UI Structure Mapping
- settings install entry -> Android settings install controls
- search query field / result list -> Android search controls and rows
- player transport controls -> Android player controls

## Business Flow Mapping
- install subscription: RN `/Users/zili/code/android/MusicFree/src/pages/setting/settingTypes/pluginSetting/views/pluginSubscribe.tsx` persists subscription URLs, while Android `SettingsViewModel.kt` exposes `installDefaultSubscription()` for the supported import path
- load searchable plugins: RN `/Users/zili/code/android/MusicFree/src/pages/searchPage/hooks/useSearch.ts` reads plugin-backed search capability for the search flow, while Android `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt` derives searchable plugins from `PluginManager.plugins`
- run search: RN `/Users/zili/code/android/MusicFree/src/pages/searchPage/index.tsx` and `/Users/zili/code/android/MusicFree/src/pages/searchPage/hooks/useSearch.ts` drive query state and execute search, while Android `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchScreen.kt` submits the controlled query into `SearchViewModel.search()`
- resolve media source: RN `/Users/zili/code/android/MusicFree/src/core/trackPlayer/index.ts` resolves playback sources through plugin manager methods, while Android `feature/search/src/main/java/com/zili/android/musicfreeandroid/feature/search/SearchViewModel.kt` resolves `getMediaSource()` with `元力WY` fallback handling
- play and pause: RN `/Users/zili/code/android/MusicFree/src/pages/musicDetail/components/bottom/playControl.tsx` toggles `TrackPlayer.play()` / `TrackPlayer.pause()` against `/Users/zili/code/android/MusicFree/src/core/trackPlayer/index.ts`, while Android `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt` and `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt` toggle `PlayerController`

## Data / Parameter Alignment
- subscription URL path: RN stores `plugin.subscribeUrl` in app config, Android currently hardcodes the supported default subscription URL in `SettingsViewModel.kt`
- search query semantics: both flows submit a user-entered text query against music-search-capable plugins and require non-empty input
- resolved media source handoff to player: both flows convert a selected search result into a resolved media source before handing the item/queue to the player layer

## Open Gaps
- controlled-live fixture not yet written
- fidelity proof intentionally deferred

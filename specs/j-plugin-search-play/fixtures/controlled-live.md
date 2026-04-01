# Controlled Live Baseline

## Subscription Source
- `https://13413.kstore.vip/yuanli/yuanli.json`
- Journey path uses `SettingsViewModel.installDefaultSubscription()`

## Query Set
- `in the end` — from `plugin/src/androidTest/java/com/zili/android/musicfreeandroid/plugin/manager/PluginRuntimeIntegrationTest.kt`
- `In The End Linkin Park` — from `feature/search/src/test/java/com/zili/android/musicfreeandroid/feature/search/MusicMatchTest.kt`
- `linkin park` — narrow fallback query derived from the same title/artist pair, reserved for reruns when ranking shifts

## Acceptance Rules
- Search result must be non-empty
- Ranking is not asserted
- Success is defined as: can open player, can observe pause/resume

## Known Volatility
- Plugin list returned by subscription may change
- Search result order may change
- Verification must rely on non-empty and playable, not exact first-row identity

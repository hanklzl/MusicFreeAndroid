# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MusicFreeAndroid is a native Android rewrite of [MusicFree](https://github.com/maotoumao/MusicFree) (a React Native music player). It replicates the plugin-based architecture—where JS plugins provide music sources—using Kotlin, Jetpack Compose, and QuickJS.

The original RN app at `/Users/zili/code/android/MusicFree` is a live reference for data models, plugin system behavior, theme colors, and UI constants.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew :app:build             # Build app module only
./gradlew :<module>:assembleDebug  # Build individual module
./gradlew test                   # Run unit tests
./gradlew :app:testDebugUnitTest # Run unit tests for app module
./gradlew connectedAndroidTest   # Run instrumented tests (requires device/emulator)
./gradlew lint                   # Run lint checks
```

- Min SDK: 29 (Android 10), Target SDK: 36
- Java compatibility: VERSION_11
- Gradle wrapper: 9.5.0-milestone-5, AGP: 9.1.0, Kotlin: 2.2.10

## Planned Module Architecture

The project is being migrated from a single `:app` module to a multi-module structure. Module dependency flow is strictly unidirectional:

```
:app → :feature:* → :data, :player, :plugin → :core
```

| Module | Responsibility |
|---|---|
| `:core` | Theme, navigation routes, base models, utilities |
| `:data` | Room DB, DataStore, Repository layer |
| `:player` | Media3/ExoPlayer, MediaSessionService, PlayerController |
| `:plugin` | QuickJS engine, JS bridge, plugin management |
| `:feature:home` | Home screen, local music, playlists |
| `:feature:player-ui` | Full-screen player, mini player |
| `:feature:search` | Plugin-driven search |
| `:feature:settings` | Settings, plugin management UI |
| `:app` | Application entry, NavHost, navigation orchestration |

## Tech Stack

- **UI:** Jetpack Compose + Material3 (BOM 2026.02.01)
- **Architecture:** MVVM, multi-module, unidirectional data flow
- **DI:** Hilt
- **Playback:** Media3 (ExoPlayer) + `MediaSessionService`
- **Plugin Engine:** QuickJS (`quickjs-android`) for JS execution isolation
- **Database:** Room
- **Preferences:** DataStore
- **Async:** Kotlin Coroutines + Flow
- **Navigation:** Compose Navigation with `@Serializable` route objects
- **Image Loading:** Coil
- **HTTP:** OkHttp

## Key Design Decisions

### Theme System
Extended Material3 with 17+ semantic colors in `MusicFreeColors` data class, accessed via `CompositionLocal`. Light/dark mode support. Reference original theme at `/Users/zili/code/android/MusicFree/src/core/theme.ts`.

### Responsive Layout
Custom `rpx(value)` utility mirrors the RN rpx system: `(value / 750f) * minWindowEdge`. Used for all spacing and sizing to match the original design.

### Plugin System
JS plugins run in QuickJS sandbox. `JsBridge` handles Kotlin ↔ JS calls. A `require()` shim provides built-in libraries (cheerio, crypto-js, dayjs, axios). All plugins expose a standard `PluginApi` interface: `search()`, `getMediaSource()`, `getLyric()`.

### Player Architecture
`MediaSessionService` wraps ExoPlayer. `PlayerController` is a `MediaController` wrapper exposed via Hilt. UI observes `StateFlow<PlayerState>`. `PlayQueue` manages queue with shuffle support.

### Data Layer
Room entities map to domain models via separate mapper functions—never expose Room entities directly to upper layers. Repository functions return `Flow<T>`. DataStore handles scalar preferences (repeat mode, quality setting, theme).

## Reference Files in Original RN Project

When implementing features, consult these paths in `/Users/zili/code/android/MusicFree/`:
- Data models: `src/types/` (music.d.ts, plugin.d.ts, artist.d.ts)
- Plugin manager: `src/core/pluginManager/`
- Player logic: `src/core/trackPlayer/`
- Theme tokens: `src/core/theme.ts`
- UI constants: `src/constants/uiConst.ts`
- Responsive pixel: `src/utils/rpx.ts`

## Key Documentation

- Design spec: `docs/superpowers/specs/2026-03-19-musicfree-android-native-rewrite-design.md`
- Milestone 1 plan: `docs/superpowers/plans/2026-03-19-milestone1-project-scaffolding.md`

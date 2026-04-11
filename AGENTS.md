# AGENTS.md

Project guidance for AI coding assistants working with this repository.

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
- Gradle wrapper: 9.5.0-milestone-5, AGP: 9.2.0-alpha04, Kotlin: 2.2.10

## Module Architecture

Multi-module structure, fully implemented. Dependency flow is strictly unidirectional:

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
JS plugins run in QuickJS sandbox. `JsBridge` handles Kotlin ↔ JS calls. A `require()` shim provides built-in libraries (cheerio, crypto-js, dayjs, axios, qs, he, big-integer). All plugins expose a standard `PluginApi` interface with 14 methods including `search()`, `getMediaSource()`, `getLyric()`, `getMusicSheetInfo()`, `recommendSheets()`, etc.

### Player Architecture
`MediaSessionService` wraps ExoPlayer. `PlayerController` is a `MediaController` wrapper exposed via Hilt. UI observes `StateFlow<PlayerState>`. `PlayQueue` manages queue with shuffle support. Search playback includes fallback mechanism (primary plugin fails → fallback to alternate source).

### Data Layer
Room entities map to domain models via separate mapper functions—never expose Room entities directly to upper layers. Repository functions return `Flow<T>`. DataStore handles scalar preferences (repeat mode, quality setting, theme). `MusicItem` supports extension fields (`extra` map) for plugin-specific metadata passthrough.

## Project Status

### Coverage (as of 2026-03-21)
- **Pages:** 16/19 — missing: `fileSelector`, `downloading`, `setCustomTheme`
- **PluginApi:** 14/14 (complete)
- **Panels/Dialogs:** 5/23

### Current Backlog (by priority)
1. Missing pages — `fileSelector` is highest priority (unlocks subsystem)
2. Plugin-backed detail flow validation — `pluginSheetDetail`, `topListDetail`, `musicDetail` chains need end-to-end runtime verification
3. Home/Drawer/History UI fidelity — large gap vs original
4. `searchMusicList` expansion — currently playlist/history only, needs local music + generic sheet sources
5. End-to-end playback chain — subscribe → search → play needs more complete control/screenshot automation
6. `musicListEditor-lite` expansion — currently playlist-first MVP
7. Data model completeness — `IMusicItem` field gaps

## Reference Files in Original RN Project

When implementing features, consult these paths in `/Users/zili/code/android/MusicFree/`:
- Data models: `src/types/` (music.d.ts, plugin.d.ts, artist.d.ts)
- Plugin manager: `src/core/pluginManager/`
- Player logic: `src/core/trackPlayer/`
- Theme tokens: `src/core/theme.ts`
- UI constants: `src/constants/uiConst.ts`
- Responsive pixel: `src/utils/rpx.ts`

### Iteration Workflow
1. 阅读原版 MusicFree RN 代码，确定实现方案，拆分为可执行、可验证的任务
2. UI 通过 `adb shell uiautomator` 获取 Layout 信息 + 截图对比，务求 100% 还原（含进退场动画）
3. 业务逻辑整理流程图，按流程图实现，关键类实现尽可能一致
4. 数据结构、数据库、请求参数做到类型和字段完全对齐
5. 每个功能做好单元测试和集成测试（Logcat 日志 + 截图 + uiautomator Layout 对比）
6. Review：代码正确性、健壮性、功能是否真实完成
7. 记录过程中犯的错误，项目最终结束完成后， 更新到项目记忆中

### Analysis Rules
- 不仅依赖截图对比，必须同时分析原版 RN 代码和当前 Android 代码
- 截图作为视觉锚点，不作为唯一真相来源
- 通过代码侧分析识别：缺失的可复用抽象、过时文档造成的假 backlog、隐藏在"缺页"背后的子系统前置依赖

### Acceptance Gates
- 编译、单元测试、集成测试、模拟器真实验证、最终 review 必须集中执行
- 运行时/模拟器验证优先于代码审查的乐观判断
- 功能可以通过编译、单测、spec review、code review，仍可能在运行时验收失败（iteration-13 教训）

### Documentation Maintenance
- 定期对项目代码进行 Review，更新或删除过时的技术文档和进度文档，避免影响上下文

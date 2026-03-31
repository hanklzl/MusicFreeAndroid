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
- Gradle wrapper: 9.5.0-milestone-5, AGP: 9.2.0-alpha04, Kotlin: 2.2.10

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

## 迭代步骤
- 阅读原版FreeMusic代码，确定原版的实现方案，包括UI细节和业务逻辑，颗粒度尽可能地细，能拆分成多个可执行、可验证的任务
- UI细节通过 adb shell uiautomator获取Layout信息， 叠加截图方式进行对比， 务求还原度做到100%， 如果涉及页面的开发， 页面的进退场动画也要考虑在内
- 业务逻辑，要整理好流程图，按流程图实现，关键类实现要尽可能地一致， 一些由于不同语言特性导致无法对齐，要考虑如何等价实现。
- 对于数据结构， 数据库， 请求参数等， 要做到类型，字段完全对齐
- 对于过程中犯的错误， 要及时记录， 并每次任务开始时， 都进行读取， 防止再犯
- 对于进度记录，要及时和详细
- 每个功能都要做好单元测试和集成测试， 尤其是集成测试，通过添加Logcat日志 + 截图 + uiautomator Layout信息跟原版进行详细对比
- 做好Review， review内容包括代码正确性、健壮性、功能是否真实完成

## 定期更新项目进度
- 针对文档记录，对项目代码进行Review，对于落后的技术文档&进度文档进行必要的更新、删除， 避免影响上下文

## Active Technologies
- Kotlin 2.2.10（Android 主实现） + Jetpack Compose + Material3, Hilt, Coroutines/Flow, QuickJS runtime, OkHttp, Room/DataStore (001-plugin-parity-search)
- 应用私有文件目录（插件脚本文件）、DataStore（偏好与配置）、现有 Room 数据 (001-plugin-parity-search)

## Recent Changes
- 001-plugin-parity-search: Added Kotlin 2.2.10（Android 主实现） + Jetpack Compose + Material3, Hilt, Coroutines/Flow, QuickJS runtime, OkHttp, Room/DataStore

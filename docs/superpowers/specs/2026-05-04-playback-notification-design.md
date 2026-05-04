# 播放通知栏设计

> 文档状态：当前规范
> 适用范围：歌曲播放通知栏展示、系统媒体控制、Android 13+ 通知权限。
> 直接执行：是（作为实现计划输入）
> 最后校验：2026-05-04

## 背景

当前 Android 侧已经具备 `MediaSessionService` 播放架构：`PlaybackService` 持有 ExoPlayer 与 `MediaSession`，`PlayerController` 通过 `MediaController` 驱动播放，并用内存 `PlayQueue` 管理队列。早期总体设计已经要求“通知栏由 Media3 自动处理”，但当前实现尚未补齐稳定通知 provider、点击回应用、通知权限与通知栏队列控制。

RN 参考实现位于 `../MusicFree`：

- `../MusicFree/src/entry/bootstrap/bootstrap.ts`：`react-native-track-player` 初始化、通知能力配置、`ContinuePlayback` 行为。
- `../MusicFree/src/service/index.ts`：通知/耳机远程事件处理，包含 play、pause、previous、next、stop、seek。
- `../MusicFree/src/core/trackPlayer/index.ts`：播放队列、当前歌曲、封面 fallback、metadata 更新。

本设计只覆盖本轮确认的 B 范围，并额外纳入 Android 13+ 通知权限。

## 目标

1. 播放歌曲时显示系统媒体通知，展示标题、歌手、封面。
2. 通知支持播放/暂停、上一首、下一首、进度条 seek。
3. 点击通知回到 `MainActivity`。
4. 播放中移除最近任务时继续播放；无媒体或未播放时停止服务。
5. Android 13+ 可请求并展示通知权限状态，避免播放正常但通知不可见。
6. 保持现有模块依赖方向，不让 `:player` 反向依赖 `:app` 或 feature。

## 非目标

1. 不实现 RN 的“通知栏显示关闭按钮”设置项。
2. 不新增音频打断策略设置，例如 `notInterrupt` 或临时 duck 配置。
3. 不做自定义通知 UI，不绕过 Media3 默认媒体通知。
4. 不实现 service 冷启动后的队列持久恢复。
5. 不改动插件音源解析策略；feature 层仍负责把播放项解析为可播放 `url`。

## 推荐方案

采用 Media3 默认通知机制，并在现有 `PlaybackService : MediaSessionService` 内完成必要定制：

- 使用 `DefaultMediaNotificationProvider` 设置稳定 notification channel、notification id、small icon。
- 为 `MediaSession` 设置 `sessionActivity`，让通知点击回到应用入口。
- 通过 `MediaSession.setMediaButtonPreferences(...)` 配置通知按钮。
- 播放/暂停和 seek 使用 Media3/ExoPlayer 原生命令。
- 上一首/下一首使用自定义 session command，回调到 player 层队列控制逻辑。

不选择完全自定义 `MediaNotification.Provider`，因为本轮目标是对齐 RN 的核心通知能力，而不是定制通知视觉。默认 provider 更能兼容锁屏、蓝牙、系统媒体控件和 Android 13+ 行为。

## 架构

### PlaybackService

`PlaybackService` 继续是唯一后台播放入口。它负责：

- 创建 ExoPlayer 并设置音乐用途的 `AudioAttributes`。
- 创建 `MediaSession`。
- 设置 Media3 默认通知 provider。
- 通过 `packageManager.getLaunchIntentForPackage(packageName)` 设置通知点击 pending intent，避免 `:player` 直接引用 `:app` 的 `MainActivity` 类型。
- 注册媒体按钮偏好。
- 处理通知栏上一首/下一首自定义 command。
- 保留播放中移除最近任务继续播放的行为。

`PlaybackService` 不直接引用 Compose、Navigation、ViewModel 或 feature 模块。

### PlayerController

`PlayerController` 继续管理应用播放队列：

- `playQueue(...)` 设置内存队列和当前播放项。
- `skipToPrevious()` / `skipToNext()` 仍是队列导航唯一入口。
- `setMediaItemAndPlay(...)` 仍负责把 `MusicItem` 转为 `MediaItem` 并播放。

由于当前 ExoPlayer 只设置当前曲目，通知栏的上一首/下一首不能直接依赖 `seekToPreviousMediaItem()` / `seekToNextMediaItem()`。实现计划应抽出一个 player 层协调接口或单例委托，让 service 的 custom command 调用与应用内按钮相同的队列逻辑。

该委托应由 `:player` 模块定义，具体实现可以由 Hilt 注入的 `PlayerController` 承担。若 service 收到 custom command 时尚无可用队列状态，命令应安全 no-op 并返回成功，不应创建崩溃路径。

### Media Metadata

`MusicItem.toMediaItem()` 是通知元数据入口：

- `title` 写入 `MediaMetadata.title`。
- `artist` 写入 `MediaMetadata.artist`。
- `album` 写入 `MediaMetadata.albumTitle`。
- `artwork` 写入 `MediaMetadata.artworkUri`。
- `artwork` 为空时回退到 `core/src/main/res/drawable/album_default.jpg` 的 `android.resource` URI。

封面加载失败不得影响播放。

## 数据流

### 播放启动

```text
Feature UI
  -> PlayerController.playQueue(...)
  -> PlayQueue 记录队列和当前项
  -> MusicItem.toMediaItem()
  -> MediaController.setMediaItem(...), prepare(), play()
  -> PlaybackService / MediaSession
  -> Media3 默认通知读取 metadata 并显示
```

### 通知控制

播放/暂停与 seek：

```text
System media notification
  -> MediaSession player command
  -> ExoPlayer
  -> Player.Listener
  -> PlayerController.emitState()
```

上一首/下一首：

```text
System media notification
  -> MediaSession custom command
  -> Player queue control delegate
  -> PlayerController.skipToPrevious()/skipToNext()
  -> 更新当前 MediaItem 与 PlayerState
```

## 权限

Android 13+ 需要 `POST_NOTIFICATIONS` 才能显示普通通知。Media playback 通知仍应按运行环境实际验证；本项目将权限纳入应用侧显式管理，避免验收时出现“播放正常但通知不可见”。

实现要求：

- Manifest 保留 `android.permission.POST_NOTIFICATIONS`。
- 新增 notification permission helper，API 33 以下视为已授权。
- `MainActivity` 在首次需要时请求通知权限，至少保证用户有授权入口。
- `PermissionsScreen` 新增“通知权限”条目，展示授权状态并可请求授权。
- 权限被拒绝时播放不失败；应用内 mini player/full player 仍正常更新。

## 错误处理

- 无通知权限：继续播放，权限管理页显示未授权，用户可手动请求。
- 封面为空或加载失败：使用默认专辑图；仍显示标题和歌手。
- 队列边界：上一首/下一首没有目标曲目时保持当前曲目，不抛异常。
- custom command 不可用：至少保留播放/暂停和 seek；测试覆盖 command 注册，避免按钮显示但不可用。
- service 被系统重建：不承诺恢复旧队列；保持现有播放架构行为。

## 测试

### 单元测试

- `MusicItem.toMediaItem()` 覆盖标题、歌手、专辑、封面 URI。
- `MusicItem.toMediaItem()` 覆盖空封面回退到默认专辑图。
- notification permission helper 覆盖 API 33 前后权限名与授权判定。
- 媒体按钮 command 常量或构建 helper 覆盖上一首/下一首自定义 action。

### Instrumentation

- `PlaybackServiceTest` 验证 service 可连接。
- 验证 `MediaSession` 有 `sessionActivity`。
- 验证 metadata 能反映当前曲目。
- 验证 play/pause/seek 仍可用。
- 验证通知栏上一首/下一首 command 能驱动 `PlayerController` 队列变化。

### 运行态验收

集中执行：

```bash
./gradlew assembleDebug
./gradlew :player:testDebugUnitTest
./gradlew :player:connectedDebugAndroidTest
```

设备/模拟器验收：

1. 授权通知权限。
2. 播放一首带封面的歌曲。
3. 下拉通知栏，确认标题、歌手、封面可见。
4. 点击播放/暂停，确认应用内播放状态同步。
5. 点击上一首/下一首，确认曲目切换与 mini player/full player 同步。
6. 拖动系统媒体进度条或使用 seek 控件，确认播放进度变化。
7. 点击通知，确认回到应用。
8. 播放中移除最近任务，确认播放继续；暂停且无媒体时服务可停止。

## 实现边界

本设计要求后续实现使用 git worktree 迭代，默认路径为 `.worktrees/feat/playback-notification`。实现计划应保持文件引用相对路径，不写入本机绝对路径。

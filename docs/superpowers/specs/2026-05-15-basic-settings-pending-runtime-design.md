# 基础设置剩余待接入项运行态设计

> 文档状态：当前规范  
> 适用范围：基础设置页当前仍显示 `待接入` 或不可点击的条目。  
> 直接执行：是（本轮 spec + plan + implement 输入）  
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)  
> 参考来源：`../../../MusicFree/src/pages/setting/settingTypes/basicSetting.tsx`、`../../../MusicFree/src/components/panels/types/associateLrc.tsx`、`../../../MusicFree/src/components/panels/types/musicItemOptions.tsx`、当前 Android `:feature:settings`、`:feature:player-ui`、`:player`、`:data`、`:logging` 实现。  
> 最后校验：2026-05-15

## 背景

此前 `2026-05-10-settings-runtime-integration-design.md` 和 `2026-05-13-settings-plugin-player-cache-design.md` 已经把多数基础设置从占位推进到 DataStore 与运行态消费。当前 Basic 页仍有以下待接入面：

- 常规：关联歌词方式、通知栏显示关闭按钮。
- 歌词：开启桌面歌词、桌面歌词位置/样式。
- 开发选项：记录错误日志、记录详细日志、调试面板、查看错误日志。

本轮目标是让当前基础设置页不再出现 `待接入`，并且每个开放项都有真实持久化和可观察运行态，不用“可点击但无效果”的假接入。

## 目标

1. `basic.associateLyricType` 接入 DataStore 与播放器歌词操作：默认搜索歌词，可切换为输入歌曲 ID。
2. `basic.showExitOnNotification` 接入 DataStore 与 MediaSession notification custom command：开启后通知栏显示关闭按钮，点击后停止播放并清空队列。
3. `lyric.showStatusBarLyric` 及位置/样式设置接入 DataStore 与 Android overlay：有悬浮窗权限时显示当前播放歌词，无权限时引导到系统授权。
4. `debug.errorLog`、`debug.traceLog`、`debug.devLog` 接入 DataStore 与日志门面：错误/详细日志可开关，调试面板作为当前进程内诊断开关保留。
5. `查看错误日志` 接入可读日志镜像：展示最近错误日志，支持复制；`清空日志` 同步清空 Logan 与可读镜像。
6. 补齐最小单元测试，完成 Debug 构建和 dev-harness 自查。

## 非目标

1. 不实现 RN 的全量桌面歌词拖拽交互；本轮只做可显示、可配置、可关闭的 Android overlay。
2. 不把桌面歌词做成独立前台服务；它跟随当前 app 进程和播放器 ViewModel 生命周期。
3. 不新增远程日志上传、在线日志页面或完整脱敏系统。
4. 不重写现有歌词搜索、歌词缓存、插件取词或 Media3 notification 架构。

## RN 对齐点

- RN `basic.associateLyricType` 值为 `search` / `input`，在音乐项面板中决定打开 `SearchLrc` 还是 `AssociateLrc`。
- RN `basic.showExitOnNotification` 语义是通知栏显示关闭按钮；Android 使用 MediaSession custom command 实现。
- RN 桌面歌词使用 `lyric.showStatusBarLyric`、`topPercent`、`leftPercent`、`align`、`widthPercent`、`fontSize`、`color`、`backgroundColor`。
- RN 开发选项包含 `debug.errorLog`、`debug.traceLog`、`debug.devLog`、查看错误日志和清空日志。

## Android 设计

### 偏好模型

在 `core` 增加轻量 enum：

- `LyricAssociationType.Search` / `Input`
- `DesktopLyricAlignment.Left` / `Center` / `Right`

在 `AppPreferences` 增加：

- `lyricAssociationType`
- `showExitOnNotification`
- `desktopLyricEnabled`
- `desktopLyricTopPercent`
- `desktopLyricLeftPercent`
- `desktopLyricWidthPercent`
- `desktopLyricFontSizeSp`
- `desktopLyricAlignment`
- `desktopLyricTextColor`
- `desktopLyricBackgroundColor`
- `debugErrorLogEnabled`
- `debugTraceLogEnabled`
- `debugDevLogEnabled`

默认值保持诊断优先：错误日志和详细日志默认开启；调试面板默认关闭。

### 设置页

`BasicSettingsContent` 移除当前剩余 `PendingValueRow`：

- 关联歌词方式改为单选 dialog。
- 通知栏显示关闭按钮改为 switch。
- 桌面歌词改为 switch，位置/样式拆成对齐、上下位置、左右位置、宽度、字体、文字颜色、背景颜色等真实可编辑行。
- 开发选项三项改为 switch，查看错误日志改为 action row。

如果用户在没有 overlay 权限时开启桌面歌词，`SettingsScreen` 调用现有 overlay settings intent 并显示 toast；偏好不提前写成已开启。

### 运行态

`PlayerViewModel` 暴露歌词关联方式和桌面歌词配置流。播放器更多操作中的“关联歌词”遵守设置：

- `Search`：打开现有搜索歌词 sheet。
- `Input`：打开输入歌曲 ID dialog，支持 `platform@id` 和 `{"platform":"...","id":"..."}`，然后用最小 `MusicItem` seed 建立歌词关联。

`PlayerScreen` 在 `desktopLyricEnabled=true` 且有 overlay 权限时创建 `DesktopLyricOverlayController`，根据当前歌词行更新悬浮窗文本；无权限时关闭偏好并提示。

`PlaybackService` 在连接时根据 `showExitOnNotification` 暴露关闭按钮。关闭命令走 `PlaybackNotificationCommandHandler` 回到 `PlayerController`，暂停、清空 MediaController media items、清空 app-owned queue 并刷新状态。

### 日志

`MfLog` 增加运行态开关，不改变调用方 API：

- `error` 受 `debugErrorLogEnabled` 控制。
- `detail` 和 `trace` 受 `debugTraceLogEnabled` 控制。
- `debugDevLogEnabled` 只作为当前进程诊断开关暴露，暂不新增 UI dev panel。

新增 `ReadableLogStore`，由 `LoganMfLogger` 在写错误日志时同步追加可读文本。`FeedbackLogExporter.clearLogs()` 同步清理该文件；设置页“查看错误日志”读取并以 dialog 展示。

## 验收

1. 基础设置页不再出现 `待接入`。
2. 关联歌词方式可持久化，并影响播放器更多操作中的关联歌词入口。
3. 通知栏关闭按钮开关开启后，MediaSession 暴露 stop/close command；点击后播放器停止并清空队列。
4. 桌面歌词开关和样式可持久化；有 overlay 权限时随当前歌词更新，无权限时引导授权。
5. 开发选项日志开关可持久化，错误日志可在设置页查看并复制。
6. 通过 `:data`、`:logging`、`:feature:settings`、`:feature:player-ui`、`:player` 相关单测，`bash scripts/dev-harness/check.sh`、`git diff --check` 与 `:app:assembleDebug`。

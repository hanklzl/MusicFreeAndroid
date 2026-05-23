# 用户操作时间线 设计

> 文档状态：当前规范
> 适用范围：Android 端 UI / 生命周期 / 业务事件埋点扩展，logan-viewer 端用户操作回放视图。
> 直接执行：是（作为实现计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 关联文档：[2026-05-05-logging-system-design.md](./2026-05-05-logging-system-design.md)
> 最后校验：2026-05-23

## 背景

当前反馈日志包（Logan + clogan）在 `tools/logan-viewer/` 以"原始日志行"形式展示，开发者拿到反馈包后需要靠 event 名、字段、时间戳手工拼出用户操作序列。Android 侧目前的事件全部打在 ViewModel/业务层（`player_play`、`search_start`、`plugin_operation_*` 等），缺少回答"用户摸了哪个按钮、进了哪个页面、按什么顺序操作、什么时候被系统切换或杀掉"所需的信号。

本设计把日志可视化从"原始日志查看器"升级为"用户操作时间线"：把已知的事件序列抽象成语义化的 ActionCard（"▶ 播放《七里香》qq · 命中缓存 · 2:30 · ✓"、"🔍 搜索 '周杰伦' 2/3 插件 → 打开歌单 周杰伦精选"、"🧩 Activity 销毁 trim_memory_complete · 存活 3:42"），同时补齐 Android 侧需要的 UI / 生命周期 / 组件状态埋点。

## 目标

1. 在 `tools/logan-viewer/` 现有 timeline 之上增加一层 ActionMatcher，规则命中产出结构化卡片，未命中事件原样降级为 raw row。
2. Android 侧补足通用 UI 交互埋点（screen / click / tab / dialog）、应用前后台切换、Activity 与关键 Store / 插件引擎 / MediaSession 的生命周期事件。
3. ActionCard 与 raw row 在**同一条时间线**按时间戳混排；播放卡显式标注 cache hit/miss/bypass；搜索卡聚合 30s 内的紧跟点击；销毁类卡片视觉降级为琥珀色，进程死亡类标红。
4. 一次性覆盖所有用户可见 Screen 的点击点（约 80–120 处），按 feature 模块分批落地。
5. 不引入新的上报通道，仍走现有 Logan 本地落盘；不做日志脱敏（与 [Logan Logging System Design](./2026-05-05-logging-system-design.md) 第一阶段一致）。

## 非目标

1. 不改 clogan 解密、envelope 拆包、AES key 管理（沿用 `tools/logan-viewer/src/format/`）。
2. 不引入分析后台 / BI；分析仍在本地完成。
3. 不做跨 session 的"用户身份串联"（需另设 anon device id，单独立项）。
4. 不做 Gantt / 时序图等更复杂可视化；v1 保持文字卡片。
5. 不动 RN 旧版逻辑对比视图。
6. 强杀场景下"销毁事件落盘失败"是 Logan 异步写入的固有限制，本设计不试图解决；通过冷启动的 `process_start_after_kill` 提供补偿信号。

## 总体阶段

```
v0  mockup     ── 单文件 H5，假数据，敲定 UI 细节
v1  Android 埋点 + viewer 规则集 ── 覆盖全部用户可见 Screen
v2  范围扩张：复合"探索会话"链式规则、anon id 串联、时序图视图
```

v0、v1 独立可发布、可回滚。

## v0 H5 Mockup

位置：`tools/logan-viewer-mockup/index.html`，单文件 HTML，内联 CSS + vanilla JS + 假数据，浏览器双击即开。

包含 3 个 session：

1. **主线流程**：启动 → 搜歌 → 进歌单 → 播 2:29 → 加歌单 → 切 Local Tab → lyric error → 后台 → Activity 被 trim → 回前台并 SavedState 恢复 → 设置 → 装插件 → 切到播放 → 失败。
2. **锁屏后台播放**：放歌中切到后台，Activity 销毁但 MediaSessionService 健在，play 卡保持 ongoing。
3. **进程死亡后冷启动恢复**：`process_start_after_kill` → 插件引擎初始化 → Store 从 snapshot 恢复 → 自动续播 → 用户主动退出（Activity / MediaSession / 插件引擎依次销毁）。

mockup 用于在动正式 Vite 工程和 Android 埋点之前敲定视觉密度、卡片信息量、文案、聚合粒度，不发布到 gh-pages。

## v1 Android 侧

### 新增 LogCategory

```kotlin
enum class LogCategory { /* 现有 ... */ , UI, NAVIGATION, RUNTIME }
```

`RUNTIME` 用于 Store / 进程级生命周期。已有的 `app` / `plugin` / `player` 不变。

### 新增事件 schema

| event | category | 必填 fields |
| --- | --- | --- |
| `screen_enter` | navigation | `route`, `params`(可空), `source` |
| `screen_exit`  | navigation | `route`, `durationMs` |
| `tab_switch`   | navigation | `from`, `to`, `source` |
| `ui_click`     | ui | `targetId`, `targetLabel`(可空), `screen` |
| `dialog_open`  | ui | `dialogId`, `screen`, `trigger` |
| `dialog_dismiss` | ui | `dialogId`, `screen`, `durationMs`, `outcome`(confirm/cancel/system) |
| `app_background` | app | `trigger`, `lastScreen`, `isPlaying`(可空) |
| `app_foreground` | app | `trigger`, `resumeScreen`, `backgroundedDurationMs` |
| `activity_created`   | app | `activity`, `hasSavedState`, `isConfigChange`, `isColdStart` |
| `activity_destroyed` | app | `activity`, `reason`(trim_memory_*/user_explicit_finish/...), `isFinishing`, `isChangingConfigurations`, `lifetimeMs` |
| `store_created`   | runtime | `storeId`, `scope`(app/activity/screen), `restoredFromSnapshot`, `snapshotKeys` |
| `store_destroyed` | runtime | `storeId`, `scope`, `reason`, `isPlaying`(可空) |
| `plugin_engine_init`      | plugin | `pluginCount`, `jsEngineVersion`, `durationMs` |
| `plugin_engine_destroyed` | plugin | `reason` |
| `media_session_started`   | player | `restoredQueueSize`(可空) |
| `media_session_destroyed` | player | `reason`, `lastSongId`, `queueSize` |
| `process_start_after_kill` | app | `lastBackgroundedDurationMs`, `suspectedReason`, `previousSessionId` |

`targetId` 命名约定：点分层级、纯 ASCII、稳定。示例：`home.tab_bar.player` / `player.controls.play` / `search.result.music_row` / `settings.row.theme`。每个 screen 维护本地常量表，禁止散落字符串。

### 新增工具

- `Modifier.loggedClick(targetId, screen, fields, onClick)` 与 `LoggedIconButton`：放在 `:core:ui`，避免 `:logging` 反向依赖 Compose。
- `ScreenLogEffect(screenId, params)` 公共 composable：在 Screen 顶层 `DisposableEffect` 中产出 `screen_enter` / `screen_exit`。
- `AppNavHost` 顶层 `NavController.OnDestinationChangedListener`，作为补丁兜底，确保所有路由切换至少有 `screen_enter`。
- 应用前后台切换：`ProcessLifecycleOwner.get().lifecycle` 观察 `ON_STOP` / `ON_START`。
- Activity 生命周期：`MainActivity.onCreate` / `onDestroy`。`reason` 通过 `isFinishing`、`isChangingConfigurations`、最近一次 `onTrimMemory(TRIM_MEMORY_COMPLETE/RUNNING_CRITICAL)` 分类。
- Store 生命周期：在 `RuntimeStore` 与 `SnapshotStore` 基类构造 / `onCleared` 写入 `store_created` / `store_destroyed`；ViewModel 通过持有 store 自动获得对应事件。`scope` 直接来自 `RuntimeStore.scope`。
- 插件引擎：`PluginManager.init()` 完成时写 `plugin_engine_init`；进程退出 hook 写 `plugin_engine_destroyed`（best-effort）。
- MediaSession：`MusicSessionService.onCreate` / `onDestroy`。
- `process_start_after_kill`：冷启动 `attachBaseContext` 后立即判定——读 `SnapshotStore` 上次 session 的 last_background_ts；若大于阈值且没有对应 `app_foreground` 配对，写入此事件，`previousSessionId` 帮 viewer 把两段 session 视觉上串起来。

### 覆盖范围（一次性，约 80–120 处点击点）

- 5 个 tab 全部 + screen 进入退出
- Home / HomeSearchBar / RecommendSheets / TopList / ListenStats / TrafficStats / ListenDetail
- Player / PlayQueue / 全部 ModalBottomSheet（Quality / Speed / AddToPlaylist / MoreOptions / LyricSearch / MusicItemOptions / MusicItemMoreMenu / PlaylistImportHost 等）
- Search / SearchMusicList / 平台 chip / 4 种结果 row
- Local / MusicListEditorLite / MusicDetail / AlbumDetail / ArtistDetail / PlaylistDetail / PluginSheetDetail / TopListDetail
- History
- Settings / SetCustomTheme / Permissions / FileSelector / PluginList / PluginSort / PluginSubscription / Downloading

按 feature 模块分批 commit，每批改完跑 debug APK 目测打包日志含对应 `ui_click`，再进入下一批：

1. `:core:ui` 基础 row（MusicItemRow 等）一次成型，多页面受益。
2. `:app` + `:feature:home`（含 tab bar、HomeScreen、ListenStats、TrafficStats）。
3. `:feature:player-ui`（PlayerScreen + 全部 Sheet）。
4. `:feature:search`。
5. `:feature:settings`（含 SetCustomTheme、Permissions、FileSelector）。
6. 剩余 feature 模块。

现有的 `player_play` 等 ViewModel 业务事件保留不动；UI 点击是补充层，不互斥。同一次"按播放键"会同时产出 `ui_click{targetId=player.controls.play}` 与 `player_play`，viewer 侧 `playAction` 规则优先吸收业务事件，`uiClick` 规则在其之后才尝试，不会重复成卡。

## v1 logan-viewer 侧

不改解密 / envelope / store 整体形态，只在 `parsed → traceGrouper → Timeline` 之间插一层 ActionMatcher。

新增目录：

```
tools/logan-viewer/src/actions/
├── types.ts              # ActionRule / ActionMatch / ActionCardData
├── matchActions.ts       # 在 session 内顺序应用规则，产出 TimelineItem[]
├── rules/
│   ├── screenVisit.ts
│   ├── tabSwitch.ts
│   ├── playAction.ts          # subtitle 含 cache_hit/miss/bypass
│   ├── searchAction.ts        # search_start + 30s 内紧跟点击
│   ├── pluginInstall.ts
│   ├── dialogAction.ts
│   ├── uiClick.ts
│   ├── appStartup.ts
│   ├── appLifecycle.ts        # app_background / app_foreground
│   ├── componentLifecycle.ts  # activity / store / plugin_engine / media_session / process_start_after_kill
│   └── errorAction.ts         # 任何 level=error 的兜底
└── __tests__/
tools/logan-viewer/src/ui/
└── ActionCard.tsx
```

### ActionRule 接口

```ts
export interface ActionRule {
  id: string;
  match(events: ParsedEvent[], startIndex: number): ActionMatch | null;
}
export interface ActionMatch {
  ruleId: string;
  consumedIndices: number[];
  card: ActionCardData;
}
export interface ActionCardData {
  ruleId: string;
  icon: string;
  title: string;
  subtitle?: string;
  result?: 'success' | 'failure' | 'partial' | 'ongoing';
  durationMs?: number;
  followups?: { arrow: string; text: string; id: string }[];
  fields: Record<string, string>;
  childEventIds: string[];
}
```

### Matcher 主循环

在每个 session 内顺序扫一遍事件，按规则数组顺序尝试匹配；命中则吞掉 consumed 事件；未命中事件降级为 `RawRow`。规则顺序：

```
appStartup → appLifecycle → componentLifecycle → tabSwitch
→ playAction → searchAction → pluginInstall → dialogAction
→ screenVisit → uiClick → errorAction
```

`errorAction` 放最后，让业务规则先有机会吞掉自己内部的失败。`appLifecycle` 与 `componentLifecycle` 前置，避免被其他规则误吞。`playAction` 在吞掉的子事件里检测 `cache_hit` / `cache_miss` / `cache_bypass` 并写入 subtitle。

### UI

- Timeline：ActionCard 与 RawRow 在同一条时间线按时间戳混排。ActionCard 高度 48–56px，RawRow 维持 22px。
- ActionCard 视觉编码：
  - `success` → 绿色边
  - `failure` → 红色边
  - `ongoing` → 蓝色边
  - `partial` → 琥珀色边
  - 销毁类（`*_destroyed`）统一标 `partial`，提示"状态可能丢失"。
  - `process_start_after_kill` 标 `failure`，提示"上次没有正常退出"。
- FilterBar：新增 `Structured only` 开关 + `Action types` 多选 chip。
- SessionList：每个 session 卡片追加 action 计数（"播放 5 · 搜索 3 · 错误 1"）。
- DetailDrawer：沿用现有抽屉，点击任意卡片 / raw row / child event 显示完整字段 + stack trace。

### 测试

- 每条规则一份 vitest fixture：构造 5–10 条 `ParsedEvent` 输入 → 断言产出的 `ActionCardData` 字段。
- `matchActions` 单测：未匹配事件按时间顺序作为 RawRow 出现；规则吞掉的事件不重复渲染。
- 同一份反馈包导入 logan-viewer，能正确渲染上述"启动 → 切 tab → 搜索 → 点结果 → 播放 → 切回 Home → 进 Settings → 返回 → 杀进程"路径为一串 ActionCard。

## 验收

**v0**：本地浏览器打开 `tools/logan-viewer-mockup/index.html`，看到 9 类卡片各至少 1 张、3 个 session、混排 + 折叠 + Structured only 切换 + Action 过滤 chip 全部工作。

**v1.1 Android**：
- `:logging` 与 `:core:ui` 单测通过。
- 编译 `:app:assembleDebug`、按上述 6 批顺序逐模块 commit，每批改完跑一次 debug APK 冒烟。
- 安装 debug → 走"启动 → 切 Search Tab → 搜索 'jay' → 点结果 → 播放 → 切回 Home → 进 Settings → 返回 → 杀进程"路径 → 反馈日志包内能看到完整 `screen_enter` / `tab_switch` / `ui_click` / `dialog_*` 事件链 + `activity_*` / `store_*` / `app_background/foreground` 事件。
- 现有 player/search/plugin 业务事件保持不动，回归对照 logan-viewer 旧视图。

**v1.2 viewer**：
- 同一份反馈包导入 logan-viewer，正确渲染为 ActionCard 串。
- 新增 vitest 全绿。
- 部署到 gh-pages 后线上访问无回归。

## 不在本次范围

- v2：把"搜索 → 进入歌单 → 播放 → 返回"整段串成"探索会话"复合卡。
- v2：跨 session 用户身份串联（需 anon device id）。
- v2：Gantt 式时序图视图。
- 日志脱敏与详细日志手动开关。
- 远程上传 / 后端接收 / 在线分析平台。

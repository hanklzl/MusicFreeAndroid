# Maestro Smoke 验收指南

> 文档状态：当前规范
> 适用范围：Debug 包真实网络 smoke、默认插件/订阅链路、Maestro 运行证据与 logcat 判读
> 直接执行：是
> 当前入口：[DOCS_STATUS](./DOCS_STATUS.md)、[Maestro smoke 设计](./superpowers/specs/2026-05-13-maestro-real-network-smoke-design.md)
> 最后校验：2026-05-13

## 定位

这套 Maestro flow 是真实网络运行态 smoke，用来验证 Debug 包在设备或模拟器上的核心用户旅程，并把失败现场与 `logcat`、结构化日志和截图关联起来。

它不是 hermetic 自动化测试。默认插件、订阅源、DNS、证书、远端响应结构和网络质量都会影响结果。失败后必须结合日志判断，不应直接把所有失败归因于代码回归。

## 前置条件

1. 本机可执行 `adb`。
2. 本机已安装 Maestro CLI，可执行 `maestro`。
3. 至少一台设备或模拟器处于 `device` 状态。
4. 设备可访问默认插件和订阅源所在网络。
5. 当前分支可以构建 Debug APK。

确认设备：

```bash
adb devices
```

预期至少一行状态为 `device`。

## 执行命令

核心 smoke：

```bash
bash scripts/maestro/run-smoke.sh --suite core
```

扩展 smoke：

```bash
bash scripts/maestro/run-smoke.sh --suite extended
```

全部 smoke：

```bash
bash scripts/maestro/run-smoke.sh --suite all
```

跳过构建并复用已安装 APK：

```bash
bash scripts/maestro/run-smoke.sh --suite core --skip-build --skip-install
```

清空 app data 后运行：

```bash
bash scripts/maestro/run-smoke.sh --suite core --clear-state
```

指定设备：

```bash
bash scripts/maestro/run-smoke.sh --suite core --serial <adb-serial>
```

Debug 包名：

```text
com.hank.musicfree.debug
```

Debug APK 路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 证据目录

运行脚本会写入：

```text
build/maestro-smoke/<timestamp>/
  device.txt
  summary.txt
  <suite>/
    <flow-name>/
      status.txt
      maestro.log
      junit.xml
      logcat-full.txt
      logcat-filtered.txt
      maestro-output/
```

最终验收结论必须写明：

- 设备或模拟器名称。
- 执行的 suite。
- 通过和失败的 flow。
- 证据目录。
- 是否存在 `AndroidRuntime`。
- 关键结构化日志是否齐全。

## Flow 清单

### core/01_launch_default_plugins

目的：验证 Debug 包启动到首页，默认插件引导不会阻塞或崩溃。

重点检查：

- 首页搜索入口可见。
- `logcat-filtered.txt` 无 `AndroidRuntime`。
- 日志包含 `app_start` 或 `main_activity_create_start`。
- 清空数据后首次启动通常应看到 `default_plugin_bootstrap_*` 事件。

### core/02_search_play

目的：验证首页进入搜索、真实插件搜索、点击首个单曲并进入播放器页。

重点检查：

- `screen.search.root`、`search.input`、`search.result.musicRow` 可定位。
- 成功时出现 `player.fullscreen.root`。
- 日志中能看到搜索、插件 API、音源解析或 player 事件。
- 搜索关键词使用 ASCII `jay`，避免 Maestro Android 非 ASCII 输入限制。

### core/03_settings_feedback_logs

目的：验证设置入口、基本设置滚动、反馈日志包隐私确认弹窗。

重点检查：

- 能从首页侧栏进入基本设置。
- “生成日志包并分享”可见。
- 弹窗文案包含“搜索词、请求地址、插件返回内容以及设备信息”。
- flow 点击“取消”，不进入系统分享面板。

### extended/recommend_sheets

目的：验证推荐歌单入口和真实插件推荐歌单列表加载。

重点检查：

- `screen.recommendSheets.root` 可见。
- 成功时 `recommendSheets.item` 可见并可进入详情。
- 失败时检查插件 API 和网络相关日志。

### extended/top_list_detail

目的：验证榜单入口、榜单列表和榜单详情链路。

重点检查：

- `screen.topList.root` 可见。
- 成功时 `topList.item` 可见并可进入详情。
- 失败时检查 `top_list_load_*`、`top_list_detail_load_*` 和插件 API 日志。

### extended/plugin_management

目的：验证从首页侧栏进入插件管理页，并确认入口菜单可见。

重点检查：

- `settings.pluginManagement.entry` 可见。
- `screen.pluginList.root` 可见。
- “订阅设置”“排序”“卸载全部”“从本地安装”“从网络安装”“更新全部插件”“更新订阅”可见。
- smoke 不执行卸载、更新和安装，避免污染状态。

### extended/player_queue

目的：通过真实搜索播放建立播放状态，再验证 mini player、播放器页和队列入口。

前置条件：设备网络可访问默认插件搜索和音源链路；flow 会自行搜索 `jay` 并点击首个单曲。

重点检查：

- `player.mini.root` 可见。
- `player.mini.queue` 可见。
- `player.queue.root` 可见。
- 日志中无 player 或 queue 相关崩溃。

## 日志判定

每个 flow 至少看三类证据：

1. Maestro 结果：`status.txt`、`maestro.log`、截图。
2. `logcat`：`logcat-full.txt` 与 `logcat-filtered.txt`。
3. 结构化日志：启动、默认插件、插件 API、搜索、播放、反馈日志包相关事件。

重点事件：

- 启动：`app_start`、`main_activity_create_start`、`edge_to_edge_enabled`
- 默认插件：`default_plugin_bootstrap_subscription`、`default_plugin_bootstrap_plugin`、`default_plugin_bootstrap_completed`、`default_plugin_bootstrap_failed`
- 插件：`plugin_*`、`plugin_api_*`、`plugin_get_media_source_*`
- 搜索：`search_*`
- 首页详情：`recommend_*`、`top_list_load_*`、`top_list_detail_load_*`、`plugin_sheet_detail_*`
- 播放：`player_*`、`play_queue_*`
- 反馈：`feedback_package_*`、`feedback_logs_*`

## 失败分类

优先视为代码问题：

- `AndroidRuntime` 崩溃。
- 首页、搜索、设置等基础导航不可达。
- 反馈日志隐私确认弹窗缺失。
- 插件加载失败没有在 UI 或日志中体现。
- flow 成功但关键结构化日志完全缺失。

优先视为环境或远端问题：

- DNS、TLS、HTTP 超时。
- 默认插件远端文件无法访问。
- 远端插件返回结构变化。
- 搜索返回空结果。
- 设备网络不可用。

诊断结论用语：

- “运行态通过”：flow 通过，`logcat` 无 `AndroidRuntime`，关键日志齐全。
- “UI smoke 通过，日志验收不完整”：flow 通过，但关键结构化日志缺失。
- “运行态失败，诊断信息完整”：flow 失败，但日志清楚说明网络、插件或业务失败原因。
- “运行态失败，诊断信息不足”：flow 失败，且缺少可定位日志，需要补打点或补锚点。

## 常见问题

### 没有设备

现象：脚本输出 `No adb device in 'device' state.`

处理：

```bash
adb devices
```

确认设备不是 `unauthorized`、`offline` 或空列表。

### 未安装 Maestro

现象：脚本输出 `Required command not found: maestro`。

处理：安装 Maestro CLI 后重新运行。

### 通知权限弹窗阻塞

flow 已尝试点击 “允许|Allow|Allow notifications”。若厂商 ROM 文案不同，先在设备上手动允许通知权限，再使用 `--skip-install` 复跑。

### 默认插件还没安装完成

首次清数据启动后默认插件需要真实网络。先看 `logcat-filtered.txt` 中的 `default_plugin_bootstrap_*` 事件，再决定是等待、复跑，还是判定网络问题。

### 搜索失败

先检查 `search_*`、`plugin_*`、`plugin_api_*`、`plugin_get_media_source_*`。若是网络或远端插件失败，结论写“运行态失败，诊断信息完整”。若没有任何搜索或插件日志，优先补日志或检查 UI 事件是否触发。

### 系统分享面板打开

`03_settings_feedback_logs.yaml` 只验证确认弹窗并点击“取消”。如果进入系统分享面板，说明 flow 或 UI 文案匹配错了，需要修正 flow，不要把分享面板纳入 smoke。

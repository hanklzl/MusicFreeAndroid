---
status: 当前规范
date: 2026-05-13
topic: Maestro 真实网络 smoke 功能测试
scope: Debug 包运行态验收、真实默认插件/订阅网络链路、logcat 与结构化日志取证
---

# Maestro 真实网络 Smoke 功能测试设计

## 1. 背景

当前仓库已经具备 Debug 默认引导插件能力：`DefaultPluginsBootstrapper` 会在冷启动后根据 `app/src/main/java/com/hank/musicfree/bootstrap/DefaultPlugins.kt` 中的订阅源和单插件 URL 自动 reconcile 插件。日志系统也已经落地 `MfLog` / Logan，覆盖启动、默认插件引导、插件、搜索、播放器、反馈日志包等核心链路。

现在需要引入 Maestro 功能测试用例，帮助后续在设备或模拟器上执行真实运行态验收，并把失败现场与 `logcat`、结构化日志和 Logan 日志包关联起来。用户已明确选择使用真实默认插件/订阅网络链路，而不是本地 mock 或稳定夹具。

## 2. 目标

1. 新增一组 Maestro YAML flow，覆盖应用核心真实用户旅程。
2. 新增统一运行脚本，负责安装/启动 Debug 包、清理并采集 `logcat`、按 suite 执行 Maestro、保存运行证据。
3. 新增验收文档，说明环境准备、执行命令、失败判定、日志事件检查点和真实网络不稳定时的处理方式。
4. 明确这些用例是“真实网络运行态 smoke”，不是 hermetic 自动化测试；失败必须结合日志与远端状态判断。
5. 复用现有 Compose testTag/resource-id 锚点，减少对视觉文案和布局位置的脆弱依赖。

## 3. 非目标

1. 不新增本地 mock 插件、MockWebServer、测试专用后端或 hermetic 数据夹具。
2. 不改生产 UI、导航、日志系统或插件系统行为。
3. 不把 Maestro flow 接入默认 Gradle test 或 CI 必跑通道。
4. 不自动生成或上传反馈日志包；日志包导出仍由设置页用户确认流程触发。
5. 不把真实网络失败直接判定为代码回归。

## 4. 方案选择

采用“核心 smoke + 可选扩展 smoke”的分层套件。

备选“单条长链路 E2E”最贴近完整用户旅程，但任何一步网络、远端插件或动画超时都会导致整条链路失败，定位粒度差。

备选“页面级独立 smoke”更容易维护，但跨页面状态、插件搜索到播放、播放后队列等核心链路覆盖较弱。

推荐分层套件的原因是：日常可以快速跑 `core`，功能验收或回归排查时再跑 `extended`。每个 flow 都有清晰日志关注点，脚本可以按 flow 保存 `logcat` 和 Maestro 输出，便于定位失败发生在启动、默认插件、搜索、播放、设置还是详情链路。

## 5. 文件布局

新增文件布局：

```text
maestro/
  flows/
    smoke/
      core/
        01_launch_default_plugins.yaml
        02_search_play.yaml
        03_settings_feedback_logs.yaml
      extended/
        recommend_sheets.yaml
        top_list_detail.yaml
        plugin_management.yaml
        player_queue.yaml
scripts/
  maestro/
    run-smoke.sh
docs/
  maestro-smoke-acceptance.md
```

`core` flow 是日常最小验收集；`extended` flow 依赖真实网络和默认插件能力更重，默认作为人工验收或问题复现补充。

## 6. 运行脚本设计

`scripts/maestro/run-smoke.sh` 提供统一入口：

```bash
bash scripts/maestro/run-smoke.sh --suite core
bash scripts/maestro/run-smoke.sh --suite extended
bash scripts/maestro/run-smoke.sh --suite all
```

默认目标包名为 Debug 包：

```text
com.hank.musicfree.debug
```

脚本职责：

1. 检查 `adb`、`maestro` 是否可用。
2. 检查至少一台设备或模拟器处于 `device` 状态。
3. 可选执行 `./gradlew :app:assembleDebug`，通过 `--skip-build` 跳过。
4. 安装 `app/build/outputs/apk/debug/app-debug.apk`。
5. 在每个 flow 前执行 `adb logcat -c`。
6. 执行对应 Maestro YAML。
7. 将 Maestro 输出、`logcat -d`、失败状态保存到 `build/maestro-smoke/<timestamp>/<suite>/<flow-name>/`。
8. `logcat` 采集需要保留完整输出，同时额外生成过滤视图，重点包含：
   - `AndroidRuntime`
   - `MusicFree`
   - `MfLog`
   - `default_plugin_bootstrap`
   - `plugin_`
   - `search_`
   - `player_`
   - `feedback_`

脚本退出码遵循 Maestro 结果：任一必跑 flow 失败则退出非 0。真实网络失败仍表现为 flow 失败，但文档会要求结合日志判定是否为代码回归。

## 7. Selector 策略

优先使用已经暴露为 resource-id 的 Compose testTag：

- `home.navBar.menu`
- `home.navBar.search`
- `home.operations.recommendSheets`
- `home.operations.topList`
- `home.drawer.settings.basic`
- `home.drawer.settings.plugin`
- `screen.search.root`
- `search.input`
- `screen.settings.root`
- `settings.basic.root`
- `settings.pluginManagement.entry`
- `player.fullscreen.root`
- `player.mini.root`
- `player.mini.queue`

当目标元素没有稳定 resource-id 时，允许退回中文文案，例如“搜索”“推荐歌单”“榜单”“生成日志包并分享”“继续”。退回文案的 flow 必须在注释中说明原因，后续若补稳定 testTag，应优先迁移。

不使用固定坐标点击，除非 Maestro 无法通过 resource-id 或文本定位系统控件。任何坐标点击都必须只用于临时兜底并在文档中标注。

## 8. Flow 设计

### 8.1 `01_launch_default_plugins.yaml`

目的：验证 Debug 包可启动到首页，默认插件引导不会阻塞或崩溃。

步骤：

1. 启动 `com.hank.musicfree.debug`。
2. 处理系统通知权限弹窗：允许或跳过，取决于设备 Android 版本。
3. 等待首页导航栏或搜索入口出现。
4. 等待一段短时间，给默认插件 reconcile 写入日志。

验收重点：

- UI 到达首页。
- `logcat` 无 `AndroidRuntime` 崩溃。
- 日志中至少出现 `app_start` 或 `main_activity_create_start`。
- 若网络可用，预期出现 `default_plugin_bootstrap_*` 事件；若未出现，需结合是否已有插件、URL 是否已跳过和 app data 是否清理判断。

### 8.2 `02_search_play.yaml`

目的：验证首页进入搜索、真实插件搜索、点击结果触发播放入口。

步骤：

1. 从首页点击搜索入口。
2. 输入固定 ASCII 关键词，例如 `jay`。Maestro Android `inputText` 不稳定支持非 ASCII 输入，中文关键词留给手工验收或后续专用输入方案。
3. 点击“搜索”或提交输入法搜索。
4. 等待搜索结果或错误状态。
5. 点击首个单曲结果。
6. 验证 `player.fullscreen.root` 出现。

验收重点：

- `screen.search.root` 和 `search.input` 可定位。
- 搜索过程不崩溃。
- 成功路径预期出现搜索、插件 API、播放解析或 player 相关日志。
- 若真实插件返回空结果或网络失败，flow 允许失败，但日志必须能说明失败原因，例如插件 API failure、axios/network failure 或 UI 错误文案。

### 8.3 `03_settings_feedback_logs.yaml`

目的：验证设置入口、基本设置滚动、反馈日志包隐私确认弹窗。

步骤：

1. 从首页打开侧栏。
2. 进入“基本设置”。
3. 滚动到“开发选项”。
4. 点击“生成日志包并分享”。
5. 验证“分享日志包”确认弹窗和隐私提示文案。
6. 点击“取消”，不触发系统分享面板。

验收重点：

- 设置页不因日志导出路径校验崩溃。
- 隐私提示必须可见，文案包含“搜索词、请求地址、插件返回内容以及设备信息”。
- 不实际创建或分享日志包，避免自动化卡在系统分享选择器。

### 8.4 `recommend_sheets.yaml`

目的：验证推荐歌单入口和真实插件推荐歌单列表加载。

步骤：

1. 从首页点击推荐歌单入口。
2. 等待 `screen.recommendSheets.root`。
3. 等待列表、空状态或错误状态。
4. 若存在歌单项，点击首个歌单进入详情。

验收重点：

- 推荐歌单页可打开。
- 真实网络成功时列表可见并能进入详情。
- 失败时日志包含 `recommend_sheets` / 插件 API / network 相关失败原因。

### 8.5 `top_list_detail.yaml`

目的：验证榜单入口、榜单列表和榜单详情链路。

步骤：

1. 从首页点击榜单入口。
2. 等待 `screen.topList.root`。
3. 等待榜单列表、空状态或错误状态。
4. 若存在榜单项，点击首个榜单进入详情。

验收重点：

- 榜单页可打开。
- 数值型 ID 规范化相关问题应能通过详情加载日志暴露。
- 失败时检查 `top_list_load_*`、`top_list_detail_load_*` 和插件 API 日志。

### 8.6 `plugin_management.yaml`

目的：验证从首页侧栏进入插件管理页，并确认默认插件管理入口可见。

步骤：

1. 从首页打开侧栏。
2. 点击插件管理入口。
3. 在设置插件页点击“进入”。
4. 到达插件管理页。
5. 打开更多菜单，确认“订阅设置”“排序”“卸载全部”可见。
6. 打开安装 FAB 菜单，确认“从本地安装”“从网络安装”“更新全部插件”“更新订阅”可见。

验收重点：

- 插件管理入口链路可达。
- 默认插件失败也必须可见，不能静默吞掉。
- 不在 smoke 中执行卸载、更新全部或安装操作，避免污染真实插件状态。

### 8.7 `player_queue.yaml`

目的：通过真实搜索播放建立播放状态，再验证 mini player、播放器页和队列入口基础可用。

前置条件：设备网络可访问默认插件搜索和音源链路；flow 自行搜索 `jay` 并点击首个单曲。

步骤：

1. 从首页或当前页面确认 `player.mini.root`。
2. 点击 mini player 进入播放器页。
3. 返回后点击 mini queue。
4. 验证播放队列弹层或队列空状态。

验收重点：

- 播放状态能够跨页面展示。
- 队列入口不崩溃。
- 关注 `player`、`play_queue`、`plugin_get_media_source_*` 相关日志。

## 9. 日志验收口径

每次运行至少检查三类证据：

1. Maestro 结果：flow 是否通过、失败步骤、截图或输出。
2. `logcat`：是否存在 `AndroidRuntime`、ANR、权限异常、ActivityNotFoundException 等运行态错误。
3. 结构化日志：是否能还原用户动作和失败边界。

重点事件包括：

- 启动：`app_start`、`main_activity_create_start`、`edge_to_edge_enabled`
- 默认插件：`default_plugin_bootstrap_subscription`、`default_plugin_bootstrap_plugin`、`default_plugin_bootstrap_completed`、`default_plugin_bootstrap_failed`
- 插件：`plugin_*`、`plugin_api_*`、`plugin_get_media_source_*`
- 搜索：`search_*`
- 首页详情：`recommend_*`、`top_list_load_*`、`top_list_detail_load_*`、`plugin_sheet_detail_*`
- 播放：`player_*`、`play_queue_*`
- 反馈：`feedback_package_*`、`feedback_logs_*`

真实网络 smoke 的判定规则：

- UI 崩溃、`AndroidRuntime`、导航不可达、确认弹窗缺失：优先视为代码问题。
- 网络超时、远端插件返回结构变化、DNS/证书/HTTP 失败：先视为环境或远端问题，除非同一提交前后稳定复现。
- flow 失败但日志清晰记录失败原因：验收结论应写“运行态失败，诊断信息完整”，而不是简单写“通过”。
- flow 成功但关键日志缺失：验收结论应写“UI smoke 通过，日志验收不完整”。

## 10. 文档验收内容

`docs/maestro-smoke-acceptance.md` 需要包含：

1. 安装 Maestro、确认 adb 设备、构建 Debug APK 的准备步骤。
2. `core`、`extended`、`all` 三类执行命令。
3. Debug 包名和 APK 路径。
4. 运行产物目录结构。
5. 每个 flow 的目的、前置条件、成功标准和重点日志。
6. 常见失败排查：
   - 无设备或设备 unauthorized。
   - 未安装 Maestro。
   - 系统权限弹窗阻塞。
   - 默认插件未安装完成。
   - 真网插件返回为空或失败。
   - 搜索成功但播放解析失败。
   - 系统分享面板不应在 smoke 中打开。
7. 如何在最终验收结论中写明设备名称、suite、flow、日志路径和未执行原因。

## 11. 实施顺序

1. 新增 Maestro 目录和 `core` flow。
2. 新增 `run-smoke.sh`，先支持 `core`。
3. 新增验收文档，记录 `core` 运行口径。
4. 新增 `extended` flow。
5. 扩展脚本支持 `extended` 与 `all`。
6. 在真实设备或模拟器上运行 `core`，再视网络状态运行 `extended`。
7. 根据实际 Maestro selector 表现微调 YAML，优先补稳定 testTag，而不是增加坐标点击。

## 12. 验证计划

静态验证：

- `bash -n scripts/maestro/run-smoke.sh`
- `git diff --check`

运行态验证：

```bash
./gradlew :app:assembleDebug --no-daemon
bash scripts/maestro/run-smoke.sh --suite core
```

若有可用设备且网络稳定，再运行：

```bash
bash scripts/maestro/run-smoke.sh --suite extended --skip-build
```

最终反馈必须写明：

- 设备或模拟器名称。
- 执行的 suite。
- 通过和失败的 flow。
- 证据目录。
- 是否存在 `AndroidRuntime`。
- 关键结构化日志是否齐全。

若无设备、未安装 Maestro 或网络不可用，必须明确说明对应原因，不得声称完整运行态验收通过。

## 13. 风险与约束

- 真实默认插件和订阅源可能变化，flow 不能依赖固定歌曲名、固定榜单名或固定歌单名。
- 搜索关键词会进入日志；这符合当前日志策略，但验收文档必须提醒日志可能包含搜索词、URL、插件返回内容和设备信息。
- 不应在 smoke 中执行“卸载全部插件”“更新全部插件”“生成并分享日志包”等会污染状态或调起系统外部 UI 的操作。
- Maestro 对 Compose testTag 的识别依赖 `testTagsAsResourceId`；若某些目标缺少该语义，优先补 UI 锚点并遵守 UI Harness 规则。
- `player_queue.yaml` 依赖已有播放状态，单独运行可能失败；脚本文档必须把它标为状态依赖 flow。

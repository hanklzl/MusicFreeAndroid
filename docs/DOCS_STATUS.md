# 文档状态索引（DOCS_STATUS）

> 文档状态：当前规范
> 适用范围：仓库文档治理与状态索引。
> 直接执行：是
> 当前入口：[AGENTS](../AGENTS.md)
> 备注：执行任务前先看本文件，再进入具体文档。
> 最后校验：2026-05-09
>
> 最后校验日期：2026-05-09
> 
> 该文档是本仓库文档治理的单一入口。执行任务前，先看这里再看具体文档。

## 状态定义

- `当前规范`：可直接用于当前执行。
- `当前参考`：可用于理解背景或约束，不应直接当作执行步骤。
- `历史记录`：仅保留历史上下文，默认不可直接执行。

## 全局规则

- 文档互引必须使用相对路径。
- 禁止使用 `/Users/...` 绝对路径。
- 跨仓库引用同样使用相对路径（例如 `../MusicFree/...`）。
- `docs/superpowers/plans/*.md` 默认视为历史执行快照。

## 文档清单

| 文档 | 状态 | 可直接执行 | 说明 / 当前入口 |
|---|---|---|---|
| [AGENTS.md](../AGENTS.md) | 当前规范 | 是 | 仓库工作总规则 |
| [docs/DOCS_STATUS.md](./DOCS_STATUS.md) | 当前规范 | 是 | 文档状态与治理入口 |
| [docs/ui-harness/screen-chrome-rules.md](./ui-harness/screen-chrome-rules.md) | 已迁移 | 否 | 已并入 [docs/dev-harness/ui/rules.md](./dev-harness/ui/rules.md)；本路径仅保留 redirect stub |
| [docs/superpowers/specs/2026-05-04-playlist-import-design.md](./superpowers/specs/2026-05-04-playlist-import-design.md) | 当前规范（歌单导入功能） | 是（作为实现计划输入） | 首页导入歌单入口、插件 importMusicSheet 能力识别与批量导入到用户歌单的设计 |
| [docs/superpowers/specs/2026-05-04-playlist-feature-design.md](./superpowers/specs/2026-05-04-playlist-feature-design.md) | 当前规范（歌单功能） | 是（作为实现计划输入） | 用户歌单 CRUD + 默认 我喜欢 + 排序 + 封面 + ⭐ surface roll-out 的设计 |
| [docs/superpowers/specs/2026-05-04-player-statusbar-inset-design.md](./superpowers/specs/2026-05-04-player-statusbar-inset-design.md) | 当前规范（播放器状态栏避让专项） | 是（作为实现计划输入） | 播放器页背景沉浸但内容层避让状态栏的设计 |
| [docs/superpowers/specs/2026-05-04-player-lyrics-design.md](./superpowers/specs/2026-05-04-player-lyrics-design.md) | 当前规范（播放页歌词） | 是（作为实现计划输入） | 播放页歌词加载、自动搜索、同步滚动、拖动跳转、翻译、偏移、搜索关联与本地导入设计 |
| [docs/superpowers/specs/2026-05-05-player-lyrics-interaction-fix-design.md](./superpowers/specs/2026-05-05-player-lyrics-interaction-fix-design.md) | 当前规范（播放页歌词交互修正） | 是（作为实现计划输入） | 播放页歌词加载态闪烁、进入页定位、平滑跟随、点击切封面与手动滑动跳转 overlay 的修正设计 |
| [docs/superpowers/specs/2026-05-09-player-detail-controls-design.md](./superpowers/specs/2026-05-09-player-detail-controls-design.md) | 当前规范（播放详情页控制区对齐） | 是（作为实现计划输入） | 播放详情页封面页功能栏贴近进度条、播放模式随机/单曲/列表三态循环与歌词页返回封面验收设计 |
| [docs/superpowers/specs/2026-05-10-player-operation-buttons-rn-align-design.md](./superpowers/specs/2026-05-10-player-operation-buttons-rn-align-design.md) | 当前规范（播放页操作按钮 RN 尺寸对齐） | 是（作为实现计划输入） | 播放详情页封面页与歌词页进度条上方操作按钮行的 RN 尺寸、资源和结构对齐设计 |
| [docs/superpowers/specs/2026-05-03-ui-harness-screen-chrome-design.md](./superpowers/specs/2026-05-03-ui-harness-screen-chrome-design.md) | 当前规范（UI Harness 设计） | 是（作为实现计划输入） | Screen 切换动画、普通 AppBar、沉浸式状态栏和 AI Coding 规则入口设计 |
| [docs/superpowers/specs/2026-05-03-splashscreen-launcher-icon-design.md](./superpowers/specs/2026-05-03-splashscreen-launcher-icon-design.md) | 当前规范（启动视觉专项） | 是（仅 SplashScreen 与 launcher icon 专项） | AndroidX SplashScreen 接入与 RN 启动图标资源对齐设计 |
| [docs/dev-harness/INDEX.md](./dev-harness/INDEX.md) | 当前规范（Dev Harness 总入口） | 是 | UI / 插件 / 播放器 / 测试 四域规则、错误库、AI skills 关联入口 |
| [docs/dev-harness/ui/rules.md](./dev-harness/ui/rules.md) | 当前规范（Dev Harness — UI） | 是 | Screen 切换动画、普通 AppBar、沉浸式状态栏、UI 设计原则 |
| [docs/dev-harness/plugin/rules.md](./dev-harness/plugin/rules.md) | 当前规范（Dev Harness — Plugin） | 是 | QuickJS 线程模型、网络通道门控、PluginManager 编排 |
| [docs/dev-harness/player/rules.md](./dev-harness/player/rules.md) | 当前规范（Dev Harness — Player） | 是 | PlayerController 连接、沉浸式 chrome、歌词跟随防抖 |
| [docs/dev-harness/test/rules.md](./dev-harness/test/rules.md) | 当前规范（Dev Harness — Test） | 是 | runTest 范式、instrumentation 主线程模型、DataStore 隔离、runner 基线、JVM 内存基线 |
| [docs/dev-harness/incidents/index.md](./dev-harness/incidents/index.md) | 当前规范（Dev Harness — Incidents 索引） | 是 | INC-YYYY-NNNN 全仓索引；guard 类型反查 |
| [docs/superpowers/specs/2026-05-09-dev-harness-foundation-design.md](./superpowers/specs/2026-05-09-dev-harness-foundation-design.md) | 当前规范（Dev Harness 基础设施专项） | 是（作为实现计划输入） | 总入口 + 错误库 + 5 skills + 测试守门 + 3 PR 编排设计 |
| [docs/superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md](./superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md) | 当前规范（Android 测试稳定性专项） | 是（作为实现计划输入） | feature androidTest runner 基线、Gradle full androidTest D8 OOM、Player instrumentation setUp 死锁、`@Ignore` 反应化、Settings VM `runTest` 迁移、HomeFidelity 断言改写、`:plugin` 集成测试拆分、MockWebServer 介入与 instrumentation DataStore 隔离 |
| [docs/superpowers/specs/2026-05-04-github-actions-debug-apk-design.md](./superpowers/specs/2026-05-04-github-actions-debug-apk-design.md) | 当前规范（CI Debug APK） | 是（仅 GitHub Actions Debug APK 打包专项） | 提交触发 Debug APK 构建、artifact 上传与 Debug 独立包名设计 |
| [docs/superpowers/specs/2026-05-05-release-apk-signing-design.md](./superpowers/specs/2026-05-05-release-apk-signing-design.md) | 当前规范（Release APK 签名） | 是（作为实现计划输入） | 侧载 Release APK 签名、GitHub `release` Environment secrets、tag 发布与手动验包 artifact 设计 |
| [docs/superpowers/specs/2026-05-05-logging-system-design.md](./superpowers/specs/2026-05-05-logging-system-design.md) | 当前规范（Logan 日志系统） | 是（作为实现计划输入） | 美团 Logan 持久化日志、用户反馈日志包、7 天淘汰、本地解码脚本与开发阶段日志规范设计 |
| [docs/superpowers/specs/2026-05-10-logging-diagnostics-coverage-design.md](./superpowers/specs/2026-05-10-logging-diagnostics-coverage-design.md) | 当前规范（日志诊断覆盖） | 是（作为实现计划输入） | 核心 ViewModel、插件、网络、文件 IO、下载、数据层与播放链路的第二阶段日志诊断覆盖设计 |
| [docs/superpowers/specs/2026-05-05-playlist-cover-and-row-display-design.md](./superpowers/specs/2026-05-05-playlist-cover-and-row-display-design.md) | 当前规范（歌单封面与详情行展示对齐 RN） | 是（作为实现计划输入） | 歌单封面 http(s) 透传、相册选图 file:// 形态、抽 `MusicItemRow` 接入歌单详情展示 platform tag 与 album 的设计 |
| [docs/superpowers/specs/2026-05-05-search-autofocus-design.md](./superpowers/specs/2026-05-05-search-autofocus-design.md) | 当前规范（搜索输入自动聚焦） | 是（作为实现计划输入） | 主搜索页与歌单内搜索页进入后自动聚焦输入框并拉起输入法的 RN 对齐设计 |
| [docs/superpowers/specs/2026-05-09-plugin-management-parity-design.md](./superpowers/specs/2026-05-09-plugin-management-parity-design.md) | 当前规范（插件管理 RN 完整对齐） | 是（作为实现计划输入） | 插件卡片操作、音源重定向、用户变量、导入单曲/歌单、安装更新反馈与全局音源解析设计 |
| [docs/superpowers/specs/2026-05-09-recommend-toplist-rn-align-design.md](./superpowers/specs/2026-05-09-recommend-toplist-rn-align-design.md) | 当前规范（推荐歌单与榜单 RN 对齐） | 是（作为实现计划输入） | 推荐歌单/榜单入口页主要 UI 对齐 RN、插件能力过滤与详情点击 seed 修复设计 |
| [docs/superpowers/specs/2026-05-09-settings-basic-alignment-design.md](./superpowers/specs/2026-05-09-settings-basic-alignment-design.md) | 当前规范（基本设置对齐） | 是（作为实现计划输入） | typed settings route、基本设置 RN 分区可见、Android 卡片分组、已生效项可编辑与未接入项禁用展示设计 |
| [docs/superpowers/specs/2026-05-10-release-settings-feedback-crash-design.md](./superpowers/specs/2026-05-10-release-settings-feedback-crash-design.md) | 当前规范（Release 设置页反馈日志崩溃修复） | 是（作为实现计划输入） | Release 包从首页侧栏进入设置页时因反馈日志导出路径校验崩溃的根因与修复设计 |
| [docs/superpowers/specs/2026-05-10-settings-runtime-integration-design.md](./superpowers/specs/2026-05-10-settings-runtime-integration-design.md) | 当前规范（基本设置运行态接入） | 是（作为实现计划输入） | 基本设置中历史条数、点击播放策略、播放详情默认页/常亮、播放/下载音质回退与移动网络播放的运行态接入设计 |
| [docs/superpowers/specs/2026-05-10-local-music-rn-parity-design.md](./superpowers/specs/2026-05-10-local-music-rn-parity-design.md) | 当前规范（本地音乐 RN 对齐） | 是（作为实现计划输入） | 本地音乐 AppBar 主题色、搜索/扫描/批量编辑/下载列表入口与持久本地曲库链路设计 |
| [docs/superpowers/specs/2026-05-10-favorite-starred-playlists-design.md](./superpowers/specs/2026-05-10-favorite-starred-playlists-design.md) | 当前规范（默认我喜欢与收藏歌单修复） | 是（作为实现计划输入） | 默认 `我喜欢` 歌单自动恢复、首页收藏歌单真实数据、插件歌单详情 heart 收藏入口设计 |
| [docs/superpowers/specs/2026-05-11-wy-recommend-toplist-detail-fix.md](./superpowers/specs/2026-05-11-wy-recommend-toplist-detail-fix.md) | 当前规范（网易推荐歌单与榜单详情修复） | 是（作为实现计划输入） | 插件返回数值型整数 ID 时规范化为无 `.0` 字符串，避免网易歌单/榜单详情调用失败 |
| [docs/home-fidelity/homepage/README.md](./home-fidelity/homepage/README.md) | 当前规范（首页专项） | 是（仅首页专项） | 首页取证目录、命名与采集顺序 |
| [docs/superpowers/specs/2026-04-11-homepage-ui-fidelity-manifest.md](./superpowers/specs/2026-04-11-homepage-ui-fidelity-manifest.md) | 当前规范（首页专项） | 是（仅首页专项） | 首页黄金数据态基线 |
| [docs/superpowers/specs/2026-04-11-homepage-main-ui-mock-design.md](./superpowers/specs/2026-04-11-homepage-main-ui-mock-design.md) | 当前参考（首页 mock 专项） | 否 | 首页主界面与底部 mini player 的第一阶段 mock 对齐设计 |
| [docs/superpowers/specs/2026-04-11-homepage-ui-fidelity-design.md](./superpowers/specs/2026-04-11-homepage-ui-fidelity-design.md) | 当前参考 | 否 | 首页 fidelity 设计决策背景 |
| [docs/superpowers/specs/2026-03-25-home-fidelity-design.md](./superpowers/specs/2026-03-25-home-fidelity-design.md) | 当前参考 | 否 | 首页 fidelity 早期基线设计 |
| [docs/superpowers/specs/2026-03-19-musicfree-android-native-rewrite-design.md](./superpowers/specs/2026-03-19-musicfree-android-native-rewrite-design.md) | 当前参考 | 否 | 项目早期总体设计，版本细节以 AGENTS 为准 |
| [docs/superpowers/specs/2026-04-02-journey-driven-iteration-workflow-design.md](./superpowers/specs/2026-04-02-journey-driven-iteration-workflow-design.md) | 历史记录 | 否 | 设计提出的 `specs/` 资产层未在当前仓库落地 |
| [docs/superpowers/plans/2026-04-11-homepage-ui-fidelity-design.md](./superpowers/plans/2026-04-11-homepage-ui-fidelity-design.md) | 历史记录 | 否 | 首页专项执行快照 |
| [docs/superpowers/plans/2026-04-02-journey-workflow-bootstrap.md](./superpowers/plans/2026-04-02-journey-workflow-bootstrap.md) | 历史记录 | 否 | 工作流引导计划快照 |
| [docs/superpowers/plans/2026-03-20-milestone6-plugin-engine-search.md](./superpowers/plans/2026-03-20-milestone6-plugin-engine-search.md) | 历史记录 | 否 | 里程碑执行快照 |
| [docs/superpowers/plans/2026-03-20-milestone4-local-music-playback-ui.md](./superpowers/plans/2026-03-20-milestone4-local-music-playback-ui.md) | 历史记录 | 否 | 里程碑执行快照 |
| [docs/superpowers/plans/2026-03-20-milestone3-player-engine.md](./superpowers/plans/2026-03-20-milestone3-player-engine.md) | 历史记录 | 否 | 里程碑执行快照 |
| [docs/superpowers/plans/2026-03-19-milestone2-data-layer.md](./superpowers/plans/2026-03-19-milestone2-data-layer.md) | 历史记录 | 否 | 里程碑执行快照 |
| [docs/superpowers/plans/2026-03-19-milestone1-project-scaffolding.md](./superpowers/plans/2026-03-19-milestone1-project-scaffolding.md) | 历史记录 | 否 | 里程碑执行快照 |

## 使用建议

1. 需要“当前可执行规则”时，只看 `当前规范`。
2. 需要理解设计背景时，再补读 `当前参考`。
3. 遇到 `历史记录`，默认只用于回溯，不直接照做。
4. 若文档状态与仓库事实冲突，先更新本索引再更新正文。

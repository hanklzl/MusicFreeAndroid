# 文档状态索引（DOCS_STATUS）

> 文档状态：当前规范
> 适用范围：仓库文档治理与状态索引。
> 直接执行：是
> 当前入口：[AGENTS](../AGENTS.md)
> 备注：执行任务前先看本文件，再进入具体文档。
> 最后校验：2026-05-04
>
> 最后校验日期：2026-05-04
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
| [docs/ui-harness/screen-chrome-rules.md](./ui-harness/screen-chrome-rules.md) | 当前规范（UI Harness Rules） | 是 | Screen 切换动画、普通 AppBar 与沉浸式状态栏强制规则 |
| [docs/superpowers/specs/2026-05-04-playlist-import-design.md](./superpowers/specs/2026-05-04-playlist-import-design.md) | 当前规范（歌单导入功能） | 是（作为实现计划输入） | 首页导入歌单入口、插件 importMusicSheet 能力识别与批量导入到用户歌单的设计 |
| [docs/superpowers/specs/2026-05-04-playlist-feature-design.md](./superpowers/specs/2026-05-04-playlist-feature-design.md) | 当前规范（歌单功能） | 是（作为实现计划输入） | 用户歌单 CRUD + 默认 我喜欢 + 排序 + 封面 + ⭐ surface roll-out 的设计 |
| [docs/superpowers/specs/2026-05-04-player-statusbar-inset-design.md](./superpowers/specs/2026-05-04-player-statusbar-inset-design.md) | 当前规范（播放器状态栏避让专项） | 是（作为实现计划输入） | 播放器页背景沉浸但内容层避让状态栏的设计 |
| [docs/superpowers/specs/2026-05-03-ui-harness-screen-chrome-design.md](./superpowers/specs/2026-05-03-ui-harness-screen-chrome-design.md) | 当前规范（UI Harness 设计） | 是（作为实现计划输入） | Screen 切换动画、普通 AppBar、沉浸式状态栏和 AI Coding 规则入口设计 |
| [docs/superpowers/specs/2026-05-03-splashscreen-launcher-icon-design.md](./superpowers/specs/2026-05-03-splashscreen-launcher-icon-design.md) | 当前规范（启动视觉专项） | 是（仅 SplashScreen 与 launcher icon 专项） | AndroidX SplashScreen 接入与 RN 启动图标资源对齐设计 |
| [docs/superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md](./superpowers/specs/2026-05-04-test-suite-rehabilitation-design.md) | 当前规范（Android 测试稳定性专项） | 是（作为实现计划输入） | feature androidTest runner 基线、Gradle full androidTest D8 OOM、Player instrumentation setUp 死锁、`@Ignore` 反应化、Settings VM `runTest` 迁移、HomeFidelity 断言改写、`:plugin` 集成测试拆分、MockWebServer 介入与 instrumentation DataStore 隔离 |
| [docs/superpowers/specs/2026-05-04-github-actions-debug-apk-design.md](./superpowers/specs/2026-05-04-github-actions-debug-apk-design.md) | 当前规范（CI Debug APK） | 是（仅 GitHub Actions Debug APK 打包专项） | 提交触发 Debug APK 构建、artifact 上传与 Debug 独立包名设计 |
| [docs/superpowers/specs/2026-05-05-release-apk-signing-design.md](./superpowers/specs/2026-05-05-release-apk-signing-design.md) | 当前规范（Release APK 签名） | 是（作为实现计划输入） | 侧载 Release APK 签名、GitHub `release` Environment secrets、tag 发布与手动验包 artifact 设计 |
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

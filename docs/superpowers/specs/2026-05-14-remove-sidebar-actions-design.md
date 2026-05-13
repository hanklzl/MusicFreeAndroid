# 侧栏删除语言与退出类入口设计

> 文档状态：当前规范
> 适用范围：本次 Android 首页侧栏入口裁剪
> 直接执行：是
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md) ｜ [AGENTS](../../../AGENTS.md)
> 最后校验：2026-05-14

## 背景

用户要求删除首页侧栏中的“语言设置”“回到桌面”“退出软件”三个功能入口，并授权 `spec + plan + implement` 直接执行、使用 worktree 与 sub-agent-driven-development，完成后合并回 `main`。

RN 原版 `../MusicFree/src/pages/home/components/drawer/index.tsx` 当前仍保留语言设置和两个退出类入口。本次变更按产品指令在 Android 侧主动裁剪这些入口，不把 RN 当前行为作为保留依据。

## 目标

- 首页侧栏不再显示“语言设置”。
- 首页侧栏底部不再显示“回到桌面”和“退出软件”。
- 已移除入口不能继续通过侧栏点击路径触发语言弹窗、回桌面或退出 App。
- 保留侧栏其他入口：基础设置、插件管理、主题设置、定时关闭、备份与恢复、权限管理、检查更新、关于 MusicFree。

## 非目标

- 不删除系统层 `HomeSystemActionHandler`，避免扩大到通知、定时关闭或未来系统动作场景。
- 不删除 RN 源码中的对应入口。
- 不新增语言设置页面，也不改现有国际化存储。
- 不改侧栏 status bar inset、页面切换动画或 AppBar harness。

## 设计

侧栏数据源集中在 `feature/home/.../HomeDrawerNavigation.kt` 的 `buildHomeDrawerUiModel()`。本次从 model 层移除三个入口：软件分区去掉语言设置条目，`footerActions` 改为空列表。UI 层 `HomeDrawerContent` 已按 model 渲染，因此不需要新增隐藏判断。

点击分发层 `HomeScreenContent.handleDrawerEntryClick()` 不再需要处理 `HomeDrawerAction.ShowLanguageDialog`。状态层 `HomeScreenState` 不再维护 `isLanguageDialogVisible`，`HomeDrawerDialogs` 不再接收语言弹窗参数，也不再渲染语言弹窗。

`HomeDrawerAction.BackToDesktop` 与 `HomeDrawerAction.ExitApp` 不再由侧栏 model 暴露。为避免无入口动作继续被误用，本次从 sealed action 中删除这两个 action；`HomeSystemActionHandler` 暂时保留，因为它是系统动作接口，不属于侧栏 model 的显示契约。

已不再被侧栏使用的三个图标资源同步删除，避免保留无入口的语言 / 回桌面 / 退出软件视觉资产。

## 测试策略

- 先改 `HomeDrawerUiModelTest`，断言软件分区只保留检查更新与关于、`footerActions` 为空，并验证被删除 anchor 不再出现。
- 更新 `HomeScreenContentTest` / `HomeScreenStateTest`，移除语言弹窗点击与返回处理预期。
- 更新 anchor 与 icon contract，删除语言、回桌面、退出软件三个侧栏入口锚点和图标映射要求。
- 运行 `:feature:home:testDebugUnitTest` 验证 home 模块单测。
- 运行 `python3 scripts/dev-harness/grep-check.py` 与 `:app:assembleDebug` 作为收尾守门。

## 风险与约束

- 这是 UI 入口裁剪，最主要风险是测试仍引用已删除 action 或 anchor，导致测试源编译失败。按 `docs/dev-harness/test/rules.md#rule-test-fixture-must-track-vm-ctor`，需要跑 home 模块单测覆盖测试源编译。
- 侧栏是浮层，必须继续遵守 `docs/dev-harness/ui/rules.md#rule-overlay-respects-statusbar`；本次不改 drawer 布局和 inset。
- 删除入口会使 Android 与 RN 当前侧栏结构不一致，属于本 spec 明确记录的产品差异。

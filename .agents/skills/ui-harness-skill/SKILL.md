---
name: ui-harness
description: >
  Use this skill whenever the task touches a Compose Screen, AppBar,
  navigation animation, status bar handling, MusicFreeScreenScaffold,
  MusicFreeTopAppBar, MusicFreeStatusBarChrome, FidelityAnchors, rpx
  sizing, immersive chrome, or any UI flow under feature/* / app/. Trigger
  phrases: "新建 Compose Screen", "改顶部栏", "状态栏", "切换动画",
  "Scaffold", "TopAppBar", "rpx", "FidelityAnchors", "沉浸式".
---

# UI Harness Skill

Cross-tool guidance for Compose Screen / Chrome work in MusicFreeAndroid.
Pairs with the UI rules + UI incidents to keep AI changes consistent
with the public Compose harness.

## 必读 gate

调起本 skill 前，必须 Read：

- [`docs/dev-harness/ui/rules.md`](references/rules.md)（软链 → 正本）
- [`docs/dev-harness/ui/incidents.md`](references/incidents.md)（软链 → 正本）

## Workflow checklist

1. 读 rules.md 与 incidents.md，识别本次改动落在哪条 rule（screen 切换动画 / 普通 AppBar / MainActivity 责任 / 沉浸式特殊 chrome / 设计原则）。
2. 普通页面默认走 `MusicFreeScreenScaffold(title, onBack) { ... }`；自定义顶部走 `MusicFreeTopAppBar` + `MusicFreeStatusBarChrome`。
3. 涉及导航动画时改集中入口 `app/.../navigation/MusicFreeNavTransitions.kt`；MUST NOT 在 Screen 内部用 `AnimatedContent` / `AnimatedVisibility` 伪装页面切换。
4. 涉及 `MainActivity` 顶部 inset 时只能减不能加；新增页面顶部 chrome 是 Screen 的责任。
5. 跑：`./gradlew :app:testDebugUnitTest --tests '*MusicFreeNavTransitionsTest' --no-daemon` 与 `*harness.contracts.*`（PR 3 起生效）。
6. 提交前检查 grep 守门：`python3 scripts/dev-harness/grep-check.py` 应全绿。
7. 若新增/修改 `FidelityAnchors`，同步更新 `docs/home-fidelity/`（见 references/fidelity-anchors.md）。

## 反例（rules.md 已禁止）

- 直接手写 `TopAppBar(colors = TopAppBarDefaults.topAppBarColors(...))`。
- `MainActivity` 在 Scaffold contentWindowInsets 中加 `WindowInsetsSides.Top`。
- 在 Screen 内用 `AnimatedContent` 伪装页面切换。
- 修改导航动画时长偏离 100ms。

## References

- [screen-scaffold-walkthrough.md](references/screen-scaffold-walkthrough.md)
- [navigation-animation.md](references/navigation-animation.md)
- [status-bar-immersive.md](references/status-bar-immersive.md)
- [fidelity-anchors.md](references/fidelity-anchors.md)

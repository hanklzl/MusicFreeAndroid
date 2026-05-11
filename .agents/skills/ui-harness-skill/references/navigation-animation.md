# Navigation Animation

集中入口：`app/src/main/java/com/zili/android/musicfreeandroid/navigation/MusicFreeNavTransitions.kt`。

约束：

- 常量 `MusicFreeScreenTransitionDurationMillis = 400`，按 RN Android `slide_from_right` 实际 medium animation 口径守门，不允许在无专项设计的 PR 内改写。
- `AppNavHost` 的 `enterTransition` / `exitTransition` / `popEnterTransition` / `popExitTransition` 全部引用 `musicFree*Transition()` helper。
- 特殊页面若需差异化动画，在 destination 注册时显式 override，并在 rules.md 的"特殊 Chrome 页面"段登记原因。

contract test 守门：`UiNavAnimationDurationContractTest`（PR 3 起生效，复用 `MusicFreeNavTransitionsTest` 同断言）。

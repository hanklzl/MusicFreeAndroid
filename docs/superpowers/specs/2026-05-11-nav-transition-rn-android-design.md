# Android 页面切换动画 RN Android 对齐设计

> 文档状态：当前规范
> 适用范围：普通 Compose Screen 页面切换动画、predictive back 开关
> 直接执行：是
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md) ｜ [UI rules](../../dev-harness/ui/rules.md)
> 最后校验：2026-05-11

## 背景

Android 原生版本当前把普通页面切换动画锁定为 `100ms`。这个值来自 RN 入口
`../MusicFree/src/entry/index.tsx` 中的 `animationDuration: 100`，但 RN Android 实际运行链路并不使用
这个字段控制 `slide_from_right` 时长。

RN 侧依赖 `@react-navigation/native-stack@6.11.0` 与 `react-native-screens@4.4.0`。
`animationDuration` 会映射为 `transitionDuration`，而 `react-native-screens` Android 端
`ScreenViewManager.setTransitionDuration()` 是 no-op。`slide_from_right` 最终走
`rns_slide_in_from_right.xml` / `rns_slide_out_to_left.xml`，资源使用
`@android:integer/config_mediumAnimTime`。因此 Android 原生版本继续锁 `100ms` 会比 RN Android 实际体验短。

## 目标

- 普通页面默认仍使用 `slide_from_right` 方向：前进从右进、向左出；返回从左回入、向右出。
- 默认动画时长改为 `400ms`，作为 Android `config_mediumAnimTime` 口径的稳定项目常量。
- `AppNavHost` 继续只引用 `MusicFreeNavTransitions.kt` 集中 helper，不在 destination 或 Screen 内散落动画参数。
- 应用级禁用 predictive back：Manifest `application` 节点设置
  `android:enableOnBackInvokedCallback="false"`。
- 更新 UI harness 规则、incident 说明和 contract tests，避免旧 `100ms` 守门继续拦截新目标。

## 非目标

- 不修改 RN 仓库源码。
- 不引入页面级特殊动画 override。
- 不改 Screen 内部 `AnimatedContent` / `AnimatedVisibility` 动画。
- 不调整系统返回逻辑、NavController back stack 或首页抽屉 back 行为。

## 实现设计

导航动画继续保留一个 Kotlin 常量：
`MusicFreeScreenTransitionDurationMillis = 400`。所有 `musicFree*Transition()` helper 复用同一个
`tween<IntOffset>`，保持进出场时长一致。

predictive back 通过 `app/src/main/AndroidManifest.xml` 的 `application` 节点禁用，而不是在
`MainActivity` 手写返回分发。这样不会改变 Compose Navigation 的 back stack 行为，也避免 Activity 层增加新的隐式规则。

## 测试与守门

- `MusicFreeNavTransitionsTest` 断言普通页面时长为 `400ms`。
- `UiNavAnimationDurationContractTest` 作为 harness contract 同步断言 `400ms`，并引用新的 UI rule anchor。
- `SplashScreenResourceContractTest` 增加 Manifest contract，断言 application 节点
  `android:enableOnBackInvokedCallback="false"`。
- 本地验证以 `:app:testDebugUnitTest` 的相关测试、`scripts/dev-harness/grep-check.py` 和
  `:app:assembleDebug` 为收尾闸门。

## 文档更新

`docs/dev-harness/ui/rules.md` 的页面切换动画 rule 更新为 RN Android medium animation 口径。
`docs/dev-harness/ui/incidents.md` 与 `docs/dev-harness/incidents/index.md` 保留
INC-2026-0006 的历史背景，但把教训从“锁 100ms”改为“不要只读取 JS `animationDuration`，需按 RN Android
实际生效链路建守门”。`.agents/skills/ui-harness-skill/references/navigation-animation.md` 同步更新，避免后续 AI
继续复制旧值。

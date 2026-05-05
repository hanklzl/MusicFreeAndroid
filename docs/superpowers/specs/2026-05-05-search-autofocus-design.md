# 搜索输入自动聚焦设计

> 文档状态：当前规范
> 适用范围：仅适用于主搜索页与歌单内搜索页的输入框自动聚焦和输入法拉起行为。
> 直接执行：是（作为实现计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> RN 参考：`../../../../MusicFree/src/pages/searchPage/components/navBar.tsx`、`../../../../MusicFree/src/pages/searchMusicList/index.tsx`
> UI Harness 规则：[screen-chrome-rules](../../ui-harness/screen-chrome-rules.md)
> 最后校验：2026-05-05

## 背景

RN 原版在两个搜索输入页都使用 `autoFocus`：

- 主搜索页 `searchPage/components/navBar.tsx` 的 `Input` 进入页面后立即聚焦。
- 歌单内搜索页 `searchMusicList/index.tsx` 的 `TextInput` 同样使用 `autoFocus`。

Android 当前差异如下：

- `feature/search/SearchScreen.kt` 的顶部 `BasicTextField` 没有 `FocusRequester` 或输入法显示逻辑。
- `feature/home/searchmusiclist/SearchMusicListScreen.kt` 的标题栏 `BasicTextField` 也没有自动聚焦。
- 首页点击搜索只执行 `navController.navigate(SearchRoute)`，页面进入后输入框不会立即获得焦点，也不会拉起输入法。

该差异影响搜索入口的连续操作体验：用户从首页点击搜索后，还需要再次点击输入框才能输入。

## 目标

1. 首页进入 `SearchRoute` 后，主搜索输入框自动获得焦点并拉起输入法。
2. 进入 `SearchMusicListRoute` 后，歌单内搜索输入框自动获得焦点并拉起输入法。
3. 自动聚焦只在页面首次进入时执行，不因输入内容变化、搜索状态变化或普通重组反复抢焦点。
4. 保持现有搜索、清空、返回、历史记录和结果页行为不变。
5. 开发在 `.worktrees/feat-search-autofocus` git worktree 中进行。

## 非目标

- 不新增导航参数区分“从首页进入”或“从其他页面进入”。
- 不改造搜索页 UI chrome、页面切换动画或状态栏策略。
- 不抽取新的跨模块搜索输入组件。
- 不调整搜索历史、插件搜索、歌单内过滤或播放逻辑。

## 设计

推荐在两个目标 Screen 内分别实现本地 autofocus 行为：

- `SearchScreen` 为顶部 `BasicTextField` 创建 `FocusRequester`，并将 `Modifier.focusRequester(focusRequester)` 附加到输入框。
- `SearchMusicListScreen` 为标题栏 `BasicTextField` 使用同样模式。
- 在每个 Screen 中使用 `LocalSoftwareKeyboardController.current` 获取输入法控制器。
- 使用 `LaunchedEffect(Unit)` 在首帧后执行一次：
  1. `focusRequester.requestFocus()`
  2. `keyboardController?.show()`

该做法直接映射 RN `autoFocus` 的页面级语义，并避免把输入框焦点行为泄漏到路由层。

## 时序

```text
进入 SearchRoute / SearchMusicListRoute
  -> Screen 组合
  -> BasicTextField 挂载 FocusRequester
  -> LaunchedEffect(Unit)
  -> requestFocus()
  -> show keyboard
```

如果用户随后清空输入、提交搜索、点击历史搜索、返回结果页或点击返回按钮，继续沿用当前页面逻辑。自动聚焦不再次触发。

## 验收

自动化验证：

- 扩展首页入口 instrumentation 测试：点击 `FidelityAnchors.Home.NavBarSearch` 后，确认搜索输入节点存在且处于 focused。
- 为歌单内搜索页补充可行的焦点测试。如果现有测试环境难以稳定进入真实路由，应至少为输入框增加稳定测试 tag，并在可组合级或 instrumentation 层验证 focused 语义。

运行态验证：

- 在设备或模拟器上从首页点击搜索，确认搜索输入框立即获得焦点并拉起输入法。
- 从歌单详情进入歌单内搜索，确认输入框立即获得焦点并拉起输入法。

构建验证：

- `./gradlew test`
- 相关 Android instrumentation 测试

## 风险与处理

- 输入法显示依赖系统窗口焦点，`requestFocus()` 过早可能只聚焦输入框但不显示键盘。实现时应在输入框已挂载后触发，必要时通过 `awaitFrame()` 延后一帧。
- Compose 测试对真实输入法显示不稳定，自动化断言以 focused 语义为主，输入法拉起通过设备/模拟器运行态验收确认。
- `SearchScreen` 是 UI Harness 规则登记的特殊 Chrome 页面，本次只改输入框焦点行为，不改变状态栏或 AppBar 处理。

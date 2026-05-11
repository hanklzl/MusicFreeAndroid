# 搜索输入仅首次进入自动聚焦设计

> 文档状态：当前规范
> 适用范围：主搜索页与歌单内搜索页的输入框自动聚焦消费时机。
> 直接执行：是（作为实现计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 相关既有规范：[搜索输入自动聚焦设计](./2026-05-05-search-autofocus-design.md)
> RN 参考：`../../../../MusicFree/src/pages/searchPage/components/navBar.tsx`、`../../../../MusicFree/src/pages/searchMusicList/index.tsx`
> UI Harness 规则：[UI rules](../../dev-harness/ui/rules.md)
> 测试规则：[Test rules](../../dev-harness/test/rules.md)
> 最后校验：2026-05-11

## 背景

`2026-05-05-search-autofocus-design.md` 要求主搜索页和歌单内搜索页进入后自动聚焦输入框，并且不因普通重组反复抢焦点。当前实现使用 `LaunchedEffect(Unit)`：

- `feature/search/.../SearchScreen.kt` 在 Screen 进入 composition 后请求 `FocusRequester.requestFocus()` 并显示输入法。
- `feature/home/.../SearchMusicListScreen.kt` 使用同样模式。

`LaunchedEffect(Unit)` 只保证同一次 composition 内执行一次。Compose Navigation 在搜索页进入详情页、播放器页等下一层 Screen 后，搜索页可能离开 composition；当用户返回同一个 back stack entry 时，该 Screen 重新进入 composition，`LaunchedEffect(Unit)` 会再次执行，导致输入框重新抢焦点并弹出输入法。

## 目标

1. 每个搜索 Screen 的同一个 Navigation back stack entry 内，自动聚焦只消费一次。
2. 首次从首页进入 `SearchRoute` 时仍自动聚焦并拉起输入法。
3. 首次进入 `SearchMusicListRoute` 时仍自动聚焦并拉起输入法。
4. 从搜索页进入另一 Screen 后再返回时，不再重复请求焦点或主动弹出输入法。
5. 搜索页被 pop 后再次从入口进入，应视为新的 Screen 实例，允许重新自动聚焦。

## 非目标

- 不取消 RN 对齐的首次进入自动聚焦。
- 不新增导航参数来控制焦点行为。
- 不调整搜索 UI chrome、状态栏、页面切换动画或 AppBar 结构。
- 不改搜索历史、插件搜索、歌单内过滤、播放或详情页导航逻辑。
- 不新增日志事件；本修正只改变本地 UI 焦点消费时机。

## 设计

推荐将“首次自动聚焦是否已消费”保存在对应 ViewModel 实例内：

- `SearchViewModel` 增加一个私有布尔状态和 `consumeInitialAutofocusRequest()` 方法。
- `SearchMusicListViewModel` 增加同名方法。
- Screen 的 `LaunchedEffect(Unit)` 先调用 `viewModel.consumeInitialAutofocusRequest()`；只有返回 `true` 时才等待一帧、请求焦点并显示输入法。

ViewModel 默认跟随 Navigation back stack entry 生命周期。搜索页跳到详情页或播放器页时，原 back stack entry 和 ViewModel 保留；返回同一 entry 时消费状态仍为已消费，所以不会再弹键盘。搜索页被 pop 后重新进入会创建新的 entry 和 ViewModel，自动聚焦行为重新可用。

## 时序

```text
首次进入 SearchRoute / SearchMusicListRoute
  -> Screen 组合
  -> ViewModel.consumeInitialAutofocusRequest() 返回 true
  -> 等待一帧
  -> requestFocus()
  -> keyboardController.show()

从该 Screen 进入另一 Screen 后返回
  -> 原 ViewModel 仍在同一 back stack entry 内
  -> Screen 重新组合
  -> consumeInitialAutofocusRequest() 返回 false
  -> 不请求焦点，不主动显示输入法

pop 掉搜索 Screen 后重新进入
  -> 新 back stack entry / 新 ViewModel
  -> consumeInitialAutofocusRequest() 再次返回 true
```

## 测试

自动化验证：

- 为 `SearchViewModel` 增加单测，断言 `consumeInitialAutofocusRequest()` 首次返回 `true`，之后返回 `false`。
- 为 `SearchMusicListViewModel` 增加同等单测。
- 扩展 `SearchMusicListScreenFocusTest`：先验证首次进入会聚焦，再复用同一个 ViewModel 重新挂载 Screen，断言输入框不会再次获得焦点。该测试覆盖“返回同一 Screen 实例”下的 UI 调用路径。
- 保留现有 `HomeEntryNavigationTest.searchEntry_opensSearchRootAndFocusesInput`，继续覆盖主搜索页首次进入聚焦。

运行命令：

- `./gradlew :feature:search:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.search.SearchViewModelTest" --no-daemon`
- `./gradlew :feature:home:testDebugUnitTest --tests "com.zili.android.musicfreeandroid.feature.home.searchmusiclist.SearchMusicListViewModelTest" --tests "com.zili.android.musicfreeandroid.feature.home.searchmusiclist.SearchMusicListScreenFocusTest" --no-daemon`
- `./gradlew :app:assembleDebug --no-daemon`

## 风险与处理

- `requestFocus()` 过早仍可能只聚焦不弹输入法，继续保留当前 `withFrameNanos { }` 延后一帧策略。
- 该状态放在 ViewModel 中是 UI 一次性消费状态，不进入持久化存储；进程重建后若还原到搜索页，重新自动聚焦是可接受行为。
- 若未来抽出公共搜索输入组件，可把同名消费方法下沉为专用焦点 gate，但本次不为两个调用点引入新抽象。

# 播放详情页控制区对齐设计

> 文档状态：当前规范
> 适用范围：播放详情页封面页功能栏位置、播放模式按钮三态循环、歌词页点击返回封面验收。
> 直接执行：是（作为后续实施计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 参考来源：`../../../../MusicFree/src/pages/musicDetail/`、当前 Android `:feature:player-ui` / `:player` 实现。
> 最后校验：2026-05-09

## 概要

本设计修正播放详情页三个运行态问题，让 Android 播放详情页更接近 RN 原版体验：

1. 封面页的功能栏应贴近底部播放进度条，而不是悬在封面与进度条之间。
2. 点击封面进入歌词页后，再次点按应能回到封面页。当前代码和单测层面已修复，本次只纳入运行态验收，避免回退。
3. 底部播放模式按钮应支持随机播放、单曲循环、列表循环三态，而不是只能在单曲循环和列表循环之间切换。

用户已确认采用“按 RN 原版对齐”的方案：布局上让功能栏与进度条成为底部连续块；播放模式上使用单一三态按钮循环。

## 当前事实

Android 当前相关代码：

- 播放详情页入口：`feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerScreen.kt`
- 播放详情页 ViewModel：`feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/PlayerViewModel.kt`
- 播放控制器：`player/src/main/java/com/zili/android/musicfreeandroid/player/controller/PlayerController.kt`
- 播放队列：`player/src/main/java/com/zili/android/musicfreeandroid/player/queue/PlayQueue.kt`
- 播放状态：`player/src/main/java/com/zili/android/musicfreeandroid/player/model/PlayerState.kt`
- 播放模式模型：`core/src/main/java/com/zili/android/musicfreeandroid/core/model/RepeatMode.kt`
- 歌词内容组件：`feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsContent.kt`
- 歌词点击返回相关测试：`feature/player-ui/src/test/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricsContentTest.kt`

已确认事实：

- `PlayerScreen` 当前封面页使用多个 `Spacer(Modifier.weight(1f))`。功能栏后仍有一个弹性空白，导致功能栏无法贴近进度条。
- `PlayerOperationsBar` 已包含收藏、质量文本、下载、倍速文本、歌词入口和更多入口，但其布局位置不符合 RN。
- `PlayerControls` 已接收 `repeatMode` 和 `shuffleEnabled`，并能显示 `ic_shuffle`，但当前点击逻辑为：
  - 已随机时点击关闭随机。
  - 未随机时点击只循环 `RepeatMode`。
  - 因此用户无法从普通循环状态通过该按钮进入随机播放。
- `PlayerController` 已具备 `setRepeatMode()`、`cycleRepeatMode()`、`toggleShuffle()`。
- `PlayQueue` 已具备 `shuffle()` / `unshuffle()`，且会尽量保持当前歌曲。
- `PlayerLyricsContentTest` 当前已有“点击歌词文本返回封面”的测试，focused test 已通过：

```bash
./gradlew :feature:player-ui:testDebugUnitTest --tests com.zili.android.musicfreeandroid.feature.playerui.lyrics.PlayerLyricsContentTest
```

RN 原版参考：

- `../../../../MusicFree/src/pages/musicDetail/index.tsx`：页面结构为 `NavBar -> Content -> Bottom`。
- `../../../../MusicFree/src/pages/musicDetail/components/content/albumCover/index.tsx`：封面页包含封面和 `Operations`。
- `../../../../MusicFree/src/pages/musicDetail/components/content/albumCover/operations.tsx`：功能栏高度 `rpx(80)`，下方 `marginBottom: rpx(24)`，紧邻底部 `Bottom`。
- `../../../../MusicFree/src/pages/musicDetail/components/bottom/index.tsx`：底部包含 `SeekBar` 和 `PlayControl`。
- `../../../../MusicFree/src/pages/musicDetail/components/bottom/playControl.tsx`：左下角播放模式按钮调用 `TrackPlayer.toggleRepeatMode()`。
- `../../../../MusicFree/src/constants/repeatModeConst.ts`：RN 播放模式包含 `SHUFFLE`、`QUEUE`、`SINGLE`。
- `../../../../MusicFree/src/core/trackPlayer/index.ts`：RN 切换顺序为 `SHUFFLE -> SINGLE -> QUEUE -> SHUFFLE`。

## 非目标

本次不做以下内容：

- 不实现下载、音源质量切换、倍速切换的完整业务能力；本次只处理功能栏位置和已有入口展示。
- 不新增队列面板或播放列表面板。
- 不重写歌词加载、歌词滚动、歌词搜索或歌词偏移逻辑。
- 不调整播放器状态栏 inset；该专项已有独立设计。
- 不修改 `MainActivity`、导航结构、插件系统、Room 或 DataStore。
- 不做横屏播放详情页视觉专项；横屏只要求不因本次改动明显崩坏。
- 不引入新的播放队列模型；继续使用当前 `PlayQueue`。

## 设计决策

采用“局部布局对齐 + 单一播放模式循环 helper”的方案。

### 封面页布局

封面页应表达 RN 的结构关系：

```text
PlayerNavBar
Cover area fills remaining vertical space
PlayerOperationsBar
small fixed gap
PlayerSeekBar
PlayerControls
bottom spacer / navigation-bar breathing room
```

实现上应移除功能栏和进度条之间的弹性 `weight` 空白。封面区域可以继续使用弹性空间居中封面；功能栏、进度条、播放控制区应组成底部连续块。

建议把封面页主体拆为私有 composable，例如 `PlayerCoverPageContent`，让 `PlayerScreen` 的 `when (contentPage)` 分支更清晰。该 composable 只负责封面页布局，不承载播放业务。

功能栏与进度条间距对齐 RN 的意图：功能栏自身高度保持 `rpx(80)`，功能栏下方到进度条保留小的固定间距，建议使用 `rpx(24)` 或基于现有视觉微调的固定值。不得再使用弹性 spacer 让距离随屏幕高度大幅变化。

### 歌词页返回封面

当前 `PlayerLyricsContent` 已允许点击歌词文本或空白区域触发 `onBackToCover()`。本次不改变该交互，不恢复“点击歌词文本不返回”的旧行为。

验收时需要确认：

- 点击封面进入歌词页。
- 点击歌词文本可返回封面。
- 点击歌词空白区域可返回封面。
- seek overlay 展示时仍不应误触返回封面。

### 播放模式三态

Android 现有状态由两个字段表达：

```text
shuffleEnabled: Boolean
repeatMode: RepeatMode.OFF / ONE / ALL
```

UI 对外应显示 RN 三态：

```kotlin
enum class PlaybackModeUi {
    Shuffle,
    Single,
    Queue,
}
```

映射规则：

- `shuffleEnabled == true` -> `Shuffle`
- `shuffleEnabled == false && repeatMode == RepeatMode.ONE` -> `Single`
- `shuffleEnabled == false && repeatMode == RepeatMode.ALL` -> `Queue`
- `shuffleEnabled == false && repeatMode == RepeatMode.OFF` -> `Queue`

`RepeatMode.OFF` 映射为 `Queue` 是兼容当前默认状态的务实选择。RN 原版没有独立“不循环”模式；播放详情页左下角三态按钮不应展示“关闭循环”这种第四状态。

点击模式按钮时，采用 RN 顺序：

```text
Shuffle -> Single -> Queue -> Shuffle
```

建议在 `PlayerController` 新增单一入口，例如 `cyclePlaybackMode()`，封装状态迁移。`PlayerViewModel` 暴露同名或对应方法，`PlayerControls` 只调用这个方法，不再自己判断 `shuffleEnabled`。

状态迁移要求：

- 当前为 `Shuffle`：
  - 先退出随机并恢复队列原顺序。
  - 设置 `RepeatMode.ONE`。
  - 发出新 `PlayerState`。
- 当前为 `Single`：
  - 确保随机关闭。
  - 设置 `RepeatMode.ALL`。
  - 发出新 `PlayerState`。
- 当前为 `Queue`：
  - 设置列表循环语义，建议保持 `RepeatMode.ALL`。
  - 开启随机并随机化队列。
  - 发出新 `PlayerState`。

这里的关键约束是：不要出现 UI 显示已退出随机，但 `PlayQueue` 仍处于随机顺序的状态。退出 `Shuffle` 必须走 `unshuffle()` 或等价恢复逻辑。

## 组件边界

### `:core`

可新增轻量 UI 映射模型或纯函数，前提是它不依赖 Compose：

- `PlaybackModeUi`
- `PlaybackModeUi.from(shuffleEnabled, repeatMode)`
- `PlaybackModeUi.next()`

如果实现阶段发现该 helper 只被 `:feature:player-ui` 使用，也可以放在 `feature/player-ui` 内部，避免扩散公共 API。

### `:player`

`PlayerController` 负责真实状态迁移：

- 新增 `cyclePlaybackMode()`。
- 复用 `PlayQueue.shuffle()` / `PlayQueue.unshuffle()`。
- 复用 `repeatMode`、`shuffleEnabled`、`emitState()`。

`PlayQueue` 不需要新增随机算法；只在测试暴露缺口时做局部修补。

### `:feature:player-ui`

`PlayerScreen` 负责：

- 调整封面页布局。
- 显示三态播放模式图标。
- 将模式按钮点击转发给 `viewModel.cyclePlaybackMode()`。
- 保留歌词页现有返回封面交互。

`PlayerControls` 不应再用 `if (shuffleEnabled) onToggleShuffle else onCycleRepeatMode` 这种分支表达业务迁移；它只根据 UI 状态显示图标并触发单一回调。

## 错误处理与边界行为

- 当前无播放队列或单曲队列时，进入随机播放不应崩溃。`PlayQueue.shuffle()` 当前对空队列和单曲队列安全返回，状态仍可显示为随机。
- 退出随机时，如果原队列已被删除或插入过，沿用 `PlayQueue.unshuffle()` 现有“保留存活歌曲并追加新歌曲”的行为。
- `RepeatMode.OFF` 在播放详情页 UI 中统一按列表循环展示；实现阶段不需要删除 `RepeatMode.OFF`，以免影响其他播放流程。
- 功能栏中的下载、倍速、音源质量入口若仍是占位，不应在本次伪装成功能完成；保留现有点击行为或禁用行为即可。

## 测试策略

### 单元测试

`PlaybackModeUi` 或等价 helper：

- `shuffleEnabled=true` 映射为 `Shuffle`。
- `repeatMode=ONE` 映射为 `Single`。
- `repeatMode=ALL` 映射为 `Queue`。
- `repeatMode=OFF` 映射为 `Queue`。
- `Shuffle.next() == Single`。
- `Single.next() == Queue`。
- `Queue.next() == Shuffle`。

`PlayerController`：

- `cyclePlaybackMode()` 从默认队列状态进入随机，`shuffleEnabled=true`。
- 随机状态再次点击进入单曲，`shuffleEnabled=false` 且 `repeatMode=ONE`。
- 单曲状态再次点击进入列表循环，`shuffleEnabled=false` 且 `repeatMode=ALL`。
- 退出随机后队列不再保持随机标记。

`PlayerScreen` / `PlayerControls`：

- 播放模式按钮可显示随机、单曲、列表循环三个图标。
- 点击模式按钮调用单一模式循环回调。
- 封面页功能栏和进度条在结构上属于底部连续块；测试优先使用 test tag 或语义顺序，避免脆弱像素断言。

`PlayerLyricsContent`：

- 保留现有点击歌词文本返回封面测试。
- 保留 overlay 阻止误触返回封面测试。

### 本地验证命令

默认收尾验证：

```bash
./gradlew :feature:player-ui:testDebugUnitTest
./gradlew :player:testDebugUnitTest
./gradlew :app:assembleDebug
```

如实现阶段触及 `:core` helper，追加：

```bash
./gradlew :core:testDebugUnitTest
```

### 运行态验收

若设备或模拟器可用，必须补以下验收：

1. 播放一首歌并进入播放详情页。
2. 确认封面页功能栏贴近进度条，二者之间只有固定小间距。
3. 点击封面进入歌词页。
4. 点击歌词文本或空白区域返回封面页。
5. 连续点击左下角播放模式按钮，确认依次看到随机、单曲循环、列表循环三种状态。
6. 在随机状态点击下一首，确认队列行为符合随机化后的顺序。
7. 从随机切到单曲后，确认图标和状态不再显示随机，当前歌曲结束或手动下一首行为符合单曲循环语义。

如果没有设备或模拟器，完成结论必须明确说明运行态验收未执行。

## 验收标准

本次完成必须满足：

- 封面页功能栏不再悬在页面中部，而是贴近底部进度条。
- 歌词页点击返回封面行为保持已修复状态，相关 focused test 通过。
- 播放模式按钮能进入随机播放状态。
- 播放模式按钮按 RN 顺序循环：随机、单曲循环、列表循环。
- 退出随机后不会留下 UI 与队列状态不一致的问题。
- 相关单元测试通过。
- Debug 构建通过。

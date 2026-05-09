# Settings Basic Alignment Design

> 文档状态：当前规范
> 适用范围：设置中“基本设置”对齐 RN 原版的第一轮设计与后续实现计划输入。
> 直接执行：是（作为实现计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 参考来源：`../MusicFree/src/pages/setting/index.tsx`、`../MusicFree/src/pages/setting/settingTypes/basicSetting.tsx`、`../MusicFree/src/types/core/config.d.ts`、当前 Android `:feature:settings` / `:data` / `:downloader` / `:feature:player-ui` 实现。
> 最后校验：2026-05-09

## Summary

本设计将 Android 设置页的信息架构对齐 RN 原版 `setting type` 语义，并优先补齐“基本设置”的可见功能面。

第一轮采用：

- typed settings route：`basic / plugin / theme / backup / about`。
- `basic` 页面按 RN 分区完整呈现，但 UI 使用 Android 卡片分组。
- 可操作项只开放当前 Android 已有运行态消费点，或实现阶段能明确接入的低风险项。
- RN 中尚未接入 Android 运行态的项显示为禁用，并标注“待接入”。
- 偏好存储继续使用现有 DataStore `AppPreferences`，不引入 MMKV。

## Confirmed Decisions

1. 第一轮展示 RN 基本设置的全部分区，但未接入运行态的设置项禁用展示。
2. 设置导航采用 typed route，对齐 RN `setting` 页的 `type` 参数，而不是保留单一设置总览页。
3. 基本设置页使用 Android 卡片分组，降低逐像素 RN 还原度，换取和当前 Android UI 的一致性及较小改动面。
4. 偏好存储使用现有 DataStore `AppPreferences`。不引入 MMKV，也不直接复刻 RN 的 MMKV `App.config` 存储实现。
5. 禁用项第一轮不写 DataStore，避免提前污染偏好 schema。
6. 后续设计、文档和实现工作在 worktree `.worktrees/feat-settings-basic-alignment` 中进行，避免主工作区冲突影响。

## Current Facts

### RN Reference

RN 原版设置页通过 `../MusicFree/src/pages/setting/index.tsx` 根据 route param `type` 选择设置类型：

- `basic`：基本设置
- `plugin`：插件管理
- `theme`：主题设置
- `backup`：备份与恢复
- `about`：关于

基本设置实现位于 `../MusicFree/src/pages/setting/settingTypes/basicSetting.tsx`，分区顺序为：

1. 常规
2. 歌单&专辑
3. 插件
4. 播放
5. 下载
6. 网络
7. 歌词
8. 缓存
9. 开发选项

RN 的偏好 key 类型定义位于 `../MusicFree/src/types/core/config.d.ts`。其中基本设置使用点号 key，例如 `basic.maxDownload`、`basic.defaultPlayQuality`、`lyric.autoSearchLyric`、`debug.errorLog`。

RN 运行态消费示例：

- `basic.clickMusicInSearch` 影响搜索结果点击行为。
- `basic.clickMusicInAlbum` 影响歌单/专辑单曲点击行为。
- `basic.defaultPlayQuality` 和 `basic.playQualityOrder` 影响播放取源质量回退。
- `basic.useCelluarNetworkPlay` 禁止移动网络播放非本地源。
- `basic.useCelluarNetworkDownload` 禁止移动网络下载。
- `basic.notInterrupt`、`basic.tempRemoteDuck`、`basic.tempRemoteDuckVolume` 影响音频打断策略。
- `basic.autoUpdatePlugin`、`basic.notCheckPluginVersion`、`basic.lazyLoadPlugin` 影响插件管理与启动流程。
- `lyric.autoSearchLyric` 控制缺歌词时自动搜索。

### Android Current State

当前 Android 设置实现集中在：

- `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsScreen.kt`
- `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/SettingsViewModel.kt`
- `feature/settings/src/main/java/com/zili/android/musicfreeandroid/feature/settings/navigation/SettingsNavigation.kt`
- `core/src/main/java/com/zili/android/musicfreeandroid/core/navigation/Routes.kt`

当前 `SettingsRoute` 是单一 `data object`，`SettingsScreen` 同时承载设置入口卡片、权限入口、存储目录和下载设置。它不区分 `basic / plugin / theme / backup / about`。

当前可确认已有运行态消费点：

- `data/src/main/java/com/zili/android/musicfreeandroid/data/datastore/AppPreferences.kt`
  - 已有 `maxDownload`
  - 已有 `useCellularDownload`
  - 已有 `defaultDownloadQuality`
  - 已有 `downloadDirRelative`
  - 已有 `lyricAutoSearchEnabled`
- `downloader/src/main/java/com/zili/android/musicfreeandroid/downloader/prefs/DownloadConfigSource.kt`
  - 消费下载偏好。
- `feature/player-ui/src/main/java/com/zili/android/musicfreeandroid/feature/playerui/lyrics/PlayerLyricLoader.kt`
  - 消费 `lyricAutoSearchEnabled`。

`MusicFreeScreenScaffold` 和 `MusicFreeTopAppBar` 已是普通设置类页面的 UI Harness 入口，必须继续使用。

## Non-Goals

本轮不实现：

- Android 桌面歌词/悬浮窗。
- 插件自动更新运行态。
- 插件懒加载运行态。
- 播放打断策略和 duck 音量策略。
- 播放失败自动换源。
- 移动网络播放拦截。
- 缓存文件统计与清理。
- 错误日志/详细日志/调试面板系统。
- 主题、备份、关于页的完整 RN 对齐。
- MMKV 接入或 DataStore 到 MMKV 的迁移。

这些能力可以在 Basic 页可见，但第一轮禁用并标注待接入。

## Navigation Design

### Route Model

将 `SettingsRoute` 从单一 object 调整为 typed route：

```kotlin
@Serializable
data class SettingsRoute(
    val type: SettingsType = SettingsType.Basic,
)

@Serializable
enum class SettingsType {
    Basic,
    Plugin,
    Theme,
    Backup,
    About,
}
```

实现阶段可根据现有 serialization 约束调整 enum 位置或 wire name，但行为语义保持不变。

### Drawer Mapping

首页抽屉入口映射：

| 抽屉入口 | Android route |
|---|---|
| 基础设置 | `SettingsRoute(SettingsType.Basic)` |
| 插件管理 | `SettingsRoute(SettingsType.Plugin)` |
| 主题设置 | `SettingsRoute(SettingsType.Theme)` |
| 备份与恢复 | `SettingsRoute(SettingsType.Backup)` |
| 关于 MusicFree | `SettingsRoute(SettingsType.About)` |

第一轮重点是 `Basic`。其他 type 页面可以保留现有入口卡片或占位锚点，但不应再混入 Basic 页。`SettingsType.Plugin` 页面承载现有 `PluginManagementEntry`，点击后进入既有 `PluginListRoute`，以保留当前插件列表实现和首页导航测试锚点。

### Screen Chrome

所有 settings type 页面使用 `MusicFreeScreenScaffold`：

- Basic 标题：`基本设置`
- Plugin 标题：`插件管理`
- Theme 标题：`主题设置`
- Backup 标题：`备份与恢复`
- About 标题：`关于 MusicFree`

这满足 `docs/ui-harness/screen-chrome-rules.md` 对普通 AppBar 页面的要求。

## Basic Settings UI

### Overall Structure

新增或拆分出 `BasicSettingsScreen` / `BasicSettingsContent`。外层负责收集 ViewModel state，内层为 stateless composable，便于测试。

布局：

- `MusicFreeScreenScaffold(title = "基本设置")`
- `LazyColumn`
- 每个 RN 分区一个卡片
- 卡片内使用紧凑设置行
- 颜色、字体、间距使用 `MusicFreeTheme`、`FontSizes`、`rpx`

不使用 RN 的横向 section index 条。用户已选择 Android 卡片分组方向。

### Reusable Rows

建议建立设置页内部通用行组件，先放在 `:feature:settings`，后续多页面复用再上移：

- `SettingSwitchRow`
  - 布尔设置，右侧 `Switch`
  - enabled 时点击整行切换
  - disabled 时降低透明度，不响应点击
- `SettingValueRow`
  - 枚举或数字设置，右侧显示当前值
  - enabled 时点击打开单选 dialog
  - disabled 时右侧显示当前默认值或 `待接入`
- `SettingActionRow`
  - 行为项，例如查看日志、清空缓存
  - 第一轮多为 disabled
- `SettingSectionCard`
  - 分区标题 + 行列表

可编辑枚举使用 `AlertDialog + RadioButton`，对齐 RN 的 `RadioDialog` 语义，也比 segmented button 更适合多选项设置。

### Enabled Items In First Round

第一轮明确可编辑：

| 分区 | 设置项 | Android key/source | UI |
|---|---|---|---|
| 下载 | 最大同时下载数目 | `AppPreferences.maxDownload` | 单选 `[1, 3, 5, 7]` |
| 下载 | 默认下载音质 | `AppPreferences.defaultDownloadQuality` | 单选 `低音质 / 标准音质 / 高音质 / 超高音质` |
| 网络 | 使用移动网络下载 | `AppPreferences.useCellularDownload` | Switch |
| 歌词 | 歌词缺失时自动搜索歌词 | `AppPreferences.lyricAutoSearchEnabled` | Switch |

`maxDownload` 当前 DataStore clamp 是 `1..10`。UI 第一轮按 RN 候选 `[1,3,5,7]` 展示；底层 clamp 保持不变，兼容已有下载设计。

### Disabled Items In First Round

以下 RN 设置项第一轮展示但禁用：

| 分区 | RN 设置项 | 第一轮展示 |
|---|---|---|
| 常规 | 历史记录最多保存条数 | disabled，右侧默认 `50` 或 `待接入` |
| 常规 | 打开歌曲详情页时 | disabled，右侧 `默认展示歌曲封面` |
| 常规 | 处于歌曲详情页时常亮 | disabled switch |
| 常规 | 关联歌词方式 | disabled，右侧 `搜索歌词` |
| 常规 | 通知栏显示关闭按钮 | disabled switch |
| 歌单&专辑 | 点击搜索结果内单曲时 | disabled |
| 歌单&专辑 | 点击专辑内单曲时 | disabled |
| 歌单&专辑 | 新建歌单时默认歌曲排序 | disabled |
| 插件 | 软件启动时自动更新插件 | disabled switch |
| 插件 | 安装插件时不校验版本 | disabled switch |
| 插件 | 启用插件懒加载 | disabled switch |
| 播放 | 允许与其他应用同时播放 | disabled switch |
| 播放 | 软件启动时自动播放歌曲 | disabled switch |
| 播放 | 播放失败时尝试更换音源 | disabled switch |
| 播放 | 播放失败时自动暂停 | disabled switch |
| 播放 | 播放被暂时打断时 | disabled |
| 播放 | 音量降低幅度 | disabled，仅在未来 duck 策略开放后展示条件项 |
| 播放 | 默认播放音质 | disabled 或只显示现有 `playQuality` 当前值，除非实现阶段确认可安全接入播放取源 |
| 播放 | 默认播放音质缺失时 | disabled |
| 下载 | 下载路径 | disabled 或继续使用现有“存储目录”入口，避免误导为 RN 绝对路径 |
| 下载 | 默认下载音质缺失时 | disabled |
| 网络 | 使用移动网络播放 | disabled |
| 歌词 | 开启桌面歌词 | disabled |
| 歌词 | 桌面歌词位置/宽度/字体/颜色 | disabled，不展示 slider 细项或折叠为待接入说明 |
| 缓存 | 音乐缓存上限 | disabled |
| 缓存 | 清除音乐/歌词/图片缓存 | disabled action |
| 开发选项 | 错误日志/详细日志/调试面板 | disabled switch |
| 开发选项 | 查看错误日志/清空日志 | disabled action |

禁用项不写入 DataStore。

### Existing Storage And Permissions Entries

当前 Settings 页中的权限管理、存储目录入口不属于 RN BasicSetting 的核心分区。

第一轮处理：

- 权限管理继续通过抽屉“权限管理”进入 `PermissionsRoute`。
- 存储目录如仍需要 settings 入口，可放在 `Download` 分区下，文案明确为 Android 存储目录，不伪装为 RN `downloadPath`。
- `onNavigateToLocalFileSelector` 当前未实际分化，本轮不扩大它的职责。

## Data Design

继续使用 `AppPreferences`。

已有 key 保持：

- `max_download`
- `use_cellular_download`
- `default_download_quality`
- `download_dir_relative`
- `lyric_auto_search_enabled`

新增 key 原则：

- 只有在本轮真正开放编辑并接入运行态时新增。
- 命名使用 Android 现有 snake_case 风格。
- RN key 映射写在文档和测试名中，不直接使用点号 key 作为 DataStore key。
- enum 读取使用 `runCatching { valueOf(...) }` 或等价防御，非法值回退默认。

ViewModel 建议输出一个聚合 state：

```kotlin
data class BasicSettingsUiState(
    val maxDownload: Int,
    val defaultDownloadQuality: PlayQuality,
    val useCellularDownload: Boolean,
    val lyricAutoSearchEnabled: Boolean,
    val storageAccessState: StorageAccessState,
)
```

setter 只暴露已启用设置：

- `setMaxDownload(value: Int)`
- `setDefaultDownloadQuality(value: PlayQuality)`
- `setUseCellularDownload(value: Boolean)`
- `setLyricAutoSearchEnabled(value: Boolean)`

## Error Handling

- DataStore 读取非法 enum：回退默认值，不让 UI 崩溃。
- 设置写入失败：现有 DataStore setter 是 suspend 写入，第一轮可以沿用 fire-and-forget；若后续需要 Toast，再引入事件流。
- 禁用行点击：无副作用，不打开 dialog，不写日志。
- 文件/缓存/日志类操作：第一轮禁用，不提供伪实现。

## Testing Design

### Unit Tests

`data/src/test/.../AppPreferencesTest.kt`：

- 保留已有下载和歌词偏好测试。
- 若实现阶段新增启用项，再补默认值、写入、非法值回退测试。
- 禁用项不测试持久化，因为不写 DataStore。

`feature/settings/src/test/.../SettingsViewModelTest.kt`：

- Basic UI state 默认值。
- `setMaxDownload` 写入 DataStore。
- `setDefaultDownloadQuality` 写入 DataStore。
- `setUseCellularDownload` 写入 DataStore。
- `setLyricAutoSearchEnabled` 写入 DataStore。

### Compose Tests

新增 Basic content 测试：

- RN 分区标题都存在。
- 已启用设置可点击或可切换。
- 禁用设置存在，显示 `待接入` 或默认值，且不可点击。
- 单选 dialog 显示候选项并触发回调。

### Navigation Tests

更新 `app/src/androidTest/java/com/zili/android/musicfreeandroid/HomeEntryNavigationTest.kt`：

- 抽屉“基础设置”进入 settings basic root。
- 插件/主题/备份/关于入口进入对应 type 或保留现有 anchor，测试与实现策略一致。

新增 anchors 建议：

- `FidelityAnchors.Screen.SettingsRoot`：任意 settings type 根。
- `FidelityAnchors.Settings.BasicRoot`
- `FidelityAnchors.Settings.BasicSectionCommon`
- `FidelityAnchors.Settings.BasicSectionDownload`
- `FidelityAnchors.Settings.BasicSectionNetwork`
- `FidelityAnchors.Settings.BasicSectionLyric`
- `FidelityAnchors.Settings.PluginManagementEntry` 等现有 anchor 继续保留。

### Build Gate

设计阶段基线已在 worktree 运行：

```bash
./gradlew :feature:settings:testDebugUnitTest
```

结果：通过。

实现收尾默认验证：

```bash
./gradlew :feature:settings:testDebugUnitTest
./gradlew :app:assembleDebug
```

有设备/模拟器时再运行受影响的 app androidTest。

## Open Risks

1. Typed `SettingsRoute` 会影响现有序列化路由测试，需要同步更新。
2. 当前抽屉中多个设置入口都导航到同一个 `SettingsRoute`，改成 typed route 后 app 层导航签名要一起调整。
3. `SettingsScreen.kt` 当前已承担入口卡片和下载设置，拆分 Basic 页时要保留既有 plugin/theme/backup/about anchors，避免破坏首页 fidelity 测试。
4. 禁用项文案必须克制，避免用户误以为功能已经生效。
5. `defaultPlayQuality` 在 Android 已有 `playQuality` 偏好，但是否等价于 RN 的默认播放音质需要实现阶段核对播放取源链路，本设计暂不默认开放。

## Acceptance Criteria

- 从抽屉点击“基础设置”打开 `基本设置` 页面。
- 页面使用普通 AppBar harness，状态栏和标题符合 UI Harness 规则。
- Basic 页按 RN 分区完整展示。
- 下载最大并发、默认下载音质、移动网络下载、歌词自动搜索可操作并持久化。
- 未接入项禁用展示且不写入 DataStore。
- 插件、主题、备份、关于入口仍可从抽屉到达对应 settings type 或现有目标。
- `:feature:settings:testDebugUnitTest` 和 `:app:assembleDebug` 通过。

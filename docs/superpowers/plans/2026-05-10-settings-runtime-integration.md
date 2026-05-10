# 基本设置运行态接入执行计划

> 文档状态：历史执行快照
> 适用范围：2026-05-10 基本设置运行态接入实现。
> 直接执行：否（执行完成后仅作回溯）
> 当前规范：`../specs/2026-05-10-settings-runtime-integration-design.md`

## 约束

- 在 `.worktrees/feat-settings-runtime` 中实现。
- 先补测试，再写生产代码。
- UI 修改遵守 `docs/dev-harness/ui/rules.md`，播放器修改遵守 `docs/dev-harness/player/rules.md`，插件修改遵守 `docs/dev-harness/plugin/rules.md`，测试修改遵守 `docs/dev-harness/test/rules.md`。
- 文档引用使用相对路径。

## 步骤

1. 新增核心设置模型和偏好持久化。
   - `core`: 点击行为、播放详情默认页、音质回退方向模型。
   - `data`: `AppPreferences` 新增 flow/setter，并让搜索历史裁剪读取 `maxHistoryLen`。
   - 测试：`AppPreferencesTest` / `DownloadPreferencesKeysTest`。

2. 接入设置页状态和 UI。
   - `SettingsViewModel`: 聚合新增偏好并提供 setter。
   - `BasicSettingsContent`: 将可运行态项改为 value row / switch row。
   - 保留暂不开放项 disabled。
   - 测试：`SettingsViewModelTest` / `BasicSettingsContentTest`。

3. 接入播放点击行为。
   - `SearchViewModel.resolveAndPlay`: 按 `clickMusicInSearch` 选择单曲播放或替换队列。
   - `PluginSheetDetailViewModel`、`TopListDetailViewModel`、`AlbumDetailViewModel`、`ArtistDetailViewModel`: 按 `clickMusicInAlbum` 选择单曲播放或替换列表。
   - 测试：对应 ViewModel 测试。

4. 接入播放详情页默认页与常亮。
   - `PlayerViewModel`: 暴露 `musicDetailDefault` / `musicDetailAwake`。
   - `PlayerScreen`: 初始页只在首次进入时读取偏好；常亮用 `DisposableEffect` 管理 window flag。
   - 测试：`PlayerViewModelTest`，必要时补 Compose 轻量测试。

5. 接入播放/下载音质回退和蜂窝播放拦截。
   - `MediaSourceResolver`: 支持未指定音质场景。
   - `PluginMediaSourceService`: 未指定音质时按偏好生成候选顺序。
   - `QualityFallback`: 支持 `QualityFallbackOrder`。
   - `DownloadConfig`: 增加下载回退方向。
   - `PlayerController`: 播放远程曲目前检查移动网络策略。
   - 测试：`PluginMediaSourceServiceTest`、`QualityFallbackTest`、`PlayerController` 单测。

6. 收口验证。
   - 先跑受影响模块单测。
   - 最后跑 `:app:assembleDebug`。
   - 若出现 harness 相关失败，先按对应 rules/incident 定位再改。

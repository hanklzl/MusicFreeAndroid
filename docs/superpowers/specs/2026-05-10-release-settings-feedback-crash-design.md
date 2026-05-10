# Release Settings Feedback Crash Design

> 文档状态：当前规范
> 适用范围：Release 包从首页侧栏进入设置页时因反馈日志导出路径校验崩溃的修复。
> 直接执行：是（作为本次实现计划输入）
> 当前入口：[DOCS_STATUS](../../DOCS_STATUS.md)、[AGENTS](../../../AGENTS.md)
> 参考来源：`app/src/main/java/com/zili/android/musicfreeandroid/di/LoggingModule.kt`、`app/src/main/java/com/zili/android/musicfreeandroid/MusicFreeApplication.kt`、`logging/src/main/java/com/zili/android/musicfreeandroid/logging/FeedbackLogExporter.kt`
> 最后校验：2026-05-10

## Summary

Release APK 点击首页侧栏中的设置项会崩溃。运行态 logcat 显示崩溃发生在设置页创建 `SettingsViewModel` 时注入 `FeedbackLogExporter`，异常为 `feedbackDir must be within cacheDir/feedback for secure sharing`。

根因不是 typed navigation/R8。`LoggingConfig.cacheDir` 当前表示 Logan 自身缓存目录（`filesDir/logan-cache`），但 `FeedbackLogExporter` 把它误当成 Android `FileProvider` 的 `<cache-path path="feedback/">` 根目录。实际 `feedbackDir` 是 `context.cacheDir/feedback`，因此构造期安全校验失败，任何需要 `SettingsViewModel` 的设置入口都会崩溃。

## Goals

- 设置页在 minified release 包中可从侧栏 `基础设置`、`插件管理`、`主题设置`、`备份与恢复`、`关于 MusicFree` 进入。
- 保持反馈日志包只能写入 FileProvider 可分享的 `cache/feedback` 目录树。
- 保持 Logan 日志缓存目录仍使用 `filesDir/logan-cache`，不改变 Logan 初始化路径。
- 增加单元测试覆盖 Logan cache 与 Android share cache 分离的生产路径。

## Non-Goals

- 不重构日志系统整体目录结构。
- 不改变反馈日志包内容、文件命名、分享 Intent 或 FileProvider authorities。
- 不调整 settings typed route / `SettingsType` 导航模型。
- 不新增 release 签名机制或 CI workflow。

## Design

在 `LoggingConfig` 中显式区分两个目录：

- `cacheDir`：继续表示 Logan 的缓存目录，供 `LoggingInitializer` 传给 `LoganConfig.setCachePath(...)`。
- `feedbackShareRootDir`：新增字段，表示 Android `FileProvider` `<cache-path>` 的根目录，生产代码传入 `context.cacheDir`。

`FeedbackLogExporter` 的安全校验改为：

```text
feedbackDir must equal or stay below feedbackShareRootDir/feedback
```

这样既允许生产配置 `filesDir/logan-cache` 与 `cacheDir/feedback` 分离，也继续阻止日志包写到 FileProvider 不会授权的任意外部路径。

## Testing

- `logging` 单元测试新增回归用例：`cacheDir = files/logan-cache`、`feedbackShareRootDir = cache`、`feedbackDir = cache/feedback` 时 `FeedbackLogExporter` 可构造并创建日志包。
- 既有非法路径测试继续要求 `feedbackDir` 不在 `feedbackShareRootDir/feedback` 下时抛 `IllegalArgumentException`。
- 本地运行 `./gradlew :logging:testDebugUnitTest --no-daemon` 和 `./gradlew :feature:settings:testDebugUnitTest --no-daemon`。
- 构建并安装 minified release APK，点击五个侧栏设置入口确认不再出现 `AndroidRuntime` 崩溃。


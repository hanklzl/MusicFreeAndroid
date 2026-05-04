# GitHub Actions Debug APK Design

> 文档状态：当前规范
> 适用范围：仅适用于新增提交触发的 GitHub Actions Debug APK 打包能力，以及 Debug 包名后缀配置。
> 直接执行：是
> 最后校验：2026-05-04

## 背景

仓库当前没有 `.github/workflows/` 工作流。项目构建基线是 Gradle Wrapper `9.4.1`、AGP `9.2.0`、Kotlin `2.3.21`，Kotlin JVM toolchain 使用 JDK 21，Android 字节码目标为 Java 17。

本次目标是做最小可用的自动打包：有提交推送到 GitHub 后，自动构建 Debug APK，并把 APK 作为 workflow artifact 提供下载。同时 Debug 包需要使用独立 `applicationId`，以便和正式包共存。

## 目标

- 每次 `push` 自动触发 Debug APK 构建。
- 保留 `workflow_dispatch`，允许手动触发同一套构建。
- 产出 `:app:assembleDebug` 的 APK artifact。
- Debug 包名为 `com.zili.android.musicfreeandroid.debug`。

## 不在本次范围

- 不配置 release 签名。
- 不构建 release APK。
- 不新增 GitHub Release 发布流程。
- 不把 lint、单元测试或仪器测试加入第一版 workflow。
- 不改 Gradle wrapper 下载源。

## 设计

新增 `.github/workflows/android-debug-apk.yml`，包含一个 `build-debug-apk` job：

- Runner：`ubuntu-latest`。
- 权限：`contents: read`。
- 触发：`push` 和 `workflow_dispatch`。
- 步骤：
  1. `actions/checkout@v6` checkout 仓库。
  2. `actions/setup-java@v5` 安装 Temurin JDK 21。
  3. `gradle/actions/setup-gradle@v5` 配置 Gradle 缓存和 wrapper 校验。
  4. `android-actions/setup-android@v3` 准备 Android SDK。
  5. 执行 `./gradlew :app:assembleDebug --no-daemon`。
  6. `actions/upload-artifact@v7` 上传 `app/build/outputs/apk/debug/*.apk`。

在 `app/build.gradle.kts` 的 `debug` build type 中设置：

```kotlin
applicationIdSuffix = ".debug"
```

由于 `defaultConfig.applicationId` 是 `com.zili.android.musicfreeandroid`，Debug APK 最终 `applicationId` 为 `com.zili.android.musicfreeandroid.debug`。

## 风险与处理

- Gradle wrapper 当前使用 Aliyun 镜像 URL。第一版 workflow 不修改该 URL；如果 GitHub-hosted runner 拉取失败，再单独评估是否恢复官方 `services.gradle.org` URL。
- workflow 只验证 Debug 构建，不代表 release 可发布。release 签名和发布链路后续独立设计。
- artifact 保留周期使用 GitHub 默认值，避免第一版引入额外策略。

## 验收

- `app/build.gradle.kts` 中 Debug build type 设置 `applicationIdSuffix = ".debug"`。
- `.github/workflows/android-debug-apk.yml` 存在，并能在 `push` 时触发。
- workflow 命令使用 `./gradlew :app:assembleDebug --no-daemon`。
- workflow 成功后可在 GitHub Actions run 中下载 Debug APK artifact。
- 本地至少运行 `./gradlew :app:assembleDebug --no-daemon`，确认 Gradle 配置仍可构建。

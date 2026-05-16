# GitHub Actions Release APK Signing Design

> 文档状态：当前规范
> 适用范围：仅适用于侧载 Release APK 的签名、GitHub Actions 构建、GitHub Release 发布与内部验包 artifact。
> 直接执行：是（作为实现计划输入）
> 最后校验：2026-05-05

## 背景

仓库当前已有 Debug APK workflow：[GitHub Actions Debug APK Design](./2026-05-04-github-actions-debug-apk-design.md)。Debug workflow 只运行 `:app:assembleDebug` 并上传 Debug APK artifact，不涉及 release 签名。

当前 `:app` 模块的 `release` build type 已开启 R8 与资源压缩，但没有 signingConfig。侧载 APK 不经过 Google Play App Signing；用于 CI 签名的 release key 就是用户设备上接受后续覆盖安装的真实证书。因此 release signing secret 的风险等级高于 Google Play upload key。

本设计目标是在 GitHub Actions 中构建签名 Release APK，同时保证 keystore 文件、密码和 alias 不进入源码工程。

## 目标

- 通过 GitHub Actions 构建可侧载安装的签名 Release APK。
- 使用 GitHub `release` Environment secrets 保存 release signing material。
- 源码中只保存 Gradle 与 workflow 的读取逻辑，不保存 `.jks`、密码、alias、`keystore.properties` 或 base64 keystore 内容。
- `v*` tag 构建正式 Release APK 并上传到 GitHub Release。
- `workflow_dispatch` 构建同一类签名 Release APK，仅上传 Actions artifact，用于内部验包。
- 版本号由源码维护，即 `versionCode` 和 `versionName` 仍在 Gradle 配置中显式提交变更。
- 保留现有 Debug APK workflow 行为。

## 不在本次范围

- 不接入 Google Play、F-Droid 或其他应用商店发布。
- 不自动改写 `versionCode` 或 `versionName`。
- 不生成 AAB。
- 不实现 key rotation 自动化。
- 不迁移现有 Debug APK workflow。
- 不新增 UI 或运行态业务功能。

## Secret 模型

在 GitHub 仓库创建名为 `release` 的 Environment，并在该 Environment 中配置以下 secrets：

| Secret | 用途 |
|---|---|
| `ANDROID_RELEASE_KEYSTORE_BASE64` | release keystore 文件的 base64 文本 |
| `ANDROID_RELEASE_STORE_PASSWORD` | keystore 密码 |
| `ANDROID_RELEASE_KEY_ALIAS` | release key alias |
| `ANDROID_RELEASE_KEY_PASSWORD` | release key 密码 |
| `LOGAN_AES_KEY` | Release Logan 日志 AES key |
| `LOGAN_AES_IV` | Release Logan 日志 AES IV |

Environment 应配置 required reviewers，并启用 Prevent self-review。deployment branches/tags 至少允许 `v*` tag；如果需要从默认分支手动触发内部验包，也应允许默认分支或受保护分支访问该 Environment。

不使用 repository-level secrets 存放 release key，避免普通 workflow job 更容易取到签名材料。组织级 secrets 也不作为第一版方案，除非后续多个仓库需要共享同一套发布凭据。

## Gradle 设计

`app/build.gradle.kts` 增加 `release` signingConfig，从环境变量读取：

- `ANDROID_RELEASE_KEYSTORE_PATH`
- `ANDROID_RELEASE_STORE_PASSWORD`
- `ANDROID_RELEASE_KEY_ALIAS`
- `ANDROID_RELEASE_KEY_PASSWORD`

`release` build type 挂载该 signingConfig，并保留现有配置：

- `isMinifyEnabled = true`
- `isShrinkResources = true`
- `proguardFiles(...)`

本地或 CI 缺少任一签名环境变量时，`assembleRelease` 应快速失败并输出明确错误。Debug 构建不读取这些环境变量，也不受 release signing 配置影响。

CI workflow 在 runner 上将 `ANDROID_RELEASE_KEYSTORE_BASE64` 解码到 `$RUNNER_TEMP/release.jks`，再通过 `ANDROID_RELEASE_KEYSTORE_PATH` 指向该临时文件。keystore 文件不写入仓库路径，也不上传 artifact。

## GitHub Actions 设计

新增 `.github/workflows/android-release-apk.yml`。

触发：

- `push.tags: ["v*"]`
- `workflow_dispatch`

基础 job：

- Runner：`ubuntu-latest`
- Environment：`release`
- 默认权限：`contents: read`
- 工具链：沿用 Debug workflow 的 JDK 21、Gradle setup、Android SDK setup
- 构建命令：`./gradlew :app:assembleRelease --no-daemon`

签名准备步骤：

1. 校验四个 release signing secrets 和两个 Logan release secrets 都存在。
2. 将 `ANDROID_RELEASE_KEYSTORE_BASE64` 通过 base64 解码到 `$RUNNER_TEMP/release.jks`。
3. 通过环境变量向 Gradle 暴露 keystore path、store password、key alias、key password、Logan key 和 Logan IV。

产物命名：

- tag 构建：`MusicFreeAndroid-${GITHUB_REF_NAME}.apk`
- 手动构建：`MusicFreeAndroid-manual-${GITHUB_RUN_NUMBER}.apk`

产物流向：

- `workflow_dispatch`：使用 `actions/upload-artifact` 上传 APK，artifact retention 建议为 14 天。
- `v*` tag：使用 GitHub CLI 将 APK 上传到同名 GitHub Release。该发布步骤单独授予 `contents: write`，其他构建步骤保持 `contents: read`。

## 安全约束

- workflow 不响应 `pull_request`，也不使用 `pull_request_target`。
- 不打印 secret，不输出 keystore 内容，不把密码写入 Gradle 文件、properties 文件或 artifact。
- 避免把签名密码作为命令行参数传递；使用环境变量供 Gradle 读取。
- release job 必须引用 `release` Environment，确保 Environment 保护规则通过前无法访问 environment secrets。
- `GITHUB_TOKEN` 默认最小权限；只有 GitHub Release 上传步骤需要写权限。
- 第一版尽量使用 GitHub 官方 actions 与 runner 预装的 `gh` CLI，减少第三方 action 暴露面。
- 后续可单独评估将所有 actions 固定到完整 commit SHA，以进一步降低供应链风险。

如果出现以下情况，应立即停止发布、删除含敏感信息的日志、重新生成 keystore 并评估已发布版本的更新路径：

- workflow 日志出现未脱敏的密码或 keystore 内容。
- keystore 明文或 base64 内容被提交到仓库。
- `release` Environment 被错误开放给不可信分支、tag 或人员。
- release signing key 被复制到不可信设备或存储位置。

## 版本策略

采用源码驱动版本号：

- 发布前通过普通代码提交更新 `versionCode` 和 `versionName`。
- CI 不从 tag 反推 `versionName`。
- CI 不使用 GitHub run number 自动生成 `versionCode`。

侧载 APK 覆盖安装依赖 Android package manager 规则：相同 `applicationId`、相同 signing certificate、且新 APK `versionCode` 高于已安装 release APK。发布检查必须覆盖该路径。

## 验收

- `./gradlew :app:assembleDebug --no-daemon` 不受影响。
- 不设置 release signing 环境变量时，`./gradlew :app:assembleRelease --no-daemon` 失败且错误信息指向缺少签名环境变量。
- 配置 `release` Environment secrets 后，`workflow_dispatch` 能产出签名 Release APK artifact。
- 推送 `v*` tag 后，workflow 能构建签名 Release APK 并上传到同名 GitHub Release。
- 下载 APK 后，使用 `apksigner verify --verbose --print-certs` 验证签名存在。
- 设备验证：旧 release APK 能被更高 `versionCode` 的新 release APK 覆盖安装。
- Debug 包 `com.hank.musicfree.debug` 仍能与 release 包 `com.hank.musicfree` 共存。

## 参考

- [GitHub Actions Debug APK Design](./2026-05-04-github-actions-debug-apk-design.md)
- [GitHub Docs: Using secrets in GitHub Actions](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)
- [GitHub Docs: Deployments and environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments)
- [GitHub Docs: Secure use reference](https://docs.github.com/en/actions/reference/security/secure-use)
- [Android Developers: Sign your app](https://developer.android.com/studio/publish/app-signing)

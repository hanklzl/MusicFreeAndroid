# README 设计

> 文档状态：当前规范
> 适用范围：仓库根目录 `README.md` 创建。
> 直接执行：是（作为实现计划输入）
> 最后校验：2026-05-05

## 背景

仓库根目录当前没有 `README.md`。项目事实主要分散在 `docs/DOCS_STATUS.md`、`AGENTS.md`、Gradle 配置、GitHub Actions workflow 和导航代码中。新增 README 的目标是给第一次进入仓库的读者一个稳定入口，同时避免重复维护完整项目规范。

README 使用中文，面向普通读者和开发者。它需要明确说明 MusicFreeAndroid 是 `../MusicFree` 的 Android 原生重写版，仍处于开发中，应用本身不内置音源，搜索、播放、歌单、歌词等能力依赖用户安装插件。

## 目标

- 创建仓库根目录 `README.md`。
- 以“WIP 项目主页 + 开发上手”的方式介绍项目。
- 给普通读者说明项目定位、功能范围、插件依赖和合规边界。
- 给开发者提供环境要求、模块架构、常用构建测试命令、文档入口和主要开发约束。
- 使用相对链接指向当前规范文档，避免 README 成为第二份容易过期的完整规范。

## 非目标

- 不新增截图、徽章或营销型内容。
- 不创建英文 README。
- 不修改代码、Gradle 配置、CI 配置或项目版本。
- 不把 `docs/superpowers/plans/*.md` 作为当前执行说明引用。
- 不写入本地绝对路径。

## README 内容结构

README 应包含以下一级或二级章节：

1. 项目简介
2. 当前状态
3. 功能范围
4. 技术栈
5. 模块架构
6. 快速开始
7. 常用构建与测试命令
8. 插件系统说明
9. 文档入口
10. 开发约束
11. CI 与发布
12. 免责声明 / 合规说明

结构可以在实现时做小幅措辞调整，但应保留以上信息覆盖面。

## 内容事实

README 可写入以下当前稳定事实：

- 项目目标：使用 Kotlin、Jetpack Compose、QuickJS 复刻 MusicFree 的插件化播放器能力与主要交互体验。
- 构建基线：Min SDK 29、Target SDK 36、compileSdk 36.1、Java compatibility `VERSION_17`、JVM toolchain JDK 21、Gradle Wrapper `9.4.1`、AGP `9.2.0`、Kotlin `2.3.21`、Compose BOM `2026.04.01`。
- 模块依赖方向：`:app -> :feature:* -> :data, :player, :plugin -> :core`。
- 当前模块：`:app`、`:core`、`:data`、`:player`、`:plugin`、`:feature:home`、`:feature:player-ui`、`:feature:search`、`:feature:settings`。
- 本地常用验证：`./gradlew :app:assembleDebug`、`./gradlew test`、`./gradlew lint`、`./gradlew connectedAndroidTest`。
- Release 构建需要签名环境变量；普通本地功能收尾默认验证 Debug 构建。
- CI 包含 Debug APK workflow 和 Release APK workflow。
- 当前状态应标记为开发中，并说明缺失页面和细节状态以 `docs/DOCS_STATUS.md`、`AGENTS.md` 与代码为准。

## 链接策略

- 文档入口链接到 `docs/DOCS_STATUS.md`、`AGENTS.md`、`docs/ui-harness/screen-chrome-rules.md`。
- RN 原版参考使用相对路径 `../MusicFree`。
- 工作流或规范引用必须使用相对路径。
- 不引用本地绝对路径。

## 验收

- 根目录存在 `README.md`。
- README 使用中文，且显式说明项目仍处于开发中。
- README 覆盖普通读者与开发者两个入口场景。
- README 中所有本仓库文档链接均为相对路径。
- README 不包含本地绝对路径。
- README 不把 Release 签名环境变量缺失描述为普通 Debug 构建阻塞项。
- 纯文档变更不要求运行 Android 构建；验收以内容检查和链接检查为主。

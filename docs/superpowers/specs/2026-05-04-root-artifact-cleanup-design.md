# Root Artifact Cleanup Design

> 文档状态：当前规范
> 适用范围：仅适用于删除仓库根目录的 `commonMain/`、`androidMain/`、`screenshots/`。
> 直接执行：是
> 最后校验：2026-05-04

## 背景

本次清理只处理三个仓库根目录下的历史内容：`commonMain/`、`androidMain/`、`screenshots/`。主工作区已有与本任务无关的 Gradle 文件改动，因此清理必须在 `.worktrees/cleanup-root-artifacts` 独立 worktree 内完成。

## 现状判断

- `commonMain/` 和 `androidMain/` 是位于仓库根目录的 `androidx.navigation.compose` 源码拷贝，不在任何 Gradle module 的 `src/` 目录下。
- `settings.gradle.kts` 只 include 当前 Android modules，没有 include 根目录 `commonMain/` 或 `androidMain/`。
- Gradle build scripts 没有配置根目录 `commonMain/` 或 `androidMain/` source set。
- `screenshots/` 是早期首页对比截图；当前首页证据目录是 `docs/home-fidelity/homepage/`。
- 对 `commonMain`、`androidMain`、`screenshots` 的引用只出现在历史计划或目录自身，不构成当前执行依赖。

## 决策

删除 `commonMain/`、`androidMain/`、`screenshots/`，不迁移、不归档。理由是这些目录不参与构建、不属于当前文档入口定义的证据目录，也不应继续作为顶层工程内容保留。

## 验收

- 删除范围只包含 `commonMain/`、`androidMain/`、`screenshots/`。
- 删除后 `git status --short` 仅显示上述目录删除，以及本次设计/计划文档。
- 删除后执行 `./gradlew :app:build`，构建结果必须与删除前基线一致为成功。
- 不修改主工作区已有的 Gradle wrapper/JVM 改动。

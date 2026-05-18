---
layout: page
title: 贡献
permalink: /contributing/
---

# 面向开发者

本页适合想从源码构建、提交 PR、改进文档的开发者。普通用户请看 [下载页](download.html)。

## 快速开始

```bash
# 克隆
git clone https://github.com/hanklzl/MusicFreeAndroid.git
cd MusicFreeAndroid

# 构建 Debug APK
./gradlew :app:assembleDebug

# 单元测试
./gradlew test
```

### 环境要求

- JDK 21
- Android SDK Platform 36
- Gradle Wrapper 9.4.1（自动下载，无需手装）
- minSdk 29 / targetSdk 36

## 项目架构概览

依赖方向单向：

```
:app → :feature:* → :data, :player, :plugin → :core
```

| 模块 | 一句话职责 |
|---|---|
| `:core` | 主题、导航路由、基础模型、通用工具 |
| `:data` | Room、DataStore、Repository |
| `:player` | Media3 / ExoPlayer、MediaSessionService、PlayerController |
| `:plugin` | QuickJS 引擎、JS 桥接、插件管理 |
| `:feature:*` | 首页、播放器、搜索、设置等具体功能 |
| `:app` | NavHost、跨模块编排、应用入口 |

## 详细文档

- **仓库开发约束、Dev Harness、模块详细职责**：[AGENTS.md](https://github.com/hanklzl/MusicFreeAndroid/blob/main/AGENTS.md)
- **发布流程与 secrets**：[RELEASE.md](https://github.com/hanklzl/MusicFreeAndroid/blob/main/RELEASE.md)
- **设计 spec**：[docs/superpowers/specs/](https://github.com/hanklzl/MusicFreeAndroid/tree/main/docs/superpowers/specs)
- **历史决策与踩坑**：[docs/dev-harness/](https://github.com/hanklzl/MusicFreeAndroid/tree/main/docs/dev-harness)

## 如何提 PR

1. Fork 仓库并新建特性分支
2. 修改本地，跑 `./gradlew test` 通过
3. 提 PR 到 `main` 分支
4. 遵循 [conventional commits](https://www.conventionalcommits.org/)（中文 commit message 即可）
5. 涉及 UI / 插件 / 播放器 / 测试相关改动时，先阅读对应 [`docs/dev-harness/<area>/rules.md`](https://github.com/hanklzl/MusicFreeAndroid/tree/main/docs/dev-harness)

## 反馈 BUG

[新建 Issue](https://github.com/hanklzl/MusicFreeAndroid/issues/new) 时请附：

- 设备型号、Android 版本、应用版本
- 复现步骤
- 日志（「设置 → 关于 → 导出日志」）

## 开源协议

本项目使用 [AGPL-3.0](https://github.com/hanklzl/MusicFreeAndroid/blob/main/LICENSE)，由 AGPL-3.0 的上游 [MusicFree](https://github.com/maotoumao/MusicFree) 衍生。

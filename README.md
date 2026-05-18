# MusicFreeAndroid

[![CI](https://github.com/hanklzl/MusicFreeAndroid/actions/workflows/android-release-apk.yml/badge.svg)](https://github.com/hanklzl/MusicFreeAndroid/actions/workflows/android-release-apk.yml)
[![Release](https://img.shields.io/github/v/release/hanklzl/MusicFreeAndroid)](https://github.com/hanklzl/MusicFreeAndroid/releases/latest)
[![License](https://img.shields.io/badge/license-AGPL--3.0-blue)](LICENSE)
![MinSDK](https://img.shields.io/badge/minSdk-29-brightgreen)
![Kotlin](https://img.shields.io/badge/kotlin-2.3-purple)
![Compose](https://img.shields.io/badge/compose-2026.04-blue)

> MusicFree 的 Android 原生重写版。无内置音源，通过用户安装的插件实现搜索、播放、歌单、歌词等能力。

## 项目状态

当前版本 v1.1.0，CI 持续构建中。完整变更见 [CHANGELOG.md](CHANGELOG.md)。

## 立即下载

- 官网：<https://hanklzl.github.io/MusicFreeAndroid/>（按 ABI 自动推荐 APK）
- 或直接到 [Releases](https://github.com/hanklzl/MusicFreeAndroid/releases/latest) 手动选 `arm64-v8a` / `x86_64`

## 功能简介

- 🧩 **插件市场**：本地文件 / 订阅 URL 两种安装方式
- 🔍 **多源搜索**：跨插件统一搜索结果
- 🎧 **后台播放**：基于 Media3 / ExoPlayer，锁屏控制 + 通知栏卡片
- 📋 **歌单管理**：本地歌单创建、导入导出、跨插件聚合
- 📝 **歌词同步**：滚动跟随、偏移微调、跨源关联
- 🌗 **主题**：亮色 / 暗色 / 自定义
- 💾 **本地音乐**：扫描设备文件，与插件源混合播放
- ⏱ **历史与收藏**：自动记录播放历史，可收藏单曲与歌单

## 复刻自 MusicFree

本项目是 [MusicFree](https://github.com/maotoumao/MusicFree)（React Native 版）的 Android 原生重写，目标对齐原版交互与插件生态。感谢上游作者 [@maotoumao](https://github.com/maotoumao)。

## 技术栈

Kotlin · Jetpack Compose + Material3 · Media3 · QuickJS · Room · Hilt · Coroutines · Navigation Compose

详细基线见 [AGENTS.md](AGENTS.md)。

## 面向开发者

- 环境：JDK 21、Android SDK 36、Gradle Wrapper 自动下载
- 一行命令构建 Debug：`./gradlew :app:assembleDebug`
- 详细开发约束、模块架构、Dev Harness：[AGENTS.md](AGENTS.md)
- 发布流程与 secrets 配置：[RELEASE.md](RELEASE.md)

## 开源协议

本项目使用 [GNU Affero General Public License v3.0](LICENSE)。本项目衍生自同样使用 AGPL-3.0 的 [MusicFree](https://github.com/maotoumao/MusicFree)，按协议要求保持同样许可。

Copyright (C) 2026 MusicFreeAndroid Contributors

## 免责声明

本应用本身不内置任何音源。搜索、播放、歌单、歌词等能力依赖用户安装的第三方插件提供，本项目对插件来源、安全性与合法性不做担保。

不得以 VIP、破解、绕过付费等表述宣传本项目。

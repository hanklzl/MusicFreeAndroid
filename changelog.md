---
layout: page
title: 变更日志
permalink: /changelog/
---

# 变更日志

本页摘录最近 3 个版本要点。**完整变更与所有历史版本以 [GitHub Releases](https://github.com/hanklzl/MusicFreeAndroid/releases) 为准**。

---

## v1.1.0（2026-05-16）

### 新功能

- feat(player): 冷启动恢复上次播放进度并支持继续播放

### 修复

- fix(player-ui): 修复播放器插件源标签垂直对齐

### 重构

- refactor(app): 应用包名重命名为 `com.hank.musicfree`

> ⚠️ 老版本（v1.0.x）客户端首次升级到 v1.1.0 时无法自动识别新包名，请前往 [下载页](download.html) 或 [GitHub Releases](https://github.com/hanklzl/MusicFreeAndroid/releases/tag/v1.1.0) 手动下载对应 ABI 的 APK 安装。

## v1.0.3（2026-05-17）

### 新功能

- feat(backup): 实现迁移备份恢复

### 修复

- fix(playlist): 添加歌曲默认插入歌单首部
- fix(player-ui): 修复播放器文字布局细节

## v1.0.2（2026-05-16）

### 新功能

- feat(release): 分 ABI APK 发布与更新链路改造
- feat(theme): 实现 setCustomTheme 与主题设置子页
- feat(listen-stats): 修复封面、跨插件归并、本地时区三项问题
- feat(playlist): 支持歌单批量修改
- feat(player): 补齐播放详情更多动作

### 修复

- fix(home): 清理专辑详情标题空值处理
- fix(plugin): 把网络下载移出 PluginManager mutex，并修正默认插件 bootstrap 重复下载
- fix(home): 对齐歌单详情整体滚动

---

[查看全部历史版本 →](https://github.com/hanklzl/MusicFreeAndroid/releases)

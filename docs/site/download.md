---
layout: page
title: 下载
permalink: /download/
---

# 下载

<p id="version-info" style="font-size: 1.1em; color: #555;">版本信息加载中…</p>

## 选择适合你的 APK

| ABI | 适合设备 | 下载 |
|---|---|---|
| **arm64-v8a** | 2017 年后绝大多数手机 | <a id="dl-arm64" href="https://github.com/hanklzl/MusicFreeAndroid/releases/latest">下载 arm64 APK</a> |
| **x86_64** | 模拟器、少数 Intel/AMD 平板 | <a id="dl-x86_64" href="https://github.com/hanklzl/MusicFreeAndroid/releases/latest">下载 x86_64 APK</a> |

> 不确定？大多数手机用 **arm64-v8a**。

## 安装步骤

1. 下载 APK 后在文件管理器或下载通知里打开
2. 系统提示「来自此来源的应用」→ **允许**（可能需要先到「设置 → 应用 → 浏览器/文件管理器 → 安装未知应用」开关）
3. 安装完成后打开应用
4. 首次启动建议先到「设置 → 插件管理」安装一个插件，否则没有音源

## 验证 APK 完整性

每个发行版本的 SHA256 列在 `release/version.json`，可与下载的 APK 对照：

```bash
# 命令行校验
shasum -a 256 MusicFreeAndroid-v1.1.0-arm64-v8a.apk

# 期望值（取自 release/version.json）
curl -s https://hanklzl.github.io/MusicFreeAndroid/release/version.json | jq -r '.variants["arm64-v8a"].sha256'
```

两个 SHA256 应完全相同。

## 历史版本

所有版本归档在 [GitHub Releases](https://github.com/hanklzl/MusicFreeAndroid/releases)。

<script src="{{ '/assets/version.js' | relative_url }}" defer></script>

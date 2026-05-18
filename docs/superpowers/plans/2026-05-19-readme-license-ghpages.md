# README/LICENSE/GitHub Pages 站点 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 README 改为用户优先、新增 AGPL-3.0 LICENSE、上线 GitHub Pages 6 页站点，全程保护现网应用更新链路（`release/version.json`）不被 site sync 覆盖。

**Architecture:** main 分支管理源（`docs/site/`），通过新增 `deploy-site.yml` workflow rsync 到 `gh-pages`（`--exclude=release/`），Jekyll minima 主题渲染。站点本身无构建步骤。两个写 `gh-pages` 的 workflow 共用 `concurrency: gh-pages-deploy` 串行。

**Tech Stack:** Jekyll (minima theme，GitHub Pages 内置) · GitHub Actions · rsync · 浏览器原生 fetch (无 JS 依赖)

**对 Spec 的偏离（重要）：** 关于 LICENSE，spec §4 说"开头 copyright 行修改为「Copyright (C) 2026 MusicFreeAndroid Contributors」"。本计划改为 **保留 AGPL-3.0 文本完全 verbatim 不动一字**，理由：AGPL 文本开头"Copyright (C) 2007 Free Software Foundation"是 FSF 对**许可证文本**的版权声明，并紧跟一句"changing it is not allowed"。修改它本身违反 AGPL。项目自身的 copyright 声明放在 README「开源协议」节即可。这是上游 MusicFree 的做法（[../MusicFree/LICENSE](../../../../MusicFree/LICENSE)）。

---

## File Structure

| 路径 | 操作 | 说明 |
|---|---|---|
| `LICENSE` | 新增 | AGPL-3.0 verbatim（来源 https://www.gnu.org/licenses/agpl-3.0.txt）|
| `README.md` | 重写 | 用户优先；删除「文档入口」「开发约束」「CI 与发布」「Release 环境变量」四节 |
| `docs/site/_config.yml` | 新增 | Jekyll minima 主题配置 |
| `docs/site/index.md` | 新增 | 首页 |
| `docs/site/download.md` | 新增 | 下载/安装 + 动态版本注入 hooks |
| `docs/site/features.md` | 新增 | 特性介绍（纯文字 + emoji） |
| `docs/site/plugins.md` | 新增 | 插件说明 + FAQ + 故障排查 |
| `docs/site/changelog.md` | 新增 | 最近 3 版本摘录 + 链 Releases |
| `docs/site/contributing.md` | 新增 | 开发者入口（指向 AGENTS.md / RELEASE.md） |
| `docs/site/assets/version.js` | 新增 | ~30 行 fetch /release/version.json 填充按钮 |
| `.github/workflows/deploy-site.yml` | 新增 | site sync workflow（`--exclude=release/` 是硬约束） |
| `.github/workflows/android-release-apk.yml` | 修改 | `concurrency.group` 改为 `gh-pages-deploy` |

---

## Task 0: 准备 worktree

**Files:** 工作区准备，无文件改动

- [ ] **Step 1: 在仓库根创建 worktree**

```bash
cd /Users/zili/code/android/MusicFreeAndroid
git worktree add .worktrees/readme-ghpages -b readme-ghpages main
cd .worktrees/readme-ghpages
```

- [ ] **Step 2: 确认 worktree 干净 + 在新分支**

```bash
git status
git rev-parse --abbrev-ref HEAD
```

Expected: `On branch readme-ghpages` / `nothing to commit, working tree clean`

后续所有 Task 在 `.worktrees/readme-ghpages` 工作目录执行。

---

## Task 1: 新增 LICENSE（AGPL-3.0 verbatim）

**Files:**
- Create: `LICENSE`

- [ ] **Step 1: 下载 AGPL-3.0 标准文本**

```bash
curl -fsSL https://www.gnu.org/licenses/agpl-3.0.txt -o LICENSE
```

- [ ] **Step 2: 验证文件大小与首尾**

```bash
wc -l LICENSE
head -3 LICENSE
tail -3 LICENSE
```

Expected: 行数约 661 行；首行包含 `GNU AFFERO GENERAL PUBLIC LICENSE`；末行约 `<https://www.gnu.org/licenses/why-not-lgpl.html>.` 之后空行。

- [ ] **Step 3: 与上游 MusicFree LICENSE 对比 SHA**

```bash
shasum -a 256 LICENSE
shasum -a 256 ../../../MusicFree/LICENSE
```

Expected: 两个 SHA256 完全相同。若不同，停下检查上游是否做了非法改动或本地下载是否被代理污染。

- [ ] **Step 4: 提交**

```bash
git add LICENSE
git commit -m "docs: 新增 AGPL-3.0 LICENSE"
```

---

## Task 2: 重写 README.md

**Files:**
- Modify (full rewrite): `README.md`

- [ ] **Step 1: 用以下内容覆盖 README.md**

```markdown
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
```

- [ ] **Step 2: 验证 markdown 链接相对路径正确**

```bash
test -f LICENSE && echo "LICENSE OK"
test -f AGENTS.md && echo "AGENTS.md OK"
test -f RELEASE.md && echo "RELEASE.md OK"
test -f CHANGELOG.md && echo "CHANGELOG.md OK"
```

Expected: 四个 OK。

- [ ] **Step 3: 提交**

```bash
git add README.md
git commit -m "docs: 重写 README，改为用户优先

删除已迁移到 AGENTS.md / RELEASE.md 的「文档入口」「开发约束」
「CI 与发布」「Release 环境变量」四节，新增徽章、立即下载、
功能简介、开源协议与免责声明，面向终端用户。"
```

---

## Task 3: 创建 Jekyll 站点配置 + 首页

**Files:**
- Create: `docs/site/_config.yml`
- Create: `docs/site/index.md`

- [ ] **Step 1: 创建 `docs/site/_config.yml`**

```yaml
title: MusicFreeAndroid
description: MusicFree 的 Android 原生重写版，插件化、无内置音源
theme: minima
baseurl: "/MusicFreeAndroid"
url: "https://hanklzl.github.io"
lang: zh-CN

plugins:
  - jekyll-seo-tag

header_pages:
  - download.md
  - features.md
  - plugins.md
  - changelog.md
  - contributing.md

minima:
  skin: auto
  social_links:
    - { platform: github, user_url: "https://github.com/hanklzl/MusicFreeAndroid" }

exclude:
  - README.md
```

- [ ] **Step 2: 创建 `docs/site/index.md`**

```markdown
---
layout: home
title: MusicFreeAndroid
---

# 🎵 MusicFreeAndroid

> 插件化、无内置音源的 Android 原生音乐播放器

[**立即下载**](download.html){: .btn .btn-primary }
[GitHub](https://github.com/hanklzl/MusicFreeAndroid){: .btn }
[上游 MusicFree](https://github.com/maotoumao/MusicFree){: .btn }

## 它是什么

MusicFreeAndroid 是 [MusicFree](https://github.com/maotoumao/MusicFree)（React Native 版）的 Android 原生重写版本，用 Kotlin + Jetpack Compose 实现。

播放器本身**不内置任何音源**，所有搜索 / 播放 / 歌单 / 歌词能力都由用户安装的第三方插件提供。这意味着你完全掌控自己的音乐来源。

## 为什么选它

| | |
|---|---|
| 🧩 **插件化** | 多源切换，能力随插件演进 |
| 🎧 **后台播放** | Media3 / ExoPlayer 原生体验 |
| 📝 **歌词同步** | 滚动跟随、偏移微调、跨源关联 |
| 🌗 **主题** | 亮色 / 暗色 / 自定义 |

## 当前状态

- **v1.1.0** · CI 持续构建中 · 长期迭代
- 完整变更见 [CHANGELOG](changelog.html) 或 [GitHub Releases](https://github.com/hanklzl/MusicFreeAndroid/releases)

## 致谢与开源

基于 [AGPL-3.0](https://github.com/hanklzl/MusicFreeAndroid/blob/main/LICENSE)。感谢上游作者 [@maotoumao](https://github.com/maotoumao) 与 MusicFree 社区。
```

- [ ] **Step 3: 提交**

```bash
git add docs/site/_config.yml docs/site/index.md
git commit -m "docs(site): 新增 Jekyll 站点配置与首页"
```

---

## Task 4: 下载页 + version.js

**Files:**
- Create: `docs/site/download.md`
- Create: `docs/site/assets/version.js`

- [ ] **Step 1: 创建 `docs/site/download.md`**

```markdown
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
```

- [ ] **Step 2: 创建 `docs/site/assets/version.js`**

```javascript
// 从同源 release/version.json 拉最新版本号 + 各 ABI 下载链接
(async () => {
  const placeholder = document.getElementById('version-info');
  const arm = document.getElementById('dl-arm64');
  const x64 = document.getElementById('dl-x86_64');
  try {
    const r = await fetch('/MusicFreeAndroid/release/version.json', { cache: 'no-store' });
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    const v = await r.json();
    if (placeholder) {
      const date = new Date(v.releasedAt).toLocaleDateString('zh-CN');
      placeholder.textContent = `最新版本 v${v.version}（${date}）`;
    }
    if (arm && v.variants?.['arm64-v8a']?.download?.[0]) {
      arm.href = v.variants['arm64-v8a'].download[0];
      arm.textContent = `下载 arm64 APK · v${v.version}`;
    }
    if (x64 && v.variants?.['x86_64']?.download?.[0]) {
      x64.href = v.variants['x86_64'].download[0];
      x64.textContent = `下载 x86_64 APK · v${v.version}`;
    }
  } catch (e) {
    if (placeholder) {
      placeholder.textContent = '无法获取最新版本信息，请直接到 GitHub Releases 选择对应 ABI。';
    }
    console.warn('version.json 加载失败', e);
  }
})();
```

- [ ] **Step 3: 验证 JS 语法（node 解析即可）**

```bash
node --check docs/site/assets/version.js && echo "JS syntax OK"
```

Expected: `JS syntax OK`

- [ ] **Step 4: 提交**

```bash
git add docs/site/download.md docs/site/assets/version.js
git commit -m "docs(site): 新增下载页与 version.js 动态版本注入"
```

---

## Task 5: 特性介绍页

**Files:**
- Create: `docs/site/features.md`

- [ ] **Step 1: 创建 `docs/site/features.md`**

```markdown
---
layout: page
title: 特性
permalink: /features/
---

# 特性介绍

## 🧩 插件市场

- 本地文件安装：选择 `.js` 插件文件直接载入
- 订阅 URL 安装：粘贴 URL 一次性导入多个插件，订阅源更新时可一键升级
- 插件启用 / 禁用 / 排序：搜索结果按当前排序展示
- 插件元信息：版本、作者、官方仓库链接
- 沙箱执行：基于 QuickJS，插件无法访问本地文件系统

## 🔍 多源搜索

- 单次输入搜索全部启用的插件
- 结果按插件分组展示
- 支持单曲、专辑、歌单、作者四类聚合
- 网络出错时单插件失败不影响其他源
- 历史搜索记录与清除

## 🎧 播放体验

- 基于 Media3 / ExoPlayer，与系统媒体框架原生集成
- 锁屏控制、通知栏卡片、车载蓝牙控制
- 后台播放，可加入电池优化白名单防止被杀
- 冷启动恢复上次播放进度
- 倍速播放、音质切换（插件支持时）

## 📋 歌单管理

- 本地歌单创建、改名、删除
- 跨插件聚合：同一歌单可包含来自不同插件的歌曲
- 批量编辑：多选移动 / 删除 / 复制到其他歌单
- 导入 / 导出 / 备份恢复

## 📝 歌词

- 自动加载插件歌词
- 滚动跟随 + 点击跳转
- 偏移微调（毫秒级）
- 跨源关联：当前插件歌词不准时，可绑定其他源歌词

## 🌗 主题与视觉

- 亮色 / 暗色 / 跟随系统
- 自定义主题色
- 沉浸式状态栏
- 响应式尺寸（与原版 RN 适配公式一致）

## 与上游 RN MusicFree 的关系

- **对齐目标**：交互流程、插件协议、数据结构与上游一致
- **目前差异**：缺 `downloading`、`setCustomTheme` 两个页面
- **不打算追的部分**：RN 周边脚手架、跨平台桥接代码

## 路线图

短期与长期优先事项（与 [AGENTS.md](https://github.com/hanklzl/MusicFreeAndroid/blob/main/AGENTS.md) 一致）：

1. 补齐 `downloading`、`setCustomTheme` 两个页面
2. 强化 `topListDetail` / `pluginSheetDetail` / `musicDetail` 详情链路运行态验收
3. 加强插件安装 → 搜索 → 播放 → 队列/状态一致性的端到端验证
4. 持续治理文档，避免历史规范被误用
```

- [ ] **Step 2: 提交**

```bash
git add docs/site/features.md
git commit -m "docs(site): 新增特性介绍页"
```

---

## Task 6: 插件说明 + FAQ + 故障排查

**Files:**
- Create: `docs/site/plugins.md`

- [ ] **Step 1: 创建 `docs/site/plugins.md`**

```markdown
---
layout: page
title: 插件与 FAQ
permalink: /plugins/
---

# 插件说明与常见问题

## 什么是插件

MusicFreeAndroid 本身**不内置任何音源**，所有搜索、播放、歌单、歌词、推荐能力都由第三方编写的插件提供。播放器只是一个**容器**与**沙箱**。

## 如何获取插件

> ⚠️ 本项目不提供也不维护任何官方插件源。请从你信任的渠道自行获取，并自行承担风险。

获取插件后，两种安装方式：

1. **本地文件**：将 `.js` 文件保存到设备，在「设置 → 插件管理 → 安装」选择文件
2. **订阅 URL**：在「插件管理 → 订阅」粘贴 URL，订阅源更新时可一键拉取所有插件最新版

## 沙箱与权限

- 插件运行在 **QuickJS** 沙箱内，不是 V8 / JavaScriptCore
- 沙箱可访问：网络请求（HTTP/HTTPS）、`require()` 注入的工具（cheerio / crypto-js / dayjs / axios / qs / he / big-integer）
- 沙箱**不能**访问：本地文件系统、设备信息、其他应用数据

## 免责声明

本项目仅提供播放器与插件运行容器，不对第三方插件的功能、合法性、安全性、内容质量负责。请确认所用插件来源合规。

---

# 常见问题与故障排查

## 装不上怎么办？

- 检查「未知来源应用安装」是否开启（系统设置 → 应用 → 你用的浏览器/文件管理器 → 安装未知应用）
- 检查下载的 APK 是否对应你的设备 ABI（参考 [下载页](download.html)）
- 旧版本残留：先卸载老版本再装新版本

## 启动闪退？

- 反馈渠道：[GitHub Issues](https://github.com/hanklzl/MusicFreeAndroid/issues)
- 反馈时请附：设备型号、Android 版本、应用版本、复现步骤
- 日志：「设置 → 关于 → 导出日志」（默认保留最近 7 天）

## 插件不生效？

- 在「设置 → 插件管理」确认插件已**启用**（开关绿色）
- 检查插件作者公告，某些服务可能被服务方限流或调整接口
- 同一搜索词在不同插件下结果差异是正常的

## 后台播放被系统杀掉？

- 「系统设置 → 电池 → 应用启动管理 / 后台耗电」中将本应用加入白名单
- 部分厂商 ROM（如小米、华为、OPPO）需要手动允许「自启动」「关联启动」「后台运行」
- 锁屏后通知栏播放卡片消失通常是被系统清理

## 歌词不准 / 不显示？

- 「全屏播放器 → 歌词面板 → 长按 → 关联其他来源」可手动绑定其他插件的歌词
- 「歌词偏移」可微调时间轴（按毫秒）
- 部分插件不返回歌词，属于该插件能力问题

## 找不到某些功能？

- 「下载管理」「自定义主题」目前仍在补齐中
- 完整路线图见 [特性页](features.html)

## 我可以贡献代码 / 翻译 / 反馈 BUG 吗？

可以，详见 [贡献页](contributing.html)。
```

- [ ] **Step 2: 提交**

```bash
git add docs/site/plugins.md
git commit -m "docs(site): 新增插件说明 + FAQ + 故障排查页"
```

---

## Task 7: 变更日志页

**Files:**
- Create: `docs/site/changelog.md`

- [ ] **Step 1: 查看当前 CHANGELOG.md 前 3 个版本**

```bash
head -60 CHANGELOG.md
```

记录前 3 个版本号与主要变更，用于填充下一步内容。

- [ ] **Step 2: 创建 `docs/site/changelog.md`，按上一步看到的内容填写**

模板（按实际 CHANGELOG 节选 3 段填入）：

```markdown
---
layout: page
title: 变更日志
permalink: /changelog/
---

# 变更日志

最近 3 个版本摘录如下。**完整变更与所有历史版本以 [GitHub Releases](https://github.com/hanklzl/MusicFreeAndroid/releases) 为准**。

---

## v1.1.0（2026-05-16）

（按 CHANGELOG.md 实际内容填）

## v1.0.3（2026-05-16）

（按 CHANGELOG.md 实际内容填）

## v1.0.2（2026-05-16）

（按 CHANGELOG.md 实际内容填）

---

[查看全部历史版本 →](https://github.com/hanklzl/MusicFreeAndroid/releases)
```

> **执行注意**：本页"前 3 个版本"是手维内容，每次发版后需手动更新；与 GitHub Releases 不自动同步。Spec §8 已记录后续可自动化此项。

- [ ] **Step 3: 提交**

```bash
git add docs/site/changelog.md
git commit -m "docs(site): 新增变更日志页"
```

---

## Task 8: 贡献页

**Files:**
- Create: `docs/site/contributing.md`

- [ ] **Step 1: 创建 `docs/site/contributing.md`**

```markdown
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
```

- [ ] **Step 2: 提交**

```bash
git add docs/site/contributing.md
git commit -m "docs(site): 新增开发者贡献页"
```

---

## Task 9: 站点部署 workflow

**Files:**
- Create: `.github/workflows/deploy-site.yml`

- [ ] **Step 1: 创建 `.github/workflows/deploy-site.yml`**

```yaml
name: Deploy Site

on:
  push:
    branches: [main]
    paths:
      - 'docs/site/**'
      - '.github/workflows/deploy-site.yml'
  workflow_dispatch:

permissions:
  contents: write

concurrency:
  group: gh-pages-deploy
  cancel-in-progress: false

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout main
        uses: actions/checkout@v6

      - name: Checkout gh-pages
        uses: actions/checkout@v6
        with:
          ref: gh-pages
          path: _ghpages

      - name: Sync site to gh-pages (preserve release/)
        run: |
          rsync -a --delete \
            --exclude='release/' \
            --exclude='.git/' \
            docs/site/ _ghpages/

      - name: Commit and push
        working-directory: _ghpages
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          if [ -z "$(git status --porcelain)" ]; then
            echo "No changes to deploy."
            exit 0
          fi
          git add -A
          git commit -m "deploy site from ${GITHUB_SHA::7}"
          git push origin gh-pages
```

- [ ] **Step 2: YAML 语法校验（如本机装有 yq 或 python yaml）**

```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/deploy-site.yml'))" && echo "YAML OK"
```

Expected: `YAML OK`

- [ ] **Step 3: 提交**

```bash
git add .github/workflows/deploy-site.yml
git commit -m "ci: 新增 deploy-site workflow

将 docs/site/ 同步到 gh-pages 分支由 Jekyll 渲染。
--exclude=release/ 是硬约束，保护应用内更新链路依赖的
release/version.json 不被覆盖。"
```

---

## Task 10: 修改 release workflow concurrency

**Files:**
- Modify: `.github/workflows/android-release-apk.yml` (lines 14-16)

- [ ] **Step 1: 查看现状**

```bash
sed -n '14,16p' .github/workflows/android-release-apk.yml
```

Expected output:
```
concurrency:
  group: android-release-apk-${{ github.ref }}
  cancel-in-progress: false
```

- [ ] **Step 2: 修改 `group` 值**

将 `android-release-apk-${{ github.ref }}` 改为 `gh-pages-deploy`。修改后的 14-16 行应是：

```yaml
concurrency:
  group: gh-pages-deploy
  cancel-in-progress: false
```

- [ ] **Step 3: 验证**

```bash
sed -n '14,16p' .github/workflows/android-release-apk.yml
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/android-release-apk.yml'))" && echo "YAML OK"
```

Expected: 显示新的 group 值；YAML OK

- [ ] **Step 4: 提交**

```bash
git add .github/workflows/android-release-apk.yml
git commit -m "ci(release): 与 deploy-site workflow 共用 gh-pages-deploy concurrency

两个 workflow 都写 gh-pages 分支，必须串行避免 race
导致 release/version.json 被覆盖或站点内容丢失。"
```

---

## Task 11: 合并到 main（squash）

**Files:** 无文件改动

- [ ] **Step 1: 确认当前分支 commits**

```bash
git log --oneline main..readme-ghpages
```

Expected: 9 个 commits（Task 1-10 各一个，Task 11 不产生 commit）

- [ ] **Step 2: 切回主工作区 main**

```bash
cd /Users/zili/code/android/MusicFreeAndroid
git checkout main
git pull --ff-only
```

- [ ] **Step 3: squash merge**

```bash
git merge --squash readme-ghpages
git status
```

Expected: 文件全部 staged。

- [ ] **Step 4: 创建合并 commit（中文，conventional commits）**

```bash
git commit -m "docs: 重写 README，新增 LICENSE 与 GitHub Pages 站点

- README 改为用户优先，删除 4 节已迁移到 AGENTS.md / RELEASE.md 的内容
- 仓库根新增 AGPL-3.0 LICENSE（verbatim，与上游 MusicFree 一致）
- docs/site/ 新增 6 页 Jekyll 站点（首页 / 下载 / 特性 / 插件 FAQ / CHANGELOG / 贡献）
- 新增 deploy-site workflow 同步到 gh-pages（--exclude=release/ 保护更新链路）
- release workflow concurrency group 改为 gh-pages-deploy 与站点部署串行

Spec: docs/superpowers/specs/2026-05-19-readme-license-ghpages-design.md"
```

- [ ] **Step 5: push 到 origin/main**

```bash
git push origin main
```

- [ ] **Step 6: 清理 worktree（merge 后不再需要）**

```bash
git worktree remove .worktrees/readme-ghpages
git branch -D readme-ghpages
```

---

## Task 12: 首次部署人工验收

**Files:** 无文件改动

- [ ] **Step 1: 在 GitHub Actions 页面确认 deploy-site workflow 触发**

打开 <https://github.com/hanklzl/MusicFreeAndroid/actions/workflows/deploy-site.yml>，确认刚刚的 push 触发了 workflow 并 ✅ 成功。

- [ ] **Step 2: 验证 release/version.json 仍存在且未变**

```bash
curl -fsSL https://hanklzl.github.io/MusicFreeAndroid/release/version.json | jq .version
```

Expected: 输出 `"1.1.0"`（或当前最新版本）。**这一步是硬约束验收：site sync 不能误删 release/。**

- [ ] **Step 3: 验证站点 6 页全部可访问（HTTP 200）**

```bash
for path in / /download/ /features/ /plugins/ /changelog/ /contributing/; do
  printf "%-20s " "$path"
  curl -fsS -o /dev/null -w "%{http_code}\n" "https://hanklzl.github.io/MusicFreeAndroid$path"
done
```

Expected: 全部 200。**注意 GitHub Pages 首次发布有 1-2 分钟 CDN 缓存延迟**，如果是 404 等 2 分钟再试。

- [ ] **Step 4: 验证下载按钮 JS 注入正常**

在浏览器打开 <https://hanklzl.github.io/MusicFreeAndroid/download/>，打开 DevTools Network 标签：

- `version.js` 应 200 加载
- `release/version.json` 应 200 加载
- 页面顶部「版本信息加载中…」应在 1 秒内变成 `最新版本 v1.1.0（YYYY/MM/DD）`
- arm64 / x86_64 两个按钮链接应指向具体版本 APK URL（hover 查看），而不是 `releases/latest`

如果 JS 失败兜底正确：页面顶部应显示「无法获取最新版本信息…」，按钮仍指向 `releases/latest`，仍可下载。

- [ ] **Step 5: 触发一次 release workflow 回归（可选但推荐）**

手动触发或等下一次 nightly：<https://github.com/hanklzl/MusicFreeAndroid/actions/workflows/android-release-apk.yml>

跑完后再次执行 Step 2 命令验证 `release/version.json` 仍可访问且版本号正确（确认两个 workflow 互不覆盖）。

- [ ] **Step 6: README 徽章验收**

打开 <https://github.com/hanklzl/MusicFreeAndroid>，确认顶部 6 个徽章（CI / Release / License / MinSDK / Kotlin / Compose）全部渲染、点击跳转正确。

- [ ] **Step 7: 完成**

无后续 commit 任务。本计划全部完成。

---

## 验收 checklist

- [ ] LICENSE 文件存在，SHA256 与上游 MusicFree LICENSE 一致
- [ ] README 显示新的 6 个徽章 + 用户优先章节
- [ ] 站点 6 页全部 200 可访问
- [ ] 下载页 JS 注入版本号 + 直链
- [ ] `release/version.json` 仍 200 可访问
- [ ] release workflow 与 deploy-site workflow 共用 `concurrency: gh-pages-deploy`
- [ ] 一次 release workflow 跑完后 `release/version.json` 仍正确

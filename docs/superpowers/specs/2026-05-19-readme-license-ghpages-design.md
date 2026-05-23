---
status: 当前规范
type: design
created: 2026-05-19
owner: lzl0120@gmail.com
---

# README / LICENSE / GitHub Pages 站点 设计

## 1. 背景

仓库当前的 `README.md` 写法偏内部开发文档（文档入口、Dev Harness 引用、CI secrets 列表等），对终端用户不友好；仓库根目录无 `LICENSE` 文件，而上游 RN 项目 [MusicFree](../../../../MusicFree/readme.md) 使用 AGPL-3.0；没有面向终端用户的产品站点提供功能介绍与下载入口。

本设计同时解决三件事：

1. 将 `README.md` 改为用户优先，把开发者内容删减到最小入口（指向已存在的 [AGENTS.md](../../../AGENTS.md) 与 [RELEASE.md](../../../RELEASE.md)）。
2. 在仓库根目录新增 `LICENSE`，正式声明 AGPL-3.0。
3. 在 `docs/site/` 新增 Jekyll 默认主题源文件，通过新增的 `deploy-site.yml` workflow 同步到 `gh-pages` 分支并由 GitHub Pages 渲染。站点提供首页、下载、特性、插件 + FAQ、变更日志、贡献入口共 6 页，纯文本 + emoji + 排版，**不包含任何截图或第三方素材**（用户明确要求规避侵权风险）。

## 2. 范围与约束

### 范围内

- 仓库根目录新增 `LICENSE`（AGPL-3.0 全文）。
- 重写 `README.md`（用户优先，删除已迁移到 AGENTS.md/RELEASE.md 的过期或重复内容）。
- 新增 `docs/site/` 目录，含 Jekyll 配置与 6 页内容。
- 新增 `.github/workflows/deploy-site.yml`，将 `docs/site/` 同步到 `gh-pages` 分支。
- 修改 `.github/workflows/android-release-apk.yml`，加入 `concurrency` group，与站点部署 workflow 串行。

### 范围外

- 不引入截图、视频、第三方 logo、专辑封面等可能涉及版权的素材。
- 不引入自定义域名（用默认 `https://hanklzl.github.io/MusicFreeAndroid/`）。
- 不引入中英双语（仅中文）。
- 不修改任何 Kotlin / Gradle 业务代码。
- 不修改 AGENTS.md / RELEASE.md（审计后确认这两个文件已经是开发者向内容的 source of truth，README 反而是 stale fork，所以本次只删 README、不向 AGENTS.md/RELEASE.md 添加任何行）。
- 不重写 `release/version.json` 生成逻辑（仍由 release workflow 维护，本次只确保 site sync 不覆盖它）。
- 不重写 `logan-viewer/` 静态工具发布逻辑（仍由 `logan-viewer-pages.yml` 维护，本次只确保 site sync 不覆盖它）。

### 硬性约束

- **`gh-pages` 分支上的 `release/` 目录绝对不能被站点同步删掉**。`release/version.json` 是应用内更新链路的关键文件，被覆盖会让所有现网用户的更新检测崩溃。site sync 必须 `--exclude=release/`。
- **`gh-pages` 分支上的 `logan-viewer/` 目录绝对不能被站点同步删掉**。该目录由 `logan-viewer-pages.yml` 发布，site sync 必须 `--exclude=logan-viewer/`。
- 三个写 `gh-pages` 的 workflow（release-apk、deploy-site、logan-viewer-pages）必须共用同一个 `concurrency` group，避免 race。
- 文档引用一律使用相对路径，不写绝对路径。
- 提交使用用户 git config 身份，不加 `Co-Authored-By: Claude`。

## 3. README 重写细则

### 3.1 审计：旧 README 内容是否仍准确

| 旧 README 章节 | 现状对比 | 本次处理 |
|---|---|---|
| 「文档入口」节 | [AGENTS.md](../../../AGENTS.md) 已完整覆盖且更准确（含 dev-harness 正确路径） | 直接删 |
| 「开发约束」节（worktree、相对路径、Compose Screen、Room→Flow、RN parity、验收） | AGENTS.md 第 54-59 行 + 数据层小节 + 验收闸门小节全部覆盖且更详细 | 直接删 |
| 「CI 与发布」节（trigger 描述） | [RELEASE.md](../../../RELEASE.md) 是发布流程权威文档 | 直接删，README 改链 RELEASE.md |
| 「Release secrets 列表（4 项）」 | **README 本身写错**：实际 workflow 用 7 个 secrets（漏 `LOGAN_AES_KEY`、`LOGAN_AES_IV`、`ANTHROPIC_API_KEY`）；权威清单在 RELEASE.md 第 11-19 行 | 删 README 错误版本，不向 RELEASE.md 添加（已完整） |
| 「本地 Release 环境变量（4 项）」 | RELEASE.md 第 75-82 行覆盖更全（含 LOGAN） | 直接删 |
| UI Harness 路径引用 `docs/ui-harness/screen-chrome-rules.md` | 该文件是 redirect stub，权威路径 `docs/dev-harness/ui/rules.md` 已在 AGENTS.md 写明 | README 中已无需引用该路径 |

**结论**：本次"迁移"实际是"清理重复 + 移除 README 唯一错误事实"，不需要向 AGENTS.md/RELEASE.md 添加任何内容。

### 3.2 新 README 章节结构

```
# MusicFreeAndroid

[badges 一行] CI · Release · License · MinSDK · Kotlin · Compose

> 一句话介绍：MusicFree 的 Android 原生重写版。无内置音源，
> 通过用户安装的插件实现搜索、播放、歌单、歌词等能力。

## 项目状态
当前版本 v1.1.0，CI 持续构建中。完整变更见 CHANGELOG。

## 立即下载
- 官网（GitHub Pages 链接）· 按 ABI 自动推荐 APK
- 或直接到 [Releases](https://github.com/hanklzl/MusicFreeAndroid/releases/latest)
  手动选 arm64-v8a / x86_64

## 功能简介
（6-8 条用户视角描述，不是模块名：插件市场 / 多源搜索 / 歌单管理 /
 歌词同步 / 后台播放 / 本地音乐 / 历史与收藏 / 主题）

## 复刻自 MusicFree
本项目是 [MusicFree](https://github.com/maotoumao/MusicFree) RN 版的
原生重写，目标对齐原版交互与插件生态，致谢上游。

## 技术栈
Kotlin · Jetpack Compose + Material3 · Media3 · QuickJS ·
Room · Hilt · Coroutines。详细基线见 [AGENTS.md](AGENTS.md)。

## 面向开发者
- 环境：JDK 21、Android SDK 36、Gradle Wrapper 自动下载
- 一行命令构建 Debug：`./gradlew :app:assembleDebug`
- 详细开发约束、模块架构、Dev Harness：[AGENTS.md](AGENTS.md)
- 发布流程与 secrets 配置：[RELEASE.md](RELEASE.md)

## 开源协议
AGPL-3.0；本项目衍生自 AGPL-3.0 的 MusicFree，按协议要求保持同样许可。
完整文本见 [LICENSE](LICENSE)。

## 免责声明
本应用本身不内置任何音源，搜索/播放等能力依赖用户安装的插件提供。
插件来源、安全性与合法性由用户自行确认。
不得以 VIP、破解、绕过付费等表述宣传本项目。
```

### 3.3 Badges

全部使用 [shields.io](https://shields.io)，无需服务器渲染：

| Badge | 链接 |
|---|---|
| CI | `![CI](https://github.com/hanklzl/MusicFreeAndroid/actions/workflows/android-release-apk.yml/badge.svg)` |
| Release | `![Release](https://img.shields.io/github/v/release/hanklzl/MusicFreeAndroid)` |
| License | `![License](https://img.shields.io/badge/license-AGPL--3.0-blue)` |
| MinSDK | `![MinSDK](https://img.shields.io/badge/minSdk-29-brightgreen)` |
| Kotlin | `![Kotlin](https://img.shields.io/badge/kotlin-2.3-purple)` |
| Compose | `![Compose](https://img.shields.io/badge/compose-2026.04-blue)` |

## 4. LICENSE 文件

仓库根目录新增 `LICENSE`，内容为 AGPL-3.0 完整文本（来源：<https://www.gnu.org/licenses/agpl-3.0.txt>），开头 copyright 行修改为：

```
Copyright (C) 2026 MusicFreeAndroid Contributors
```

不修改 AGPL 文本其他任何字符。

## 5. GitHub Pages 站点

### 5.1 源码组织

源文件位于 `main` 分支的 `docs/site/`：

```
docs/site/
├── _config.yml          # Jekyll 配置
├── index.md             # 首页
├── download.md          # 下载/安装
├── features.md          # 特性介绍
├── plugins.md           # 插件说明 + FAQ + 故障排查
├── changelog.md         # 最近版本摘录 + 链 GitHub Releases
├── contributing.md      # 开发者贡献入口
└── assets/
    └── version.js       # 下载页动态拉取 release/version.json
```

`_config.yml`：

```yaml
title: MusicFreeAndroid
description: MusicFree 的 Android 原生重写版
theme: minima
baseurl: "/MusicFreeAndroid"
url: "https://hanklzl.github.io"
plugins:
  - jekyll-seo-tag
header_pages:
  - download.md
  - features.md
  - plugins.md
  - changelog.md
  - contributing.md
```

### 5.2 每页内容大纲

#### `index.md`（首页）

```
🎵 MusicFreeAndroid
一句话标语：插件化、无内置音源的 Android 原生音乐播放器

[立即下载]（大按钮 → /download/）  [GitHub]  [上游 MusicFree]

## 它是什么
2 段：复刻自 RN 版 MusicFree、本地原生重写、播放器仅是容器、所有音源来自插件。

## 为什么选它
4 个卡片（emoji + 标题 + 一句话）：
🧩 插件化 · 多源切换
🎧 Media3 后台播放
📝 实时歌词同步
🎨 主题与暗黑模式

## 当前状态
v1.1.0 / CI 持续构建中 / 长期迭代中

## 致谢与开源
基于 AGPL-3.0 / 上游 MusicFree 链接
```

#### `download.md`（下载/安装）

```
## 选择适合你的 APK
> 自动检测：[版本信息加载中…] ← 由 assets/version.js 填入

| ABI | 适合设备 | 下载 |
|---|---|---|
| arm64-v8a | 2017 年后的绝大多数手机 | [APK 链接（由 JS 填）] |
| x86_64 | 模拟器、少数 Intel/AMD 平板 | [APK 链接（由 JS 填）] |

不确定？大多数手机用 arm64-v8a。

## 安装步骤
1. 下载 APK 后在文件管理器打开
2. 系统提示「来自此来源的应用」→ 允许
3. 安装并打开
4. 首次启动建议先到「设置 → 插件管理」安装一个插件

## 验证 APK 完整性
SHA256 列在 version.json，可与下载文件对照（示例命令）。

## 历史版本
所有版本归档在 [GitHub Releases](https://github.com/hanklzl/MusicFreeAndroid/releases)
```

HTML 端兜底链接：

```html
<a id="dl-arm64" href="https://github.com/hanklzl/MusicFreeAndroid/releases/latest">arm64-v8a APK</a>
<a id="dl-x86_64" href="https://github.com/hanklzl/MusicFreeAndroid/releases/latest">x86_64 APK</a>
```

JS 成功 → 直链到具体版本 APK；JS 失败 → 跳 Releases 页让用户手选。**任何情况下用户都能下到包**。

#### `features.md`（特性介绍 —— 纯文字）

按 6 大类分 H2，每类 3-5 条要点：插件市场 / 多源搜索 / 播放体验 / 歌单管理 / 歌词 / 主题。末尾两节：

- 与上游 RN MusicFree 的关系（对齐目标、当前差异、不打算追的部分）
- 路线图（与 AGENTS.md「当前优先事项」对齐：补 `downloading` / `setCustomTheme`、详情链路验收、端到端验证、文档治理）

#### `plugins.md`（插件说明 + FAQ + 故障排查）

```
## 什么是插件
本项目不提供也不内置任何音源，插件由第三方编写，决定能搜什么/播什么。

## 如何获取插件
重点：明确不提供官方插件源。
- 自行从公开渠道获取（用户自负风险）
- 安装方式：本地文件 / 订阅 URL

## 沙箱与权限
QuickJS 沙箱、网络出口、不能访问本地文件系统等关键事实。

## 免责声明
本项目仅是容器，不对第三方插件内容负责。

## 常见问题
### 装不上？
### 启动闪退？
### 插件不生效？
### 后台播放被杀？
### 歌词不准/不显示？
```

#### `changelog.md`

```
最近版本摘录（从仓库 CHANGELOG.md 取前 3 个版本节段）+ 「完整变更见
GitHub Releases」链接。

> 部署 workflow 不自动同步全文，只在 site 源里手维 3 段；
> 完整、最新永远以 GitHub Releases 为准。
```

#### `contributing.md`

```
本页面向想从源码构建、提 PR、写文档的开发者。

## 快速开始
- 环境：JDK 21 / Android SDK 36 / Gradle Wrapper 自动
- 命令：`./gradlew :app:assembleDebug`

## 项目架构概览
模块依赖单向：`:app → :feature:* → :data, :player, :plugin → :core`
表格列模块名 + 一句话职责（与 AGENTS.md 同步，但只列入门级信息）。

## 详细文档
- 仓库开发约束、Dev Harness、模块详细职责：AGENTS.md
- 发布流程与 secrets：RELEASE.md
- 设计 spec：docs/superpowers/specs/
- 历史决策：docs/dev-harness/incidents/

## 如何提 PR
- fork、新建分支、`./gradlew test` 通过、提 PR
- 遵循 conventional commits（中文）
- 涉及 UI/插件/播放器/测试时按 AGENTS.md 读对应 dev-harness rules
```

### 5.3 下载页动态化脚本

`docs/site/assets/version.js`（约 30 行，无依赖）：

```js
// 加载站点同源的 release/version.json，填充下载按钮与版本信息
(async () => {
  const placeholder = document.getElementById('version-info');
  const arm = document.getElementById('dl-arm64');
  const x64 = document.getElementById('dl-x86_64');
  try {
    const r = await fetch('/MusicFreeAndroid/release/version.json', { cache: 'no-store' });
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    const v = await r.json();
    if (placeholder) {
      placeholder.textContent = `最新版本 v${v.version}（${new Date(v.releasedAt).toLocaleDateString('zh-CN')}）`;
    }
    if (arm && v.variants?.['arm64-v8a']?.download?.[0]) {
      arm.href = v.variants['arm64-v8a'].download[0];
    }
    if (x64 && v.variants?.['x86_64']?.download?.[0]) {
      x64.href = v.variants['x86_64'].download[0];
    }
  } catch (e) {
    if (placeholder) {
      placeholder.textContent = '无法获取最新版本，请直接到 GitHub Releases 选择对应 ABI。';
    }
    console.warn('version.json 加载失败', e);
  }
})();
```

`baseurl` 在 `_config.yml` 锁死为 `/MusicFreeAndroid`，JS 也用同一前缀，避免漂移。

### 5.4 同步 workflow

新增 `.github/workflows/deploy-site.yml`：

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
            --exclude='logan-viewer/' \
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

**关键点**：

- `--exclude='release/'` 是硬性约束，绝对不能去掉，否则会覆盖应用内更新链路依赖的 `release/version.json`。
- `--exclude='logan-viewer/'` 是硬性约束，绝对不能去掉，否则会删除 Logan Viewer 的 GitHub Pages 静态产物。
- 不创建 `.nojekyll`：本设计启用 Jekyll 渲染 `docs/site/` 内容（主题 = minima），`.nojekyll` 会禁用 Jekyll。
- GitHub Pages Source 维持 `Branch: gh-pages` / `/ (root)`，Jekyll 自动从根读取 `_config.yml`。

### 5.5 release workflow 改造

GitHub Actions 一个 workflow 只能声明一个 `concurrency`。现有 `.github/workflows/android-release-apk.yml` 用的是 `android-release-apk-${{ github.ref }}`（防同 tag 重复触发）；本次需要把它替换为 `gh-pages-deploy`，与 site / Logan Viewer workflow 共用，确保任意时刻只有一个 workflow 在写 `gh-pages` 分支：

```yaml
concurrency:
  group: gh-pages-deploy
  cancel-in-progress: false
```

**取舍**：替换后失去「同 tag 重复触发互斥」能力。可以接受，因为：

- tag 是不可变引用，强制重 push 极少见。
- 真正的 race（覆盖 `release/version.json`、丢失 site 内容）比同 tag 重跑严重得多。

如果未来需要恢复同 tag 防抖，可在 job 级别加二级排他（不在本设计范围内）。

## 6. 实施顺序

1. 仓库根新增 `LICENSE`（AGPL-3.0 全文）。
2. 重写 `README.md`（删除旧 4 节，按 §3.2 章节结构填充）。
3. 新增 `docs/site/_config.yml` + 6 个 markdown + `assets/version.js`。
4. 新增 `.github/workflows/deploy-site.yml`。
5. 修改 `.github/workflows/android-release-apk.yml` 的 `concurrency` group 为 `gh-pages-deploy`。
6. 提交 PR 或合并到 main，触发首次站点部署。
7. 部署后人工验收：
   - 访问 `https://hanklzl.github.io/MusicFreeAndroid/` 确认 minima 主题渲染。
   - 访问 `/MusicFreeAndroid/download/`，按钮显示具体版本号 + 直链到当前 release APK。
   - `curl -I https://hanklzl.github.io/MusicFreeAndroid/release/version.json` 返回 200，确认 site sync 未误删 `release/`。
   - `curl -I https://hanklzl.github.io/MusicFreeAndroid/logan-viewer/` 返回 200，确认 site sync 未误删 `logan-viewer/`。
   - 触发一次 `workflow_dispatch` 走一次 release workflow（或等下一次 nightly），确认 `release/version.json` 仍能正常更新、不被 site sync 覆盖。

## 7. 风险与缓解

| 风险 | 缓解 |
|---|---|
| site sync 误删 `release/version.json` → 现网应用更新崩溃 | `--exclude='release/'` 强制保留；首次部署后立刻 curl 验证；release workflow 跑一次回归确认仍能写入 |
| site sync 误删 `logan-viewer/` → GitHub Pages 日志工具 404 | `--exclude='logan-viewer/'` 强制保留；站点发布后立刻 curl 验证 |
| Jekyll 渲染失败（语法错、front matter 错） | `_config.yml` 与每页用最简 front matter；首次部署后人工访问每个页面确认无 500 |
| 站点 baseurl 与 JS 路径漂移 | `_config.yml` 锁 `baseurl: "/MusicFreeAndroid"`，JS 写死同名前缀；任何改动需同步两处 |
| `version.json` schema 变更 | JS 用 optional chaining + try/catch，HTML 端兜底链接保证 fallback 可下载 |
| 多个 workflow 并发写 gh-pages | 共用 `concurrency: group: gh-pages-deploy` 串行 |
| GitHub Pages 部署延迟 | Pages 全球 CDN 缓存最长 ~10 分钟；属于 GitHub 平台行为，不可缓解 |

## 8. 后续工作（不在本次范围）

- 若需要截图：先建立内部「素材合规」流程，确认无第三方版权风险（专辑封面、第三方 logo 等）后再补。
- 中英双语：站点和 README 同时翻译，工作量翻倍，按读者反馈决定。
- 自定义域名：申请域名 → DNS CNAME 到 `hanklzl.github.io` → 仓库设置启用 → 站点 `_config.yml` 改 `url`。
- CHANGELOG 自动同步：可在 release workflow 末尾把 CHANGELOG.md 截取前 3 段写入 `docs/site/changelog.md` 并 commit，避免人工维护。

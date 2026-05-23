# logan-viewer

MusicFreeAndroid 反馈日志可视化 H5 工具。

浏览器内拖入 `musicfree-feedback-*.zip`，按 `sessionId` 分会话、`traceId` 折叠
展示用户操作链路；含 `error` 的 trace 自动展开、红条高亮。

## 设计 spec

参见 [docs/superpowers/specs/2026-05-23-logan-viewer-design.md](../../docs/superpowers/specs/2026-05-23-logan-viewer-design.md)。

## 使用

### 浏览器（推荐）

GitHub Actions 在每次 `tools/logan-viewer/**` 变更时构建并发布到 Pages。访问 repo
的 Pages URL（CI workflow `logan-viewer-pages` 完成后 Settings → Pages 可看到）。

本地开发 / 自测：

```bash
cd tools/logan-viewer
npm install
npm run dev          # Vite dev server，浏览器拖入 zip
npm run build        # 生产打包
npm run preview      # 本地预览 dist
```

> **浏览器兼容性**：建议 Chrome / Edge 最新版。Safari 16.4+ 也可工作；更老的
> Safari 因 `DecompressionStream('gzip')` 缺失会失败。

### CLI

```bash
npm run fixture      # 生成 demo fixture 到 out/sample-feedback.zip
npm run decode -- out/sample-feedback.zip > events.json
```

`npm run decode` 行为对齐 `tools/logan/decode-logan.sh`：默认尝试内置 debug pair，
release pair 是占位（见下）；环境变量 `LOGAN_AES_KEY` / `LOGAN_AES_IV` 可覆盖。

### 单测 / typecheck

```bash
npm test             # vitest
npm run typecheck    # tsc --noEmit
```

## 模块切分

```
src/
├── format/      # 纯函数：zip → 解密 → 行 JSON
├── model/       # types + index + traceGrouper + filter
├── state/       # Zustand store（loadZip 经 Worker）
├── worker/      # decode.worker.ts：消息驱动，跑 format + index
├── ui/          # React 组件（TopBar / SessionList / FilterBar / Timeline / DetailDrawer）
└── keys/        # AES key 内置常量（debug 实值 + release 占位）
```

## AES key 风险声明

> **若把 release key 内置进此前端代码并发布到公共 Pages，意味着拿到任意反馈包
> 的人都可以解密日志。**

仓库默认状态：`src/keys/builtin.ts` 里 `RELEASE_KEY_PAIR.isPlaceholder = true`，
不会用于解密。要解 release 反馈包，需要二选一：

1. 在 CLI 通过 `LOGAN_AES_KEY` / `LOGAN_AES_IV` 环境变量临时注入（不进版本控制）；
2. 在 `src/keys/builtin.ts` 里手动把 release pair 改成真实 key/iv，然后接受公开
   风险。

风险缓解：

- key 只能解密，不能签发 / 伪造日志；
- 反馈包是用户主动发给开发者的，trust 边界 ≈ "私聊给开发者一份日志"；
- 业务侧禁止把 token / 用户原文输入 / 含用户名的绝对路径写入 `fields`（参见
  [docs/superpowers/specs/2026-05-05-logging-system-design.md](../../docs/superpowers/specs/2026-05-05-logging-system-design.md)）。

`src/keys/builtin.ts` 是唯一入口，未来如需收口可改为表单输入或迁移到私有部署。

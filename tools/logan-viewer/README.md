# logan-viewer

MusicFreeAndroid 反馈日志可视化 H5 工具。

> **当前阶段：M1（format 层 + CLI）。** Vite/React UI 在后续里程碑落地。

## 设计 spec

参见 [docs/superpowers/specs/2026-05-23-logan-viewer-design.md](../../docs/superpowers/specs/2026-05-23-logan-viewer-design.md)。

## 现有能力（M1）

```bash
cd tools/logan-viewer
npm install
npm test               # 单测
npm run fixture        # 生成测试 fixture
npm run decode -- ../logan/out/sample-feedback.zip > out.json
```

CLI 是纯 Node 实现，复用未来浏览器侧也会用到的 `format/` 模块。

## AES key 风险声明

> **release key 内置于此仓库，意味着拿到任意反馈包的人都可以解密日志。**

缓解：

- key 只能解密，不能签发 / 伪造日志；
- 反馈包是用户主动发给开发者的，trust 边界 ≈ "私聊给开发者一份日志"；
- 业务侧禁止把 token / 用户原文输入 / 含用户名的绝对路径写入 `fields`（参见
  [docs/superpowers/specs/2026-05-05-logging-system-design.md](../../docs/superpowers/specs/2026-05-05-logging-system-design.md)）。

`src/keys/builtin.ts` 是唯一入口，未来如需收口可改为表单输入或迁移到私有部署。

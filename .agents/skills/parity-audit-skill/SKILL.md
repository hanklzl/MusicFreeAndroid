---
name: parity-audit-skill
description: 跨工具调用的 RN/Android parity 审计 sub-agent。触发短语："跑 parity audit"、"RN/Android 对齐扫描"、"对齐审计"、"parity-audit"。
---

# Parity Audit Skill

## 用途

把 RN MusicFree（参考实现）与 MusicFreeAndroid 在编译产物上的行为差异流水线化抓取并归集。`mode=audit` 时直接 `gh issue create`；`mode=dry-run` 时仅生成 `issue.md` 草稿。

## 调用契约

| 参数 | 取值 | 默认 |
|---|---|---|
| `--scope` | `core` / `non-core` / `all` / `page:<id>` | 读 `docs/parity-audit/state.json.next_recommended` |
| `--mode` | `audit` / `dry-run` | `audit`。后续 plan 会补 `replay:<run-id>` |
| `--limit` | 整数 | `5` |
| `--device` | adb serial | 取 `adb devices` 第一行 |

## 调用流程

1. 读 `docs/parity-audit/state.json` 与 `queue.md`
2. 检查当前 git 分支不是 `main`（在 `main` 上拒绝执行，提示切到 worktree）
3. 跑 `scripts/parity-audit/audit.sh --scope <...> --mode <...> --limit <...>`
4. audit.sh 内部串联：bootstrap → build → install → install-plugins → 循环 scenario(run-scenario → parse-events → screenshot-ssim → compare → 渲染 issue.md → 若 mode=audit 则上传截图+提交 Issue) → state 更新 → REPORT.md

## 强约束

- 任何 Maestro flow 缺失 / 构建失败 / 设备未连，必须把 scenario 标 `blocked_*` 写入 state，不能沉默跳过
- 在 `main` 分支拒绝执行
- `mode=dry-run` 阶段任何"创建 Issue"动作都视为 bug——必须只到 `issue.md` 草稿落盘为止
- 真实创建 Issue 前必须走指纹查重；同指纹 open Issue 只准追加评论，**禁止**重复创建

## 失败矩阵

详见 `references/failure-modes.md`。

## 工具差异点

详见 `references/tool-host-notes.md`，分 Claude Code 与 Codex 两段。

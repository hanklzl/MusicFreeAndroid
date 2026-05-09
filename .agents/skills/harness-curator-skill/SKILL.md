---
name: harness-curator
description: >
  Use this skill to periodically audit the Dev Harness: detect drift,
  recurrence of indexed incidents, missing guards, and Claude Code
  user auto-memory entries that should be promoted to repo-level rules.
  Trigger phrases: "巡检 harness", "更新错误库", "盘点 incidents",
  "校核 rules", "校核 guard", "同步 user memory 项目级条目",
  "生成 harness 报告".

  This skill produces REPORT.md only — it does NOT modify
  incidents.md / rules.md / index.md directly. Patches in REPORT
  must be applied by a human reviewer.
---

# Harness Curator Skill

Periodic audit + drift detection for the Dev Harness.

## 必读 gate

- [`docs/dev-harness/INDEX.md`](../../../docs/dev-harness/INDEX.md)
- [`docs/dev-harness/incidents/index.md`](../../../docs/dev-harness/incidents/index.md)
- 每域 `rules.md` + `incidents.md`（按需）
- `~/.claude/projects/.../memory/MEMORY.md`（识别项目级、可 promote 条目；个人会话偏好留原位）

## 不变约束

本 skill 不直接修改 `incidents.md` / `rules.md` / `index.md`。仅产 REPORT.md，patch 由人合入。该约束由 PR 3 的 `harness-curator-skill/SKILL.md` 自身 contract test 守护。

## Workflow checklist

详见 [curate-workflow.md](references/curate-workflow.md)。

1. 创建 worktree：`git worktree add .worktrees/harness-curate-$(date +%F) -b harness/curate-$(date +%F) main`，遵循 AGENTS.md 路径约束。
2. 在 worktree 内执行盘点：见 references/drift-detection.md。
3. 挖掘候选 incidents（最近 commit + user memory promotion）：见 references/memory-promotion.md。
4. 输出 `REPORT.md`：见 references/report-template.md。
5. 不修改 incidents.md / rules.md / index.md，REPORT 中的 patch 等用户确认后再合并。

## References

- [curate-workflow.md](references/curate-workflow.md)
- [drift-detection.md](references/drift-detection.md)
- [memory-promotion.md](references/memory-promotion.md)
- [report-template.md](references/report-template.md)

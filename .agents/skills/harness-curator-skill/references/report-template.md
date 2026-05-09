# REPORT Template

```markdown
# Harness Curate Report — YYYY-MM-DD

## Drift

- (空) 或 incident-level 列表 + diff

## Recurrence

- (空) 或 grep / contract-test 失败 + 复发位置 + 建议修复

## New Candidates (from recent commits)

- 草稿 incident（id 占位 TBD-N）+ commit 引用

## Memory Promotion

- user memory 条目 → 提议 promote 到 docs/dev-harness/<area>/rules.md

## 建议 diff

```diff
--- a/docs/dev-harness/<area>/rules.md
+++ b/docs/dev-harness/<area>/rules.md
@@ ...
```
```

REPORT 不直接落到 main；由人合入对应 `.worktrees/feat-...` 或独立 PR。

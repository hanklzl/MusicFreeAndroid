# Drift Detection

针对每条 `status=active` 的 incident：

1. **rule_ref 存在性**：anchor 在 rules.md 中是否找得到（grep `^## .*\{#<anchor>\}` 或显式 `# rule-...`）。
2. **guard.target 存在性**：contract-test 的 target 文件是否在工作树中。
3. **复发签名**：grep signature 跑出非空 → 报 recurrence，列文件:行。
4. **rules ↔ incidents 反向引用**：rules.md 每条 MUST 是否有 `implemented_by:` 行；缺则报 `rule without evidence`。
5. **DOCS_STATUS.md 登记**：dev-harness 文档全部登记为"当前规范"。
6. **symlinks**：跑 `bash scripts/dev-harness/symlinks-check.sh`。

每条问题落入 REPORT.md 的对应小节，附建议 diff（unified diff 块）。

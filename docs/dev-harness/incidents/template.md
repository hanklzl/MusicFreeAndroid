# Incident Template

> 复制本文件为新条目时，先去掉本说明段。`id` 取下一个未占用的 `INC-YYYY-NNNN`，并在本期 `docs/dev-harness/incidents/index.md` 同步登记。

## INC-YYYY-NNNN — 一句话标题

- id: INC-YYYY-NNNN
- area: ui | plugin | player | test
- date: YYYY-MM-DD
- status: active | superseded by INC-XXXX | stale
- rule_ref: docs/dev-harness/<area>/rules.md#<anchor>
- guard:
    type: contract-test | grep | manual | grep + manual
    target: <relative path to test file>           # 仅 contract-test 类型必填
- signature: |
    <一行可直接跑的 grep / 测试入口命令；contract-test 类型可省略 signature 但必须保留 type/target>
- fix_ref: <相对路径或 commit hash>

### 根因

简短描述触发条件、症状、为什么会发生。

### 复发条件

何种代码或配置会再触发此问题。grep / contract-test 的设计依据。

### 教训

未来如何避免；已经写入 rule 的关键句。

### 备注（可选）

- 关联其他 incident、未自动化 guard 的原因、升级触发条件。

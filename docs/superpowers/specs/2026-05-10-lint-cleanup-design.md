---
状态：当前规范（lint 修复专项）
适用范围：在隔离 git worktree 中运行 `./gradlew lint`，按实际报告修复 lint 问题。
直接执行：是（作为 implementation plan 输入）
入口：[AGENTS.md](../../../AGENTS.md)
最后校验：2026-05-10
---

# lint 修复专项设计

## 0. 背景与目标

本轮目标是在不污染主工作区的前提下，运行仓库级 `./gradlew lint`，读取真实 lint 报告，并修复导致 lint 不达标的问题。

已确认的仓库事实：

- 主工作区当前干净，当前分支为 `main`。
- `.worktrees/` 已在根目录 `.gitignore` 中。
- 仓库约束要求功能开发默认使用 `.worktrees/<branch-name>`。
- 仓库认可的 lint 命令为 `./gradlew lint`。

## 1. 范围

本轮采用用户选择的方案：先跑完整 lint，看具体数量后再决定是否扩大到 warning 清零。

### 目标

1. 从当前 `main` 创建隔离 worktree：`.worktrees/fix-lint-issues`。
2. 在 worktree 内创建并使用分支：`fix/lint-issues`。
3. 运行 `./gradlew lint`，读取终端输出和生成的 lint 报告。
4. 将问题分为三类：
   - 必须修复：导致 lint 失败的 error / fatal。
   - 可低风险顺手修复：局部、确定、不会改变行为的 warning。
   - 需要后续判断：会扩大改动面或涉及产品/架构取舍的 warning。
5. 优先修复必须修复项；warning 是否全清以 lint 报告规模和风险为准。
6. 修复后重跑 `./gradlew lint`，以通过作为本轮核心验收。

### 非目标

- 不做无关格式化、批量重构或架构调整。
- 不新增 lint baseline 来掩盖现有问题。
- 不修改 `docs/superpowers/plans/*.md` 作为当前执行依据。
- 不把 Release 构建作为普通 lint 修复的阻塞条件。
- 不默认运行仪器测试，除非 lint 修复触及相关测试 wiring 且有必要验证。

## 2. 执行策略

### 2.1 worktree 隔离

执行前确认 `.worktrees/` 仍被忽略。若 `.worktrees/fix-lint-issues` 已存在，则先检查其分支和状态：

- 若它已经是本轮目标分支且工作区可用，复用该 worktree。
- 若它属于其他任务或有未归属改动，改用新的语义化路径，避免覆盖用户工作。

### 2.2 lint 取证

第一轮只运行：

```bash
./gradlew lint
```

随后读取模块 lint 报告。优先使用机器可读的 XML 或文本报告定位文件和规则；HTML 报告仅作为辅助。

### 2.3 修复原则

修复顺序按风险从低到高：

1. 明确的 Android API / manifest / resource / permission / accessibility lint error。
2. 明确的 Kotlin 或 Compose 使用问题，且修复不改变业务意图。
3. 可局部修复的 warning，例如无效资源、过期属性替换、明显缺失的描述或注解。
4. 需要较大重构或行为判断的 warning 先记录，再决定是否纳入本轮。

不通过新增 suppress、baseline 或关闭 lint 规则来完成验收，除非单个问题经代码事实证明是 lint 误报，并且 suppress 的范围足够小、理由清楚。

## 3. 验收

核心验收：

```bash
./gradlew lint
```

通过条件：命令成功结束，不再有阻塞本轮的 lint error / fatal。

补充验收按改动面决定：

- 如果只改资源、manifest 或小范围注解，通常不额外跑全量单测。
- 如果改 Kotlin 生产代码，至少补跑相关模块编译或 `./gradlew :app:assembleDebug`。
- 如果改测试 wiring，再补跑相关测试 task。

## 4. 风险与处置

| 风险 | 处置 |
|---|---|
| lint 输出问题数量很大 | 先修阻塞项，warning 按风险分组回报，不做大范围无关改动。 |
| lint 报告指向生成文件或第三方压缩资产 | 不直接改生成物或 vendored 资产，优先确认是否需要排除或局部 suppress。 |
| worktree 路径已被占用 | 检查状态后复用或换新路径，不覆盖用户改动。 |
| lint 修复引发编译失败 | 回到最小修复，补跑相关编译命令后再给结论。 |

## 5. 完成输出

完成时需要说明：

- 使用的 worktree 和分支。
- 首轮 lint 的失败类别。
- 修复过的文件和规则类型。
- 最终验证命令及结果。
- 未纳入本轮的 warning 或残余风险。

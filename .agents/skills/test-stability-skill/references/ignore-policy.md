# @Ignore Policy

约束：

- 仓库当前应保持 `@Ignore` 全部归零。
- 新增 `@Ignore` MUST 同步登记一条 incident，写明触发条件、临时绕过原因、升级方案。
- harness-curator-skill 巡检时会扫描 `*.kt` 中的 `@Ignore`，未登记 incident 的会进 REPORT。

验证：

```bash
grep -rn "@Ignore" --include="*.kt" 2>/dev/null | grep -v build/ | grep -v .worktrees/
```

预期：空输出。

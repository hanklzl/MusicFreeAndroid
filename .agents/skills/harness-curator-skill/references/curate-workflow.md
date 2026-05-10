# Curate Workflow

```bash
DATE=$(date +%F)
git worktree add .worktrees/harness-curate-$DATE -b harness/curate-$DATE main
cd .worktrees/harness-curate-$DATE

# 1. 跑现状基线
bash scripts/dev-harness/check.sh --skip-contract-tests || echo "现状基线已记入 REPORT"

# 2. 盘点最近 30 天 commit
git log --since="30 days ago" --pretty=format:'%h %ad %s' --date=short > /tmp/recent-commits.txt

# 3. 解析 incidents 索引
python3 scripts/dev-harness/grep-check.py || true   # 失败仅作信号
```

输出：worktree 内 `REPORT.md`，分节列 drift / recurrence / new candidates / memory promotion / 建议 diff。

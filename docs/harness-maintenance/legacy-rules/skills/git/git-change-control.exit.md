# Git Exit Gate / Unknown Policy / Rollback

> 主文件：[git-change-control.md](git-change-control.md)

## Git Exit Gate

每个任务结束前执行。

```powershell
git status --short
git diff --name-only
git log -1 --oneline
```

报告必须包含：Git Exit Gate 终态、dirty 文件清单、每个文件分类、后续建议。

禁止：
- DONE 状态下有 unknown dirty
- PARTIAL 状态下进入无关新任务
- BLOCKED 状态下不写阻塞原因

---

## Unknown Dirty Policy

### 判定
dirty 文件没有明确任务编号、提交批次、分类或继承编号时归为 `unknown`。

### 处理
1. 立即停止任何 commit / push / deploy
2. 在报告中记录 `unknown_files: [...]`
3. 至少 git log + git blame + 旧 report 引用三项调查
4. 调查完成前只能是 `BLOCKED_DIRTY_UNKNOWN`

### 禁止
- 不允许把 unknown 强行归类
- 不允许与其他 batch 一起提交
- 不允许"先 commit 再说"

---

## Rollback Policy

### 触发
- 部署后核心 API 失败
- 远端 health check 失败
- 关键业务链路回归
- 用户明确要求回滚

### 流程
1. 生成 `runtime/qa/out/rollback-<task>-<timestamp>.md`
2. 远端 `git revert <commit-hash>`
3. 重新构建 / 重启容器
4. 重新验证 health + smoke
5. 更新状态文件
6. 进入 Git Exit Gate `BLOCKED` 状态

### 禁止
- 不允许仅回滚远端而不回滚本地 commit
- 不允许跳过 evidence 报告就回滚

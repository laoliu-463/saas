# Git Batch Submit

> 本文件定义批次提交流程。配合 [git-change-control.md](git-change-control.md) 使用。

## 1. 适用场景

- 多任务积累 dirty / untracked，需要分批提交
- 多领域 dirty 同时存在
- 业务代码与 docs-only 必须分开提交

## 2. 批次划分顺序

| 顺序 | 批次 | 内容 | 部署 |
|---|---|---|---|
| 1 | reports | `harness/reports/*.md` | 否 |
| 2 | harness-docs | 状态文件、规则文件 | 否 |
| 3 | cleanup-retire | 归档/删除/.gitignore | 否 |
| 4 | backend | `backend/src/` | 视任务 |
| 5 | frontend | `frontend/src/` | 视任务 |
| 6 | sql | migration | 视任务 |
| 7 | docker_deploy | compose/Dockerfile | 是 |

每批次独立编号 `GIT-BATCH-N`。

## 3. 提交步骤

### A. Intake 与分类
```powershell
git status --short
git diff --name-only
```
输出 Dirty Classification 表。

### B. 起草 Batch 计划
编号、文件清单、验证命令、风险、回滚方案。

### C. Scope 隔离
逐文件 `git add -- <file>`，每次 add 后检查 `git diff --cached --name-only`。

### D. 审查 9 项
1. staged 属于单一任务/batch
2. 不含 unknown
3. 不含 FORBIDDEN_SCOPE
4. 不含非本任务代码
5. 不含环境敏感文件
6. 不含临时文件
7. `git diff --cached --check` 无输出
8. scope 一致
9. commit message 规范

### E. Commit
```powershell
git commit -m "<type>(<scope>): <task-id> <description>"
```

### F. 双 Remote 推送
```powershell
git push gitee feature/<branch>
git push origin feature/<branch>
```

### G. 报告收口
更新 CURRENT_STATE.md、DOMAIN_STATUS.md、HARNESS_CHANGELOG.md。

## 4. 推送失败处理

| 错误 | 处理 |
|---|---|
| non-fast-forward | `git pull --ff-only` 后重试 |
| permission denied | 联系维护者更新 SSH key |
| repository not found | 确认 remote URL |

## 5. 关联文件

- [git-change-control.md](git-change-control.md)
- [post-task-gc.md](post-task-gc.md)

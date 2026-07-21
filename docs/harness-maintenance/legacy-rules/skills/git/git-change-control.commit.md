# Git Commit / Push / Deploy Gate

> 主文件：[git-change-control.md](git-change-control.md)

## Staged Scope Gate

每次 `git add` 后、`git commit` 前必须执行。

```powershell
git diff --cached --name-only
git diff --cached --check
```

必须确认：
- staged 文件全部属于同一任务或同一 batch
- staged 不包含 unknown / FORBIDDEN_SCOPE / 环境敏感文件
- staged 不含临时文件（`*.tmp`、`.bak`、`debug-*`）
- `git diff --cached --check` 无输出

---

## Commit Gate

每次 `git commit` 前。

必须满足：
- 对应 Scope 测试通过
- `git diff --cached --check` 通过
- commit message 符合规范
- 稳定 evidence 已生成并绑定本任务 owned set
- staged 文件清单已写入报告

禁止 commit：包含 unknown / 跨 Scope / 环境敏感 / 临时文件。

---

## Push Gate

每次 `git push` 前。

```powershell
git remote -v
git branch --show-current
git log -1 --oneline
```

推送当前 upstream；无 upstream 时设置到 `origin`：
```powershell
git push
git push --set-upstream origin <current-branch>
```

`gitee` 为只读镜像，不作为自动推送目标。优先使用 `git-push-safe.ps1 -OwnedFiles <paths>` 完成 scoped staging、commit 和 push。

每次 push 必须记录：commit hash、remote、branch、结果、时间戳。

---

## Deploy Commit Gate

任何远端/本地部署前。

必须满足：
- 代码已 commit 并推送到目标 remote
- 远端 `git pull --ff-only` 拉到目标 commit
- 远端 `git rev-parse HEAD` 等于目标 commit
- 远端 `git status --short` 为空

禁止：
- 从 dirty 工作区直接 scp/rsync
- 使用未提交代码部署
- 远端手工 patch 后再 commit

`.env` 例外：可以是远端本地运行配置，但不得 commit。

部署后必须验证：容器状态、health check、commit hash、jar/dist 时间。

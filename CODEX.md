# CODEX.md

Codex 在本仓库执行任务时必须优先遵守 `AGENTS.md`。

最小进入顺序：

1. 读 `AGENTS.md`。
2. 读 `CLAUDE.md`。
3. 读 `docs/README.md`。
4. 读 `harness/CURRENT_STATE.md`。
5. 按 `harness/TASK_ROUTING.md` 选择领域、skill、eval 和 runbook。

默认执行入口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env test -Scope full -Message "说明本次修改"
```

文档 / Harness 变更：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\commands\agent-do.ps1 -Env test -Scope docs -Message "docs: update harness"
```

real-pre、密钥、Docker volume 和远端部署安全边界以 `harness/FORBIDDEN_SCOPE.md` 为准。


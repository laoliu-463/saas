# Prompt: release check

请执行上线前总检查：

1. 读取 `AGENTS.md`、`docs/harness-maintenance/legacy-rules/policies/agent-contract.md`、`docs/harness-maintenance/legacy-rules/state/snapshots/01-当前项目状态.md`。
2. 读取 `docs/10-部署运行总览.md`、`docs/deploy/README.md`、`docs/验收/real-pre联调手册.md`。
3. 执行本地安全检查、构建、重启、健康检查和业务验证。
4. 如果用户明确要求远端发布，只准备并验证 `release/real-pre.json`；实际部署进入 Jenkins 发布队列。
5. 生成 evidence report。
6. 输出 `PASS` / `PARTIAL` / `FAIL`，并列出未验证项。

默认命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope full -ReportKey release-check -OwnedFiles 'path1;path2' -DeployRemote false -Message "chore: release check"
```

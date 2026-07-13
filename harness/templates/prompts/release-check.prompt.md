# Prompt: release check

请执行上线前总检查：

1. 读取 `AGENTS.md`、`harness/rules/policies/agent-contract.md`、`harness/rules/state/snapshots/01-当前项目状态.md`。
2. 读取 `docs/10-部署运行总览.md`、`docs/deploy/README.md`、`docs/验收/real-pre联调手册.md`。
3. 执行本地安全检查、构建、重启、健康检查和业务验证。
4. 如果用户明确要求远端部署，执行 `deploy-remote.ps1`。
5. 生成 evidence report。
6. 输出 `PASS` / `PARTIAL` / `FAIL`，并列出未验证项。

默认命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope full -DeployRemote false -Message "chore: release check"
```

# Runbook：real-pre 远端发布

## 适用场景

用户要求把已验证变更发布到远端 real-pre 时使用。本 Runbook 不授予普通 Codex 任务 SSH 或部署权限。

## 普通 Codex 任务职责

1. 在独立 worktree / `codex/*` 分支完成修改。
2. 运行 `agent-do.ps1` 本地验证并生成稳定 evidence。
3. 提交、推送并创建 PR 或候选提交。
4. 报告目标完整 SHA、CI 结果和剩余风险。
5. 等待 Merge Queue 串行合并；不得调用旧部署脚本。

禁止直接 SSH、修改 `/opt/saas/app`、修改 `/opt/saas/env/.env.real-pre`、现场构建镜像或部署未合并分支。

## 唯一发布入口

```text
release/real-pre push
→ .github/workflows/release-images.yml
→ Jenkins saas-real-pre-cd 队列
→ lock('saas-real-pre-deploy')
→ scripts/deploy-release.sh
```

Jenkins 参数由 CI 自动提交，不接受人工改成其他分支或短 SHA。发布控制器配置见 `../cicd-real-pre-policy.md`。

## 发布结果判定

- `PASS`：五项版本一致，`/opt/saas/releases/current.json` 已原子更新。
- `FAIL`：构建、digest、迁移、健康或版本校验失败；不得更新 current。
- `BLOCKED`：缺凭证、未启用 Flyway、目标非后继或外部环境不可用。
- `ROLLBACK`：必须重新进入同一 Jenkins 队列并显式设置 `ROLLBACK_APPROVED=true`。

旧 `deploy-remote.ps1`、`deploy-real-pre.sh` 和 `rollback-real-pre.sh` 只保留阻断提示，不是备用入口。

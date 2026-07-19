# Deployment State

> 远端发布主源：`harness/rules/cicd-real-pre-policy.md` 与 `harness/rules/environment/envs/remote-real-pre-env.md`。

| 环境 | 状态 | 执行边界 |
| --- | --- | --- |
| test | 可用于 mock / P0 回归 | 用户明确要求或专项测试 |
| 本地 real-pre | 默认工程验证环境 | `agent-do.ps1` 可构建、重启和验证本地容器 |
| 远端 real-pre | 仅 Jenkins 单通道发布 | `release/real-pre` + 完整 SHA/digest + 全局锁 |

当前约束：

- 普通 Codex 任务无远端部署和回滚权限；`-DeployRemote true` 会被拒绝。
- 旧 SSH、共享工作树和现场构建入口已停用。
- real-pre P0 不能因容器健康直接判定全量通过，仍需真实样本与业务闭环证据。
- `.env.real-pre` 不进入 Git；Flyway 未显式启用时发布必须 BLOCKED。
- `current.json` 只有在后端、前端、镜像和数据库版本全部一致后更新。

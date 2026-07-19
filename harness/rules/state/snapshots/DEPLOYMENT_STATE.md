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
- `.env.real-pre` 不进入 Git；只有本次确需迁移而 Flyway/数据库门禁不满足时发布才 BLOCKED。
- 无迁移差异的发布不执行数据库写操作；数据库/Flyway 观测值保留在发布记录中。
- `current.json` 只有在应用、镜像以及本次适用的数据库门禁全部通过后更新。

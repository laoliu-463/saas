# 维护者 Runbook

本目录只面向 Jenkins 管理员、CI/CD 维护者、值班人员和获授权的紧急恢复人员。普通开发者不需要阅读这些实现细节。

Runbook 只做路由，不复制部署规则。服务器地址、凭证和真实环境文件不写入仓库。

| 场景 | 入口 |
| --- | --- |
| real-pre 发布 | [real-pre-release.md](real-pre-release.md) |
| 回滚与故障处理 | [rollback.md](rollback.md) |
| 数据库迁移 | [database-migration.md](database-migration.md) |
| Break-glass 紧急恢复 | [break-glass.md](break-glass.md) |
| CI/CD 维护 | [ci-cd-maintenance.md](ci-cd-maintenance.md) |

普通开发入口仍是 [日常开发流程](../development-flow.md)。

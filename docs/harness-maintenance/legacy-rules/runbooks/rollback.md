# Runbook：回滚

## 本地代码回滚

优先使用非破坏方式：

```powershell
git revert <bad_commit>
```

未经用户明确要求，禁止 `git reset --hard` 或覆盖用户修改。

## 本地容器回滚

1. 回退或 revert 当前任务提交。
2. 通过 `agent-do.ps1` 重新构建、重启和验证本地服务。
3. 生成新的 evidence；不得沿用回滚前结论。

## 远端 real-pre 回滚

远端回滚不是手工 Docker 或 Git 操作，必须：

1. 从 `/opt/saas/releases/current.json` 与 `previous.json` 确认完整 SHA 和 digest；
2. 重新进入 Jenkins `saas-real-pre-deploy` 全局队列；
3. 目标镜像仍需完整 SHA、OCI revision 和 digest 一致；
4. 显式设置 `ROLLBACK_APPROVED=true`；
5. 重新验证后端、前端和镜像身份，并记录数据库/Flyway 观测值；应用回滚不执行旧迁移；
6. 生成独立回滚发布记录。

`scripts/rollback-real-pre.sh` 已停用，不是备用入口。

## 数据库边界

- real-pre 禁止清库、删除 volume 或执行未经评审的破坏性 SQL。
- 数据库回滚需独立影响评估、备份和迁移审查。
- 应用回滚不自动等于数据库回滚；版本不兼容时必须 BLOCKED。
- 数据库迁移 forward-only；如需数据库修复，必须作为新的受控迁移独立评审。

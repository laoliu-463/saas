# RBAC real-pre 修复证据

- 时间：2026-07-24
- 环境：本地代码验证 + 远端 real-pre / Jenkins
- 分支：`codex/rbac-real-pre-fix`
- 实现提交：`e2262dd0`
- 工作树：实现提交时干净

## 变更

- 寄样导出改为仅允许 `admin`、`biz_leader`、`biz_staff`、`ops_staff`。
- 渠道角色保留寄样查看和操作权限，但禁止导出。
- 新增 Flyway 迁移，撤销已有 real-pre 数据库中渠道角色的 `sample:export-samples` 权限。
- 修正权限目录和相关测试计数。
- 远端 `biz_staff` 演示账号密码已同步，登录 HTTP 200。

## 验证

- `git diff --check`：PASS
- Maven 编译：PASS
- 权限策略、权限目录合同、迁移合同：20/20 PASS
- Testcontainers PostgreSQL 集成测试：未执行成功；本机无 Docker，阻塞原因已记录。
- Jenkins #59：浏览器环境已修复，商品链和业绩看板 PASS；RBAC 发现真实缺口后失败。
- Jenkins #59 回滚：PASS；旧版本、前后端健康和调度恢复均通过，部署锁已释放。

## 结论

`PARTIAL`：代码修复已完成并待 PR 验证；远端生产当前保持旧版本健康，未上线未验证的新版本。

## Retro

本次先修复 Jenkins Playwright 运行环境，再暴露真实 RBAC 差异；后续必须先通过 PR CI 和迁移集成测试，再进入 Jenkins 发布，不再用手工数据库改动替代版本化迁移。

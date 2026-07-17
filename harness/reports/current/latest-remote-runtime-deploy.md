# Evidence Report

## Metadata

- Time: 2026-07-17 21:52:00 +08:00
- Environment: real-pre
- Scope: full
- Code/deploy commit: c89db16ae140e166feba3cee1188ed19edb08b6d
- Remote branch: feature/auth-system
- Remote worktree: clean
- Deploy remote: true

## Deployment path

- GitHub `origin/feature/auth-system`、Gitee `feature/auth-system` 和远端服务器均已同步到 `c89db16a`。
- 固定 `deploy-remote.ps1` 执行成功；此前发现并修复了 outbox 索引 guard 的 SQL 引号转义问题。
- 未使用 mock 数据、未清理 PostgreSQL/Redis volume、未绕过提交一致性校验。

## Build and local validation

- Backend Maven build: PASS。
- Frontend `npm ci` + production build: PASS。
- Local backend/frontend container rebuild: PASS。
- Local health checks: PASS。
- real-pre P0 preflight: PASS。

## Remote schema and build

- Activity、role-aware attribution、order attribution、performance、V2 config、product backfill schema guards: PASS。
- `performance_calculation_execution`、`performance_adjustment_ledger`: 存在。
- `idx_domain_event_outbox_dispatch_order`: 存在，增量迁移幂等执行通过。
- Remote Maven build: PASS。
- Backend JAR guard: PASS，host/container size 均为 `81187109` bytes。

## Remote runtime verification

- backend-real-pre、frontend-real-pre、postgres-real-pre、redis-real-pre: healthy。
- `GET http://127.0.0.1:8081/api/system/health`: `{"status":"UP"}`。
- frontend `/login`: HTTP 200。
- outbox 锁定查询 `EXPLAIN`: `Limit -> LockRows -> Index Scan using idx_domain_event_outbox_dispatch_order`。
- 部署后 backend 日志未发现新的 `input_snapshot` JSONB 类型错误。

## Current data facts

- `performance_calculation_execution`: `FAILED=150`、`RUNNING=4`、`SUCCEEDED=154031`。
- `performance_adjustment_ledger`: 当前 0 行；部署验证未伪造退款事件。
- `domain_event_outbox`: `PENDING=290126`、`PUBLISHED=341170`、`FAILED=1`、`DEAD=153`。

## Conclusion

PARTIAL：远端部署和运行时验证通过，代码级 JSONB 修复及 outbox 索引已生效；历史 150 条失败执行和约 29 万条待处理事件仍未自动回放/清空，需单独执行受控恢复并持续观察。

## Residual risk

- 存量 `performance_calculation_execution.FAILED=150` 尚未重放，不能以部署成功替代业务数据恢复。
- outbox PENDING 积压仍大，索引改善选取路径但不会自动消化存量；需观察 dispatcher 吞吐、锁耗时和 backlog 趋势。
- 远端日志中未发现 `skills for real engineers` 固定字样；旧日志保留限制仍然存在，不能证明更早历史绝对不存在。

## Retro

本轮补充了部署 guard 的真实远端验证，并修复了内嵌 SQL 引号转义导致的假失败。后续应为部署脚本中的每个 SQL guard 增加 shell 级 dry-run/集成测试，避免迁移已成功但门禁误报。

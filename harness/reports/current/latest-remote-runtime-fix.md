# Evidence Report

## Metadata

- Time: 2026-07-17 21:28:26 +08:00
- Environment: real-pre
- Scope: backend
- Branch: codex/fix-remote-runtime-issues
- Code commit validated: 1fe014a9
- Owned worktree: clean after evidence finalization
- Deploy remote: false（用户尚未授权远端部署）

## Problem and evidence chain

- 现象：远端历史日志未发现 `skills for real engineers` 固定字样；同一时间窗口确认到退款业绩流水写入失败，以及 outbox 锁定查询耗时较高并形成积压。
- 根因证据：`PerformanceAdjustmentLedgerMapper` 继承 MyBatis-Plus 默认 `insert`，`JacksonTypeHandler` 将 Map 绑定为 `VARCHAR`，PostgreSQL 不能将其隐式写入 `JSONB`；远端错误为 `column "input_snapshot" is of type jsonb but expression is of type character varying`。
- 性能证据：原 outbox 查询在远端 `EXPLAIN` 为 `Parallel Seq Scan -> Sort -> Gather Merge`；新增部分索引后本地 real-pre 同形查询为 `Index Scan using idx_domain_event_outbox_dispatch_order`。

## Owned Files

~~~text
backend/src/main/resources/db/alter-domain-event-outbox-dispatch-20260717.sql
backend/src/main/resources/db/migrate-all.sql
backend/src/main/resources/mapper/PerformanceAdjustmentLedgerMapper.xml
backend/src/test/java/com/colonel/saas/domain/event/DomainEventOutboxDispatchIndexTest.java
backend/src/test/java/com/colonel/saas/mapper/PerformanceAdjustmentLedgerMapperTest.java
backend/src/test/java/com/colonel/saas/testsupport/BaseIntegrationTest.java
backend/src/test/resources/db/mapper-integration-schema.sql
harness/scripts/commands/deploy-remote.ps1
~~~

## Build and test result

- `mvn -f backend/pom.xml -DskipTests package`: PASS。
- 基线 `PerformanceRefundAdjustmentServiceTest,DddOutboxInventoryContractTest`: PASS。
- 修复前真实 PostgreSQL 回归测试：按预期 FAIL，复现 JSONB/VARCHAR 类型错误。
- 修复后 `PerformanceAdjustmentLedgerMapperTest,DomainEventOutboxDispatchIndexTest`: PASS。
- `git diff --check`: PASS。
- `deploy-remote.ps1` PowerShell 语法检查：PASS。

## Local real-pre result

- 固定入口 `agent-do.ps1 -Env real-pre -Scope backend` 已执行：构建、后端容器重启、健康检查、P0 preflight 均 PASS。
- 由于隔离 worktree 原有 PostgreSQL volume 缺少当前业绩表，按已有幂等迁移 `alter-performance-final-attribution-20260716.sql` 补齐本地 schema；迁移完成后再次用固定 `restart-compose.ps1` 重启后端并复核。
- 当前容器：backend-real-pre、postgres-real-pre、redis-real-pre、frontend-real-pre 均 healthy。
- `GET http://127.0.0.1:8081/api/system/health`: HTTP 200，`{"status":"UP"}`。
- 本地 PostgreSQL 已确认 `performance_calculation_execution`、`performance_adjustment_ledger` 存在。
- outbox 部分索引存在，查询计划为：`Limit -> LockRows -> Index Scan using idx_domain_event_outbox_dispatch_order`。
- 后端重启后未发现 `input_snapshot` JSONB 类型错误。
- 本地 `performance_calculation_execution` 当前无记录、`performance_adjustment_ledger` 当前 0 行；未用 mock 数据伪造业务成功。

## Remote result

- 远端未部署，远端仍需用户明确授权后执行固定 `agent-do.ps1 ... -DeployRemote true`。
- 前一轮远端取证中，固定字样 `skills for real engineers` 在可见容器日志、`/opt/saas/logs`、systemd journal 和源码范围内均未命中；旧容器日志保留不足，不能据此判断更早历史是否存在该字样。
- 因未部署，远端原有退款失败记录和 outbox 积压尚未被本次修复闭环验证。

## Harness result

- TASK_GATE=PASS，无本任务新增的 Harness 限制违规。
- REPOSITORY_HEALTH=PARTIAL：仓库既有 `harness/reports` 文件数量历史债务（reports 根目录 23/20、current 63/50），本轮未删除或重排用户历史证据。

## Conclusion

PARTIAL：代码修复、迁移、回归测试、本地 real-pre 构建/重启/健康和查询计划验证均通过；远端尚未部署，因此不能声明远端问题已关闭。

## Residual risk

- 远端部署前，远端 `performance_adjustment_ledger` JSONB 写入和 outbox 查询仍保留原风险。
- 新索引只改善可重试事件的选取路径，不会自动清空约 28 万条 outbox 积压；部署后需观察 backlog、锁查询耗时和退款失败计数。
- 本地旧 volume 的历史业绩迁移包含既有数据补齐 UPDATE，已完成但未改变业务规则；远端部署时沿用固定迁移门禁。
- 本地另有 `OrderSyncService` 的 `BadSqlGrammarException` 日志，但未出现 `input_snapshot` 关键字，未将其误判为本任务根因。

## Retro

本轮将 JSONB 写入回归测试固定在真实 PostgreSQL Mapper 层，并将 outbox 索引同时纳入 fresh schema、增量迁移和远端部署 guard。后续可把 outbox `EXPLAIN`/积压阈值纳入周期性运行观测，避免索引退化只在生产日志中暴露。

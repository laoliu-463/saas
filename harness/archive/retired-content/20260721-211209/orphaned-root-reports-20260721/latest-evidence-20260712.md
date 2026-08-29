# Evidence Report — 活动商品多线程同步治理

## 结论

PARTIAL

## 证据

- **时间 / 环境**：2026-07-12 23:16 CST；本地 `real-pre`，远端未部署。
- **分支 / 工作区**：`codex/ddd-user-role-application`；并发治理提交 `653eb41b`、证据文档提交 `6834e223` 已推送到 `origin`；工作区仍有历史并行改动。
- **修改文件**：`DistributedConcurrencyLimiter`、`ProductActivityManualSyncService`、`StaleProductSyncJobReconcileJob`、`application.yml`、活动商品同步文档及对应测试。
- **构建**：`mvn -DskipTests -Djacoco.skip=true package` 通过；编译与 Spring Boot repackage 均成功。
- **相关回归**：55 个相关测试通过，0 failures，0 errors；覆盖并发槽、手动同步、stale 回收、活动列表分页并发、backfill deadlock、Controller。
- **本地 Docker**：后端镜像已重建并重启；backend/postgres/redis healthy；`GET /api/system/health` 返回 200 / `{"status":"UP"}`。
- **业务 SQL**：最近手动同步任务 `statusPartitionParallelism=2`；活动 `3929905` 为 `SUCCESS`（392 行、303 updated），活动 `3916506` 为 `PARTIAL`（达到 1000 行上限）。
- **Redis**：并发 leases=0，活动锁 key 数=0；任务结束后资源已释放。
- **Harness**：`git diff --check` 通过；`check-harness-limits.ps1` 返回 PASS。

## 风险

- 全量 `mvn clean test` 本轮仍未完成：JaCoCo 0.8.11 在 Surefire 扫描 `SampleControllerTest` 时抛出 `Truncated class file`，实际执行测试数为 0；这是测试工具链阻塞，不是业务断言通过证据。
- 未跟踪的 `AllActivitiesSyncAndInspectTest` 未执行；它使用 `real-pre`、会执行真实活动商品写入并生成 JWT，不纳入默认回归。
- 未跟踪的 `LockOwnerReleaseGuardTest` 未纳入本次范围；单独报告的 5 处 `OrderSyncService.release(String)` 违规需要另立任务治理。
- 单活动状态分片默认并发已设为 2，但当前没有 buyin QPS 压测证据；需继续观察 429、20000 和接口耗时。
- 本次只验证了本地 real-pre；远端服务器没有修改，也没有 SSH 部署。
- 工作区仍有历史脏文件，不能使用会整体暂存/推送的自动入口；本轮只推送了 `653eb41b`。

## 下一步

- 先观察本地真实上游一轮；远端部署需另行确认。
- 若出现限流，将 `PRODUCT_SYNC_ACTIVITY_PRODUCT_MANUAL_STATUS_PARTITION_PARALLELISM` 灰度降回 1。

# Evidence Report — 活动商品多线程同步治理

## 结论

PARTIAL

## 证据

- **时间 / 环境**：2026-07-12 21:45 CST；本地 `real-pre`，远端未部署。
- **分支 / 工作区**：`codex/ddd-user-role-application`，并发治理代码提交 `653eb41b` 已推送到 `origin`；工作区仍有历史并行改动。
- **修改文件**：`DistributedConcurrencyLimiter`、`ProductActivityManualSyncService`、`StaleProductSyncJobReconcileJob`、`application.yml`、活动商品同步文档及对应测试。
- **构建**：`mvn -DskipTests package` 通过。
- **回归**：52 个相关测试通过，0 failures，0 errors；覆盖并发槽、手动同步、stale 回收、backfill deadlock、Controller。
- **本地 Docker**：后端镜像已重建并重启；backend/postgres/redis healthy；`GET /api/system/health` 返回 200 / `{"status":"UP"}`。
- **业务 SQL**：最近手动同步任务 `statusPartitionParallelism=2`；活动 `3929905` 为 `SUCCESS`（392 行、303 updated），活动 `3916506` 为 `PARTIAL`（达到 1000 行上限）。
- **Redis**：并发 leases=0，活动锁 key 数=0；任务结束后资源已释放。
- **Harness**：`git diff --check` 通过；`check-harness-limits.ps1` 返回 PASS。

## 风险

- 全量 `mvn test` 本轮未重新跑完；历史执行曾超时，不能写成全量通过。
- 单活动状态分片默认并发已设为 2，但当前没有 buyin QPS 压测证据；需继续观察 429、20000 和接口耗时。
- 本次只验证了本地 real-pre；远端服务器没有修改，也没有 SSH 部署。
- 工作区仍有历史脏文件，不能使用会整体暂存/推送的自动入口；本轮只推送了 `653eb41b`。

## 下一步

- 先观察本地真实上游一轮；远端部署需另行确认。
- 若出现限流，将 `PRODUCT_SYNC_ACTIVITY_PRODUCT_MANUAL_STATUS_PARTITION_PARALLELISM` 灰度降回 1。

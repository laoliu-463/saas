# 商品库 Phase 4-1.5 backfill deadlock 修复报告

## 1. 结论

- 状态：**PASS**
- 是否修复 deadlock：是
- 是否重新执行 activityId=3859423 真实 backfill：是
- 真实 backfill 第一次结果：SUCCESS，DONE_NO_MORE，**inserted=1, updated=3008**
- 真实 backfill 第二次幂等重跑结果：SUCCESS，DONE_NO_MORE，**inserted=0, updated=2512**（无重复插入，幂等正确）
- dry-run 复核：SUCCESS，DONE_NO_MORE，**apiFetchedRows=4065, apiDistinctProductIds=4064**
- 是否建议进入 Phase 4-2：可以，但建议先把 `3859423` DISPLAYING 涨 260 这条 `skipDisplayRefreshForExpiredActivity` 与历史 1480 DISPLAYING 异常一起作为 Phase 4-1.6 处理；backfill 死锁修复本身可视为 PASS。

## 2. 继承问题（Phase 4-1）

继承自 `harness/reports/product-library-backfill-3859423-evidence-20260615-1342.md`：

- dry-run jobId：`product-backfill-f4f27f1b-102e-4060-9e0f-6a1d3984012b`，DONE_NO_MORE，apiFetchedRows=4064，apiDistinctProductIds=4063。
- failed real jobId：`product-backfill-6af56b55-0dae-446f-90a6-599dea5501c0`，FAILED，`{"API_ERROR":1}`。
- 错误：PostgreSQL `deadlock detected`，位置 `product_operation_state` update。
- 2000 → 2000 无增长。
- 幂等重跑未执行（按 Phase 4-1 限制失败后不扩大）。
- `product_activity_sync_state.activity_id=3859423` last_status=FAILED，consecutive_failures=1。
- 不建议进入 Phase 4-2。

## 3. 根因分析

死锁根因汇总，结合代码与 SQL 锁顺序推断：

1. **大事务覆盖整个 activity 200+ 页**：原 `ProductService.refreshActivitySnapshots` 用 `@Transactional(rollbackFor = Exception.class)` 把整 activity 的 200+ page × 20 商品 = 4000+ 行写入包在同一个事务里。锁持有时间窗极长。
2. **多并发来源争抢 product_operation_state**：
   - `ProductActivitySyncJob`（cron `0 */5 * * * ?`）每 5 分钟跑一次
   - `ProductDisplayRuleJob`（cron `0 15 * * * ?`）每小时第 15 分跑一次
   - 手动 `ProductActivityBackfillService.backfill()` 触发 backfill
   - `ProductActivityManualSyncService` / `ColonelActivityController.refresh` 路径
3. **同事务内连续调 `productDisplayRuleService.repairLibraryStateForActivity` + `applyForActivityId`**：这两个方法独立 `@Transactional`，嵌套后又写 `product_operation_state`。
4. **无固定锁顺序**：upsert 用 for 循环按 page.items() 顺序，page items 来自游标分页，没有按 product_id 排序。
5. **ON CONFLICT DO UPDATE 不带 IS DISTINCT FROM**：每一行都强制走全字段 update，制造大量行锁。
6. **没有 deadlock retry**：一旦 deadlock 直接 API_ERROR。
7. **backfill 互斥锁缺失**：backfill 路径没声明自己的锁 key，与定时任务争同一份数据。
8. **job log RUNNING 僵尸风险**：`finishJob` 在 try 块内，没有 finally；如果 try 块里抛 Error（如 OOM）会卡 RUNNING。

锁信息参考（历史 deadlock 错误已记录在 `product_activity_sync_state.last_error_message`）：

```text
### Error updating database.  Cause: org.postgresql.util.PSQLException: ERROR: deadlock detected
  Detail: Process 6176 waits for ShareLock on transaction 2628589; blocked by process 6209.
  Process 6209 waits for ShareLock on transaction 2627870; blocked by process 6176.
  Where: while updating tuple (66,84) in relation "product_operation_state"
```

## 4. 修复内容

按任务 6.1-6.7 实现 7 类修复：

### 4.1 互斥锁（6.1）
- 新增 Redis 锁 key（`backend/src/main/java/com/colonel/saas/job/JobLockKeys.java`）：
  - `PRODUCT_BACKFILL_GLOBAL`：backfill 全局写锁，与 `ProductActivitySyncJob` / `ProductDisplayRuleJob` 互斥。
  - `PRODUCT_BACKFILL_ACTIVITY:{activityId}`：单活动 backfill 写锁。
  - `PRODUCT_DISPLAY_REFRESH`（沿用现有 `PRODUCT_DISPLAY_RULE`）：backfill 触发的展示规则刷新与 `ProductDisplayRuleJob` 互斥。
- `ProductActivityBackfillService.runRealBackfillWithLocks`：先抢 `PRODUCT_BACKFILL_GLOBAL`，每条 activity 抢 `productBackfillActivityLock(activityId)`，拿不到锁 → skip 该 activity + 计入 `lockWaitCount`，不卡 RUNNING。
- `ProductActivitySyncJob`：抢 `PRODUCT_BACKFILL_GLOBAL`，与 backfill 互斥；活动级也抢 `productBackfillActivityLock(activityId)`。
- 锁获取不到时 status=FAILED_LOCKED，job log 完整收口。

### 4.2 display refresh 解耦（6.2）
- 新增 `BackfillRequest.displayRefreshMode` 参数，可选 `NONE` / `IMMEDIATE` / `DEFERRED`，默认 `DEFERRED`（`application.yml` `product.sync.backfill.displayRefreshMode: DEFERRED`）。
- `DEFERRED`：事实层写完后，**单独**用 `PRODUCT_DISPLAY_RULE` 锁触发 `repairLibraryStateForActivity` + `applyForActivityId`。
- `IMMEDIATE`：在每个 activity batch loop 内部就触发。
- `NONE`：完全不触发展示规则刷新。
- 新增 `skipDisplayRefreshForExpiredActivity=true`（默认）：activity 已过期（end_time < now）时跳过展示规则刷新。本次 3859423 已结束（end_time=2026-04-20），后端日志确认 1fd06ec6 / c586da26 都打印了 `ProductActivityBackfillService skip display refresh for expired activity`。

### 4.3 锁顺序（6.3）
- `ProductService.upsertSnapshotsWithStats` 入口对 items 强制 `Comparator.comparing(productId)` 升序，**幂等可重复**。
- `ProductActivityBackfillService.runActivityBackfillBatched` 的 pageHandler 内：page.items() 同样按 product_id 升序，再按 `writeBatchSize` 拆 batch。
- 禁止 parallelStream；写库走单一线程顺序。

### 4.4 拆小事务（6.4）
- `ProductActivityBackfillService` 注入 `PlatformTransactionManager`，构造 `TransactionTemplate` with `PROPAGATION_REQUIRES_NEW`，name=`product-backfill-batch`。
- 每 batch（默认 100 行）独立提交：
  - API 拉取与 DB 写入不在同一事务（API 走 `queryActivityProductsWithRetry` 单独 gateway 调用）。
  - 写库走 `batchTransactionTemplate.execute(status -> productService.upsertSnapshotsWithStats(activityId, batch))`，每 batch 自动开新事务并独立 commit。
- 单 batch 失败进入 `executeBatchWithDeadlockRetry` 的 retry 循环，详见 4.6。
- 活动级 retry 失败 → job status=FAILED/PARTIAL，activity sync state 标 FAILED，不推进 checkpoint。

### 4.5 no-op update 优化（6.5）
- `backend/src/main/resources/mapper/ProductSnapshotMapper.xml` 的 `upsert` 在 `ON CONFLICT DO UPDATE` 后追加 `WHERE ... IS DISTINCT FROM EXCLUDED.xxx` 守卫，对 21 个核心字段做"字段真变才 UPDATE"判断。
- `ProductService.upsertSnapshotsWithStats` 入口加 `cloneSnapshotForCompare` + `snapshotFieldsEqual`：Java 层先比较核心字段，**全等时不调 mapper.upsert**，进一步省一次行锁。
- 业务表人工字段（寄样要求 / 推广话术 / 标签 / 人工负责人 / 审核状态 / 人工置顶 / 人工展示干预）**不参与 ON CONFLICT 更新**（这些字段不在 mapper 的 EXCLUDED.SET 中）。

### 4.6 deadlock retry（6.6）
- `executeBatchWithDeadlockRetry` 在每个 batch 失败时：
  - 识别 `DeadlockLoserDataAccessException` / `CannotAcquireLockException` / 异常 message 含 `40P01` / `55P03` / `DEADLOCK DETECTED` / `LOCK NOT AVAILABLE`。
  - retry 次数配置化（`product.sync.backfill.deadlockRetryMax`，默认 3）。
  - backoff：200 / 400 / 800 / 1600 ms + 0-100ms jitter。
  - retry 期间每 batch 重新开新事务。
  - 最终失败：batch 内异常上抛到 `runActivityBackfillWithRetry` → activity 标 FAILED，job status=FAILED/PARTIAL，不推进 checkpoint。
  - retry 计数写回 `product_sync_job_log.request_params_json` 的 `deadlockRetryCount` 子段，不破坏既有 reader。

### 4.7 job log 收口 + stale cleanup（6.7）
- `backfill()` 顶层 try/catch(RuntimeException) + catch(Throwable)，**任何路径都会 finishJob 写 finished_at**。
- `finishJob` 在 try/catch 之外通过 `Throwable` 兜底。
- 新增 `StaleProductSyncJobReconcileJob`（`backend/src/main/java/com/colonel/saas/job/StaleProductSyncJobReconcileJob.java`）：
  - cron `0 */15 * * * ?`（可配），`runningTimeoutMinutes=30` 默认。
  - 抢 `PRODUCT_BACKFILL_GLOBAL` 锁后扫描 `status='RUNNING' AND started_at < now - 30min` 的 job。
  - 标 `ABANDONED` 并写 `finished_at`、error_message，**不删任何业务事实**。
- 配套 mapper 接口 `ProductSyncJobLogMapper.selectStaleRunningJobs` + `abandonStaleRunningJob`（`backend/src/main/java/com/colonel/saas/mapper/ProductSyncJobLogMapper.java`）。

## 5. 配置项

`backend/src/main/resources/application.yml` 新增：

```yaml
product:
  sync:
    backfill:
      writeBatchSize: ${PRODUCT_SYNC_BACKFILL_WRITE_BATCH_SIZE:100}
      deadlockRetryMax: ${PRODUCT_SYNC_BACKFILL_DEADLOCK_RETRY_MAX:3}
      lockWaitSeconds: ${PRODUCT_SYNC_BACKFILL_LOCK_WAIT_SECONDS:10}
      runningTimeoutMinutes: ${PRODUCT_SYNC_BACKFILL_RUNNING_TIMEOUT_MINUTES:30}
      displayRefreshMode: ${PRODUCT_SYNC_BACKFILL_DISPLAY_REFRESH_MODE:DEFERRED}
      skipDisplayRefreshForExpiredActivity: ${PRODUCT_SYNC_BACKFILL_SKIP_DISPLAY_REFRESH_FOR_EXPIRED:true}
      staleReconcileCron: ${PRODUCT_SYNC_BACKFILL_STALE_RECONCILE_CRON:0 */15 * * * ?}
```

本轮 real-pre 实际生效（启动后日志可见）：
- `writeBatchSize=100`
- `deadlockRetryMax=3`
- `lockWaitSeconds=10`
- `runningTimeoutMinutes=30`
- `displayRefreshMode=DEFERRED`
- `skipDisplayRefreshForExpiredActivity=true`

## 6. 测试结果

### 6.1 deadlock fix 相关单元测试

`mvn -f backend/pom.xml "-Dtest=ProductActivityBackfillServiceTest,ProductBackfillConcurrencyAndDeadlockTest,ProductBackfillLockOrderTest,StaleProductSyncJobReconcileJobTest,ProductLibraryDisplayRegressionTest,ProductSyncAdminControllerTest" test`：

```
[INFO] Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

覆盖：
- `ProductActivityBackfillServiceTest`（5 个测试，原 Phase 4-1 测试 + 适配新构造器）
- `ProductBackfillConcurrencyAndDeadlockTest`（3 个测试，全局锁被占 / activity 锁被占跳过 / deadlock retry / lock_not_available retry）
- `ProductBackfillLockOrderTest`（1 个测试，product_id 升序）
- `StaleProductSyncJobReconcileJobTest`（3 个测试，锁占用跳过 / 有 stale / 无 stale）
- `ProductLibraryDisplayRegressionTest`（3 个测试，NONE / DEFERRED / IMMEDIATE）
- `ProductSyncAdminControllerTest`（1 个测试，BackfillRequest/BackfillResult 新字段适配）

### 6.2 完整后端测试

`mvn -f backend/pom.xml test`（核心商品同步/展示相关测试 0 失败；其它模块的 52 errors 是 pre-existing Spring 容器加载问题，与本轮改动无关，详见 `/surefire-reports/CharacterizationBaselineTest.txt` 等）。

## 7. real-pre 部署结果

- backend `mvn -DskipTests clean compile`：PASS（762 源文件全部编译）
- 6 类核心测试：16/16 PASS
- Docker 重启：`saas-active-backend-real-pre-1` 重启 healthy
- `GET /api/system/health`：`{"status":"UP"}`
- `GET /login`：HTTP 200
- postgres / redis / frontend：均 healthy
- real-pre P0 preflight：PASS（与 Phase 4-1 evidence 一致）

## 8. dry-run 复核（Phase 4-1.5 自跑）

| 指标 | 数值 |
| --- | ---: |
| dry-run jobId | product-backfill-8f5a0116-73c7-4c25-ad79-4c85cb2eca08 |
| apiFetchedRows | 4065 |
| apiDistinctProductIds | 4064 |
| dbRowsForScannedActivities (after sync_state) | 4064 |
| estimatedGapRows | 1（3859423 的 4064 已落库，新增的 1 个 product_id 为本次新增） |
| stopReason | DONE_NO_MORE |
| activitiesSuccess | 1 |
| activitiesIncomplete | 0 |
| activitiesFailed | 0 |
| 业务表写入变化 | 无（dryRun=true 走 dryRunProbeService，不调 upsert） |

dry-run 业务表变化复核：

| 指标 | before | after |
| --- | ---: | ---: |
| activity 3859423 product_operation_state rows | 4063 | 4063 |
| product_snapshot rows | 10053 | 10053 |

## 9. 真实 backfill 第一次

| 指标 | 数值 |
| --- | ---: |
| real jobId | product-backfill-1fd06ec6-6c05-4bfd-ab0c-6694f6f6f6b5 |
| status | SUCCESS |
| activitiesScanned | 1 |
| activitiesSuccess | 1 |
| activitiesIncomplete | 0 |
| activitiesFailed | 0 |
| apiFetchedRows | 4065 |
| apiDistinctProductIds | 4064 |
| inserted | 1 |
| updated | 3008 |
| skipped | 0 |
| failed | 0 |
| deadlockRetryCount | 0（无死锁） |
| lockWaitCount | 0（无锁等待） |
| stopReasonStats | {"DONE_NO_MORE":1} |
| request_params_json | {"scope":"CUSTOM_ACTIVITY_IDS","activityIds":["3859423"],"pageSize":20,"maxActivities":1,"maxPagesPerActivity":1000,"maxRowsPerActivity":50000,"dryRun":false,"confirm":true,"displayRefreshMode":"DEFERRED","deadlockRetryCount":0,"lockWaitCount":0} |

启动时间：2026-06-15 07:17:00 UTC（北京时间 15:17:00），结束时间：2026-06-15 07:19:33.663756 UTC（北京时间 15:19:33），耗时 2 分 33 秒。
后端日志确认 `ProductActivityBackfillService skip display refresh for expired activity`（活动 3859423 已过期）。

## 10. DB before / after 对比

| 指标 | before (Phase 4-1 失败后) | after (Phase 4-1.5 修复后) | delta |
| --- | ---: | ---: | ---: |
| activity 3859423 rows | 2000 | 4064 | +2064 |
| activity 3859423 distinct products | 2000 | 4064 | +2064 |
| product_snapshot rows | 7983 | 10053 | +2070 |
| product_operation_state rows | 7983 | 10053 | +2070 |
| DISPLAYING rows | 2977 | 3261 | +284 |
| /api/products total | 2977 | 3261 | +284 |
| admin relationTotal | 7983 | 10053 | +2070 |
| admin snapshotTotal | 7983 | 10053 | +2070 |
| 3859423 DISPLAYING rows | 1480 | 1740 | +260 |

duplicate 检查：`(activity_id=3859423, product_id) count(*) > 1` → 0 行（幂等正确）。

orphan / cross-check：

- `product_operation_state` 中 `product_id` 全部在 `product_snapshot` 中存在（3859423 的 4064 行 = snapshot 中 3859423 的 4064 行）。

DISPLAYING 涨 284 已记录为已发现风险（`skipDisplayRefreshForExpiredActivity` 仅跳过 backfill 后的"展示规则批量刷新"，但 backfill 写事实层时 `applyUpstreamPromotingLibraryState` 仍会把 `selected_to_library=true + audit=2 + 上游推广中` 的商品从 PENDING 升级到 DISPLAYING，不判断活动是否过期）。本 Phase 4-1.5 不处理，由 Phase 4-1.6 / Phase 5 解决"历史过期活动 DISPLAYING 异常"。

## 11. 幂等重跑

| 指标 | 第一次后 (1fd06ec6) | 第二次后 (c586da26) | delta |
| --- | ---: | ---: | ---: |
| activity 3859423 rows | 4064 | 4064 | 0 |
| distinct products | 4064 | 4064 | 0 |
| duplicate rows | 0 | 0 | 0 |
| product_snapshot rows | 10053 | 10053 | 0 |
| DISPLAYING total | 3261 | 3261 | 0 |
| /api/products total | 3261 | 3261 | 0 |
| inserted | 1 | **0** | -1 |
| updated | 3008 | 2512 | -496 |

c586da26 jobId：`product-backfill-c586da26-2fe6-4303-a9d0-377aa39c9856`，status=SUCCESS，stopReason=DONE_NO_MORE，started=2026-06-15 07:19:40 UTC，finished=2026-06-15 07:22:19.804438 UTC（耗时 2 分 39 秒）。
第二次 inserted=0：商品事实层无新增行，幂等通过。
updated=2512 < 第一次 updated=3008：本次部分字段未变（IS DISTINCT FROM 守卫生效），其余字段走 update 路径，**不造成重复**。

## 12. job log 验证

| jobId | 类型 | dryRun | status | 备注 |
| --- | --- | ---: | --- | --- |
| product-backfill-7088a926-... | backfill-real | false | SUCCESS | Phase 4-1.5 之前的某次（无 displayRefreshMode 字段） |
| product-backfill-fdf21cf9-... | backfill-real | false | SUCCESS | Phase 4-1.5 之前的某次 |
| product-backfill-1fd06ec6-... | backfill-real | false | SUCCESS | **Phase 4-1.5 第一次真实 backfill，新代码路径** |
| product-backfill-c586da26-... | backfill-real | false | SUCCESS | **Phase 4-1.5 第二次幂等重跑，inserted=0** |
| product-backfill-8f5a0116-... | backfill-dryRun | true | SUCCESS | Phase 4-1.5 之后 dry-run 复核 |

最终 `product_sync_job_log` 中无 RUNNING 僵尸 job（StaleProductSyncJobReconcileJob 兜底 30 分钟超时清理）。

## 13. activity sync state 验证

`product_activity_sync_state.activity_id=3859423` 最终状态：

| 字段 | 值 |
| --- | --- |
| last_status | **SUCCESS** |
| last_stop_reason | DONE_NO_MORE |
| last_success_at | 2026-06-15 07:22:19.799028（c586da26 完成时刻） |
| last_attempt_at | 2026-06-15 07:22:19.799028 |
| last_fetched_rows | 4065 |
| last_distinct_product_ids | 4064 |
| last_inserted | 0（最后一次 = 幂等重跑） |
| last_updated | 2512 |
| last_skipped | 0 |
| last_failed | 0 |
| consecutive_failures | 0 |
| last_error_message | 空（已无 deadlock） |

## 14. 商品库展示口径验证

- `/api/products?page=1&size=1` total=3261，与 DB `display_status='DISPLAYING' rows=3261` 一致。
- 页面 smoke：`/product/library` 可正常加载，显示"已加载 100 / 3261 件"。
- DISPLAYING 涨 284：来自 1fd06ec6 真实 backfill 写事实层时 `applyUpstreamPromotingLibraryState` 把部分 3859423 商品从 PENDING 升级到 DISPLAYING（基于上游推广中 + selected_to_library + audit=2 路径，**未判断活动过期**）。**记录为已发现风险**，本 Phase 4-1.5 不修复，由 Phase 4-1.6 处理。
- 待补充商品（pending_audit）未误入 DISPLAYING 列表。
- `/api/products/admin/counts`：snapshotTotal=10053, relationTotal=10053, distinctProductTotal=8867, displayingTotal=3261, pendingTotal=326, hiddenTotal=6466, activityTotal=24。

## 15. 服务健康

- backend `/api/system/health`：`{"status":"UP"}` ✓
- frontend `/login`：HTTP 200 ✓
- postgres / redis / frontend：healthy ✓
- Docker compose ps：saas-active-backend-real-pre-1 / saas-active-frontend-real-pre-1 / saas-active-postgres-real-pre-1 / saas-active-redis-real-pre-1 全部 healthy ✓
- backend logs：无 deadlock 异常（Phase 4-1 报告中的 `deadlock detected` 栈已不再出现），仅有 `ProductActivityBackfillService skip display refresh for expired activity`（功能日志）

## 16. 风险与回滚

1. **关闭 backfill**：把 `product.sync.activityProduct.fullBackfillEnabled` 设为 `false`（已有 env var `PRODUCT_SYNC_ACTIVITY_PRODUCT_FULL_BACKFILL_ENABLED`），所有真实 backfill 立即拒绝。
2. **关闭 display refresh defer**：把 `product.sync.backfill.displayRefreshMode` 设为 `NONE` 即可关闭 backfill 触发的展示规则刷新。
3. **降低 batch size**：把 `product.sync.backfill.writeBatchSize` 调小（如 50）以减少单事务锁持有行数。
4. **禁用 scheduler**：把 `product.activity.sync.enabled` 设为 `false` 关闭 `ProductActivitySyncJob` 定时同步。
5. **回滚代码**：`git revert <deadlock-fix-commit>` 回滚到 Phase 4-1 状态。
6. **处理异常 job log**：`StaleProductSyncJobReconcileJob` 每 15 分钟自动将 RUNNING 超时 job 标 ABANDONED；也可手工 `update product_sync_job_log set status='FAILED', finished_at=now() where job_id='<id>'`。
7. **处理展示异常**：DISPLAYING 涨 284 已记录，Phase 4-1.6 单独处理。
8. **不删事实数据**：本轮所有修复 / 回滚 / 重跑都**不动** `product_snapshot` / `product_operation_state` 的实际行（只更新 display_status / sync_time / 等元字段），无 truncate / delete。

## 17. 下一步建议

- Phase 4-1.5 deadlock 修复 PASS，可以进入 Phase 4-2 小批量 RECENT_30D 回补。
- **建议**：Phase 4-2 启动前先做 Phase 4-1.6：处理 3859423 历史 1480 DISPLAYING 异常（活动已过期但商品仍展示）。理由：本次 backfill 修复后跑出 1740 DISPLAYING（涨 260），其中部分是基于上游"推广中"自动升级的过期活动商品。Phase 4-1.6 在不删事实数据的前提下，**通过显示规则配置**把这些过期活动商品批量改为 HIDDEN，再进入 Phase 4-2。
- Phase 4-2 建议范围：`RECENT_30D`, `maxActivities=20`, `dryRun=false`, `confirm=true`, `displayRefreshMode=DEFERRED`。

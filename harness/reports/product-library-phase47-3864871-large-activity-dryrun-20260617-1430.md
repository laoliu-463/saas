# 商品库 Phase 4-7：3864871 超大活动专项 dry-run 报告

## 1. 结论

**PARTIAL**：`3864871` 在 `maxPagesPerActivity=3000`、`maxRowsPerActivity=60000` 下未到 `DONE_NO_MORE`，终态为 `MAX_ROWS_REACHED`。

本阶段未执行真实 backfill，未传 `dryRun=false`，未传 `confirm=true`，不得宣布 `3864871` 已补齐，也不得宣布 RECENT_30D 全量完成。

## 2. 背景

RECENT_30D 已完成 23/24 活动真实回补。唯一剩余活动为 `3864871`。

上一次 `maxPages=2000` 已拉取 `40000` rows / `35679` distinct，但 `MAX_PAGES_REACHED`。本阶段只做 dry-run，不真实写库。

## 3. 执行参数

| 参数 | 值 |
| --- | --- |
| activityId | 3864871 |
| scope | CUSTOM_ACTIVITY_IDS |
| dryRun | true |
| pageSize | 20 |
| maxActivities | 1 |
| maxPagesPerActivity | 3000 |
| maxRowsPerActivity | 60000 |
| confirm | false |

说明：前置检查时已存在同参数 RUNNING job，本轮未重复提交，改为接管轮询该 job。

## 4. 前置检查

| 项 | 结果 |
| --- | --- |
| Branch | `feature/ddd/DDD-VERIFY-001` |
| Commit | `2d088e00` |
| Docker | backend/frontend/postgres/redis 均 healthy |
| backend health | `UP` |
| auth login | code=200，认证成功，未记录凭证 |
| RUNNING job | 发现 1 个既有同参数 dry-run job |
| Redis lock | 前置查询未发现残留；运行中 Redis backfill lock=1，结束后 lock=0 |
| product_snapshot baseline | 53006 rows / 41128 distinct |
| product_operation_state baseline | 53006 rows / 41128 distinct |
| DISPLAYING baseline | 14636 |
| HIDDEN baseline | 37993 |
| PENDING baseline | 377 |
| /api/products total baseline | 14636 |
| duplicate_groups baseline | 0 |

## 5. dry-run 结果

| 指标 | 数值 |
| --- | ---: |
| jobId | `product-backfill-b5e983f8-456d-40cb-9940-e30512ac2ea2` |
| status | PARTIAL |
| activitiesScanned | 1 |
| activitiesSuccess | 0 |
| activitiesIncomplete | 1 |
| activitiesFailed | 0 |
| apiFetchedRows | 60000 |
| apiDistinctProductIds | 52032 |
| stopReasonStats | `{"MAX_ROWS_REACHED":1}` |
| errorMessage | `stopReason=MAX_ROWS_REACHED; message=dry run failed` |
| duration | 27m33s |
| lockWaitCount | 0 |
| deadlockRetryCount | 0 |

日志片段：`harness/reports/product-backfill-3864871-phase47-backend-log-20260617-1429.txt`。

## 6. before / after 验证

| 指标 | before | after | delta |
| --- | ---: | ---: | ---: |
| product_snapshot | 53006 | 53009 | +3 |
| product_operation_state | 53006 | 53009 | +3 |
| distinctProduct | 41128 | 41129 | +1 |
| DISPLAYING | 14636 | 14639 | +3 |
| HIDDEN | 37993 | 34628 | -3365 |
| PENDING | 377 | 3742 | +3365 |
| /api/products total | 14636 | 14639 | +3 |
| duplicate_groups | 0 | 0 | 0 |
| RUNNING job | 1 | 0 | -1 |
| Redis backfill lock | 0 | 0 | 0 |

`product_snapshot` / `product_operation_state` 的 +3 不是 `3864871` dry-run 写入。数据库按 `create_time >= 2026-06-17 05:59:00` 查询，新增行属于 `3929905`、`3929906`、`3916506`；backend 日志显示它们来自 `ProductActivitySyncJob` 的真实同步，分别 `created=1/createdCount=1`，且 `dryRun=false`。同一窗口内 `3864871` 新增行数为 0，job_log 的 `inserted/updated/skipped/failed` 均为 0。

状态分布变化由 06:15 的 `ProductDisplayRuleJob completed, processedProductIds=14639` 解释；这会调整展示状态，但不代表 `3864871` dry-run 真实写库。

## 7. 结论解释

`3864871` 在 `maxRowsPerActivity=60000` 下仍未完整读取。由于 `60000 = 3000 * pageSize(20)`，该结果等价于 3000 页 / 60000 行边界仍不足。

不允许真实 backfill。不允许继续直接加到 5000。建议进入 Phase 4-8：分段式 cursor/page checkpoint 设计。

## 8. 风险

1. 本阶段未真实写库。
2. 未补齐 `3864871`。
3. 不能宣布 RECENT_30D 全量完成。
4. 如果继续盲目加页数，仍可能触发长任务、限流或状态不可观测问题。
5. 状态接口没有按页刷新 `apiFetchedRows`，中间进度主要依赖 backend 日志旁路观察。
6. 本轮执行窗口内存在商品定时同步与展示规则投影并发运行，环境不是完全静默；结论依赖 activityId 与 job_log 交叉排除，而不是简单表总数不变。

## 9. 下一步建议

进入 Phase 4-8：为 `3864871` 设计分段拉取和 checkpoint，明确分段边界、幂等、锁、失败恢复和验证口径后再评估真实 backfill。
